package com.opencapture.openzcine.pairing

import com.opencapture.openzcine.R
import com.opencapture.opencine.nikon.CameraConnectionFailure
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The copy for a failed connect comes from the typed failure, never from its message (A4-45). */
class ConnectionFailureMessagesTest {
    /** The fake copy answers with the resource id, as the app's other pairing tests do. */
    private val copy = PortCopy { id, args -> "$id ${args.joinToString(" ")}".trim() }

    @Test
    fun `a refused initiator maps to guidance about Connect to PC, whatever the message says`() {
        val message = friendlyCameraConnectionFailure(CameraConnectionFailure.REJECTED_INITIATOR, "Init_Fail 1", copy)
        assertEquals("${R.string.conn_fail_rejected_initiator}", message)
    }

    /**
     * An unspecified Init_Fail is not a forgotten phone (iOS `isRejectedInitiator` reads only
     * rejectedInitiator); the old text classifier matched its "rejected the PTP-IP handshake".
     */
    @Test
    fun `an unreachable camera gets reachability guidance and no packet text`() {
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(
                CameraConnectionFailure.UNREACHABLE,
                "The camera rejected the PTP-IP handshake: unspecified.",
                copy,
            ),
        )
        assertFalse(CameraConnectionFailure.UNREACHABLE.savedProfileUnavailable)
    }

    @Test
    fun `empty failure falls back to reachability guidance`() {
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(null, null, copy),
        )
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.OTHER, " ", copy),
        )
    }

    /**
     * The transport's non-numeric-host refusal is an implementation detail, never operator
     * copy: it reaches this mapper only if some path hands the dialer a non-dialable key
     * (#327), and the operator's remedy is the rejoin/rediscover flow, not address formatting.
     */
    @Test
    fun `numeric IPv4 transport refusal maps to rejoin guidance`() {
        assertEquals(
            "${R.string.conn_fail_non_numeric_host}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.NON_NUMERIC_HOST, "anything", copy),
        )
    }

    @Test
    fun `typed failures with their own copy`() {
        assertEquals(
            "${R.string.conn_fail_timeout}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.TIMEOUT, "Init timed out.", copy),
        )
        assertEquals(
            "${R.string.conn_fail_connection_closed}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.CONNECTION_CLOSED, null, copy),
        )
        assertEquals(
            "${R.string.conn_fail_pairing_challenge_unavailable}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.PAIRING_CHALLENGE_UNAVAILABLE, null, copy),
        )
        assertEquals(
            "${R.string.conn_fail_saved_profile_required}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.SAVED_PROFILE_REQUIRED, null, copy),
        )
    }

    @Test
    fun `plain operator text is preserved`() {
        assertEquals("USB cable unplugged.", friendlyCameraConnectionFailure(null, "USB cable unplugged.", copy))
        val busy = "Another device is connected to this camera. Disconnect it there, then try again."
        assertEquals(busy, friendlyCameraConnectionFailure(CameraConnectionFailure.BUSY, busy, copy))
    }

    /**
     * A raw protocol or transport error reaches this mapper as an untyped failure; the operator
     * gets the reachability copy, never the dump (ZAB-147, the old "ptp-ip"/"0x" sanitizer).
     */
    @Test
    fun `a technical failure detail falls back to reachability guidance`() {
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(CameraConnectionFailure.OTHER, "PTP-IP response 0x2019", copy),
        )
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(null, "USB bulk transfer failed 0x80070005", copy),
        )
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(
                CameraConnectionFailure.OTHER,
                "java.lang.IllegalStateException: unexpected packet",
                copy,
            ),
        )
        assertEquals(
            "${R.string.conn_fail_unreachable}",
            friendlyCameraConnectionFailure(
                CameraConnectionFailure.OTHER,
                "read failed: errno 54 (Connection reset by peer)",
                copy,
            ),
        )
    }

    /** No pairing source classifies failure words again (the producer types them). */
    @Test
    fun `no connect failure is classified by its text`() {
        val pairing = Path.of(requireNotNull(System.getProperty("openzcine.repositoryRoot")))
            .resolve("Apps/Android/app/src/main/kotlin/com/opencapture/openzcine/pairing")
        for (name in listOf("ConnectionFailureMessages.kt", "SavedCameraConnector.kt", "PairingExperience.kt")) {
            val source = pairing.resolve(name).readText()
            for (token in listOf("rejectedinitiator", "closed the connection", "savedprofilerequired", "numeric ipv4")) {
                assertFalse("$name classifies \"$token\"", source.lowercase().contains("contains(\"$token"))
            }
        }
    }
}
