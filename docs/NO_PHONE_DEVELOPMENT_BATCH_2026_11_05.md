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

## M2n session-only debug transcript (PR #35 integrated)

- The fixed-phrase UI now has an immutable, bounded **in-RAM session-only ledger**: exact known IDs only, distinct local TLS writes and local receives, at most 8 visible activity entries and no message history after End.
- Separate event-admission checks reject stale callback generations, late READY after END and phrases before READY; full 8-bit phrase/control field negative tests add packet fuzz coverage.
- Full technical scope and independent device/authorization gates: [M2n ledger QA](M2N_EPHEMERAL_TRANSCRIPT_EVENT_QA.md).
- **This is not a production conversation subsystem.** Typed user messages and audio are still disabled and will require separate reviewed cryptography, authorization and real hardware testing.

## M2o transport-neutral lifecycle model (PR #36 merged)

- Added an actual Kotlin **state-only** attempt reducer with monotonic generations and exact token identity; represents status for future LAN/Bluetooth/Wi-Fi Direct methods without creating any radio connection.
- Rejects duplicate/out-of-order lifecycle transitions, foreign or stale callbacks and callbacks after termination. Concurrent cancellation/READY behavior and planned method metadata are covered by JVM tests.
- There is **no** arbitrary message, packet, audio, encryption or authorization API. The reducer is intentionally **unwired to the live Android UI/transport**; CI security review-boundary checks reject accidental use in app main sources until a separate reviewed integration.
- [M2o model and test matrix](M2O_TRANSPORT_NEUTRAL_LIFECYCLE.md). Hardware testing and real transport adapters remain pending.

## M2p virtual transport adapter and fault-injection QA (PR #37 merged)

- Added a **JVM-test-only** virtual adapter for the M2o lifecycle, with deterministic delayed event callbacks and explicit simulated LAN/Bluetooth/Wi-Fi Direct methods. It does **not** activate any radio or crypto.
- Fault matrix exercises delayed READY after cancellation, same-millisecond callback ordering, early/duplicate event rejection, 90-second timeout, network failures, stale callbacks crossing attempt reset, background exit, bounded queue and seeded cancellation stress.
- The security script forbids moving this simulator into Android `app/src/main` or referencing it from real communication paths. Both `ReleaseCapabilities` and the real network/debug-only boundaries remain unchanged.
- [M2p fault matrix and limits](M2P_VIRTUAL_TRANSPORT_FAULT_MATRIX.md).

## M2q actual debug-LAN connection stage observability (PR #38 merged)

- The real fixed-phrase LAN test now emits **fixed progress labels** for TLS negotiation, peer identity checking, reciprocal QR proof and the final READY exchange. UI only accepts progress for the current unfinished attempt, without implying verification was successful.
- Disconnects and handshake failures are categorized through safe enum values: timeout vs READY rejection, Wi-Fi changes, peer silence, ten-minute session limit, malformed packets and transport interruption.
- The per-phone opt-in QA report now includes last operation **started** and final fixed failure/end codes, never raw IPs, QR data, peer identity, messages or exception text.
- No production text/voice or Bluetooth/Wi-Fi Direct transport was activated. See [M2q LAN status + failure QA](M2Q_LAN_STATUS_AND_FAILURE_QA.md); physical T01–T16 acceptance remains NOT TESTED.

## M2r Wi-Fi continuity and bounded foreground cancel (PR #39 merged)

- Fail closed when original Wi-Fi Network / IPv4 / prefix changes while QR scanning, waiting for peer, retrying TCP, TLS negotiating, verifying pins/QR, or just before READY. Best-effort ~2-second watcher during pre-READY and existing heartbeat after READY; no roaming across a test session.
- User **End test** arms an independent ~1.2-second socket close before queueing best-effort CLOSE. Scheduler or writer rejection closes immediately; stale CameraX/session callbacks are guarded.
- Safe `WIFI_CHANGED` classification and JVM boundary tests, without new permissions, network services or production capabilities.
- Physical Android timing and two-phone behavior are still NOT TESTED: [M2r audit](M2R_WIFI_CANCELLATION_RECOVERY_QA.md).

## M2s product-facing chat UI (PR #40 merged)

- **Home → Text now opens the first true native conversation layout** with an editable composer, outgoing/local bubbles, optional labeled fictional incoming examples, 280-character counter, local time labels, and clear/end controls.
- Messages are **NOT sent**: this is an explicit local presentation sandbox, never a peer session, delivered receipt or authorization. No transport/crypto/database dependencies are permitted in the preview by CI security gate.
- All text resides only in in-memory Compose `remember` state and is cleared on End, foreground exit, or navigation away. Preview history is capped at 12 entries; `ReleaseCapabilities` remain false.
- Source implementation, privacy disclosures and next core feature gates: [M2s chat preview](M2S_CHAT_UI_LOCAL_PREVIEW.md). Two actual phones and secure text acceptance remain NOT TESTED.

## M2t canonical message statuses and test-only receipt contract (this PR)

- Home → Text now includes optional **What will message statuses mean?** with clear current-vs-future labels. Actual chat entries still say **Local preview • not sent**; no fake received/delivered/read messages.
- The review-only JVM reducer demonstrates status ordering and stale/failure rejection with numeric IDs only, capped metadata and explicit simulated callbacks. It cannot send messages, verify approvals, or authenticate receipts; CI forbids inclusion in Android main sources.
- Detailed [M2t status and future real-message gates](M2T_MESSAGE_DELIVERY_STATUS_CONTRACT.md). Independent protocol/security review and actual two-phone messaging remain pending.

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
