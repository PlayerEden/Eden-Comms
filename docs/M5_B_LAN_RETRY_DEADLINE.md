# M5-B — live-session LAN retry deadline and cancellation boundary

**November 7, 2026 — source-backed runtime timing fix.**

## Finding

The real debug LAN joiner (`LanDemoSession.connectTcp`) has a 120-second
TCP retry allowance while the other phone finishes reciprocal QR pairing.
Before this fix, even at elapsed 119,999 ms it began a new TCP connect with
the full 1,500 ms per-attempt timeout. That allowed an individual blocking
connection attempt to consume more time than remained in the advertised
retry window. This does not imply a past physical two-phone timeout failure.

## Fix

- Add pure `LanConnectRetryPolicy.attemptTimeoutMs(elapsedMs)` returning
  the smaller of 1,500 ms and the remaining 120-second retry budget.
- Return 0 when elapsed time is invalid or the budget is exhausted,
  and have `LanDemoSession.connectTcp` **throw** before calling
  `Socket.connect` in that case. Java treats Socket.connect(timeout=0)
  as **infinite blocking**; zero must never reach the socket.
- Sample monotonic `SystemClock.elapsedRealtime` immediately before each
  blocking TCP attempt. Existing IOException handling still closes and
  releases the raw socket, and TLS/certificate/QR/Wi-Fi failures retain
  their no-downgrade, no-silent-retry behavior.
- If retry mode is disabled, preserve the original 7-second single TCP
  connect timeout; no behavior change to a one-shot diagnostic.

## Verification / limits

- JVM unit tests verify 1,500 ms cap, boundary at 118,500, 119,999 and
  120,000 ms, invalid time and zero means stop.
- Python source regression asserts the **real** join path uses the bounded
  timeout and cannot accidentally pass zero to Socket.connect.
- [ ] PR Android/JVM, Python, security/permission/privacy and signer CI
- [ ] Fresh merged-main CI
- [ ] Owner two-device actual retry timing, interrupted Wi-Fi, captive/guest
  network and Android cancellation tests remain deferred.
- [ ] M4-UI07 manual emulator screenshot and independent protocol review
  still pending; neither is replaced by these source/JVM checks.

This bounds the individual blocking TCP **attempt** based on remaining time;
it is not a hard real-time guarantee for all scheduler and socket-creation
overhead or proof that a real handset completes connection within 120 s.

No API, authentication, approval, message payload, storage, TLS, crypto,
radio permission, signing, user interface or production capability change.
Rollback by reverting M5-B PR. No data migration.
