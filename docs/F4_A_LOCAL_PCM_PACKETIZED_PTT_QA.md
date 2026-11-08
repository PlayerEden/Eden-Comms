# F4-A — real microphone → bounded PCM packets → local speaker

**Date:** November 8, 2026. **Parent:** Wi-Fi Direct F3-B3-A,
PR #106, merged-main commit
`27988e58e7d3f4cdb31d7d2395574eff07fdf7fc`; its PR and
fresh merged-main Android CI passed.

## Why F4-A now?

Eden Comms' first version is offline, two-person
**Connect → Communicate → Disconnect** over authenticated LAN,
Bluetooth Classic and Wi-Fi Direct, with optional foreground
push-to-talk. LAN has debug-only pinned TLS text/ACK, but remote
audio must not be bolted onto an unreviewed data protocol.
Bluetooth/Wi-Fi Direct still require independent protocol reviews
and physical two-phone testing. Meanwhile real one-phone microphone
and speaker testing was already available through
[the local audio screen](M6_D1_LOCAL_PTT_AUDIO.md).

F4-A makes that existing *real* audio UI exercise a bounded,
strictly validated **packet pipeline**. This is closer to the
encoding/ordering/discard path future remote PTT will require, while
remaining entirely in one Android process with **zero audio sent**.

## What changed

- New debug-only `DebugPttPcmFrames`: one 16 kHz, mono, signed PCM16
  little-endian stream is segmented into **20 ms = 640-byte** packets.
  Every packet has fixed `ECPT` magic, version 1, 16-bit 1-based
  sequence number, exact 16-bit PCM byte count and an explicit
  last-frame flag. The header is 10 bytes. Only the final packet
  may be shorter than 640 bytes; it must contain complete 16-bit
  samples, with **no silent padding or truncation**.
- Bounded to the existing **5-second = 160,000-byte** capture,
  **250 packets** maximum and the existing 1,600-byte minimum.
  Reassembly refuses duplicate, missing and out-of-order sequences;
  bad magic, version, flags, size or sample alignment; premature
  final; missing final; clips too short or longer than five seconds.
- The existing debug `DebugLocalPttActivity` still requests
  `RECORD_AUDIO` **only** on explicit Record, grants never start
  the microphone, and Hold/Release/Start/Stop work unchanged. After
  Android `AudioRecord` has collected real PCM, its worker converts
  bytes into *in-RAM packets*, then clears the original capture
  buffer. The screen stores the packet list, not a raw PCM clip.
- **Play on THIS phone** now validates/reassembles those packets and
  sends the reconstructed PCM to the existing real Android
  `AudioTrack`. Playback PCM clears after playback/stop. Discard,
  background, Activity teardown and stale recording workers clear
  packet buffers. Android/OS audio buffers are outside the app's
  guaranteed control, so do not claim forensic erasure.
- A debug-only JVM test suite uses actual PCM byte arrays (not mocks
  for framing) to verify exact lossless round-trip for short and full
  five-second clips, 20 ms frames, a 10 ms final frame, deterministic
  randomized even PCM lengths, altered headers and sequence/flag
  failures, and explicit buffer wiping. Existing Android build,
  permission and source checks verify no network or storage access.

**This is a codec/sequence *prototype*, not an approved remote voice
wire format.** It has no speech compression, jitter buffer, latency
measurement, encrypted audio transport, application-level
acknowledgement, retransmission, key material or remote UI. Header
checks detect structural corruption, not arbitrary changes to the
PCM payload. Only independently reviewed authenticated transport
can supply data authenticity. A passing JVM test does not prove
microphone latency, audio quality or OEM speaker output.

## One-phone QA checklist (physical tests NOT performed)

1. Install the debug APK using your existing signing identity; do
   not uninstall merely to update (verify actual signer/in-place
   install separately under F6).
2. Open **Walkie-talkie → Try local microphone and speaker**. The
   remote Hold to talk control remains disabled. Confirm the new
   text says the recording is packetized and **never sent**.
3. Deny mic permission. Verify no capture occurs. Grant permission;
   verify it **does not auto-start**. Explicitly tap Record.
4. Hold 1–3 seconds and release; select **Play on THIS phone**.
   Confirm that your own spoken audio is audible locally.
5. Record close to five seconds; confirm the recording stops within
   the existing five-second capture limit. Try accessible Start/
   Stop and replay/discard.
6. Start then quickly stop, leave the screen, or revoke permission;
   no late worker may reappear as a ready clip. No background capture
   and no remote transmission should occur.
7. Check narrow screens, font enlargement, speaker/headset routing,
   orientation, screen-off/microphone indicator behavior and audio
   interruptions. Record coarse pass/fail only; never upload audio.

## Remaining gates for real LAN PTT

- [ ] Full PR Kotlin/JVM/Python/security + Android APK CI green
- [ ] Fresh merged-main Android CI green
- [ ] One-phone physical microphone/speaker/permission/audio route QA
- [ ] Independently review **LAN session** cryptographic controls and
      agree on a separately versioned audio frame format and
      directional/session binding, transmit permission and packet
      size limits before sending actual media
- [ ] Create a bounded transport owner, one active push-to-talk
      speaker at a time, buffer backpressure, jitter/loss behavior,
      foreground/mic permission loss teardown, remote receiver,
      audio output and explicit disconnect
- [ ] Physically test two Android phones, wrong/expired QR,
      decline, late frame, repeated talk, interruptions,
      battery/thermal behavior and actual voice latency
- [ ] Only afterward consider remote PTT release capability.
      Bluetooth and Wi-Fi Direct also require their own radio and
      security acceptance gates.

There are no new Android permissions, production capabilities,
packages, signing keys, on-device databases, network traffic, or
peer history in this change.

**Rollback:** revert F4-A PR, retaining the original one-phone
record/play UI. All packet samples are in-memory only.

Related: [canonical full-function roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md),
[original local audio test](M6_D1_LOCAL_PTT_AUDIO.md).
