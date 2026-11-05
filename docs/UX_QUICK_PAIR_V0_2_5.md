# Eden Comms — Guided QR connection test v0.2.5

Date: November 4, 2026. Status: feature branch / physical QA required.
Scope: simplify the already-implemented **development-only** two-QR LAN test. This is not shipping messaging.

## First-time user flow

1. **Connect nearby** from Home opens this test directly (no extra Connect → Wi-Fi → Test nesting).
2. Person A taps **Show a code**; Person B taps **Scan a code**. If a local key has never been created, a short, explicit consent dialog creates it without an unrelated settings detour.
3. Person B scans A's QR, then their reply QR remains on screen **while the app automatically tries a local connection** (no Join/Connect button).
4. Person A taps **Scan their code** and scans B's reply. The app automatically starts the Host listener (no Start listening or Host is ready button).
5. Both phones report success/failure in plain language; opt-in **Technical details** displays existing safe diagnostic messages. Test socket closes. No messages/voice sent.

The two in-person QR scans remain essential: the host cannot safely trust the joiner's exact certificate merely from the first host QR. Single-QR+SAS is **future design requiring independent security review**, not implemented in this version.

## Technical constraints and safety

- Automatic retry is **TCP only**, max 120s from first connection attempt; retriable failures are connection refusal or TCP connect timeout, with 700ms delay between attempts. Insecure TLS, different-network, no-route and bad pin/session proof are NOT silently retried.
- Host listener uses 90s bounded accept window once reply QR is scanned. Both phones remain on the same Wi-Fi; no WAN needed. No discovery, cloud service, new permissions, direct device identifiers or message storage.
- Cancellation, page exit and app background should close the attempt and ignore stale callbacks. No silent identity regeneration; legacy v1 identity upgrade is explicitly consented and old keys are preserved until validated.
- Existing **Advanced Developer Tools** retains manual LAN TLS diagnostic, which is separate from the normal QR flow.
- ReleaseCapabilities CAN_CONNECT / CAN_SEND_MESSAGES / CAN_TRANSMIT_VOICE remain **false**. This is not a persistent chat session or independently reviewed security claim.

## Device QA before accepting

- Both physical phones on same Wi-Fi, mobile data off, router WAN optionally disconnected. Install same version and scan fresh codes. Check that presenter auto-listens, joiner retains QR during TCP retries and both see successful pinned TLS + QR session proof.
- Recreate former **PEER-UNREACHABLE** timing case by waiting 5–15s before host scans the reply; Join should recover through bounded TCP retries.
- Host never scans reply: Join times out without authentication or app message data. Host cancels or backgrounds: listener closes. Join cancels, backgrounds or rotates: retries stop.
- Wrong/stale/self QR, different network, guest isolation, missing/revoked camera permission and timeout fail closed. Do not share active QR screenshots.
- Test older Android 13 Galaxy A32 5G and Android 14–16 phone, compact display/200% font/TalkBack, navigation back/history and light/dark themes.
- Verify only expected permissions (INTERNET, ACCESS_NETWORK_STATE, user-initiated CAMERA), no messages/voice, no peer trust persistence, and no raw network addresses in logs/screenshots.
- User test: hand phones to people unfamiliar with the app; compare number of manual actions and completed pairings with v0.2.4.

**Known limitation:** still requires two QR scans and a user press to switch the first phone from showing its code to scanning the reply. Removing the second scan safely requires a separately reviewed mutual-confirmation protocol.
