# Eden Comms

**Working name:** Eden Comms (EC) — an Eden Practical Android communications utility.

> **DEVELOPMENT PROTOTYPE — NOT A PRODUCTION SECURE MESSENGER.** The Android debuggable build now has a functional prototype of short two-way **test** text over the QR-pinned TLS 1.3 session, after bilateral approval and matching final confirmations. It also keeps the four fixed test phrases. It is **unreviewed and not physically verified**; do not use for sensitive or emergency communications. Production connection, normal text and PTT capability flags remain off. See [M3e](docs/M3E_DEBUG_TWO_WAY_TEXT.md).

## Product goal

A simple, accessible way for two people nearby to **Connect → Communicate → Disconnect**, with no accounts, app-operated servers, analytics or advertising. Planned connectivity: local Wi-Fi, Bluetooth, Wi-Fi Direct. Text first; optional push-to-talk after verified two-phone messaging works.

## Current implemented prototype (pre-beta source, not a public release)

- Native Kotlin + Jetpack Compose home, connection preview, settings, offline help and opt-in local device identity.
- **Home → Text** now opens the same nearby debug QR/TLS pairing flow as **Connect nearby**, and after both people approve/confirm a session, shows short typed test messages in a chat-first screen. Real TLS-written and peer-received events are labeled separately. Preset phrases and diagnostics remain behind an optional disclosure. This is **unreviewed debug-only** communication, not a secure release. The older fictional/local-only layout sandbox is preserved under Settings → Developer tools → Text layout sandbox (and never transmits). [M3f notes](docs/M3F_CHAT_FIRST_EXPERIENCE.md).
- **Home → Connect Nearby**: experimental local Wi-Fi + reciprocal QR identity exchange + pinned TLS/session checks, then **Approve on both phones** and both final confirmations before **four presets OR short typed text** appear. Real debug text bubbles show actual local TLS writes and received packets, not fake receipts. Chat disappears on disconnect; copied QA includes only counts. This is an **unreviewed, debug-only functional experiment**, not approved production messaging; no voice/PTT, saved peer trusts, Bluetooth or Wi-Fi Direct yet.
- **Permissions now requested:** `INTERNET` (Android also requires it for local TCP), `ACCESS_NETWORK_STATE` (Wi-Fi selection), and `CAMERA` (user-initiated QR scan). No microphone, Bluetooth, contacts or location permissions in the current preview APK.
- Theme preference is stored locally; Android Keystore identity can persist once explicitly created. App manifest disables backup and cleartext traffic with data-transfer exclusion rules; physical-device behavior still needs verification.
- Android debug CI: JVM tests, security boundary checks, packaged permission audit, strict direct-dependency baseline, public-safe launch evidence, and APK checksum. **These do not prove there are zero SDK data transfers or establish security certification.**
- The original stacked development branches were integrated into `main` with rollback checkpoints preserved; see [the v0.2.12 integration checkpoint](docs/INTEGRATION_CHECKPOINT_V0_2_12.md). Further no-phone CI and UI improvements build on this baseline. Review exact commit, signing identity, matching CI and on-device QA before installing.
- The debug-only test result screen can copy a strictly limited per-phone observation (never QR/network identity data) to support a later consolidated [two-phone QA worksheet](docs/M2K_BATCH_PHONE_QA_WORKSHEET.md). It is opt-in and the system clipboard has its own privacy limitations.

## Build

Requires Android SDK API 37.0, Android build tools 36.0.0, JDK 17+, Gradle 9.3.1.
From a clone, run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

The debug APK will be under `app/build/outputs/apk/debug/app-debug.apk`.
The normal GitHub Actions workflow publishes a **transiently debug-signed APK artifact**, SHA-256, signer certificate fingerprint, a permissions/dependency evidence report and a resolved runtime dependency tree after a successful build. **This ordinary APK is not promised to upgrade over older CI APKs.** A separate [owner-triggered, secret-backed internal test signing path](docs/M2L_INTERNAL_TEST_SIGNING.md) is scaffolded but requires a dedicated persistent key and protected GitHub environment secrets before use; it is not a release or proof of upgrade compatibility.
A Gradle Wrapper is not yet committed; build environment and packaging verification remain open until CI confirms success.

The preview includes an in-app **Privacy & data** explainer under Settings and How it works. It is not yet a publicly hosted privacy policy; publishing requires verified publisher details and a final security/data-flow review.

