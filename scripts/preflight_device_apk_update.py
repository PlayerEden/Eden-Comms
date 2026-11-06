#!/usr/bin/env python3
"""Read-only Android device update preflight for Eden Comms test APKs.

Compares a candidate APK to the APK actually installed in the selected
Android user/profile via ADB. Does not install, uninstall, alter app data,
export Android Keystore material, or attest that an update will succeed.

Only the installed public base APK is temporarily pulled for package/signing
inspection; its temporary local copy is deleted automatically. Any ambiguity
or unavailable evidence fails closed.
"""
import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from audit_apk_identity import (
    IdentityAuditError,
    parse_apk_badging,
    parse_signer_fingerprint,
    sdk_tool,
    sha256,
)

APP_ID = "com.edenpractical.comms"


def command_output(command):
    """Never print raw ADB/SDK stderr, which may contain private device paths."""
    try:
        completed = subprocess.run(
            command, check=True, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, timeout=60,
        )
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        raise IdentityAuditError(
            "Android device inspection command failed; nothing was installed or removed"
        ) from exc
    if len(completed.stdout) > 1_000_000:
        raise IdentityAuditError("Unexpectedly large device inspection output")
    return completed.stdout


def choose_device(devices_output, selected=None):
    """A unique authorized ADB device, or an explicitly selected one."""
    lines = devices_output.splitlines()
    if not lines or lines[0].strip() != "List of devices attached":
        raise IdentityAuditError("Could not parse adb devices; check Android Platform Tools")
    devices = {}
    for line in lines[1:]:
        if not line.strip() or line.startswith("*"):
            continue
        fields = line.split()
        if len(fields) < 2:
            raise IdentityAuditError("Malformed ADB device listing")
        serial, state = fields[0], fields[1]
        if serial in devices:
            raise IdentityAuditError("Duplicate ADB device entry")
        devices[serial] = state

    if selected is not None:
        if selected not in devices or devices[selected] != "device":
            raise IdentityAuditError(
                "The explicitly selected ADB device is not connected and authorized"
            )
        return selected

    ready = [serial for serial, state in devices.items() if state == "device"]
    if len(devices) != 1 or len(ready) != 1:
        raise IdentityAuditError(
            "Connect and authorize exactly one phone, or pass --serial from adb devices"
        )
    return ready[0]


def parse_installed_base_path(pm_output):
    """Find exactly one installed base APK, never arbitrary user/app paths."""
    paths = []
    for line in pm_output.splitlines():
        line = line.strip()
        if not line.startswith("package:"):
            continue
        path = line[len("package:"):]
        if (path.startswith("/") and path.endswith("/base.apk") and
                "\\" not in path and ".." not in path.split("/") and
                not any(ch.isspace() for ch in path)):
            paths.append(path)
    if len(paths) != 1:
        raise IdentityAuditError(
            "The selected Android user/profile has no uniquely readable Eden Comms base APK; "
            "do NOT uninstall to bypass this check"
        )
    return paths[0]


def apk_identity(apk, runner=command_output, find_sdk=sdk_tool):
    """Inspect APK metadata and verified certificate without reading app data."""
    apk = Path(apk)
    if not apk.is_file() or apk.stat().st_size == 0:
        raise IdentityAuditError("APK is missing or empty")
    badge = runner([find_sdk("aapt"), "dump", "badging", str(apk)])
    signer = runner([find_sdk("apksigner"), "verify", "--print-certs", str(apk)])
    return {
        **parse_apk_badging(badge),
        "signer_certificate_sha256": parse_signer_fingerprint(signer),
    }


def compare_update(installed, candidate):
    """A safe candidate requires same package/certificate and higher version."""
    if candidate.get("application_id") != APP_ID:
        raise IdentityAuditError("Candidate APK is not the expected Eden Comms package")
    if installed.get("application_id") != APP_ID:
        raise IdentityAuditError("Installed package identity does not match Eden Comms")
    current_cert = installed.get("signer_certificate_sha256")
    next_cert = candidate.get("signer_certificate_sha256")
    if not (isinstance(current_cert, str) and isinstance(next_cert, str) and
            re.fullmatch(r"[0-9a-f]{64}", current_cert) and
            re.fullmatch(r"[0-9a-f]{64}", next_cert)):
        raise IdentityAuditError("Verified signer evidence unavailable")
    if current_cert != next_cert:
        raise IdentityAuditError(
            "BLOCKED: signing certificates differ. An in-place update is not "
            "established. Do NOT uninstall: settings and local Keystore identity "
            "may be lost. Obtain a compatible APK or plan a separately consented reset."
        )
    old_version = installed.get("version_code")
    new_version = candidate.get("version_code")
    if (type(old_version) is not int or type(new_version) is not int or
            old_version < 1 or new_version <= old_version):
        raise IdentityAuditError(
            "BLOCKED: the candidate versionCode is not higher than the installed version"
        )
    return {
        "package": APP_ID,
        "installed_version": installed.get("version_name", "unknown"),
        "installed_version_code": old_version,
        "candidate_version": candidate.get("version_name", "unknown"),
        "candidate_version_code": new_version,
        "certificate_sha256": next_cert,
    }


def preflight(candidate_apk, requested_serial=None,
              runner=command_output, find_sdk=sdk_tool):
    candidate_apk = Path(candidate_apk)
    # Reject a bad candidate before touching a USB-connected device.
    candidate = apk_identity(candidate_apk, runner, find_sdk)
    if candidate["application_id"] != APP_ID:
        raise IdentityAuditError("Candidate APK is not the expected Eden Comms package")

    serial = choose_device(runner(["adb", "devices"]), requested_serial)
    pm_output = runner(["adb", "-s", serial, "shell", "pm", "path", APP_ID])
    remote_base = parse_installed_base_path(pm_output)

    # The public APK is not an app data directory, secret or Android Keystore
    # export. No persistent device-identifying report is generated.
    with tempfile.TemporaryDirectory(prefix="eden-comms-apk-preflight-") as temporary:
        installed_base = Path(temporary) / "installed-base.apk"
        runner(["adb", "-s", serial, "pull", remote_base, str(installed_base)])
        installed = apk_identity(installed_base, runner, find_sdk)

    return compare_update(installed, candidate), sha256(candidate_apk)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Read-only preflight; NEVER installs or uninstalls Eden Comms"
    )
    parser.add_argument("--apk", required=True, type=Path,
                        help="New Android test APK you intend to install")
    parser.add_argument("--serial", help="ADB device serial; needed if multiple devices connected")
    options = parser.parse_args(argv)

    try:
        result, apk_sha = preflight(options.apk, options.serial)
    except (IdentityAuditError, OSError) as exc:
        print("FAIL / DO NOT UPDATE: " + str(exc), file=sys.stderr)
        return 1

    print("PASS — STATIC, READ-ONLY UPDATE PREFLIGHT (NOT A DEVICE UPDATE TEST)")
    print("Package: " + result["package"])
    print("Installed: " + result["installed_version"] +
          " (code " + str(result["installed_version_code"]) + ")")
    print("Candidate: " + result["candidate_version"] +
          " (code " + str(result["candidate_version_code"]) + ")")
    print("Matching certificate SHA-256: " + result["certificate_sha256"])
    print("Candidate APK SHA-256: " + apk_sha)
    print("No app install, uninstall, identity reset or data modification occurred.")
    print("An actual in-place install and identity-preservation check still require "
          "hands-on testing. Keep the old APK and its signer evidence.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
