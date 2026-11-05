# Eden Comms — M2o transport-neutral lifecycle groundwork

**Recorded:** November 5, 2026  
**Status:** Source implementation and JVM CI verification pending; no Android physical-device testing.  
**Security/release status:** A review-only, **state-only** model. No authenticated transport integration, real messages, voice/PTT, or claims of security readiness.

## What this actually adds

`app/src/main/java/com/edenpractical/comms/transport/NearbySessionLifecycle.kt` implements an in-memory state reducer that can consistently represent a local-Wi-Fi, Bluetooth, or Wi-Fi Direct **connection attempt** by the existing `ConnectionMethod` enum.

It intentionally performs **zero** Android connectivity work. It does not discover nearby peers, open sockets, listen to radios, send bytes, request permissions, read a QR, generate keys, compute a handshake transcript, check certificate pins, or infer bilateral consent. The planned Bluetooth/Wi-Fi Direct transports are still **not implemented**.

### State and event contract

| State | Meaning | Permitted next operation |
| --- | --- | --- |
| `IDLE` | No tracked attempt | Explicit `begin(method)` |
| `STARTING` | An attempt is being tracked; no link has been reported | `linkPresent(attempt)` or terminate |
| `LINK_PRESENT` | An adapter **claims** a link exists, not verified secure identity | `previewReady(attempt)` or terminate |
| `PREVIEW_READY` | **Simulated/diagnostic** readiness, not permission to communicate | Terminate |
| `TERMINATED` | Terminal reason recorded; further callbacks rejected | Explicit `resetAfterTermination()` |

A callback must present the exact `Attempt` object the model generated. Each attempt has a monotonic generation number (not a secret, proof, session key, or authorization token). Old callbacks cannot corrupt a fresh attempt, including a callback from a *different coordinator* with the same numeric generation. Duplicate/out-of-order transitions on the **current** attempt fail closed with `INVALID_SEQUENCE`; old-attempt events are simply ignored. Ending a session is final until explicitly reset.

Terminal reasons are a **fixed list**, such as user cancellation, foreground/background transition, peer leaving, network change, timeout, verification rejection or invalid sequencing. No raw exception strings, private addresses, peer identities or content are stored.

## Automated QA, independent of phones

JVM tests cover:

- Every planned method (LAN, Bluetooth, Wi-Fi Direct) through exactly the same *simulated* state transitions, **without implementing those radio transports**.
- No preview readiness before a link status, no authorization or message/voice capability changes after the preview-ready label.
- No double begin, silent retry, duplicate link status, duplicate READY or resurrection after terminal state.
- Explicit reset, monotonic attempt IDs, foreign-coordinator tokens, prior-attempt events arriving after a new attempt begins.
- Every fixed terminal reason, ending during each active phase and 30 concurrent cancellation/ready races; termination must win as final state.
- Snapshot minimization: no message content, keys, device ID, peer address or authentication artifacts.

## Explicit isolation boundary

`scripts/check_security_review_boundary.sh` prevents any other Android main-source file from referring to `NearbySessionLifecycle`. This makes the new model available to review and JVM testing but **not** connected to the existing live debug QR/TLS probe, to a future production approval adapter, to any UI send button, or to an encrypted message/voice feature. The preexisting debug-only four fixed phrases continue to operate through the already tested `LanDemoSession` and `DebugSessionTranscript`.

**This is not a security proof.** Any later adapter must have its own design and review; `LINK_PRESENT` or `PREVIEW_READY` must never substitute for independently authenticated peer verification or bilateral approval. The separate `ProductionSessionAuthorization` remains unwired, and `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, and `CAN_TRANSMIT_VOICE` remain false.

## Next adapter development and gates

1. After the consolidated [two-phone debug QR/TLS acceptance](M2K_BATCH_PHONE_QA_WORKSHEET.md), map real transport progress into sanitized lifecycle events. Write independent adapter tests first; review event provenance, attempt ownership and teardown to avoid spoofed READY or reconnect confusion. **Do not treat simulated PREVIEW_READY as verified identity.**
2. Plan a transport-neutral **application-message** interface only after independent review of the chosen authenticated secure session, message framing, replay resistance and genuine bilateral approval. The present model has deliberately **no** send API.
3. Implement LAN *real typed messages* only after that review and physical-device QA. Bluetooth and Wi-Fi Direct remain later milestones, with separate Android permission/radio/device tests.
4. Any future integration PR that references `NearbySessionLifecycle` must deliberately update the security boundary and include targeted review and negative tests. Do not silently bypass the check.

The final hardware batch remains **not tested**; no new APK install is necessary to inspect this purely conceptual model. Sources of truth: [security/QA](SECURITY_AND_QA.md), [current batch plan](NO_PHONE_DEVELOPMENT_BATCH_2026_11_05.md), [physical acceptance issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26), and [signing issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27).
