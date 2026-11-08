"""F2-F guard: deterministic Bluetooth TEXT/ACK frame foundation only.

This does not certify the protocol, trust the radio, or grant send authority.
The live RFCOMM probe must keep closing the socket after both confirmations.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
BT = ROOT / "app/src/debug/java/com/edenpractical/comms/bluetooth"
MAIN = ROOT / "app/src/main/java/com/edenpractical/comms"
TEST = ROOT / "app/src/test/java/com/edenpractical/comms/bluetooth"
ROADMAP = ROOT / "docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md"


class F2FFrameSafetyBoundary(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.frame = (BT / "DebugBluetoothTextFrameCodec.kt").read_text()
        cls.probe = (BT / "DebugRfcommTlsProbe.kt").read_text()
        cls.controller = (BT / "DebugRfcommLinkController.kt").read_text()
        cls.test = (TEST / "DebugBluetoothTextFrameCodecTest.kt").read_text()
        cls.release = (MAIN / "model/ReleaseCapabilities.kt").read_text()
        cls.roadmap = ROADMAP.read_text()

    def test_codec_rejects_invalid_length_version_utf8_and_unrecognized_kind(self):
        for marker in (
            'HEADER_BYTES = 16', 'MAX_TEXT_BYTES = 512',
            'MAX_FRAME_BYTES = HEADER_BYTES + MAX_TEXT_BYTES',
            'MAGIC = 0x45434254', 'VERSION = 1',
            'private const val TEXT = 1', 'private const val ACK = 2',
            'require(messageId > 0)',
            'onMalformedInput(CodingErrorAction.REPORT)',
            'onUnmappableCharacter(CodingErrorAction.REPORT)',
            'bytes.size == HEADER_BYTES + bodyLength',
            'require(bodyLength == 0)',
            'require(bodyLength > 0)',
            'throw IllegalArgumentException("Unrecognized Bluetooth frame kind")'
        ):
            self.assertIn(marker, self.frame, marker)
        for fixture in (
            'textAndAckRoundTripUnicodeAndExactlyBoundedLengths',
            'rejectMalformedTextBeforeAnyMessageIdIsReserved',
            'rejectTruncationPrefixOverrunHeaderMutationsAndInvalidUtf8',
            'twoIndependentLedgersSupportBidirectionalTextAndTruePeerAppAckRehearsal',
            'repeatedSkippedOrOutOfOrderInboundFailsClosed',
            'ackRequiresLiveLocalPendingMessageAndOneUseId',
            'pendingAndDirectionBudgetsAreFiniteAndCannotBeRearmed'
        ):
            self.assertIn(fixture, self.test)

    def test_session_ledger_is_bounded_and_never_infers_receipt_from_local_write(self):
        for marker in (
            'MAX_MESSAGES_PER_DIRECTION = 128', 'MAX_PENDING = 8',
            'private var closed = false',
            'private var nextOutgoing = 1L',
            'private var nextIncoming = 1L',
            'private val pending = LinkedHashSet<Long>()',
            'pending.size < MAX_PENDING',
            'pending.add(id)',
            'frame.messageId == nextIncoming',
            'pending.remove(frame.messageId)',
            'fun close()', 'pending.clear()'
        ):
            self.assertIn(marker, self.frame, marker)
        self.assertNotIn('System.currentTimeMillis()', self.frame)
        self.assertNotIn('SharedPreferences', self.frame)

    def test_unwired_bluetooth_remains_authentication_gate_not_raw_rfcomm(self):
        for source in (self.probe, self.controller):
            self.assertNotIn('DebugBluetoothTextFrameCodec', source)
            self.assertNotIn('DebugBluetoothTextLedger', source)
        for forbidden in (
            'BluetoothSocket(', 'SSLEngine', 'Socket(', 'InputStream(',
            'OutputStream(', 'TestPacketCodec', 'sendDebugText(',
            'openFileOutput(', 'AudioRecord', 'SharedPreferences',
            'ProductionSessionAuthorization'
        ):
            # Source comments can mention SSLEngine and BluetoothSocket; the
            # package imports/classes must not contain actual transport APIs.
            if forbidden in ('SSLEngine', 'BluetoothSocket('):
                implementation = "\n".join(
                    line for line in self.frame.splitlines()
                    if not line.strip().startswith('*') and
                    not line.strip().startswith('//')
                )
                self.assertNotIn(forbidden, implementation)
            else:
                self.assertNotIn(forbidden, self.frame, forbidden)
        self.assertIn('closeQuietly(oldSocket)', self.controller)
        self.assertIn('stop(token, DebugRfcommCompletionPolicy.endAfterConsent(', self.controller)
        for flag in ('CAN_CONNECT', 'CAN_SEND_MESSAGES', 'CAN_TRANSMIT_VOICE'):
            self.assertIn(f'const val {flag} = false', self.release)
        self.assertFalse((MAIN / 'bluetooth/DebugBluetoothTextFrameCodec.kt').exists())

    def test_existing_roadmap_updated_not_replaced(self):
        for marker in ('Updated November 8, 2026',
                       'F0 + F1', 'F2-A–E', 'F2-F',
                       'F3-A — actual Wi-Fi Direct TCP reachability',
                       'F3-B1 — QR-to-pinned mutual TLS over real JVM TCP',
                       'F3-B2 — encrypted QR challenges and transcript agreement',
                       'F3-B3-A — P2P interface-bound real TCP diagnostic',
                       'F3-B3/C — live Wi-Fi Direct authenticated text', 'F4 — PTT',
                       'F5 — optional privacy', 'F6 + F7'):
            self.assertIn(marker, self.roadmap)
        self.assertIn('F2-F — Bluetooth text/ACK format — SOURCE/CI VERIFIED',
                      self.roadmap)
        self.assertIn('F2-G — encrypted frame interoperability lab',
                      self.roadmap)
        self.assertIn('PR #99, merged-main commit', self.roadmap)
        self.assertIn('without representing them as functional radios', self.roadmap)


if __name__ == '__main__':
    unittest.main()
