# Eden Comms — integrated Preview + real emulator visual QA

**Date:** 2026-11-09. **Branch:**
`feature/integrated-preview-and-emulator-visual-2026-11-09`.

## Why this is the next milestone

The owner wants the COMPLETE usable Android app ready for visual and
hands-on acceptance, not another succession of small isolated
reliability PRs. Current `main` already has actual debug-only,
QR/pinned TLS/dual-approved one-to-one encrypted test TEXT and
peer-app ACK and bounded foreground voice on **same Wi-Fi,
Bluetooth Classic and Wi-Fi Direct**. Production release flags
remain deliberately disabled. Code CI has passed across prior
milestones, but there is **no on-handset two-phone acceptance**.

We found that the old M4-UI07 emulator visual driver was itself
outdated: it tapped Home `CONNECT NEARBY` and expected the Wi-Fi
QR screen immediately, although the app now correctly shows an
intermediate three-method chooser. It also expected a `COMING
SOON` voice page, although Voice/PTT now routes to method selection,
and an older Help heading. A source/Gradle pass would never detect
those broken visual navigation assertions.

## Integrated build and visuals (real APK, no mock session)

- Use the already-reviewed
  `.github/workflows/offline-preview-apk.yml` pipeline and
  `com.edenpractical.comms.preview` isolated development
  package. Preserve pinned public AOSP **test-only** signer,
  monotonic build version, explicit `main` and SHA evidence,
  no production keys, accounts, forced cloud or release unlock.
- On the **same Actions run**, a new `integrated-visual` job
  downloads the exact signed Preview APK published by
  `build-preview`. It installs it in an API 35 Pixel 5
  emulator and drives the actual native UI; no debug sandbox
  APK can be quietly substituted.
- The driver captures real Home, **three-method chooser**,
  **Same-Wi-Fi inactive QR guidance**, Help, Settings, privacy,
  local-only text sandbox, empty keyboard, compact layout,
  200%-font Home, landscape and the real Voice → Connect
  chooser. It records sanitized `summary.json` plus PNG files.
- No camera, QR contents, radio scan, live socket, peer identity,
  consent, message, microphone or voice sample is created in
  this visual run. Such steps require two real phones. Physical
  device detection is actively refused before ADB install.
- A failed screen label/IME assertion must make the visual job
  fail; previous PNG evidence and a **sanitized** failure
  category remain. No fake PASS or unverified screenshot.
- The packaged `START-HERE-TEST.md` now begins with a concise
  real end-to-end test card rather than outdated Bluetooth
  diagnostic instructions.

## First real integrated run — build PASS, visual harness blocked

