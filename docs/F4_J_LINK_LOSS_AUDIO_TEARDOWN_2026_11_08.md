# F4-J — stop local audio immediately on a broken LAN test connection

**November 8, 2026 · debug-only source/CI reliability checkpoint · not physical acceptance**

## Why

Eden Comms Preview 200004 updated successfully on one Android phone, but the
live two-phone walkie-talkie path is still pending handset verification.

Before F4-J, disconnecting the real encrypted LAN session called the
session's `close()`, wiped the in-memory voice queue and closed its socket.
Local Android `AudioRecord` / `AudioTrack` were normally stopped later,
when the networking thread posted an ENDED event and the foreground Compose
screen handled it. That was usually fast, but a busy UI queue could delay
microphone/speaker shutdown after a lost Wi-Fi link, a host timeout or an
unexpected peer disconnect.

## Change

- `LanDemoSession` has a one-shot **local-only audio stop callback** invoked
  directly during its idempotent `close()`, after socket and executor cleanup.
  This callback never opens another transport or sends a radio packet.
- The real Android screen installs the callback on the current connection
  before starting Host/Join. Its `DebugSessionAudioOwner` compares object
  identity: an old session's late close cannot stop microphone/speaker audio
  belonging to a newly paired session.
- An ordinary user's End, Home/background, fresh QR restart or screen disposal
  revokes the old ownership *before* closing its socket and calling the
  existing local audio cleanup. A network-triggered close directly calls
  `DebugLanVoiceAudio.cancelAll()` without waiting on the UI event loop;
  the original ENDED/FAILED event remains responsible for result-screen
  messages and sanitized diagnostic status.
- Close still cancels live queued PCM, zeroes the in-memory voice assembler,
  closes the floor arbiter and both TLS writers. **No automatic reconnect**:
  recovery always requires two new QR codes and two human approvals, as before.

## Automated verification

- JVM tests cover current-only closure, old-session delayed callback,
  voluntary revoke, once-only stop, and contention between new activation
  and old network close.
- Python guards assert the actual screen and session close are wired to the
  ownership test and the original test-only permissions/capabilities.
- Android compile, JVM, Python and release-boundary CI are required for merge.

## Physical two-phone checks (NOT completed)

1. On a paired, mutually approved same-Wi-Fi connection, start a short
   hold-to-talk transmission, then switch off Wi-Fi on the sending device.
   Check that Android stops its microphone quickly, no new audio leaves the
   phone and the UI reports link loss rather than a successful delivery.
2. Repeat during a waiting GRANT, during audible speaker playback, and during
   the accessible three-second tap-to-talk recording.
3. Start a new independent QR/TLS session after an interruption. Confirm the
   old connection cannot stop new microphone playback or reuse a grant.
4. Background/lock/End and Hide during microphone activity. Confirm the
   camera/microphone permission and app's notification behavior match
   the expected foreground-only lifetime. Verify no unexpected audio
   persistence or silent reconnect.
5. Use only harmless test phrases; compare audio on both phones. A green
   source build does not measure actual audio delay, prove two-phone LAN
   interoperability or establish cryptographic review.

**Rollback:** revert F4-J PR. No wire migration, package/signing changes,
new credentials, permissions, on-disk data or public release claims.
Preview 200004 remains the older verified installation baseline; the next
signed Preview can consolidate F4-H/I/J for device QA.
