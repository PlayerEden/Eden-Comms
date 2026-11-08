# Eden Comms — functional test-build completion targets

**Priority pivot, November 7, 2026.** The owner wants to test an app where
the intended controls do real work on paper, not merely pass unit tests.
The exact requested no-phone commitment remains: implement and automate
every safe preparatory part now, but do not claim physical interoperability.

## Functional readiness ladder

1. **Real local function:** Operates on one phone, even with all radios off.
   The D1 microphone/speaker loopback is a first example.
2. **Debug two-peer function:** Actually exchanges user test data across
   two phones with explicit identities and user consent, but not necessarily
   independently security reviewed or device-tested. LAN short text currently
   satisfies the source criterion, not hardware acceptance.
3. **Reviewed secure release function:** independently assessed, exercised on
   physical devices including negative tests, documented data handling,
   approved signing and release gates. None of the production communication
   capability flags is enabled yet.

**Remaining before a genuinely full-function debug demo:**

- Local Wi-Fi text: receipt-status design and exact post-approval behaviors.
- Bluetooth: authenticated live channel retained after consent for text;
  requires independent protocol review and real provider QA first.
- Wi-Fi Direct: bind radio identity to QR/TLS/approval, then text.
- Voice/PTT: local D1 hardware QA, hold/release UI for actual verified
  peer session, audio chunking/buffering/latency, lifecycle/perms and
  independent security/device review.
- Optional encrypted local history, expiry, and peer trust remain separate
  opt-in later-phase capabilities, not implicit parts of messaging.

For the first user testing session it is legitimate to ship a debug app
where only LAN text and local voice functionality are available, **but**
the test scope must be explicit. Do not call incomplete connectivity
functional just because a screen can open or radio groups form.

**No bypass:** All unverified transports must fail closed. No plaintext
fallback, trust inferred from Bluetooth bonds, unreviewed remote audio,
sensitive data testing, fake ACKs, or production flag flips. Do not add
unsafe transport code just to meet a feature-complete date. CI does not
substitute for a human security or device test.
