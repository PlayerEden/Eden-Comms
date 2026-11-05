# Eden Comms — M2j pairing and debug-conversation UI audit

**Checkpoint:** November 4, 2026  
**Status:** Implemented for automated compilation / JVM check; **physical visual and two-phone review pending**.  
**Security boundary:** Debug-only four fixed harmless phrases; no production text, voice, bilateral approval or saved conversations.

## Why this pass

Pairing and diagnostics shared one large surface. In the prior preview, long technical exporter status crowded the connected screen, received phrases appeared only as unstyled lines, the four phrase buttons were forced into two columns even at large font sizes, and failure details remained behind a collapsed panel. Camera startup failure had no on-screen retry. The 248 dp QR image could extend past very narrow content widths.

## UI adjustments

| Screen/state | Change | Truthfulness/accessibility reason |
| --- | --- | --- |
| Show host / scan host | `PAIRING • STEP 1 OF 2` | Two-QR verification is not misrepresented as single-scan |
| Show join reply / scan reply | `PAIRING • STEP 2 OF 2` | Both in-person scans still required |
| Automatic connection | `LOCAL CONNECTION • VERIFYING`, spoken status updates | Avoids claiming connection before TLS/QR/READY |
| Scanner setup failure | **Restart camera** action | Recover from a camera startup problem without leaving pairing |
| QR display | Scale to available content width, max 248 dp | Avoid horizontal clipping on smaller phones |
| Choosing a role | Buttons stack when content is narrower than 360 dp or font scale is at least 1.3 | Keep 52 dp minimum controls and readable labels |
| Preset phrase actions | Same adaptive stacking, 56 dp minimum controls | Prevent two-column clipping at 200% font |
| Live demo | Distinct, labeled **You / Other phone** activity bubbles, most recent eight entries | Distinguish observed remote phrases from local TLS writes, without delivery/read receipts |
| Live diagnostics | Explicit **Technical link status** toggle (collapsed by default) | Show useful test content first, keep exporter *compatibility only* details accessible |
| Result summary | Test reached READY or not, exact local written/received counters, plain visible reason | No hidden recovery explanation or unsupported messaging/security claim |

Test copy and breakpoint decisions are in `PairingTestUiCopy` with JVM tests. The app continues to use existing scoped in-memory test state, disconnect cleanup, stale-callback guard, Android Keystore identity and local-only TLS 1.3 test transport. The buttons do not add any new packet types, messages, permissions or server endpoints.

## Automated verification gate

- [ ] Android debug Kotlin compilation and JVM tests pass on the final head.
- [ ] Security boundary script still rejects production connect/text/voice activation.
- [ ] Existing APK permissions, backup exclusions, dependency baseline, APK identity and signing-certificate evidence unchanged.
- [ ] New copy tests cover truthful completion summaries and responsive button thresholds; prior navigation and transport tests remain green.

## Still requires real-device inspection

Do not treat compile passing as a visual screenshot review. On the same Android test installation (do not uninstall to bypass signing mismatch):

1. Small portrait screen (approximately 320–360 dp width) and landscape: confirm two QR steps and every phrase button remain reachable through scrolling, no cutoff or hidden Cancel / End test.
2. Default and 200% system font: labels must wrap and buttons must not overlap; keyboard isn't needed for preset phrases.
3. Dark and light theme: contrast for green/gray status, own versus remote phrase bubbles, and accessible buttons.
4. TalkBack: step labels, QR description, polite status changes and per-phrase speaker / sender state should read logically. A screen reader cannot independently interpret the graphic QR; users still need camera pairing assistance.
5. Simulated camera denied/startup failure: verify instructions, request prompt, restart action and Cancel.
6. Both phones: scan both QR codes, complete exact pinned TLS 1.3 + QR proof, compare A-to-B/B-to-A preset sends, disconnect, wrong/expired QR and no-WAN Wi-Fi. Reference [physical QA issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26).

**Release blockers unchanged:** independent security protocol assessment, live authenticated approval, full two-device networking evidence and durable APK signer/update compatibility [#27](https://github.com/PlayerEden/Eden-Comms/issues/27).

No APK update is necessary for this UI-only review until a phone tester is available and signing continuity is resolved.
