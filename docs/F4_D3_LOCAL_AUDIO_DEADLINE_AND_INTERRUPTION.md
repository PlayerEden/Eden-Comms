# F4-D3 — one-phone audio deadline, interruption, and teardown

**November 8, 2026 · debug-only. No remote transmission and no phone QA.**

## Purpose and implementation
F4-D2 introduced ordered 20 ms PCM framing and local AudioTrack streaming after
the existing user-initiated, at-most-five-second microphone recording. F4-D3 makes
that experiment less vulnerable to stalled audio routes or quick Stop/Back actions:

- AudioRecord reads now use `READ_NON_BLOCKING`; when no audio is available,
  the microphone worker pauses 10 ms rather than waiting indefinitely on a driver.
- AudioTrack streams frames using `WRITE_NON_BLOCKING`; each frame may require
  partial writes and zero-byte retry. A closed, monotonic eight-second wall-time
  budget bounds the *entire* local playback attempt, not each frame separately.
- Stop explicitly invalidates the playback generation before stopping the track.
  Late worker completion cannot change "Playback stopped" into "finished".
  A previous playback worker must finish cleanup before another playback or capture
  starts. Old worker cleanup may refresh controls without altering a newer status.
- Only a completely written queue followed by observed playback-head drain can
  say "finished". Cancelled, partially written, or timed-out frames cannot.
- Pending PCM packets and consumed packet bytes are best-effort cleared, with
  existing onPause/onDestroy lifecycle cleanup. Neither Android hardware buffers
  nor VM copies are promised erased.

**Limits:** This is still record-then-play on **one phone**. It is not a live
microphone-to-speaker monitor, network latency measurement, encryption, peer
authentication, a voice call, or proof of real-time audio performance. OEM drivers
may still behave differently and must be tested on actual phones.

## Source and test acceptance
- JVM tests: monotonic clock, expiry edge, rollback, explicit close and invalid
  clock start, plus prior F4-D2 frame-order/copy/cap tests.
- Python guards: non-blocking AudioRecord/AudioTrack, global finite clock,
  separate workers, fresh generation on stop, no old completion UI overwrite,
  no network adapters or persistence, unchanged release capabilities/manifest.
- GitHub Actions: Android debug compile and JVM/Python tests, exact permissions,
  security boundary, Android APK packaging, test signer and artifact.
- All physical audio, lifecycle, lock-screen, call-interruption, headphones,
  denial/revocation, and hardware latency validation remain **owner-deferred**.
  Independent protocol/privacy review is still required before any LAN/Bluetooth/
  Wi-Fi Direct remote audio feature or normal release capability.

## Suggested on-phone test (not executed)
1. Grant microphone permission only after tapping Record; grant must not auto-start.
2. Record 1–5 seconds, play it back locally, test speaker and headphones.
3. Tap Stop mid-play then immediately try Play again; it must never display
   a stale "finished" message or run two playback workers at once.
4. Background or lock the device while recording/playing; return and verify
   recording is discarded and no worker continues.
5. Disable the audio route or take a call during playback; app should eventually
   stop and report that playback did not complete, without background capture.
6. Rotate, use TalkBack, deny microphone permission, and retry after allowing it.
   All observations belong in sanitized QA notes; never upload actual voice.

## Rollback
Revert F4-D3 alone to restore the F4-D2 bounded local frame rehearsal.
No app data, package identity, signer, stored keys, or release permissions change.
