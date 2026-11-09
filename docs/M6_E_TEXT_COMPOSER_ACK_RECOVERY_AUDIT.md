# M6-E — Unicode-safe text composer, honest delivery states and accessible timeline

**Date:** 2026-11-09. **Status:** source implementation / automated checks pending.
No real two-phone test, screenshots, TalkBack pass, IME/OEM acceptance,
network/security certification or physical device privacy review has occurred.

## Scope and observation

The two existing verified debug text panels for Bluetooth Classic and
Wi-Fi Direct used `.take(280)` on UTF-16 code units. That silently truncates
Unicode input, can split a surrogate pair, and disagrees with the
280-*code-point* validation used by the actual debug wire. Both panels also
rendered each message in an unconstrained vertical stack, and Wi-Fi Direct
did not visibly explain a failed queue attempt or confirm that the draft
was retained. A local queue acceptance or peer-app ACK must never be called
a delivery or human read receipt.

## Changes

- **Both approved-connection text panels:** use the same existing
  `DebugTextDraftPolicy` for candidate acceptance and IME/button send,
  rejecting invalid or over-limit paste rather than truncating. Show
  code-point count, 2–4-line composer and a 52dp Send button.
- **Reading longer threads:** bounded 236dp vertical timeline with
  stage-independent manual scrolling; if a user scrolls back, do not force
  auto-follow. Expose `JUMP TO LATEST` when appropriate. Message body/status
  form one screen-reader item. This is a source-layout change; actual
  large-font and screen-reader behavior still needs device verification.
- **Truthful status copy:** `Received on this phone` for incoming;
  `Written to encrypted test link here — no peer-app ACK observed` for
  an outgoing local write; `Other app returned ACK — not a read receipt`
  when a real authenticated app ACK is observed. The queue attempt is
  explicitly distinct from these events.
- **Bluetooth and Wi-Fi Direct send feedback:** show `Queued on this phone`
  only after the actual existing offer succeeds, or `Not queued. Draft kept`
  if it fails. Bluetooth and Wi-Fi Direct host/join keep the draft on
  refusal and clear it only after real local queue acceptance. A real
  written event clears stale queue feedback; failures never claim success.
  Source event/ACK owners and existing privacy controls are unchanged.
- **Accessibility:** heading semantics, one merged message/status item,
  bounded view, polite text-only queue outcome announcement. No message
  contents are injected into new status, logs, QA count exports or storage.

## Evidence / gates

- Kotlin/JVM `app/src/test/java/com/edenpractical/comms/ui/DebugNearbyTextUiCopyTest.kt`:
  actual copy, Unicode/code-point and invalid-input boundaries.
- Python `scripts/tests/test_m6_e_text_composer_ack_recovery.py`: UI/source
  lifecycle, accepted vs rejected queue paths, copy boundaries and
  release guardrails.
- Previous F2-Q and F3-C source tests updated specifically to stop
  requiring unsafe UTF-16 `.take(280)` truncation.
- Final-head PR Android debug APK compilation/JVM/Python/security tests
  must pass; independent postmerge main CI must pass separately.
- **NOT RUN:** manual emulator sizes, 200% font, landscape/light/dark,
  real TalkBack/keyboard, radio packet/ACK interoperability, device mic,
  on-phone data inspection and independent security review.

## Boundary / rollback

No new transport, identity, QR, TLS, approval, crypto, protocol, message
size, data retention, permission, background task, server or production
feature. UI may be rendered only by the already-verified debug parent.
Existing `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE`
release flags stay FALSE. If a real UX regression is observed, revert
M6-E only; do not change signing identity or wipe user data to roll back UI.

## Next

1. Exact-head and postmerge CI; remediate source failures, do not wave through
   failed checks.
2. Emulator visual QA: small/large portrait, landscape, dark/light,
   200% text and IME visibility, message scrolling/Jump to latest.
3. Existing single consolidated 18-case x three-method physical two-phone
   matrix; add send-failure and emoji cases, but record NOT RUN until observed.
4. Independent protocol/storage/privacy/accessibility review before any
   production capability flags are enabled.
