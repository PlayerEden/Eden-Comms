# F2-K — observe real verified Bluetooth connection steps

**November 8, 2026. Debug-only; no Bluetooth messaging or independent security certification.**

The F2-J guided QR-first Bluetooth screen is easier to use, but the old
diagnostic exposed one generic `RFCOMM link / TLS probe failed` result.
For a real two-phone attempt this obscured *where* TLS/QR/approval stopped.
F2-K now adds **sanitised, current-attempt progress from the ACTUAL Android
Bluetooth TLS worker**, rather than introducing another independent lab.

## Exactly what is observable

The same real `DebugRfcommTlsProbe.run()` that runs on the existing RFCOMM
socket emits an event ONLY AFTER each operation has completed successfully:

1. Exact pinned mutual TLS 1.3 certificate and provider post-handshake checks.
2. Bilateral fresh QR nonce/challenge exchange over that TLS socket.
3. Both sides exchange and compare the same-channel TLS exporter.
4. Both sides compare the domain-separated encrypted commitment.
5. Both human approvals and both encrypted final CONFIRM records have been
   checked AND the QR binding is still fresh.

Each milestone is a **fixed generic label** (e.g. `Verified test step 3/5`),
not an address, identity, QR, key or transcript. The debug Activity shows
the latest verified step near the actual link status. A monotonic guard in
the real RFCOMM controller accepts callbacks only from the current attempt
and only while the socket is live, foreground and still permitted. The
previous attempt's display resets at the beginning of a new link. The
terminal screen retains only that attempt's final coarse stage to clarify
failure or confirmed end; no event history or persisted telemetry.

The old **complete-and-close diagnostic remains unchanged**: no transfer of
socket/SSLEngine ownership to UI or send APIs, no TEXT/ACK, no voice, no
automatic peer trust, no background operation, no new permissions or signing.
A milestone does NOT confer permission to communicate. Neither a shown
stage nor CI tests prove the other phone was physically verified: two-phone
Android/Conscrypt testing and an independent protocol/security review are
still required before retaining the same engine for actual messaging.

## Simple next two-phone QA

With two Android 12+ phones and Bluetooth, not WLAN/cellular:

1. Launch **Developer Tools → Bluetooth discovery test** on both.
2. Exchange both in-person QR codes using the guided F2-J setup; on the
   host, start the one-time link; on the joiner, search, stage the target
   signal and tap Join. Follow system permission/discoverability prompts.
3. On each phone, observe the last generic **Verified test step N/5**
   shown alongside status. Both must independently choose approval. A
   normal approved run eventually closes and reports successful bilateral
   confirmation. It does NOT open a Bluetooth chat.
4. If one side fails, record only its generic step number and terminal
   description; retry with new QR codes. Check decline, expired/wrong QR,
   radio off, screen background, permission revoked, and timeout.
5. Never share raw QR contents, MAC addresses, device keys, TLS exporter or
   any sensitive text. No private/emergency communication in debug Preview.

## Remaining core work

- Independent review and documented two-phone provider evidence for the
  existing TLS/QR/exporter/approval and same-socket lifetime design.
- Separately implement **single-owner live same TLS engine and socket**
  handoff after verified bilateral final confirmations, with bounded
  foreground lifetime and fail-closed termination; no fake reauth from a
  printed progress label.
- Only then add strict live encrypted Bluetooth TEXT/peer-app ACK and reuse
  the actual conversation UI; next Wi-Fi Direct and remote PTT follow.

**Verification:** Kotlin checks for static safe labels, Python actual-worker
call-order and token/terminal source gates, full Android/Gradle, privacy,
permissions and APK evidence in CI. These are NOT device QA.

**Rollback:** revert the F2-K PR. No wire-format, key, persisted data,
signer, Android permission or release-mode change.
