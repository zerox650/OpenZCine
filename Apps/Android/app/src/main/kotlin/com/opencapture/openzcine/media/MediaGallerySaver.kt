package com.opencapture.openzcine.media

import android.content.Context
import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import com.opencapture.opencine.appkit.MediaStoreInsertPolicy
import com.opencapture.openzcine.R
import com.opencapture.openzcine.lstr
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** A complete immutable video or still copy that may be written into the device gallery. */
internal data class MediaGalleryArtifact(
    val file: Path,
    val displayName: String,
    val mimeType: String,
    val expectedBytes: Long,
    val captureTimestampMillis: Long? = null,
) {
    companion object {
        /** Converts the existing complete-cache staging result into a gallery input. */
        fun fromStagedShare(
            share: StagedMediaShare,
            captureTimestampMillis: Long? = null,
        ): MediaGalleryArtifact {
            if (!share.mimeType.startsWith("video/") && !share.mimeType.startsWith("image/")) {
                throw InvalidMediaGalleryArtifactException(
                    "Only staged video or still-image media can be saved to Gallery.",
                )
            }
            if (!isSafeMediaDisplayName(share.displayName)) {
                throw InvalidMediaGalleryArtifactException("The prepared media has an unsafe display name.")
            }
            val mimeType = galleryMediaMimeType(share.displayName)
                ?: throw InvalidMediaGalleryArtifactException(
                    "${share.displayName} isn't a supported Gallery video or photo.",
                )
            val file = share.file.toAbsolutePath().normalize()
            val byteCount =
                runCatching { Files.size(file) }
                    .getOrElse {
                        throw InvalidMediaGalleryArtifactException(
                            "${share.displayName} is no longer available for Gallery.",
                            it,
                        )
                    }
            return MediaGalleryArtifact(
                file,
                share.displayName,
                mimeType,
                byteCount,
                captureTimestampMillis,
            )
        }
    }
}

/** A scoped MediaStore row that remains hidden while its bytes are being written. */
internal data class PendingMediaGalleryItem(val identifier: String)

/** Small platform seam that keeps MediaStore orchestration host-JVM testable. */
internal interface MediaGalleryGateway {
    fun createPending(artifact: MediaGalleryArtifact): PendingMediaGalleryItem

    fun openPending(item: PendingMediaGalleryItem): OutputStream

    fun publish(item: PendingMediaGalleryItem)

    fun deletePending(item: PendingMediaGalleryItem)
}

/** Debug-only failure point selected by the variant-specific demo harness. */
internal enum class MediaGalleryFailureInjection {
    NONE,
    WRITE_ONCE,
    ;

    companion object {
        fun parse(raw: String?): MediaGalleryFailureInjection =
            when (raw?.lowercase(Locale.ROOT)) {
                "write", "write-once" -> WRITE_ONCE
                else -> NONE
            }
    }
}

/** Android 10+ scoped-storage adapter for publishing operator-selected media. */
internal class AndroidMediaGalleryGateway(
    private val resolver: ContentResolver,
    failureInjection: MediaGalleryFailureInjection = MediaGalleryFailureInjection.NONE,
) : MediaGalleryGateway {
    private val failNextWrite = AtomicBoolean(failureInjection == MediaGalleryFailureInjection.WRITE_ONCE)

    override fun createPending(artifact: MediaGalleryArtifact): PendingMediaGalleryItem {
        val isImage = MediaStoreInsertPolicy.isImage(artifact.mimeType)
        val collection =
            if (isImage) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        fun values(mimeType: String) =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, artifact.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, GALLERY_POLICY.relativePath(artifact.mimeType))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                artifact.captureTimestampMillis?.let { timestamp ->
                    put(
                        if (isImage) {
                            MediaStore.Images.ImageColumns.DATE_TAKEN
                        } else {
                            MediaStore.Video.VideoColumns.DATE_TAKEN
                        },
                        timestamp,
                    )
                }
            }
        val uri =
            try {
                resolver.insert(collection, values(artifact.mimeType))
            } catch (rejected: IllegalArgumentException) {
                // Some providers reject camera-raw MIME types (.nef); retry the
                // same row generically so the bytes still land in the Gallery.
                resolver.insert(collection, values(FALLBACK_MIME_TYPE))
            } ?: throw MediaGalleryStoreUnavailableException(
                "Android didn't create a pending Gallery item.",
            )
        return PendingMediaGalleryItem(uri.toString())
    }

    override fun openPending(item: PendingMediaGalleryItem): OutputStream {
        val uri = android.net.Uri.parse(item.identifier)
        val output =
            resolver.openOutputStream(uri, "w")
                ?: throw MediaGalleryStoreUnavailableException(
                    "Android didn't open the pending Gallery item.",
                )
        if (!failNextWrite.compareAndSet(true, false)) return output
        return object : FilterOutputStream(output) {
            private fun fail(): Nothing = throw IOException("Injected Gallery write failure.")

            override fun write(value: Int) = fail()

            override fun write(bytes: ByteArray, offset: Int, length: Int) = fail()
        }
    }

    override fun publish(item: PendingMediaGalleryItem) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        val updated = resolver.update(android.net.Uri.parse(item.identifier), values, null, null)
        if (updated != 1) {
            throw MediaGalleryPublishException("Android didn't publish the completed Gallery item.")
        }
    }

    override fun deletePending(item: PendingMediaGalleryItem) {
        // A zero count is idempotent success: the provider may already have discarded the row.
        resolver.delete(android.net.Uri.parse(item.identifier), null, null)
    }

    private companion object {
        val GALLERY_POLICY = MediaStoreInsertPolicy("OpenZCine")

        /** Generic MIME retried when a provider rejects an exact raw-photo type. */
        const val FALLBACK_MIME_TYPE = "application/octet-stream"
    }
}

