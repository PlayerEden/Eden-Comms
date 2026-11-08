# F4-D4 — future LAN voice frame rehearsal (not network voice)

**November 8, 2026 · isolated debug-source and JVM tests only.**

## What exists
This slice builds on one-phone M6-D1 / F4-D2 / F4-D3 local microphone,
20-millisecond packetized playback, and cancellation work. It introduces
a *separate*, unpublished future LAN frame candidate that **does not run inside**
the actual LAN connection or take microphone input in the app.

A DEBUG-only Kotlin codec defines one strict **658-byte** envelope:

| Field | Bytes | Description |
|---|---:|---|
| `ECVF` ASCII magic | 4 | Candidate voice data marker, NOT crypto |
| Version | 1 | Exactly 1 |
| Kind | 1 | Exactly 1 (PCM) |
| Flags | 1 | Must equal zero |
| Reserved | 1 | Must equal zero |
| Local synthetic sequence | 8 | Big-endian long, allowed 0–249 |
| PCM length | 2 | Big-endian unsigned, exactly 640 |
| PCM | 640 | 20 ms at 16 kHz mono signed 16-bit |

The five-second/250-frame cap is for **this test lab**, not a final PTT product
limit. Audio bytes are copied and discarded in JVM tests; returned decoded
frames must be closed/wiped by the test consumer. A single synthetic receiver
accepts only ordered, gap-free sequences and permanently closes on malformed,
replayed or skipped packets. No jitter buffer, FEC, congestion handling, or
real-time network playback is claimed.

**Critical known limitation:** a frame numbered zero from an earlier synthetic
session can be accepted by a newly created receiver. This is explicitly proven
in tests. Sequence checks alone are *not* cross-session replay protection and
the codec provides no cryptographic authentication or encryption. The existing
LAN text consent, mutual TLS and QR proof must NOT be assumed to authorize
voice. This frame layout is a draft, not a final security-reviewed standard.

## Next real integration requirements (still blocked)
Before remote LAN PTT is considered for any hardware build:
1. Independent review of existing QR-pinned LAN TLS identity, session ownership,
   exporter/channel binding, both final human approvals and session teardown.
   Physical two-phone encrypted LAN text/ACK acceptance remains pending.
2. Review a separate voice start/stop and per-session/per-turn binding, replay
   protection, ciphertext length framing and nonce behavior, output buffering,
   backpressure, latency and cancellation. Never admit untrusted pre-approval
   voice frames or imply that a public sequence number verifies a peer.
3. Keep mic capture permission on explicit foreground user action. Stop capture
   and output immediately on release, disconnect, background/call interruption,
   permission/radio loss and timeout. No hidden recorder, audio file or remote
   transmission before approved debug-only session flow exists.
4. Only after both protocol approval and OEM/Conscrypt phone testing, stage a
   **separate** reviewed LAN debug voice transport PR. Wi-Fi Direct and Bluetooth
   voice require their own transport reviews and capability gates. Production
   `ReleaseCapabilities` must remain false until broader release acceptance.

## CI evidence expected
- Kotlin unit tests: full-size and maximum frame limits; valid exact round-trip,
  copied PCM and best-effort zeroing; modified header/length/sequence; truncated
  or extra bytes; ordered synthetic exchange; gap/duplicate/malformed rejection;
  and explicit *cross-session* replay weakness.
- Python + shell: new codec and receiver remain under DEBUG-only source;
  all `src/main` and live LAN/radio/voice sources MUST NOT reference them; no
  network, auth authority, file/storage, mic or release change.
- Android debug compile, JVM/Python tests, security/permission audit, signing
  and artifact checks. Passing GitHub Actions is **not** physical device or
  cryptographic review.

## Rollback
Revert F4-D4 alone; F4-D3 local audio test remains available. No package,
signer, persisted data, identity, permission or security capability migration.
