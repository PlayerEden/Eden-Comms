# Eden Comms — internal APK test-build identity and upgrade safety

**Scope:** development APKs only. This is packaging evidence, not a stable signing key, tested upgrade path or security certification. Does not enable real messaging.

## Why this exists

Android package IDs are not enough to decide whether a new APK will update an installed app. The signing certificate must match (or a supported key-rotation proof must be valid), and the versionCode must be suitable. GitHub Actions currently builds with a **transient debug key**; we cannot assume two separate CI jobs use the same signer. Uninstalling Eden Comms can erase app-local settings and the optional Android Keystore identity, so never advise that as an automatic workaround.

## Artifact inspection added to CI

`scripts/audit_apk_identity.py` uses Android SDK `aapt dump badging` and `apksigner verify --print-certs` to fail if the built APK differs from the Gradle package/version values, is not verifiably signed, or lacks exactly one signing certificate. It publishes two **public-safe, read-only** evidence files alongside `app-debug.apk`, its SHA-256 file, and existing privacy/dependency reports:

- `apk-identity.json`: package ID, versionName, versionCode, APK SHA-256, signing **certificate** SHA-256, CI source revision, and an explicit **unverified signer continuity** indicator.
- `apk-identity.md`: human-readable warning and comparisons for testers.

The tool never reads/publishes a signing **private key** or the Android Keystore's device keys. The public signing certificate digest is not the APK's SHA-256; both are recorded separately.

Example (after building): `python3 scripts/audit_apk_identity.py --apk app/build/outputs/apk/debug/app-debug.apk --output-dir app/build/play-evidence`

For an offline static comparison against earlier published evidence, supply `--previous <old-apk-identity.json>`. This intentionally refuses differing signer, differing package, non-incremented versionCode and unsupported evidence schema. A PASS is a **necessary preflight**, not a guarantee an actual device upgrade succeeds.

## Newly scaffolded internal test signing path — human setup still required

A **separate** [manual internal-test APK workflow](../.github/workflows/android-internal-test-apk.yml) and [M2l setup guide](M2L_INTERNAL_TEST_SIGNING.md) can now be reviewed and exercised with disposable credentials. It has not been activated with a persistent dedicated key or proven on two sequential signed releases. Ordinary CI debug artifacts still use transient debug signing. The internal workflow refuses wrong/missing secrets or configured certificate-pin mismatch and is owner/main-only by design.

## Outstanding signing continuity work (Issue #27)

A future separate implementation should adopt a durable **test-only signing identity**, protected in a suitable secret store, with limited access and a documented recovery/rotation plan. Never commit the keystore or password to this public repository or re-use a future production signing identity. Keep `applicationId = com.edenpractical.comms` unchanged. Increment `versionCode` for each distributed update build.

Before promising in-place upgrades:
1. Establish a properly protected persistent test signer and prove two successive CI APKs carry its same signing certificate.
2. Verify package, signing certificate and versionCode on both APKs using the generated evidence.
3. On hardware, install an earlier APK, create opt-in local identity, install the newer APK **without uninstalling**, and verify identity/preferences persist.
4. Document transition behavior if an earlier artifact already has a different debug signature. This may require a one-time clean installation and identity reset after informed consent.

## Current safety boundary

This CI-only addition does not modify application logic, permissions, TLS/QR, message handling, `ReleaseCapabilities`, Android package identity or signing configuration. The experimental debug demo remains **four fixed harmless phrases**, with real two-phone testing still pending [Issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26).

**References:**
- [Android Developers — How app updates work](https://developer.android.com/google/play/app-updates)
- [Android Developers — apksigner](https://developer.android.com/tools/apksigner)
