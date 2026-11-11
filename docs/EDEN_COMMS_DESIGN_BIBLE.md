# Eden Comms — Product & Design Bible

**Version:** 0.1 · **Consolidated:** November 10, 2026 · **Status:** working
reference for owner review, not an independently approved product/release.

**One-sentence purpose:** A practical, no-account, local-first Android
utility for two nearby people to **Connect → Verify → Text or Talk →
Disconnect**, without relying on an Eden Practical-operated server.

This document is the **single front door to current product, interaction,
visual, and scope decisions**, not a replacement for a security audit or a
claim that planned functions work on physical devices. It consolidates
[Product Vision](PRODUCT_VISION.md), [Privacy & Security Charter](PRIVACY_SECURITY_CHARTER.md),
[Decisions](DECISIONS.md), the [UX design baseline](UX07_VISUAL_BASELINE.md),
the [visual asset kit](VISUAL_ASSET_KIT_V1.md), and the
[core roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md).

## 1. Identity and governing principles

- **Product:** Eden Comms (EC); umbrella brand: **Eden Practical**.
- **Promise:** "Software that does what you need. Nothing more." Practical,
  dependable and understandable are more important than novelty or flair.
- **Audience:** Individuals who want straightforward nearby 1:1 communication
  without making an account; normal people should not need to know IPs,
  fingerprints, radio protocols or Android debugging concepts.
- **Priorities:** Privacy and safety → clear/usable core flow → reliability
  and accessibility → restrained polish. Do not trade security for convenience.
- **Local first:** no mandatory Internet access for peer messages, Eden
  server, cloud account or discovery service; no analytics, advertising,
  contact harvesting, invasive tracking or persistent background scan.
- **Honest language:** a QR code, radio connection, TLS handshake, socket
  write, or remembered peer is not by itself a verified human or received
  message; never use false "Connected", "Delivered" or "Secure" labels.

### Precedence and change management

1. **Security/privacy normative MUSTs:** [Privacy & Security Charter](PRIVACY_SECURITY_CHARTER.md)
   and [retention policy](DATA_RETENTION_AND_EXPIRY.md). A design bible
   cannot lower their bar.
2. **Product and UX interpretation:** *this* current bible, with
   [Decisions Log](DECISIONS.md) for explicit open/approved trade-offs.
   New user decisions supersede this draft only after being recorded.
3. **What is actually implemented:** `main` source, current tests, CI
   and [current handoff](CURRENT_HANDOFF.md), never a design mockup.
4. **Quality/acceptance evidence:** [two-phone test matrix](TWO_PHONE_ACCEPTANCE_MATRIX.md),
   real screenshots and independent review reports. Source presence ≠ pass.
5. **Historical milestone notes:** important traceability, but older
   "coming soon", colors and milestone statuses do **not** override
   the current code or this consolidated reference.

If a document or UI disagrees, add a dated conflict to the
[quality audit](OVERALL_QUALITY_AUDIT_2026_11_10.md); avoid silently
rewriting accepted privacy requirements to make it pass.

## 2. Product scope — what is and is not Eden Comms

**Core intended product:**
- Android-first one-to-one nearby messaging and short push-to-talk.
- Choice of **Same Wi-Fi**, **Bluetooth Classic**, and **Wi-Fi Direct**.
  Wi-Fi may work through a local router **without WAN**; Wi-Fi Direct
  is router-free; Bluetooth uses nearby RFCOMM. Device support varies.
- Explicit in-person pairing, authenticated peer/session binding,
  **both people's separate approvals**, readable connection stages,
  safe failure and easy Disconnect.
- Text send/receive, honest peer-*app* acknowledgment, foreground
  voice, and default ephemeral conversation state.
- Separate optional local-encrypted text history and separately
  expiring saved *public* peer identity; straightforward Forget/Delete.

**Not in first release:** remote Internet relay, sign-in/accounts,
social feed, global messaging, cloud backup, live location, analytics,
ads, mesh network, complicated command dashboard, public friends list,
groups, attachment transfer and silent auto-reconnect. These are
separate future design decisions, not hidden unfinished requirements.

**Critical boundary:** The debug build contains experimental
functional code. Its `ReleaseCapabilities.CAN_CONNECT`,
`CAN_SEND_MESSAGES`, and `CAN_TRANSMIT_VOICE` are **all FALSE** in
production. Neither this document nor a green CI run authorizes
turning them on.

## 3. Primary user journey (ideal screen contract)

