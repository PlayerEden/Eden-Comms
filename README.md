# Eden Comms

**Continuing this project in a new chat?** Start at [CONTINUE_HERE.md](CONTINUE_HERE.md) for the current handoff, chronological work log, QA status, and safe next task. The detailed milestone documents remain in `docs/`.

**Current product reference:** [Eden Comms Design Bible](docs/EDEN_COMMS_DESIGN_BIBLE.md)
· [November 10 complete project/CI/UX audit](docs/OVERALL_QUALITY_AUDIT_2026_11_10.md).
Older milestone summaries below are preserved for history and may describe
features as unavailable that now exist in the **debug** app. See the Bible
and [current handoff](docs/CURRENT_HANDOFF.md) for present status.

**Working name:** Eden Comms (EC) — an Eden Practical Android communications utility.

> **DEVELOPMENT PROTOTYPE — NOT A PRODUCTION SECURE MESSENGER.** The Android debuggable build now has a functional prototype of short two-way **test** text over the QR-pinned TLS 1.3 session, after bilateral approval and matching final confirmations. It also keeps the four fixed test phrases. It is **unreviewed and not physically verified**; do not use for sensitive or emergency communications. Production connection, normal text and PTT capability flags remain off. See [M3e](docs/M3E_DEBUG_TWO_WAY_TEXT.md).

- **F4-D live Wi-Fi voice stream (experimental):** The existing large **HOLD TO TALK** button now transmits short, ordered 20ms audio frames *while held* through the **same real two-QR, both-approved TLS 1.3 LAN test session**, and the other phone's speaker can start playout before release. Strict 3-second cap, 240ms buffered audio ceiling, cancellation closes the turn, no saved sound or production permissions. **No distributed radio-floor arbitration or physically verified latency yet**; two-phone testing and independent review remain OPEN. The optional tap mode still offers a record-then-send fallback. [F4-D test guide](docs/F4_D_LIVE_WIFI_PTT_STREAM_2026_11_08.md).

- **F4-C hold to talk:** The actual nearby debug chat now has a big **HOLD TO TALK / RELEASE TO SEND** gesture: touch to record, lift to transmit the same maximum-three-second voice burst on the existing approved Wi-Fi TLS socket. A cancelled gesture does not send the unfinished clip; **Use tap controls instead** provides the original two-tap option and a keyboard/screen-reader alternative. This is **still record-then-send**, not true low-latency streaming or reviewed Bluetooth voice. [F4-C test notes](docs/F4_C_HOLD_TO_TALK_GESTURE_2026_11_08.md).

- **F4-B pre-phone audio reliability:** an immediate/failed recording now returns a clear retry message instead of leaving the voice button on “Finishing”; the button blocks repeat capture during finalization. The receiving phone reports when its **AudioTrack actually starts** rather than treating network reception as speaker playback. **Hide conversation** stops local mic/speaker and suppresses incoming playback while hidden (it does not prevent the other phone transmitting); temporary voice/text frame copies are wiped after test TLS reads/writes as best effort. No new permissions or production voice capability. [F4 test notes](docs/F4_LIVE_LAN_VOICE_BURST_TEST_2026_11_08.md).

- **F4 first two-phone walkie-talkie burst:** after the EXISTING live same-Wi-Fi two-QR TLS connection and both approvals, the debug chat now includes **START TALKING → SEND VOICE BURST**. The phone captures at most three seconds of 16kHz mono audio and sends ordered frames through the **same verified test TLS socket**. The other phone attempts local speaker playback; no account, background service, saved audio or new pairing. This is a **short recorded burst, not yet continuous low-latency hold-to-talk**, and physical two-phone audio QA / independent protocol review are still open. RELEASE voice remains disabled. [Hands-on F4 testing and limitations](docs/F4_LIVE_LAN_VOICE_BURST_TEST_2026_11_08.md).

