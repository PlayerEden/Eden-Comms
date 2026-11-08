# F4-F — host-arbitrated floor before any LAN microphone capture

**November 8, 2026 — experimental Android source/CI milestone, not two-device acceptance.**

F4-D streams 20 ms mono PCM through the existing two-QR, mutually pinned
TLS 1.3, exporter-proved, *both-human-approved* Wi-Fi test session. F4-E
observed START/END but could not prevent simultaneous attempts. F4-F adds
a shared floor protocol on **that same confirmed encrypted socket**.

## User-visible behavior

- **Hold to talk:** the presenter/host may reserve an idle floor locally.
  The joining phone sends REQUEST and waits for a correlated GRANT. **No
  AudioRecord starts and no PCM leaves the phone while waiting.**
- **Tap controls instead (accessibility):** uses the SAME floor request and
  grant before it starts its legacy 3-second record-then-send fallback. It
  cannot bypass the floor because the session rejects sendVoiceBurst without
  an owned grant.
- Both pressing at once: the host serializes decisions. If it reserved the
  floor first, it returns DENY to the joiner. If a joiner's REQUEST won,
  the host cannot begin its own capture until the joiner's release.
- Releasing while still waiting sends CANCEL; a GRANT already in flight is
  ignored rather than starting the microphone unexpectedly.
- After the last validated PCM/END, the same single TLS writer releases the
  local floor. The remote host does not free the channel until the matching
  RELEASE is received after a complete audio turn. A missing reply, stuck
  grant or unexpected control closes the **entire session** on timeout; it
  never silently reassigns a possibly still active floor.

## Wire and trust boundaries

The strict `ECFL` control envelope is 16 bytes: 4-byte magic, one-byte
version, kind, zero reserved, and positive 64-bit request ID. Allowed
`REQUEST / GRANT / DENY / CANCEL / RELEASE` controls are role-checked with
monotonic sequence/replay rejection. An ECFL frame is **not a credential**:
it is only interpreted on the already authenticated, committed, approved
debug TLS session. Host arbiter and joiner state exist only in RAM and reset
on each fresh connection. Existing strict `ECVP` voice turn START/DATA/END
and 20 ms PCM ordering continue unchanged. No new background service,
Bluetooth/Wi-Fi Direct voice, server, account, pairing key or local storage.

Pending grants close the session after >3 seconds. Owned floors close after
>9 seconds (allowing the 3-second record plus up-to-3-second paced tap
fallback, transmission scheduling and teardown). The three-second PCM cap,
12-frame live sender/receiver queues and no saved recordings still apply.
Local screen hide, disconnect, background and microphone denial stop
capture; only release debug manifest requests RECORD_AUDIO.
`ReleaseCapabilities` stays false for all production communications.

## Automated acceptance and limitations

- Strict floor codec round trips through the actual test packet framing,
  version/length/reserved/role/id rejection.
- JVM virtual host/join tests: exact REQUEST→GRANT→RELEASE, simultaneous
  host win→DENY, canceled stale GRANT ignored, replay/wrong-role refusal,
  early RELEASE rejection, hard session expiry.
- Android compile and unit tests, Python UI/session wiring checks and
  debug/release capability + permission guards.
- This code path has **not** yet been verified between two actual Android
  devices, measured for microphone-onset-to-speaker latency, or externally
  reviewed for protocol/cryptographic safety. It is not an emergency radio.

## Two-phone QA — still pending

Install a **new Preview APK** built from this commit on both phones. Preview
200003 predates live streaming/F4-E/F4-F. On the same Wi-Fi, complete fresh
two-QR handshake, both independent approvals and final confirmations.

1. Phone A hold → B hears audio **while A is holding**; then B → A.
   Observe waiting/grant status and actual first audible speech.
2. Both press at once repeatedly (including host a fraction of a second
   later): only one microphone should send, and the loser can retry when
   floor is released. Confirm no ghost audio.
3. Joiner press and immediate release **before GRANT**: microphone never
   turns on or accidentally transmits. Repeat while a grant is in flight.
4. Accessible tap controls on *both* phones: same floor requirement,
   3-second record-then-send, ability to cancel a pending request.
5. Denied microphone, tiny hold, 3-second automatic cutoff, blocked speaker,
   interrupting Wi-Fi, screen lock/background, Hide, End chat, simultaneous
   typed text and peer ACK; no stuck TX/RX or floor after disconnect.
6. WAN unplugged, same local router: ensure neither path uses cellular,
   cloud or remote service. Reconnect requires fresh QR + both approvals.

Record only build revision, pass/fail, rough latency and sanitized event counts.
Never upload raw speech, QR contents, keys, private IP/MAC or message text.

**Rollback:** revert the F4-F PR; no signer, APK package or data migration.
**Next:** fix real-device race/audio routing findings; review security and
consider a compact codec only if physical measurements justify it.
