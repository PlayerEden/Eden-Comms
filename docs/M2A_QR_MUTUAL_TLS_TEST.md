# M2a — In-app QR mutual TLS test over local Wi-Fi

Date: November 4, 2026. Status: implemented on branch; CI/physical Android QA reported separately.

## Purpose
Move beyond copying full certificate fingerprints and IP addresses through other messaging apps. The app can now exchange the required **public identity data locally, face-to-face**, via its own QR scanner and display. Then use existing **mutually pinned TLS 1.3** to attempt a real one-shot handshake on the same Wi-Fi network. No server, cloud, Bluetooth, clipboard, external scanner, media upload, chat storage, or voice transmission.

**Security limitation:** This is an opt-in *development handshake test*, not a production-ready secure messenger, full messaging service, or independently audited pairing protocol. The peer's Android Keystore and TLS certificate chain/provider behavior still require real two-phone testing. No application message bytes flow at this stage.

## Why two scans?
A single host QR gives the joiner the **host's** exact certificate identity but does not independently authenticate the **joiner's** identity to the host. To avoid an unverified or trust-all TLS state, we initially require an explicit *second in-person QR*, shown on the joiner's phone and scanned by the host. That supplies the joiner's exact full fingerprint and binds the response to the host's fresh session nonce.

One-scan + short-code UX is a future potential improvement, but requires rigorous transcript-bound mutual authentication and anti-MITM review. We do not silently trust an unverified certificate to make the UX easier.

## QR protocol (public information only)
- Host QR: `EC2H|<IPv4>|<SHA256-full-hex>|<nonce-128bit>|<issued-unix-seconds>`.
- Joiner QR: `EC2J|<host-nonce>|<joiner-SHA256-full-hex>|<joiner-nonce-128bit>`.
- Canonical uppercase hex, exact lengths, strict version/field count/ASCII, five-minute lifetime with at most 45-second clock skew.
- Only local private/link-local numeric IPv4, never DNS/remote address. Joiner checks Wi-Fi subnet before initiating TLS. Fixed port 42519 in existing LAN probe.
- Public certificate fingerprint is not a secret, but neither QR should be published outside an in-person pairing. IP and nonce are ephemeral metadata; no names, phone numbers, GPS, contacts, message history or private keys.
- Host checks reply's host nonce with a constant-time comparison and rejects self-pairing. Both sides pin the full certificate during mutual TLS.
- Replays from a previous or expired host session are rejected by nonce and TTL; this is *not* a substitute for protocol-review-level replay protection for real messaging.

## Android implementation
- `LocalPairingQrCodec.kt` + JVM tests: strict bounded canonical QR protocol.
- `LocalPairingQrCamera.kt`: QR generation in memory via ZXing Core 3.5.4; scanning via AndroidX CameraX 1.6.2 (stable) and in-app QR decoder. No cloud QR service.
- QR camera runtime permission is requested **only when a person opens a scanner**, not on first launch. Scanner is life-cycle bounded, closes when the UI leaves; no images saved, screenshots automated, telemetry or contacts.
- `LocalPairingTestScreen.kt`: opt-in creation/reuse of Keystore identity, Host QR, Join QR scan, reciprocal QR scan, host Listen, join Connect, visible one-shot TLS outcome.
- `MainActivity.kt`: Local Wi-Fi routes to a clearly labeled QR **test**; normal Host/Join feature flags remain disabled until verified usable messaging exists.
- manifest / permission audit now allows only INTERNET, ACCESS_NETWORK_STATE and CAMERA; rejects Bluetooth, location, microphone, contacts and SMS.
- App version `0.2.0-qr-tls-test`.

## Two-phone QA — required before advancing to actual chat
1. Install on two Android phones, turn **mobile data off**, connect to the same Wi-Fi (router Internet/WAN may be disconnected). On each, navigate Local Wi-Fi → Test QR pairing.
2. Create or reuse each phone's opt-in Android Keystore identity.
3. Host: choose Host and display QR. Joiner: choose Join and scan the host QR in Eden Comms. The joiner now displays its reply QR.
4. Host: scan the joiner's QR, tap **Start listening**. Joiner: tap **Connect to host**.
5. Both should report a successful TLS 1.3 mutual certificate check. Each socket closes immediately. If one phone fails, do not mark as verified or unlock messaging.
6. Negative tests: scan an unrelated/expired QR, scan own code, connect on different Wi-Fi, restart after 5 minutes, deny camera permission, rotate/reopen scanner, turn off Wi-Fi mid-test, close screen while listening. Every case must fail safely without fallbacks.
7. Check CameraX image orientation, denial/retry, resource cleanup and ability to scan on both phones; check Android Keystore certificate signature and server-side client certificate request. Debug screenshots permitted; redact QR/IP and public pins in shared QA reports.
8. Confirm app manifest contains **no microphone, location, Bluetooth or contact** permissions. Android 17 target-SDK 37 future migration to ACCESS_LOCAL_NETWORK needs a separate permission review.

## Blocking gaps before first encrypted text
- Two-phone proof of authenticated TLS 1.3 with each device's Android Keystore key.
- A session-bound bilateral confirmation state and strict gated message IO. One-shot probe currently **closes after handshake**.
- Network error/reconnect and trust lifecycle handling, stress/negative QA, user-facing simple pairing refinement.
- Independent security review before claiming production confidentiality, accessibility and usability across Android devices.
- Session-only memory policy and text composer/transcript never persist without explicit later opt-in.

## References
- https://developer.android.com/jetpack/androidx/releases/camera
- https://developer.android.com/media/camera/camerax/analyze
- https://central.sonatype.com/artifact/com.google.zxing/core
- https://developer.android.com/privacy-and-security/local-network-permission
