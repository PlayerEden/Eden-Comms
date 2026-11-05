# Eden Comms — Decisions Log
Keep choices explicit; proposed items are not locked.

| ID | Date | Decision | Status |
|---|---|---|---|
| D-001 | 2026-11-03 | Working name Eden Comms (EC), under Eden Practical; repository remains Eden-Comms | Working |
| D-002 | 2026-11-03 | Android first, one-to-one local peer messaging, no company server, no account | Direction accepted |
| D-003 | 2026-11-03 | Three supported connectivity modes planned: LAN, Bluetooth, Wi-Fi Direct | Direction accepted |
| D-004 | 2026-11-03 | Privacy-first with clear disclosures, accessible simple UI, restrained government-utility feel | Direction accepted |
| D-005 | 2026-11-03 | Session-only default; optional encrypted local history and disappearing mode | Proposed detail |
| D-006 | 2026-11-03 | Kotlin + Jetpack Compose instead of HTML Canvas/Pixi for native permissions, connectivity and accessibility | Proposed technical direction |
| D-007 | 2026-11-03 | Implement secure LAN text first, then Bluetooth and Wi-Fi Direct, then voice | Proposed sequencing |
| D-008 | 2026-11-03 | Security review and crypto-library selection required before sending real encrypted messages | Required gate |
| D-009 | 2026-11-03 | Simple home screen and QR/short-code peer verification; no need to understand IP addresses | Proposed UI |
| D-010 | 2026-11-03 | Group messaging, file transfer and mesh networking deferred | Proposed scope |
| D-011 | 2026-11-04 | Do not persist verified peer identities by default. Optional **per-device** remember duration from 1 to 30 days, never longer. No silent extension; each phone chooses independently. | Direction accepted; storage not implemented |
| D-012 | 2026-11-04 | Store only minimal verified **public peer identity** and necessary expiry/integrity metadata if opted in. No peer private keys, messages, phone numbers or IPs; no cloud backup. Expiry and manual Forget enforce fail-closed behavior. | Design accepted; pending security review |
| D-013 | 2026-11-04 | Message retention is an independent **per-chat** option: session-only by default; later encrypted local history capped at 30 days, best-effort deletion. One user's timer cannot delete the other person's copy. | Direction accepted; not implemented |
| D-014 | 2026-11-04 | Settings remain simple; a contextual **?** provides a short, clear explanation for material privacy/security choices, with optional **Learn more** to an offline How it works section. | UX direction accepted; not implemented |
| D-015 | 2026-11-04 | Local discovery should be explicitly user-initiated and time-limited, with minimal advertised metadata; discovering a device **never** means trusting its identity. QR remains an option; remote short-code verification requires reviewed transcript-bound cryptography. | Design direction; not implemented |
| D-016 | 2026-11-04 | Failed two-phone QR/TLS test blocks messaging release. Show sanitized phase-specific failure codes, never raw exceptions/IPs/keys. Confirm successful real-phone handshake before advancing. | One-phone success screenshot received; paired QA incomplete |
| D-017 | 2026-11-04 | Before real text, run an explicitly test-only reciprocal QR session/challenge proof inside pinned TLS 1.3; no chat/voice or persisted trust, no feature flag changes. Require both physical-device outcomes and negative tests. | In development on M2c branch; security review pending |

## Decisions needed before M1
- Lowest Android OS version and which devices can be manually tested.
- Exact cryptographic protocol/library, key storage and identity verification.
- **Resolved direction:** trusted peers are stored only with explicit opt-in for 1–30 days. No implementation until bilateral verification is reliable.
- Are session notifications shown as contentless by default?
- Package ID and signing/release identity (never reuse game package IDs).

## Change process
Record new decisions here with trade-offs and why. Do not silently turn a proposed detail into an implemented promise. Every major milestone gets targeted QA and a rollback point.
