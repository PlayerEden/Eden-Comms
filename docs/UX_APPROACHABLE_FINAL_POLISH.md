# Eden Comms — UX-06: approachable field-utility polish

Date: 2026-11-04. Applies to the reference-led visual branch, following tester screenshots of UX-05.

## Decision

**Lock the graphite/olive identity and angular design system**. Stop chasing decorative fidelity to the poster mockup. Refine the experience for nontechnical people: opening the app and choosing nearby Wi-Fi must feel self-explanatory. Security remains uncompromised but is enforced underneath the everyday UI.

## Changes implemented

- **Home:** preserved masthead and icon art, status, Connect button and two mode tiles; changed jargon/abstract language to “NEARBY / Connect and talk”, and plain-language text. Clear but small “development preview” disclosure. The Text tile still opens Connect and says “Coming soon”; the Voice/PTT tile opens a nonfunctional preview.
- **Connect Nearby:** prominently recommends the only near-term connection method, Local Wi-Fi; Bluetooth and Wi-Fi Direct moved under “Other options / Later” with no click actions. Simple “Scan → Confirm → Talk” summary instead of suggesting knowledge of certificates or TLS. Avoid showing technical developer-testing instructions in normal UX.
- **Local Wi-Fi:** retained scalable phone-to-phone graphic, reduced unnecessary prose, uses one plain-language three-step explanation. Host and Join remain truly disabled. Display accurately says “Pairing coming soon,” not “secure connection established.”
- **Voice / PTT:** preserved “HOLD TO TRANSMIT” exactly, explicitly **disabled** with improved text contrast for legibility. “COMING SOON” and microphone-off wording avoid implying real transmission.
- **Settings:** ordinary page now shows Appearance, Privacy and a single Advanced entry. Technical identity creation and one-shot TLS test remain available via Settings → Device and developer tools → corresponding option, not removed or auto-triggered. The history row explains that session-only is a **planned default**, rather than claiming messaging already exists.
- **How it works:** short, welcoming guide and a concise description of privacy limitations, including that recipients can save what they see.

## Safety boundaries

- Android Keystore, TLS 1.3 / exact peer-pin handshake test and its manual fingerprint screen remain unchanged. The manual test is available from Advanced / Developer tools.
- `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain **false**.
- No microphone/camera/Bluetooth/location permissions, no new servers or analytics, no message storage, and no plaintext transports.
- This design pass changes no runtime crypto, protocol, persistence or signing behavior. Screen and vector assets remain native Android, no WebViews or screenshots-as-UI.

## Acceptance: manual Android QA

1. Home is compact and friendly, with one clear Connect CTA; no overflow or clipped left edge at normal and large font scales.
2. Connect displays **Local Wi-Fi** first, with other methods visibly **later** and non-interactive; default connection path no longer exposes certificate or IP jargon.
3. Local Wi-Fi's Host/Join are visibly unavailable, explanatory steps are concise, and no fake connection occurs.
4. Voice / PTT's **HOLD TO TRANSMIT** can be read even in disabled state; pressing it does **nothing** and accesses no mic. Disabled/coming-soon state still unmistakable.
5. Settings → Device and developer tools → LAN handshake remains available for existing private offline technical tests; Android Back unwinds correctly.
6. Home/Connect/Local Wi-Fi/Voice/Settings remain comfortable in portrait, 320dp widths, landscape and larger fonts; scroll only when necessary.
7. Dark/light themes and system bars remain legible; previous user-reported left-glyph clipping, light-status-bar contrast, and Android system Back regressions have not returned.
8. Keep security claims factual. No tester data in screenshots or public PR logs.

## Next milestone

After user visual acceptance, prioritize a **real two-phone offline LAN connection using verified identities and simple QR-first pairing**, then gated encrypted session-only text. Do not let UI polish turn into indefinite delay of actual communication. Review the stacked draft PR chain and integrate into a clean development baseline when ready.
