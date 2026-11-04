#!/usr/bin/env bash
set -euo pipefail
APK="${1:?APK path required}"
AAPT="${ANDROID_HOME:?ANDROID_HOME missing}/build-tools/36.0.0/aapt"
test -f "$APK"
test -x "$AAPT"
PERMISSIONS="$("$AAPT" dump permissions "$APK")"
echo "$PERMISSIONS"
# M1f LAN test permits only INTERNET (needed for local TCP sockets) and
# ACCESS_NETWORK_STATE (select the Wi-Fi network without WAN/cellular fallback).
# Bluetooth, microphone, location, camera, contacts remain prohibited.
if echo "$PERMISSIONS" | grep -Eq "uses-permission:.*android.permission.(BLUETOOTH|BLUETOOTH_CONNECT|BLUETOOTH_SCAN|RECORD_AUDIO|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|CAMERA|READ_CONTACTS|READ_SMS)"; then
  echo "FAIL: preliminary app unexpectedly requests sensitive communication permissions" >&2
  exit 1
fi
for REQUIRED in INTERNET ACCESS_NETWORK_STATE; do
  if ! echo "$PERMISSIONS" | grep -q "android.permission.$REQUIRED"; then
    echo "FAIL: missing required LAN test permission $REQUIRED" >&2
    exit 1
  fi
done
echo "PASS: required LAN-only permissions present; sensitive extras absent"
