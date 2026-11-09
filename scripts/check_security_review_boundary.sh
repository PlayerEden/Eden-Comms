#!/usr/bin/env bash
set -euo pipefail

# M2f experimental approval code must stay physically unreachable from
# app UI, the LAN probe, and production message/voice paths until an
# independent protocol review authorizes a separate integration PR.
MAIN="app/src/main/java"
CAPABILITIES="$MAIN/com/edenpractical/comms/model/ReleaseCapabilities.kt"
test -f "$CAPABILITIES"

# The proposed wire parser is unit-test-only and must not ship in any APK.
test -f "app/src/test/java/com/edenpractical/comms/security/ReviewOnlyApprovalFrame.kt"
if test -e "$MAIN/com/edenpractical/comms/security/ReviewOnlyApprovalFrame.kt"; then
  echo "FAIL: unreviewed approval parser was placed in app main sources" >&2
  exit 1
fi

for NAME in CAN_CONNECT CAN_SEND_MESSAGES CAN_TRANSMIT_VOICE; do
  if ! grep -Eq "^[[:space:]]*const val[[:space:]]+$NAME[[:space:]]*=[[:space:]]*false([[:space:]]*//.*)?$" "$CAPABILITIES"; then
    echo "FAIL: $NAME must remain false until reviewed production integration" >&2
    exit 1
  fi
done

for CLASS in ReviewOnlyApprovalFrame ProductionSessionAuthorization; do
  if grep -RFn --include='*.kt' --exclude="$CLASS.kt" "$CLASS" "$MAIN"; then
    echo "FAIL: review-only $CLASS was referenced by app code" >&2
    exit 1
  fi
done

LEGACY="$MAIN/com/edenpractical/comms/security/SessionSecurityGate.kt"
grep -Fq 'val maySendMessage: Boolean' "$LEGACY"
grep -Fq 'val mayTransmitAudio: Boolean' "$LEGACY"
if ! grep -A1 'val maySendMessage: Boolean' "$LEGACY" | grep -Eq 'get\(\)[[:space:]]*=[[:space:]]*false'; then
  echo "FAIL: old mock gate could authorize message send" >&2; exit 1
fi
if ! grep -A1 'val mayTransmitAudio: Boolean' "$LEGACY" | grep -Eq 'get\(\)[[:space:]]*=[[:space:]]*false'; then
  echo "FAIL: old mock gate could authorize voice" >&2; exit 1
fi

# M2h proposed transcript builder and bilateral approval simulation must
# remain JVM-test-only. No installed APK or live socket/Compose UI may link
# these unreviewed approval semantics. Review future class refactors explicitly.
for CLASS in ReviewOnlyChannelCommitment ReviewOnlyBilateralApproval; do
  TEST_FILE="app/src/test/java/com/edenpractical/comms/security/$CLASS.kt"
  test -f "$TEST_FILE" || { echo "FAIL: missing M2h test-only $CLASS" >&2; exit 1; }
  if find "$MAIN" -name "$CLASS.kt" -print -quit | grep -q .; then
    echo "FAIL: unreviewed M2h $CLASS moved to app main sources" >&2; exit 1
  fi
  if grep -RFn --include='*.kt' "$CLASS" "$MAIN"; then
    echo "FAIL: production app refers to review-only approval simulator $CLASS" >&2
    exit 1
  fi
done

# M2o is an isolated state-only design model. It is compiled but must NOT
# be plugged into networking, user permission prompts, or production send/audio
# until its adapter and the authentication boundary receive separate review.
MODEL="$MAIN/com/edenpractical/comms/transport/NearbySessionLifecycle.kt"
test -f "$MODEL"
if grep -RFn --include='*.kt' --exclude='NearbySessionLifecycle.kt' 'NearbySessionLifecycle' "$MAIN"; then
  echo "FAIL: unreviewed M2o lifecycle model was wired into app code" >&2
  exit 1
fi

# M2p virtual adapters are intentionally JVM-test-only. Do NOT package the
# fake callbacks or accept their pretend READY as proof of peer authentication.
SIMULATOR="app/src/test/java/com/edenpractical/comms/transport/ScriptedNearbyTransportAdapter.kt"
test -f "$SIMULATOR" || { echo "FAIL: M2p test-only simulator missing" >&2; exit 1; }
if find "$MAIN" -name 'ScriptedNearbyTransportAdapter.kt' -print -quit | grep -q .; then
  echo "FAIL: M2p virtual adapter moved into distributable Android sources" >&2
  exit 1
fi
if grep -RFn --include='*.kt' 'ScriptedNearbyTransportAdapter' "$MAIN"; then
  echo "FAIL: live app path refers to M2p fake transport events" >&2
  exit 1
fi

# M2s is a real chat UI layout, but ONLY a local presentation sandbox.
# Never let it silently gain transport/crypto APIs, persistent chat storage,
# saveable Compose transcript state, or live peer authority. Future reviewed
# network integration requires explicitly replacing this guard in its own PR.
CHAT="$MAIN/com/edenpractical/comms/chat"
test -f "$CHAT/LocalChatPreview.kt"
test -f "$CHAT/LocalChatPreviewScreen.kt"
if grep -REn --include='*.kt' \
  '^(import com\.edenpractical\.comms\.(security|transport|protocol)\.|import java\.net\.|import android\.bluetooth\.|import android\.net\.wifi\.)' "$CHAT"; then
  echo "FAIL: M2s local preview imported network, protocol or authentication code" >&2
  exit 1
fi
if grep -REn --include='*.kt' \
  '(rememberSaveable|SharedPreferences|openFileOutput|SQLiteDatabase|sendPacket\(|Socket\(|DataStore|LocalTlsIdentity)' "$CHAT"; then
  echo "FAIL: M2s text preview gained persistence or network send capability" >&2
  exit 1
fi

# M2t is a presentation vocabulary and TEST-ONLY hypothetical delivery
# reducer. A fake peer-app acknowledgment is never production authorization.
STATUS_GUIDE="$CHAT/ConversationStatusGuide.kt"
TEST_REDUCER="app/src/test/java/com/edenpractical/comms/chat/ReviewOnlyMessageDelivery.kt"
test -f "$STATUS_GUIDE" && test -f "$TEST_REDUCER"
if find "$MAIN" -name 'ReviewOnlyMessageDelivery.kt' -print -quit | grep -q .; then
  echo "FAIL: unreviewed delivery simulator moved into shipped Android sources" >&2
  exit 1
fi
if grep -RFn --include='*.kt' 'ReviewOnlyMessageDelivery' "$MAIN"; then
  echo "FAIL: production app references simulated message delivery evidence" >&2
  exit 1
fi
if ! grep -Fq 'currentLiveSteps: Set<Step> = setOf(Step.LOCAL_PREVIEW)' "$STATUS_GUIDE"; then
  echo "FAIL: unreviewed chat status was exposed as a live message state" >&2
  exit 1
fi

# M3e/F1 open *debug-only* text and peer-app ACK on the current pinned TLS test,
# after BOTH approvals + BOTH final confirmations. The bounded Unicode
# candidate remains unreviewed and is referenced ONLY by the test packet
# envelope; never by release chat UI, generic transport, or other code.
CHAT_WIRE="$MAIN/com/edenpractical/comms/protocol/ProposedChatWireCodec.kt"
TEST_ENVELOPE="$MAIN/com/edenpractical/comms/protocol/TestPacketCodec.kt"
DEBUG_LAN="$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt"
test -f "$CHAT_WIRE" && test -f "$TEST_ENVELOPE"
if grep -RFn --include='*.kt' --exclude='ProposedChatWireCodec.kt' \
  --exclude='TestPacketCodec.kt' 'ProposedChatWireCodec' "$MAIN"; then
  echo "FAIL: unreviewed text packet escaped the explicitly scoped debug envelope" >&2
  exit 1
fi
grep -Fq 'fun debugText(messageId: Long, content: String): Packet' "$TEST_ENVELOPE"
# F1 review-scoped exception: the exact empty-body ECTX ACK is admitted in
# the existing authenticated debug TLS session, never in normal release.
grep -Fq 'fun debugAck(messageId: Long): Packet' "$TEST_ENVELOPE"
grep -Fq 'ProposedChatWireCodec.Packet.Ack(packet.messageId)' "$TEST_ENVELOPE"
grep -Fq 'is ProposedChatWireCodec.Packet.Text -> Packet(' "$TEST_ENVELOPE"
grep -Fq 'is ProposedChatWireCodec.Packet.Ack -> Packet(' "$TEST_ENVELOPE"
grep -Fq 'TestPacketCodec.Type.DEBUG_ACK -> {' "$DEBUG_LAN"
grep -Fq 'peerAckLedger.acknowledge(' "$DEBUG_LAN"
grep -Fq 'approvalGate.maySendPreset()' "$DEBUG_LAN"
grep -Fq 'queuePeerAppAck(packet.messageId)' "$DEBUG_LAN"
grep -Fq 'sendPacket(TestPacketCodec.debugAck(id))' "$DEBUG_LAN"
grep -Fq 'peerAckLedger.close()' "$DEBUG_LAN"
test -f "$MAIN/com/edenpractical/comms/transport/DebugPeerAckLedger.kt"
if grep -RFn --include='*.kt' 'TestPacketCodec.debugAck(' "$MAIN" | \
  grep -vE 'LanDemoSession.kt'; then
  echo "FAIL: F1 debug ACK escaped restricted TLS debug LAN session" >&2
  exit 1
fi
grep -Fq 'Type.DEBUG_TEXT ->' "$DEBUG_LAN" 2>/dev/null || \
  grep -Fq 'TestPacketCodec.Type.DEBUG_TEXT ->' "$DEBUG_LAN"
grep -Fq 'if (!approvalGate.maySendPreset()) return TextSendResult.AWAITING_APPROVAL' "$DEBUG_LAN"
grep -Fq 'debugTextReceive.accept(' "$DEBUG_LAN"
grep -Fq 'if (packet.text.isEmpty() ||' "$DEBUG_LAN"
grep -Fq 'FLAG_DEBUGGABLE' "$DEBUG_LAN"
if grep -RFn --include='*.kt' 'sendDebugText(' "$MAIN" | \
  grep -vE 'LanDemoSession.kt|LocalPairingTestScreen.kt'; then
  echo "FAIL: experimental user text was referenced outside debug test screen/socket" >&2
  exit 1
fi

# M2v experimental APPROVE/DECLINE is restricted to the existing
# debuggable four-preset LAN test and its on-screen human decision UI.
# A successful debug approval does NOT unlock normal text or voice and
# must not reference the production authorization policy.
if ! grep -Fq 'FLAG_DEBUGGABLE' "$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt"; then
  echo "FAIL: experimental debug consent transport lost its debuggable-only guard" >&2
  exit 1
fi
for FILE in $(grep -Rl --include='*.kt' 'DebugApprovalGate' "$MAIN" || true); do
  case "$FILE" in
    "$MAIN/com/edenpractical/comms/transport/DebugApprovalGate.kt"|\
    "$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt"|\
    "$MAIN/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt") ;;
    *) echo "FAIL: unreviewed debug approval model referenced outside experiment: $FILE" >&2
       exit 1 ;;
  esac
done
if grep -REn --include='*.kt' 'ProposedChatWireCodec|ProductionSessionAuthorization|ReviewOnlyBilateralApproval' \
  "$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt" \
  "$MAIN/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt"; then
  echo "FAIL: debug approval test incorrectly linked to general messaging or policy model" >&2
  exit 1
fi

# M2w RFC 9266 interoperability verification remains inside the debuggable
# pinned-TLS fixed-phrase test. No production path may use the exporter match
# as a credential, mutual consent, arbitrary-text permission or encryption key.
SECURITY="$MAIN/com/edenpractical/comms/security"
test -f "$SECURITY/DebugTlsChannelAgreement.kt"
test -f "$SECURITY/DebugExporterBindingFrame.kt"
if ! grep -Fq 'debugConsentCommitment = DebugTlsChannelAgreement.verify(' \
  "$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt"; then
  echo "FAIL: debug test no longer verifies matching TLS exporter before READY" >&2
  exit 1
fi
for SYMBOL in TlsExporterReadiness DebugTlsChannelAgreement DebugExporterBindingFrame; do
  for FILE in $(grep -Rl --include='*.kt' "$SYMBOL" "$MAIN" || true); do
    case "$FILE" in
      "$SECURITY/TlsExporterReadiness.kt"|\
      "$SECURITY/DebugTlsChannelAgreement.kt"|\
      "$SECURITY/DebugExporterBindingFrame.kt"|\
      "$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt") ;;
      *) echo "FAIL: debug exporter symbol referenced outside TLS debug boundary: $FILE" >&2
         exit 1 ;;
    esac
  done
