#!/usr/bin/env bash
set -euo pipefail
APK="${1:?APK path required}"
AAPT="${ANDROID_HOME:?ANDROID_HOME missing}/build-tools/36.0.0/aapt"
test -f "$APK"
test -x "$AAPT"
PERMISSIONS="$("$AAPT" dump permissions "$APK")"
echo "$PERMISSIONS"
# M1 preparation has NO transport or microphone capabilities. Any permission
# request must arrive with explicit feature implementation/security review.
if echo "$PERMISSIONS" | grep -Eq "uses-permission:.*android.permission.(INTERNET|BLUETOOTH|BLUETOOTH_CONNECT|BLUETOOTH_SCAN|RECORD_AUDIO|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|READ_CONTACTS|READ_SMS)"; then
  echo "FAIL: preliminary app unexpectedly requests sensitive communication permissions" >&2
  exit 1
fi
echo "PASS: no communication/microphone/location/contacts permissions requested"
