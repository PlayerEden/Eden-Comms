#!/usr/bin/env python3
"""Strict, source-backed evidence for Play disclosure review (NOT certification).

Run: python3 scripts/audit_play_surface.py --apk app/build/outputs/apk/debug/app-debug.apk --output-dir build/play-evidence
Standard-library only. The artifact is deliberately public/non-sensitive.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
ALLOWED_PERMISSIONS = frozenset({
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CAMERA",
})
# AndroidX Core merges a self-declared signature-level permission to guard
# non-exported dynamic broadcast receivers on older Android versions.
# This exact applicationId-bound name is expected in the packaged APK ONLY.
# A different generated permission or arbitrary Android permission still fails.
GENERATED_MERGED_ONLY_PERMISSIONS = frozenset({
    "com.edenpractical.comms.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
})
# Android 12+ experimental Classic inquiry and one-time secure RFCOMM link
# (CONNECT and explicit discoverability ADVERTISE) exist in debug ONLY.
# M3p adds debug Wi-Fi state and version-scoped Wi-Fi Direct permission.
# M6-D1 adds only debug RECORD_AUDIO for explicit local mic/speaker playback;
# production main manifest remains at original LAN+QR permissions.
DEBUG_ONLY_PERMISSIONS = frozenset({
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.BLUETOOTH_ADVERTISE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.NEARBY_WIFI_DEVICES",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.CHANGE_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.RECORD_AUDIO",
})
EXPECTED_APK_PERMISSIONS = (
    ALLOWED_PERMISSIONS | DEBUG_ONLY_PERMISSIONS | GENERATED_MERGED_ONLY_PERMISSIONS
)
SCOPES = ("implementation", "debugImplementation", "testImplementation",
          "releaseImplementation", "api", "runtimeOnly", "compileOnly")


class AuditError(ValueError):
    pass


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def parse_apk_permissions(aapt_output):
    """Only manifest permissions, never unrelated mentions in package output."""
    return set(re.findall(
        r"^\s*uses-permission(?:-sdk-\d+)?:\s+name='([^']+)'",
        aapt_output, flags=re.MULTILINE,
    ))


def parse_manifest(path):
    root = ET.parse(path).getroot()
    declared = {
        item.attrib[ANDROID + "name"] for item in root
        if item.tag in {"uses-permission", "uses-permission-sdk-23"}
        and ANDROID + "name" in item.attrib
    }
    app = root.find("application")
    if app is None:
        raise AuditError("AndroidManifest lacks application")
    attrs = {
        "allowBackup": app.get(ANDROID + "allowBackup"),
        "usesCleartextTraffic": app.get(ANDROID + "usesCleartextTraffic"),
        "dataExtractionRules": app.get(ANDROID + "dataExtractionRules"),
        "fullBackupContent": app.get(ANDROID + "fullBackupContent"),
    }
    components = []
    for typ in ("activity", "activity-alias", "service", "receiver", "provider"):
        for item in app.findall(typ):
            if item.get(ANDROID + "exported") == "true":
                components.append((typ, item.get(ANDROID + "name", "<unnamed>")))
    return declared, attrs, components


def backup_restrictions_ok(data_extraction_path, backup_rules_path):
    def excluded(parent, tag):
        section = parent.find(tag)
        return section is not None and any(
            item.tag == "exclude" and item.get("domain") == "root"
            and item.get("path") == "." for item in section
        )
    try:
        extraction = ET.parse(data_extraction_path).getroot()
        legacy = ET.parse(backup_rules_path).getroot()
    except ET.ParseError as exc:
        raise AuditError("Malformed Android backup restrictions") from exc
    return (
        excluded(extraction, "cloud-backup")
        and excluded(extraction, "device-transfer")
        and any(item.tag == "exclude" and item.get("domain") == "root"
                and item.get("path") == "." for item in legacy)
    )


def direct_dependencies(build_text):
    """Capture ALL direct configuration calls in this project's Gradle DSL."""
    aliases = dict(re.findall(
        r"\bval\s+(\w+)\s*=\s*platform\(\s*\"([^\"]+)\"\s*\)", build_text
    ))
    calls = re.findall(
        r"(?m)^\s*(" + "|".join(SCOPES) + r")\s*\(\s*(\"[^\"]+\"|\w+)\s*\)",
        build_text,
    )
    all_calls = re.findall(
        r"(?m)^\s*(?:" + "|".join(SCOPES) + r")\s*\(", build_text
    )
    if len(calls) != len(all_calls):
        raise AuditError("Unrecognized Gradle dependency notation: review required")
    records = set()
    for scope, expr in calls:
        if expr.startswith('"'):
            coord = expr[1:-1]
        elif expr in aliases:
            coord = "platform:" + aliases[expr]
        else:
            raise AuditError("Unrecognized Gradle dependency alias: review required")
        records.add(scope + "|" + coord)
    return sorted(records)


def approved_dependencies(path):
    return sorted({
        line.strip() for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    })


def aapt_permissions(apk):
    binary = shutil.which("aapt")
    if not binary:
        home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if home:
            binary = str(Path(home) / "build-tools" / "36.0.0" / "aapt")
    if not binary or not Path(binary).is_file():
        raise AuditError("Android aapt unavailable: cannot examine packaged APK")
    try:
        output = subprocess.run([binary, "dump", "permissions", str(apk)],
                                text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, check=True, timeout=30)
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        raise AuditError("Unable to examine packaged APK permissions") from exc
    return parse_apk_permissions(output.stdout)


