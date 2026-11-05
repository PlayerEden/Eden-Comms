# Eden Comms — Technology, Security, Privacy, Legal and Roadmap Research

**Research date:** 2026-11-04  
**Scope:** Android-first, peer-to-peer, local/offline-first communications without accounts or Eden-controlled servers. Focused on current repo `PlayerEden/Eden-Comms`; source code on stacked PRs, with physical two-device QA still pending.  
**Status:** Research/decision support, **not** an independent security audit or legal advice. Verify laws and SDK behavior at release time.

## Executive decision

**Recommended path:** preserve the simple direct local Wi-Fi + QR test architecture for initial reliability; use **mutual pinned TLS 1.3** as the encrypted transport, add reviewed RFC 9266 TLS-exporter channel binding for bilateral authorization on compatible devices, and do not enable real chat until independent review + two-device hostile-LAN tests. Plan **Wi-Fi Direct** as the first routerless fallback, with **Wi-Fi Aware** as capability-gated optional enhancement. Defer Google Nearby Connections until a privacy/dependency audit (it can speed up multi-radio connectivity but collects usage analytics/device information according to its docs). Do not implement novel cryptography.

### Current code reality (no marketing shortcuts)

- Native Kotlin/Jetpack Compose Android prototype. App debug package `com.edenpractical.comms`. minSdk 26, targetSdk 36 as of v0.2.8. **Installability from API 26 does not equal TLS 1.3 runtime support**: Android's platform TLS 1.3 table starts at API 29.
- Manifest grants `INTERNET` (required by Android for LAN TCP), `ACCESS_NETWORK_STATE`, and opt-in `CAMERA`. No contacts, location, mic or Bluetooth; `allowBackup=false`, cleartext disabled. Confirm via final built APK.
- Existing test flow: in-person two QR scans, full expected public cert fingerprints and fresh QR nonces, mutually authenticated TLS 1.3, challenge-based session proof, four fixed *test* phrase IDs, in-memory feedback, bounded foreground lifecycle. This is **not** a production messaging client and has not passed independent crypto review.
- Release switches `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` are all false. `ProductionSessionAuthorization` is an unwired policy model; `ReviewOnlyApprovalFrame` is a strict 39-byte JVM-test-only parser proposal, excluded from installed APKs.
- **No claim** that the current app works across two physical devices with WAN disconnected, in background, over Bluetooth, or with real typed chat.

## 1. What is truly possible offline?

| Option | Internet? | Same router? | Android needs | Strengths | Tradeoffs | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| Direct Wi-Fi LAN + QR (current) | No | Usually yes (or same hotspot) | `INTERNET` and network state; camera; Android 17 local permission when target 37 | Predictable full-duplex TCP, few dependencies, good text and potential PTT throughput | Local subnet/router; guest network client isolation; manual scanning; IP metadata visible | **Keep as primary baseline** |
| LAN NSD/mDNS discovery | No | Yes | Android `NsdManager`; local network permission/picker strategy | Removes manual IP/port setup; foreground peer selection | Multicast can be blocked; service broadcast reveals presence; discovery is NOT authentication | Add opt-in foreground discovery after security |
| Wi-Fi Direct / Wi-Fi P2P | No | No | `WifiP2pManager`, nearby Wi-Fi permission; device support | Routerless higher-rate communication; text/voice potential | OEM behavior, group-owner negotiations, Location Mode for discovery on some APIs, battery/power and permission complexity | **First routerless experiment** |
| Wi-Fi Aware (NAN) | No | No | Device feature and runtime availability; Wi-Fi + nearby permissions | Direct data path, potentially fast discovery, higher throughput than BLE | Many devices lack support; availability/firmware/concurrency varies; Android 8+ API not availability guarantee | Optional hardware-gated experiment |
| Bluetooth Classic | No | No | Bluetooth runtime permissions, discoverability | Can work without Wi-Fi router; suited to smaller links and text | Bonding/pairing UX, device support and throughput/latency variations; not a long-range radio | Secondary transport after Wi-Fi Direct |
| BLE | No | No | BLE scan/advertise/connect | Good for short control/discovery and low-rate nearby messaging | Small MTU/throughput, background limits, unreliable voice performance | Discovery/control or low-rate backup; **not default PTT** |
| Google Nearby Connections | No | No | **Google Play services SDK**, multiple Bluetooth/Wi-Fi permissions | Handles multi-radio discovery and encrypted transport, saves integration time | External dependency, Google says usage analytics/device info collected; affects strict independence and data disclosures | Review as optional, not default |
| Hotspot / local router without WAN | No | Shared hotspot/AP | Wi-Fi association and local sockets | Practical emergency/off-grid LAN; PTT potential | One device/AP supplies power; OS hotspot automation limits and client isolation | Supporting test scenario |
| Remote Internet P2P (WebRTC/STUN/TURN or relays) | Usually yes | No | Signaling/rendezvous + NAT traversal + possibly TURN relay | Users can communicate across cities | Cannot truthfully claim *no infrastructure* in general; relays and signaling create metadata; costs and reliability | Separate future cloud-optional product decision |
| Multi-hop mesh | Possibly no | No | Relays/peers, routing, incentives, discovery, anti-spam, permission review | May extend range through intermediate devices | **No range guarantee**; store-and-forward and routing metadata on relays; phones must cooperate and stay active; battery and anti-abuse challenges | Research project, not near-term feature |