done
if grep -REn --include='*.kt' \
  'DebugTlsChannelAgreement|DebugExporterBindingFrame' "$CHAT"; then
  echo "FAIL: chat preview tried to use experimental exporter as messaging approval" >&2
  exit 1
fi

# M2x requires actual debug control packets to contain a verified, current
# QR+challenge+TLS-exporter transcript commitment. The codec cannot use the
# legacy 3-byte generic APPROVE/DECLINE wire shape.
CONSENT_SOURCE="$MAIN/com/edenpractical/comms/security/DebugApprovalCommitment.kt"
TEST_CODEC="$MAIN/com/edenpractical/comms/protocol/TestPacketCodec.kt"
DEBUG_LAN="$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt"
test -f "$CONSENT_SOURCE"
grep -Fq 'val sessionCommitment: List<Byte>' "$TEST_CODEC"
grep -Fq 'COMMITMENT_BYTES = 32' "$TEST_CODEC"
grep -Fq 'requireCurrentConsentCommitment(packet)' "$DEBUG_LAN"
grep -Fq 'TestPacketCodec.approve(commitment)' "$DEBUG_LAN"
grep -Fq 'TestPacketCodec.decline(commitment)' "$DEBUG_LAN"
for SYMBOL in DebugApprovalCommitment SessionProofChallenges; do
  for FILE in $(grep -Rl --include='*.kt' "$SYMBOL" "$MAIN" || true); do
    case "$FILE" in
      "$SECURITY/SessionProofExchange.kt"|\
      "$SECURITY/DebugApprovalCommitment.kt"|\
      "$SECURITY/DebugTlsChannelAgreement.kt"|\
      "$DEBUG_LAN") ;;
      *) echo "FAIL: debug consent transcript used outside approved test boundary: $FILE" >&2
         exit 1 ;;
    esac
  done
done

# M2y requires a separate explicit confirmation on both debug peers.
# Merely receiving two approvals must never unlock a preset phrase; all four
# events (two human approvals + two final confirmations) are required.
DEBUG_GATE="$MAIN/com/edenpractical/comms/transport/DebugApprovalGate.kt"
grep -Fq 'private var localConfirmationWritten = false' "$DEBUG_GATE"
grep -Fq 'private var peerConfirmationObserved = false' "$DEBUG_GATE"
grep -Fq 'fun maySendPreset(): Boolean = current() == State.CONFIRMED' "$DEBUG_GATE"
grep -Fq 'fun peerConfirmationObserved(): State' "$DEBUG_GATE"
grep -Fq 'fun localConfirmationWritten(): State' "$DEBUG_GATE"
grep -Fq 'fun confirm(commitment: ByteArray): Packet' "$TEST_CODEC"
grep -Fq 'TestPacketCodec.Type.CONFIRM ->' "$DEBUG_LAN"
grep -Fq 'sendPacket(TestPacketCodec.confirm(commitment))' "$DEBUG_LAN"
grep -Fq 'DebugApprovalGate.State.CONFIRMED ->' "$DEBUG_LAN"
grep -Fq 'event.approvalState != DebugApprovalGate.State.CONFIRMED' \
  "$MAIN/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt"

# M3k: the M3j discovery contract may ONLY be integrated with the isolated
# Bluetooth Classic debug activity under src/debug. It must still never leak
# into main/release UI, transport or production message/voice authorization.
DISCOVERY_MODEL="$MAIN/com/edenpractical/comms/transport/NearbyDiscoverySession.kt"
DISCOVERY_COPY="$MAIN/com/edenpractical/comms/model/NearbyDiscoveryPolicy.kt"
test -f "$DISCOVERY_MODEL" && test -f "$DISCOVERY_COPY"
if grep -RFn --include='*.kt' --exclude='NearbyDiscoverySession.kt' \
  'NearbyDiscoverySession' "$MAIN"; then
  echo "FAIL: model-only discovery state reached a live app codepath" >&2
  exit 1
fi
if grep -REn '^(import android\\.|import java\\.net\\.|import javax\\.net\\.)' \
  "$DISCOVERY_MODEL"; then
  echo "FAIL: future discovery state opened Android/platform networking dependencies" >&2
  exit 1
fi
grep -Fq 'const val MAX_SCAN_MS = 30_000L' "$DISCOVERY_MODEL"
grep -Fq 'const val MAX_OBSERVATIONS = 32' "$DISCOVERY_MODEL"
grep -Fq 'fun onBackground(attempt: Attempt): Boolean' "$DISCOVERY_MODEL"
grep -Fq 'fun permissionRevoked(attempt: Attempt): Boolean' "$DISCOVERY_MODEL"
grep -Fq 'fun resetAfterStop(): Boolean' "$DISCOVERY_MODEL"
grep -Fq 'Automatic Eden Comms peer discovery is OFF and NOT IMPLEMENTED' "$DISCOVERY_COPY"

# Bluetooth activity and Android Nearby devices permission are debug-only.
BT_DEBUG="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothDiscoveryActivity.kt"
BT_MANIFEST="app/src/debug/AndroidManifest.xml"
test -f "$BT_DEBUG" && test -f "$BT_MANIFEST"
grep -Fq 'class DebugBluetoothDiscoveryActivity' "$BT_DEBUG"
grep -Fq 'Manifest.permission.BLUETOOTH_SCAN' "$BT_DEBUG"
grep -Fq 'NearbyDiscoverySession()' "$BT_DEBUG"
grep -Fq 'override fun onStop()' "$BT_DEBUG"
grep -Fq 'cancelDiscovery()' "$BT_DEBUG"
grep -Fq 'registerForActivityResult(' "$BT_DEBUG"
grep -Fq 'android:exported="false"' "$BT_MANIFEST"
if grep -REn 'android.permission.(BLUETOOTH|ACCESS_FINE_LOCATION|NEARBY_WIFI_DEVICES|RECORD_AUDIO)' \
  app/src/main/AndroidManifest.xml; then
  echo "FAIL: debug-only Bluetooth permission leaked to release" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugBluetoothDiscoveryActivity.kt' -print -quit | grep -q .; then
  echo "FAIL: Bluetooth inquiry activity was moved into release source" >&2
  exit 1
fi
# M3l may temporarily hold only opaque Bluetooth device OBJECTS in the
# debug-only activity. Source must NEVER extract identifiers, persist the
# unverified results, open a Bluetooth socket, or expand release permissions.
BT_LEDGER="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothCandidateLedger.kt"
test -f "$BT_LEDGER"
grep -Fq 'MAX_CANDIDATES = 12' "$BT_LEDGER"
grep -Fq 'token !== current' "$BT_LEDGER"
grep -Fq 'fun select(current: Token, ordinal: Int): Boolean' "$BT_LEDGER"
grep -Fq 'candidateLedger.clear()' "$BT_DEBUG"
grep -Fq 'stopHardware()' "$BT_DEBUG"
if grep -REn '(getAddress\(|getName\(|createRfcommSocket|createBond\(|bondedDevices|SharedPreferences|openFileOutput\()' \
  "$BT_DEBUG" "$BT_LEDGER"; then
  echo "FAIL: debug Bluetooth selection accessed peer identity, storage or socket" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugBluetoothCandidateLedger.kt' -print -quit | grep -q .; then
  echo "FAIL: unverified Bluetooth candidate ledger entered release source" >&2
  exit 1
fi
# M3m RFCOMM is an OS socket experiment, STRICTLY in debug sources.
# No payload stream, unreviewed protocol framing, insecure fallback, secrets,
# or release Bluetooth permissions are allowed. One activity is user-controlled.
BT_RFCOMM="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugRfcommLinkController.kt"
BT_GATE="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugRfcommAttemptGate.kt"
test -f "$BT_RFCOMM" && test -f "$BT_GATE"
grep -Fq 'listenUsingRfcommWithServiceRecord(' "$BT_RFCOMM"
grep -Fq 'device.createRfcommSocketToServiceRecord(SERVICE_UUID)' "$BT_RFCOMM"
grep -Fq 'created.accept(HOST_WAIT_MS.toInt())' "$BT_RFCOMM"
grep -Fq 'closeQuietly(oldServer)' "$BT_RFCOMM"
grep -Fq 'closeQuietly(oldSocket)' "$BT_RFCOMM"
grep -Fq 'gate.opened(' "$BT_RFCOMM"
grep -Fq 'Manifest.permission.BLUETOOTH_CONNECT' "$BT_DEBUG"
grep -Fq 'Manifest.permission.BLUETOOTH_ADVERTISE' "$BT_DEBUG"
grep -Fq 'Android' "$BT_DEBUG"
for PERMISSION in BLUETOOTH_SCAN BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE; do
  grep -Fq "android.permission.$PERMISSION" "$BT_MANIFEST"
done
if grep -REn '(getInputStream\(|getOutputStream\(|listenUsingInsecureRfcomm|createInsecureRfcomm|DataOutputStream|sendDebugText\(|TestPacketCodec)' \
  "$BT_RFCOMM" "$BT_GATE"; then
  echo "FAIL: Bluetooth debug RFCOMM socket acquired payload streams or insecure path" >&2
  exit 1
fi
if find "$MAIN" \( -name 'DebugRfcommLinkController.kt' -o -name 'DebugRfcommAttemptGate.kt' \) -print -quit | grep -q .; then
  echo "FAIL: unverified Bluetooth link entered release source" >&2
  exit 1
fi
# M3n-A introduces in-person reciprocal QR metadata, NOT TLS or general
# messaging. Both roles must complete fresh local QR preparation before the
# existing debug-only unverified RFCOMM socket can be attempted.
BT_QR_CODEC="app/src/debug/java/com/edenpractical/comms/bluetooth/BluetoothQrPairingCodec.kt"
BT_QR_COORD="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothQrCoordinator.kt"
BT_QR_PANEL="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothQrPanel.kt"
for FILE in "$BT_QR_CODEC" "$BT_QR_COORD" "$BT_QR_PANEL"; do test -f "$FILE"; done
grep -Fq 'HOST_TAG = "ECBH1"' "$BT_QR_CODEC"
grep -Fq 'JOIN_TAG = "ECBJ1"' "$BT_QR_CODEC"
grep -Fq 'MAX_AGE_SECONDS = 300L' "$BT_QR_CODEC"
grep -Fq 'SessionProofBinding(hostPin, joinPin, hostNonce, joinNonce)' "$BT_QR_CODEC"
grep -Fq 'qrCoordinator.readyForDebugLink(hostRole = true)' "$BT_DEBUG"
grep -Fq 'qrCoordinator.readyForDebugLink(hostRole = false)' "$BT_DEBUG"
grep -Fq 'qrCoordinator.clear()' "$BT_DEBUG"
grep -Fq 'LocalPairingQrScanner(' "$BT_QR_PANEL"
grep -Fq 'LocalPairingQrDisplay(' "$BT_QR_PANEL"
if grep -REn '(getInputStream\(|getOutputStream\(|listenUsingInsecureRfcomm|createInsecureRfcomm|sendDebugText\(|createBond\(|SharedPreferences|openFileOutput\()' \
  "$BT_RFCOMM" "$BT_GATE" "$BT_QR_CODEC" "$BT_QR_COORD" "$BT_QR_PANEL"; then
  echo "FAIL: unauthenticated Bluetooth QR or RFCOMM path accessed message streams, storage, or insecure fallback" >&2
  exit 1
fi
if find "$MAIN" \( -name 'BluetoothQrPairingCodec.kt' -o -name 'DebugBluetoothQrCoordinator.kt' -o -name 'DebugBluetoothQrPanel.kt' \) -print -quit | grep -q .; then
  echo "FAIL: QR-only Bluetooth debug preparation entered release source" >&2
  exit 1
