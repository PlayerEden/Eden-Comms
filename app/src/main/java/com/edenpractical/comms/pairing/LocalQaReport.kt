package com.edenpractical.comms.pairing

import com.edenpractical.comms.transport.LanDemoStatus
import com.edenpractical.comms.transport.LanProbeDiagnostics

/**
 * A deliberately non-sensitive, human-shareable observation of ONE PHONE'S
 * debug-only fixed-phrase and typed-text test. No QR tokens, peer certificates, Wi-Fi addresses,
 * exception details, phrase content, or human/device identifiers are included.
 *
 * This is not a success certificate or a peer delivery/read receipt.
 */
internal object LocalQaReport {
    enum class Role(val label: String) {
        PRESENTER("Showed first QR"), JOINER("Scanned first QR"), NOT_SELECTED("Not selected")
    }

    enum class Finish(val label: String) {
        USER_ENDED("User ended test"),
        REMOTE_OR_LINK_STOPPED("Remote phone or local connection ended"),
        CONNECTION_FAILED("Connection could not be established"),
        APP_BACKGROUNDED("App went into background"),
        NOT_RECORDED("No end reason recorded")
    }

    data class Observation(
        val versionName: String,
        val androidApiLevel: Int,
        val role: Role,
        val reachedReady: Boolean,
        val writtenToTls: Int,
        val receivedHere: Int,
        val finish: Finish,
        val lastCheckpoint: LanDemoStatus.Progress? = null,
        val endCode: LanDemoStatus.EndCode? = null,
        val failureCode: LanProbeDiagnostics.Code? = null,
        val tlsAndQrReady: Boolean = false,
        val localApprovalWritten: Boolean = false,
        val peerApprovalObserved: Boolean = false,
        val localConfirmationWritten: Boolean = false,
        val peerConfirmationObserved: Boolean = false,
        val installedVersionCode: Long? = null,
        val sourceRevision: String = "unavailable",
        val apkSignerSha256: String? = null,
        val testTextWrittenToTls: Int = 0,
        val testTextReceivedHere: Int = 0,
        val testTextPeerAckEvents: Int = 0,
        val voiceSpeakerStarts: Int = 0,
        val voicePlaybackProblemTurns: Int = 0
    )

    /** No free-form status or exception strings are accepted as report fields. */
    fun format(observation: Observation): String {
        require(observation.writtenToTls in 0..10_000 &&
            observation.receivedHere in 0..10_000) { "Invalid debug-only counters" }
        require(observation.androidApiLevel in 1..100) { "Invalid Android API level" }
        require(observation.testTextWrittenToTls in 0..10_000 &&
            observation.testTextReceivedHere in 0..10_000 &&
            observation.testTextPeerAckEvents in 0..10_000
        ) { "Invalid debug text counters" }
        require(observation.voiceSpeakerStarts in 0..10_000 &&
            observation.voicePlaybackProblemTurns in 0..10_000
        ) { "Invalid bounded local voice observations" }
        return listOf(
            "EDEN COMMS — LOCAL TEST OBSERVATION",
            "One phone only; NOT a security certification or a completed two-phone test.",
            "App version: ${BuildEvidenceReport.version(observation.versionName)}",
            "App versionCode: ${BuildEvidenceReport.code(observation.installedVersionCode)}",
            "Source revision: ${BuildEvidenceReport.revision(observation.sourceRevision)}",
            "APK signing certificate SHA-256: ${BuildEvidenceReport.signer(observation.apkSignerSha256)}",
            "This public APK signer is NOT the TLS peer identity or QR certificate.",
            "Android API: ${observation.androidApiLevel}",
            "QR role: ${observation.role.label}",
            "Reached debug READY: ${if (observation.reachedReady) "YES" else "NO"}",
            "Pinned TLS/QR/channel-binding test link ready: ${if (observation.tlsAndQrReady) "YES" else "NO"}",
            "Local debug approval written to TLS: " +
                if (observation.localApprovalWritten) "YES" else "NO",
            "Peer debug approval observed here: " +
                if (observation.peerApprovalObserved) "YES" else "NO",
            "Local final session confirmation written to TLS: " +
                if (observation.localConfirmationWritten) "YES" else "NO",
            "Peer final session confirmation observed here: " +
                if (observation.peerConfirmationObserved) "YES" else "NO",
            "These observations are NOT independently reviewed human authorization.",
            "Preset phrases written to local TLS: ${observation.writtenToTls}",
            "Preset phrases received by this app: ${observation.receivedHere}",
            "Debug typed text written to local TLS: ${observation.testTextWrittenToTls}",
            "Debug typed text received by this app: ${observation.testTextReceivedHere}",
            "Peer-app ACK events for debug text observed here: ${observation.testTextPeerAckEvents}",
            "ACK means the other app responded; NOT message display, human reading or guaranteed delivery.",
            "ACK counts can be incomplete if the link ended before events reached this screen.",
            "Local live speaker-start callbacks observed here: ${observation.voiceSpeakerStarts}",
            "Live voice turns with local speaker failure or dropped PCM: ${observation.voicePlaybackProblemTurns}",
            "These are local Android app observations, NOT proof of audible sound or delivery.",
            "Voice counts can be incomplete after interruption or if the UI stopped observing.",
            "No audio, PCM, speech content, peer identities or delivery receipts copied.",
            "No debug typed text content, peer IDs or delivery/read receipts copied.",
            "Finish: ${observation.finish.label}",
            "Last connection operation STARTED: " +
                (observation.lastCheckpoint?.name ?: "NOT_RECORDED"),
            "Connection end category: " +
                (observation.endCode?.name ?: "NOT_RECORDED"),
            "Handshake failure category: " +
                (observation.failureCode?.name ?: "NOT_RECORDED"),
            "An operation STARTED is not proof it finished successfully.",
            "TLS writes are NOT delivery or read receipts.",
            "Physical peer verification and no-WAN behavior require separate QA.",
            "No QR, IP, private keys, peer identities, or phrase contents are included."
        ).joinToString("\n")
    }
}
