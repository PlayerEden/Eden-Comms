# F4-D2 — local-only 20 ms microphone frame pipeline

**November 8, 2026 · debug implementation / source QA, not remote voice**

## What changed
The existing M6-D1 explicit microphone recording still captures at most five seconds on
one phone, with Android RECORD_AUDIO granted on demand and no capture in the background.
When a user explicitly taps **Play on THIS phone**, F4-D2 splits the captured 16-kHz,
mono, 16-bit PCM into **640-byte / 20-millisecond** frames. The last frame is
zero-padded if necessary; at most **250 frames** can exist for one clip.

A finite, ordered, copied in-memory frame queue rejects skipped/replayed frame
sequences, incorrect frame sizes, over-capacity writes and writes after closure.
The local playback worker consumes those ordered frames into Android AudioTrack
**MODE_STREAM**, handles partial writes, waits a bounded time for output drain,
and wipes each consumed frame plus remaining frames at teardown as best effort.
Pause, stop, finish, worker errors and generation changes prevent new playback.
The existing hold/release and accessible start/stop capture controls remain.

**Important:** this is *capture, then local packetized playback*, not live simultaneous
audio streaming, a network frame format, a jitter-buffer implementation or a call.
No data is sent to any peer. Do not mistake local packet-order tests for latency,
real-time reliability, authenticated transport or encryption validation.

## Hard security boundary
- Only `src/debug` Android audio code; unit/source tests may reference it.
- No sockets, packet networks, Bluetooth, Wi-Fi Direct, TLS, identity or approval wiring.
- No change to release manifest, production permissions, app ID/signing, or persisted data.
- `ReleaseCapabilities.CAN_CONNECT/CAN_SEND_MESSAGES/CAN_TRANSMIT_VOICE` remain false.
- No audio files or saved sessions; RAM and OS hardware buffers cannot be guaranteed erased.
- This is not a secure messenger or emergency communications capability.

## Test/acceptance matrix
- **Automated:** 640 bytes per 20ms, 250 frame limit, exact size/order, replay/gap/overflow
  rejection, odd/oversized clip rejection, tail zero-padding, copied input isolation,
  consumption and cleanup. Source guard verifies STREAM/WRITE_BLOCKING without remote hooks.
- **Android CI:** compile debug Kotlin/JVM, Python/security tests, exact release/debug
  permission guard, signer/APK build. A passing CI run only verifies source and package.
- **Physical one-phone (owner-deferred):** explicit mic permission deny/grant/re-tap,
  record/stop under five seconds, playback audibility and duration on speaker/headset,
  stop playback, screen off/background interruption, Activity teardown and empty restart.
  Watch for capture gaps and AudioTrack partial writes on actual devices.
- **Independent security review (blocking before remote PTT):** authenticated foreground
  session ownership, end-to-end voice framing encryption, nonce/replay ordering, consent,
  backpressure/jitter, audio path denial on disconnect, and platform privacy behavior.
  Future LAN remote PTT must be a separate gated implementation; Bluetooth/Wi-Fi Direct
  voice cannot be enabled from these local results.

## Rollback
Revert this F4-D2 PR; the M6-D1 sample recording and static local playback can be
restored without user data migration, keys or package identity changes.
