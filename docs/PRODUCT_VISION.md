# Eden Comms — Product Vision v0.1
Status: proposal / planning, 2026-11-03. Working name: Eden Comms (EC).
Brand: Eden Practical. Repository: PlayerEden/Eden-Comms.

## Mission
Make private, understandable, nearby Android communications available without accounts, company-operated servers, or a working internet connection. Guiding flow: **Connect → Communicate → Disconnect.**

## Product principles
1. Function before flair: one obvious primary action per screen.
2. Privacy by architecture: no cloud message services, analytics, advertising, third-party trackers, or mandatory registration.
3. End-to-end encryption for every transport; never silently downgrade to plaintext.
4. Honest limits: no claim of total anonymity, invisibility, undetectability, or guaranteed erasure.
5. Accessible: scalable text, TalkBack, 48dp+ touch targets, high contrast, haptics optionally disabled, no color-only status.
6. Minimal permissions and clear permission rationales; ask when features are used.
7. A restrained utility aesthetic: charcoal/navy, muted teal, off-white, amber only for attention, clear typography. Avoid tactical jargon or fake military branding.
8. Offline by default. LAN, Bluetooth, and Wi-Fi Direct all planned; no internet relay service.

## Target use cases
- Two nearby people on the same Wi-Fi network, including when the router has no internet.
- Two people connecting directly without a router via Bluetooth or Wi-Fi Direct.
- One person sends a quick text; later, a quick push-to-talk voice transmission.
- User can choose whether chat history remains on the device.

## Primary screens
1. **Home**: Nearby / Connect, last connection method and clear offline/local status.
2. **Connect**: discover, manual transport picker, request/accept, QR or short-code identity verification.
3. **Session**: peer name, verified-encrypted indicator, transport, connection quality if measurable, message composer, disconnect.
4. **Preferences**: Session-only default; encrypted local history optional; disappearing-message timer as best-effort retention; connection and accessibility options.
5. **How It Works / Privacy**: short human-readable explanation first; technical details expandable.
6. **About / Diagnostics**: app version, permission rationale, local-only diagnostics with user-controlled redaction, no background submission.

## User journey
Open app → tap Connect Nearby → select peer → approve pairing on both phones → compare verification code → chat → disconnect. If scan fails, provide a visible manual connection option; do not require IP entry for normal use.

## Message and privacy semantics
- **Session-only (default)**: app does not intentionally persist chat content; it clears in-memory session state when session ends where possible. This does not guarantee erasure from all device memory, notification surfaces, recipient copies, or OS snapshots.
- **Keep on this device**: encrypted local history only; no app-managed cloud sync; app excludes sensitive content from Android backup/device transfer where supported.
- **Disappearing**: each device independently applies timer to its own copies; never imply remote deletion is enforceable. Default is off until explicitly configured.
- Pairing may store only a verified peer identity/fingerprint locally when explicitly opted into trusted peers; distinguish key retention from chat retention.
- No contact-book upload, phone number, public profile, or account.

## In scope by progression
1:1 text; LAN; verified peer pairing; reconnect within one session; visible delivery states; Bluetooth Classic; Wi-Fi Direct; push-to-talk; optional voice notes; secure local history; disappearing timers; accessibility and help.

## Later / optional
Small group sessions, image/file exchange, message export (explicit user action with warning), profile cosmetics if any.

## Explicitly out of scope for initial releases
Internet-based messaging, cloud synchronization, accounts, remote delivery/store-and-forward, automatic media uploads, mesh relays, social feed, ads, location tracking, complicated tactical dashboard, or promises of perfect secrecy.

## Definition of a good product
A first-time user can connect to a nearby phone and send a message without reading a manual. Connection type, verification state, permissions, and retention behavior are readily understandable. No hidden remote telemetry.
