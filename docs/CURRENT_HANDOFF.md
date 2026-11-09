# Eden Comms — Current development handoff

Updated: **November 9, 2026**. This is a concise launch point, not a
substitute for GitHub's live status. On resuming, check **main SHA, open PRs
and latest green Android CI** before modifying any files.

## Product direction

Simple local-first one-to-one Android messaging and push-to-talk using
same-network Wi-Fi, Bluetooth Classic or router-free Wi-Fi Direct. No
mandatory account, backend, always-on discovery or cloud. Session-only is
the default. For any future persistent chat or peer record, obtain separate
explicit consent with **30 days maximum** and a simple local erase control.
All transport/crypto code remains unreviewed development software.

## Current sources of truth

- [Delivery roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md)
- [Latest decisions](DECISIONS.md), [Data retention](DATA_RETENTION_AND_EXPIRY.md)
  and [Peer memory](TRUST_RETENTION_AND_HELP_PLAN.md)
- [Recent QA and decisions](WORK_LOG.md)
- [CI runs](https://github.com/PlayerEden/Eden-Comms/actions)
- [GitHub PRs](https://github.com/PlayerEden/Eden-Comms/pulls)

## Implemented in source and automated regression (not phone-accepted)

- Android/Compose app and in-person QR pairing.
- LAN: mutual pinned TLS and QR challenge / exporter proof, independent
  approvals, short two-way typed text and peer-app ACK; short debug live PTT
  uses the same approved channel.
- Bluetooth Classic: in-person QR/pinned TLS, bilateral consent and
  real debug TEXT/ACK on one retained RFCOMM socket (F2-Q, PR #146).
- Wi-Fi Direct: temporary Android group, bound TCP interface, checked
  three-QR exchange, same authenticated TLS/consent, debug TEXT/ACK and
  cancellation (F3-C, PR #147). The zero-data reachability probe remains
  distinct from authorization.
- Ephemeral local disappearing timer (1/5/15-minute screen display only).

**Physical device acceptance:** LAN full matrix, live Bluetooth and Wi-Fi
Direct interoperability, Android TLS providers/OEM behavior, two-way audible
PTT and accessibility tests still need the planned consolidated on-device
pass. **Independent security audit** is a separate requirement. Do not
interpret a green JVM/CI run as passing either.

## Completed source checkpoint — F5-A

[PR #148](https://github.com/PlayerEden/Eden-Comms/pull/148) merged at
`53e581aad2c0cc30426b9674ed603d910d5c4d70`. [Post-merge Android
CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42582592960).
Opt-in Keystore AES-GCM history of *actual approved LAN text events*,
stored in app `noBackupFilesDir`. Every new session defaults to RAM-only;
1h, 24h, 7d, 14d or 30d are explicit choices. Expiry filtering and
per-peer delete are separate from peer memory. Independent Android storage/
backup/security acceptance remains open; release variant is no-op.

## Active source stage — F5-B (PR #149)

[PR #149](https://github.com/PlayerEden/Eden-Comms/pull/149) implements a
**separately consented** local public peer certificate fingerprint record:
1–30 days maximum, default to no saved peer, no auto-extension on repeated
read/reconnect, explicit Forget and Forget all, encrypted with a *different*
Android Keystore AES-GCM key/file from F5-A. A record is associated with this
phone's own public identity; changing or deleting the self identity invalidates
old records. A saved public pin is read/displayed ONLY after a **fresh actual
LAN QR/TLS + bilateral confirmed** session. The transport and approval gate
never read peer memory, so there is **no auto-pairing, auto-trust, or shortcut
past the next human verification**. Build variant is DEBUG-only; release is no-op.

**PR acceptance:** final-head Android compile, JVM + Python policy/security
guards, independent manual review of storage consent and identity reset.
Real two-phone QA, Keystore/OEM backup/clock/restore negative tests, and
independent crypto review remain open; do not call this trusted contacts or
a production secure communication feature.

## Prioritized next source work

1. Finish F5-B PR #149 checks; merge only once exact-head Android CI
   passes, then confirm the independent main-branch CI run.
2. Port **opt-in, authenticated-session-only history/peer-memory controls**
   to Bluetooth and Wi-Fi Direct once each method's live callback and
   identity proof semantics are verified; avoid UI duplication.
3. Connect voice where appropriate, unify Connect → Text/Talk → Disconnect
   into simple nontechnical navigation, then consolidate a single hardware
   acceptance matrix. Independent security review remains a release gate.

**Rollback:** revert only the relevant merged PR; do not reset package ID,
signing keys, stored app identity or unrelated features. For F5-A, provide
a user-visible Delete saved history before any schema change; do not claim
that a source rollback erased data already on a handset.
