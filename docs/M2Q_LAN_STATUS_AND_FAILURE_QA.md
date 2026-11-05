# Eden Comms M2q — Live LAN test observability and safe failure categories

**November 5, 2026.** This code is part of the **debug-only, four-preset-phrase LAN experiment**. It is not tested on two physical phones and is not a cryptographic or production-messaging certification.

## Concrete behavior implemented

Previously the UI displayed a generic “connecting” state from TCP connect through mutual TLS 1.3, certificate pin check, reciprocal QR session proof and READY exchange. A failure after the QR proof could be reported as though the QR proof itself failed. Session liveness failures also shared one generic text explanation.

M2q emits **typed progress events** as each check **starts**. The status describes an ongoing operation, NOT successful authentication, a remotely confirmed action or human permission.

| Operation started | UI text intent |
| --- | --- |
| TCP listener / join attempt | Waiting for a peer or connecting on local Wi-Fi |
| TLS negotiation | Negotiating the local TLS connection |
| Certificate pin verification | Checking the other phone's certificate identity |
| Reciprocal QR binding | Checking both phones' QR session proof |
| Post-proof READY exchange | Waiting for both phones to confirm test readiness |
| Actual mutual READY complete | Only now show connected debug test |

`LanDemoSession.Phase.PROGRESS` includes one of four fixed `LanDemoStatus.Progress` enum values. Progress callbacks are accepted by Compose only for the current attempt and while the debug transcript is still WAITING. A callback cannot reopen the test after END, CANCEL or a new pairing attempt; `emit()` also stops normal progress/activity after a terminal event is issued.

## Failure and disconnect classification

- **Before READY:** `LanProbeDiagnostics.Step.READY_EXCHANGE` and distinct `READY_TIMEOUT` / `READY_REJECTED` codes distinguish the final test readiness check from QR binding or TLS failures. Existing fixed, privacy-safe diagnostic descriptions remain, with no raw exception messages.
- **After READY:** `LanDemoStatus.EndCode` uses a fixed classification for a remote CLOSE packet, Wi-Fi change, ten-minute session limit, more than 32 seconds of peer silence, impossible monotonic timestamp, transport interruption, malformed protocol frame/packet or watchdog failure. Malformed test packets now produce protocol-specific outcomes rather than generic network interruption.
- Normal non-terminal events are suppressed once a terminal event is chosen, even with racing heartbeat/writer/reader threads.
- The app continues to disconnect and close its sockets when session liveness or network checks fail; there is no automatic reconnection, peer trust retention, voice, arbitrary text, or plaintext fallback.

**Important nuance:** Codes are diagnostic clues derived from local observations; they do not prove that the peer received a message, that an adversary attacked the transport, or that a protocol is production secure. A low-level I/O error can remain an ambiguous “connection interrupted” event.

## One-phone QA evidence

The already opt-in **Copy this phone's test summary** includes three additional fixed-only fields:

- **Last connection operation STARTED:** `TLS_NEGOTIATING`, `PEER_IDENTITY`, `QR_PROOF`, `BOTH_READY`, or `NOT_RECORDED`.
- **Connection end category:** `LanDemoStatus.EndCode`, or `NOT_RECORDED`.
- **Handshake failure category:** existing `LanProbeDiagnostics.Code`, or `NOT_RECORDED`.

No QR/session nonce, certificate fingerprint, IP/SSID, app log, raw Throwable message, arbitrary phrase text, device identifier or key is included. The clipboard is written only on explicit user tap; it is not encrypted storage. Two independent phone reports remain necessary for a useful paired test.

## Automated verification

- `LanDemoStatusTest`: progress copy cannot claim authorization or success, end labels are constant and do not include raw data, exceptions from the frame/packet codec are classified separately from ordinary socket interruption, nested causes and liveness categories.
- `LanProbeDiagnosticsTest`: post-QR READY timeout and invalid response are distinguishable, while private exception messages remain absent.
- `LocalQaReportTest`: default reports remain valid; fixed progress/end/handshake categories are copied without PII and STARTED never means PASSED.
- Existing M2n late READY/phrase callback and M2p virtual adapter cancellation tests remain part of the standard Android/JVM CI run.

## Deferred physical tests

Use [M2k's consolidated two-phone matrix](M2K_BATCH_PHONE_QA_WORKSHEET.md) and [issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26). With two phones, separately record the last **started** operation and terminal category during successful setup, rejected QR/pin, router WAN disabled, guest Wi-Fi isolation, forced Wi-Fi switch, abrupt remote close, 32-second silence and timeout. Validate that the UI never announces READY before the fixed two-way QR and TLS check completes. On Android 13 and newer devices verify the labels stay legible and accessible in portrait/landscape, large font and TalkBack.

The new typed diagnostic evidence does not reduce those acceptance criteria or authorize real messaging/voice. Signing-key continuity [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) and independent protocol review also remain open.

**No changes:** package identity, Android permissions, TLS version, QR/packet format, credential handling, normal release feature flags, saved history, production connect/text/voice, or app distribution signing.
