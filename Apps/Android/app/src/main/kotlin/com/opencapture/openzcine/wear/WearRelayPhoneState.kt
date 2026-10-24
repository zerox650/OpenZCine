package com.opencapture.openzcine.wear

import android.content.Context
import com.opencapture.opencine.core.PowerStatus
import com.opencapture.opencine.nikon.CameraPropertySnapshot
import com.opencapture.opencine.nikon.CameraRecordingException
import com.opencapture.opencine.nikon.CameraSessionState
import com.opencapture.opencine.nikon.CameraStorageStatus
import com.opencapture.openzcine.R
import com.opencapture.openzcine.lstr
import com.opencapture.openzcine.wearrelay.WatchCommandResult
import com.opencapture.openzcine.wearrelay.WatchConnectionState
import com.opencapture.openzcine.wearrelay.WatchRecordState
import com.opencapture.openzcine.wearrelay.WatchRelayState
import com.opencapture.openzcine.wearrelay.WatchTimecode

/**
 * Current safety conditions for a record request arriving from the wearable.
 *
 * The watch intentionally bypasses the phone's visible confirmation prompt,
 * but never these live-monitor, lifecycle, camera-session, or pending-command
 * gates. This is a pure value so its rejection policy has JVM coverage.
 */
internal data class WearRecordCommandSafety(
    val monitorFront: Boolean,
    val applicationResumed: Boolean,
    val cameraConnected: Boolean,
    val recordCommandPending: Boolean,
    val recordConfirmationPending: Boolean,
    val cameraControlPending: Boolean,
)

/** Returns the operator-safe reason a wearable record request cannot run, if any. */
internal fun wearRecordCommandRejection(safety: WearRecordCommandSafety, context: Context? = null): String? =
    when {
        !safety.applicationResumed || !safety.monitorFront ->
            lstr(context, R.string.wear_record_reject_monitor)
        !safety.cameraConnected -> lstr(context, R.string.wear_record_reject_camera)
        safety.recordCommandPending -> lstr(context, R.string.wear_record_reject_pending)
        safety.recordConfirmationPending -> lstr(context, R.string.wear_record_reject_confirmation)
        safety.cameraControlPending -> lstr(context, R.string.wear_record_reject_control)
        else -> null
    }

/** Builds a rejected reply without changing a camera/session value. */
internal fun rejectedWearRecordResult(
    safety: WearRecordCommandSafety,
    isRecording: Boolean,
    context: Context? = null,
): WatchCommandResult =
    WatchCommandResult(
        accepted = false,
        isRecording = isRecording,
        error = wearRecordCommandRejection(safety, context) ?: "unavailable",
    )

/**
 * Routes the watch toggle through the same typed camera recording seam as the
 * phone, after the foreground/session/pending-work policy accepts it. Live
 * preview is deliberately not required: watchOS can record while its feed is
 * paused or the command dashboard is selected.
 */
internal suspend fun executeWearRecordCommand(
    safety: WearRecordCommandSafety,
    isRecording: Boolean,
    context: Context? = null,
    setRecording: suspend (Boolean) -> Unit,
): WatchCommandResult {
    if (wearRecordCommandRejection(safety, context) != null) {
        return rejectedWearRecordResult(safety, isRecording, context)
    }
    val target = !isRecording
    return try {
        setRecording(target)
        WatchCommandResult(accepted = true, isRecording = target, error = null)
    } catch (error: CameraRecordingException) {
        WatchCommandResult(
            accepted = false,
            isRecording = isRecording,
            error = error.message ?: "unavailable",
        )
    }
}

/** Retains the latest real frame readouts when the connected phone pauses preview. */
internal fun retainWatchFrameMetadata(
    state: WatchRelayState,
    previous: WatchRelayState?,
): WatchRelayState {
    if (state.connection != WatchConnectionState.CONNECTED || previous == null) return state
    val previousTimecode = previous.timecode
    val hasPreviousTimecode =
        previousTimecode.on ||
            previousTimecode.hour != 0 ||
            previousTimecode.minute != 0 ||
            previousTimecode.second != 0 ||
            previousTimecode.frame != 0
    return state.copy(
        timecode =
            if (!state.timecode.on && hasPreviousTimecode) previousTimecode else state.timecode,
        liveFPS =
            if (state.liveFPS == "—" && previous.liveFPS != "—") {
                previous.liveFPS
            } else {
                state.liveFPS
            },
    )
}

/**
 * Creates the honest Android phone snapshot consumed by the Wear OS relay.
 *
 * Timecode/FPS begin unavailable and are replaced only by metadata from an
 * actually presented native camera frame inside [AndroidWearPhoneRelay].
 * Storage enters only from the real Swift-core property snapshot, and the battery only from the
 * common session's typed [power].
 */
internal fun androidWatchRelayState(
    sessionState: CameraSessionState,
    cameraProperties: CameraPropertySnapshot,
    isRecording: Boolean,
    monitorFront: Boolean,
    applicationResumed: Boolean,
    liveFeedActive: Boolean,
    power: PowerStatus,
): WatchRelayState {
    val connectedIdentity = (sessionState as? CameraSessionState.Connected)?.identity
    val foreground = monitorFront && applicationResumed
    val connection =
        when {
            !foreground -> WatchConnectionState.DISCONNECTED
            connectedIdentity != null -> WatchConnectionState.CONNECTED
            else -> WatchConnectionState.NO_CAMERA
        }
    val hasCamera = connection == WatchConnectionState.CONNECTED
    val reportedPower = if (hasCamera) power else PowerStatus.unknown
    return WatchRelayState(
        recordState = if (isRecording) WatchRecordState.RECORDING else WatchRecordState.STANDBY,
        timecode = WatchTimecode.unavailable(),
        mediaStatus = null,
        media = if (hasCamera) cameraProperties.storage?.let(::storageLabel) ?: "—" else "—",
        // Retain the deployed raw scalar for old receivers. Active wrist presentation uses
        // the declared common reading below, including explicit unknown and exhaustion.
        cameraBatteryPercent =
            if (hasCamera) cameraProperties.batteryPercent?.takeIf { it in 0..100 } ?: 0 else 0,
        cameraName = if (hasCamera) connectedIdentity?.name.orEmpty() else "",
        isRecording = isRecording,
        connection = connection,
        feedLive = hasCamera && liveFeedActive,
        liveFPS = "—",
        batteryReading = reportedPower.batteryReading, batteryExhausted = reportedPower.exhausted.value,
    )
}

private fun storageLabel(storage: CameraStorageStatus): String = storage.readout?.label ?: "\u2014"
