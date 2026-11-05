# M1c: Pinned peer identity verification — design checkpoint

Status: **implementation of a certificate verification helper, NOT a connected or audited messenger**.

## Delivered
- JCA/Android-provider X.509 DER parsing with a bounded input and no trailing bytes.
- Full SHA-256 certificate fingerprint for peer identity, and constant-time pin matching.
- A TLS 1.3-only session check that rejects certificate mismatch, missing pins or lower TLS versions. This is not a TrustManager or a complete TLS setup.
- Unit tests include a public test certificate, fingerprint fixture, TLS 1.2 downgrade, wrong and absent pins, malformed and oversized DER.
- No private keys, real peer identities, message content, certificate trust database, Android networking permission, or socket connections are included in this change.

## Why TLS 1.3 is a candidate
Android 10 and later support TLS 1.3 by default with the platform provider. Using platform TLS can avoid inventing encryption or bundling/maintaining an additional cryptographic stack. The repository currently targets minSdk 26 (Android 8) but this helper **must fail closed** on versions that cannot use TLS 1.3. Do not silently enable TLS 1.2; evaluate whether to increase minSdk before enabling this path.

The certificate fingerprint becomes trustworthy **only after** the full identity (not a short unauthenticated label) is compared over an authenticated out-of-band method such as a properly designed QR process. Showing a hash is not itself verification.

## Risks/open design work
1. First-time trust bootstrapping: how two unknown phones exchange public certificate material, verify BOTH parties out of band, and avoid MITM, fake confirmations and clipboard/QR substitution.
2. Key lifecycle: per-session ephemeral certificates vs persistent Android Keystore identity, whether users can explicitly save a trusted contact, unlink/reset and recover.
3. TLS context and mutual authentication: implement a strict pinned TrustManager and correct server-side client-auth requirement. **Never** use a trust-all manager or cleartext bootstrap to send application content.
4. A pinned certificate alone does not prove which human holds the phone. Display verified peer status only after an actual authenticated transcript and two-party confirmation, not by setting a UI flag.
5. Bluetooth and Wi-Fi Direct must use the same reviewed authentication rules; JSSE SSLEngine may be appropriate for Bluetooth streams only after integration testing.
6. Private data metadata leaks: discovery names, public cert or fingerprint exposure, network access, log and backup rules.
7. Actual socket timeouts, session cancellation and transport switching. On any failed check, close and wipe the channel state; never retry in plaintext.
8. This helper is **not** proof that TLS certificate verification was performed by a safe configuration. It must be invoked within a reviewed secure-transport adapter, which validates peer identity during or immediately following handshake BEFORE sending messages.
9. Full user-facing fingerprint comparison is awkward. A safer QR/short-code UX needs dedicated review; do not truncate pins casually.

## Next implementation slice (M1d)
- Define and test an explicit local certificate enrollment UX and mutual pinned trust boundary with no plaintext message path.
- Build a strict TLS 1.3 channel adapter whose constructor needs a previously user-confirmed pin; no unverified channel can reach the message dispatcher.
- Integrate session gate with actual authenticated events, not arbitrary strings.
- Then LAN discovery/connect with real-device testing and protocol/no-traffic captures.

## Testing gate
Before claiming real privacy: two phones with Internet disconnected, TLS handshake negative tests, active MITM trials, replay/downgrade, identity substitution, app network capture, Android backup behavior and independent crypto architecture review.

Sources
- https://developer.android.com/privacy-and-security/security-ssl
- https://source.android.com/docs/core/ota/modular-system/conscrypt
- https://mas.owasp.org/MASVS/
