# M2c — QR session binding check inside pinned TLS (development test only)

Status: implemented as a distinct stacked draft PR on November 4, 2026. Physical QR/session proof QA **NOT YET DONE**.

## Starting evidence

The tester shared a screenshot of one phone reporting successful mutually pinned TLS 1.3 in v0.2.3. Neither the second phone's terminal state nor no-WAN/negative tests have been documented. Existing success is a handshake-only, one-shot socket, not a chat session.

## Why add one more small test

Two in-person QR scans pin the exact host and joiner leaf certificates and carry two 128-bit QR nonces. TLS 1.3 authenticates each pinned certificate. Before any chat authorization, test whether both endpoints agree on the same QR transcript and can complete a fresh two-way exchange over that specific TLS channel. This reduces the risk of accidentally treating any earlier authenticated TLS connection as permission to send. It is not a substitute for a reviewed application confirmation protocol.

## v0.2.4 behavior

1. Host and Join perform the **same two in-person QR scans** as v0.2.3.
2. Host listens first, Join connects within 60 seconds; TLS 1.3 must successfully authenticate both exact pinned certificates.
3. After TLS/pin checks, Host sends a fixed-size versioned proof record containing the two public certificate fingerprints, both QR nonces and a fresh connection challenge.
4. Join validates the whole record and replies with its own fresh challenge while echoing Host's challenge. Host validates that response.
5. Host acknowledges Join's challenge; Join verifies and echoes Host's challenge in the final record. Host verifies. Only then does **the test** say QR session agreement passed.
6. All records are sent **inside mutually pinned TLS**. This uses the existing TLS provider, not new encryption, custom signing or trust-all authentication. Each record is fixed-size (134 bytes) with bounded reads, version/type checks and constant-time comparisons of nonces/pins.
7. The existing **Advanced Settings / developer-only manual LAN probe** remains a handshake-only diagnostic for older tester flows; it cannot accidentally claim QR-session proof without both scanned QR nonces. Both probe variants leave all messaging capabilities disabled.
8. Socket closes immediately. **No text, voice, conversation data, peer trust store, contacts, telemetry or remote server requests.** `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` remain false. `SessionSecurityGate` does not get marked verified by this test.

This check cannot prove resistance to a compromised endpoint, and it is not a reviewed end-to-end messaging protocol. Explicit user approval and transcript-bound confirmation for real sessions must be designed and independently reviewed before any message I/O.

## QA requirements

- CI: full JVM tests, build, permission audit, APK/SHA-256; codec success + mismatch tests (both QR nonces and peer fingerprints), truncated record failure and failure-closed behavior.
- Physical: both phones must display `Mutual TLS 1.3 + QR session check passed` for one new pairing; capture safe redacted screenshots from each.
- Repeat with router WAN disconnected and cellular data off. Verify there is no DNS/cloud rendezvous in app code.
- Repeat stale QR, wrong certificate pin, mismatched/nonmatching QR transcript, expired host QR, cancelled scanner/listener, Wi-Fi drop during proof, join timeout, and different versions (v0.2.3 vs v0.2.4 should fail closed).
- Verify error states are privacy-safe; screenshots must not include active QR, network addresses, pins or identifying device values.
- Validate on Android 13 Galaxy A32 5G plus other Android handset; camera, large text, screen rotation, and small displays.
- An independent security review and robust connection lifecycle/explicit bilateral app authorization remain **hard blockers** for real messages.

## Handoff

Once test evidence passes, next work is an actual long-lived authenticated session adapter: bind peer identity, consent and live socket lifetime into `SessionSecurityGate`; on timeout, peer mismatch, Wi-Fi change or app cancellation revoke privileges and close immediately. Implement the message layer separately, *only* after these gates pass. Do not expose raw exceptions or untrusted payloads in UI/logs.
