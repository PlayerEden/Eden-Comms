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

## Completed code checkpoint — F4-U Bluetooth/P2P voice bursts

[PR #151](https://github.com/PlayerEden/Eden-Comms/pull/151) merged to
`main` as `48c94f0c9a218363a5cd95d326517332ad41735e`.
[Independent post-merge CI passed 19/19](https://github.com/PlayerEden/Eden-Comms/actions/runs/42653580697).
The previous DEBUG Bluetooth Classic and Wi-Fi Direct host/join voice option
was a three-second **record-then-send** encrypted voice burst (not real-time).
No physical radio/voice acceptance or cryptographic review.

## Completed code checkpoint — F4-V authenticated live nearby voice

[PR #152](https://github.com/PlayerEden/Eden-Comms/pull/152)
merged as `ad821579dd5f03d770f9df96e9dac1683bf8ab79`.
[Post-merge CI SUCCESS 19/19](https://github.com/PlayerEden/Eden-Comms/actions/runs/42658640505).
Bluetooth Classic and Wi-Fi Direct host/join reuse LAN's actual
host-arbitrated encrypted REQUEST/GRANT/DENY/CANCEL/RELEASE floor;
Android foreground mic sends 16kHz mono PCM 20ms frames WHILE held
(max 3s) over the same QR/pinned TLS/dual-approved channel, streaming
to remote AudioTrack. No verified physical radio/OEM behavior or
independent security review; debug only. Previous F4-U whole-clip path
is a regression-only fallback and also requires floor.

## Completed checkpoint — M6-A unified Connect → Text/Talk → Disconnect

[PR #153](https://github.com/PlayerEden/Eden-Comms/pull/153)
merged into `main` as `b9166cdc4f2809146c5aeeb8eac4f34397a860a7`.
[Independent main CI SUCCESS](https://github.com/PlayerEden/Eden-Comms/actions/runs/42663929342).
The Home Connect/Text/Talk choices now lead to the same method chooser:
LAN, Bluetooth Classic and Wi-Fi Direct use their actual debug entrypoints,
and verified Bluetooth/P2P chats share Text / Talk / Disconnect presentation.
Existing QR, bilateral approval, session owner and teardown remain authoritative.
No release capability enabled; no phone acceptance.

## Active milestone — M6-B consolidated physical acceptance preparation

[One two-phone acceptance matrix](M6_B_TWO_PHONE_ACCEPTANCE_MATRIX.md)
supersedes repeatedly asking for isolated APK tests. Prepared as a
**NOT TESTED** plan; it does not assert any actual phone PASS. Cross-transport
C01–C13, LAN/Bluetooth/Wi-Fi Direct method-specific checks, privacy/retention,
permission, accessibility, OEM TLS/Keystore, failure and reconnect cases all
have explicit expected outcomes, stop conditions and sanitized evidence sheet.

**Next:** Run one deliberately scheduled paired-device session when the owner
can access both handsets. Record only real PASS/FAIL/BLOCKED evidence; open
focused bugfix PRs for failures, retest, then seek independent security review
before any production capability is enabled. Avoid extra preview APKs or
claiming JVM TLS proves radio/audio acceptance.

**Rollback:** revert only the relevant merged PR; do not reset package ID,
signing keys, stored app identity or unrelated features. For F5-A, provide
a user-visible Delete saved history before any schema change; do not claim
that a source rollback erased data already on a handset.
