package com.edenpractical.comms.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQaReportTest {
    private fun sample(
        version: String = "0.2.12-approval-review-only",
        ready: Boolean = true,
        written: Int = 4,
        received: Int = 3
    ) = LocalQaReport.Observation(
        versionName = version,
        androidApiLevel = 36,
        role = LocalQaReport.Role.PRESENTER,
        reachedReady = ready,
        writtenToTls = written,
        receivedHere = received,
        finish = LocalQaReport.Finish.USER_ENDED
    )

    @Test fun onePhoneObservationCannotBeMistakenForTwoPhoneCertification() {
        val text = LocalQaReport.format(sample())
        assertTrue(text.contains("One phone only"))
        assertTrue(text.contains("NOT a security certification"))
        assertTrue(text.contains("TLS writes are NOT delivery or read receipts"))
        assertTrue(text.contains("Physical peer verification and no-WAN behavior require separate QA"))
        assertTrue(text.contains("Preset phrases received by this app: 3"))
        assertTrue(text.contains("App version: 0.2.12-approval-review-only"))
    }

    @Test fun untrustedVersionIsNeverCopiedAsArbitraryText() {
        val text = LocalQaReport.format(sample(version = "bad\nSECRET-QR-IP=192.168.1.1"))
        assertTrue(text.contains("App version: unavailable"))
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("192.168"))
    }

    @Test fun incompleteConnectionIsDescribedTruthfully() {
        val text = LocalQaReport.format(sample(ready = false, written = 0, received = 0))
        assertTrue(text.contains("Reached debug READY: NO"))
        assertTrue(text.contains("phrases received by this app: 0"))
    }

    @Test fun rejectsImpossibleCountersAndSdkLevels() {
        val default = sample()
        for (bad in listOf(-1, 10001)) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalQaReport.format(default.copy(writtenToTls = bad))
            }
            assertThrows(IllegalArgumentException::class.java) {
                LocalQaReport.format(default.copy(receivedHere = bad))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalQaReport.format(default.copy(androidApiLevel = 0))
        }
    }

    @Test fun fixedLanCheckpointsAndTerminalCategoriesAreOptionalAndSafe() {
        val fail = sample(ready = false, written = 0, received = 0).copy(
            lastCheckpoint = com.edenpractical.comms.transport.LanDemoStatus.Progress.BOTH_READY,
            failureCode = com.edenpractical.comms.transport.LanProbeDiagnostics.Code.READY_TIMEOUT
        )
        val failure = LocalQaReport.format(fail)
        assertTrue(failure.contains("Last connection operation STARTED: BOTH_READY"))
        assertTrue(failure.contains("Handshake failure category: READY_TIMEOUT"))
        assertTrue(failure.contains("not proof it finished successfully"))
        assertFalse(failure.contains("SECRET-QR"))
        val ended = sample().copy(
            lastCheckpoint = com.edenpractical.comms.transport.LanDemoStatus.Progress.QR_PROOF,
            endCode = com.edenpractical.comms.transport.LanDemoStatus.EndCode.WIFI_CHANGED
        )
        val report = LocalQaReport.format(ended)
        assertTrue(report.contains("Connection end category: WIFI_CHANGED"))
        assertTrue(report.contains("Handshake failure category: NOT_RECORDED"))
    }

    @Test fun consentEvidenceIsSeparatedFromTlsReadiness() {
        val localOnly = sample(ready = false, written = 0, received = 0).copy(
            tlsAndQrReady = true, localApprovalWritten = true,
            peerApprovalObserved = false
        )
        val text = LocalQaReport.format(localOnly)
        assertTrue(text.contains("Pinned TLS/QR test link ready: YES"))
        assertTrue(text.contains("Local debug approval written to TLS: YES"))
        assertTrue(text.contains("Peer debug approval observed here: NO"))
        assertTrue(text.contains("Reached debug READY: NO"))
        assertTrue(text.contains("NOT independently reviewed human authorization"))
        assertFalse(text.contains("CERTIFICATE"))
    }

    @Test fun outcomeAndRoleAreFixedLabelsNotRawNetworkErrors() {
        for (role in LocalQaReport.Role.values()) {
            for (finish in LocalQaReport.Finish.values()) {
                val text = LocalQaReport.format(sample().copy(role = role, finish = finish))
                assertTrue(text.contains("QR role: ${role.label}"))
                assertTrue(text.contains("Finish: ${finish.label}"))
                assertFalse(text.contains("Exception:"))
            }
        }
    }
}
