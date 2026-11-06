# Eden Comms M3d — secure one-time internal APK signer setup

**Prepared:** 2026-11-06  
**Status:** Script and automated simulated checks; **owner setup NOT executed**. No persistent signing credentials were created, copied or uploaded by ChatGPT. No real update was attempted.

The manual [Internal signed test APK workflow](../.github/workflows/android-internal-test-apk.yml) already isolates long-lived test signing from disposable CI signing. To actually use it, the repo owner must supply a dedicated long-lived **test-only** PKCS12 keystore and five GitHub **environment secrets**. This local helper eliminates the risky manual copy/paste of a base64 keystore or passwords.

## 1. Do this on your own trusted PC, not in ChatGPT

Install Java JDK (keytool) and [GitHub CLI (gh)](https://cli.github.com/), then log in to GitHub as **PlayerEden**:

    gh auth login

Create an off-repository, private, backed-up location for the keystore. On Windows PowerShell, an example is a private directory such as your user profile (avoid a synced/shared folder; a secure password-manager attachment or encrypted external drive is better). **Do not put the private key anywhere inside the GitHub checkout**, and do not paste private key/password contents into a chat, issue or pull request.

Generate a **new test-only** PKCS12 signer. This command prompts locally for a password—**do not put the password in the command line**:

    keytool -genkeypair -alias eden-comms-internal-test -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12 -keystore "C:\private-location\eden-comms-internal-test.p12" -dname "CN=Eden Comms Internal Test"

On macOS/Linux use a private path such as "$HOME/private-eden-signing/eden-comms-internal-test.p12". When using a POSIX OS, ensure **chmod 600** on the file. Keep a secure **offline backup of the exact keystore and both passwords**; they cannot be recovered from GitHub environment secrets. PKCS12 normally uses the same password for the store and key.

This key must **not** be a production Play App Signing key, existing app identity key, Apple certificate, QR/TLS peer key, or the throwaway key used by normal CI.

## 2. Protect the GitHub environment before any upload

In your browser: [Eden-Comms environment settings](https://github.com/PlayerEden/Eden-Comms/settings/environments) → create/open **internal-test**.

Set **Deployment branches and tags → Selected branches and tags**, with exactly one branch pattern: **main**. Do not allow a wildcard, a release branch, tags, or all branches. Where GitHub supports it, require deployment review from an authorized reviewer and prevent self-approval, but keep in mind some settings depend on plan/repository ownership. Review all workflow changes to main before running the manual signer; a trusted Actions workflow is required because GitHub's encrypted secrets do not protect against malicious workflow code once authorized.

The helper refuses uploading credentials if it cannot prove:
- the GitHub CLI is authenticated as **PlayerEden**;
- the environment is named **internal-test** and uses the custom selected-branches policy;
- the *only* permitted branch policy is **main**;
- **none** of the five signer secret names already exist.

## 3. Inspect readiness without exposing secrets

From your trusted local Eden-Comms checkout:

    python scripts/setup_internal_test_signing.py --check

This read-only operation may report five missing names before initial setup. GitHub exposes secret **names**, not the stored values. A successful name check does not mean the secret values are valid.

## 4. Configure new signer ONCE

When the environment is correctly restricted, and the key is backed up **outside** the repository:

    python scripts/setup_internal_test_signing.py --configure --keystore "C:\private-location\eden-comms-internal-test.p12"

The local helper asks for the keystore password and private-key password with hidden input. It invokes keytool with password **environment-variable references**, never passwords in command arguments. It checks a PrivateKeyEntry, uses the key to sign a local in-memory certificate request, exports the public DER certificate to calculate SHA-256, then shows **only the public SHA-256 fingerprint**.

It will ask you to type **UPLOAD NEW TEST KEY** before doing any write. With approval, it uploads exactly these five secrets **directly over standard input to GitHub CLI**, which encrypts them for GitHub:

- EC_INTERNAL_KEYSTORE_BASE64
- EC_INTERNAL_KEYSTORE_PASSWORD
- EC_INTERNAL_KEY_ALIAS
- EC_INTERNAL_KEY_PASSWORD
- EC_INTERNAL_CERT_SHA256

It does **not** create a .env credentials file, upload an APK, copy passwords onto the terminal, write secrets into this repository, or trigger GitHub Actions. The helper contains **no overwrite/rotate option**. If one or all of the names already exist, it refuses automatic modification even if the stored values may be wrong. Resolve partial or old configurations manually to avoid permanently losing update continuity.

**If an upload fails partway:** some of the five secrets may already exist. **Do not run the signing workflow yet or blindly rerun this helper.** Inspect the names in the environment, reconcile against the backed-up keystore and fingerprints, and repair deliberately without overwriting an existing distributed signer.

## 5. Verify two actual signed artifacts — separate manual acceptance

After local setup, the repo owner manually runs **GitHub → Actions → Internal signed test APK → Run workflow** on **main**. The workflow requires both its pinned public signing certificate SHA-256 and the five configured secrets before it publishes a signed APK artifact.

- Verify the completed Actions run passed and the downloadable artifact contains the APK, APK SHA-256 and \`apk-identity.json\` with the source revision, versionCode, package, and **full public signer certificate** fingerprint.
- Trigger a **fresh second manual workflow run**, not a rerun of the old job, so \`versionCode = 10000 + GITHUB_RUN_NUMBER\` increases. Confirm the same public signer fingerprint on A and B.
- Before replacing an existing phone install, use [M3b read-only installed-APK preflight](M3B_READ_ONLY_DEVICE_UPGRADE_PREFLIGHT.md) and the [M3c on-device evidence](M3C_ON_DEVICE_BUILD_EVIDENCE.md). A normal CI debug APK might be **incompatible** with the new internal-test signer; never uninstall to bypass a mismatch without understanding Android Keystore identity and app-settings loss.
- With informed tester consent, install B over A using the exact real device and a normal Android **in-place upgrade**. Check the **same phone's** previously created opt-in Android Keystore identity and settings survived. Repeat on the second phone. Physical T01–T36 still require both phones.

These are physical acceptance steps, **not completed by this PR**. No Android app networking/QR/authorization behavior changes and no message or voice capability is enabled.

### Security and limits

- Use **one backed-up key for all test updates**. A lost/rotated signer can require a clean install that destroys local identities/settings. Do not assume Android key-rotation support across all prior debug builds.
- Anyone able to change a workflow on \`main\` or approve a privileged environment run can potentially abuse its secrets. Protect repo writers, environment approvals, and review any Gradle/workflow changes that might exfiltrate secrets. Repository public CI artifacts may be downloadable by others.
- The helper uses Python standard library, gh CLI, and JDK keytool, on the owner's computer only. Simulated unit tests mock all upload operations; they **never** call GitHub with actual credentials.
- A one-time pass means five *secret names* exist, **not** that an owner has activated a trustworthy signer or proven upgrades. [Signing issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) stays **OPEN** until two verified builds and real Keystore/settings preservation pass.
- [Real-device two-phone QA #26](https://github.com/PlayerEden/Eden-Comms/issues/26) and [independently reviewed normal text #42](https://github.com/PlayerEden/Eden-Comms/issues/42) remain **OPEN**.

### References

[GitHub CLI secret set](https://cli.github.com/manual/gh_secret_set) · [GitHub environment secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets) · [Oracle keytool](https://docs.oracle.com/en/java/javase/22/docs/specs/man/keytool.html) · [Existing M2l signing path](M2L_INTERNAL_TEST_SIGNING.md)
