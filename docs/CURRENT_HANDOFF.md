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

## Completed code checkpoint — M6-A unified Connect → Text/Talk

[PR #153](https://github.com/PlayerEden/Eden-Comms/pull/153)
merged to `main` at `b9166cdc4f2809146c5aeeb8eac4f34397a860a7`.
[Independent postmerge CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42663929342).
One debug Connect chooser routes to already authenticated Same Wi-Fi LAN,
Bluetooth Classic and Wi-Fi Direct entrypoints. Existing nearby verified
sessions show the shared **Text/Talk/Disconnect** shell and optional
privacy under a disclosure; low-level diagnostics moved to Advanced.
Choosing a radio never authorizes or verifies it, and production switches
are still FALSE. Physical two-phone UX and real OEM/security acceptance OPEN.

## Completed code checkpoint — M6-B tab safety and QA evidence

[PR #154](https://github.com/PlayerEden/Eden-Comms/pull/154) merged to
`main` as `3d0e2d6e050d7617079fd1929c85da227e6dea9c`.
[Independent main CI passed 19/19](https://github.com/PlayerEden/Eden-Comms/actions/runs/42669197572).
Nearby Bluetooth and Wi-Fi Direct verified sessions now cancel pending mic floor
or release active AudioRecord when switching Talk → Text or unmounting Talk.
Optional Advanced RAM-only local counters for text, ACK, floor and voice are
copyable with an explicit user tap but retain NO text/audio/peer data.
[18-case x 3-method phone test matrix](TWO_PHONE_ACCEPTANCE_MATRIX.md) is
ready but **NOT RUN** on physical devices.

## Completed source checkpoint — M6-C accessible verified conversation

[PR #155](https://github.com/PlayerEden/Eden-Comms/pull/155) merged to
`main` at `9c158b1e9dfea6b82350069cb7cd842956cebd9c`.
[Final PR CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42691177877);
[independent main CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42691600680).
Verified Bluetooth/P2P Text buttons now consistently stop pending/active
voice before switching from Talk; angular chat cards, screen-reader semantics
and live mic permission refresh are present. Home, Help, Settings and
Privacy & data now truthfully describe three experimental transports, opt-in
local text history and separate peer memory. No emulator/TalkBack/device
acceptance or independent security audit. Production CAN_* remain FALSE.
[Detailed audit](M6_C_USABILITY_ACCESSIBILITY_AUDIT.md).

## Completed source checkpoint — M6-D guided nearby QR setup

[PR #156](https://github.com/PlayerEden/Eden-Comms/pull/156)
merged to main as `475756467698407848429def12c0ab6fd04145d3`.
[Final PR Android CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42692297197)
and [independent main CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42692721109).
Bluetooth's two QR stages and Wi-Fi Direct's three QR stages now
show accurate host/join next actions, accessible status/heading semantics
and a clear NOT YET VERIFIED boundary. No actual phone visual/TalkBack
or two-device acceptance took place. See
[M6-D audit](M6_D_QR_PAIRING_GUIDANCE_AUDIT.md).

## Completed source checkpoint — M6-E text and peer-app ACK clarity

[PR #157](https://github.com/PlayerEden/Eden-Comms/pull/157) merged
as `6dc2070692a7143c3e76e35599a16ec44a01580d`.
[Exact-final-head PR Android/JVM/Python/security CI SUCCESS](https://github.com/PlayerEden/Eden-Comms/actions/runs/42696448115);
[independent `main` CI SUCCESS](https://github.com/PlayerEden/Eden-Comms/actions/runs/42696732161).
Both Bluetooth and Wi-Fi Direct verified debug text use the same
280-Unicode-codepoint draft gate (no UTF-16 truncation), bounded
scrolling and Jump to latest. Queue acceptance, local TLS write,
peer-app ACK and local receive are displayed separately. Failed local
queue attempts preserve the draft and report failure. No emulator,
TalkBack, actual two-phone or independent crypto acceptance.
[M6-E source audit](M6_E_TEXT_COMPOSER_ACK_RECOVERY_AUDIT.md).

## Active source milestone — M6-F keyboard/capture/retry-state polish

Branch `feature/m6-f-conversation-ime-privacy-recovery-2026-11-09`.
[Source audit](M6_F_NEARBY_IME_CAPTURE_RECOVERY_AUDIT.md).

- All three real Bluetooth/P2P debug connection screens now give
  keyboard/system-bar inset space **before** the outer vertical scroll.
  Debug-only manifest requests `adjustResize` for these screens.
- Verified Text → Talk and Disconnect hide the text keyboard and clear
  focus, while existing Talk → Text floor cancellation remains intact.
- Bluetooth's QR/chat debug Activity requests `FLAG_SECURE` before
  displaying pairing material, matching Wi-Fi Direct. This is best
  effort OS screenshot/Recents protection, not full privacy assurance.
- Bluetooth's old queued/rejected label clears on STOPPED and fresh
  verified Ready; reconnect still needs new QR/TLS/dual human consent.
- Source regression checks added for insets, keyboard focus, manifest,
  screenshot flag, reset and release capabilities. Source changes do
  not claim any emulator/manual visual or actual two-device QA.

## Next priorities

1. Obtain final-head M6-F Android/JVM/Python/security CI green, merge PR,
   then independently verify the merged `main` commit.
2. Run real emulator UI/IME/large-font/landscape checks when accessible,
   noting the existing manual emulator workflow only covers disconnected
   scenes/local sandbox; don't fake a connected-session screenshot.
3. Do **one consolidated two-phone** run with the
   [M6-F C07/C11/C12/C16 addendum](TWO_PHONE_ACCEPTANCE_MATRIX.md)
   when devices are available. Obtain independent crypto/backup/
   privacy/accessibility review before enabling release `CAN_*` flags.

**Rollback:** revert only the relevant merged PR; do not reset package ID,
signing keys, stored app identity or unrelated features. For F5-A, provide
a user-visible Delete saved history before any schema change; do not claim
that a source rollback erased data already on a handset.