/** Parses the camera's PTP date without inventing a timestamp when metadata is incomplete. */
internal fun mediaCaptureTimestampMillis(
    captureDate: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? =
    runCatching {
        LocalDateTime.parse(captureDate, PTP_CAPTURE_DATE_FORMATTER)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

private val PTP_CAPTURE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

/** Stable operator-facing failure categories for one Gallery item. */
internal enum class MediaGalleryFailureKind {
    PERMISSION_DENIED,
    SOURCE_UNAVAILABLE,
    STORAGE_UNAVAILABLE,
    WRITE_FAILED,
    PUBLISH_FAILED,
    CLEANUP_FAILED,
}

/** One failed item in a sequential Gallery batch. */
internal data class MediaGalleryFailure(
    val displayName: String,
    val kind: MediaGalleryFailureKind,
    val operatorMessage: String,
)

/** Items intentionally omitted before the MediaStore writer runs. */
internal data class MediaGalleryOmissions(
    val nonVideoCount: Int = 0,
    val incompleteCount: Int = 0,
    val preparationFailureCount: Int = 0,
    val temporaryCleanupFailureCount: Int = 0,
) {
    init {
        require(nonVideoCount >= 0)
        require(incompleteCount >= 0)
        require(preparationFailureCount >= 0)
        require(temporaryCleanupFailureCount >= 0)
    }

    val totalCount: Int
        get() = nonVideoCount + incompleteCount + preparationFailureCount
}

/** Complete and partial outcome from a sequential Gallery batch. */
internal data class MediaGalleryBatchResult(
    val savedCount: Int,
    val failures: List<MediaGalleryFailure>,
) {
    init {
        require(savedCount >= 0)
    }

    val failedCount: Int
        get() = failures.size

    /** Concise, truthful result text shared by browser and playback. */
    fun operatorMessage(
        omissions: MediaGalleryOmissions = MediaGalleryOmissions(),
        context: Context? = null,
    ): String {
        val parts = mutableListOf<String>()
        when {
            savedCount > 0 -> parts += galleryQuantity(context, savedCount,
                R.string.gallery_saved_one, R.string.gallery_saved_other, savedCount)
            failedCount > 0 -> parts += failures.first().operatorMessage
            else -> parts += lstr(context, R.string.gallery_saved_none)
        }
        if (savedCount > 0 && failedCount > 0) {
            parts += galleryQuantity(context, failedCount,
                R.string.gallery_failed_save_one, R.string.gallery_failed_save_other, failedCount)
        } else if (savedCount == 0 && failedCount > 1) {
            parts += galleryQuantity(context, failedCount - 1,
                R.string.gallery_failed_more_one, R.string.gallery_failed_more_other, failedCount - 1)
        }
        if (omissions.nonVideoCount > 0) {
            parts += galleryQuantity(context, omissions.nonVideoCount,
                R.string.gallery_skipped_nonvideo_one, R.string.gallery_skipped_nonvideo_other,
                omissions.nonVideoCount)
        }
        if (omissions.incompleteCount > 0) {
            parts += galleryQuantity(context, omissions.incompleteCount,
                R.string.gallery_incomplete_one, R.string.gallery_incomplete_other,
                omissions.incompleteCount)
        }
        if (omissions.preparationFailureCount > 0) {
            parts += galleryQuantity(context, omissions.preparationFailureCount,
                R.string.gallery_prepare_failed_one, R.string.gallery_prepare_failed_other,
                omissions.preparationFailureCount)
        }
        if (omissions.temporaryCleanupFailureCount > 0) {
            parts += galleryQuantity(context, omissions.temporaryCleanupFailureCount,
                R.string.gallery_export_removed_one, R.string.gallery_export_removed_other,
                omissions.temporaryCleanupFailureCount)
        }
        return parts.joinToString(
            separator = lstr(context, R.string.gallery_message_separator),
            postfix = lstr(context, R.string.gallery_message_period),
        )
    }
}

/**
 * English inflects the noun by count; the other locales read one form for every count. Keeps the
 * count==1 selection in one place so a new fragment cannot re-derive it.
 */
private fun galleryQuantity(
    context: Context?,
    count: Int,
    oneRes: Int,
    otherRes: Int,
    vararg args: Any?,
): String = lstr(context, if (count == 1) oneRes else otherRes, *args)

/**
 * Sequentially copies complete prepared media into scoped MediaStore rows.
 *
 * Each row remains `IS_PENDING=1` until the source byte count is verified. Any
 * exception or cancellation after insertion attempts deletion before the
 * failure is returned or cancellation is rethrown.
 */
internal class MediaGallerySaver(private val gateway: MediaGalleryGateway) {
    fun save(
        artifacts: List<MediaGalleryArtifact>,
        context: Context? = null,
        cancellationCheck: () -> Unit = {},
    ): MediaGalleryBatchResult {
        var saved = 0
        val failures = mutableListOf<MediaGalleryFailure>()
        artifacts.forEach { artifact ->
            cancellationCheck()
            val failure = saveOne(artifact, context, cancellationCheck)
            if (failure == null) saved += 1 else failures += failure
        }
        return MediaGalleryBatchResult(savedCount = saved, failures = failures)
    }

    internal fun saveOne(
        artifact: MediaGalleryArtifact,
        context: Context? = null,
        cancellationCheck: () -> Unit,
    ): MediaGalleryFailure? {
        var phase = SavePhase.VALIDATE
        var pending: PendingMediaGalleryItem? = null
        try {
            validateArtifact(artifact)
            cancellationCheck()
            phase = SavePhase.CREATE
            pending = gateway.createPending(artifact)
            cancellationCheck()
            phase = SavePhase.WRITE
            gateway.openPending(pending).use { output ->
                copyExact(artifact, output, cancellationCheck)
            }
            cancellationCheck()
            phase = SavePhase.PUBLISH
            gateway.publish(pending)
            return null
        } catch (cancelled: CancellationException) {
            val cleanupFailure = pending?.let(::cleanupFailure)
            cleanupFailure?.let(cancelled::addSuppressed)
            throw cancelled
        } catch (error: Exception) {
            val cleanupFailure = pending?.let(::cleanupFailure)
            return classifyFailure(artifact.displayName, phase, error, cleanupFailure, context)
        }
    }

    private fun validateArtifact(artifact: MediaGalleryArtifact) {
        if (!isSafeMediaDisplayName(artifact.displayName)) {
            throw InvalidMediaGalleryArtifactException("The Gallery display name is unsafe.")
        }
        if (galleryMediaMimeType(artifact.displayName) != artifact.mimeType) {
            throw InvalidMediaGalleryArtifactException("The prepared Gallery media type is inconsistent.")
        }
        if (artifact.expectedBytes <= 0) {
            throw InvalidMediaGalleryArtifactException("The prepared Gallery media is empty.")
        }
        if (
            !Files.isRegularFile(artifact.file, LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(artifact.file) ||
                Files.size(artifact.file) != artifact.expectedBytes
        ) {
            throw InvalidMediaGalleryArtifactException("The prepared Gallery media is no longer complete.")
        }
    }

    private fun copyExact(
        artifact: MediaGalleryArtifact,
        output: OutputStream,
        cancellationCheck: () -> Unit,
    ) {
        FileChannel.open(
            artifact.file,
            StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS,
        ).use { source ->
            if (source.size() != artifact.expectedBytes) {
                throw InvalidMediaGalleryArtifactException("The prepared Gallery media changed before writing.")
            }
            val buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES)
            var copied = 0L
            while (copied < artifact.expectedBytes) {
                cancellationCheck()
                buffer.clear()
                buffer.limit(
                    minOf(buffer.capacity().toLong(), artifact.expectedBytes - copied).toInt(),
                )
                val read = source.read(buffer)
                if (read < 0) {
                    throw InvalidMediaGalleryArtifactException("The prepared Gallery media ended early.")
                }
                if (read == 0) continue
                output.write(buffer.array(), 0, read)
                copied += read.toLong()
            }
            cancellationCheck()
            output.flush()
            if (copied != artifact.expectedBytes || source.size() != artifact.expectedBytes) {
                throw InvalidMediaGalleryArtifactException("The prepared Gallery media changed while writing.")
            }
        }
    }

    private fun cleanupFailure(item: PendingMediaGalleryItem): Exception? =
        try {
            gateway.deletePending(item)
            null
        } catch (error: Exception) {
            error
        }

    private fun classifyFailure(
        displayName: String,
        phase: SavePhase,
        error: Exception,
        cleanupFailure: Exception?,
        context: Context? = null,
    ): MediaGalleryFailure {
        if (cleanupFailure != null) {
            return MediaGalleryFailure(
                displayName = displayName,
                kind = MediaGalleryFailureKind.CLEANUP_FAILED,
                operatorMessage = lstr(context, R.string.gallery_fail_cleanup, displayName),
            )
        }
        if (error is SecurityException) {
            return MediaGalleryFailure(
                displayName = displayName,
                kind = MediaGalleryFailureKind.PERMISSION_DENIED,
                operatorMessage = lstr(context, R.string.gallery_fail_permission, displayName),
            )
        }
        val kind =
            when {
                error is InvalidMediaGalleryArtifactException ->
                    MediaGalleryFailureKind.SOURCE_UNAVAILABLE
                phase == SavePhase.CREATE -> MediaGalleryFailureKind.STORAGE_UNAVAILABLE
                phase == SavePhase.WRITE -> MediaGalleryFailureKind.WRITE_FAILED
                phase == SavePhase.PUBLISH -> MediaGalleryFailureKind.PUBLISH_FAILED
                else -> MediaGalleryFailureKind.SOURCE_UNAVAILABLE
            }
        val message =
            when (kind) {
                MediaGalleryFailureKind.SOURCE_UNAVAILABLE ->
                    lstr(context, R.string.gallery_fail_source, displayName)
                MediaGalleryFailureKind.STORAGE_UNAVAILABLE ->
                    lstr(context, R.string.gallery_fail_storage, displayName)
                MediaGalleryFailureKind.WRITE_FAILED ->
                    lstr(context, R.string.gallery_fail_write, displayName)
                MediaGalleryFailureKind.PUBLISH_FAILED ->
                    lstr(context, R.string.gallery_fail_publish, displayName)
                MediaGalleryFailureKind.PERMISSION_DENIED,
                MediaGalleryFailureKind.CLEANUP_FAILED,
                -> lstr(context, R.string.gallery_fail_generic, displayName)
            }
        return MediaGalleryFailure(displayName, kind, message)
    }

    private enum class SavePhase {
        VALIDATE,
        CREATE,
        WRITE,
        PUBLISH,
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 256 * 1024
    }
}

/** The prepared item is not a safe, complete Gallery input. */
internal class InvalidMediaGalleryArtifactException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** MediaStore refused to create or open a pending row. */
internal class MediaGalleryStoreUnavailableException(message: String) : IOException(message)

/** MediaStore did not publish exactly one completed row. */
internal class MediaGalleryPublishException(message: String) : IOException(message)

/** Exact MediaStore video MIME metadata after core-owned video classification. */
internal fun galleryVideoMimeType(displayName: String): String? =
    when (displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
        "mov" -> "video/quicktime"
        "mp4", "m4v" -> "video/mp4"
        else -> null
    }

/** Exact MediaStore image MIME metadata for camera stills, raws included. */
internal fun galleryImageMimeType(displayName: String): String? =
    when (displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "heif", "heic", "hif" -> "image/heif"
        "nef" -> "image/x-nikon-nef"
        "tif", "tiff" -> "image/tiff"
        "dng" -> "image/x-adobe-dng"
        "png" -> "image/png"
        else -> null
    }

/** Exact Gallery MIME for any supported staged camera media, video or still. */
internal fun galleryMediaMimeType(displayName: String): String? =
    galleryVideoMimeType(displayName) ?: galleryImageMimeType(displayName)
