# F2-P — terminal Bluetooth consent timeout and monotonic late-tap rejection

**November 8, 2026 · real debug RFCOMM consent gate · no live chat**

## The actual bug

The current Bluetooth Classic RFCOMM/TLS diagnostic requests an explicit
APPROVE or DECLINE **from each person** only after pinned TLS, QR challenges,
exporter binding and transcript agreement. Its worker calls
`DebugBluetoothConsentDecision.await(25_000)` and closes the socket after
denial, timeout or final confirmations.

Before F2-P, `await()` returned TIMEOUT without changing the decision
object's state. A late UI callback racing the worker's terminal cleanup
could still receive `approve() == true` after the worker had **stopped
waiting**. The controller's socket-token and foreground checks and eventual
close prevented this becoming messaging authorization, but a stale click
could be misleading and weakened our exact-consent lifecycle contract.

## The F2-P correction

The **real Kotlin `DebugBluetoothConsentDecision`**, which the real
`DebugRfcommTlsProbe` creates and the real controller uses, now:

- Consumes the single worker wait once. A second waiter returns CANCELED,
  never another approval.
- Treats elapsed WAIT timeout as a terminal `TIMEOUT` state. After it
  returns, all APPROVE and DECLINE taps are rejected.
- Uses an injected-for-JVM-tests, normally real `System.nanoTime`
  monotonic clock to reject a tap once the configured bound expires,
  **even when the worker thread has not yet awakened**. A backward or
  overflowing elapsed-time reading fails closed. This is independent of
  wall-clock date changes.
- Keeps an early, explicit, within-budget approve/decline accepted once.
  A timed-out worker's final result takes precedence if a tap races
  the deadline.
- On worker interruption, closes/cancels the decision and preserves the
  interrupt before rethrowing; user/background/permission closure wakes
  the wait and supersedes any previous tap with CANCELED.

No new cryptography, saved trust, account, network packet, permission,
background service, UI page, Bluetooth TEXT/ACK or voice was added.
The existing probe still immediately closes its TLS engine and the
controller still closes the exact RFCOMM socket after all outcomes.
The one-use F2-L completion receipt remains **diagnostic only**, not a
message authorization token.

## Verification

Six new deterministic Kotlin/JVM regression tests cover:
late tap after worker timeout, late tap while worker remains suspended,
just-before-deadline approval, monotonic rollback, interrupt/close and
duplicate-waiter refusal. Existing approve/decline, background
cancellation and invalid timeout tests still run. A new Python source
guard checks that **actual** RFCOMM worker/controller still uses the
25-second bounded consent exchange and same token/close-after-consent
model, with production communications disabled.

These tests cannot simulate actual humans or Android Bluetooth timings.
A physically tested delayed callback, foreground/background sequence
and permission/radio-loss case remain required on two Android 12+
phones with fresh reciprocal QRs and no private data in reports.

## Scope and next gate

F2-N/O established stronger JDK TLS/QR/consent-to-TEXT interoperability.
F2-P makes the **on-device Bluetooth diagnostic's actual consent wait**
more strictly fail-closed. Neither change grants retention of the real
RFCOMM socket for chat: actual Android Conscrypt/RFCOMM interoperability
and independent same-channel protocol review remain mandatory before
a separately gated live Bluetooth messaging experiment.

The latest signed handset Preview is **200008**; it predates F2-P.
No new signed APK is needed to verify unit tests. Bundle the consent
fix into a later consolidated handset test Preview.

**Rollback:** revert the F2-P PR. No wire/storage/signing migration.
