# Eden Comms M4-UI09 — readable native navigation headers

**2026-11-07. Source-based accessibility improvement; emulator/hardware QA still pending.**

## Finding

Compose TopBar restricted both Home branding and all route titles to one line
even on narrow Android screens and at 200% font scale. Long headings such as
TEST CONVERSATION or LAN CONNECTION TEST may truncate in the app bar. This is
a source-backed risk, not a screenshot-confirmed defect.

## Change

- Read live available width and Android font scale through BoxWithConstraints
  and LocalDensity. Pure TopBarLayout.maxTitleLines returns 2 when content
  width is below 360dp or font scale >= 1.3 and 1 on normal wider screens.
- Allow the header height to grow (retain heightIn(min = 64.dp)), allocate the
  remaining Row width with weight to both Home branding and page titles,
  and retain a two-line ellipsis fallback where unavoidable.
- Keep the full 48dp accessible Back target, the same Back callback, route
  history, stage-aware leave confirmation and QR/chat cleanup. No session
  data crosses into header layout policy.
- JVM boundary tests and Python source/release-boundary assertions.

## Explicit verification gates

- [ ] PR Android compile, Kotlin and Python regression, security/permissions
  and APK package/signing checks.
- [ ] Fresh merged-main CI.
- [ ] First manual M4-UI07 emulator workflow and screenshot inspection of
  narrow/200%-font Home and route headers. The workflow is opt-in on main.
- [ ] Owner-deferred real Android/TalkBack/keyboard and two-phone acceptance;
  independent protocol review and APK signer continuity QA.

Source tests cannot establish visual legibility on an actual screen. This
does not alter network, TLS, approval, QR, voice, messages, storage,
permissions or production availability flags. Rollback by reverting this PR.
