# Eden Comms — M2r foreground Wi-Fi continuity and bounded cancellation

**Engineering checkpoint:** 2026-11-05  
**Scope:** Real debug-only LAN QR/TLS fixed-phrase test. **Two physical phones NOT TESTED.**  
**Production text, voice, Bluetooth and Wi-Fi Direct:** Not enabled.

## Risks addressed

During the earlier two-QR process a phone could switch Wi-Fi after the initial QR was shown or scanned. The TCP join retry loop already checked Wi-Fi changes, and the heartbeat did so after the debug session reached READY, but the presenter could sit in a 90-second `accept()`, the joiner could be verifying TLS/QR while changing networks, and a scanner could accept a QR from an earlier Wi-Fi state. This could produce misleading failures and prolong a cancelled/stale attempt.

The user-initiated `endByUser()` also queued a best-effort CLOSE packet on the single writer. If the writer was stuck, it had no independent, bounded fallback to close the socket; leaving the UI did not guarantee the queued writer could finish promptly.

## Implemented and isolated

1. **Single Wi-Fi identity per pairing.** `WifiLinkContinuity.unchanged()` compares the original Android Network object, IPv4 address and prefix; loss, roaming, a changed IPv4, or prefix change fails closed. It does not use Wi-Fi scanning, geolocation, internet access or raw SSID. JVM tests exercise exact match, each field changed, null network/IP and invalid prefix values using fake identifiers. Wi-Fi addresses are never copied into error text or diagnostics.

2. **QR and authentication checkpoints.** The pairing UI checks continuity before decoding either QR and immediately before starting the test socket. On a Wi-Fi switch during a scan, the app discards temporary QR/pin values and directs the user to begin again with fresh QR codes rather than reusing the old session. The live `LanDemoSession` rechecks original Wi-Fi **before TLS**, **after pin verification**, **after QR proof**, **after channel-binding diagnostic**, and **before emitting READY**.

3. **Wi-Fi watcher during setup.** A foreground-only 2-second poll observes the original link while the host waits for the peer, the joiner retries TCP, and TLS/QR/READY is in progress. It closes the sockets and reports a typed `WIFI_CHANGED` failure rather than waiting for a 90-second accept / 120-second retry timeout. The watcher cancels after READY; the existing heartbeat continues verifying the Wi-Fi link after READY. A missed timing window is still checked at the next handshake boundary. This is a best-effort scheduling interval, not a hard 2-second Android OS guarantee.

4. **Bounded user-initiated shutdown.** `LanGracefulStop` arms a forced socket close **before** queueing the optional encrypted CLOSE packet. An independent cancellation scheduler (not the heartbeat or potentially blocked writer thread) requests `close()` after **1,200 ms** even if the writer stalls. If scheduling or writer submission is rejected, it closes immediately. `SessionSocketRegistry` handles idempotent close and late socket registration. This is a target delay under normal executor scheduling, not a real-time kernel guarantee. Abrupt EOF is preferable to leaving a test connection alive.

5. **Late camera and UI callbacks.** A camera failure emitted after leaving the scanner no longer overwrites another screen. Disposing the pairing composable increments the attempt generation before shutting down the socket, rejecting already posted previous-attempt connection events. Existing cancellation and session-only phrase-clearing rules are unchanged.

6. **Safe diagnostics.** `LanWifiChangedException` has no raw message or private context. `LanProbeDiagnostics.Code.WIFI_CHANGED` produces one fixed explanation for Wi-Fi changes at any handshake step. Other existing QR, TLS, READY and liveness classifications remain.

## Automated QA and limitations

- `LanRecoveryPolicyTest` verifies network token, address and prefix continuity; no WAN assumption; invalid snapshots; timer-arming-before-send; both scheduler and writer rejection fallback; and no unnecessary immediate close when a graceful CLOSE is queued.
- `LanProbeDiagnosticsTest` covers safe, non-sensitive `WIFI_CHANGED` classifications during TCP, TLS, QR and READY.
- Existing JVM simulated two-endpoint transcript, immutable session ledger, socket-registry race, and M2p virtual transport fault matrix remain part of CI. The actual Android code is compiled by the existing `assembleDebug` and static security checks.

**What CI cannot prove:** Android connectivity callback latency, real router roaming, camera cancellation timing, TLS socket shutdown on both actual handsets, the behavior of a writer blocked by a real peer, Android 13/newer compatibility, timing accuracy or battery impact. Independent protocol review and real mutual approval are still outstanding; this milestone does not enable arbitrary text or voice.

## Physical test additions to existing M2k T01–T16

- After showing the host's QR, switch that phone to another Wi-Fi, then scan the reply QR. Expect a fresh-pairing instruction; old QR must not reconnect.
- While the host is listening (or joiner retrying), switch one phone's Wi-Fi. The test should end with `WIFI_CHANGED`, with no late READY, no background traffic, and temporary QR state cleared.
- During TLS/pin/QR proof, change Wi-Fi and ensure the session never becomes connected.
- On an active fixed-phrase test, tap **End test** during rapid sending. Verify the UI ends immediately, the remote detects close/loss, and no old callbacks repopulate the session. Use both phones' safe QA reports.
- Cancel during camera scan, background the app, and reenter. Ensure a late CameraX callback does not modify the new screen. Check TalkBack, portrait and landscape behavior.

Use [consolidated worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md) and [physical issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26). Signing/update compatibility [#27](https://github.com/PlayerEden/Eden-Comms/issues/27) remains open; do not uninstall to bypass signature mismatch without informed consent.

**No changes to:** Android manifest permissions, signing configuration, library dependencies, package ID, TLS 1.3, reciprocal QR proof/wire packet formats, fixed four-phrase limit, persistent storage, or disabled production communication flags.
