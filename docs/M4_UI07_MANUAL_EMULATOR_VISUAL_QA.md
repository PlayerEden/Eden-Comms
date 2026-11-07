# Eden Comms M4-UI07 — manual emulator screenshot and accessibility smoke

**Source implementation 2026-11-07. Manual emulator execution not yet performed.**

## Goal
Take the QA lessons from Operation Phosphor, Sector Rebound and GridMates without
importing game source, keys, persistence or permissive network behavior. This
is a reproducible visual smoke test; it cannot validate secure communications.

## Implementation
- A new Actions workflow is triggered only by a human from the reviewed main
  branch. It builds the same debug Android source, runs existing Kotlin/Python
  security, permission and APK signing checks, and launches a Pixel 5 Android 35
  emulator without a camera.
- An emulator-only ADB driver refuses physical devices, navigates only safe
  idle and local sandbox routes, captures ten evidence screenshots for Home,
  idle QR choice, Help, Settings, Privacy, local text sandbox, empty keyboard,
  compact size, 200% font and landscape Voice disabled placeholder.
- No real QR display/scan, public-key identity creation, approval, pair attempt,
  message transmission, audio/PTT or radio discovery. Nothing is typed.
- Screen checks use UI accessibility labels plus basic nonblank PNG validation.
  Results upload for seven days with source revision and emulator API, not
  arbitrary XML trees, addresses, keys, drafts or network diagnostics.
- A Python unittest checks manual-only workflow, emulator restriction, label
  parsing, blank PNG rejection and safe-route source contract during normal
  PR CI without starting an emulator.

## How to use
After green PR and fresh main CI, open GitHub -> Actions ->
M4-UI07 manual emulator visual QA -> Run workflow, selecting main. This is
not launched from ordinary PR builds. Examine screenshot artifacts and the
summary JSON. Record the exact run, full output and KEEP/TUNE/REVERT decisions
in issue #82. A successful Kotlin/source CI is NOT a successful emulator test.
Do not remove assertions if device hierarchy or keyboard checks fail: inspect
failure logs and update the harness deliberately.

## Gates
- [ ] New PR green Android/JVM/Python/security/privacy/signing CI
- [ ] Fresh merged-main CI green
- [ ] Separate manual emulator workflow successful with screenshot artifacts
- [ ] Screenshot human review across compact/landscape/200%-font and keyboard
- [ ] Physical user-triggered Android/TalkBack/two-phone/upgrade QA
- [ ] Independent protocol security assessment

No production text or voice enablement, message storage, radio permission
changes or crypto alterations. Revert PR for rollback; no saved-data migration.
