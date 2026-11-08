# Eden Comms M5-A — two-peer failure rehearsal and connection recovery

**November 7, 2026 • automated JVM reliability groundwork • not a device or security sign-off**

## What the audit found

The repository already tests many individual lifecycle and consent conditions,
including a two-endpoint pipe transcript. The missing cross-check was
connecting two independently progressing virtual endpoints with per-peer
approval gates, local cancellation and late callbacks, plus proving that
the real socket ownership primitive closes both connected localhost ends.

## Newly implemented automated checks

1. Independent virtual host and joiner use the existing fake transport
   adapter, session lifecycle and debug approval state machines. A
   diagnostic PREVIEW_READY state never authorizes sending. Each side
   must independently record its own approvals and final confirmations
   before even hypothetical preset-test sending is allowed.
2. Tests simulate a host cancelling, peer-loss notification at the joiner,
   backgrounding while events are queued, timeout before ready, Wi-Fi
   change, verification rejection and late callbacks arriving during a
   new session generation. Stale callbacks cannot revive or terminate
   a different generation. The simulator may enumerate LAN, Bluetooth
   and Wi-Fi Direct labels; this does not implement or enable their radios.
3. Local JVM loopback opens a random-port plain TCP socket pair,
   registers its handles through the existing SessionSocketRegistry,
   closes each endpoint, confirms sockets/listener are closed and
   confirms late socket registration is rejected. No payload is sent.
4. Synthetic failure types pass through the existing classifier and
   human-readable recovery guide, checking accuracy and avoiding
   raw exception details, automatic reconnect or security bypass.
5. A Python source contract verifies these new tests are isolated to
   test source and production flags remain disabled.

## Required acceptance

- [ ] PR Android/Kotlin test and debug APK, Python, privacy, permission,
  security and disposable-signature identity checks pass.
- [ ] Fresh merged-main CI passes and immutable commit recorded.
- [ ] First M4-UI07 manually triggered emulator visual QA remains open.
- [ ] Owner-triggered physical two-phone tests still deferred.
- [ ] Independent cryptographic/protocol security assessment outstanding.

## Limits and next step

The JVM tests **do not** exercise real Wi-Fi, handset background timing,
Android QR/camera, real TLS/Keystore, remote message delivery/ACK,
Bluetooth/Wi-Fi Direct, visual accessibility or security of production
messaging. M5-B should be a narrow, source-demonstrated real-session
interruption fix if new evidence warrants one, not a speculative rewrite.

All application changes in this PR are **test-only Kotlin files**.
No app runtime, protocol, encryption, permissions, message persistence,
signer, UI or release flags changed. Rollback by reverting this PR.
