# Eden Comms — Privacy and Security Charter v0.2
Status: **normative project requirements; not evidence of an audited product**. Date: 2026-11-03.
Owner: Eden Practical / Eden Comms.

## Mission and user promise
Eden Comms exists to provide understandable, direct, private communication between nearby people. We are not building a surveillance, advertising, social, or cloud communications product.

**Security outranks convenience, speed of release, growth, analytics, or cosmetic features.** If a feature cannot meet the privacy bar, it will remain disabled or be removed. An offline connection does not on its own imply end-to-end encryption.

### What we aim to protect
- Message, voice, and (future) file **content** from local routers, nearby radio eavesdroppers, other network peers, ISPs, malicious hotspot operators, and unintended third parties.
- Contact/peer identity, connection logs, message metadata, encryption keys, and locally saved content from unnecessary collection, persistence, telemetry, and backups.
- Peer authenticity: a nearby device must not silently impersonate the intended correspondent.

### What cannot be promised
- Zero radio/network metadata: BLE/Bluetooth, Wi-Fi and IP identifiers, timing and traffic volume can be exposed at the radio and local network layers.
- Protection after either phone is compromised, unlocked by an adversary, screen-recorded, or physically photographed.
- Control over messages intentionally copied, recorded, photographed, forwarded or retained by the other participant.
- Total elimination of Android/OEM/Google network traffic, keyboard cloud services, OS-level backups, or other installed apps' behavior.
- Provable secure deletion from every flash-memory cell or past memory snapshot. Disappearing messages are best-effort.
- Untraceable, anonymous or completely hack-proof communications.

## Mandatory engineering policies (MUST)
1. **No messaging cloud dependency:** exchange communication payloads only between paired devices via local Wi-Fi, Wi-Fi Direct, or Bluetooth; no company-operated relay, external domain, cloud push, or remote content store.
2. **Network minimization:** no ads, analytics/telemetry, crash SDKs with remote reporting, contact uploads, invasive permission SDKs, automatic update trackers, third-party auth or external messaging services. Network-scoped connections must be confined to explicitly paired local peers; audit sockets/DNS with WAN connected and disconnected. Android's INTERNET permission may still be needed for local IP sockets.
3. **Authentication before "verified":** user-friendly short code or QR comparison with peer cryptographic identity; explicit accept on both phones. Mis-matched, changed, stale, revoked, or not-yet-verified identities must cause clear warnings and fail closed. A mere Bluetooth bond, Wi-Fi password, or IP address is *not* peer verification.
4. **End-to-end encryption over every transport:** security layer independent of LAN/Bluetooth/Wi-Fi Direct. No plaintext fallback or silent downgrade. Established, maintained, licensed cryptographic implementation only; no custom cipher, key derivation, handshake, or own-made "crypto." Protocol selection, implementation and audit are M1 blocking decisions.
5. **Minimize retained data:** default session-only and no chat content written to disk, standard logs, notifications, caches, backups or crash traces. Explicitly opt-in to on-device encrypted chat history or remembered trusted peers. Keys scoped to a session with forward secrecy as a reviewed design goal; key lifecycle and identity continuity documented.
6. **Protect sensitive display and input:** no message previews in system notifications by default; secure-screen/recents protection evaluated as default with an explained accessible alternative; avoid automatic clipboard copies. Configure text input to request no personalized-learning while warning that third-party keyboards might still retain what is typed.
7. **OS and data lifecycle controls:** exclude encrypted history, identity secrets and drafts from cloud backup, device-to-device migration and future cross-platform transfer. Verify behavior on supported Android versions/OEM devices; do not rely only on allowBackup=false.
8. **Minimal and contextual permissions:** request only the nearby devices/network/mic permissions used for a selected feature, explain why, handle denials; no contacts/calendar/location tracking. If a platform API requires location services for nearby discovery, explain it, and do not collect or transmit coordinates.
9. **Voice privacy:** permission and a live verified session required before recording or transmitting; clear ongoing mic/transmit indicator and one-action stop; never upload audio or transcriptions.
10. **Public-verifiable source and release integrity:** preserve public source, dependencies/licenses, reproducible build instructions, signed releases, checksums, changelog, SBOM/dependency audits, and rollback strategy. Protect signing keys outside the repository. Avoid promising bit-for-bit reproducibility until demonstrated.
11. **Security testing and disclosure:** per-milestone unit/fuzz/negative tests, peer spoofing and MITM attempts, encryption validation, fail-closed permission testing, backup audits and network capture. An independent review is required before public claims of strong privacy; no release with known critical/high security findings.
12. **Honest UX:** label M0 "preview — not connected"; label encrypted-but-unverified states distinctly from verified states. Do not conflate secure transport with anonymous/untraceable traffic or guaranteed deletion.

## Candidate E2EE handshake design — not locked
For two nearby, simultaneously connected phones, an established authenticated *interactive* handshake (e.g., a reviewed Noise XX implementation) may be a simpler fit than asynchronous server-prekey-based messaging. Users would compare an out-of-band identity/fingerprint confirmation. Final library, key storage and session rekey/ratchet choices require dedicated cryptographic review; don't select protocol based on marketing claims.

## Threat scenarios and pass gates
| Scenario | Required safe behavior |
| --- | --- |
| Attacker on same Wi-Fi reads captured traffic | Message/audio payload unreadable; no keys in plaintext |
| Rogue device tries to join | Both endpoints explicitly approve and verify; wrong code rejected |
| Attacker alters/drops/replays messages | Authentication detects tampering; replay rejected; failures clear |
| Bluetooth/Wi-Fi transport changes | New link re-authenticated as required; no plaintext fallback |
| Phone loses network mid-send | Never claim "delivered" without acknowledgment; retry rules protect against duplicates |
| User turns off history | Content not intentionally persisted; best-effort cleanup; accurate deletion explanation |
| Android cloud backup or device transfer | Chats/keys excluded; actual device/restore tests prove policy |
| Someone monitors network metadata | Explain observable timing/identifiers; never claim invisibility |
| Keyboard/screenshot/notification exposure | Default settings minimize exposure and document residual risk |
| Compromised phone or willing recipient copies content | Warn that this is outside app's protection guarantees |

## Release gate
**STOP/NO-SHIP** on plaintext fallback, missing authentication, exposed secrets, unreviewed cryptography, hidden remote traffic, unprotected backups, misleading privacy status, critical/high unresolved security flaws, or absent security test evidence. "Runs on my phone" is not a substitute for this gate.

## How this affects the roadmap
- M0: no Internet/Bluetooth/microphone permission, no fake connection, backup opt-out, explicit preview. Build success is not security assurance.
- M1: complete security architecture/protocol selection, identity verification and negative/security tests **before** accepting real messages, even on a LAN.
- M2/M3: transport modules must reuse and pass the same security contract independently; no trust downgrade via discovery.
- M4: voice must encrypt audio and use verified peer identity; no surprise background microphone.
- M5: history, disappearing, screenshots/notifications/keyboard privacy, backup/restore tests are first-class features.
- M6: independent security assessment and privacy-claims review. Security acceptance gates apply at *every* stage, not only at M6.

## References
- https://noiseprotocol.org/
- https://signal.org/docs/specifications/doubleratchet/
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- https://developer.android.com/security/fraud-prevention/activities