fi
# M3n-B1: only this DEBUG TLS engine driver may consume RFCOMM streams.
# The flow MUST enforce exact Keystore-backed pinned TLSv1.3 plus exporter
# availability, and MUST close immediately with no application message API.
BT_TLS_ENGINE="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugTlsEngineHandshake.kt"
BT_TLS_PROBE="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugRfcommTlsProbe.kt"
BT_TLS_INPUT="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothTlsProbeInput.kt"
for FILE in "$BT_TLS_ENGINE" "$BT_TLS_PROBE" "$BT_TLS_INPUT"; do test -f "$FILE"; done
# F2-K: the real TLS diagnostic call now has a read-only progress callback
# while preserving the unchanged human approval callback (checked below).
grep -Fq 'val completion = DebugRfcommTlsProbe.run(' "$BT_RFCOMM"
grep -Fq 'onVerifiedStage = { step -> verified(token, step) }' "$BT_RFCOMM"
grep -Fq ') { decision ->' "$BT_RFCOMM"
grep -Fq 'DebugTlsEngineHandshake.negotiate(' "$BT_TLS_PROBE"
grep -Fq 'PinnedTlsPeer.requirePinnedTls13(engine.session, expectedPin)' "$BT_TLS_PROBE"
grep -Fq 'SSLEngines.isSupportedEngine(engine)' "$BT_TLS_PROBE"
grep -Fq 'SSLEngines.exportKeyingMaterial(' "$BT_TLS_PROBE"
grep -Fq 'MAX_RECORD_BODY = 18_432' "$BT_TLS_ENGINE"
grep -Fq 'MAX_STEPS = 256' "$BT_TLS_ENGINE"
grep -Fq 'result.bytesProduced() != 0' "$BT_TLS_ENGINE"
grep -Fq 'fun unconnectedEngine(client: Boolean): SSLEngine' app/src/main/java/com/edenpractical/comms/security/Tls13PeerContext.kt
grep -Fq 'if (!client) needClientAuth = true' app/src/main/java/com/edenpractical/comms/security/Tls13PeerContext.kt
grep -Fq 'qrCoordinator.newTlsProbeInput(hostRole = true)' "$BT_DEBUG"
grep -Fq 'qrCoordinator.newTlsProbeInput(hostRole = false)' "$BT_DEBUG"
if grep -REn '(TestPacketCodec|sendDebugText\(|listenUsingInsecureRfcomm|createInsecureRfcomm|onAuthenticatedRemoteApproval\(|ProductionSessionAuthorization)' \
 "$BT_RFCOMM" "$BT_TLS_PROBE" "$BT_TLS_ENGINE" "$BT_TLS_INPUT"; then
  echo "FAIL: unreviewed Bluetooth text/consent or insecure fallback in TLS probe" >&2
  exit 1
fi
if find "$MAIN" \( -name 'DebugRfcommTlsProbe.kt' -o -name 'DebugTlsEngineHandshake.kt' -o -name 'DebugBluetoothTlsProbeInput.kt' \) -print -quit | grep -q .; then
  echo "FAIL: debug-only Bluetooth TLS handshake probe entered release code" >&2
  exit 1
fi
# M3n-B2: encrypted fixed-width, phase-ordered QR proof, exporter agreement,
# and final transcript comparison precede M3n-C1 consent; all RFCOMM sockets
# still close, including after a successful approval diagnostic.
BT_CONTROL="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugTlsControlTransport.kt"
BT_COMMIT="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothCommitmentFrame.kt"
test -f "$BT_CONTROL" && test -f "$BT_COMMIT"
grep -Fq 'DebugTlsControlTransport(engine, wireIn, wireOut)' "$BT_TLS_PROBE"
grep -Fq 'SessionProofExchange.runHost(' "$BT_TLS_PROBE"
grep -Fq 'SessionProofExchange.runJoin(' "$BT_TLS_PROBE"
grep -Fq 'DebugExporterBindingFrame.exchange(' "$BT_TLS_PROBE"
grep -Fq 'DebugApprovalCommitment.derive(' "$BT_TLS_PROBE"
grep -Fq 'DebugBluetoothCommitmentFrame.exchange(' "$BT_TLS_PROBE"
grep -Fq 'request.freshBinding()' "$BT_TLS_PROBE"
grep -Fq 'DebugTlsEngineHandshake.readRecord(wireInput)' "$BT_CONTROL"
grep -Fq 'engine.unwrap(ByteBuffer.wrap(encoded), clear)' "$BT_CONTROL"
grep -Fq 'engine.wrap(ByteBuffer.wrap(b, off, len), encrypted)' "$BT_CONTROL"
# F2-C moves byte-accounting and live-provider closure checks into a
# dedicated debug-only fail-closed gate; do not silently drop those checks.
BT_PROVIDER="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugTlsProviderReadiness.kt"
test -f "$BT_PROVIDER"
grep -Fq 'DebugTlsProviderReadiness.requireExactControlResult(' "$BT_CONTROL"
grep -Fq 'DebugTlsProviderReadiness.requireControlReady(engine)' "$BT_CONTROL"
grep -Fq 'DebugTlsProviderReadiness.requireControlReady(engine)' "$BT_TLS_PROBE"
grep -Fq 'result.bytesConsumed() != consumed' "$BT_PROVIDER"
grep -Fq 'result.bytesProduced() != produced' "$BT_PROVIDER"
grep -Fq 'engine.isInboundDone' "$BT_PROVIDER"
grep -Fq 'engine.isOutboundDone' "$BT_PROVIDER"
grep -Fq 'protocol != "TLSv1.3"' "$BT_PROVIDER"
if grep -REn '(TestPacketCodec|sendDebugText\(|ProductionSessionAuthorization|SharedPreferences|DataStore|openFileOutput\()' "$BT_PROVIDER"; then
  echo "FAIL: F2-C TLS provider gate gained messaging, release trust or storage" >&2
  exit 1
fi
grep -Fq 'QR_PROOF(PROOF_BYTES)' "$BT_CONTROL"
grep -Fq 'EXPORTER(DebugExporterBindingFrame.RECORD_BYTES)' "$BT_CONTROL"
grep -Fq 'COMMITMENT(COMMIT_BYTES)' "$BT_CONTROL"
grep -Fq 'MessageDigest.isEqual(incoming.copyOfRange(6, RECORD_BYTES), local)' "$BT_COMMIT"
grep -Fq 'DebugRfcommAttemptGate.End.CONSENT_CONFIRMED' "$BT_RFCOMM"
if grep -REn '(TestPacketCodec|sendDebugText\(|listenUsingInsecureRfcomm|createInsecureRfcomm|onAuthenticatedRemoteApproval\(|SharedPreferences|DataStore|openFileOutput\()' \
 "$BT_RFCOMM" "$BT_TLS_PROBE" "$BT_TLS_ENGINE" "$BT_TLS_INPUT" "$BT_CONTROL" "$BT_COMMIT"; then
  echo "FAIL: M3n-B2 control channel included text, storage or insecure Bluetooth" >&2
  exit 1
fi
if find "$MAIN" \( -name 'DebugTlsControlTransport.kt' -o -name 'DebugBluetoothCommitmentFrame.kt' \) -print -quit | grep -q .; then
  echo "FAIL: encrypted debug Bluetooth control entered release source" >&2
  exit 1
fi
# M3n-C1: real two-person consent on the verified M3n-B2 channel only.
# One explicit tap EACH (APPROVE or DECLINE), followed by two encrypted
# FINAL CONFIRM records. No Bluetooth text/voice or production trust.
BT_CONSENT_DECISION="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothConsentDecision.kt"
BT_CONSENT_FRAME="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothConsentFrame.kt"
BT_CONSENT_EXCHANGE="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothConsentExchange.kt"
for FILE in "$BT_CONSENT_DECISION" "$BT_CONSENT_FRAME" "$BT_CONSENT_EXCHANGE"; do
  test -f "$FILE"
done
grep -Fq 'DebugBluetoothConsentExchange.run(' "$BT_TLS_PROBE"
grep -Fq 'DebugBluetoothCommitmentFrame.exchange(' "$BT_TLS_PROBE"
grep -Fq 'onApprovalRequired = { onConsentRequired(consent) }' "$BT_TLS_PROBE"
grep -Fq 'DebugApprovalGate()' "$BT_CONSENT_EXCHANGE"
grep -Fq 'DebugBluetoothConsentFrame.Type.APPROVE' "$BT_CONSENT_EXCHANGE"
grep -Fq 'DebugBluetoothConsentFrame.Type.DECLINE' "$BT_CONSENT_EXCHANGE"
grep -Fq 'DebugBluetoothConsentFrame.Type.CONFIRM' "$BT_CONSENT_EXCHANGE"
grep -Fq 'gate.mayWriteConfirmation()' "$BT_CONSENT_EXCHANGE"
grep -Fq 'DebugApprovalGate.State.CONFIRMED' "$BT_CONSENT_EXCHANGE"
grep -Fq 'transport.advance(' "$BT_CONSENT_EXCHANGE"
grep -Fq 'APPROVAL(DebugBluetoothConsentFrame.RECORD_BYTES)' "$BT_CONTROL"
grep -Fq 'CONFIRM(DebugBluetoothConsentFrame.RECORD_BYTES)' "$BT_CONTROL"
grep -Fq 'decisionLatch.await(maxWaitMs, TimeUnit.MILLISECONDS)' "$BT_CONSENT_DECISION"
grep -Fq 'oldConsent?.close()' "$BT_RFCOMM"
grep -Fq 'fun approve(): Boolean' "$BT_RFCOMM"
grep -Fq 'fun decline(): Boolean' "$BT_RFCOMM"
grep -Fq 'approvalPending &&' "$BT_DEBUG"
grep -Fq 'APPROVE THIS TEST SESSION' "$BT_DEBUG"
grep -Fq 'DECLINE AND CLOSE SESSION' "$BT_DEBUG"
grep -Fq 'DebugRfcommAttemptGate.End.CONSENT_CONFIRMED' "$BT_RFCOMM"
grep -Fq 'DebugRfcommAttemptGate.End.CONSENT_DECLINED' "$BT_RFCOMM"
if grep -REn '(TestPacketCodec|sendDebugText\(|listenUsingInsecureRfcomm|createInsecureRfcomm|ProductionSessionAuthorization|SharedPreferences|DataStore|openFileOutput\(|AudioRecord|MediaRecorder|onAuthenticatedRemoteApproval\()' \
 "$BT_RFCOMM" "$BT_TLS_PROBE" "$BT_CONTROL" "$BT_CONSENT_FRAME" "$BT_CONSENT_EXCHANGE" "$BT_CONSENT_DECISION"; then
  echo "FAIL: M3n-C1 debug approval exposed app messages, audio, persistence or release trust" >&2
  exit 1
fi
if find "$MAIN" \( -name 'DebugBluetoothConsentDecision.kt' -o -name 'DebugBluetoothConsentFrame.kt' -o -name 'DebugBluetoothConsentExchange.kt' \) -print -quit | grep -q .; then
  echo "FAIL: debug-only Bluetooth user approval entered release sources" >&2
  exit 1
fi
# F2-D is JVM-unit-test-only *real TLS* evidence, not radio interoperability
# and never a runtime key generator or an authority to open Bluetooth chat.
F2D_TEST="app/src/test/java/com/edenpractical/comms/bluetooth/DebugPairedTlsEngineInteropTest.kt"
test -f "$F2D_TEST"
grep -Fq 'Tls13PeerContext(hostIdentity.manager, hostExpected).unconnectedEngine(client = false)' "$F2D_TEST"
grep -Fq 'Tls13PeerContext(joinIdentity.manager, joinExpected).unconnectedEngine(client = true)' "$F2D_TEST"
grep -Fq 'wrongOutOfBandPeerPinCannotCompleteMutualTlsHandshake()' "$F2D_TEST"
grep -Fq 'Files.createTempDirectory("eden-f2d-test-only-")' "$F2D_TEST"
grep -Fq 'Files.deleteIfExists(' "$F2D_TEST"
if find app/src/main app/src/debug -name 'DebugPairedTlsEngineInteropTest.kt' -print -quit | grep -q .; then
  echo "FAIL: F2-D ephemeral certificate fixture entered a distributable source set" >&2
  exit 1
fi
if grep -RFn --include='*.kt' 'DebugPairedTlsEngineInteropTest' app/src/main app/src/debug; then
  echo "FAIL: production or debug runtime references F2-D JVM-only TLS fixture" >&2
  exit 1
fi

