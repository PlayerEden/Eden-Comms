# Eden Comms — Development Roadmap v0.1

> **M3f chat-first UI pass (2026-11-06):** Home Text now goes directly to the existing nearby debug test connection, the fictional local-only text sandbox is in Developer Tools, and successful QR/TLS/two-person confirmed sessions show a proper conversation first. Fixed phrases/TLS details are collapsed as optional test tools. Unicode drafts accept 280 full code points; terminal and lifecycle paths explicitly clear temporary text/drafts. [M3f scope and physical QA limits](M3F_CHAT_FIRST_EXPERIENCE.md). This does NOT authorize production messaging or replace hardware/security review.

> **M3e core app functional text development (2026-11-06):** The existing QR-pinned TLS **debug test** now accepts bounded 280-code-point typed Unicode text after bilateral in-session approvals **and** both final confirmations, using the previously isolated parser with no ACK/receipt implementation. The debug session UI shows real written/received test text as ephemeral bubbles and QA copies **counts only**. See [M3e](M3E_DEBUG_TWO_WAY_TEXT.md). **This is unreviewed, not physically tested, not production secure texting, and not for sensitive content.** Release `CAN_*` flags, voice, Bluetooth/Wi-Fi Direct and peer retention remain disabled. The owner will schedule physical testing later; prioritize useful core features and CI in the meantime.

> **M3d secure signing bootstrap (2026-11-06):** Added an [owner-run local setup guide and helper](M3D_OWNER_SIGNING_SETUP.md) to validate a test-only PKCS12 private-key entry, check the protected exact-main GitHub environment, and upload five signing secrets via authenticated GitHub CLI **stdin** after deliberate human consent. The script refuses secret overwrite/key rotation. ChatGPT has **not provisioned or accessed a permanent signing key**, and no actual in-place phone update has been performed; #27, #26 and independent security review stay OPEN.

> **M3c installed-build QA evidence (2026-11-06):** Development-only Connect nearby now shows the installed APK version/versionCode, source revision and short public APK signing-certificate fingerprint; explicit copy actions provide full safe build identification before pairing and in each phone's test summary. [M3c evidence and limitations](M3C_ON_DEVICE_BUILD_EVIDENCE.md). This is not peer identity verification or actual two-phone/update acceptance. Stable test signing, physical QA and qualified independent review remain OPEN; real text/voice still off.

> **M3b installed-APK preflight (2026-11-06):** A read-only desktop/ADB tool now compares a proposed test APK to the **actual installed phone app** by verified package, certificate signer and strictly higher versionCode, failing closed without installing, uninstalling or accessing Keystore keys. [M3b use and limitations](M3B_READ_ONLY_DEVICE_UPGRADE_PREFLIGHT.md). The protected signing credentials, two-phone installs, hardware matrix and independent protocol review remain OPEN; real messages and voice remain disabled.

> **M3a protocol review handoff (2026-11-06, review-only):** Adds deterministic exhaustive debug approval state-space regression tests (55,987 short event traces, two-endpoint interleavings and explicit interrupted-final-confirmation asymmetry) plus an exact source review map and independent-auditor/physical-QA evidence checklist. [M3a reviewer package](M3A_PROTOCOL_REVIEW_HANDOFF_2026_11_06.md). Neither this work nor CI constitutes independent review or physical acceptance; no unrestricted user text/voice has been enabled. See open issues #42, #26 and #27.

> **M2y final two-phone confirmation (2026-11-05):** Each phone must now observe both session-bound human approvals, **write one session-bound final CONFIRM** on pinned TLS, and **receive the other phone's CONFIRM** before showing or exchanging four fixed test phrases. The UI and one-phone QA distinguish these observations; failure/timeout/duplicate/stale controls stay closed. [M2y implementation and untested hardware acceptance](M2Y_DEBUG_MUTUAL_FINAL_CONFIRMATION.md). This remains **unreviewed, debug-only**, not actual encrypted user text or production consent. Core M3 requires independent protocol review and two-phone physical evidence before live typed chat.

