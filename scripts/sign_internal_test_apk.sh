#!/usr/bin/env bash
# Re-sign a Gradle-built, aligned debug APK with a dedicated internal-test key.
# The original temporary Android debug signature is replaced by apksigner.
# Do not use production Play App Signing keys or expose this key on PR builds.
set -euo pipefail
umask 077

if [[ "$#" -ne 2 ]]; then
    echo "Usage: sign_internal_test_apk.sh input-debug.apk output-internal.apk" >&2
    exit 2
fi

source_apk="$1"
output_apk="$2"
[[ -f "$source_apk" && -s "$source_apk" ]] || {
    echo "FAIL: missing input APK" >&2; exit 1;
}
[[ "$source_apk" != "$output_apk" ]] || {
    echo "FAIL: signing must not overwrite the default CI debug APK" >&2; exit 1;
}
for name in EC_INTERNAL_KEYSTORE_BASE64 EC_INTERNAL_KEYSTORE_PASSWORD EC_INTERNAL_KEY_ALIAS EC_INTERNAL_KEY_PASSWORD; do
    [[ -n "${!name:-}" ]] || {
        echo "FAIL: missing internal-test signing credential: $name" >&2; exit 1;
    }
done

sdk_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk_home" ]] || { echo "FAIL: Android SDK path missing" >&2; exit 1; }
tools="$sdk_home/build-tools/36.0.0"
[[ -x "$tools/apksigner" && -x "$tools/zipalign" ]] || {
    echo "FAIL: Android Build Tools 36.0.0 are required" >&2; exit 1;
}
temp_keystore="$(mktemp "${RUNNER_TEMP:-/tmp}/eden-internal-test-XXXXXXXX")"
cleanup() {
    rm -f -- "$temp_keystore"
}
trap cleanup EXIT
printf '%s' "$EC_INTERNAL_KEYSTORE_BASE64" | base64 --decode > "$temp_keystore"
[[ -s "$temp_keystore" ]] || {
    echo "FAIL: invalid empty internal-test keystore" >&2; exit 1;
}

# Android Gradle Plugin already zipaligns APKs. Do not change APK bytes after signing.
"$tools/zipalign" -c -P 16 4 "$source_apk" >/dev/null
mkdir -p -- "$(dirname "$output_apk")"

# Passwords are passed by variable NAME, not as command-line plaintext.
# apksigner strips the previous disposable debug signatures when re-signing.
"$tools/apksigner" sign \
    --ks "$temp_keystore" \
    --ks-key-alias "$EC_INTERNAL_KEY_ALIAS" \
    --ks-pass env:EC_INTERNAL_KEYSTORE_PASSWORD \
    --key-pass env:EC_INTERNAL_KEY_PASSWORD \
    --out "$output_apk" "$source_apk"
"$tools/apksigner" verify --min-sdk-version 26 "$output_apk" >/dev/null
echo "Internal test APK re-signed and verified; signer pin audit still required."
