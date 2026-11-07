# Eden Comms — M3o Wi-Fi Direct device capability preflight

Checkpoint: November 7, 2026. **Read-only, debug APK only.** This is an initial
Wi-Fi Direct foundation, **not Phase C1 permission implementation, a discovery
session, a working P2P link, verified messaging, or a security assessment**.

## Implemented

- Developer Tools opens a separate Wi-Fi Direct preflight activity in the
  debug build only. Release has no activity route.
- A one-tap RECHECK reads only whether Android advertises the
  `android.hardware.wifi.direct` feature and whether its
  `WifiP2pManager` service is present. These are *potential capability*
  observations, **not** current radio readiness or an observed nearby peer.
- Pure deterministic policy explains Android 13+ `NEARBY_WIFI_DEVICES`
  versus Android 8–12L `ACCESS_FINE_LOCATION` for future P2P discovery and
  connection, including the separate Location Mode requirement for discovery.
- This stage requests **zero** additional manifest permissions and calls
  **zero** P2P discovery/connection/channel APIs. Nothing is scanned or stored.
- JVM policy tests exercise APIs 26–37, unavailable feature/service, no
  discovery authority, and minSdk. Python guards check debug-only routing,
  release manifest, zero P2P calls and zero new manifest permissions.

Official Android references:
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://developer.android.com/develop/connectivity/wifi/wifi-permissions

## Future work (not implemented)

1. **C1**: Review exact permissions for API 26–32 and 33+, explicit rationale,
   manifest only in debug while experimental, runtime denial/revocation;
   verify radio and Location Mode readiness on devices.
2. **C2**: Foreground-only user-initiated P2P discovery/stop, bounded scans,
   callbacks and group formation, no unverified device trust.
3. **C3**: Fresh mutually authenticated QR/TLS/consent integration only after
   independent protocol review and two-phone physical security acceptance.
4. **Release gates**: Production CAN_* remain false; Wi-Fi Direct text,
   voice and trusted contacts are not available. Real-device tests and
   security review are intentionally still pending.

## Deferred owner two-phone checklist

On owner-approved hardware QA only: compare preflight result against Android
Wi-Fi Direct support on two devices, vary Wi-Fi/Location Mode, test permission
denials and API/version differences after C1 is implemented. Avoid recording
nearby device identifiers or QR materials.

Rollback: revert this PR; the read-only debug entry point and its pure policy
are isolated. No persisted state or new permission needs cleanup.
