# Eden Comms — Full project/GitHub quality audit

**Audit date:** November 10, 2026.
**Source baseline:** `PlayerEden/Eden-Comms` public `main` at
`359443f3a29ef7cce5442b54e94124dd0eaea72a`.
**Scope:** repository structure, normative product/design docs, current
Android app/manifest/build boundaries, core debug transport status,
owner visual feedback, GitHub issues, workflows, tests, artifact evidence,
privacy/release gates, documentation continuity and next work priority.

**Important limits:** This is a **repository and evidence audit**, not a
complete manual line-by-line security assessment, device interoperability
test, proof of cryptography, OS accessibility validation, UX focus group,
legal opinion or production certification. Reaching a source or CI checkpoint
does not establish a physical end-to-end pass. The local Android device
session and independent protocol review were NOT run during this audit.

## Executive assessment

**Assessment:** A real integrated, owner-installable Android **debug
prototype** with substantial source-level functionality, working source CI
and a clear local-first vision; **not an accepted/secure production app**.
Biggest bottlenecks are incomplete human/device evidence and fragmentary,
dated project truth — **not** lack of another minor networking milestone.

**Key discovery:** 584 source-tree files, **152 documents under `docs/`**,
84 production/main Kotlin files, 73 debug Kotlin files, 123 JVM `*Test.kt`
classes, 103 `scripts/tests/test_*.py` Python files and five workflows.
Before this pass, no file named "design bible" or single consolidated
contemporary product/UX contract existed. The documentation is thorough
but fragmented enough that older, now-false "coming soon" claims persist.
This audit adds [Eden Comms Design Bible](EDEN_COMMS_DESIGN_BIBLE.md) as
an **owner-reviewable synthesis**, keeping charter/decisions authoritative
on their respective security/approval boundaries.

## Verified CI and artifacts on baseline

