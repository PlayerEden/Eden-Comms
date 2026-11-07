# Eden Comms — Core Feature Completion Plan (living plan, November 6, 2026)

**Owner's instruction:** Build a well-rounded Android communication app now through small, reviewed GitHub PRs, with automated tests after each step. **The owner will explicitly say when physical two-phone testing begins.** Do not stall coding to request hardware evidence or Google Play signing. Keep those items tracked as uncompleted release gates.

**Product promise under development:** **Connect → Communicate → Disconnect**. Private-by-design, in-person, one-to-one, Android-first messaging and later push-to-talk between nearby phones, requiring **no user account, Eden-operated server, mobile data or Internet connection**. Transport methods are same-network Wi-Fi, Bluetooth Classic, and Wi-Fi Direct. Peer identity must be verified, never inferred from radio discovery.

## Progress truth and non-negotiable guards

| Area | Present verified source checkpoint | Remaining definition of done |
| --- | --- | --- |
| App shell, basic navigation and theme | Native Kotlin/Compose, responsive layout, help/privacy/dev screens, CI builds | Real-device accessibility/visual audits and final user copy |
| LAN connection | Debug two-QR session, pinned mutual TLS 1.3, exporter binding, bilateral human approval and final confirmations | Real two-phone WAN-off/negative device QA, independent protocol security review, approved release networking |
| Text | Short bounded real debug-only outgoing local TLS writes and received peer test text in an ephemeral conversation; no ACK | Reviewed authorized general text, actual peer delivery ACK semantics, hardware QA |
| Connection recovery | Wi-Fi status, fresh QR retry, failure guidance and cancellation | On-device network interruption and reliability testing |
| Bluetooth | M3j testable discovery state machine; **M3k raw Classic inquiry; M3l anonymous staging; M3m bounded secure RFCOMM OS socket; M3n-A reciprocal in-person QR prepared (no app encryption/text yet)** | Device selection, RFCOMM link, checked identity + authenticated encrypted messages over that link |
| Wi-Fi Direct | Plans, discovery/permission lifecycle model only | Android P2P discovery, link, verified encrypted communication |
| Voice / PTT | Disabled UI prototype and roadmap | Audio recording/playback and transport with verified-session/permission gates; physical latency/dropout testing |
| Privacy | Session-only in-memory debug text; Hide conversation and Clear my screen | User opt-in encrypted local 1–30-day history, best-effort disappearing timer, independently optional 1–30-day peer trust |
| Release | Green CI, sanitized QR QA worksheet, debug APK artifacts | Stable test signing/update evidence, full device matrix, independent security audit, privacy policy, Google Play prerequisites |

**Do not conflate** passing tests or discovering a device with authenticating a peer. The source still has `ReleaseCapabilities.CAN_CONNECT=false`, `CAN_SEND_MESSAGES=false`, `CAN_TRANSMIT_VOICE=false`. Leave these false until independent review and actual device acceptance. No sensitive or emergency communication in the debug APK. `main` is source of truth; PRs must include acceptance evidence and a rollback path.

## Ordered work packages

### Phase A — Finish a dependable first transport (LAN)
- **A1.** Keep existing QR/TLS debug texting reliable: exact session state, clear reconnect rules, message-in-flight failure handling and UI consistency. No silent replay or plaintext fallback.
- **A2.** Add *authenticated peer application ACK* only after its packet design is reviewed. Distinguish **queued**, **written to local TLS**, **received by peer app**, and unknown/failed; never label delivered/read based on local socket success.
- **A3.** Optional LAN NSD/mDNS convenience discovery can make selection simpler, but retain QR and an explicit human identity step; LAN guest-network isolation and no-WAN support remain core.
- **LAN completion gate:** two real Android devices exchange ordinary non-sensitive text both directions on the same router **with WAN unplugged**; reject wrong/stale QR, wrong peer, dropouts and replay; independent review approves protocol. If not, LAN is **debug prototype only**.

