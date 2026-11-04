# UX-07 — Visual baseline and four usability refinements

2026-11-04. Status: implementation and Android CI reviewed separately.

The visual identity is now considered stable: grounded graphite, olive accents, angular panels, consistent launcher artwork. Do not block communications work on further decorative styling.

## Four user-visible refinements
1. **Home language:** “Connect and communicate” includes both Text and Voice / PTT.
2. **Connection simplicity:** one clear Local Wi-Fi route, described once. Bluetooth and Wi-Fi Direct grouped under “Later,” not shown as competing primary choices. No repeated “Start with Wi-Fi / Recommended first.”
3. **Voice / PTT hierarchy:** connection status → large disabled HOLD TO TRANSMIT control → one compact explanation; remove repeated channel labels and preview disclaimers. Real mic use remains forbidden by capability gates.
4. **Local Wi-Fi:** remove the permanent three-step instructional wall; retain one brief explanation beneath the phone-to-phone illustration. Host/Join remain disabled until verified QR pairing is actually functional.

## Release boundaries
- No new runtime permissions, storage, endpoints, background discovery or plaintext pathways.
- Android Keystore identity and manual TLS probe stay accessible via Settings → Advanced → Developer tools.
- Every screen must remain scrollable for landscape and accessibility text magnification, but avoid unnecessary scrolling in normal portrait.
- This is a code/UX milestone; next work is QR-based in-person mutual identity enrollment and local TLS interoperability. Avoid implying successful end-to-end messages until verified by on-device QA.

## QA
- Home fits at normal phone height; no clipped first letters, theme regressions or repeated copy.
- Home → Connect → Local Wi-Fi Back unwinds normally.
- Voice Hold to transmit disabled but legible; no permission dialog and no transmission.
- Android CI app build, unit tests and sensitive permission audit passed before locking this baseline.
