# Eden Comms — Grounded field-utility UI and Push-to-Talk return

Date: November 4, 2026. Source: user feedback after UX-02, concept board with sharp panels, slate and olive accents, launcher badge. Status: implemented visual-development slice, functional communications still gated.

## Decision: design before new transports

We will refine the normal-user look and navigation before extending beyond a local Wi-Fi foundation. The reference concept is a **visual north star**, not a pixel-exact replica: a purposeful, trustworthy native Android utility with hints of a field instrument, not a neon game dashboard or an official government product.

**Keep**
- Structured near-black graphite surfaces, muted olive/sage highlights, light/gray text
- Compact beveled accents and sharp-ish edges on key cards and buttons
- Consistent simple line icons for local Wi-Fi, Bluetooth, chat and PTT
- Deliberate information order: connection state, Connect, communications, Settings
- Minimal words, no repeated giant marketing headlines
- Native Material 3 scaling, reliable Back navigation and accessible tap targets

**Tone down**
- Decorative lines, wedges and angled corners: only on key surfaces, 5–7 dp cuts
- Military aesthetics: practical utilitarian, no camouflage, rank badges, faux official seals or security certifications
- Small "terminal-like" captions: use only short eyebrow labels, never for primary information
- Status color: muted and clearly textual; green alone never means "verified"

**Avoid**
- Background texture, animated glows, fine print, gratuitous icons and ever-present dashboards
- Fake "secure" or "connected" indicators, simulated pairing success, microphone access without a verified connection
- Forcing people to type a public fingerprint in normal use or export it to another app
- Heavy UI frameworks/third-party SDKs that increase attack surface, size or maintenance

## Screen map implemented

### Home
- Real "Not connected" state.
- One primary **Connect nearby** button (navigates to Connect screen, does not start a socket).
- Compact mode entries: **Text messages** points to Connect; **Walkie-talkie** opens the PTT preview.
- Settings, How it works. Development disclaimer.

### Connect nearby
- Local Wi-Fi opens its own screen.
- Bluetooth / Wi-Fi Direct are clearly future options, not clickable fake modes.
- Compact steps: Scan → Verify → Talk.

### Local Wi-Fi
- Pairing summary and disabled Host/Join buttons pending real QR identity exchange and bilateral confirmation.
- Manual full-fingerprint/IPv4 TLS test remains only at Settings → Developer tools.
- No messaging or microphone activity is enabled.

### Walkie-talkie
- A clearly labeled **Push-to-talk** screen with a visible **Hold to talk** control.
- Hold-to-talk button is disabled and labeled "NOT ACTIVE". It does not request RECORD_AUDIO, create an audio stream, record locally, or imply voice service is currently functional.
- Planned behavior: verified local peer, user presses/holds to transmit, release stops transmission immediately, clear transmission/receive states and robust cancellation on disconnect/background.
- First offline text messaging remains prior to live PTT engineering; media codec and safety/privacy design come after.

## QA and security gates
- `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain false.
- No new permissions, external services, plaintext transport, memory stores, cloud messaging, analytics, recordings or background scanning.
- Prior M1f local TLS probe, Android Keystore identity and complete full-certificate peer pinning stay intact for developer-only testing.
- Physical Android review: portrait and landscape, font scaling, dark/light, Home → Walkie-talkie → system Back, Connect → Wi-Fi → Back, no text clipping or status-bar contrast regressions.
- Never publish an APK as a secure working messenger until two phones complete real authenticated encrypted pairing and text exchange plus security review.
- Next code iteration after visual polish: QR-first local trust enrollment and verified offline LAN text; voice is an explicitly later phase.

## Reference mockup limitations
The generated multi-screen poster is a creative reference, not a runnable spec. The real interface will not reproduce tiny fonts, exaggerated bevel lighting, device hardware frames or any apparent authenticated state unless implemented and tested.
