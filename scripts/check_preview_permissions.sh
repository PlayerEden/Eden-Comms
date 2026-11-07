#!/usr/bin/env bash
set -euo pipefail
APK="${1:?APK path required}"
AAPT="${ANDROID_HOME:?ANDROID_HOME missing}/build-tools/36.0.0/aapt"
test -f "$APK"
test -x "$AAPT"
PERMISSIONS="$("$AAPT" dump permissions "$APK")"
echo "$PERMISSIONS"

# M3m: DEBUG APK intentionally declares Bluetooth SCAN for user inquiry,
# CONNECT for a user-requested RFCOMM socket, and ADVERTISE solely for
# Android's explicit 120-second discoverability prompt on Host.
# Main/release manifest MUST NOT request any Bluetooth permission.
# M3p: Android 13+ Nearby Wi-Fi plus API <=32 location and Wi-Fi read state,
# debug only; microphone, contacts and other radio permissions prohibited.
# Fail on unexpected permission additions rather than silently expanding access.
ACTUAL="$(printf '%s\n' "$PERMISSIONS" | grep -oE 'android\.permission\.[A-Z_]+' | sort -u || true)"
EXPECTED="$(printf '%s\n' \
  android.permission.ACCESS_NETWORK_STATE \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.BLUETOOTH_ADVERTISE \
  android.permission.ACCESS_WIFI_STATE \
  android.permission.CHANGE_WIFI_STATE \
  android.permission.CHANGE_NETWORK_STATE \
  android.permission.NEARBY_WIFI_DEVICES \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.CAMERA \
  android.permission.INTERNET | sort -u)"
if test "$ACTUAL" != "$EXPECTED"; then
  echo "FAIL: debug APK permission set differs from reviewed LAN+QR+Bluetooth+M3p Wi-Fi Direct baseline" >&2
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
for BT_PERMISSION in BLUETOOTH_SCAN BLUETOOTH_CONNECT BLUETOOTH_ADVERTISE; do
  grep -Fq "android.permission.$BT_PERMISSION" app/src/debug/AndroidManifest.xml
done
for WIFI_PERMISSION in ACCESS_WIFI_STATE CHANGE_WIFI_STATE CHANGE_NETWORK_STATE NEARBY_WIFI_DEVICES ACCESS_FINE_LOCATION; do
  grep -Fq "android.permission.$WIFI_PERMISSION" app/src/debug/AndroidManifest.xml
done
grep -Fq 'android:maxSdkVersion="32"' app/src/debug/AndroidManifest.xml
test "$(grep -Fc 'android:usesPermissionFlags="neverForLocation"' app/src/debug/AndroidManifest.xml)" -ge 2
grep -Fq 'android:exported="false"' app/src/debug/AndroidManifest.xml
echo "PASS: exact debug LAN+QR/Bluetooth/M3p Wi-Fi Direct permissions"
