# F6-U1 — optional owner-approved Android install-over (not executed on phones)

**November 8, 2026 · source/CI development checkpoint**

## What this adds
Eden Comms already has a **read-only** installed-vs-candidate APK preflight,
an owner's protected manual `internal-test` signing workflow, and an in-app
public APK signature/version/source report. This F6-U1 slice adds a **separate
owner-invoked** CLI for an actual Android install-over *when the owner chooses
to do one later*.

`scripts/install_internal_test_update.py` requires all of the following:
1. A real candidate APK for `com.edenpractical.comms`, a single authorized
   USB-debugging device (or explicit `--serial` selection), and an already
   installed Eden Comms base APK.
2. A read-only preflight, verifying the exact same package, **matching public
   APK signing certificate SHA-256**, and **strictly increasing versionCode**.
   It displays the public candidate APK file hash. A previous transient-signing
   APK may be incompatible; that mismatch is a hard stop, not an invitation to
   uninstall.
3. The operator manually enters the full prompt:
   `INSTALL OVER com.edenpractical.comms <candidate_versionCode>`.
   Refusal, typo or missing consent stops without installing.
4. The script selects the same device and repeats the **full preflight**,
   byte-hash check and metadata comparison after the prompt, to reject a
   changed file/device/installed base before the install.
5. Only then it uses the ordinary `adb -s <selected> install -r <candidate>`
   operation. It does **not** invoke uninstall, Android app-data clear,
   downgrade, restore, fake-signing flags or install on multiple phones.
6. On Android's explicit `Success` result, it pulls **only the public
   installed base APK** into an automatically deleted temporary directory and
   reads back package ID, versionCode and signing certificate. Unexpected
   results are failures requiring manual investigation, never an automatic
   second install, reinstall, or reset.

**Limits:** An in-place binary update and public APK metadata do NOT establish
that the user's app-local Keystore keys, preferences, pairings or identity were
preserved. There is no feasible way for this CLI to export/restore the Android
Keystore private key. The tester must independently compare the app's own
identity and settings **before and after**. Nor does this prove TLS or
Bluetooth/Wi-Fi Direct functionality, reviewed security, or production readiness.

## When the user is ready to test

On a **trusted PC** with Android SDK Build Tools 36.0.0, Platform Tools,
Python 3 and a securely obtained compatible internal-test APK:

- Keep the old APK, previous identity/version/signer evidence and a private
  record of the app's opt-in identity and settings.
- Open Eden Comms before upgrading, note the same phone's **local identity**
  without exporting private keys.
- Attach **only one phone** and explicitly authorize its USB-debugging prompt.
  ADB multi-device deployments are disabled by default.
- Run from the repository root:

```sh
python3 scripts/install_internal_test_update.py --apk /private/path/eden-comms-internal-test.apk
```

- If multiple devices are deliberately connected, an exact authorized ADB
  `--serial` is required. Do not post serials or paths in GitHub issues.
- Review the printed package, version and **public** signing certificate.
  The tool has made NO device changes up to this point. Only type the
  full literal approval shown if you consent to replacing that phone's
  installed application binary.
- After the read-back, open Eden Comms and separately confirm prior local
  Keystore identity, preferences and pairing setup survived. Check actual
  retained settings and that private chat content was not restored.
- Repeat independently on phone B, with consent. Then do #26 physical
  no-WAN LAN QR, TLS, approved text and ACK tests using harmless messages.
- Turn off USB debugging when finished.

**Do not run** the script on an app that was installed with a different signer;
the tool will refuse. Never remove the old app simply to obtain a passing
result without an informed, explicit reset decision. If Android fails the
actual install or post-install package readback, stop and investigate: no
automatic rollback or retained-identity guarantee is provided.

## What is still required for issue #27
A protected **durable** test-only signing key must actually be created and
stored by the repository owner outside source control; the GitHub
`internal-test` environment with five secrets must be configured. Run **two
new separate manual** signed builds, verify each pinned signer plus monotonic
version code and APK SHA-256, then exercise installation-over-existing on both
Android devices and manually confirm identity/preferences preservation.

No one configured or accessed owner keys, connected to a real phone, or
completed these gates for this source slice. The ordinary automated
`android-debug` APK still has an ephemeral signer; it is NOT promised to
update an older APK safely.

## Tests and rollback
- Python unit tests mock ADB and SDK output; they never connect to a phone.
  They verify user refusal, bad preflight, changed file, one-device rule,
  install rejection, bad public readback and command ordering.
- Python source guards prevent adding this script to automatic GitHub
  workflows and prohibit automatic uninstall/clear/downgrade.
- Existing Android/JVM/package/security CI remains required.
- Revert F6-U1 to restore the prior read-only preflight; no on-device state,
  signer, permissions, app identity or package code changes are in this PR.

See [the protected signer setup guide](M3D_OWNER_SIGNING_SETUP.md), [read-only
preflight](M3B_READ_ONLY_DEVICE_UPGRADE_PREFLIGHT.md) and
[issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27).