| Evidence | Observed result | What it proves / does not |
|---|---|---|
| [Final PR #165 checks](https://github.com/PlayerEden/Eden-Comms/actions/runs/42898321068) | **SUCCESS** | Compiled and passed configured source/JVM/Python checks on exact PR head |
| [Independent merged-main debug CI](https://github.com/PlayerEden/Eden-Comms/actions/runs/42898764151) | **SUCCESS** | Baseline source tests and Android package checks passed |
| [Owner Preview 200013 build job](https://github.com/PlayerEden/Eden-Comms/actions/runs/42898764189) | **SUCCESS** | Actual `com.edenpractical.comms.preview` APK signed/test-audited/uploaded |
| [Same-run Preview artifact](https://github.com/PlayerEden/Eden-Comms/actions/runs/42898764189/artifacts/13803015002) | **Exists; ~16.7MB ZIP** | Installable APK delivery; **not** functional QA |
| [Same-run emulator visual job](https://github.com/PlayerEden/Eden-Comms/actions/runs/42898764189) | **FAILURE** | Emulator booted, driver failed at launch; **no accepted screenshots** |
| Owner screenshots of earlier Preview Home | **Observed** | Real Home first impression and scroll discoverability gap only |
| [Consolidated two-phone matrix](TWO_PHONE_ACCEPTANCE_MATRIX.md) | **NOT RUN** | No demonstrated two-way text/PTT over three real phone transports |
| [Independent crypto/privacy review](PRIVACY_SECURITY_CHARTER.md) | **NOT RUN** | No basis to enable production `CAN_*` or promise audited E2EE |

The preview workflow's overall failure is **not** proof the APK build failed;
it consists of a successful build job and a failed visual job. Do not label
the whole pipeline green or treat the test APK as production.

## Findings and priorities

Severity = **P0 release-blocking**, **P1 next delivery priority**,
**P2 maintenance / important before release**, **P3 optional future
housekeeping**. "Open" means still lacking observed acceptance; a source fix
may be present but unverified on device.

### P0 — Production claims blocked (correct existing safety boundary)

**Evidence:** `app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt`
keeps `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE`
**FALSE**. Experimental functionality is enabled in debug/build-only
paths. [Charter](PRIVACY_SECURITY_CHARTER.md),
[release plan](GOOGLE_PLAY_NORTH_AMERICA_LAUNCH_READINESS.md),
[real-phone matrix](TWO_PHONE_ACCEPTANCE_MATRIX.md).

**Open:** independent protocol/crypto review; malicious-peer,
replay/MITM, device-/OEM-specific TLS and radio/voice tests;
Keystore/expiry/delete/Android backup/migration verification;
TalkBack/UI, permission/foreground/mic lifecycle, valid publisher
signing, Play/legal/privacy readiness. **Keep production blocked.**

**Status:** release gate correctly closed; not a new exploit finding.
Green CI, previews and code comments are not substitutes.

### P1-A — Automated visual acceptance harness was broken

**Observed:** Latest `integrated-visual` GitHub Actions job failed at
`adb shell am start -W -n com.edenpractical.comms/com.edenpractical.comms.MainActivity`
even though the workflow supplied
`--package com.edenpractical.comms.preview`.

**Root cause found in real source:** In
`scripts/emulator_visual_qa.py`, a prior source edit wrote literal
`\\n` sequences **inside one Python comment**. The entire
`PACKAGE = opts.package` / `ACTIVITY = ...` setup was commented
out. Existing string-presence tests did **not** detect the
non-executing statements.

**Source repair in this audit:** Restore actual multi-line Python
statements and add
`scripts/tests/test_preview_emulator_package_binding.py` that executes
the real argument parser with mocked Android driver for both
`.preview` and normal package IDs and checks the resulting Activity
component. **Still open until exact final PR/main CI and a real emulator
run produce inspected screenshots.** The screenshots are a delivery
artifact, not a connection/security acceptance.

### P1-B — No consolidated physical functional acceptance

**Evidence:** [18 cases × 3 transports](TWO_PHONE_ACCEPTANCE_MATRIX.md)
still marked NOT RUN. Current source includes debug LAN, Bluetooth
Classic and Wi-Fi Direct QR/pinned TLS/dual-consent messaging and
bounded PTT, but source review does not establish real OS pairing,
audio audibility, throughput, disconnect/recovery or independent trust.

**Action:** one consolidated two-phone session when the owner has the
devices; log separate PASS/FAIL/BLOCKED per transport/case, record
only sanitized observations, prioritize failures discovered there.
Do not ask repeatedly for new test sessions or add isolated previews.

### P1-C — Missing present-tense product/design reference

**Evidence:** `docs/PRODUCT_VISION.md` is dated **November 3** and
remains proposal text; `UI_UX_RESET_BRIEF.md`,
`UX07_VISUAL_BASELINE.md` and `UX_APPROACHABLE_FINAL_POLISH.md`
represent successive visual and feature stages. Some still say
Bluetooth/Wi-Fi Direct/voice/host-join are disabled or "Later."
`DECISIONS.md` and `DATA_RETENTION_AND_EXPIRY.md` still describe
some peer/history features as LAN-only despite the later F5-C debug
integration. Color proposals also differ from current graphite/olive
tokens. Multiple dated roadmaps appear current at different moments.

**Action implemented:** [Design Bible](EDEN_COMMS_DESIGN_BIBLE.md) now
collects current scope, user journey, live palette, security invariants,
delivery semantics, state of each feature and acceptance ladder in one
front door. Retain old notes for rollback/provenance, but label them
**historical**, and point new work at Bible + charter + current source.
This consolidation is **draft for owner review**, not an invented
approval of new product policies.

### P2-A — Documentation and issue status drift

**Evidence:** the root `README.md` has grown to roughly **32 KB** of
milestone updates and its leading description centers an old test
stage. `CURRENT_HANDOFF.md` has "active" old branch/PR CI steps
after merges. `DECISIONS.md` contains historical proposed status
notes, sometimes literal escaped-newline text. There are **nine open
GitHub issues**, all returned without labels; core Bluetooth, Wi-Fi
Direct, voice and retention issues (#59–#62) remain broadly titled
without distinguishing *implemented debug source* from outstanding
*physical/security acceptance*.

**Action:** link Bible/audit prominently, keep README as product
entry not a chronological changelog, keep implementation history in
`WORK_LOG`, and annotate existing issues with concise
"source/CI/device/review" checkpoints rather than closing them
prematurely. Do not delete historical milestone records.

### P2-B — Mobile UX and accessibility coverage is partial

**Observed owner feedback:** phone dark-theme Home is legible, but
lower Settings and How it works were below the fold with no cue.
[PR #165](https://github.com/PlayerEden/Eden-Comms/pull/165) added a
conditional, tappable overflow hint and was source-CI verified.
**The revised APK was not yet accepted on the owner's phone.**

**Still open:** actual large-font/200% readability, TalkBack,
light mode, portrait/landscape edge cases, permission denial,
IME/chat controls, QR camera transitions, screen-reader status and
per-session Android Back. Current failed emulator harness means
those wider screen captures are missing.

### P2-C — Test quantity exceeds real-world evidence breadth

123 Kotlin JVM test classes and 103 Python test files show substantial
care around source contracts, retries and negative security gates.
They are valuable; nevertheless some Python tests assert source
strings, and **no** emulator-connected or physical two-phone
integration tests are accepted. Neither test count nor a green Gradle
run can prove radio interoperability or actual microphone behavior.

**Action:** keep unit tests and architecture checks, but organize the
next QA time around visual screenshots, one-device experience,
two-device test matrix and security review outcomes. The new test
for P1-A deliberately validates executing parser behavior rather
than only a source string.

### P2-D — Test APK provenance and privacy must stay explicit

The installed Preview uses a **public AOSP test signing key** and a
distinct `com.edenpractical.comms.preview` ID. This is convenient
for update/install visual QA, **not proof of software origin or a
secure distribution channel**. The ordinary debug APK and future
production package/signing are separate. No automatic uninstall
or copying identities between packages. AndroidManifest release
uses `allowBackup=false`, data-extraction rules and cleartext
traffic off, but the **device/OEM migration and data deletion tests
remain open**. Continue using harmless test data.

### P3 — Retention of selected visual evidence

The integrated visual artifact currently has a short **two-day**
retention. Useful for temporary QA cost/storage control, but a
successful key baseline should have a deliberate *sanitized* evidence
record in GitHub before it expires. Never store QR frames, keys,
private text, PTT audio or sensitive phone screenshots in the repo.

## Core component audit summary

| Domain | Good present evidence | Remaining risk |
|---|---|---|
| Product scope | Clear local/no-account mission and three-method chooser | Several contradictory old guides; Bible now reconciles |
| Android app | Native Compose, versioned Preview, owner Home screenshots | Full on-device UX regression not evidenced |
| LAN | Real debug QR/TLS/approved Text and ACK source | No consolidated physical peer confirmation |
| Bluetooth | Real debug RFCOMM authenticated text and voice source | Permission/radio/OEM/2-phone evidence missing |
| Wi-Fi Direct | Real debug group/interface/QR/TLS/text/PTT source | Group cleanup and physical session QA missing |
| Text/delivery | Separate local queue/write/peer-app ACK semantics | Human-read absent by design; need phone observation |
| Audio/PTT | Bounded foreground and floor logic, test harnesses | Audible 2-phone timing and denial/interruption not proved |
| Storage/privacy | Opt-in local Keystore encrypted retention source | OS backup/reinstall/expiry/delete not assessed independently |
| Automated CI | Exact SHA main Android CI and signed APK success | Emulator visuals failed; parser root cause isolated |
| Release | Production capabilities deliberately FALSE | Independent protocol/legal/Play gates outstanding |
| GitHub | Public source, detailed PR/work log, no open PR at baseline | Nine broadly-open unlabelled issues, dated status in docs |

## Recommended sequence (avoid micro-milestone churn)

1. **Now:** merge the audit-only documentation and narrow emulator
   package-binding regression on green exact-head CI; independently
   verify merged-main CI.
2. **Next:** run the corrected **same integrated Preview** emulator
   job, ensure real PNGs exist, inspect scenes visually and fix any
   actual layout/navigation issue. Keep source behavior unchanged
   unless a real problem is observed.
3. **Owner visual pass:** review current APK Home cue, Connect method
   chooser, Settings, privacy, font/rotation, and basic offline
   controls. Use actual screenshots to prioritize visual changes.
4. **One two-phone acceptance session:** all three transports,
   real text/peer-app ACK and audible PTT, interrupts and data
   retention; record case results instead of "looks functional."
5. **Before production:** independent crypto/protocol and
   privacy/storage audit; launcher/accessibility and official
   Android/Play legal/permissions review; staged release checks.
6. **Documentation hygiene:** migrate current status into Bible,
   handoff and issue checklists; leave old records archival.

## GitHub owner change rules

- Real changes go through a short branch/PR with an accurate issue or
  observed behavior, executable tests where practical, exact-final-head
  CI, squash merge, independent main verification and rollback note.
- New functionality must correspond to the approved Bible scope and
  cite security charter/decision; do not add a "test-only" fake route
  that resembles an authenticated real conversation.
- Keep separate **SOURCE / CI / VISUAL / PHYSICAL / INDEPENDENT SECURITY**
  acceptance fields. Never declare future tests as completed.
- A design disagreement gets one dated decision, not an extra
  overlapping roadmap. Update Bible for current UX only after
  owner confirmation.

**Conclusion:** Eden Comms is further along in *code* than in measured
product acceptance. The highest-value next step after fixing the
emulator test is **seeing and testing the real integrated app**, not
another small radio callback refactor.
