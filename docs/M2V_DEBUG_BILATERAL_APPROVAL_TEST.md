# Eden Comms M2v — live two-person approval experiment for fixed-phrase debug LAN

**Date:** 2026-11-05  
**Scope:** Actual code path in the debuggable Android LAN QR/TLS **test only**, not a released secure messenger.  
**JVM/Android CI:** pending at PR creation.  
**Two actual phones:** NOT TESTED.  
**Independent cryptographic/protocol review:** NOT PERFORMED.  
**User-written text / voice / saved peer trust:** DISABLED.

## Product improvement

The existing development test could authenticate its pinned TLS/QR link and exchange four fixed phrases immediately, without either person explicitly approving that current connection. This milestone adds a native per-phone **Approve this debug test** / **Decline and disconnect** screen *after* the existing mutual pinned TLS 1.3, two reciprocal QR and READY checks, but *before* any preset phrase exchange.

Both phones now must separately tap **Approve**. After the local APPROVE control is written to TLS **and** the other phone's APPROVE control is decoded from the verified debug socket, that phone transitions to the existing four-phrase test. A remote decline ends the test; local decline sends best-effort DECLINE then closes. Waiting longer than the ~90-second experimental approval limit ends the session. Network/background/cancel failures still close and discard temporary session state.

**Important distinction:** The UI truthfully says **“Both debug approvals observed by this phone”**, not that both devices universally agree or that the protocol is production authorized. An application cannot guarantee the other side received its final control after arbitrary packet loss. No named peer identity or new short-code check is implemented here. The earlier QR exchanges are still the only means of selecting the peer.

## Debug control format only

To `TestPacketCodec` version 1's existing **exact three-byte** `[version, type, phrase-id]` envelope, add:

| Type | Fixed payload | Meaning in this experimental session |
| --- | --- | --- |
| 5 | `01 05 00` | Local human tapped Approve and the packet was written over the debug TLS socket |
| 6 | `01 06 00` | Local human tapped Decline; best-effort notice, close immediately afterward |

Unknown types and nonzero payload bytes on APPROVE/DECLINE are rejected. Existing READY (1), PRESET (2, only IDs 1–4), HEARTBEAT (3), and CLOSE (4) formats do not change. The new types are **only read after** the current pinned TLS, QR proof and READY sequence; duplicate or out-of-order control is rejected with connection teardown. An unsolicited PRESET before this phone observes both approvals fails closed.

**Protocol limitation:** These 3-byte controls do **not** contain a versioned RFC 9266 exporter-bound commitment, explicit QR transcript hash, final acknowledgment, or sender-specific application signature. The transport is pinned TLS but this simple debug control exchange is **not an independently reviewed production bilateral authorization protocol**. Do not wire it to `ProductionSessionAuthorization` or use it to enable real text.

## Actual code and state transitions