- [PR #162](https://github.com/PlayerEden/Eden-Comms/pull/162)
  merged as `b25128017fd83bb35757d0d992f84d81262dd56e`.
  Exact final PR-head CI PASS:
  [42739102672](https://github.com/PlayerEden/Eden-Comms/actions/runs/42739102672);
  independent merged-main Android CI PASS:
  [42739390819](https://github.com/PlayerEden/Eden-Comms/actions/runs/42739390819).
- [First integrated Preview run 42739390527](https://github.com/PlayerEden/Eden-Comms/actions/runs/42739390527):
  its **installable Preview APK build PASSED**, versionCode
  **200010**, source `b251280`, isolated package
  `com.edenpractical.comms.preview`, audited pinned public
  development signer. The 17 MB APK was actually created.
- Its **emulator visual job FAILED BEFORE INSTALL**. The
  `reactivecircus/android-emulator-runner@v2` action executes
  each `script:` line in a separate shell. The original
  block first set `apk=...` in one line, then used
  `test -s "$apk"` on the next, when `apk` had already
  been lost. The action's log shows the failing `test`
  and NO driver execution; **zero screenshot evidence** and
  zero real Android UI assessment. This is a harness
  execution bug, not evidence that app navigation failed.
- Narrow recovery: invoke `python3 scripts/emulator_visual_qa.py`
  in **one single `script:` command**, resolving the
  downloaded same-run APK path inline. Added a Python
  source regression to prevent future per-line shell
  variable assumptions. A new signed Preview and emulator
  screenshot run must be actually checked; no claim of
  visual PASS before it is observed.

## Second integrated run — real APK verified; emulator launch component corrected

- [Signed Preview #200011](https://github.com/PlayerEden/Eden-Comms/actions/runs/42742224371/artifacts/13730070434)
  was **successfully built, signed, package-identity audited, and uploaded** from
  `180c85c3c3c170fb63cbea7e40f7ab6f2a127d35`. The exact release flags
  remain disabled; this is a separate installable **debug Preview**, not a
  production messenger or independently assessed secure build. The failed
  parent workflow status does **not** invalidate its successful APK artifact.
- Its emulator job successfully booted an Android 35 emulator and reached
  the test script, but failed attempting
  `am start ... com.edenpractical.comms.preview/.MainActivity`. The Kotlin
  activity is **`com.edenpractical.comms.MainActivity`**, while the Android
  `applicationIdSuffix = ".preview"` changes only the APK package identity.
  The correct explicit component is
  `com.edenpractical.comms.preview/com.edenpractical.comms.MainActivity`.
  No app-screen captures succeeded, so this is **a launch-driver failure**,
  not evidence that the application's Home screen crashed.
- The follow-up fix modifies **only** the emulator screenshot script and its
  source regression to keep the fully qualified Activity class while changing
  the installed package. No actual transport, text, voice, permissions,
  signing, manifest or release-capability behavior changes.
- The visual run must still be observed before asserting even
  disconnected-screen navigation/large-font PASS. Two-phone and independent
  security acceptance remain NOT RUN.

## Screenshot review rubric — inspect the real PNGs

The emulator workflow's PASS only establishes that required UI labels
were found, the empty keyboard opened and screenshots are nonblank.
The **images** still need qualitative review before declaring
visual acceptance. Compare all captured scenes side-by-side:

- **Home:** is CONNECT NEARBY the obvious primary action? Are
  Settings and How it works discoverable without hunting? Do
  text/voice tiles make it clear a connection is needed?
- **Three-method chooser:** all three actual radio options must
  appear with understandable distinctions, adequately tall
  touch targets and no untrue "connected" indicator. Check
  whether third card requires excessive scrolling.
- **Same-Wi-Fi idle QR:** no fictitious approval or session;
  show a clear supported Wi-Fi state or honest lack-of-network
  guidance. A real radio partner is not present on the emulator.
- **Settings, Privacy, Help:** reading order, contrast and
  navigation must be clear, with no clipped disclaimer and no
  path to developer-only tools masquerading as normal chat.
- **Local-only text sandbox + keyboard:** no message is sent;
  check that the input and Send area are not obscured by IME.
- **Compact, 200% text and landscape:** legible heading, no
  sideways clipping, and visibly reachable connection actions.
  Preserve scrolling as an accessibility fallback.

For each observed issue, use **KEEP / TUNE / BLOCKED** and
identify the scene plus layout symptom; do not invent a score
or claim remote chat has been visually observed. The next
source fix should address the largest visible usability issue,
not another speculative networking micro-milestone.

## Acceptance checklist

- [ ] Exact final PR-head Android build, Kotlin/JVM tests,
  Python regression, security/privacy/permission/signer checks
- [ ] Merge approved single PR; independently verify postmerge main
- [ ] Main-triggered Preview signed APK artifact with exact SHA,
  package ID, versionCode, stable known public TEST signer
- [ ] Real API 35 emulator job PASS with PNGs + inspected visual
  summary; if FAIL, investigate and repair/re-run
- [ ] Owner one-phone first impression, screenshot/font/rotation
  and actual install-over behavior (still NOT RUN)
- [ ] Owner two-phone in-person fresh QR + approvals, typed
  text, peer app ACK and bidirectional audible PTT across
  each supported transport, timeout/recovery (still NOT RUN)
- [ ] Independent protocol/crypto/storage/backup/privacy and
  accessibility review before production release (NOT RUN)

## No greenwashing

A green source PR proves the source compiles/tests, not that
buttons are physically readable. A green emulator captures real
screens but does **not** establish remote communication.
A green physical two-phone run demonstrates behavior on tested
devices but is **not** an independent security assessment.
No user screenshots are fabricated. `ReleaseCapabilities` always
keeps CAN_CONNECT/CAN_SEND_MESSAGES/CAN_TRANSMIT_VOICE FALSE.

**Rollback:** revert integrated preview workflow/driver/guide
changes only; never reset existing Android package ID, app
identity or signing keys. The physical acceptance plan remains
one consolidated run, not 18 disconnected demo packages.