### Phase B — Bluetooth Classic (next active stream)
- **M3k. Debug inquiry — NOW:** Include the scanner in `src/debug` only, require explicit Android **Nearby devices** permission, user START and STOP, limit 30 seconds, count untrusted nearby observations, clean up on background/revocation, no MAC/name retention. CI compiles and permission-audits. No pairing or transport yet.
- **M3l. Anonymous selection — implementation integrated:** A cap of 12 deduplicated opaque BluetoothDevice objects retained in RAM only per debug scan, rendered as numbered unverified signals; explicit selection stops inquiry and stages one for a future verification step. No friendly names, addresses, peer identity, Bluetooth connection or extra permission; do not confuse any signal with Eden Comms. **Still outstanding:** design a truly recognizable Eden Comms peer selection/advertising approach and independently verified identity binding after a real connection.
- **M3m. Debug RFCOMM link — integrated:** Build one-shot Android secure Bluetooth Classic RFCOMM host/join in a debug-only socket controller using explicit CONNECT/ADVERTISE permissions, system discoverability prompt, 60-second host/18-second join timeout, 45-second socket cap, and cancellation/foreground/permission-loss guards. **NO app payload streams, peer identity, message sending or TLS integration**; discovery/OS bond is not an authenticated Eden Comms peer.
- **M3n-A. In-person QR — implementation integrated:** real offline two-QR Bluetooth-only public certificate pins/nonces exchange using CameraX/Android Keystore, expiry and role bound prepared transcript. **Unverified**: the actual Bluetooth radio target and RFCOMM socket are not cryptographically bound to these QR public keys; no socket payload I/O.\n- **M3n-B1. Debug TLS 1.3 handshake — source integrated:** actual SSLEngine TLS 1.3 protocol traffic over debug-only RFCOMM with exact certificate pin from QR, local Keystore identity, required client auth, bounded TLS framing, exporter **availability only**, and immediate diagnostic socket closure. NOT YET the complete authenticated QR session. No user text or production permission.\n- **M3n-B2. Encrypted QR/channel transcript — implemented in debug source:** after exact mutual TLS 1.3 certificate pin checks, exchange four fixed TLS-encrypted role-bound QR nonce/challenge proof frames, compare bilateral exporter values in two TLS-encrypted controls, and compare one domain-separated transcript commitment in two final TLS-encrypted controls. **Always close diagnostic RFCOMM socket afterward.** Hardware interoperability and external security review remain unperformed; this is not user approval or a production session.\n- **M3n-C1. Bilateral Bluetooth human consent — integrated as debug diagnostic:** after the same QR-pinned TLS channel proves both QR nonces, fresh challenges, exporter and matching transcript, separately request human APPROVE or DECLINE on both phones, exchange same-session encrypted approval records and two automatic final encrypted CONFIRM frames using the existing `DebugApprovalGate`. **Always close RFCOMM afterward**, with no chat, voice, saved trust or release capability. No real-phone or independent security review yet.
- **M3n-C2. Bounded Bluetooth debug text — deferred:** only after expert review and two-phone provider/consent QA may the same authorized live TLS session carry short-lived debug text with strict framing/receipts and fail-closed lifecycle. Never infer trust from Bluetooth bond; no plaintext fallback or production release flags.
- **Bluetooth completion gate:** two phones with Wi-Fi/cellular disabled can find and select each other, verify peer, exchange text and disconnect; permission deny/revoke, radio-off, timeout, OS lifecycle and unreliable radio failures are tested; no plaintext or unapproved messages.

