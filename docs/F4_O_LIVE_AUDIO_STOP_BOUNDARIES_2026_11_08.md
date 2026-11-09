# F4-O — microphone release and speaker-buffer stop barriers

**November 8, 2026 · debug-only LAN voice reliability · source/JVM/CI**

## Problem

Two independent, foreground Android audio callbacks can race with a user's
stop gesture or a network interruption:

1. `AudioRecord.read()` may finish a 20 ms (640-byte) frame just as the user
   releases Talk, hides the conversation or the connected LAN session ends.
   The old worker checked `capturing` *before* finishing the blocking read,
   then called `onFrame(frame)` even if the stop happened in between.
   This could enqueue one additional PCM frame after the user's stop.
2. Incoming `LivePlayback.offer()` copied PCM into the speaker queue after
   inspecting an unsynchronized `cancelled` flag. A simultaneous
   `cancel()` could empty the queue first and leave a newly enqueued
   voice chunk in a stopped worker's buffer.

Neither issue requires a new microphone/audio format or different network
protocol. The intended behavior is a clean **one-directional stop barrier**,
not an impossible guarantee that already-sent audio can be recalled.

## Changes

- `DebugLiveMicFrameGate` serializes the worker's very short in-memory
  `onFrame` callback against `finishCapture()`, `cancelAll()` and
  natural worker teardown. Once Stop wins, no new PCM can enter the outgoing
  bounded writer queue. A handoff that already won remains committed and
  may already have left the phone.
- `DebugLivePlaybackBuffer` owns a 12-frame/240 ms queue whose offer,
  normal finish and canceled drain share a synchronized boundary. PCM
  copies offered *before* cancellation are discarded and wiped; offers
  arriving after cancellation are rejected. Buffer overflow fails closed.
- Android `AudioTrack` worker checks the canceled/generation state again
  after polling each speaker frame, immediately before attempting playback.
  A previously polled frame or sound already in the device output driver
  cannot be unwritten; the existing `AudioTrack.stop()` remains best effort.
- New JVM concurrency tests cover mic stop-before-offer, mic simultaneous
  stop/handoff, playback cancel-before/after-offer, race under load,
  overflow and normal end tail. Python source guard checks **real Android**
  call-site wiring, unchanged consent/TLS boundaries and debug-only
  production flags.

## Boundaries and device checks

The new two small helper objects are per-active foreground voice turn,
in memory only. They have no file, socket, audio permission, Bluetooth,
Wi-Fi Direct, cloud account, identity or independent audio pipeline.

On the next consolidated signed Preview on two physically approved
same-Wi-Fi phones:

1. Hold Talk, speak, then release during an active syllable. Confirm the
   local mic stops and subsequent speech is **not newly queued**.
   Previously transmitted sound cannot be recalled.
2. Repeat with **Hide conversation**, **End** and Home/background,
   while another phone is receiving a live voice turn.
3. Drop the Wi-Fi link during an incoming stream and repeat after
   fresh QR, fresh approvals and confirmations. Old speaker frames
   must not replay on a new session.
4. Confirm audio still plays audibly and in order when no interruption
   occurs. Test both talk directions, three-second cap and record-then-send
   fallback. Distinguish UI start indicators from actually audible sound.
5. Record build number, model, OS version and sanitized pass/fail only.
   Never include actual private speech, QR images, IP addresses or keys.

**Status:** source/JVM/CI only. No real device mic/speaker timing measured;
no security review or production claim. Preview 200006 predates F4-O,
and remains the existing signed handset baseline.

**Rollback:** revert F4-O PR. No wire migration or signing change; earlier
Preview remains usable for comparison.
