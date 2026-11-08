# F3-B2 — verify QR nonce challenges and full transcript on the same real TLS socket

**Date:** November 8, 2026. **Parent:** F3-B1 PR #104, merged-main
`36da69b6b91042106135b3cc99949eb5d7b22e48`, green PR and
fresh merged-main Android CI.

## Actual work toward the core application

Eden Comms targets offline two-person **Connect → Communicate →
Disconnect**, across local Wi-Fi LAN, Bluetooth Classic, and Wi-Fi
Direct, with later foreground remote push-to-talk. F3-A established
a debug-only real Wi-Fi Direct group TCP reachability experiment.
F3-B1 established that canonical three-QR public certificate pins can
mutually authenticate **two real TCP TLS 1.3 sockets on JVM loopback**.

F3-B2 tests what happens **after** that TLS handshake, on the
**same socket**. A correct certificate alone must not accept stale or
different QR pairing challenges. This new debug-only JVM lab exchanges
existing bounded QR nonce/role challenge records, then two final
full-transcript commitments, all encrypted under the already pinned
TLS session. It closes BOTH sockets immediately afterward.

**No user messages, audio, trust enrollment, real radio TLS or human
approval are activated by this change.** TLS control records are
application-protocol bytes, but **not chat/message payloads**. They
never create a reusable or authorized chat channel.

## Protocol test sequence

1. Each independently validated three-QR expectation (F3-B1) retains
   safe, copied full host/join certificate pins, both 16-byte QR
   nonces, its assigned role, and its canonical 32-byte transcript.
   These are held in RAM and cleared when the expectation closes.
2. Open separate real JVM TCP listener and connector on **IPv4
   loopback only** with finite 4-second accept, 2-second connect and
   4-second socket I/O timeouts. Both endpoints must complete mutual
   TLS 1.3 and verify the exact out-of-band certificate fingerprint
   using existing `Tls13PeerContext` and `PinnedTlsPeer`.
3. Over the exact accepted TLS socket, call existing
   `SessionProofExchange.runHost/runJoin` with the same two
   fingerprint pins and two QR nonces. Its four fixed role-ordered
   records prove that both authenticated endpoints know those QR
   fields **and** answer freshly exchanged host/join 16-byte
   challenges on this TLS connection.
4. Derive a domain-separated SHA-256 of the **complete canonical QR
   transcript**, ordered pins/nonces, and both fresh challenges.
   Exchange two fixed 38-byte encrypted role-ordered `ECWC`
   commitment frames and compare the full 32-byte value. A mismatch
   fails closed. This is a *public comparison*, NOT a keyed MAC,
   TLS exporter, human approval or independent authorization token.
5. Close TLS client/server sockets, listener and temporary buffers
   in `finally`/`use`, whether successful or not. No socket
   escapes the test API; no plaintext fallback, user-data read/write,
   retained session, file or QR log exists.

## Evidence

The F3-B2 extensions to
`DebugWifiDirectQrPinnedTlsSocketTest` use the existing two
different ephemeral keytool-generated test identities and real
`SSLSocket` endpoints. They cover:
- Successful full three-QR → mutual TLS → encrypted nonce challenges
  → encrypted full transcript agreement, repeated on **two new real
  connections** with fresh per-connection proof challenges.
- QR nonce mismatch while *both certificate fingerprints are
  unchanged*: mutual TLS can succeed but the encrypted challenge
  proof fails on both sides.
- Identical public certificates and QR nonces, but a different valid
  QR issue timestamp: QR challenge proof can succeed, but the
  **full canonical transcript commitment** rejects the mismatch.
- A private/non-loopback Wi-Fi Direct network address cannot be used
  in the JVM-only lab; wrong host/join role and closed QR context
  also fail before opening a socket.

Separate deterministic
`DebugWifiDirectFutureQrCommitmentTest` checks fixed-size role
ordering, transcript and challenge sensitivity, both-pipe
agreement, incorrect role, altered commitment, incomplete record,
and all-zero/invalid digest rejection. Source/Python and shell
checks keep this protocol isolated from the current Android Wi-Fi
Direct radio controller and release configuration.

## Still blocking real communications

These are **not** completed by this PR:
- The Android Wi-Fi Direct group/link controller still performs only
  F3-A's **unverified, zero-user-data TCP test**, not TLS.
- No proof yet that the actual selected P2P device is the person
  presenting the three QR codes; public QR metadata can be relayed.
- No TLS exporter comparison / RFC 9266 channel-binding
  interchange on the real Wi-Fi Direct TCP link.
- No two **independent human approvals**, two final encrypted CONFIRM
  records, secure ownership/cleanup of a *live* Android P2P socket,
  or production-safe user text and peer-app ACK.
- **Independent security review and physical two-phone OEM/Conscrypt
  acceptance** have NOT happened and are required before using the
  real Wi-Fi Direct socket to transmit user content.
- Signed APK in-place update, background/permission/radio-loss
  scenarios, remote PTT, and launch acceptance are still open.

## Next

- [ ] PR Android/JVM/Python/security/APK CI green.
- [ ] Fresh merged-main CI green.
- [ ] F3-B3: independent protocol review and physical Android
      Wi-Fi Direct TLS/exporter/QR identity test matrix; only after
      approval, integrate the authenticated **live** P2P session.
- [ ] F3-C: reviewed and physically validated text/ACK on approved
      session; F4 remote audio/PTT afterward.

**Rollback:** revert F3-B2 PR. No persistent peer history, Android
permissions, on-device keys, signing, package name or current live
radio behavior change.

See [full-function roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md)
and [F3-B1 QR-pinned real TLS evidence](F3_B1_QR_PINNED_REAL_TLS_LOOPBACK_LAB.md).
