# Eden Comms M5-C — test-session teardown write gate

**2026-11-07 • scoped live-session race hardening • device QA deferred**

## Source-backed finding

LanDemoSession writer tasks previously checked the ending flag at entry and
then later called sendPacket, which only checked ready and stopped. In the
meantime endByUser or declineForTest could set ending=true while sockets
remained briefly open for graceful CLOSE/DECLINE. A previously eligible
regular PRESET, DEBUG_TEXT, APPROVE, CONFIRM or HEARTBEAT could therefore
reach the shared output lock after shutdown began. Likewise IOException
from an in-flight writer after local cancellation could be categorized as
an unexpected LINK_INTERRUPTED failure, and a queued local SENT callback
could survive while ending was true.

These are race windows visible in source, **not observed handset failures**.

## Change

- Add a pure test-only-session teardown policy (not an approval or trust gate)
  that rejects every ordinary packet when ending is true and explicitly
  permits only best-effort CLOSE/DECLINE terminal controls.
- Check the policy inside sendPacket's existing output lock immediately
  before accessing the live SSL socket and writing bytes. Socket closure
  and the independent 1.2-second forced-close timer are unchanged.
- Suppress ordinary progress/approval/SENT events and unexpected-link
  failure reports once voluntary teardown is observed. True unexpected
  failures while the session remains active still report normally.
- JVM tests cover every packet type, event state and a deterministically
  delayed queued-writer check; Python source guard checks the live call
  site rather than merely testing the pure policy.

## Limits

No locking strategy can revoke network bytes **already actively being written**
before the local end begins; forced close still bounds a stalled writer.
A report callback that passed a concurrent check moments before teardown
may still finish; the Compose screen's attempt and finished-state admission
checks remain important. This does not make the protocol production-secure
or add delivery receipts.

## Verification / release gates

- [ ] PR Android/JVM/Python/security/privacy/permissions/signer checks.
- [ ] Fresh merged-main Android CI.
- [ ] Owner-deferred two-handset cancellation amid active TLS writes and
  reader callbacks, backgrounding, QR and network-loss tests.
- [ ] M4-UI07 manual emulator screenshot run and independent protocol
  security assessment are still unexecuted.

No identity, TLS cipher, QR, socket transport, capabilities, permission,
persistent message history, signer or Android build configuration changed.
Rollback by reverting this PR.
