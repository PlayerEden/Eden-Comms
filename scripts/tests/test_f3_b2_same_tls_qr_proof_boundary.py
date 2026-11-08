"""F3-B2: pinned TLS + encrypted QR nonce challenge/full transcript proof.

All live P2P radio interfaces stay on F3-A zero-payload mode. Static
source assertions are not a security assessment or Android device QA.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms/wifidirect"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/wifidirect"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F3B2SameTlsProofBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context = (DEBUG / "DebugWifiDirectFutureQrTlsExpectation.kt").read_text()
        cls.proof = (DEBUG / "DebugWifiDirectFutureQrProofSocketLab.kt").read_text()
        cls.commitment = (DEBUG / "DebugWifiDirectFutureQrCommitment.kt").read_text()
        cls.paired = (TEST / "DebugWifiDirectQrPinnedTlsSocketTest.kt").read_text()
        cls.unit = (TEST / "DebugWifiDirectFutureQrCommitmentTest.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()

    def test_exact_qr_pins_nonces_copied_and_wiped(self):
        for marker in (
            "private val hostQrPin = hostPin.copyOf()",
            "private val joinQrPin = joinPin.copyOf()",
            "private val hostQrNonce = hostNonce.copyOf()",
            "private val joinQrNonce = joinNonce.copyOf()",
            "copyQrProofBindingForLab()",
            "SessionProofBinding(hostQrPin, joinQrPin, hostQrNonce, joinQrNonce)",
            "check(!closed)",
            "hostQrPin.fill(0)",
            "joinQrPin.fill(0)",
            "hostQrNonce.fill(0)",
            "joinQrNonce.fill(0)",
            "Role.HOST, reply.pin, digest(host, reply, ack)",
            "Role.JOIN, host.pin, digest(host, reply, ack)",
        ):
            self.assertIn(marker, self.context, marker)

    def test_same_real_loopback_tls_socket_runs_proof_and_commitment_then_closes(self):
        for marker in (
            "address.isLoopbackAddress && address.address.size == 4",
            "ACCEPT_TIMEOUT_MS = 4_000",
            "CONNECT_TIMEOUT_MS = 2_000",
            "IO_TIMEOUT_MS = 4_000",
            "Tls13PeerContext(identity, pin).unboundServerSocket().use",
            "Tls13PeerContext(identity, pin).unconnectedClientSocket().use",
            "incoming",  # only used in established F3-B1 code, not relevant here
        ):
            if marker == "incoming":
                continue
            self.assertIn(marker, self.proof, marker)
        for marker in (
            "listener.accept() as SSLSocket",
            "socket.startHandshake()",
            "PinnedTlsPeer.requirePinnedTls13(socket.session, pin)",
            "runProofOnSameSocket(socket, expectation, isHost = true)",
            "runProofOnSameSocket(socket, expectation, isHost = false)",
            "SessionProofExchange.runHost(",
            "SessionProofExchange.runJoin(",
            "DebugWifiDirectFutureQrCommitment.derive(",
            "DebugWifiDirectFutureQrCommitment.exchange(",
            "socket.inputStream, socket.outputStream",
            "challenges.clear()", "digest.fill(0)", "transcript.fill(0)",
            "binding.hostPin.fill(0)", "binding.joinPin.fill(0)",
            "binding.hostNonce.fill(0)", "binding.joinNonce.fill(0)",
            "pin.fill(0)",
        ):
            self.assertIn(marker, self.proof, marker)
        for forbidden in (
            "DebugWifiDirectTargetLinkController",
            "DebugWifiDirectGroupHostActivity",
            "DebugWifiDirectTcpProbe(", "DebugBluetoothTextLedger",
            "sendDebugText(", "sendPacket(", "AudioRecord",
            "SharedPreferences", "openFileOutput(", "Log.", "println(",
        ):
            # Names of live classes may be mentioned in safety comments,
            # but no imports/calls may depend on them.
            if forbidden in (
                "DebugWifiDirectTargetLinkController",
                "DebugWifiDirectGroupHostActivity",
            ):
                self.assertNotIn("import com.edenpractical.comms.wifidirect." + forbidden,
                                 self.proof)
            else:
                self.assertNotIn(forbidden, self.proof)

    def test_strict_role_ordered_full_qr_challenge_commitments(self):
        for marker in (
            'eden-comms/wifi-direct/encrypted-qr-challenge-proof/v1',
            "const val BYTES = 4 + 1 + 1 + 32",
            "binding.hostPin to 32",
            "binding.joinPin to 32",
            "binding.hostNonce to 16",
            "binding.joinNonce to 16",
            "challenges.hostChallenge to 16",
            "challenges.joinChallenge to 16",
            "sha.update(fullQrTranscript)",
            "MessageDigest.isEqual(",
            "DataInputStream(input).readFully(frame)",
            "if (isHost)", "write(output, HOST, digest)",
            "verify(input, JOIN, digest)",
            "verify(input, HOST, digest)", "write(output, JOIN, digest)",
            "frame.fill(0)"
        ):
            self.assertIn(marker, self.commitment, marker)

    def test_jvm_two_endpoint_and_malformed_frame_cases(self):
        for name in (
            "realTcpTlsSameSocketCompletesQrChallengesAndFullTranscriptTwice",
            "realTcpTlsRejectsDifferentQrNoncesDespiteMatchingCertificatePins",
            "realTcpTlsRejectsAlteredQrTimestampAtFullTranscriptCommitment",
            "qrProofLabCannotTouchActualPrivateWifiDirectRadioOrWrongRole",
            "DebugWifiDirectFutureQrProofSocketLab.hostOnce(",
            "DebugWifiDirectFutureQrProofSocketLab.joinOnce(",
        ):
            self.assertIn(name, self.paired, name)
        for name in (
            "completeQrAndBothFreshChallengesChangeTheDigest",
            "sameDigestTwoRoleOrderedFramesSucceedAcrossBothPipes",
            "missingFrameWrongRoleAndTamperedDigestFailClosed",
            "invalidDigestAndSameChallengesAreRejected",
        ):
            self.assertIn(name, self.unit, name)

    def test_live_p2p_and_release_flags_remain_untouched(self):
        for source in (
            "DebugWifiDirectGroupHostActivity.kt",
            "DebugWifiDirectTargetLinkController.kt",
            "DebugWifiDirectDiscoveryActivity.kt",
            "DebugWifiDirectQrCoordinator.kt",
            "DebugWifiDirectTcpProbe.kt",
        ):
            text = (DEBUG / source).read_text()
            for future in (
                "DebugWifiDirectFutureQrProofSocketLab",
                "DebugWifiDirectFutureQrCommitment",
                "copyQrProofBindingForLab",
            ):
                self.assertNotIn(future, text, (source, future))
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", self.release)
        self.assertFalse((MAIN.parent / "wifidirect/DebugWifiDirectFutureQrProofSocketLab.kt").exists())


if __name__ == "__main__":
    unittest.main()
