# Eden Comms — Trust retention, message retention and contextual help

Decision date: **November 4, 2026**. Status (November 9): **remembered peer trust remains unimplemented; F5-A implements experimental opt-in encrypted LAN message history only**. The two controls remain independent. Physical Android/backup and independent security review are OPEN.

## One sentence we want users to understand

**Remember only what you choose, for no more than 30 days. Chat history is separate, and nothing is saved by default.**

Important: local identity keys and device configuration are separate from conversation data; a previously generated Android Keystore identity may survive the current session. “Nothing saved by default” here means **no peer trust record or chat history is saved without opt-in**, not that the APK writes literally nothing.

## A. Remembering another phone

- Present only **after two peers have fully authenticated and both people have approved their peer identities**. Do not show this as an enabled feature in the present failed-handshake test.
- Default **Just this session**. Selecting it means **no persistent peer trust record**.
- Optional **Remember this device for 1–30 days**. Simple presets: 1, 7, 30 days, with a 1–30 custom day chooser if useful. **30 is a non-negotiable maximum**, no “forever” or automatic extension. The user can forget earlier.
- **Per device, independent:** A may remember B for 7 days; B may choose not to remember A. A's selection changes **only A's local trust cache**, never B's device and never B's message history.
- Saving a public identity does not reveal the private key, but it can reveal that two devices were linked. Consent and backup exclusions still matter.
- Store the least necessary versioned metadata: verified full peer certificate/public-key pin, issuance/expiry, and a schema version. Any friendly nickname is an additional optional metadata choice; never persist IP, phone number, contact list, message contents, session nonce, or peer private key.
- **Reconnect:** New TLS session authenticates possession of the *matching private key* and enforces a fresh encrypted handshake. Stored public identity is a pin, **not** permission to bypass authentication. Changed identity / expiry / malformed record / clock rollback / deleted self identity → block and require fresh verification.
- **Storage controls:** Use app-private storage with cryptographic integrity (Keystore-backed authenticated protection, review key lifecycle and migration), exclude cloud backup and device transfer, remove records on manual Forget and on expiry checks. Can't guarantee exact physical flash erasure or deletion while the app never runs.
- **Expiry enforcement:** On launch, resume, reconnect, before trusting, and during active app use; avoid promising a specific background erase moment. On expiry revoke eligibility immediately, then best-effort purge stored bytes. No auto-renew.

## B. Saving messages is a different control

- Default chat mode: **session-only**, in-memory as far as practical, no intentional persistent transcript, backup or message preview.
- Optional per-chat encrypted local history: chosen **1–30-day** retention, hard maximum, expires independently of peer trust. Encryption key storage, lock-screen access, process death, rollback, deletion testing and notifications require a separate review.
- A phone's retention selection **does not control the recipient's phone**. A recipient could screenshot, photograph, quote or save content independently. No false “deleted everywhere” claim.
- A can remember B's public identity and save zero messages. A can also choose a message retention period while not remembering B's peer identity—then new sessions require re-verification.
- For voice/PTT, no recording or saved clips unless a separately reviewed, explicit opt-in feature is introduced.

## C. Plain-language UX and progressive disclosure

**After a real verified connection** (conceptual UI):

> **Remember this device?**  
> Reconnect more easily next time.  
> ( ) Just this session — default  
> ( ) 1 day  ( ) 7 days  ( ) 30 days  [Choose 1–30]  
> [?] What gets saved?  
> [Not now]  [Remember device]

**Quick question-mark popover** (two or three sentences):

> “Only this phone keeps the other device’s verified public identity and expiry time. Messages are not saved by this setting. You can forget the device sooner.”

**Learn more** opens a focused local page under **How it works → Privacy & device verification**:
- What is a public identity versus a private key?
- Why reconnect is still authenticated.
- What gets saved and exactly when it expires.
- What the app cannot prevent, including peer copies, screenshots, network metadata, compromised endpoints and storage remnants.
- How to forget a device, clear history and reset local identity.
- What discovery broadcasts on a shared Wi-Fi and why it is foreground-only.

Use ? only for nontrivial data/privacy choices, not every ordinary label. Use accessible touch targets and screen-reader text; include no marketing-style “unhackable” claims, modal walls of text or forced tutorials.

## D. How it interacts with nearby discovery

- Discovery is opt-in, **foreground only**, and time-limited. No always-on advertising/scanning.
- When feasible use temporary, rotating identifiers to minimize a trackable fingerprint. Discovery visibility does **not** mean authenticated or safe; only handshake verification can confer a trusted state.
- Without a stored peer pin, first-time or expired relationships require another explicit verification method (QR or reviewed, transcript-bound short code compared across a separate authentic channel such as speaking in person/by phone).
- A familiar nickname alone never bypasses cryptographic checks.

## E. Engineering gates and milestone ordering

1. **Now:** Diagnose user-reported two-phone local TLS failure with sanitized error categories. No trust storage or real messages.
2. **Next:** Prove mutual TLS + both peer identities on physical devices, validate timeout/negative cases; establish session-bound two-sided authorization.
3. **Then:** Offer temporary trust in memory for a session; after independent security review, optional encrypted/integrity-protected peer public-identity store (1–30 days) and UI help.
4. **Then:** Add default session-only encrypted text with explicit per-chat retention design.
5. **Then:** Test notification/cache/backups, replay/identity change/expiration/clock rollback, restore and device migration; later Voice / PTT.
6. **Release:** independent security review and no overbroad deletion, secrecy or anonymity claims.

## Acceptance criteria

- Confirm both sides choose durations independently. No implicit consent and no default persisted peer trust.
- Enforce duration in range 1..30 at *every* persistence entry point. A parsed value above 30, clock rollback, corruption or expired entry never grants trust.
- No automatic retention renewal on reconnect. Deleting identity resets all peer trust.
- Save only approved minimum peer identity metadata; zero message bodies or peer private keys in trust records; app backup exclusions verified on devices.
- Distinguish **trust retention** vs **chat retention** in Settings, with quick explanation and offline full help.
- User-facing status states come only from proven authenticated channels, not from discovery or a QR scan alone.
- Test manual Forget and expiry at exact boundary, process restart, OS clock change, uninstall/reinstall and different users' choices.

**Implementation status (November 9):** Peer remembering still contains only pure `PeerTrustExpiry` rules/tests; it **does not store, remember or authenticate using a saved peer identity**. F5-A PR #148 separately adds explicitly opted-in, encrypted **LAN debug** message history for up to 30 days with an in-app erase control. Neither feature is approved for production security, and persisted history does not confer peer trust or shortcut mutual TLS + human confirmation.
