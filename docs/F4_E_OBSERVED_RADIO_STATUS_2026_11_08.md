# F4-E — visible receive/transmit state on the real LAN test chat

**November 8, 2026 · debug-only source milestone, NOT a distributed transmit-floor protocol**

The existing F4-D Android test chat streams microphone PCM in 20 ms frames
over its already mutually authenticated, both-approved local Wi-Fi TLS session
while the user holds the PTT button. F4-E makes the other phone's observed
talking state visible instead of waiting for its completed voice-turn event.

## Implemented

- The same real `LanDemoSession` TLS reader emits `VOICE_STARTED` **only
  after accepting a correctly ordered, approved ECVP START** and
  `VOICE_FINISHED` **only after validated END**. The codec, QR verification,
  TLS writer and existing mutual human approvals did not change.
- The chat shows **RECEIVING • OTHER PHONE IS TALKING** while the reader
  has an active remote voice turn. The local PTT shows **TRANSMITTING • LIVE
  TEST AUDIO** during its own capture, plus Stop/Ready status.
- An observed remote turn disables starting another hold and blocks starting
  a new accessibility/tap-mode capture. A tap-mode recording already in
  progress retains its Send/Stop action if simultaneous STARTs cross in flight.
- Session end, failed connection, fresh QR attempt and other normal teardown
  clear the remote indicator; stale posted events remain attempt-fenced.
- Muting/hiding locally still suppresses audio on that phone without claiming
  the peer stopped transmitting. The network reader may continue to observe
  START/END during an active, hidden conversation.

## What this does *not* solve

This is a **courtesy half-duplex indicator**, not permission from the peer to
transmit and NOT an exclusive radio floor. Two people pressing at effectively
the same instant can still collide. Receiving status means a *validated
network frame was seen*, not that the loudspeaker emitted audible sound. The
3-second cap, 12-frame queues, no recording persistence, debug-only
permission gate and disabled release voice capabilities remain unchanged.

**Next functional milestone:** implement a session-scoped, host-arbitrated
REQUEST → GRANT → RELEASE exchange, with deterministic simultaneous-press
handling, bounded timeout, verified one-turn ownership and fail-closed
cancellation. Start Android microphone capture only after the requester has
the explicit floor. Retain strict control/PCM packet ordering, no plaintext
paths, peer consent and session teardown. Then run actual two-phone latency,
collision and interruption QA.

## Verification

GitHub Android debug workflow checks Gradle compilation, JVM unit tests,
Python contract tests and the release/debug permission boundary. F4-E adds
`scripts/tests/test_f4_e_live_radio_status.py` to assert the receiver state
is sourced from validated frame events and the UI blocks observed overlap.
Actual two-phone acoustic acceptance and an independent protocol review
remain open; do not use this build for sensitive or emergency communications.

**Rollback:** revert the F4-E PR. This change introduces no new persistent
data, APK identity/signing, permissions or wire-format migration.
