# Eden Comms M2y — mutual final agreement confirmation in debug LAN

**Date:** 2026-11-05  
**Implementation:** Android debug-only fixed-phrase connection; no user-written messages enabled.  
**Independent protocol/security review:** NOT PERFORMED.  
**Physical two-phone approval/confirmation testing:** NOT TESTED.

## What changed

M2v added real per-phone **Approve/Decline** buttons after pinned mutual TLS 1.3 and reciprocal QR proof. M2w required both phones to compare an RFC 9266 channel binding. M2x bound each Approve/Decline control to the same canonical current-session commitment (QR certificate identities/nonces, new proof challenges and TLS exporter).

Before M2y, one phone unlocked the four fixed test phrases as soon as **it** observed two approvals. There was no separate demonstration that the peer app had reached that same step. This milestone adds a **final application-level two-way confirmation** on the already pinned debug TLS socket, sent automatically only after each phone observes both human approvals.

### Current debug-only flow

1. Both people scan fresh reciprocal QR codes; the devices finish pinned TLS 1.3, per-connection QR proof, RFC 9266 exporter match, then READY.
2. Each person independently taps **Approve** (or can Decline). The session commitment is checked before accepting any remote approval.
3. Each phone, **only after observing both approvals**, automatically writes one **CONFIRM** packet containing the same exact current session commitment; the peer separately validates it.
4. The fixed four-phrase test activates on a phone **only when that phone has all four facts**: its own approval written to TLS, peer approval received on the TLS socket, its own CONFIRM written to TLS, and the peer CONFIRM received and verified from that same TLS socket. A local write never implies peer delivery/read.
5. Decline, cancel, Wi-Fi change, timeout, invalid ordering, duplicate confirmation or wrong-session commitment remains fail-closed. Each new socket starts from the initial WAITING state.

### New strict wire control

The debug inner `TestPacketCodec` has a new type `07` **CONFIRM**, encoded as **35 bytes** `[version=01, type=07, reserved=00, current-session SHA-256 commitment (32 B)]` inside the existing bounded outer `FrameCodec` on pinned TLS. The existing 35-byte session-bound APPROVE (05) / DECLINE (06) remain, and fixed 3-byte READY (01) / PRESET (02) / HEARTBEAT (03) / CLOSE (04) remain unchanged. Generic 3-byte CONFIRM is refused. No typed text, audio or other arbitrary data is accepted.

The receiver validates this packet against its current commitment **before** calling `DebugApprovalGate.peerConfirmationObserved()`. The gate rejects duplicate/unsolicited confirmations and never unlocks presets with two approvals alone. A peer confirmation can arrive just before the local writer callback changes state: it is recorded only if the peer's APPROVE was already observed on this same ordered stream, and cannot enable presets before the local approval and local final confirmation are complete. A single queued confirmation per session limits duplicated sends.

### UI and privacy-preserving observations

The Android approval screen remains visible during automatic final confirmation. It distinguishes **both people approved, confirming session** from **local confirmation written, waiting for peer** and **peer confirmation observed, waiting for local**. The four test phrases do not appear before state CONFIRMED.

The shareable one-phone QA report now has distinct boolean fields for (a) test TLS/QR/link readiness, (b) local and peer human-approval control observations, (c) local and peer final-confirmation observations, and (d) actual fixed-phrase stage. It cannot expose text, identity, QR, IP, commitment or raw errors; a phone's report is **not** a certification or proof of the second phone's experience.

## Automated acceptance and review constraints

- `DebugApprovalGateTest`: no preset after just two approvals, either confirmation order, final confirmation racing with local callback, invalid/duplicate/unsolicited control, decline after confirmation, irreversible close, fresh-attempt isolation, all release flags false.
- `DebugApprovalControlWireTest`: uses actual bounded packet framing with two in-memory endpoint state machines, exercises four-event gate, stale/wrong session CONFIRM, duplicate/early CONFIRM, decline and duplicate approvals. **No actual Android radio or TLS is simulated with security equivalence.**
- `TestPacketCodecTest`: type 07 fixed 35-byte fixture, all 255 forbidden nonzero control-reserved fields, explicit rejection of old unbound controls, malformed payload, wrong versions, zero commitment and length errors.
- Privacy-safe `LocalQaReportTest` and `PairingTestUiCopyTest` cover truthful separation of completed TLS checks, human approval and final confirmation.
- `scripts/check_security_review_boundary.sh` requires `DebugApprovalGate.maySendPreset()` to require CONFIRMED and requires CONFIRM packet handling from the guarded debug-only test. `ProposedChatWireCodec` remains unwired to LAN/Android UI and `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain false.
- No new manifest permissions, Android SDKs, servers, persistent chat, microphone, background discovery, receiver acknowledgment or real text.

## Still NOT proven, even with final CONFIRM

This is **not a production-secure bilateral authorization protocol**. Confirmation is a status exchange over an already pinned TLS connection; it has not been independently reviewed for human identity, canonical transcript, races, downgrade/replay resistance, protocol-level liveness, unexpected I/O failure, use of RFC 9266 and app-level consent. Reliable global agreement across disconnections cannot be guaranteed: one device may have processed a final CONFIRM while another is interrupted. Both phones require their own positive and negative physical evidence.

A qualified independent security review, the actual two-phone no-internet test, and stable APK update signing are still required **before** integrating real typed text. The [core M3 secure messaging issue](https://github.com/PlayerEden/Eden-Comms/issues/42), [two-phone acceptance issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26), and [durable signer issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) remain OPEN.

### Add these cases to the consolidated physical worksheet

- T32: Two actual phones approve separately, see the final confirmation state, then reach preset stage only after both final controls.
- T33: One phone's final CONFIRM is dropped or peer socket interrupted: neither phone may fabricate the other's confirmation or delivery/read receipt.
- T34: Duplicate/early/incorrect-session CONFIRM injected through an isolated controlled test harness: protocol must close without enabling presets.
- T35: Cancel/Decline/background/Wi-Fi change during final CONFIRM: stale callbacks cannot reopen a completed/terminated session.
- T36: Old M2x client without CONFIRM cannot silently enable presets with a current M2y client.

**All tests are currently NOT TESTED on hardware.** The next meaningful milestone is an independent review of this exact M2v–M2y experiment and separately evidenced physical QA, then the intentionally disabled secure text sender/receiver.

Related docs: [M2x session commitment](M2X_DEBUG_SESSION_BOUND_APPROVAL.md), [M2w exporter matching](M2W_DEBUG_TLS_CHANNEL_BINDING_MATCH.md), [M2k physical test worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md).