- `DebugApprovalGate` is in-memory, per `LanDemoSession`, never persisted or reused on reconnect. States: WAITING; LOCAL_ONLY (this phone's TLS write completed); PEER_ONLY (peer APPROVE observed here); BOTH_OBSERVED; DECLINED; INVALID; CLOSED.
- The gate permits **only the fixed four synthetic phrases** when BOTH_OBSERVED. It is consulted both before enqueueing a phrase and immediately before the writer's actual send. The receive path independently rejects premature PRESET controls.
- `approveForTest()` is triggered exclusively by an explicit UI tap. Local approval enters the gate **after TLS write succeeds**, never merely when UI submits work. A bounded one-at-a-time write flag rejects double taps. The peer's control is observed only by the existing authenticated debug reader.
- `declineForTest()` prevents further sends and attempts to write DECLINE, with the existing independent ~1.2-second forced socket-close fallback; the UI immediately closes the local test.
- `LanDemoStatus` now has safe end reasons for PEER_DECLINED and APPROVAL_TIMED_OUT; no QR, IP, certificate, raw exception or message text is shown or copied.
- `LocalPairingTestScreen` has a separate approval stage with two ≥52dp actions, separate local vs peer observation wording, refusal to imply independent security certification, and a return to the existing debug phrase screen **only when both observations are present**. Old socket callbacks remain rejected by attempt generation.
- One-phone opt-in QA report now separately records **pinned TLS/QR test READY**, **local approval written**, **peer approval observed**, and **phrase-stage READY**, rather than conflating local security checks with consent.

## Automated acceptance

- Unit tests on `DebugApprovalGate`: one-sided tap cannot activate presets, either ordering, duplicate/late/rejected controls terminate, decline revokes, close irreversible, no release flags change.
- `TestPacketCodecTest` verifies `01 05 00` and `01 06 00` fixtures, strict 3-byte decode, all 255 invalid nonzero control payloads for both types, no arbitrary payload acceptance.
- `DebugApprovalControlWireTest` executes two simulated endpoints through the *real* codec and actual bounded `FrameCodec` in memory, demonstrating that four fixed phrases remain gated until both control observations. This tests byte order and state transitions **only**, not TLS, Android networking or human identity.
- UI copy/QA tests distinguish a verified TLS/QR test link from an incomplete approval and keep observation labels sanitized. `scripts/check_security_review_boundary.sh` forbids this debug gate outside the guarded test transport/UI, and continues to prohibit arbitrary `ProposedChatWireCodec` use and `ProductionSessionAuthorization` integration.
- All production capabilities (`CAN_CONNECT`, `CAN_SEND_MESSAGES`, `CAN_TRANSMIT_VOICE`) remain false; ordinary release builds still cannot create a `LanDemoSession`.

## Later two-phone physical QA — all UNTESTED

After reviewing [M2k consolidated worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md), use two separate actual debug-compatible signed installs to check:

1. Both phones scan fresh reciprocal QR codes, pass TLS/QR, and **stop at the approval screen**. No preset phrases available to either phone.
2. A taps Approve, B does nothing for 10 seconds: A reports local write only; B reports peer observation only; no PRESET sent or accepted.
3. B taps Approve: both eventually show the fixed-phrase screen; messages A→B and B→A appear only now. Compare per-phone event counts.
4. B declines, before or after A approves: B closes immediately; A sees PEER_DECLINED if the control arrives, otherwise safe link loss. Neither sends phrases.
5. Leave either phone waiting past the 90-second limit (allow heartbeat scheduler tolerance); both should stop, no unexpected background connection.
6. Force duplicate/early/invalid APPROVE if an audited test harness is later built; new code must fail closed. Do not manually inject raw packets into sensitive environments.
7. Background/cancel at pending approval, switch Wi-Fi, reopen and pair fresh; stale controls cannot approve a new session.
8. Inspect portrait/landscape, narrow phone, 200% font, TalkBack/keyboard and large-font wrapping for the consent screen.
9. Verify the old pre-M2v APK fails safely with new controls/version mismatch and **does not silently send presets**; treat compatibility as needing explicit handling.

Record **two independent phone reports**, APK commit, signer, Android API versions and safe categories; do not copy sensitive QRs or identities. Successful JVM builds are not real-phone evidence. Signing continuity [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) remains outstanding.

## Real product / next critical security gates

The real production conversation milestone remains [core M3 issue #42](https://github.com/PlayerEden/Eden-Comms/issues/42). This experimental approval exchange **does not meet** the previously specified [M2f authenticated approval proposal](M2F_AUTHENTICATED_APPROVAL_PROPOSAL.md): an independent reviewer must still finalize a commitment spanning actual TLS exporter, both fingerprints, fresh QR roles/nonces and challenges, explicitly bound approvals, replay/downgrade protection, and live app-scoped authorization. Only then may that reviewed design interact with `ProductionSessionAuthorization`, a live send/receive pipeline and the isolated bounded TEXT/ACK codec from M2u. User-typed messages and voice remain disabled until reviewed integration and separate physical tests.

**No new:** Android permissions, Bluetooth/Wi-Fi Direct, account/servers, storage of peers or chat messages, public status claims, new encryption library, mic/PTT or Google Play publication.
