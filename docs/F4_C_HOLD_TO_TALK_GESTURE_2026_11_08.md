# F4-C — hold-to-talk / release-to-send gesture on the real LAN debug chat

**November 8, 2026. Development-only. Physical phone QA remains open.**

The F4/F4-B Android app already captures and transfers up to three seconds of
16-kHz PCM from one phone to the other on the authenticated, both-approved
same-Wi-Fi TLS test session. F4-C replaces the default two-tap interaction
with a much more direct, familiar **touch and hold** control.

## Actual behavior

1. On the already verified nearby test conversation, touch and **HOLD TO
   TALK**. It starts the same foreground microphone recording, capped at 3
   seconds. The button changes to **RELEASE TO SEND**.
2. Lift your finger to finish capture and queue the **same existing bounded
   voice burst** through the approved TLS session. No new transport, pairing,
   server, account or background microphone service exists.
3. If a gesture is cancelled (drag-interrupted, route removed or Android
   cancels the pointer), the app cancels the microphone and **does not send**
   an unintended partial recording.
4. The first press may request Android's microphone permission. After grant,
   press again. If it is denied, regular typed test chat still works.
5. Use **USE TAP CONTROLS INSTEAD** to show the older START TALKING / SEND
   VOICE BURST option. A screen reader / keyboard's accessibility action on
   the big surface invokes the same two-tap fallback, rather than requiring
   an inaccessible long-press gesture.
6. The existing F4-B short/failed recording recovery, local playback status,
   conversation hide/mute behavior, old-attempt rejection and transient
   buffer cleanup remain.

**Important:** This is now physically *hold-to-record*, **not yet low-latency
audio streaming while held**. Audio is sent only after release or when the
maximum three-second recording ends. The other phone attempts playback
after receiving the complete short burst. No radio "floor" ownership/
collision arbitration yet. Avoid private or emergency conversations in this
unreviewed Preview.

## Pre-physical verification

- Gradle Android debug compilation and full JVM unit tests
- Python guards for actual Compose pointer press/release/cancel, real app
  callbacks, approved LAN TLS session, microphone prompt and accessible
  alternative
- Release voice and all connection capability flags remain **false**;
  RECORD_AUDIO is still debug-only in the debug manifest
- No changes to on-the-wire voice codec, TLS, certificates/keys, QR or signer

## Two-phone QA at next test checkpoint

Install the **same Preview APK built from a revision including F4-C** on
both phones. Preview 200003 predates this UI change. Connect via reciprocal
QR, pinned TLS and both on-screen approvals.

Try A holds for about one second and releases. Check B actually hears a
short voice phrase. Test B to A. Try 3-second automatic finish, a
millisecond tap, a pointer cancel/drag, microphone permission denied,
Hide conversation while holding, End chat while holding, Wi-Fi loss,
screen lock/background, and the optional two-tap accessibility controls.
After each interrupted case, verify there is no unexpected voice and a
new session still requires fresh QR codes and both approvals.

The next **functional** milestone remains sending audio *during* the press
and playing it in bounded, low-latency chunks while preserving mutual TLS
and explicit floor ownership. That work must not be described as complete
until both Android phones demonstrate it.

**Rollback:** revert F4-C; no stored audio, new production permission,
wire-format change or signing migration.
