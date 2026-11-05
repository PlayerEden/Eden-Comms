# Eden Comms — M2m contextual privacy/security help

**Code-only checkpoint, November 5, 2026.** Physical accessibility and layout QA remain pending.

Implements the first accepted **D-014** lightweight `?` help pattern without overwhelming every user with consent dialogs. Help appears only when a person taps it:

- **Connect nearby → Why scan two codes?** Explains that host QR and joiner reply are both necessary to agree on current certificate pins and fresh QR session values, and that this is still a test rather than production voice/text authorization.
- **Settings → Message history → What does this mean?** Explains the current debug-only temporary phrase screen, *future* session-only default, separate locally persistent Android Keystore identity, and the other person's ability to capture/keep content. Offers a direct jump to the already offline **Privacy & data** page.

### Invariants

No device key, QR, IP, remote identity or stored data is read for a help dialog. No access to a microphone, Bluetooth or network APIs is added. No new permissions, data persistence, consent semantics, trust-retention records or messaging capability switches. These dialogs are **information only**; pressing them never approves a peer, weakens TLS, changes data expiry or enables sending.

### Automated verification

- Pure JVM tests enforce the copy's boundaries: two QR directions, no false production consent, demo-only phrases vs future chat, local key persistence separate from chat and no promise of deleting a recipient's copy.
- Existing Android compile, security gate, APK/permission/privacy/dependency checks must remain green.

### Later phone QA

On two devices with default and 200% font, dark and light, portrait/landscape, check the help buttons are discoverable and at least 48dp tall, AlertDialog text scrolls/is accessible, Back/dismiss works, and the Privacy link returns properly. No public 'secure messenger' assertion is appropriate until the separate real-device and independent security gates pass.

**Planning decision:** D-014 contextual explanations, initial pairing + session history; other unimplemented privacy choices remain proposed.
