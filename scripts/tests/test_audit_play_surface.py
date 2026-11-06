"""Focused unit tests for Play-readiness evidence checks (stdlib only)."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "audit_play_surface.py"
spec = importlib.util.spec_from_file_location("audit_play_surface", MODULE)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class LaunchEvidenceTests(unittest.TestCase):
    def test_aapt_parser_accepts_only_declared_permission_lines(self):
        lines = (
            "package: name='com.edenpractical.comms'\n"
            "uses-permission: name='android.permission.INTERNET'\n"
            "uses-permission: name='android.permission.ACCESS_NETWORK_STATE'\n"
            "uses-permission: name='android.permission.CAMERA'\n"
            "uses-feature: name='android.hardware.camera'\n"
            "uses-permission-sdk-23: name='android.permission.POST_NOTIFICATIONS'\n"
            "implied: android.permission.RECORD_AUDIO (not actually requested)\n"
        )
        self.assertEqual(audit.parse_apk_permissions(lines), {
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA",
            "android.permission.POST_NOTIFICATIONS",
        })

    def test_bad_permission_addition_is_not_in_allowlist(self):
        sample = "uses-permission: name='android.permission.READ_CONTACTS'\n"
        extras = audit.parse_apk_permissions(sample) - audit.ALLOWED_PERMISSIONS
        self.assertEqual(extras, {"android.permission.READ_CONTACTS"})

    def test_only_exact_app_scoped_androidx_receiver_permission_is_allowed(self):
        expected = "com.edenpractical.comms.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        self.assertEqual(audit.GENERATED_MERGED_ONLY_PERMISSIONS, {expected})
        self.assertEqual(
            audit.EXPECTED_APK_PERMISSIONS - audit.ALLOWED_PERMISSIONS,
            {expected, "android.permission.BLUETOOTH_SCAN"}
        )
        extra = "com.otherapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        self.assertNotIn(extra, audit.EXPECTED_APK_PERMISSIONS)
        self.assertNotIn("android.permission.RECORD_AUDIO",
                         audit.EXPECTED_APK_PERMISSIONS)

    def test_direct_dependency_parser_flags_unknown_syntax(self):
        example = '''
dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("com.google.zxing:core:3.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
'''
        self.assertEqual(audit.direct_dependencies(example), sorted([
            "implementation|platform:androidx.compose:compose-bom:2026.09.00",
            "implementation|com.google.zxing:core:3.5.4",
            "debugImplementation|androidx.compose.ui:ui-tooling",
            "testImplementation|junit:junit:4.13.2",
        ]))
        with self.assertRaises(audit.AuditError):
            audit.direct_dependencies(example + '\nimplementation(files("secret.jar"))\n')
        with self.assertRaises(audit.AuditError):
            audit.direct_dependencies(example + '\nimplementation(newSdkAlias)\n')

    def test_manifest_and_backup_rules_parse_expected_policy(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "AndroidManifest.xml"
            manifest.write_text('''<?xml version="1.0"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <application android:allowBackup="false"
        android:usesCleartextTraffic="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules">
        <activity android:name=".MainActivity" android:exported="true" />
    </application>
</manifest>''', encoding="utf-8")
            declared, attrs, components = audit.parse_manifest(manifest)
            self.assertEqual(declared, {"android.permission.CAMERA"})
            self.assertEqual(attrs["allowBackup"], "false")
            self.assertEqual(attrs["usesCleartextTraffic"], "false")
            self.assertEqual(components, [("activity", ".MainActivity")])

            extraction = root / "data_extraction_rules.xml"
            backup = root / "backup_rules.xml"
            extraction.write_text('''<data-extraction-rules>
              <cloud-backup><exclude domain="root" path="." /></cloud-backup>
              <device-transfer><exclude domain="root" path="." /></device-transfer>
            </data-extraction-rules>''', encoding="utf-8")
            backup.write_text('''<full-backup-content>
              <exclude domain="root" path="." />
            </full-backup-content>''', encoding="utf-8")
            self.assertTrue(audit.backup_restrictions_ok(extraction, backup))
            extraction.write_text('''<data-extraction-rules>
              <cloud-backup><exclude domain="root" path="." /></cloud-backup>
            </data-extraction-rules>''', encoding="utf-8")
            self.assertFalse(audit.backup_restrictions_ok(extraction, backup))

    def test_current_app_gradle_dependencies_match_checked_in_baseline(self):
        repo = Path(__file__).resolve().parents[2]
        direct = audit.direct_dependencies(
            (repo / "app/build.gradle.kts").read_text(encoding="utf-8")
        )
        baseline = audit.approved_dependencies(
            repo / "scripts/play_direct_dependency_baseline.txt"
        )
        self.assertEqual(direct, baseline)

    def test_report_does_not_claim_compliance(self):
        example = {
            "build_sha": "test",
            "apk_sha256": "aa" * 32,
            "status": "PASS_STATIC_INVENTORY",
            "apk_permissions": sorted(audit.ALLOWED_PERMISSIONS),
            "backup_exclusion_rules_present": True,
            "direct_gradle_dependencies": [],
            "problems": [],
        }
        report = audit.report_markdown(example)
        self.assertIn("Not legal clearance", report)
        self.assertIn("Mandatory human verification", report)


if __name__ == "__main__":
    unittest.main()
