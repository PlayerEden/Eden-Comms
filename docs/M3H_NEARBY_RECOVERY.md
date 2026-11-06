# Eden Comms M3h — Nearby connection recovery without silent reconnect

**Development date:** November 6, 2026  
**Scope:** Actual debug-only nearby Wi-Fi pairing UX and recovery. No new radio transport or production trust claims. Physical phone QA explicitly deferred by the owner.

## Product improvement

The existing two-QR → pinned TLS 1.3 → both approvals and final confirmations → test-text flow works in source, but previously stopped at a mostly generic **Couldn't connect** or test-result screen. If Wi-Fi had changed while the app was in the system Settings, the cached UI state might not refresh before asking the user to choose a new role.

**M3h makes recovery actionable rather than automatic:**

- On the initial **Connect nearby** screen, display *only this phone's observed* usable local Wi-Fi status. It does **not** prove the other phone is reachable, using the same Wi-Fi, or authenticated. A connected router with no internet can still support local pairing.
- If no usable Wi-Fi is detected, explicitly offer **Open Wi-Fi settings** and **Check Wi-Fi again**. Launching system Settings is user-triggered, not an app-initiated network switch.
- On return to the app (`ON_RESUME`), refresh Wi-Fi **only** when the UI is on the initial selection or the completed-result screen. Never overwrite the Wi-Fi object captured by a live QR scan, active TLS negotiation, approval or conversation.
- **Start over with fresh QR codes** calls the existing cancellation routine first, invalidating stale callback attempts, closing an old debug socket if present, clearing old QR values and peer pins, and dropping in-memory chat content. It then refreshes Wi-Fi, drops prior QA session state and presents a new role chooser. No reuse of expired or previously failed pairing proof.
- When the test ends, a new **nearby recovery guide** uses only a fixed failure code, end code, and completed-session reason. Network/isolation, timeouts, mismatched identity/proof, unsupported TLS exporter, protocol rejection and human decline have distinct explanations and suggestions.
- When network troubleshooting is relevant, the result page can reopen Android Wi-Fi settings and refresh this phone's observed state **without** auto-connecting, reusing QR codes, automatically approving another person or resending a message.
- The method label is explicit: **same-Wi-Fi development test now**; **Bluetooth and Wi-Fi Direct are planned**, but not available or being scanned by this build.
- The previous result-screen claim that session text was *erased* is replaced by accurate wording: the app drops visible message references, but that is not secure erasure of the OS memory, keyboard or another phone.

## Security and privacy guardrails

- No changes to pinned mTLS, QR identity/proof, TLS exporter, bilateral human approval/final confirmation, or debug packet encoding/dispatch.
- No automatic reconnect, message resend, saving peer trust, IP/SSID display, network scan, fallback to unencrypted sockets, app server, new permission, Bluetooth connection, Wi-Fi Direct connection or voice.
- Display guidance is fixed code-to-copy mapping; it **never** accepts exception-message strings, QR payloads, addresses, remote identities or typed text. A result title is a local observation, not a diagnosis of the peer.
- `CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` remain false. All typed test traffic remains debuggable-only. A user-directed fresh retry still requires the entire two-QR authenticated protocol and bilateral final confirmations.
- The debug QA report is still copied **only by explicit tap** and retains coarse checkpoint/end/failure codes, not network or message bodies.

## Automated verification

The M3h JVM unit tests cover **every** currently defined `LanProbeDiagnostics.Code` and `LanDemoStatus.EndCode`, including identity mismatch, exporter unavailable, timeouts, interrupted Wi-Fi, invalid packets, user cancellation and peer decline. They verify nonblank privacy-safe advice, no fabricated auto-retry/receipt promises, end-reason priority over later Wi-Fi changes and no claim that seeing Wi-Fi proves peer authentication.

The Python source checks verify that new manual retry invalidates current test state and QR references, refreshes Wi-Fi, does not run a hidden `runProbe()`/send in result handling, observes Wi-Fi changes only in safe idle/result lifecycle states and does not accidentally enable future radio methods or production release flags.

**CI does not establish handset interoperability or physical-device security.**

## Owner-deferred physical acceptance (NOT TESTED)

When the owner chooses to test:

1. Disconnect Wi-Fi before opening **Connect nearby**. Verify no connection role buttons; open phone Wi-Fi Settings; enable Wi-Fi; return and verify the app detects the change with no QR scan or hidden connection.
2. Start a two-QR attempt, change Wi-Fi, return to the app and verify the active attempt closes rather than silently switching networks.
3. Force local network isolation / guest Wi-Fi / host not accepting, timeouts, mismatched/expired QR and unsupported identity/provider cases where practical; verify each gives non-sensitive guidance and no bypass option.
4. Use **Start over with fresh QR codes** on both devices. Old invitation/reply QR codes and old approval state should not be accepted as a fresh connection.
5. Prove no automatic resend of the last typed message after a failure or retry; exercise Android background/foreground, rotation, large fonts and accessibility.
6. Confirm no Bluetooth or Wi-Fi Direct OS permission or scanning starts and that their status remains **planned**.

Physical QA issue [#26](https://github.com/PlayerEden/Eden-Comms/issues/26) remains open. Production protocol review issue [#42](https://github.com/PlayerEden/Eden-Comms/issues/42) remains open. True Bluetooth and Wi-Fi Direct transport implementation will require permission-scoped Android radio APIs, discovery design and fresh authentication with the same security gates; this milestone does not pretend to implement those radios.

Related: [M3f chat-first UX](M3F_CHAT_FIRST_EXPERIENCE.md) · [M3g session privacy](M3G_SESSION_PRIVACY_CONTROLS.md) · [Product vision](PRODUCT_VISION.md).
