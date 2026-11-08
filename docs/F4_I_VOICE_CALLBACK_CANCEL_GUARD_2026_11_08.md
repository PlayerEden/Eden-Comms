# F4-I — cancellation-safe foreground voice handoff

**November 8, 2026 | source/CI reliability pass | no new Preview yet**

## Problem investigated

Eden Comms's accessible **START TALKING / SEND VOICE BURST** mode
captures a maximum three-second audio clip on an Android AudioRecord worker,
then queues the clip to its already-approved TLS session. The user may
press Cancel, hide the conversation or background the app while that worker
finishes. The previous generation guard could prevent *most* canceled
callbacks, but a callback already in flight could still reach
`sendVoiceBurst(clip)` after the user tried to stop it.

A separate condition was found on live hold-to-talk: if the session
granted a voice floor but its live capture couldn't be started, the
microphone failure path could leave that floor reserved unnecessarily.

## Changes

- The accessible/tap fallback uses a per-recording synchronized,
  one-shot `DebugTapVoiceSendGate`. A cancel/Hide/background that wins
  the gate before the worker's send handoff prevents that clip from
  **ever being enqueued**. Only one callback can claim the send.
- The send decision and `live.sendVoiceBurst(clip)` enqueue are in the
  same short synchronized critical section. If the send committed before
  cancellation, the UI explicitly warns that already queued audio
  cannot be recalled. This is not a mechanism to erase transmitted sound.
- When the mic produced no usable data, the code releases an unstarted
  floor even if its UI callback is stale; the worker-owned PCM is zeroed.
- On a failed live audio startup, release the unused host/granted floor.
  A floor with an active buffered writer is still released **only after**
  its END, preserving F4-H's END→RELEASE guarantee.
- No other authenticated LAN wire format, TCP socket, pairing, approval,
  Android permission, voice codec, signer, package identity, production
  feature switch or data persistence changes.

## Tests

New concurrency/JVM tests verify cancel-first, send-first, simultaneous
cancel vs send, duplicate callback and simulated writer rejection. Python
contract checks verify the real UI paths route through the gate and that
Hide, teardown and cancellation invalidate it, plus existing debug-only
release boundaries.

## Physical QA still needed

1. Upgrade a future signed Preview **over existing 200004**; do not
   uninstall or reset pairing just to work around a version/signature
   failure. Preview 200004 itself predates F4-H/F4-I.
2. On two already approved same-Wi-Fi phones, use **tap mode** on A.
   Begin recording then immediately Cancel or Hide (or background).
   B must not hear a newly queued recording when cancellation wins.
3. Repeat near the three-second automatic stop while quickly
   canceling, then start another tap session. Observe no ghost burst
   from the old capture.
4. Fail/revoke microphone permission or make Android audio unavailable.
   The phone must return to a sensible Ready or error state and the
   opposite phone should not remain indefinitely blocked by a floor.
5. Validate live hold-to-talk still ends on release, screen exit and
   network loss. For a send already committed before cancellation,
   record that it can no longer be recalled, not a false unsend claim.

Only harmless test audio; no real-world private/emergency claims.
User-confirmed **200004 update success** covered installation only,
not the two-phone voice/audio/latency test.

**Rollback:** revert the F4-I PR. Existing test Preview 200004 stays
installable and does not contain this source-only fix.