## Roadmap and safety

- [Product vision](docs/PRODUCT_VISION.md)
- [M3e: working debug-only two-way text prototype; physical QA deferred](docs/M3E_DEBUG_TWO_WAY_TEXT.md)
- [M3f: chat-first navigation, Unicode composer and session cleanup](docs/M3F_CHAT_FIRST_EXPERIENCE.md)
- [Milestones M0–M6](docs/DEVELOPMENT_ROADMAP.md)
- [Security threat model and QA](docs/SECURITY_AND_QA.md)
- [Privacy and security charter — no-ship requirements](docs/PRIVACY_SECURITY_CHARTER.md)
- [Data retention and expiry — future per-chat design](docs/DATA_RETENTION_AND_EXPIRY.md)
- [Decisions log](docs/DECISIONS.md)
- [M0 development status](docs/M0_EXECUTION_STATUS.md)
- [Google Play / North America launch plan](docs/GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md)
- [L0 data handling and SDK inventory](docs/L0_DATA_HANDLING_AND_SDK_REGISTER.md)
- [Privacy policy working draft — NOT PUBLISHED](docs/drafts/EDEN_COMMS_PRIVACY_POLICY_WORKING_DRAFT.md)
- [M2g: TLS channel-binding compatibility diagnostic](docs/M2G_TLS_EXPORTER_COMPATIBILITY_DIAGNOSTIC.md)
- [M2h: review-only approval transcript and decision simulator](docs/M2H_APPROVAL_TRANSCRIPT_AND_SIMULATOR.md)
- [v0.2.12 integration, test APK, signing caveat and rollback checkpoint](docs/INTEGRATION_CHECKPOINT_V0_2_12.md)
- [Consolidated two-phone test worksheet (NOT TESTED)](docs/M2K_BATCH_PHONE_QA_WORKSHEET.md)
- [Internal signed test APK setup, safety and human gate](docs/M2L_INTERNAL_TEST_SIGNING.md)
- [Current development batch plan (code vs future hardware/security gates)](docs/NO_PHONE_DEVELOPMENT_BATCH_2026_11_05.md)
- [M2o transport-neutral lifecycle groundwork — status model only, unwired](docs/M2O_TRANSPORT_NEUTRAL_LIFECYCLE.md)
- [M2p JVM-only virtual transport adapter and fault matrix (not real radios)](docs/M2P_VIRTUAL_TRANSPORT_FAULT_MATRIX.md)
- [M2q real debug LAN step labels, categorized disconnects, and safe QA fields](docs/M2Q_LAN_STATUS_AND_FAILURE_QA.md)
- [M2r Wi-Fi roaming and bounded test cancellation audit (device QA pending)](docs/M2R_WIFI_CANCELLATION_RECOVERY_QA.md)
- [M2s actual chat UI screen, local preview, and real-text security gates](docs/M2S_CHAT_UI_LOCAL_PREVIEW.md)
- [M2t message-status guide, future ACK meanings and review-only delivery test contract](docs/M2T_MESSAGE_DELIVERY_STATUS_CONTRACT.md)
- [M2u actual bounded UTF-8 TEXT/ACK parser (unwired) and real LAN security gate](docs/M2U_BOUNDED_TEXT_WIRE_AND_SECURITY_GATE.md)
- [Core M3 real secure LAN text implementation and independent review tracking](https://github.com/PlayerEden/Eden-Comms/issues/42)
- [M2v actual two-phone debug approval flow, security limits and physical QA](docs/M2V_DEBUG_BILATERAL_APPROVAL_TEST.md)
- [M2w required matching TLS 1.3 RFC 9266 exporter binding before debug READY](docs/M2W_DEBUG_TLS_CHANNEL_BINDING_MATCH.md)
- [M2x session-bound Approve/Decline controls for debug fixed-phrase LAN](docs/M2X_DEBUG_SESSION_BOUND_APPROVAL.md)
- [M2y automatic final mutual confirmation before any fixed-phrase send](docs/M2Y_DEBUG_MUTUAL_FINAL_CONFIRMATION.md)

Transport traffic visibility, peer metadata, compromised devices and a recipient recording messages are outside what end-to-end encryption alone can protect. We will never market guarantees unsupported by testing.

## Open source and contributions

The repository is public. Being public does not by itself grant permission to reuse code under an open-source license; a license decision is pending. Do not commit personal chat logs, private keys, signing materials, or sensitive network captures.
