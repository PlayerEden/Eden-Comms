# Eden Comms M2e — Production Session Authorization Boundary

**Date:** 2026-11-04  
**Status:** Design and model-only implementation. Unreviewed; no real messaging, UI consent screen, network authorization, voice, or published security claims.  
**Branch:** `feat/m2e-production-session-policy`, stacked on the v0.2.7 audited two-phone test PR.

## Why this stage matters

`SessionSecurityGate` previously exposed `maySendMessage == true` after plain application-provided session strings and two model approvals. This was never connected to production network I/O, but it was an unsafe *future integration trap*. The legacy gate's `maySendMessage` and `mayTransmitAudio` are now permanently **false**—it remains a model-only state machine, even if feature flags change later. The new `ProductionSessionAuthorization` explicitly separates:

1. **Application policy/sequence:** which peer/session was chosen, whether both approvals for that session occurred, and whether all state is revoked on interruption.
2. **Authenticated evidence:** whether TLS 1.3, the exact peer certificate, freshness and the correct QR/session transcript are actually cryptographically verified. **This model cannot prove those facts.**
3. **Production capability:** separate flags for connection/text/voice, currently ALL `false`. A completed model **does not** grant permission to transmit.

The v0.2.7 debug `LanDemoSession` remains a **different, test-only path**, restricted to predetermined harmless phrases. It must never be reused as an unrestricted messaging transport or used to auto-call authorization callbacks.

## Planned trusted flow

```text
explicit in-person selection
  → fresh two-way QR exchange; local Android Keystore identity
  → open new transport / uniquely identify this connection attempt
  → mutual TLS 1.3 with EXACT expected peer public certificate fingerprint
  → separately reviewed channel + QR transcript binding and freshness checks
  → only trusted adapter reports onAuthenticatedChannel(attempt, peer, transcript)
  → show identity proof and both people independently approve this exact session
  → reviewable authenticated consent packet on SAME live verified channel
  → only trusted adapter reports onAuthenticatedRemoteApproval(attempt, transcript)
  → model APPROVALS_COMPLETE (still no production messages)
  → after security review + real-device QA + explicit release gates:
       future transport adapter may enforce send/receive permissions
```

### No authorization by labels or callbacks alone

An `AttemptId` is for **rejecting late events**, not a secret/auth token. Public 32-byte fingerprints and 32-byte session transcript digests are *inputs*, not proofs. Calling `onAuthenticatedChannel` or `onAuthenticatedRemoteApproval` from UI, QR parsing, discovery results, unsanitized sockets, developer tools, or unverified frames would be a critical integration bug.

Future trusted adapter requirements:
- Exact peer pin must match the fingerprint obtained from the *current* human-verified QR exchange, not a cached label, hostname, IP address, or old contact entry.
- Mutual TLS 1.3 client/server roles must authenticate both certificates and peer private-key possession, with neither plaintext nor weaker TLS fallback.
- Channel proof must bind the live transport, both identities, both fresh QR nonces, roles, agreed protocol version, and a *reviewed* transcript/channel-binding scheme. Do not claim that hashing a public QR alone binds TLS. Android provider API support/secure exporter design is an open decision.
- Remote approval must be freshly authenticated by that peer over that same live verified channel and transcript. A local tap is not evidence of the remote person's acceptance.
- On network/socket change, app background, manual cancellation, peer change, version mismatch, expiry, unauthenticated packet, or any failed check: revoke and close. A new transport gets a NEW attempt; previously confirmed peer trust cannot carry over automatically.
- Send paths must consult capabilities and a live trusted transport binding **immediately before enqueue and before actual write**. Receive paths must not deliver data to chat UI before authorization. Do not unlock history, notifications, attachments or audio by toggling UI state.
- No persisted peer trust by default. A future optional saved verified public identity (max 30 days) needs a separate audited integrity/retention implementation and never replaces proof of the private key at reconnect.

## New code, intentionally unwired

