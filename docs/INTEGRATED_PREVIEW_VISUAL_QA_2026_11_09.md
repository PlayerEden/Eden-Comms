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
