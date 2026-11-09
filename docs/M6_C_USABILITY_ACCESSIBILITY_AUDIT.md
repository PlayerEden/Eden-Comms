# M6-C — Usability, accessibility and visual consistency audit

**Date:** November 9, 2026 · **Status:** source audit and focused fixes; actual
Android screenshots, TalkBack/device testing and two-phone QA **NOT RUN**.

**Scope:** existing debug first-run/Home → Connect → verified Bluetooth or
Wi-Fi Direct Text/Talk → optional privacy/Advanced; common Settings/Help and
Privacy & data copy. This pass intentionally does **not** modify radio discovery,
QR, TLS, human consent, PTT floor arbitration, encrypted persistence or release
capabilities. See [the complete 18-case two-phone test plan](TWO_PHONE_ACCEPTANCE_MATRIX.md).

## Source issues found and addressed

| Priority | Finding | Change | Evidence |
|---|---|---|---|
| P0 | The inactive Text tab used a direct selection assignment, bypassing the immediate Talk-release path. A disposal callback would eventually run but the button itself was inconsistent. | Both Text buttons now use the same **chooseText** handler; Talk disposal still releases/cancels. | M6-B microphone owner and M6-C source boundary checks |
| P1 | The privacy page said Bluetooth/Wi-Fi Direct voice was *unavailable*, and called encrypted text history/public peer memory **LAN-only** despite the F4/F5 implementations across transports. | Rewrote all six disclosure sections to distinguish debug capabilities, per-transport QR information, independent opt-in storage, OS permissions, and production restrictions. | Expanded JVM PrivacyPreviewContentTest |
| P1 | Nearby verified chat panel had a rounded card inconsistent with the app's angular FieldFrame design and no explicit selected-tab/disclosure semantics. | Matched 7dp cut corners, outlined surface, TalkBack selected/not-selected states, section heading and expanded/collapsed state labels. | New Python UX source tests; Android CI compile |
| P1 | Nearby Talk permission state was remembered even after leaving for Android Settings, where microphone access could be revoked. | Re-read RECORD_AUDIO on lifecycle ON_RESUME; **no automatic permission request**. | Android lifecycle source boundary tests; physical revoke QA open |
| P2 | Voice instructions described internal 20-ms PCM/GRANT records and told screen-reader users to activate twice to 'send', though audio now streams while held. | Plain-language 3-second hold / wait your turn / release to stop; correct keyboard/screen-reader two-activation guidance, polite status announcement. | M6-C UX source tests; TalkBack speech device review open |
| P2 | Home and help copy implied conversations could not be saved, and an obsolete restored 'ptt' route could show the old Coming Soon mock. Settings switch had no explicit accessibility label. | Explain session-only **default**, optional local encrypted text; legacy route opens the real Connect chooser, add Dark appearance switch label and semantic section headings. | M6-C UX source tests; Android CI |
| P2 | Privacy disclosure summary 'No normal messaging, voice, account or saved conversations' was misleading for debug testing. | Distinguish missing **production** messaging, cloud relay and accounts from existing *experimental* debug text, PTT and opt-in history. | Disclosure test |

## What is *not* claimed

No phone has been used in this pass. A Kotlin/Python test validates source
behavior and compiler contracts, **not** how a TalkBack user hears a
particular phone, a Pixel emulator screenshot, real large-font/landscape
layout, color contrast under Android OS settings, microphone permission
revocation across all OEMs, or reliable two-phone audio. Those remain
acceptance items. The app remains an unreviewed development build, not a
private/emergency communication service.

## Next focused passes (after exact-head and postmerge CI)

1. **Pairing walkthrough and recovery:** audit stage-specific Host/Join QR
   language at 200% font, show what the user must do next before technical
   diagnostics; maintain independent in-person approval and clear retry.
2. **Text accessibility:** inspect keyboard IME action, error and receipt
   announcements, scroll retention, text expansion and unambiguous sent vs
   acknowledged labels; no invented read receipt.
3. **Talk and privacy on-screen states:** check larger fonts/landscape
   controls, speaker/mic lifecycle state announcements, copy feedback, and
   full accessibility focus/order with TalkBack on a real phone.
4. **Visual QA:** run established manual emulator/screenshot matrix for
   small/large widths and light/dark themes; check tap targets, clipped
   text, contrast, edge-to-edge status/navigation bars, keyboard inset.
5. **Single field-device pass:** only when two phones available; run
   [the consolidated matrix](TWO_PHONE_ACCEPTANCE_MATRIX.md), document
   distinct actual device evidence and open narrow fixes. Separate
   independent security/privacy review is mandatory before production.

**Rollback:** revert only this M6-C PR if a verified UX regression appears.
No transport, Keystore identity, message history schema or signing keys
are intentionally changed here. Production `CAN_*` remain false.
