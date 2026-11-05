# Eden Comms — M2k consolidated two-phone QA worksheet

**Prepared:** November 5, 2026  
**State:** Not executed. This is a phone-test *collection plan*, not a pass result.

Use **one deliberate hands-on session** after the no-device CI passes are merged, the desired final APK is available, and signing compatibility is reviewed. The app is still a debug-only, fixed-phrase prototype. **Do not attempt normal or sensitive communications.**

## Before touching either phone

1. Record phone A/B Android API versions and device model privately (no serial/IMEI).
2. Confirm the exact APK artifact URL, source commit, package name, versionCode, SHA-256, and **signing certificate digest**. Refer to [test APK update safety](TEST_APK_UPDATE_SAFETY.md).
3. Check whether each existing installation has a matching signer. **Never uninstall merely to fix an update error** without recognizing that this discards locally created Android Keystore identity/settings.
4. If a stable internal test signer hasn't been configured, assume each ordinary CI debug APK might have a different signer. Do not claim upgrade preservation.
5. Keep active QR images, certificate pins, session nonces, private IPs, raw logs and network captures out of public reports.

## One paired test run

| ID | Device exercise | Record A | Record B |
| --- | --- | --- | --- |
| T01 | Both on same local Wi-Fi; two reciprocal QRs | NOT TESTED | NOT TESTED |
| T02 | Disable cellular and router WAN (keep local Wi-Fi); repeat pairing | NOT TESTED | NOT TESTED |
| T03 | On M2v+, both approve after QR/TLS; A sends 4 fixed phrases, then B sends 4 back | NOT TESTED | NOT TESTED |
| T04 | Rapid taps and receive ordering/counters; check for false duplicate activity | NOT TESTED | NOT TESTED |
| T05 | A ends test; B must stop and disallow further sending | NOT TESTED | NOT TESTED |
| T06 | Disconnect Wi-Fi on B; verify bounded disconnect on A | NOT TESTED | NOT TESTED |
| T07 | B displays reply QR while A delays scanning 5–15 seconds; retry should recover | NOT TESTED | NOT TESTED |
| T08 | A never scans reply QR; no false 'Connected' success | NOT TESTED | NOT TESTED |
| T09 | Test stale, self and mismatched QR codes; reject safely | NOT TESTED | NOT TESTED |
| T10 | Cancel, Android Back, app background and camera permission denial | NOT TESTED | NOT TESTED |
| T11 | Older Android 13 + newer Android phone TLS/Keystore interop | NOT TESTED | NOT TESTED |
| T12 | Release/non-debug build must reject test session | NOT TESTED | NOT TESTED |
| T13 | Permission/backup/log inspection; no mic/Bluetooth/location/contacts | NOT TESTED | NOT TESTED |
| T14 | Portrait, landscape, small width, 200% text, TalkBack, dark/light | NOT TESTED | NOT TESTED |
| T15 | Version mismatch versus handshake-only build; reject unapproved phrases | NOT TESTED | NOT TESTED |
| T16 | **Only after durable signing:** upgrade same package in place and verify app settings and local Keystore identity still work | NOT TESTED | NOT TESTED |
| T17 | Both phones reach approval stage after QR/TLS; no preset phrase buttons before both approve | NOT TESTED | NOT TESTED |
| T18 | A approves first, B waits; no phrase exchanges, distinct local and peer approval statuses | NOT TESTED | NOT TESTED |
| T19 | B approves next; both enter preset conversation and correlate activity counts | NOT TESTED | NOT TESTED |
| T20 | B declines before A approval; prompt stop on both phones and safe reason | NOT TESTED | NOT TESTED |
| T21 | Approval limit (~90s) expires; session closes without false ready stage | NOT TESTED | NOT TESTED |
| T22 | Cancel/background/Wi-Fi switch while awaiting approval; no stale approval accepted after new pairing | NOT TESTED | NOT TESTED |
| T23 | Two API 31+ devices compare identical RFC 9266 exporter binding after QR/TLS, then reach approval | NOT TESTED | NOT TESTED |
| T24 | Controlled malformed/wrong-role/mismatched exporter frame aborts before READY; no presets | NOT TESTED | NOT TESTED |
| T25 | Unsupported exporter/API below 31 fails closed with safe category; no QR-only fallback | NOT TESTED | NOT TESTED |
| T26 | Pre-M2w and M2w APK pairing incompatibility fails closed; no phantom approvals/presets | NOT TESTED | NOT TESTED |
| T27 | Both phones approve with matching current QR/challenge/exporter commitment and then exchange presets | NOT TESTED | NOT TESTED |
| T28 | Old generic 3-byte approval and new 35-byte session-bound controls are incompatible and fail closed | NOT TESTED | NOT TESTED |
| T29 | Controlled stale/modified consent commitment is rejected before the gate changes | NOT TESTED | NOT TESTED |
| T30 | Decline, Wi-Fi change, background/cancel/restart never authorize via stale commitment | NOT TESTED | NOT TESTED |
| T31 | No-WAN real two-phone session clears at end; status, accessibility, counters and 1:1 behavior verified | NOT TESTED | NOT TESTED |

Start a *new* pairing session with fresh QR codes for each applicable test. Never reuse an active QR from earlier screenshots. A single passing run does **not** complete negative/network/device/installation tests.

## Capturing neutral evidence

After each run, the **Test summary** screen offers **Copy this phone's test summary**. It copies only the app version, Android API, selected QR role, whether that phone reached the debug READY stage, fixed-phrase TLS write event count, received phrase count and a fixed ending reason. It **does not** copy QR bytes, peer pins, IP addresses, chat text or exception content. Clipboard use is explicit and may expose its contents to the OS/other apps; clear it when no longer needed.

Collect **A and B separately**. Never rely on A's write counter as proof B received it; reconcile B's receive counter with A's send count, and vice versa. Even matching counts are just test observations—not cryptographic delivery receipts.

For each ID, change each NOT TESTED to PASS, FAIL, or BLOCKED only after watching the actual phone. Note the exact test APK(s), Android API(s), whether WAN/cellular were disabled, and the safe categorical symptoms. Keep sensitive captures local/private.

## Stop / escalation rules

- A TLS pin mismatch, unexpected authenticated session, unsigned/re-signed update mismatch, suspected identity loss, wrong-QR acceptance, unapproved content exchange or invisible ongoing connection is a **stop**: do not use app for real conversations; document issue for repair.
- Redo any affected test after fixes, on the exact new signed build. Tests from one build do not automatically cover a later build.
- Real typed text, live bilateral approval, voice/PTT, peer-trust retention, Wi-Fi Direct and Bluetooth all require separate implementation and future security/phone milestones; **this worksheet does not certify them**.

**Source test bundle:** [M2D](M2D_TWO_PHONE_TEST_BUNDLE.md).  
**Open physical acceptance issue:** [#26](https://github.com/PlayerEden/Eden-Comms/issues/26).  
**Open APK signing continuity:** [#27](https://github.com/PlayerEden/Eden-Comms/issues/27).
