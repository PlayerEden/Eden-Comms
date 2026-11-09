"""F2-N: paired JVM TLS controls -> text/ACK on EXACT same engine and pipes.

Full JDK engine interop tests, not simulated human consent and not Android
RFCOMM. Preserve the live Bluetooth diagnostic's close-after-consent rule.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"


class F2NSameTlsEngineHandoff(unittest.TestCase):
    def test_real_jvm_test_uses_same_engine_and_pipes_across_both_phases(self):
        src = (TEST / "DebugPairedTlsEngineInteropTest.kt").read_text()
        start = src.index("fun sameTlsEnginesSurviveControlToTextAckThenFutureOwnersTearDown()")
        stop = src.index("fun leftoverEncryptedControlRecordCannotBecomeFutureText()", start)
        active = src[start:stop]
        for marker in (
            "DebugTlsEngineHandshake.negotiate(host, pipe.hostInput, pipe.hostOutput)",
            "DebugTlsEngineHandshake.negotiate(join, pipe.joinInput, pipe.joinOutput)",
            "PinnedTlsPeer.requirePinnedTls13(host.session, joinIdentity.pin)",
            "PinnedTlsPeer.requirePinnedTls13(join.session, hostIdentity.pin)",
            "val hostControls = DebugTlsControlTransport(host, pipe.hostInput, pipe.hostOutput)",
            "val joinControls = DebugTlsControlTransport(join, pipe.joinInput, pipe.joinOutput)",
            "for (index in phases.indices)",
            "hostControls.clear()",
            "joinControls.clear()",
            "DebugBluetoothFutureTlsTextLab(host, pipe.hostInput, pipe.hostOutput)",
            "DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)",
            "val hostBundle = DebugBluetoothFutureChannelBundle(",
            "val joinBundle = DebugBluetoothFutureChannelBundle(",
            "hostOwner.begin(71, 100, claimed, hostBundle, hostContext)",
            "joinOwner.begin(72, 100, claimed, joinBundle, joinContext)",
            "joinLab.transmit(accepted.ack)",
            "hostLab.transmit(result.ack)",
            "hostOwner.observe(71, 112, true, true, hostContext)",
            "joinOwner.stop(72)",
            "hostBundle.isClosed()",
            "joinBundle.isClosed()",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, active)
        self.assertLess(active.index("val hostControls ="),
                        active.index("val hostLab ="))
        self.assertLess(active.index("hostControls.clear()"),
                        active.index("val hostLab ="))
        # No replacement engine / pipe can silently be substituted mid-test.
        self.assertEqual(1, active.count("val (host, join) = connect()"))
        self.assertEqual(1, active.count("val pipe = PairPipes()"))
        self.assertNotIn("BluetoothSocket", active)
        self.assertNotIn("DebugBluetoothProbeCompletion", active)

    def test_leftover_control_frames_are_rejected_not_resynchronized(self):
        src = (TEST / "DebugPairedTlsEngineInteropTest.kt").read_text()
        start = src.index("fun leftoverEncryptedControlRecordCannotBecomeFutureText()")
        stop = src.index("fun wrongOutOfBandPeerPinCannotCompleteMutualTlsHandshake()", start)
        negative = src[start:stop]
        for marker in (
            "val oldControl = DebugTlsControlTransport(host, pipe.hostInput, pipe.hostOutput)",
            "oldControl.output.write(extra)",
            "oldControl.clear()",
            "DebugBluetoothFutureTlsTextLab(join, pipe.joinInput, pipe.joinOutput)",
            "assertThrows(IllegalArgumentException::class.java) { future.receive() }",
            "assertThrows(javax.net.ssl.SSLException::class.java) { future.receive() }",
        ):
            self.assertIn(marker, negative)

    def test_real_bluetooth_must_continue_closing_after_consent(self):
        probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        controller = (BT / "DebugRfcommLinkController.kt").read_text()
        for source in (probe, controller):
            self.assertNotIn("DebugBluetoothFutureChannelBundle", source)
            self.assertNotIn("DebugBluetoothFutureTlsTextLab", source)
            self.assertNotIn("DebugBluetoothFutureOwnedSession", source)
        self.assertIn("DebugBluetoothConsentExchange.run(", probe)
        self.assertIn("engine?.closeOutbound()", probe)
        self.assertIn("transport?.clear()", probe)
        self.assertIn("stop(token, DebugRfcommCompletionPolicy.endAfterConsent(", controller)
        self.assertIn("closeQuietly(oldSocket)", controller)
        flags = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for cap in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {cap} = false", flags)


if __name__ == "__main__":
    unittest.main()
