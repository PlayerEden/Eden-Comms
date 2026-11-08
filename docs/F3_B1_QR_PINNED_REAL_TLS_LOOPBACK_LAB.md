# F3-B1 — three Wi-Fi Direct QRs → real mutually pinned TCP TLS 1.3 (JVM lab)

**Date:** November 8, 2026. **Parent:** F3-A PR #103, merged into
`main` as `4be138f1bd3311eebe8a73d7f3b3883391d9110a`, with
passing fresh merged-main Android CI.

## Why we built this

The target remains a two-person, offline **Connect → Communicate →
Disconnect** experience over LAN, Bluetooth Classic and Wi-Fi Direct,
with optional authenticated remote foreground PTT. F3-A reached real
application TCP handshakes on the Android P2P debug path but **sent
no data and did not authenticate its endpoint**.

F3-B1 addresses the next narrow integration question: does the
existing three-QR public certificate identity exchange provide the
exact expected peer pin required by Eden Comms' existing TLS 1.3
mutual-authentication code over a **real TCP connection**?

**Yes, in JVM loopback source tests only.** A new future-only lab
builds two TLS sockets (not simulated SSLEngines or in-memory pipes),
presents different short-lived certificates generated at test time,
validates exact QR-derived pins from opposite peers, and closes
both sockets after the TLS handshake. No user/application data flows,
and no connection escapes the test to become a chat session.

## Source and test evidence

- `DebugWifiDirectFutureQrTlsExpectation` validates host's
  current 180-second host QR and local identity pin, the joiner's
  separately scanned reply, and the fresh third acknowledgment QR.
  Its joiner path additionally checks the exact own public certificate
  pin and fresh transcript before accepting the host's expected pin.
  Host and joiner derive the same canonical, domain-separated 32-byte
  public transcript SHA-256; buffer copies clear on close.
- `DebugWifiDirectFutureTlsSocketLab` uses the existing production
  `Tls13PeerContext` (exact-pin `VerifiedPeerTrustManager`)
  and `PinnedTlsPeer.requirePinnedTls13` after both sides perform
  actual `SSLSocket.startHandshake()`. Server requires client
  authentication. Only IPv4 **loopback** is accepted in this test lab.
  Accept/connect/handshake have finite OS timeouts. All sockets and
  expected pin byte buffers are closed/cleared. It exposes no
  application-data read/write, message, voice or session owner.
- `DebugWifiDirectQrPinnedTlsSocketTest` generates two distinct EC
  certificates in a temporary directory by JVM `keytool`, performs
  all three QR steps on the two logical endpoints, starts the real
  TCP listener and connector concurrently, and verifies that both
  sides mutually authenticate TLS 1.3 with the opposite pin.
- Negative tests reject a complete but **wrong QR host fingerprint**
  when the real host TLS certificate is different; corrupted third
  QR transcript; expired host QR; wrong local public identity; wrong
  role; a closed expectation; and a request to use a real/private
  radio address rather than lab loopback.
- Python and shell guards forbid new live Wi-Fi Direct controller,
  group-host or QR-activity references to this lab. Android's current
  F3-A **zero-data TCP probe is unchanged**. No new permissions,
  network destinations, saved trust, plaintext traffic, logs or
  release flags.

## Security and implementation limitation

This test shows **private-key possession for the exact scanned
public certificate on a separately established JVM TLS connection**.
It does **not** establish that the selected anonymous Wi-Fi Direct
radio target matches the person holding the QR, or that QR public
metadata could not be relayed. The host creates its third QR; that
alone cannot prove the joiner scanned it. There is still no
bilateral approval on the same live Wi-Fi Direct connection, no
QR nonce/exporter proof over that connection, no acceptance of
messages, no real Android Conscrypt compatibility test, and no
independent protocol/security review.

Do not wire `DebugWifiDirectFutureTlsSocketLab` into
`DebugWifiDirectTargetLinkController` or
`DebugWifiDirectGroupHostActivity` or expose its TLS sockets for
chat. The lab is **JVM loopback only**. Positive JVM tests never
justify calling Wi-Fi Direct authenticated or secure for real users.

## Next milestones

- [ ] F3-B1 PR Kotlin/JVM, Python/security and Android packaging CI.
- [ ] Fresh merged-main CI.
- [ ] F3-B2 reviewed and bounded *real P2P* TLS/QR exporter/session
  design, then physical two-phone P2P/OEM/Conscrypt tests.
- [ ] Bind both humans' explicit approval and final encrypted
  confirmation to the *same* active P2P TCP/TLS connection, with
  cancellation, foreground, group removal and timeout cleanup.
- [ ] Only after security/device gates: full encrypted TEXT/ACK
  transport and user chat UI (F3-C), then real remote PTT (F4).
- [ ] Stable internal-test signing and verified in-place APK updates
  as planned in F6. Do not change existing signing credentials.

**Rollback:** revert F3-B1 PR; no device user data, package, keys,
stored peer identity, Android permission or real Wi-Fi Direct
radio behavior changes.

See [canonical F0–F7 roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md)
and [F3-A real TCP reachability checkpoint](F3_A_WIFI_DIRECT_ZERO_DATA_TCP_REACHABILITY.md).
