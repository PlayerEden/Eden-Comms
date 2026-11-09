"""M6-H: a successfully issued P2P three-QR TLS request is single-use.

These source contracts and JVM claim tests cannot certify that actual
Android Wi-Fi Direct/TLS, QR cameras or two-phone consent work in practice.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class M6HOneUseVerifiedP2pQr(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gate = (D / "wifidirect/DebugWifiDirectVerifiedQrUseGate.kt").read_text()
        cls.qr = (D / "wifidirect/DebugWifiDirectQrCoordinator.kt").read_text()
        cls.host = (D / "wifidirect/DebugWifiDirectGroupHostActivity.kt").read_text()
        cls.join = (D / "wifidirect/DebugWifiDirectDiscoveryActivity.kt").read_text()
        cls.join_owner = (D / "wifidirect/DebugWifiDirectTargetLinkController.kt").read_text()
        cls.session = (D / "wifidirect/DebugWifiDirectApprovedTcpSession.kt").read_text()
        cls.flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.unit = (
            ROOT / "app/src/test/java/com/edenpractical/comms/wifidirect/"
            "DebugWifiDirectVerifiedQrUseGateTest.kt"
        ).read_text()

    def test_one_shot_claim_is_thread_safe_and_not_an_authorization(self):
        for phrase in (
            "private var issued = false",
            "@Synchronized",
            "fun available(): Boolean = !issued",
            "fun claim(): Boolean",
            "if (issued) return false",
            "issued = true",
            "fun resetForNewQrExchange()",
            "issued = false",
        ):
            self.assertIn(phrase, self.gate, phrase)
        self.assertEqual(3, self.gate.count("@Synchronized"))
        for forbidden in (
            "WifiP2pManager", "SSLEngine", "Socket", "startActivity(",
            "approve(", "SharedPreferences", "openFileOutput(",
            "ProductionSessionAuthorization", "ApplicationInfo",
        ):
            self.assertNotIn(forbidden, self.gate, forbidden)

    def test_proof_is_claimed_after_actual_request_object_construction(self):
        assert "private val verifiedRequestClaim = DebugWifiDirectVerifiedQrUseGate()" in self.qr
        start = self.qr.index("fun newVerifiedTransportRequest(hostRole: Boolean)")
        method = self.qr[start:self.qr.index("fun readyForVerifiedTransport(", start)]
        for text in (
            "if (!verifiedRequestClaim.available()) return null",
            "DebugWifiDirectQrCodec.checkFresh(h, now)",
            "DebugWifiDirectFutureQrTlsExpectation.host(",
            "DebugWifiDirectFutureQrTlsExpectation.join(",
            "require(!MessageDigest.isEqual(targetPin, own.publicFingerprint()))",
            "val request = DebugBluetoothTlsProbeInput(",
            "if (verifiedRequestClaim.claim())",
            "request.close()",
            "targetPin.fill(0)",
            "proof.hostPin.fill(0)",
            "proof.joinPin.fill(0)",
            "proof.hostNonce.fill(0)",
            "proof.joinNonce.fill(0)",
        ):
            self.assertIn(text, method, text)
        self.assertLess(method.index("val request = DebugBluetoothTlsProbeInput("),
                        method.index("if (verifiedRequestClaim.claim())"))
        self.assertIn('If it fails or closes, exchange all three fresh codes again.', method)
        self.assertIn("verifiedRequestClaim.available() &&",
                      self.qr.split("fun readyForVerifiedTransport(", 1)[1])
        self.assertIn(
            "private fun clearQrOnly() {\n        verifiedRequestClaim.resetForNewQrExchange()",
            self.qr
        )
        # No socket failure, approval refusal or timeout is allowed to reset.
        self.assertEqual(1, self.qr.count("verifiedRequestClaim.resetForNewQrExchange()"))
        self.assertEqual(1, self.qr.count("verifiedRequestClaim.claim()"))

    def test_both_real_radio_roles_request_proof_with_original_guards(self):
        for activity, role in ((self.host, "true"), (self.join, "false")):
            self.assertIn(
                f"preparation.newVerifiedTransportRequest({role})",
                activity
            )
            self.assertIn("preparation.readyForVerifiedTransport(", activity)
            self.assertIn("DebugWifiDirectApprovedTcpSession.Phase.CHAT", activity)
            self.assertIn("localVoice.disable()", activity)
            self.assertIn("preparation.clear()", activity)
        self.assertIn("pendingVerifiedRequest = request", self.join_owner)
        self.assertIn("pendingVerifiedRequest?.close()", self.join_owner)
        self.assertIn("request.close()", self.session)
        self.assertIn("DebugRfcommTlsProbe.runStreams(", self.session)
        self.assertIn("phase = Phase.AWAITING_APPROVAL", self.session)

    def test_negative_and_concurrent_claim_regressions_cover_single_use(self):
        for test in (
            "oneQrTranscriptCanBeClaimedOnlyOnceUntilExplicitFreshQrReset",
            "simultaneousConnectionAttemptsCannotBothClaimOneQrTranscript",
            "aFreshExchangeGetsOneNewUseWithoutRestoringAnyOtherAuthority",
            "CountDownLatch(1)",
            "AtomicInteger(0)",
            "assertEquals(1, successes.get())",
            "assertFalse(gate.available())",
        ):
            self.assertIn(test, self.unit, test)

    def test_no_debug_one_shot_leaks_to_release_or_toggles_capabilities(self):
        self.assertFalse(
            (MAIN / "wifidirect/DebugWifiDirectVerifiedQrUseGate.kt").exists())
        for flag in (
            "CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"
        ):
            self.assertIn(f"const val {flag} = false", self.flags)


if __name__ == "__main__":
    unittest.main()
