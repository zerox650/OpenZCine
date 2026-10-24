@file:androidx.media3.common.util.UnstableApi

package com.opencapture.openzcine.frameio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.opencapture.opencine.frameio.FrameioDeliveryException
import com.opencapture.opencine.runtime.MonitorMovieExportLut
import com.opencapture.opencine.runtime.MonitorMovieExportContainer
import com.opencapture.opencine.runtime.MonitorMovieCodecFactory
import com.opencapture.opencine.runtime.MonitorMovieCodec
import com.opencapture.opencine.runtime.MonitorNativeMovieExporter
import com.opencapture.opencine.runtime.MonitorMovieExportPreparer
import com.opencapture.opencine.runtime.MonitorMovieExportInput
import com.opencapture.opencine.runtime.MonitorPreparedMovieExport
import com.opencapture.opencine.runtime.MonitorLutMovieExporter
import com.opencapture.opencine.runtime.MonitorMovieExportErrorFactory
import com.opencapture.opencine.runtime.MonitorMovieExportFailure
import com.opencapture.openzcine.FeedLutSelection
import com.opencapture.openzcine.R
import com.opencapture.openzcine.bridge.SwiftCore
import com.opencapture.openzcine.lstr
import com.opencapture.openzcine.lut.AndroidLutLibrary
import com.opencapture.openzcine.media.StagedMediaShare
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** One explicit per-run configuration shared by every external media destination. */
internal data class MediaDeliveryConfiguration(
    val bakeLut: Boolean = false,
    val exportContainer: MonitorMovieExportContainer = MonitorMovieExportContainer.MOV,
    val includeMetadata: Boolean = true,
    val selectedLut: FeedLutSelection? = null,
    val forceFrameioReupload: Boolean = false,
) {
    init {
        require(!bakeLut || selectedLut != null) {
            "A LUT-baked delivery requires an operator-selected LUT."
        }
    }
}

/** Non-secret camera/media context retained while a network hop tears down the session. */
internal data class FrameioArtifactContext(
    val cameraID: String,
    val captureDate: String,
    val supportsLutBake: Boolean,
    val stableClipIdentity: String = "",
)

/** One validated original staged from the complete camera cache for upload. */
internal class FrameioDeliveryArtifact(
    val share: StagedMediaShare,
    val byteCount: Long,
    val context: FrameioArtifactContext =
        FrameioArtifactContext(
            cameraID = "",
            captureDate = "",
            supportsLutBake = true,
        ),
) {
    override fun toString(): String = "FrameioDeliveryArtifact(redacted)"
}

/** Upload-ready output plus the exact look that was actually baked, if any. */
internal class FrameioPreparedArtifact(
    val share: StagedMediaShare,
    val byteCount: Long,
    val appliedLutName: String?,
    val transientExport: Path?,
) {
    override fun toString(): String = "FrameioPreparedArtifact(redacted)"
}

/** Typed preparation boundary between approved cache copies and cloud upload. */
internal interface FrameioArtifactPreparer {
    suspend fun prepare(
        artifact: FrameioDeliveryArtifact,
        options: MediaDeliveryConfiguration,
        onProgress: suspend (Double) -> Unit,
    ): FrameioPreparedArtifact

    suspend fun release(prepared: FrameioPreparedArtifact)
}

/** Default test/minimal path: upload the already-approved staged original unchanged. */
internal object PassthroughFrameioArtifactPreparer : FrameioArtifactPreparer {
    override suspend fun prepare(
        artifact: FrameioDeliveryArtifact,
        options: MediaDeliveryConfiguration,
        onProgress: suspend (Double) -> Unit,
    ): FrameioPreparedArtifact {
        if (options.bakeLut) {
            throw FrameioDeliveryException("LUT-baked Frame.io export is unavailable in this install.")
        }
        return FrameioPreparedArtifact(
            share = artifact.share,
            byteCount = artifact.byteCount,
            appliedLutName = null,
            transientExport = null,
        )
    }

    override suspend fun release(prepared: FrameioPreparedArtifact) = Unit
}

/** Resolves only a built-in core cube or revalidated app-private stored selection. */
internal interface FrameioLutProvider {
    suspend fun approvedPayload(selection: FeedLutSelection): MonitorMovieExportLut?
}

/** Production LUT resolver. Kotlin never parses `.cube` source or invents colour math. */
internal class AndroidFrameioLutProvider(
    private val library: AndroidLutLibrary,
) : FrameioLutProvider {
    override suspend fun approvedPayload(selection: FeedLutSelection): MonitorMovieExportLut? =
        when (selection) {
            is FeedLutSelection.BuiltIn -> {
                if (!SwiftCore.isAvailable) return null
                val packed = SwiftCore.bakeLut(selection.value.wireOrdinal, BUILT_IN_CUBE_SIZE)
                    ?: return null
                MonitorMovieExportLut(selection.value.label, BUILT_IN_CUBE_SIZE, packed)
            }
            is FeedLutSelection.Stored -> {
                if (!library.prepare(selection.value)) return null
                val packed = library.packedCube(selection.value) ?: return null
                val name = library.displayName(selection.value) ?: return null
                MonitorMovieExportLut(name, packed.cubeSize, packed.rgba)
            }
        }

    private companion object {
        const val BUILT_IN_CUBE_SIZE = 33
    }
}

