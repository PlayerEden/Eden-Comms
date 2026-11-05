# Eden Comms M2i — no-device debug-session reliability pass

**Date:** 2026-11-04. **Scope:** debug-only fixed-phrase LAN test.  
**Physical status:** NOT TESTED. **Independent protocol review:** NOT DONE.  
**Production connect, free text and voice remain disabled.**

## Why this pass

The current prototype has an important physical two-phone acceptance gate (#26), but several timing, retry, packet-ordering and clean-disconnect behaviors can be checked deterministically on the JVM first. This reduces avoidable hardware test churn without implying a working security product.

## Changes

1. **Watchdog policy:** Extract elapsed-realtime liveness decisions to `DemoSessionLivenessPolicy`. Keep the existing 10-minute maximum session and >32-second peer-silence cutoff. Test exact boundary timestamps, fresh heartbeats, absolute session expiry, impossible timestamp ordering, and a concurrent receive timestamp advancing during the watchdog sample. The Android heartbeat loop still closes sockets and revokes the demo on expiry.
2. **Bounded join retries:** `LanConnectRetryPolicy.delayBeforeRetryMs` caps the last retry delay by remaining budget (max 120 seconds); `LanDemoSession.connectTcp` also checks the deadline **before** opening each new socket. The existing 700 ms retry / 1.5 s TCP connect values, and strict rule **never retry TLS/pin/proof/routing failures**, remain intact.
3. **Simulated two-endpoint transcript:** A JVM pipes test runs the actual `SessionProofExchange` host and join roles followed by an exact READY handshake, four fixed phrase IDs sent each direction and CLOSE. Wrong peer pin and unexpected early preset must fail before demo message exchange. **No real sockets, real Android Keystore, TLS engine, radio, IP addresses or physical handsets are exercised.** Its result is evidence for packet sequence logic only.
4. **No APK capability changes:** no new permissions, external network endpoints, message composer, key handling, dependencies, or production authorization integration.

## Automated QA matrix

| Scenario | Source of automated evidence | Physical requirement |
| --- | --- | --- |
| Join initially refused or TCP timeout | Existing retry policy tests + bounded-delay boundary tests | Presenter starts late; joiner retry timing |
| TLS/certificate failure is never retried as TCP | Existing `LanConnectRetryPolicyTest` negative cases | Wrong peer/changed pin on real TLS |
| Deadline reached during retries | New pre-connect deadline + delay policy tests | Real 120 s timeout responsiveness |
| Heartbeat and session lifetime | New `DemoSessionLivenessPolicyTest` | Wi-Fi drop, radio sleep, app background |
| Positive exact QR proof + READY + four fixed phrase IDs each way + CLOSE | New `DebugTwoEndpointTranscriptTest` using JVM pipes | **Both** physical phones must independently show correct receipts |
| Wrong remote public pin or premature packet | New simulated transcript negatives and existing codec/proof tests | Replay/spoof/malformed peer on real socket |
| Cancellation/register race and bounded queue | Existing `SessionSocketRegistryTest` and `DemoSendBudgetTest` | Foreground/background and rapid tapping |

## Unchanged release blockers

- Two real devices, ideally including Android 13 and a newer Android release.
- Same Wi-Fi with router WAN disconnected and cellular disabled: prove real offline behavior.
- Both QR directions, exact mutually pinned TLS 1.3, live Android exporter diagnostic, reciprocal delivery and clean disconnect.
- All negative / timeout / wrong-device cases from `docs/M2D_TWO_PHONE_TEST_BUNDLE.md`.
- Correct Android signing identity and upgrade-safe installed-state behavior (issue #27).
- Independent protocol review and separately integrated, actually authenticated bilateral approval **before** any unrestricted chat or voice can be enabled.

The user should not install this branch as a new test build merely for JVM test expansion. The next on-device APK should be chosen from a verified later build and the signing caveat must be respected.