- **F2-K Bluetooth connection verification visibility:** the ACTUAL diagnostic RFCOMM/TLS worker now reports a privacy-safe, five-step progress label **only after each real certificate-pin, QR, TLS exporter, transcript and bilateral-approval check succeeds**. The local screen shows the last verified stage so real two-phone testing can identify where setup fails, but does **not** unlock Bluetooth chat or voice. No key, QR, radio address or message content is exported; the socket still closes after the diagnostic. [F2-K notes and next hardware checks](docs/F2_K_LIVE_BLUETOOTH_VERIFICATION_VISIBILITY_2026_11_08.md).

- **F2-J guided Bluetooth connection test:** the existing actual Bluetooth debug screen now presents the in-person **two-QR exchange first**, with clear host/join guidance, role-relevant radio search, readily accessible approval/decline, and an optional advanced raw scan. A finished attempt clears temporary codes and candidate selection. This is **not live Bluetooth text or voice**: the verified diagnostic still closes the RFCOMM socket after both people approve. [Two-phone test notes](docs/F2_J_GUIDED_BLUETOOTH_TWO_PHONE_SETUP_2026_11_08.md).

- **F0-LAN live chat usability:** The actual QR-pinned, both-people-approved debug LAN conversation now supports a **30-minute maximum** (instead of ten), keeps the existing heartbeat/silence/cancel enforcement, and accepts **keyboard Send** using the very same validated text sender and the visible Send button. Source tests and physical QA remain distinct. [Live chat usability changes](docs/F0_LAN_CHAT_TEST_USABILITY_2026_11_08.md).

- **Keep nearby chat on screen (F0-UX):** while this app visibly shows/scans QR codes, checks the link, awaits mutual approval or displays the active test chat, it requests that Android keep the display awake. The request ends at idle/result, on leaving the screen, or when the view is no longer visible. **No background service or WakeLock**; the power button can still lock the phone. The 30-minute test session maximum and stop-on-background remain.

- **Convenient repeat pairing (F0-UX):** after a LAN test ends, use **Try again • Show a new code** or **Try again • Scan a new code** based on the last role. This is a single-tap *fresh setup*, not automatic reconnection: the previous TLS socket, callbacks, QR materials and approval are cleared, and both people must scan/approve again. **Start over** still lets you switch roles. [Roadmap](docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md).\n\n- **Ready for hands-on testing:** each new Preview ZIP now includes [START-HERE-TEST.md](docs/TEST_BUILD_QUICKSTART.md), a short one-phone and two-phone guide for the actual LAN text, ACK and local microphone checks. The guide is stamped with that APK's exact source and version in CI. The Preview app remains development-only.\n\n- **Easy repeatable Android Preview APKs:** The [offline Preview workflow](docs/OFFLINE_PREVIEW_APK_UPDATES.md) copies the working pattern from Sector Rebound/Operation Phosphor: `com.edenpractical.comms.preview`, stable **public AOSP test-only** signing certificate, automatically increasing version codes, and no private key setup. The first Preview installs separately from old Eden Comms; subsequent Preview versions can be installed over Preview without a reset (subject to physical confirmation). **Never use this public key for Play/release or sensitive communications.**

- **F6-U1 safe install-over preparation:** a separate, opt-in owner CLI requires same APK signer, higher versionCode, exact human approval and a repeated read-only check before `adb install -r`; reads back public APK identity afterward. It **never uninstalls** or automatically clears data. A real stable signer and hardware identity-preservation test are still NOT configured/completed. [Owner-only upgrade runbook](docs/F6_U1_EXPLICIT_SAFE_INSTALL_OVER.md).

- **F0-LAN-QA two-phone readiness (debug reporting only):** the actual same-Wi-Fi test's one-phone QA summary now counts peer-app ACK *events* received from the live, mutually confirmed session, without exposing text, peer IDs or certificates. This is not a read/delivery receipt or security acceptance; both phones must still be physically tested. [Two-phone comparison guide](docs/F0_LAN_TWO_PHONE_ACK_QA_EVIDENCE_2026_11_08.md).

