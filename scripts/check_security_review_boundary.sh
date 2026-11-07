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

# M3e opens *debug-only* short text on the current pinned TLS test,
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
grep -Fq 'if (candidate !is ProposedChatWireCodec.Packet.Text) throw TestPacketException()' "$TEST_ENVELOPE"
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
grep -Fq 'DebugRfcommTlsProbe.run(created, request) { decision ->' "$BT_RFCOMM"
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
grep -Fq 'result.bytesProduced() != phase.bytes' "$BT_CONTROL"
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
echo "PASS: M3n-C1 encrypted bilateral APPROVE/DECLINE + two final CONFIRM frames; no messages, voice, persistence or production connection"
