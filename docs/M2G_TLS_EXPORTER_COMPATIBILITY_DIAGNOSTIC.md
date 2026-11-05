# M2g — Live TLS 1.3 exporter compatibility diagnostic (debug fixed-phrase test)

> **Historical document (M2g).** The statements below accurately describe the older *one-phone* diagnostic when M2g shipped. **M2w supersedes that flow** with a required RFC 9266 two-phone exporter equality exchange **inside the pinned TLS socket** before READY, which does transmit 32-byte channel-binding values to the peer. See [M2w current protocol and API 31+ fail-closed requirements](M2W_DEBUG_TLS_CHANNEL_BINDING_MATCH.md). Do not interpret the historical “no bytes exchanged” text as current behavior.


**Prepared:** November 4, 2026  
**Status:** Implemented as a **non-authorizing development diagnostic**. Requires CI verification and future two-physical-phone testing; **not** an authenticated bilateral approval protocol, cryptographic audit, or production messaging feature.  
**Depends on:** M2f test-only protocol proposal, M2e unwired authorization model, existing v0.2.7 debug LAN fixed-phrase test, L0/L1 privacy work.

## Goal and evidence

Before building a bilateral consent exchange, check that the actual Android TLS implementation can produce the standard TLS 1.3 channel-binding value on a **live, mutually pinned** socket, instead of relying on Android/API assumptions.

Google/Android API reference:
- `android.net.ssl.SSLSockets.isSupportedSocket`: Android API 29+
- `SSLSockets.exportKeyingMaterial`: Android API **31+**, requires live TLS connection; may return `null` if handshake not complete or socket closed; TLS provider support varies; `null` context is NOT the same as an empty context.
- https://developer.android.com/reference/android/net/ssl/SSLSockets
- RFC 9266 §2: `tls-exporter` channel binding, ASCII `EXPORTER-Channel-Binding` label, **zero-length context**, **32-byte** output; this value is NOT a secret encryption key: https://www.rfc-editor.org/rfc/rfc9266.html

### What this PR adds

1. `TlsExporterReadiness.probeAfterVerifiedDemoProof(SSLSocket)` runs only after the existing mutual pinned TLS 1.3 handshake, exact peer fingerprint and QR/session challenge proof have passed on the **debug-gated fixed-phrase** `LanDemoSession`. It uses `SSLSockets.isSupportedSocket` and the Android API31 exporter. On all unsupported/error cases it reports a safe descriptive status without throwing an error into the existing demo or altering its message flow.
2. It returns **only a diagnostic status enum**. The 32 bytes are never returned to caller, shown, logged, hashed for release consent, stored or put on wire, and are zeroed best-effort after checking their length; never treat them as secret data.
3. `preflight` is a JVM-testable pure policy step. API 26–30, non-TLS1.3, closed socket and non-platform provider paths result in unavailable statuses, with no QR-only or weaker TLS fallback.
4. The screen displays the diagnostic status on the active fixed-phrase test page so a tester can later report **each phone's own status**, explicitly labeled non-approval. Existing ability to exchange **four fixed harmless test phrases** is unaffected.
5. `scripts/check_security_review_boundary.sh` fails if any other main-source class starts using `TlsExporterReadiness`. `ProductionSessionAuthorization`, release capability flags, test-only `ReviewOnlyApprovalFrame`, permissions, persistence, SDK dependencies and protocol packets are unchanged.

### What is NOT implemented

- The two phones **do not exchange or compare exported bindings**; two statuses of AVAILABLE are necessary compatibility evidence only, not consent or a proof that both computed the same value.
- No canonical approval transcript, cryptographic approval packet, authenticated approval receive handler, local/remote consent screen, production trusted session adapter, free-text message, PTT, account or peer retention.
- No remote telemetry, no dependency on Google Play services, no raw session secrets stored. No extra data is transmitted by the new probe (the fixed-phrase demo still sends existing TLS/QR/test traffic).
- Existing Android minSdk26 is **not** a declaration that TLS1.3 or this exporter works on API26–28. Real target policy must be decided later; no silent downgrade.

## Physical QA checklist for later (needs real devices)

Follow `docs/M2D_TWO_PHONE_TEST_BUNDLE.md` first to verify the QR/TLS session positive/negative cases. For each test keep **only** sanitized device make/model, Android version/API, local diagnostic status, test phrases sent/received, close/cancel outcome. Do not save screenshots containing QR codes, addresses, fingerprints or raw exported bytes.

| Case | Expected local diagnostic | Notes |
| --- | --- | --- |
| Android 12+/API31+, supported platform TLS1.3 socket, pinned QR proof | `AVAILABLE` if provider actually exports | Both phones report **independently**; never claim shared matching output |
| Android 11/API30 | `API_TOO_OLD` | Existing demo may still work with TLS 1.3, but no production consent |
| Android 10/API29 | `API_TOO_OLD` | Existing demo may still work with TLS 1.3 |
| Android 8/9/API26–28 | `API_TOO_OLD` *only if reaching this diagnostic* | TLS1.3 itself likely unavailable on platform; never downgrade |
| Wrong QR/cert pin, stale QR or broken handshake | Session fails before diagnostic | No status should imply approval |
| Cancel/background/network drop | Session revokes / exits test | No production send path ever enabled |
| Platform socket unsupported/closed/exporter null or provider exception | corresponding unavailable status | No retry using a different hash/key-exchange scheme |

**Critical future test:** To build actual approval, a qualified independent protocol reviewer must specify exact canonical transcript bytes/role/nonce/version/commitment, test vectors, properly authenticated frame ordering, both-person explicit approval semantics, expiry and replay resistance, fail-closed lifecycle, provider compatibility on two phones, and sign off before live integration. An RFC exporter by itself is **not** an authenticated application approval protocol.

## Automated acceptance

- Kotlin/JVM unit tests include `TlsExporterReadinessTest` (API 26–30, TLS1.2, closed/unconnected socket, unsupported provider, non-approval copy).
- CI compiles Android debug APK, original tests, exact dependency+permission inventory, backup/cleartext and security boundary.
- `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, and `CAN_TRANSMIT_VOICE` remain literally false. No change to demo packet bytes; no AAB/Play publication.
- CI successful on **final PR head** before calling milestone complete.

**Rollout:** This is a visibility enhancement to the **existing debug two-phone test**. It is safe to integrate only after automated checks, but it neither replaces two-device acceptance testing nor provides independently audited secure messaging.
