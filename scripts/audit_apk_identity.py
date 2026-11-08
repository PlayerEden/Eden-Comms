#!/usr/bin/env python3
"""Inspect an APK's declared ID/version and verified signing certificate.

Development evidence only. A matching package name alone never proves that an
APK can update an installed copy. No private key, signature material, or
sensitive device information is read or included in reports.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


class IdentityAuditError(ValueError):
    pass


def unique_match(pattern, text, label):
    matches = re.findall(pattern, text, flags=re.MULTILINE)
    if len(matches) != 1:
        raise IdentityAuditError("Expected exactly one " + label)
    return matches[0]


def source_identity(gradle_text):
    return {
        "application_id": unique_match(
            r'^\s*applicationId\s*=\s*"([^"]+)"\s*$', gradle_text, "source applicationId"),
        "version_code": int(unique_match(
            r'^\s*versionCode\s*=\s*(\d+)\s*$', gradle_text, "source versionCode")),
        "version_name": unique_match(
            r'^\s*versionName\s*=\s*"([^"]+)"\s*$', gradle_text, "source versionName"),
    }


def expected_identity(gradle_text, internal_test_version_code=None,
                      offline_preview_version_code=None):
    expected = source_identity(gradle_text)
    if internal_test_version_code is not None and offline_preview_version_code is not None:
        raise IdentityAuditError("Public preview and private internal-test builds cannot mix")
    if offline_preview_version_code is not None:
        if (type(offline_preview_version_code) is not int or
                not 200_001 <= offline_preview_version_code <= 2_100_000_000):
            raise IdentityAuditError("Invalid offline preview versionCode")
        expected["application_id"] += ".preview"
        expected["version_code"] = offline_preview_version_code
        expected["version_name"] = (
            expected["version_name"].split("-")[0] + "-preview-" +
            str(offline_preview_version_code)
        )
        return expected
    if internal_test_version_code is None:
        return expected
    if (type(internal_test_version_code) is not int or
            not expected["version_code"] < internal_test_version_code <= 2_100_000_000):
        raise IdentityAuditError("Invalid or non-incrementing internal test versionCode")
    expected["version_code"] = internal_test_version_code
    base_version = expected["version_name"].split("-")[0]
    expected["version_name"] = base_version + "-internal-" + str(internal_test_version_code)
    return expected


def parse_apk_badging(output):
    match = re.search(
        r"(?m)^package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'",
        output,
    )
    if not match:
        raise IdentityAuditError("aapt did not report an unambiguous APK identity")
    return {
        "application_id": match.group(1),
        "version_code": int(match.group(2)),
        "version_name": match.group(3),
    }


def parse_signer_fingerprint(output):
    matches = re.findall(
        r"(?m)^Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$",
        output,
    )
    if len(matches) != 1 or matches[0][0] != "1":
        raise IdentityAuditError("Expected exactly one verified APK signer")
    fingerprint = matches[0][1].replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", fingerprint):
        raise IdentityAuditError("Invalid signing certificate SHA-256 fingerprint")
    return fingerprint


def sdk_tool(name):
    binary = shutil.which(name)
    if binary:
        return binary
    home = os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")
    if home:
        path = Path(home) / "build-tools" / "36.0.0" / name
        if path.is_file():
            return str(path)
    raise IdentityAuditError(name + " not found in Android SDK build tools")


def tool_output(command):
    try:
        result = subprocess.run(
            command, check=True, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, timeout=45,
        )
        return result.stdout
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        raise IdentityAuditError("APK inspection/verification tool failed") from exc


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect(apk, gradle_text, build_sha,
            internal_test_version_code=None, expected_signer_sha256=None,
            offline_preview_version_code=None):
    if not apk.is_file():
        raise IdentityAuditError("APK missing")
    observed = parse_apk_badging(tool_output([sdk_tool("aapt"), "dump", "badging", str(apk)]))
    expected = expected_identity(
        gradle_text, internal_test_version_code, offline_preview_version_code
    )
    if observed != expected:
        raise IdentityAuditError("Packaged APK identity/version differs from Gradle source")
    signer_output = tool_output(
        [sdk_tool("apksigner"), "verify", "--print-certs", str(apk)]
    )
    fingerprint = parse_signer_fingerprint(signer_output)
    if expected_signer_sha256 is not None:
        if (not re.fullmatch(r"[0-9a-fA-F]{64}", expected_signer_sha256) or
                fingerprint != expected_signer_sha256.lower()):
            raise IdentityAuditError("APK signer does not match the expected certificate pin")
    return {
        "schema": "eden-comms.debug-apk-identity.v1",
        "source_commit": build_sha,
        **observed,
        "apk_sha256": sha256(apk),
        "signer_certificate_sha256": fingerprint,
        "signer_continuity_verified": False,
        "configured_signer_fingerprint_matched": expected_signer_sha256 is not None,
        "signing_profile": ("public-offline-preview" if offline_preview_version_code is not None
                            else "pinned-internal-test" if expected_signer_sha256 is not None
                            else "ordinary-debug"),
        "install_safety_note": (
            "A configured signer match does NOT verify compatibility with an "
            "already installed APK. Compare both signer certificates and "
            "versionCodes before updating. Uninstalling can erase app-local "
            "settings and Android Keystore identity."
        ),
    }


def compare_to_previous(current, previous):
    """Fail closed for in-place update *candidates*; this is not a device test."""
    if previous.get("schema") != current["schema"]:
        raise IdentityAuditError("Previous APK identity evidence schema incompatible")
    if current["application_id"] != previous.get("application_id"):
        raise IdentityAuditError("Update candidate changes Android application ID")
    if current["signer_certificate_sha256"] != previous.get("signer_certificate_sha256"):
        raise IdentityAuditError("Update candidate changes signing certificate")
    previous_code = previous.get("version_code")
    if type(previous_code) is not int or current["version_code"] <= previous_code:
        raise IdentityAuditError("Update candidate must increase versionCode")
    return True


def markdown(report):
    return "\n".join([
        "# Eden Comms — debug APK identity evidence",
        "",
        "**This verifies the current APK, not compatibility with an existing install.**",
        "",
        "- Source commit: `" + report["source_commit"] + "`",
        "- Package: `" + report["application_id"] + "`",
        "- Version: `" + report["version_name"] + "` (code " + str(report["version_code"]) + ")",
        "- APK SHA-256: `" + report["apk_sha256"] + "`",
        "- APK signing certificate SHA-256: `" + report["signer_certificate_sha256"] + "`",
        "- Signing profile: `" + report.get("signing_profile", "ordinary-debug") + "`",
        "- Configured internal test signer matched: " +
            ("YES" if report.get("configured_signer_fingerprint_matched") else "NOT CHECKED"),
        "- Existing-install signer continuity: **NOT VERIFIED**",
        "",
        "## Before attempting an Android in-place update",
        "",
        "Compare this package ID, signer certificate and versionCode against the",
        "installed app's originating APK or prior artifact metadata. Android may",
        "reject mismatched signatures. Do **not** uninstall just to bypass that",
        "rejection if settings/Keystore identity must be preserved.",
        "",
        "A successful static comparison is not a substitute for a physical upgrade test.",
        "Secrets and private signing keys are never stored in this report.",
        "",
    ]) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--previous", type=Path, help="Optional earlier identity JSON for fail-closed update comparison")
    parser.add_argument("--internal-test-version-code", type=int,
                        help="Strict versionCode override used only by manually signed internal APK builds")
    parser.add_argument("--offline-preview-version-code", type=int,
                        help="Distinct .preview package using a PUBLIC fixed development signer, never release")
    parser.add_argument("--expected-signer-sha256",
                        help="Fail if APK signer does not match the configured public certificate fingerprint")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    try:
        report = inspect(
            args.apk,
            (repo / "app/build.gradle.kts").read_text(encoding="utf-8"),
            os.environ.get("GITHUB_SHA", "local-unassigned"),
            internal_test_version_code=args.internal_test_version_code,
            expected_signer_sha256=args.expected_signer_sha256,
            offline_preview_version_code=args.offline_preview_version_code,
        )
        if args.previous is not None:
            previous = json.loads(args.previous.read_text(encoding="utf-8"))
            compare_to_previous(report, previous)
            report["update_candidate_static_comparison"] = "PASS"
        args.output_dir.mkdir(parents=True, exist_ok=True)
        (args.output_dir / "apk-identity.json").write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8",
        )
        (args.output_dir / "apk-identity.md").write_text(
            markdown(report), encoding="utf-8",
        )
        print(markdown(report))
        return 0
    except (IdentityAuditError, OSError, ValueError, json.JSONDecodeError) as exc:
        print("FAIL: APK identity evidence unavailable: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