- **M5-A automated reliability rehearsal:** Independent virtual two-peer cancellation/approval/late-callback tests, plain JVM loopback socket cleanup, and safe error-to-recovery classification. [M5-A QA and limits](docs/M5_A_TWO_PEER_RELIABILITY_REHEARSAL.md). This does not verify real Android/TLS phone-to-phone behavior.

- **M5-B — bounded TCP retry deadline:** The debug LAN joiner no longer starts a full-length blocking TCP connection attempt at the end of its two-minute pairing retry window, and explicitly rejects zero (unbounded) socket timeouts. [M5-B audit and verification](docs/M5_B_LAN_RETRY_DEADLINE.md). Real two-phone reliability still requires physical QA.

- **M5-C — debug teardown race hardening:** A final synchronized socket-write guard blocks ordinary debug packets when disconnect or decline starts, while retaining graceful CLOSE/DECLINE and forced-close behavior. Suppresses misleading late activity/link-failure reports during local cancellation. [M5-C source audit](docs/M5_C_TEARDOWN_WRITE_GATE.md).

- **M5-D — terminal UI callback protection:** The debug pairing screen now accepts ENDED/FAILED only from the current live attempt and invalidates that attempt before displaying its result, preventing late terminal callbacks from rewriting a completed screen. [M5-D verification](docs/M5_D_TERMINAL_UI_ADMISSION.md).

- **M5-E — fresh restart and callback guards:** Incomplete QR preflight now cleans up ephemeral pairing state, camera callbacks are generation/revision checked, and identity setup cannot auto-continue an outdated role after backgrounding. [M5-E runbook](docs/M5_E_RESTART_CALLBACK_ADMISSION.md). Device validation remains outstanding.

- **M6-D1 local voice test:** Actually records and plays a short sample on one debug phone with user permission, accessible Start/Stop and best-effort privacy cleanup. **Not remote PTT or a secure voice call.** [Audio test](docs/M6_D1_LOCAL_PTT_AUDIO.md), [all-function test-build targets](docs/FUNCTIONAL_TEST_BUILD_TARGETS.md).

- **F4-D2 local PCM frame rehearsal (debug only):** the M6-D1 mic sample is now broken into bounded, ordered **20 ms / 640-byte** frames and played through streaming AudioTrack on the **same phone**; strict ordering/limits, wipe-on-teardown and JVM/source guards. **Not live streaming or remote PTT.** [F4-D2 testing and review gates](docs/F4_D2_LOCAL_20MS_PTT_FRAMES.md).
- **F4-D3 local audio interruption hardening (debug only):** non-blocking microphone reads and 20 ms speaker-frame writes, an eight-second finite playback clock, and cancellation-safe result/worker cleanup for the same-phone demo. **Not live or remote PTT.** [F4-D3 QA and safety gates](docs/F4_D3_LOCAL_AUDIO_DEADLINE_AND_INTERRUPTION.md).

- **F4-D4 future LAN voice frame rehearsal (DEBUG/JVM only):** fixed-size 658-byte candidate PCM packet, strict header and ordering validation, synthetic local two-endpoint tests, and deliberate proof that sequence numbers **do not** protect against replay across sessions. **Unwired to LAN, microphones, Bluetooth and Wi-Fi Direct; no remote PTT or security approval.** [F4-D4 protocol limitations and future gates](docs/F4_D4_FUTURE_LAN_VOICE_FRAME_REHEARSAL.md).

- **F3-B3-A P2P interface-bound two-phone reachability (debug only):** host and joiner TCP tests now use the exact current Android Wi-Fi Direct group interface, reject unsuitable local/subnet routes, and bind sockets to that interface. This also handles Android anonymizing the host owner address. **Zero user data; no P2P TLS, authenticated peer, consent or chat yet.** [F3-B3-A physical QA and security gates](docs/F3_B3_A_P2P_INTERFACE_BOUND_TWO_PHONE_QA.md).

