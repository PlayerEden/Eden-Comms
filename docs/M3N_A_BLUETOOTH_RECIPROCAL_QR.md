# Eden Comms M3n-A — In-person Bluetooth two-QR preparation

**Checkpoint:** November 6, 2026  
**Source:** `app/src/debug` only. **No actual Bluetooth messages, mutual application cryptographic authentication, user approval or production release enablement.** Real phones and independent security assessment are intentionally deferred by the owner.

## Why this is an important but limited milestone

The existing Wi-Fi chat uses **mutually pinned TLS 1.3 sockets, fresh reciprocal QR certificates/nonces, a four-record challenge exchange, an RFC 9266 TLS exporter agreement, and two human approvals plus two final confirmations** before development-only typed text. An Android Classic `BluetoothSocket` provides RFCOMM `InputStream`/`OutputStream`, not a `java.net.Socket` or `SSLSocket`. The current `Tls13PeerContext.wrapConnectedClient(rawSocket: java.net.Socket, ...)` cannot be used to authenticate that RFCOMM stream. Merely opening a secure OS Bluetooth socket or seeing a device name/SDP UUID does not meet our peer identity, approval or application encryption requirements.

**M3n-A therefore builds the real *in-person QR bootstrap* without claiming that it cryptographically authenticates the Bluetooth socket.** Future M3n-B must implement and independently review a TLS `SSLEngine` wrapper over the RFCOMM byte stream (or an equivalently reviewed authenticated, encrypted transport), with exact peer fingerprint pinning, fresh handshake transcript and channel binding. Only **then** may we attempt bilateral approvals and bounded debug test text in M3n-C.

## Implemented now in the debug APK

Under **Settings → Device and developer tools → Bluetooth discovery test**, a separate QR panel uses the app's existing **offline QR drawing and CameraX scanning components**, not an external scanner or cloud service:

1. **Local identity** — reuse the existing Android Keystore-backed EC/X.509 identity. If absent, **CREATE LOCAL DEVICE IDENTITY** is a separate explicit tap; this does not silently regenerate or replace a key.
2. **Phone A: Host QR** — tap **1 — SHOW MY FRESH HOST QR**. A versioned canonical `ECBH1` code contains the full local 32-byte X.509 certificate SHA-256 pin, a new 16-byte nonce and creation time. It contains **no IP address, MAC, Bluetooth name, user identifier, phone number, message or private key**.
3. **Phone B: Join QR** — tap **1 — SCAN OTHER PHONE'S HOST QR**, physically scan the host's display and show the generated `ECBJ1` reply. The reply contains the scanned host nonce, the join phone's own full 32-byte public certificate pin and a new 16-byte nonce. The join phone labels this step **unconfirmed**; showing a QR cannot prove the host received it.
4. **Phone A: Scan reply** — tap **2 — SCAN OTHER PHONE'S REPLY QR**, validate version, exact host nonce, distinct peer pin and freshness. Both devices now have a canonical ordered **host pin / join pin / host nonce / join nonce** transcript in memory.
5. **User action to test the underlying socket** — The existing **HOST ONE-TIME BLUETOOTH TEST LINK** and **JOIN STAGED UNVERIFIED SIGNAL** buttons now require that the appropriate side has a fresh prepared QR transcript (max five minutes from host creation). Radio discovery and selecting a signal are still required on the joining device; this opaque staged signal **is not matched to a QR-scanned physical device**. The socket remains strictly an unverified 45-second debug RFCOMM experiment with **no application payload I/O**.
6. **Cleanup** — Cancel erases prepared QR metadata. Leaving the activity, destruction or expiry rejects any pending QR binding; future new QR attempts replace prior nonces and fingerprints in RAM. OS permission/discoverability UI may temporarily interrupt the foreground activity; callbacks re-check permission, current role/QR freshness and foreground state before starting a socket. No QR metadata is intentionally written to app storage, backups, logs or QA exports.

The helper `BluetoothQrPairingCodec.Prepared.binding(now)` creates the existing `SessionProofBinding` structure for **future** channel-verified authentication but this M3n-A path does **not** run `SessionProofExchange` on RFCOMM, exchange signatures or keys, call socket `getInputStream`/`getOutputStream`, calculate a TLS exporter or enable chat.

## Negative tests and safety rules

- Unit tests exercise role parity, complete canonical QR transcript agreement, zero/non-32-byte certificate pin rejection, self-pair rejection, wrong-host/replayed replies, unknown version, lower-case/noncanonical encodings, malformed/overflow time, 300-second expiry and future skew, copy isolation and clear/zeroization of local in-memory copies.
- Python source tests require both real local CameraX QR and QR display integration, explicit identity creation, both host/join admission checks before the debuggable radio socket, and **zero** RFCOMM application I/O, chat codec, audio, alternate network or hidden Bluetooth permissions.
- `scripts/check_security_review_boundary.sh` now enforces both QR roles' guards, compiler-safe independent source isolation and absence of unreviewed stream reads, writes, insecure RFCOMM or persistence in the debug QR/RFCOMM paths.
- Existing same-LAN QR/TLS debug text implementation is untouched; `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain **false in release**. No new permissions or external libraries. Current debug Bluetooth permissions remain SCAN, CONNECT and ADVERTISE only.
- **Do not interpret “QR prepared” as authenticated.** A malicious or unrelated Bluetooth device can still be staged, and the peer on the RFCOMM socket has not proven possession of the QR identity key or matching channel transcript. This is not a secure encrypted application conversation.

## Still required before Bluetooth text

**M3n-B:** a bounded, cancelable, testable, authenticated encrypted byte-stream adapter for `BluetoothSocket`, with mutual Keystore-backed certificate identity proof, full 32-byte pins from *both* in-person QRs, fresh per-link challenges, provider-dependent TLS/exporter capability checks and explicit protocol/crypto expert review. No plaintext fallback, no reuse of Wi-Fi IP assumptions, no connection trusting a Bluetooth bond or a name.

**M3n-C:** current-connection-specific two-human approvals, two final confirmations, bounded debug text, truthful delivery status and drop/timeout/replay behavior. Real hardware QA and independent protocol review must still precede any production release enablement.

**Owner-deferred two-phone QA**: validate QR camera on small/large devices, pairing code expiry and role mismatch, camera permission denial, stale/reused QR, host and join buttons locked before preparation, exact Linux/Android radio functionality, system discoverability prompts, background cleanup, rotation, and that no Bluetooth text/audio is accepted. Do not log or share QR strings, pins, radio addresses or packets.

## References

- [Bluetooth M3m real unverified RFCOMM socket](M3M_DEBUG_RFCOMM_LINK.md)
- [Wi-Fi M3e debug two-way text](M3E_DEBUG_TWO_WAY_TEXT.md)
- [Core completion plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md)
- [Bluetooth track #59](https://github.com/PlayerEden/Eden-Comms/issues/59), [physical QA #26](https://github.com/PlayerEden/Eden-Comms/issues/26), [independent security gate #42](https://github.com/PlayerEden/Eden-Comms/issues/42)
- Official Android [BluetoothSocket](https://developer.android.com/reference/android/bluetooth/BluetoothSocket) and [SSLEngine](https://developer.android.com/reference/javax/net/ssl/SSLEngine)
