# Eden Comms — Android compatibility, accessible design & TLS signing research
**Date:** November 4, 2026. **Status:** research-backed engineering targets, not completed compatibility certification.

> **Updated physical evidence (November 4, 2026):** Following v0.2.3 Keystore compatibility changes, one device displayed a successful pinned TLS 1.3 handshake and intentional test-link closure. This does **not** prove the precise earlier failure cause or certify Android 13 support across devices. Second-phone and offline negative-case evidence is still outstanding. New v0.2.4 QR session-binding test remains untested on devices.

## Why compatibility matters
Eden Comms is a local-first communications utility for people who may keep modest Android phones for years. Avoid gating basic communication on high-end CPU, new cameras, high memory, subscriptions, Google Play Services, cellular data or an internet connection. Security requirements remain independent of phone price.

**Audience evidence:** AARP's *2026 Tech Trends and Adults 50-Plus* research reports that smartphone ownership among adults aged 50+ reached **90% in 2025**. It also reports that **three in five** adults 50+ feel technology is not designed with their age in mind. Source: https://www.aarp.org/pri/topics/technology/internet-media-devices/2026-technology-trends-older-adults/ (updated July 20, 2026; survey fielded Sept–Oct 2025). This supports readable wording and easy discovery, not an inference that all older adults own old phones.

**Accessibility evidence:** Android recommends touch targets of **at least 48dp**, particularly for people with dexterity or vision constraints. Source: https://developer.android.com/guide/topics/ui/accessibility/apps . Large system font, TalkBack, and low-contrast conditions need actual-device verification, not visual inspection alone.

## OS and support tiers
Android native app currently installs on **API 26+ (Android 8+)**, target API 36, compiled against Android API 37. **Installable does not mean a fully functional secure connection.**

| Device / OS | Planned status | Evidence / gate |
| --- | --- | --- |
| Android 8–9 (API 26–28) | **Exploratory**, UI install only for now | Built-in platform TLS 1.3 is not guaranteed. Never silently downgrade to TLS 1.2; alternative reviewed provider would be a separate explicit milestone. |
| Android 10–12 (API 29–32) | Intended secure local communications baseline | Platform TLS 1.3 supported by default from Android 10; test identity Keystore and real two-phone mutual handshake. |
| Android 13 (API 33) | **Priority physical compatibility target** | User tested a Galaxy A32 5G running Android 13 / One UI 5.1. TLS handshake reached negotiation but FAILED; this is a code/interop bug until proven otherwise. No unique device IDs are saved in this research. |
| Android 14–16 (API 34–36) | Primary recent Android QA | Test network handling, camera permissions, accessibility, 3-button/gesture navigation, foreground lifecycle and background restrictions. |
| Android 17 (API 37) | Future compatibility | Reassess local-network permission model and target-SDK behavior before raising target to API 37. |

**Source for TLS version compatibility:** Android 10 TLS 1.3 defaults: https://developer.android.com/about/versions/10/behavior-changes-all ; Android `SSLSocket` protocol support table: https://developer.android.com/reference/javax/net/ssl/SSLSocket .

**Important security caveat:** An OS that still runs the APK may be out of vendor security support. A current security patch and uncompromised endpoint remain important; the app cannot fix missing OS updates.

## Code compatibility hypothesis from two-phone QA
Earlier physical QA failed TCP connection, then after listener ordering improved, the tester reached **TLS-HANDSHAKE-FAILED** on one phone. It is not evidence of a successful verified session. This could be Android/provider interop, certificate selection, signing, peer-pin rejection or a product bug. Do not pin fault on older phones without a reproducible test.

Android's Keystore `KeyGenParameterSpec.Builder.setDigests` documentation notes that TLS private keys **usually need `KeyProperties.DIGEST_NONE`** because the TLS stack may supply the digest and ask the key to sign. Our earlier v1 identity authorized only `DIGEST_SHA256`. This may block a raw ECDSA signing request on particular providers. Source: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder .

The same spec may need **SHA-256 permission to self-sign a valid X.509 certificate**. Keep **both** `DIGEST_SHA256` and `DIGEST_NONE` for new EC-P256 identities, subject to physical-device testing. Android Keystore itself does not guarantee complete TLS compatibility. Sources: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec ; https://developer.android.com/privacy-and-security/keystore .

### Repair approach (M2c)
- V1 signing identity remains readable and **never silently replaced**.
- V2 identity uses a new Keystore alias and authorizes SHA-256 + raw ECDSA digest-none signing. Validate self-signed cert before trusting its fingerprint.
- In the *opt-in QR test*, show a clear **Update this phone's identity** action **only** when the older digest authorization is identified. Require explicit confirmation; do not regenerate keys or reset trust silently.
- Generate and verify v2 **before** deleting v1. On failure preserve the old key. A changed public fingerprint invalidates old QR codes; pair both phones again in person.
- Distinguish locally surfaced raw-signing exception types from a generic TLS handshake failure, but **never** reveal raw exceptions, private key material, certs, QR contents, IPs or identifiers.
- **No pinning bypass, TLS 1.2 downgrade, insecure key export or plaintext fallback.** Failure is a test failure, not permission to send content.

### Two-device retest
1. Install the same new diagnostic build on both phones. Confirm Android version, no need to capture IMEI/serial or user account.
2. In QR Test, if a phone says it has an older identity, explicitly approve the update on that phone. If no update is offered, proceed without resetting it.
3. Use fresh Host and Join QRs; both phones on same Wi-Fi, mobile data off, WAN optional.
4. Host scans reply, taps **Start listening**, waits for **Host is ready**; Join connects before timeout.
5. Record only safe code and final status **on both phones**. If successful on both, verify next that no app data was transmitted and the connection closes. If failed, investigate stage-specific auth and provider behavior.
6. Negative tests: wrong pin, stale QR, client cert missing, host timeout, Wi-Fi isolation, app backgrounding, key reset, rotated screen. Fail closed.
7. Do not enable Text/Voice until two phones actually prove mutual TLS and separate peer/session confirmation is implemented.

## Accessible-device QA acceptance
- 48dp+ touch targets for interactive controls, large font 200% where possible, screen reader descriptions and readable contrast in dark/light modes.
- Common 320dp wide phones, shorter displays, landscape, older cameras and slow chipsets.
- No need to type fingerprints/IPs, no external QR app, minimal permission prompts, clear error action.
- Moderate memory/battery use, no continuous discovery, no unexpected background services.
- Test actual Android 13 A32 5G, affordable Android 10–12, and Android 14–16. Android 8–9 remains **research tier** until a vetted TLS 1.3 provider supports it.
- Do not interpret failed TLS as necessarily the phone's fault; investigate our own implementation first.

## Sources
- AARP 2026 technology research: https://www.aarp.org/pri/topics/technology/internet-media-devices/2026-technology-trends-older-adults/
- Android Accessibility: https://developer.android.com/guide/topics/ui/accessibility/apps
- Android 10 TLS changes: https://developer.android.com/about/versions/10/behavior-changes-all
- Android `SSLSocket` version support: https://developer.android.com/reference/javax/net/ssl/SSLSocket
- Android Keystore digest permissions: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
- Android Keystore certificate generation rules: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec
- Android Keystore guidance: https://developer.android.com/privacy-and-security/keystore