1. **Home — "Not connected":** Eden Comms identity, a single prominent
   **CONNECT NEARBY** action, brief explanation of local communication,
   Text and Voice/PTT entries that take the user toward connection,
   Settings and How it works. No fake active session or huge technical wall.
2. **Choose connection:** Clearly distinguish Same Wi-Fi / Bluetooth /
   Wi-Fi Direct in normal language and show readiness/permission issues.
   No scan until the user explicitly chooses a method.
3. **Pair & verify:** Explain who shows/scans the fresh QR exchange and
   what the next person must do. UI must visibly distinguish **discovered /
   linking / identity not yet verified / awaiting both approvals / verified**.
   A mismatched, expired or canceled attempt ends without a chat.
4. **Conversation:** A consistent Text / Talk experience after a real
   mutually authenticated session is confirmed. An honest local queue
   status, real socket write status, separate peer-app ACK, and
   receiving events. ACK does **not** mean human read.
5. **Talk:** Big, obvious hold-to-talk (plus accessible tap alternative).
   Never enable mic before actual floor grant/permission/foreground.
   Clear transmit/receive state, bounded duration/buffering and
   obvious release/cancel. Do not imply audio was heard merely because
   a frame was written.
6. **Disconnect/recover:** One visible exit; stop mic, speaker, socket,
   temporary identity proofs and pending sends. Fresh user intent,
   QR, authentication and both approvals required to start again.
   Report actual permission/radio/OS cleanup failures, not a fake retry.
7. **Settings/Privacy/Help:** Concise human-first controls; developer
   diagnostics under a distinct advanced section, never in the normal
   connection path.

**Accessibility:** Android Back, responsive portrait/landscape,
large text and TalkBack semantics; minimum 48dp action targets; no
color-only state. Scroll affordances appear if content extends below
the viewport. Never sacrifice content availability merely to avoid
scrolling. The user's November 10 screenshots revealed Home's missing
scroll cue; source fix is in PR #165, but the updated device appearance
must still be checked.

## 4. Visual identity — current implementation, not obsolete concepts

Use the **current approved-looking field-utility direction**: clean native
Jetpack Compose UI, graphite canvas, restrained sage/olive accents,
off-white copy, angular cards/buttons, sparse line-art vector icons and
a modest masthead. Avoid neon, fake official emblems, game-like HUDs,
oversized marketing panels, forced military terminology or long on-screen
technical explanations.

| Current source token | Dark reference | Role |
|---|---|---|
| `Graphite` | `#0D1112` | Main background |
| `GraphiteSurface` | `#161B1D` | Cards |
| `GraphiteRaised` | `#1F2628` | Raised cards |
| `SoftIvory` | `#E6E8E6` | Main text |
| `PaleSage` | `#8FAA6A` | Important action/accents |
| `LightSurface` | `#FFFFFF` | Light-mode surface |
| `Ink` | `#232A25` | Light-mode ink |

These come from `MainActivity.kt` and current UI material.
Older proposal palettes in `PRODUCT_VISION.md` and
`UI_UX_RESET_BRIEF.md` are *historical explorations*.
The [visual asset kit](VISUAL_ASSET_KIT_V1.md) remains the icon/vector
reference. It is **not** a claim that every page has been visually accepted.
Do not add rasterized screen mockups or a WebView just to imitate a concept.

**Copy rules:** "Connect nearby", "Not connected", "Text",
"Voice / PTT", "Settings", "How it works", "Disconnect". Use familiar
words first, with optional expandable help. Progress copy only reflects
actual state; the Preview must say it is experimental/not suitable for
sensitive or emergency conversations.

## 5. Connection and security invariants

All three transport implementations must retain the *same* security goal:
a real peer session using pinned mutual TLS 1.3 and in-person QR identity/
transcript proof, followed by separate approvals on both devices and final
confirmation. **Do not treat QR alone, Bluetooth bonding, P2P group owner,
Wi-Fi network membership or remembered public fingerprint as authorization.**

No plaintext fallback, auto-approval, automatic reconnection, background
microphone, silent permission request, content logging, remote history
sync or indefinite retained peer trust. Unexpected link change, background,
timeout, denial or permission loss must fail closed. Local metadata
retention must not silently make a new session trusted.

**Not certified:** This specific experimental TLS/control construction has
**not** received the mandatory independent cryptographic/protocol review.
Source/regression checks and an actual connected device test cannot
substitute for it. Do not promote to production or advertise audited E2EE.

## 6. Messages, storage and deletion semantics

