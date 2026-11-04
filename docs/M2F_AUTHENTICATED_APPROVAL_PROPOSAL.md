# M2f — Authenticated bilateral approval exchange (REVIEW PROPOSAL)

**Prepared:** 2026-11-04  
**Status:** Proposed design + bounded *unwired* codec/tests. Not an implemented security protocol, no proof of consent in a real session, no production authorization, and **not independently reviewed**.  
**Base:** M2e v0.2.8 policy-only PR #17; current debug v0.2.7 remains the physical two-phone test candidate.

## Why we're changing the design

The production authorization model correctly denies all release sends, but the named callbacks `onAuthenticatedChannel` and `onAuthenticatedRemoteApproval` could be abused by a poorly integrated UI or QR parser. Those are POLICY callbacks, not cryptographic evidence. This milestone specifies what evidence a future *trusted adapter* must possess before invoking them. It deliberately stops short of wiring a live stream.

### Roles and required evidence

| Concept | Authenticated evidence | Not sufficient by itself |
|---|---|---|
| Chosen peer | Fresh out-of-band public cert fingerprint of expected phone + peer's TLS private-key proof | IP address, hostname, discovered advertising name, saved contact label |
| Current connection | Full mutual TLS 1.3 handshake with exact pinned peer cert + live socket and unique attempt | Successful TCP connect, TLS without pinned peer identity |
| QR exchange | Both QR pins and fresh nonces, roles and handshake challenges verified inside authenticated transport | Scanning only the first QR, stale screenshot |
| Channel binding (preferred) | RFC 9266 `tls-exporter` binding computed from the **live** TLS 1.3 connection | Hashing public QR bytes alone |
| Human approval | Each person's explicit action in the app for the **current** peer/connection, communicated as a versioned authenticated control frame over that socket | A UI button in only one app, local boolean, an untrusted remote packet |
| Permission to send | All above **and** reviewed adapter + current live channel + feature gate | Mere model state or successful demo packet |

## Standard channel binding available on Android

RFC 9266 section 2 defines the `tls-exporter` channel binding, using the TLS exporter label **`EXPORTER-Channel-Binding`**, a **zero-length context**, and **32 bytes**. RFC 9266 explicitly says the exported channel-binding value is **not secret** and MUST NOT be used to encrypt privileged payloads. Its purpose here is to tie the approval transcript to this particular TLS connection.

Android exposes `android.net.ssl.SSLSockets.exportKeyingMaterial(SSLSocket, label, context, length)` starting at **API 31**. The Android platform supports TLS 1.3 in its default SSLSocket from **API 29**. Our APK's `minSdk=26` means "installable", NOT guaranteed TLS 1.3 on 26–28. The currently supported testing devices are Android 13 and newer, but provider behavior still requires actual verification.

**Decision for experimental V1:** prefer Android 12+ (API 31+) *for the exporter-bound approval mode* where provider support is confirmed. If missing, refuse to enable this new mode—**do not silently treat a QR-only SHA-256 as equivalent channel binding**. Leave earlier Android installs on the existing disabled production/diagnostic path until a separate review accepts an alternative provider or a vetted transport. Avoid writing a homemade TLS exporter or custom cryptographic scheme.

## Proposed consent transcript (reviewer must finalize byte format)

An approved commitment should be derived from a **versioned, length-delimited canonical transcript** that covers: both exact certificate hashes, role orientation, both fresh QR nonces, protocol ID/version, both live handshake challenge values and **the RFC 9266 32-byte exporter value from the same TLS socket**. The proposed domain separator is `EdenComms-Approval-v1` to prevent cross-protocol confusion. Whether to use SHA-256 and the exact ordering, length-prefixing, and proof exchange must be finalized by a security reviewer and supplied as test vectors before any data path is enabled. **This document is not itself a new cryptographic standard or a reviewed KDF.**

### Proposed message/UX sequence

