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
        assertTrue(text.contains("Pinned TLS/QR/channel-binding test link ready: YES"))
        assertTrue(text.contains("Local debug approval written to TLS: YES"))
        assertTrue(text.contains("Peer debug approval observed here: NO"))
        assertTrue(text.contains("Reached debug READY: NO"))
        assertTrue(text.contains("NOT independently reviewed human authorization"))
        assertFalse(text.contains("CERTIFICATE"))
    }

    @Test fun finalConfirmationsAreSeparateFromApprovalsInOnePhoneReport() {
        val observed = sample(ready = false, written = 0, received = 0).copy(
            tlsAndQrReady = true,
            localApprovalWritten = true,
            peerApprovalObserved = true,
            localConfirmationWritten = true,
            peerConfirmationObserved = false
        )
        val text = LocalQaReport.format(observed)
        assertTrue(text.contains("Local final session confirmation written to TLS: YES"))
        assertTrue(text.contains("Peer final session confirmation observed here: NO"))
        assertTrue(text.contains("Reached debug READY: NO"))
        assertFalse(text.contains("SECRET"))
        val finished = LocalQaReport.format(observed.copy(
            peerConfirmationObserved = true, reachedReady = true
        ))
        assertTrue(finished.contains("Peer final session confirmation observed here: YES"))
        assertTrue(finished.contains("Reached debug READY: YES"))
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
    @Test fun localQaBuildMetadataIsPublicAndNotConfusedWithPeerIdentity() {
        val source = "a".repeat(40)
        val signer = "b".repeat(64)
        val report = LocalQaReport.format(sample().copy(
            installedVersionCode = 10017,
            sourceRevision = source,
            apkSignerSha256 = signer
        ))
        assertTrue(report.contains("App versionCode: 10017"))
        assertTrue(report.contains("Source revision: $source"))
        assertTrue(report.contains("APK signing certificate SHA-256: $signer"))
        assertTrue(report.contains("NOT the TLS peer identity or QR certificate"))
        assertFalse(report.contains("IP ADDRESS"))
        val sanitized = LocalQaReport.format(sample().copy(
            installedVersionCode = -1,
            sourceRevision = source + "\\npeer=secret",
            apkSignerSha256 = "bad"
        ))
        assertTrue(sanitized.contains("App versionCode: unavailable"))
        assertTrue(sanitized.contains("Source revision: unavailable"))
        assertTrue(sanitized.contains("APK signing certificate SHA-256: unavailable"))
        assertFalse(sanitized.contains("peer=secret"))
    }

    @Test fun peerAppAckIsOnlyAnObservedEventNotAReadReceipt() {
        val report = LocalQaReport.format(sample().copy(
            testTextWrittenToTls = 3,
            testTextReceivedHere = 2,
            testTextPeerAckEvents = 1
        ))
        assertTrue(report.contains("Peer-app ACK events for debug text observed here: 1"))
        assertTrue(report.contains("NOT message display, human reading or guaranteed delivery"))
        assertTrue(report.contains("can be incomplete"))
        assertFalse(report.contains("Hello from this phone"))
    }

    @Test fun peerAppAckCountCannotBecomeAnUnboundedOrSecretReport() {
        for (invalid in listOf(-1, 10_001)) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalQaReport.format(sample().copy(testTextPeerAckEvents = invalid))
            }
        }
        val maximum = LocalQaReport.format(sample().copy(testTextPeerAckEvents = 10_000))
        assertTrue(maximum.contains("Peer-app ACK events for debug text observed here: 10000"))
        val absent = LocalQaReport.format(sample())
        assertTrue(absent.contains("Peer-app ACK events for debug text observed here: 0"))
    }

    @Test fun localVoiceObservationsAreCountedNotMisrepresentedAsSoundOrDelivery() {
        val report = LocalQaReport.format(sample().copy(
            voiceSpeakerStarts = 2,
            voicePlaybackProblemTurns = 1
        ))
        assertTrue(report.contains("Local live speaker-start callbacks observed here: 2"))
        assertTrue(report.contains("Live voice turns with local speaker failure or dropped PCM: 1"))
        assertTrue(report.contains("NOT proof of audible sound or delivery"))
        assertTrue(report.contains("Voice counts can be incomplete"))
        assertTrue(report.contains("No audio, PCM, speech content, peer identities"))
        assertFalse(report.contains("AudioRecord bytes="))
        assertFalse(report.contains("Voice sound heard: YES"))
    }

    @Test fun localVoiceCountsRejectNegativeAndUnboundedValues() {
        for (invalid in listOf(-1, 10_001)) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalQaReport.format(sample().copy(voiceSpeakerStarts = invalid))
            }
            assertThrows(IllegalArgumentException::class.java) {
                LocalQaReport.format(sample().copy(voicePlaybackProblemTurns = invalid))
            }
        }
        val maximum = LocalQaReport.format(sample().copy(
            voiceSpeakerStarts = 10_000, voicePlaybackProblemTurns = 10_000
        ))
        assertTrue(maximum.contains("observed here: 10000"))
        val absent = LocalQaReport.format(sample())
        assertTrue(absent.contains("Local live speaker-start callbacks observed here: 0"))
        assertTrue(absent.contains("Live voice turns with local speaker failure or dropped PCM: 0"))
    }

    @Test fun developmentTextReportContainsCountsNeverTypedContent() {
        val report = LocalQaReport.format(sample().copy(
            testTextWrittenToTls = 2,
            testTextReceivedHere = 3
        ))
        assertTrue(report.contains("Debug typed text written to local TLS: 2"))
        assertTrue(report.contains("Debug typed text received by this app: 3"))
        assertTrue(report.contains("No debug typed text content"))
        assertFalse(report.contains("Hello from this phone"))
        assertThrows(IllegalArgumentException::class.java) {
            LocalQaReport.format(sample().copy(testTextWrittenToTls = -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalQaReport.format(sample().copy(testTextReceivedHere = 10001))
        }
    }

}
