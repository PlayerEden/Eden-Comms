# M6-D1 — One-phone microphone and speaker test

**November 7, 2026. Debug-only functional audio milestone. No remote PTT.**

## What can actually be used

The Voice / PTT screen keeps its large remote-transmit control disabled.
In a DEBUG APK, a separate button opens a non-exported local audio Activity.
It uses Android AudioRecord to record real microphone input and AudioTrack
to play the same sample through the same phone's output.

The user can test:
- Hold to record and release to stop (five seconds maximum).
- Accessible Start and Stop recording without a long press.
- Explicit Play, Stop playback, and Discard recording.
- Android microphone-permission denial/grant (grant never autostarts).
- Background/exit teardown and best-effort in-RAM sample clearing.

This is not a call, Bluetooth voice, Wi-Fi Direct voice, LAN voice, an
audio attachment, a secure messenger, or a proof of working physical audio.
There is no peer, socket, service, file or remote audio transport.

## Security and privacy limits

- Code, microphone permission and Activity are src/debug ONLY.
- Activity is non-exported and checks FLAG_DEBUGGABLE at runtime.
- No recording before user action plus RECORD_AUDIO runtime permission.
- 16kHz mono 16-bit PCM, at most five seconds / 160,000 bytes in RAM.
- No audio file, background recorder/service, peer identity or network path.
- OnPause/onDestroy invalidate pending workers, stop playback/recording and
  discard sample as best effort. Audio OS/driver buffers cannot be guaranteed
  erased. Avoid confidential, personal or emergency conversations.
- ReleaseCapabilities.CAN_TRANSMIT_VOICE, CAN_CONNECT, and
  CAN_SEND_MESSAGES remain FALSE. No production or cryptographic review.

## Physical follow-up (NOT RUN)

On a test phone, verify permission denial; grant then explicit re-tap;
hold/release mic; Start/Stop alternative; playback vs headphones and speaker;
five-second cap; playback interrupt; background while recording; Activity
reopen does not retain sample; accessibility services and landscape layout.
A real device is necessary to verify mic hardware, threading, permission
prompts, audio latency and interruption behavior.

## Full usable test-build target

| User-intended feature | Source state | Next functional integration |
| --- | --- | --- |
| Nearby same-Wi-Fi text | Debug mutual TLS/QR/approval and short test text | Physical WAN-off negative QA, protocol review, peer receipt design |
| Bluetooth nearby chat | QR-pinned TLS + bilateral consent diagnostic, then closes | Independent protocol/device verification before enabling debug chat |
| Wi-Fi Direct chat | Discovery/QR preparation and unverified OS-link diagnostic | Bind the radio to QR-pinned peer, encrypted agreed session and text |
| Voice/PTT | **Now: real one-phone mic-to-speaker only** | Safe framed voice over reviewed, authorized peer session |
| Reliable connect/disconnect/restart | M5-A–E source and JVM/CI hardening | Actual interruption/Android device validation |
| Optional encrypted history, disappearance, remembered peers | Not implemented | Separate consented encryption/retention and security review |

## Build evidence requirements

- [ ] Android debug compile, JVM + source tests, permission + privacy audit.
- [ ] Fresh merged-main checks, immutable commit, debug APK artifact.
- [ ] Verify release manifest has **no** RECORD_AUDIO.
- [ ] Test hardware, Bluetooth/Wi-Fi Direct messaging, remote PTT,
  visual QA and independent security review remain OPEN.

Rollback: revert this PR; no migration. The permission is debug-only.
