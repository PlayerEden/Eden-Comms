# F1 — LAN peer-app text ACK (development-only, not a read receipt)

**November 7, 2026.** Real debug-transport functional integration; no real phones tested, not security certified. The original ECTX ACK format existed only as a rejected candidate. This PR admits it *only* on the already-proven and mutually approved **debug LAN TLS 1.3** test session.

## Reviewed shape for this scoped development experiment

- ECTX outer framing: 4-byte `ECTX` magic, protocol version 1, **type 2 (ACK)**, reserved flags exactly zero, unsigned-positive 64-bit text ID within signed Long positive range, a 16-bit **zero body length**; exactly **17 bytes**.
- The ACK is serialized only inside the session's existing `FrameCodec` over pinned **mutual TLS 1.3**, after two QR proofs, exporter agreement, explicit human approvals on both phones, and separate final confirmations. The APP cannot use an ACK to grant trust or enable sending; `ReleaseCapabilities` remains false.
- On the receiver: accept valid fresh incoming text after confirmation; report `TEXT_RECEIVED`; then enqueue a bounded, best-effort ACK on the existing synchronized TLS writer. This signifies only **text passed the other app's receiving code path**, not displayed, read, persisted or accepted by a human.
- On the sender: register at most **16** outstanding unique monotonic sent text IDs. Only an actually returned ACK matching an outstanding ID during the same live confirmed test session can update the ephemeral UI. The ACK may race with the local TLS write callback; it is withheld until the write succeeds, then emitted after `TEXT_WRITTEN`. Unknown/duplicate/replayed/invalid ACK fails closed. No automatic resend, plaintext fallback or false success on missing ACK.
- ACK outbound queue is capped at **two** in-flight work items; unable to queue means no ACK is sent. When outgoing unacknowledged IDs fill the cap, additional user sends return Busy rather than accumulate memory.
- The UI shows **Other app returned test ACK • not read** beside the matching outgoing test bubble. The unacknowledged bubble continues to say **Written to test link**; a local write alone is not peer-app receipt. If a callback arrives first, UI retains at most 16 ID-only markers and never shows the receipt without the matching outgoing bubble. Clear, disconnect, or new pairing drops those markers.
- ACK ID and ephemeral UI counters are **not stored** or shared with analytics; no new Android permission, service, signer or backend.

## Threats, limitations, and protocol review note

This is a strictly limited development-protocol proposal being trialed in source/CI. An authenticated malicious peer could deliberately forge its own ACK; ACK means **an ACK control came from the pinned peer endpoint**, not human reading, irreversible delivery or secure peer identity certification. A peer crash after parsing can still lose content. A missed ACK remains unknown. ACKs are not retransmitted. Real thread ordering, packet interleaving, provider behavior and L2/L3 disruption need hardware QA; independent security review remains necessary before any release usage. The ACK ID is scoped by a fresh mutually approved QR/TLS session; no durable message ID or cross-session resume.

## Automated and device acceptance

- [ ] JVM exact 17-byte ACK and malformed/missing body, role/consent checks.
- [ ] JVM race ledger: unknown/early/duplicate/future ACK, queue cap and cancellation.
- [ ] Python source guard pins actual authenticated reader, writer, UI and release flags.
- [ ] Android build, full JVM/Python suite, security/permission/Play inventory and APK checks on PR.
- [ ] Fresh merged-main CI verified and recorded.
- [ ] **Owner-deferred:** two physical devices see distinct local write versus returned ACK; no ACK on denied consent/wrong QR/disconnect/drop, slow peers, spoofed/replay frames, 16-outstanding backpressure, cleared UI and process death.
- [ ] External protocol/security assessment prior to production.

**Rollback:** revert F1 PR to restore previous debug TEXT-only codec. No persisted data migration, signing change or release capability change.
