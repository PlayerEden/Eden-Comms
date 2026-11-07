# Eden Comms M3s-A — Temporary Wi-Fi Direct local group-host experiment

**November 7, 2026 · Android debug APK only · NOT physically tested.**

## What was implemented

Developer Tools → Wi-Fi Direct preflight → **OPEN 20-SECOND LOCAL GROUP
HOST TEST** on API 29+ with Wi-Fi Direct hardware support, Wi-Fi and global
Location Mode enabled, and the applicable runtime permission granted.

An explicit human START checks current Android P2P radio state and
`WifiP2pManager.requestGroupInfo` before touching it. If Android reports
an existing group, this test stops without creating or removing that group.
Otherwise it submits `createGroup(Channel, config, ActionListener)` with
random, attempt-only network name and passphrase. The config explicitly uses
`WifiP2pConfig.Builder.enablePersistentMode(false)`. The random network
name and credentials are never shown, logged, stored or shared by the app;
Android may broadcast the temporary SSID as part of ordinary Wi-Fi Direct.

The **ActionListener onSuccess** only establishes that Android accepted
the group-creation request. Subsequent `requestGroupInfo` callbacks must
report the local phone as group owner before the UI can display *host
observed*. This is not authenticated connectivity to another person.

The separate, pure `DebugWifiDirectGroupHostGate` rejects stale tokens,
second starts, group creation before the existing-group check, unexpected
group-owner observations, and repeated lifecycle transitions. It requires
a stop/cleanup phase after a group-creation request and never unlocks
messages or normal release networking.

## Resource lifetime

The group is a **20-second maximum request** while the screen is
foreground. STOP, timeout, Wi-Fi/P2P off, permission/Location Mode loss,
platform errors, channel loss, and background trigger cleanup.

If a group-creation request was never submitted, only this app's channel
is closed. If a request *was* submitted, the adapter calls
`removeGroup` and subsequently `requestGroupInfo` to check for a
group still being reported. Cleanup is bounded to four seconds. Because
Android callbacks are asynchronous and device behavior varies, **even a
successful group-absent check is not a proof that the radio can never
recreate a group later**, and an unavailable/failed callback is labeled
*cleanup unconfirmed*. Check Android Wi-Fi Direct system settings if
removal is unconfirmed. No automatic background retry is promised.

## What has NOT been implemented

- No outbound `connect` to an M3r numbered peer, no selected-device
  lookup, no OS invitations to arbitrary nearby hardware, and no
  `requestPeers` in this activity
- No group information, passphrase, network address, peer identifier,
  IP, QR contents or message recorded, copied or displayed
- No TCP/UDP socket, mutual TLS integration, application text, voice/PTT,
  trusted-contact state, account or release capability
- API 26–28 local group host is deliberately **not implemented**; a
  cross-version nonpersistent setup must be separately vetted
- Two-phone hardware acceptance **NOT PERFORMED**
- External professional protocol/privacy assessment **NOT PERFORMED**

All preexisting M3q/M3r discovery, candidate preview, and Bluetooth and
LAN source continue unchanged. Release manifests still have the existing
LAN/QR permissions only, and `ReleaseCapabilities.CAN_CONNECT`,
`CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain false.

## Automated verification

- JVM gate tests: only explicit human request with prerequisites and
  API 29+; preexisting group blocks create and cleanup; request acceptance
  separate from owner observation; Stop/timeout/lifecycle cleanup fences;
  delayed callback and fresh-token generation.
- Python contract: only debug group-host activity may issue
  `createGroup`/`removeGroup`; no `connect`, `requestPeers`,
  sockets, persistence, secret logging, release exposure or permission
  change. PR and post-merge Android CI must pass.

## Deferred owner hardware checks

On controlled Android API 29+ test phones, verify group creation/ownership,
existing-group refusal, Start/Stop, idle timeout, background, process
termination, radio/Location Mode/permission revocation, platform callback
ordering, cleanup failures, and OS group retention. Avoid collecting real
group passwords or nearby peer identifiers. All outcomes are currently
**UNVERIFIED ON HARDWARE**.

## Next M3s-B

Before a **targeted** `connect` to one of the anonymous M3r devices,
design in-person device recognition (e.g. separately scanned fresh
out-of-band identity material bound to the P2P target), test it on two
phones, and keep QR-pinned TLS and human bilateral consent separate from
any OS-level group formation. A group or P2P radio sighting is never
proof of an Eden Comms identity.

Official references:
- https://developer.android.com/develop/connectivity/wifi/wifi-direct
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder

Rollback: revert the M3s-A PR. No application data migration or
credential migration is required.
