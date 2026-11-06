# Eden Comms M3i — One-tap nearby entry and honest connection-method catalog

**Date:** November 6, 2026  
**Status:** Android implementation and automated checks; two-phone field QA and independent security review are owner-deferred.

## What changed in the actual application

- **Home → CONNECT NEARBY remains one tap** into the current QR-first local Wi-Fi development session. It still needs the two reciprocal QR scans, pinned mutual TLS 1.3, both human approvals and both matching final confirmations before any short debug test text is available.
- Home also has a small **Connection methods and availability** link for someone curious how the app works or whether it supports a router-free connection.
- The **Connection methods** screen now shows exactly one working development option: **Same Wi-Fi — DEV TEST**. Its **Continue with same Wi-Fi** action enters the existing QR pairing route *only if the installed app is debuggable*. It does not start sockets or scan until the normal QR flow performs its existing guarded steps.
- **Bluetooth** and **Wi-Fi Direct** are shown as **PLANNED • Not scanning or connecting**, as ordinary explanatory information with **no action buttons**. They require future permissions, discovery, connection establishment and verified mutual identity/consent; no radio is accessed in M3i.
- Removed the old misleading **LocalWifiScreen** whose UI said *PAIRING COMING SOON*, showed nonfunctional HOST/JOIN buttons and stated all text was disabled even though the app has an actual debug-only typed test chat. The old `wifi` route aliases the honest connection-method screen so previously restored navigation does not crash.
- No account, public directory, central matching service, automatic peer discovery, saved contacts, external IP field or internet connection is required for the current same-LAN development test.

## Fail-closed transport selection

`NearbyMethodCatalog` is a pure, dependency-free method and availability catalog. It has three defined entries keyed by the existing `ConnectionMethod` enum. A method being listed never means its radio, scanner, permissions, identity proof or security evaluation exists.

`select(method, debuggable)` returns **LaunchDebugLanQr** only for `LAN` with `debuggable=true`. Every Bluetooth, Wi-Fi Direct and non-debug attempt returns **NotAvailable**; this doesn't change the global release `CAN_CONNECT`, `CAN_SEND_MESSAGES`, or `CAN_TRANSMIT_VOICE` values (**all remain false**). A selection never substitutes for the separate pairing/TLS/human-approval authorization path.

The catalog does not read hardware state, conduct any discovery, open a socket, prompt for permissions, persist method preferences or automatically fall back to a different transport. This allows future method-specific Android adapters to be introduced and reviewed *without* treating a fake nearby-device listing as proof of trust.

## Automated checks and physical QA

- JVM unit tests exhaustively check every method, unique catalog mapping, code path gating in debug vs non-debug builds, no Bluetooth or Wi-Fi Direct enablement, and all production-release gates still off.
- Python source-contract tests check Home's one-tap entry, discoverable method chooser, removal of the obsolete mock Wi-Fi page, future radios' noninteractive presentation, and zero additions of Bluetooth/nearby Wi-Fi/location/mic permissions or radio API calls.
- Full Android Gradle JVM tests, APK packaging and security boundary checks run in PR CI.
- **No actual phone, Bluetooth or Wi-Fi Direct scans were tested or performed**. The owner will decide when hardware testing resumes. See [hardware issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26).

## For future implementation milestones

A future transport must have an explicitly user-started, foreground-bounded discovery lifecycle, clear permission rationale, no global account or location advertising, and fresh verified peer identity/session proof for every connection. Never enable an Android Bluetooth/Wi-Fi Direct action by flipping a display label. Both radios need separately reviewed functionality and handset QA.

**Next likely development slice:** opt-in discovery/transport permission design and testable lifecycle/cancel contracts *without* declaring a future radio ready. Avoid adding production signing, Google Play secrets or physical-test requirements prematurely.

Related: [Product vision](PRODUCT_VISION.md), [M3h network recovery](M3H_NEARBY_RECOVERY.md), [M3g session privacy](M3G_SESSION_PRIVACY_CONTROLS.md), [release security gate #42](https://github.com/PlayerEden/Eden-Comms/issues/42).
