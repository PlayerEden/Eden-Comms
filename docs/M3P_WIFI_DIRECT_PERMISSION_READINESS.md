# Eden Comms M3p — Wi-Fi Direct permissions and radio-readiness

**Checkpoint:** November 7, 2026. Debug-only Android preflight. No physical
two-phone tests or independent security review have been performed.

## What actually changed

- Wi-Fi Direct Developer Tools now reads Android's advertised P2P feature,
  service presence, read-only Wi-Fi enabled state, and device-wide Location
  Mode. It never accesses location coordinates or nearby peer identifiers.
- The explicit **REQUEST WI-FI DIRECT TEST PERMISSION** button launches the
  OS permission dialog only after a human tap. Android API 33+ asks for
  `NEARBY_WIFI_DEVICES`, and API 26–32 asks for `ACCESS_FINE_LOCATION`.
  Android groups nearby permissions on some OS versions; no app-controlled
  background permission grants are attempted.
- Debug manifest only: `ACCESS_WIFI_STATE` (normal), `NEARBY_WIFI_DEVICES`
  with `neverForLocation`, `ACCESS_FINE_LOCATION` limited to API 32.
  Production/main manifest remains unchanged, LAN+camera only.
- Pure readiness policy distinguishes unsupported service, Wi-Fi off or
  unknown, Location Mode off or unknown, permission missing, and all observed.
  **All observations still explicitly have `discoveryAllowed=false`.**
- Permission state gate allows one outstanding Android permission callback,
  rejects repeat attempts, ignores stale/duplicate returns and cancels on
  screen exit. If a platform permission dialog stops the Activity, the
  pending attempt fails closed; a permission result is never peer trust.
- Manual Wi-Fi and Location settings links are user-triggered. Returning
  only rereads device state.
- Kotlin tests cover readiness blockers and permission deny/cancel/late
  callbacks. Python security tests and strict APK audit scripts are updated.
  No new dependencies.

## Not done — hard gates

There is **no** P2P channel initialization, radio broadcast receiver,
`discoverPeers`, group owner negotiation, socket, proof exchange,
typed text, voice, saved contact, or release capability. In particular
"prerequisites observed" does not prove that P2P is actually turned on.
Bluetooth M3n-C1 is unchanged; it always closes, with text disabled.
Production `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE`
all remain false. Debug APK is not suitable for private/sensitive messaging.

## Deferred physical QA

On owner-requested real phones: API 26–32 and API 33+ grant, deny,
deny-again, revoke in settings, rotation/background while permission
dialog is visible, Wi-Fi off, Location Mode off and unsupported hardware.
Confirm there is **no peer scan, group or connection** after any outcome.
Do not retain MAC addresses, QR payloads, location or chat content.

## Next

M3q should introduce debug-only P2P state broadcasts and bounded
foreground discovery **as a separate reviewed PR**, with user Start/Stop,
explicit permission and Location Mode checks, cancellation, callback
generation fencing and no peer trust or chat. Group formation will need
its own step. Independent review and two-phone testing remain open.

Android platform sources:
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://developer.android.com/develop/connectivity/wifi/wifi-permissions

Rollback: revert this PR; the previous M3o observation-only preflight needs
no data migration or permission cleanup.
