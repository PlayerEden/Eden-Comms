# F0-LAN usability — longer real debug text sessions and keyboard Send

**November 8, 2026 · functional debug-only app changes, not a radio or security certification.**

The same-Wi-Fi debug conversation **already exchanges real bounded typed text**
and per-message peer-app ACKs using its reciprocal QR, pinned TLS 1.3 and
mutual approval/final-confirmation flow. This slice improves that existing
live chat rather than creating another synthetic protocol rehearsal:

- **30-minute maximum per established READY test link** instead of the old
  ten-minute cutoff. It starts at TLS/QR test readiness, before the human
  approvals; it is NOT an idle timer reset or automatic re-approval.
- The existing **32-second peer silence timeout**, 8-second heartbeat,
  90-second consent timeout, Wi-Fi-change detection, background/cancel socket
  close, ACK rules and bounded send queues remain unchanged.
- The active real-chat composer now requests the standard Android
  **keyboard Send action**, while retaining its visible Send button.
  It calls the SAME `onSend` callback only for a non-hidden, accepted,
  nonblank <=280-code-point draft. The existing
  `LanDemoSession.sendDebugText` enforces real current-session approval,
  Unicode, framing and writer limits independently.
- The active chat explicitly states the 30-minute readiness-based cutoff
  and peer liveness behavior. End states describe the same limit.

No new packet types, plaintext transport, mic access, extra permissions,
storage, Bluetooth/Wi-Fi Direct/voice messaging, background operation or
release-capability changes. The existing test APK workflow produces
`com.edenpractical.comms.preview` for optional install-over on later runs.

## Smoke test once two phones are available
1. Start **Eden Comms Preview** on both phones with no sensitive data, same
   Wi-Fi (WAN not required), exchange reciprocal QR and individually approve.
2. In the real nearby test chat, send a short harmless message by **keyboard
   Send**, then one by the visible button. Check actual received bubbles on
   the other phone and distinguish peer-app ACK from local TLS write.
3. Confirm blank/invalid drafts cannot be keyboard-sent; hide the
   conversation and confirm keyboard/composer is absent.
4. Leave both phones in foreground and connected after **more than ten
   minutes**. Verify heartbeat continues, then deliberately disconnect.
   A full 30-minute timeout is optional extended hardware QA, not a
   prerequisite to short two-phone functional testing.
5. Reject the old QR after ending, repeat without WAN and verify background,
   connectivity loss and consent declines still close instead of resuming.

Actual two-phone validation remains [#26](https://github.com/PlayerEden/Eden-Comms/issues/26);
qualified independent cryptographic review before claims of secure consumer
messaging remains [#42](https://github.com/PlayerEden/Eden-Comms/issues/42).
Do not use this debug app for sensitive messages.

## Verification
Kotlin JVM tests assert boundary (ten minutes no longer cuts off, thirty
minutes still does), peer silence and fixed status copy. A Python source
contract confirms keyboard Send uses the same validated callback and that
production capabilities stay off. Android/Gradle, Python, permissions,
security checks and test APK build must pass before merging.

**Rollback:** revert this slice. No APK identity, signer, storage or
network-protocol migration.
