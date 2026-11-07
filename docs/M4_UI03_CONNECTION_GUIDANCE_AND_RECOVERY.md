# Eden Comms M4-UI03 — Readable connection progress and recovery

**November 7, 2026 • source/CI milestone; physical testing deferred by owner.**

## Design

Previous on-phone screenshots showed a strong visual system but an intimidating
density of technical descriptions. M4-UI01/02 reduced technical copy and exposed
optional help. This milestone makes **what is happening right now** and **what
to do next** immediately understandable in the experimental same-Wi-Fi QR
test, without modifying the security protocol or connection behavior.

### Stage cues, bound to real UI state rather than a timed animation

A new plain Kotlin `NearbyConnectionCues` object maps the existing
`LocalPairingTestScreen` stage and actual observed approval/confirmation
flags into a short heading and next action. The three phases are:

1. **Exchange codes.** Role-specific prompts for showing the first QR,
   scanning the host QR, showing the joiner's reply and scanning the reply.
   QR exchange is never called authentication or approved messaging.
2. **Check the link.** Both apps stay on the same Wi-Fi with no approval
   implied merely by a connection attempt.
3. **Both people decide.** Distinguish neither person approved, this
   phone's approval observed, peer approval observed, both observed and
   pending final confirmations. The live session state machine alone may
   transition to the debug chat stage after its existing checks.

When the already-existing screen reaches `demo`, the cue identifies it as
**TEST CONVERSATION — ACTIVE**, while explicitly cautioning that it is not
reviewed secure messaging. Idle and terminal states never show an active
session cue. These cues are pure text/layout; no timers, radio interfaces,
networking, UI-generated trust, automatic approval or retries.

Redundant static one-line instructions underneath QR display/camera steps
are removed; the old Cancel, camera, stop and real-time status/announcements
remain. Essential security/approval copy stays on screen.

### Recovery and diagnostic layering

On `result`, show the result and the observed local failure category,
then an immediate **Next: ...** instruction. Wi-Fi setting/recheck buttons,
when indicated, and **Start over with fresh QR codes** remain exposed.
The warning that local chat clears only as best effort and production
messaging/voice remain disabled is still visible.

Longer technical summaries and the opt-in **Copy this phone's test summary**
button move behind **View test details and QA report**. Copy behavior
remains one explicit tap and retains the warning that the operating system
and other apps may read clipboard contents. No saved logs, peer contacts,
IP/SSID, message history, payloads or private keys are created.

## Regression and release gates

- Pure Kotlin cue tests cover each QR role, idle/result safety, approval
  stages including inconsistent flags, and active test-chat cue only after
  the real `demo` state.
- Python source assertions check stage inputs, visible recovery/actions,
  QA copy button being gated behind disclosure, no hidden networking/consent,
  and production capability flags remaining false.
- Existing Kotlin/JVM, Python protocol/security, permissions/dependencies,
  and APK package/signing tests must pass, including a fresh merged-main run.

**Still open, deliberately not requested from the owner:** real-phone
screenshots, large font, TalkBack, portrait/landscape, two-device QR/session
tests and independent protocol/security review. Automated verification is
not proof that a two-phone link is safe or works reliably.

Rollback: revert M4-UI03 PR; there is no persisted user data migration.
