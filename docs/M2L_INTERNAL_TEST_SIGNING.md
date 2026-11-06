# Eden Comms M2l — protected test APK signing path

**Status:** Implementation and disposable-key CI smoke test can be checked without phones.  
**Not activated:** A real durable signing key and five GitHub **environment secrets** have **not** been provided/configured. A real in-place upgrade has not been tested. Issue #27 stays OPEN.

## Why two APK pipelines

- **Every PR/push** still runs the ordinary [Android debug checks](../.github/workflows/android-debug.yml), signing with a **throwaway default Android debug key**. These build artifacts are for static, compile and one-install investigation only. Even with the same `applicationId`, subsequent debug APKs might not install over one another.
- **Manually triggered, owner-restricted:** [Internal signed test APK](../.github/workflows/android-internal-test-apk.yml) runs **only on `main`**, through the `internal-test` GitHub environment, and only if the actor is `PlayerEden`. It will **fail closed if required secrets or pinned signing certificate are absent/incorrect**. It builds/tests with no signing secrets in Gradle, then uses Android `apksigner` with a dedicated internal-test key to replace the ephemeral debug signature.
- **Never** use a production Play App Signing key for internal-test APKs or vice versa. This APK is still debuggable and has no production messaging/voice.

## M3d owner-run signing setup assistant (still requires human action)

The optional [M3d trusted-computer signing setup](M3D_OWNER_SIGNING_SETUP.md) now provides a safer path for this one-time setup. A Python helper checks that the GitHub **internal-test** environment admits **main only**, verifies a user-owned **existing PKCS12 private-key entry/passwords**, calculates the public signing certificate fingerprint, and uploads the five environment secrets through authenticated `gh secret set` **stdin**. It refuses overwriting any existing signing-secret names. **No permanent key is created, provided or configured by the source repository or ChatGPT**. The steps below remain valid for manual setup. All protected signing and physical update requirements are still OPEN until genuinely executed.

## One-time human setup (do not share secrets in a chat)

1. On a trusted PC, generate a **new, dedicated test-only** Android signing keystore using Android Studio or `keytool`. Use a strong unique keystore/password, non-identifying certificate subject, and long enough validity for testing. Back up the keystore securely OFF the repo, and do not recycle any existing production signing keys.
2. With `keytool -list -v -keystore <path>`, capture the **public certificate SHA-256** fingerprint for the chosen alias (64 hexadecimal digits after removing colons). This is a certificate fingerprint, **not** the APK file hash.
3. Convert the keystore file bytes to a single-line base64 value **locally**, without adding it to the repository or logs. Use the repository's GitHub Settings → Environments to create and protect an environment called `internal-test`; limit deployment to `main` and require reviewer approval where available. Add the following **environment secrets** (never repository files):
   - `EC_INTERNAL_KEYSTORE_BASE64`: base64-encoded dedicated test keystore
   - `EC_INTERNAL_KEYSTORE_PASSWORD`: keystore store password
   - `EC_INTERNAL_KEY_ALIAS`: the key alias
   - `EC_INTERNAL_KEY_PASSWORD`: key password
   - `EC_INTERNAL_CERT_SHA256`: pinned 64-character public signing certificate SHA-256
4. Protect `main`, review workflow and Gradle changes carefully, limit repository write permissions, and periodically review Actions logs/artifact access. GitHub-hosted public repo build artifacts can be externally accessible. The environment/secret controls are **not** a substitute for repo governance; malicious code merged into `main` could abuse the signing process.
5. Once configured, manually select Actions → **Internal signed test APK** → Run workflow on **main**. Never select a fork, feature branch or unreviewed SHA. The workflow itself denies other actors/refs. Do not use `pull_request_target`.
6. Download **only** the `eden-comms-internal-test-<run>-<commit>` artifact when all checks pass. The ZIP contains `eden-comms-internal-test.apk`, its SHA-256 and the public-safe APK identity/permission/dependency reports. The keystore is **never** uploaded as an artifact.
7. For the **next** test APK, trigger a **new** manual run. It uses `versionCode = 10000 + GITHUB_RUN_NUMBER` with a source-derived `-internal-<versionCode>` name. Re-running the **same** job does **not** increment the number and may not be accepted as an upgrade.

## What the workflow enforces

- Package remains `com.edenpractical.comms` and the release capability flags remain false.
- Version code is a canonical increasing positive integer beyond the current baseline `11`. Ordinary CI still builds at the baseline.
- An explicit `apksigner` verification runs; packaged version/name must match the explicit CI override, and the signing certificate **must match the pinned expected SHA-256**. Wrong/missing credentials or certificate fail before any artifact upload.
- Existing build JVM tests, security script, permissions/backup/dependency audit, manifest inventory and APK SHA-256 remain in the manual workflow.
- Normal PR CI also produces a short **smoke signature** using a **disposable CI-only key**, which is immediately destroyed and not distributed as an update-safe signer. It validates tooling but does not prove a persistent key exists.
- Signing secrets are passed **only to the post-build signing step**, never into Gradle build or normal PR jobs. Only the signed internal APK is included in the internal manual artifact.

## M3c in-app evidence added (not signing setup)

The development-only Connect nearby screen now displays and copies the **installed APK's public signing certificate SHA-256**, versionCode and CI source revision using Android PackageManager. See [M3c](M3C_ON_DEVICE_BUILD_EVIDENCE.md). These fields allow two testers to compare their actually installed builds without exporting Android Keystore keys or peer QR identities. They do **not** prove a persistent signing key exists, that a proposed APK can upgrade the installed version, or that both phones reached the same approved session.

## Upgrade safety gate, still open

1. Record exact **old** and **new** APK fingerprints, package IDs and versionCodes. Compare using `scripts/audit_apk_identity.py --previous <old-apk-identity.json>` where appropriate.
2. Install internal APK **A**, create the app's identity with explicit consent and change theme. Generate internal APK **B** via a fresh manual run using the **same pinned certificate** and a higher versionCode. Install **B over A**, without uninstalling, and verify identity and settings survive.
3. **Older debug APKs signed by transient CI keys will likely be incompatible with the newly adopted stable test key.** Warn the tester and get explicit informed consent before any one-time clean install/identity reset. Do not imply Android Keystore identity can be restored from exported app data.
4. An in-place upgrade also requires a real Android package-manager test. Matching static certs and higher versionCode do **not** prove an upgrade is safe on every phone.
5. Independently choose future production Play App Signing/upload-key procedures; do not re-use this test key.

**Do not mark issue #27 resolved** until an actual durable signer is configured, two sequential internal builds match it, and both affected devices pass the preservation test. This work does not change app TLS, QR, packet formats, privacy preferences or any production communication capability.
