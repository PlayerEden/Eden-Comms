# Eden Comms — Data Minimization and 30-Day Retention Policy (Design v0.1)

Status: **product direction approved in principle; exact UI/technical implementation proposed and not yet coded or audited**.
Date: 2026-11-03.
Related: [Product Vision](PRODUCT_VISION.md), [Privacy and Security Charter](PRIVACY_SECURITY_CHARTER.md), [Decisions](DECISIONS.md).

## Non-negotiable product direction

The safest data is data the app never intentionally stores. **Session-only is the default** for every new conversation. A user may opt into **local encrypted persistent history** separately for each conversation, but retained message content may not have a lifetime longer than **30 days**. There is no indefinite-history option, cloud archive, message export by default, or silent automatic retention.

This is local data minimization, not a promise that a compromised device, malicious recipient, OS, or physical flash memory contains no traces. We must never claim remote deletion or absolute erasure.

## Recommended user-facing settings

In the verified chat screen, provide one simple setting: **Message retention**.

| Setting | Meaning |
| --- | --- |
| **Session only (default)** | Text visible only during the current live session; no intentional message-content disk writes; best-effort in-memory cleanup at disconnect/end |
| **1 hour** | Store encrypted local messages for up to one hour after receipt |
| **24 hours** | Store encrypted local messages for up to one day after receipt |
| **7 days** | Store encrypted local messages for up to seven days after receipt |
| **14 days** | Store encrypted local messages for up to fourteen days after receipt |
| **30 days (maximum)** | Store encrypted local messages for no longer than thirty days after receipt |

Possible future custom option: 1–30 days. **No unlimited / forever / keep until manually deleted** setting.

The chat screen should say, for example, `Saved only on this phone • Auto-delete after 7 days`. Make the retention control reachable through chat settings, not a complex settings dashboard. Explain that the recipient may have different settings or external copies.

## Message lifecycle — proposed privacy contract

1. **No-history default:** The application does not intentionally write message bodies, decrypted audio or drafts to logs, cache files, Android backup, notifications, crash reports, analytics, or any database when session-only is selected. Visible content is held only in app memory and best-effort cleared at session end. There is no history restoration.
2. **Opt-in local persistence:** Persist only message ciphertext and the minimal metadata strictly needed for rendering, ordering and expiry in **app-private encrypted storage**. Key material should be separately protected (e.g. Android Keystore-backed wrapping keys), with per-conversation or per-message key separation subject to cryptographic review.
3. **Absolute expiry timestamp:** For received messages, set immutable `expiresAt = firstLocallyReceivedAt + selectedDuration`; for a sender's local copy, use `firstLocallySentAt`. Receiving/read status, opening chat, reconnecting, editing settings and device restarts never refresh a message's expiry.
4. **On access, filter first:** Before any expired item could appear in conversation UI, notification, search or local export, treat it as absent. Run best-effort deletion whenever the chat is opened, when the app starts, after a new message, at retention-setting changes and via opportunistic background cleanup.
5. **Do not rely on exact background timing:** Android may delay scheduled background work. WorkManager helps remove expired encrypted records later, but **never** rely on it as the sole visibility filter; expiry checks are enforced synchronously in the data-access path.
6. **Shortening retention:** Lowering a conversation's timer immediately applies the stricter limit to existing unexpired local messages; delete those already past the new deadline. Turning persistence off deletes local history promptly and returns to session-only mode.
7. **Lengthening retention:** A longer setting applies only to future stored messages; it never resurrects expired/deleted content or extends existing message expiry. This avoids silent resurrection.
8. **Clock manipulation:** Offline phones have no guaranteed trusted wall clock. Persist enough minimal monotonic/last-seen time evidence to detect obvious clock rollback where possible, never intentionally *extend* retention when time moves backward, and fail safely if timestamps cannot be trusted. Document unavoidable limitations; don't pretend there is cryptographically guaranteed exact time offline.
9. **Deletion limits:** Delete expired ciphertext and remove corresponding keys where feasible (cryptographic erasure as a design goal) and purge related indexes/previews. The OS, file-system snapshots, storage wear leveling, backups by other apps, screenshots or recipient copies may retain data. Never promise provable flash-level secure erase.
10. **No external server:** Persistent mode is *only* a history of messages already received directly by this device during a verified local session. It must not turn into cloud synchronization, a remote spool, always-on discovery or delivery while offline/disconnected.

## Two-party semantics and trust

The retention menu controls what **this phone's official app** intentionally keeps. It does not grant a user technical authority to erase another person's device.

Future protocol design should support communicating per-message maximum lifetime under the authenticated encrypted channel, so compliant Eden Comms clients can honor the **shorter** sender/recipient retention limit. Do not claim that an untrusted, modified or compromised recipient will honor these rules. No remote delete command should be marketed as guaranteed.

When both people opt into persistence, both independently see their local retention policy. If one party has `Session only`, their device never stores conversation history. Consent to storing a trusted peer identity/certificate remains distinct from consent to retaining chat content.

## Metadata and safeguards

- Avoid collecting phone number, contact list, public profile, geographic coordinates or central account IDs.
- Do not store plaintext search indexes, notification previews, thumbnails, share intents or diagnostics derived from conversations.
- Encrypted local history must be excluded from cloud backup AND Android device-to-device migration, with tested backup/data-extraction rules on supported OS versions/OEMs. `allowBackup=false` alone may be insufficient on some Android 12+ devices.
- No auto-export, attachment/media caching, synced clipboard, or third-party analytics. Future images, audio notes and files inherit the same or stricter TTL and secure local-storage requirements.
- Device pairing identity/trust can be stored only by explicit separate user choice; the data-retention clock for chat messages does not automatically delete cryptographic identity material, which has its own user-visible remove/forget action.
- Debug builds allow screenshots for synthetic QA, but should not be used with real sensitive conversations; release-screen protection is separately evaluated.

## Test acceptance matrix — M5, with early architecture gates

- Default session-only creates **no message-body files or database rows**, including on a crash, killed process, reboot and reconnect.
- Persisted content at rest is encrypted; keys/identities are protected with reviewed Android mechanisms and no plaintext cache/WAL/search/notification leaks.
- Test all TTL boundaries (before/at/after expiration), restart, background restrictions, offline mode, daylight savings, clock rollback/forward, storage pressure, process kill and device migration.
- Shortening the period deletes aged entries immediately; lengthening affects only future entries; switching to session-only purges stored history.
- Fuzz corrupted timestamps, malformed/corrupt encrypted database entries, crashes during deletion, and racing incoming packets.
- Backup/restore and Android device-to-device migration tests verify sensitive records never appear on the other device through system transfer.
- Check sender/recipient mismatch and rogue recipient scenarios; confirm accurate product wording and no guarantees of remote erasure.
- No-shipment if retained message content can exceed 30 days through normal app behavior, if expiration can revive old content, or if content leaks into unencrypted storage.

## Phase placement

- M1–M4: architect message records, crypto keys and transports to avoid unintended persistence from the outset. Do not implement unlimited history and plan to retrofit privacy later.
- M5: implement encrypted per-chat persistence, expiry filtering, deletion and consent UI; run targeted device/backup tests.
- M6: audit claims, retention/erasure limits and remaining exposure paths.

## Engineering references

- Android Auto Backup and device-to-device transfer limitations: https://developer.android.com/identity/data/autobackup
- Android backup best practices: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- Android WorkManager background scheduling: https://developer.android.com/develop/background-work/background-tasks/persistent
- OWASP MASVS data storage controls: https://mas.owasp.org/MASVS/