**Non-negotiable fact:** a smartphone app cannot communicate over arbitrary distance in the absence of a physical medium or reachable network. Local radio range, Wi-Fi topology and power limits still apply. `Offline` means no *WAN/central service required* for a particular supported transport, not infinite coverage.

Sources: Android wireless connectivity https://developer.android.com/develop/connectivity/wifi ; Wi-Fi Direct https://developer.android.com/develop/connectivity/wifi/wifip2p ; Wi-Fi Aware https://developer.android.com/develop/connectivity/wifi/wifi-aware ; Bluetooth permissions https://developer.android.com/develop/connectivity/bluetooth/bt-permissions ; Nearby https://developers.google.com/nearby/connections/overview and https://developers.google.com/nearby/connections/android/get-started .

## 2. Cryptography/identity options, differentiated

| Layer / method | What it does well | What it does NOT automatically do | Recommended Eden use |
| --- | --- | --- | --- |
| **TLS 1.3 + exact bilateral certificate pinning** | Session traffic confidentiality/integrity; mutual authentication of pinned public cert key holders; modern ephemeral transport key agreement | Human consent, contact mapping, cross-session chat history protection, replay-proof application approval by itself | **Keep current transport** after real-device audit |
| **RFC 9266 tls-exporter** | Channel-binding value unique to a live TLS connection for supported provider | **Not secret**, not message encryption key, not available in Android platform API below 31 | Prefer for reviewed in-channel consent binding |
| Android Keystore identity | Safeguards long-term private keys, sometimes hardware-backed | Does not secure screen/UI, proof of real person, unpatched OS or end-to-end app authorization | Continue, explicit creation/rotation, verify hardware claims |
| Authenticated `Noise XX/IK` | Standardized key-agreement handshake patterns with mutual/authenticated static keys and forward-secrecy designs | Not a drop-in complete identity-management/consent UX; library and Android key integration still need review | Evaluate only if TLS design becomes limiting |
| Signal PQXDH + Double Ratchet / Triple Ratchet | Excellent for asynchronous/long-lived messaging, per-message forward secrecy and post-compromise recovery designs; PQ variants | Adds major state/prekey/lifecycle complexity; offline-only peer-to-peer with no rendezvous/store requires adapted design. Open-source **libsignal AGPLv3** licensing must be handled | **Not required for first live local session; later for offline queued messages/forward secrecy** |
| AES-GCM / ChaCha20-Poly1305 | Standard AEAD encryption of bytes *with correct nonces and key management* | Authentication of human peer or safe session protocol by itself | Use only through vetted standard TLS/protocol/library; do not write custom crypto |
| Short code / SAS | Human can compare a short value to detect active MITM when based on correctly bound key exchange | If guessed, autoaccepted, not compared, or based on publicly supplied QR alone, loses authentication | Future one-QR-plus-bilateral-verification research; do not remove second QR without review |
| MLS (Messaging Layer Security) | Standard protocol for authenticated evolving **group** message sessions | Additional group membership/epoch/key-distribution complexity; not first 1:1 milestone | Later group conversations, after authenticated 1:1 |
| Message encryption at rest | Protects an explicitly opted-in chat archive on a locked/lost device | Cannot revoke recipient's copy; no guaranteed forensic erasure | Session-memory-only default, optional future local encrypted history |

