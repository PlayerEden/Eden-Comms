# Eden Comms — Development APK policy
Updated 2026-11-03. This applies to internal test/debug builds, NOT a public security release.

## Why
Development iterations should be quick and easy to test on a real Android device. Every debug APK is **not** a release candidate and does not need full public-release packaging, a fresh security audit, or store-readiness work.

## M3f developer UX policy

The Home **Text** entry now opens nearby pairing and the active debug-only session's primary conversation UI. The old local-only fictional layout tool remains under Developer tools. New real-typed development text is still explicitly **unreviewed**, not for sensitive conversations, and its on-screen app content is cleared on app background/disconnect; system keyboards and screenshots may retain copies. See [M3f](M3F_CHAT_FIRST_EXPERIENCE.md). Do not confuse polished conversation styling with independent security testing or production chat readiness.

## Debug/testing builds
- Android screenshots and screen recording are **allowed** to support bug reports and UI feedback (app checks debuggable flag).
- Do not use personal or sensitive real-world messages in development builds; test with synthetic data.
- Keep standard compile/unit tests and basic regression checks; run targeted checks for changed systems.
- v0.2.6 first added a **debug-only preset-phrase exchange**. **M3e** extends the same existing QR-pinned TLS 1.3 and bilateral approval/final confirmation test with **up to 280 code points of typed TEST text**. It is development-only, unreviewed, not yet verified on two physical phones and NOT for sensitive real-world information. Content is temporary RAM only and clears on disconnect/background; keyboard/system copies may linger. No background transport or release access. See [M3e](M3E_DEBUG_TWO_WAY_TEXT.md).
- Use GitHub Actions debug APK artifacts for convenient test builds; no release tags, publishing, formal signing, marketing, or lengthy release-note processes for every small update.
- Fix meaningful issues as discovered, and perform deeper checks at the **end of each major milestone**.
- Prefer one cohesive APK after a useful batch of changes rather than rebuilding/delivering for every tiny change.
- Use Google Drive for APK delivery if ChatGPT attachment delivery fails (previously successful).
- Preserve core security invariants **even in debug builds** whenever actual transport is eventually enabled: authenticated encryption, correct identity verification, no plaintext fallback, no server/telemetry, minimal permissions.
- Debug screenshot availability is a documented exception to *screen-capture privacy hardening*, **not** permission to weaken encryption or permit debug logging of secrets.

## Future release builds
- Android `FLAG_SECURE` is retained for non-debuggable builds, subject to final usability/accessibility review before release.
- Production policy includes independent security assessment, build/signing provenance, backups/metadata checks, audited dependencies, and major device testing. These gates are not repeated for every development APK.
- Formal public release only after explicit readiness review and all blocking defects resolved.

## QA tiers
| Tier | Trigger | Required |
|---|---|---|
| Quick dev check | Small source/UI change | Build and relevant tests; screenshot feedback |
| Milestone check | Major subsystem complete | All core tests + targeted real-device/security regression |
| Release candidate | Preparing for real users | Complete security, privacy, reliability, accessibility and signing review |
