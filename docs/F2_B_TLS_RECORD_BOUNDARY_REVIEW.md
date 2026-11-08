# F2-B — debug Bluetooth TLS record boundary and buffer-cleanup review

**Date:** November 8, 2026 (UTC). **Parent checkpoint:** F2-A, main commit
`e0b08daf53e75215b734dfba40d34ebbaf8a2d9c`.

## Purpose and implementation

The existing debug RFCOMM TLS 1.3 adapter supports **fixed-width
encrypted protocol controls only** (QR proof, exporter agreement,
commitment agreement, two-person approval and final confirmations). Even
after successful consent it intentionally closes the Bluetooth link.
It is **not** a Bluetooth messaging, voice, or production session.

Before considering any future post-consent text transport, source review
found two narrow issues suitable for a conservative hardening pass:

1. `DebugTlsEngineHandshake.readRecord` admits several TLS record outer
   types/legacy versions that may be needed for an initial handshake.
   After that handshake, `DebugTlsControlTransport` did not separately
   validate that its incoming and outgoing buffers represent **exactly one
   complete TLS 1.3 encrypted record**. F2-B adds
   `DebugTlsRecordBoundary.requireSingleCiphertext` on both sides of
   the existing authenticated `SSLEngine` processing. Only outer type
   23, version `0x0303`, and a 1–16,640-byte body with an exact
   5-byte header and no concatenated record are accepted. RFC 8446
   section 5.2 is the spec for the ciphertext envelope. **This header
   check does not authenticate anything**; the existing pinned TLS
   engine must still successfully unwrap and validate every byte.
2. On errors or interrupted stream reads, temporary plaintext, encrypted
   records and scratch arrays could outlive their useful scope.
   F2-B puts these wipes in `finally` blocks on both control paths,
   the handshake scratch buffer and packet write paths, and partial
   incoming TLS bodies. These are best-effort in-memory hygiene only;
   they do **not** guarantee clearing JVM copies, device RAM or another
   device's data.

## Compatibility and fail-closed boundary

- The existing `DebugTlsEngineHandshake.readRecord` continues to read
  initial handshake records using its current bounded compatibility rules.
  Strict ciphertext-envelope validation is **after the negotiated,
  mutually pinned session**, not on ClientHello/ServerHello.
- The adapter continues to require exactly one complete decrypted control
  per `unwrap`, and exactly one encrypted record per `wrap`. This is a
  deliberately limited *development diagnostic*, not a supported
  general-purpose TLS streaming adapter.
- TLS 1.3 providers may fragment/coalesce data, emit more than one record
  per wrap, or produce KeyUpdate/post-handshake traffic. This prototype
  can reject such behavior; we do not guess, concatenate frames, or
  silently downgrade or claim Android provider compatibility.
- The maximum accepted TLS ciphertext length is the RFC 8446
  `2^14 + 256` bound, distinct from the older broad initial-handshake
  parser limit. A failed check closes the attempt under the existing
  RFCOMM controller and may produce a generic FAILED result.
- No message/control phases were added, no data persistence, no new
  Android permission, no new connection destination, no release capability
  activation, no signing/package migration. All Bluetooth radio work
  remains debug-only and explicitly user-initiated.

## Tests and next steps

New `DebugTlsRecordBoundaryTest` exercises valid single records and
rejects wrong outer type, wrong version, empty/oversized ciphertext,
short buffer, mismatched length and concatenated TLS records.
`test_f2_b_tls_record_boundary.py` checks integration at the actual
read/write call sites, cleanup presence, preserved authentication flow
and disabled release capabilities. Existing M3n-B1/B2/C1 tests remain.

**Required before any future Bluetooth texting:**
- [ ] Green PR Android/Kotlin/Python/permissions/security/source CI
- [ ] Fresh merged-main CI for immutable commit
- [ ] Independent protocol and Android TLS provider review — **not performed**
- [ ] Android 12+ two-phone actual RFCOMM/TLS/QR/consent
      interoperability, failure, background, permission and OS provider
      tests — **owner-deferred**
- [ ] Separate explicit decision about a bounded post-consent
      encrypted debug-message session with cancellation, framing,
      versioning, receipt semantics and independent security review

**Rollback:** revert the F2-B PR. No persistent user data is created
and there is no schema or signature migration.

**References:** RFC 8446 §5.2,
https://www.rfc-editor.org/rfc/rfc8446.html#section-5.2 ;
`docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md`,
`docs/M3N_C1_BLUETOOTH_BILATERAL_CONSENT.md`,
`docs/F2_A_BLUETOOTH_CONSENT_COMPLETION_HARDENING.md`.
