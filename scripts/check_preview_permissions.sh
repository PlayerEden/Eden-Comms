#!/usr/bin/env bash
set -euo pipefail
APK="${1:?APK path required}"
AAPT="${ANDROID_HOME:?ANDROID_HOME missing}/build-tools/36.0.0/aapt"
test -f "$APK"
test -x "$AAPT"
PERMISSIONS="$("$AAPT" dump permissions "$APK")"
echo "$PERMISSIONS"

# M3k: DEBUG APK now intentionally requests BLUETOOTH_SCAN at runtime for
# user-started, count-only Classic inquiry. Main/release Manifest MUST NOT
# request it. No other Bluetooth, location, microphone or contacts permission.
# Fail on unexpected permission additions rather than silently expanding access.
ACTUAL="$(printf '%s\n' "$PERMISSIONS" | grep -oE 'android\.permission\.[A-Z_]+' | sort -u || true)"
EXPECTED="$(printf '%s\n' \
  android.permission.ACCESS_NETWORK_STATE \
  android.permission.BLUETOOTH_SCAN \
  android.permission.CAMERA \
  android.permission.INTERNET | sort -u)"
if test "$ACTUAL" != "$EXPECTED"; then
  echo "FAIL: debug APK permission set differs from approved LAN + QR + explicit Bluetooth scan baseline" >&2
  echo "Expected:" >&2
  echo "$EXPECTED" >&2
  echo "Actual:" >&2
  echo "$ACTUAL" >&2
  exit 1
fi

if grep -Eq 'android.permission.(BLUETOOTH|NEARBY_WIFI_DEVICES|ACCESS_(FINE|COARSE)_LOCATION|RECORD_AUDIO)' \
  app/src/main/AndroidManifest.xml; then
  echo "FAIL: sensitive radio/location permission leaked into main/release manifest" >&2
  exit 1
fi
test -f app/src/debug/AndroidManifest.xml
grep -Fq 'android.permission.BLUETOOTH_SCAN' app/src/debug/AndroidManifest.xml
grep -Fq 'android:usesPermissionFlags="neverForLocation"' app/src/debug/AndroidManifest.xml
grep -Fq 'android:exported="false"' app/src/debug/AndroidManifest.xml
echo "PASS: debug APK has only expected LAN, QR camera and explicit Bluetooth inquiry permission"
