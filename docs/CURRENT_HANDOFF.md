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

## Completed code checkpoint — F5-C verified nearby privacy

[PR #150](https://github.com/PlayerEden/Eden-Comms/pull/150) merged into
`main` at `9f69de394069fa8ac1c17d5ba459bccefb808e01`.
[Post-merge CI passed 19/19 checks](https://github.com/PlayerEden/Eden-Comms/actions/runs/42648245844).
One experimental, **debug-only** opt-in history/remember-peer UI now runs
after real approved Bluetooth Classic and Wi-Fi Direct host/join sessions.
Saved peer public fingerprints are not TLS pin authorization, and both
choices reset appropriately. No physical-device/security acceptance.

## Active source stage — F4-U: actual Bluetooth/P2P short voice bursts

[PR #151](https://github.com/PlayerEden/Eden-Comms/pull/151)
`feature/f4-u-nearby-voice-burst-2026-11-09`:

- Extends the existing post-confirmation **same SSLEngine** application
  writer/reader (also used by P2P), not QR, radio discovery or TCP reachability.
  Adds strictly parsed ECVP START/DATA/END 640-byte/20ms PCM frames alongside
  ECBT TEXT/ACK, using the already-tested LAN audio wire codec.
- The foreground mic captures **one up-to-3-second burst**, with explicit
  Android microphone permission. Only on release does the current approved
  authenticated channel copy, transmit, and sequence the bounded PCM; the
  receiver reconstructs a complete valid turn and tries local speaker
  playback. This is NOT real-time streaming or coordinated simultaneous
  voice floor. No voice saving/cloud, and no alternate raw socket.
- Shared DEBUG audio owner/UI used by Bluetooth and Wi-Fi Direct host and
  join. Permission/background/STOP tears down capture, playback and queued
  unsent PCM. Incoming audio is wiped after synchronous playback handoff.
- JVM QR/exporter/dual-approval paired TLS engine test now verifies audio in
  both directions on original engine. Negative frame/mixed transport and
  security Python/shell boundary checks. Final-head CI and post-merge
  acceptance must be checked before declaring the milestone merged.

## Next substantive steps

1. Fix any F4-U compile/CI regressions; merge only if the precise final
   branch SHA passes Android + source/security checks, then verify main CI.
2. Add a real collision/half-duplex voice-floor policy and, if viable,
   lower-latency live audio over both nearby transports. Never promise
   radio latency before actual two-phone QA.
3. Simplify Connect → Text/Talk → Disconnect using a shared user-level
   navigation flow instead of several dev-focused diagnostics.
4. Consolidate actual hardware/voice/privacy/backup/accessibility QA and
   independent cryptographic review. Production CAN_* remain OFF.

**Rollback:** revert only the relevant merged PR; do not reset package ID,
signing keys, stored app identity or unrelated features. For F5-A, provide
a user-visible Delete saved history before any schema change; do not claim
that a source rollback erased data already on a handset.
