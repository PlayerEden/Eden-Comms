# M2h — Candidate approval transcript & bilateral-decision simulator

**Created:** 2026-11-04  
**Implementation status:** **JVM test sources only. Not an operational protocol; not in any APK.**  
**Depends on:** M2f review-only `ECAP` packet proposal, M2e unwired production authorization policy, M2g on-device TLS exporter **availability** diagnostic. **No physical two-phone exporter evidence exists yet.**

## Why this is deliberately test-only

The security-sensitive gap is not a button: both devices must unambiguously agree on **which peer**, **which newly authenticated TLS session**, **which QR session** and **which explicit human decisions** were involved. A QR digest, a received packet or a successful standard exporter call alone does not prove that an authenticated peer expressly accepted the session. The current app cannot responsibly authorize arbitrary messages just because it compiles.

This milestone makes the proposed contract inspectable and attack-testable **without shipping it**. Independent protocol review must approve the exact byte encoding, replay/expiry rules, sender authentication and UX before a trusted adapter can wire anything into a live socket.

## Test-only candidate transcript (NOT a standard)

`ReviewOnlyChannelCommitment.candidateSha256` accepts:
1. Presenter full SHA-256 certificate pin (32B), joiner full pin (32B), **fixed presenter-first order**.
2. Presenter QR nonce (16B), joiner QR nonce (16B).
3. Presenter proof challenge (16B), joiner proof challenge (16B).
4. A **hypothetical, already authenticated RFC 9266 `tls-exporter` value** (32B) taken from the *same live, mutually pinned TLS 1.3 socket* in an eventual implementation.

All inputs are **test fixture bytes** today. The M2g compatibility probe deliberately does **not** expose its exporter bytes to this code, and the current `SessionProofExchange` challenge values are not surfaced to it. The test fixture is not evidence that both devices calculated matching values.

Candidate canonical byte stream is:
- 1 byte length + 21 ASCII bytes `EdenComms-Approval-v1`;
- 1 byte format version `01`, presenter role `01`, joiner role `02`;
- 7 ordered fields, each with **1-byte length + bytes**, exact sizes 32, 32, 16, 16, 16, 16, 32;
- `SHA-256` of this exact byte stream, intended as a *proposal* for the 32B `ECAP` commitment.

**Golden fixture**: pins = `11` × 32, `22` × 32; QR nonces = `33` × 16, `44` × 16; challenges = `55` × 16, `66` × 16; synthetic exporter = `77` × 32. Canonical buffer length **192 bytes**. Draft SHA-256 = `626d383808ad60d20ca4c23d3070d6e502fd287793f10b0c2fa1cd9bfce59934`. This is an independently computed **test vector**, not a cryptographic protocol endorsement. Avoid ever logging actual live exporter bytes, QR nonces or temporary private authentication information.

**Required reviewer decision:** version/domain encoding, exact field lengths, validation of zero/duplicate/random challenge values, session resumption, retransmission and transcript agreement must be reviewed; SHA-256 is an existing primitive, but our *application protocol design* is not standardized by it.

## Test-only decision simulator

`ReviewOnlyBilateralApproval` accepts explicit local decision and a *hypothetical authenticated* remote `ReviewOnlyApprovalFrame.Packet`, with the expected role and 32B commitment:

- Neither local-only nor remote-only APPROVE completes the **simulated** state.
- Either person DECLINE → DENIED. A wrong role or commitment → DENIED.
- Duplicated local or remote decision → DENIED. Extra frames after simulated agreement → DENIED; test-only state is **never a transport capability**.
- Cancel/disconnect revoke immediately. Deadline expiry → DENIED. Events from an old attempt are ignored after a new one begins.
- A caller mutation of an input buffer cannot rewrite the stored commitment.
- The simulator never connects to Android, sockets, app UI, existing `ProductionSessionAuthorization`, release flags, or external libraries. The method name `simulatePeerFrame` explicitly warns: **this does not establish remote sender authentication**.

### Attack and failure coverage in JVM tests

| Attack/edge case | Expected review-only behavior |
| --- | --- |
| Only one human agrees | No simulated agreement |
| Peer APPROVE before local tap | Still pending; local must approve |
| Either human DECLINE | Denied |
| Wrong peer role | Denied |
| Changed QR nonce or proof challenge | Different candidate commitment |
| Changed/absent exporter | Different commitment or error |
| Wrong/stale commitment | Denied |
| Duplicate or late packet | Denied/ignored based on attempt |
| Disconnect or Cancel after simulated agreement | Revoked |
| Deadline elapses | Denied |
| Old callback after reconnect | Ignored; cannot authorize new attempt |
| Malformed frames | Rejected by existing `ReviewOnlyApprovalFrame` strict 39-byte parser |

Do **not** confuse those model tests with a malicious-LAN penetration test. This milestone does not prove MITM protection, transcript agreement, identity verification, reliable delivery, user awareness, socket lifecycle correctness, or absence of bugs on actual Android devices.

## Independent-review handoff before live implementation

1. **Protocol and canonical transcript:** reviewer specifies and signs off on domain/fields/canonical byte layout, challenge handling, session freshness, role binding, test vectors and replay defenses.
2. **Android TLS provider behavior:** test real API 31+ devices and versions, lifecycle, exporter exact bytes from both sides if safe under controlled test conditions, and refusal on unsupported API/providers. Ensure exporter from same *already pinned/verified* socket.
3. **Authenticated approval reader:** only accepts `ECAP` over the currently verified channel and only after exact peer identity + QR proof. No untrusted UI/discovery/input may call the production policy callbacks.
4. **Explicit user decisions:** design understandable confirm/decline UX, especially whether the existing two QR scans amount to informed consent; fail closed with a clear cancel option and deadline.
5. **Disconnect/race conditions:** stopped/suspended UI, timeout, repeated approvals, simultaneous network loss, wrong role, denial and stale packets. Nobody may infer that both sides know all acknowledgments arrived after arbitrary packet loss.
6. **Production integration audit:** release feature flags stay false until independent security review and physical two-phone hostile-LAN QA. Only then consider a separately gated, minimum free-text prototype; PTT stays later.

## Security and CI enforcement

- New files **only under `app/src/test/java`**:
  - `ReviewOnlyChannelCommitment.kt`
  - `ReviewOnlyBilateralApproval.kt`
  - `ReviewOnlyApprovalNegotiationTest.kt`
- `scripts/check_security_review_boundary.sh` explicitly rejects any reference to these classes from main app Kotlin code, and insists they remain test-only.
- No network packet or client API changes; no additional Android permissions, data collection, chat history, Bluetooth, backend, or crypto library. All production `ReleaseCapabilities` unchanged and hard-false.
- APK checksums/CI should match the **final PR head**. This milestone itself does not justify requesting a physical phone test.

## External references

- IETF RFC 9266 channel-binding type: https://www.rfc-editor.org/rfc/rfc9266.html
- Android `SSLSockets.exportKeyingMaterial` (API 31+): https://developer.android.com/reference/android/net/ssl/SSLSockets
- Project prior design: `docs/M2F_AUTHENTICATED_APPROVAL_PROPOSAL.md`
- Physical test script: `docs/M2D_TWO_PHONE_TEST_BUNDLE.md`

**Do not interpret "SIMULATED_AGREEMENT" as proof of mutual consent.** A protocol reviewer must scrutinize the design before a trusted live adapter exists.
