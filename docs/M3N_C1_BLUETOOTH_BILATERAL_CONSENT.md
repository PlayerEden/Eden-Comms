# Eden Comms M3n-C1 — Human Bluetooth approval and two final confirmations

**Checkpoint: November 6, 2026.** Debug-build-only developer experiment. **No real phones tested. No independent protocol/security assessment. No user Bluetooth chat or voice. Production CAN_* flags remain false.**

## What actually runs in this debug APK

This is the next step after M3n-B2's real pinned TLS 1.3 engine over an explicit Android 12+ Bluetooth RFCOMM socket, fresh two-QR public certificate pins/nonces, four encrypted QR challenge records, bilateral exporter comparison and final domain-separated transcript agreement.

Once **both devices** reach the final M3n-B2 transcript commitment, the existing short-lived RFCOMM socket no longer closes *before consent*. Instead, **each phone independently displays one explicit choice**:

**APPROVE THIS TEST SESSION** or **DECLINE AND CLOSE SESSION**.

Only an actually live, foreground, permission-granted socket with the correct session-token can request/show these controls. The session is limited to the same 45-second RFCOMM lifetime established in M3m. A separate per-socket decision latch awaits the local tap for up to 25 seconds. It never approves on its own.

### Fixed encrypted consent protocol, on the same exact TLS 1.3 connection

1. **Identity/channel proof first.** A person cannot approve merely because the Bluetooth scan found a signal or two QR codes were displayed. Before the approval buttons appear, both endpoints must complete pinned TLS 1.3 with mutual Keystore-backed certificates, fresh QR nonce/challenge proof, a two-way exporter agreement, and two commitment echoes in the M3n-B2 protocol.
2. **Human decision, one per phone.** Tapping APPROVE writes an encrypted, exact-commitment **APPROVE** frame. Tapping DECLINE writes a corresponding **DECLINE** frame, then terminates the diagnostic. No tap within 25 seconds is a rejection. All frames are 39 fixed bytes before TLS encryption, carrying the protocol magic/version, host/join role, APPROVE/DECLINE/CONFIRM type and the exact 32-byte session commitment.
3. **Both approvals observed.** Each side must actually receive a valid APPROVE from the other role, with the same session commitment, *after* sending its own. The reused `DebugApprovalGate` rejects duplicates, early confirmations, invalid transitions and any declined/closed attempt.
4. **Two final confirmations.** After **both** approvals, the protocol automatically sends one separate encrypted, same-session CONFIRM per phone, then validates a matching CONFIRM received from the other phone. Automatic final confirmation is a protocol acknowledgement, **not a second human tap**. Neither side reports fully confirmed until both its outgoing confirmation and incoming peer confirmation have been recorded by the gate.
5. **The socket closes on every outcome.** A correct four-event approval+confirmation gate produces terminal status **CONSENT_CONFIRMED**, announces only a diagnostic success and closes the Bluetooth socket. DECLINE or timed-out human consent yields **CONSENT_DECLINED** and closes it. Mismatched roles/commitment, early/duplicate controls, TLS failure, scan/radio/permission loss, backgrounding or the 45-second socket deadline also closes it. Both approvals remain session-specific and are never saved as contacts or automatic trust.

**Nothing in this implementation sends or receives chat text, attachments, audio or PTT.** No message composer, call screen, chat permissions, background reconnect, trust enrollment or production send capability is connected to `DebugApprovalGate.CONFIRMED`. The developer experiment always closes the socket, even on success.

## Security controls and limitations

