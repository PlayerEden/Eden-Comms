# F4 — first real two-phone LAN walkie-talkie voice burst (debug only)

**Development date:** November 8, 2026. **Not public, not reviewed for private or emergency communications.** Source/CI is not physical test evidence. This milestone is **short voice bursts**, not continuous live audio or a Bluetooth walkie-talkie.

## What the app actually does now

With **both Android phones already on the same Wi-Fi**, the existing two-QR mutual certificate pinning, TLS 1.3, RFC 9266 exporter binding, both explicit human approvals and both final confirmations produce the same active nearby test chat as before. **Only on that actual active test session**:

- The new Walkie-talkie section has **START TALKING**, followed by **SEND VOICE BURST**. Android asks for microphone permission only when the user chooses to record. A denied request leaves text chat working.
- The phone captures 16-kHz, 16-bit, mono signed PCM in precisely 640-byte / 20ms frames, with an automatic **3-second hard recording limit**. Releasing via Send also ends recording. Recording is on the foreground app only; a separate 3-second prerecorded clip is not saved.
- The session makes one bounded **ECVP START → ordered DATA frames → END** voice burst. Each actual packet rides the **same existing TLS writer**, only after its debug approval gate is CONFIRMED. No separate radio socket, plaintext fallback, IP input, independent voice login or account.
- On the other phone, an exact-size, sequence-checked, single-turn receiver reconstructs the short PCM burst in RAM and plays it through the local speaker. No raw PCM, recording, voice transcript or file is stored. Outbound and inbound temporary buffers are zeroed as best effort.
- The LAN session still closes on disconnect, leaving/backgrounding, broken Wi-Fi, heartbeat/silence timeout and the existing **30-minute limit**. Canceling also stops microphone capture and playback. One burst is queued at a time; the UI remains honest that **a write to TLS is not confirmation of what the other person heard**.

This design minimizes dependencies and CPU overhead but is **not streaming PTT**. You must finish the short burst before the other person can hear it. The next voice milestone will evolve this into true hold-to-talk / low-latency streaming with a listening indicator, explicit transmit floor control and better real-world audio behavior **after physical QA of this simpler path**.

## Source boundaries

- `app/src/main/.../protocol/DebugLanVoiceWireCodec.kt`: strict experimental versioned PCM envelope, 3 seconds maximum, monotonic turn numbers, exact ordering. Codec is not encryption.
- `TestPacketCodec.kt`: accepts only exact ECVP voice packets, in addition to existing debug text/ACK/consent.
- `LanDemoSession.kt`: the **only** permitted voice sender/receiver. Constructor enforces `FLAG_DEBUGGABLE`; actual TLS session, QR binding, local/peer approvals and final confirmation gate remain unchanged. Voice frames are never sent until the same gate also used by real text has finished. The same session's sole writer sends all voice, heartbeats and controls. No background voice.
- `DebugLanVoiceAudio.kt`: mic/speaker only, no network, persistence or service; uses a debug runtime check. RECORD_AUDIO permission exists **only** in the debug AndroidManifest, so release has no microphone entitlement. The production `CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` flags stay **false**.
- `LocalPairingTestScreen.kt`: prompts on tap, shows start/send and accurate remote playback status, cancels on actual session exit. Text chat remains available.
- The older `DebugFutureLanVoiceFrameCodec` stays separate, **unwired** from live Android app/transport.

## F4-C revised hold-to-talk interface

When running a Preview that includes F4-C, the default voice action is a
large **HOLD TO TALK** surface. Keep a finger down while speaking, then
**RELEASE TO SEND** (three seconds maximum). This still buffers audio during
the press and sends the encrypted burst afterward, not live as you talk.
If you cannot use touch-and-hold, choose **USE TAP CONTROLS INSTEAD** to
show the original START TALKING / SEND VOICE BURST option. A cancelled
gesture must not send unintended audio. Screen-reader/keyboard activation
uses the same two-tap fallback. Check permission grant/denial, pointer
cancellation and background/disconnect before relying on the gesture.