| Component | Purpose | Does not do |
| --- | --- | --- |
| `ProductionSessionAuthorization` | Thread-synchronized per-attempt state machine. Validates public pin length/distinctness, checks expected identity/transcript, requires local + authenticated-remote events, drops stale callbacks, blocks on mismatches, revokes on transport loss or app pause. | Verify certificates, produce TLS transcript, exchange packets, prove human consent or authorize current release data transfer |
| `SessionSecurityGate` | Legacy application-state model. Its text/audio authorization getters always return `false`; `modelApprovalsComplete` is explicitly non-production. | Confer secure message permission based solely on arbitrary strings |
| `ReleaseCapabilities` | Compile-time hard lockdown: `CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE` remain false. The new policy also gates on these switches; it is not connected to a sender. | Identify a remote peer or authorize a session |

No app UI button, socket, test-probe code or network send was wired to the new model. Production messaging is **not yet implemented**.

## Security-review handoff (external review REQUIRED)

An *independent* qualified reviewer must inspect live source and actual Android builds, rather than rely on a self-authored audit. Requested areas:

| Area | Required evidence / negative tests |
| --- | --- |
| Identity/consent | Two physical phones, in-person QR/peer selection, distinct keys, incorrect peer, attempted relay/impersonation, mistimed human confirmation |
| Crypto/network | TLS 1.3 only, exact mutual cert pin validation, no hostname-based mismatch loophole, no trust-all / plaintext/downgrade, provider/version compatibility |
| Transcript/replay | Both roles, both QR nonces and fresh channel bound; replay a prior QR or approval; swap peer/address or session while preserving UI state |
| Authorization | Try send/receive before authentication, after only one approval, with a wrong transcript, after a socket drop, after app pause, on a new connection using an old callback |
| Persistence/privacy | No content in logs/crashes/backup/notifications; no unwanted peer records, WAN contacts or Android permissions |
| Lifecycle | Android 13 A32 5G and a newer phone, same LAN without WAN, Wi-Fi loss, app pause/resume, process death and blocked neighbor |
| Release | Review selected crypto library/provider, certificate/identity rotation, external threat model, license/dependency status, release flags, reproducible artifacts and signed build |

Treat **OWASP MASVS-AUTH, MASVS-CRYPTO, MASVS-NETWORK, MASVS-STORAGE and MASVS-PRIVACY** as review categories, not a claim of certification. Android cautions that `SSLSocket` doesn't automatically verify DNS hostnames; Eden Comms instead plans an explicitly reviewed out-of-band *peer certificate identity* check because local IP addresses aren't stable identity. The distinction requires specialist review.

### References for reviewer
- OWASP MASVS: https://mas.owasp.org/MASVS/
- OWASP MASVS NETWORK controls: https://mas.owasp.org/MASVS/08-MASVS-NETWORK/
- Android TLS / SSLSocket guidance: https://developer.android.com/privacy-and-security/security-ssl
- Project threat model: `docs/SECURITY_AND_QA.md`
- Debug-only QR proof: `docs/M2C_SESSION_BINDING_TEST.md`
- Physical QA script: `docs/M2D_TWO_PHONE_TEST_BUNDLE.md`

## Acceptance gates and next implementation

1. CI compile + JVM tests: no one-sided approval, stale attempt, altered peer fingerprint/transcript, invalid lengths, unapproved reconnect, or legacy gate can grant release send permissions.
2. Confirm production `ReleaseCapabilities` stay false, no new network/permission/peer-history path, and no release/Play submission from this PR.
3. Design and independently review the **actual channel-bound authenticated remote-approval packet/protocol** before writing a production adapter. Do not invent custom cryptographic algorithms or trust codes.
4. Physical two-phone audit for existing v0.2.7 debug transport still pending; no change to that test plan.
5. Only after approved crypto design and physical QA, integrate trusted events with a live scoped transport, implement strictly controlled text, then repeat negative testing. Voice/PTT remains gated separately.

**Security status:** a policy model was developed and statically reviewed by its authoring assistant. This is **not an independent security audit**, protocol proof, penetration test or product certification.
