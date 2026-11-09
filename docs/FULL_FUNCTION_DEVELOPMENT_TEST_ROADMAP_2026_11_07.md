# Eden Comms — full-function development test roadmap

**Updated November 8, 2026. Source of truth:** `main` plus small reviewed PRs. [Original core feature plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md) remains the long-term specification; this is its prioritized execution schedule, not a replacement for earlier safety decisions.

## Revised development priority — complete usable core code before the next phone test

**Owner direction, November 9, 2026:** Stop spending development cycles on
Preview/APK checkpoints and simulated features while real core paths remain
unavailable. Continue small, reviewable source PRs with actual Android wiring
and automated tests, then run one consolidated physical test of the complete
core. Only request another signed Preview when the owner is ready to test.
Green CI establishes a source/build checkpoint, **not** hardware success.

**Core completion queue (implementations before consolidated device QA):**

1. **Same-Wi-Fi:** Keep the existing real mutually approved text/ACK + live
   voice. Fix real bugs; no more mock-only LAN milestones. E3 now brings an
   actual selectable RAM-only local message expiry timer to the live chat,
   with monotonic deadlines and no hidden persistence or remote erasure claim.
2. **Bluetooth Classic:** Bridge the real QR/TLS/exporter/both-confirmed
   RFCOMM worker into a single bounded, cancelable, same-engine encrypted
   TEXT/ACK chat owner and actual conversation UI. Do not make a diagnostic
   completion receipt or caller-supplied boolean into authorization.
   Retaining a live authenticated channel needs independent protocol review
   before even experimental use; implement the reviewed design in debug
   with negative-case tests. Bluetooth voice follows text and throughput QA.
3. **Wi-Fi Direct:** Complete actual OS group/socket -> pinned mutual TLS ->
   QR/challenge/exporter -> both human approvals -> TEXT/ACK and route it to
   the same clear chat UI, no router or IP input. No mocked READY, no
   unrelated default Wi-Fi route or plaintext fallback.
4. **Core privacy:** Implement opt-in encrypted local history (never default),
   expiry choices capped at 30 days, disappearing messages and an optional
   separately expiring verified peer identity. Every read checks expiration;
   backup/device transfer and deletion behavior need review. E3 session
   disappearing is **not** encrypted saved history or remote deletion.
5. **PTT across available authenticated transports:** Existing LAN half-duplex
   voice must remain cancelable and accessible; add Wi-Fi Direct after its
   encrypted text session works. Bluetooth voice only if measured radio
   bandwidth/latency permits. No background mic and no fake transmit status.
6. **App finish:** One obvious Connect -> Communicate -> Disconnect path,
   real transport availability indicators, readable state/permissions and
   actionable recovery. Keep telemetry, accounts, relay and mandatory cloud
   out of first release.

**Non-negotiable separation:** It is fine to write and CI-check all core
experimental code before physical testing. It is **not** fine to label that
code a validated secure messenger, turn on production release capabilities,
skip external cryptographic review, or claim reliable radios/voice before
handset evidence. Keep `ReleaseCapabilities` false until those gates pass.

## November 9 code milestone: actual OS Wi-Fi Direct TEXT/ACK integration (F3-C)

**Review PR #147; pending independent phone/security validation.** The previous
P2P code had separate three-QR preparation, short-lived Android group creation,
and zero-byte TCP diagnostics. F3-C now keeps the real QR coordinator in EACH
host/join activity and performs a new user-initiated, strictly group-interface-
bound TCP session only after the three fresh QR frames were checked locally.
The existing full pinned SSLEngine TLS/QR-challenge/exporter/commitment/
two-human-APPROVE/two-encrypted-CONFIRM protocol (also used for debug
Bluetooth) runs over that exact P2P socket, then owns real bounded encrypted
TEXT/ACK and chat UI. It is not unlocked by discovery or the old TCP test.

Actual Android P2P group callbacks, group ownership, unambiguous OS-interface
selection, foreground and permission state, socket closure, bounded expiry,
and cancellation are checked. Android host/join cannot obtain remote
application-message permissions from a radio name/address, a successful
connection, a QR transcript alone or simulated engine output.

The JVM suite already exercises the shared encrypted protocol end-to-end, and
new F3-C source guards prove the Android group/socket/QR/consent/chat owner
wiring. **Not yet proved:** physical Wi-Fi Direct interoperability, Android
TLS exporter availability across both phones, system P2P invitation lifecycle,
reconnection after OS disruptions, independent protocol review, reliable
voice, or production security. Do not use for secrets or emergencies.
Release flags remain OFF. No Preview packaging checkpoint.

After merging green source checks: harden real-device lifecycle edge cases
and unify connection method selection, then implement optional encrypted
history, time-bounded peer memory, and P2P PTT where practical. Reserve a
consolidated physical test for the complete debug core.

## November 9 code milestone: actual approved Bluetooth TEXT/ACK (F2-Q)

**Source implementation in PR #146:** Unlike F2-F/F2-G's isolated codec/lab,
F2-Q links the **real Android RFCOMM worker** to a bounded debug text session
on the **same** actually pinned TLS engine and original socket streams, only
after fresh QR challenges, exporter/commitment proofs, both human approvals,
and both encrypted final confirmations. It now has a visible Bluetooth text
composer, inbound message display, actual peer-app ACK states, 10-minute
foreground budget, current-attempt fencing, cancellation and RAM cleanup.
The existing two-engine JVM tests exercise real encrypted TEXT/ACK both ways
after the full control protocol. The JVM exporter/consent inputs are test
fixtures; Android's actual provider, Bluetooth radio and humans are not.

This supersedes the dated F2-A–O historical rows below that said Bluetooth
text is `unwired` or `not available`. **Still open:** independent review
of exact same-engine transition, Android 12+ OEM/Conscrypt/RFCOMM two-phone
acceptance, cancellation races and real audibility/latency for any later
Bluetooth voice. The debug path is unreviewed and not for private/emergency
communication; release `CAN_*` flags remain false. No new Preview build.

