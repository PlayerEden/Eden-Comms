# Eden Comms M3g — Conversation privacy controls and truthful retention status

**Implemented:** 2026-11-06. **Scope:** Development-only QR-pinned LAN test conversation and the Settings/Privacy screens. **Phone testing:** owner-deferred. **Release capabilities:** all disabled.

## What is functional now

The live debug chat now supports **Hide conversation** and **Clear my screen**:

- **Hide conversation** is a visual privacy curtain on this phone. It removes the message list **and composer** from the rendered UI while active, without closing the connection or stopping incoming test traffic. **Show conversation** reveals the current in-memory session text again. The curtain is **not** deletion, cryptographic protection, screen-capture prevention or a promise that someone could not observe an earlier screen.
- **Clear my screen** requires an explicit confirmation dialog. It drops the visible debug transcript and unsent draft from this phone's Compose state, but **leaves the current LAN test connected**. Aggregate non-content written/received counters remain for manual QA reporting. Separate per-direction message ID high-water marks reject duplicate callbacks after clear or after old bubbles scroll off.
- Incoming text that arrives **after** clearing or text already queued for a local TLS write can still appear. The other phone retains its own copies. Android keyboards, screen recording, snapshots and memory may retain information even after the app drops references. This is **not secure erasure**.
- Disconnect, explicit end, decline, network failure, foreground loss and navigation still end the current debug session and discard its temporary visible content.

No messages or peer records are persisted to a local database or settings preference. No cloud service, new permission, peer identity storing, background transport or voice activation.

## Settings and privacy truthfulness

Settings now explicitly states **Message history — Session only • currently active**. It shows **Encrypted history**, **Disappearing messages** and **Remember nearby devices** as **Not available yet**, with plain-language explanations. No option selectable today can enable local message persistence, timer-based erasure or peer-trust storage.

The [retention design](DATA_RETENTION_AND_EXPIRY.md) still sets the future opt-in encrypted local-message-history hard limit at **30 days**, separate from peer trust and with no indefinite retention. Delivering that feature requires reviewed at-rest encryption, expiry-before-read enforcement, key lifecycle, backup exclusions, clock-rollback handling and on-device acceptance before any persistent message-body writes.

M3g also **corrects a stale in-app privacy disclosure** left over from M3e. Before this change, Privacy & Data incorrectly said user-typed messages could not reach another phone. It now distinguishes:
- **Home → Text / nearby QR debug session:** short typed *test* messages are transmitted over pinned TLS after **both** approvals and confirmations. This has **not** been physically accepted or independently security reviewed and is not safe for sensitive content.
- **Developer Tools → Text layout sandbox:** an isolated fictional/local-only UI; typing here never intentionally transmits.
- **Saved content:** Android Keystore local identity and the light/dark UI preference may persist; chat text is temporary. A copied opt-in QA report includes only counts and public build details, never message bodies.

The on-screen privacy help now describes the actual debug-text behavior and the limitations of local erasure. Release claims remain explicitly gated.

## Automated verification

- JVM tests: clearing wipes visible message text, preserves counts, refuses replay of already seen IDs even after clearing or bounded-tail eviction; ended sessions cannot reopen.
- JVM tests: retention wording makes current vs future status explicit, shows visual-hiding versus clearing distinctions, accurately explains Android Keystore vs peer trust.
- JVM tests: disclosure never says typed test messages stay entirely local, and lists actual debug transport/permissions/limits.
- Python source-contract regression tests: help/Settings cannot pretend future encrypted persistence is active; dialog confirmation and UI hiding are required; release `CAN_*` flags still false.
- Existing debug LAN approval, TLS framing, message codec, app packaging and security boundary tests remain required.

## Manual acceptance — when the owner is ready

1. Connect two phones with current QR → pinned TLS → bilateral approvals/final confirmations.
2. Type synthetic/non-sensitive test messages. Tap **Hide conversation**: no message bodies or draft should be visible; confirm new test text can arrive while hidden and appears after **Show conversation**.
3. Tap **Clear my screen**; first Cancel (must preserve content), then confirm. Old text and draft disappear only from this phone; the peer's text remains on their phone.
4. Send a *new* message and verify it appears, but old messages don't reappear via callbacks. Counters continue to be accurate.
5. Test narrow screens, large text, portrait/landscape and TalkBack. Disconnect/background and verify content cleanup. Verify saved message history stays unavailable.
6. Verify Privacy & Data accurately states two-way debug text exists and warns against secrets. Compare M3e/M3f evidence if needed.

**Not completed:** any physical two-phone QA, independent protocol review, protected persistent encryption, verified deletion, actual production text or PTT, Bluetooth or Wi-Fi Direct. No release/privacy certification implied.

Reference: [M3e debug two-way text](M3E_DEBUG_TWO_WAY_TEXT.md) · [M3f chat-first UX](M3F_CHAT_FIRST_EXPERIENCE.md) · [Data retention design](DATA_RETENTION_AND_EXPIRY.md) · [Physical QA issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26) · [Core production review issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42).
