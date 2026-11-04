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

echo "PASS: review-only proposal unwired; diagnostic limited to demo; legacy send/audio denied; release capabilities off"
