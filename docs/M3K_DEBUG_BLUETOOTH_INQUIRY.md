# Eden Comms M3k — Debug-only Bluetooth Classic inquiry (no connection)

**Date:** 2026-11-06  
**Implemented in source:** A real Bluetooth Classic inquiry in **debug** builds on Android 12+ (API 31+), not just a simulated lifecycle. The scanner calls `BluetoothAdapter.startDiscovery()` after the user presses Start and grants Android's Nearby devices permission. **No phones have yet run this code.** The workflow's Android compilation, tests and permission checks are separate from any actual on-device discovery evidence.

## How to use it later (never for sensitive communication)

1. Install a debug-signed tester build **after checking signer/update compatibility**. Open **Settings → Device and developer tools → Bluetooth discovery test**.
2. This is a separate debug-only activity. The user must press **START 30-SECOND TEST SEARCH** and grant the Nearby devices permission (`BLUETOOTH_SCAN`) when Android requests it. No permission is requested merely by viewing the methods page.
3. The actual inquiry runs for at most 30 seconds and counts up to 32 unverified radio observation broadcasts. **No device name, MAC address, bonded peers, identifiers or messages are displayed or stored.**
4. **STOP SEARCH** ends the inquiry. Leaving/backgrounding the activity, permission revocation, radio failure, elapsed-time limit or event cap also closes it. The app unregisters the broadcast receiver and best-effort calls `cancelDiscovery()`; on-device Android radio behavior still requires physical verification.
5. The screen cannot select a specific Eden Comms peer, connect RFCOMM, authenticate a peer, open a TLS session, or exchange text/voice. Other Bluetooth devices can be counted; even a positive count does **not** establish an Eden Comms device nearby.

## Permissions and packaging

- The additional `android.permission.BLUETOOTH_SCAN` permission with `android:usesPermissionFlags="neverForLocation"` lives **only** at `app/src/debug/AndroidManifest.xml`. No `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`, location, mic or contacts permissions have been added. Bluetooth is optional hardware (`uses-feature required=false`).
- The scanner Activity lives in `app/src/debug/java/.../bluetooth/DebugBluetoothDiscoveryActivity.kt`, is `exported=false`, and is launched only through a **FLAG_DEBUGGABLE** guarded Developer Tools action. The same class is **not in a release APK**.
- This first actual inquiry intentionally supports **API 31+ only**. The app's overall `minSdk` remains 26; older phones still use existing local-Wi-Fi functionality. Legacy Bluetooth inquiry and its location-permission implications are deferred pending Android compatibility and user privacy review.
- `NearbyDiscoverySession` (M3j) is now used from the debug source only, preserving its distinct explicit person consent, observed OS permission, bounded scan lifecycle, cancellation and anti-replay state. `SCAN_FINISHED` is a new terminal outcome for an Android inquiry ending normally.
- Hardware broadcasts may be delivered by Android's Bluetooth process. The receiver is dynamically registered for `ACTION_FOUND` and `ACTION_DISCOVERY_FINISHED`; counts are **untrusted** and cannot unlock a session, even if a third-party app could spoof a broadcast. The scanner does not inspect `EXTRA_DEVICE`.
- Production source manifest and all `CAN_*` release flags remain unchanged. Developer-only discovery does not alter PINNED TLS, QR, approval, session-bound final confirmation, or LAN message framing.
- The debug APK and underlying app are **not** independently reviewed secure messaging or emergency communications tools.

## Automated checks and release guards

- Android `testDebugUnitTest` and `assembleDebug` must compile the new debug-only Activity and merged manifest.
- `scripts/check_preview_permissions.sh` and `scripts/audit_play_surface.py` now require the precise debug permission expansion (Bluetooth SCAN only), while still enforcing the exact original LAN+QR main/release permissions, AndroidX merged receiver permission, and no new dependency.
- `scripts/check_security_review_boundary.sh` ensures the scanner remains under debug sources and M3j discovery model remains unreachable from main network/production code.
- Python tests verify the debug-only manifest, guarded routing, OS permission request and cancellation/cleanup paths.
- JVM tests check normal scan-finishing is terminal, cannot authenticate a peer, and stale callbacks cannot start another scan.
- Actual runtime permissions, Bluetooth inquiry behavior, OS backgrounding and permission revocation must be confirmed on real Android devices later.

## Owner-deferred physical acceptance

When ready for phone testing: check an Android 12+ phone with Bluetooth disabled, enabled, permission initially denied and granted, then revoked mid-scan, app backgrounded and returned, Stop tapped, and inquiry time limit. Count observations without recording addresses or device names; ensure no Bluetooth pairing dialog or message send appears, no radio activity remains after Stop, and no scan silently resumes. Check the normal LAN test is unaffected. Repeat on a second OEM if possible.

## Next steps

**M3l:** Add debug-only **explicit peer selection** with privacy-sensitive ephemeral identifiers and a deliberate, per-attempt verification flow; be cautious about unrelated devices and Bluetooth pairing behavior.  
**M3m:** Debug RFCOMM host/join transport, interruption and cancellation (no general text).  
**M3n:** Reuse independently reviewed authenticated session authorization for real two-way debug Bluetooth text, without assuming Bluetooth pairing itself is authentication.

The full ordered work plan is [CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md).

Official Android guidance: [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [Find Bluetooth devices](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices).
