package com.edenpractical.comms.pairing

import com.edenpractical.comms.transport.LanDemoStatus
import com.edenpractical.comms.transport.LanProbeDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyRecoveryGuideTest {
    private val guide = NearbyRecoveryGuide

    private fun failure(code: LanProbeDiagnostics.Code) = guide.forResult(
        LocalQaReport.Finish.CONNECTION_FAILED, code, null, true
    )

    private fun end(code: LanDemoStatus.EndCode) = guide.forResult(
        LocalQaReport.Finish.REMOTE_OR_LINK_STOPPED, null, code, true
    )

    @Test fun allCategorizedFailuresHaveSpecificAndPrivacySafeNextStep() {
        LanProbeDiagnostics.Code.entries.forEach { code ->
            val advice = failure(code)
            assertTrue(code.name, advice.title.isNotBlank())
            assertTrue(code.name, advice.detail.isNotBlank())
            assertTrue(code.name, advice.nextStep.isNotBlank())
            val rendered = advice.title + advice.detail + advice.nextStep
            assertFalse(rendered.contains("192.168."))
            assertFalse(rendered.contains("SSID"))
            assertFalse(rendered.contains("certificate fingerprint"))
            assertFalse(rendered.contains("skip verification", ignoreCase = true))
            assertFalse(rendered.contains("automatic reconnect", ignoreCase = true))
        }
        assertEquals(NearbyRecoveryGuide.Category.NETWORK,
            failure(LanProbeDiagnostics.Code.WIFI_CHANGED).category)
        assertEquals(NearbyRecoveryGuide.Category.IDENTITY,
            failure(LanProbeDiagnostics.Code.CERTIFICATE_REJECTED).category)
        assertEquals(NearbyRecoveryGuide.Category.COMPATIBILITY,
            failure(LanProbeDiagnostics.Code.EXPORTER_UNAVAILABLE).category)
        assertEquals(NearbyRecoveryGuide.Category.TIMEOUT,
            failure(LanProbeDiagnostics.Code.HOST_WAIT_TIMEOUT).category)
    }

    @Test fun everyTerminalSessionReasonHasClearHumanReadableFollowup() {
        LanDemoStatus.EndCode.entries.forEach { code ->
            val advice = end(code)
            assertTrue(code.name, advice.title.isNotBlank())
            assertTrue(code.name, advice.nextStep.isNotBlank())
            assertFalse(advice.nextStep.contains("automatically", ignoreCase = true))
            assertFalse(advice.nextStep.contains("restore", ignoreCase = true))
        }
        val rejected = end(LanDemoStatus.EndCode.PROTOCOL_REJECTED)
        assertTrue(rejected.nextStep.contains("Do not bypass"))
        assertTrue(rejected.nextStep.contains("reviewing"))
        val declined = end(LanDemoStatus.EndCode.PEER_DECLINED)
        assertEquals(NearbyRecoveryGuide.Category.COMPLETED, declined.category)
        assertTrue(declined.nextStep.contains("both people"))
        assertEquals(NearbyRecoveryGuide.Category.TIMEOUT,
            end(LanDemoStatus.EndCode.APPROVAL_TIMED_OUT).category)
    }

    @Test fun currentWifiIsNotClaimedToProvePeerConnectivityOrAuthorization() {
        assertTrue(guide.wifiStatus(true).contains("on this phone"))
        assertTrue(guide.wifiStatus(true).contains("still need the same Wi-Fi"))
        assertTrue(guide.wifiStatus(false).contains("No usable"))
        val missing = guide.forResult(
            LocalQaReport.Finish.CONNECTION_FAILED, null, null, false
        )
        assertEquals(NearbyRecoveryGuide.Category.NETWORK, missing.category)
        assertTrue(missing.wifiCheckHelpful)
        assertTrue(missing.nextStep.contains("check again"))
        val unknown = guide.forResult(
            LocalQaReport.Finish.CONNECTION_FAILED, null, null, true
        )
        assertEquals(NearbyRecoveryGuide.Category.UNKNOWN, unknown.category)
    }

    @Test fun locallyEndedOrBackgroundedSessionsCannotBeMisreportedAsHandshakeFailures() {
        val userEnded = guide.forResult(
            LocalQaReport.Finish.USER_ENDED,
            LanProbeDiagnostics.Code.CERTIFICATE_REJECTED,
            LanDemoStatus.EndCode.PROTOCOL_REJECTED,
            false
        )
        assertEquals(NearbyRecoveryGuide.Category.COMPLETED, userEnded.category)
        assertTrue(userEnded.title.contains("You ended"))
        val background = guide.forResult(
            LocalQaReport.Finish.APP_BACKGROUNDED, null, null, false
        )
        assertEquals(NearbyRecoveryGuide.Category.INTERRUPTED, background.category)
        assertTrue(background.detail.contains("background"))
        assertTrue(background.nextStep.contains("fresh QR"))
    }

    @Test fun verifiedSessionEndReasonWinsOverLaterChangedWifi() {
        val peerEnded = guide.forResult(
            LocalQaReport.Finish.REMOTE_OR_LINK_STOPPED, null,
            LanDemoStatus.EndCode.PEER_ENDED, false
        )
        assertEquals(NearbyRecoveryGuide.Category.COMPLETED, peerEnded.category)
        assertFalse(peerEnded.wifiCheckHelpful)
    }
}
