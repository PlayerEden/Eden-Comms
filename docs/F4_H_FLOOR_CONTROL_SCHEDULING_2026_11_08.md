# F4-H — floor-control scheduling under live microphone load

**November 8, 2026 · debugging and reliability milestone · source/CI only**

**Field report received:** Eden Comms Preview 200004 successfully updated the
prior Preview installation on one Android phone. This establishes an observed
in-place update; it does **not** establish live speech, two-device floor
arbitration, security review, or compatibility with all devices.

## Why this pass

F4-F makes the presenter/host arbitrate REQUEST → GRANT / DENY → RELEASE.
However the same single writer thread also drains up to **three seconds of
20 ms microphone frames**. When the other phone requests the floor during
that three-second stream, the presenter's DENY can sit *behind the entire
speech job*. The waiting phone has a three-second floor timeout. Depending
on scheduling and load, ordinary simultaneous-talk contention could be
mistaken for an unresponsive peer and close the connection.

## F4-H changes

- A separate small, bounded **control scheduling lane** promptly queues
  REQUEST, GRANT, DENY and pending-request CANCEL. It is not a second
  network connection or a second TLS session: every packet still goes
  through `sendPacket`, its shared `outputLock`, and the existing
  peer-pinned, human-approved mutual TLS 1.3 socket.
- The control lane only admits **four** queued/in-flight controls; if an
  essential control cannot be queued, the entire test session fails closed.
  All slots return on completed write, exception or enqueue rejection.
- An active audio turn's **END then RELEASE** remains on the original audio
  writer, preserving the voice framing order even if the user hides the chat
  while a record-then-send burst is still in progress.
- Socket close terminates both executors and clears the in-RAM floor.
  No app permissions, signer, package, production capability, user
  history, new transport or wire protocol version change.

## Automated checks

- A JVM concurrency test deliberately blocks the audio executor, then
  requires a floor response on the separate lane before audio unblocks.
- JVM tests check bounded capacity, rejection after shutdown, write
  exceptions and capacity release.
- Python source checks ensure that all floor frames use the existing TLS
  lock, that audio END still precedes RELEASE, and that production
  communications/permissions stay disabled.
- Existing F4-F grant, simultaneous host/join decisions, replay/cancel,
  timeout and trace tests must remain green.

## Real two-phone acceptance still pending

1. Start voice on phone A and press Talk on B *while A is still speaking*.
   B should receive a busy/deny promptly instead of losing the entire
   conversation to a request timeout.
2. Repeat with the presenter/host and joiner reversed, and vary whether
   the second press is just before, during, or immediately after the stream.
3. Finish a full three-second accessible tap recording and hide chat during
   the buffered send; ensure no second speaker turns on before END/RELEASE.
4. Repeat after immediate release, cancel while requesting, background,
   disconnect, Wi-Fi interruptions, and session expiration. Record only
   sanitized outcomes, rough voice delay and installed version.

**Status semantics:** a passed Android build only proves compilation and
automated test behavior. It is not proof of acoustic quality, latency,
security, or actual device interoperability. Keep testing harmless speech
and do not use Eden Comms Preview for emergency communication.

**Rollback:** revert the F4-H PR. The prior approved audio protocol and
public Preview APK 200004 remain usable test baselines; neither requires
new keys or uninstalling. A new signed Preview should be generated only
when a consolidated handset test checkpoint is useful.