```text
A: SHOW CODE                              B: SCAN CODE
         B verifies A's QR fingerprint + nonce
A: SCAN B's REPLY                       B: SHOW REPLY
         A verifies B's fingerprint + nonce
         mutual pinned TLS 1.3 and QR-session proof
         both check live exporter + canonical session commitment
         both UI surfaces show "Verify this connection" (no chat yet)

local explicit approval A       local explicit approval B
     |                                  |
     +-- ECAP/v1 APPROVE -------------->|
     |<-------------- APPROVE ECAP/v1 --+
     |                                  |
     both accept only from the already authenticated peer
     when BOTH local and remote approvals for this attempt are observed
     display connection verified, after an optional reviewed final ACK
     ... only a future audited adapter can enable real messaging ...
```

**Usability caution:** the current two QR scans already require explicit user actions. A future UX review should determine whether well-worded QR actions can constitute the required local consent without adding multiple extra taps. Do not infer remote consent merely because the other phone is online or sent a QR. One-QR-plus-short-comparison-code is a separate protocol and requires separate security review and threat testing; don't implement simply by removing the second scan.

### Pure draft frame for parsing tests ONLY

`ReviewOnlyApprovalFrame` is deliberately a **strict, 39-byte envelope**:
`ASCII ECAP (4) | version (1) | decision APPROVE/DECLINE (1) | sender role (1) | proposed channel/QR commitment (32)`.

- Exact length, magic, version, enum values, nonzero digest and expected role/commitment are validated, with copied byte arrays.
- It creates no network or TLS bytes, requires no permissions and changes zero release flags. A packet without trusted authenticated socket context is **not proof** of remote authorization.
- A future authenticated reader must reject wrong role, wrong attempt/session commitment, duplicate conflicting decisions, replay, absence of the local person's consent and packets received before successful identity/QR checks. Keep a bounded deadline and immediately revoke on Cancel, app background, network loss or identity change.
- A future reviewer should decide if a final acknowledgment is needed to make connection state coherent on both phones. It is impossible to ensure simultaneous knowledge of all delivery states after arbitrary network failure; never falsely promise both devices received all approvals.
- The debugger test with fixed phrase IDs remains unchanged. The old test's four-record challenge **does not expose its challenges to this new codec**; live integration requires a reviewed adapter, not an assumption that a QR-only digest is enough.

## Required independent security assessment

Threat cases: MITM on hostile Wi-Fi, QR screenshot replay, stale nonce, self-pairing, swap of roles or fingerprints, concurrent connection confusion, TLS exporter mismatch, wrong provider/API, deliberate drop/reordering/duplicate APPROVE/DECLINE, app background/cancel race, one user says no, simulated UI callback, stale remote confirmation across reconnect, intentional traffic capture, and device key rotation.

Review output must include: named reviewer and date; exact canonical transcript and wire test vectors; provider compatibility results (API 31+, older Android policy); authenticated sender-to-channel binding design; human-approval UX; replay/downgrade defenses; threat model against an active local attacker; error and retention behavior; licensing/dependency audit; two-phone positive/negative test evidence; and sign-off before any release send gate changes.

## Sources

- RFC 9266 TLS 1.3 channel bindings: https://www.rfc-editor.org/rfc/rfc9266.html
- Android `SSLSockets.exportKeyingMaterial` (API 31): https://developer.android.com/reference/android/net/ssl/SSLSockets
- Android `SSLSocket` TLS version table: https://developer.android.com/reference/javax/net/ssl/SSLSocket
- RFC 8446 TLS 1.3 exporter: https://www.rfc-editor.org/rfc/rfc8446.html
- OWASP MASVS AUTH and NETWORK: https://mas.owasp.org/MASVS/07-MASVS-AUTH/ and https://mas.owasp.org/MASVS/08-MASVS-NETWORK/
- Current project policy model: `docs/M2E_PRODUCTION_SESSION_AUTH_BOUNDARY.md`

**STOP LINE:** No trusted production stream, no text, no PTT, no new permissions, no remembered peers; this is proposed parser/test infrastructure and review specifications, not an implementation of approved encrypted messaging.
