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

## CI-discovered provider buffer bug and bounded debug-only refinement

The first two paired tests (runs `42450874742` and `42451110802`)
completed both real TLS 1.3 handshakes and opposite certificate pin
checks but failed on the first received control. They exposed a real
gap in isolated source/bounds tests. The diagnostic run `42451270030`
confirmed `DebugTlsProviderReadiness.requireExactControlResult`
rejected the first record because the engine produced **zero
application bytes** yet did not return the expected OK result.

Adding safe status/count diagnostics in run `42451405986` identified
the concrete JVM provider result: `BUFFER_OVERFLOW`,
`NOT_HANDSHAKING`, zero bytes consumed from a 1,033-byte encrypted
TLS record, and zero produced. The debug control adapter had allocated
`ByteBuffer.allocate(phase.bytes)` (134/38/39 bytes) instead of the
provider-advertised `engine.session.applicationBufferSize`. According
to the JSSE API, an engine can require a full advertised application
buffer to unwrap a TLS record **even when that record ultimately yields
no plaintext**.

The next paired test (run `42451561871`) verified that larger scratch
allocation eliminated `BUFFER_OVERFLOW`. It then found a second,
legitimate TLS 1.3 provider transition on the same zero-plaintext
record: `Status.OK`, `HandshakeStatus.FINISHED`, complete ciphertext
consumption (`1031/1031`), and `0` application bytes. Per JSSE,
`FINISHED` can be a transient result indicating completed handshake
processing. F2-D now permits that transient result **only** through a
separate no-plaintext TLS-only verifier. The normal encrypted protocol
control verifier still strictly requires `NOT_HANDSHAKING`; the
engine itself must also return to `NOT_HANDSHAKING` immediately after
processing this TLS-only record. The two-record prefix cap remains.
`NEED_TASK`, `NEED_WRAP`, `NEED_UNWRAP`, abnormal statuses,
partial ciphertext consumption and nonzero output remain rejected.

F2-D now allocates the TLS scratch buffer using the **bounded
provider-specified application capacity**; the pre-existing F2-C gate
rejects anything outside 1–32,768 bytes. Decrypted application data
is still delivered only if its length matches the exact protocol
control length, and the entire scratch buffer is cleared in a
`finally` block, including overflow/exception paths.

A related **debug-only** compatibility guard can consume **at most
two** complete encrypted TLS 1.3 records that `SSLEngine.unwrap`
authenticates and reports as `Status.OK`, `NOT_HANDSHAKING`, full
ciphertext consumed and **zero application bytes**. For example, TLS 1.3
NewSessionTicket may arrive after the initial handshake. Such records
are never delivered as control data, and a third zero-plaintext
record fails closed. Any nonzero, non-exact control frame, incomplete
TLS record, partial consumption, abnormal provider state or
half-closed engine is rejected. No plaintext bypass or fallback.

**Follow-up verification is mandatory:** test the real JVM engines
again, verify the original failure is fixed without adding a new
failure, then require green PR and merged-main CI. This work is not
proof of Android Conscrypt or physical Bluetooth compatibility,
nor authorization to retain a session or send messages.

## Important limitations

- A successful OpenJDK/SunJSSE test, including the bounded TLS-only skip, is **not** evidence that the Android
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

**Rollback:** revert F2-D PR. The debug TLS control adapter has only a bounded post-handshake record-tolerance change; no on-device messaging is enabled. There is no persistent data,
database, saved trust or APK update migration.

Primary context:
`docs/F2_C_PROVIDER_READINESS_AND_CONTINUATION_BUDGET.md`,
`docs/F2_B_TLS_RECORD_BOUNDARY_REVIEW.md`,
`docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md`.
