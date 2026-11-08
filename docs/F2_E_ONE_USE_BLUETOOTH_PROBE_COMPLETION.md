# F2-E — one-use verified-path Bluetooth diagnostic completion

**Date:** November 8, 2026. **Source checkpoint:** merged F2-D
`10e475832471292872d0f8501194b1fb1b60e4fc`.

## Goal

Carry the result of the already implemented mutual TLS 1.3 + QR +
exporter + exact transcript commitment + bilateral human approval +
two encrypted final confirmations across the **debug RFCOMM probe /
controller boundary** without treating a successful callback as a
reusable permission to communicate.

F2-D established paired JVM TLS-provider interoperability and fixed a
provider buffer problem, but neither JVM CI nor this F2-E checkpoint
constitutes physical Android radio acceptance or independent security
review. There are still no Bluetooth user messages or remote voice.

## Actual F2-E implementation

The real `DebugRfcommTlsProbe.run(...)` remains a blocking, one-socket
worker call. After the complete protocol/consent exchange succeeds,
it **rechecks fresh QR expiry**, clears temporary binding arrays,
and returns a `DebugBluetoothProbeCompletion` object. Any exception,
wrong certificate pin, QR mismatch, consent refusal, timeout, missing
confirmation or expired QR produces **no completion receipt**.
The existing `finally` continues clearing in-memory transcript data,
exporter and peer pin and closing the TLS engine outbound side.

`DebugBluetoothProbeCompletion` is a debug-only, minimal *diagnostic*
outcome: it contains the host/join role, issuance time and QR expiration
time, **not** a Bluetooth socket, transport, TLS key, exporter, peer
fingerprint, transcript commitment, message or send API. It has a
**3-second** wall-clock handoff window and is consumed at most once.
A failed acknowledgement, role mismatch, expiry, clock rewind,
background or missing Bluetooth CONNECT permission permanently burns
it. A caller invoking its factory cannot manufacture real TLS proof:
only the call path inside the actual verified worker is evidence of
the sequence.

`DebugRfcommLinkController.connected()` receives the completion on
that same worker. It checks **object identity** of the active attempt
token, that the gate is still `LINK_OPEN`, matches the host/join role
and calls `acknowledgeOnce` while holding the controller lock. The
normal F2-A foreground/permission status check remains in the final
stop path. A wrong or stale result cannot resurrect a stopped/restarted
attempt. If completion is not acknowledged, the controller terminates
with BACKGROUND, PERMISSION_LOST or FAILED as appropriate, never
reporting a false successful approval. Receipt cleanup runs through
`finally`.

**This is not a live channel handoff**: the controller still closes
the Bluetooth RFCOMM socket on every success, failure, background
transition, permission loss or timeout. Its existing **45-second**
link lifetime remains unchanged. The F2-C future 90-second
`DebugBluetoothContinuationBudget` remains unwired.

## Tests

- New `DebugBluetoothProbeCompletionTest`: positive one-use receipt,
  duplicate use, wrong role, role retry after a failed attempt,
  background, permission loss, QR expiry, time-window expiry,
  clock rollback, early close, invalid inputs and near-overflow clock.
- New `test_f2_e_verified_handoff_boundary.py`: checks the
  **real source order**, current controller token and role binding,
  existing socket closure, no reusable credential or send API and
  disabled release capabilities.
- `scripts/check_security_review_boundary.sh`: explicit tests for
  the receipt being debug-only, single-use, fast-expiring and unable to
  carry a socket or production messaging.
- Existing M3n, F1, F2-A/B/C/D checks and Android build/test suite
  continue to apply.

## Mandatory acceptance gates (not bypassed)

1. [ ] F2-E PR Android/Kotlin/Python/audit CI green
2. [ ] Fresh merged-main CI green
3. [ ] Independent TLS/RFCOMM, QR identity binding, session lifecycle,
        post-consent state-machine and message-frame security review
4. [ ] Two-phone Android 12+ physical test: reciprocal QR, actual
        RFCOMM TLS/provider exporter, consent/decline, lost permission,
        background, wrong target, delayed or duplicated completion
5. [ ] Separately reviewed design/implementation of an **owned live
        post-consent transport session** and bounded text/ACK framing.
        This diagnostic receipt must never be used as authorization
        without real channel and session binding.

**Never claim JVM tests are physical QA.** Production
`CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE`
stay `false`. No production capabilities, new permissions,
signer/package identity, storage or saved trust were added.

**Rollback:** revert the F2-E PR. No persistence or migration.

See also
`docs/F2_D_PAIRED_TLS_ENGINE_INTEROP_TEST.md`,
`docs/F2_C_PROVIDER_READINESS_AND_CONTINUATION_BUDGET.md`,
`docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md`.