- All new files `DebugBluetoothConsentDecision`, `DebugBluetoothConsentFrame`, `DebugBluetoothConsentExchange` are in **`app/src/debug` only**. Existing `app/src/main` `DebugApprovalGate` is reused; release capability flags and permissions are unchanged. The three Bluetooth permissions SCAN/CONNECT/ADVERTISE remain debug-manifest only, used after explicit user action; no location, contacts, mic or additional SDKs.
- QR public certificate fingerprints, QR nonces, TLS exporter and session commitment remain per-link temporary data. Consent frames travel only inside the already mutually pinned TLS channel via `DebugTlsControlTransport` and are compared using constant-time digest equality. The authenticated connection has no raw plaintext fallback.
- The TLS control transport has only five forward-only phases: **QR_PROOF → EXPORTER → COMMITMENT → APPROVAL → CONFIRM**. APPROVAL and CONFIRM are each exactly 39 bytes. A phase cannot move backwards, skip steps or run again on a terminated socket.
- The `DebugRfcommLinkController` stores the consent gate only for the currently active RFCOMM generation. Closing the activity, losing foreground/Bluetooth CONNECT permission, explicit Disconnect or deadline closes the gate, unblocks its waiting worker, closes the socket and rejects stale UI callbacks.
- The chosen Bluetooth signal still is not treated as a trusted contact; the cryptographic identity is the **exact QR-pinned TLS certificate**, not a device name/MAC or Android pairing/bond state.
- The custom TLS record adapter remains **unaudited** and has no physical two-phone interoperability evidence. Some Android TLS providers might fragment application frames or emit post-handshake traffic this intentionally restrictive prototype rejects. Source/unit/CI green is **not a security certification or a claim two phones connected**.
- Due to strict short session limits, human approval must occur promptly after both phones verify their link. Timing and UI accessibility need actual two-phone QA.

## Test plan and regression evidence

Kotlin deterministic tests:
- `DebugBluetoothConsentFrameTest`: three consent frame types, host/join roles, correct exact 39-byte framing, wrong/stale commitment, bad role/version/type/magic, zero/short commitment, truncation and distinct APPROVE vs CONFIRM.
- `DebugBluetoothConsentDecisionTest`: one explicit tap, no duplicate approval, DECLINE, waiting timeout, cancel overriding a previous tap, background closure waking a blocked worker, and rejection of unbounded waits.
- `DebugTlsControlTransportTest`: now **five** forward-only phases with terminal no-reuse, rejecting premature or backward approval/confirmation phases.
- Reused `DebugApprovalGateTest`, `DebugApprovalStateSpaceTest`, `DebugApprovalControlWireTest`, `DebugBluetoothCommitmentFrameTest` and M3n-B2 QR/exporter tests remain enabled.

Source and CI guards:
- `scripts/tests/test_m3n_c1_bluetooth_human_consent_boundary.py` requires exact TLS/QR/exporter/commitment completion **before** displaying Approve, two role-bound encrypted confirmations, explicit reject/timeout/cancel, fresh active connection gate, UI showing controls only on the live link and no Bluetooth chat.
- `scripts/check_security_review_boundary.sh` enforces debug-only source and restrictions against plaintext fallback, arbitrary user data, voice, persistence and production authorization.
- `assembleDebug`, JVM/Python tests, exact APK manifest permission inventory and fresh post-merge main CI must pass. **None of these substitutes for independent security review or actual phone testing.**

## Next after M3n-C1

**M3n-C2 — Bounded debug-only typed Bluetooth text, after review of the consent protocol and end-to-end device behavior.** Potentially use the same verified session and explicit approvals to permit tightly bounded transient plaintext inside TLS, with fail-closed message framing, delivery status and replay/timeout tests. Do not implement before the M3n-C1 protocol is audited for logic flaws and tested on real Android providers. The app's **production** capability flags must remain disabled until a separate security-release decision.

**Owner-deferred two-phone QA — when requested:** With two Android 12+ devices, test QRs, discovery, radio permissions, full pinned TLS/proof agreement, separate approvals on both, automatic two final encrypted confirms, one decline and remote observation, 25s no-tap timeout, wrong-commit/stale approval, background/rotation/permission loss, accessible controls and automatic socket closure. Expect **no** Bluetooth text or voice even if consent succeeds. Avoid collecting raw QR codes, MAC addresses, TLS exporter/keying material, chat content or diagnostic packet captures.

## Related work

[M3n-B2 encrypted QR and exporter agreement](M3N_B2_BLUETOOTH_ENCRYPTED_QR_AGREEMENT.md) · [Master core plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md) · [Bluetooth #59](https://github.com/PlayerEden/Eden-Comms/issues/59) · [Deferred physical QA #26](https://github.com/PlayerEden/Eden-Comms/issues/26) · [Independent review #42](https://github.com/PlayerEden/Eden-Comms/issues/42).
