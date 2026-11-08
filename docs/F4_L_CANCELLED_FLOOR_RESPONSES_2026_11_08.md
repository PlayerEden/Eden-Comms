# F4-L — rapid canceled Talk requests must not break the LAN session

**November 8, 2026 · debug-only LAN radio reliability · source/CI checkpoint**

## Observed source-level risk

F4-F/F4-H introduced the REQUEST → host GRANT/DENY → CANCEL/RELEASE
half-duplex radio floor. The joiner previously remembered only **one**
canceled request ID. An ordinary user can hold and release Talk several
times quickly while encrypted replies are still in transit.

For example: the joiner requests #1 and cancels, requests #2 and cancels,
then requests #3. On a slower phone, the host's earlier GRANT #1 may arrive
after #2 is already canceled. The old joiner state remembered only #2,
so it treated the legitimate old GRANT #1 as a protocol violation and
closed the entire chat.

That is a reliability failure, not evidence of a malicious peer.

## F4-L repair

- Each approved LAN TLS session keeps a **bounded set** of canceled joiner
  request IDs, not just the most recent one. At most 32 IDs are retained
  in volatile memory. No audio, identities or keys are recorded.
- A response matching one of these canceled IDs is ignored **exactly
  once**, never starts an Android microphone, and removes its ID.
  Replays/unsolicited grants still fail closed.
- If more than 32 unanswered cancellations accumulate, the oldest IDs
  are discarded; an extraordinarily delayed reply for a discarded ID
  still fails closed. This prevents unbounded memory use and is an
  explicit conservative limit, not a silent extension of trust.
- A new pending/current request still requires its **own exact**
  matching GRANT. Existing host-side monotonically increasing IDs,
  same one approved pinned TLS socket, bounded control queue, hard
  floor deadlines and disconnect cleanup remain unchanged.
- Close wipes the outstanding abandoned-ID set. No new permission,
  background service, transport, retry loop, production capability,
  APK package, signer, or wire protocol.

## Automated verification

- JVM simulated two-phone floor transitions: cancel three requests
  before any responses arrive, receive their late grants, then accept
  only the exact current request's grant.
- Old DENY responses during a busy host talk are similarly ignored.
- Duplicate old replies are rejected as replay attempts.
- After more than 32 abandoned requests, older unknown replies fail
  closed while a retained latest canceled response may be consumed.
- Python source checks cover the bounded set, correct UI cancel flow,
  and release permission/capability boundaries.

## Physical QA still required

1. On the same Wi-Fi and after new reciprocal QR, both approvals and
   confirmations, let the joining phone rapidly press/release Talk
   a few times (or briefly cancel while WAITING FOR RADIO CHANNEL).
2. Repeat while the presenter is talking and on a slow/busy phone.
   The joiner must not suddenly disconnect just because an earlier
   canceled GRANT/DENY arrived; it must never start a canceled mic.
3. After the canceled requests settle, make a fresh successful
   transmission in each direction and confirm intelligible audio.
4. Try Wi-Fi loss, backgrounding and session end during pending
   requests. Never silently reconnect or reuse an old grant.
5. Keep sanitized results only: build number, phone model/Android version,
   whether mic started, pass/fail, and approximate delay. No QR
   contents, IP/MAC addresses, speech recordings or secrets.

**Status:** a source/build pass is not proof of two-phone behavior,
audible voice, or security. The latest previously signed test checkpoint
is Preview 200005; this source-only fix is not in that APK. Keep using
harmless test speech and do not rely on this for emergency communication.

**Rollback:** revert the F4-L PR. No saved-state or signing migration.
