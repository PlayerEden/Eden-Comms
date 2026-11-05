# Eden Comms — no-phone engineering batch + consolidated device gate

**Updated:** 2026-11-05. **Release status:** development-only prototype, NOT an independently reviewed secure messenger.

## Why batching is useful

The current user cannot access both phones during ordinary work hours. We can implement and verify **code-level** changes incrementally on branches with JVM/Android CI while deliberately reserving **one larger, documented real-phone session** for hardware-only evidence. Every source change still needs regression CI and review; postponing physical QA is **not** permission to ship unverified communication features.

## Completed on `main` before this batch

| Work | Code / CI state | Hardware evidence |
| --- | --- | --- |
| Prior stacked branch integration and rollback | PR #25 and preserved legacy documents via #28; `main` green | Two-phone pairing NOT TESTED |
| APK identity evidence | PR #29, source package/version and signer certificate reports | In-place update NOT TESTED |
| TCP/heartbeat/QR fixed-phrase simulations | PR #30, full JVM / APK CI | Real radio, TLS and two-way phrase receipts NOT TESTED |
| Two-step pairing and fixed-phrase UI polish | PR #31, Android/JVM QA | Landscape, 200% font, TalkBack, CameraX physical QA NOT TESTED |
| Explicit per-phone QA summary and 16-scenario worksheet | PR #32, on-demand copy of bounded metadata, no peer identity/QR copy | QA reports from two actual phones NOT COLLECTED |

## This batch's internal test APK engineering

- **M2l — internal signing pipeline:** a separate, *manually triggered main-only* workflow for a dedicated long-lived test certificate, with secrets isolated from Gradle, APK re-sign and pinned certificate verification. It uses a monotonic versionCode, artifact checksums and no normal PR secrets. Ordinary debug CI remains ephemeral.
- **Must be done by a human later:** generate a dedicated test signing keystore outside the repo; configure `internal-test` protected environment secrets and approval; make two manual signed runs; prove a real in-place upgrade. Until then, issue #27 **remains open**.
- Use [M2l setup and safety documentation](M2L_INTERNAL_TEST_SIGNING.md) and [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27).

## Current M2n no-phone transcript work (this branch)

- The fixed-phrase UI now has an immutable, bounded **in-RAM session-only ledger**: exact known IDs only, distinct local TLS writes and local receives, at most 8 visible activity entries and no message history after End.
- Separate event-admission checks reject stale callback generations, late READY after END and phrases before READY; full 8-bit phrase/control field negative tests add packet fuzz coverage.
- Full technical scope and independent device/authorization gates: [M2n ledger QA](M2N_EPHEMERAL_TRANSCRIPT_EVENT_QA.md).
- **This is not a production conversation subsystem.** Typed user messages and audio are still disabled and will require separate reviewed cryptography, authorization and real hardware testing.

## Other safe development work we can do with no phone

1. Build code-only policy, protocol and UI regression tests, including malformed QR/frame cases, retries, timeouts, stale callbacks, lifecycle cleanup and low-memory/large-font navigation checks where automation supports them.
2. Continue production-independent **UI groundwork** (help tooltips, onboarding, empty-state guidance and true session-only conversation model). Do not expose a functional free-text send button until the authenticated live approval adapter and full real-device tests pass.
3. Define interfaces and isolated JVM simulations for future cryptographically reviewed authenticated bilateral approval, transport-neutral message processing and ephemeral conversation state. **Never** call test-only simulated approval a finished secure protocol.
4. Keep distribution readiness source-backed: signing fingerprints, privacy disclosures, dependency/license auditing, Play export/legal checklist and release/rollback steps.

## Work that cannot be signed off in CI alone

- Android Keystore + mutual TLS 1.3 + exact peer certificate pins on both actual phones, two QR rounds, READY and fixed phrase receipt observations in each direction; actual Wi-Fi with router WAN disconnected and cellular off.
- Wrong/stale/self QR, hostile LAN, certificate mismatch, networking changes, cancellation/background/camera denial and older-version behavior on real devices.
- Actual clean-install and signed upgrade preservation of local identity/theme, package-manager error handling, realistic battery/latency testing.
- Real arbitrary typed text and voice/audio: **additional independent protocol review, authentic bilateral human consent, secure message transport and separate hardware acceptance** required. Do not activate `ReleaseCapabilities` based on design documents, successful JVM tests or simulator transcripts.

## Later physical test plan

Use [the M2k worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md). Record PASS / FAIL / BLOCKED / NOT TESTED on **both** devices. Treat any failed high-risk case as a blocker and fix with a new PR + retest affected cases, rather than silently checking it off. The first consolidated test is for the debug-only fixed-phrase prototype; typed text/PTT need a **separate** later milestone test after review.

## GitHub process

- Do work in small, isolated PRs; confirm final-head CI and merged-`main` CI; preserve commit history and checkpoints.
- Group **device tests**, not unreviewed code changes. Do not accumulate a huge untested PR stack.
- Current source of truth: [development roadmap](DEVELOPMENT_ROADMAP.md), [physical issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26), [signing issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) and [security gates](SECURITY_AND_QA.md).
