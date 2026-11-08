# F4-D — first real in-press LAN audio streaming (debug-only)

**November 8, 2026 — source implementation / CI milestone, not physical acceptance**

The existing F4/F4-B/F4-C Android test app already has a mutually pinned TLS
1.3 local Wi-Fi session after two in-person QR exchanges, channel-binding
verification and both people's actual human approvals/final confirmations.
Before F4-D, the voice sender buffered up to three seconds, sent after release
and the receiving phone waited for END before speaker playback.

F4-D now uses **the SAME real ECVP 20ms PCM START/DATA/END frames** but changes
the timing: **during touch-and-hold**, the real Android microphone thread
delivers each frame to an intentionally tiny queue. The existing single,
authenticated TLS writer sends each frame as it arrives. The actual TLS
reader validates strict turn/order and calls a bounded, non-blocking Android
audio queue, whose own thread starts an AudioTrack MODE_STREAM early. Release
(or the 3-second safety cap) sends END after the final captured frame.

This is the first code path where one handset can begin hearing live audio
**while the other person is still holding the button**. It is not proof of
measured low latency, no glitching, acoustic quality or device compatibility.

## Concrete limits and why they matter

- Only the existing same-Wi-Fi, pinned TLS 1.3 session can carry voice.
  `LanDemoSession.startLiveVoice()` refuses closed/unready sessions and
  requires the existing final human approval/confirmation gate. No
  plaintext or extra radio socket, account, network discovery or public
  messaging feature.
- A turn sends **at most 150 × 20ms / 640-byte, 16kHz mono PCM frames**
  (maximum 3 seconds). The microphone -> TLS writer bridge holds at
  most **12 frames (240ms)**. If outbound network buffering saturates,
  the turn ends instead of silently extending memory usage or queue delay.
- The remote TLS reader never waits for speaker hardware: a separate,
  bounded 12-frame queue and AudioTrack MODE_STREAM worker play the frames
  promptly; when overloaded or busy, audio is dropped on that handset
  rather than blocking TLS control frames or creating a recorded history.
- A local speaker/microphone-busy or observed incoming remote turn
  prevents starting a normal new local turn where possible. **This is
  courtesy half-duplex, NOT cryptographic or distributed radio-floor
  arbitration.** If two users press at effectively the same instant,
  they can still collide. A later feature must make floor ownership
  deterministic and visible.
- A cancelled gesture stops and wipes **future/unwritten** samples.
  **Unlike record-then-send, any frames already transmitted while held
  may already have been heard. Cancellation cannot retract them.**
- The older **USE TAP CONTROLS INSTEAD** route intentionally retains
  record-then-send as a fallback for accessibility / device problems.
  Because the F4-D receiver plays validated frames progressively,
  fallback bursts also use the bounded streaming speaker path.
- Microphone permission is still requested on demand in debug builds.
  Neither debug microphone capture nor speaker playback continues
  after app background, leaving the chat or closing the test session.
  No disk/audio history, global identity, call service or cloud.
  `CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` remain
  **false** in the production capability model. The release manifest
  still does not request microphone access.

## Real two-phone test — not yet completed

Use a newer stable-signed Preview built from source including F4-D on
**both Android devices**. The previous Preview 200003 predates this.
Connect to the same Wi-Fi router (no WAN needed), complete reciprocal QR,
both human approvals and final confirmations.

1. A presses **HOLD TO TALK**, speaks for about 2–3 seconds, and releases.
   On B, verify whether **speaker playback actually begins before A releases**.
   Record A's press time, B's first audible sound (approximately), and
   whether the speaker says voice started. Repeat B → A.
2. Repeat on different phones/volume levels, with A wearing headphones.
   The UI may report AudioTrack.play() started even if hardware is inaudible.
3. Press both phones at once. This first slice does **not** guarantee floor
   arbitration; report collision/drop rather than assuming exclusivity.
4. Hold and drag to cancel. Some audio may ALREADY have been transmitted;
   verify it stops promptly. Test early release (less than one PCM frame),
   automatic 3-second cutoff, held tap fallback, mic permission denied,
   local hidden conversation, screen lock/background and session disconnect.
5. While voice is running, send a harmless typed test message and verify
   text/ACK, heartbeat, and the original Wi-Fi link's 30-minute timeout
   survive. Slow Wi-Fi should not cause unbounded audio accumulation.
6. After any stop, fresh QR codes and both approvals must be required to
   start another session. Never upload real microphone sound, raw QR,
   private key, certificate, IP/MAC or message content to GitHub.

**Important:** This is still unreviewed debug software, not a secure
communications product, emergency tool or production walkie-talkie.
GitHub CI validates Kotlin/JVM frame order/queue bounds, real Android
compilation and static safety contracts; it CANNOT substitute for actual
microphone/speaker, timing, TLS provider or independent protocol review.

## Next steps

1. Physical two-device A/B live speech QA and repair audio routing,
   capture, jitter/latency problems using grounded observations.
2. Explicit network-level **floor request/grant/release** with deterministic
   simultaneous-press handling, bounded timeouts and truthful transmit/
   receive indicators. Do not call the current courtesy guard a full floor.
3. Improve jitter buffering, PCM backpressure and possible compact codec
   if it is needed on slower links — retain fail-closed session ownership.
4. Reuse on Bluetooth Classic/Wi-Fi Direct **only after each has its own
   physically tested, independently reviewed retained encrypted session**.

Rollback: revert F4-D PR. No saved user records, release permission, signing
or identity migration. F4-C tap/burst mode remains source-visible.
