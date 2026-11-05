# Eden Comms v0.2.7 — Static Session Lifecycle Audit

**Date:** 2026-11-04  
**Scope:** Code and automated checks only. **No new on-phone physical test, independent crypto assessment, packet capture, or security certification.**  
**Base:** v0.2.6 debug-only two-phone preset-message demo (draft PR #14).

## Reviewed surfaces

- Guided two-QR pairing, KeyStore identity, local Wi-Fi routing, same-subnet checks and QR freshness
- Mutual TLS 1.3, exact leaf-certificate pinning, QR nonce/challenge exchange, in-TLS READY exchange
- Demo socket lifecycle, cancellation, app background, disconnect and Wi-Fi change
- Preset-message framing, writer queue, keepalives and local temporary UI state
- Sanitized diagnostics, permission boundaries and disabled production chat/voice flags

## Findings and changes

| Severity | Confirmed code risk | Remediation |
| --- | --- | --- |
| High | A cancelled test could race with a freshly accepted or newly wrapped socket, leaving a socket outside the shutdown code's reference. | New `SessionSocketRegistry`: registration and cancellation share one monitor, reject/close late sockets and refuse further processing between authentication stages. |
| Medium | Rapid taps could queue arbitrarily many preset sends and delay a disconnect. | `DemoSendBudget` caps outstanding preset sends at 2; UI distinguishes Busy from Disconnected. |
| Medium | Heartbeat task could enqueue duplicate pending writes when a slow TLS write blocks the sender, or silently stop if an Android connectivity check throws. | At most one pending heartbeat using an atomic flag; unexpected watchdog exceptions terminate the session instead of disabling monitoring. |
| Medium | Public QR fingerprints and short-lived nonce metadata remained referenced by the UI after a successful or failed test. | Drop temporary QR data when READY, on failure, cancellation, app background, explicit End and screen disposal. This is reference cleanup, **not** guaranteed secure memory erasure. |
| Low | Safe diagnostic text still referred to removed Host/Join/Start Listening buttons. | Updated plain-language troubleshooting. |

## Automated QA gates

- `SessionSocketRegistryTest`: close active sockets, reject late sockets, do not mistakenly untrack wrapped TLS socket, concurrency/cancellation race.
- `DemoSendBudgetTest`: fixed maximum and simultaneous callers.
- Existing `SessionProofExchangeTest`, certificate pinning tests, frame codec negative tests and QR/session tests.
- Kotlin/Android compilation, full JVM test suite, manifest permission audit, APK packaging and SHA-256 in GitHub Actions.
- Verify `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, and `CAN_TRANSMIT_VOICE` remain false; no new microphone/Bluetooth/location/contacts permissions.
- Record CI run, commit hash, artifacts and any failures on the draft PR. A green build is not proof of physical connectivity.

## Known limits, not fixed by static audit

- Two distinct Android phones have not yet exercised the v0.2.7 socket/protocol path.
- Successful reciprocal QR scan does not replace independent verification of a full production authorization and identity lifecycle.
- The debug-only demo sends **predefined, non-sensitive phrase IDs** over pinned TLS; general text, voice, retained history, account identity and trusted-peer persistence remain unavailable.
- Current keepalive and session timeouts require Android device checks under battery saver/background/screen rotation.
- A cancelled socket may already have transmitted bytes before the cancellation event. The goal is fail-closed shutdown after user cancellation, not retroactive removal of anything delivered.
- There is no proof of external attack resistance, release readiness or cryptographic certification.

## Deferred physical QA

Keep the same **one comprehensive two-phone test** plan: Wi-Fi with router WAN disconnected, Android 13 A32 5G and newer phone, first-use key consent, automatic Host/Join transition, both-direction preset transfers, one-sided End, abrupt Wi-Fi loss, wrong/stale QR, background/cancel, TalkBack/font scale, and no production permissions or history.

Do not request a new installation solely for this audit. Review/integrate stacked draft PRs bottom-up after accepted evidence. Keep the previous known-good build artifact and rollback path.
