# Eden Comms M3s-C1 — Explicit unverified Wi-Fi Direct target link

**November 7, 2026 · debug APK only · CI not equivalent to device QA.**

## Purpose and critical distinction

This is an **Android operating-system radio-group negotiation test**, not
secure pairing. Earlier M3r shows anonymous Wi-Fi Direct devices, and M3s-B
offers an independent three-QR public identity preparation. Those two are
**not bound to each other** by Android. A Wi-Fi Direct OS group can form with
an arbitrary nearby device, including one that is not running Eden Comms.
A successful Android `connect()` ActionListener says the **request** was
accepted, not that the intended peer formed a link or authenticated.

Do not use the diagnostic to communicate sensitive or emergency information.

## Implemented debug flow

1. Developer Tools → Wi-Fi Direct preflight → OPEN 30-SECOND DISCOVERY TEST.
   Existing explicit scan, permission, Location Mode, positive Android P2P
   radio state, bounded platform peer-list reads and anonymous 8-device
   candidate ledger remain.
2. Tap an **Unverified device N** row. This only stages a local ordinal.
   The UI explicitly warns that QR metadata does **not** identify the radio
   target and that Android might display a system invitation.
3. Tap the **separate** REQUEST 18-SECOND UNVERIFIED LINK TEST button.
   Only on Android API 29+, while still foreground, permission/Wi-Fi/Location
   Mode ready, discovery SCANNING and with a selected candidate.
4. The ledger transfers the selected opaque `WifiP2pDevice` to a single
   debug-only platform adapter **after wiping the rest of the candidate
   session**. Discovery ends, closes its channel and requests discovery stop.
   No target string ever reaches UI, QR metadata, logs or persistence.
5. The adapter starts its own bounded channel, explicitly checks Android's
   positive P2P radio state, then `requestGroupInfo`. If Android already
   reports a group, **stop without calling connect/removeGroup**.
6. The *sole* M3s-C1 platform connector reads the transient target
   `deviceAddress` only to create `WifiP2pConfig.Builder` via
   `MacAddress.fromString`, with `enablePersistentMode(false)`.
   It then calls `WifiP2pManager.connect` once. This is **not** a trusted
   identity, nor a saved MAC address.
7. ActionListener success is displayed as **Android accepted REQUEST**.
   Later bounded polling of `requestConnectionInfo` can display **Android
   reports group FORMED**, but never "verified peer" or "connected Eden
   Comms contact". The app does not read or display group owner IP or
   credentials, inspect remote certificate pins, or open any application
   TCP/UDP socket.

## Stop, failure and best-effort cleanup

The link attempt runs for a maximum of 18 seconds after tap. A foreground
monitor checks prerequisites each second. Permission loss, Wi-Fi/P2P off,
Stop, leaving the screen, channel loss, platform failure and timeout
invalidate the token before any further callbacks. If Android connection
negotiation was not submitted, close the new channel without touching a
previous group. If submitted, request `cancelConnect`, then `removeGroup`,
then `requestGroupInfo` to verify that Android presently reports no group.
Cleanup callback processing is capped at four seconds; a failed/missing
callback yields **cleanup UNCONFIRMED**, not an assertion that the radio
group disappeared. OS callbacks can occur after the check, and force-stopping
the app may leave a system-owned connection. Inspect Android Wi-Fi Direct
settings if group removal is uncertain. Do not automatically retry or
persist group configuration.

A single connection controller owns the OS API. The M3q discovery Activity
still has **no direct** `requestConnectionInfo` or `connect` calls, and
does not gain app messaging authority. The existing independent M3s-A local
group-hosting feature is unchanged.

## Not provided

- No guarantee that the selected radio is the phone from M3s-B's three QR
  codes, nor a signed identity or authenticated TLS session
- No bilateral human approval bound to a live encrypted channel
- No TCP/UDP, user text, acknowledgements, voice/PTT, contact list, saved
  trusted peer, background scan, account or Internet server
- No new permissions, production component or release capabilities
- No real-device testing or external professional security audit

Android official documentation:
- https://developer.android.com/develop/connectivity/wifi/wifi-direct
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder

## Verification and later step

Pure Kotlin tests cover start/confirmation gates, rejection of existing
groups, request-versus-formed distinction, timeout/Stop, cleanup and stale
callback fencing. Python source audits keep the one platform connector
isolated, prohibit direct client network I/O, and assert production
permissions/capabilities are unchanged. PR Android compile, unit/security
regression, APK checks and fresh merged-main CI must pass.

**Hardware gates not completed:** two phones (API 29–current), P2P discovery
and selection, correct/incorrect device invitations, OS dialog cancellation,
preexisting group, link formation, Stop/background/rotation/process kill,
timeout, permission revoke, Wi-Fi off, group removal and retry, OEM failure
recovery, accessibility and network restoration.

**Next M3s-C2/M3t:** after physical diagnostics, a reviewed channel-binding
protocol must prove exact certificate/key possession on a freshly created
transport with all QR nonces, expiry, roles and selected link bound; human
bilateral consent must occur *after* authentication. Encrypted short text
only after professional review, end-to-end two-phone acceptance, and a
separate release decision.

Rollback: revert M3s-C1 PR. No persistent message/peer migration.
