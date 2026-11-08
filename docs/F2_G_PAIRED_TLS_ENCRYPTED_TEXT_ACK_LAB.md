# F2-G — paired real TLS 1.3 engines encrypt TEXT and ACK in JVM lab

**Date:** November 8, 2026. **Parent:** F2-F merged-main
`8f3c9dc113d8a58a7efe2fff569d5e70cfba291f`, green Android CI.

## User goal and honest scope

Eden Comms must eventually offer **Connect → Communicate → Disconnect**
between two Android phones without Eden-operated services: LAN,
Bluetooth Classic, Wi-Fi Direct text, then optional foreground PTT.
F2-F integrated a strict, bounded debug-only `ECBT` TEXT/ACK message
format. Until F2-G there was **no test exchanging those frames under
actual TLS encryption** between two authenticated engines.

F2-G adds a small, **unwired, debug-only encrypted protocol lab**:
`DebugBluetoothFutureTlsTextLab` encrypts one valid F2-F plaintext
frame using a real `SSLEngine.wrap` and accepts only one
`SSLEngine.unwrap`-authenticated complete TLS 1.3 ciphertext record
producing a valid exact F2-F frame. It exercises paired JVM TLS engines
with two distinct test-time EC certificates and independently pinned
peer fingerprints. At no point does this lab connect to an Android
radio, the actual on-device Bluetooth probe, a foreground chat UI,
device identity enrollment or human approval.

## Exact behavior

- Before and after encryption/decryption the existing F2-C
  `DebugTlsProviderReadiness` gate requires TLS 1.3, an idle
  non-closed engine and provider buffer sizes within a hard bound.
  A peer certificate pin is verified in the **JVM test fixture** before
  the lab is constructed. The lab alone is NOT a trust validator.
- Transmit validates the full `ECBT` TEXT/ACK plaintext **before**
  calling `wrap`, requires exact consumed/produced lengths, and
  requires one valid `TLSCiphertext` envelope before writing only
  encrypted bytes. No raw RFCOMM or plaintext fallback is possible
  through this class.
- Receive reads one complete bounded TLS record, rejects malformed or
  non-encrypted record envelopes, allocates the provider-specified
  bounded application buffer, and accepts exact fully consumed TLS
  result lengths. It can skip **at most two** authenticated,
  zero-application-byte TLS 1.3 provider records; it never treats
  partial application bytes or abnormal engine states as a ticket.
- Successful decrypted application plaintext must be one complete
  `ECBT` frame: 16–528 bytes with exact payload length/version/type
  checks, strict UTF-8 for TEXT and zero payload for ACK. Invalid
  plaintext is cleared and never returned to the caller.
- Ciphertext, plaintext buffers and temporary records are wiped on
  success/failure through `finally`. A frame/engine failure
  permanently closes the **lab object** (not the underlying socket).
  The caller retains responsibility for closing an I/O stream to
  interrupt a blocking read. No session persistence or background
  retransmission exists.
- Callers must serialize operations. There is no authenticated
  continuation handoff, consent binding or concurrent transport owner
  in this PR. Do not simply wire this lab into RFCOMM.

## Evidence and outstanding gates

- Paired F2-D JVM fixture generates two short-lived EC X.509
  identities at test time, performs actual mutual TLS 1.3 negotiation,
  checks each exact opposite SHA-256 pin and both provider states.
- New paired test: host sends Unicode TEXT; join receives only the
  decrypted text and sends app-level ACK inside encrypted TLS; host
  receives ACK, clears outstanding ID. Then join sends Unicode TEXT
  and host sends ACK, clearing the other side. This is two-way
  encrypted message+ACK evidence, **not two Android phones**.
- Wrong-frame input fails before encryption and invalidates the lab;
  forged `TLSCiphertext` is rejected and the reader fails closed;
  repeated reads after failure are rejected.
- Python and shell security checks ensure this code is debug-only,
  cannot carry stored messages or audio and is **not referenced** by
  the live RFCOMM probe or controller. Release capability flags remain
  all `false`.
- [ ] PR Kotlin/JVM+Android package+Python/security checks
- [ ] Fresh merged-main CI
- [ ] **Blocked for actual Bluetooth chat:** independent protocol
      review of owned post-consent session/record framing and ACK
      semantics, physical two-phone OEM/Conscrypt/Keystore/QR/exporter/
      consent/permission/timeout tests, and an explicitly approved
      debug-only live message transport plus UI.

**This milestone is NOT a working Bluetooth messenger.** The real
`DebugRfcommTlsProbe` still closes the RFCOMM socket after successful
diagnostic consent; its one-use completion receipt never authorizes
future communications. No APK version, signing, permissions, radio UI,
identity, saved data, or release behavior is changed.

**Rollback:** revert F2-G PR, with no on-device migration. Continue from
the [canonical full-function roadmap](FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md)
and [F2-F codec contract](F2_F_DEBUG_BLUETOOTH_TEXT_FRAME_REHEARSAL.md).
