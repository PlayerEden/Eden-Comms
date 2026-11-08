# F2-D — paired JVM TLS 1.3 engine interoperability lab

**Date:** November 8, 2026 (UTC). **Parent:** merged F2-C main
`c4d4270c1c17a4b8af8ccb0dec7f93144a79ed33`.

## Goal

The F2 Bluetooth roadmap has implementation-level security constraints
but has **never had a real, two-endpoint TLS 1.3 `SSLEngine` integration
test**. Earlier tests validated the record envelope, exact buffer/handshake
state and intentional single-endpoint failures separately. F2-D adds
bounded **JVM provider interoperability evidence**, not real radios or
an independent protocol assessment.

## Harness (JVM unit tests only)

`DebugPairedTlsEngineInteropTest` belongs to
`app/src/test/java`, **never** `app/src/main` or `app/src/debug`.
Two different short-lived, self-signed EC X.509 identities are generated
by the CI JDK's own `keytool` into a temporary test directory.
The test loads them through `KeyStore` and `KeyManagerFactory`,
computes separate full SHA-256 leaf-certificate pins, then constructs
the **real project** `Tls13PeerContext` and the actual debug
`DebugTlsEngineHandshake`, `DebugTlsProviderReadiness` and
`DebugTlsControlTransport`. No fake trust-all manager, single
shared identity, hardcoded repository private keys, or cryptographic
bypass is introduced.

Two dedicated daemon worker threads and in-memory `PipedInputStream`/
`PipedOutputStream` pairs simulate the bidirectional byte streams of an
RFCOMM connection. Each has a 65,536-byte bounded pipe buffer. Handshakes
must finish within 15 seconds and both sides must verify the other
certificate against a **pre-provisioned, opposite-endpoint pin**.

The positive test then walks through all five existing encrypted control
lengths—QR proof (134), exporter (38), commitment (38), approval (39),
confirmation (39)—in both directions on the real established engines,
including exact frame length and byte-for-byte recovered content.
The control bytes are deliberately **artificial fixture data**, not
meaningful QR/exporter/consent credentials. This does not exercise the
real QR session-protocol parser or grant authorization to send user text.

The negative test changes one expected out-of-band certificate pin.
The joining engine must reject the peer during handshake; the test
closes both pipes and joins or interrupts the other simulated endpoint.
The harness also checks that controls cannot be used after clearing
the adapter. All key files, sockets/pipes, executor threads and test-only
working material are cleaned up after tests. Java's memory model does
not guarantee removing every historical copy of a key.

## Important limitations

- A successful OpenJDK/SunJSSE test is **not** evidence that the Android
  Conscrypt provider, `android.net.ssl.SSLEngines` exporter, a specific
  Bluetooth stack, RFCOMM timeouts, Android Keystore-backed identities
  or physical phones behave the same way. Android 12+ two-phone test
  remains owner-deferred.
- A failing real-provider integration test is valuable. We must not
  weaken certificate pinning, TLS 1.3, encrypted record count checks
  or foreground consent to make it green.
- A strict one-record-per-frame diagnostic might reject valid
  TLS-provider fragmentation or TLS 1.3 post-handshake traffic.
  This indicates an interoperability risk and needs explicit review
  before a general-purpose authenticated stream adapter can exist.
- `keytool` is **test infrastructure only**; ephemeral private keys are
  never committed and no runtime Android permission, signing key or
  release package identity is changed.
- This is not a production cryptography review, threat-model
  completion, or permission to retain a live Bluetooth session.

## Verification and next steps

- [ ] Kotlin paired-provider tests on CI
- [ ] Existing Kotlin and Python regressions
- [ ] Permission, isolated-source and security-boundary audit
- [ ] Pull-request Android packaging and test-only signer smoke
- [ ] Fresh merged-main CI
- [ ] **Blocking for real Bluetooth chat:** independent TLS/RFCOMM
      protocol/security review and two-device Android OEM/Conscrypt/
      Keystore/QR/exporter/consent tests (not performed)

Follow-on F2-E should use actual test results to decide whether to
refactor the diagnostic TLS stream handling, but should still leave
Bluetooth user data and release capabilities disabled until the gates
above are satisfied. The earlier F2-C future observation budget is
not a cryptographic proof and is still unwired.

**Rollback:** revert F2-D PR. There is no on-device behavior,
database, saved trust or APK update migration.

Primary context:
`docs/F2_C_PROVIDER_READINESS_AND_CONTINUATION_BUDGET.md`,
`docs/F2_B_TLS_RECORD_BOUNDARY_REVIEW.md`,
`docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md`.