### Phase C — Wi-Fi Direct
- **M3s-C1. Short-lived targeted OS link, still unverified (debug-only, API 29+):** After selecting one of M3r's anonymous candidates, a separate user confirmation may submit one nonpersistent Android P2P `connect` request. Refuse preexisting groups, distinguish request accepted from group formed, cap at 18 seconds and clean up via best-effort `cancelConnect`/`removeGroup` with explicit unconfirmed status. No proof that selected radio matches M3s-B's QR person and no TLS, app socket, text or PTT. Two-phone hardware and independent security gates remain OPEN.
- **M3s-B. In-person QR metadata preparation (debug-only, no radio target):** independent host→joiner→host→joiner three-QR process with fresh pin/challenges, strict expiry and matching short transcript code. It does not bind an M3r candidate, prove possession of a peer private key, or authorize connection. Never use an Android local device MAC as a dependable identity QR; `requestDeviceInfo` may report a redacted address. Only a short-lived, explicitly confirmed, **unverified debug OS-link probe** is available in M3s-C1; binding the selected radio to QR identity and enabling app data remain blocked pending reviewed cryptographic verification and real device QA.
- **M3s-A. Temporary group host (debug-only, Android 10+):** user-started 20-second `WifiP2pManager.createGroup` with explicit `enablePersistentMode(false)` and random transient credentials; first check for an existing group and refuse to change it. On cancellation/timeouts/background attempt `removeGroup`, then check the Android group state; warn on unconfirmed cleanup. Does not connect any anonymous candidate, expose group secrets, start sockets, or authorize messages; physical tests and independent assessment still pending.
- **M3r. Anonymous candidate staging (debug-only, no connection):** Current foreground-only discovery may read a bounded `WifiP2pDeviceList` snapshot via a single restricted adapter, retain eight opaque candidates only during the same 30-second scan and show anonymous ordinal preview selection. No device name/address display, no persistence, no group/socket/trust/messaging, no new permissions. Two-phone hardware and independent security review remain unperformed.
- **M3q. Debug P2P scan — implemented in debug source, not hardware-verified:** user-tapped 30-second Android `discoverPeers` only after M3p readiness and platform radio enabled; STOP, background, permission/radio loss, timeout and channel errors close. Shows only anonymous peer-list **change event count** (not identifiable peers). No group creation, selected device, messaging or production rights.

- **C1.** Device support and Android 13+ `NEARBY_WIFI_DEVICES` consent flow; legacy location requirements handled explicitly by Android version, with honest explanations and no location analytics.
- **C2.** Foreground `WifiP2pManager` discovery/selection and group owner/link negotiation with visible timeouts, cancel and manual retry. Correctly interpret asynchronous callbacks; a discoverPeers success is **not** a verified peer.
- **C3.** Reuse the independently reviewed authenticated stream/session model after the P2P link forms; debug 1:1 text with fresh mutual verification.
- **Wi-Fi Direct completion gate:** two phones exchange encrypted text **without a router or Internet**; unsupported OEM/OS cases fail clearly without weakening security; physical interoperability matrix later.

### Phase D — Useful voice / Push-to-Talk
- **D1.** Audio capture/playback components and local loopback simulation with explicit mic state; benchmark latency and battery; do not record in background by accident.
- **D2.** Hold-to-talk, release-to-stop and accessible toggle-to-talk modes; app background/disconnect/call interruption instantly blocks recording and transmission; clear transmit/receive states.
- **D3.** Frame and encrypt audio only inside independently reviewed authenticated sessions; LAN first, then Wi-Fi Direct. Bluetooth voice requires throughput/latency results before support.
- **PTT completion gate:** two phones can transmit short non-sensitive speech in both directions with acceptable measured latency and no unsolicited microphone capture or unauthenticated audio transmission.

### Phase E — Retention and convenience (separate choices)
- **E1.** Default **session-only**, with no intentional message-body persistence. Test rotation/background/process kill, backups and clipboard/keyboard limitations.
- **E2.** **Opt-in per-conversation encrypted local history**: choices session-only/1 hour/24 hours/7 days/14 days/30 days; hard **30-day maximum**. End-to-end content remains encrypted in transit; independently review on-device encryption/keys and backup exclusions. Expiry is enforced before every read. No indefinite mode.
- **E3.** Disappearing timer with local best-effort expiry; shorter settings shorten existing lifetimes, longer settings apply to future messages only, never resurrect deleted text. Never promise remote deletion.
- **E4.** **Optional remembered peer** is a separate opt-in retaining only a verified public identity for 1–30 days. Identity must be reproven on each connection. Explicit Forget and clock rollback handling; no default trusted contacts.
- **Privacy completion gate:** backup/device transfer, data deletion, expired contents and recipient-copy limitations independently tested and clearly explained.

