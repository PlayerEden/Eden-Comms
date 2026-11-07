# M4-UI01 — Progressive help and less intimidating first-run screens

**November 7, 2026 • Source UI refinement based on owner's one-phone screenshots.**

## Problem seen on the actual phone

The angular olive/dark style and basic navigation already look cohesive. But
the Connect Nearby entry screen displayed the APK version/source commit and
signer before two primary user actions; the Settings page expanded several
unavailable future settings; Privacy & Data was a wall of dense paragraphs;
Home and connection methods had extra technical exposition. That distracts
from the intended **Connect → Communicate → Disconnect** flow.

## Changes

- Reusable Compose `FieldHelp` (`? + question`) and
  `FieldDisclosureCard` (`summary + expandable full detail`).
  Each explanation uses an **at least 48 dp tappable row**, works with
  touch/TalkBack without hover or long press, exposes Expanded/Collapsed
  semantics, and has no side effects on radios, permissions or identities.
- **Home:** preserve brand/connection status and primary Connect button,
  put development-method explanations behind `? What can this preview do?`.
- **Connect Nearby (LAN QR):** keep Show a code / Scan a code and immediate
  status clear; hide source commit/signer and Copy installed build details
  behind `? Advanced: build details`; retain the help dialog under
  `? Why scan two codes?`. Existing build evidence and copy behavior stay.
- **Connection methods:** show same-Wi-Fi action and safety disclaimer by
  default; collapse long future Bluetooth/Wi-Fi Direct roadmaps and rules.
- **Settings:** keep the active session-only history status and its existing
  Learn more modal; collapse three unavailable future privacy features into
  one labeled group.
- **Privacy & Data:** always display the critical unreviewed-security warning,
  actual short test-text transmission, no voice and no sensitive use.
  The full existing legal/trust/permissions paragraphs are still available
  under six visible, labeled expandable cards; the absence of a published
  policy remains visible. Expandable content is informational, not an
  approval or permission gate.
- **Accuracy:** clarify that *debug* Wi-Fi Direct diagnostics may require
  location permission on pre-Android-13 phones. No location coordinates are
  read; release permission inventory and capabilities are unchanged.

## QA/acceptance

Automated guard checks expandability, accessibility sizing, preserved build
evidence copying, existing pairing/navigation, visible privacy warning,
permissions and zero production capability changes. Full Android compile,
unit/security tests and APK audit required before PR merge.

**Still needs owner's on-device visual QA**: portrait/landscape, small display
width, large system font, TalkBack/screen reader, scroll after opening one
or several cards, rotating mid-expansion, pressing Back, opening two-code
help and expanding/copying advanced build evidence. Screenshots should
contain no live QR secrets, device addresses or personal chat text.

This PR is **UI only**. It does not activate production secure messaging,
Wi-Fi Direct/Bluetooth app transport or voice. Existing two-phone transport
acceptance and independent security review are still outstanding.

Rollback: revert UI PR; no user-data migration.
