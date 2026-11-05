# Eden Comms — M2n ephemeral debug transcript and event admission

**Code checkpoint:** November 5, 2026  
**Hardware QA:** NOT TESTED (both-phone gate #26 still open)  
**Real encrypted text/voice:** DISABLED; protocol independently reviewed: NO

## Problem and scope

The debug fixed-phrase screen previously maintained a mutable list of rendered phrase text and two independent counts. Socket reader/writer events are posted to the UI handler, and their delivery order can race with disconnect, lifecycle stop or a new attempt. The existing version guard blocks stale *attempts* but the transcript's own pre-READY, post-END and repeated-READY behavior was not encapsulated as a testable lifecycle policy.

This milestone **does not implement a future text messenger**. It replaces those debug UI structures with one immutable, in-RAM ledger for only the four existing harmless test phrase IDs.

## Implemented

- `DebugSessionTranscript` has phases **WAITING → ACTIVE → FINISHED**. Only an actual debug READY callback activates it. Only recognized IDs 1–4 can be recorded as either **WRITTEN_TO_TLS** (local sender-thread write completed) or **RECEIVED_HERE** (this app decoded a peer packet).
- A maximum of **eight** recent phrase IDs and directional labels are visible. Counts remain independent and are cumulative **local event counts**, not remote delivery or read receipts.
- `finish()` clears the visible entries, preserves only local counters + whether READY occurred for the optional sanitized QA report, and refuses any new entries or another READY. `fresh()` starts the next attempt with zero state. Compose uses `remember`, **never** `rememberSaveable`; no database, file, message-body clipboard, backup, analytics or background service.
- The Compose pairing screen now consumes the one ledger to render activity and the result/copy-report counts. There is no second counter or mutable phrase text array to diverge.
- `DebugSessionEventAdmission` checks attempt generation and visible stage before any callback can record activity or accept READY. In particular, a READY posted late **after ENDED** can no longer reopen the conversation UI. The existing `LanDemoSession` mutual TLS, QR proof, fixed READY packet, transport and debug-only init guard are unchanged.

## Automated tests

| Test | Expected behavior |
| --- | --- |
| SENT / RECEIVED before READY | Ignore; show no activity or counts |
| Only the four valid IDs | Unknown/malformed IDs never render |
| Multiple READY events | Cannot reset or resurrect a transcript |
| 25 alternating events | Keep final eight in direction order, accurate 13/12 event totals |
| END after active; duplicate END | Clear activity; preserve local counts; reject new events |
| END before READY; delayed READY | Never become an active test |
| Callback from previous generation | Cannot be applied to new attempt |
| Previous immutable snapshot | Remains unchanged by new events |
| All 256 possible phrase ID bytes | Reject every ID except 1–4 |
| All 255 nonzero bytes in READY/HEARTBEAT/CLOSE | Reject hidden control payload |

## Boundaries and future session-only text

This small model is a **safe UI/lifecycle precedent**, not general chat infrastructure. It does not accept arbitrary strings, timestamps, identities, read receipts, message IDs or encryption keys, and cannot send network packets or enable a released feature. General session-only text needs **new design and separate independent protocol review** (mutual authorization, message framing, replay limits and end-to-end security), then fresh two-phone QA. Do not reuse the test-only packet codec as a production text protocol.

## Device acceptance deferred to consolidated run

On two real phones verify: authentic reciprocal QR+TLS READY, visible ordered preset bubbles, local write and remote receipt counters, both-side disconnect, 32s silence, app background, cancel, old QR, changed Wi-Fi, and no WAN/cellular. Confirm no activity survives End, Back, reset, navigation or process recreation. Snapshot and signed APK update preservation are independent tests. Use [M2k batch worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md).

**No changes:** `ReleaseCapabilities`, Android package ID, permissions, TLS/QR packet formats, network APIs, signing pipeline, data retention settings.