**Next code milestone:** Wi-Fi Direct Android OS group -> bound local socket
-> real mutually pinned TLS/QR/exporter -> both explicit human approvals ->
encrypted TEXT/ACK and unified chat UI. No anonymous P2P discovery or TCP
handshake alone authorizes data. Then finish optional encrypted local
retention/trust and share the completed core for one consolidated device test.

## Current delivery status and next integration queue (November 8, 2026)

**No duplicate roadmap:** this document is the active execution plan for the user's complete core application test. The [core feature completion plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md) remains the full long-term scope. A green GitHub workflow confirms source/build gates only; neither it nor a debug APK constitutes physical radio acceptance or security review.

| Core experience | Implemented / CI status | Needed before the intended two-phone demonstration |
| --- | --- | --- |
| **F0 + F1 — LAN text and peer-app ACK** | Debug-only reciprocal QR, mutual TLS, bilateral approval, two-way typed test text and peer-app ACK in source/CI. F0-LAN-QA adds **real event-derived ACK counts** to sanitized one-phone QA reports. The F0-LAN usability pass extends actual debug chat to **30 minutes from READY** and enables keyboard Send. The current F0-UX retry pass adds **one-tap fresh-QR same-role restart after a finished test** (no session/approval reuse). **No TLS/consent/wire change.** | Actual two phones must independently verify remote text, ACK behavior, wrong/expired QR, no-WAN operation and interruption; independent cryptographic assessment required. |
| **F2-A–E — Bluetooth identity and consent** | Debug Classic discovery, RFCOMM, mutual TLS/QR/exporter/consent, paired JVM TLS interoperability and one-use verified-path diagnostic completion integrated; latest merged F2-E commit `045b866cad1074b12996d0261c4c1fc70519dfd3` passed main CI. Bluetooth F2-J now puts the existing QR/TLS/approval diagnostic in a **guided QR-first host/join setup**, with optional raw radio scanner, early consent controls and fresh QR reset on terminal; **no live text enabled**. | Physical OEM/Conscrypt/provider verification and independent protocol/security review; diagnostic still closes immediately after consent. |
| **F2-F — Bluetooth text/ACK format — SOURCE/CI VERIFIED** | Pure debug-only strict codec + per-session finite TEXT/ACK ledger and virtual two-peer tests; **not wired to a socket or UI**. PR #99, merged-main commit `8f3c9dc113d8a58a7efe2fff569d5e70cfba291f`, CI passed. | Security-reviewed live session owner and transport, reliable ciphertext framing and reviewed UI integration. |
| **F2-G — encrypted frame interoperability lab — SOURCE/CI VERIFIED** | Future-only debug adapter and actual *two* JVM TLS 1.3 engines exchange F2-F TEXT and ACK over encrypted TLS records in both directions, plus forged ciphertext rejection. **Not connected to actual RFCOMM, QR approvals, UI or phone.** PR #100, merged main `551593b25695b31dc28d85e59b70ea9103440284`, green CI. | Independent record/session security review, Android provider/handset compatibility and a reviewed owner of the retained connection before any Bluetooth text. |
| **F2-H — bounded future session ownership — SOURCE/CI VERIFIED** | A debug-only one-shot resource owner and a corrected monotonic lifetime budget; JVM tests close an owned encrypted test channel, clear message state and reject stale callbacks, backward time, expired/denied sessions and concurrent teardown. **Neither the real RFCOMM socket nor F2-E consent receipt is wired into this owner.** PR #101, merged main `7b66ad4297ae64b0651a818642090a58e65a1588`, green CI. | Independent protocol review and physical Bluetooth/TLS provider QA **before** retaining any actual post-consent socket. A resource owner alone is not a verified communication session. |
| **F2-I — context-bound future Bluetooth handoff — SOURCE/CI VERIFIED** | Future-only debug owner now rejects different host/join role, peer certificate pin or transcript commitment at matching generation; context copied/cleared, with negative JVM tests and a source-mapped independent review matrix. **Arbitrary matching context is NOT proof, and real Bluetooth still closes.** PR #102, merged `af2a866fc50c84437f0ad7275d6d6aead63bedbc`, green main CI. | Expert sign-off and physical Android Bluetooth TLS/provider tests remain blocking before separately reviewed live socket retention, encrypted app text/ACK and chat UI. |
| **F3-A — actual Wi-Fi Direct TCP reachability — SOURCE/CI VERIFIED** | Debug-only host and joiner can explicitly perform one bounded TCP handshake using the OS-reported group owner private IPv4; **zero application bytes** and no identity/authentication. Real JVM loopback socket test; PR #103 merged as `4be138f1bd3311eebe8a73d7f3b3883391d9110a`, fresh main CI passed; **real P2P physical test pending**. | Two-phone Wi-Fi Direct routing and group timing QA; the result is untrusted and must never enable text or trust. |
| **F3-B1 — QR-to-pinned mutual TLS over real JVM TCP — SOURCE/CI VERIFIED** | Future-only debug lab validates full three-QR public transcript, generates opposite expected public certificate pins, then runs actual two-endpoint TCP TLS 1.3 mutual handshakes and wrong-pin/expired/tampered QR negative tests. **JVM loopback ONLY, no real P2P socket or application payload.** PR #104 merged-main `36da69b6b91042106135b3cc99949eb5d7b22e48`, full CI green. | Independent protocol review and two-phone Wi-Fi Direct/OEM TLS testing before live Wi-Fi Direct QR-bound transport, consent or messaging. |
| **F3-B2 — encrypted QR challenges and transcript agreement over real JVM TCP TLS — SOURCE/CI VERIFIED** | Future-only JVM loopback test, extending F3-B1's mutually pinned real TLS sockets with existing four-record fresh nonce challenges and two strict, encrypted role-ordered 32-byte full transcript commitments. Wrong QR nonces and mismatched issuedAt are rejected even with correct cert pins. **No live Android P2P, user content or human consent.** PR #105 merged `21b3e8e9e11507e528d9f458c4ccf94a8954d3ea`, green merged-main CI. | Independent review, actual Android P2P same-socket/Conscrypt/exporter device tests and two approvals still block live chat. |
| **F3-B3-A — P2P interface-bound real TCP diagnostic (merged #106)** | Fixes a physical test gap: Android may redact the host owner address, and a joiner socket without a local bind could follow another network route. Debug-only host/join re-read exact OS P2P group interface, require one eligible private local IPv4, and bind the test socket source/host listener to that interface; wrong subnet/missing group fails closed. **No data, TLS, trust or live chat.** PR #106 merged main `27988e58`; physical QA pending. | Two-phone physical OEM test of interface visibility/routing, cancel, same-group membership and 5-second listener coordination. QR↔TLS peer binding and external security review remain open. |
| **F3-B3/C — live Wi-Fi Direct authenticated text** | Anonymous P2P link and three-QR preparation exist, but **no TLS on a real P2P connection**, radio-to-QR binding, bilateral consent or text path. | Reviewed and physically validated same-channel pinned TLS, QR nonce/exporter proof, two human approvals, encrypted TEXT/ACK, disconnect. |
| **F4 — PTT / F4-D4 future LAN voice rehearsal** | Local-only D1/D2/D3 recording, 20 ms frames and interruption handling are source/CI integrated. F4-D4 adds a separate **debug-only, unwired** strict 658-byte voice-frame candidate and synthetic JVM ordering/replay tests; explicitly demonstrates lack of cross-session authentication. **No remote voice or live voice packet sent.** | Independent LAN session/voice cryptographic and UX review, a real session/utterance binding, bounded encrypted streaming/playback and physical two-phone QA remain blockers before any remote voice. |
| **F5 — optional privacy** | Session-only RAM chat; local Hide/Clear supported. | Optional encrypted retention (maximum 30 days), disappearing messages, optional verified public identity recall, and data review. |
| **F6 + F7 — test APK, acceptance and launch** | Normal debug builds remain transient. The new **independent .preview APK** uses a fixed public AOSP test signer and higher per-run versionCode (as in Sector Rebound/Operation Phosphor); no user secrets. Existing protected private signer and F6-U1 safe installer remain optional for other package. **Preview workflow and physical upgrade acceptance are distinct.** | First install of preview cannot upgrade/transfer original `com.edenpractical.comms` data. Subsequent matching preview builds are static-compatible, but real phone save/Keystore continuity needs a device test. Private release signer, two-phone radio and independent security review required for production. |

**Practical development order (user testing and convenience first):** (1) polish the existing real LAN debug chat and QR UI so retrying never requires remembering the previous role (**F0-UX fresh-QR retry**); (2) record actual two-phone observations for LAN, no-WAN, peer-app ACK, disconnect and privacy; (3) move **Bluetooth Classic** from already integrated diagnostic/TLS/consent and future text codec to a real reviewed retained session with physical interoperability checks; (4) bind **Wi-Fi Direct** OS-selected group and socket to the verified QR/TLS peer before enabling text; (5) develop **foreground LAN push-to-talk** on a reviewed live channel and test actual cross-phone audio; (6) optional privacy improvements after the working communication paths. **Development APK updates are solved with the existing separate Preview workflow**, and further release-style signing work is deferred. Independent protocol review and physical tests remain required before making secure-communications or production claims. No offline transport or human approval may be assumed from a cosmetic UI cue. Continue non-sensitive UI, lifecycle and test-preparation work **without representing them as functional radios** or authorization to send; actual OS radios, peer identity and both approvals must come from the working session.

**F2-P real Bluetooth consent timeout is terminal (November 8):**
Hardened the **actual** debug Bluetooth consent decision used by the
real RFCOMM/TLS controller: once its 25-second wait times out, it
consumes the decision, and later APPROVE/DECLINE callbacks cannot
appear accepted. A monotonic nanoTime bound also rejects a late tap
if the worker has not yet awakened. Clock rollback, interruption,
duplicate waiters, foreground/permission closure and just-in-time
approval stay fail-closed with deterministic JVM tests and source
guards. This fixes a real lifecycle ambiguity, not a mock future owner.
The same verified diagnostic still closes after both confirmations.
No Bluetooth TEXT/ACK, TLS retention, new permissions or production
communication authority. Two-phone Android testing and independent
same-channel security review remain open.
[F2-P late-tap acceptance gate](F2_P_TERMINAL_BLUETOOTH_CONSENT_DEADLINE_2026_11_08.md).

**F2-O actual QR, transcript and consent over same JVM TLS session (November 8):**
Extends the genuine two-peer pinned JDK TLS 1.3 integration fixture
beyond F2-N's synthetic control *sizes*. It now runs the actual ordered
QR challenge/echo, role-specific exporter comparison, derived transcript
commitment, and encrypted APPROVE/CONFIRM protocol code on the **same**
engines/streams before continuing to encrypted TEXT/peer-app ACK both
directions and F2-M future owner shutdown. A wrong exporter is rejected
before either simulated approval callback. The exporter is **fixture
supplied**, not Android channel-derived; approvals are **automatic test
callbacks**, not human; and the future owner still uses caller claims.
This is NOT Android Bluetooth RFCOMM/Conscrypt or authorization for chat.
The real Bluetooth controller still closes after human consent, and
independent security review and physical two-phone evidence still block
actual socket handoff and live text.
[F2-O test/review handoff](F2_O_FULL_JVM_QR_CONSENT_SAME_ENGINE_2026_11_08.md).

**F2-N exact same-engine TLS control-to-text JVM rehearsal (November 8):**
The two real, mutually pinned JVM TLS 1.3 engines and their original
byte pipes now traverse all five strict encrypted control **record
lengths**, retire the old control adapter, and exchange encrypted
TEXT/peer-app ACK in both directions on the **same** TLS engines and
byte streams. The F2-M bundled future owner then closes both engine
outbound and its pipes on expiry/cancel; an encrypted leftover
control packet is rejected as invalid application text.
This provides stronger continuity regression, **not proof of real
Bluetooth QR/exporter/consent**: control payloads and owner booleans
in this fixture are synthetic. No RFCOMM controller/probe wiring, new
Android permission, actual socket retention, UI, or production chat.
Physical Android provider tests and independent protocol review still
block live Bluetooth messages.
[F2-N test/review handoff](F2_N_SAME_ENGINE_CONTROL_TO_TEXT_HANDOFF_JVM_2026_11_08.md).

**F2-M future same-channel resource teardown bundle (November 8):**
A debug-only, **unwired** `DebugBluetoothFutureChannelBundle` now models
one owner closing its caller-supplied TLS record adapter, RFCOMM-like
Closeable and associated SSLEngine outbound together, in order, at most
once and despite individual close failures. It can be held as the single
Closeable in the existing F2-H/I future session owner, with JVM
expiry/context-mismatch/cancel-concurrency tests and source privacy
guards. This is NOT a real verified same-socket TLS handoff: arbitrary
caller-owned objects and a mock radio are not cryptographic evidence
or permission to send. The actual Bluetooth diagnostic still closes
its RFCOMM socket immediately after both confirmations. Independent
protocol review and physical Android provider testing must precede
any live Bluetooth text/ACK or socket retention.
[F2-M reviewer handoff](F2_M_FUTURE_BLUETOOTH_CHANNEL_TEARDOWN_2026_11_08.md).

**F2-L monotonic Bluetooth post-consent receipt (November 8):**
The actual debug Bluetooth RFCOMM/TLS worker now issues its existing
non-authorizing, one-use consent completion with an Android monotonic
elapsed-time stamp; the real active controller consumes it with a second
elapsedRealtime sample inside its socket-token, role, foreground and
permission gate. Both QR wall-clock expiry and ≤3000ms monotonic age
must pass: moving the wall clock cannot revive a stale receipt. JUnit
negative-clock, QR-expiry and one-use tests and real-callsite source guards
added. The diagnostic **still closes the exact RFCOMM socket** immediately
after confirmation; no Bluetooth text/voice, stored trust, new permissions,
signing change or production capability. External same-channel TLS owner
review and physical Android 12+ Bluetooth provider tests remain blocking.
[F2-L handoff](F2_L_MONOTONIC_BLUETOOTH_CONSENT_COMPLETION_2026_11_08.md).

**F4-S non-blocking live microphone reads (November 8):**
The approved foreground LAN hold-to-talk AudioRecord worker previously used
a blocking read even though F4-O prevented post-Stop PCM enqueue. F4-S uses
non-blocking reads with a 5 ms retry, a monotonic 450 ms stalled-driver cutoff
and an unchanged hard 3-second turn. A completed 20 ms frame still passes
through the F4-O cancel gate. JVM clock/partial-read/stop/idle tests and Python
source/privacy/approval guards added. The accessible tap fallback and all
TLS/QR/approval, audio wire, package, signing and production flags remain
unchanged. Preview 200008 predates F4-S; real OEM stop latency and audibility
remain physical two-phone QA, not CI acceptance.
[F4-S driver-read QA](F4_S_NONBLOCKING_LIVE_MIC_DRIVER_BUDGET_2026_11_08.md).

**F4-R signed local-speaker-diagnostics Preview (November 8):**
The next numbered, public-development-signed Preview packages the already
merged F4-Q local AudioTrack speaker-start and refused/overflowed PCM
observation counters. A focused two-phone worksheet compares the real
audible experience on each device to the independent local QA counters,
without retaining speech. The existing Preview app ID, public test signer,
no-cloud/approved-LAN-only boundaries, and production feature flags
remain unchanged. The signed build's checksum and package/signer must
pass CI checks; device installation, audibility, network interruptions
and independent security review remain separate owner QA gates.
[F4-Q field guide](F4_Q_LOCAL_VOICE_PLAYBACK_EVIDENCE_2026_11_08.md).

**F4-Q honest local speaker observations for two-phone QA (November 8):**
A correctly validated incoming voice START/END no longer hides a silent
speaker rejection. Android's actual AudioTrack start callbacks and
locally rejected START/DATA frames now produce distinct one-time per-turn
on-screen notices and bounded content-free counts in the existing user-copied
one-phone QA report. Old attempts cannot mutate a new session; intentionally
hidden playback is not treated as failure. JVM concurrency/QA-format
tests and Python source checks added. No new wire, permission, server,
production communications flag or audio storage. Preview 200007 predates
F4-Q; real speaker audibility still requires the owner's two-phone QA.
[F4-Q field report](F4_Q_LOCAL_VOICE_PLAYBACK_EVIDENCE_2026_11_08.md).

**F4-P signed handset Preview of F4-O (November 8):**
Consolidates the already-merged atomic outgoing microphone Release/Hide
frame barrier and cancellation-safe 240 ms incoming speaker buffer into
one new versioned Preview, together with the earlier F4-H through F4-N
reliability changes. Updated field guide covers one-phone smoke QA and
two-phone rapid stop, normal talk turnaround, Wi-Fi-off, speaker cleanup
and fresh-QR reconnect. The public AOSP test signer and separate Preview
package remain unchanged. Android CI and APK identity verification are
the *build* acceptance gates; user-device audible/timing outcomes and
security review remain pending.

**F4-O live audio stop-frame barriers (November 8):**
The real Android AudioRecord worker now serializes its final 20ms PCM
queue handoff against Release/Hide/background, preventing a late frame from
being newly queued *after* Stop wins. The receiver's 240ms playback
queue now serializes offer/cancel/wipe and checks canceled state after poll
before AudioTrack playback. JVM concurrent-stop/overflow and Python
source checks added. No wire, TLS, approval, signer, permission or
production change. Preview 200006 remains the signed handset baseline;
two-physical-phone timing and actual audibility are unverified.
[F4-O test handoff](F4_O_LIVE_AUDIO_STOP_BOUNDARIES_2026_11_08.md).

**F4-N consolidated signed rapid-cancel test Preview (November 8):**
The next separate, pinned-public-test-signed Android Preview packages the
F4-L canceled-request reply fix together with the green F4-M virtual
two-phone packet regression lab. Updated test guide includes a short,
repeatable five-press rapid cancel scenario, a fresh-grant voice turnaround
and Wi-Fi/offline disconnect QA. It keeps the existing Preview package,
public development signer, debug-only transport and permission boundary.
Real Android in-place upgrade and audio behavior remain user-device checks,
not things CI can establish.

**F4-M integrated virtual two-phone radio regression (November 8):**
Deterministic JVM tests now drive two independent floor arbiters and real
TestPacketCodec framed bytes through full host/joiner turnarounds, rapid
cancellation, delayed GRANT/DENY, control priority, bounded PCM queues,
broken-session teardown, stale callbacks and invalid frame sequences.
This is a source/JVM simulation, **not** live Wi-Fi/TLS transport or real
Android microphone/speaker acceptance. No runtime/permission change;
Preview 200005 remains the last signed handset test baseline.
[F4-M lab handoff](F4_M_VIRTUAL_TWO_PHONE_RADIO_REGRESSION_2026_11_08.md).

**F4-L multiple canceled radio requests / stale reply handling (November 8):**
When a joining phone rapidly cancels multiple Talk requests, old legitimate
GRANT/DENY responses must not be mistaken for new permission or a fatal
protocol violation. The joiner now retains at most 32 canceled IDs,
consumes each late response once, rejects replays and clears all IDs on
session closure. JVM virtual host/joiner sequences and Python source
checks added; real two-phone timing/voice tests remain pending. Preview
200005 predates F4-L, so a future consolidated test build must include it.
[F4-L review and QA handoff](F4_L_CANCELLED_FLOOR_RESPONSES_2026_11_08.md).

**F4-J immediate local audio shutdown on link loss (November 8):**
A one-shot session close hook now stops Android microphone/speaker directly
during unexpected encrypted-session teardown rather than waiting for a
posted UI event. Object-identity ownership fences prevent a stale old QR/TLS
session from stopping audio from a newer attempt. New JVM concurrency and
Python source guards; real two-phone Wi-Fi-off, interruption, screen lifecycle
and speaker checks remain untested. No silent reconnect or new permission.
[F4-J handoff](F4_J_LINK_LOSS_AUDIO_TEARDOWN_2026_11_08.md).

**F4-I cancellation-safe microphone send handoff (November 8):**
A one-shot synchronized per-recording gate now orders an accessible tap
capture's AudioRecord callback against Cancel/Hide/background. Cancel
before send prevents a late voice clip from being enqueued; already-queued
sound cannot be retracted. No-audio and live microphone-start failure
also release otherwise unused floors. Concurrency/JVM and source
regression coverage added; real handset cancellation timing and LAN
audibility still untested. Source only, no new Preview signed yet.
[F4-I verification handoff](F4_I_VOICE_CALLBACK_CANCEL_GUARD_2026_11_08.md).

**F4-H floor-control scheduling under live audio (November 8):**
On an already-approved local TLS test session, a separate four-packet
bounded *scheduling* lane now sends REQUEST/GRANT/DENY/CANCEL without
waiting behind a three-second PCM writer. All actual writes still share
the exact same TLS output stream/outputLock; active voice END then
RELEASE remains writer-ordered. JVM audio-blocking/congestion tests
and source guards added; two-phone collision timing and real
speaker/microphone tests remain unverified. **Preview 200004
successfully updated an older Preview on one user's Android
phone** (only installation acceptance, not LAN voice QA).
[F4-H test handoff](F4_H_FLOOR_CONTROL_SCHEDULING_2026_11_08.md).

**F4-F host-granted PTT floor (November 8):** Both the live hold-to-talk
and accessible record-then-send paths now request the SAME per-session
host-arbitrated floor before starting a microphone. The joiner must receive
a matching encrypted GRANT; the host serializes simultaneous requests,
rejects early release/replays, treats stale canceled grants as inert and
closes the entire TLS session on a stuck lease rather than silently handing
ownership to another microphone. Debug-only source/CI work, **physical A/B
collision and audible latency QA NOT DONE**, independent review outstanding.
[F4-F source/QA handoff](F4_F_REAL_LAN_FLOOR_HANDSHAKE_2026_11_08.md).

**F4-E observed radio state (November 8):** The real approved LAN test
reader now produces explicit START/END observations for the nearby conversation.
The UI displays `RECEIVING • OTHER PHONE IS TALKING`, local transmitting
status and prevents a *new* microphone press while an already observed
remote turn is active. This does not arbitrate simultaneous presses or
certify that the other phone's sound is audible. Source/CI first, device
QA still pending. [F4-E change and next deterministic floor](F4_E_OBSERVED_RADIO_STATUS_2026_11_08.md).

**F4-D live LAN streaming milestone:** The actual Android debug hold-to-talk path now queues and sends **20ms PCM frames DURING the press** over the already-approved reciprocal-QR/pinned-TLS Wi-Fi session and starts bounded AudioTrack MODE_STREAM playback on the other handset before END. Bounded outgoing/incoming queues (12 frames each), hard 3-second turn cap, no recording persistence, cancellation only stops future audio and all existing debug/release permission and explicit approval gates remain. **Source/CI milestone only:** no physical Android A↔B evidence, actual latency measurements or reviewed distributed floor arbitration; simultaneous starts can still collide. The original tap record-then-send fallback remains. [F4-D hands-on test and next floor/latency work](F4_D_LIVE_WIFI_PTT_STREAM_2026_11_08.md).

**F4-C practical push-to-talk interaction:** Actual foreground debug chat offers **hold to record / release to send** with gesture cancellation, a 3-second maximum, clear microphone permission handling and explicit accessibility/tap fallback. Reuses F4's approved two-phone Wi-Fi TLS short burst; **does NOT transmit speech during a hold or arbitrate a shared radio floor yet**. Preview 200003 predates this; two-phone gesture/voice QA remains open. Next: actual low-latency packet streaming *while held*, early peer audio playout, floor state, then physical testing. [F4-C implementation/test notes](F4_C_HOLD_TO_TALK_GESTURE_2026_11_08.md).

**F4-B pre-phone reliability and privacy:** Short/failed microphone captures now complete with a clear no-audio status instead of stranding the voice button; local speaker feedback distinguishes real Android playback start from merely receiving encrypted frames. Hiding the conversation stops local mic/speaker and suppresses incoming sound (not the remote transport). The test packet layer best-effort wipes temporary encoded voice/text/consent buffers after TLS read/write, including error cases. No release capability or APK signer change. These are **source/test confidence only**, not physical voice QA. [F4 hands-on test checklist](F4_LIVE_LAN_VOICE_BURST_TEST_2026_11_08.md). Next: actual two-phone recording/playback and UX observations, then low-latency hold-to-talk/floor control.

**F4 first actual LAN voice burst:** The debug nearby conversation now supports a *real two-phone* up-to-three-second microphone capture → same TLS session ordered PCM transfer → peer phone speaker playback. The pre-existing pinned TLS 1.3, reciprocal QR, matching channel binding, bilateral human approvals and final confirmations are prerequisites; no audio can go over a raw radio socket or before the existing approval gate. Explicit debug microphone permission, foreground-only behavior and no saved audio; release voice flag remains false. This is **record then send**, not yet streaming press-and-hold walkie-talkie, and physical A↔B QA/independent review remain OPEN. [F4 field test notes](F4_LIVE_LAN_VOICE_BURST_TEST_2026_11_08.md). Next: actual two-phone test, then low-latency PTT/floor control before expanding to reviewed Bluetooth or Wi-Fi Direct transports.

**F2-K actual Bluetooth verification visibility:** The real debug Bluetooth worker now emits **five coarse verification milestones only after its actual TLS pinning, encrypted QR challenges, exporter, commitment and both final confirmations succeed**. Controller gates these to the current foreground/permitted attempt and the Activity shows only the latest non-sensitive stage, including at terminal. This makes the **real two-phone handshake** diagnosable, not a synthetic success receipt or approval to retain a socket. The existing connection still closes after consent. [F2-K test and safety notes](F2_K_LIVE_BLUETOOTH_VERIFICATION_VISIBILITY_2026_11_08.md). Next *functional blocker* is independently reviewed same-engine/socket continuation with actual two-phone Android TLS provider evidence, then Bluetooth TEXT/ACK.

**F2-J guided Bluetooth milestone:** The actual debug Bluetooth Activity now presents **QR first**, stage-aware 3-step host/join guidance, radio search when relevant to the joiner, approval/decline beside the active link, optional raw radio diagnostics and a fresh QR/signal reset after the diagnostic ends. This is convenience for the **real diagnostic**, not Bluetooth messaging; it still closes after bilateral encrypted confirmation, and independent review plus two-phone Android testing remain necessary before live text. [Implementation and hands-on notes](F2_J_GUIDED_BLUETOOTH_TWO_PHONE_SETUP_2026_11_08.md).

**C2 foreground convenience:** QR display/scanning, local connection checks, mutual consent and test chat now request Android View-level screen-on only while visibly active; idle/result/exit release it. Existing ON_STOP still closes the test socket. No WAKE_LOCK permission, permanent service, saved preference or extra radio permission. Actual device timeout and cleanup QA remain open.

**User checkpoint (November 8):** owner described Preview build 200002 as “good.” That is helpful usability feedback, **not** a documented pass of every physical two-phone, no-WAN, negative or independent security acceptance case in #26/#42. Next hands-on review should focus on what works and what is awkward, not recreating test harnesses already present.

### Convenience milestones and what remains

| Priority | User-facing result | Status / next gate |
| --- | --- | --- |
| **C1 — quick retry without shortcuts** | From ended LAN test, one tap **Try again • Show/Scan a new code** based only on the previous role; full reset, new QR exchange and approvals. Keep **Start over** to change roles. | Source/CI check for this PR; physical usability feedback on next Preview APK |
| **C2 — low-friction peer connection** | Clear show/scan/approve/connected guidance, readable phone UI, visible incoming messages and honest ACKs. Keep the display awake **only while a foreground QR or test chat is actually visible**; no account, manual IP or background service. | View-scoped screen-awake policy + JVM/source tests; verify on two phones including timeout/cancel/exit, then adjust from tester observations |
| **C3 — reliable nearby methods** | Guided in-person Bluetooth exchange: one phone shows QR, the other scans, followed by user-controlled host/search/join and bilateral approval; then build actual encrypted text over a reviewed retained session. Wi-Fi Direct follows on the same simple UX. | **F2-J** guided UI + **F2-K** real TLS verification checkpoints [implemented](F2_K_LIVE_BLUETOOTH_VERIFICATION_VISIBILITY_2026_11_08.md); two-phone physical radio/provider evidence, independent same-socket protocol review and actual TEXT/ACK still OPEN |
| **C4 — remote voice and optional privacy** | First up-to-three-second A↔B voice burst on existing approved same-Wi-Fi TLS. After device QA, progress to true continuous low-latency hold-to-talk, half-duplex floor control and listening/transmitting indicators; no saved audio. | **F4 first real LAN voice burst implemented in source** [F4 QA handoff](F4_LIVE_LAN_VOICE_BURST_TEST_2026_11_08.md); Android two-phone audio QA and independent review OPEN; Bluetooth and Wi-Fi Direct voice not connected |


**Repeatable Preview testing:** the [offline Preview APK build path](OFFLINE_PREVIEW_APK_UPDATES.md) now keeps public AOSP signing on **`com.edenpractical.comms.preview` only** with per-run version increments and no GitHub secrets. This is a separate debug app from any prior `com.edenpractical.comms` installation; it cannot silently update/carry over the original package's identity. Real device update QA remains open.\n\n**F6-U1 update safety slice:** [owner-only explicit install-over and public package readback](F6_U1_EXPLICIT_SAFE_INSTALL_OVER.md) is prepared with mocked CI tests. It does **not** activate GitHub signing secrets, update an Android device, or verify Keystore/settings continuity; issue #27 remains OPEN. Avoid consuming extra emulator Actions minutes to simulate physical acceptance.\n\n**F0-LAN usability:** [live test chat session and keyboard Send](F0_LAN_CHAT_TEST_USABILITY_2026_11_08.md) now allow a longer (still finite) experimental chat, retaining live Wi-Fi/peer/approval/liveness gates. Physical two-phone QA and independent review still OPEN; no new transport capability is implied.\n\n**First practical test checkpoint:** F0-LAN-QA provides a source-backed two-phone worksheet and count-only peer-app ACK evidence (not a receipt or signoff). Once the owner is ready, compare both phone QA reports after LAN two-way typed messaging + ACK and test local mic/speaker independently; don't pretend Bluetooth, Wi-Fi Direct or remote PTT already work. For a **full core-feature test**, real authenticated text on all three transports and remote PTT must also have honest reachable user flows; independent security review and physical device tests remain mandatory before claiming security acceptance.

## Goal, scope, and three distinct finish lines

**Goal:** When the owner is ready to test, every promised *first-version* communication interaction should have a real source implementation and an honest, reachable debug-build UI. No fabricated radios, confirmations, delivery receipts or mock voice. The build must fail closed for unsupported links. We are building **on paper and in CI now**; real Android and independent protocol validation remain separate and mandatory.

1. **Implemented:** code + user path exists, with cleanup on error/background and explicit consent.
2. **CI verified:** JVM/protocol/property tests, Python/source/permission review, Android debug APK and fresh merged-main CI; no phone claims.
3. **Device/security accepted:** owner two-phone positive and negative tests **plus** independent assessment where trust/crypto/data are involved. Only then consider non-debug features or publishing.

**First-version scope:** two people, no account or Internet, LAN + Bluetooth Classic + Wi-Fi Direct text, optional foreground push-to-talk after an authenticated connection, explicit disconnect, session-only by default, and separately opt-in privacy features. No group chat, mesh, voice calls in background, cloud sync, attachments, public contacts directory, or automatic trust from a detected radio.

## Functional work packages, in order

| Step | Deliverable & real user action | Prior art/current state | Code + CI definition of done | Hardware/review gate (not yet performed) |
|---|---|---|---|---|
| **F0** | **One dependable basic session:** Show QR → Scan QR both ways → approve → text → disconnect/restart. Explicit text-status wording. | LAN pinned TLS 1.3 + proof + mutual approvals/confirmations and typed debug text; M5-A–E safe teardown/retry, not device tested | Verify end-to-end source state, finite timeouts, no plaintext fallback. Keep ephemeral messages and code/permission audit. | Two different phones, same Wi-Fi without Internet, wrong/old QR, refusal, interruption, two-way send, update continuity. |
| **F1 — SOURCE/CI INTEGRATED** | **Peer-app ACK for LAN debug text:** Distinguish queued, written to TLS, and an ACK actually returned by peer's app. Never claim read. | LAN debug ACK handling implemented with bounded IDs; physical device and security review outstanding. | Review fixed 17-byte zero-payload ACK protocol; accept it **only** on the authenticated, mutually confirmed live debug TLS session; reader ACKs only validated new text; sender tracks bounded outstanding IDs; duplicate/unknown/replay/early ACK handled fail-closed. UI shows honest per-message receipt, never plaintext or storage. Tests for races/size/cancel and CI. | Real phone receipt ordering/latency/interruption, external protocol assessment before production. |
| **F2** | **Bluetooth one-to-one text:** Discover/choose → show reciprocal QR → verify pinned peer → approve on each phone → short typed chat → disconnect. | Debug discovery, bounded RFCOMM socket, QR, mutually pinned TLS engine, QR/exporter/commitment, two-person consent. Diagnostic currently *always closes* afterward. | Before actual chat, review RFCOMM TLS record adapter, authenticated continuation and consent trace; introduce bounded post-consent session lifetime, reliable frame reads/writes and text/ACK with no insecure fallback. Separate reviewed PRs for protocol validation, retention of live session, then debug chat UI. | **Blocking:** independent review of record parser/session and real Android provider interoperability; physical test of radio, OS permission, false-target rejection and interruptions before claiming text works. |
| **F3** | **Wi-Fi Direct one-to-one text without router:** identify target, form short-lived group, verify QR peer, mutually approve, chat, disconnect and remove group. | Debug discovery, anonymous candidate selection, explicit link request/group hosting, separate QR preparation. **Not cryptographically linked.** | Bind selected P2P link and actual socket peer to QR-pinned TLS public identity; connect real group host/client; reuse reviewed session, approval, text/ACK; explicit group cleanup and failure states. No trust based only on a group/device name. | **Blocking:** platform/OEM two-phone P2P interoperability and independent target/identity protocol review. |
| **F4** | **Voice/PTT:** Hold to speak (plus accessible toggle), release stops; peer hears only in approved foreground session; visible transmitting/receiving state. | D1 real local mic → local speaker debug test (5s PCM), not remote. | Audio framing and bounded jitter/queues, mic permission at use, only encrypted authenticated session, stop on background/call/disconnect, no audio file or hidden capture. LAN first, Wi-Fi Direct second; Bluetooth optional after throughput test. | Real devices: output route, latency, duplex, loss, Bluetooth throughput if attempted; independent protocol/privacy review before enabling remote voice. |
| **F5** | **Privacy features as separate choices:** session-only default, optional 1h/24h/7/14/30d encrypted local history, best-effort disappearing timer, independently optional 1–30d remembered verified public identity. | Debug in-RAM text, Hide/Clear this phone; no stored history or peer trust yet. | Explicit opt-in and erase/expiry-before-read, encryption/keystore/backup safeguards, clock rollback handling, no remote-deletion promise. Settings reflect what exists, not coming-soon switches. | Crypto/storage review; device backup/restore and deletion/expiry tests. Can be omitted from initial functional **communications** field-test build if clearly labeled. |
| **F6** | **Production-quality UX and package:** one obvious Connect action, clear method selector, text/voice once confirmed, end/back warnings, accessible status, predictable manual retry, stable test installer. | Native Compose, M4 UI, CI APK. Manual emulator visual QA and stable signer still not fully verified. | Emulator/screens/large fonts/TalkBack targeted checks, lifecycle and low-memory behavior, no secret logging, exact APK inventory, deterministic test signing and upgrades, versioned release notes. | Owner test matrix and repeatable identity-preserving upgrades. |
| **F7** | **Security, acceptance, release:** close the actual blockers rather than merely passing CI. | Development feature flags FALSE, no independent certification. | Threat model and protocol review package with binary identity, QR/session-binding, errors, ACK, audio, encrypted storage, 1:1 disconnect. Track critical fixes and reproducible evidence. | External security review, owner physical two-phone QA, signed release build, privacy policy/Play data safety and store testing. |

## Dependency / stop rules

- **LAN** is the only existing real debug text transport. Complete F1 within its authorized test channel; it does **not** grant Bluetooth/Wi-Fi Direct/PTT authority.
- **Bluetooth F2** builds on diagnostic M3n-C1 but may **not** just skip its mandatory close, attach a composer, or claim security. TLS engine/provider handling and peer identity/session semantics need expert review and physical validation.
- **Wi-Fi Direct F3** must bind the selected radio's resulting authenticated session to the actual QR peer; group formed is not proof of who joined.
- **PTT F4** never sends to an unverified/consent-pending session. On-device D1 proves only local capture/output, not network voice.
- All work uses explicit debug-only flows until real device and external security acceptance; `ReleaseCapabilities.CAN_CONNECT/CAN_SEND_MESSAGES/CAN_TRANSMIT_VOICE` stay **false**.
- Never upload QR payloads, keys, MAC/IP addresses, identities, private chat text or raw security traces. Document generic test observations only.
- **No phone now:** focus on source, deterministic JVM two-peer fakes, conservative protocol bounds, UX, and CI; owner initiates hardware testing when ready.

## PR template / verification contract

Each narrow PR must include a source change where safe; unit tests for success, malformed frames, early/duplicate/replay IDs, simultaneous disconnect and unknown states; permission/privacy/security audit; feature-local runbook with rollback; Android compile + APK review; **green PR CI and fresh merged-main CI**, recorded with immutable SHA. Failed checks are repaired, not ignored. Never change signing, package ID or existing app data without a separately reviewed migration.

## Consolidated testing day — planned, NOT RUN

- **Preparation:** choose a stable **same-signer** internal test APK (do not assume ordinary GitHub CI debug APK upgrades in place); verify package/version/fingerprint, keep both devices charged. No confidential content.
- **LAN baseline:** two phones with WAN disconnected but router on; reciprocal QR; both approvals/confirmations; send in both directions; verify text and peer-app ACK status vs local TLS write; disconnect/restart; wrong QR/refusal/roam/background/rotate.
- **Bluetooth:** turn Wi-Fi/cellular off; discover/select real target, verify QR identity and both consents, exchange test text if reviewed/implemented, stop radios/permissions mid-session and confirm close.
- **Wi-Fi Direct:** no router/Internet; connect supported devices; verify QR binding, exchange text when implemented, leave, confirm no lingering group.
- **PTT:** mic deny/grant, local sample, authenticated remote hold/release, speaker and headset, latency/packet loss, background/call interruption, no unsolicited recording.
- **Privacy & accessibility:** clear/hide/history settings, 30-day cap, timer semantics, backup exclusion, TalkBack, font scale, compact screen and orientation; device restart and APK update identity continuity.
- **Evidence:** screenshots only of generic/empty screens, counts/phase codes and sanitized QA logs. Keep actual QR/key/text/peer IDs out of issues.

## Accepted checkpoint vs current blockages

- M5-A through M5-E: test/cancellation/reset hardening, source/JVM/CI only.
- M6-D1: real on-phone mic/speaker loopback implemented, not physically exercised.
- **F1 LAN ACK and F2-F/G/H/I Bluetooth source/CI evidence are integrated; F3-A real TCP and F3-B1/B2 JVM QR-pinned TLS/session proof are CI verified; F3-B3-A debug P2P-interface TCP probe merged; F4-D2/D3 local audio source verified; F4-D4 isolated future LAN voice packet rehearsal is the current source slice, not a usable transport.** F2/F3/F4 are **not** claimed available as complete communication transports merely because diagnostics, protocol models or local audio exist.
- Blockers: owner-deferred physical QA [#26](https://github.com/PlayerEden/Eden-Comms/issues/26), stable signed upgrades [#27](https://github.com/PlayerEden/Eden-Comms/issues/27), security review of LAN [#42](https://github.com/PlayerEden/Eden-Comms/issues/42), manual emulator visual QA [#82](https://github.com/PlayerEden/Eden-Comms/issues/82), launch [#19](https://github.com/PlayerEden/Eden-Comms/issues/19). Treat a closed issue without its physical/review evidence as **not accepted**.

**Current next source slice after F4-D4:** F0-LAN-QA improves the real LAN debug test report by counting admitted peer-app ACK events and supplying a two-phone comparison worksheet, without modifying TLS, radio, data packets or the existing security gates. [F0-LAN QA evidence](F0_LAN_TWO_PHONE_ACK_QA_EVIDENCE_2026_11_08.md). Owner-deferred physical acceptance #26 and independent review #42 remain blocking.\n\n**After merged F3-B3-A (#106; `27988e58`):** physical P2P interface routing QA remains owner-deferred. F4-D2/D3 rehearse local ordered 20 ms audio playback and bounded cancellation; F4-D4 models an unwired, non-authorizing LAN PCM frame candidate; prioritize independent Bluetooth/Wi-Fi Direct session review and continue non-gated UI/PTT test groundwork by closing the independent session/record security review and two-phone provider gate before enabling persistent sockets; meanwhile develop Wi-Fi Direct and voice transport preparations without implying either is functional. Do not divert into an unbounded cosmetic audit. Keep scope honest: a complete *debug source* is still a hypothesis until real phones are tested.
