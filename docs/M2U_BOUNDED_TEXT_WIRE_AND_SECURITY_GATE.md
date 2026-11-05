# Eden Comms M2u — bounded text wire candidate and security review gate

**Date:** 2026-11-05  
**Status:** Working Kotlin encode/decode implementation with JVM tests; **unwired** to the Android chat UI, live TLS socket, or actual delivery. **NOT independently reviewed.**  
**Release:** `CAN_CONNECT = false`, `CAN_SEND_MESSAGES = false`, `CAN_TRANSMIT_VOICE = false`. No Google Play messaging release authorized.

## Why this is an actual core feature step

After M2s (native conversation screen) and M2t (truthful status semantics), the next missing part is a deterministic, bounded, interoperability-testable representation for actual user-entered text and message-level ACKs. This PR implements the binary parser/serializer in **app main source**, but does not dispatch it to the network. The format is a **review candidate** that may change; it is not automatically approved merely because it passes unit tests.

Source: `app/src/main/java/com/edenpractical/comms/protocol/ProposedChatWireCodec.kt`. The security review script blocks any other `app/src/main` Kotlin source from referring to it until a separate reviewed integration changes that rule.

## Candidate INNER payload (not encrypted by this codec)

The existing `FrameCodec` is a length-bounded outer envelope for the current fixed-phrase debug test. The proposed chat inner payload is:

| Field | Size | Value |
|---|---:|---|
| Magic | 4 B | ASCII `ECTX` |
| Version | 1 B | `1` |
| Packet kind | 1 B | `1` TEXT or `2` ACK |
| Reserved flags | 1 B | Exactly `0` |
| Message ID | 8 B | Big-endian **positive** signed 64-bit ID |
| Payload length | 2 B | Big-endian unsigned byte count |
| Payload | 0–1120 B | TEXT: strict UTF-8; ACK: **exactly zero** bytes |

TEXT requires **1–280 Unicode code points**, nonblank, bounded **1–1120 bytes** after encoding. Unpaired surrogates, malformed UTF-8, overlong byte sequences and invisible C0/C1 controls (except tab and LF) are rejected, never normalized or silently replaced. No user text or peer metadata is logged in an exception. A large/incorrect declared length is rejected **before allocation**.

An ACK contains only a positive message ID. **Decoding an ACK does not authenticate its sender**: this codec has no cryptography, no proof of peer consent, no channel binding, no replay defense, no ACK-to-session ownership and no human-read evidence. A future reviewed transport and state machine must separately prove all of those conditions before displaying **Other app acknowledged receipt**. In particular, an arbitrary message ID on a wrong socket may not qualify.

### Published deterministic test vectors (inner payload only)

- TEXT id=1, text=`Hi`: `45435458010100000000000000000100024869`
- ACK id=42: `45435458010200000000000000002a0000`

The codec is intentionally wire/transport independent; the tests show its bytes can be enclosed in the existing opaque `FrameCodec` in-memory, **not** that the debug socket accepts arbitrary text.

## Protocol/security direction — candidate, not a sign-off

Use the already implemented **mutual TLS 1.3 with exact QR-pinned peer certificate identities** as the baseline secure local channel rather than inventing a second homemade encryption layer. **Do not simply widen `TestPacketCodec`**: its four harmless phrases and its READY state are specifically debug-only and are not real bilateral human consent.

A future live text adapter must be designed and **independently reviewed** as one complete system, binding:

1. Two current human-selected peer identities and **fresh reciprocal QR challenges** to the **same** mutually authenticated TLS 1.3 socket; reject key, role or nonce mismatch and expired/replayed QR.
2. A reviewed canonical channel/session transcript. Prefer the standard **RFC 9266 `tls-exporter`** channel-binding value on Android where provider support is confirmed; its value is **not secret** and must **not be used as a message encryption key**. Android platform `SSLSockets.exportKeyingMaterial` is available from **API 31**. On unsupported versions/providers, fail closed rather than silently downgrading to QR-only evidence.
3. **Explicit consent by both people** for this exact peer/session, with the remote action authenticated on that live channel. Neither a button on one phone, QR scanning alone, nor a fake test callback proves both approvals.
4. Only after the reviewed approvals: a new message-frame namespace/version, strict duplicate/out-of-order/replay rules, bounded queue and read timeouts, authenticated ACK provenance, revocation on network change/background/cancel, and session-only erasure. No silent retries; never report human read based on app ACK.
5. Independent negative testing: wrong peer, QR replay, role swap, dropped/reordered approvals, forged/early/stale ACK, old-session IDs, malformed UTF-8/oversized payloads, blocked writer, Wi-Fi switch, no WAN and Android screen/lifecycle behavior.
6. Distribution: a dedicated stable signing key, two in-place test installs preserving Android Keystore identity, real two-phone positive/negative matrix, and review of permissions, privacy disclosures and Play requirements.

### Source-backed standards

- RFC 9266 (standard TLS 1.3 exporter channel binding, label `EXPORTER-Channel-Binding`, zero-length context, 32 bytes; not a secret key): https://www.rfc-editor.org/rfc/rfc9266.html
- Android `SSLSockets.exportKeyingMaterial` API 31: https://developer.android.com/reference/android/net/ssl/SSLSockets
- OWASP MASVS network controls: https://mas.owasp.org/MASVS/08-MASVS-NETWORK/

This project has **not** received an independent protocol review. The candidate parser and these references are **inputs to that review, not evidence of an approved secure design**.

## QA in this PR

`ProposedChatWireCodecTest` covers stable little/no-ambiguity big-endian fixtures, multilingual/emoji/newline round trips, max-length Unicode, blank/overlong/invalid surrogate, all unknown versions/types/flags, negative/zero IDs, truncation/extra payload, inconsistent lengths, malformed UTF-8, ACK payload rejection, bounded frame interoperability, and release flags hard false.

`scripts/check_security_review_boundary.sh` prohibits references to `ProposedChatWireCodec` from all other Android main-source files, including UI and `LanDemoSession`. No new permissions, dependencies, socket traffic, data persistence or external service is introduced.

## Next milestone should be live product work

**M2v:** With independent review and actual physical debug LAN QR/TLS acceptance underway, implement a dedicated human approval screen + authenticated two-way consent on the same verified session, keeping real send disabled until its new security tests pass. Then integrate this reviewed text codec and authenticated ACK behavior into the native chat screen on two devices. The codec may need revision based on reviewer feedback; record any wire change with new golden fixtures and reject incompatible versions.

The existing consolidated two-phone debug test [issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26), signer continuity [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) and independently reviewed real-text acceptance remain **OPEN**. This PR does not mark any physical test PASS.
