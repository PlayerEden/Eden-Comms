# M1d — Pairing primitives and mutual TLS handshake policy

Status: pre-network engineering checkpoint; NOT a finished pairing feature or audited secure messenger.
Date: 2026-11-03.

## Implemented
1. A strict X509ExtendedTrustManager that accepts only a single, valid peer leaf certificate matching a pre-provisioned FULL SHA-256 pin, checked while the TLS stack evaluates peer certificates. The same pin policy applies to the client and server role.
2. Pin bytes are defensively copied; a caller cannot mutate trust after construction.
3. TLS context factory requires an explicit local X509KeyManager and pinned peer hash. It configures future unconnected client sockets to support TLS 1.3 ONLY, and unbound server sockets to require a client certificate (mutual TLS).
4. Versioned QR payload codec EC1:<64 uppercase hex chars>, containing only the public certificate fingerprint, with no name, phone number, network address or secret keys.
5. Automated unit tests include malformed/forged QR payloads, pin mismatches, missing/ambiguous certificate chains, inappropriate auth-type inputs, pin mutation attempts, and TLS 1.3 + client-certificate-required socket settings.

**Nothing in this iteration connects to the local network.** Android manifest requests no networking or microphone permission. Messages remain disabled.

## Why this is safer than post-handshake comparison alone
A post-handshake hash comparison can occur after unauthenticated I/O if integrated poorly. A pinned trust manager rejects a wrong certificate *inside the TLS handshake* so a future transport cannot claim established peer trust merely because the socket is connected. Configuring TLS 1.3 on each socket prevents a provider silently negotiating TLS 1.2. Both ends MUST verify the other's fingerprint and require the other end's certificate.

## Non-negotiable caveats
- The full pin MUST be verified in-person/out-of-band against the intended person's physical phone (e.g. scan displayed QR). NEVER accept a pin supplied by an untrusted connection, DNS discovery, local broadcast, or unsolicited network message as trusted.
- A QR image alone does not establish consent or prove ownership. Require explicit two-sided confirmation, authenticated TLS transcript binding, and the existing SessionSecurityGate before unlocking message input.
- An exact leaf pin is a user-paired trust anchor, not a normal public CA chain check. This initial manager intentionally rejects multi-certificate chains. Full PKIX/algorithm constraints, EKU/KeyUsage checks, certificate revocation, and credential lifecycle need independent security review before networking is enabled.
- The socket configuration helpers are NOT a complete transport adapter. They do not start a handshake, enforce timeouts, protect application I/O ordering, manage socket close on failure, or bind authenticated confirmation events to real TLS sessions.
- The included key-manager test stub has **no private key** and cannot authenticate an actual phone. Real keys, identity provisioning, validity/rotation, storage and self-signed certificate generation are still to be implemented and tested.
- A stable certificate fingerprint can identify a device if repeatedly shared. QR display should be explicit, temporary and user-controlled; never broadcast it in discovery.
- Android TLS 1.3 is supported by default on Android 10+. The app still declares minSdk 26 (Android 8), so the future connection path MUST fail closed on platforms lacking TLS 1.3, or formally raise the minSdk. There is no fallback to TLS 1.2.
- Development builds still allow screenshots and screen recording for UI testing; DO NOT put real user communications into test builds.

## Remaining blocking work
1. Actual Android Keystore-backed local X.509 identity with credential lifecycle; verify hardware-backed behavior when available.
2. Camera/QR flow on both phones with explicit pairing consent, reject spoofed peers, identity changes, stale QR, and revoked trust.
3. Real TLS handshake integration on client/server plus authenticated transcript-bound two-way confirmation; only then expose application streams through SessionSecurityGate.
4. LAN discovery and network permission flow with correct Android 17+ local network behavior, app-scope no-cloud testing.
5. Two real phones, WAN disconnected, wrong-pin/rogue peer tests, TLS-downgrade tests, app packet capture, crash/reconnect, and independent cryptographic review before privacy claims.

## Engineering test evidence
- Source plus all new tests locally typechecked against Java 21 / Kotlin 1.9, and 14 proposed tests passed under a temporary local JUnit-shaped test harness. This is preliminary only; GitHub Actions with actual JUnit is the authoritative compilation/test gate.
- Do not distribute or treat a debug APK as a release for an internal parser/trust check.

References:
- https://developer.android.com/privacy-and-security/security-ssl
- https://developer.android.com/reference/javax/net/ssl/X509ExtendedTrustManager
- https://mas.owasp.org/MASVS/
