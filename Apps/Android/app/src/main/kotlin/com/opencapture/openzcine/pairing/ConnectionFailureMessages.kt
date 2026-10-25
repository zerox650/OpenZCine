package com.opencapture.openzcine.pairing

import com.opencapture.openzcine.R
import com.opencapture.opencine.nikon.CameraConnectionFailure

/**
 * Operator copy for a failed camera connect, the Android counterpart of iOS
 * `StartupConnectionCopy.friendly` plus the `rejectedInitiator` guidance in `NativeAppRoot`.
 *
 * It reads the typed [CameraConnectionFailure] the Swift session layer sends, never the failure
 * message's words (A4-45). Device logs show camera-AP Wi-Fi often succeeds (Android may silently
 * rejoin a previously approved SSID) while the PTP-IP handshake refuses this initiator, so that
 * refusal gets its own guidance instead of a generic "Couldn't reach".
 *
 * [detail] is shown as is only when the failure has no copy of its own (another device holds the
 * camera, or an untyped failure) and the text is operator-facing; a blank or technical detail
 * reads as unreachable.
 *
 * [copy] resolves the guidance in the operator's language; the JVM tests pass a fake that answers
 * with the resource id, keeping their assertions free of any language's wording.
 */
internal fun friendlyCameraConnectionFailure(
    failure: CameraConnectionFailure?,
    detail: String?,
    copy: PortCopy,
): String =
    when (failure) {
        CameraConnectionFailure.REJECTED_INITIATOR ->
            copy.text(R.string.conn_fail_rejected_initiator)
        CameraConnectionFailure.TIMEOUT -> copy.text(R.string.conn_fail_timeout)
        CameraConnectionFailure.CONNECTION_CLOSED -> copy.text(R.string.conn_fail_connection_closed)
        CameraConnectionFailure.PAIRING_CHALLENGE_UNAVAILABLE ->
            copy.text(R.string.conn_fail_pairing_challenge_unavailable)
        CameraConnectionFailure.SAVED_PROFILE_REQUIRED ->
            copy.text(R.string.conn_fail_saved_profile_required)
        // The transport refusing a non-numeric dial target means some path handed the dialer a
        // non-address key (#327); the operator's remedy is the rejoin/rediscover flow.
        CameraConnectionFailure.NON_NUMERIC_HOST ->
            copy.text(R.string.conn_fail_non_numeric_host)
        // Never surface socket errors or PTP-IP packet dumps to the operator.
        CameraConnectionFailure.UNREACHABLE -> copy.text(R.string.conn_fail_unreachable)
        // The wake dialled the network and the body never answered: asleep, or off this network.
        // The remedy is the body, not the transport, so it gets its own guidance.
        CameraConnectionFailure.CAMERA_SILENT -> copy.text(R.string.conn_fail_camera_silent)
        CameraConnectionFailure.BUSY, CameraConnectionFailure.OTHER, null ->
            detail?.trim()?.takeIf { it.isNotEmpty() && !it.looksTechnical() }
                ?: copy.text(R.string.conn_fail_unreachable)
    }

/**
 * An untyped failure's detail is shown only when it is operator text. A message naming a protocol
 * frame, a hex code or a transport exception is the dump the old text sanitizer replaced (ZAB-147).
 */
private fun String.looksTechnical(): Boolean {
    val lower = lowercase()
    return listOf("0x", "ptp", "errno", "exception").any { it in lower }
}
