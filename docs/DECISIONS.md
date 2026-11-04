# Eden Comms — Decisions Log
Keep choices explicit; proposed items are not locked.

| ID | Date | Decision | Status |
|---|---|---|---|
| D-001 | 2026-11-03 | Working name Eden Comms (EC), under Eden Practical; repository remains Eden-Comms | Working |
| D-002 | 2026-11-03 | Android first, one-to-one local peer messaging, no company server, no account | Direction accepted |
| D-003 | 2026-11-03 | Three supported connectivity modes planned: LAN, Bluetooth, Wi-Fi Direct | Direction accepted |
| D-004 | 2026-11-03 | Privacy-first with clear disclosures, accessible simple UI, restrained government-utility feel | Direction accepted |
| D-005 | 2026-11-03 | Session-only default; optional encrypted local history and disappearing mode | Accepted product direction |
| D-006 | 2026-11-03 | Kotlin + Jetpack Compose instead of HTML Canvas/Pixi for native permissions, connectivity and accessibility | Proposed technical direction |
| D-007 | 2026-11-03 | Implement secure LAN text first, then Bluetooth and Wi-Fi Direct, then voice | Proposed sequencing |
| D-008 | 2026-11-03 | Security review and crypto-library selection required before sending real encrypted messages | Required gate |
| D-009 | 2026-11-03 | Simple home screen and QR/short-code peer verification; no need to understand IP addresses | Proposed UI |
| D-010 | 2026-11-03 | Group messaging, file transfer and mesh networking deferred | Proposed scope |
| D-011 | 2026-11-03 | Security and privacy outrank feature speed/convenience; no-ship on unsafe transports, unverified peers, or cloud leakage | Binding project principle |
| D-012 | 2026-11-03 | No silent downgrade to plaintext; all communication transports share a reviewed E2EE/authentication layer | Binding project principle |
| D-013 | 2026-11-03 | Distinguish verified identity, encrypted-but-unverified, and disconnected; clear user-controlled pairing | Binding project principle |
| D-014 | 2026-11-03 | No external messaging endpoint/analytics/ads, no app-managed backup; protect sensitive chat content from logs, keyboard learning and notifications by design | Binding project principle |
| D-015 | 2026-11-03 | Default ephemeral session-only content; retention and trusted-peer storage only after explicit consent; disclaim limits of deletion | Binding project principle |
| D-016 | 2026-11-03 | Candidate Noise XX reviewed implementation for live nearby peer handshake; exact protocol and library not chosen yet | Research candidate |
| D-017 | 2026-11-03 | Persistent message history must be opt-in, on-device encrypted, configured separately for each conversation, with an absolute maximum lifetime of 30 days | Accepted product direction |
| D-018 | 2026-11-03 | Default session-only remains unchanged; no indefinite, cloud-synced or automatically backed-up chat content; no guarantees of remote deletion or provable flash erasure | Accepted security principle |
| D-019 | 2026-11-03 | Suggested per-chat retention presets: 1 hour, 24 hours, 7 days, 14 days, 30 days; expiry measured from local first receipt/sending and never extended by rereading | Proposed UX/implementation |
| D-020 | 2026-11-03 | Changing to a shorter TTL deletes aged local messages immediately; longer TTL never revives/extends old messages; app filters expired content before display | Proposed implementation guardrail |

The [Privacy and Security Charter](PRIVACY_SECURITY_CHARTER.md) sets mandatory security/no-ship gates for all milestones. The [Data Retention and Expiry design](DATA_RETENTION_AND_EXPIRY.md) specifies the 30-day ceiling and deletion limitations.

## Decisions needed before M1
- Lowest Android OS version and which devices can be manually tested.
- Exact cryptographic protocol/library, key storage and identity verification.
- Are trusted peers saved only when opted in?
- Are session notifications shown as contentless by default?
- Package ID and signing/release identity (never reuse game package IDs).

## Change process
Record new decisions here with trade-offs and why. Do not silently turn a proposed detail into an implemented promise. Every major milestone gets targeted QA and a rollback point.