# F2-E: completion of BOTH encrypted confirmations may create a one-use
# diagnostic receipt. It is not a retained authenticated link or send grant.
# The same controller token must consume it and immediately close the socket.
BT_RECEIPT="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothProbeCompletion.kt"
test -f "$BT_RECEIPT"
grep -Fq 'return DebugBluetoothProbeCompletion.afterVerifiedDiagnostic(' "$BT_TLS_PROBE"
grep -Fq 'val atEnd = request.freshBinding()' "$BT_TLS_PROBE"
# F2-K permits read-only stage visibility only when the real worker still
# has BOTH the actual current-token stage fence and original human-consent
# callback. No runtime handoff, plaintext user message or socket retention.
grep -Fq 'val completion = DebugRfcommTlsProbe.run(' "$BT_RFCOMM"
grep -Fq 'created, request,' "$BT_RFCOMM"
grep -Fq 'onVerifiedStage = { step -> verified(token, step) }' "$BT_RFCOMM"
grep -Fq ') { decision ->' "$BT_RFCOMM"
grep -Fq 'if (active !== token || !gate.isLive(token) ||' "$BT_RFCOMM"
grep -Fq '(lastVerifiedStage?.step ?: 0) >= step.step' "$BT_RFCOMM"
grep -Fq 'onConsentRequired(consent)' "$BT_TLS_PROBE"
grep -Fq 'onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)' "$BT_TLS_PROBE"
grep -Fq 'active === token' "$BT_RFCOMM"
grep -Fq 'gate.snapshot().phase == DebugRfcommAttemptGate.Phase.LINK_OPEN' "$BT_RFCOMM"
grep -Fq 'completion.acknowledgeOnce(' "$BT_RFCOMM"
grep -Fq 'completion.close()' "$BT_RFCOMM"
grep -Fq 'MAX_HANDOFF_AGE_SECONDS = 3L' "$BT_RECEIPT"
grep -Fq 'if (consumed || closed) return false' "$BT_RECEIPT"
grep -Fq 'consumed = true' "$BT_RECEIPT"
grep -Fq 'closeQuietly(oldSocket)' "$BT_RFCOMM"
if find "$MAIN" -name 'DebugBluetoothProbeCompletion.kt' -print -quit | grep -q .; then
  echo "FAIL: F2-E diagnostic-only receipt moved into release source" >&2
  exit 1
fi
if grep -REn '(sendDebugText\(|TestPacketCodec|BluetoothSocket\(|SharedPreferences|openFileOutput\(|ProductionSessionAuthorization)' "$BT_RECEIPT"; then
  echo "FAIL: F2-E receipt gained a socket, message, persistence or production authorization" >&2
  exit 1
fi

# F2-F: future-only Bluetooth TEXT/ACK codec and ledger must not be
# wired to RFCOMM, release code, or credentials. The actual socket still
# closes on completion. Only independently reviewed TLS may carry messages.
BT_TEXT_FRAME="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothTextFrameCodec.kt"
BT_TEXT_TEST="app/src/test/java/com/edenpractical/comms/bluetooth/DebugBluetoothTextFrameCodecTest.kt"
test -f "$BT_TEXT_FRAME" && test -f "$BT_TEXT_TEST"
grep -Fq 'MAX_TEXT_BYTES = 512' "$BT_TEXT_FRAME"
grep -Fq 'MAX_MESSAGES_PER_DIRECTION = 128' "$BT_TEXT_FRAME"
grep -Fq 'MAX_PENDING = 8' "$BT_TEXT_FRAME"
grep -Fq 'bytes.size == HEADER_BYTES + bodyLength' "$BT_TEXT_FRAME"
grep -Fq 'onMalformedInput(CodingErrorAction.REPORT)' "$BT_TEXT_FRAME"
grep -Fq 'pending.remove(frame.messageId)' "$BT_TEXT_FRAME"
grep -Fq 'frame.messageId == nextIncoming' "$BT_TEXT_FRAME"
# Use literal checks for concrete APIs/imports. Do not reject a comment
# explaining that this model has NO SSLEngine or BluetoothSocket access.
for forbidden in 'import android.bluetooth.' 'import android.net.' \
  'import java.net.' 'import java.io.' 'import javax.net.ssl.' \
  'BluetoothSocket(' 'SSLEngine(' 'TestPacketCodec' 'sendDebugText(' \
  'AudioRecord' 'SharedPreferences' 'openFileOutput(' \
  'ProductionSessionAuthorization'; do
  if grep -Fq "$forbidden" "$BT_TEXT_FRAME"; then
    echo "FAIL: F2-F future-only frame model gained live socket, trust, audio or persistence" >&2
    exit 1
  fi
done
if grep -Fq 'DebugBluetoothTextFrameCodec' "$BT_TLS_PROBE" ||
   grep -Fq 'DebugBluetoothTextLedger' "$BT_TLS_PROBE" ||
   grep -Fq 'DebugBluetoothTextFrameCodec' "$BT_RFCOMM" ||
   grep -Fq 'DebugBluetoothTextLedger' "$BT_RFCOMM"; then
  echo "FAIL: F2-F plaintext frames were wired into unreviewed live RFCOMM" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugBluetoothTextFrameCodec.kt' -print -quit | grep -q .; then
  echo "FAIL: F2-F debug frame prototype entered release source" >&2
  exit 1
fi

# F2-G: real paired TLS TEXT/ACK lab MUST remain isolated from live RFCOMM
# and release. Actual user chat still requires independently reviewed owned
# post-consent TLS session and physical Android provider QA.
BT_TLS_TEXT_LAB="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothFutureTlsTextLab.kt"
BT_TLS_PAIRS="app/src/test/java/com/edenpractical/comms/bluetooth/DebugPairedTlsEngineInteropTest.kt"
test -f "$BT_TLS_TEXT_LAB" && test -f "$BT_TLS_PAIRS"
grep -Fq 'engine.wrap(ByteBuffer.wrap(frame), encrypted)' "$BT_TLS_TEXT_LAB"
grep -Fq 'engine.unwrap(ByteBuffer.wrap(record), plaintext)' "$BT_TLS_TEXT_LAB"
grep -Fq 'DebugTlsRecordBoundary.requireSingleCiphertext(record)' "$BT_TLS_TEXT_LAB"
grep -Fq 'DebugTlsProviderReadiness.requireControlReady(engine)' "$BT_TLS_TEXT_LAB"
grep -Fq 'DebugNearbyApplicationFrame.inspect(frame)' "$BT_TLS_TEXT_LAB"
grep -Fq 'DebugTlsControlTransport.MAX_NO_PLAINTEXT_RECORDS' "$BT_TLS_TEXT_LAB"
grep -Fq 'pairedTlsEnginesExchangeEncryptedTextAndPeerAppAckBothDirections()' "$BT_TLS_PAIRS"
grep -Fq 'pairedTlsLabRejectsMalformedEncryptedRecordAndClosesItself()' "$BT_TLS_PAIRS"
if grep -Fq 'DebugBluetoothFutureTlsTextLab' "$BT_TLS_PROBE" ||
   grep -Fq 'DebugBluetoothFutureTlsTextLab' "$BT_RFCOMM"; then
  echo "FAIL: F2-G future encrypted TEXT lab entered live RFCOMM diagnostic" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugBluetoothFutureTlsTextLab.kt' -print -quit | grep -q .; then
  echo "FAIL: F2-G future encrypted TEXT lab entered release code" >&2
  exit 1
fi
for forbidden in 'import android.bluetooth.' 'import android.net.' \
  'import java.net.' 'BluetoothSocket(' 'TestPacketCodec' \
  'sendDebugText(' 'ProductionSessionAuthorization' 'SharedPreferences' \
  'openFileOutput(' 'AudioRecord'; do
  if grep -Fq "$forbidden" "$BT_TLS_TEXT_LAB"; then
    echo "FAIL: F2-G lab acquired radio, production text, storage or audio privileges" >&2
    exit 1
  fi
done

# F2-H: future-only resource owner closes a one-shot simulated link,
# never claims actual RFCOMM or authenticated send/receive capabilities.
BT_FUTURE_OWNER="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothFutureOwnedSession.kt"
BT_FUTURE_BUDGET="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothContinuationBudget.kt"
test -f "$BT_FUTURE_OWNER" && test -f "$BT_FUTURE_BUDGET"
grep -Fq 'elapsedMs < lastObservedMs' "$BT_FUTURE_BUDGET"
grep -Fq 'lastObservedMs = elapsedMs' "$BT_FUTURE_BUDGET"
grep -Fq 'budget.beginObservation(generation, elapsedMs, observations)' "$BT_FUTURE_OWNER"
grep -Fq 'budget.stillObserved(' "$BT_FUTURE_OWNER"
grep -Fq 'if (phase != Phase.OBSERVING || attempt != generation)' "$BT_FUTURE_OWNER"
grep -Fq 'closeQuietly(owned)' "$BT_FUTURE_OWNER"
grep -Fq 'closeQuietly(released)' "$BT_FUTURE_OWNER"
grep -Fq 'competingDisconnectAndDeadlineCallbacksCloseExactlyOnce' \
  'app/src/test/java/com/edenpractical/comms/bluetooth/DebugBluetoothFutureOwnedSessionTest.kt'
if grep -Fq 'DebugBluetoothFutureOwnedSession' "$BT_TLS_PROBE" ||
   grep -Fq 'DebugBluetoothFutureOwnedSession' "$BT_RFCOMM"; then
  echo "FAIL: F2-H future owner wired into live RFCOMM" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugBluetoothFutureOwnedSession.kt' -print -quit | grep -q .; then
  echo "FAIL: F2-H future owner moved to release source" >&2
  exit 1
fi
for forbidden in 'import android.bluetooth.' 'import javax.net.ssl.' \
  'BluetoothSocket' 'SSLEngine' 'DebugBluetoothProbeCompletion' \
  'DebugBluetoothTextFrameCodec' 'TestPacketCodec' 'sendDebugText(' \
  'SharedPreferences' 'ProductionSessionAuthorization' 'AudioRecord'; do
  if grep -Fq "$forbidden" "$BT_FUTURE_OWNER"; then
    echo "FAIL: F2-H future owner gained identity, payload or radio send authority" >&2
    exit 1
  fi
done

# F2-I: role, peer-pin and transcript correlation is necessary, not
# sufficient, for a future owned Bluetooth session. This copy-only context
# must not become a credential, live-RFCOMM handoff or production capability.
BT_FUTURE_CONTEXT="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothFutureSessionContext.kt"
test -f "$BT_FUTURE_CONTEXT"
grep -Fq 'private val pin = expectedPeerPin.copyOf()' "$BT_FUTURE_CONTEXT"
grep -Fq 'private val commitment = transcriptCommitment.copyOf()' "$BT_FUTURE_CONTEXT"
grep -Fq 'MessageDigest.isEqual(local.first, remote.first)' "$BT_FUTURE_CONTEXT"
grep -Fq 'MessageDigest.isEqual(local.second, remote.second)' "$BT_FUTURE_CONTEXT"
grep -Fq 'context?.sameAs(claimedContext) == true' "$BT_FUTURE_OWNER"
grep -Fq 'budget.terminate(generation)' "$BT_FUTURE_OWNER"
grep -Fq 'releasedContext?.close()' "$BT_FUTURE_OWNER"
if grep -Fq 'DebugBluetoothFutureSessionContext' "$BT_TLS_PROBE" ||
   grep -Fq 'DebugBluetoothFutureSessionContext' "$BT_RFCOMM"; then
  echo "FAIL: F2-I unreviewed context was wired into real Bluetooth diagnostic" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugBluetoothFutureSessionContext.kt' -print -quit | grep -q .; then
  echo "FAIL: F2-I future context entered production source" >&2
  exit 1
fi
for forbidden in 'import android.bluetooth.' 'import javax.net.ssl.' \
  'BluetoothSocket' 'SSLEngine' 'sendDebugText(' 'TestPacketCodec' \
  'AudioRecord' 'SharedPreferences' 'ProductionSessionAuthorization'; do
  if grep -Fq "$forbidden" "$BT_FUTURE_CONTEXT"; then
    echo "FAIL: F2-I review context acquired radio, message or authorization APIs" >&2
    exit 1
  fi
done

# F2-Q: real typed debug Bluetooth chat must originate ONLY from the actual
# freshly verified RFCOMM TLS engine AFTER QR/exporter/commitment and BOTH
# encrypted confirmation records. No receipt, caller claim or plaintext send.
# This is not an independent protocol assessment or Android hardware QA.
BT_LIVE_TEXT="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothApprovedTextChannel.kt"
BT_LIVE_UI="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothTextPanel.kt"
for FILE in "$BT_LIVE_TEXT" "$BT_LIVE_UI"; do test -f "$FILE"; done
grep -Fq 'onApprovedChannel(approvedEngine, wireIn, wireOut)' "$BT_TLS_PROBE"
grep -Fq 'onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)' "$BT_TLS_PROBE"
grep -Fq 'transport.clear()' "$BT_TLS_PROBE"
grep -Fq 'onApprovedChannel = { engine, wireInput, wireOutput ->' "$BT_RFCOMM"
grep -Fq 'DebugBluetoothApprovedTextChannel(' "$BT_RFCOMM"
grep -Fq 'isForeground() && hasConnectPermission() && liveText == null' "$BT_RFCOMM"
grep -Fq 'oldText?.close()' "$BT_RFCOMM"
grep -Fq 'closeQuietly(oldSocket)' "$BT_RFCOMM"
grep -Fq 'ArrayBlockingQueue<Pending>(10)' "$BT_LIVE_TEXT"
grep -Fq 'encrypted.transmit(pending.bytes)' "$BT_LIVE_TEXT"
grep -Fq 'encrypted.receive()' "$BT_LIVE_TEXT"
grep -Fq 'ledger.acceptAck(frame)' "$BT_LIVE_TEXT"
grep -Fq 'onTextEvent = { event ->' "$BT_DEBUG"
grep -Fq 'linkController.offerText(draft)' "$BT_DEBUG"
grep -Fq 'chatLines = emptyList()' "$BT_DEBUG"
for FILE in DebugBluetoothApprovedTextChannel.kt DebugBluetoothTextPanel.kt; do
  if find "$MAIN" -name "$FILE" -print -quit | grep -q .; then
    echo "FAIL: F2-Q live Bluetooth test moved into release sources" >&2
    exit 1
  fi