- **F3-B2 same-TLS Wi-Fi Direct QR proof lab:** on two real JVM-loopback TLS sockets with mutually pinned peer certificates, both sides now exchange fresh QR nonce challenges and encrypted full-QR transcript commitments on the **same** connection. A wrong nonce or changed QR timestamp fails. **Still no live radio TLS, human approval, user text or PTT.** [F3-B2 technical handoff](docs/F3_B2_SAME_TLS_QR_PROOF_AND_TRANSCRIPT.md).

- **F3-B1 Wi-Fi Direct QR-pinned TLS JVM lab:** three fresh in-person QR identity frames feed exact expected peer pins into two *real TCP* endpoints running mutual TLS 1.3 with different ephemeral certificates; wrong pin and tampered/expired QR are rejected. **Unwired, JVM loopback only: no live P2P TLS, messages, voice or contact trust.** [F3-B1 verification and review gates](docs/F3_B1_QR_PINNED_REAL_TLS_LOOPBACK_LAB.md).

- **F3-A Wi-Fi Direct TCP reachability (debug only):** temporary group host and target joiner have explicit controls for one real, bounded TCP handshake using Android's current private group-owner address. **No application payload, QR binding, TLS, peer trust, text or voice.** See the [F3-A two-phone test design and blockers](docs/F3_A_WIFI_DIRECT_ZERO_DATA_TCP_REACHABILITY.md) and [canonical full-function roadmap](docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md).

- **F2-I identity-bound future Bluetooth handoff review:** future-only session ownership now requires matching host/join role, peer public certificate pin and transcript commitment on every same-attempt observation; mismatches close the test resource. The [independent Bluetooth session review packet](docs/F2_I_BLUETOOTH_SESSION_REVIEW_GATE.md) maps real live-probe source to the remaining expert/security and Android device gates. **Still no live Bluetooth chat.**

- **F2-H bounded future Bluetooth resource owner:** one-shot debug-only Closeable ownership and exact timeout, disconnect, background, permission-loss and clock-rollback cleanup, with paired JVM TLS stream teardown tests. **Still no retained live RFCOMM socket or Bluetooth chat.** [F2-H engineering handoff](docs/F2_H_BOUNDED_FUTURE_SESSION_OWNERSHIP.md).

- **F2-G encrypted Bluetooth TEXT/ACK lab:** two real JVM TLS 1.3 engines with distinct pinned test identities now exchange encrypted UTF-8 TEXT and peer ACK in both directions, with malformed ciphertext rejection. **Purely debug lab, no Android Bluetooth text yet.** [F2-G evidence and acceptance gates](docs/F2_G_PAIRED_TLS_ENCRYPTED_TEXT_ACK_LAB.md).

- **F2-F future Bluetooth TEXT/ACK protocol rehearsal:** strict 512-byte UTF-8 framed text, zero-payload peer-app ACK, monotonic IDs and bounded pending state, with virtual two-peer JVM tests. **Not connected to RFCOMM, not encrypted by the codec, no live Bluetooth chat.** The [current full-function F0–F7 roadmap](docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md) and [F2-F acceptance/runbook](docs/F2_F_DEBUG_BLUETOOTH_TEXT_FRAME_REHEARSAL.md) track the remaining functional and physical/security gates.

- **F2-E one-use Bluetooth completion receipt:** The verified debug TLS/QR/exporter/two-person-consent path now issues a role- and expiry-bound, single-use diagnostic result, acknowledged only for the active socket token before **immediate closure**. No retained Bluetooth session, chat or voice. [F2-E acceptance gates](docs/F2_E_ONE_USE_BLUETOOTH_PROBE_COMPLETION.md).

