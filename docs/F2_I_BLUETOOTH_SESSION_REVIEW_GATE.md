# F2-I — identity-bound future handoff policy and independent review gate

**Date:** November 8, 2026. **Parent:** F2-H PR #101, merged commit
`7b66ad4297ae64b0651a818642090a58e65a1588`; its PR and fresh
merged-main Android checks passed. This is the next slice in the
[canonical F0–F7 roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md).

## What was accomplished, and what was NOT

F2-F introduced strict debug-only 16–528-byte TEXT/ACK frames and finite
message ledgers. F2-G passed them through **two genuine pinned JVM TLS 1.3
engines** in both directions; F2-H introduced one-use future resource
ownership, tested pipe closure and fixed elapsed-clock rollback. The
remaining issue is not the bytes or unit-test encryption: a live,
post-consent **Android Bluetooth** session needs identity-bound,
non-reusable ownership of the same socket/TLS engine under a single
lifecycle and independently validated protocol.

F2-I makes the future-only policy more precise:
- `DebugBluetoothFutureSessionContext` holds copied 32-byte **peer
  certificate pin**, copied 32-byte **transcript commitment**, and
  host/join role. Invalid/zero-sized inputs fail. Public-size bytes
  are compared with `MessageDigest.isEqual`, and internal buffers
  are cleared at close. Mutating/closing the caller's input cannot
  rebind an already admitted future session.
- `DebugBluetoothFutureOwnedSession` still owns a **test Closeable**,
  not an Android Bluetooth socket, and still has **no send/read** API.
  It now captures an independent context on admission and requires a
  matching role, peer pin and commitment at **every** observation.
  A mismatch on the current generation terminates its lifetime budget,
  closes its resource and clears context. Stale generations do not
  terminate the current attempt.
- Focused JVM tests reject changed role/peer/commitment, malformed or
  zero context, closed context, input-array mutation, and mismatching
  current attempts; older expiry/permission/background/concurrent close
  tests and F2-H paired TLS expiry tests still run. Python/shell
  regression guards forbid wiring the future context into the live
  RFCOMM diagnostic or release code.

**Crucial:** this context is **NOT cryptographic proof**. Any test caller
can construct a context from arbitrary public bytes. Matching it does
not establish QR possession, TLS authenticity, actual human approval,
RFCOMM identity or a right to send a single message. The F2-E receipt
is diagnostic, non-authorizing and must never become a send token.
The live Bluetooth probe still closes its existing 45-second test
socket immediately after successful consent.

## Reviewer source map — actual live evidence and stop boundary

| Required trust condition | Current live source | Independent review question |
| --- | --- | --- |
| QR role, expected public peer pin and expiry | `DebugBluetoothTlsProbeInput.kt` | Can stale, substituted or reflected QR inputs cross a role or peer boundary? Verify clock and nonces |
| Same live RFCOMM socket, mutually pinned TLS 1.3 | `DebugRfcommTlsProbe.kt`, `DebugTlsEngineHandshake.kt`, `PinnedTlsPeer.kt` | Does Android 12+ **Conscrypt** expose matching exporter/pin/handshake behavior across devices? |
| Fresh QR challenge proof, transcript commitment | `SessionProofExchange.kt`, `DebugApprovalCommitment.kt`, `DebugBluetoothCommitmentFrame.kt` | Are both peers bound to the same roles, pins, challenges and TLS exporter without reflection/replay? |
| Two separate human approvals, then two encrypted CONFIRM frames | `DebugBluetoothConsentExchange.kt`, `DebugBluetoothConsentFrame.kt` | Can timeout, decline, cancellation, duplicate or unexpected control frames ever result in an accepted session? |
| Consistent active attempt token, foreground, permission | `DebugRfcommLinkController.kt`, `DebugRfcommAttemptGate.kt` | Can a late callback, background change, permission revocation, or stale attempt resurrect a link? |
| Strict TLS record/SSLEngine bounds | `DebugTlsControlTransport.kt`, `DebugTlsRecordBoundary.kt`, `DebugTlsProviderReadiness.kt` | Are exact records, tickets, post-handshake states and partial reads/writes handled safely on OEM providers? |
| Proposed encrypted frame/ACK transport | `DebugBluetoothTextFrameCodec.kt`, `DebugBluetoothFutureTlsTextLab.kt`, `DebugBluetoothTextLedger` | How will concurrent reads/writes, ACK loss/replay, strict sequence, timeout and closing be serialized? |
| Future bounded owner and identity correlation | `DebugBluetoothContinuationBudget.kt`, `DebugBluetoothFutureOwnedSession.kt`, `DebugBluetoothFutureSessionContext.kt` | Who owns the **same** engine, RFCOMM socket, cancellation timer, peer pin and verified transcript after consent? |

