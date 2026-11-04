# M2a — In-app QR mutual TLS test over local Wi-Fi

Date: November 4, 2026. Status: implemented on branch; CI/physical Android QA reported separately.

## Purpose
Move beyond copying full certificate fingerprints and IP addresses through other messaging apps. The app can now exchange the required **public identity data locally, face-to-face**, via its own QR scanner and display. Then use existing **mutually pinned TLS 1.3** to attempt a real one-shot handshake on the same Wi-Fi network. No server, cloud, Bluetooth, clipboard, external scanner, media upload, chat storage, or voice transmission.

**Security limitation:** This is an opt-in *development handshake test*, not a production-ready secure messenger, full messaging service, or independently audited pairing protocol. The peer's Android Keystore and TLS certificate chain/provider behavior still require real two-phone testing. No application message bytes flow at this stage.

## Physical tester observation — November 4, 2026

A user completed an initial attempt involving **two real phones** and reached identity setup and the QR connection test. At least one phone reported **“Secure LAN test failed”**. **Do not mark the handshake or mutual authentication PASS.** The report does not reveal whether the underlying cause was Wi-Fi isolation, reachability, listening order, TLS provider compatibility or certificate rejection. Screenshots do not establish both devices' exact state or any successful authentication.

**Next diagnostics patch:** Show bounded safe phase/category codes for Wi-Fi, host listen/bind timeout, peer reachability/connect timeout, TLS negotiation and certificate rejection. Never surface raw exceptions, IP addresses or certificate fingerprints in screenshots or logs. Repeat test with host listening first and both devices on the same local Wi-Fi, WAN disconnected. Capture only both phones' safe codes and app versions. Fix the demonstrated cause; do not relax TLS 1.3 or certificate pinning.

## Second physical test — November 4, 2026

After installing the safe-diagnostics APK on two phones, the tester showed QR generation and an ensuing **PEER-UNREACHABLE** error. That category was previously a combination of `ConnectException` and `NoRouteToHostException`, so it does **not** establish which network failure occurred. It does mean the Join phone failed opening a TCP connection, **before TLS certificate verification**. No successful handshake was established.

Screenshots show the Host QR but do not establish that the Host scanned the reply QR and reached **Start listening**, or that the listener remained active when Join attempted to connect. The original host timeout was only 30 seconds; a phone that connects later can encounter a refused socket.

### Next focused patch and test
- Increase the **opt-in test listener** from 30 to **60 seconds**, preserving explicit time limits and all existing TLS/pinning restrictions.
- Split `ConnectException` into **HOST-NOT-ACCEPTING** (likely no listener/port blocked) and `NoRouteToHostException` into **NETWORK-UNREACHABLE**; treat categories as investigative clues, never proof of a particular root cause. Do not surface raw network addresses.
- Explicitly instruct **Host**: scan Join's reply QR → tap **Start listening** → wait until the screen says **Host is ready**.
- Only then instruct **Join** to tap **Connect to host**; both phones remain on the same Wi-Fi with guest/client isolation off. No WAN required. Keep both screens open.
- Observe and record the Host and Join status codes independently. If Host says **LISTEN-UNAVAILABLE**, inspect binding; if Join says **HOST-NOT-ACCEPTING**, ensure the Host is still listening; if **NETWORK-UNREACHABLE** or timeout, inspect Wi-Fi isolation/routing; if handshake error appears, only then investigate TLS identity.
- No real text or audio is enabled. No diagnostics containing raw IPs, QR payloads or fingerprints should be shared publicly. Because QR contains a time-limited network address and public identity data, avoid posting live QR screenshots.

## Third physical test — November 4, 2026: TLS handshake failed

Following connection listener/timing corrections, user tested with a **Samsung Galaxy A32 5G running Android 13** and another Android phone; at least one phone reported `TLS-HANDSHAKE-FAILED`. That is evidence the test reached TLS negotiation; **it is NOT evidence of completed authentication or secure messaging**. The exact failure cause remains unconfirmed.

Android docs describe `DIGEST_NONE` as usually necessary for Keystore keys used by TLS/SSL signing. Our v1 EC key allowed only SHA-256, so test a new explicitly approved **v2 dual-digest identity** (SHA-256 + NONE), with new public fingerprint and new QR scans. Do **not** silently rotate or weaken pins. Source: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder

The new app must classify identity signing failures privately; then test **both phones** and document sanitized results. A valid Android 13 TLS 1.3 provider exists, so an older handset is not the presumed cause. Full details: `docs/ANDROID_DEVICE_COMPATIBILITY_RESEARCH_2026-11.md`.

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
