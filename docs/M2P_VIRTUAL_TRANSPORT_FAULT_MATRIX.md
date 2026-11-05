# Eden Comms — M2p virtual transport fault-injection pass

**Date:** November 5, 2026  
**Scope:** JVM-only deterministic simulation, building on M2o lifecycle state.  
**Android handsets / real radios / authenticated transport:** NOT TESTED.  
**General messaging, voice, peer approval:** NOT IMPLEMENTED or ENABLED.

## Purpose

We want to reuse the same session lifecycle across eventual local Wi-Fi, Bluetooth, and Wi-Fi Direct without requiring a phone for each small code change. M2o added a transport-neutral, review-only lifecycle reducer. M2p adds a **disposable virtual-time adapter and extensive fault-injection tests** exercising that reducer, still outside the shipping app.

This is **not** a Bluetooth or Wi-Fi Direct driver. No Android `BluetoothAdapter`, `WifiP2pManager`, sockets, permissions, QR, encryption, keys, actual phone connection or user text are involved. The only implemented transport with live debug behavior remains the earlier LAN TLS + reciprocal QR demo carrying four fixed harmless phrases.

## Test-only simulator

- File: `app/src/test/java/com/edenpractical/comms/transport/ScriptedNearbyTransportAdapter.kt`.
- Supports fixed synthetic events: `LINK_PRESENT`, `PREVIEW_READY`, `PEER_LOST`, `NETWORK_CHANGED`, `TIMED_OUT`, `VERIFICATION_REJECTED`.
- `begin()` tracks the chosen connection method; `post(attempt, event, delayMs)` schedules a simulated platform callback against a **particular attempt object**.
- `advanceBy(deltaMs)` moves a deterministic, monotonic **virtual clock** forward and processes events in due-time / insertion order, reporting only a sanitized event class, timestamp, lifecycle state and whether the state model accepted it.
- **Deliberate cancellation pressure:** queued callbacks still arrive after an immediate cancel/background event, simulating work already posted by a platform callback thread. A correct coordinator must reject them, rather than depending on the framework to cancel every callback.
- At most **64** queued events, and at most **24 hours** per virtual scheduling/time step. Invalid negative/oversized delays and overflow are refused. No actual waits, radio wakeups, timers, Android dependencies, user payloads or persistent records.

The simulation exercises `NearbySessionLifecycle` as written in M2o. In particular, a simulated `PREVIEW_READY` describes **test progress only**. It makes no claim about TLS, certificate pins, peer trust or bilateral approval, and can never authorize a production send.

## Automated failure matrix

| Scenario | Simulated expectation |
| --- | --- |
| LAN, Bluetooth, Wi-Fi Direct nominal sequence | STARTING → LINK_PRESENT → PREVIEW_READY → TERMINATED; production flags remain false |
| Two callbacks at identical virtual time | Stable insertion ordering |
| PREVIEW_READY before LINK_PRESENT | `INVALID_SEQUENCE`; terminal, no recovery without reset |
| Duplicate LINK_PRESENT | Fail closed, then reject queued READY |
| Immediate cancel before link/READY/timeout callbacks | All later callbacks rejected, cancel reason preserved |
| Previous attempt timeout/READY after reset and new attempt | Stale callbacks cannot alter the current session |
| Timeout and READY on same deadline | Timeout event preempts READY when queued first; no false connection |
| Verification rejection, network change or peer loss | Session ends and later READY refused |
| App background while connection is starting | Ends immediately; queued events cannot revive it |
| Queue full, negative/oversized/invalid time | Bounded resource behavior; reject malformed scheduling |
| Seeded fault matrix (3 methods × 30 runs × 50 callbacks) | Deterministic late-callback rejection after cancellation |

All tests run under the existing `app:testDebugUnitTest` Gradle step on every PR; no physical device is necessary. The **existing Android debug CI** still enforces `ReleaseCapabilities` false, signer/manifest/privacy/dependency checks and APK build.

## Security boundary: no simulator in APK

`scripts/check_security_review_boundary.sh` now checks that the virtual adapter stays under `app/src/test` and is never referenced by Android `app/src/main` code. The main-source M2o lifecycle model remains otherwise **unwired**; no change is made to `LanDemoSession`, `ProductionSessionAuthorization`, future bilateral approval logic, packet parsing or microphone functions.

Successful JVM tests prove only these scripted transition expectations, **not** correctness of unimplemented radio transports or a secure messaging protocol. Independent review of the eventual authenticated transport adapter and approval bindings is still required before real messaging or voice.

## Later consolidated hardware QA

Continue to use [the M2k two-phone worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md) and physical [issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26). The first phone test concerns real LAN Wi-Fi + reciprocal QR/TLS **fixed phrases**; it does not certify Bluetooth, Wi-Fi Direct or future typed messages. Test signing continuity remains [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27). None of those physical acceptance requirements is marked PASS here.

## Next practical implementation

Once M2p has green PR and `main` CI, reasonable no-phone work includes verifying **real LAN debug lifecycle observable states** against the failure matrix with a carefully separated adapter design and additional tests, and then planning a reviewed bridge into production-safe session state. Do not connect the virtual adapter or state labels to an unrestricted send API.