done
if grep -REn '(SharedPreferences|DataStore|openFileOutput\(|AudioRecord|MediaRecorder|ProductionSessionAuthorization|TestPacketCodec)' \
  "$BT_LIVE_TEXT" "$BT_LIVE_UI"; then
  echo "FAIL: F2-Q live Bluetooth channel gained persistence, audio or release authority" >&2
  exit 1
fi

# F3-A/B3 Wi-Fi Direct TCP reachability: bind to Android P2P group interface,
# one bounded TCP handshake, zero application bytes and no trust grant.
P2P_TCP="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectTcpProbe.kt"
P2P_HOST="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectGroupHostActivity.kt"
P2P_JOIN="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectTargetLinkController.kt"
test -f "$P2P_TCP"
grep -Fq 'listener.bind(InetSocketAddress(local, port), 1)' "$P2P_TCP"
grep -Fq 'outbound.bind(InetSocketAddress(local, 0))' "$P2P_TCP"
grep -Fq 'listener.soTimeout = ACCEPT_TIMEOUT_MS' "$P2P_TCP"
grep -Fq 'outbound.connect(' "$P2P_TCP"
grep -Fq 'testAddressAllowed(' "$P2P_TCP"
grep -Fq 'info.groupFormed' "$P2P_HOST"
grep -Fq 'info.isGroupOwner' "$P2P_HOST"
grep -Fq 'info.groupFormed' "$P2P_JOIN"
grep -Fq 'info.isGroupOwner' "$P2P_JOIN"
grep -Fq 'tcpProbe?.close()' "$P2P_HOST"
grep -Fq 'tcpProbe?.close()' "$P2P_JOIN"
for forbidden in '.getInputStream(' '.getOutputStream(' '.inputStream' \
  '.outputStream' '.read(' '.write(' '.send(' 'SSLEngine' \
  'TestPacketCodec' 'sendDebugText(' 'sendPacket(' 'SharedPreferences' \
  'openFileOutput(' 'Log.' 'println('; do
  if grep -Fq "$forbidden" "$P2P_TCP"; then
    echo "FAIL: F3-A TCP handshake probe gained payload, TLS or persistence" >&2
    exit 1
  fi
done
if find "$MAIN" -name 'DebugWifiDirectTcpProbe.kt' -print -quit | grep -q .; then
  echo "FAIL: F3-A unauthenticated TCP probe moved into release source" >&2
  exit 1
fi

# F3-B3-A: reject Wi-Fi/cellular/default-route or guessed P2P interfaces.
P2P_INTERFACE="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectP2pInterface.kt"
P2P_INTERFACE_TEST="app/src/test/java/com/edenpractical/comms/wifidirect/DebugWifiDirectP2pInterfaceTest.kt"
test -f "$P2P_INTERFACE" && test -f "$P2P_INTERFACE_TEST"
grep -Fq 'NetworkInterface.getByName(osGroupInterfaceName)' "$P2P_INTERFACE"
grep -Fq 'return viable.singleOrNull()?.address' "$P2P_INTERFACE"
grep -Fq 'owner != local &&' "$P2P_INTERFACE"
grep -Fq 'sameNetwork(local, owner, item.prefixLength)' "$P2P_INTERFACE"
grep -Fq 'group.getInterface()' "$P2P_HOST"
grep -Fq 'group.getInterface()' "$P2P_JOIN"
grep -Fq 'm.requestGroupInfo(c)' "$P2P_HOST"
grep -Fq 'm.requestGroupInfo(c)' "$P2P_JOIN"
grep -Fq 'localP2pAddress = local' "$P2P_HOST"
grep -Fq 'localP2pAddress = local' "$P2P_JOIN"
grep -Fq 'if (forJvmLoopback && groupOwnerAddress?.isLoopbackAddress != true)' "$P2P_TCP"
if grep -Fq 'NetworkInterface.getNetworkInterfaces()' "$P2P_INTERFACE"; then
  echo "FAIL: F3-B3-A P2P probe scans unspecified interfaces as route fallback" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugWifiDirectP2pInterface.kt' -print -quit | grep -q .; then
  echo "FAIL: F3-B3-A P2P interface diagnostic moved to release source" >&2
  exit 1
fi

# F3-B1: actual mutually pinned TLS 1.3 over REAL JVM TCP loopback only,
# using the three QR public identity expectations. Never use it on current
# unverified live Wi-Fi Direct radio, or enable message/voice I/O.
P2P_QR_TLS="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectFutureQrTlsExpectation.kt"
P2P_TLS_LAB="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectFutureTlsSocketLab.kt"
P2P_TLS_TEST="app/src/test/java/com/edenpractical/comms/wifidirect/DebugWifiDirectQrPinnedTlsSocketTest.kt"
test -f "$P2P_QR_TLS" && test -f "$P2P_TLS_LAB" && test -f "$P2P_TLS_TEST"
grep -Fq 'DebugWifiDirectQrCodec.decodeAck(' "$P2P_QR_TLS"
grep -Fq 'DebugWifiDirectQrCodec.decodeReply(' "$P2P_QR_TLS"
grep -Fq 'MessageDigest.isEqual(host.pin, ownPin)' "$P2P_QR_TLS"
grep -Fq 'address.isLoopbackAddress && address.address.size == 4' "$P2P_TLS_LAB"
grep -Fq 'PinnedTlsPeer.requirePinnedTls13(incoming.session, peerPin)' "$P2P_TLS_LAB"
grep -Fq 'PinnedTlsPeer.requirePinnedTls13(client.session, peerPin)' "$P2P_TLS_LAB"
grep -Fq 'wrongPeerFingerprintInScannedQrFailsRealMutualTls' "$P2P_TLS_TEST"
for source in "$P2P_HOST" "$P2P_JOIN" "$P2P_TCP" \
  'app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectQrCoordinator.kt'; do
  if grep -Fq 'DebugWifiDirectFutureTlsSocketLab' "$source"; then
    echo "FAIL: F3-B1 loopback TLS LAB wired into live P2P" >&2
    exit 1
  fi
done
# F3-C reuses ONLY the already validated copy-only QR expectation in the
# same foreground QR coordinator, never the future loopback socket lab.
for source in "$P2P_HOST" "$P2P_JOIN" "$P2P_TCP"; do
  if grep -Fq 'DebugWifiDirectFutureQrTlsExpectation' "$source"; then
    echo "FAIL: raw future QR expectation escaped the live verified coordinator" >&2
    exit 1
  fi
done
for forbidden in 'getInputStream(' 'getOutputStream(' '.write(' '.read(' \
  'sendDebugText(' 'sendPacket(' 'SharedPreferences' 'openFileOutput(' \
  'AudioRecord' 'Log.' 'println('; do
  if grep -Fq "$forbidden" "$P2P_TLS_LAB"; then
    echo "FAIL: F3-B1 lab gained application data or storage APIs" >&2
    exit 1
  fi
done
if find "$MAIN" -name 'DebugWifiDirectFutureTlsSocketLab.kt' -print -quit | grep -q .; then
  echo "FAIL: F3-B1 unreviewed TLS lab in release source" >&2
  exit 1
fi

# F3-B2: fresh-QR nonce and full transcript agreement runs as encrypted
# test-control data ON THE SAME mutually pinned JVM-only TLS socket. There is
# no reviewed radio, user-approval, text or voice session yet.
P2P_PROOF_LAB="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectFutureQrProofSocketLab.kt"
P2P_PROOF_FRAME="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectFutureQrCommitment.kt"
P2P_PROOF_CONTEXT="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectFutureQrTlsExpectation.kt"
test -f "$P2P_PROOF_LAB" && test -f "$P2P_PROOF_FRAME"
grep -Fq 'address.isLoopbackAddress && address.address.size == 4' "$P2P_PROOF_LAB"
grep -Fq 'SessionProofExchange.runHost(' "$P2P_PROOF_LAB"
grep -Fq 'SessionProofExchange.runJoin(' "$P2P_PROOF_LAB"
grep -Fq 'DebugWifiDirectFutureQrCommitment.exchange(' "$P2P_PROOF_LAB"
grep -Fq 'PinnedTlsPeer.requirePinnedTls13(socket.session, pin)' "$P2P_PROOF_LAB"
grep -Fq 'copyQrProofBindingForLab()' "$P2P_PROOF_CONTEXT"
grep -Fq 'DataInputStream(input).readFully(frame)' "$P2P_PROOF_FRAME"
grep -Fq 'sha.update(fullQrTranscript)' "$P2P_PROOF_FRAME"
for live in "$P2P_HOST" "$P2P_JOIN" "$P2P_TCP" \
  'app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectQrCoordinator.kt'; do
  if grep -Fq 'DebugWifiDirectFutureQrProofSocketLab' "$live" ||
     grep -Fq 'DebugWifiDirectFutureQrCommitment' "$live"; then
    echo "FAIL: F3-B2 unreviewed QR proof connected to live P2P radio" >&2
    exit 1
  fi
done
for forbidden in 'sendDebugText(' 'sendPacket(' 'AudioRecord' 'SharedPreferences' \
  'openFileOutput(' 'Log.' 'println(' 'DebugBluetoothTextLedger' \
  'DebugBluetoothTextFrameCodec'; do
  if grep -Fq "$forbidden" "$P2P_PROOF_LAB" ||
     grep -Fq "$forbidden" "$P2P_PROOF_FRAME"; then
    echo "FAIL: F3-B2 test-control proof gained messaging, storage or voice" >&2
    exit 1
  fi
done
if find "$MAIN" -name 'DebugWifiDirectFutureQrProofSocketLab.kt' -print -quit | grep -q .; then
  echo "FAIL: F3-B2 future QR proof lab moved into release source" >&2
  exit 1
fi

# F3-C: live Wi-Fi Direct uses real OS-reported P2P group interface,
# original single TCP stream, exact mutual TLS/QR/exporter and bilateral
# confirmed consent BEFORE encrypted application TEXT/ACK. It is not released.
P2P_VERIFIED="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectApprovedTcpSession.kt"
P2P_INLINE_QR="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectInlineQrPanel.kt"
P2P_CHAT_UI="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectTextPanel.kt"
P2P_CHAT_STATE="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectChatUiState.kt"
P2P_QR_COORD="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectQrCoordinator.kt"
for FILE in "$P2P_VERIFIED" "$P2P_INLINE_QR" "$P2P_CHAT_UI" "$P2P_CHAT_STATE"; do
  test -f "$FILE"
  if grep -REn '(SharedPreferences|DataStore|openFileOutput\(|AudioRecord|MediaRecorder|ProductionSessionAuthorization|TestPacketCodec|Log\.|println\()' "$FILE"; then
    echo "FAIL: live P2P text owner gained storage, audio, release or logging authority" >&2
    exit 1
  fi
done
grep -Fq 'newVerifiedTransportRequest(hostRole: Boolean)' "$P2P_QR_COORD"
grep -Fq 'DebugWifiDirectFutureQrTlsExpectation.host(' "$P2P_QR_COORD"
grep -Fq 'DebugWifiDirectFutureQrTlsExpectation.join(' "$P2P_QR_COORD"
grep -Fq 'copyLiveQrProofBinding()' "$P2P_QR_COORD"
grep -Fq 'DebugWifiDirectInlineQrPanel(preparation, hostRole = true)' "$P2P_HOST"
grep -Fq 'DebugWifiDirectInlineQrPanel(preparation, hostRole = false)' \
  'app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectDiscoveryActivity.kt'
