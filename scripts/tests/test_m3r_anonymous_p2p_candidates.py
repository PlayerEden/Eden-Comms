"""M3r bounded anonymous Wi-Fi P2P candidate review, NOT a connection path."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
PKG = DEBUG / "java/com/edenpractical/comms/wifidirect"
NS = "{http://schemas.android.com/apk/res/android}"


class M3rAnonymousCandidates(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ui = (PKG / "DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.reader = (PKG / "DebugWifiDirectCandidateReader.kt").read_text()
        cls.ledger = (PKG / "DebugWifiDirectCandidateLedger.kt").read_text()
        cls.gate = (PKG / "DebugWifiDirectDiscoveryGate.kt").read_text()

    def test_exactly_one_restricted_peer_read_boundary(self):
        # The Android API actually returns device objects. Only this one
        # debug adapter may receive them, never UI/production/send/voice.
        invocations = [
            p for p in PKG.glob("*.kt")
            if "manager.requestPeers(channel)" in p.read_text()
        ]
        self.assertEqual([p.name for p in invocations],
                         ["DebugWifiDirectCandidateReader.kt"])
        self.assertIn("manager.requestPeers(channel)", self.reader)
        self.assertIn("if (closed || !authorized()) return@requestPeers", self.reader)
        self.assertIn("readCount >= MAX_READS", self.reader)
        self.assertIn("outstanding", self.reader)
        self.assertIn("val objects = peers.deviceList.take(", self.reader)
        self.assertIn("MAX_CANDIDATES = 8", self.ledger)
        self.assertIn("MAX_READS = 12", self.reader)
        for src in (self.reader, self.ledger, self.ui):
            for secret in ("deviceAddress", "deviceName", "EXTRA_P2P_DEVICE_LIST",
                           "BluetoothDevice", "getLastKnownLocation(",
                           "getConnectionInfo(", "requestConnectionInfo(",
                           "connect(", "createGroup(", "removeGroup(",
                           "Socket(", "DatagramSocket(", "getMacAddress(",
                           "openFileOutput(", "SharedPreferences", "Log.d(",
                           "Log.i(", "Log.e(", "println("):
                self.assertNotIn(secret, src, secret)

    def test_user_selection_has_no_android_object_or_session_authority(self):
        for marker in ("private val handles = ArrayList<T>(MAX_CANDIDATES)",
                       "private var selected: Int? = null", "token !== current",
                       "handles.none { it == handle }", "handles.clear()",
                       "selected = null", "token = null",
                       "fun selectedOrdinal(current: Token): Int?",
                       "fun rows(current: Token): List<Row>",
                       "Unverified device"):
            self.assertIn(marker, self.ledger, marker)
        for marker in ("requestCandidateSnapshot(active)",
                       "candidateReader?.close()", "candidateLedger.clear()",
                       "candidateToken = null", "candidateRows = emptyList()",
                       "if (candidateLedger.choose(currentToken, ordinal))",
                       "candidateToken === currentToken",
                       "candidateReader === currentReader",
                       "gate.snapshot().phase == DebugWifiDirectDiscoveryGate.Phase.SCANNING",
                       "!foreground || !prerequisites()",
                       "LOCAL PREVIEW SELECTED", "for (candidate in candidateRows)",
                       "No user text, audio, or authenticated identity"):
            self.assertIn(marker, self.ui, marker)
        self.assertNotIn("selectedHandle(", self.ledger)
        self.assertNotIn("selectedDevice(", self.ledger)
        # M3s-C1: explicitly selected debug link is one-use only. A one-shot
        # callback receives an opaque Android handle AFTER ledger erasure.
        self.assertIn("fun consumeForOneShotLink(current: Token", self.ledger)
        self.assertIn("clear()\n        sink(handle)", self.ledger)
        self.assertIn("requestUnverifiedLink()", self.ui)
        self.assertIn("targetController?.stop(DebugWifiDirectTargetLinkGate.End.BACKGROUND)", self.ui)
        self.assertIn("No new permissions", (ROOT / "docs/M3R_ANONYMOUS_P2P_CANDIDATES.md").read_text())

    def test_no_release_exposure_or_new_permission(self):
        release = (MAIN / "AndroidManifest.xml").read_text()
        debug = ET.parse(DEBUG / "AndroidManifest.xml").getroot()
        acts = [x.get(NS + "name") for x in debug.findall("application/activity")]
        self.assertTrue(any("DebugWifiDirectDiscoveryActivity" in a for a in acts))
        self.assertNotIn("DebugWifiDirectCandidateReader", release)
        self.assertFalse((MAIN / "java/com/edenpractical/comms/wifidirect").exists())
        flags = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + flag + " = false", flags)


if __name__ == "__main__":
    unittest.main()
