#!/usr/bin/env python3
"""Owner-invoked *optional* safe install-over of an already installed Eden Comms APK.

This command is never run by CI, never uninstalls, never clears app data, and
never claims that Android Keystore identity survived. It requires an actual
authorized USB/ADB device, exact package/signing/version preflight, an exact
human confirmation, and a read-back of the installed public APK metadata.

It intentionally does not support fresh installs or signer transitions.
"""
import argparse
import re
import sys
import tempfile
from pathlib import Path

from audit_apk_identity import IdentityAuditError, sha256, sdk_tool
from preflight_device_apk_update import (
    APP_ID, apk_identity, choose_device, command_output, parse_installed_base_path,
    preflight,
)

CONSENT_PREFIX = "INSTALL OVER "
MAX_APK_BYTES = 400_000_000


def confirmed_install(
    apk, requested_serial=None, *, runner=command_output,
    find_sdk=sdk_tool, ask=input, preflight_check=preflight,
):
    """Install only with one checked phone, two read-only checks and literal consent.

    The public read-back is NOT a private identity/settings preservation test.
    Caller supplies injectable command adapters for no-device CI tests.
    """
    apk = Path(apk)
    if not apk.is_file() or not 1 <= apk.stat().st_size <= MAX_APK_BYTES:
        raise IdentityAuditError("Missing, empty or oversized candidate APK")
    # Select a specific device once. No silent switching between USB devices.
    serial = choose_device(runner(["adb", "devices"]), requested_serial)
    # This already verifies exact package, installed signer and strictly
    # increasing versionCode. The installed APK is inspected in temp storage.
    initial, initial_sha = preflight_check(
        apk, requested_serial=serial, runner=runner, find_sdk=find_sdk
    )
    expected = {
        "package": APP_ID,
        "installed_version_code": initial["installed_version_code"],
        "candidate_version_code": initial["candidate_version_code"],
        "certificate_sha256": initial["certificate_sha256"],
    }
    if initial.get("package") != APP_ID or any(
        type(expected[key]) is not int
        for key in ("installed_version_code", "candidate_version_code")
    ) or not (
        0 < expected["installed_version_code"] <
        expected["candidate_version_code"] <= 2_100_000_000
    ):
        raise IdentityAuditError("Invalid preflight compatibility evidence")
    if not (isinstance(initial_sha, str) and
            re.fullmatch(r"[0-9a-f]{64}", initial_sha)):
        raise IdentityAuditError("Candidate APK SHA-256 unavailable")
    if not (isinstance(expected["certificate_sha256"], str) and
            re.fullmatch(r"[0-9a-f]{64}", expected["certificate_sha256"])):
        raise IdentityAuditError("Verified APK signing fingerprint unavailable")

    print("READ-ONLY PREFLIGHT PASSED — NO CHANGES YET.")
    print("Package:", APP_ID)
    print("Installed code:", expected["installed_version_code"])
    print("Candidate code:", expected["candidate_version_code"])
    print("Matching public signer SHA-256:", expected["certificate_sha256"])
    print("Candidate APK SHA-256:", initial_sha)
    print("WARNING: matching signatures/versions do not prove a safe update or")
    print("preservation of local Keystore identity, chats or preferences.")
    print("This action will replace the installed app binary without uninstalling.")
    print("A signing mismatch MUST NOT be fixed by uninstalling automatically.")
    required = CONSENT_PREFIX + APP_ID + " " + str(expected["candidate_version_code"])
    if ask("To authorize one in-place install, type exactly '" +
           required + "': ").strip() != required:
        raise IdentityAuditError("No exact update approval; nothing was installed")

    # Fail closed if selected phone was disconnected or its installed package
    # changed while the human considered the prompt. Also check the bytes of
    # the APK itself did not change between preflight and actual installation.
    choose_device(runner(["adb", "devices"]), serial)
    repeat, repeat_sha = preflight_check(
        apk, requested_serial=serial, runner=runner, find_sdk=find_sdk
    )
    if repeat_sha != initial_sha or sha256(apk) != initial_sha or any(
        repeat.get(k) != v for k, v in expected.items()
    ):
        raise IdentityAuditError(
            "Candidate/device changed after approval; update aborted before install"
        )

    # Never use uninstall, pm clear, downgrade flags or install-multiple.
    output = runner(["adb", "-s", serial, "install", "-r", str(apk)])
    if not output.strip().splitlines() or output.strip().splitlines()[-1].strip() != "Success":
        raise IdentityAuditError(
            "Android did not confirm install success. Do not uninstall or reset app data"
        )

    # Verify installed public package metadata after the actual install.
    choose_device(runner(["adb", "devices"]), serial)
    pm_output = runner(["adb", "-s", serial, "shell", "pm", "path", APP_ID])
    remote_base = parse_installed_base_path(pm_output)
    with tempfile.TemporaryDirectory(prefix="eden-comms-upgrade-check-") as folder:
        local_base = Path(folder) / "installed-base.apk"
        runner(["adb", "-s", serial, "pull", remote_base, str(local_base)])
        actual = apk_identity(local_base, runner=runner, find_sdk=find_sdk)

    if (actual.get("application_id") != APP_ID or
            actual.get("version_code") != expected["candidate_version_code"] or
            actual.get("signer_certificate_sha256") !=
            expected["certificate_sha256"]):
        raise IdentityAuditError(
            "Post-install public package identity did not match the candidate. "
            "Do not uninstall or retry blindly; inspect the phone manually"
        )
    return {
        "package": APP_ID,
        "previous_version_code": expected["installed_version_code"],
        "installed_version_code": expected["candidate_version_code"],
        "signer_certificate_sha256": expected["certificate_sha256"],
        "candidate_apk_sha256": initial_sha,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Explicit owner-authorized Eden Comms install-over; NEVER uninstalls"
    )
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument(
        "--serial", help="Exact authorized adb devices entry if more than one device"
    )
    args = parser.parse_args(argv)
    try:
        report = confirmed_install(args.apk, args.serial)
    except (IdentityAuditError, OSError, KeyError, ValueError) as exc:
        print("UPDATE NOT VERIFIED: " + str(exc), file=sys.stderr)
        print("No automatic uninstall, reset, downgrade, or cleanup was attempted.",
              file=sys.stderr)
        return 1
    print("ANDROID IN-PLACE INSTALL + PUBLIC PACKAGE READ-BACK PASSED.")
    print("Package:", report["package"])
    print("Installed versionCode:", report["installed_version_code"])
    print("Public signer SHA-256:", report["signer_certificate_sha256"])
    print("Installed APK SHA-256 evidence:", report["candidate_apk_sha256"])
    print("NOT VERIFIED: local Android Keystore identity, preferences, session")
    print("continuity, peer QR identity, device-specific or production security.")
    print("Manually compare the exact pre/post identity and settings on THIS phone")
    print("before considering the upgrade accepted; repeat separately on phone B.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
