# Eden Comms M3l — Ephemeral Bluetooth signal staging (debug only)

**Checkpoint:** November 6, 2026.  
**What is real:** The Android 12+ debug-only Bluetooth Classic inquiry from M3k now reads `BluetoothDevice.EXTRA_DEVICE` in `ACTION_FOUND` broadcasts and shows a temporary, capped list of **unverified anonymous signals**. A person can tap a numbered signal to stage it locally. **What does NOT exist:** identification of a real Eden Comms counterpart, Bluetooth bonding, RFCOMM connections, authenticated peer identity, Bluetooth message transport or remote approval.

## Functional UI and flow

On a **debug** APK, visit **Settings → Device and developer tools → Bluetooth discovery test**, tap **Start** and grant Android's Nearby devices scan permission. The M3k scan remains strictly foreground-only, 30 seconds maximum and 32 raw inquiry observations maximum. M3l adds:

- Up to **12 distinct opaque BluetoothDevice objects** retained **only in the activity's RAM** during that one scan. Duplicate `ACTION_FOUND` handles collapse to one numbered signal; raw observation counts may be greater than the unique signal count.
- A list of `Unverified signal 1`, `Unverified signal 2`, etc. These labels are **local, temporary ordinals**, not identifiers and not proof of Eden Comms identity. They contain no MAC, friendly name, RSSI, Bluetooth class, advertising payload or known-contact information.
- Each row has an explicit **Stage unverified signal** action. Selecting is permitted only while foreground with permission, stops the radio scan, discards all other handles, and retains at most **one opaque handle** until the user taps **Forget**, starts a new scan or leaves the screen. No socket, pairing dialog, key exchange, text or trusted peer is created. The selected handle is **not used** by any other code path in this milestone.
- A normal scan finish or safety timeout retains the temporary list for review. **Stop search and clear results**, **Clear all signal results**, **Forget selected signal**, app background, destruction, permission loss and next scan drop the in-memory handles, labels and selection. Nothing is persisted to Android storage or added to backups.
- Stale model scan-token callbacks cannot add items or select candidates in a fresh ledger; this is a local software safety guard, **not proof of radio broadcast origin**. Android's untrusted `ACTION_FOUND` broadcasts may include non-Eden or spoofed devices and can arrive late. Never grant trust or route any message based on these results.

## Why no device names yet?

On current Android versions, reading `BluetoothDevice.getName()` requires `BLUETOOTH_CONNECT`. M3l **does not request that permission**, does not call the getter, and never extracts the MAC address. A future M3m/M3l+ phase must deliberately explain peer identification, capability advertisement and Android permissions in a security-reviewed design before adding a meaningful recognizable-device UI. This anonymous list is a development utility, not a polished production peer picker.

The actual Android Bluetooth Scan permission remains **debug-only** with `neverForLocation`; main/release manifest permissions and production flags **CAN_CONNECT, CAN_SEND_MESSAGES and CAN_TRANSMIT_VOICE all remain false**. `DebugBluetoothCandidateLedger` and the scanner reside exclusively under `app/src/debug`; the latter is non-exported. LAN QR/TLS test and session-only chat are untouched.

## Automated evidence

- Kotlin JVM tests cover the 12 unique-candidate bound, equals-based deduplication, invalid selection rejection, post-scan review, selection dropping all other candidates, explicit clear, fresh-generation token invalidation and stale callback rejection.
- Python source contracts ensure only the debug activity consumes BluetoothDevice handles; it cannot read names, addresses, bond state or service discovery, open RFCOMM sockets, transmit text, persist data or add production radio permissions.
- Existing M3k permission/APK audits, security boundary script and full Android debug compilation continue to run.
- This does **not** count as independent protocol review or physical Bluetooth scanning confirmation.

## Owner-deferred physical acceptance

**Not tested on actual phones.** Later validate on two Android 12+ OEM devices: grant/deny/revoke Nearby devices permission, start scan, see numbered signals from nearby unrelated Bluetooth devices, repeat broadcasts dedupe, cap unique signals at 12, stage one, verify radio stops and **no pairing prompt/message appears**, Forget erases selection, Start creates a fresh list, Stop and background clear, finish/timeout leaves an untrusted review list, old broadcasts cannot trigger a connection, portrait/landscape, TalkBack, small screens, and the working LAN test unchanged. Do not capture/attach raw MACs or identifiers as evidence.

## Next functional milestones

**M3m**: controlled debug RFCOMM host/join connection with explicit Android permission and verified cleanup, but **no unrestricted user messages**. Design real Eden Comms peer capability identification and an independent explicit verification step; names/bond status cannot be accepted as trust. A later **M3n** must reuse an independently reviewed authenticated-session/approval model before Bluetooth test text.

[Core Feature Completion Plan](CORE_FEATURE_COMPLETION_PLAN_2026_11_06.md) · [M3k inquiry](M3K_DEBUG_BLUETOOTH_INQUIRY.md) · [Bluetooth track #59](https://github.com/PlayerEden/Eden-Comms/issues/59) · [Physical QA #26](https://github.com/PlayerEden/Eden-Comms/issues/26).

Android sources: [Find Bluetooth devices](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices) and [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).
