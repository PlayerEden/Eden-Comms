# Eden Comms — UX-04: Screen composition and density

Date: November 4, 2026. Status: code implemented; automated and physical QA status recorded in PR #9.

## Goal
**Core screens should generally fit a normal portrait phone without scrolling**, without reducing touch targets or making text unreadable. Keep the visual identity from the grounded field-utility reference (olive, graphite, small cut corners, structured surfaces). This is a density/content hierarchy pass, **not** another theme overhaul.

## Implemented
- The global page body now adapts page padding/gaps for shorter screen heights (threshold 650dp) and retains *scroll as a fallback* in small displays, landscape, large fonts and accessibility modes. Never force text offscreen.
- **Home:** preserved connection status and primary Connect nearby button. Replaced two tall full-width communication cards with compact two-column Text / Walkie-talkie tiles; shortened the footer copy. Settings and Help remain discoverable.
- **Connect nearby:** one clear title, three concise connection rows and compact Scan → Verify → Talk summary. Bluetooth/Wi-Fi Direct are still future, non-clickable states.
- **Local Wi-Fi:** removed the oversized centered icon/description panel; introduced a compact network-status row, disabled Host/Join, and shorter pairing steps. No simulated pairing.
- **Walkie-talkie:** removed redundant instructional list and duplicate long description. Placed the disabled Hold to talk control prominently, with a concise Not connected state, status and release behavior note.
- **Light appearance:** synchronize system status/navigation bar backgrounds with light/dark theme and disable the Android 3-button automatic navigation-bar contrast scrim where supported. The prior code changed icon colors but left the window navigation bar dark gray in light mode. OS edge-to-edge policies vary; verify on device.
- Updated development `versionName` to `0.1.4-ui-density` only; no package ID, signature, permission, Keystore or LAN protocol changes.

## Acceptance checks (physical Android)
1. Home and Connect fit without scrolling at normal font size on a typical phone; no clipped controls. Tablet landscape/narrow width and enlarged fonts may scroll rather than truncate content.
2. Local Wi-Fi Host/Join are disabled and clearly labeled as upcoming; not represented as functional.
3. Walkie-talkie immediately exposes a large disabled **Hold to talk** control and an honest Not connected state. No microphone permission or real voice.
4. Home → Walkie-talkie → system Back → Home; Home → Connect → Wi-Fi → Back → Connect. Root Back exits normally.
5. Text, status labels and tiles fit at narrow widths/large system font without clipped left glyphs, overlapping captions or cramped buttons.
6. Light-mode Android status + 3-button navigation bar backgrounds and icon contrast remain readable; dark mode unaffected. Confirm on Android 16 phone and, where possible, another device/OS.
7. Existing Keystore/handshake test remains accessible at Settings → Developer tools and unaffected. No plaintext downgrade or extra network path.
8. No automated screenshot capture pipeline or full release checklist for this internal visual pass.

## Product boundaries
- `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` stay false.
- Internal LAN TLS probe remains available, **not** a working messenger.
- Session-only remains default. Optional encrypted history max 30 days is still planned, not live.
- Future: QR-first local pairing, verified offline LAN text, then PTT. Remote/relay is deferred.
