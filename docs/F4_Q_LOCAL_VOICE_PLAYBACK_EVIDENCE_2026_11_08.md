# F4-Q — truthful local speaker evidence in two-phone QA

**November 8, 2026 · debug-only LAN voice · source/CI implementation, not field acceptance**

## What was missing

An authenticated, approved LAN peer can send a valid encrypted START,
multiple 20 ms PCM packets and END. The protocol layer can verify all
those bytes, even if the receiving Android phone's actual speaker rejects
a frame due to output-device unavailability or the local 240 ms queue
being full. Before F4-Q, the UI displayed a playback-busy warning for
rejected START only: a rejected DATA packet could be silently dropped
while the chat still showed a correctly completed incoming voice turn.

A valid network voice turn **does not** establish that sound played here,
let alone that the human on the other end heard anything.

## Changes

- Each authenticated incoming START creates a new, volatile
  `DebugVoicePlayoutTurn` for that active LAN test attempt.
- Android's existing AudioTrack start callback counts as a **local speaker
  start observation**, not proof of audible sound.
- If START or any DATA is rejected by `DebugLanVoiceAudio.acceptLiveFrame`,
  or Android reports that speaker playback could not start, the app shows
  a **local playback failure** note. Repeated bad DATA frames are counted
  only **once per remote turn** rather than flooding the screen.
- A delayed successful speaker callback does not erase an already
  observed playback-drop warning. A callback from an old turn, old
  connection or ended test cannot update the new one.
- The result screen and opt-in **Copy this phone's test summary**
  include two bounded 0–10,000 counters:
  1. Local live speaker-start callbacks.
  2. Live voice turns with a local speaker failure or dropped PCM.
- Counts reset on a new fresh-QR pairing attempt, survive ordinary
  cleanup long enough for a result report, and are never persisted.

## Physical two-phone QA worksheet

1. Install **the same newly signed Preview** on both phones; ensure the
   package and signer match the existing Preview identity. Complete the
   fresh reciprocal QR, two human approvals and final confirmations.
2. Send three harmless short voice turns A → B, then B → A. Note both
   phones' **actually audible sound**, not just UI START/END indicators.
3. End the conversation on both sides, open **View test details and QA
   report**, then choose **Copy this phone's test summary**. Compare
   speaker-start and playback-problem counts with the turns that were
   *actually heard* locally. These are one-phone diagnostic counts, not
   delivery, read, sound-quality or receipt certifications.
4. Repeat when the receiving phone has its conversation **hidden**, its
   speaker is intentionally occupied, or it is disconnected during audio.
   Hidden conversations should not claim unexpected speaker failures:
   the mute was intentional. Do not assume a positive speaker-start
   callback means the sound was heard.
5. Re-pair with new QR and both approvals. Ensure counts from the earlier
   session do not carry into the new test. Avoid screenshots containing
   QR material, IP addresses or private conversations.

**Limits:** Android speaker hardware response and audio routing are
only verified by real phones. Green JVM/Android builds cannot establish
actual audibility or latency; this is not an independent security review.
The debug-only TLS/QR/approval and voice-floor path remains unchanged.
No file storage, microphone permission change, cloud, peer discovery,
wire change or production communication capability is introduced.

Preview **200007** is still the latest previously signed handset build
and does not include F4-Q; packaging can be consolidated with the next
testing checkpoint after a green merge.

**Rollback:** revert F4-Q PR; no state migration or signer change.
