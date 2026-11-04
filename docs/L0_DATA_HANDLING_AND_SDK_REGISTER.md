# Eden Comms — L0 Data Handling & Third-Party Inventory

**Prepared:** November 4, 2026  
**Scope:** checked-in source at the L0 evidence baseline; the current Android debug build is *not* a production messenger.  
**Status:** Engineering inventory, **NOT** an approved Google Play Data safety response, completed privacy policy, independent traffic audit, vendor data certification, or legal review.

## Why this inventory exists

Google Play requires the publisher to disclose collection and sharing by the app **including SDKs**. Permissions are NOT the same as collected data; an `INTERNET` permission or a local QR doesn't by itself show whether the app sends data to developer/third-party systems. In-app on-device access usually has a different disclosure status from off-device transmission, and end-to-end encrypted sender-to-recipient traffic may be excluded from "collection" under Google's exact criteria. This determination requires a reviewed live app and actual SDK behaviors before filling the Play Console form.

Current Play policy: https://support.google.com/googleplay/android-developer/answer/10787469

## Application data flows — REVIEW REQUIRED

| Data type / process | Current purpose / source | Persisted? | Leaves phone? | Human-review questions |
| --- | --- | --- | --- | --- |
| Local Keystore private identity | Explicit first-use setup for local TLS identity | **Yes**, app's persistent Android Keystore (subject to device behavior) | Intended no; only public certificate/fingerprint travels in QR/TLS | Key invalidation, backups/restores, OEM/hardware compatibility, reset and rotation |
| QR invite: public fingerprint, short nonce, LAN IPv4 | In-person setup to identify one phone and connect | Transient Compose/worker memory; cleared on session exit, best effort | **Yes**: QR image visibly shown to another person; scanned locally on other phone | Public QR replay, screenshots, connection metadata exposure; no accidental crash/log persistence |
| Reciprocal QR reply | Mutual fingerprint/nonce confirmation | Transient, until session authenticated/ended | **Yes**: visible to the other phone's camera | Role binding and freshness, no retained QR images |
| TLS session/proof control packets | Verify two peer certificates and challenge/QR session binding | Session memory only | **Yes**, directly between the two phones over local Wi-Fi | Test untrusted LAN, crypto proof, packet capture, no third-party endpoints |
| Fixed test phrase IDs and test counters | Development-only two-phone prototype | In-memory at most 8 visible lines, wiped on stop/background; UI counts not durable | **Yes**, directly to paired phone over authenticated TLS after checks | Physical device proof still pending; not user text, not production |
| App theme preference | Dark/light UI toggle | **Yes**, private SharedPreferences `eden_comms_ui` | Intended no | Device transfer/backup exclusion, no analytics SDK |
| Camera preview | Scans peer QR only while pairing screen is active | No camera frame storage intentionally | Intended no; QR data is processed locally | Permission prompt, camera lifecycle, accessibility, unexpected camera SDK data flow |
| App IP / Wi-Fi connection metadata | Network route, same subnet and TCP session checks | Transient | Exposed at network level to router/peer; not knowingly uploaded | Network capture; Android/OEM services may have independent connectivity |
| Real user messages, saved contacts, PTT audio, recorded voice | **Not implemented / disabled** | No | Not via release features | Re-audit data safety and permissions before adding any of these |
| Publisher-operated backend, ads, push, analytics, login | **Not in project design** | No by design | No app-controlled endpoint intended | Prove no unexpected vendor SDK/OS effects with offline network capture |

**Do not write "zero data collected" or "end-to-end encrypted messages" in a public listing based on this table.** Actual disclosure depends on runtime behavior, final feature set, dependencies, users' consent, and Google's policy definitions.

## Declared manifest permissions — approved L0 preview

| Permission | Present now | Why |
| --- | --- | --- |
| `android.permission.INTERNET` | Yes | Android's network-stack permission, including **local** TCP sockets; not a claim of WAN use |
| `android.permission.ACCESS_NETWORK_STATE` | Yes | Select/inspect current Wi-Fi network |
| `android.permission.CAMERA` | Yes | Explicit QR camera scan with runtime permission |

No Bluetooth/microphone/contacts/location permissions are intended at L0. An automated **exact allowlist** in `scripts/audit_play_surface.py` checks APK merged permissions; a new or changed permission must trigger code review and explicit baseline update.

App manifest currently has `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, root exclusions for both cloud backup/device transfer and legacy backup, and just the launcher activity exported. This is a source/packaging configuration claim; verify OS backup behavior on real devices.

## Direct Android dependencies (automatic evidence in CI)

Sources: `app/build.gradle.kts` and `scripts/play_direct_dependency_baseline.txt`. Current external packages include:

- AndroidX Compose / Activity, AndroidX CameraX (Camera2, Lifecycle, View, Core).
- Kotlin coroutines Android.
- ZXing QR generation/reading core.
- JUnit for local tests.

The BOM, debug-only tooling and exact pinned versions are inventoried by the CI audit. The CI artifacts now additionally include Gradle `releaseRuntimeClasspath.txt` to expose **transitive** dependencies resolved for release runtime, though this is *not* an SPDX/CycloneDX SBOM or proof of license compliance.

### Vendor and license review (NOT YET COMPLETED)

- [ ] Review each direct + transitive component for licensing/NOTICE obligations, dependency provenance, advisories, telemetry, SDK Index notes and data transfer behavior.
- [ ] Distinguish ordinary AndroidX/CameraX/ZXing from Google Play services/analytics: no automatic classification by package prefix.
- [ ] Record SBOM for release, capture exact resolved versions including transitive nodes, perform vulnerability scanning (avoid unsupported "0 vulnerabilities" claims).
- [ ] Decide whether to include any third-party transport services; a Google Play services dependency materially changes independence/privacy analysis and must have owner approval.
- [ ] Review dependency/legal implications of Signal's AGPLv3 if ever considered; it is **not** currently included.

## Review process when anything changes

1. A developer adds a permission, exported component, direct dependency or changes backup/cleartext settings.
2. CI **fails** and includes a non-sensitive `launch-surface.json` / `launch-surface.md` report when possible; no silent permission baseline changes.
3. Review the feature's actual runtime network transfers, retention, notices and whether the user sees a contextual permission/consent explanation.
4. Update this register, source baselines, threat model, privacy policy and Google Play Data safety *before* approving any appropriate allowlist change.
5. Independently recheck the merged release AAB manifest, transitive SDK collection, and applicable laws before final US launch, then Canada and Mexico.

Automated evidence: `scripts/audit_play_surface.py`, `scripts/tests/test_audit_play_surface.py`; GitHub workflow `.github/workflows/android-debug.yml`.

## North America launch dependencies

- **US:** BIS public export/software classification, Play identity/closed testing/target SDK/policies, state privacy & consumer-law applicability, support/incident response.
- **Canada:** PIPEDA and provincial applicability/Quebec requirements, English/French as required, local terms/support.
- **Mexico:** current 2025 LFPDPPP, Spanish/localization and consumer law depending on actual features and publisher processing.

See `docs/GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md` and GitHub issue #19. These areas remain **pending legal review**.

**Go/no-go:** This register is useful evidence for a publisher's compliance process, **not** a legal conclusion, cryptographic audit, completed privacy policy or permission to publish a beta/production messenger.
