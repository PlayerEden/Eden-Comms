# M5-E — fresh QR restart and asynchronous callback admission

**November 7, 2026 • scoped debug-only restart hardening**

## Source-based findings

The pairing screen already resets most state on the visible "Start over"
button and uses generation checks for transport callbacks. This audit found
three additional source paths needing explicit cleanup/admission:

1. If runProbe sees a missing local identity, invite, reply or peer pin,
   it previously displayed "result" without invoking stopTest, removing QR
   references, ending the typed-text ledger or recording CONNECTION_FAILED.
   A partially scanned invite/reply could therefore remain in UI-held RAM
   until a later explicit reset.
2. QR-camera callbacks were guarded only by scanning stage. A callback
   queued by a cancelled scanner could arrive while a newly started attempt
   happened to be on the same scan stage.
3. Asynchronous **consented** local identity creation/legacy-upgrade could
   finish after the app was backgrounded (stage="choose" was excluded from
   active link shutdown). Its continuation might then automatically open a
   QR role without a new foreground tap.

These are implementation paths, not physically observed bugs.

## Changes

- Fail the incomplete runProbe preflight closed: invalidate generation,
  close any probe, release invite/reply/pin/QR references, finish temporary
  text/phrase state and record a generic local connection failure. Keep
  the user-facing recovery action requiring fresh QR codes.
- Invalidate the QR camera revision on reset. Check both current attempt
  and scanner revision for **both** camera payload and error callbacks.
- Guard both consented local Keystore operations after their background
  work finishes: only continue an unchanged requested show/scan role when
  the original generation is active, the screen still shows "choose", and
  the Android Activity is RESUMED. Backgrounding at "choose" invalidates
  the pending role/generation. Local key creation already explicitly
  consented to may still finish; it never authorizes a stale QR continuation.
- Close identity/upgrade prompts on explicit reset. No automatic reconnect.

## Test and release gates

- [ ] Kotlin JVM matrix for role/generation/foreground and scan revisions.
- [ ] Python source checks against real Compose preflight/scan/consent paths.
- [ ] PR full Android debug build/JVM, privacy/security, permissions, APK
      signer and dependency checks.
- [ ] Fresh merged-main Android CI.
- [ ] Owner-deferred physical two-phone QR/camera, Android backgrounding,
      real TLS/consent, visual accessibility and test-install continuity.
- [ ] M4-UI07 **manual** emulator visual QA and independent security review
      remain outstanding.

No production message sending, voice, permission, TLS/QR protocol, stored
conversation history, signer or identity cryptography is enabled/changed.
This is safe-state UI admission, not independent peer authorization.
Rollback by reverting the PR; no migration is necessary.