### Phase F — Product finishing / dependability
- One obvious Home action and easy pair→verify→chat→disconnect, then preferred transport picker only when needed; no account, phone number, IP-field requirement or confusing tactical dashboard.
- Delivery state truth, actionable failure descriptions, permissions asked at use time, session privacy controls in the chat, no fake signals, accessibility (TalkBack, 48dp touch, font scale), portraits/landscape and small screens.
- Native notifications only if privacy-consistent, contentless by default and justified for foreground session; no push server or background discovery.
- Performance/energy: bounded queues, no always-on scans, no excess storage or network polling; measure CPU/battery before making resource claims.
- Reliable APK upgrade strategy preserving local Keystore identity and user settings; explicit installer preflight and release notes.
- Optional voice notes follow PTT; group/file/mesh/online messaging remain OUTSIDE initial release scope.

### Phase G — Acceptance, security and launch
- **G1. Owner-triggered physical QA batch** on multiple phones and Android/OEM versions (not performed until requested). Capture sanitized phase/counter evidence; WAN-off LAN, BT-only, Wi-Fi Direct, PTT, permissions denied, radio loss, process death, old QR, accessibility, APK update preserving identity.
- **G2. Independent professional protocol and privacy review** of exact QR/TLS/channel binding/approval/ACK/transport bridges, key lifecycle, storage encryption, backups and failure modes; repair all critical findings. Internal CI cannot stand in for this.
- **G3. Release delivery:** stable private release signing, Android Play App Signing, versioned AAB, final verified Privacy Policy, data safety and required legal/security declarations, closed/open Google Play tests and staged rollout. User will decide when the repository becomes private and when Google Play setup begins. No private signing keys in Git.
- **Release gate:** no production capability flags are enabled until independently reviewed **and** actual device QA passes. U.S. launch first; Canada/Mexico only after country-specific readiness.

## How we develop without the phones for now

Every work package should have (1) a narrow reversible branch/PR, (2) actual source changes rather than only documentation, (3) JVM and Python contract tests for state, failure, and permissions, (4) Android compile/build and APK manifest/dependency checks, (5) a no-screenshots-needed development handoff, and (6) an explicit list of physical device tests **not performed**. Merge only after green PR CI; check fresh main CI. Later run **one consolidated owner-led physical QA pass** against a stable-signed test build rather than blocking every small development PR.

**Definition of three independent states:**
- **Implemented:** code exists and compiles; UI may expose a debug-only prototype.
- **Automated verified:** CI tests, mocked/virtual faults, static permission and package inspection pass.
- **Physically/security accepted:** exact hardware outcomes and external assessment exist; only this can support release/security claims.

## Immediate sequence (first three actionable PRs)
1. **PR M3k — merged:** First real debug-only Bluetooth Classic inquiry, opt-in scan permission, 30-second count-only scan, cleanup and Android QA boundary.
2. **PR M3l — current:** Explicit unverified numbered signal selection in debug, transient bounded BluetoothDevice handles and cancellation. *Identity recognition/connection is still unimplemented*; no fake Eden peer list or unreviewed handshake.
3. **PR M3m — current:** Real debug-only secure RFCOMM socket host/join with explicit OS permissions, bound lifetime, disconnect and zero user-text/audio I/O. Follow-on: **M3n** approved authenticated session over this connection before any Bluetooth text.

Then prioritize **M3n Bluetooth authenticated debug text** and **C1–C3 Wi-Fi Direct**, while retaining periodic LAN security/ACK work. Once both transport families have viable debug communication paths, proceed with PTT and storage milestones.

## Current issues and correction
- [#26 — physical two-phone QA](https://github.com/PlayerEden/Eden-Comms/issues/26): owner-deferred, **open**.
- [#27 — durable test signing/upgrades](https://github.com/PlayerEden/Eden-Comms/issues/27): **open**. No promise existing disposable CI debug APKs update safely.
- [#19 — launch readiness](https://github.com/PlayerEden/Eden-Comms/issues/19): **open**.
- [#42 — independently reviewed true LAN text](https://github.com/PlayerEden/Eden-Comms/issues/42): source issue was marked closed although its security-review and physical acceptance checkboxes remain unmet. Reopen/track as a **release-blocking acceptance gate**, not as a denial that debug text implementation exists.

Documentation and source files are public development material; do not upload signing material, real messages, QR tokens, network addresses, peer certificate fingerprints or unredacted packet captures.
