# Eden Comms M3m — Real one-time debug RFCOMM host/join (no messages)

**Date:** 2026-11-06  
**Scope:** Android 12+ debug-build-only Bluetooth Classic **secure RFCOMM socket establishment**, not authenticated app communications. **Android CI compiles and checks this implementation; no real phones have tested it yet.**

## What is now implemented

**Settings → Device and developer tools → Bluetooth discovery test** includes the M3k/M3l actual Bluetooth Classic inquiry and temporary numbered signal selection plus three new controls:

1. **HOST ONE-TIME BLUETOOTH TEST LINK**: The host explicitly taps the action, grants Android 12+ Nearby devices **BLUETOOTH_CONNECT** and **BLUETOOTH_ADVERTISE**, then accepts Android's **ACTION_REQUEST_DISCOVERABLE** dialog requesting up to **120 seconds** of discoverability. Only after Android returns an approved duration and the app is foreground does a dedicated worker thread call `listenUsingRfcommWithServiceRecord` with a fixed, non-secret diagnostic UUID and `BluetoothServerSocket.accept`. A **60-second host wait** closes the server socket if no connection happens.
2. On the second phone: tap **START 30-SECOND TEST SEARCH** (explicit **BLUETOOTH_SCAN**), stage an anonymous unverified signal, then tap **JOIN STAGED UNVERIFIED SIGNAL** and separately grant **BLUETOOTH_CONNECT**. The worker stops discovery before calling `BluetoothDevice.createRfcommSocketToServiceRecord` and blocking `BluetoothSocket.connect`. An **18-second join timeout** closes the socket if needed. Bluetooth may show its own OS-level bonding prompt. No address/name is extracted by app code.
3. An accepted or connected `BluetoothSocket` only yields the on-screen label **RFCOMM socket opened (UNVERIFIED)**. The code NEVER calls the socket's InputStream or OutputStream, reads data, writes packets, exchanges keys, performs Eden mutual identity approval, sends chat text or records voice. The socket automatically closes after **45 seconds**, or sooner when the user taps **DISCONNECT**, the screen backgrounds/destroys, permissions disappear or a connection fails. Blocking socket calls run on worker threads; `close()` unblocks them.

**Do not call this encrypted Eden Comms messaging.** A secure Bluetooth Classic socket may use Android's link-level security, but no authenticated app session, QR-pinned TLS, end-to-end secure channel, verified peer identity, human approval handshake, or RFCOMM message framing has been performed. A remote device supporting the debug service UUID could connect; UUID discovery is not identity verification. The connected state is only an OS link diagnostic.

## Security and privacy boundaries

- All new `DebugRfcommLinkController`, `DebugRfcommAttemptGate`, revised `DebugBluetoothDiscoveryActivity` and `DebugBluetoothCandidateLedger` code remains under `app/src/debug/java`. The Activity remains **non-exported**, reached only through the app's debug-gated Developer Tools.
- The `app/src/debug/AndroidManifest.xml` adds **BLUETOOTH_CONNECT** and **BLUETOOTH_ADVERTISE** alongside prior **BLUETOOTH_SCAN**, all explicit at time of use. The `app/src/main/AndroidManifest.xml` remains LAN+QR only, with no Bluetooth, location, contacts or microphone permissions. **Production** `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain false.
- A host asks Android to become discoverable for 120 seconds; Android (not the app) manages visibility and may keep the device discoverable even if the app disconnects or closes during that period. The user consent and duration must be clearly understood.
- The join target is a short-lived opaque `BluetoothDevice` object from the previous scan; no friendly name/MAC, peer identity, bond database, logs, network endpoint, sensitive QA or persistent discovery history is exposed by the app. **Nothing identifies it as Eden Comms**; unrelated or malicious Bluetooth devices may appear.
- A `DebugRfcommAttemptGate` uses per-attempt token identity and strictly transitions IDLE → CONNECTING → LINK_OPEN → STOPPED. It rejects stale/foreign callbacks, connection completion after cancellation, permission loss, hidden-UI connection attempts and auto-resume. A queued end-state UI callback is ignored if a newer session started.
- Server and client sockets are closed from the UI/cancel thread on timeout/exit; no busy polling, unbounded transport lifetime, automatic retry or plaintext RFCOMM fallback. The connected OS socket is not retained across navigation or app process death.
- No Bluetooth SDP service UUID should be treated as an authentication credential; it is fixed and public by design.

## Automated evidence

- Android CI must run `testDebugUnitTest` and `assembleDebug`, compiling actual host and join calls and checking the packaged permission set.
- Kotlin `DebugRfcommAttemptGateTest` covers both roles, foreground+permission admission, real socket completion transition, rejection after stop, all end codes, cross-generation replay and foreign tokens. Existing scan/candidate tests stay intact.
- Python `test_m3m_debug_rfcomm_contract.py` and security shell guards require actual RFCOMM host+join APIs, worker threads, timeouts and socket close, explicit Android permission/discoverability prompts, and forbid socket stream reads/writes, insecure RFComm, storage, logs, message codec and production radio permission leakage.
- `scripts/check_preview_permissions.sh`, `scripts/audit_play_surface.py` and source-set boundary checks enforce the three explicitly reviewed debug Bluetooth permissions plus original main LAN/QR/Camera and the AndroidX dynamically generated merged permission.
- **These checks are source/VM/packaging evidence, not proof of Android Bluetooth interoperability, link establishment, OS prompt UX or application cryptographic security.**

## Owner-deferred physical QA — NOT YET DONE

When ready, use two Android 12+ phones with Bluetooth enabled, ideally different OEMs:

1. On host: explicit HOST action → separately request Nearby devices permissions → Android 120-second discoverability prompt. Grant, check **waiting** label. Deny visibility, verify no host socket created. Revoke CONNECT, ensure closure. Avoid private/sensitive data.
2. On client: grant scan and discover host; **stage the correct anonymous signal manually** (UX is intentionally crude until verified peer naming/advertisement is designed), then tap JOIN and approve CONNECT. The OS may show a pairing/bond dialog. Observe only **unverified socket open** messages; no chat composer/messages.
3. Verify host 60-second and join 18-second connection timeouts, 45-second connected-socket auto-close, explicit Disconnect, app home/background, Bluetooth off, permission revocation, stale candidate, return from OS settings, no undisclosed resume and no automatic chat.
4. Check Android system discoverability persists until its granted duration, even if the app itself disconnects. Do not promise instant system visibility cancellation.
5. Confirm Android rotation, narrow screens, TalkBack and ongoing LAN text remain unaffected. Use sanitized counts/status labels only; never capture device MACs, QR pins, chat content or debug packet traces.

This milestone does **not** complete Bluetooth communications. **M3n** must first perform independently reviewed transport-neutral cryptographic identity proof, fresh session binding and both human approvals over this new stream, then test chat **only after all gates**, plus real phone and security review before production.

## Links
- [Complete core plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md)
- [M3k raw inquiry](M3K_DEBUG_BLUETOOTH_INQUIRY.md) · [M3l anonymous signal staging](M3L_EPHEMERAL_BLUETOOTH_CANDIDATES.md)
- [Bluetooth feature track #59](https://github.com/PlayerEden/Eden-Comms/issues/59) · [deferred handset issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26) · [independent security review #42](https://github.com/PlayerEden/Eden-Comms/issues/42)
- [Android Connect Bluetooth devices](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices) · [Android discoverability](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#ACTION_REQUEST_DISCOVERABLE)
