# M6-D — QR stage instructions and accessible setup status

**Date:** November 9, 2026. **Status:** Android source changes and source
regression; Android emulator screenshots, TalkBack, physical QR/radio and
two-phone acceptance **NOT RUN**.

## Problem

The Bluetooth setup showed transport diagnostics and numbered buttons, but
there was no consistent explanation of **what the other phone must do next**
for each stage. Wi-Fi Direct showed "Step 1 — Verify nearby person" during
the entire three-QR exchange, even at the second or third code. That can make
novice users think the flow is stuck or that showing a code is approval.

## Changes in debug UI only

- Bluetooth Classic: present actual two-code stage, host/join next action,
  and distinct "codes exchanged" state after QR preparation. Explain that a
  prepared QR exchange is still an unverified link.
- Wi-Fi Direct: present QR 1/3, 2/3, 3/3 or codes exchanged based strictly on
  the coordinator's observed stage. Give specific instruction for each of the
  eight role/stage states, rather than a persistent Step 1 heading.
- Add a screen-reader heading and polite status announcement on each panel.
  Preserve camera retry/cancel, existing accessible-size targets and explicit
  device-identity creation.

**Security boundary:** This is stage-derived presentation. No new
discovery, automatic camera retry, TLS, QR payload, approval, Keystore,
storage, message, radio, permission, protocol or release behavior. A visible
step or code comparison never proves authentication. Production CAN_* remain
false; no new APK or hardware-success claim.

## Automated evidence

- Dedicated Python source regression in
  `scripts/tests/test_m6_d_qr_walkthrough.py` checks each stage,
  actionable copy, retained controls, accessibility properties and release
  guardrails.
- Exact-head Android/Gradle/JVM/Python/security CI and separate postmerge main
  CI must pass before marking the milestone complete.
- The existing `docs/TWO_PHONE_ACCEPTANCE_MATRIX.md` remains not run.

## Follow-on work

Inspect actual small/large/landscape/dark/light Android screens and
TalkBack focus; pair Bluetooth and Wi-Fi Direct on two different phones;
revisit stage copy from real confusion reports. Then audit text composer
accessibility, delivery-versus-ACK language, microphone permission behavior,
and real background disconnect. No credential or peer-trust shortcuts.

**Rollback:** revert only the M6-D UI/docs/tests PR. Session protocol and
on-device stored data are unaffected by this source-only presentation pass.
