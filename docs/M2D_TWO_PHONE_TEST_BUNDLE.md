# Eden Comms v0.2.6 — Two-phone test bundle

> **v0.2.7 update:** See `docs/M2D_SESSION_LIFECYCLE_AUDIT.md` for safety fixes and added regression tests. The physical two-phone acceptance checklist below remains outstanding; no need for an intermediate install solely for the audit.

**November 4, 2026.** Development-only branch: `feat/m2d-two-phone-demo-session`.
This is **not a secure messenger release** and has not passed physical device QA.

## What is new

Build one APK that tests more than QR pairing:

1. Home → **Connect nearby** on both phones.
2. A taps **Show a code**; B taps **Scan a code**. B scans A; A scans B's reply. No manual listening/Connect buttons.
3. Both sides must authenticate exact certificate pins via TLS 1.3, complete the reciprocal QR nonce / challenge proof and a fixed READY exchange over that TLS socket.
4. Only then does the debug screen display **Connected — test conversation** and four buttons with fixed, harmless phrases: `Hello!`, `Message received.`, `Testing the connection.`, `Can you read this?`.
5. Tap phrases on A and B; compare which phone says **You** versus **Other phone** and sent/received counters. Sent only means written to TLS; there are **no false delivered/read receipts**.
6. Tap **End test and disconnect** on one phone. The second must report that the session ended. Test transcript is in screen memory only and cleared when the test ends, cancels, backgrounds, or resets.

All test message frames use the existing bounded `FrameCodec` **inside TLS only**, with a strict versioned 3-byte `TestPacketCodec` payload. Input cannot carry arbitrary text. This is not a production messaging protocol. It sends predefined phrases over the local network; the app therefore must NOT claim that no test data is transmitted. No voice, attachments, contact data, peer memory, chat history, cloud service or persistent chat storage.

## Timing and disconnection

- Presenter TCP listener is bounded to 90 seconds after the second QR is scanned. Joiner retries only TCP connection refusals/timeouts for up to 120 seconds while reply QR is visible. Never retry/downgrade failed TLS/pinning.
- TLS and QR proof have a 15-second socket timeout, then the established demo uses a heartbeat every 8 seconds, considers a silent peer disconnected after ~32 seconds, and expires after 10 minutes even if active.
- Wi-Fi interface/address change closes the test. App backgrounding, navigation away and manual Cancel also close the test; no background radio service.
- Malformed frame, wrong version, unknown message ID, missing READY, replay/stale QR, wrong pinned certificate, or broken TLS must fail closed. Both phones should still need the same Wi-Fi; WAN internet is not required.
- Development **debuggable app** is required for this experimental session; release builds refuse instantiation. Normal `ReleaseCapabilities` remain false for connection, text and voice.
- The older `LAN connection test` remains in Developer Tools as a handshake-only diagnostic.

## Physical test script (record PASS / FAIL / NOT TESTED)

| ID | Action | Expected |
| --- | --- | --- |
| T01 | Same Wi-Fi, WAN on; two fresh reciprocal QRs | Both screens say Connected — test conversation |
| T02 | Turn mobile data off and remove router WAN, keep LAN | Same behavior without any internet |
| T03 | A sends Hello!, B sends Message received. | Correct directions and counters on BOTH devices |
| T04 | Send 3–5 distinct phrases in both directions | No missing/duplicate UI entries; counters consistent (without delivery claims) |
| T05 | End test on one phone | Other phone exits test session; no more sends |
| T06 | Disconnect one phone's Wi-Fi while connected | Other phone reports disconnection within bounded timeout; no cellular fallback |
| T07 | Joiner shows reply before presenter scans it | Automatic connection retries work without manual Connect |
| T08 | Presenter never scans reply QR | Safe connection failure, never a false success |
| T09 | Wrong / expired / old / own QR | Reject safely; no test phrase sent |
| T10 | Cancel, system Back, app background or permission denial | Sockets closed; no unintended ongoing session or data |
| T11 | Android 13 Galaxy A32 5G + a newer Android phone | Both negotiate verified TLS/session proof; test preset exchange works |
| T12 | Try non-debuggable build, if available | Test conversation cannot be instantiated |
| T13 | Permission and storage inspection | No microphone, Bluetooth, location, contacts; no transcripts/identities in backup/logs |
| T14 | Landscape, narrow screen, 200% text, TalkBack | Buttons accessible; no clipping/unreachable buttons |
| T15 | Mix older handshake-only APK and this version | Fail closed on incompatible READY exchange; never claim chat connection |

## Limitations and next milestone

Automated JVM tests can prove frame parsing and policies, but not real Android Keystore interop, local network behavior, end-to-end confidentiality, delivery or independent security assurance. No recorded latency measurements, no persistent chat, no push notifications, no voice, no remembered peers, and no support for arbitrary typing. Do not submit personal information.

After both phones pass this bundle, implement a reviewed authenticated/authorized session adapter, graceful reconnection, safe production text design, and broader real-device security/UX evaluation. Do not turn on real text or Voice/PTT because this demo works.
