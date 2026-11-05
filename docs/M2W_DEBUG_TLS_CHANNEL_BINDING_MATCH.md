# M2w — two-phone RFC 9266 channel-binding equality (debug-only)

**Engineering checkpoint:** 2026-11-05. **Security status:** UNREVIEWED experiment, no unrestricted messaging/voice.  
**Two physical Android phones:** NOT TESTED. **Independent security/protocol reviewer:** NOT ENGAGED.  
**Purpose:** Replace M2g's one-phone “exporter available” compatibility report with a required equality exchange **between both phones** before their debug fixed-phrase test becomes READY.

## Real behavior

The existing experimental connection still does:

1. Two face-to-face QR scans and local Wi-Fi continuity.
2. Mutually authenticated TLS 1.3 and exact SHA-256 peer certificate pins.
3. The fixed-size reciprocal QR session proof on that same TLS socket.
4. **NEW — M2w:** Both sides obtain the RFC 9266 `tls-exporter` value from their own live, already authenticated TLS 1.3 socket and exchange the 32-byte channel binding inside the protected TLS stream. The presenter sends first, then the joiner confirms its local value is equal and sends its own. The presenter also verifies equality. The comparison uses `MessageDigest.isEqual`. A mismatch, invalid role/frame, missing provider support or timeout aborts the debug session before READY. This is a **channel-binding interoperability experiment**, not approval of a messaging protocol.
5. Only then the existing mutual READY control exchange.
6. The M2v per-phone **Approve/Decline** controls, requiring local TLS write and peer control observation before any of the four synthetic phrases.

The older `TlsExporterReadiness.probeAfterVerifiedDemoProof` compatibility-only utility remains in source for historical diagnostics/tests but is **not used to authorize** anything. The live debug test now calls `DebugTlsChannelAgreement.verify()`.

### Important compatibility change

Android's platform exporter API is available only at **API level 31+** (Android 12+), and an installed provider may still return unsupported. The M2w debug test **fails closed** without a matching binding and does **not** silently fall back to QR-only proof, TLS 1.2, alternate local hashes, or ordinary text. Older Android versions can still run other app screens, but cannot complete this stricter debug LAN test.

Older pre-M2w APKs have a different test wire sequence and must reject incompatible new clients rather than claiming success. Treat mixed versions as a negative phone QA case. This is deliberate experimental incompatibility, not a production rollout or data migration.

## Exact review candidate record

This frame is transported exclusively **inside the already authenticated TLS socket** and only after the QR session proof.

| Field | Size | Value |
| --- | ---: | --- |
| Magic | 4 bytes | `ECCB` |
| Version | 1 byte | `01` |
| Role | 1 byte | `01` presenter or `02` joiner |
| RFC 9266 channel binding | 32 bytes | `EXPORTER-Channel-Binding`, empty context, length 32 |
| **Total** | **38 bytes** | Exact read size; no dynamic allocation based on untrusted lengths |

The presenter sends its record then receives the joiner's; the joiner reads, compares, sends. Wrong role/version/magic, zero value, nonmatching value, truncated record, unsupported API/provider and timeout all fail closed. The exported binding bytes and ephemeral frame buffers are best-effort overwritten after use; no binding, peer certificate, QR token, IP or raw error is logged/persisted or placed in the human-shareable QA report. Best-effort byte wiping is **not** a guarantee of Android/JVM physical memory erasure.

**RFC 9266 notes:** The exported value is specifically for channel binding. RFC 9266 says disclosure does not leak TLS channel secrets, but it **MUST NOT be reused as an encryption key or authentication secret**. This experiment compares equality only. A matching value is not proof that both humans knowingly approved this peer, that a text message was delivered, or that the client is secure.

## Security boundary and automated coverage

- `DebugExporterBindingFrame` is a fixed-length pure Kotlin record, tested against exact 38-byte shape, both roles, mismatched values, corrupted header, bad lengths, all-zero bytes, and fake in-memory two-endpoint exchange.
- `DebugTlsChannelAgreement` is the Android socket adapter. It invokes the platform exporter only after the pinned TLS/QR proof and rejects API/provider incompatibility. Real Android timing/exporter equality is **not** proven by JVM tests.
- `LanDemoSession` now calls it *before READY and before any M2v approval*. Its progress label is **Comparing this TLS connection's channel binding with the other phone…**; this describes an operation **started**, not a certificate of security.
- `LanProbeDiagnostics` has fixed **EXPORTER_UNAVAILABLE / EXPORTER_MISMATCH / EXPORTER_TIMEOUT** categories, with no raw addresses, peer fingerprints or binding bytes.
- The one-phone report now labels its debug READY outcome **Pinned TLS/QR/channel-binding test link ready**, distinct from local approval, peer approval and the four-phrase stage.
- `scripts/check_security_review_boundary.sh` requires a live match call in the debug session and forbids use of exporter logic in general chat/production files. The M2u free-text codec remains unwired, `ProductionSessionAuthorization` unwired, and `ReleaseCapabilities.CAN_CONNECT/CAN_SEND_MESSAGES/CAN_TRANSMIT_VOICE` all **false**.

## Physical two-phone acceptance (all NOT TESTED)

Add these to the consolidated [M2k worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md):

- T23: two API 31+ phones with a supported TLS exporter both pass the channel-binding comparison and reach the approval stage.
- T24: mismatch/wrong role/corrupted frame in a controlled negative harness must abort before READY (do not use raw production messages or expose exporter bytes publicly).
- T25: unsupported provider or API < 31 must stop with a safe **EXPORTER_UNAVAILABLE**, never fall back, send a preset phrase or show “approved.”
- T26: mixed older (pre-M2w) and newer APKs fail closed; no accidental approval or preset exchange.

Also rerun M2k T01–T22, especially Wi-Fi changes, cancel, stalled QR and both-person Approve/Decline tests. Capture separately sanitized reports on both devices; do not share QRs, exporter bytes, network addresses, raw packets or certificate fingerprints in public issues.

## Still needed for actual encrypted LAN text

This is **NOT** the independent protocol review required by [core M3 issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42). In particular the tiny M2v APPROVE/DECLINE controls **remain generic**, without a reviewed application transcript commitment including pin/QR/challenge/exporter role binding, explicit signed-off protocol semantics, final agreement confirmation, authenticated ACKs, replay/out-of-order handling, or approved application-level authorization. The M2u UTF-8 TEXT/ACK codec is not connected and user-written text remains local-only.

**Next practical gate:** obtain a qualified independent cryptographic/security protocol review of the *actual* M2w channel-binding + M2v human consent sequence, prioritize their required fixes, then implement a separately reviewed scoped real-LAN text sender/receiver with its own physical and negative test suite. Passing Android CI alone cannot close the review or the physical two-phone gates.

**Primary references:**
- RFC 9266: https://www.rfc-editor.org/rfc/rfc9266.html
- Android SSLSockets API: https://developer.android.com/reference/android/net/ssl/SSLSockets
- Main roadmap: [Development Roadmap](DEVELOPMENT_ROADMAP.md).
