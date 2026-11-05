# Eden Comms — Security, Privacy and QA v0.1
Status: **planning / unaudited**. This document is not a security certification.

## Threat model
We seek to protect the **contents** of text and voice against nearby eavesdroppers, other LAN clients, routers, ISPs and passive observers outside the two verified endpoints. We seek to limit stored data, outbound communications, third-party dependencies, and accidentally exposed identifiers. No internet or account is needed to exchange nearby messages.

We **do not** promise to hide that radios/devices are communicating, prevent IP/MAC/Bluetooth identifiers being observed by local networks, prevent a recipient from copying/recording content, protect a compromised/modified endpoint, prevent screenshots or external camera photos, or guarantee secure erasure of all past device memory and backups.

A shared LAN router can observe network metadata even if message contents are end-to-end encrypted. Android/Google/OEM system services may continue their own networking independently of Eden Comms. `INTERNET` permission may be necessary for Android local TCP sockets, even if the app never calls an internet endpoint.

## Android Keystore / compatibility policy

- A real Android 13 physical test reached **TLS-HANDSHAKE-FAILED** after network connectivity improved. Treat as unresolved TLS interop, not as proof that the handset is outdated.
- Android guidance: TLS authentication keys may need `DIGEST_NONE`, whereas our earlier local EC identity authorized only SHA-256. Versioned new identities are configured for `DIGEST_SHA256` (valid self-signed certificate) and `DIGEST_NONE` (raw ECDSA for TLS provider) and need real-device verification.
- Existing v1 identity is **never silently overwritten**. Explicit, user-confirmed upgrade generates/validates v2 before deleting v1; saved remote pins/previous QRs become invalid and require fresh in-person verification. No contacts, phone serial, IMEI or private-key bytes are stored.
- Continue requiring pinned TLS 1.3 on each side; do not downgrade for Android 8–9 without a separately reviewed provider/protocol. Android 10+ is an initial communication target, not a verified support guarantee.
- Keystore algorithm authorization is a prerequisite only, **not proof of a successful TLS handshake**. Share safe categorical errors, never raw SSL exception messages or key material.

Research and QA matrix: `docs/ANDROID_DEVICE_COMPATIBILITY_RESEARCH_2026-11.md`.

## Non-negotiable security requirements
- End-to-end encryption of application messages, attachment data and audio over **every** transport.
- Authenticated peer verification with QR or short authentication string; state plainly when a connection is not yet verified. No silent MITM acceptance.
- Established, actively maintained crypto libraries/protocols; no custom encryption algorithms or speculative homegrown key exchange.
- Secure random, nonce/replay handling, keys scoped to identities/sessions, safe rekeying and teardown, failure-closed behavior. Cryptographic specifics and dependencies reviewed before implementation.
- Privacy-preserving Android Keystore usage as appropriate; don't assume Keystore alone gives full end-to-end messaging semantics.
- No ads, analytics SDKs, cloud push service, phone-number registration, contacts upload, app-controlled backend, or hidden external requests.
- No readable message/audio bodies or secrets in logs, crashes, notifications, screen snapshots, caches or developer telemetry.
- Configure Android backup rules/data-extraction rules for both cloud backup and device transfer; test on actual devices/API levels.
- Prefer ephemeral session memory by default. Explain that clearing memory is best effort and remote deletion cannot be enforced.
- Do not persist peer names/IP/MAC or message metadata except necessary configuration/consented saved trusted-peer identities.
- Requests for Bluetooth, Wi-Fi nearby, local network and microphone permissions must be contextual and explained.

## Time-limited identity and message retention — accepted design, not yet enabled

- **Default: persist zero peer trust records and zero chat bodies.** The user's own explicitly created Android Keystore identity is a separate local key that may persist and can be reset; don't confuse it with saved *peer* trust.
- A user may explicitly remember a **verified public peer certificate/public-key identity** for a selected 1–30-day duration; recipient independently chooses their own duration. Saved peer identity is not a message or secret, but can reveal an association if a device is compromised.
- Each reconnect must cryptographically prove the matching private key; no saved name, broadcast, QR hash or device label is sufficient to authenticate.
- Future minimal trust store: versioned per-peer record (public pin / minimum creation and expiry fields), integrity-protected using Android Keystore-backed cryptographic material, app-private, backups/data-transfer excluded. No peer IP, MAC, phone number, chat body or unnecessary stable nickname.
- **No automatic renewal.** Fail closed on expiry, tampering, missing identity, rollback of the clock or identity reset; purge expired records at start/foreground/reconnection and immediately on explicit Forget. Background exact-time erasure and physical flash-cell erasure cannot be guaranteed.
- Future message-history opt-in is **separate per chat**, encrypted locally, max 30 days, session-only default. Deleting the local copy never forces recipients to delete theirs; recipients can photograph or independently save text.
- Explain these facts in plain-language inline disclosures with a contextual question-mark Help and an offline Learn more section. Never imply that an expired trust record is a remotely erased message.
- Zero new peer-trust persistence until a reviewed authentication protocol and the device-to-device tests pass. Model-only expiry calculations are not proof of an implemented store.

## API/platform constraints to track
- Android 12+ Bluetooth nearby-device permissions; Android 13+ NEARBY_WIFI_DEVICES for Wi-Fi Direct, with possible legacy location requirements for earlier versions.
- Wi-Fi Direct uses sockets requiring INTERNET manifest permission; location mode is required for some peer-discovery APIs.
- Android 17 local-network access enforcement introduces ACCESS_LOCAL_NETWORK for apps targeting API 37; evaluate NSD service picker alternative.
- Android 14+ foreground microphone services have while-in-use and background-start restrictions. Start conservative: voice while session screen visible.
- Google Play submission target SDK at least API 36 as of August 31, 2026, subject to future changes.

## Test matrix (each check recorded as PASS/FAIL/NOT TESTED)
| Area | Core test |
|---|---|
| Identity | both peers confirm QR/code; wrong code, stale and changed identity rejected |
| Encryption | captured LAN/Bluetooth/Wi-Fi Direct packets cannot reveal plaintext; transport cannot downgrade |
| Network | router WAN disconnected; no cloud DNS/API calls by app; other OS network behavior noted separately |
| LAN | same router, guest isolation, blocked multicast, phone changes Wi-Fi |
| Bluetooth | discovery, permission denial, out-of-range, reconnection, paired/unpaired |
| Wi-Fi Direct | no router, unsupported hardware, permission and location-mode denial, group-owner behavior |
| Messaging | ordered, duplicate, lost, interrupted, large and malicious frames |
| Data | no content in logs, app backups, transfers, screenshots of recents, notification previews, or crash reports |
| Retention | session end, app killed, OS restart, saved history encryption, disappearing best-effort |
| Audio | permission denied, mic state visible, live voice cutoff, background/screen-off restrictions, latency/battery |
| Accessibility | TalkBack, font scaling, 48dp targets, high contrast, screen reader labels, no color-only warnings |
| Release | clean build, signing, SHA-256 checksum, version, regression check, rollback procedure |

## Security gates
- Never market as secure, anonymous, invisible, or untraceable solely because traffic stays local.
- Before real use: confirm crypto library maintenance, licensing and threat model; document key verification and key rotation.
- Before public release: independent review proportional to the security promises. Fix critical/high flaws before marking production-ready.

## Sources for engineering verification
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices
- https://developer.android.com/develop/connectivity/wifi/wifip2p
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/google/play/requirements/target-sdk
- https://signal.org/docs/specifications/doubleratchet/
