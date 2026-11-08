# F2-C — Android TLS provider readiness and bounded continuation groundwork

**Checkpoint:** November 8, 2026 (UTC). Parent merged checkpoint: F2-B
`fef5f768cd8b9c1d66e9b157385cf72777dbc5d1`.

## What changed in runtime

The debug-only Bluetooth RFCOMM diagnostic already performs mutual QR-pinned
TLS 1.3, QR challenge proof, exporter agreement, bilateral human approval,
and two final encrypted confirmations before closing. It is **not** an
application messaging channel.

F2-B added exact TLS 1.3 ciphertext-envelope validation. F2-C strengthens
**actual live source**, before the first encrypted QR control and before/after
each subsequent control:

- `DebugTlsProviderReadiness.requireControlReady` requires negotiated TLS
  1.3, `NOT_HANDSHAKING`, neither inbound nor outbound engine closed, and
  bounded provider packet/application buffer sizes.
- `requireExactControlResult` requires `Status.OK`, no new handshake state,
  and exact expected consumed/produced byte counts. Existing single-record
  RFC 8446 ciphertext checks and `finally` cleanup remain in place.
- If Android's `SSLEngine` reports closed, half-closed, renegotiating,
  post-handshake activity, underflow/overflow or unsupported buffer sizes, the
  diagnostic **fails closed** instead of accepting an apparent proof frame.
- This may make unsupported real Android providers fail the diagnostic.
  That is intentional for this source checkpoint: we cannot infer OEM
  interoperability without two-phone execution, and must not silently
  skip control records or downgrade cryptography.

Android SSLEngine documentation notes that `wrap`/`unwrap` may process
both handshake and application data:
https://developer.android.com/reference/javax/net/ssl/SSLEngine .
TLS 1.3 permits post-handshake messages such as KeyUpdate:
https://www.rfc-editor.org/rfc/rfc8446.html#section-4.6.3 .
This strict adapter **does not support** a general post-handshake state
machine. A future audited transport may need provider-specific handling
and more flexible record buffering, with explicit protocol versioning.

## Future-only post-consent budget: not wired or authenticated

Added `DebugBluetoothContinuationBudget`, a **pure JVM/debug-only
lifecycle model** for a possible future longer-lived session. It defaults
to a 90-second observation window with a 120-second hard configuration cap
and a positive generation token. The budget is one-shot: no automatic
rearm, no clock reversal, timeout extension, permission/background bypass,
or stale-generation revival.

The observation fields are caller-supplied booleans. **They do not prove
a pin, QR proof, exporter or consent.** This code is not referenced by
the live RFCOMM controller or TLS probe and must NEVER be used directly
as a transport authorization gate. It does not construct or hold a
socket, read/write messages, persist peers or extend the current
45-second diagnostic. It only provides deterministic lifecycle policy
test groundwork for a later reviewed integration.

## Tests and status

- `DebugTlsProviderReadinessTest`: all engine handshake states, closed/
  half-closed sides, bounded/invalid buffers, invalid protocols, exact
  `SSLEngineResult` and non-OK statuses.
- `DebugBluetoothContinuationBudgetTest`: incomplete observations,
  old attempt, deadline/clock reversal, background, permission loss,
  termination and no automatic restart.
- `scripts/tests/test_f2_c_tls_provider_and_future_budget.py`:
  verify actual provider checks occur before the post-handshake QR controls
  and in control I/O, while the future budget remains unwired.
- Existing M3n and F2-B source tests updated **to assert the stronger
  provider gate**, not to accept missing safety checks.
- [ ] PR debug Android compilation/JVM/Python/privacy/permission/CI pass
- [ ] Fresh merged-main CI on immutable SHA
- [ ] External TLS transport/security assessment: **not performed**
- [ ] Two Android phones with varying OEM/providers, wrong/expired QR,
      missing permission, UI/background interruption: **not performed**

## Next implementation sequencing

1. **F2-D:** independently review real TLS record/handshake interoperability
   and identify provider-specific failure handling; consider virtual paired
   `SSLEngine` tests with realistic ephemeral identities/keys. JVM-only
   mocks do not prove Android RFCOMM correctness.
2. **F2-E:** only after the mandatory review/device gate, design and verify a
   transport-owned post-consent connection lifetime and exact identity-bound
   session handoff. The owner will choose when to conduct physical tests.
3. **F2-F:** audited bounded encrypted text/ACK over the approved session
   followed by the actual Bluetooth chat UI. No plaintext fallback.

**Nonnegotiable:** release `CAN_CONNECT`, `CAN_SEND_MESSAGES`,
`CAN_TRANSMIT_VOICE` remain FALSE; no live Bluetooth user messages, new
permissions, trust persistence, signatures, APK identity or migrations.

**Rollback:** revert the F2-C PR; there is no persistent data.
