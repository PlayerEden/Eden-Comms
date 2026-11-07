# Eden Comms M3q — Bounded foreground Wi-Fi Direct discovery

**Checkpoint:** November 7, 2026. Android **debug APK only**. M3q is a
non-authenticated radio diagnostic. Neither two-phone acceptance nor an
independent security/protocol audit has been performed.

## Implemented scope

- Developer Tools → Wi-Fi Direct device preflight → **OPEN 30-SECOND DISCOVERY
  TEST**, only after Wi-Fi, device Location Mode, API-specific permission and
  advertised platform support appear ready.
- Dedicated, nonexported `DebugWifiDirectDiscoveryActivity` in `src/debug`.
  User explicitly chooses **START 30-SECOND SEARCH**. No automatic start on
  entry, background resume, rotation or permission grant.
- Real `WifiP2pManager.initialize` opens a per-attempt channel. On API 29+
  `requestP2pState` supplies positive P2P radio readiness; on API 26–28,
  a foreground P2P state broadcast must report enabled. Only then may
  `discoverPeers` be attempted. The async ActionListener success is reported
  only as **OS discovery accepted**, never as peer found or verified.
- A 30-second deadline covers **radio readiness + start + discovery**. One
  active attempt at a time. The user can STOP. Background, permission loss,
  Wi-Fi/Location Mode off, P2P disable, channel disconnect, platform failure,
  event cap and timeout all stop. The adapter best-effort sends
  `stopPeerDiscovery` and closes channels (API 27+). No automatic retry.
- Dynamic foreground-only `WIFI_P2P_STATE_CHANGED_ACTION`,
  `WIFI_P2P_DISCOVERY_CHANGED_ACTION` and
  `WIFI_P2P_PEERS_CHANGED_ACTION` receivers. The last is counted as up to 32
  **anonymous list-change events**, **not actual device counts**. No
  `requestPeers`, `EXTRA_P2P_DEVICE_LIST`, device names/MACs,
  Bluetooth bonds, location coordinates or persistent peer data.
- A pure Kotlin `DebugWifiDirectDiscoveryGate` enforces one-shot transitions,
  event cap and token reference identity. Old callbacks cannot revive stopped
  attempts. Unit tests exercise false radio, premature callback, late
  onSuccess, restart, Stop, background and event flood.
- Debug-only `CHANGE_WIFI_STATE` and `CHANGE_NETWORK_STATE` **normal**
  Android permissions are added according to the official P2P setup guidance,
  along with existing M3p `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES` and
  legacy capped `ACCESS_FINE_LOCATION`. Exact APK and privacy allowlists
  updated. Release/main manifest unchanged and `CAN_*` all false.

## Strict exclusions and limitations

- **No peer selection or connection, group formation, app-socket or TLS
  session, sharing, typed text, audio, voice/PTT, saved contact or release
  capability.** Discovering radio activity does NOT verify Eden Comms identity.
- A positive Android discovery-start callback does NOT prove a peer exists.
- System events are not authenticated identities. A conservative false state
  report can stop a scan. Some OEMs may not deliver state broadcasts
  consistently. On API 26–28 the test may time out while waiting for the
  state broadcast. This is intentional fail-closed behavior, not a confirmed
  compatibility failure.
- Android platform stop is best effort, async, and not equivalent to
  proving radio shutdown. Actual OEM behavior remains to be measured.
- Debug APK remains unsuitable for private or emergency communication.

## Automated verification contract

JVM: `DebugWifiDirectDiscoveryGateTest`, previous M3o/M3p Kotlin tests and
existing Bluetooth/LAN state security tests.

Python: `test_m3q_wifi_direct_discovery_boundary.py`, existing M3o/M3p,
Bluetooth and launch-surface permission guards. CI Android compilation and
debug APK permission inventory must pass on PR and merged main.

## Deferred owner physical QA (NOT PERFORMED)

Use two Android phones only when owner requests testing: Android 8–12L and
13+, visible explicit start, detection vs no-peers, 30-second timeout,
Stop, rotation/background, app kill, denied/revoked permission, Wi-Fi off,
Location Mode off, P2P disabled, OS channel loss, wrong device events,
accessibility and repeated start/stop. Verify no identifiable devices or
messages appear and no radio discovery remains after exit. Avoid recording
MACs, QR contents, personal location or packet captures.

## Next

M3r: **anonymous, capped peer selection review**, only after exploring a
recognizable Eden Comms signal handshake; then a separate P2P group-owner
negotiation PR with no trust inferred from radio presence. Authenticated
messaging only after independent protocol review and real-device acceptance.

Official Android references:
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager.Channel

Rollback: revert M3q; the preceding M3p preflight remains. No migration,
stored peer, message or release permission change.
