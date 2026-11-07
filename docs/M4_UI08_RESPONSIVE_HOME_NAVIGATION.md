# Eden Comms M4-UI08 — responsive Home choices

**2026-11-07 | UI-only source refinement | physical QA deferred**

## Finding

Home always rendered Text and Voice / PTT as side-by-side half-width tiles,
regardless of available content width or Android font scale. The app itself
has 20dp horizontal padding, so a 320dp viewport leaves just 280dp for both
tiles and their internal padding. This is a source-backed risk of wrapping or
clipping, not an observed screenshot failure.

The subtitles also did not clearly communicate that the only functional text
flow is an unreviewed development test and voice transmission is unavailable.

## Implementation

- HomeModeLayout.shouldStack checks the available content width (below 360dp)
  and Android font scale (at least 1.3), showing full-width vertically
  stacked Text / Voice tiles in those conditions. Wider normal-font screens
  retain the side-by-side overview.
- The Text tile reads Development test only; Voice / PTT reads Not available.
  The existing debug-only connection entry and disabled voice placeholder
  remain unchanged.
- Existing icon, clickable tile, minimum card height, primary Connect Nearby
  action and How it works/Settings entry points remain unchanged.
- Added pure Kotlin threshold tests and Python source/flag regressions. No
  transport, QR, key, permissions, privacy storage or release capability change.

## Acceptance

- [ ] PR Android/Kotlin/Python/security/privacy/permissions/APK CI.
- [ ] Fresh merged-main Android CI.
- [ ] First manual M4-UI07 Android emulator screenshot run, then inspect compact,
  large-font and normal Home appearance for real wrapping/clipping.
- [ ] Owner-deferred physical TalkBack, scrolling, text scales, 2-phone link,
  APK update and independent protocol/security review.

Passing source CI does not prove visual or accessible layout on Android.
Rollback by reverting this PR; no persisted schema or migration.
