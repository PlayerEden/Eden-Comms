# Eden Comms — M3s-B: in-person Wi-Fi Direct QR identity preparation

**Checkpoint: November 7, 2026 · debug-only · no radio connection, physical QA or independent security review.**

## Why not put a Wi-Fi Direct address in the QR?

Android `WifiP2pManager.requestDeviceInfo` can return a redacted local address
without the highly restricted `LOCAL_MAC_ADDRESS` permission. Wi-Fi Direct peer
discovery also does not attest that a radio belongs to a particular Eden Comms
user. Neither a P2P address nor an anonymous device-list ordinal should be used
as the app's persistent/trusted identity. This milestone requests **no MAC
permissions** and never reads/displays a device address.

Android references:
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- https://developer.android.com/develop/connectivity/wifi/wifip2p

## Implemented in the debug APK

Developer Tools → Wi-Fi Direct preflight →
**OPEN IN-PERSON WI-FI DIRECT QR PREPARATION**. This works offline and without
activating P2P discovery or a P2P group. It reuses the existing in-app
CameraX/ZXing QR camera and QR renderer, plus the existing local Keystore
certificate identity. Local identity creation only happens on an explicit tap.

A three-step, nontransport protocol:

1. **Host QR:** exact public certificate SHA-256 fingerprint (32 bytes),
   fresh 16-byte random challenge and issuance time.
2. **Joiner QR:** a second public fingerprint and fresh challenge, plus the
   exact echoed host challenge. Self-pairing and wrong sessions are rejected.
3. **Host acknowledgement QR:** both challenges and a 16-byte truncated,
   domain-separated SHA-256 digest of the canonical public metadata.
   The joiner must scan this third QR. Both phones can then display the
   same short `XXXX-XXXX` visual comparison code; two humans compare it
   face to face.

Protocol role tags are `ECWDH1`, `ECWDJ1`, `ECWDA1`; QR payloads must be
printable ASCII and **no longer than 170 characters** to fit the existing QR
renderer. Hex is uppercase and timestamps canonical. Max age is 180 seconds,
with up to 45 seconds of future clock skew. Invalid tags, sizes, noncanonical
hex, timestamps, altered challenges, wrong role, expired invitation and
same-device public fingerprints reject without echoing sensitive fields.

No QR includes an IP address, Wi-Fi MAC, SSID, group password, contact,
location, account, message or private key. `FLAG_SECURE` blocks ordinary
screen capture and app-switcher previews (it does not prevent another camera
from photographing a physical screen). The Activity clears public challenge
buffers, QR payloads and the short code on Cancel, leaving the screen,
destroy or expiry. The existing Keystore identity itself is intentionally
preserved.

## Security truth and remaining gates

A matching code demonstrates that **the public QR transcript data matches**,
not that any device possesses the corresponding private key, that an M3r
selected Wi-Fi Direct candidate is the same phone, that a group is joined,
or that the human on the other side approved a secure session.

These QR messages are not secret and can be copied or relayed. A man in the
middle can imitate unauthenticated QR records. The visual code is only a
manual cross-check, **not authorization**. The host's display of its
acknowledgement is not proof the joiner received it.

This feature has **no network API**, no `requestDeviceInfo`, no
`LOCAL_MAC_ADDRESS`, no `WifiP2pManager.connect`, no `createGroup`,
socket, TLS handshake, signed challenge/response, message, voice/PTT,
contact store or saved trust. It never converts an M3r candidate selection
into a connection or unlocks `ReleaseCapabilities.CAN_CONNECT`,
`CAN_SEND_MESSAGES` or `CAN_TRANSMIT_VOICE`.

Two-phone camera/QR flow QA, permission deny, expiration, wrong-device
and malicious relay tests **NOT PERFORMED**. Independent security/protocol
review **NOT PERFORMED**. Ordinary debug Keystore identity, APK update
preservation and OEM/device behavior are not certified.

## M3s-C proposal

Add a separately reviewed, temporary targeted P2P group join with manual
explicit consent and strict stop/removal. The candidate itself cannot be
assumed to match the QR party. Only a fresh cryptographic private-key
signature or exact pinned mutual TLS 1.3 exchange, securely bound to the
chosen radio/group and QR challenges, can establish the remote peer's
cryptographic identity. Bilateral human approval must follow on that
authenticated session. No user communication before separate real-device
QA and professional protocol review.

Rollback: revert M3s-B PR. No saved peer or app data migration.
