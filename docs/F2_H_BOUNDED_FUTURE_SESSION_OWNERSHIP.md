# F2-H — bounded one-shot Bluetooth resource ownership (future-only)

**Date:** November 8, 2026. **Parent:** F2-G PR #100, merged main
`551593b25695b31dc28d85e59b70ea9103440284` with successful PR
and fresh merged-main Android CI.

## Real user outcome still targeted

The Eden Comms core goal is two-person offline
**Connect → Communicate → Disconnect**, using LAN, Bluetooth Classic
and Wi-Fi Direct typed text, followed by optional foreground PTT.
F2-G demonstrated *real JVM TLS 1.3 encryption* of a proposed
Bluetooth TEXT/ACK frame between two different, pinned test identities,
but did **not** retain a real Android RFCOMM socket after approval.
F2-H tests the **lifecycle of owning and closing one future
post-approval link** without granting any messaging rights.

## Actual source changes

### Repair existing F2-C lifetime policy

The future-only `DebugBluetoothContinuationBudget` checks an at-most
90-second default, at-most 120-second upper bound and finite deadline
using caller-supplied elapsed time. A discovered rollback gap allowed
`begin(500) → observe(560) → observe(550)`: the last 550 remained
after the original 500 start and was previously accepted. F2-H records
`lastObservedMs` and **terminates permanently on any backward
observation**, not merely one before the original start. An accepted
observation updates the last monotonic time. The real debug Bluetooth
socket continues to use its separate **45-second** cap.

### One-owner resource lifecycle rehearsal

`DebugBluetoothFutureOwnedSession` (debug source only) is a one-shot
state machine: `NEW → OBSERVING → CLOSED`. It accepts one
`Closeable` resource for one positive generation, finite elapsed
clock and all F2-C observation flags. Presenting a distinct resource
transfers closure responsibility even if admission fails. A duplicate
attempt cannot replace or terminate the active resource, including a
duplicate start with that same object.

The owner is terminal and closes its resource on any of:
- invalid first admission (missing observations, clock or generation);
- expiry, backward clock, foreground loss or Bluetooth CONNECT loss
  on a matching active observation;
- explicit matching-generation user stop or owner-level close.

Wrong-generation late callbacks do **not** tear down the current
resource. Termination is synchronized so competing stop/deadline/
background callbacks take ownership of the `Closeable` at most
once; resource closure happens outside the policy lock. If resource
closure throws, the session stays closed rather than silently rearming.
Close does not promise the underlying OS accepted a clean shutdown:
the live owner, when independently reviewed, must report any such
operating-system failure.

**Critically:** observation booleans are *claims* passed by a test,
not cryptographic proof of a peer, and neither an F2-E completion
receipt nor construction of this owner grants authority to read/write
a user message. The owner has **no text, ACK, TLS, Android radio or
socket API**, identity memory or persistence.

### Integrated real-JVM TLS resource reclamation test

The existing F2-D/F2-G paired-engine test fixture establishes two
separate pinned TLS 1.3 engines over synthetic in-memory pipes. It
passes the future owner a `Closeable` composite covering both
encrypted test labs, both ephemeral message ledgers and the paired
pipes. The test sends one *encrypted* text successfully, then advances
the synthetic monotonic clock exactly to the deadline. It verifies:
1. the owner stops and closes;
2. pending IDs are cleared (no phantom peer ACK);
3. further encrypted lab writes fail;
4. the underlying stream is closed and cannot be written; and
5. duplicate teardown cannot revive the session.

Additional JVM tests cover missing human-confirmation observations,
stale role/generation callbacks, duplicate/rejected resource admission,
expiry, rollback after prior valid observations, permission loss,
backgrounding, user cancel, repeated owner close, synthetic close
failure and four concurrent teardown triggers. Source/static tests
protect this boundary.

## Hard limitation and gates

This is **NOT** the real Android Bluetooth session owner. The live
`DebugRfcommTlsProbe` and `DebugRfcommLinkController` do **not**
reference either the future budget or the future resource owner.
The live debug diagnostic still closes its socket after two human
approvals and two final encrypted confirmations. Its F2-E receipt is
deliberately non-authorizing. There is no new Bluetooth text UI,
voice, permission, saved trust, test signing, package/version or
production release capability.

- [ ] PR Android/JVM/Python/permissions/privacy/security CI succeeds
- [ ] New merged-main CI succeeds
- [ ] Independent review of TLS/RFCOMM record semantics, transcript/
      identity binding, human consent, deadlines and post-consent
      state ownership **before retaining a live radio socket**
- [ ] Real two-phone Android 12+ OEM/Conscrypt/Keystore/RFCOMM
      negative/positive tests, including close and permission loss
- [ ] Then: separately reviewed live-owner integration, encrypted
      frame/ACK path and actual chat UI. No plaintext fallback.
- [ ] Complete Wi-Fi Direct QR-bound transport, LAN-first remote PTT,
      optional encrypted retention and durable same-signer test APK
      via the [canonical F0–F7 roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md).

**Rollback:** revert F2-H PR. No on-device migration exists.

Related: [F2-C policy](F2_C_PROVIDER_READINESS_AND_CONTINUATION_BUDGET.md),
[F2-G encrypted engine lab](F2_G_PAIRED_TLS_ENCRYPTED_TEXT_ACK_LAB.md).