grep -Fq 'DebugWifiDirectP2pInterface.resolve(' "$P2P_HOST"
grep -Fq 'DebugWifiDirectP2pInterface.resolve(' "$P2P_JOIN"
grep -Fq 'server.bind(InetSocketAddress(local, PORT), 1)' "$P2P_VERIFIED"
grep -Fq 'outbound.bind(InetSocketAddress(local, 0))' "$P2P_VERIFIED"
grep -Fq 'DebugWifiDirectTcpProbe.testAddressAllowed(owner)' "$P2P_VERIFIED"
grep -Fq 'DebugRfcommTlsProbe.runStreams(' "$P2P_VERIFIED"
grep -Fq 'DebugBluetoothApprovedTextChannel(' "$P2P_VERIFIED"
grep -Fq 'DebugBluetoothConsentExchange.run(' "$BT_TLS_PROBE"
grep -Fq 'onVerifiedStage(DebugBluetoothVerifiedStage.HUMAN_CONFIRMATIONS)' "$BT_TLS_PROBE"
grep -Fq 'onApprovedChannel(approvedEngine, wireIn, wireOut)' "$BT_TLS_PROBE"
grep -Fq 'closeDecision?.close()' "$P2P_VERIFIED"
grep -Fq 'closeQuietly(closeSocket)' "$P2P_VERIFIED"
grep -Fq 'verifiedSession?.close()' "$P2P_HOST"
grep -Fq 'verifiedSession?.close()' "$P2P_JOIN"
if find "$MAIN" \( -name 'DebugWifiDirectApprovedTcpSession.kt' -o \
  -name 'DebugWifiDirectInlineQrPanel.kt' -o \
  -name 'DebugWifiDirectTextPanel.kt' -o \
  -name 'DebugWifiDirectChatUiState.kt' \) -print -quit | grep -q .; then
  echo "FAIL: F3-C unreviewed Wi-Fi Direct chat entered release source" >&2
  exit 1
fi

# D1: local microphone must be debug-only and must never acquire a socket.
LOCAL_AUDIO="app/src/debug/java/com/edenpractical/comms/audio/DebugLocalPttActivity.kt"
LOCAL_POLICY="app/src/debug/java/com/edenpractical/comms/audio/DebugLocalPttPolicy.kt"
test -f "$LOCAL_AUDIO" && test -f "$LOCAL_POLICY"
grep -Fq 'class DebugLocalPttActivity : Activity()' "$LOCAL_AUDIO"
grep -Fq 'ApplicationInfo.FLAG_DEBUGGABLE' "$LOCAL_AUDIO"
grep -Fq 'Manifest.permission.RECORD_AUDIO' "$LOCAL_AUDIO"
grep -Fq 'override fun onPause()' "$LOCAL_AUDIO"
grep -Fq 'terminateAndDiscard()' "$LOCAL_AUDIO"
grep -Fq 'DebugLocalPttPolicy.MAX_PCM_BYTES' "$LOCAL_AUDIO"
grep -Fq 'audioClip?.fill(0)' "$LOCAL_AUDIO"
grep -Fq 'android.permission.RECORD_AUDIO' app/src/debug/AndroidManifest.xml
grep -Fq 'android:name=".audio.DebugLocalPttActivity"' app/src/debug/AndroidManifest.xml
if grep -REn '(Socket\(|java\.net\.|javax\.net\.|BluetoothSocket|WifiP2p|LanDemoSession|startService\(|openFileOutput\(|SharedPreferences|MediaRecorder\.OutputFormat)' \
 "$LOCAL_AUDIO" "$LOCAL_POLICY"; then
  echo "FAIL: local microphone test gained transport, background or persistence" >&2
  exit 1
fi
if find "$MAIN" \( -name 'DebugLocalPttActivity.kt' -o -name 'DebugLocalPttPolicy.kt' \) -print -quit | grep -q .; then
  echo "FAIL: local audio code entered production source" >&2
  exit 1
fi
if grep -Fq 'android.permission.RECORD_AUDIO' app/src/main/AndroidManifest.xml; then
  echo "FAIL: debug mic permission entered release" >&2
  exit 1
fi
# F4-D4: future LAN PCM frame candidate stays a pure DEBUG-only lab.
# This does not pass LAN QR/TLS/consent review or authorize network audio.
VOICE_FUTURE="app/src/debug/java/com/edenpractical/comms/audio/DebugFutureLanVoiceFrameCodec.kt"
VOICE_FUTURE_TEST="app/src/test/java/com/edenpractical/comms/audio/DebugFutureLanVoiceFrameCodecTest.kt"
test -f "$VOICE_FUTURE" && test -f "$VOICE_FUTURE_TEST"
grep -Fq 'const val PACKET_BYTES = HEADER_BYTES + PCM_BYTES' "$VOICE_FUTURE"
grep -Fq 'decoded.sequence != nextSequence' "$VOICE_FUTURE"
grep -Fq 'arbitraryOldFrameCanEnterFreshSyntheticReceiverSoNoSessionTrustIsClaimed' "$VOICE_FUTURE_TEST"
if grep -REn '(Socket\(|java\.net\.|javax\.net\.|BluetoothSocket|WifiP2p|LanDemoSession|AudioRecord|AudioTrack|SharedPreferences|startService\(|openFileOutput\(|sendPacket\(|ProductionSessionAuthorization)' "$VOICE_FUTURE"; then
  echo "FAIL: F4-D4 voice lab gained a live transport, capture, persistence or security authority" >&2
  exit 1
fi
if find "$MAIN" -name 'DebugFutureLanVoiceFrameCodec.kt' -print -quit | grep -q .; then
  echo "FAIL: unreviewed F4-D4 voice codec moved into release source" >&2
  exit 1
fi
for forbidden in 'DebugFutureLanVoiceFrameCodec' 'DebugFutureLanVoiceSequenceRehearsal'; do
  if grep -Rq "$forbidden" app/src/main/java app/src/debug/java/com/edenpractical/comms/bluetooth app/src/debug/java/com/edenpractical/comms/wifidirect app/src/debug/java/com/edenpractical/comms/audio/DebugLocalPttActivity.kt; then
    echo "FAIL: unreviewed F4-D4 voice model wired into live app/radio/microphone" >&2
    exit 1
  fi
done

# F5-A: actual encrypted debug-only local persistence is OPTIONAL ONLY after
# bilateral verified LAN text events. The released variant cannot write data.
HISTORY_POLICY="app/src/main/java/com/edenpractical/comms/privacy/LocalHistoryPolicy.kt"
HISTORY_ENVELOPE="app/src/main/java/com/edenpractical/comms/privacy/LocalHistoryEnvelope.kt"
HISTORY_DEBUG="app/src/debug/java/com/edenpractical/comms/privacy/DebugLocalHistoryStore.kt"
HISTORY_RELEASE="app/src/release/java/com/edenpractical/comms/privacy/DebugLocalHistoryStore.kt"
HISTORY_PAIRING="app/src/main/java/com/edenpractical/comms/pairing/LocalPairingTestScreen.kt"
HISTORY_UI="app/src/main/java/com/edenpractical/comms/pairing/DebugConversationPanel.kt"
for FILE in "$HISTORY_POLICY" "$HISTORY_ENVELOPE" "$HISTORY_DEBUG" \
  "$HISTORY_RELEASE" "$HISTORY_PAIRING" "$HISTORY_UI"; do test -f "$FILE"; done
grep -Fq 'SESSION_ONLY = 0L' "$HISTORY_POLICY"
grep -Fq 'THIRTY_DAYS = 30L * ONE_DAY' "$HISTORY_POLICY"
grep -Fq 'val choices = listOf(' "$HISTORY_POLICY"
grep -Fq 'fun clockSafe(' "$HISTORY_POLICY"
grep -Fq 'fun shorten(' "$HISTORY_POLICY"
grep -Fq 'Cipher.getInstance("AES/GCM/NoPadding")' "$HISTORY_ENVELOPE"
grep -Fq 'GCMParameterSpec(TAG_BITS, iv)' "$HISTORY_ENVELOPE"
grep -Fq 'cipher.updateAAD(aad)' "$HISTORY_ENVELOPE"
grep -Fq 'LocalHistoryEnvelope.open(aes, bytes, aad())' "$HISTORY_DEBUG"
grep -Fq 'LocalHistoryEnvelope.seal(aes, plaintext, aad())' "$HISTORY_DEBUG"
grep -Fq 'KeyStore.getInstance("AndroidKeyStore")' "$HISTORY_DEBUG"
grep -Fq 'app.noBackupFilesDir' "$HISTORY_DEBUG"
grep -Fq 'ApplicationInfo.FLAG_DEBUGGABLE' "$HISTORY_DEBUG"
grep -Fq 'private const val KEY_ALIAS' "$HISTORY_DEBUG"
grep -Fq 'fun save(' "$HISTORY_DEBUG"
grep -Fq 'fun read(' "$HISTORY_DEBUG"
grep -Fq 'fun erasePeer(' "$HISTORY_DEBUG"
grep -Fq 'fun eraseAll(' "$HISTORY_DEBUG"
grep -Fq '): Boolean = false' "$HISTORY_RELEASE"
grep -Fq 'List<LocalHistoryPolicy.SavedMessage> = emptyList()' "$HISTORY_RELEASE"
if grep -Fq 'AndroidKeyStore' "$HISTORY_RELEASE" ||
   grep -Fq 'Cipher.getInstance' "$HISTORY_RELEASE" ||
   grep -Fq 'FileOutputStream' "$HISTORY_RELEASE"; then
  echo "FAIL: F5-A production no-op gained persistent storage" >&2
  exit 1
fi
grep -Fq 'historyDurationMs by remember' "$HISTORY_PAIRING"
grep -Fq 'LocalHistoryPolicy.SESSION_ONLY' "$HISTORY_PAIRING"
grep -Fq 'LanDemoSession.Phase.APPROVAL_COMPLETE ->' "$HISTORY_PAIRING"
grep -Fq 'activeHistoryPin = expected.copyOf()' "$HISTORY_PAIRING"
grep -Fq 'LanDemoSession.Phase.TEXT_WRITTEN, LanDemoSession.Phase.TEXT_RECEIVED ->' "$HISTORY_PAIRING"
grep -Fq 'historyDurationMs != LocalHistoryPolicy.SESSION_ONLY' "$HISTORY_PAIRING"
grep -Fq 'disappearAfterMs == DebugLocalDisappearingPolicy.OFF' "$HISTORY_PAIRING"
grep -Fq 'localHistoryStore.save(' "$HISTORY_PAIRING"
grep -Fq 'localHistoryStore.erasePeer(' "$HISTORY_PAIRING"
grep -Fq 'historyDurationMs = LocalHistoryPolicy.SESSION_ONLY' "$HISTORY_PAIRING"
grep -Fq 'onHistoryDurationChange = { changeLocalHistoryDuration(it) }' "$HISTORY_PAIRING"
grep -Fq 'onHistoryErase = { eraseLocalHistory() }' "$HISTORY_PAIRING"
grep -Fq 'DELETE SAVED HISTORY FOR THIS PERSON' "$HISTORY_UI"
grep -Fq 'Session only' "$HISTORY_UI" || grep -Fq 'LocalHistoryPolicy.label(historyDurationMs)' "$HISTORY_UI"
grep -Fq 'android:allowBackup="false"' app/src/main/AndroidManifest.xml
grep -Fq 'android:dataExtractionRules="@xml/data_extraction_rules"' app/src/main/AndroidManifest.xml
grep -Fq 'android:fullBackupContent="@xml/backup_rules"' app/src/main/AndroidManifest.xml
grep -Fq '<device-transfer>' app/src/main/res/xml/data_extraction_rules.xml
grep -Fq '<exclude domain="root" path="." />' app/src/main/res/xml/data_extraction_rules.xml
if grep -REn '(SharedPreferences|DataStore|openFileOutput\(|Log\.|println\(|MediaRecorder|AudioRecord|BluetoothSocket|WifiP2pManager|startService\()' \
  "$HISTORY_DEBUG" "$HISTORY_POLICY" "$HISTORY_ENVELOPE" "$HISTORY_RELEASE"; then
  echo "FAIL: F5-A local history added logging, audio, radios or external storage" >&2
  exit 1
fi

# F5-B: remembered PUBLIC peer metadata is a separate, optional DEBUG-only
# ciphertext store, never a live TLS/session authorization input or release feature.
PEER_MEMORY_POLICY="app/src/main/java/com/edenpractical/comms/privacy/PeerMemoryPolicy.kt"
PEER_MEMORY_DEBUG="app/src/debug/java/com/edenpractical/comms/privacy/DebugPeerMemoryStore.kt"
PEER_MEMORY_RELEASE="app/src/release/java/com/edenpractical/comms/privacy/DebugPeerMemoryStore.kt"
PEER_MEMORY_UI="app/src/main/java/com/edenpractical/comms/pairing/DebugRememberPeerPanel.kt"
for FILE in "$PEER_MEMORY_POLICY" "$PEER_MEMORY_DEBUG" "$PEER_MEMORY_RELEASE" "$PEER_MEMORY_UI"; do
  test -f "$FILE"
