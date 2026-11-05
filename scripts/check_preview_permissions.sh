#!/usr/bin/env bash
set -euo pipefail
APK="${1:?APK path required}"
AAPT="${ANDROID_HOME:?ANDROID_HOME missing}/build-tools/36.0.0/aapt"
test -f "$APK"
test -x "$AAPT"
PERMISSIONS="$("$AAPT" dump permissions "$APK")"
echo "$PERMISSIONS"
# M2 QR test permits INTERNET (local TCP sockets), ACCESS_NETWORK_STATE
# (local Wi-Fi selection), and CAMERA (explicit in-app QR scan only).
# Bluetooth, microphone, contacts and location remain prohibited.
if echo "$PERMISSIONS" | grep -Eq "uses-permission:.*android.permission.(BLUETOOTH|BLUETOOTH_CONNECT|BLUETOOTH_SCAN|RECORD_AUDIO|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|READ_CONTACTS|READ_SMS)"; then
  echo "FAIL: preliminary app unexpectedly requests sensitive communication permissions" >&2
  exit 1
fi
for REQUIRED in INTERNET ACCESS_NETWORK_STATE CAMERA; do
  if ! echo "$PERMISSIONS" | grep -q "android.permission.$REQUIRED"; then
    echo "FAIL: missing required LAN test permission $REQUIRED" >&2
    exit 1
  fi
done
echo "PASS: expected LAN + opt-in QR camera permissions; prohibited permissions absent"
