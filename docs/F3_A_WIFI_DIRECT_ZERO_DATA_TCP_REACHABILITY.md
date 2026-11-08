# F3-A — real Wi-Fi Direct TCP reachability, zero application data

**Date:** November 8, 2026. **Parent:** F2-I PR #102, merged
`af2a866fc50c84437f0ad7275d6d6aead63bedbc`, successful PR and
fresh merged-main Android CI.

## Why this slice

Eden Comms' first version must support a local **Connect → Communicate →
Disconnect** experience on LAN, Bluetooth Classic and Wi-Fi Direct,
with optional foreground PTT after authentication. Bluetooth F2-I is
blocked from live chat by independent protocol review and physical
two-phone RFCOMM/TLS provider testing. F3-A makes parallel progress
toward Wi-Fi Direct: group discovery and formation were already present,
but nothing had exercised an app-level TCP connection on the P2P link.

**F3-A is a real TCP connection probe, not a messenger.** It is available
only in debuggable APKs and sends/reads **zero application data**. A
successful TCP handshake is NOT evidence of identity, QR possession,
TLS encryption, bilateral consent, or peer-app receipt.

**November 8 F3-B3-A routing correction:** The original host-address
assumption below has been superseded by the
[F3-B3-A OS P2P interface QA handoff](F3_B3_A_P2P_INTERFACE_BOUND_TWO_PHONE_QA.md).
Android may anonymize the host's group-owner address. Both endpoints
now re-read the current group, select the OS-named P2P interface,
and bind their test sockets to its eligible local IPv4 or fail
closed. The F3-A zero-payload and no-trust limits remain unchanged.

## Actual Android user path

**Hosting phone (separate debug group-host screen):**
1. Explicitly tap START 20-SECOND GROUP HOST TEST.
2. Once Android reports HOST_OBSERVED, tap
   **RUN 5-SECOND TCP LISTENER TEST — NO DATA**.
3. Re-read current `WifiP2pManager.requestConnectionInfo` to verify
   `groupFormed` and `isGroupOwner`, then `requestGroupInfo`.
   Bind one listener to the **private IPv4 of the exact OS-reported
   P2P group interface**. A redacted group-owner address on the host
   is permitted only if that specific local interface is valid;
   never use 0.0.0.0 or a default network.
4. Listen for one inbound TCP handshake for at most 5 seconds.
   Immediately close any accepted socket and listener. No user-data
   APIs, QR data, file storage or audio are involved.
5. Stop/background/timeout closes any listener before group cleanup.

**Joining phone (debug anonymous-discovery/target-link screen):**
1. Search, choose a numbered **unverified** P2P candidate, explicitly
   request the 18-second link test and wait for GROUP_OBSERVED.
2. Tap **TEST ONE TCP CONNECTION — NO DATA OR TRUST** while the host's
   listener is running. This is a third explicit user action.
3. On the same active link token, verify `groupFormed` and
   `!isGroupOwner`, then read `requestGroupInfo`. Require an
   eligible private local IPv4 on the P2P group interface and a
   private OS group-owner IPv4 within its reported subnet. Bind
   the local P2P source before the bounded 2-second TCP connect.
4. Close the connector immediately, regardless of success/failure.
   Never infer peer identity from the owner address or a platform
   connection-success callback. No messages, recording or screenshots
   of address information.

**Coordination caveat:** the existing temporary host expires in 20
seconds and the link request expires in 18 seconds; the host's TCP
listener is only open for 5 seconds. The two taps must overlap. OEM
group negotiation timing can prevent either screen from reaching the
correct state. This does not mean any test actually succeeded on phones.

## Transport and security contract

- New debug-only `DebugWifiDirectTcpProbe`: one-shot `ServerSocket`
  or `Socket`, no `InputStream`, `OutputStream`, `read`, `write`,
  packet protocol, TLS, authentication, trust, identity persistence,
  app message or microphone API. It transfers **no app payload**.
- Fixed high TCP port `39761`, host accept timeout 5 seconds, join
  connect timeout 2 seconds, one accepted connection at most.
- Only eligible RFC1918 IPv4 addresses on the OS-reported **P2P group
  interface** are accepted. The host may tolerate a null/wildcard
  *redacted* owner address but never binds to a wildcard. The joiner
  requires the concrete OS owner IPv4 in the same reported subnet.
  Link-local, multiple, malformed, public or IPv6 candidates fail
  closed. JVM loopback is allowed only in isolated JVM fixtures.
- Cancel closes sockets to abort blocking I/O. One probe per
  user-started group/link attempt. Late callbacks are checked against
  current Android group token and foreground state.
- No OS network address/MAC, group secret, QR pin, session nonce,
  test text, or exception detail is logged, displayed or persisted.
  There is no fallback to default internet routing or insecure chat.
- Existing `ReleaseCapabilities.CAN_CONNECT`,
  `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` are unchanged/false,
  with no new Android permissions or non-debug components.
- An arbitrary device on the P2P link could complete the host's
  socket handshake; **do not use this result to authenticate anyone**.

## Verification and rollout gates

- New Kotlin JVM loopback fixture tests a **real** host+join socket
  handshake, bounded cancellation, one-shot admission and rejection of
  unsafe address classes. JVM loopback is not proof of Android P2P.
- Python + shell review guards keep all byte transfer and identity
  code out of the TCP adapter and release source, enforce P2P
  role/group gates, and preserve the separate QR preparation flow.
- [ ] PR Kotlin/JVM, Python, Android APK/security audit and CI pass.
- [ ] Fresh merged-main Android CI pass.
- [ ] **Physical two-phone** group/connection experiment and OEM
      routing test, including denial, timeout, cancel, background,
      Wi-Fi off, stale callback and failed group cleanup (not run).
- [ ] Independent review of real Wi-Fi Direct QR-to-group/P2P socket
      identity binding, pinned TLS, bilateral consent, framing and
      lifecycle before enabling any chat payload.
- [ ] F3-B: reviewed *authenticated* TLS control and private identity
      match to the correct P2P group/QR person.
- [ ] F3-C: actual encrypted text+peer-app ACK and clean disconnect
      after the security/device acceptance gates; later remote PTT.

**No physical device testing or independent review occurred in this PR.
F3-A must not be called an authenticated connection.**

**Rollback:** revert F3-A PR; no app database, signing, saved identity,
Android permission or existing chat path changes. The old radio-only
diagnostics remain available.
