# Eden Comms — easy, repeatable offline development Preview APKs

**November 8, 2026 · based on the proven Sector Rebound/Operation Phosphor preview workflow.**

## Why this exists

APK updates are useful *during testing*. Android will usually refuse to
replace an existing package if its signing certificate changes, even when both
APKs are only debug builds. Eden Comms' usual PR/CI debug artifacts may be
signed with different disposable keys. We do not need owner production keys or
a Google Play account to solve the **development preview** use case.

A new **opt-in `com.edenpractical.comms.preview`** Android package builds the
real debuggable Eden Comms application using a **fixed public AOSP test key**
and an automatically increasing versionCode per NEW Actions workflow run.
The visible launcher label is **Eden Comms Preview**. Both Android packages
can exist on the same phone.

## How to use it

1. Go to [Eden-Comms → Actions → Eden Comms - installable offline Preview APK](https://github.com/PlayerEden/Eden-Comms/actions/workflows/offline-preview-apk.yml).
2. Choose **Run workflow → main**. The first workflow-file merge also runs one
   automatic smoke build. Normal app PRs and merges do NOT trigger duplicate
   preview builds; run it when you actually want a new tester APK to save
   GitHub Actions minutes.
3. Once green, download the **eden-comms-offline-preview-...** artifact.
   Extract `eden-comms-preview-<number>.apk`; its SHA-256 and
   `apk-identity.json` are included. No key or password setup required.
4. Install the first preview normally: it is a **separate app** from any
   previously installed `com.edenpractical.comms` build. It will not change
   your existing app or its locally generated TLS/QR identity.
5. For the **next** preview, start a **new** workflow run (do not rerun the
   old job). Its versionCode will increase and the AOSP public test signer
   stays the same, so Android should offer **Update** over the existing
   `.preview` install without uninstalling, keeping that preview app's local
   data and identity. Physical install-over preservation must still be
   confirmed on the phone.

**This solves the repeatable preview APK workflow**, not automatic upgrading
from the old non-preview package. Android treats the two package names as
different apps; it will not copy their Keystore identity or settings across
the initial transition. We deliberately avoid changing/re-signing that
existing application, because its prior debug certificate may differ.

## Security boundaries and limitations

- The AOSP test private key is publicly known to everyone. The workflow
  downloads it from Google's AOSP source, checks **pinned source object hashes**
  before use, and signs **only** the separate `.preview` package.
- This is intentionally *not* a trusted production signing identity. Anyone
  can sign an APK with this public key. Do **not** treat the certificate as
  proof an APK is genuine or use this build with sensitive/private/emergency
  communications. Fetch APKs from the actual repo workflow and compare
  its included SHA-256 and source revision.
- No signing secrets need to be uploaded. The separate private
  `internal-test` workflow remains available if owner-signed continuity
  is later important. A future Google Play release must use a different
  signing strategy and must never be signed using the public AOSP key.
- The current `ReleaseCapabilities` remain OFF. LAN text, local one-phone
  microphone, Bluetooth and Wi-Fi Direct experiments stay debug-gated;
  nothing here makes them a production-secure communications product.
- New Android version codes only guarantee ordering **within this preview
  workflow**. Two concurrent manual runs are serialized; reruns are rejected.
  This is not a QA claim about any previously installed old debug APK.
- The Preview launcher label and exact package are intentional, preventing
  accidental confusion with any later production build.
- After Android upgrades a preview APK, verify the installed version, app
  settings and previously consented local identity manually. Passing APK
  signature and CI checks is not itself an Android hardware upgrade test.

## Technical contract

- `-PedenOfflinePreview=true` and
  `-PedenOfflinePreviewVersionCode=<200000 + Actions run number>` must appear
  together; Gradle fails closed if either is missing or if the private
  internal signer version override is supplied.
- The separate `.preview` ID and launcher label apply only to **debug**.
  The ordinary `com.edenpractical.comms` baseline code and the private
  internal-test workflow are unchanged.
- APK is signed with the public AOSP test key using the existing project
  signing script. Its certificate, exact package, exact monotonically
  allocated code, and source revision are verified before artifact upload.
- Unit tests, security-boundary guards, and the debug permission checks run;
  the artifact publishes an APK hash and public metadata, no private keys.
- No emulator/physical phone validation is implied. Independent LAN security
  review and two-phone feature QA remain separate.

**Rollback:** revert the preview workflow/Gradle opt-in/evidence changes.
This does not alter package identity or signing for ordinary Eden Comms
builds, nor any saved data on testers' existing main application.