**Priority:** prove identity, authorization and teardown before new encryption modes. More algorithms do not necessarily mean more security.

Sources: TLS exporter RFC 9266 https://www.rfc-editor.org/rfc/rfc9266.html ; Android SSLSockets https://developer.android.com/reference/android/net/ssl/SSLSockets ; Android SSLSocket platform TLS versions https://developer.android.com/reference/javax/net/ssl/SSLSocket.html ; Noise https://noiseprotocol.org/noise.html ; Signal Double Ratchet https://signal.org/docs/specifications/doubleratchet/ ; PQXDH https://signal.org/docs/specifications/pqxdh/ ; libsignal https://github.com/signalapp/libsignal ; MLS RFC 9420 https://www.rfc-editor.org/rfc/rfc9420.html ; Android key attestation https://developer.android.com/privacy-and-security/security-key-attestation .

## 3. Practical privacy model

**Privacy ≠ encryption alone.** A router or nearby observer may still see that two IP/MAC addresses are communicating and infer timing/rate and approximate proximity, even if message contents are opaque. Neither Android OEM/OS networking nor the peer's screenshots/copies are under our application's control.

Default proposal:
- No account, central relay, contact sync, ad SDK, analytics SDK, background location, silent peer retention or cloud back-end.
- Two-phone in-person approval, session-only chat memory and no notification body by default.
- Camera required only for user-initiated QR scan; never save scanned QR bitmap, raw payload, IP or certificate bytes to analytics/logs.
- No unencrypted local cache, retained preview in Recents, clipboard copy without warning, or automatic backup of messages. Review Android `FLAG_SECURE` / screenshot UX against accessibility and user expectations.
- **Peer trust and conversation history are independent options**. If introduced: explicit per-peer consent to remember only verified public pin, max 1–30 days, no silent renewal; separately opted-in encrypted chat retention, also with clear expiry semantics. The existing repo defines these policies but has not implemented a complete trust store.
- Explain what we cannot guarantee: remote erasure, screenshot prevention from another camera, physical compromise, absolute anonymity, radio invisibility, zero OS-generated network traffic, unchanged battery usage.
- If Google Nearby Connections or any SDK is adopted, re-audit their data transfers and Google Play labels. Nearby's provider docs say performance/device information may be collected for usage analytics. Distinguish **Eden-owned servers** from third-party service data.

Threat tests: malicious nearby peer, spoofed discovery advertisement, MITM on public Wi-Fi, captured/stale QR, copied invite photo, role reversal, replayed approval, false receipt, fake TLS handshake success UI, app background and Wi-Fi migration, modified phone, OS crash dump, backup/device migration, intercepted voice notes, untrusted SDK telemetry.

Source: OWASP MASVS/MASTG https://mas.owasp.org/MASVS/ ; FTC mobile-app security guidance https://www.ftc.gov/business-guidance/resources/app-developers-start-security .

## 4. Android-specific engineering risks

**Important 2026 platform change:** Android 17 (target SDK 37+) requires either runtime **`ACCESS_LOCAL_NETWORK`** for broad local sockets or suitable privacy-preserving system-mediated discovery/pickers. Current target SDK is **36**; docs explicitly say *do not request the new permission until targeting 37*. Review `NsdManager`'s `FLAG_SHOW_PICKER` and permission-free selected-service connections where applicable. Update consent UX and QA when eventually targeting 37. Android 17's `ACCESS_LOCAL_NETWORK` permission belongs to the Nearby Devices group. https://developer.android.com/privacy-and-security/local-network-permission

**TLS availability:** platform TLS 1.3 begins at API 29; standard `SSLSockets.exportKeyingMaterial` begins API 31. Older API26–28 installation must not silently downgrade to TLS 1.2 or claim validated encrypted compatibility. Decide explicitly if production will be API31+ or use a separately reviewed provider for older devices. https://developer.android.com/reference/javax/net/ssl/SSLSocket.html ; https://developer.android.com/reference/android/net/ssl/SSLSockets

