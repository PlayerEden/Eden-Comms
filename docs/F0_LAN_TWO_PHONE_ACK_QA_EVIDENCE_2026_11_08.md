# F0-LAN-QA — honest two-phone ACK evidence in the existing app

**November 8, 2026. Source/UI reporting improvement; NOT a physical test or security signoff.**

## What changed
The existing **DEBUG-only same-Wi-Fi** chat already carries bounded text over
QR-pinned TLS 1.3 with two approvals and two final confirmations, and uses
the live application's peer-ACK handler. Its *copied one-phone QA report* did
not show returned peer-app ACKs, making it difficult to distinguish two-phone
receipt from a local TLS write in a later physical test.

This slice adds a saturated **peer-app ACK-event count (0–10,000)**, incremented
only from `LanDemoSession.Phase.TEXT_PEER_ACK` for a current-generation,
confirmed, active debug chat. It intentionally counts admitted callbacks
rather than inferring ACKs from local sends. A UI-only set of recent message
IDs still drives visible per-message receipt hints, but **only the numeric
count** enters the copied QA report.

The count persists while the completed attempt is displayed, even though
`stopTest()` clears message/ACK IDs and the socket. It resets on fresh QR
pairing or fresh connection attempt. It is never saved across Activity
recreation, written to disk, sent to a server, or automatically copied.
This adds no chat transport packets and changes no authentication or
production permissions.

## How to interpret the two actual phone reports later
On **both phones**, after ending a mutually approved harmless test session,
open **View test details and QA report → Copy this phone's test summary**
and compare these public counts:

| Phone A evidence | Corresponding Phone B evidence |
| --- | --- |
| Text written to local TLS | Text received by B's app |
| Text received by A's app | Text written to local TLS by B |
| A's peer-app ACK events | ACKs B's app sent (not separately counted) |
| READY + both approvals + both final confirms | Same checkpoints independently on B |

**Do not mark delivery or security accepted merely because counts match.**
A local write does not prove the other app received text. A peer-app ACK only
means the receiving application responded on the test socket, not that the
message was presented or read by a person. The number of ACK events can be
lower than locally written messages after disconnect, a congested writer or
callbacks arriving after teardown. An event count is not cryptographic
peer identification; independent review and full negative tests remain open.

### Minimum practical owner-deferred handset matrix
1. Verify each phone's Android version plus installed **source revision,
   version code and APK signer SHA-256**. Do not uninstall to force an
   upgrade; different signers can lose app-local identity.
2. Same router Wi-Fi, normal WAN first. Exchange reciprocal fresh QRs,
   verify PIN/TLS/QR readiness, separately approve on both phones and
   wait for both final confirmations on **each** screen.
3. Send 2–3 harmless typed test messages **in each direction**, inspect
   actual remote bubbles and local peer-app ACK hints, then end chat.
   Compare both copied one-phone summaries. Do not share actual text,
   QR frames, IPs, or peer certificate/private identity material.
4. Repeat with router Internet/WAN **disconnected**, mobile data disabled;
   confirm the same local-Wi-Fi experience without any cloud dependency.
5. Negative cases: expired/wrong/self/other-device QR, local/peer
   decline, Wi-Fi change, background/rotation/Back/disconnect and stale
   callback. Fail closed, no automatic message resend or restoration.
6. Independently review actual TLS/channel binding, QR freshness, approval,
   TEXT/ACK and lifecycle protocols with a qualified reviewer. Run real
   installation-over tests under a durable test signer.

The F0 physical gate remains [issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26),
security acceptance remains [issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42),
and durable test signing remains [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27).
No voice/PTT, Bluetooth or Wi-Fi Direct messaging is enabled.

## Verification contract
- JVM tests for report labels, bounded counters, no message content,
  and zero-ACK cases; Python source guards for live-event-only updates,
  fresh-attempt reset, terminal preservation and disabled release flags.
- Exact PR-head and post-merge CI for Android Kotlin tests, Python/security,
  manifest, debug APK, signer evidence and artifact.
- Physical two-phone and independent security review are **NOT run** by CI.

**Rollback:** revert this slice; no package, signing, database, permission,
protocol or Keystore identity migration.
