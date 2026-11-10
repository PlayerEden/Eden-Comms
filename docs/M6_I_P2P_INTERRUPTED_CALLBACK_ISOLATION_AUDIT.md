# M6-I — Wi-Fi Direct interrupted callback isolation

**Date:** November 9, 2026.
**Branch:** `feature/m6-i-p2p-stale-callback-isolation-2026-11-09`.
**Real Android debug source only; not a device or independent security acceptance.**

## Concrete issues found

1. `DebugWifiDirectTargetLinkController.startVerifiedText` asks Android
   asynchronously for `requestConnectionInfo` followed by
   `requestGroupInfo`. Its original `endPendingVerified(message)`
   unconditionally closed the *current* `pendingVerifiedRequest`
   and reported `STOPPED`, without checking that the completion still
   belonged to the originating radio token or exact one-use QR request.
   A callback delivered after `stop()`, another attempt, or successful
   handoff into the same-session TLS worker could overwrite fresh state,
   or cancel a newer pending request.
2. `DebugWifiDirectDiscoveryActivity` created a separate controller
   for each explicit selected radio, but its `onReport`,
   `onFinished`, `onVerifiedStatus`, `onVerifiedApproval`,
   `onVerifiedText`, and `onApprovedIdentity` wrote directly into
   Activity/Compose state. An obsolete controller could deliver a late
   callback and clear a later session's chat, microphone, approved
   local privacy context, preparation, or UI phase. The existing
   `canContinue` voice closure checked identity, but the **callbacks
   before that closure** did not.

## Actual source repairs

- `endPendingVerified` now takes the originating
  `DebugWifiDirectTargetLinkGate.Token` and the exact original
  `DebugBluetoothTlsProbeInput`. All five asynchronous failure paths
  and exception exits pass both. The failure handler will close the
  QR request and emit STOPPED **only** if that token is still the
  current owner, the original proof object is still pending, the
  OS group is still in `GROUP_OBSERVED`, and no verified TLS
  session was already adopted. Stale events return without touching
  another request or UI.
- The actual join Activity now checks
  `DebugWifiDirectCallbackFence.owns(targetController, ownedController)`
  **before each** controller-owned status, termination, approval,
  text/audio, or public identity callback. An old `onFinished`
  can no longer null a newer controller or clear its state.
- The pure `DebugWifiDirectCallbackFence` compares reference
  identities, not name, MAC, address, certificate or string;
  the gate does not authorize networking and has no separate
  trusted-session state. Its JVM tests cover stale/new attempt,
  stale/new QR object, failed/disappeared group, successful TLS
  promotion, null owners, and a deterministically delayed callback
  fired only after a newer controller is current.
- The Python source test checks that each Android callback site
  passes its **captured** token and request and that identity is
  checked before side effects, while preserving TLS/socket close,
  queued PCM wiping, mic disable and production flags.

## Boundaries deliberately unchanged

Bluetooth already fences its worker and UI events to the active
RFCOMM token. Its transport, timer, permission and audio logic are
unchanged. Wi-Fi Direct group creation/removal, socket, TLS provider,
QR issue/consume (M6-H), exporter, in-person human consent,
encrypted CONFIRM, message wire, voice floor and local Keystore
retention are also unchanged. No auto-reconnect, hidden permission,
cloud, signing/package change or release `CAN_*` unlock.

## Verification / evidence required

- Exact-final-head PR Android debug compile, JVM/Python regressions,
  APK/release/security checks; **then** independently checked
  merged-main CI.
- This is a source-lifetime fix. **NOT RUN:** actual Android callback
  ordering on OEM phones, mid-handshake RF dropout, Wi-Fi Direct
  cleanup confirmation, actual two-phone text/ACK/PTT,
  LAN/Bluetooth cross-transport acceptance, camera, OS accessibility,
  screenshot, backup/storage, or independent crypto/privacy review.
- During the **one existing consolidated two-phone** QA session,
  interrupt joiner's connection info/group-info (turn radio off,
  revoke Nearby permission, background Activity) and immediately
  start a new explicit three-QR attempt when old cleanup has
  completed. A late callback must never clear a newer controller,
  present old STOPPED over CHAT, shut down a new microphone,
  re-enable old QR, or claim message delivery. Repeat with a
  canceled request and a successfully approved session; record
  sanitized counts and results only.

**Rollback:** revert the narrow M6-I changes if actual Android
behavior disagrees. Keep one-use QR and mutual approval safety
gates enforced; never loosen them to make an interrupted retry
seem smoother. Production `CAN_CONNECT`,
`CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain FALSE.
