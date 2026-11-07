"""M3n-A: real in-person QR PREPARATION before debug RFCOMM only.

These tests enforce exact debug-only source/manifest/link admission boundaries.
They do not certify the QR camera on hardware, cryptographic RFCOMM
authentication, TLS, bilateral approval or message sending.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "app/src/main"
DEBUG = ROOT / "app/src/debug"
BT = DEBUG / "java/com/edenpractical/comms/bluetooth"


class BluetoothReciprocalQrBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.codec = (BT / "BluetoothQrPairingCodec.kt").read_text()
        cls.coordinator = (BT / "DebugBluetoothQrCoordinator.kt").read_text()
        cls.panel = (BT / "DebugBluetoothQrPanel.kt").read_text()
        cls.activity = (BT / "DebugBluetoothDiscoveryActivity.kt").read_text()
        cls.link = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.release = (MAIN / "java/com/edenpractical/comms/model/ReleaseCapabilities.kt").read_text()
        cls.manifest = (MAIN / "AndroidManifest.xml").read_text()
        cls.debug_manifest = (DEBUG / "AndroidManifest.xml").read_text()
        cls.lan_qr = (MAIN / "java/com/edenpractical/comms/pairing/LocalPairingQrCamera.kt").read_text()

    def test_bluetooth_qr_does_not_include_wifi_ip_or_radio_names(self):
        self.assertIn('HOST_TAG = "ECBH1"', self.codec)
        self.assertIn('JOIN_TAG = "ECBJ1"', self.codec)
        self.assertIn("MAX_AGE_SECONDS = 300L", self.codec)
        self.assertIn("MAX_QR_LENGTH = 170", self.codec)
        self.assertIn("requireFresh(host, nowSeconds)", self.codec)
        self.assertIn("SessionProofBinding(hostPin, joinPin, hostNonce, joinNonce)", self.codec)
        self.assertIn("MessageDigest.isEqual(reply.hostNonce, host.nonce)", self.codec)
        self.assertIn("MessageDigest.isEqual(reply.pin, ownPin)", self.codec)
        for forbidden in (
            "wifiIp", "hostIpv4", "IpAddress", "BluetoothDevice",
            "BluetoothSocket", "getAddress(", "getName(",
            "SharedPreferences", "DataStore", "Log.", "println(",
            "ServerSocket", "Socket(", "getInputStream(",
            "getOutputStream(", "BluetoothAdapter"
        ):
            self.assertNotIn(forbidden, self.codec, forbidden)
        self.assertIn("hostPin.fill(0)", self.codec)
        self.assertIn("joinNonce.fill(0)", self.codec)

    def test_in_person_two_qr_camera_is_real_and_explicit(self):
        for expected in (
            "AndroidKeystoreIdentity()",
            "repository.existing()",
            "repository.createWithConsent()",
            "BluetoothQrPairingCodec.freshHost(",
            "BluetoothQrPairingCodec.encodeHost(invite)",
            "BluetoothQrPairingCodec.decodeHost(raw, now)",
            "BluetoothQrPairingCodec.freshReply(",
            "BluetoothQrPairingCodec.encodeReply(response)",
            "BluetoothQrPairingCodec.decodeReply(raw, invite, now)",
            "BluetoothQrPairingCodec.preparedHost(",
            "BluetoothQrPairingCodec.preparedJoin(",
            "fun readyForDebugLink(hostRole: Boolean): Boolean",
            "prepared?.binding(now)",
            "prepared?.peerFingerprint(now)",
            "fun clear()",
            "prepared?.clear()"
        ):
            self.assertIn(expected, self.coordinator, expected)
        for expected in (
            "LocalPairingQrScanner(", "LocalPairingQrDisplay(",
            "key(qr.scannerGeneration)", "onPayload = qr::scanned",
            "1 — SHOW MY FRESH HOST QR",
            "1 — SCAN OTHER PHONE'S HOST QR",
            "2 — SCAN OTHER PHONE'S REPLY QR",
            "I SHOWED THE REPLY — CONTINUE",
            "CANCEL AND ERASE QR PREPARATION",
            "private key",  # must not appear in UI as available transport proof
        ):
            if expected == "private key":
                self.assertNotIn(expected, self.panel.lower())
            else:
                self.assertIn(expected, self.panel, expected)
        self.assertIn("fun cameraFailed()", self.coordinator)
        self.assertIn("fun retryCamera()", self.coordinator)
        self.assertIn("LocalPairingQrScanner(", self.lan_qr)

    def test_both_rfccom_roles_fail_closed_before_fresh_qr(self):
        for role in ("true", "false"):
            self.assertGreaterEqual(
                self.activity.count("qrCoordinator.readyForDebugLink(hostRole = " + role + ")"),
                2, role
            )
        self.assertIn("DebugBluetoothQrPanel(qrCoordinator)", self.activity)
        self.assertIn("qrCoordinator.loadExistingIdentity()", self.activity)
        self.assertIn(
            "qrCoordinator.stage == DebugBluetoothQrCoordinator.Stage.HOST_PREPARED",
            self.activity
        )
        self.assertIn(
            "qrCoordinator.stage == DebugBluetoothQrCoordinator.Stage.JOIN_PREPARED",
            self.activity
        )
        self.assertIn("qrCoordinator.clear()", self.activity)
        self.assertIn("if (!discoverabilityPromptOpen)", self.activity)
        self.assertIn("QR preparation is frozen while this unverified", self.activity)
        self.assertIn("Peer", self.coordinator)
        # QR must never trigger any RFCOMM socket directly.
        for dangerous in (
            "linkController.host(", "linkController.join(", "createRfcommSocket",
            "sendDebugText(", "createBond(", "getInputStream(", "getOutputStream("
        ):
            self.assertNotIn(dangerous, self.coordinator + self.panel, dangerous)

    def test_release_flags_and_no_unsupported_permission_changes(self):
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + name + " = false", self.release)
        for permission in (
            "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE",
            "NEARBY_WIFI_DEVICES", "ACCESS_FINE_LOCATION", "RECORD_AUDIO"
        ):
            self.assertNotIn("android.permission." + permission, self.manifest)
        for permission in (
            "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE"
        ):
            self.assertIn("android.permission." + permission, self.debug_manifest)
        for cls in ("BluetoothQrPairingCodec", "DebugBluetoothQrCoordinator", "DebugBluetoothQrPanel"):
            self.assertFalse(
                (MAIN / "java/com/edenpractical/comms/bluetooth" / (cls + ".kt")).exists()
            )
        for forbidden in (
            ".inputStream", ".outputStream", "getInputStream(", "getOutputStream(",
            "TestPacketCodec", "SessionProofExchange", "write(", "read("
        ):
            self.assertNotIn(forbidden, self.link, forbidden)


if __name__ == "__main__":
    unittest.main()