**Bluetooth/Wi-Fi Direct permission burden:** Android 12 Bluetooth uses `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`; Wi-Fi Direct discovery on API33+ requires `NEARBY_WIFI_DEVICES` and on older targets may require location. Users need a clear explanation. Avoid asking for all transport permissions during first launch. https://developer.android.com/develop/connectivity/bluetooth/bt-permissions ; https://developer.android.com/develop/connectivity/wifi/wifip2p

**Voice/PTT:** microphone permission, OS background-foreground-service and while-in-use restrictions, buffering, echo control, packet jitter and latency are separate projects. Start strictly foreground-only with a verified live session; treat voice recording/storage as separate consent and design. Evaluate standardized low-latency codecs (e.g. Opus), Bluetooth throughput, Wi-Fi power and actual OEM behavior before choosing a default. No microphone permission should be added now.

**Quality engineering:** dependency pinning/updates, R8/release configurations, reproducible APK checksums, CI permissions validation, foreground cancellation, portrait/landscape + TalkBack, API matrix and physical no-WAN traffic capture.

## 5. Legal, distribution and policy questions (NOT legal advice)

### United States encryption export controls
**Encryption software is lawful to develop and use in the US**, but distribution/export classification may apply—even for free mobile apps and open-source repositories. BIS's official guidance explains mass-market `740.17(b)(1)`, possible ECCN `5D992.c`, self-classification or CCATS and certain annual reporting requirements/exceptions. **Do not assume a free APK automatically exempts everything**; BIS discusses publicly available encryption source/object code and when notification/classification is still relevant. Before public international distribution, inventory encryption functions/dependencies, determine classification/export destinations and complete the relevant BIS steps or consult qualified export counsel.
Sources: https://www.bis.gov/learn-support/encryption-controls/encryption-items-not-subject-to-ear ; https://www.bis.gov/learn-support/encryption-controls/mass-market ; https://www.bis.gov/learn-support/encryption-controls/annual-self-classification .

### Google Play and marketing promises
Google Play requires a privacy policy linked **within the app and on its listing**, and an accurate Data safety form for published apps—even if no data is collected. **Data sent end-to-end encrypted only between sender and recipient can be out of scope for Google's “collection” disclosure**, subject to Google's exact definition; third-party SDK data/analytics or app-controlled endpoints must be analyzed separately. Internal test-only tracks have specific exceptions for display of the Data safety section, not carte blanche for inaccurate disclosures. Avoid unsupported promises such as “untraceable,” “military-grade,” “zero data collected” if SDKs collect telemetry, “fully secure” before review, or “disappearing messages cannot be recovered.”
Sources: https://support.google.com/googleplay/android-developer/answer/10787469 ; https://support.google.com/googleplay/android-developer/answer/10144311 ; https://support.google.com/googleplay/android-developer/answer/9859455 ; FTC https://www.ftc.gov/business-guidance/resources/app-developers-start-security .

### US state / Kentucky law
Kentucky's **Consumer Data Protection Act took effect January 1, 2026**. Its main size/revenue triggers are processing data of **100,000 Kentucky consumers**, or **25,000** consumers plus **over 50% revenue from selling personal data**, among other scope/exception details. A small app with no developer-collected user data may fall outside its applicability, **but that isn't a blanket waiver of privacy/security duties, FTC principles or Google Play disclosures**. Reassess if the business gains accounts, analytics, chat storage or a large user base. More states (including California) have separate laws and different definitions. Source: Kentucky Attorney General https://www.ag.ky.gov/about/Office-Divisions/ODP/KCDPA/Pages/default.aspx and https://www.ag.ky.gov/about/Office-Divisions/ODP/Pages/default.aspx .

### EU/EEA privacy (only if relevant to distribution or offering)
If offering services to people in the EU or monitoring them, GDPR may apply **even to US companies**, depending on the actual activities. Encryption and pseudonymization are not identical to anonymous data. When users alone communicate locally, determine carefully what data the **developer** actually processes (e.g. crash telemetry/SDKs) vs what stays between users. If targeting EU, review lawful basis, privacy notices, retention, transfer obligations, security and relevant rights with counsel.
Sources: https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/application-gdpr_en .

