# Eden Comms M3n-B1 — Pinned TLS 1.3 engine over a debug RFCOMM socket

**Checkpoint:** November 6, 2026. **Debug-only source, experimental implementation.**
**Physical devices tested:** none. **Independent protocol security review:** not performed.
**Bluetooth messages/audio:** disabled. **Production flags:** all false.

## New actual capability, not merely a design

After M3k radio inquiry, M3l selection, M3m bounded secure RFCOMM host/join and M3n-A reciprocal, in-person QR preparation, the Android debug Bluetooth test now **attempts a real mutual TLS 1.3 handshake over the BluetoothSocket byte streams** using Android's `SSLEngine`. This is a one-shot diagnostic that closes the socket after success or failure. It neither negotiates app messages nor grants authorization.

- `Tls13PeerContext.unconnectedEngine(client)` reuses the existing Android Keystore-backed local private-key manager and **exact SHA-256 certificate-pin trust manager** from Wi-Fi, explicitly restricts to `TLSv1.3`, sets server `needClientAuth=true` and has no trust-all fallback.
- `DebugBluetoothTlsProbeInput` captures the full expected remote fingerprint from the freshly prepared, host/join-role-specific `ECBH1`/`ECBJ1` QR transcript. It is copied in RAM and zeroed after the attempt; no Bluetooth name/MAC/IP is used as identity. The private key stays in Android Keystore.
- `DebugTlsEngineHandshake` is the **only** low-level TLS record transport: it handles `SSLEngine.wrap` / `unwrap`, delegated tasks and bounded `NEED_UNWRAP_AGAIN` progress over the existing RFCOMM `InputStream`/`OutputStream`. It admits at most 18,432 bytes per TLS record, 32,768 bytes per provider buffer and 256 processing steps; rejects invalid TLS record headers, oversize/partial records, provider overflow, unexpected decrypted application plaintext, stalled reads and incomplete handshakes. No plaintext fallback or app message methods exist. The socket's existing 45-second open cap provides interruption through forced close. This is a **handshake driver**, not a general-purpose audited TLS transport library.
- `DebugRfcommTlsProbe` confirms the completed TLS protocol is exactly 1.3 and the remote TLS leaf certificate SHA-256 matches the complete fingerprint from in-person QR (`PinnedTlsPeer.requirePinnedTls13`). It requires an Android 12+ supported `SSLEngine` and a nonzero RFC-9266-style `EXPORTER-Channel-Binding` export; the exporter bytes are cleared and never logged, used for encryption, sent in clear, or displayed.
- `DebugRfcommLinkController` calls that probe directly on the existing bounded RFCOMM worker after an OS link opens, with exact host/join role checks and immediate socket teardown. Status **TLS_PROBE_PASSED** is terminal: the socket is CLOSED even when TLS certificate checking passes. Loss of foreground, permission, timeout or a stale worker token fails closed; cancellation closes sockets and unblocks pending operations.
- The Bluetooth UI clearly says a pinned TLS certificate test is still not a full Eden Comms verified conversation. The person still uses two fresh QRs before trying a socket. Existing LAN QR/TLS chat is untouched, as are the original release permissions.

## Important security properties still NOT established

A local pinned TLS handshake can validate the matching certificate identity over this particular RF socket, **if it succeeds on actual Android hardware**. However, these milestones have **NOT** done the next critical steps:

1. **QR nonce/session binding over the encrypted stream.** The `SessionProofBinding` from M3n-A is prepared but no four-record challenge/proof is exchanged through the new TLS engine. TLS pinned certificate identity alone does not prove that both peers agreed on the particular QR *attempt*, ordered roles, both QR nonces, or handshake transcript. A device whose certificate matches a reused/compromised public pin must not be mistaken for a fresh approved session.
2. **Peer exporter agreement.** The Android exporter API is checked for local availability and nonzero output. The exported value is NOT compared with a value cryptographically committed by the remote side over the same verified TLS channel. A local export does not prove shared agreement and does not authorize sending.
3. **Mutual human approval and final confirmations.** Neither person sends a debug APPROVE/CONFIRM over this RFCOMM TLS engine. The `ProductionSessionAuthorization` policy remains unconnected; Bluetooth text, voice, delivery receipts and contacts remain unavailable.
4. **Independent audit and real Android hardware.** Android provider behavior, mutual client cert negotiation, startup/timeout, QR camera and RFCOMM link have not been validated on a phone. Unit tests and static checks **cannot certify** the cryptographic implementation. A qualified security review is required before release capability changes.

M3n-B1 deliberately closes the socket immediately after the TLS/exporter diagnostic; no app packet streams, generic `SSLSocket` wrapper, or text composer exist in this Bluetooth path. Do not use or describe this debug test as a private messenger.

## Automated test and code guards

- Kotlin: `DebugTlsEngineHandshakeTest` verifies bounded TLS record framing, fragmented reads, truncated/oversized/corrupt versions and a genuine TLS 1.3 engine failing without a peer. `Tls13PeerContextTest` checks client/server engine mode, TLSv1.3-only and mandatory client auth.
- Python: `test_m3n_b1_tls_engine_boundary.py` checks real worker integration, exact-pin trust manager, host/join QR input, exporter checks, no unreviewed app packets, exact debug permission set and release flags.
- Security shell: `check_security_review_boundary.sh` permits only the explicitly scoped, debug-only encrypted handshake driver to read RFCOMM streams, forbids app text codecs, insecure Bluetooth methods and production integration; exact APK permission audit stays unchanged.
- Existing LAN/QR/messaging, M3k/M3l/M3m/M3n-A regression tests must all remain green. CI evidence is limited to compilation, deterministic unit/source tests and packaging, not live mutual TLS against two phones.

## Follow-on milestones, prioritized

**M3n-B2 — Fresh QR proof + exporter agreement inside authenticated TLS.** Implement a narrowly scoped, length-bounded encrypted control exchange that binds BOTH actual peer identities, both QR nonces, fresh host/join challenges, full TLS 1.3 exporter and roles, with exact transcript and negative tests. Reject unknown versions, replay, swapped roles, clock expiry and interrupted delivery. This should not include user text.

**M3n-C — Bilateral debug approval and short text.** Only after authenticated TLS+QR+exporter proof is implemented and independently reviewed, add exact-session human Approve/Decline, bilateral Confirm, zero plaintext fallback, then bounded debug text and honest delivery semantics. The user will trigger consolidated two-phone QA later. Release capability flags remain false until independent review and real device acceptance.

## References

- [Previous in-person two QR preparation](M3N_A_BLUETOOTH_RECIPROCAL_QR.md)
- [Prior RFCOMM source and deferred hardware checklist](M3M_DEBUG_RFCOMM_LINK.md)
- [Master core-feature plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md)
- [Bluetooth issue #59](https://github.com/PlayerEden/Eden-Comms/issues/59) · [owner hardware #26](https://github.com/PlayerEden/Eden-Comms/issues/26) · [independent review #42](https://github.com/PlayerEden/Eden-Comms/issues/42)
- Android references: [SSLEngine](https://developer.android.com/reference/javax/net/ssl/SSLEngine), [SSLEngines exporter](https://developer.android.com/reference/android/net/ssl/SSLEngines).
