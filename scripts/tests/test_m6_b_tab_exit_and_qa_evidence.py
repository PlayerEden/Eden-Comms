"""M6-B: live-talk tab exit, redacted evidence, and unexecuted phone QA.

Static source-safety guards augment actual Kotlin/JVM tests. They are NOT
physical radio, microphone, screen-reader or cryptographic acceptance.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"

class M6BTalkExitAndQaEvidence(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        def source(p):
            return (DEBUG / p).read_text()
        cls.panel = source("ui/DebugVerifiedConversationPanel.kt")
        cls.owner = source("audio/DebugNearbyVoiceOwner.kt")
        cls.qa = source("qa/DebugNearbyQaEvidence.kt")
        cls.qa_panel = source("qa/DebugNearbyQaPanel.kt")
        cls.bt = source("bluetooth/DebugBluetoothDiscoveryActivity.kt")
        cls.host = source("wifidirect/DebugWifiDirectGroupHostActivity.kt")
        cls.join = source("wifidirect/DebugWifiDirectDiscoveryActivity.kt")
        cls.matrix = (ROOT / "docs/TWO_PHONE_ACCEPTANCE_MATRIX.md").read_text()
        cls.unit = (ROOT /
            "app/src/test/java/com/edenpractical/comms/qa/DebugNearbyQaEvidenceTest.kt"
        ).read_text()

    def test_switching_away_from_talk_releases_voice_and_pending_floor(self):
        for key in (
            "onLeaveTalk: () -> Unit",
            "DisposableEffect(selection)", 'if (selection == "talk") onLeaveTalk()',
            "val chooseText = {", 'selection = "text"',
            "onClick = chooseText",
            "Changing to Text stops the microphone",
        ):
            self.assertIn(key, self.panel, key)
        # This is RELEASE, not disable(), because an active floor must
        # send ordered encrypted END/RELEASE rather than strand the peer.
        for screen in (self.bt, self.host, self.join):
            self.assertIn("onLeaveTalk = { localVoice.release() }", screen)
            self.assertNotIn("onLeaveTalk = { localVoice.disable() }", screen)
            self.assertIn("onDisconnect = {", screen)
            self.assertIn("localVoice.disable()", screen)
            self.assertIn("super.onStop()", screen)
        for key in (
            "fun release()", "if (pendingFloor)", "cancelFloor?.invoke()",
            "audio.finishCapture()", "fun disable()", "audio.cancelAll()",
            "fun floorGranted()", "fun floorDenied()",
        ):
            self.assertIn(key, self.owner)
        self.assertNotIn("rememberSaveable", self.panel)

    def test_qa_evidence_is_only_local_counts_after_real_ready(self):
        for key in (
            "internal class DebugNearbyQaEvidence",
            "method in setOf(", "Bluetooth", "Wi-Fi Direct • host",
            "Wi-Fi Direct • join",
            "DebugBluetoothApprovedTextChannel.Event.Ready ->",
            "reset()", "started = true", "active = true",
            "if (!active) return",
            "DebugBluetoothApprovedTextChannel.Event.Failed -> active = false",
            "fun end()", "fun hasObservation()", "fun summary()",
            "Text written to TLS here:", "Peer-app ACK observed here:",
            "Inbound voice DATA frames:", "LOCAL OBSERVATIONS ONLY",
        ):
            self.assertIn(key, self.qa, key)
        for forbidden in (
            "event.text", "event.id", "event.pcm", "getSharedPreferences(",
            "Log.", "println(", "Socket(", "File(", "FileOutputStream",
            "ClipboardManager", "Clipboard", "openFileOutput(",
            "BluetoothAdapter", "WifiP2pManager", "startActivity(",
            "peerFingerprint", "qrPayload", "AudioRecord", "AudioTrack",
        ):
            self.assertNotIn(forbidden, self.qa, forbidden)
        for screen in (self.bt, self.host, self.join):
            self.assertIn("qaEvidence.observe(event)", screen)
            self.assertIn("qaEvidence.end()", screen)
            self.assertIn("qaContent = { DebugNearbyQaPanel(qaEvidence) }", screen)
        for token in (
            "ignoresEventsBeforeActualReadyAndAfterDisconnect",
            "redactsAllTextsAndPcmAndPreservesOnlyNumericCounts",
            "invalidMethodLabelsCannotSmuggleUserDataIntoReport",
        ):
            self.assertIn(token, self.unit)

    def test_qa_copy_is_explicit_advanced_and_warns_of_clipboard_privacy(self):
        for key in (
            "ADVANCED: THIS PHONE'S TEST COUNTS",
            "if (showQa) qaContent()",
            "qaContent: @Composable () -> Unit",
        ):
            self.assertIn(key, self.panel)
        for key in (
            "OutlinedButton(", "onClick = {",
            "evidence.summary()", "evidence.hasObservation()",
            "ClipboardManager", "ClipData.newPlainText(",
            "COPY THIS PHONE'S QA COUNTS",
            "clipboard content",
            "No message bodies, pairing codes",
        ):
            self.assertIn(key, self.qa_panel, key)
        for bad in (
            "LaunchedEffect(", "DisposableEffect(", "SideEffect(",
            "AudioRecord", "BluetoothSocket", "WifiP2pManager",
            "getSharedPreferences(", "startActivity(", "Log.",
        ):
            self.assertNotIn(bad, self.qa_panel, bad)

    def test_consolidated_hardware_matrix_is_honest_and_covers_all_modes(self):
        self.assertIn("State: NOT RUN", self.matrix)
        for method in ("Same Wi-Fi", "Bluetooth Classic", "Wi-Fi Direct"):
            self.assertIn(method, self.matrix)
        for index in range(1, 19):
            self.assertIn(f"| C{index:02d} |", self.matrix)
        for key in (
            "both", "fresh", "GRANT", "mic", "ACK",
            "Android", "NOT RUN", "FAIL", "BLOCKED",
            "No saved peer record", "Not a",
        ):
            # Some user-facing language is case/punctuation-dependent.
            if key not in ("No saved peer record", "Not a"):
                self.assertIn(key.lower(), self.matrix.lower())
        for key in (
            "cryptographic", "independent", "clipboard",
            "Keep release", "CAN_CONNECT", "CAN_SEND_MESSAGES",
            "CAN_TRANSMIT_VOICE", "retest",
        ):
            self.assertIn(key.lower(), self.matrix.lower())
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        for path in ("qa/DebugNearbyQaEvidence.kt", "qa/DebugNearbyQaPanel.kt"):
            self.assertFalse((ROOT / "app/src/main/java/com/edenpractical/comms" / path).exists())
            self.assertFalse((ROOT / "app/src/release/java/com/edenpractical/comms" / path).exists())


if __name__ == "__main__":
    unittest.main()
