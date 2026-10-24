package com.opencapture.openzcine.media

import android.content.Context
import com.opencapture.opencine.nikon.NikonStillPhotoClassification
import com.opencapture.opencine.nikon.NikonStillPreviewStrategy
import com.opencapture.openzcine.R
import com.opencapture.openzcine.lstr

/** The text-and-chrome state of one Android still-photo viewer request. */
internal sealed interface StillPreviewUiState {
    /** The camera thumbnail is being requested before any payload transfer. */
    data object Preparing : StillPreviewUiState

    /** A decodable image payload is arriving in the shared progressive cache. */
    data class Downloading(
        val progress: Double,
        val message: String,
    ) : StillPreviewUiState

    /** A full still image decoded successfully from the cache. */
    data object FullPreview : StillPreviewUiState

    /** The camera thumbnail remains visible because no full preview is available. */
    data class ThumbnailFallback(val message: String) : StillPreviewUiState

    /** The camera transfer failed before a full preview could be decoded. */
    data class Failed(val message: String) : StillPreviewUiState

    /** The viewer has invalidated its request and must not accept more updates. */
    data object Closed : StillPreviewUiState
}

/** Pure presentation policy for full-preview progress and honest decoder fallbacks. */
internal object StillPreviewStates {
    fun initial(): StillPreviewUiState = StillPreviewUiState.Preparing

    fun downloading(
        classification: NikonStillPhotoClassification,
        progress: Double,
        context: Context? = null,
    ): StillPreviewUiState.Downloading {
        val message =
            when (classification.previewStrategy) {
                NikonStillPreviewStrategy.PROGRESSIVE ->
                    lstr(context, R.string.still_preview_loading_progressive, classification.formatLabel)
                NikonStillPreviewStrategy.COMPLETE_FILE ->
                    lstr(context, R.string.still_preview_downloading_complete, classification.formatLabel)
                // The RAW side of a pair transfers for sharing even though it
                // never claims a decoded full preview.
                NikonStillPreviewStrategy.THUMBNAIL_ONLY ->
                    lstr(context, R.string.still_preview_downloading_file, classification.formatLabel)
            }
        return StillPreviewUiState.Downloading(progress.coerceIn(0.0, 1.0), message)
    }

    fun decoderUnavailable(
        classification: NikonStillPhotoClassification,
        hasThumbnail: Boolean,
        context: Context? = null,
    ): StillPreviewUiState.ThumbnailFallback =
        StillPreviewUiState.ThumbnailFallback(
            buildString {
                append(
                    when (classification.previewStrategy) {
                        NikonStillPreviewStrategy.THUMBNAIL_ONLY ->
                            lstr(context, R.string.still_preview_decoder_unavailable_file, classification.formatLabel)
                        NikonStillPreviewStrategy.PROGRESSIVE,
                        NikonStillPreviewStrategy.COMPLETE_FILE,
                        ->
                            lstr(context, R.string.still_preview_decoder_unavailable_image, classification.formatLabel)
                    },
                )
                append(' ')
                append(
                    if (hasThumbnail) {
                        lstr(context, R.string.still_preview_thumbnail_shown)
                    } else {
                        lstr(context, R.string.still_preview_no_thumbnail)
                    },
                )
            },
        )

    fun transferFailed(
        message: String,
        hasThumbnail: Boolean,
        context: Context? = null,
    ): StillPreviewUiState.Failed =
        StillPreviewUiState.Failed(
            if (hasThumbnail) "$message ${lstr(context, R.string.still_preview_thumbnail_shown)}" else message,
        )
}

/**
 * Generation gate for asynchronous thumbnail, cache, and decode work.
 *
 * Compose cancellation stops the caller, while this tiny gate also rejects a
 * non-cooperative bitmap decode that returns after the viewer has closed or a
 * new request has replaced it.
 */
internal class StillViewerLoadGate {
    private var generation: Long = 0

    fun begin(): Long {
        generation += 1
        return generation
    }

    fun invalidate() {
        generation += 1
    }

    fun accepts(requestGeneration: Long): Boolean = generation == requestGeneration
}