- **F2-D paired TLS engine interoperability:** JVM-only test lab uses two ephemeral X.509 identities with real TLS 1.3 engines, opposite certificate pins, fixed encrypted control lengths and wrong-pin rejection. **Not Android radio QA or a security audit; Bluetooth chat remains disabled.** [F2-D test evidence and acceptance gates](docs/F2_D_PAIRED_TLS_ENGINE_INTEROP_TEST.md).

- **F2-C Bluetooth provider readiness:** Live debug TLS control exchanges now fail closed on a closed/half-closed engine, new handshake state, unsupported buffer sizes or incorrect result counts. A 90-second future continuation-budget policy is **unwired**; diagnostic socket still closes after consent. [F2-C acceptance and limitations](docs/F2_C_PROVIDER_READINESS_AND_CONTINUATION_BUDGET.md).

- **F2-B TLS record boundary hardening:** Debug Bluetooth controls now require exactly one bounded TLS 1.3 ciphertext envelope after the pinned handshake, with best-effort cleanup on error. **Still no Bluetooth user messages, no device proof and no independent security signoff.** [F2-B handoff](docs/F2_B_TLS_RECORD_BOUNDARY_REVIEW.md).

- **Full-function development roadmap (active):** [F0–F7 implementation, dependencies, and consolidated physical test plan](docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md). First integration: app-level LAN text ACK after both phones explicitly confirm; Bluetooth/Wi-Fi Direct chat and remote PTT remain unfinished.

## Product goal

A simple, accessible way for two people nearby to **Connect → Communicate → Disconnect**, with no accounts, app-operated servers, analytics or advertising. Planned connectivity: local Wi-Fi, Bluetooth, Wi-Fi Direct. Text first; optional push-to-talk after verified two-phone messaging works.

## Current implemented prototype (pre-beta source, not a public release)

