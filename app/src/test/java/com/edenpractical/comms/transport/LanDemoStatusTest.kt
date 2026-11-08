package com.edenpractical.comms.transport

import com.edenpractical.comms.protocol.FrameFormatException
import com.edenpractical.comms.protocol.TestPacketException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanDemoStatusTest {
    @Test fun everyProgressLabelNamesAnInProgressOperationNotApproval() {
        val steps = LanDemoStatus.Progress.values()
        assertEquals(5, steps.size)
        assertTrue(steps.all { it.display.isNotBlank() && it.display.endsWith("…") })
        for (label in steps.map { it.display }) {
            assertFalse(label.contains("Connected —"))
            assertFalse(label.contains("approved", ignoreCase = true))
            assertFalse(label.contains("secure messenger", ignoreCase = true))
        }
    }

    @Test fun sessionLimitExplanationMatchesActualWatchdogDuration() {
        assertEquals(30L * 60_000L, DemoSessionLivenessPolicy.MAX_SESSION_MS)
        assertTrue(LanDemoStatus.EndCode.SESSION_LIMIT.display.contains("30-minute"))
        assertFalse(LanDemoStatus.EndCode.SESSION_LIMIT.display.contains("ten-minute"))
    }

    @Test fun allEndCategoriesUseFixedNonSensitiveDescriptions() {
        for (reason in LanDemoStatus.EndCode.values()) {
            val message = reason.display
            assertTrue(message.isNotBlank())
            assertFalse(message.contains("192."))
            assertFalse(message.contains("QR:"))
            assertFalse(message.contains("PRIVATE KEY"))
        }
    }

    @Test fun protocolCorruptionIsDifferentFromOrdinarySocketDrop() {
        assertEquals(LanDemoStatus.EndCode.PROTOCOL_REJECTED,
            LanDemoStatus.activeFailure(TestPacketException()))
        assertEquals(LanDemoStatus.EndCode.PROTOCOL_REJECTED,
            LanDemoStatus.activeFailure(FrameFormatException("SECRET-FRAME-RAW")))
        assertEquals(LanDemoStatus.EndCode.LINK_INTERRUPTED,
            LanDemoStatus.activeFailure(IOException("SECRET-REMOTE-IP")))
    }

    @Test fun wrappedProtocolErrorsStillSanitizeTheCause() {
        val wrapper = IOException("SECRET-CERTIFICATE")
        wrapper.initCause(TestPacketException())
        val reason = LanDemoStatus.activeFailure(wrapper)
        assertEquals(LanDemoStatus.EndCode.PROTOCOL_REJECTED, reason)
        assertFalse(reason.display.contains("SECRET"))
    }

    @Test fun livenessOutcomesHaveDifferentEndCategories() {
        assertEquals(LanDemoStatus.EndCode.PEER_SILENT,
            LanDemoStatus.livenessEnd(DemoSessionLivenessPolicy.EndReason.PEER_SILENT))
        assertEquals(LanDemoStatus.EndCode.SESSION_LIMIT,
            LanDemoStatus.livenessEnd(DemoSessionLivenessPolicy.EndReason.SESSION_LIMIT))
        assertEquals(LanDemoStatus.EndCode.CLOCK_INVALID,
            LanDemoStatus.livenessEnd(DemoSessionLivenessPolicy.EndReason.INVALID_TIMING))
    }
}
