# Eden Comms — Native UI / Pairing UX Reset (UX-01)
Date: 2026-11-03. Owner: Eden Practical. Status: **Design direction approved, implementation in progress.**
Input: three Android screenshots from an M1f internal test APK and direct user feedback.

## Why this reset is necessary
The current app feels like a website wrapped in an APK: oversized dark-blue panels, bright aqua/pill buttons, long explanatory paragraphs, technical milestones in primary copy and a two-phone connection test that asks people to manually type a full 256-bit certificate fingerprint and an IP address. That is overwhelming, requires external clipboard/text sharing in practice, and undermines confidence before anyone tests real communications.

**Core product principle: Connect → Verify → Talk → Disconnect.** Security stays enforced in the implementation, while technical ceremony moves out of the normal person's path.

## Visual system: quiet, native, credible
- **Direction:** native Android utility, calm graphite / slate-green / off-white. Restraint, clarity, useful space, no cyberpunk neon, fake terminal glow, comic graphics, promotional hero cards or faux military/official endorsements.
- Dark background #121613, surface #1D231F, elevated surface #272E28; primary muted sage #B9CCB9, foreground #E9EEE7, supporting text #B7C1B5, outline #515F53.
- Light alternative: background #F5F6F2, surface #FFFFFF, primary #42634A, foreground #232A25, secondary #58665C, outline #C5CEC5.
- Native Material 3 type hierarchy and meaningful whitespace. One clear primary action per screen. Rounded corners ~12–14dp, not giant pill cards; restrained borders, almost no shadows. Touch targets ≥48dp.
- Use compact status rows and short labeled sections rather than oversized stacked cards. Explanatory detail belongs in Help or Developer tools. No UI claim of "secure/verified" unless the crypto layer has established it.
- Accessible on 320dp+ screens, large font scaling, portrait/landscape, TalkBack, sufficient contrast and visible enabled/disabled state. No reliance on color alone.
- Screenshots are allowed in debug APKs, disabled production-side only after release accessibility/privacy review. Do not spend CI minutes building automated screenshot reports for every test APK.

## Normal user flow: extremely simple
### Home
- **Eden Comms** identity.
- Real connection status: "Not connected", "Connected — verified" ONLY when verified cryptographic state exists. In M1f UI, never show connected.
- Primary: **Connect nearby**.
- Compact non-marketing facts: local-first, session-only default, no account.
- Secondary: Settings, How it works. No long protocol explanations.

### Connect nearby
- A short local Wi-Fi explanation: same Wi-Fi; router Internet not required.
- Eventually two choices: **Host** and **Join**. In unfinished builds, label as in development and **disable**, rather than offering fake connect actions.
- Proposed pairing: host creates local session → displays an in-app **QR** containing authenticated public identity/session-binding material → joiner scans with in-app camera → encrypted handshake and explicit reciprocal acceptance/short verification (must be reviewed for MITM resistance) → usable session.
- No requirement to manually type a 64-hex-digit fingerprint or IP address in the normal journey. No telling people to send fingerprints via SMS, another messenger, clipboard or public link. A public fingerprint is not secret, but unauthenticated external sharing can facilitate impersonation or substitution.
- Optional re-pairing/trusted device identity is separate consent from retaining messages. Changing identity or revocation must be visibly handled.
- Until the cryptographic verifier has proven both peers and both have approved the session, **NO CHAT COMPOSER, no transmission**.

### Settings
- Appearance preference.
- **Message history:** Session-only default; optional encrypted history with per-chat max 30-day expiry to be implemented later, never claim this feature exists prematurely.
- Device identity management.
- **Developer tools** entry is where existing opt-in Android Keystore inspection and LAN TLS handshake test live.

### Developer tools (internal builds)
- Keep the existing **manual** 64-hex fingerprint + host IPv4 + TLS 1.3 handshake probe solely for diagnostic/two-phone testing until the QR enrollment flow works.
- Label it "developer test, no messaging" on every relevant page. Use synthetic test data only.
- Preserve explicit opt-in identity creation and forget, in-process cancel/close semantics, fail-closed TLS 1.3 and full pinned certificate.
- No log/notification/backup access to private keys, no new third-party network dependency or plaintext fallback.
- Do not remove a verified existing dev transport merely for aesthetics; move it out of the normal path.

## Implementation scope for UX-01
1. Replace saturated navy/neon/aqua palette with neutral Material 3 graphite/sage design; preserve light/dark setting.
2. Replace giant scrollable home and connect cards with compact layout, short copy and predictable one-action hierarchy.
3. Create correct navigation to Settings → Developer tools → LAN handshake test, with proper back flow and page-specific scroll reset.
4. Keep local Keystore identity and LAN test functionality fully intact. Do not add hidden automatic pairing, network discovery or messaging capabilities.
5. Remove outdated copy claiming no INTERNET permission: Android local TCP sockets use INTERNET, even when WAN is absent.
6. Ensure feature-disabled buttons visually and semantically remain disabled.
7. No APK distribution for purely internal changes; a UI milestone APK is useful after Android CI passes, because the tester can actually evaluate screenshots/feel.

## Acceptance and QA
- On Home, "Connect nearby" is the only dominant action; no giant explanatory paragraphs.
- No manual fingerprint prompt in Home or normal Connect pages. Technical test is located only in Settings → Developer tools.
- QR-first planned states are clearly **planned**, not falsely working. Real in-app QR/scan implementation belongs to next pairing milestone.
- Standard back navigation works for Home → Settings → Developer → LAN, and separate identity screens. Page scroll does not carry over after navigation.
- Theme looks neutral/professional in dark and light; no blue neon; text readable at enlarged Android font sizes and narrow screen widths. UI screenshot review is manual with actual device feedback, not recurring CI capture.
- Android debug app still permits screenshots; Android app permission audit still detects anything unexpected.
- Connection capability gates stay off: CAN_CONNECT=false, CAN_SEND_MESSAGES=false and CAN_TRANSMIT_VOICE=false until tested implementation.
- Run compile/unit tests and focused existing security regressions, then test visually on one phone (portrait/landscape). Two-phone handshake remains an outstanding integration task. Explicit external security review before public claims.

## Current phase and next features
UX-01 is the visual and navigation foundation; M1f is a working LAN TLS *probe*, not yet a secure usable chat. **Next** integrate QR-first identity exchange and a real two-party confirmed LAN connection, then enable gated session-only messages following on-device and MITM tests. We deliberately postpone remote P2P, relay, voice and non-LAN radios.

## Notes
- Read this with [Product Vision](PRODUCT_VISION.md), [Data Retention & Expiry](DATA_RETENTION_AND_EXPIRY.md), [Privacy Security Charter](PRIVACY_SECURITY_CHARTER.md) and [Test Build Policy](TEST_BUILD_POLICY.md) where present.
- No "military-grade", "unhackable", "anonymous", guaranteed remote deletion or "fully secure" claims.