### Minors, voice and content
If directed to children under 13 or the operator has actual knowledge of collection from them, **COPPA** may apply, including parental notice/consent and retention safeguards; Google's Families policies can impose additional restrictions if the app targets children. Don't casually advertise a private messenger to children without a reviewed plan. If future voice functionality stores/transmits recordings or records calls, evaluate federal and relevant **state recording/interception consent laws** before launching: the federal one-party consent rule does not erase stricter state-specific rules or consent requirements; ordinary live PTT should not silently record. An app offering only direct messages today is not an automatic substitute for legal analysis of provider obligations.
Sources: FTC COPPA https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions ; federal interception 18 USC 2511 https://uscode.house.gov/us/usc/t18/s2511/overview.html .

### Open-source licensing
`libsignal` is licensed **GNU AGPLv3**, with substantial obligations if incorporated, and the repository explicitly contains an encryption export notice. Other third-party crypto, QR, camera and codec libraries have their own licenses, notices and distribution terms. Maintain an SBOM/license inventory and get licensing advice before adopting a library, particularly if you want proprietary source code or closed-source paid distribution. Do not copy another messenger's code merely because its repository is public.
Source: https://github.com/signalapp/libsignal .

## 6. What we should prioritize (product + engineering)

| Order | Workstream | Pass criterion |
| --- | --- | --- |
| P0 | **Complete reviewed authenticated approval design** | Security reviewer accepts exact mutual identity, RFC 9266 binding, canonical transcript, both approvals, negative/replay scenarios and Android version policy |
| P0 | **Two physical devices / WAN-disabled LAN** | Both Android 13+ phones exchange only fixed test phrases in both directions, reject wrong QR/cert, disconnect cleanly, no internet fallback |
| P1 | **Production, controlled 1:1 text session** | Only gated sends after verified approval over same transport; in-memory only default; evidence from real-device QA and packet capture |
| P1 | **Simpler discovery UX** | NSD foreground discovery without authenticating by names or leaking stable identifiers |
| P2 | **Routerless Wi-Fi Direct proof-of-concept** | Supported devices connect without router; permissions and fallback clear; identical authentication used on new transport |
| P2 | **Privacy and release foundations** | Privacy policy/Data safety, SDK license/export inventory, SBOM, threat-model and independent security QA |
| P3 | **Optional Wi-Fi Aware and Bluetooth** | Availability-gated, battery/performance tested, no downgrade of authentication, optional permissions |
| P3 | **Voice/PTT** | Verified session, mic consent, foreground lifecycle, measured jitter/latency, no unintended recording or plaintext |
| Later | **Ratchet/offline store-and-forward, group MLS, remote/internet mode** | Separate protocol and privacy/security review; honest infrastructure and retention disclosures |

## Open decisions for owner

**1. Product philosophy:** strict *no Google-dependent services/SDKs* (max privacy and independence), or allow an optional Nearby Connections plugin after disclosure? Recommendation: **strict core, optional later**.

**2. OS support:** initial production communication on **Android 12/API31+**, with older systems showing an unsupported notice until a vetted cryptographic provider is verified? Recommendation: **yes for the exporter-bound v1 session**, but preserve installability/testing decisions separately.

**3. Pairing UX:** two QR scans for strongest simple in-person identity exchange versus future one QR + human code comparison? Recommendation: **retain two QRs for first release**, revisit later with reviewed protocol/field testing.

**4. Emergency use:** initial priority indoor/local Wi-Fi and hotspot text/PTT versus routerless ad-hoc range extension? Recommendation: **prove LAN text first, then Wi-Fi Direct; don't claim mesh/off-grid communication before real hardware work**.

**5. Open source policy:** proprietary app, fully open-source security-sensitive code, or mixed licensing? Recommendation: **decide before adding AGPLv3 dependencies or publishing compiled artifacts**. Public GitHub repository by itself does not grant a blanket reuse license.

---

**Bottom line:** Eden Comms can plausibly become a genuinely useful account-free, local encrypted communication tool. The highest risk isn't a shortage of encryption algorithms; it is **trust, OS interoperability, user consent, and making accurate promises**. Keep all release send/audio flags off until code review and real-device evidence justify them.
