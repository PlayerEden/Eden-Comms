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

## Active work at this handoff — F5-A

Opt-in Keystore AES-GCM history of *actual confirmed LAN chat events*,
one peer at a time, stored only in app `noBackupFilesDir`. Session-only
remains the default; 1h, 24h, 7d, 14d or 30d are explicit choices. Expiry
is absolute and checked on read; shortening never extends old records;
corruption/clock rollback fails closed. View/read requires a new actually
approved peer; no saved pin grants reconnect. Debug and release build
variants have real/no-op store implementations, respectively. A standalone
work log records the exact PR and CI verification status. This is still
experimental, unaudited storage and needs Android Keystore/backup/device QA.

## Prioritized next source work

1. Complete F5-A Android CI/security review; fix blockers; merge only green
   exact source SHA, then check main CI.
2. Add separately user-approved **remember peer** public-pin cache, 1–30
   days, no silent extension, never a trust/authentication bypass.
3. Connect the optional history/peer choices consistently to Bluetooth and
   Wi-Fi Direct **after** accurate authenticated-session event wiring.
4. Complete additional voice integrations and simplify Connect → Talk/Text →
   Disconnect. Then one consolidated phone acceptance matrix and external
   crypto audit; no production release before those pass.

**Rollback:** revert only the relevant merged PR; do not reset package ID,
signing keys, stored app identity or unrelated features. For F5-A, provide
a user-visible Delete saved history before any schema change; do not claim
that a source rollback erased data already on a handset.