> **M2x session-bound consent checkpoint (2026-11-05):** Debug Approve/Decline controls now include an explicit 32-byte canonical commitment to both QR certificate identities/nonces, live proof challenges, and the matched RFC 9266 TLS exporter. Both devices must independently compare it on the pinned connection before granting **only four fixed test phrases**; old three-byte controls fail closed. This is an unreviewed **debug-only** step, not actual production human authorization or real text. [M2x implementation and risks](M2X_DEBUG_SESSION_BOUND_APPROVAL.md). Core M3 remains blocked on independent security review and physical two-device acceptance.

> **M2w live TLS binding comparison (2026-11-05):** The debug four-phrase LAN test now requires both phones to confirm **matching RFC 9266 TLS channel-binding bytes on the same pinned TLS 1.3 connection** after reciprocal QR proof and before READY/M2v Approve/Decline. Unsupported Android API/provider, wrong record/role, mismatch and timeout fail closed (Android API 31+ required). New fixed-size protocol, safe statuses, tests and hardware checklist: [M2w](M2W_DEBUG_TLS_CHANNEL_BINDING_MATCH.md). This is NOT independent security review, a finished session-bound human authorization protocol, or real typed chat; core M3 issue #42 remains open.

> **M2v human approval experiment (2026-11-05):** After successful debug pinned TLS/QR/READY, **both people must now explicitly Approve** from a native Android screen before either device can exchange the four predefined harmless test phrases. Debug APPROVE/DECLINE control packets use the existing verified test socket; the local app distinguishes its own completed TLS write from the other phone's observed approval, handles denial/timeouts, and clears state on disconnect. This is a meaningful **live debug-only product flow**, **NOT independently reviewed production consent** or real text. Read [M2v design and physical acceptance](M2V_DEBUG_BILATERAL_APPROVAL_TEST.md). Core M3 [issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42) still requires independently reviewed session-bound approval, unrestricted encrypted text integration and two-phone QA.