## Blocking engineering questions before any live retention

1. **Engine handoff:** currently `DebugRfcommTlsProbe.run()` closes
   its `SSLEngine` outbound and wipes the binding, exporter and
   commitment in `finally`; `DebugRfcommLinkController.stop()`
   immediately closes the `BluetoothSocket`. A live design needs a
   separately reviewed, explicit transfer of ownership of the **same
   live RFCOMM socket** and TLS state, *after* two encrypted CONFIRM
   records. Simply skipping `stop()` or forwarding F2-E completion
   must be rejected.
2. **Single attempt authority:** bind the exact active controller
   token/role and actual pinned peer/commitment **at the same time**;
   never trust the Bluetooth radio's name, MAC, bond, an arbitrary
   constructed F2-I context, or a user-interface timer.
3. **Concurrency and closure:** one bounded reader/writer and one
   socket owner, with foreground/permission/radio loss, user Back/End,
   cancellation, rejection and elapsed deadline all terminating
   transport and clearing session-only text. No late callback, stale
   generation or plaintext fallback may send.
4. **Data protocol:** audit exact TEXT/ACK envelope sizes, strict
   UTF-8, IDs, malformed-frame handling, empty/duplicate/unknown ACK,
   early ACK, loss, post-handshake TLS-only records and treatment of
   incomplete application frames. Never call a TLS write a peer ACK
   or treat peer ACK as a read receipt.
5. **No false persistence:** no saved trust by default, no history,
   key/exporter leaks, raw QR logs, Bluetooth identities or actual
   message contents in issue/CI artifacts. Avoid unreviewed PTT
   and Wi-Fi Direct integration.

## Physical Android interoperability test (NOT YET RUN)

On **two Android 12+ physical phones** with Bluetooth enabled and
Wi-Fi/cellular disabled: test discover/host/join, opposite QR pins,
fresh reciprocal nonces, actual pinning and TLS exporter agreement,
two independent Approve/Decline actions, two encrypted CONFIRM
records and diagnostic socket closure. Negative cases: incorrect/
expired QR, wrong Bluetooth target, duplicate approval/CONFIRM,
mutual timeout, app background, system permission revocation,
radio-off, interrupted records, malformed ciphertext, restart while
previous callback is pending. Capture only coarse phase/count codes
and public build version/signer evidence. Test connection closure and
restarting with a fresh QR. **Do not enable Bluetooth text** for this
diagnostic session or assume a JVM pass establishes Android behavior.

## Independent security review handoff — unsigned

Before a separate PR changes the live Bluetooth socket lifetime or
adds a chat composer, an **independent security review** must examine
the actual Kotlin live protocol and proposed owned-session/record
adapter. Require documented findings and sign-off for peer pinning,
QR/challenge/exporter transcript, consent state machine, session
handoff, timeout/cancel, message framing/ACK and no raw fallback.
Physical Android Conscrypt/RFCOMM test evidence is also required.
Neither review nor device QA was performed by this PR. No claim of
secure production communications is authorized.

- [ ] F2-I PR Android/Kotlin/Python/security/packaging CI green
- [ ] Fresh merged-main Android CI green
- [ ] Independent expert signs off on protocol and same-channel owner
- [ ] Two-phone Android/OEM test matrix recorded without secrets
- [ ] Separate reviewed debug-only live Bluetooth chat integration PR
- [ ] F3 Wi-Fi Direct, F4 LAN-first remote PTT, F5 opt-in privacy,
      F6 durable signed upgrades and F7 release remain open

**Rollback:** revert F2-I; no signing identity, Android permissions,
app data, Bluetooth behavior, migration or release capability changed.
