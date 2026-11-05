# M1b — Transport framing and cryptographic selection
Status: **in development; not a cryptographic implementation or a secure messenger**.
Date: 2026-11-03

## Delivered in this iteration
- Binary stream-frame codec with a fixed `ECOM` marker, protocol version, reserved flags, 4-byte length and opaque payload.
- Input is bounded to 1–16,384 bytes BEFORE allocation to reduce accidental memory exhaustion on malformed or hostile data.
- Rejects unknown versions, flags, invalid length, truncated header/body, and garbage frames.
- Unit tests cover binary payloads, sequential frames, sizes, malformed input, truncation and a deterministic malformed-byte corpus.
- **No socket / Bluetooth / Wi-Fi Direct is connected to the codec. No messages are sent.**
- Does not weaken the previous authenticated-connection gate or enable screenshot protection on debug APKs.

## Critical security boundary
**Framing is not encryption or authentication.** The codec does not claim that input bytes are ciphertext and does not protect any payload by itself. Never call it on a raw, unencrypted or unidentified socket. All connection paths must remain disabled until a reviewed cryptographic session wraps traffic and confirms the intended peer on both devices. Source inspection and a unit test are not proof that messages would be confidential in a future networked app.

## Crypto candidates under investigation (NOT approved)
### A. Noise XX (interactive)
Strengths: well-defined interactive Diffie–Hellman handshake patterns and transport keys; compatible conceptually with local first-time pairing and independent transports. Research and vet maintained JVM/Android implementation, especially Android providers for X25519 and AEAD, entropy, test vectors, nonce failure, transcript/SAS binding, and external audit. 
Risk: finding a **maintained, independently audited implementation** that works on all supported Android versions is not assured. For example, jchambers/java-noise explicitly describes lack of an independent audit. Do not treat protocol specifications as proof that a library is safe.

### B. TLS 1.3 with mutual authentication and verified session identity
Strengths: platform-managed well-studied secure channel on Android 10+; Conscrypt available on Android; TLS framing, integrity and key exchange already provided.
Risks: the app needs an acceptable authenticated peer identity path without any conventional public CA/server, certificate issuance/rotation, verification without a naive permissive trust manager, and compatibility for Android 8–9 if minSdk remains 26. A deliberate out-of-band verification model is required. Reject ad-hoc trust-all logic and downgrade to TLS 1.2 without explicit review.

**Decision:** Neither is selected. No crypto implementation may be labeled secure or wired into production merely because it compiles.

## Cross-transport architecture after review
`LAN/Bluetooth/Wi-Fi Direct stream` -> **authenticated, encrypted, verified channel** -> bounded application frames -> message/voice parser.

- Explicit user action starts discovery/advertising. No permanent broadcasting of stable device names/identities without consent.
- Session status and peer verification must come from real authenticated channel state, NOT from UI-supplied booleans, strings, or untrusted network claims.
- Both phones compare a cryptographically derived QR/fingerprint (full identity) or an approved high-entropy short authentication string with anti-spoof safeguards. Verification semantics need external review.
- Message body/username/type should not be present in plaintext discovery packets. Outer packet timing/size/IP/radio identifiers may remain observable.
- Preserve default session-only retention; no message payload in notifications, logs, diagnostic exports or backup.

## Android permissions and device compatibility (2026)
- Android 17 (target API 37+) restricts direct LAN access via `ACCESS_LOCAL_NETWORK` or privacy-preserving picker grants. For NSD discovery, Android supports `FLAG_SHOW_PICKER`; evaluate its older-version fallback.
- App currently targets API 36 and requests **no INTERNET/network permissions**. Do not add network permissions until M1 authenticated transport is selected and audited.
- Real phones with Wi-Fi disabled, guest isolation, blocked multicast and no WAN must be tested. Use targeted, not release-wide, QA during development.

## Security exit criteria for M1b
1. Security library comparison completed, maintenance/license/AAR metadata/independent audit evidence inspected.
2. Peer identity model, handshake transcript, SAS/QR model, trust persistence and revocation specified.
3. Reviewed crypto library hooked to the session gate; real peer events cannot be spoofed by arbitrary untrusted strings.
4. M1 messaging enabled only after ciphertext-in-transit tests, MITM and replay tests, two-device validation, and no-cloud packet capture.
5. No plaintext downgrade, regardless of transport or failure path.

## References
- https://noiseprotocol.org/noise_rev34.html
- https://github.com/jchambers/java-noise
- https://developer.android.com/privacy-and-security/security-ssl
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/reference/android/net/nsd/NsdManager
- https://mas.owasp.org/MASVS/08-MASVS-NETWORK/
