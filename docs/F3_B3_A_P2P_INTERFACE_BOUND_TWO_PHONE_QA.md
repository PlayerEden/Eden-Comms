# F3-B3-A — Wi-Fi Direct P2P interface-bound TCP diagnostic and two-phone QA

**Date:** November 8, 2026. **Parent:** F3-B2 PR #105, merged
`21b3e8e9e11507e528d9f458c4ccf94a8954d3ea`, with green
pull-request and post-merge Android CI.

## Reason for this change

F3-A offered a real, bounded, zero-payload TCP reachability test after
Android reported a temporary Wi-Fi Direct group. Its original host
listener used `WifiP2pInfo.groupOwnerAddress` directly. Android's
`WifiP2pManager` API says the group-owner address may be anonymized
on the host unless the caller has the privileged
`LOCAL_MAC_ADDRESS` permission; the diagnostic must **not** request
that privilege. Merely dialing a private IPv4 group-owner address
from the joiner also does not guarantee that the selected local
source interface is Wi-Fi Direct rather than default Wi-Fi/cellular.

F3-B3-A fixes those specific diagnostic gaps. It does **not** enable
a secure message session, alter the three-QR exchange or run TLS
on a real P2P radio. The actual user objective remains offline
two-person **Connect → Communicate → Disconnect**.

Android API references:
- [WifiP2pManager — connection info and owner-address anonymization](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)
- [WifiP2pGroup.getInterface — OS P2P group interface name](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pGroup#getInterface())

## Exact changed behavior (debug APK only)

On each explicit Wi-Fi Direct TCP test tap, the active token and
foreground/permission/group phase are rechecked before any socket
is opened. Two Android callbacks are required:
`requestConnectionInfo` verifies `groupFormed` and the host/joiner
role; then `requestGroupInfo` supplies the **exact current P2P group
interface name**, not a user-input name or a guess such as `wlan0`.

`DebugWifiDirectP2pInterface.resolve` uses
`NetworkInterface.getByName(group.getInterface())` and no fallback
enumeration. It requires an up, non-loopback interface with **exactly
one eligible RFC1918 IPv4** address with a meaningful OS-reported
subnet prefix. It rejects ambiguity, a missing interface, invalid
prefixes, public addresses, multiple candidates and permission
failures. It never displays, logs or stores the interface name,
subnet, MAC, IP, group password or network name.

**Host:** if the OS reports a concrete group-owner IPv4 it must match
the selected local P2P interface address. If the OS redacts it as
null/wildcard, use the **selected group interface's own local IPv4**
only. Bind the one-shot 5-second `ServerSocket` to that address, not
to a wildcard, cellular or ordinary Wi-Fi address.

**Joiner:** require a concrete RFC1918 group-owner address, a
different local RFC1918 IPv4 from the **same** OS group interface,
and proof from its OS subnet prefix that both addresses are on the
same network. Call `Socket.bind(localP2pAddress:0)` **before** the
bounded 2-second `connect(ownerAddress:39761)`. Refuse to connect
when the local P2P interface is missing or incompatible, rather than
letting the OS choose an unrelated source interface. This binding
is still a routing safeguard, not an identity assertion.

The original explicit test buttons, single-use TCP probes,
5-second host accept, 2-second join connect, 18-second join link and
20-second host group lifetime remain unchanged. No user payload,
QR proof, TLS, human consent, voice or session retention is performed.

## JVM and repository evidence

- `DebugWifiDirectP2pInterfaceTest` checks redacted host owner,
  matching/mismatching host address, local-versus-owner join subnet,
  non-byte-aligned network masks, ambiguous addresses, prefix errors,
  missing P2P interface and no loopback/default-route fallback.
- `DebugWifiDirectTcpProbeTest` additionally rejects both Android
  roles when a local P2P binding was not supplied and refuses use
  of the JVM loopback exception with private non-loopback networks.
- Existing positive **real JVM loopback TCP** tests still cover a
  host listener, join connection and cancellation without app data.
- Python and shell boundaries verify OS group-info and role checks,
  explicit source binds, no ad-hoc `NetworkInterface` fallback,
  no payload API, no new release permissions, no live QR/TLS wiring.

**Not verified:** actual Android OEM group-interface naming and
visibility, subnet prefix availability, socket routing on a
specific manufacturer's device, two physical phones, exporter/
Conscrypt behavior, hardware cleanup and external security review.

## Two-phone device QA checklist (NOT YET RUN)

Use two Android 12+ devices with the **same intended debug APK**
installed; note Android version/build and permission results only,
not raw IP/MAC/QR keys. Prefer an offline test with mobile data and
ordinary WAN Wi-Fi unavailable, while keeping Wi-Fi Direct enabled.

1. On phone A open **Wi-Fi Direct — temporary local group** and start
   the explicit 20-second host test. Note only whether Android
   reached `HOST_OBSERVED`.
2. On phone B run anonymous Wi-Fi Direct discovery, select A's
   unverified candidate, and start the 18-second target link test.
   Note only whether `GROUP_OBSERVED` was reached.
3. On A, tap **RUN 5-SECOND TCP LISTENER TEST — NO DATA**; while that
   listener is running, on B tap **TEST ONE TCP CONNECTION — NO DATA
   OR TRUST**. Expect either a reported handshake or a graceful,
   clearly explained failure. These timings are narrow; repetition
   requires a **new**, explicit group/link attempt.
4. Repeat when mobile data is enabled but no ordinary Wi-Fi router
   is available; failure must not trigger a guessed/default IP
   fallback. Toggle app foreground, revoke nearby permissions, or
   switch Wi-Fi Direct off while an attempt is active; verify no
   later callback reports an active connection.
5. Test one OEM that masks `groupOwnerAddress` on the host. The host
   may still bind using the OS group interface, or safely refuse
   if it is unavailable. It must never request the privileged
   `LOCAL_MAC_ADDRESS` permission.
6. Verify timeout, Back/Stop and group removal; Android cleanup is
   best effort, so inspect system Wi-Fi Direct settings if a group
   persists. Do not record raw addresses, QR contents or passphrases.

Store outcomes as coarse **host observed / join observed / interface
available / TCP completed / cleanup confirmed** yes/no statuses,
alongside device Android version. No two-phone evidence has been
submitted yet.

## Follow-on acceptance gates

F3-B3-A does **not** solve real P2P-to-QR user identity. In particular,
a different nearby device on the group could complete the TCP
handshake. The actual Android application must separately get
independent review of binding the chosen QR peer to the live
P2P/TLS connection, mutually pinned TLS 1.3, fresh encrypted QR
challenge and full transcript, TLS exporter channel binding,
**two explicit human approvals and final confirmations**, bounded
transport ownership and encrypted TEXT/peer-ACK semantics.
Only physical two-phone OEM acceptance and that review may justify
a later debug-only message integration. Release
`CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE`
must remain `false` until final release security gates.

- [ ] F3-B3-A PR Kotlin/Android/Python/security CI green
- [ ] Fresh merged-main CI green
- [ ] Actual two-phone interface-bound TCP experiment completed
- [ ] Independent protocol/security review and same-socket live
      TLS/QR/consent design accepted
- [ ] F3-B3-B/C reviewed encrypted P2P text/ACK integration
- [ ] F4 LAN-first remote PTT and F6 durable internal APK signing
      accepted on devices

**Rollback:** revert F3-B3-A PR; no stored identity, key, package
name, signer, new Android permission, release capability or existing
user chat is changed.
