# M6-G — Nearby failure/recovery instructions and fresh-state reset

**Date:** November 9, 2026. **Branch:**
`feature/m6-g-failure-recovery-guidance-2026-11-09`.
**Scope:** real DEBUG Bluetooth Classic and Wi-Fi Direct host/join
disconnected/failing state presentation; ephemeral UI reset on a new
user-initiated attempt. Actual device acceptance has **NOT RUN**.

## Motivation and source observations

- Bluetooth has genuine QR/TLS/dual-confirmed text and talk, but after
  a stopped link the verbose technical status and newly reset QR setup
  offered little visible explanation of how **both** phones should retry.
- Wi-Fi Direct has several distinct Android radio stages. A host with
  pending group cleanup must not be told a new link is already safe;
  an unconfirmed cleanup cannot be described as removed. An expired
  join discovery and a stopped verified channel need different help.
- An old STOPPED `DebugWifiDirectChatUiState` could persist visually
  during a new user-initiated host/search attempt and look like the
  *new* attempt had already failed.

## Implemented

1. New `DebugNearbyRecoveryGuide` and `DebugNearbyRecoveryPanel`
   under `src/debug` only. Fixed, bounded, non-secret copy; semantic
   heading and polite status. There are no clickable auto-retry buttons,
   radio APIs, permissions, storage, cryptography or approval code.
2. Bluetooth Activity shows instructions **only** in observed RFCOMM
   `STOPPED`. If current Bluetooth connect permission is absent, point
   to OS permission and a new QR exchange; otherwise explicitly say
   that both people must re-exchange codes, host/search/select the radio
   and separately approve again. Existing staged candidate handles
   and QR are still discarded by the original owner.
3. Wi-Fi Direct **host** distinguishes Android `CLEANING`,
   `STOPPED` with `CLEANUP_UNCONFIRMED`, other `STOPPED`, and
   a stopped verified TLS chat while the temporary group is still
   observed. Guidance says use the **existing STOP AND REMOVE TEST
   GROUP** button when needed. No claim that cleanup succeeded until
   the genuine gate has reported it.
4. Wi-Fi Direct **join** distinguishes stopped verified link (with no
   active discovery), stopped discovery, and stopped discovery with
   observed radio disabled. Guidance never treats finding a device as
   identifying/approving the other person.
5. A new **explicitly initiated** Wi-Fi Direct host/search attempt
   calls `verifiedChat.beginNewAttempt()` after the existing gate
   admits it. This clears stale RAM status, queue feedback, lines,
   draft and ACK bookkeeping to an unverified **IDLE** state.
   Only the real `DebugBluetoothApprovedTextChannel.Event.Ready`
   can mark `CHAT`; no restored trust or background reconnect.
6. Added JVM recovery copy + UI-state tests and
   `scripts/tests/test_m6_g_actual_failure_recovery.py` ensuring
   wiring only to observed state, unmodified release flags and no
   auto-retry authority.

## Claims and gates

- Exact PR-head Android debug compile, Kotlin/JVM, Python and security
  checks are required before merge, plus separate postmerge main CI.
- **NOT RUN:** real Bluetooth permission/timeout/decline/radio-loss
  UI, P2P cleanup confirmation on OEM handsets, real user reboot,
  two-phone reconnect, local history backup/deletion, TalkBack/IME,
  screenshot privacy and independent cryptographic assessment.
- No fake connected preview, emulator screenshot, or new APK promised.
  Underlying QR/TLS/exporter, bilateral human confirmations, socket
  owners, microphone floor arbitration, cleanup requests and data
  retention logic are unchanged. Production `CAN_CONNECT`,
  `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` remain FALSE.

## Acceptance added to the ONE consolidated device pass

During existing C02/C04/C05/C07/C12/C16/C17, let Bluetooth link time
out, decline consent, remove Nearby permission and turn off radio in
separate tests. Verify correct screen guidance without phantom ready
states, and new two-code proof/bilateral approval after every restart.
For Wi-Fi Direct, interrupt discovery, authentication and group cleanup
on both host/join; confirm the CLEANING state never claims cleanup
finished, unconfirmed state asks for Android settings, and new
host/search UI shows **not verified** with no stale draft/ACK.
Record sanitized PASS/FAIL/BLOCKED/NOT RUN only. External security
review remains necessary even after all device tests pass.

**Rollback:** revert only M6-G UI/presentation/new-attempt state
reset if actual devices expose misleading transitions. Do not
change package identity, stored keys, history or connectivity
authorization to work around a UI regression.
