# M4-UI02 — Guided Developer Tools and truthful pairing explanations

**November 7, 2026 · One-phone visual and wording refinement.**

The prior owner's Android screenshots showed attractive dark/olive native
styling, but technical labels and explanations could feel intimidating.
M4-UI01 made detailed disclosures optional. This second pass concentrates
on the path from **Settings → Developer Tools** and the first steps of
**Connect Nearby**.

## Changes

- Developer Tools becomes **Testing and setup**, beginning with actions a
  person can inspect without another phone: local device identity and the
  fictional text layout sandbox. A one-tap `?` explains the difference
  between this phone's key and an authenticated *other* person.
- Connection diagnostics sit under an **Advanced connection checks** heading.
  The separate manual same-Wi-Fi handshake diagnostic remains directly
  available. Bluetooth and Wi-Fi Direct debug tools live behind an
  explicitly labeled optional radio experiments toggle, and are only
  rendered when the APK is debuggable. All original non-exported debug
  activities, permissions, platform checks and bounded radio stops remain
  unchanged.
- A user can see what each tool does **before** entering it. Opening Developer
  Tools does not start a scan, pair a device, issue network activity, or
  create identity keys.
- "How it works" now has three plain-language steps, supported by expandable
  answers for internet access, why both people approve and why local
  temporary test text is not a guarantee of permanent erasure.
- Connect Nearby uses short "one phone shows, the other scans" copy, and
  a separate accessible answer to **Which button should I choose?**
  The existing reciprocal two-QR explanation dialog stays available.
- The technical APK version/source/signer panel now comes **after** the
  primary pairing card instead of before it; it remains opt-in and the
  copy-to-clipboard action remains functional.
- **Safety-critical copy correction:** the approval screen formerly claimed
  it allowed only four fixed phrases and never typed text. That was
  inconsistent with actual debug short typed two-way test text. The corrected
  warning explicitly says that *after both phones approve and confirm*,
  short typed TEST messages and preset phrases can be exchanged. It keeps
  the unreviewed-security/no-secrets/no-voice warning permanently visible
  and offers optional `?` technical explanation of QR, TLS and identity.

This is a wording, layout and navigation pass. No new permissions,
dependencies, message schemas, transport APIs, radio access, persistence,
keys, signing behavior or release capability flags are authorized.

## QA requirements

- Python source regression: existing navigation, conditional radio exposure,
  nontechnical help, preserved build copy, unhidden safety warnings, real
  debug text claim, no hidden approvals or auto-connection.
- Existing Kotlin/JVM and Python security regressions, signed APK packaging,
  dependency/permission inventory, and post-merge verification.
- **Owner physical visual follow-up still pending:** Settings → Developer Tools
  with radio section collapsed then open, Home → How It Works, Connect
  Nearby with Show/Scan visible, optional build details below the panel,
  large system font, screen rotation, TalkBack, and Back navigation.
- Real two-phone cryptographic and radio QA, and independent security review,
  still pending. Do not use debug text for sensitive communication.

Rollback: revert PR. No device data migration.
