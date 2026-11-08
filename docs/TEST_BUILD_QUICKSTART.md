# Eden Comms Preview — field test quick start

This sheet accompanies the **development-only** APK downloaded from the
official `PlayerEden/Eden-Comms` GitHub Actions Preview workflow. This is
NOT a reviewed secure messenger. Use only harmless sample text.

## Install/update (Android)

- Extract the ZIP under **Artifacts**; tap `eden-comms-preview-<code>.apk`.
  Android may ask you to approve installing this APK from the app you used
  to open it. Follow your phone's prompts only if you intended the install.
- The app label is **Eden Comms Preview** and its ID is
  `com.edenpractical.comms.preview`. It **installs alongside** any prior
  Eden Comms without copying that app's data or device identity.
- A **newer Preview** built by this same workflow uses the same public
  development signer and a higher versionCode, and should offer an
  **Update** over the previous Preview. Don't uninstall to resolve an
  unexpected mismatch; keep the old app and report it first.
- The publicly known AOSP test signing key is NOT a security guarantee.
  Retrieve APKs only from the source repository's actual Actions run.

## One-phone checks (can do these without another device)

1. Open **Eden Comms Preview** and look through Home, Connect Nearby,
   Settings and Developer Tools. Confirm app navigation, font sizes,
   and scrolling work in portrait and landscape.
2. Open **Voice / PTT → Try local microphone and speaker**.
   Grant microphone permission only for this local test. Record a harmless
   sample, stop, and play it back **on the same phone**. This is NOT remote voice.
3. On Connect Nearby, verify the **Show code** and **Scan code** flow is
   understandable. A real peer is required to complete it. Don't treat
   a code or Bluetooth/Wi-Fi Direct detection as an authenticated partner.
4. Check the installed version/source information under advanced build
   details, and report the build code if something goes wrong.

## Two-phone LAN chat (same local Wi-Fi; do not use private messages)

1. Install **the same Preview APK** on both Android phones; connect both
   to the same non-guest Wi-Fi. A router with no Internet/WAN can work.
2. Phone A chooses **Show code**. Phone B chooses **Scan code**. Complete
   the **reciprocal QR exchange on both screens**. Do not share QR frames
   online.
3. Both people explicitly approve the current test connection and wait
   for **both final confirmations**. Don't send anything before that.
   While the QR screen or active test chat is visible, the app asks
   Android to keep the screen awake to avoid an accidental timeout.
   The power button can still lock; backgrounding or leaving still
   ends the live test. Idle/result screens do not hold the display awake.
4. On A, type `hello test` and send using the **keyboard Send action**.
   Send another via **SEND TEST MESSAGE**. Check that B shows the actual
   received messages; test reverse direction B → A.
5. Observe `Written to test link` versus `Other app returned test ACK`.
   The first is a local write; the second is an app response, **not** a
   human read receipt or a guarantee someone saw the message.
6. Confirm **Hide conversation / Clear my screen**, then disconnect from
   one phone. The other should also end the session. Check fresh QR codes
   are required on restart. The end screen now offers **Try again • Show a
   new code** or **Try again • Scan a new code** using only your prior role.
   Both phones still exchange **fresh** QR codes and both approve again. Use
   **Start over with fresh QR codes** to choose the other role. When
   convenient, try WAN-off and Wi-Fi-change negative tests.
7. A connected test link now has a **30-minute maximum counted from READY**
   (including time spent approving). It also closes if the other phone
   stops responding; you do not have to wait the full 30 minutes.

## New two-phone *live* push-to-talk (F4-F source checkpoint)

This Preview includes **F4-D live 20 ms audio streaming**, **F4-E
receiving/transmitting indicators**, and **F4-F per-session transmit-floor
coordination**. These are experimental tests, not a reviewed secure
walkie-talkie. Use only harmless spoken samples and the **same new APK on
both phones**.

1. Complete the fresh reciprocal QR exchange on the same local Wi-Fi.
   Both people must approve and complete the final confirmation before
   voice can be used. This is not a cloud or mobile-data connection.
2. Phone A presses and **holds HOLD TO TALK**, says a short test phrase
   and releases. The microphone must not start until its side has the
   channel. When the joining phone requests the channel, it may briefly
   show **WAITING FOR RADIO CHANNEL • MIC OFF**. The presenter/host
   arbitrates simultaneous requests. Audio is transmitted **while held**,
   not all at the end, with a 3-second maximum per press.
3. Phone B should hear the phrase **before A releases**. Listen for real
   audible speech; an app status indicating the speaker started is not
   proof sound was heard. Repeat B → A.
4. Press both Talk buttons nearly simultaneously. Only one side should
   obtain the channel; the other should remain silent and retry after
   the first transmission finishes. Also try the joining phone pressing
   very briefly and releasing **while waiting for a grant**. Its
   microphone should never start. A missing/stuck grant may end the
   connection intentionally rather than reassigning it unsafely.
5. For accessibility, choose **USE TAP CONTROLS INSTEAD**. The two-tap
   **START TALKING / SEND VOICE BURST** mode still records first, then
   sends its short audio. It must request exactly the same radio floor
   before starting. If it is waiting, tapping cancels the request.
6. Repeat after denying microphone permission; short accidental taps;
   the three-second automatic cutoff; **Hide conversation**; using the
   Home/power button; ending the chat; and turning Wi-Fi off. Verify no
   unintended microphone activity, audio continuing in the background,
   stuck transmit state or old session restored without fresh QR.
   A cancelled *active* live transmission cannot retract samples that
   already reached the other phone.
7. Check that typed test text, application acknowledgements and the
   normal session timeout still behave sensibly during and after voice.
   Avoid sharing audio, identity codes, IP addresses or raw debug logs.

**Important:** F4-F passed source/build checks, but simultaneous presses,
latency, speaker audibility, Bluetooth/Wi-Fi Direct communication and
cross-device compatibility are **not physically verified yet**. The Preview
is not suitable for real private, safety-critical or emergency radio use.
For detailed QA and failure reports, see
[F4-F two-phone handoff](F4_F_REAL_LAN_FLOOR_HANDSHAKE_2026_11_08.md).

## What to report (do not post private material)

- Which APK `versionCode` and public `source revision` were installed;
  the two Android versions and whether you used one phone or two.
- What you **tapped**, what you **actually saw on both screens**, and the
  first place it stopped or behaved unexpectedly.
- Optional: after the test, **View test details and QA report → Copy this
  phone's test summary** from each phone. This exports local event counts,
  version and signer, not chat content or QR data. Clipboard contents can
  be read by the OS/other apps; share only if appropriate.

Do not paste peer QRs, private keys, actual message content, IPs, raw
certificates or personally identifying device details into an issue.

**Still experimental:** LAN voice and collision behavior need actual two-phone
QA and an independent security review. Bluetooth/Wi-Fi Direct messaging,
long-running/background radio use, public release and production credentials
remain unfinished.

See the canonical [two-phone field test issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26)
and [debug LAN usability notes](F0_LAN_CHAT_TEST_USABILITY_2026_11_08.md).
