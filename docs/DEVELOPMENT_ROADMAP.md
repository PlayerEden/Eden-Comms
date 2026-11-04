# Eden Comms — Development Roadmap v0.1
Status: proposal. **Do not implement all planned features at once.** Small mergeable steps, demonstrable Android APKs, security checkpoints, and rollbackable releases.

## Priority decision: reliable offline-local communication first (2026-11-03)
**Ship the local foundation before working on remote internet communication, relays or long-range radio.** Prioritize **one** dependable local transport rather than partially implementing three. Recommended first: two phones on the same Wi-Fi/LAN using a router **with its WAN/internet connection physically disconnected**; actual router Wi-Fi remains on. A hotspot or direct link can be added after this path works. Bluetooth Classic can be an alternative first transport if LAN proves impractical, but is otherwise a follow-up, not a simultaneous blocker.

### Narrow vertical-slice order
1. Establish locally protected device identity and authenticated **mutual TLS 1.3** handshake; no trust-all certificates, no plaintext fallback, no independent message transmission before authentication and user verification.
2. Implement the **real LAN connection**: host and join via local discovery or explicitly selected peer, with consent on both phones, cancellation/timeouts and understandable failure states. Internet/WAN must not be required.
3. Connect the existing framing and in-memory message state to the authenticated channel. Demonstrate one text message each way, acknowledgment and disconnect. **Session-only** is default with no intentional persistent chat transcript.
4. Run a focused **two-phone test** with internet disabled at the router, cellular data disabled on both phones, and no cloud account; test peer swap/MITM, wrong pins, dropped connection and unexpected network requests.
5. Fix reliability, low-memory/low-battery behavior and confusing UI before introducing a second transport. Test APKs stay development builds, not full public releases.

### First genuinely useful APK acceptance gate
- Two Android phones can establish a user-approved, authenticated encrypted session on the same local Wi-Fi.
- Both can exchange short text **with the router's internet/WAN disconnected**; no cloud service, cellular data or account required.
- A wrong/unverified peer cannot read or exchange application messages; peer verification and transport errors fail closed.
- The chat screen indicates the real connection/verification status, not a simulated success.
- Disconnect closes the channel, stops discovery and clears session-only messages from app-managed memory where possible.
- Basic crash/reconnect/radio-off tests pass, with at least one pair of real phones and a trace showing no external service dependency.
- Provide an **internal debug APK** for tester feedback. Independent security review is still required before public security promises or a production release.

### Explicitly deferred
**Different internet connections / remote P2P, STUN/TURN relays, hosted services, VPN convenience integration, LoRa hardware, group chat and voice** are deferred until the core offline local build works reliably. Bluetooth and Wi-Fi Direct remain important nearby options, but not prerequisites for the first LAN text success.

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
- Android minSdk and test devices.
- Peer identity persistence: ephemeral by default vs optional saved trusted peers.
- Handling of background notifications while in a live session.
- Audio capability support for Bluetooth.
- Exact crypto library and audited implementation approach.
- Group chat and files: post-1:1 only.