> **M2u next real-LAN texting milestone (2026-11-05):** The app source now includes an actual **strict, bounded UTF-8 TEXT/ACK wire codec** with stable fixtures and comprehensive negative parser tests. The candidate is intentionally **unwired** to TLS, QR/approval, or the native chat UI by a CI security guard: ACK bytes alone are not authenticated receipts. See [M2u codec and independent review gate](M2U_BOUNDED_TEXT_WIRE_AND_SECURITY_GATE.md) and the [core M3 real secure LAN messaging issue](https://github.com/PlayerEden/Eden-Comms/issues/42). The product focus is now live user-approved text; proposals/bench tests cannot substitute for approved protocol or physical testing. Real unrestricted send/receive and voice remain disabled.

> **M2t message-state feature pass (2026-11-05):** The native chat preview now includes an optional **status meanings** help dialog, clearly distinguishing local-only activity from **future** approval, queue, encrypted local write, authenticated app acknowledgment and failure states. A JVM-test-only event reducer checks ACK ordering, old sessions, bounds and no auto retry; **no simulated receipt code ships in the APK**. See [M2t report](M2T_MESSAGE_DELIVERY_STATUS_CONTRACT.md). The next core milestone is not more simulated transport work: it is the independent protocol/authentication decision needed before a safely reviewed real-LAN text integration.

> **M2s product experience checkpoint (2026-11-05):** After PRs #35–#39 strengthened debug-only LAN session correctness, the next milestone implements **Home → Text** as a native Compose conversation-screen preview with user typing, clearly local-only bubble states, fictional opt-in example messages, and in-memory session clearing. **No user text is transmitted** and no peer/voice permission is enabled. See [M2s UI scope and real-LAN messaging gates](M2S_CHAT_UI_LOCAL_PREVIEW.md). Following work should prioritize actual secured chat features through the independent authentication/protocol review and physical two-phone acceptance gates, not indefinite infrastructure-only audits.

> **Living batch checkpoint — November 5, 2026:** The original stacked Android source has been integrated into `main` (PR #25 + #28), with green CI. Subsequent no-phone passes covered APK identity evidence (#29), TCP/heartbeat and simulated fixed-phrase QA (#30), pairing UI/accessibility polish (#31), and privacy-minimized per-phone QA summary + consolidated [16-scenario physical worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md) (#32). The optional **M2l internal-test signing workflow** is being prepared; it is not a persistent signing identity until protected secrets are configured and two physical installs verify upgrades. See the [current no-phone batch plan](NO_PHONE_DEVELOPMENT_BATCH_2026_11_05.md) and [signing setup](M2L_INTERNAL_TEST_SIGNING.md). **No real two-phone test, unrestricted text/voice, reviewed protocol or production release is complete.** Earlier historical notes below refer to past development branches, not today's `main` state.

> **Latest physical evidence — November 4, 2026:** After v0.2.3 Keystore compatibility changes, the tester shared **one phone's screen** stating `Mutual TLS 1.3 handshake passed. Test link closed; text is still disabled.` This is the first reported successful pinned TLS test, not evidence that *both* phone screens passed or that no-WAN/negative cases were checked. The screenshot contains no QR, secret or network address. Record the other phone's result and perform hostile/mismatch/replay/timeout tests before claiming the device matrix passed. Messaging/PTT remain disabled.

> **v0.2.9 research & approval proposal (unwired):** Adds `ReviewOnlyApprovalFrame` in **JVM test sources only** (strict proposed packet shape, **not live approval**), negative parsing tests, and a CI check requiring all production text/audio flags to remain off and forbidding accidental import into app/network code. Proposes RFC 9266 TLS-exporter channel binding with API 31+ compatibility and discusses alternatives, privacy/metadata limits, Android 17 local-network permission, Google Play, BIS encryption export controls and other legal issues. See `docs/M2F_AUTHENTICATED_APPROVAL_PROPOSAL.md` and `docs/EDEN_COMMS_TECH_SECURITY_PRIVACY_LEGAL_RESEARCH_2026-11.md`. Not reviewed, no real messaging unlocked, no new handset test required.

> **v0.2.8 security boundary (model only):** The legacy `SessionSecurityGate` now cannot expose release text/voice permission merely from model confirmation strings; `ProductionSessionAuthorization` models fresh per-attempt peer pins, authenticated-channel evidence, *both* approvals, stale-callback rejection and revocation. It is **not wired to networking or real messaging**. `ReleaseCapabilities` remain disabled. See `docs/M2E_PRODUCTION_SESSION_AUTH_BOUNDARY.md` for exact trust boundaries, independent-review requirements and unimplemented adapter tasks.

> **v0.2.7 static audit:** Cancellation-safe socket ownership, bounded pending sends, coalesced heartbeat writes, temporary QR reference cleanup and clearer automatic-pairing error messages are tracked in `docs/M2D_SESSION_LIFECYCLE_AUDIT.md`. Automated checks only—two-phone hardware QA and independent security review still pending.

> **New test bundle — v0.2.6:** A **debug-only temporary two-phone conversation** is being built for a fuller physical test: after the existing mutual TLS 1.3, full certificate pins, two-QR session proof, and an in-TLS READY exchange, each phone can send/receive only four predefined harmless test phrases (NOT arbitrary text). Session-only UI counters, manual disconnect, limited heartbeat/timeout and Wi-Fi change handling; no chat history or peer storage. Production messaging/PTT capability flags remain off. Physical Android QA and independent security review are still pending. See `docs/M2D_TWO_PHONE_TEST_BUNDLE.md`.

> **Guided QR UX branch (November 4, 2026):** v0.2.5 reduces visible taps, opens the QR test directly from Home, hides Host/Join labels, and automatically starts the presenter listener after scanning the reply. The joining phone keeps its reply QR visible and makes bounded TCP-only retries while the presenter scans. No real messages/PTT or peer retention; the two-QR mutual identity check and TLS 1.3 requirement are unchanged. Physical two-phone UX/security QA is pending. See `docs/UX_QUICK_PAIR_V0_2_5.md`.

> **Prior security work:** v0.2.4 adds a *test-only* four-message QR session/challenge proof *inside the existing mutually pinned TLS 1.3 socket*, then closes it. This is not chat and does not unlock the application security gate. Dedicated on-device testing and protocol/security review remain required. GitHub PRs are stacked and unmerged; integrate in order only after milestone checks. See `docs/M2C_SESSION_BINDING_TEST.md`.

> **Previous failure history:** Initial two-phone test: generic failure → `PEER-UNREACHABLE` → `TLS-HANDSHAKE-FAILED` on a Galaxy A32 5G / Android 13. The v2 Keystore repair was a plausible signing-interop fix, not proof of root cause. Preserve this history for regressions.

> **L0 engineering evidence (2026-11-04):** Add strict reviewed APK permission allowlist and direct dependency baseline; audit backup/cleartext settings, exported components and generate downloadable CI dependency and launch-surface evidence for future Google Play Data safety review. This is **not a privacy certification** or proof of zero collection. See `docs/L0_DATA_HANDLING_AND_SDK_REGISTER.md` and `scripts/audit_play_surface.py`. Do not add SDKs, permissions or new network data flows without updating this register and reviewing Play disclosures.

> **L1 in-app privacy review (v0.2.10 preview):** Settings and How it works now link to a readable **Privacy & data** screen disclosing local Keystore identity persistence, QR peer/network metadata, temporary test phrases, camera/local-network permissions and privacy limitations. It explicitly says this is **not** the final Google Play policy. A marked working draft exists at `docs/drafts/EDEN_COMMS_PRIVACY_POLICY_WORKING_DRAFT.md`. The publisher's actual legal identity, contact method, final runtime/SDK data audit and stable public HTTPS policy URL remain OPEN gates. No website or publishing identity was invented and no store submission was attempted. Tests cover the in-app disclosure text. Production messaging/PTT remain off.

## Google Play / North America launch track — starts now, mandatory before release

**Goal:** Publish Eden Comms on Google Play when the app and security are ready, with a **staged North American rollout: U.S. first, Canada second, Mexico third** (rather than committing to simultaneous publication). This is an explicit product goal, **not a claim that any country is launch-cleared**. Other countries/territories need separate eligibility and legal checks.

**Living checklist:** [`docs/GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md`](GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md). Track this work **in parallel with** the connection/security milestones—not after code is finished.

- **Start now:** Evaluate U.S. BIS encryption export classification/notification including already-public repository/APK artifacts; inventory cryptography, third-party licenses, SDK data flows, public claims, target audience, country restrictions and account/business structure. Seek appropriate qualified legal counsel for jurisdiction-specific questions.
- **Before external beta:** Choose/verify Play developer account, privacy-policy hosting and in-app link, Data safety mapping, support contacts, intended age groups, security review plan, signed build pipeline and production test plan.
- **Before U.S. production:** Clear independent crypto review and two-physical-phone tests; complete Play testing/eligibility, App Bundle/Play App Signing, store disclosures/content rating, API target policy, permissions, export legal assessment and staged-release/rollback readiness.
- **Before Canada:** Evaluate PIPEDA and applicable provincial rules (including Quebec Law 25), localization/French requirements, actual data processing, and region-specific device/support QA.
- **Before Mexico:** Evaluate the current federal private-sector data privacy law, any required Spanish notices/translations, local consumer/distribution issues, and region-specific device/support QA.
- **After launch:** Monitor policy/law changes, compatibility, incidents, security updates, transparency, retention and license obligations. Add each country deliberately in Play Console.

**Stop rule:** No production publication, public security claims, unrestricted messaging or PTT simply because CI succeeds. Legal scope, device QA and crypto verification must be separately accepted. Play policies and laws can change: recheck official requirements before each country's launch.

> **M2g v0.2.11 channel-binding compatibility diagnostic:** After the existing debug-only two-QR/mutual TLS1.3 session proof, the app now attempts Android's standardized RFC 9266 TLS exporter on API31+ *only to report a local support status* on the fixed-phrase test screen. It never stores/displays/transmits the 32-byte output, changes no wire packets or user-message/voice flags, and **does not implement consent or confirm both phones match**. JVM preflight/negative tests and CI audit boundary added. Real two-phone capability QA and independent reviewed approval protocol remain pending; Android API26–30 may still install but cannot use this standard Android exporter. See `docs/M2G_TLS_EXPORTER_COMPATIBILITY_DIAGNOSTIC.md`.

> **M2h v0.2.12 approval design test matrix (JVM-only):** Candidate length-delimited presenter/joiner QR+identity+challenge+synthetic TLS exporter commitment with golden vector; test-only bilateral approval/decline ordering and adversarial stale/duplicate/wrong-role/reconnect scenarios. CI explicitly forbids these classes from app main sources; neither raw TLS exporter bytes nor approval frames are sent by the installed APK. This is a **candidate protocol, not independently reviewed authenticated consent**, and does not enable any real messages. See `docs/M2H_APPROVAL_TRANSCRIPT_AND_SIMULATOR.md`.

## Current next steps — ordered by security and real-device evidence

1. **Finish M2b physical evidence.** Confirm success on both screens for v0.2.3; repeat with router WAN disabled; test invalid/stale QRs, wrong identities, network loss, cancellation and older-device behavior. Capture only sanitized outcomes, no live QR/IPs.
2. **M2c: verify QR session binding over TLS.** The v0.2.4 test-only proof now checks both QR nonces/certificate pins plus fresh per-connection challenges on the existing mutually pinned socket. Re-run two physical phones and negative cases. Then design a reviewed, user-approved, long-lived encrypted session with a real SessionSecurityGate adapter and reliable teardown. No communication is enabled by a test proof alone.
3. **M2d: usability without compromising identity.** Optional, foreground-only, time-limited nearby discovery; advertised names/IDs minimized and rotated, no automatic trust. First-time users can verify in person by QR or securely designed code comparison at a distance; code comparison requires additional protocol review before enabling. No background broadcast by default.
4. **M2d test bundle (before real messages):** Validate both phones can exchange only a small set of synthetic, predefined phrases over pinned TLS after QR and session checks, with in-memory counters and disconnection handling. This is a debug-only development probe, not a supported messenger.
5. **M2e: minimal verified offline text.** Two-way authenticated encrypted text on same Wi-Fi without WAN, in-memory session-only conversation default. Real-device negative tests, no content logging/backups. Do not enable voice yet.
5. **M3: optional peer trust retention.** **Never remember peers by default.** After proven bilateral verification, each user can independently opt to remember only that peer's *public identity* for **1–30 days, hard maximum 30 days**; no automatic renewal. On expiry or clock rollback, refuse prior trust and reverify. Forget immediately on request, exclude from backup and protect local integrity. Reconnect still cryptographically proves possession of the pinned private key. Trust storage is **not** conversation storage and is not yet implemented.
6. **M3: optional encrypted message retention (independent).** Session-only default; separately opt in per chat to encrypted local history with 1–30 day limit, automatic eligibility/expiry check on next use, best-effort deletion, and no remote deletion guarantee. A person can remember a peer and retain *zero* messages, or vice versa.
7. **Privacy explanations:** short setting labels, a contextual **?** for privacy/security-sensitive choices, and offline **Learn more → How it works** guidance. No giant modal on every action. Explain exactly what is stored, who controls it, when it expires, and what cannot be guaranteed.
8. **Only then Voice / PTT, Bluetooth, Wi-Fi Direct, further polish and release review.** Never ship a promise of encrypted communication based on a successful build alone. Prioritize a clean reviewed integration branch and rollbackable checkpoints.

See `docs/TRUST_RETENTION_AND_HELP_PLAN.md`, `docs/M2A_QR_MUTUAL_TLS_TEST.md` and `docs/SECURITY_AND_QA.md`.

Status: proposal. **Do not implement all planned features at once.** Small mergeable steps, demonstrable Android APKs, security checkpoints, and rollbackable releases.

## Platform and implementation approach
- Android first; native **Kotlin + Jetpack Compose** UI and Android connectivity APIs.
- Shared core message envelope, peer identity, authenticated session, and message handling; transports plug in via interface rather than duplicating crypto.
- Candidate transports: LAN via TCP sockets + Android Network Service Discovery (NSD/mDNS); Bluetooth Classic via RFCOMM; Wi-Fi Direct via WifiP2pManager + TCP sockets after link establishment.
- Transport support is device/OS dependent; provide explicit fallbacks. Do not promise seamless automatic handoff or Bluetooth voice performance without real-device testing.
- PTT/audio via Android audio APIs, with explicit mic consent and OS-compliant foreground service behavior where required.
- targetSdkVersion: at least API 36 for contemporary Play submission, monitor API 37 local network permission changes. Minimum supported Android release remains a pending decision after device compatibility review.
- Crypto library and handshake design are **architecture decisions requiring security review**, not a place to invent custom crypto primitives.

## Milestone M0 — Repo and architecture baseline
- Create product vision, roadmap, security/threat model, decisions log, and QA matrix.
- Native Android project skeleton, Kotlin/Compose, deterministic Gradle build.
- README with setup, build, no-network promise and caveats.
- GitHub Actions lint/unit tests/build for debug APK with checksums; commit isolation, tagged checkpoints, no secrets in Git.
**Gate:** clean checkout builds a local debug APK and CI equivalent; empty app no network requests.

## Milestone M1 — First usable APK: verified encrypted LAN text
- Two Android installs can locate a local session via NSD/mDNS, join, and disconnect.
- Both parties approve a connection. Peer identity + visible QR/short-code confirmation prevents silent impersonation.
- Adopt reviewed, well-maintained cryptographic protocol/library; encrypted and authenticated handshake and messages from the first payload; reject unverified sessions where indicated.
- Typed text, timestamps local to device, sending/sent/delivered status only where receipt is verifiable, no external server dependency.
- Session-only retention by default; no chat body in logs, notifications, backups, crash telemetry, or persistent cache.
- Handle mismatched versions, app pause, failed connection, reconnect attempts, and explicit failure states.
**Gate:** test on TWO real phones sharing a router with WAN disconnected; text works both directions; intentional plaintext sniff/capture yields no readable message payload; connection mismatch rejected; app makes no third-party calls.
**Deliverable:** `EC-M1-LAN-debug.apk` + SHA-256 + test notes. Not ready for public security claims until reviewed.

## Milestone M2 — Bluetooth peer-to-peer
- Bluetooth Classic RFCOMM data transport behind same session/crypto interfaces.
- Peer discovery/selection, Android permission prompts, bonded/unbonded experience documented.
- Clear fallback when Bluetooth disabled/unavailable, session drops, or devices out of range.
**Gate:** two phones exchange encrypted text with Wi-Fi and cellular data disabled (enable Bluetooth as needed); no plaintext; robust reconnect/error UI.
**Deliverable:** EC-M2 debug APK + test record.

## Milestone M3 — Wi-Fi Direct peer-to-peer
- WifiP2pManager discover/connect with supported permissions; use socket data channel through shared secure protocol.
- Show when Android location services or OS permissions are needed for peer discovery, without ever collecting location into app data.
- Connection chooser: Auto (recommend available method), LAN, Wi-Fi Direct, Bluetooth. Manual override; actual transport shown in session.
**Gate:** two phones with no router/internet complete verified encrypted exchange, each manual transport works on tested devices; incompatible devices get understandable fallback.
**Deliverable:** EC-M3 debug APK + compatibility matrix.

## Milestone M4 — Practical voice
- Add hold-to-talk, release-to-stop and accessible toggle-to-talk, plus transmit/receive status.
- Start with supported LAN / Wi-Fi Direct audio tests; validate Bluetooth latency/throughput before claiming live PTT there.
- Explicit microphone permissions; only capture while expected, show active mic state, suppress audio when disconnected.
- Later voice notes if worthwhile; keep audio encrypted end-to-end.
**Gate:** measure reliability, interruption behavior and battery on TWO devices; test screen-on/off and background restrictions; no audio without verified session.
**Deliverable:** EC-M4 debug APK.

## Milestone M5 — Retention, polish, help
- User-selectable modes: Session-only (default), encrypted-on-device history, disappearing timers (best effort).
- Local storage encryption and deletion behaviors reviewed; backup and device-transfer exclusions; permissions review.
- Compact onboarding and How It Works page; accessibility checks with TalkBack, font scaling, 48dp+ controls; portrait/landscape and small/large screen layouts.
- Optional peer trust list, explicit reset/wipe local state (scope disclosed).
**Gate:** verify mode semantics after process kill, restart, OS restore/transfer, update, and notification handling; no misleading deletion claims.
**Deliverable:** EC-M5 beta APK.

## Parallel milestone L — Google Play & North America legal/distribution readiness

**Begins during development**, alongside M2 security and M3–M6 work; do not postpone until app-store submission. Follow the dedicated [Google Play & North America launch checklist](GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md), including independent legal review and per-country gates. Define a U.S. production launch first, with Canada and Mexico added only after country-specific approval.

**Gate:** U.S. export/Play/security/privacy and tester requirements resolved before U.S. publication; Canada/Mexico have separate documented legal, localization, support and QA sign-offs before their Play Console regions are enabled.

## Milestone M6 — Privacy/security audit and release candidate
- Independently review authenticated key exchange, key lifecycle, replay/downgrade resistance, message framing, auth failures, transport changes, local encryption, logs and backups.
- Static checks, dependency audit, Android permission/network audit, manual packet capture, MITM and peer spoof simulations; avoid storing sensitive material in public issue attachments.
- Device matrix: old/current Android, different OEMs, battery saver, no WAN, hostile shared LAN, airplane-mode toggles, Bluetooth-only, Wi-Fi Direct.
- Accessibility, usability, battery, crash recovery, performance, UI polish, release signing and repeatable build.
**Gate:** all critical issues fixed; security/privacy claims reviewed; documented limitations; consented tester feedback; release APK/AAB + checksum and rollback notes.

## Engineering rules
- One major milestone per PR or tightly scoped group of PRs. Changes behind feature flags if incomplete.
- Every milestone: unit/integration tests; emulator if possible, two-phone manual verification for actual radios; bug fixes before advancing.
- `main` remains stable. Use `dev` or feature branches; protect `main` as feasible. Build artifacts always tie to commit hash.
- GitHub holds source of truth; track decisions and changelog, release notes, QA evidence, and known bugs.
- No publication of raw personal messages, keys, local network addresses, or packet captures containing sensitive data.
- If a transport cannot satisfy encrypted, authenticated communication requirements, ship it disabled rather than insecure.

## First development sprint after plan approval
1. Set up Android Kotlin/Compose skeleton, package naming, Gradle, lint/tests, CI artifact naming.
2. Build a polished-but-minimal home + connect + session UI using simulated transport; all buttons functional.
3. Implement a transport-neutral message/session interface and in-memory session store.
4. Select/review secure protocol/library and identity verification design before real messages.
5. Implement LAN discovery/connect first; test local-only, then two phones.

## Open decisions

- **Launch scope accepted as planning target:** U.S. first; Canada and Mexico conditional phases. Decide Play publisher account type (Personal/Organization), responsible legal entity, support channel, audience age bracket, free-only vs monetized, translation level and legal reviewer early.
- Confirm that Canada and Mexico are desirable market launches after the legal/privacy assessment; Google Play country targeting is not equivalent to approval of the country's regulatory requirements.
- Android minSdk and test devices.
- **Resolved direction:** ephemeral/session-only peer trust by default; optional explicitly chosen, per-peer public-identity trust for up to 30 days. Implementation remains gated on verified local authentication.
- Handling of background notifications while in a live session.
- Audio capability support for Bluetooth.
- Exact crypto library and audited implementation approach.
- Group chat and files: post-1:1 only.
