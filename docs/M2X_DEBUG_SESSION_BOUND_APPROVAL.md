# M2x — debug approval controls bound to the current QR and TLS session

> **Historical milestone:** M2y subsequently added a required session-bound final confirmation exchanged in both directions after both APPROVE controls. The M2x-era flow below is no longer the complete current behavior. See [M2y final confirmation](M2Y_DEBUG_MUTUAL_FINAL_CONFIRMATION.md).


**Date:** 2026-11-05  
**Scope:** Experimental, debuggable Android two-phone fixed-phrase test.  
**Real text/voice and peer trust:** DISABLED. **Independent protocol review:** NOT PERFORMED. **Physical two-phone test:** NOT PERFORMED.

## What M2x changes

M2v introduced live per-phone Approve and Decline controls, but those controls were generic `01 05 00` / `01 06 00`. M2w then required both phones to compare the standard RFC 9266 TLS channel binding before READY. M2x combines the existing **current per-connection** evidence into a canonical 32-byte SHA-256 *identifier* for an experimental approval: presenter and joiner certificate fingerprints, both fresh QR nonces, both per-connection proof challenges and the matching RFC 9266 tls-exporter. The canonical order is **presenter first**, regardless of which phone computes the digest.

The human approval UI does not become production authorization. This new SHA-256 digest is a **public session commitment, not a MAC, encryption key, human verification code or independently reviewed proof**. Both endpoint controls still travel exclusively inside the pinned TLS test socket. No endpoint, secret, pin, nonce, challenge, full exporter, digest or raw error is shown to users, logged to telemetry or saved persistently.

## Wire format and strict downgrade refusal

The existing 3-byte debug READY/PRESET/HEARTBEAT/CLOSE frames stay unchanged inside the bounded outer `FrameCodec`.

New approval controls are **exactly 35 bytes**:

| Field | Bytes | Meaning |
| --- | ---: | --- |
| Version | 1 | `01` — debug packet format |
| Type | 1 | `05` APPROVE or `06` DECLINE |
| Reserved phrase ID | 1 | `00`, never text |
| Session commitment | 32 | Domain-separated SHA-256 digest of the verified current connection transcript |

The commitment input uses the fixed ASCII domain `EdenComms-Debug-Consent-v1`, then explicit presenter/joiner role bytes `01 02`, followed by the **fixed-width** fields in this exact order: presenter fingerprint (32), joiner fingerprint (32), presenter QR nonce (16), joiner QR nonce (16), presenter proof challenge (16), joiner proof challenge (16), TLS exporter value (32). All fields are required and nonzero, and paired fingerprints/nonces/challenges must differ. Because each field has a fixed width, this encoding is unambiguous, but it is **not a reviewed standardized protocol**.

The receiving socket **first validates exact frame structure, then matches the commitment to its own freshly calculated value, then and only then calls** `DebugApprovalGate.peerObserved()` or handles DECLINE. An old generic three-byte control, wrong-length or malformed packet, stale session commitment, role/pin/nonce/challenge/exporter mismatch, or all-zero commitment fails closed. The connection never silently upgrades an old approval record to this new representation.

The local sender creates the commitment only after QR proof and successful live RFC 9266 matching, writes the explicitly chosen action to the pinned TLS socket, and counts the local approval only after that write completes. `SessionProofChallenges` copies are cleared after derivation and `debugConsentCommitment` is zeroed on session close (best effort, not guaranteed physical memory erasure). All old error/timeout/cancellation/heartbeat safeguards continue.

## User-visible behavior

The same simple two-phone test remains:

1. Both phones exchange fresh reciprocal QR codes and authenticate pinned TLS.
2. Complete the role/challenge QR proof, match the channel exporter and exchange READY.
3. Each person can **Approve** or **Decline**; their control is now tied to the exact current test session.
4. Only if this phone has both local TLS write and matched peer approval observation can it exchange the **four fixed phrases**.

The UI still describes this as an experimental approval; it does not enable typed text or voice or mislabel a local TLS write as a guaranteed peer delivery.

## Automated tests and source safeguards

- `DebugApprovalCommitmentTest`: both roles derive a matching value from identical fixed-width evidence; changing either fingerprint, QR nonce, proof challenge, exporter or role order changes the commitment; malformed/zero/erased input fails.
- `TestPacketCodecTest`: exact 35-byte v1 control fixtures, strict header/body validation, the old three-byte approval/decline records rejected, malformed lengths, zero commitment, invalid control contents and no free text.
- `DebugApprovalControlWireTest`: two simulated endpoints pass real encoded control frames and check binding prior to admitting local/remote approval, while wrong-session/stale controls never unlock the gate. This is JVM simulation, NOT radio/TLS testing.
- The pre-existing `DebugApprovalGateTest`, Android/JVM tests and strict security boundary remain part of CI. The source guard requires use of the matched exporter, session-bound consent code and no references from production messaging or chat.
- No new SDK/permission, background service, persistence, protocol fallback, signing key or release capability flag. The unrelated M2u typed text serializer still cannot be invoked by the live LAN test.

## Independent review and physical acceptance STILL OPEN

These new records improve the **experimental per-session** approval semantics but must not be called independently secure. A qualified reviewer still needs to assess the entire transcript definition, threat model, human identity selection, all message/control ordering, consent semantics, replay/downgrade resistance, sender authenticity, terminal cleanup and app-level acknowledgments. The two sides do **not** exchange a final explicit application agreement ACK, and this is NOT an approved production messaging protocol.

Add physical tests to [M2k worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md):

- T27 — two supported Android phones complete QR/TLS/exporter/READY, approve separately, and exchange four presets only afterward;
- T28 — old M2v/M2w generic three-byte APPROVE/DECLINE clients are rejected on new M2x clients, no silent backward compatibility;
- T29 — test harness introduces a different session's commitment, altered field or corruption: no approval granted and no phrase accepted;
- T30 — decline before/after first approval, Wi-Fi change, background and restart: no stale action gains authorization;
- T31 — supported no-WAN two-device link, user-readable approval screen, counters, disconnection and session clearing.

**All handset tests remain NOT TESTED.** The [core secure messaging goal #42](https://github.com/PlayerEden/Eden-Comms/issues/42), [two-phone QA #26](https://github.com/PlayerEden/Eden-Comms/issues/26) and [durable signer #27](https://github.com/PlayerEden/Eden-Comms/issues/27) must remain open until independently evidenced.

References: [RFC 9266](https://www.rfc-editor.org/rfc/rfc9266.html), [M2v baseline](M2V_DEBUG_BILATERAL_APPROVAL_TEST.md), [M2w exporter equality](M2W_DEBUG_TLS_CHANNEL_BINDING_MATCH.md).
