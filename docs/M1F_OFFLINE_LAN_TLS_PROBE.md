# M1f — Offline LAN mutual TLS handshake probe (development only)

Date: 2026-11-03.
Status: **one-shot authenticated TLS handshake tester**, NOT a working chat.

## What is implemented
- When both devices have explicitly created an Android Keystore signing identity, a user may type the **other phone's full certificate fingerprint** after checking it on the physical phone.
- Explicit acknowledgement is required before a host or join operation can begin.
- Host binds **only** to the selected Wi-Fi interface's IPv4 address, port 42519. Join uses **numeric IPv4 only**, an address on the same Wi-Fi subnet and Android's network-specific socket factory. No DNS or cloud rendezvous.
- A Wi-Fi router **without Internet/WAN** is intentionally supported; no cellular-network fallback.
- Client and server use the previously implemented X509ExtendedKeyManager (local identity) and exact pinned X509ExtendedTrustManager (remote identity). Server requires client certificate, and TLS 1.3 is the only enabled protocol.
- Both sides run TLS handshake; reject mismatched fingerprints/certificates, downgrade or handshake timeout. The socket closes immediately on success or failure.
- **No application messages are sent or accepted** at this stage. The only network data is the TLS negotiation.
- Runtime: opens a short-lived connection only on direct user action, closes after the probe or on screen exit/cancel. No background discovery or always-running service.
- Kotlin unit tests cover strict private/subnet-only IPv4 policy and rejection of names/invalid addresses.

## Permissions and privacy
- Adds Android INTERNET permission because raw **local IP sockets** require it, and ACCESS_NETWORK_STATE to find the Wi-Fi interface. INTERNET permission does **not** mean the app contacts an external domain or server. This code uses no DNS, telemetry, cloud, tracker or third-party SDK.
- Bluetooth, Wi-Fi Direct, location, camera, contacts and microphone permissions remain **absent**. No background scanning.
- Debug screenshots enabled to let testers share UI errors. Do not use real private data during development.

## Blockers before enabling messages
1. Complete two-phone, LAN-only, no-WAN tests using generated Android Keystore identities. Desktop/JVM tests cannot prove this handshake actually interoperates with two Android devices.
2. Proper user-friendly mutual QR enrollment of complete authenticated identity material. Manually typed full pins are a deliberately inconvenient *development* substitute; never call this public-ready pairing.
3. Pair the secure TLS channel to the SessionSecurityGate with authenticated two-sided confirmation. Keep all message/audio flags false before that end-to-end gate.
4. Correct behavior under device rotation, background/foreground app changes, connection cancellation, clock skew, certificate expiration, attacker on LAN and Wi-Fi isolation.
5. Negative MITM/replay/downgrade testing plus actual network capture. Android 17 local network permission behavior also needs targetSdk-specific verification.
6. Production security review and a complete real chat/data-retention implementation before release claims.

## Test sequence for two phones
- Phone A and Phone B connect to **same Wi-Fi router**; turn mobile data off. Router WAN may be unplugged.
- On each phone, open **Device identity** and tap **Create local identity**. Check the public fingerprint shown.
- Open LAN handshake test. Physically compare the other person's full fingerprint and type it. On A select Host; note A's local Wi-Fi IPv4. On B enter A's host IPv4 and select Join.
- Both phones should report **mutual TLS 1.3 handshake passed**. This test sends no chat messages.
- Intentionally change one fingerprint digit and repeat: connection MUST fail.
- Turn router Wi-Fi off, or disconnect a phone; handshake MUST fail, without switching to cellular.
- Record success/failure and phone Android versions in the PR without sharing real local IP addresses or fingerprints publicly.

## References
- https://developer.android.com/reference/android/net/ConnectivityManager
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/reference/javax/net/ssl/SSLSocket
