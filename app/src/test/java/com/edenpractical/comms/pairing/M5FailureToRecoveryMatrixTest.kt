package com.edenpractical.comms.pairing

import com.edenpractical.comms.transport.LanProbeDiagnostics
import com.edenpractical.comms.transport.LanWifiChangedException
import com.edenpractical.comms.security.DebugExporterUnavailableException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uses synthetic JVM errors to connect the real classifier to human recovery
 * advice. Never an actual device, verified TLS peer, or end-to-end link.
 */
class M5FailureToRecoveryMatrixTest {
    private data class Scenario(
        val step: LanProbeDiagnostics.Step,
        val error: Throwable,
        val expectedCode: LanProbeDiagnostics.Code,
        val expectedCategory: NearbyRecoveryGuide.Category
    )

    @Test fun classifiedFailuresMapToPrivacySafeNextStepsWithoutAutomaticTrust() {
        val badCertificate = SSLHandshakeException("PRIVATE-TEST-MARKER")
        badCertificate.initCause(CertificateException("PRIVATE-TEST-MARKER"))
        val scenarios = listOf(
            Scenario(LanProbeDiagnostics.Step.WIFI, LanWifiChangedException(),
                LanProbeDiagnostics.Code.WIFI_CHANGED, NearbyRecoveryGuide.Category.NETWORK),
            Scenario(LanProbeDiagnostics.Step.TCP_CONNECT,
                ConnectException("PRIVATE-TEST-MARKER"),
                LanProbeDiagnostics.Code.HOST_NOT_ACCEPTING, NearbyRecoveryGuide.Category.NETWORK),
            Scenario(LanProbeDiagnostics.Step.WAIT_FOR_PEER,
                SocketTimeoutException("PRIVATE-TEST-MARKER"),
                LanProbeDiagnostics.Code.HOST_WAIT_TIMEOUT, NearbyRecoveryGuide.Category.TIMEOUT),
            Scenario(LanProbeDiagnostics.Step.TLS_HANDSHAKE, badCertificate,
                LanProbeDiagnostics.Code.CERTIFICATE_REJECTED, NearbyRecoveryGuide.Category.IDENTITY),
            Scenario(LanProbeDiagnostics.Step.CHANNEL_BINDING,
                DebugExporterUnavailableException(),
                LanProbeDiagnostics.Code.EXPORTER_UNAVAILABLE,
                NearbyRecoveryGuide.Category.COMPATIBILITY),
            Scenario(LanProbeDiagnostics.Step.READY_EXCHANGE,
                SocketTimeoutException("PRIVATE-TEST-MARKER"),
                LanProbeDiagnostics.Code.READY_TIMEOUT, NearbyRecoveryGuide.Category.TIMEOUT)
        )

        for (scenario in scenarios) {
            val classified = LanProbeDiagnostics.classify(scenario.step, scenario.error)
            assertEquals(scenario.step.name, scenario.expectedCode, classified.code)
            val advice = NearbyRecoveryGuide.forResult(
                LocalQaReport.Finish.CONNECTION_FAILED,
                classified.code, null, true
            )
            assertEquals(scenario.expectedCode.name, scenario.expectedCategory, advice.category)
            assertTrue(scenario.expectedCode.name, advice.nextStep.isNotBlank())
            val userVisible = classified.display() + advice.title + advice.detail + advice.nextStep
            assertFalse(userVisible.contains("PRIVATE-TEST-MARKER"))
            assertFalse(userVisible.contains("automatic reconnect", ignoreCase = true))
            assertFalse(userVisible.contains("skip verification", ignoreCase = true))
            assertFalse(userVisible.contains("secure messaging is enabled", ignoreCase = true))
        }
    }

    @Test fun localCancelAndBackgroundExitAreNotMisrepresentedAsIdentityFailure() {
        val cases = listOf(
            LocalQaReport.Finish.USER_ENDED to NearbyRecoveryGuide.Category.COMPLETED,
            LocalQaReport.Finish.APP_BACKGROUNDED to NearbyRecoveryGuide.Category.INTERRUPTED
        )
        for ((finish, category) in cases) {
            val advice = NearbyRecoveryGuide.forResult(
                finish, LanProbeDiagnostics.Code.CERTIFICATE_REJECTED, null, false
            )
            assertEquals(category, advice.category)
            assertTrue(advice.nextStep.contains("fresh QR"))
        }
    }
}
