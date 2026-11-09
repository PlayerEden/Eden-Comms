# M6-H — Wi-Fi Direct three-QR transport request is genuinely single-use

**Date:** 2026-11-09. **Branch:** `feature/m6-h-one-use-p2p-qr-transport-2026-11-09`.
**Scope:** debug-only, real Wi-Fi Direct three-QR preparation → original pinned TLS request construction.
This is a source/JVM reliability and fail-closed checkpoint, not a device or independent crypto review.

## Concrete issue found in live source

`DebugWifiDirectQrCoordinator.newVerifiedTransportRequest(hostRole)`
already checked in-person three-QR stage, expiry, role, local/peer pins
and nonce binding, but it returned a **new** `DebugBluetoothTlsProbeInput`
each time those checks passed. No one-use flag was checked or consumed.
The original comment called its proof expectation one-use, whereas the
implementation permitted repeated issuance from the same still-valid
three-QR transcript. This mattered especially after failed host
connection setup when the P2P group remained observed.

This PR makes the one-use source claim accurate; it does **not** say
we have proved protocol safety on actual Android handsets.

## Implementation

- New tiny `DebugWifiDirectVerifiedQrUseGate` in `src/debug`:
  three synchronized read/claim/reset methods and one `issued`
  bit. Its only job is preventing duplicate transport-request
  issuance. It cannot connect, encrypt, discover or approve.
- The actual QR coordinator checks `available()` before building
  its role-specific pinned TLS request. It claims the one use
  **after** constructing a valid request; any competing duplicate
  request gets closed and returns null, including its sensitive
  temporary contents.
- `readyForVerifiedTransport(hostRole)` becomes false after the
  first successful issuance; repeated host/join attempts cannot
  rebuild a new TLS socket request from the same QR proof.
- **Only** `clearQrOnly()`, already used when the user starts new
  QR preparation or explicitly clears/cancels it, resets the gate.
  A TCP/socket failure, time limit, identity check failure after
  issuance, approval denial or cleanup uncertainty does not reset
  the claim or silently retry.
- Fixed on-screen coordinator copy after issuance: if the session
  fails or ends, exchange all three fresh QR codes again. A new
  QR scan alone is not authorization: the unchanged original
  mutual pinned TLS, transcript/exporter proof, **both** human
  approvals and encrypted final confirmations remain mandatory.

## Regression gates

- JVM unit `DebugWifiDirectVerifiedQrUseGateTest`: hundredfold
  duplicate claims rejected, explicit reset permits precisely one
  new claim, 32 concurrent claimers result in exactly one winner.
- Python `scripts/tests/test_m6_h_one_use_verified_p2p_qr.py`:
  actual coordinator and both real Host/Join integration paths,
  request construction before claim, private proof wiping,
  debug-only scope and `CAN_*` production flags FALSE.
- Require exact final PR-head Android/JVM/Python/security CI
  green before merge, then independent postmerge main CI.
- **NOT RUN:** actual phone QR camera scans, one-use failure/retry
  timing, Wi-Fi Direct group creation/cleanup, TLS provider behavior,
  keyboard/TalkBack, radio latency, on-device saved history or
  independent cryptographic/security/privacy acceptance.

## One consolidated phone acceptance addendum

During the existing C03/C04/C05/C06/C12/C17 pass, use harmless
test data to trigger a **failed/expired/declined** attempt after the
three-QR proof was handed to a real transport. Confirm that a second
attempt with unchanged QR is rejected on the original phone, and
that both people can explicitly start a **new three-QR exchange**
and complete mutual TLS + both approvals. Repeat for host and
joiner and with a stop during asynchronous connection setup.
Do not log QR codes, pins, identities or message bodies.
Record PASS/FAIL/BLOCKED/NOT RUN, not inferred security approval.

## Release/rollback

No new permission, package ID, signing key, storage, server,
network, crypto algorithm, SSLEngine, audio or production
permission; no automatic reconnection or chat authority.
Release `CAN_CONNECT`, `CAN_SEND_MESSAGES` and
`CAN_TRANSMIT_VOICE` all remain **FALSE**.
If a handset reveals an unexpected valid retry regression,
roll back M6-H only, but **do not** intentionally reuse a
stale QR transcript as a workaround without a fresh review.
