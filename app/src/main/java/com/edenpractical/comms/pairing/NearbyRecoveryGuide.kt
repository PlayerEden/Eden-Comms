package com.edenpractical.comms.pairing

import com.edenpractical.comms.transport.LanDemoStatus
import com.edenpractical.comms.transport.LanProbeDiagnostics

/**
 * Fixed, privacy-minimized nearby connection recovery copy for DEBUG builds.
 *
 * Classifies *observed* local failures, not a diagnosis of the other phone.
 * Never accepts free-form exception text, IPs, SSIDs, certificates, QR
 * payloads or chat content. No automatic retries or trust decisions.
 */
internal object NearbyRecoveryGuide {
    enum class Category { NETWORK, PAIRING, IDENTITY, COMPATIBILITY, TIMEOUT, INTERRUPTED, COMPLETED, UNKNOWN }

    data class Advice(
        val category: Category,
        val title: String,
        val detail: String,
        val nextStep: String,
        val wifiCheckHelpful: Boolean = false
    )

    fun wifiStatus(available: Boolean): String =
        if (available) {
            "Wi-Fi appears available on this phone. Both phones still need the same Wi-Fi."
        } else {
            "No usable Wi-Fi connection found on this phone."
        }

    fun forResult(
        finish: LocalQaReport.Finish,
        failure: LanProbeDiagnostics.Code?,
        end: LanDemoStatus.EndCode?,
        wifiAvailable: Boolean
    ): Advice {
        if (finish == LocalQaReport.Finish.USER_ENDED) return Advice(
            Category.COMPLETED, "You ended this conversation",
            "The development test session is closed on this phone.",
            "To talk again, start over on both phones with fresh QR codes."
        )
        if (finish == LocalQaReport.Finish.APP_BACKGROUNDED) return Advice(
            Category.INTERRUPTED, "The app left the screen",
            "The test socket was closed because this app went to the background.",
            "Keep both apps open and pair again with fresh QR codes."
        )
        // A known remote/protocol reason is more informative than a later
        // network observation (which may have changed after the test ended).
        if (end != null) return fromEnd(end)
        if (failure != null) return fromFailure(failure)
        if (!wifiAvailable) return Advice(
            Category.NETWORK, "Check your Wi-Fi",
            "This phone has no usable local Wi-Fi interface right now.",
            "Connect both phones to the same non-guest Wi-Fi, then check again.",
            wifiCheckHelpful = true
        )
        return Advice(
            Category.UNKNOWN, "Start a fresh connection",
            "This test did not record a specific local failure category.",
            "Check both phones are ready, then repeat the two QR scans."
        )
    }

