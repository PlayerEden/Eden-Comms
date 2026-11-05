# Eden Comms M2t — message-status semantics and delivery-event contract

**Date:** 2026-11-05  
**Implemented on branch:** A native chat-status explainer and immutable meaning labels.  
**Test-only:** Hypothetical delivery reducer and simulated acknowledgments (JVM tests only).  
**NOT implemented:** Peer-authenticated arbitrary text transmission, delivery receipts, encryption protocol approval, voice, or hardware acceptance.

## Visible, concrete product change

In **Home → Text → What will message statuses mean?**, a scrollable, accessible offline guide explains exactly what the user will see when actual secure messaging is implemented. The one functional status today is **Local preview • not sent**. All others are prominently labeled **FUTURE ONLY**; none are inserted into the actual transcript as simulated deliveries.

The canonical definitions are kept in `app/src/main/java/com/edenpractical/comms/chat/ConversationStatusGuide.kt`. `LocalChatPreview.DisplayState.LOCAL_ONLY` now uses the canonical current label, so a second stale copy can't cause UI and model mismatch. The existing local preview remains functional and never sends or receives arbitrary text.

## Planned statuses and necessary evidence

| Status | What it can claim | Proof needed before enabling in real chat |
| --- | --- | --- |
| Local preview • not sent | UI-only text shown on this phone | None; available today |
| Waiting for connection approval | User must still accept a genuinely authenticated peer/session | Independent mutual-approval protocol review; actual local + remote approval |
| Waiting to send | A message was accepted to a bounded session queue | Reviewed authorization/queue ownership on a live session |
| Written to encrypted connection | Local authenticated transport completed a write | Confirmed local write; **not** proof of peer receipt |
| Other app acknowledged receipt | Peer app explicitly acknowledged **that message** in **that authenticated session** | Cryptographically bound fresh app-level ACK; no claim that a human read it |
| Could not deliver | A pending/attempted message failed or session ended | Transport/session error with safe, provenance-correct state transition |
| Conversation ended | Session closed, local in-memory content cleared | Lifecycle owner cleared buffers; never promise deleting a recipient's copy |

No **human read receipt** is planned or inferred from an app ACK. Reconnects and repeated sends require explicit policies; the app must not silently retry failed messages.

## Review-only delivery reducer

`app/src/test/java/com/edenpractical/comms/chat/ReviewOnlyMessageDelivery.kt` is intentionally a **JVM-only hypothetical model**. It stores just monotonic numeric message IDs, simulated statuses, and an exact object-reference session attempt—not message bodies, peer fingerprints, cryptographic evidence, or network handles. Its deliberately explicit `injectSimulated...` methods **cannot authenticate anyone** and must never be used as authorization in an APK.

It exercises structural invariants for a future reviewed adapter:

- No queued message before deliberately simulated bilateral approval.
- A local encrypted write and a peer-app acknowledgment are two different events.
- A peer-app ACK before a write, for an unknown ID, after failure, or from a stale/foreign session must never promote a message.
- Failure is terminal for that message; no automatic retry or silent resending.
- Close immediately drops all per-message status entries and blocks late callbacks; a new attempt has a fresh identity, and old attempt tokens remain rejected.
- Pending metadata queue is capped at 32 IDs.
- `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` all remain **false**, even during tests simulating future readiness.

The test-only reducer is *not* a reviewed real-message protocol; showing that its internal transitions pass does not establish that any live bytes or ACKs would be secure. Its code must stay outside `app/src/main`.

## CI boundary

`scripts/check_security_review_boundary.sh` explicitly enforces the reducer's location in `app/src/test`, refuses references to it in distributable Android source, and requires `ConversationStatusGuide.currentLiveSteps` to contain only `LOCAL_PREVIEW`. The pre-existing M2s guard blocks chat preview imports of TLS, network, protocol and persistent storage. The production capability switches remain off.

## Next real-feature work, not another indefinite simulation arc

1. **Security decision and independent review**: select a battle-tested cryptographic implementation/protocol and review live bilateral approval, transcript binding, message framing/limits, replay and ACK authentication semantics. Existing M2f/M2h proposals are not approvals. Do **not** invent cryptographic primitives in an ad-hoc messenger.
2. **Reviewed Android integration**: bind a real, non-forgeable per-attempt verified session to a bounded message queue; implement real text send/receive and authenticated app-level ACKs. Only after this step may an actual UI message progress beyond local-only.
3. **Two-phone physical validation**: finish [debug QR/TLS worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md), then a separate encrypted text QA suite (good link, peer mismatch, revoked permission, stale ACK, replay, no WAN, disconnect, background, rotation, large font, device upgrades). Do not use successful virtual events as handset proof.
4. After actual LAN chat works, carry out the product plan for nearby discovery, Bluetooth, Wi-Fi Direct, optional trust retention and PTT.

## Remaining hardware/product risks

The screen guide/dialog and keyboard still require large-font, TalkBack, portrait/landscape, Recents preview and actual Android lifecycle testing. The debug signer is not confirmed stable across update installs (issue #27). The real two-device QR/TLS matrix is still pending (issue #26), and unrelated real-text delivery, crypto review and Google Play gates remain open.

**Scope unchanged:** no extra permissions, radio drivers, app accounts, cloud, analytics, persistent chat messages, network protocol packets, or production message/voice functionality.
