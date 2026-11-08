# F2-J — guided Bluetooth two-phone setup (development test only)

**2026-11-08.** F2 continues after the proven Preview APK path and F0 usability
work. This is a meaningful Android **screen** improvement to the existing real
Bluetooth RFCOMM / reciprocal-QR / pinned TLS 1.3 / bilateral consent
diagnostic. It does **not** carry chat messages or voice.

## What changed in the actual test app

- The two-phone **in-person QR exchange now appears first**, instead of below
  the standalone raw radio scanner. One person shows a QR; the other scans;
  the first phone scans the reply.
- Clear one-at-a-time **Step 1 codes → Step 2 Bluetooth → Step 3 verify and
  approve** guidance derives from actual role, QR stage, scan/selection and
  controller phase. A cosmetic step never authorizes a link or claims a peer
  has been authenticated.
- The host sees its Host action directly after the locally prepared QR. The
  joining phone sees **Search** and numbered unverified radio signals only
  when its own QR preparation is ready. After explicit selection, Join is
  available nearby. Radio-only inquiry remains under **Advanced • Raw radio
  scan**, not a mandatory first step.
- After the controller ends any link, the screen clears the stale QR
  preparation and anonymous signal selection, so retrying begins with new
  QR codes and cannot silently reuse a prior choice. The foreground/permission
  cancellation and socket cleanup are unchanged.
- During a link, consent/decline and disconnect are near the step guidance,
  not buried below all discovery diagnostics. Deep technical text is optional.

This is a development-only, foreground diagnostic. **It still closes the
Bluetooth socket after both people approve and both encrypted final
confirmations**. There is no Bluetooth text conversation, trusted contact,
saved pairing, auto-connect, background messaging or remote PTT. No new
permissions, signing keys or ReleaseCapabilities flags.

## Two-phone physical checkpoint (owner has not reported this yet)

On two Android 12+ phones with Bluetooth on and Wi-Fi/cellular disabled:

1. Open the developer Bluetooth test on both. Choose **Show QR** on Phone A
   and **Scan** on Phone B. Exchange both QR codes in person.
2. On A, tap **HOST ONE-TIME BLUETOOTH TEST LINK** and accept Android
   discoverability if asked. On B, tap **START 30-SECOND TEST SEARCH**, choose
   A's unverified signal, and tap **JOIN STAGED UNVERIFIED SIGNAL**.
3. On both phones, the link must perform the real QR-pinned TLS/channel
   checks before showing individual APPROVE/DECLINE. Approve separately.
   Record the final *non-sensitive status* of each phone.
4. Verify the link ends after its confirmations. Do not expect a chat
   composer or data transfer. Check Reject, wrong QR, retry with **new**
   codes, no peer, permission denied, background and Bluetooth radio-off.
5. Confirm the Advanced radio-only scan is still accessible when needed,
   without confusing the radio signal with a verified person.

Do not copy raw QR frames, private keys, certificates, addresses or chat data
into a GitHub issue. This is not a security certification. The next
**functionality** milestone remains reviewed ownership of the same real TLS
engine/socket after consent, live encrypted TEXT/ACK and phone testing, then
shared chat UI. Independent expert review and physical provider compatibility
remain open before claiming secure or production Bluetooth communication.

## Source/CI checks

The pure cue matrix has JVM tests for QR, host, join, scan, consent and
terminal states. A Python guard confirms QR-first ordering, single user
action per role, existing terminal reset, and that both release capabilities
and automatic socket-closure security limits remain unchanged. Android
compile, JVM/Python tests, manifest/permission checks and APK packaging must
pass before merge. A green run is NOT physical Bluetooth acceptance.

**Rollback:** revert this UI-only slice. No wire-format change, Android
keystore migration, production app ID/signature change or storage change.
