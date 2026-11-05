# M1a — Security gate and pairing architecture checkpoint
Status: implemented **gate model only**, crypto/transport **not implemented** (2026-11-03).

## What changed
- `SessionSecurityGate` expresses an application-level **default deny** policy: discovery or handshake is not enough; the user must explicitly verify the binding on BOTH devices before app messages or microphone transmission can be enabled.
- Any identity mismatch, verification mismatch, security failure, or transport loss denies communication; no implicit trust carried over across a reconnect.
- Automated negative tests cover both-confirmation requirement, stale verification, wrong peer identity, disconnect and protocol failures.
- **Development APKs allow screenshots/screen sharing** to speed up visual feedback. Non-debuggable release builds still use `FLAG_SECURE`, subject to release readiness and accessibility review. Both reduce autofill/content-capture exposure, but no such setting protects against cameras, malicious keyboards, accessibility abuse, or compromised endpoints.
- CI inspects the built APK permissions; any unexpected network/Bluetooth/microphone/location/contacts permission request fails this pre-network build.
- Product still requests no communication permissions, does not establish an encrypted channel, and **cannot send messages**.

## Test/build cadence
- See [Development APK policy](TEST_BUILD_POLICY.md): rapid internal builds are not public releases and do not need full release audits for each minor UI change.
- Use only synthetic test content in debug builds; screenshot protection remains disabled during UI testing.

## Important limitation — gate model is not cryptography
The gate accepts events for conceptual handshake establishment and confirmation; those calls are not proof of authenticity. No developer may wire this gate to network sockets until a real audited cryptographic adapter authenticates peer identity, session transcript, transport framing and confirmation messages. An attacker controlling upstream input could otherwise forge those events. The release-level trust boundary must live in cryptography, **not** in UI state flags.

## M1b crypto research — decision pending
Possible starting point: established interactive Noise protocol handshake plus out-of-band fingerprint/short authentication string verification. The protocol framework is well-specified, but Java/Kotlin library implementations must be individually assessed for Android support, security audit evidence, maintenance, licensing, release authenticity, test vectors, invalid-input handling and key-lifecycle policies.

Alternatives to evaluate: TLS 1.3 with explicit mutual identity verification and pinned peer certificates. Neither option is approved yet. No hand-rolled handshake or cipher.

Crypto design review must resolve:
1. Ephemeral key agreement and forward secrecy vs long-term identity continuity.
2. The exact session-binding computation, authenticated QR/SAS display and verified local+remote acknowledgment.
3. MITM, replay, renegotiation, cross-transport switch and downgrade prevention.
4. Key storage through Android Keystore where appropriate, deletion/backup exclusions and secure-memory limitations.
5. Error handling, packet limits, version negotiations, timeouts, denial-of-service and peer metadata minimization.
6. Third-party dependency provenance, audit results and negative/fuzz test vectors.

## M1b acceptance criteria before real LAN messages
- Reviewed library/protocol integration with interop and negative tests.
- Distinct UI labels for *not connected*, *encrypted but not verified*, *verified* and *blocked*.
- Both devices actively verify the same cryptographically derived session binding before sending.
- No plaintext downgrade and no traffic to public internet services.
- Two real phones communicate with WAN disconnected, MITM/spoof tests, packet capture, protocol downgrade/failure tests.
- Security-review record plus reproducible APK, checksum and runtime manifest audit.

References:
- https://noiseprotocol.org/
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- https://mas.owasp.org/MASVS/