done
grep -Fq 'MAX_PEERS = 8' "$PEER_MEMORY_POLICY"
grep -Fq 'MAX_AGE_MS = PeerTrustExpiry.MAX_DAYS * ONE_DAY_MS' "$PEER_MEMORY_POLICY"
grep -Fq 'fun retainedExpiry(' "$PEER_MEMORY_POLICY"
grep -Fq 'minOf(existingExpiryMs, requestedExpiryMs)' "$PEER_MEMORY_POLICY"
grep -Fq 'app.noBackupFilesDir' "$PEER_MEMORY_DEBUG"
grep -Fq '"eden_comms_debug_peer_memory_aes_1"' "$PEER_MEMORY_DEBUG"
grep -Fq '"eden-comms/debug-peer-memory-v1/"' "$PEER_MEMORY_DEBUG"
grep -Fq 'LocalHistoryEnvelope.seal(' "$PEER_MEMORY_DEBUG"
grep -Fq 'LocalHistoryEnvelope.open(' "$PEER_MEMORY_DEBUG"
grep -Fq 'PeerMemoryPolicy.clockSafe(' "$PEER_MEMORY_DEBUG"
grep -Fq 'state.ownPin.contentEquals(ownPin)' "$PEER_MEMORY_DEBUG"
grep -Fq 'fun remember(' "$PEER_MEMORY_DEBUG"
grep -Fq 'fun forgetAll()' "$PEER_MEMORY_DEBUG"
grep -Fq 'Long? = null' "$PEER_MEMORY_RELEASE"
grep -Fq 'Boolean = false' "$PEER_MEMORY_RELEASE"
grep -Fq 'LanDemoSession.Phase.APPROVAL_COMPLETE ->' "$HISTORY_PAIRING"
grep -Fq 'peerMemoryStore.rememberedUntil(' "$HISTORY_PAIRING"
grep -Fq 'peerMemoryStore.remember(' "$HISTORY_PAIRING"
grep -Fq 'peerMemoryStore.forget(' "$HISTORY_PAIRING"
grep -Fq 'DebugRememberPeerPanel(' "$HISTORY_PAIRING"
grep -Fq 'if (!privacyHidden) {' "$HISTORY_PAIRING"
grep -Fq 'Forget all remembered phones on this device' "$PEER_MEMORY_UI"
grep -Fq 'Forget verified phone' "$PEER_MEMORY_UI"
grep -Fq 'DebugPeerMemoryStore(context).forgetAll()'   'app/src/main/java/com/edenpractical/comms/MainActivity.kt'
if grep -REn '(Log\.|println\(|SharedPreferences|BluetoothSocket|WifiP2pManager|Socket\(|sendPacket\(|AudioRecord|MediaRecorder|openFileOutput\()' \
  "$PEER_MEMORY_POLICY" "$PEER_MEMORY_DEBUG" "$PEER_MEMORY_UI" "$PEER_MEMORY_RELEASE"; then
  echo "FAIL: F5-B peer metadata gained radio, logging, audio or outside storage" >&2
  exit 1
fi
if grep -REn '(DebugPeerMemoryStore|PeerMemoryPolicy)' \
  app/src/main/java/com/edenpractical/comms/security \
  app/src/main/java/com/edenpractical/comms/transport \
  app/src/debug/java/com/edenpractical/comms/bluetooth \
  app/src/debug/java/com/edenpractical/comms/wifidirect; then
  echo "FAIL: F5-B saved peer metadata wired into authentication/transport" >&2
  exit 1
fi
if grep -REn '(KeyStore|Cipher|getSharedPreferences|FileOutputStream|noBackupFilesDir)' "$PEER_MEMORY_RELEASE"; then
  echo "FAIL: F5-B release variant acquired remembered-peer persistence" >&2
  exit 1
fi

# F5-C: Bluetooth and P2P retain exactly the original protocol's session
# verification. Only after both encrypted final confirmations may the transport
# hand public pins to a UI-only, opt-in metadata controller, which never
# supplies admission/pin decisions to TLS, QR or network/socket code.
NEARBY_OWNER="app/src/debug/java/com/edenpractical/comms/privacy/DebugVerifiedNearbyPrivacy.kt"
NEARBY_UI="app/src/debug/java/com/edenpractical/comms/privacy/DebugVerifiedNearbyPrivacyPanel.kt"
BT_LIVE_SCREEN="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothDiscoveryActivity.kt"
P2P_HOST_SCREEN="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectGroupHostActivity.kt"
P2P_JOIN_SCREEN="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectDiscoveryActivity.kt"
P2P_VERIFIED_SESSION="app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectApprovedTcpSession.kt"
for FILE in "$NEARBY_OWNER" "$NEARBY_UI"; do test -f "$FILE"; done
grep -Fq 'onApprovedIdentity(ownForUi, peerForUi)' "$BT_RFCOMM"
grep -Fq 'onApprovedIdentity(ownForUi, peerForUi)' "$P2P_VERIFIED_SESSION"
grep -Fq 'liveText === approved' "$BT_RFCOMM"
grep -Fq 'text === approved' "$P2P_VERIFIED_SESSION"
grep -Fq 'DebugVerifiedNearbyPrivacyPanel(localPrivacy)' "$BT_LIVE_SCREEN"
grep -Fq 'DebugVerifiedNearbyPrivacyPanel(localPrivacy)' "$P2P_HOST_SCREEN"
grep -Fq 'DebugVerifiedNearbyPrivacyPanel(localPrivacy)' "$P2P_JOIN_SCREEN"
for FILE in "$BT_LIVE_SCREEN" "$P2P_HOST_SCREEN" "$P2P_JOIN_SCREEN"; do
  grep -Fq 'localPrivacy.openVerified(' "$FILE"
  grep -Fq 'localPrivacy.receiveEncryptedEvent(' "$FILE"
  grep -Fq 'localPrivacy.close()' "$FILE"
done
grep -Fq 'val storageMutex = Mutex()' "$NEARBY_OWNER"
grep -Fq 'val activeDuration = retentionMs' "$NEARBY_OWNER"
grep -Fq 'activeDuration == LocalHistoryPolicy.SESSION_ONLY' "$NEARBY_OWNER"
grep -Fq 'history.save(' "$NEARBY_OWNER"
grep -Fq 'history.changeRetention(' "$NEARBY_OWNER"
grep -Fq 'peers.remember(' "$NEARBY_OWNER"
grep -Fq 'peers.forget(' "$NEARBY_OWNER"
grep -Fq 'peers.forgetAll()' "$NEARBY_OWNER"
grep -Fq 'DebugRememberPeerPanel(' "$NEARBY_UI"
grep -Fq 'DELETE SAVED HISTORY FOR VERIFIED PHONE' "$NEARBY_UI"
if grep -REn '(BluetoothSocket|WifiP2pManager|SSLEngine|InetSocketAddress|TestPacketCodec|createInsecure|sendDebugText\(|Log\.|println\(|SharedPreferences|openFileOutput\()' \
  "$NEARBY_OWNER" "$NEARBY_UI"; then
  echo "FAIL: F5-C optional metadata controller gained transport, crypto or unsafe storage" >&2
  exit 1
fi
if grep -REn '(DebugVerifiedNearbyPrivacy|DebugPeerMemoryStore|DebugLocalHistoryStore)' \
  app/src/main/java/com/edenpractical/comms/security \
  app/src/main/java/com/edenpractical/comms/transport \
  app/src/debug/java/com/edenpractical/comms/bluetooth/DebugRfcommTlsProbe.kt \
  app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectQrCoordinator.kt; then
  echo "FAIL: F5-C retained peer records entered QR/TLS authentication" >&2
  exit 1
fi
if find app/src/release/java -name '*DebugVerifiedNearbyPrivacy*' -print -quit | grep -q .; then
  echo "FAIL: F5-C experimental nearby privacy entered release source" >&2
  exit 1
fi

# F4-U: small microphone BURSTS on verified Bluetooth/P2P TLS only.
# PCM is carried by the existing approved text engine, never raw RFCOMM,
# unauthenticated TCP, a second socket, production capability or disk storage.
NEARBY_VOICE_FRAME="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugNearbyApplicationFrame.kt"
NEARBY_VOICE_CHANNEL="app/src/debug/java/com/edenpractical/comms/bluetooth/DebugBluetoothApprovedTextChannel.kt"
NEARBY_VOICE_OWNER="app/src/debug/java/com/edenpractical/comms/audio/DebugNearbyVoiceOwner.kt"
NEARBY_VOICE_UI="app/src/debug/java/com/edenpractical/comms/audio/DebugNearbyVoicePanel.kt"
for FILE in "$NEARBY_VOICE_FRAME" "$NEARBY_VOICE_CHANNEL" "$NEARBY_VOICE_OWNER" "$NEARBY_VOICE_UI"; do
  test -f "$FILE"
done
grep -Fq 'DebugLanVoiceWireCodec.decode(bytes).clear()' "$NEARBY_VOICE_FRAME"
grep -Fq 'DebugBluetoothTextFrameCodec.decode(bytes)' "$NEARBY_VOICE_FRAME"
grep -Fq 'DebugNearbyApplicationFrame.inspect(frame)' "$BT_TLS_TEXT_LAB"
grep -Fq 'fun offerVoiceClip(pcm: ByteArray)' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'val incomingVoice = DebugLanVoiceAssembler()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'private fun writeVoice(pcm: ByteArray)' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'DebugLanVoiceWireCodec.MAX_FRAMES' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'pending.voiceClip?.fill(0)' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'incomingVoice.clear()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'fun offerVoiceClip(pcm: ByteArray)' "$BT_RFCOMM"
grep -Fq 'fun offerVoiceClip(pcm: ByteArray)' "$P2P_VERIFIED"
grep -Fq 'event.pcm.fill(0)' "$BT_RFCOMM"
grep -Fq 'event.pcm.fill(0)' "$P2P_VERIFIED"
grep -Fq 'DebugNearbyVoicePanel(localVoice)' "$BT_LIVE_SCREEN"
grep -Fq 'DebugNearbyVoicePanel(localVoice)' "$P2P_HOST_SCREEN"
grep -Fq 'DebugNearbyVoicePanel(localVoice)' "$P2P_JOIN_SCREEN"
grep -Fq 'DebugHoldToTalkControl(' "$NEARBY_VOICE_UI"
grep -Fq 'ActivityResultContracts.RequestPermission()' "$NEARBY_VOICE_UI"
grep -Fq 'audio.startLiveCapture(' "$NEARBY_VOICE_OWNER"
grep -Fq 'audio.acceptLiveFrame(kind, pcm)' "$NEARBY_VOICE_OWNER"
grep -Fq 'audio.cancelAll()' "$NEARBY_VOICE_OWNER"
grep -Fq 'offerFrame?.invoke(pcm)' "$NEARBY_VOICE_OWNER"
if grep -REn '(BluetoothSocket|WifiP2pManager|ServerSocket|DatagramSocket|SharedPreferences|DataStore|openFileOutput\(|FileOutputStream|Log\.|println\(|ProductionSessionAuthorization|startService\()' \
  "$NEARBY_VOICE_OWNER" "$NEARBY_VOICE_UI" "$NEARBY_VOICE_FRAME" "$NEARBY_VOICE_CHANNEL"; then
  echo "FAIL: F4-U voice model gained raw radio, persistence or release authorization" >&2
  exit 1
fi
if find app/src/release -name '*DebugNearbyVoice*' -print -quit | grep -q .; then
  echo "FAIL: unreviewed F4-U voice model entered release source" >&2
  exit 1
fi

# F4-V: host-arbitrated encrypted floor and live 20ms PCM while held.
# A saved public pin, discovery address or local button is not a voice grant.
grep -Fq 'private const val FLOOR_MAGIC = 0x4543464c' "$NEARBY_VOICE_FRAME"
grep -Fq 'DebugLanVoiceFloorCodec.decode(bytes)' "$NEARBY_VOICE_FRAME"
grep -Fq 'private val floor = DebugLanVoiceFloor(isHost)' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'fun requestVoiceFloor(): DebugLanVoiceFloor.Ask' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'fun cancelUnstartedVoiceFloor()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'fun beginLiveVoice(): Boolean' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'fun offerLiveVoiceFrame(pcm: ByteArray)' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'fun finishLiveVoice(): Boolean' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'floor.remoteAudioStart()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'floor.remoteAudioEnd()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'floor.timedOut(nowMs())' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'outbound.remainingCapacity() < (if (first) 5 else 4)' "$NEARBY_VOICE_CHANNEL"
grep -Fq '!floor.maySendAudio()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'voiceEndFrames = count' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'liveVoiceActive = false' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'floor.close()' "$NEARBY_VOICE_CHANNEL"
grep -Fq 'isHost = request.hostRole' "$BT_RFCOMM"
grep -Fq 'isHost = request.hostRole' "$P2P_VERIFIED"
grep -Fq 'fun requestVoiceFloor()' "$BT_RFCOMM"
grep -Fq 'fun requestVoiceFloor()' "$P2P_VERIFIED"
grep -Fq 'fun offerVerifiedLiveVoiceFrame(pcm: ByteArray)' \
  'app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectTargetLinkController.kt'
