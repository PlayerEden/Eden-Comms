# Eden Comms M3c — on-device APK build evidence for two-phone QA

**Prepared:** 2026-11-06  
**Status:** Android code + JVM regression tests. **No real handset result, stable signing identity, independent protocol review or unrestricted messaging.**

## The actual improvement

Before pairing, the development-only **Connect nearby** screen displays an installed build label containing version, versionCode, abbreviated Git source commit and abbreviated APK signing-certificate fingerprint. A **Copy installed build details** action lets a tester copy a complete public metadata report without starting a QR session.

The existing **Copy this phone's test summary** action now includes the same installed app versionCode, full source commit and APK signing-certificate SHA-256 alongside that phone's fixed-phrase counters and approval/confirmation observations. This helps troubleshoot the common failure where two APKs share `0.2.12-approval-review-only` but were built from different commits or signed by different disposable CI keys.

**Very important distinction:** the APK signing certificate is public package distribution metadata. It is **NOT** the peer's TLS/QR certificate, device-local Android Keystore private key, a session identifier, or proof of user authorization. A matching source SHA and signing certificate on two phones does **NOT** prove that the phones successfully connected, exchanged any packets, or can upgrade in place.

### Trust and privacy

- The app reads only its own installed public package information using `PackageManager` and the current Android APK signer certificate; older Android versions use the legacy platform package signature API.
- For API 28+, report the **currently signing APK** (not the oldest certificate in a rotation history). If multiple signers or inaccessible package metadata prevent a unique result, print `unavailable` rather than selecting one incorrectly.
- The source revision is a 40-character lowercase Git commit SHA injected at CI build time. Local IDE builds without a provided revision display `unavailable`. The app never guesses an unprovided source SHA.
- Both reports enforce strict formatting. No arbitrary exception text, device serial, network address, QR code, peer certificate, public TLS pin, text message, private key or identity material is inserted.
- Clipboard copying is **always a voluntary tap**; Android/the keyboard/other apps can access clipboard contents depending on the device. Clear copied QA evidence when no longer needed.
- The short on-screen SHA/digest prefixes are convenient visual hints, **not collision-resistant acceptance tests**. For a full comparison, check the complete values and original APK's independent `apk-identity.json` evidence.

### CI/revision stamp

Both GitHub Actions paths now pass `-PedenSourceRevision=$GITHUB_SHA` to Gradle. The value must be lowercase 40-hex; a local build without it gets the literal `unavailable`. `BuildConfig.SOURCE_REVISION` is generated at build time. No private signing material, source-address, API token or peer data is added to it. The protected internal-test APK path still signs **after** Gradle; the source-stamp does not change its signing key policy or versionCode allocation.

For PR workflows, GitHub's checked-out PR merge-test commit may differ from the source branch head; **use the Actions run's actual source SHA** when matching evidence. For merged `main` and manually triggered `main` internal test builds, the stamp is the checked-out `main` revision.

### Practical two-phone sequence

1. On each phone, open **Connect nearby** in the same test build and tap **Copy installed build details**. Paste into a private QA note marked A or B; check **full source revision, versionCode and APK signer** against the identified CI artifact. If any value is missing, do not call the build conclusively identified.
2. Confirm test APK update suitability **before installing** using the existing [M3b read-only desktop preflight](M3B_READ_ONLY_DEVICE_UPGRADE_PREFLIGHT.md). The in-app build report cannot inspect another proposed APK or prove compatibility with a future signer.
3. Only then run the [T01–T36 two-device physical worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md) on the two phones. After the experiment, copy **each** phone's test summary. A's TLS-write count is not evidence B received a phrase.
4. For actual in-place update acceptance, install two successive **persistently signed** test builds on real devices without uninstalling; verify the persisted local Android Keystore identity/settings independently. This is still open under [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27).

### Limitations / verification

- JVM tests cover known valid and untrusted metadata, missing/local source, strict 40/64-hex forms, safe one-phone QA copy and no accidental peer-identity claims.
- Standard Android CI must still pass, including Gradle BuildConfig generation, Android compilation, unit tests, security-boundary audit and APK packaging.
- The actual Android `PackageManager` behavior and on-screen labels on physical handsets are **NOT TESTED** by JVM tests or ordinary Actions builds.
- The ordinary CI debug signer is still disposable. No protected persistent signing keystore/secrets are configured by this PR.
- Release `CAN_CONNECT`, `CAN_SEND_MESSAGES` and `CAN_TRANSMIT_VOICE` remain **false**. No real typed text/PTT, added permissions, external services, saved peer trust or chat history.

**References:** [#26 hardware QA](https://github.com/PlayerEden/Eden-Comms/issues/26), [#27 signing continuity](https://github.com/PlayerEden/Eden-Comms/issues/27), [#42 reviewed real text](https://github.com/PlayerEden/Eden-Comms/issues/42).
