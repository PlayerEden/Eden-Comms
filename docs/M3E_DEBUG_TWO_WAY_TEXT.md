# Eden Comms M3e — functional, ephemeral two-way text (debug only)

**Date:** 2026-11-06  
**Status:** Source and Android/JVM verification pending. **Actual two-phone behavior NOT TESTED.**
**App direction:** Core application features during GitHub development; physical device testing later at owner's request. No Google Play release or production credential setup.

## What now actually works in code

Home → **CONNECT NEARBY** opens the existing QR-first LAN development test. After both phones complete reciprocal QR pin verification, TLS 1.3 authentication, session challenge proof and matching debug TLS channel binding, **each person must approve**. Each phone must then both write its own session-bound final confirmation and observe the other phone's matching confirmation.

Only **after** the existing debug gate reaches CONFIRMED, the test screen exposes:

- **Type a short TEST message** — 280-character UI cap, strict Unicode UTF-8 parser with at most 280 code points and 1,120 encoded bytes. Blank text, invalid surrogates, control characters, malformed lengths/IDs and arbitrary ACK packets are rejected.
- **SEND TEST TEXT** — queues bounded text for a single writer thread; it is written via the **same pinned TLS 1.3 test socket** used for approval and four fixed preset phrases. The UI never shows a message as written until the writer confirms local TLS write.
- **Conversation bubbles** — a real peer's parsed test text can be shown as **Other phone • Received here**; local text shows **You • Written to TLS (not delivered)**. No fabricated peer message or read receipt is injected into this test transcript.
- **In-memory conversation** — no database, saved state, notification, message export, peer profile storage or chat backup. Only the last 12 typed text events remain visible; disconnect, backgrounding, reset or navigation disposes the content. The Android keyboard and screenshots may still retain content, so **do not use sensitive messages**.
- **Local QA report** includes only two typed-message counters (written to TLS, received locally), never the text or remote identity. Existing four preset counters remain separate.

This is a development *prototype* you can try later, not a secure communication service ready for private use.

## Safe protocol/feature boundary

The existing [ProposedChatWireCodec](../app/src/main/java/com/edenpractical/comms/protocol/ProposedChatWireCodec.kt) remains **unreviewed**. Its strict versioned TEXT frame shape is now consumed **only** inside the debug `TestPacketCodec` envelope and only on the pre-existing debuggable `LanDemoSession`. Debug consent, TLS session binding, identity challenges, approval and confirmations are unchanged. The test-only text parser cannot replace human approval, create a peer acknowledgment or enable release capability flags.

The parser supports an ACK proposal but the **runtime test explicitly rejects every ACK**. A successful write to local TLS does not prove the other phone received anything; it means only the local socket write returned successfully. A received bubble means this app parsed a frame on its current encrypted test socket, not that a person read it.

An additional strictly increasing, **per-connection incoming message ID** check rejects duplicates/old IDs and premature text. This is a bounded test-session constraint, not long-term cryptographic replay protection.

The CI `scripts/check_security_review_boundary.sh` still requires:
- `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` = `false`;
- the debug-only session to enforce `FLAG_DEBUGGABLE` before creating a socket;
- typed text to be sent **only** by the existing debug session and Compose pairing-test screen;
- text dispatch **only** after `DebugApprovalGate.maySendPreset()`, which demands both approvals and both final confirmations;
- no production chat UI networking, text/message history, voice or plaintext fallback.

## Automated tests and what remains unknown

Added pure-JVM tests for ECTX TEXT framed encoding, invalid UTF-8/controls/IDs, zero ACK acceptance, legacy control isolation, premature/non-monotonic incoming text, bounded activity and irreversible text clearing. Existing mTLS/QR/approval state-space tests still run in Actions; they do not replace real handset tests.

**Not yet physically verified:** two devices establishing the session simultaneously, typing actual text and observing it on the peer, delivery order/latency, Android IME behavior, Wi-Fi drop mid-message, screen rotation, lifecycle cleanup, mixed Android versions, or no-internet test. The [M2k worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md) should be expanded during owner hands-on QA. Physical tests remain **owner-led when ready**.

**Not yet implemented:** reviewed real secure production messages, durable conversations, encrypted on-device history (opt-in), trusted peer list, Bluetooth, Wi-Fi Direct, push-to-talk audio, independent protocol security assessment, Google Play signing. Do not claim this build independently audited or recommend it for emergency/private messaging.

**Product intent:** Continue developing actual features and testing their source behavior through CI without holding all UI/core improvements hostage to hardware availability. Release and independent assessment are separate owner-gated milestones. Preserve existing debug code and test artifacts to make later physical QA straightforward.

References: [Product vision](PRODUCT_VISION.md) · [M2y debug session approvals](M2Y_DEBUG_MUTUAL_FINAL_CONFIRMATION.md) · [M2u text parser](M2U_BOUNDED_TEXT_WIRE_AND_SECURITY_GATE.md) · [Hardware QA #26](https://github.com/PlayerEden/Eden-Comms/issues/26) · [Core secure release messaging #42](https://github.com/PlayerEden/Eden-Comms/issues/42).