[F4-C gesture test notes](F4_C_HOLD_TO_TALK_GESTURE_2026_11_08.md).

## F4-B pre-phone reliability improvements

Source tests now cover two practical recording/display hazards before two-phone
hardware testing:

- Releasing the microphone too quickly or failing to initialize it produces a
  visible **No usable microphone audio** result instead of an indefinite
  **Finishing voice capture** state. The voice button cannot start another
  capture until the prior one has finished. A brief test phrase remains better
  than a split-second tap.
- The receiving display says **speaker playback started** only after the
  local Android `AudioTrack.play()` call succeeds. That does **not** prove a
  person heard the sound, and actual OEM speaker QA remains open. If playback
  cannot start or the microphone is busy, the app says so.
- **Hide conversation** also stops this phone's microphone/speaker and
  suppresses new inbound playback while hidden. It is **local privacy, not a
  network block**: the other phone can still send to the active TLS session.
- Temporary encoded voice/text/consent buffers owned by the test packet layer
  are zeroed after synchronous TLS reads/writes and on parsing errors. This
  is best-effort JVM cleanup, **not guaranteed heap/OS memory erasure** and
  not an independent security endorsement.

The underlying live LAN TLS/QR and bilateral human consent requirements are
unchanged. All voice functionality remains debug-only, and the existing
production voice flag and release microphone permission stay disabled.

## What to test with two phones

Install the **same newly built Preview version** on both phones; Preview 200002 does NOT contain this feature. Use only harmless test words. On the same local Wi-Fi, show/scan the two QR codes and independently approve both test sessions.

1. On phone A, enter nearby chat. Tap **START TALKING**, grant microphone permission if requested, tap START again, say `Test one two` clearly, then tap **SEND VOICE BURST** (or allow automatic 3-second end). Observe A's **Sending… / Written to TLS** status.
2. On B, listen for A's voice from the phone speaker and confirm the app reports that a burst was received/played. Test B → A as well. Do NOT infer B heard it merely from A's written status.
3. Deny mic access then retry; typed text must still function. Repeat a second/third burst after the previous one finished. Send an extremely short tap: it should not send malformed audio or crash.
4. While recording, hit **End chat**, press Home/background, switch off Wi-Fi, or lock the phone. Capture and playback must stop, the TLS session must close, and fresh QR + consent must be required before a new chat.
5. Try both people recording at once; report whether sound is missed or delayed. There is **no floor arbitration** in this first burst milestone, so simultaneous use is not yet a robust push-to-talk experience.
6. On a speaker-enabled phone, verify playback isn't much too loud. If possible, use headphones to avoid feedback. Confirm the test connection can still send text and regular heartbeats after a voice burst.

Record only the **app version**, whether voice actually played, permissions outcome, and the first failure step. Never post raw microphone audio, QR secrets, device IDs, certificate bytes or private speech. Do not use this experimental app for emergency communications.

## Remaining core roadmap

1. **F4-next:** two-phone Android QA, mic/speaker OEM testing, audio routing, burst timing, UI clarity. Fix any recording lockups / missing audio before more features.
2. **F4-stream:** upgrade the working debug LAN voice path to low-latency true press-and-hold PTT, half-duplex floor ownership, safe receive jitter buffering and bounded rate/backpressure. Keep user-visible listening/transmitting states truthful and all audio ephemeral.
3. **F2 Bluetooth** and **F3 Wi-Fi Direct:** separately review actual authenticated live session ownership, verify on two Android phones, then reuse the proven voice path only when those live transport sessions pass their own gates.
4. Independent cryptographic/protocol review and actual hardware tests before secure messaging / production claims. No app store release work is needed for these internal Preview tests.

**Rollback:** revert this milestone. No stored user data, permission migration in release, signing or saved account data involved.
