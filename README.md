# Eden Comms

**Working name:** Eden Comms (EC) — an Eden Practical Android communications utility.

> **DEVELOPMENT PROTOTYPE — NOT A PRODUCTION SECURE MESSENGER.** The Android debug build supports an experimental, foreground-only two-phone pairing test (two in-person QR scans, mutually pinned TLS 1.3 and a temporary exchange of four fixed test phrases). It cannot send arbitrary messages or voice. Real-device compatibility and independent security review are still pending; **do not use for sensitive communications**. Production text, connection and PTT capability switches remain off.

## Product goal

A simple, accessible way for two people nearby to **Connect → Communicate → Disconnect**, with no accounts, app-operated servers, analytics or advertising. Planned connectivity: local Wi-Fi, Bluetooth, Wi-Fi Direct. Text first; optional push-to-talk after verified two-phone messaging works.

## Current implemented prototype (development branch)

- Native Kotlin + Jetpack Compose home, connection preview, settings, offline help and opt-in local device identity.
- Experimental local pairing: current Wi-Fi + QR identity exchange + test-only TLS/session checks and in-memory synthetic phrase IDs. This is not audited real messaging; no contacts, chat history, voice/PTT, saved peer trusts, Bluetooth or Wi-Fi Direct yet.
- **Permissions now requested:** `INTERNET` (Android also requires it for local TCP), `ACCESS_NETWORK_STATE` (Wi-Fi selection), and `CAMERA` (user-initiated QR scan). No microphone, Bluetooth, contacts or location permissions in the current preview APK.
- Theme preference is stored locally; Android Keystore identity can persist once explicitly created. App manifest disables backup and cleartext traffic with data-transfer exclusion rules; physical-device behavior still needs verification.
- Android debug CI: JVM tests, security boundary checks, packaged permission audit, strict direct-dependency baseline, public-safe launch evidence, and APK checksum. **These do not prove there are zero SDK data transfers or establish security certification.**
- Source code development lives in stacked **draft pull requests**, not all merged into `main`. Review the branch/commit and matching CI before using an artifact.

## Build

Requires Android SDK API 37.0, Android build tools 36.0.0, JDK 17+, Gradle 9.3.1.
From a clone, run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

The debug APK will be under `app/build/outputs/apk/debug/app-debug.apk`.
The GitHub Actions workflow publishes a **debug APK artifact**, SHA-256, a permissions/dependency evidence report, and a resolved runtime dependency tree after a successful build.
A Gradle Wrapper is not yet committed; build environment and packaging verification remain open until CI confirms success.

The preview includes an in-app **Privacy & data** explainer under Settings and How it works. It is not yet a publicly hosted privacy policy; publishing requires verified publisher details and a final security/data-flow review.

## Roadmap and safety

- [Product vision](docs/PRODUCT_VISION.md)
- [Milestones M0–M6](docs/DEVELOPMENT_ROADMAP.md)
- [Security threat model and QA](docs/SECURITY_AND_QA.md)
- [Decisions log](docs/DECISIONS.md)
- [M0 development status](docs/M0_EXECUTION_STATUS.md)
- [Google Play / North America launch plan](docs/GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md)
- [L0 data handling and SDK inventory](docs/L0_DATA_HANDLING_AND_SDK_REGISTER.md)
- [Privacy policy working draft — NOT PUBLISHED](docs/drafts/EDEN_COMMS_PRIVACY_POLICY_WORKING_DRAFT.md)
- [M2g: TLS channel-binding compatibility diagnostic](docs/M2G_TLS_EXPORTER_COMPATIBILITY_DIAGNOSTIC.md)

Transport traffic visibility, peer metadata, compromised devices and a recipient recording messages are outside what end-to-end encryption alone can protect. We will never market guarantees unsupported by testing.

## Open source and contributions

The repository is public. Being public does not by itself grant permission to reuse code under an open-source license; a license decision is pending. Do not commit personal chat logs, private keys, signing materials, or sensitive network captures.
