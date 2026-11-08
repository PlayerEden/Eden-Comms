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

**Still experimental:** LAN physical QA and independent security review
are open; actual Bluetooth and Wi-Fi Direct messaging, remote push-to-talk,
public release and production credentials remain unfinished.

See the canonical [two-phone field test issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26)
and [debug LAN usability notes](F0_LAN_CHAT_TEST_USABILITY_2026_11_08.md).
