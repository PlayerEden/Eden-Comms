"""M6-E source contracts: bounded accessible text, real queue feedback, no fake receipts.

Static checks cannot establish Android keyboard/TalkBack layout, real radio
delivery, two-phone ACK timing, data erasure or security certification.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"


class M6ETextComposerAckRecovery(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui_copy = (DEBUG / "ui/DebugNearbyTextUiCopy.kt").read_text()
        cls.bt_ui = (DEBUG / "bluetooth/DebugBluetoothTextPanel.kt").read_text()
        cls.p2p_ui = (DEBUG / "wifidirect/DebugWifiDirectTextPanel.kt").read_text()
        cls.bt_owner = (DEBUG / "bluetooth/DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.p2p_state = (DEBUG / "wifidirect/DebugWifiDirectChatUiState.kt").read_text()
        cls.host = (DEBUG / "wifidirect/DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (DEBUG / "wifidirect/DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.draft = (MAIN / "pairing/DebugTextDraftPolicy.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_both_verified_composers_use_the_same_utf_safe_draft_gate(self):
        for panel in (self.bt_ui, self.p2p_ui):
            for phrase in (
                "DebugNearbyTextUiCopy.acceptsDraft(candidate)",
                "DebugNearbyTextUiCopy.canSubmit(draft)",
                "DebugTextDraftPolicy.codePoints(draft)",
                "KeyboardOptions(imeAction = ImeAction.Send)",
                "if (DebugNearbyTextUiCopy.canSubmit(draft)) onSend()",
                "sendFeedback: String =",
                "liveRegion = LiveRegionMode.Polite",
            ):
                self.assertIn(phrase, panel, phrase)
            self.assertNotIn("it.take(280)", panel)
            self.assertNotIn("onSend = { onSend() }", panel)
            self.assertNotIn("rememberSaveable", panel)
        for source in (self.draft, self.ui_copy):
            self.assertIn("MAX_CODE_POINTS", source)
        self.assertIn("DebugTextDraftPolicy.accepts(text)", self.ui_copy)
        self.assertIn("DebugTextDraftPolicy.maySend(text)", self.ui_copy)

    def test_message_list_is_bounded_scrollable_without_read_position_theft(self):
        for panel in (self.bt_ui, self.p2p_ui):
            for phrase in (
                ".heightIn(max = 236.dp)", ".verticalScroll(scroll)",
                "snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.isScrollInProgress) }",
                "if (moving && !autoScrolling)",
                "LaunchedEffect(lines.lastOrNull()?.id, lines.lastOrNull()?.outgoing)",
                "if (lines.isNotEmpty() && followNewest)",
                "scroll.animateScrollTo(scroll.maxValue)",
                "Text(\"JUMP TO LATEST\")",
                ".semantics(mergeDescendants = true) { }",
                "Modifier.semantics { heading() }",
            ):
                self.assertIn(phrase, panel, phrase)
            self.assertNotIn("LazyColumn", panel)
            self.assertNotIn("SharedPreferences", panel)
            self.assertNotIn("openFileOutput(", panel)

    def test_queue_written_peer_ack_are_distinct(self):
        for phrase in (
            "Received on this phone",
            "Written to encrypted test link here",
            "no peer-app ACK observed",
            "Other app returned ACK",
            "not a read receipt",
            "Queued on this phone for encrypted sending",
            "Not yet a write or delivery receipt",
            "Not queued. Draft kept.",
        ):
            self.assertIn(phrase, self.ui_copy, phrase)
        for panel in (self.bt_ui, self.p2p_ui):
            self.assertIn("DebugNearbyTextUiCopy.lineStatus(line.outgoing, line.acknowledged)", panel)
            self.assertIn("A peer-app ACK is not a read receipt", panel)
        self.assertIn("chatSendFeedback =", self.bt_owner)
        self.assertIn("linkController.offerText(draft)", self.bt_owner)
        self.assertIn("sendFeedback = \"\"", self.p2p_state)
        self.assertIn("DebugNearbyTextUiCopy.queueFeedback(accepted)", self.p2p_state)
        for screen in (self.host, self.join):
            self.assertIn("verifiedChat.noteQueueResult(true)", screen)
            self.assertIn("verifiedChat.noteQueueResult(false)", screen)
            self.assertIn("sendFeedback = verifiedChat.sendFeedback", screen)
            self.assertIn("verifiedChat.clearQueueFeedback()", screen)
        self.assertIn("chatSendFeedback = \"\"", self.bt_owner)
        self.assertIn("sendFeedback = chatSendFeedback", self.bt_owner)

    def test_debug_presentation_must_not_gain_authorization_or_release_rights(self):
        for panel in (self.bt_ui, self.p2p_ui, self.ui_copy):
            for forbidden in (
                "BluetoothSocket", "WifiP2pManager", "SSLEngine",
                "approveForTest(", "startDiscovery(", "requestVoiceFloor(",
                "KeyStore", "SharedPreferences", "openFileOutput(",
                "ProductionSessionAuthorization", "Log.", "println(",
            ):
                self.assertNotIn(forbidden, panel, forbidden)
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.flags)
        self.assertFalse((ROOT / "app/src/release/java/com/edenpractical/comms/ui/DebugNearbyTextUiCopy.kt").exists())


if __name__ == "__main__":
    unittest.main()
