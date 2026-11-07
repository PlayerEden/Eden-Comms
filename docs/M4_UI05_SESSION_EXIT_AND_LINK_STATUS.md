# Eden Comms — M4-UI05: Session-aware navigation and local link status

**2026-11-07 · UI-only safety/clarity pass · owner hardware testing remains deferred.**

## Why

The prior M4-UI04 app provided a clear **End chat and disconnect** action, but
Android Back and the app-bar Back arrow could leave a QR pairing/approval/active
test screen without explaining that navigation disposes and closes the local
session. This is an accidental-disconnect and usability hazard, not permission
to retain or silently resume a link.

## Implementation

- Route the visible Back arrow and system Back gesture through the **same**
  in-progress-stage check. For QR exchange, link checking, approval/final
  confirmation or active test chat, show **Leave this connection test?**,
  **Stay here**, and **Leave and disconnect**. No nag dialog on the idle,
  initial-check, identity-error or ended result screen.
- Explain that leaving drops the session on this phone, invalidates pending
  QR/approval flow, and requires both people to pair again using fresh codes.
  In chat, mention best-effort local text cleanup and that the other phone
  may retain copies.
- Continue using the **existing** disposal path for generation fencing,
  socket closure, temporary QR release and RAM-only text/draft cleanup.
  No new automatic background connection, retry or data persistence.
- A compact **LOCAL STATUS** line derived only from actual stages:
  NOT CONNECTED, EXCHANGING QR CODES, CHECKING TEST LINK,
  APPROVAL AND CONFIRMATION PENDING, TEMPORARY TEST CHAT ACTIVE or
  TEST LINK ENDED. No inferred delivery/read status or peer authentication
  beyond the live test state.
- The app bar names the confirmed debug demo **TEST CONVERSATION**.
  The terminal result retains **Start over with fresh QR codes** and adds
  a clear **Done — return to previous screen** action.
- Parent navigation holds only a non-sensitive stage name; no message,
  address, certificate, QR, peer ID or transcript is lifted into app state.

## Acceptance / rollback

- Pure Kotlin route policy unit tests and Python source contract guards added.
- Existing CI should confirm Android/JVM build, security boundary, manifests,
  runtime permission inventory, signer/identity and test APK artifact.
- **Not yet performed:** single-device Back/gesture/dialog/TalkBack/large
  font/rotation screenshot checks, two-phone disconnection behavior or
  independent security review. Physical testing is deferred by the owner.
- Debug-only test behavior, same-Wi-Fi TLS/QR and two-person approvals
  unchanged. Production connect/text/voice flags stay disabled.
- Roll back this PR to restore previous UI; no persistent data migration.

## Follow-up

After M4-UI05 CI, prioritize a broader full-app usability/accessibility and
security-boundary audit, then run physical screenshots and two-phone flows only
when the owner explicitly opts in.
