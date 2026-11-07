# Eden Comms — M4-UI04: Temporary conversation readability and controls

**2026-11-07 · UI-only change · physical testing deferred at owner's request.**

## Motivation

Owner's first-phone screenshots validated the Eden Comms visual style but
showed that lengthy information and technical terminology can bury the main
actions. M4-UI01 through M4-UI03 simplified initial connection and recovery.
This pass addresses the **actual debug test conversation** without changing
the transport, message protocol, bilateral consent, release flags or
encrypted-session rules.

## Implemented

- A quieter conversation header, retaining the **DEV TEST**, no-sensitive-
  information and voice-disabled statements in the default view.
- Left-aligned **Other phone** and right-aligned **You** message bubbles with
  larger body text, unambiguous direction and grouped accessibility
  semantics. The label on a local write says **Written to test link**, while
  the other label says **Received on this phone**. Both are observations of
  actual writer/receiver callbacks; neither is a delivery/read receipt or
  authenticated production chat.
- Repeated technical disclaimer removed from each outgoing bubble. One
  conspicuous line below the conversation list still states **Not a delivery
  or read receipt**, with an optional `?` explanation of what the local
  write observation actually means.
- Maintain a bounded 220dp message viewport and the existing
  12-message RAM-only transcript. Respect user scrolling to older messages:
  new arrivals auto-scroll only while following latest; a manual
  **Jump to latest** appears when the reader has scrolled away. No unread
  counters or fake remote read events.
- **Hide conversation** continues to hide both message bubbles and composer
  while retaining a clear reveal control. **Clear my screen** retains its
  existing explicit local-only confirmation and does not close the socket.
  Both actions have at least 48dp targets and stack when the screen is
  narrow or the font scale is large.
- The single existing **End chat and disconnect** control now comes
  directly after the chat panel, *before* optional preset phrases/TLS QA
  tools, avoiding an extra scroll to find the exit. It invokes the same
  existing end-by-user, QR/identity release, transcript finish and draft
  clear code. A nearby line warns that the remote person may keep a copy.
- Composer remains limited to short validated test text (280 Unicode code
  points) and enabled only when the caller's existing debug send policy
  allows it. No new retention, app notifications or implicit network
  operations.

## Verification

- Pure Kotlin tests ensure message labels and counters refer only to
  local observed actions, not delivered/read/verified states.
- New Python UI/source guards plus updated legacy M3f/M3g contracts verify
  hide/privacy boundaries, real transcript inputs, local-only copy semantics,
  font-scaled controls, scroll follow and reachable End action.
- Existing Android build, JVM/security regressions, manifest permission
  inventory, signing/identity evidence and APK artifact publication must
  pass in PR and fresh merged-main CI.

**Unverified:** actual phone display in dark/light, large fonts, TalkBack
message grouping and scroll behavior, keyboard resizing, rotation, physical
two-phone test and professional protocol review. User will explicitly say
when they're ready to test again; no device test requested here.

## Security boundaries

A successful developer TLS session is not independently certified secure
messaging. A local write is not evidence of delivery or reading by the peer.
Clearing the on-screen list does not guarantee erasure from Android memory,
keyboard histories, screenshots or the other person's device. Production
connect, send and voice capabilities remain disabled.

Rollback: revert PR. No saved-message or user-data migration.
