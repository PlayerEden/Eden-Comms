# Eden Comms — Reference-led visual asset kit (UX-05)

Date: 2026-11-04. Status: source delivered on draft PR; Android build and physical visual QA recorded separately.

## Design target
Reproduce the **spirit of the original Eden Comms concept board** (graphite, olive, structured panels, radio/shield motif, clear hierarchy) in a real native Android app without copying unrealistic mockup text sizes, poster lighting or over-decoration.

Use a hybrid architecture: native Compose UI for layout, semantics, buttons, scroll behavior and state; Android VectorDrawable for reusable art/icon assets. No rasterized screen mockups, webview, heavy bitmap headers, added runtime image libraries or third-party tracking.

## Finalized dark reference tokens
| Name | Hex | Purpose |
| --- | --- | --- |
| Background | `#0D1112` | App canvas |
| Surface | `#161B1D` | Content cards |
| Surface raised | `#1F2628` | Secondary surfaces |
| Accent | `#8FAA6A` | Actions and headers |
| Accent alternate | `#6C8A5B` | Sparse supporting lines |
| Foreground | `#E6E8E6` | Main text |
| Secondary text | `#9AA3A3` | Readable supporting copy |

Native light mode remains available and should not reproduce the dark-background decorations literally.

## Deliverables / exact source
- `app/src/main/java/com/edenpractical/comms/ui/FieldVisuals.kt`: `FieldPalette`, reusable native `FieldHeaderRule` and `FieldFrame`; geometry and accent consistent across screens.
- `app/src/main/res/drawable/ic_eden_comms.xml`, `ic_eden_comms_foreground.xml`, `ic_eden_comms_monochrome.xml`, `ic_eden_comms_background.xml`: coherent angular shield + signal arcs + mast + mountain logo for in-app and adaptive launcher use. No new app ID.
- `ic_eden_radio_tower.xml`: connection / radio identity.
- `ic_eden_settings.xml`, `ic_eden_info.xml`: matching line-art navigation icons.
- `ic_eden_pairing_phones.xml`: scalable, light-on-dark phone-to-phone Wi-Fi illustration. **Conceptual, not evidence of connectivity or encryption.**
- Existing `ic_eden_wifi`, `ic_eden_bluetooth`, `ic_eden_chat`, `ic_eden_ptt` stay in app as native vector icons.

## Applied screens
- **Home**: compact branded masthead, muted olive keyline, clear disconnected status, one strong Connect CTA, compact Text and Voice / PTT tiles, small icon navigation. Short descriptions; aim for no required scrolling on normal phones.
- **Connect Nearby**: radio graphic, Wi-Fi/Bluetooth/Wi-Fi Direct choices, clear future availability, compact Scan → Verify → Talk. Only Local Wi-Fi opens its preview, no fake connection.
- **Local Wi-Fi**: scalable phone-to-phone graphic with disabled Host/Join, explicit under-development state and an easy three-step pairing guide.
- **Voice / PTT**: renamed completely from Walkie-talkie; hero control labeled **HOLD TO TRANSMIT**, disabled until secure audio is real. Small explanatory text and truthful disconnected state.

## Constraints and QA
- Accessibility is more important than image fidelity. Maintain ≥48dp taps, scalable native text, proper Android Back, high contrast and no clipped glyphs at large font size.
- Prefer vector art with no baked-in prose; do not rasterize headings, buttons or labels.
- **No trust claim from green UI:** only authenticated security state can mark a connection verified.
- Existing TLS/Keystore LAN developer tools remain intact; `ReleaseCapabilities` flags unchanged; no microphone, camera, Bluetooth or new external network permissions.
- Use no sensitive content in test screens. Debug screenshots permitted.
- Visual QA on Home/Connect/Local Wi-Fi/PTT/Settings in light/dark, with app icon masked and Android themed.
- Check screens for visual balance, landscape/short portrait scrolling, 200% font scale, no clickable fake features, no mistaken button states and nav bar contrast.
- No whole-screen screenshots as bitmap assets; no recurring screenshot automation yet.

## Still outstanding
This design kit does **not** implement QR scanning, a fully verified secure LAN session, encrypted chat, audio capture/PTT transmission, Bluetooth, Wi-Fi Direct or temporary persistent history. Continue functional milestones only after the visual baseline is approved.