grep -Fq 'DebugLanVoiceFloor.Ask.PENDING' "$NEARBY_VOICE_OWNER"
grep -Fq 'fun floorGranted()' "$NEARBY_VOICE_OWNER"
grep -Fq 'fun floorDenied()' "$NEARBY_VOICE_OWNER"
grep -Fq 'audio.startLiveCapture(' "$NEARBY_VOICE_OWNER"
grep -Fq 'audio.finishCapture()' "$NEARBY_VOICE_OWNER"
grep -Fq 'offerFrame?.invoke(pcm)' "$NEARBY_VOICE_OWNER"
grep -Fq 'cancelFloor?.invoke()' "$NEARBY_VOICE_OWNER"
grep -Fq 'fun receiveLiveFrame(kind: DebugLanVoiceWireCodec.Kind' "$NEARBY_VOICE_OWNER"
grep -Fq 'Only one phone has the floor' "$NEARBY_VOICE_UI"
for FILE in "$BT_LIVE_SCREEN" "$P2P_HOST_SCREEN" "$P2P_JOIN_SCREEN"; do
  grep -Fq 'localVoice.floorGranted()' "$FILE"
  grep -Fq 'localVoice.floorDenied()' "$FILE"
  grep -Fq 'localVoice.receiveLiveFrame(' "$FILE"
  grep -Fq 'localVoice.disable()' "$FILE"
done
if find app/src/release -name '*DebugNearbyVoice*' -print -quit | grep -q .; then
  echo "FAIL: unreviewed live floor voice entered release variant" >&2
  exit 1
fi

echo "PASS: F4-V verified shared TLS host floor and live foreground PCM, release still disabled"

# M6-A: one product-facing debug method chooser and a reusable, presentational
# Text/Talk/Disconnect shell. Selection is NOT authentication: all actual
# sessions continue to require their already-verified QR/TLS/consent gates.
M6_MAIN="app/src/main/java/com/edenpractical/comms/MainActivity.kt"
M6_METHODS="app/src/main/java/com/edenpractical/comms/model/NearbyMethodCatalog.kt"
M6_CHAT="app/src/debug/java/com/edenpractical/comms/ui/DebugVerifiedConversationPanel.kt"
test -f "$M6_MAIN" && test -f "$M6_METHODS" && test -f "$M6_CHAT"
grep -Fq 'NearbyMethodCatalog.select(method, debug)' "$M6_MAIN"
grep -Fq 'NearbyMethodCatalog.Selection.LaunchDebugBluetooth' "$M6_MAIN"
grep -Fq 'NearbyMethodCatalog.Selection.LaunchDebugWifiDirectPreflight' "$M6_MAIN"
grep -Fq 'Intent().setClassName(context, target)' "$M6_MAIN"
grep -Fq 'if (!debuggable) return Selection.NotAvailable' "$M6_METHODS"
grep -Fq 'ConnectionMethod.LAN -> Selection.LaunchDebugLanQr' "$M6_METHODS"
grep -Fq 'ConnectionMethod.BLUETOOTH -> Selection.LaunchDebugBluetooth' "$M6_METHODS"
grep -Fq 'ConnectionMethod.WIFI_DIRECT -> Selection.LaunchDebugWifiDirectPreflight' "$M6_METHODS"
grep -Fq 'internal fun DebugVerifiedConversationPanel(' "$M6_CHAT"
grep -Fq 'Text("DISCONNECT THIS TEST SESSION")' "$M6_CHAT"
grep -Fq 'if (selection == "text") textContent()' "$M6_CHAT"
grep -Fq 'if (showPrivacy) privacyContent()' "$M6_CHAT"
grep -Fq 'DebugVerifiedConversationPanel(' "$BT_LIVE_SCREEN"
grep -Fq 'DebugVerifiedConversationPanel(' "$P2P_HOST_SCREEN"
grep -Fq 'DebugVerifiedConversationPanel(' "$P2P_JOIN_SCREEN"
grep -Fq 'linkController.disconnect()' "$BT_LIVE_SCREEN"
grep -Fq 'DebugWifiDirectGroupHostGate.End.USER_STOP' "$P2P_HOST_SCREEN"
grep -Fq 'DebugWifiDirectTargetLinkGate.End.USER_STOP' "$P2P_JOIN_SCREEN"
grep -Fq 'DebugWifiDirectReadinessPolicy.State.PREREQUISITES_OBSERVED' \
  'app/src/debug/java/com/edenpractical/comms/wifidirect/DebugWifiDirectPreflightActivity.kt'
for FLAG in CAN_CONNECT CAN_SEND_MESSAGES CAN_TRANSMIT_VOICE; do
  grep -Fq "const val $FLAG = false" \
    app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt
done
if grep -REn '(BluetoothSocket|WifiP2pManager|LanDemoSession\(|approveForTest\(|openFileOutput\(|SharedPreferences|ProductionSessionAuthorization|startDiscovery\(|discoverPeers\()' \
  "$M6_CHAT" "$M6_METHODS"; then
  echo "FAIL: M6-A presentation acquired radio, approval, or persistence authority" >&2
  exit 1
fi
for F in \
  'app/src/release/java/com/edenpractical/comms/ui/DebugVerifiedConversationPanel.kt' \
  'app/src/main/java/com/edenpractical/comms/ui/DebugVerifiedConversationPanel.kt'; do
  if test -f "$F"; then
    echo "FAIL: M6-A debug-only chat shell entered release source" >&2
    exit 1
  fi
done

# M6-B: exiting the Talk tab must release/cancel an authenticated floor.
# Evidence is RAM-only and redacted, copied only by explicit advanced click.
M6_QA_OWNER="app/src/debug/java/com/edenpractical/comms/qa/DebugNearbyQaEvidence.kt"
M6_QA_PANEL="app/src/debug/java/com/edenpractical/comms/qa/DebugNearbyQaPanel.kt"
test -f "$M6_QA_OWNER" && test -f "$M6_QA_PANEL"
test -f docs/TWO_PHONE_ACCEPTANCE_MATRIX.md
grep -Fq 'onLeaveTalk: () -> Unit' "$M6_CHAT"
grep -Fq 'DisposableEffect(selection)' "$M6_CHAT"
grep -Fq 'if (selection == "talk") onLeaveTalk()' "$M6_CHAT"
grep -Fq 'onClick = chooseText' "$M6_CHAT"
grep -Fq 'if (showQa) qaContent()' "$M6_CHAT"
for F in "$BT_LIVE_SCREEN" "$P2P_HOST_SCREEN" "$P2P_JOIN_SCREEN"; do
  grep -Fq 'onLeaveTalk = { localVoice.release() }' "$F"
  grep -Fq 'qaEvidence.observe(event)' "$F"
  grep -Fq 'qaEvidence.end()' "$F"
  grep -Fq 'qaContent = { DebugNearbyQaPanel(qaEvidence) }' "$F"
done
grep -Fq 'DebugBluetoothApprovedTextChannel.Event.Ready ->' "$M6_QA_OWNER"
grep -Fq 'if (!active) return' "$M6_QA_OWNER"
grep -Fq 'fun summary()' "$M6_QA_OWNER"
grep -Fq 'LOCAL OBSERVATIONS ONLY' "$M6_QA_OWNER"
grep -Fq 'evidence.summary()' "$M6_QA_PANEL"
grep -Fq 'ClipboardManager' "$M6_QA_PANEL"
grep -Fq 'onClick = {' "$M6_QA_PANEL"
grep -Fq 'COPY THIS PHONE' "$M6_QA_PANEL"
if grep -REn '(Socket\(|BluetoothAdapter|WifiP2pManager|AudioRecord|AudioTrack|Log\.|println\(|openFileOutput\(|SharedPreferences|FileOutputStream)' \
  "$M6_QA_OWNER" "$M6_QA_PANEL" "$M6_CHAT"; then
  echo "FAIL: M6-B UI/QA model gained raw radio, audio device, sensitive logging or disk" >&2
  exit 1
fi
if grep -REn '(event\.text|event\.pcm|event\.id|qrPayload|peerFingerprint)' "$M6_QA_OWNER"; then
  echo "FAIL: M6-B local QA counts attempt to save or display message/audio/peer payloads" >&2
  exit 1
fi
for F in \
  app/src/main/java/com/edenpractical/comms/qa/DebugNearbyQaEvidence.kt \
  app/src/release/java/com/edenpractical/comms/qa/DebugNearbyQaEvidence.kt \
  app/src/main/java/com/edenpractical/comms/qa/DebugNearbyQaPanel.kt \
  app/src/release/java/com/edenpractical/comms/qa/DebugNearbyQaPanel.kt; do
  if test -e "$F"; then
    echo "FAIL: M6-B experimental local QA evidence entered non-debug source" >&2
    exit 1
  fi
done

echo "PASS: M6-B Talk-to-Text stops foreground mic and QA reports are redacted/opt-in"

# M6-C: source-level usability/accessibility and privacy disclosure integrity.
# UI changes must not touch the verified admission or production capability.
M6_VOICE_UI="app/src/debug/java/com/edenpractical/comms/audio/DebugNearbyVoicePanel.kt"
M6_PRIVACY="app/src/main/java/com/edenpractical/comms/privacy/PrivacyPreviewContent.kt"
test -f docs/M6_C_USABILITY_ACCESSIBILITY_AUDIT.md
grep -Fq 'onClick = chooseText' "$M6_CHAT"
grep -Fq '"Text mode not selected"' "$M6_CHAT"
grep -Fq '"Talk mode selected"' "$M6_CHAT"
grep -Fq 'shape = CutCornerShape(7.dp)' "$M6_CHAT"
grep -Fq 'stateDescription = if (showPrivacy)' "$M6_CHAT"
grep -Fq 'Lifecycle.Event.ON_RESUME' "$M6_VOICE_UI"
grep -Fq 'lifecycle?.removeObserver(observer)' "$M6_VOICE_UI"
grep -Fq 'ContextCompat.checkSelfPermission(' "$M6_VOICE_UI"
grep -Fq 'activate again to stop or cancel' "$M6_VOICE_UI"
grep -Fq 'liveRegion = LiveRegionMode.Polite' "$M6_VOICE_UI"
grep -Fq 'Session-only by default. Local encrypted history is optional' "$M6_MAIN"
grep -Fq 'contentDescription = "Dark appearance"' "$M6_MAIN"
grep -Fq '"ptt" -> NearbyScreen(' "$M6_MAIN"
grep -Fq 'Same Wi-Fi' "$M6_PRIVACY"
grep -Fq 'Bluetooth Classic' "$M6_PRIVACY"
grep -Fq 'Wi-Fi Direct' "$M6_PRIVACY"
grep -Fq 'live push-to-talk' "$M6_PRIVACY"
grep -Fq 'separate encrypted local storage' "$M6_PRIVACY"
grep -Fq 'Production secure messaging remains disabled' "$M6_PRIVACY"
if grep -REn '(BluetoothSocket|WifiP2pManager|SSLEngine|approveForTest\(|openFileOutput\(|SharedPreferences|ProductionSessionAuthorization|startDiscovery\(|discoverPeers\()' \
  "$M6_CHAT" "$M6_VOICE_UI" "$M6_PRIVACY"; then
  echo "FAIL: M6-C presentation or disclosure gained transport, storage or trust authority" >&2
  exit 1
fi
if grep -En '(Bluetooth and Wi-Fi Direct voice remain unavailable|DEBUG LAN-only|In debug LAN chat ONLY)' "$M6_PRIVACY"; then
  echo "FAIL: M6-C privacy disclosure regressed to obsolete claims" >&2
  exit 1
fi
for FLAG in CAN_CONNECT CAN_SEND_MESSAGES CAN_TRANSMIT_VOICE; do
  grep -Fq "const val $FLAG = false" \
    app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt
done
echo "PASS: M6-C consistent debug UX, microphone permission refresh and truthful privacy"

