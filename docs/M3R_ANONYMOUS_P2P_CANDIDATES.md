# Eden Comms M3r — Anonymous Wi-Fi Direct candidate preview

**Checkpoint:** November 7, 2026. Debug-only, in-memory UI staging.
Two-phone hardware testing and independent protocol review are not performed.

## Shipped in source

The existing M3q developer-only **START 30-SECOND SEARCH** stays unchanged.
Once the current search receives Android's successful `discoverPeers` callback,
M3r may call `requestPeers` (the Android API that actually returns a P2P
device list). Nearby peer-list-change broadcasts can also trigger a refresh,
subject to a one-request-at-a-time and twelve-read ceiling.

The new `DebugWifiDirectCandidateReader` is the sole adapter allowed to
request a Wi-Fi P2P peer list. At every asynchronous callback it checks that
this is the exact current user-started discovery, screen is foreground,
platform permission/radio/Location Mode is still granted/available, and
the original candidate-reader instance and session token remain current.

The `DebugWifiDirectCandidateLedger` stores **at most eight opaque Android
device objects in RAM** for this single attempt. It deduplicates internally,
never reorders existing list labels, and exposes only `Unverified device 1`,
`Unverified device 2`, etc. It does not expose or return device objects via
the UI API. Names, MAC addresses and wireless metadata are not displayed,
logged, saved, copied or shared. A selected ordinal is an **on-screen
preview only**, NOT a pairing request, a radio connection, a trusted contact,
or verified Eden Comms identity.

The existing 30-second maximum, STOP button, background cleanup, stale
callback fencing, permissions/radio/Location Mode checks, platform error
handling and cancellation remain. All candidate handles and selections
are wiped on each stop, timeout, radio failure, app background or restart.

**These may be ANY nearby Wi-Fi Direct-capable devices; none are proven
to run Eden Comms.** Selection cannot trigger `connect`, `createGroup`,
network sockets, TLS, pairing, text or voice.

## Automated boundaries and regressions

- Kotlin JVM tests: stable anonymous labels, deduplication, eight-item cap,
  selection invalid ordinals, zero stored handles after clear, and rejecting
  old attempt tokens.
- Python security guard: `requestPeers` confined to one debug reader with
  callback permission and current-attempt fences; forbidden identifiers,
  persistence, network/group/send APIs; no release permissions or production
  `CAN_*` flags changed.
- Existing M3q source and all older Bluetooth, LAN, privacy and exact APK
  permission inventories must still pass.
- No new permissions, components, dependencies, login, remote server or
  persistent storage.

## Risks and release gates

A neighboring Android device might not be an Eden Comms phone. Anonymous
ordinal labels cannot establish which human/device is intended. M3s-C1
now exposes a **separate, explicitly human-confirmed, debug-only, time-limited
UNVERIFIED platform link request** after candidate selection. This is
not a verified in-person target, QR identity match or authorized app session.
No app text, voice, group persistence or production trust is enabled.
Real-device negative tests and independent cryptographic verification remain
required before any sensitive communication can proceed.

Actual API/OEM behavior, callback ordering and 30-second discovery remain
unverified without hardware. The UI is not suitable for sensitive or
emergency messaging. Debug Android device objects are held temporarily,
so the prior M3q guarantee of zero peer-object access applies to M3q only,
not to this new, explicitly scoped M3r candidate stage.

## Next development milestone

**M3s design & implementation** should separate P2P group negotiation
from peer authentication: a temporary, explicitly user-approved P2P
connection may only lead to a separate fresh QR/pinned TLS/exporter and
bilateral consent diagnostic, never production messaging by itself.
Before enabling even a debug P2P connection, evaluate first-hand device
recognition to avoid attaching to an arbitrary nearby device.

Two-phone physical QA and independent protocol assessment remain separate
uncompleted security gates.

Android references:
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager

Rollback: revert M3r; M3q still runs bounded, anonymous event-only scanning,
with no storage migration.