    private fun fromFailure(code: LanProbeDiagnostics.Code): Advice = when (code) {
        LanProbeDiagnostics.Code.WIFI_UNAVAILABLE, LanProbeDiagnostics.Code.WIFI_CHANGED,
        LanProbeDiagnostics.Code.PEER_UNREACHABLE, LanProbeDiagnostics.Code.HOST_NOT_ACCEPTING,
        LanProbeDiagnostics.Code.NETWORK_UNREACHABLE, LanProbeDiagnostics.Code.LISTEN_UNAVAILABLE ->
            Advice(
                Category.NETWORK, "Check the nearby Wi-Fi connection",
                "The phones did not establish the local test link. A guest network " +
                    "or device-isolation setting could prevent nearby connections.",
                "Check that both phones are on the same non-guest Wi-Fi. " +
                    "Keep both apps open, then scan new codes on both phones.",
                wifiCheckHelpful = true
            )
        LanProbeDiagnostics.Code.HOST_WAIT_TIMEOUT, LanProbeDiagnostics.Code.CONNECT_TIMEOUT,
        LanProbeDiagnostics.Code.HANDSHAKE_TIMEOUT,
        LanProbeDiagnostics.Code.SESSION_BINDING_TIMEOUT,
        LanProbeDiagnostics.Code.EXPORTER_TIMEOUT,
        LanProbeDiagnostics.Code.READY_TIMEOUT ->
            Advice(
                Category.TIMEOUT, "The connection took too long",
                "The connection or one of its checks did not finish in time.",
                "Keep both phone screens open; start again with two fresh QR codes.",
                wifiCheckHelpful = true
            )
        LanProbeDiagnostics.Code.CERTIFICATE_REJECTED,
        LanProbeDiagnostics.Code.SESSION_BINDING_REJECTED,
        LanProbeDiagnostics.Code.EXPORTER_MISMATCH,
        LanProbeDiagnostics.Code.READY_REJECTED ->
            Advice(
                Category.IDENTITY, "Device verification did not match",
                "This phone could not verify the expected identity or shared test session.",
                "Do not bypass verification. Restart on both phones with new QR codes."
            )
        LanProbeDiagnostics.Code.TLS_UNSUPPORTED,
        LanProbeDiagnostics.Code.LOCAL_SIGNING_FAILED,
        LanProbeDiagnostics.Code.TLS_HANDSHAKE_FAILED,
        LanProbeDiagnostics.Code.EXPORTER_UNAVAILABLE ->
            Advice(
                Category.COMPATIBILITY, "This device needs a compatibility check",
                "The required TLS identity, signing or channel-binding test could not finish.",
                "Check the device identity in Developer Tools and keep the safe local QA " +
                    "summary for later. Do not disable verification."
            )
        LanProbeDiagnostics.Code.CONNECTION_DROPPED ->
            Advice(
                Category.INTERRUPTED, "The local connection was interrupted",
                "The test stopped before the phones finished verifying one another.",
                "Return both phones to the same Wi-Fi and repeat the QR steps.",
                wifiCheckHelpful = true
            )
        LanProbeDiagnostics.Code.UNKNOWN -> Advice(
            Category.UNKNOWN, "The connection could not finish",
            "This phone did not classify a specific reason for the failed test.",
            "Keep both phones open, then try fresh QR codes; retain the QA summary if it repeats."
        )
    }

    private fun fromEnd(code: LanDemoStatus.EndCode): Advice = when (code) {
        LanDemoStatus.EndCode.PEER_ENDED, LanDemoStatus.EndCode.PEER_DECLINED ->
            Advice(
                Category.COMPLETED, "The other phone ended this test",
                "The peer stopped or declined the current development connection.",
                "If both people still want to connect, begin a new pairing on both phones."
            )
        LanDemoStatus.EndCode.SESSION_LIMIT ->
            Advice(
                Category.COMPLETED, "This test session reached its time limit",
                "Development connections end after the session limit.",
                "To continue, start a new session with fresh QR codes."
            )
        LanDemoStatus.EndCode.WIFI_CHANGED, LanDemoStatus.EndCode.LINK_INTERRUPTED,
        LanDemoStatus.EndCode.PEER_SILENT ->
            Advice(
                Category.NETWORK, "The nearby connection ended",
                "The local Wi-Fi link changed, stopped responding or was interrupted.",
                "Check both phones' Wi-Fi and start again with fresh codes.",
                wifiCheckHelpful = true
            )
        LanDemoStatus.EndCode.APPROVAL_TIMED_OUT ->
            Advice(
                Category.TIMEOUT, "Both approvals were not completed",
                "The session ended before both phones finished their approvals and confirmations.",
                "Keep both screens open and approve only the intended phone in a new session."
            )
        LanDemoStatus.EndCode.PROTOCOL_REJECTED ->
            Advice(
                Category.IDENTITY, "A message failed the test protocol check",
                "This session was closed because an unexpected or invalid packet was received.",
                "Do not bypass checks or resume the old session. Save the local QA " +
                    "summary and start again with fresh codes only after reviewing the issue."
            )
        LanDemoStatus.EndCode.CLOCK_INVALID, LanDemoStatus.EndCode.MONITOR_FAILED ->
            Advice(
                Category.INTERRUPTED, "The local safety monitor stopped the test",
                "The session timer or monitoring state could not be trusted.",
                "Start a fresh session only after checking the phone's time and app state."
            )
    }
}
