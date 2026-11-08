# F4-M — integrated virtual two-phone radio regression

**November 8, 2026 · test-only source milestone · NOT two physical phones**

## Why build this lab?

F4-F through F4-L each passed separate tests for radio-floor arbitration,
responsive control scheduling, cancellation and cleanup. Those tests did
not exercise a *single combined story* in which both radios exchange
actual encoded packet envelopes, switch who owns the floor, lose Wi-Fi,
and handle repeated canceled requests. F4-M adds that integration
regression before another signed Preview build or hardware QA.

## What the virtual test actually exercises

`DebugLanVirtualTwoPhoneRegressionTest` uses two independent in-memory
`DebugLanVoiceFloor` owners (presenter/host and joining phone),
`TestPacketCodec.write/read` over a `ByteArrayOutputStream`, the
real `DebugLanVoiceWireCodec` 20 ms/640-byte PCM frames,
`DebugLanLiveVoiceQueue`, `DebugLanVoiceAssembler`,
`DebugLanFloorControlQueue`, `DebugTapVoiceSendGate` and
`DebugSessionAudioOwner`. Delays and disconnects are injected
deterministically by the test, not inferred from timers or simulated
UI screenshots.

The executable tests cover:

1. Host reserves radio; joining phone makes five quick requests and
   cancels them while the host is busy. Delayed Deny replies arrive in
   reverse order, cannot activate a microphone, and duplicate replies
   still fail closed.
2. One host PCM frame is encoded, sent and decoded while the local
   capture queue is **not finished**. An exact END follows. Host
   relinquishes control; joiner then obtains an exact correlated GRANT,
   transmits a complete new turn and sends RELEASE after END. Host can
   immediately reserve a subsequent turn.
3. Multiple delayed GRANT replies to canceled requests are ignored.
   A canceled accessible-tap send cannot enqueue audio, discarded
   unsent PCM is not replayed, and the current request still requires
   its own separate grant.
4. A DENY is processed on the bounded control executor while a
   different PCM executor remains deliberately blocked.
5. A link is torn down mid-turn with another request pending.
   Both floors close, incomplete audio cannot be finalized, a late
   Deny and late END cannot revive the old connection, and an old
   audio-stop callback cannot stop a newly activated session.
6. A malformed PCM sequence fails closed. Missing grants and
   unreleased leases require whole-session teardown instead of
   silently transferring radio-floor ownership.

## Limits of this evidence

The test uses the **actual framed packet format**, but the underlying
transport is an in-memory byte array — NOT mutual TLS, a Wi-Fi link,
Android audio devices or two handsets. It does not claim security
review, intelligible sound, realistic delay, Android lifecycle timing
or independence from OEM microphone/speaker implementation.

The separate real LanDemoSession still owns pinned mutual TLS 1.3,
fresh reciprocal QR proof, both parties' approvals and final
confirmations. Its handlers are source-guarded against drift but the
full Android/Conscrypt socket is **not** exercised by this test.

No new runtime feature, audio encoding, queue allocation, app
permission, signing identity, cloud account, data storage or production
capability is introduced. Preview **200005** remains the latest
previously signed handset baseline; this milestone changes tests and
documentation only.

## Next QA gate

- Android/JVM tests and Python security/boundary checks must pass both
  PR and post-merge main workflows.
- On two physical, freshly paired and mutually approved phones: verify
  both audio directions, busy talk collisions, rapid canceled requests,
  3-second cutoff, tap accessibility, background/lock, Wi-Fi loss and
  fresh QR reconnect. No automatic resume or stale voice grant.
- Keep sanitized pass/fail and handset model/Android build details;
  never include QR codes, peer IPs, keys or actual sensitive speech.
- If hardware results show a regression, open a focused fix PR.
  Do not interpret a green test as proof of a production-secure radio.

**Rollback:** revert the F4-M test/docs PR; no migration or APK
installation change is required.
