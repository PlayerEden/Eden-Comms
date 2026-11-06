# Eden Comms M3a — protocol review handoff and non-device evidence

**Prepared:** 2026-11-06  
**Baseline:** `main` merge commit `e1d024b0bf327df7949b7ea27b8aa99f34b7fe66` (M2y, PR #47).  
**Status:** Internal review preparation, **not** a qualified independent assessment, a two-phone PASS, production messaging authorization, or an approval to release.  
**Change scope:** JVM regression tests and review documentation only. No application production source, network permissions, external services, stored user content, release flags, signing configuration, or live message paths changed.

## What an independent reviewer receives

This is a **review target**, not a claim of completed review. The reviewer must evaluate the **end-to-end** security argument rather than bless a single approval function.

| Layer / trust boundary | Current source to inspect | What it proves and does not prove |
| --- | --- | --- |
| Android local identity and mutual TLS | `security/AndroidKeystoreIdentity.kt`, `security/DeviceTlsIdentityKeyManager.kt`, `security/Tls13PeerContext.kt`, `security/VerifiedPeerTrustManager.kt`, `security/PinnedTlsPeer.kt` | Experimental device key material and certificate pin checks; no reviewed peer identity lifecycle |
| Reciprocal QR, roles, challenges | `security/PairingInviteCodec.kt`, `security/SessionProofExchange.kt`, `pairing/LocalPairingQrCodec.kt`, `pairing/LocalPairingTestScreen.kt` | Session proof checks after pinned TLS; QR freshness and human verification require physical and adversarial review |
| RFC 9266 debug channel match | `security/DebugTlsChannelAgreement.kt`, `security/DebugExporterBindingFrame.kt`, `security/TlsExporterReadiness.kt` | Checks a 32-byte exporter value on Android API 31+ where supported; not a key, encryption layer, or human-consent proof |
| Debug approval transcript | `security/DebugApprovalCommitment.kt`, `protocol/TestPacketCodec.kt` | Session-scoped 32-byte digest and strict 35-byte APPROVE/DECLINE/CONFIRM packets; digest is not an independently authenticated MAC |
| Debug control processing | `transport/LanDemoSession.kt`, `transport/DebugApprovalGate.kt`, `transport/SessionSocketRegistry.kt`, `transport/WifiLanEndpoint.kt` | Per-phone four-fact gate for **four fixed phrases only**; no global agreement or unrestricted messaging authority |
| Proposed message/ACK format | `protocol/ProposedChatWireCodec.kt`, `chat/LocalChatPreviewScreen.kt`, `model/ReleaseCapabilities.kt` | Strict codec and local-only UI; **not wired** to socket, production authorization, authenticated ACK or network send |

The review should also inspect `app/src/main/AndroidManifest.xml`, `scripts/check_security_review_boundary.sh`, `docs/PRIVACY_SECURITY_CHARTER.md`, `docs/M2U_BOUNDED_TEXT_WIRE_AND_SECURITY_GATE.md` and `docs/SECURITY_AND_QA.md`. The root of all paths above is `app/src/main/java/com/edenpractical/comms/` unless otherwise noted.

## Observed experimental wire and state behavior

1. Each peer uses mutual pinned TLS 1.3. Both people scan reciprocal QR information and the peers complete a fixed four-record proof with fresh connection challenges. This stage is not consent.
2. The debug-only RFC 9266 exporter comparison follows, then a fixed READY exchange. No arbitrary user text is allowed.
3. Each person explicitly taps Approve. An APPROVE control is exactly `01 05 00` plus a 32-byte current-session commitment; DECLINE is `01 06 00` plus the commitment. Both run inside the existing framed TLS stream.
4. Once this phone has recorded its own successful APPROVE write and observed the peer's APPROVE, it sends exactly one `01 07 00` plus commitment (CONFIRM) on the same socket. The four preset phrases are gated on *this phone's* own approval written, peer approval observed, own confirmation written and peer confirmation observed.
5. The 32-byte commitment derives from domain-separated SHA-256 of canonical QR certificate pins, two QR nonces, two fresh proof challenges and a 32-byte TLS exporter. This is a session identifier, **not a secret or cryptographic signature**. Normal typed text/voice are still off in release and debug.

### Important limitation: asymmetric final receipt

A successful local TLS write is **not** proof that the peer received that control. A split outcome is possible: A has written its CONFIRM and B receives it, then B writes its own CONFIRM but the connection drops before A receives B's. B may locally have all four facts while A has only three. The new `DebugApprovalStateSpaceTest.interruptedFinalDeliveryCanLeaveOneSideConfirmedAndTheOtherNot` deliberately preserves this counterexample. It is **not** a discovered encryption break; it is a reason to avoid equating local confirmation with distributed agreement or a delivered/read message receipt. Another round of flags is not, by itself, a solution.

## Model-based regression evidence (not independent proof)

New test-only `DebugApprovalStateSpaceTest`:
- Enumerates all **55,987** control-event traces through length six (local/peer APPROVE, local/peer CONFIRM, DECLINE and CLOSE) against a separate state oracle. Every prefix must maintain the exact four-fact debug preset gate; duplicates, premature controls and terminal states fail closed.
- Enumerates legal two-endpoint approval/confirmation interleavings under ordered per-direction delivery, checking that neither endpoint unlocks its preset phrases early.
- Reproduces the asymmetry above and checks that close revokes both debug gates.

The code was checked locally with a Kotlin compiler and minimal JUnit-compatible assertion stubs prior to opening this PR. **GitHub Android/JVM CI on this branch remains the authoritative project test result; no physical TLS or Android execution occurred in this local check.** These tests do not verify secure cryptography, kernel sockets, Android UX, remote human identity, network loss timing, replays by a malicious endpoint, or cross-version interoperability.

## Risks to resolve before enabling live user text

| ID | Review question / unresolved risk | Evidence needed |
| --- | --- | --- |
| M3-R1 | Can a phone mistake a QR, key, role, nonce, challenge or exporter from another attempt for this peer/session? | Threat model, independent transcript/implementation review, wrong/stale/self QR and MITM-style negative tests |
| M3-R2 | Is the certificate pinning + Android TLS exporter behavior reliable across supported handsets/providers, including errors, timeout and unsupported API? | Two-phone API/provider matrix, no-WAN tests and fail-closed downgrade cases |
| M3-R3 | Can premature, duplicated, reordered, stale, cross-session or malformed APPROVE/DECLINE/CONFIRM ever authorize traffic? | Independent state/wire review and targeted adversarial tests beyond JVM model |
| M3-R4 | What precise authorization, liveness and user-visible semantics hold when final approval writes, receipts or disconnects race? | Reviewer-approved real-text session state machine and explicit asymmetric-disconnect policy |
| M3-R5 | How will a TEXT message ID be scoped to a single authenticated current session; how will ACK provenance, duplicates and retries be authenticated? | New reviewed live TEXT/ACK adapter and negative tests; no fake read receipts |
| M3-R6 | Do cancellation, backgrounding, Wi-Fi changes, process death and stuck writers promptly revoke all send permissions? | Actual two-phone lifecycle and timeout evidence; independent concurrency review |
| M3-R7 | Can in-place updates retain the correct APK signer and the user's Android Keystore identity? | Dedicated test signer configured outside repository, both phone signer fingerprints and actual update tests |

## Evidence handoff / reviewer output

A qualified independent reviewer should record: (a) name/organization, scope and code **commit SHA** reviewed; (b) assumptions and attacker capabilities; (c) findings with severity, reproduction conditions and affected paths; (d) whether any protocol changes or version bumps are required; (e) specific regression tests and re-review criteria; and (f) final explicit PASS/FAIL/OPEN decision **for the reviewed scope**, not a blanket certification. Do not publish private QR payloads, identity fingerprints, local addresses, secrets or personal chat content as evidence.

For physical acceptance, execute **both A and B columns** of `docs/M2K_BATCH_PHONE_QA_WORKSHEET.md` (T01–T36) using the *same* identified signed build and attach only sanitized per-phone summaries to issue [#26](https://github.com/PlayerEden/Eden-Comms/issues/26). Signing/version continuity is separately tracked in [#27](https://github.com/PlayerEden/Eden-Comms/issues/27).

## Exit conditions for real TEXT integration

- [ ] Independent review addresses M3-R1 through M3-R6 with any high/critical issues fixed and rechecked.
- [ ] Two real phones complete required positive/negative no-WAN QR/TLS/debug-control tests; record independent results from both.
- [ ] Stable test APK signer and safe in-place Android update are demonstrated; app identity survives.
- [ ] Separate, reviewed authorization and per-session TEXT/ACK design specifies disconnect disagreement, bounds, replay, IDs, provenance, cancel and erase behavior.
- [ ] Only a **new, explicitly gated implementation PR** may connect normal text to the live session; it must retain debug/release restrictions until its own security and device acceptance.

**Do not close issue [#42](https://github.com/PlayerEden/Eden-Comms/issues/42) for this handoff.** It tracks the first genuine peer-to-peer text conversation, which remains unimplemented.
