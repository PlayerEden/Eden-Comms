# F4-S — non-blocking live microphone read and stalled-driver cutoff

**2026-11-08 · debug-only voice reliability · source/JVM CI · physical QA pending**

## Actual issue

The approved LAN walkie-talkie was bounded to three seconds and F4-O
blocked newly queued PCM after the user released Talk. However, the
**actual Android live AudioRecord worker** still used
`AudioRecord.READ_BLOCKING`. On a stalled or buggy microphone driver, that
call could block after the user's release and delay the worker's
`AudioRecord.release()`, even though outgoing queue admission was closed.

A delayed release is especially undesirable when users Hide/End the
conversation, go Home, drop Wi-Fi or start a new test on the same phone.

## Narrow F4-S repair

- The real live 20 ms capture loop now uses
  `AudioRecord.READ_NON_BLOCKING` and short **5 ms** retry sleeps when
  the driver has produced no bytes. No busy polling or alternate
  transport is introduced.
- One volatile per-turn `DebugLiveMicReadBudget` fails closed after
  **450 ms with no positive bytes**, **three seconds total**, an
  elapsed-clock rollback, or an explicit stop. Partial 20 ms frames
  are still completed before being offered; incomplete PCM is wiped.
  Positive partial reads refresh only the stall deadline, **not**
  the original three-second maximum.
- Existing `DebugLiveMicFrameGate` still prevents any frame from
  entering the encrypted writer after Release/Hide wins. F4-J session
  close still directly asks Android to cancel local mic/speaker.
- The separate accessible record-then-send tap fallback stays as-is.
  No new wire record, socket, approval, credential, permission,
  background service, storage, audio format or production capability.

## Source and regression evidence

Eight pure Kotlin/JVM tests cover zero-byte reads, 450 ms dead
driver, partial progress, original three-second cap, elapsed-clock
rollback, negative reads, explicit stop and bounded retry interval.
A source guard checks the **actual Android call site** and privacy,
consent, TLS and release boundaries. CI builds but does not simulate
real OEM microphone hardware.

## Physical testing on a *future* signed Preview

1. With fresh same-Wi-Fi QR and both approvals on two phones, use
   ordinary live Hold to Talk in both directions. Confirm frames are
   audible during the hold, not all delayed to the end. Check it still
   stops at three seconds, and short pauses/speech do not
   unexpectedly lose audio.
2. Release mid-syllable. Confirm Android's microphone indicator stops
   promptly. Test immediate reacquisition after the previous
   bounded worker exits. F4-O means already transmitted bytes cannot
   be retracted.
3. Repeat while hiding the local conversation, pressing End, going
   Home, locking the screen, or disabling Wi-Fi. Any old session must
   stop and require new QR and two approvals; no auto-reconnect.
4. Check an OEM that reports intermittent zero-length AudioRecord
   reads. Record only sanitized pass/fail, version, Android API,
   audible/not-audible and approximate cutoff; avoid audio recordings
   and any private identity, QRs or Wi-Fi addresses.
5. Test accessible record-then-send mode separately, because F4-S
   intentionally does not change that older path.

**Known limits:** Android `startRecording()`, native `stop()` and
`release()` may themselves misbehave; a source-level non-blocking
read cannot guarantee hardware latency. A 450 ms gap in raw PCM
production terminates this short test turn rather than continuing
indefinitely; this should be validated on actual devices and may
need a revised bound if common OEMs underrun. No independent security
review or real two-phone voice acceptance is claimed.

**Install status:** Preview 200008 predates F4-S. This is a source-level
PR only; do not claim it is already installed. Rollback: revert F4-S;
no persistent-state or APK-signing migration.
