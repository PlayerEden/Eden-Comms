# Eden Comms — M3j foreground discovery contract and privacy rules

**Checkpoint:** 2026-11-06  
**Implementation:** A transport-independent state model and truthful explanation in the existing Connection methods screen. No Bluetooth/Wi-Fi Direct permissions or scanning. Physical testing remains explicitly owner-deferred.

## Why this milestone matters

Eden Comms already has a usable *development-only* two-QR same-Wi-Fi conversation flow. Bluetooth and Wi-Fi Direct are the next transport options, but merely listing a nearby phone cannot grant identity trust. Before integrating their Android radio adapters we need a bounded, cancelable foreground discovery contract that neither requests permission silently nor revives stale discoveries after an app background/permission revocation.

## What is implemented

### Foreground discovery contract — model only

`NearbyDiscoverySession` is a synchronized, JVM-testable **future-adapter lifecycle**. It deliberately has **no Android API imports, radio adapter, sockets, QR tokens, user text, peer names or discovered-device list**. It is not wired to `MainActivity`, the current `LanDemoSession` or `NearbySessionLifecycle`. The security script refuses runtime references elsewhere.

For a *future* Bluetooth/Wi-Fi Direct adapter, the intended sequence is:

1. **Person initiates discovery while foregrounded.** LAN is excluded because its present connection is QR-first. Only one attempt can run at a time.
2. **The person explicitly consents.** This is not an Android permission grant and not peer authorization.
3. **A real Android permission result is checked by the future adapter.** Denial terminates that attempt; this model never raises an OS prompt.
4. **The future adapter must prove its actual scan started** while the app remains foreground. Model state alone cannot start hardware.
5. **During the bounded scan**, each unauthenticated observation is just a count; limit 32 events, a **30-second maximum**, monotonic elapsed-time checks. Candidate sightings are *never* trusted peers or permission to message.
6. **Stop and discard.** Cancel, backgrounding, permission revocation, radio unavailability, timeout, clock inconsistency, invalid sequencing or event flood terminate the attempt. Restart needs an **explicit reset** and new token. Late old-token callbacks never affect a new attempt.

The future Android adapter must independently stop the OS scan and check actual permissions and foreground state. The current model has **no hardware stop callback** and must not be interpreted as a complete radio implementation.

### Actual app copy (present today)

**Connection methods → How future discovery will work** now explains:
- Search is **OFF and NOT IMPLEMENTED**; current usable test is same Wi-Fi with reciprocal QR verification.
- A person will select a method and explicitly start discovery.
- The app must explain and request only the needed OS permission at that point; declining means no scan.
- Scanning will be foreground-only and time-bounded, with a visible Stop control.
- Discovered devices are untrusted until both humans complete separate identity/session checks.
- No background scanning, public account listing, automatic reconnect or saved scan results for the initial design.

The planned Bluetooth/Wi-Fi Direct entries remain **noninteractive**. The working Home **CONNECT NEARBY** direct QR flow is unchanged; this milestone does not insert any extra step.

## Test coverage and guardrails

- JVM checks for both future methods: no background start, no LAN discovery, person consent and independent OS permission result, malformed/out-of-order transitions, denial, radio unavailable, explicit cancellation, background/revocation, timer expiry, backwards clock, maximum observation flood and delayed callbacks after restart.
- A deterministic repeatable randomized-delay matrix tests that a canceled discovery cannot be resurrected by queued events.
- Kotlin policy tests guarantee every method's real discovery availability is currently **false** and privacy copy does not claim a search or peer trust is ready.
- Python source contracts require the state model to remain unreachable from live Android code, method UI to remain informational, and no Bluetooth/nearby-Wi-Fi/location/microphone permissions.
- `scripts/check_security_review_boundary.sh` now explicitly fails if the discovery state model appears in production UI/transport code or gains platform network dependencies.

The core release flags remain `CAN_CONNECT=false`, `CAN_SEND_MESSAGES=false`, `CAN_TRANSMIT_VOICE=false`. Current short typed LAN messages remain **debug-only**, unreviewed, untested on physical phones and not appropriate for private/emergency communications.

## Future integration steps (not yet built)

When implementing actual Bluetooth or Wi-Fi Direct later, create a dedicated Android adapter behind this lifecycle, with Android-version-specific manifest and runtime permission review, user-visible search/stop controls, device capability checks, explicit cleanup on background/permission loss, device discovery filtering and a hardware timeout. Never substitute the `SCANNING` model state for an Android scan result, nor accept a discovery candidate as an authenticated peer.

Only after connecting a real adapter should the pair repeat verified QR identity proof, mutual TLS/session binding, both in-person approvals and final confirmations, or an independently reviewed equivalent on the new transport. User-led two-phone device QA and independent protocol security review remain separate release gates.

**No physical tests performed for M3j.** Deferred QA issue [#26](https://github.com/PlayerEden/Eden-Comms/issues/26) and independent review issue [#42](https://github.com/PlayerEden/Eden-Comms/issues/42) stay open.

Related: [M3i method catalog](M3I_CONNECTION_METHOD_CATALOG.md), [M3h Wi-Fi recovery](M3H_NEARBY_RECOVERY.md), [Product vision](PRODUCT_VISION.md).
