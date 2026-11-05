package com.edenpractical.comms.pairing

import com.edenpractical.comms.transport.LanDemoStatus
import com.edenpractical.comms.transport.LanProbeDiagnostics

/**
 * A deliberately non-sensitive, human-shareable observation of ONE PHONE'S
 * debug-only fixed-phrase test. No QR tokens, certificates, Wi-Fi addresses,
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
        val peerApprovalObserved: Boolean = false
    )

    /** No free-form status or exception strings are accepted as report fields. */
    fun format(observation: Observation): String {
        require(observation.writtenToTls in 0..10_000 &&
            observation.receivedHere in 0..10_000) { "Invalid debug-only counters" }
        require(observation.androidApiLevel in 1..100) { "Invalid Android API level" }
        val safeVersion = observation.versionName.takeIf {
            it.length in 1..64 && it.all { ch ->
                ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' ||
                    ch == '.' || ch == '_' || ch == '-'
            }
        } ?: "unavailable"
        return listOf(
            "EDEN COMMS — LOCAL TEST OBSERVATION",
            "One phone only; NOT a security certification or a completed two-phone test.",
            "App version: $safeVersion",
            "Android API: ${observation.androidApiLevel}",
            "QR role: ${observation.role.label}",
            "Reached debug READY: ${if (observation.reachedReady) "YES" else "NO"}",
            "Pinned TLS/QR test link ready: ${if (observation.tlsAndQrReady) "YES" else "NO"}",
            "Local debug approval written to TLS: " +
                if (observation.localApprovalWritten) "YES" else "NO",
            "Peer debug approval observed here: " +
                if (observation.peerApprovalObserved) "YES" else "NO",
            "These observations are NOT independently reviewed human authorization.",
            "Preset phrases written to local TLS: ${observation.writtenToTls}",
            "Preset phrases received by this app: ${observation.receivedHere}",
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
            "No QR, IP, keys, identities, or phrase contents are included."
        ).joinToString("\n")
    }
}
