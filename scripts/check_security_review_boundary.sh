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

# M2u includes an ACTUAL bounded Unicode/ACK packet parser in main sources,
# but it is an UNREVIEWED INNER PAYLOAD candidate, not an authorized send path.
# Any integration into sockets, UI, trust checks or packet dispatch must be a
# separate change with independent protocol review and device QA.
CHAT_WIRE="$MAIN/com/edenpractical/comms/protocol/ProposedChatWireCodec.kt"
test -f "$CHAT_WIRE"
if grep -RFn --include='*.kt' --exclude='ProposedChatWireCodec.kt' \
  'ProposedChatWireCodec' "$MAIN"; then
  echo "FAIL: proposed text wire candidate referenced from live application sources" >&2
  exit 1
fi

# The TLS exporter is currently a DEBUG-DEMO-ONLY compatibility diagnostic.
# Only its implementation and the fixed-phrase debug-gated demo may reference
# it; broader usage requires fresh independent protocol review.
for FILE in $(grep -Rl --include='*.kt' 'TlsExporterReadiness' "$MAIN" || true); do
  case "$FILE" in
    "$MAIN/com/edenpractical/comms/security/TlsExporterReadiness.kt"|\
    "$MAIN/com/edenpractical/comms/transport/LanDemoSession.kt") ;;
    *) echo "FAIL: TLS exporter diagnostic referenced outside the gated demo: $FILE" >&2; exit 1 ;;
  esac
done

echo "PASS: reviewed boundaries intact; M2u chat wire unwired; test-only approvals/ACK simulators isolated; production send/voice flags off"
