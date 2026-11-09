# F2-L — monotonic post-consent Bluetooth completion

**November 8, 2026 · debug-only real RFCOMM/TLS diagnostic · JVM + Android CI**

## Why this matters

The actual Android Bluetooth diagnostic verifies a single RFCOMM socket
using pinned mutual TLS 1.3, reciprocal QR challenges, TLS exporter,
session commitment, two independent approvals, and two encrypted
final CONFIRM records. It then issues a one-use **diagnostic-only**
completion receipt, checks it against the current controller attempt
and immediately closes the RFCOMM socket.

Previously, this receipt's short three-second age was checked only
using `System.currentTimeMillis()`. Android wall time can change when
users adjust date/time or the network synchronizes it. A reset could
make a receipt that was old in real elapsed time appear three seconds
old or less. The separate 45-second RFCOMM lifetime still limits
exposure, but a per-receipt clock should never depend on wall time alone.

## F2-L change

- The actual `DebugRfcommTlsProbe.run` stamps the completion receipt
  with `SystemClock.elapsedRealtime()` **after** successful dual
  approvals/final confirmations. It also retains wall time for QR
  issued/expiry validation.
- The actual `DebugRfcommLinkController` passes its own
  `SystemClock.elapsedRealtime()` when consuming that receipt
  inside the exact active attempt-token/role/foreground/permission
  gate.
- `DebugBluetoothProbeCompletion` consumes once and requires
  BOTH valid QR/wall timing and a nonnegative, at-most-3000ms
  monotonic delta. Wall time moving backward, forward, or returning
  to its previous value cannot refresh real elapsed time.
- JUnit tests cover both boundary times, clock rollback/reset,
  altered host/join role, permission loss, foreground loss,
  QR expiry, construction errors, one-use/close, and Long.MAX_VALUE
  arithmetic. Source-level guards ensure the live worker and
  controller, not just a fake clock, actually use monotonic time.

This is **not** a new credential or authorization to send messages.
The real diagnostic still closes its RFCOMM socket even after success.
No TEXT/ACK/voice, persistence, background service, new signing,
permissions, transport, key material, or production feature was added.

## How this relates to the actual Bluetooth chat milestone

The next major milestone cannot simply remove the `stop()` call:
a reviewed design must transfer the **same** RFCOMM socket,
TLS engine, provider record buffers, QR/exporter-bound transcript,
and a strictly bounded in-memory session owner after final consent.
A future-only test owner with arbitrary caller-supplied booleans is not
cryptographic proof. No post-consent live engine/socket retention is
approved without independent security review and two-physical-phone
Android provider interoperability evidence.

The [F2-I independent review checklist](F2_I_BLUETOOTH_SESSION_REVIEW_GATE.md)
remains open, as do user-deferred two-phone tests.

## Physical hardware QA gate

On two Android 12+ phones, with Wi-Fi/cellular off and Bluetooth on:

1. Use the existing guided debug Bluetooth QR-first setup and actual
   RFCOMM host/join steps. Confirm both people separately approve and
   the fixed `Verified test step 5/5` appears only if all controls
   completed.
2. Verify the diagnostic closes normally **without** opening a chat.
   Repeat with wrong/expired QR, decline, Bluetooth-off, app background,
   and permission revocation, checking each end remains closed.
3. If safe and feasible in a dedicated test, change Android's date/time
   between two test attempts. Time edits cannot replay an already
   used receipt. Do not infer anything about private traffic from
   the UI's coarse proof label.
4. Record only public build/version and sanitized step/outcome; never
   upload QRs, MACs, peer certificates, addresses, keys or contents.

**Acceptance:** PR and merged-main Android/JVM/Python/security CI green.
Even success does not prove OEM RFCOMM/TLS compatibility or
independent protocol review. Preview 200008 is the latest signed
handset baseline and predates F2-L.

**Rollback:** revert this narrowly scoped Bluetooth diagnostic PR.
No state migration or change to existing Preview signer/app ID.
