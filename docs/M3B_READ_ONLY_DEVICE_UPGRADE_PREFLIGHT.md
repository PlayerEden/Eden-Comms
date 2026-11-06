# Eden Comms M3b — check a candidate APK against the installed phone, without changing it

**Date:** November 6, 2026  
**Status:** Read-only developer preflight + automated mocked tests. This does **not** mean the persistent internal-test signing key is configured or an Android in-place update has passed.

## Why this matters

The ordinary GitHub debug APK may carry a different disposable signing certificate on every build. **An update that fails signing validation cannot be made safe merely by matching the package name.** Uninstalling to get around the mismatch can discard the Android Keystore identity and app-local settings.

M3b adds `scripts/preflight_device_apk_update.py`, a desktop developer tool that checks the **actual installed app** against a proposed new APK before trying an update. It reads the signed public base APK using the normal Android Debug Bridge (ADB), not private app files or Keystore keys.

### Preconditions

- Trusted laptop/desktop with Python 3, Android SDK **Platform Tools** (`adb`) and Android SDK **Build Tools 36.0.0** (`aapt`, `apksigner`).
- Phone connected over USB, developer options/USB debugging explicitly enabled and ADB authorization accepted **on that phone**. Turn off USB debugging when finished testing.
- Unmodified candidate APK plus its original CI source, `apk-identity.json`, and SHA-256 metadata. **Do not use APKs from random links.**
- Prefer a candidate from the dedicated protected [internal-test workflow](../.github/workflows/android-internal-test-apk.yml) after the owner configures its real signer. Normal Actions debug artifacts are **not** promised upgrade-compatible.

### One-phone preflight

From the repository root on the trusted computer:

```sh
adb devices
python3 scripts/preflight_device_apk_update.py --apk /path/to/eden-comms-internal-test.apk
```

If multiple devices are connected, explicitly select one with `--serial <serial-from-adb-devices>` and run it **separately for A and B**. Do not paste device serials or temporary app paths into public issues or chat. This script never silently chooses among two phones, nor does it install anything.

The script:

1. Verifies the proposed APK's reported application ID is **exactly** `com.edenpractical.comms`, and its signing certificate is readable.
2. Selects one authorized ADB device, then queries `pm path com.edenpractical.comms` for the installed application's base APK. Missing/unreadable/multiple bases **fail closed**.
3. Copies only that **public installed APK binary** into a temporary local directory. The copy is automatically deleted after inspection.
4. Uses `aapt` and `apksigner` to verify **both** APK package IDs, signer certificate SHA-256 fingerprints and versionCodes. The candidate must have the **same signer** and a **strictly higher versionCode**.
5. If those preflight conditions pass, prints public package/version/certificate hashes and the candidate APK SHA-256. It prints **no device serial, QR, peer identity, private key or installed filesystem path**. It produces no public file or persistent report.

A matching certificate/version is a **static preflight**, not proof Android accepted an update or preserved its identity. The tool intentionally does not execute `adb install`, `adb uninstall`, `pm clear`, any app IPC, or any command to read private app data.

### If the preflight fails

**Do not uninstall automatically.** Investigate whether (a) you downloaded the wrong APK; (b) a disposable CI signer differs from the chosen long-lived internal signer; (c) the new code is not greater; (d) you selected the wrong phone/profile; or (e) that device cannot expose the APK to ADB without elevated access. Never root a phone or disable Android signature checks to satisfy this test. A legitimate unavoidable signer transition needs a **separate informed clean-install decision**, acknowledging identity reset.

### After PASS (manual hardware acceptance remains required)

Keep the previous APK and its evidence. Confirm the same phone is still selected. Then, with explicit tester approval, use Android Studio's normal install-over-existing process or:

```sh
adb -s <selected-device-serial> install -r /path/to/eden-comms-internal-test.apk
```

**This command is an example for the human tester, not a command run by the script.** Validate the existing opt-in Android Keystore identity, retained settings, newly installed version, and new peer QR flow. Repeat **independently on the second phone**. If the install fails, record only a redacted categorical error; do not try uninstalling as an automatic fix.

For a freshly installed app, the preflight will fail because there is no installed base APK to compare. That is expected and does not prove a new installation would succeed.

## Automated tests and nonclaims

`scripts/tests/test_preflight_device_apk_update.py` checks strict one-device selection, offline/unauthorized/multiple-device rejection, abnormal installed paths, wrong package, signer changes, downgrade/equal version rejection, and a mocked ADB pull → inspect → temporary cleanup path. No real USB devices are accessed in CI. It runs through the existing `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` CI test stage.

**OPEN / not claimed:** Configuration of a persistent protected test signer; two successive signed APKs with the same certificate; actual `adb install -r`; preservation of local identity/settings on both real devices; all T01–T36 two-phone network/protocol tests; independent protocol review; normal user text and voice.

Keep [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27), [issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26) and [issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42) OPEN until their own actual acceptance criteria are met.