/** Export seam kept separate so host tests do not need a codec or Android looper. */
internal interface FrameioLutVideoExporter {
    suspend fun export(
        source: Path,
        target: Path,
        lut: MonitorMovieExportLut,
        onProgress: suspend (Double) -> Unit,
    )
}

/** Media3 construction is host-owned; export completion and polling belong to the runtime. */
internal class AndroidMedia3FrameioLutVideoExporter(
    context: Context,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : FrameioLutVideoExporter {
    private val context = context.applicationContext
    private val owner = MonitorNativeMovieExporter(
        factory = MonitorMovieCodecFactory { source, target, lut, completion ->
            // Preserve the monitor cube's electrical/display-encoded SDR working space.
            val videoFrameProcessorFactory = DefaultVideoFrameProcessor.Factory.Builder()
                .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL)
                .build()
            val transformer = Transformer.Builder(this.context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        completion.completed()
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        completion.failed()
                    }
                }).build()
            val effect = SingleColorLut.createFromCube(lut.toArgbCube())
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source.toFile())))
                .setEffects(Effects(emptyList(), listOf(effect)))
                .build()
            object : MonitorMovieCodec {
                private val progress = ProgressHolder()
                override fun start() { transformer.start(item, target.toString()) }
                override fun progressPercent(): Int? =
                    if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) progress.progress else null
                override fun cancel() { transformer.cancel() }
            }
        },
        errors = frameioMovieExportErrors(this.context),
        mainDispatcher = mainDispatcher,
    )

    override suspend fun export(source: Path, target: Path, lut: MonitorMovieExportLut, onProgress: suspend (Double) -> Unit) =
        owner.export(source, target, lut, onProgress)
}

/** Composition supplies the approved LUT, branded filename and existing operator errors. */
internal class AndroidFrameioArtifactPreparer(
    exportRoot: Path,
    private val     lutProvider: FrameioLutProvider,
    exporter: FrameioLutVideoExporter,
    deleteExport: (Path) -> Boolean = Files::deleteIfExists,
    context: Context? = null,
) : FrameioArtifactPreparer {
    private val owner = MonitorMovieExportPreparer(
        exportRoot = exportRoot,
        exporter = object : MonitorLutMovieExporter {
            override suspend fun export(source: Path, target: Path, lut: MonitorMovieExportLut, onProgress: suspend (Double) -> Unit) =
                exporter.export(source, target, lut, onProgress)
        },
        errors = frameioMovieExportErrors(context),
        ownedFilenamePrefix = "frameio",
        fallbackFilename = "OpenZCine",
        deleteExport = deleteExport,
    )

    override suspend fun prepare(
        artifact: FrameioDeliveryArtifact,
        options: MediaDeliveryConfiguration,
        onProgress: suspend (Double) -> Unit,
    ): FrameioPreparedArtifact {
        val prepared = owner.prepare(
            input = MonitorMovieExportInput(artifact.share.file, artifact.share.displayName, artifact.share.mimeType, artifact.byteCount, artifact.context.supportsLutBake),
            bakeLut = options.bakeLut,
            container = options.exportContainer,
            approvedLut = { lutProvider.approvedPayload(requireNotNull(options.selectedLut)) },
            onProgress = onProgress,
        )
        val share = if (prepared.transientExport == null) artifact.share else
            StagedMediaShare(prepared.file, prepared.displayName, prepared.mimeType)
        return FrameioPreparedArtifact(share, prepared.byteCount, prepared.appliedLutName, prepared.transientExport)
    }

    override suspend fun release(prepared: FrameioPreparedArtifact) = owner.release(
        MonitorPreparedMovieExport(prepared.share.file, prepared.share.displayName, prepared.share.mimeType, prepared.byteCount, prepared.appliedLutName, prepared.transientExport),
    )
}

private fun frameioMovieExportErrors(context: Context? = null) = MonitorMovieExportErrorFactory { reason, filename ->
    FrameioDeliveryException(when (reason) {
        MonitorMovieExportFailure.UNSUPPORTED_BAKE -> lstr(context, R.string.frameio_export_unsupported_bake, filename)
        MonitorMovieExportFailure.APPROVED_LUT_UNAVAILABLE -> lstr(context, R.string.frameio_export_lut_unavailable)
        MonitorMovieExportFailure.INCOMPLETE_EXPORT -> lstr(context, R.string.frameio_export_incomplete)
        MonitorMovieExportFailure.INVALID_MOV_FILE -> lstr(context, R.string.frameio_export_invalid_mov)
        MonitorMovieExportFailure.INCOMPLETE_MOV_HEADER -> lstr(context, R.string.frameio_export_incomplete_header)
        MonitorMovieExportFailure.UNSUPPORTED_MOV_HEADER -> lstr(context, R.string.frameio_export_unsupported_header)
        MonitorMovieExportFailure.CODEC_FAILED -> lstr(context, R.string.frameio_export_codec_failed)
        MonitorMovieExportFailure.CODEC_STALLED -> lstr(context, R.string.frameio_export_codec_stalled)
        MonitorMovieExportFailure.INCOMPLETE_CLEANUP -> lstr(context, R.string.frameio_export_cleanup_incomplete)
        MonitorMovieExportFailure.TEMPORARY_CLEANUP -> lstr(context, R.string.frameio_export_cleanup_temporary)
    })
}
