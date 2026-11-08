"""Preview workflow contract: repeatable development-only signature, never release."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/build.gradle.kts"
WORKFLOW = ROOT / ".github/workflows/offline-preview-apk.yml"
MAIN_MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
PUBLIC_REPORT = ROOT / "app/src/main/java/com/edenpractical/comms/pairing/BuildEvidenceReport.kt"


class OfflinePreviewTest(unittest.TestCase):
    def test_build_is_distinct_debug_app_and_requires_explicit_opt_in(self):
        code = APP.read_text()
        self.assertIn('applicationId = "com.edenpractical.comms"', code)
        self.assertIn('val offlinePreview = providers.gradleProperty("edenOfflinePreview").orNull', code)
        self.assertIn('applicationIdSuffix = ".preview"', code)
        self.assertIn('manifestPlaceholders["edenAppLabel"] = "Eden Comms Preview"', code)
        self.assertIn('require(offlinePreviewCode == null || internalTestVersionCode == null)', code)
        self.assertIn('require((offlinePreview == "true") == (offlinePreviewCodeText != null))', code)
        self.assertIn('defaultConfig.versionCode = offlinePreviewCode', code)
        self.assertIn('buildTypes {\n        debug {', code)
        self.assertIn('android:label="${edenAppLabel}"', MAIN_MANIFEST.read_text())

    def test_preview_workflow_uses_immutable_public_test_signer_and_distinct_identity(self):
        workflow = WORKFLOW.read_text()
        for required in (
            "workflow_dispatch:",
            "github.ref == 'refs/heads/main'",
            'cancel-in-progress: false',
            'GITHUB_RUN_ATTEMPT',
            '200000 + GITHUB_RUN_NUMBER',
            'edenOfflinePreview=true',
            'edenOfflinePreviewVersionCode=$EDEN_PREVIEW_CODE',
            'com.edenpractical.comms.preview',
            'testkey.pk8',
            'testkey.x509.pem',
            '586c1bd5cf96f9358f36b37ea98fef93f4d0a8e3',
            'e242d83e2bf72169ab0abd5280d4d455caef71eb',
            'scripts/sign_internal_test_apk.sh',
            '--offline-preview-version-code "$EDEN_PREVIEW_CODE"',
            '--expected-signer-sha256 "$signer"',
            'scripts/check_security_review_boundary.sh',
            'scripts/check_preview_permissions.sh',
            'dist/eden-comms-preview-*.apk',
        ):
            self.assertIn(required, workflow)
        self.assertNotIn("${{ secrets.", workflow)
        self.assertNotIn("pull_request_target", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertNotIn("app:assembleRelease", workflow)
        self.assertNotIn("--internal-test-version-code", workflow)
        self.assertNotIn("EC_INTERNAL_CERT_SHA256", workflow)

    def test_copied_installed_evidence_uses_exact_runtime_package(self):
        source = (ROOT / "app/src/main/java/com/edenpractical/comms/pairing/"
                  "InstalledBuildEvidence.kt").read_text()
        report = PUBLIC_REPORT.read_text()
        self.assertIn('BuildConfig.APPLICATION_ID', source)
        self.assertIn('applicationId = packageName', source)
        self.assertIn('it == "com.edenpractical.comms.preview"', report)
        self.assertIn('"App package: ${packageId(evidence.applicationId)}"', report)

    def test_releases_and_capability_flags_stay_separate(self):
        code = APP.read_text()
        flags = (ROOT / "app/src/main/java/com/edenpractical/comms/model/"
                 "ReleaseCapabilities.kt").read_text()
        for name in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn(f"const val {name} = false", flags)
        # This is opt-in 'debug' ONLY, not release-app signing or production
        # distribution. No private production keys, and no root app rename.
        self.assertLess(code.index('applicationIdSuffix = ".preview"'),
                        code.index("release {"))
        self.assertIn('release {', code)


if __name__ == "__main__":
    unittest.main()
