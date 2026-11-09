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

## Completed code checkpoint — F5-B remembered public peer

[PR #149](https://github.com/PlayerEden/Eden-Comms/pull/149) was merged as
`5364eea23914d95481625bde30590c7c2b3f93fc`.
[Main-branch CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42585024948).
Separate DEBUG Keystore AES-GCM public fingerprint/expiry cache, explicit
1–30-day consent and per-peer/clear-all deletion. F5-A chat history is
separate. Records are viewed only after fresh verified LAN approval; they
never confer an automatic connection or trust. Production store remains no-op.

## Active source stage — F5-C Bluetooth/P2P optional privacy

[PR #150](https://github.com/PlayerEden/Eden-Comms/pull/150),
`feature/f5-c-authenticated-bluetooth-p2p-privacy-2026-11-09`:
- Bluetooth RFCOMM and Wi-Fi Direct host/join now emit public peer and local
  certificate pins to the **UI-only** shared privacy controller after actual
  mutual TLS, QR/exporter binding, both human approvals, both final encrypted
  confirmations and adoption of the original live session. Saved pins are
  NEVER read by discovery, TLS, trust gates, P2P routing or connection
  approval. Leaving/permission loss/STOP closes the privacy view.
- One DEBUG-only reusable `DebugVerifiedNearbyPrivacy` and Compose panel
  provide session-only-by-default opt-in history durations (1 hour to 30 days),
  read/delete local encrypted text, separately consented 1–30-day remembered
  PUBLIC fingerprint, Forget and Forget all. Uses the existing independent
  F5-A and F5-B ciphertext stores/keys. Only actual live encrypted
  text Written/Received callbacks may save; Ready/ACK/Failed do not.
- Disk ops are serialized so changing to a shorter duration or turning it
  OFF cannot be silently undone by a queued old-duration save. UI callbacks
  are fenced by session lifecycle revisions. Debug only, no Preview workflow.

**Acceptance pending:** final PR head Android compilation, JVM/Python/shell
regressions, then post-merge main CI if green. Android OEM/TLS exporter,
Bluetooth radio, P2P routing, local Keystore/backup/clock and independent
crypto QA remain explicitly untested, not production accepted.

## Next core work

1. Verify and safely merge F5-C only after green exact-head CI; confirm
   independent main-branch checks; update this handoff and work log.
2. Implement missing voice transport support, while avoiding false
   encryption/security claims and keeping connection lifecycle bounded.
3. Simplify Connect → Text/Talk → Disconnect across LAN, Bluetooth and
   Wi-Fi Direct; avoid multiple technical test panels in final usability.
4. Plan one consolidated two-phone hardware/regression/accessibility
   acceptance matrix and independent security review. Release CAN_* flags
   must remain false until they pass.

**Rollback:** revert only the relevant merged PR; do not reset package ID,
signing keys, stored app identity or unrelated features. For F5-A, provide
a user-visible Delete saved history before any schema change; do not claim
that a source rollback erased data already on a handset.
