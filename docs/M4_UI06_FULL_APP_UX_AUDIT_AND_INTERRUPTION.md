# Eden Comms — M4-UI06 full-app UX source audit and recovery refinement

**2026-11-07 • based on merged M4-UI05 main `4f1a9d7e` • source/CI audit only • no owner hardware QA**

## Scope and evidence

Reviewed the actual native Compose app routes and pairing/test chat code, read-only
transport status and recovery, QR camera, permission manifests, signing/upgrade
tooling, automated Kotlin/Python checks, and the development roadmap. This is
**not** an independent protocol audit, penetration test, device usability test,
TLS cryptographic certification, or production release assessment.

Cross-repository process references (not borrowed application code or keys):
- [Operation Phosphor emulator smoke](https://github.com/PlayerEden/Operation-Phosphor/blob/main/.github/workflows/android-emulator-smoke.yml): launch + screenshot + signed install-over regression, with physical-playtest limits explicitly separated.
- [Sector Rebound scoped UI regression](https://github.com/PlayerEden/Ricochet-Rumble/blob/main/.github/workflows/ui-ux-scoped-audit.yml): fixed viewport/layout evidence and before/after QA.
- [GridMates Sudoku visual QA](https://github.com/PlayerEden/SudokuTogether/blob/main/.github/workflows/visual-qa.yml): emulator screen captures and Android UI checks; current `main` is largely docs, so no claim about feature parity or current shipped state.
- [The Town Was Fine visual review](https://github.com/PlayerEden/The-Town-Was-Fine/blob/main/docs/visual/REVIEW_PROTOCOL.md): evidence-backed baseline, KEEP/TUNE/REVERT decision.

**Do not copy** game keystores, debug signing certificates, app identifiers,
state retention, automated chat reconnect semantics or game permissions into Eden
Comms. A diagnostic messaging test is intentionally more restrictive.

## Audit findings and disposition

| Priority | Area | Source-grounded finding | Action / gate |
| --- | --- | --- | --- |
| P0 | Android keyboard | Root Compose screen had status/navigation bar padding but no IME-aware outer inset; the test composer sits below a bounded message pane, and keyboard occlusion was not device-tested | **Addressed in M4-UI06:** IME padding on app root; physical keyboard / gesture / system-font verification remains open |
| P0 | Interruption after rotation/recreation | Navigation route uses saveable history but QR/link/chat state is intentionally in-memory. An Android recreation during a live pairing could return directly to an idle-looking pairing screen without explaining the old session is gone | **Addressed in M4-UI06:** save only an in-progress Boolean; after recreation, explain fresh QR requirement on idle pairing screen. No replay/reconnect or saved content |
| P1 | Test composer reachability | M4-UI04 displayed detailed local-write counts, a no-receipt explanation and expandable help *above* the message field and Send action, adding scroll in an active chat | **Addressed in M4-UI06:** send field/action appear directly after message list and Jump to latest. Retain a single clearly labeled, optional explanation **below** Send |
| P1 | Nested scrolling / viewport | App shell is vertically scrollable and chat has a separate 220dp message list. This is intentional to avoid unbounded chat but needs actual phone scroll-gesture scrutiny, landscape and 200% text | Keep behavior; do not redesign based on screenshots from other apps or untested touch assumptions |
| P1 | Session exit truth | M4-UI05 now guards system and in-app Back during active attempt; if a test ends, result offers a fresh-code restart and Done | Preserve; device test must exercise both Back paths, rotation, pause, background and peer disconnect |
| P1 | Safe APK updating | Existing internal-only signer workflow and `scripts/preflight_device_apk_update.py` intentionally require exact package, increasing code, continuous signer, protected credentials | Borrow verification *method* from game repos, not signer. Owner-run old→new install-over remains unproven; never claim in-place updates work |
| P1 | Consent/transport | Current debug same-Wi-Fi short text only after QR, pinned TLS and dual approval/confirmation; Bluetooth and Wi-Fi Direct have bounded separate diagnostics, not messenger transports | No change. Physical two-phone negative tests + independent cryptographic review before normal messaging |
| P2 | UI/accessibility | Phone screenshots started the M4 series, but no repeat Android screenshot audit, TalkBack traversal, font scale, tiny height, or keyboard verification has covered this head | Follow-up: add a **separate, opt-in Android emulator visual smoke** with sanitized screenshots and review against this exact source revision; no automation requiring owner devices |
| P2 | Voice and storage | Voice/PTT is only a preview, no microphone or normal message-history retention | Preserve release-closed flags and no sensitive content in test sessions |

## M4-UI06 implementation

1. The app shell uses Compose `imePadding()` after status/navigation bar insets,
   giving the focused message composer room when Android displays a keyboard.
   This is a structural mitigation, **not proof** of good physical keyboard UX.
2. M4-UI04 chat read/write labels, bounded 12-message RAM-only transcript,
   Hide/Clear, 220dp message viewport, scroll-follow, and visible End action
   remain. Technical local-write counters / explanation move below Send.
3. Save only `pairingWasInProgress: Boolean` with Compose saved UI state,
   derived from actual active QR/approval/demo stages. Its initially restored
   value is captured once; if a process/Activity recreation restores an active
   pairing route, the new idle screen explains that the old attempt cannot
   continue and fresh codes are required. Explicit reset/start dismisses the
   explanation. The current attempt's callback returns the Boolean to false
   upon an idle/result stage or explicit exit.
4. No saved peer identity, QR payload, certificates, transcript, draft, socket,
   approval or trust; no automatic retry, delivery/read acknowledgements,
   permissions, network/crypto code or voice changes.

## Automated and owner-deferred acceptance

- [ ] PR CI: Android debug compilation, pure Kotlin tests, Python M4/audit/source
  regression, release capability/permission/privacy gates, dependency and APK
  identity/signing checks and debug test APK artifact.
- [ ] Fresh merged-main Android CI, then record exact commit and run in PR.
- [ ] **Owner-deferred:** real portrait/landscape keyboard open and close,
  tap Send without content loss, physical TalkBack/200% font, nested scroll,
  Back gesture/dialog, Android rotation with active QR/live chat and two-phone
  socket-close behavior, secure independent protocol review.
- [ ] Later optional emulator smoke: launch, navigation, text scale,
  screenshot comparison, rotation and recreation; do not treat this as real
  network validation. Gate against Activity/Compose visual and APK exact SHA.

**Rollback:** revert M4-UI06 PR. No persisted chat or database migration.

## Next development priorities

1. Scope a focused Android emulator/screenshot QA harness inspired by other
   repositories, run only after workflow reliability and minutes cost are
   reviewed. Prefer static fake/sandbox data, never secrets or real QR payloads.
2. Review actual instrumented keyboard/200% font output and fix any clipped
   controls; keep only source-backed user claims until verified.
3. Perform owner-triggered two-phone and install-over QA only after the user
   explicitly says they are ready, then commission independent security review
   before production text or voice.