- **Session-only is the default** on every new conversation. Text only
  intentionally persists if the person separately opts into
  encrypted local history for that verified conversation.
- Planned/current debug history options: **1 hour, 24 hours, 7 days,
  14 days or 30 days** maximum; no unlimited retain, no silent renewal.
- Separate remembered *public peer* fingerprint option with independent
  1–30-day expiry; it is informational only after a **newly** verified session.
- The 1/5/15-minute disappearing option is best-effort local UI suppression,
  not forced deletion from the other device or a cryptographic erase proof.
- Encrypted local files and Android Keystore/backup exclusions must undergo
  actual device migration, expiration, delete, reinstall and OS-version QA.
- Privacy copies must not claim that screenshots, third-party keyboards,
  recipient photographs, compromised phones or flash remnants vanish.
- **Delivery vocabulary:** queued locally → written on local encrypted link
  → **peer app ACK** → (no implemented human read receipt).

For implementation details and constraints, use
[retention](DATA_RETENTION_AND_EXPIRY.md),
[peer-memory](TRUST_RETENTION_AND_HELP_PLAN.md) and
[privacy charter](PRIVACY_SECURITY_CHARTER.md), not dated shortcuts.

## 7. Actual development/readiness status — November 10, 2026

| Area | Integrated in DEBUG source | Automated evidence | Physical-device acceptance |
|---|---|---|---|
| Native app/Home/Connect/Settings/Help | Yes | Android/JVM/Python main CI passing | Owner photographed Home; rest pending |
| Same-Wi-Fi QR/TLS/text/peer-app ACK | Yes | Source/JVM checks passing | Two-phone consolidated test NOT RUN |
| Bluetooth verified text/ACK | Yes | Source/JVM checks passing | Real two-phone matrix NOT RUN |
| Wi-Fi Direct verified text/ACK | Yes | Source/JVM checks passing | Real two-phone matrix NOT RUN |
| Nearby foreground voice/PTT | Debug code across three methods | Source/JVM checks passing | Two-way audible test NOT RUN |
| Optional local history/peer memory | Debug implementation | Source/JVM checks passing | OEM/backup/expiry/delete QA NOT RUN |
| Signed owner Preview APK | Yes: latest artifact 200013 | Separate build job **SUCCESS** | Owner has installed earlier Preview; 200013 visual retest pending |
| Automated emulator screenshots | Driver exists | **FAIL**: wrong package bound at launch | No successful screenshot suite |
| Security/privacy production approval | Release flags FALSE | Static audit checks exist | Independent review NOT RUN |

**Last checked main:** `359443f3a29ef7cce5442b54e94124dd0eaea72a`.
Check [GitHub Actions](https://github.com/PlayerEden/Eden-Comms/actions)
for any new merged revision before relying on these entries.
An audit PR is correcting the emulator package binding; its success must
be observed separately.

## 8. What "done" actually means

**Level 1 — coded:** real app code reachable from real UI.
**Level 2 — CI evidence:** Android source builds; targeted JVM/Python
and security-boundary tests pass for exact SHA.
**Level 3 — visual acceptance:** actual APK opens in emulator AND owner
reviews primary screens on a phone (portrait/landscape, light/dark,
200% font, keyboard, back, accessibility).
**Level 4 — functional acceptance:** one consolidated **18-case ×
three-transport** two-phone session demonstrates fresh QR, independent
approvals, real text/ACK and audible PTT, negative paths, foreground
and privacy/OS behavior; record PASS/FAIL/BLOCKED per case.
**Level 5 — release assurance:** independent protocol/crypto,
storage/backup/deletion, accessibility and platform/legal review,
release signing, correct store declarations and a deliberately
authorized production capability transition.

**Only levels 1–2 are broadly evidenced today.** The Home first-impression
test gives partial level 3. Do not hide outstanding levels under a
single green "complete" badge. After first physical QA, fix **observed
failures** before speculative edge-case polish.

## 9. Future feature decision gate

Before adding a feature, answer: (a) Does it serve simple nearby
communication? (b) Is it essential to Connect/Verify/Text/Talk/Disconnect?
(c) Can the person understand it without training? (d) Does it
preserve the charter and local-first/no-account approach? (e) What
observable acceptance proves it? If not, defer it.

Next priority by actual evidence: make real emulator UI captures work,
inspect them beside owner's screenshots, then conduct the consolidated
two-phone check when available. Do not turn the next work session into
another chain of technical micro-milestones.
