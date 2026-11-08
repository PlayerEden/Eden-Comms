# Eden Comms M5-D — terminal UI event admission and one-time result

**2026-11-07 • debug-only pairing screen lifecycle safeguard**

## Source finding

The real Android pairing screen already used attempt+stage checks for
READY, progress, presets, and typed-text callbacks. However its ENDED and
FAILED handler branches checked only the attempt number, so a delayed or
duplicate terminal callback from that same attempt could rewrite a result
screen or its local QA finish classification. Production transport has its
own atomic single-terminal guard; this change adds an independent defense
at the UI event boundary, not an assertion that duplicate terminal emission
was observed on a handset.

## Change

- Add a pure DebugSessionEventAdmission.canAcceptTerminal policy requiring
  current attempt, an active connection stage, and a compatible WAITING or
  ACTIVE ephemeral transcript. Reject result, choose, identity, scan or
  already-finished stages, and callbacks for prior attempt generations.
- Apply that policy to BOTH ENDED and FAILED in the actual Compose handler.
- On the first accepted terminal event, invoke existing stopTest() to
  increment the generation, close and clear the probe reference, finish
  temporary text and clear drafts before writing terminal QA status.
- Keep existing QR cleanup, result copy and explicit retry path.
- Add JVM state/stage/generation tests and Python source tests tied to the
  real screen branches. No UI copy changes.

## Acceptance and limits

- [ ] PR Android/JVM/Python and privacy/security/signer CI passed.
- [ ] Fresh main commit CI passed.
- [ ] Manual M4-UI07 emulator screenshot workflow remains pending.
- [ ] Owner-deferred physical two-phone connection, background/teardown,
  interrupted messages and TalkBack remain unverified.
- [ ] Independent cryptographic/protocol review remains outstanding.

The guard protects against stale queued events. It does not claim live
transport emits duplicates, prove Android callback ordering, grant any send
authority, create delivery receipts or recover dropped network messages.
All debug session security restrictions and production feature gates remain
disabled. Rollback by reverting the PR; no persisted data migration.