- Native Kotlin + Jetpack Compose home, connection preview, settings, offline help and opt-in local device identity.
- **M4-UI01 — less intimidating in-person UI pass:** retained the original Eden Comms angular olive/dark styling and explicit preview-only warnings while making technical build evidence, lengthy privacy explanations, planned settings and future radio details expandable behind accessible question-mark help. On-device screenshots informed this pass; repeat visual QA is pending. [UI handoff](docs/M4_UI01_PROGRESSIVE_HELP.md).
- **M4-UI02 — guided developer tools and clearer pairing instructions:** this-phone setup tools first, optional Bluetooth/Wi-Fi Direct radio experiments explicitly collapsed, plain-language same-Wi-Fi and approval help, build metadata below primary pairing actions. Corrects the previous approval warning so it accurately admits short typed TEST messages after mutual debug confirmation. No new radio, storage, signing, or release authority. [UI-02 handoff](docs/M4_UI02_GUIDED_TOOLS_AND_PAIRING.md).
- **M4-UI03 — connection progress and recovery:** role-aware three-step guidance shown only as the *real* QR/connection/approval state advances; no approval/trust inferred from elapsed time. Failed/ended sessions now show the recovery action and fresh-code restart first, with optional details and QA report below. No transport, permissions or release authorization changes. [UI-03 handoff](docs/M4_UI03_CONNECTION_GUIDANCE_AND_RECOVERY.md).
- **M4-UI09 — readable navigation headers:** Up to two lines on narrow/large-font Android screens, with protected Back target and unchanged live-session exit safeguards. [M4-UI09 acceptance](docs/M4_UI09_READABLE_TOP_BAR.md).
- **M4-UI08 — responsive Home choices:** Stacks the two communication tiles on narrow/large-font devices and accurately labels experimental text and unavailable voice. Pure UI; no production permission changes. [M4-UI08 acceptance](docs/M4_UI08_RESPONSIVE_HOME_NAVIGATION.md).
- **M4-UI07 — manual-only emulator QA harness:** Safe Android35 screenshot/navigation tests across compact, large-font, landscape and empty sandbox keyboard. First actual emulator execution still open; no real pairing or app messages. [Runbook](docs/M4_UI07_MANUAL_EMULATOR_VISUAL_QA.md).
- **M4-UI06 — app-wide UX audit and interruption clarity:** IME-aware chat navigation, test composer/send ahead of optional receipt diagnostics, and non-sensitive saved-state warning if Android recreates a live QR route. No socket, message, voice or release changes. Cross-repository QA practices documented with explicit unverified-device gates. [M4-UI06 audit/handoff](docs/M4_UI06_FULL_APP_UX_AUDIT_AND_INTERRUPTION.md).
- **M4-UI05 — safe session exit and link status:** shared system/in-app Back confirmation while QR pairing, approval or temporary debug chat is underway; clear local-only status cues, honest fresh-QR restart warnings and Done action on ended results. [UI-05 handoff](docs/M4_UI05_SESSION_EXIT_AND_LINK_STATUS.md).
- **M4-UI04 — readable session-only test chat:** simpler participant/write/receive labels, larger chat text, at-bottom-only automatic scrolling with an explicit Jump to latest, accessible stacked Hide/Clear controls, and the unchanged End chat and disconnect action moved ahead of optional diagnostics. Message history remains RAM-only and no read/delivery receipts are invented. [UI-04 handoff](docs/M4_UI04_CHAT_READABILITY_AND_CONTROLS.md).
- **Home → Text** now opens the same nearby debug QR/TLS pairing flow as **Connect nearby**, and after both people approve/confirm a session, shows short typed test messages in a chat-first screen. Real TLS-written and peer-received events are labeled separately. Preset phrases and diagnostics remain behind an optional disclosure. This is **unreviewed debug-only** communication, not a secure release. The older fictional/local-only layout sandbox is preserved under Settings → Developer tools → Text layout sandbox (and never transmits). [M3f notes](docs/M3F_CHAT_FIRST_EXPERIENCE.md).
- **Home → Connect Nearby**: experimental local Wi-Fi + reciprocal QR identity exchange + pinned TLS/session checks, then **Approve on both phones** and both final confirmations before **four presets OR short typed text** appear. Real debug text bubbles show actual local TLS writes and received packets, not fake receipts. Chat disappears on disconnect; copied QA includes only counts. This is an **unreviewed, debug-only functional experiment**, not approved production messaging; no voice/PTT or saved peer trusts; a separate debug-only Bluetooth inquiry, anonymous signal staging, **two-QR in-person identity preparation** and unverified RFCOMM socket test exist, but **Bluetooth cannot carry Eden Comms messages or voice**; Wi-Fi Direct now has a debug-only 30-second discovery test and an **anonymous temporary device selection preview**. It can read nearby P2P device objects only into RAM for the active scan; users see numbered unverified devices, never names or addresses. Selection is NOT a Wi-Fi Direct connection, pairing, trusted identity, messaging or voice. An additional debug-only Android 10+ test can briefly create a nonpersistent **local** Wi-Fi Direct group after a separate user tap, with timed best-effort cleanup; it never connects a selected candidate or sends application data. A separate three-QR **in-person public-identity preparation** now generates a temporary comparison code without reading a P2P MAC, connecting a device, or authorizing messaging. An additional, independently confirmed debug-only step can make a short, **unverified** Wi-Fi Direct connection request to one selected anonymous radio, followed by bounded best-effort cleanup. Android group formation never proves the device is the QR partner, and no app messages/voice can flow over it.
- **Permissions now requested:** `INTERNET` (Android also requires it for local TCP), `ACCESS_NETWORK_STATE` (Wi-Fi selection), and `CAMERA` (user-initiated QR scan). The debug APK additionally declares `BLUETOOTH_SCAN` (only requested when the user starts a 30-second inquiry), `BLUETOOTH_CONNECT` (separately requested for an unverified, time-limited RFCOMM socket), and `BLUETOOTH_ADVERTISE` (explicit Android host discoverability dialog). These Bluetooth permissions are **excluded from release builds**. Debug Wi-Fi Direct testing additionally declares `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `NEARBY_WIFI_DEVICES` (Android 13+) and `ACCESS_FINE_LOCATION` (only through Android 12L) for platform Wi-Fi P2P discovery. The release manifest retains only LAN/QR permissions. No microphone or contacts permission, location coordinates, Bluetooth messages or Wi-Fi Direct app messages.
- Development chat includes **Hide conversation** (visual curtain) and **Clear my screen** (local in-memory UI clear only), with message content discarded on session exit; these do not delete another phone's copies or guarantee erasure. Settings shows session-only active; encrypted history, disappearing timers and saved peer trust remain future-only. [M3g](docs/M3G_SESSION_PRIVACY_CONTROLS.md).
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
- [M3g: working local privacy curtain, confirmed clear and honest retention status](docs/M3G_SESSION_PRIVACY_CONTROLS.md)
- [M3h: nearby Wi-Fi recovery, targeted guidance and fresh-QR restart](docs/M3H_NEARBY_RECOVERY.md)
- [M3i: real nearby connection method catalog and one-tap QR navigation](docs/M3I_CONNECTION_METHOD_CATALOG.md)
- [M3j: foreground discovery lifecycle contract, opt-in rules and cancel safety](docs/M3J_FOREGROUND_DISCOVERY_CONTRACT.md)
- [Complete core-feature build plan: LAN, Bluetooth, Wi-Fi Direct, PTT, privacy, release gates](docs/CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md)
- [M3k: real debug-only Bluetooth Classic inquiry, not peer connection](docs/M3K_DEBUG_BLUETOOTH_INQUIRY.md)
- [M3l: ephemeral numbered Bluetooth signal selection, still unverified and unconnected](docs/M3L_EPHEMERAL_BLUETOOTH_CANDIDATES.md)
- [M3m: real debug-only RFCOMM host/join with no authenticated text](docs/M3M_DEBUG_RFCOMM_LINK.md)
- [M3n-A: in-person Bluetooth reciprocal QR, not authenticated channel or text](docs/M3N_A_BLUETOOTH_RECIPROCAL_QR.md)
- [M3n-B1: debug pinned TLS 1.3 engine handshake over RFCOMM (still no app text)](docs/M3N_B1_PINNED_TLS_ENGINE_RFCOMM.md)
- [M3n-B2: encrypted Bluetooth QR nonce, exporter and transcript agreement, still no app text](docs/M3N_B2_BLUETOOTH_ENCRYPTED_QR_AGREEMENT.md)
- [M3n-C1: in-person bilateral Bluetooth Approve/Decline plus two final encrypted confirmations, still no chat](docs/M3N_C1_BLUETOOTH_BILATERAL_CONSENT.md)
- [M3o: read-only Wi-Fi Direct capability preflight](docs/M3O_WIFI_DIRECT_PREFLIGHT.md)
- [M3p: debug-only Wi-Fi Direct permission and radio readiness](docs/M3P_WIFI_DIRECT_PERMISSION_READINESS.md)
- [M3q: bounded foreground Wi-Fi Direct discovery, anonymous event counts only](docs/M3Q_WIFI_DIRECT_BOUNDED_DISCOVERY.md)
- [M3r: anonymous temporary Wi-Fi Direct device selection preview, still no connection](docs/M3R_ANONYMOUS_P2P_CANDIDATES.md)
- [M3s-A: debug-only temporary local Wi-Fi Direct group host, no peer connection or messages](docs/M3S_A_TEMPORARY_P2P_GROUP_HOST.md)
- [M3s-B: three-code in-person Wi-Fi Direct QR identity preparation, no radio target binding](docs/M3S_B_IN_PERSON_QR_PREPARATION.md)
- [M3s-C1: explicit 18-second unverified selected-device P2P link, no app data](docs/M3S_C1_TARGET_LINK_DIAGNOSTIC.md)
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
