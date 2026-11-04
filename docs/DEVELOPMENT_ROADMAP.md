# Eden Comms — Development Roadmap v0.1

> **Latest physical evidence — November 4, 2026:** After v0.2.3 Keystore compatibility changes, the tester shared **one phone's screen** stating `Mutual TLS 1.3 handshake passed. Test link closed; text is still disabled.` This is the first reported successful pinned TLS test, not evidence that *both* phone screens passed or that no-WAN/negative cases were checked. The screenshot contains no QR, secret or network address. Record the other phone's result and perform hostile/mismatch/replay/timeout tests before claiming the device matrix passed. Messaging/PTT remain disabled.

> **Active development:** v0.2.4 adds a *test-only* four-message QR session/challenge proof *inside the existing mutually pinned TLS 1.3 socket*, then closes it. This is not chat and does not unlock the application security gate. Dedicated on-device testing and protocol/security review remain required. GitHub PRs are stacked and unmerged; integrate in order only after milestone checks. See `docs/M2C_SESSION_BINDING_TEST.md`.

> **Previous failure history:** Initial two-phone test: generic failure → `PEER-UNREACHABLE` → `TLS-HANDSHAKE-FAILED` on a Galaxy A32 5G / Android 13. The v2 Keystore repair was a plausible signing-interop fix, not proof of root cause. Preserve this history for regressions.

## Current next steps — ordered by security and real-device evidence

1. **Finish M2b physical evidence.** Confirm success on both screens for v0.2.3; repeat with router WAN disabled; test invalid/stale QRs, wrong identities, network loss, cancellation and older-device behavior. Capture only sanitized outcomes, no live QR/IPs.
2. **M2c: verify QR session binding over TLS.** The v0.2.4 test-only proof now checks both QR nonces/certificate pins plus fresh per-connection challenges on the existing mutually pinned socket. Re-run two physical phones and negative cases. Then design a reviewed, user-approved, long-lived encrypted session with a real SessionSecurityGate adapter and reliable teardown. No communication is enabled by a test proof alone.
3. **M2d: usability without compromising identity.** Optional, foreground-only, time-limited nearby discovery; advertised names/IDs minimized and rotated, no automatic trust. First-time users can verify in person by QR or securely designed code comparison at a distance; code comparison requires additional protocol review before enabling. No background broadcast by default.
4. **M2e: minimal verified offline text.** Two-way authenticated encrypted text on same Wi-Fi without WAN, in-memory session-only conversation default. Real-device negative tests, no content logging/backups. Do not enable voice yet.
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
- **Resolved direction:** ephemeral/session-only peer trust by default; optional explicitly chosen, per-peer public-identity trust for up to 30 days. Implementation remains gated on verified local authentication.
- Handling of background notifications while in a live session.
- Audio capability support for Bluetooth.
- Exact crypto library and audited implementation approach.
- Group chat and files: post-1:1 only.
