# Eden Comms — M3f Chat-First Development Experience

**Date:** 2026-11-06  
**Status:** Feature implementation and automated checks. Android physical QA intentionally deferred by the owner. Production messaging, voice and release permissions remain disabled.

## Why this milestone

M3e put real short **development test** text on a pinned TLS 1.3 connection, but the user-facing flow still behaved like a technical packet exercise:

- Home **Text** opened a *local-only* fictional layout sandbox rather than the actual nearby test connection.
- After bilateral approval, four preset debug phrases and their counters consumed the top half of the active screen. Typed chat was buried below them.
- The initial composer counted UTF-16 characters; non-BMP characters (like emoji) consumed two units despite the actual wire encoding limiting Unicode **code points**.
- A few terminal/failure paths in the screen closed the socket but did not explicitly clear the debug text state and unfinished draft.

## Updated flow

1. **Home → Text** now opens the same **Connect nearby** QR-first development path as the main Connect button. Text is not falsely marketed as production-ready; the tile says **Nearby test chat**.
2. **Settings → Developer Tools → Text layout sandbox** keeps the old fictional/local-only presentation editor available for UX experiments. It cannot send anything to another phone.
3. Following mutual QR verification, pinned TLS, both human approvals and both in-session final confirmations, the active screen now shows a **Conversation** panel **first**, with a visible **DEV TEST** label and truthful privacy warning.
4. A bounded **220dp scrollable conversation viewport** allows up to 12 recent development messages without forcing a long stack of chat bubbles to push the composer completely off screen. New message counts scroll the inner list toward the latest activity. Both local/TLS-written and remote/received labels remain distinct; no fake delivered/read status.
5. Typed draft validation now matches the actual test-wire **280 Unicode code-point** boundary (including emoji) and refuses malformed surrogate/control characters before submission. The transport still independently validates the incoming and outgoing bytes; this Compose-only filter is NOT cryptographic authority.
6. The existing four fixed test phrases, transcript counts and detailed link diagnostics are still accessible under **Show preset phrases and connection details**, collapsed by default. The **End chat and disconnect** action stays visible.
7. Decline, explicit end, failed connection, remote end, reset, app background and Composable disposal now explicitly drop the temporary typed-message list and draft in addition to socket cleanup. QA reports still contain **counts only**, not message bodies or peer identities.

## Automated checks

- JVM tests cover emoji/Unicode 280-point boundaries, empty or whitespace drafts, tab/newline, forbidden control characters, invalid surrogates and release capability flags.
- Python source-contract tests cover Home Text routing, sandbox placement, chat-before-presets ordering, fixed-height bounded viewport, source-bound honest statuses and all terminal cleanup paths.
- Existing M3e unit tests, framing/TLS/approval checks and **scripts/check_security_review_boundary.sh** remain mandatory in Android CI. Release `CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` are still false.
- CI compilation and unit tests are **not** evidence that any two real phones can connect. Do not claim the chat is a verified secure messenger.

## Owner-deferred testing (not performed)

When the owner is ready for a physical pass, add to [M2k's device QA worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md):

- Open Home **Text**; verify it enters nearby QR flow. Navigate separately to the local-only Text layout sandbox under Developer tools.
- Complete two-phone QR/mTLS/approval/confirmation and confirm the chat **composer is visible near the top**, not after the test phrase controls.
- Send a short Unicode test message both ways; compare the two phones. **Never use sensitive text**.
- Tap **Show preset phrases and connection details**, verify the old debug controls still work, collapse them again.
- Send more than 12 messages and check scrolling/viewport; use landscape, narrow device, font scaling and TalkBack. Check the keyboard does not hide Send or cause broken scrolling.
- End chat, decline, background, disconnect Wi-Fi and restart. Confirm **no old text/draft reappears**. The system keyboard, screenshots and receiver may retain copies outside Eden Comms' control.
- Do not treat local write as authenticated delivery acknowledgment.

## Still planned

An independently reviewed secure production text protocol, verified physical LAN, Bluetooth/Wi-Fi Direct, authenticated reconnect, optional encrypted local history, actual voice/PTT, permanent production signing and eventual Google Play publishing are outside this change. These are future functional/release milestones, not reasons to stop development UI and no-phone automated testing now.

References: [Product Vision](PRODUCT_VISION.md) · [M3e debug text](M3E_DEBUG_TWO_WAY_TEXT.md) · [M2k physical worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md) · [Issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26) · [Issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42).
