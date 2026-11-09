"""F4-V: authenticated Bluetooth / Wi-Fi Direct host-arbitrated live PTT.

Source gates only: Android OEM, radio throughput, voice intelligibility,
echo, permission flows and independent cryptographic review are STILL OPEN.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DEBUG = ROOT / "app/src/debug/java/com/edenpractical/comms"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"

class F4VNearbyFloorLiveVoice(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        def d(name):
            return (DEBUG / name).read_text()
        cls.frame = d("bluetooth/DebugNearbyApplicationFrame.kt")
        cls.channel = d("bluetooth/DebugBluetoothApprovedTextChannel.kt")
        cls.rfcomm = d("bluetooth/DebugRfcommLinkController.kt")
        cls.p2p = d("wifidirect/DebugWifiDirectApprovedTcpSession.kt")
        cls.join = d("wifidirect/DebugWifiDirectTargetLinkController.kt")
        cls.host_ui = d("wifidirect/DebugWifiDirectGroupHostActivity.kt")
        cls.join_ui = d("wifidirect/DebugWifiDirectDiscoveryActivity.kt")
        cls.bt_ui = d("bluetooth/DebugBluetoothDiscoveryActivity.kt")
        cls.owner = d("audio/DebugNearbyVoiceOwner.kt")
        cls.panel = d("audio/DebugNearbyVoicePanel.kt")
        cls.test = (ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth/DebugPairedTlsEngineInteropTest.kt").read_text()
        cls.floor = (MAIN / "transport/DebugLanVoiceFloor.kt").read_text()
        cls.lan_floor_test = (ROOT / "app/src/test/java/com/edenpractical/comms/transport/DebugLanVoiceFloorTest.kt").read_text()

    def test_same_approved_session_multiplexes_strict_floor_text_and_pcm(self):
        for good in (
            "private const val FLOOR_MAGIC = 0x4543464c",
            "enum class Kind { TEXT_OR_ACK, VOICE, FLOOR }",
            "DebugLanVoiceFloorCodec.decode(bytes)",
            "DebugLanVoiceWireCodec.decode(bytes).clear()",
            "DebugBluetoothTextFrameCodec.decode(bytes)",
        ):
            self.assertIn(good, self.frame)
        for good in (
            "private val floor = DebugLanVoiceFloor(isHost)",
            "DebugNearbyApplicationFrame.Kind.FLOOR ->",
            "floor.incoming(", "DebugLanVoiceFloorCodec.decode(frame)",
            "floor.remoteAudioStart()", "floor.remoteAudioEnd()",
            "fun requestVoiceFloor(): DebugLanVoiceFloor.Ask",
            "fun cancelUnstartedVoiceFloor()",
            "floor.cancelPending() ?: floor.releaseLocal()",
            "floor.timedOut(nowMs())", "floor.close()",
            "Event.FloorGranted", "Event.FloorDenied",
        ):
            self.assertIn(good, self.channel, good)
        for bad in ("BluetoothSocket", "ServerSocket", "DatagramSocket",
                    "SharedPreferences", "openFileOutput(", "Log.", "println(",
                    "ProductionSessionAuthorization"):
            self.assertNotIn(bad, self.channel)

    def test_true_20ms_microphone_packets_while_held_with_bounded_writer(self):
        for good in (
            "fun beginLiveVoice(): Boolean", "fun offerLiveVoiceFrame(pcm: ByteArray)",
            "fun finishLiveVoice(): Boolean",
            "DebugLanVoiceWireCodec.start(liveTurn)",
            "DebugLanVoiceWireCodec.data(liveTurn, liveSequence, pcm)",
            "DebugLanVoiceWireCodec.end(liveTurn, count)",
            "outbound.remainingCapacity() < (if (first) 5 else 4)",
            "DebugLanVoiceWireCodec.MAX_FRAMES",
            "if (closed || !liveVoiceActive || !floor.maySendAudio()",
            "if (!outbound.offer(Pending(end, voiceEndFrames = count)))",
            "pending.bytes.fill(0)",
            "incomingVoice.clear()",
        ):
            self.assertIn(good, self.channel, good)
        for good in (
            "audio.startLiveCapture(", "audio.finishCapture()",
            "audio.acceptLiveFrame(kind, pcm)",
            "onFrame = { pcm ->", "offerFrame?.invoke(pcm)",
            "onStopped = { hadPcm ->",
            "finishStream?.invoke()", "cancelFloor?.invoke()",
            "fun floorGranted()", "fun floorDenied()",
            "DebugLanVoiceFloor.Ask.PENDING",
            "Talk channel busy", "Mic remains OFF", "audio.cancelAll()",
        ):
            self.assertIn(good, self.owner, good)
        self.assertNotIn("audio.startCapture(", self.owner)
        self.assertNotIn("audio.playReceived(", self.owner)
        self.assertIn("Hold to request the talk channel", self.panel)
        self.assertIn("ActivityResultContracts.RequestPermission()", self.panel)

    def test_role_binding_is_same_authenticated_qr_and_dual_confirm(self):
        self.assertIn("isHost = request.hostRole", self.rfcomm)
        self.assertIn("isHost = request.hostRole", self.p2p)
        for path in (self.rfcomm, self.p2p):
            for good in (
                "fun requestVoiceFloor()", "fun cancelUnstartedVoiceFloor()",
                "fun beginLiveVoice()", "fun offerLiveVoiceFrame(pcm: ByteArray)",
                "fun finishLiveVoice()", "event.pcm.fill(0)",
                "Event.LiveVoiceFrame",
            ):
                self.assertIn(good, path, good)
        for good in (
            "fun requestVerifiedVoiceFloor()",
            "fun offerVerifiedLiveVoiceFrame(pcm: ByteArray)",
            "verifiedSession?.offerLiveVoiceFrame(pcm)",
        ):
            self.assertIn(good, self.join)
        for view in (self.bt_ui, self.host_ui, self.join_ui):
            for key in (
                "localVoice.floorGranted()", "localVoice.floorDenied()",
                "localVoice.receiveLiveFrame(event.kind, event.pcm)",
                "localVoice.disable()", "DebugNearbyVoicePanel(localVoice)",
            ):
                self.assertIn(key, view, key)
        for bad in ("DebugNearbyVoiceOwner(", "requestVoiceFloor(",
                    "offerLiveVoiceFrame("):
            for path in (
                "bluetooth/DebugBluetoothQrCoordinator.kt",
                "wifidirect/DebugWifiDirectQrCoordinator.kt",
                "wifidirect/DebugWifiDirectTcpProbe.kt",
            ):
                self.assertNotIn(bad, (DEBUG / path).read_text())

    def test_real_paired_tls_and_existing_collision_replay_tests(self):
        for good in (
            "isHost = true", "isHost = false",
            "hostChannel.get().requestVoiceFloor()",
            "joinChannel.get().requestVoiceFloor()",
            "joinFloorGranted.await(5, TimeUnit.SECONDS)",
            "hostChannel.get().beginLiveVoice()",
            "hostChannel.get().offerLiveVoiceFrame(pcm20)",
            "hostChannel.get().finishLiveVoice()",
            "joinLiveStarted.await(5, TimeUnit.SECONDS)",
            "joinLiveEnded.await(5, TimeUnit.SECONDS)",
            "fullJvmControlSequence(",
        ):
            self.assertIn(good, self.test)
        for name in (
            "simultaneousPressGoesToHostAndJoinerIsDenied",
            "cancellationBeforeGrantNeverStartsAbandonedMicrophone",
            "lateAbandonedGrantDoesNotCorruptANewerPendingRequest",
            "replayWrongRoleAndUnapprovedRemotePcmAreRejected",
            "missingGrantAndNeverReleasedLeaseRequireWholeSessionClose",
        ):
            self.assertIn(name, self.lan_floor_test)
        model = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        for flag in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {flag} = false", model)


if __name__ == "__main__":
    unittest.main()
