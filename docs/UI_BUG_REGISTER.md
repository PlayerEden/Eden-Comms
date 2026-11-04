# Eden Comms — UI regression register

Source: Real Android screenshots and feedback, November 4, 2026. These issues are independent of cryptographic security claims; all actual messaging capabilities remain disabled.

| ID | Problem observed | Root cause | Code status | Device QA |
|---|---|---|---|---|
| UX-003 | Text at left edge of Home/Settings navigation rows is visibly shaved or clipped (e.g., `Device identity`, `Developer tools`) | Full-width `TextButton` with zero content padding and an inner full-width `Row`; internal text/button layout clips glyph edges on the tested phone | Replaced with a native clickable `Row` and explicit 10dp horizontal padding | **Needs real-device visual retest**, normal/large fonts, dark/light |
| UX-004 | Light-theme status-bar icons (time, Wi-Fi, battery) are near white on a pale background, difficult to read | The theme statically requested light (white) status-bar icons regardless of Compose light/dark setting and modern edge-to-edge draws over app background | `WindowCompat` now switches status-bar and navigation-bar icon contrast on appearance changes | **Needs real-device retest** on Android 15/16, both themes |
| UX-005 | Android hardware/gesture Back exits the entire app from Settings/Connect/Developer screens instead of returning to prior screen | Compose page state was not connected to Android's Back dispatcher; only a custom in-app arrow changed the page | Replaced single page with a saveable route history and `BackHandler`, same pop callback used by in-app arrow. JVM route-history tests added | **Needs real-device retest**, hardware/gesture Back |
| UX-006 | “Message history” wraps awkwardly in Settings due to an undersized left column | Reused two-column compact fact row for a settings item | Converted to a vertical settings label and value | Needs phone narrow-width/font-scaling QA |

## QA walk-through for the next tester APK
1. Open Home → Settings; press Android system Back; confirm **Home appears without exiting**. Repeat with the on-screen back arrow.
2. Home → Settings → Developer tools → LAN handshake test. System Back must unwind **LAN → Developer → Settings → Home**. From Home, Android Back may exit normally.
3. Home → Settings → Developer tools → Device identity; system Back must go back to **Developer tools**, not Settings.
4. In both dark/light themes, inspect the first glyph of Settings, Device identity, Developer tools, their subtitles, and Home rows; there should be no truncation at the left border.
5. Switch dark/light modes and examine status and navigation icons; both must remain legible against their background. Repeat after app rotation and resume.
6. Check narrow displays and Android large-font settings, TalkBack, and whether message-history label/value are readable without awkward squeezing.
7. Reconfirm pre-network security restrictions, Android Keystore state, LAN test availability under Developer tools, and debug screenshot allowance.

**Scope:** Layout and navigation repair; no crypto, networking, manifest permissions, transmission flags, retention semantics or key handling changes. Do not distribute an extra APK solely to showcase this minor UI maintenance; include it with the next meaningful development test build.
