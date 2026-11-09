# M6-F — Verified nearby conversation IME, capture and retry-state hardening

**Date:** 2026-11-09. **Branch:** `feature/m6-f-conversation-ime-privacy-recovery-2026-11-09`.
**Scope:** actual connected debug activities and their shared Text/Talk shell.
Source/JVM/CI evidence is not a physical or emulator visual acceptance test.

## Problems observed in source

1. The MAIN activity already applies `statusBarsPadding()`,
   `navigationBarsPadding()` and `imePadding()` before the scrollable
   content; the Bluetooth Classic and two Wi-Fi Direct debug activities did
   not. These screens can become cramped under an on-screen keyboard,
   small landscape viewport or enlarged system text. Android/OEM behavior
   still requires actual device/emulator observation.
2. Text-to-Talk or Disconnect in the verified shell removed text content
   without explicitly clearing its input focus/hiding the IME. The
   microphone owner correctly releases pending floor on Talk → Text and
   on session exit; this change does not alter that authority.
3. Bluetooth's QR/verified-text activity did not set `FLAG_SECURE`, unlike
   the two Wi-Fi Direct owner activities. The screen contains pairing
   proof and unreviewed debug message bodies and should be omitted from
   ordinary OS screenshots/Recents.
4. The Bluetooth stopped-session callback reset text/draft/ACKs but left
   the RAM queue-feedback string in place. A subsequent approved
   conversation on the same Activity could show the previous queue hint.

## Production-safe changes

- Each of the three real nearby debug activities uses insets in this
  order on the outer scrollable root:
  `fillMaxSize → statusBarsPadding → navigationBarsPadding →
  imePadding → verticalScroll → padding(20.dp)`.
- Debug-only Android manifest uses `adjustResize` for *exactly those*
  three activities. No release manifest, targetSdk, mic or radio
  permission changes.
- Shared `DebugVerifiedConversationPanel` clears focus and hides the
  Android software keyboard **before** opening Talk or invoking the real
  Disconnect callback. It preserves M6-B/M6-C idempotent Talk → Text
  pending-floor cancel/active audio release and on-dispose cleanup.
- Bluetooth debug Activity sets `FLAG_SECURE` prior to loading local
  QR identity or composing any UI, matching existing Wi-Fi Direct screens.
  This is platform *best effort*: no claim of preventing all cameras,
  accessibility capture, external cameras or OS-specific leak paths.
- Real Bluetooth STOPPED state and fresh authenticated Ready event clear
  `chatSendFeedback`, which contains only a generic queue status.
  Restart still requires fresh QR/TLS/dual approval. No automatic
  reconnection or trust restoration.

## Verification and limits

- Existing Android/JVM/Python/security CI is required on the **exact
  final PR head**, then separately on merged `main`.
- `scripts/tests/test_m6_f_keyboard_capture_recovery.py` tests
  source contract insets, debug manifest, focus handling,
  `FLAG_SECURE`, stopped session reset, release flags and other guards.
  Old M6-A static expectation updated to recognize safe Disconnect
  wrapper without weakening the real owner-callback check.
- **NOT RUN:** physical devices, real authenticated Bluetooth/P2P
  conversation, TalkBack/IME accessibility, keyboard overlap at
  portrait/landscape/200% font, Android 12–16/OEM variations,
  screenshot/Recents capture attempts, or independent privacy review.
  There is no Android emulator/adb in the current development container.
- The existing `m4-ui07-manual-emulator.yml` workflow only covers
  **safe, disconnected** scenes and a local text sandbox, not live
  authenticated Text/Talk. No synthetic connected screenshot was
  created. It needs an explicit manual run for its existing coverage.

## Device acceptance (addendum, not a new QA batch)

When two phones are available for the one consolidated test matrix,
add to C07/C11/C12/C16: open keyboard, paste emoji, toggle Text ↔ Talk,
disconnect, rotate/landscape and 200% text. Ensure IME dismisses on
Talk/Disconnect; buttons remain reachable with IME, no unexpected mic
activation, and rejected send draft is retained. Check Bluetooth
screenshot/Recents redaction without sharing QR or actual message
screenshots; also review new QR/human approval required on reconnect.

**Release stop:** until hardware/interoperability, visual/large-text,
independent crypto/privacy/storage and accessibility audits pass,
keep `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE`
all **FALSE**. This milestone does not certify messaging for secrets,
medical or emergency use.

**Rollback:** revert M6-F alone if real-device IME insets cause an
OS-specific regression. No signing-key, identity, stored-history or
transport resets.
