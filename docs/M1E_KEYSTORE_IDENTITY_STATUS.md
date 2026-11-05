# M1e — Explicit Android Keystore device identity

Status: committed device-identity foundation, **not yet a working two-phone connection** (2026-11-03).

## Changes
- Android 8+ compatible Keystore EC P-256 signing key and generic, random-serial self-signed X.509 certificate, issued only when the user explicitly taps **Create local identity**.
- Identity public fingerprint can be displayed locally. No user name, phone number, hardware attestation challenge, GPS coordinates or IP addresses in the certificate subject or fingerprint.
- Android Keystore retains a non-exportable private key handle. The app does not write private key bytes to SharedPreferences, files, messages or backups.
- Existing identity is inspected, validity checked and its self-signature verified; invalid/expired identity **fails closed** instead of silently changing identity.
- Users can explicitly forget the identity (warn that future peer pin checks will fail until re-paired).
- Separate X509ExtendedKeyManager bridges local AndroidKeyStore signing identity into the already-developed pinned mutual TLS context for both client/server roles.
- JVM tests check TLS alias/algorithm routing and reject accidental RSA/unknown alias exposure.
- Identity management is on a background thread in the UI, and error messages do not include key material.
- App still requests **no INTERNET, Bluetooth or microphone permission** and does not expose any send/connect action.

## Important limitations
1. JVM tests are not a real Android Keystore test. Android device/OS/hardware providers may behave differently; first tester run needs to verify generation, self-signature, reuse, reset and handling of unavailable hardware Keystore.
2. Keystore support/hardware-backed protection varies. Do not claim all phones have hardware-backed non-exportable keys. A compromised or unlocked endpoint may be able to invoke cryptographic operations.
3. No automatic trusted-peer enrollment, messages, LAN sockets or handshake occurred.
4. A local fingerprint is not verification of a remote phone. Both users must compare full identities independently by an out-of-band method. Future UX should provide a camera-only local QR scan with no remote service.
5. The identity is a *local device credential*, not stored conversation history. Creation is opt-in, and it can be forgotten; history remains session-only by default.
6. Certificate expiration is two years. Expiry/corruption must show a repair prompt, never silently regenerate and cause peers to trust an unknown identity.
7. On-device QA must establish that self-signed certificate is accepted by the platform TLS 1.3 provider for mutual authentication and key use; no success claim until then.
8. Android 8/9 cannot be assumed to negotiate platform TLS 1.3; the eventual connection path must fail closed or set a higher supported minimum.

## Next vertical slice toward offline LAN
- Integrate two user-verified identity fingerprints with the pinned TLS socket, then add **LAN-only** host/join (same Wi-Fi, WAN disconnected).
- No plaintext fallback, no unverified message or audio feature; accurate connection UI and disconnect behavior.
- First no-internet two-phone test should prove a real TLS 1.3 handshake works with Keystore keys, then enable gated app-framed text after authenticated two-party confirmation.
- No release-style APK packaging for internal work. Preserve debug screenshots for synthetic testing.

References:
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec
- https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
- https://developer.android.com/privacy-and-security/keystore
