# Eden Comms

**Working name:** Eden Comms (EC) — an Eden Practical Android communications utility.

> **M0 DEVELOPMENT FOUNDATION — NOT A MESSENGER YET.** The current shell cannot connect, send texts, encrypt messages or transmit voice. Do not use this project for sensitive communications until its networking, encryption, verification and independent review milestones are complete.

## Product goal

A simple, accessible way for two people nearby to **Connect → Communicate → Disconnect**, with no accounts, app-operated servers, analytics or advertising. Planned connectivity: local Wi-Fi, Bluetooth, Wi-Fi Direct. Text first; optional push-to-talk after verified two-phone messaging works.

## Current M0 implementation

- Native Kotlin + Jetpack Compose Android shell with Home / Connect / Settings / How It Works.
- Restrained Eden Practical visual identity and a basic launcher icon.
- Honest status: *Not connected*; connection controls explain that real communication is not implemented.
- Simple locally stored appearance preference.
- No requested network, Bluetooth, location or microphone permissions.
- Explicit cloud-backup and device-transfer exclusion policies (must still be tested later).
- Debug build and unit-test GitHub Actions workflow.

## Build

Requires Android SDK API 37.0, Android build tools 36.0.0, JDK 17+, Gradle 9.3.1.
From a clone, run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

The debug APK will be under `app/build/outputs/apk/debug/app-debug.apk`.
The GitHub Actions workflow publishes a **debug APK artifact** with SHA-256 after a successful build.
A Gradle Wrapper is not yet committed; build environment and packaging verification remain open until CI confirms success.

## Roadmap and safety

- [Product vision](docs/PRODUCT_VISION.md)
- [Milestones M0–M6](docs/DEVELOPMENT_ROADMAP.md)
- [Security threat model and QA](docs/SECURITY_AND_QA.md)
- [Decisions log](docs/DECISIONS.md)
- [M0 development status](docs/M0_EXECUTION_STATUS.md)

Transport traffic visibility, peer metadata, compromised devices and a recipient recording messages are outside what end-to-end encryption alone can protect. We will never market guarantees unsupported by testing.

## Open source and contributions

The repository is public. Being public does not by itself grant permission to reuse code under an open-source license; a license decision is pending. Do not commit personal chat logs, private keys, signing materials, or sensitive network captures.
