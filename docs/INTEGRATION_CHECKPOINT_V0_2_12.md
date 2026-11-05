# Eden Comms — v0.2.12 pre-beta integration checkpoint

**Recorded:** 2026-11-04  
**Status:** Source integration and debug QA only. Not a production, independently reviewed encrypted messenger.

## Why this baseline exists
The Android prototype was developed as a *stack* of draft pull requests, while `main` still contained only the original README. The tested cumulative source is a **linear descendant** of the old `main`: 138 commits ahead and 0 behind as checked on November 4. This document records a full-history integration route, rather than manually squashing or cherry-picking experimental security changes.

## Evidence and rollback
- Old `main` commit: `c3b0422fa86019f4ba564175764aa3b96d3b2d7b`. Immutable-by-process branch: `checkpoint/pre-integration-main-2026-11-04`.
- Full development source checkpoint: `9d677a2819f4540dde204b43edd2732ef7fc3177`, branch `checkpoint/v0.2.12-tested-source`.
- PR #24, the last original stacked draft, has Android CI workflow run [41913469652](https://github.com/PlayerEden/Eden-Comms/actions/runs/41913469652), run number 65, concluding **success** (JVM tests, debug assemble, security boundary, permission/dependency/privacy checks, SHA-256, published debug APK). That PR workflow built its synthetic merge ref; check the artifact metadata against the precise run when installing.
- The workflow artifact name is `eden-comms-security-prep-cc19a373a0b628b280528db2703d21148fa8fa33`, artifact ID `13364745067`, with a reported expiration of **2026-11-18**. Download its ZIP through the Actions run; the ZIP includes `app-debug.apk` and `app-debug.apk.sha256`.
- Historic PRs are retained for discussion and review provenance: #1–#14, #16–#18 and #20–#24; #15 was closed and superseded by #16. Do not mistake their separate draft states for independently tested production releases.
- Integration should use a **merge commit** if merging a consolidation PR into `main` so all original commits remain reachable. Do **not** delete checkpoints. Future regression rollback should use a new revert/fix PR, not force-push `main`.

## Frozen prototype properties
- Android `applicationId` and namespace: `com.edenpractical.comms`. Do not rename as a side effect of integration.
- Integration source version: `0.2.12-approval-review-only`, `versionCode 11`, `targetSdk 36`.
- `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` are all **false**. Security review-only proposal files belong in JVM test sources, and CI enforces their separation from the packaged app.
- Only the explicitly debug-gated local Wi-Fi / two-QR / mutually pinned TLS 1.3 prototype and **four fixed harmless test phrases** are available for experimental physical QA. No free text, production chat, production voice/PTT, cloud transport or publicly warranted security claims.
- As of this checkpoint, the recorded real-device evidence is **one handset screen** reporting a TLS 1.3 test success for v0.2.3. Both-handset status, v0.2.12 four-phrase test, mismatch cases and Wi-Fi without WAN are **not yet verified**.

## APK installation caveat — important
The current Actions workflow creates an ordinary **debug-signed** APK and does **not** configure a durable private signing key. The package name can remain identical while the signature changes between CI jobs. Android then rejects an in-place upgrade with a signature mismatch. **Do not promise seamless APK updates or ask a tester to uninstall an existing install without warning:** uninstalling can erase app-local settings and the Android Keystore identity. A persistent, securely stored test-signing identity must be established before claiming update-safe builds; private signing keys must not be committed to this public repository.

The published artifact is a ZIP. Download the ZIP from the successful run, extract `app-debug.apk`, and check its hash against the bundled `app-debug.apk.sha256` before testing. This is an experimental debug build, not suitable for confidential messages.

## Next acceptance gates
1. Confirm the cumulative integration PR's own CI on its **final head**, then confirm `main` CI after integration. Keep this doc, the two checkpoint branches and the PR trace.
2. Test on **two separate physical Android phones**: both QR scans, both devices reporting matched peer identity and mutual TLS success, all four fixed phrases sent and received **in both directions**, visible counters, end-test propagation, loss of Wi-Fi, wrong/stale QR and role timing. Capture sanitized screenshots and version labels; never upload private QR tokens, identities or secrets.
3. Prove LAN operation with **WAN disconnected** (do not infer from ordinary Wi-Fi testing).
4. Resolve the APK signing/update compatibility before distributing incremental test builds to users who must preserve their local identity.
5. Commission an independent protocol/security assessment and separately integrate **actual** authenticated bilateral approval before unrestricted text or voice can ever be enabled.