def build_report(repo, apk):
    manifest = repo / "app/src/main/AndroidManifest.xml"
    declared, attrs, components = parse_manifest(manifest)
    debug_declared, _, debug_exported = parse_manifest(
        repo / "app/src/debug/AndroidManifest.xml"
    )
    binary = aapt_permissions(apk)
    deps = direct_dependencies((repo / "app/build.gradle.kts").read_text(encoding="utf-8"))
    baseline = approved_dependencies(repo / "scripts/play_direct_dependency_baseline.txt")
    backup_ok = backup_restrictions_ok(
        repo / "app/src/main/res/xml/data_extraction_rules.xml",
        repo / "app/src/main/res/xml/backup_rules.xml",
    )
    problems = []
    if declared != ALLOWED_PERMISSIONS:
        problems.append("Release/main manifest permissions differ from approved set")
    if debug_declared != DEBUG_ONLY_PERMISSIONS or debug_exported:
        problems.append("Debug-only radio/local microphone manifest differs from approved set")
    if binary != EXPECTED_APK_PERMISSIONS:
        problems.append("Packaged debug APK permissions differ from approved merged set")
    if binary - declared != (DEBUG_ONLY_PERMISSIONS | GENERATED_MERGED_ONLY_PERMISSIONS):
        problems.append("Unexpected manifest-merge permission addition or removal")
    if deps != baseline:
        problems.append("Direct dependency baseline changed: manual privacy/license review required")
    if attrs["allowBackup"] != "false" or attrs["usesCleartextTraffic"] != "false":
        problems.append("Backup or cleartext network restriction changed")
    if attrs["dataExtractionRules"] != "@xml/data_extraction_rules" or attrs[
        "fullBackupContent"] != "@xml/backup_rules":
        problems.append("Backup/data transfer exclusion references changed")
    if not backup_ok:
        problems.append("Root cloud/device-transfer or legacy backup exclusions missing")
    if components != [("activity", ".MainActivity")]:
        problems.append("Exported application component inventory changed")
    report = {
        "kind": "Eden Comms Play-readiness evidence (not a compliance certification)",
        "build_sha": os.environ.get("GITHUB_SHA", "local-unassigned"),
        "apk_sha256": digest(apk),
        "source_permissions": sorted(declared),
        "apk_permissions": sorted(binary),
        "approved_source_permissions": sorted(ALLOWED_PERMISSIONS),
        "approved_debug_only_permissions": sorted(DEBUG_ONLY_PERMISSIONS),
        "actual_debug_only_permissions": sorted(debug_declared),
        "expected_androidx_merged_only_permission": sorted(GENERATED_MERGED_ONLY_PERMISSIONS),
        "application_settings": attrs,
        "exported_main_components": [
            {"type": typ, "name": name} for typ, name in components
        ],
        "backup_exclusion_rules_present": backup_ok,
        "direct_gradle_dependencies": deps,
        "direct_dependency_baseline_matches": deps == baseline,
        "runtime_dependency_tree": "See separately generated releaseRuntimeClasspath.txt",
        "review_outstanding": [
            "This does NOT detect SDK data transfers, prove no off-device collection, "
            "grant export classification, or determine Google Play Data safety answers.",
            "Review release-build merged manifest and AAB before publication; current artifact is debug APK.",
            "Audit transitive dependency licenses, data collection, and SDK privacy practices manually.",
            "Run two-phone no-WAN and hostile-LAN tests before claiming secure offline communication.",
        ],
        "problems": problems,
        "status": "PASS_STATIC_INVENTORY" if not problems else "REVIEW_REQUIRED",
    }
    return report


def report_markdown(report):
    lines = [
        "# Eden Comms — Android Play launch-surface evidence",
        "",
        "**Evidence only. Not legal clearance, a completed Data safety form or a secure-product audit.**",
        "",
        "- Source commit (CI context): `" + report["build_sha"] + "`",
        "- APK SHA-256: `" + report["apk_sha256"] + "`",
        "- Status: **" + report["status"] + "**",
        "- Packaged APK permissions: " + ", ".join(report["apk_permissions"]),
        "- AndroidX merged-only permission: " + ", ".join(
            report.get("expected_androidx_merged_only_permission", [])),
        "- Backup exclusion rules: " + ("found" if report["backup_exclusion_rules_present"] else "MISSING"),
        "- Direct Gradle dependencies: " + str(len(report["direct_gradle_dependencies"])),
        "",
        "## Direct dependency inventory (not a license approval)",
        "",
    ]
    lines += ["- `" + d + "`" for d in report["direct_gradle_dependencies"]]
    lines += [
        "",
        "## Mandatory human verification before Play publication",
        "",
        "- Actual data flows including any SDK collection/sharing and Android runtime behavior",
        "- Transitive dependencies, licenses, vulnerabilities, cryptographic export classification",
        "- Public privacy policy and Play Data safety declarations for the final published build",
        "- Production AAB merged-manifest review, app signing, physical device and security testing",
        "",
        "## Check findings",
        "",
    ]
    lines += ["- " + p for p in report["problems"]] or ["- No unexpected items **within these limited checks**."]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    try:
        report = build_report(repo, args.apk)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        (args.output_dir / "launch-surface.json").write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        (args.output_dir / "launch-surface.md").write_text(
            report_markdown(report), encoding="utf-8")
        print(report_markdown(report))
        if report["problems"]:
            return 1
        return 0
    except (AuditError, OSError, ET.ParseError) as exc:
        print("FAIL: Play readiness evidence unavailable: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
