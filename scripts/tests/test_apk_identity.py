"""Pure unit tests; never require Android SDK or a device."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audit_apk_identity as mod


EXAMPLE_GRADLE = """
android {
    defaultConfig {
        applicationId = "com.edenpractical.comms"
        versionCode = 11
        versionName = "0.2.12-approval-review-only"
    }
}
"""
EXAMPLE_AAPT = """package: name='com.edenpractical.comms' versionCode='11' versionName='0.2.12-approval-review-only' platformBuildVersionName='17'
sdkVersion:'26'
"""
FINGERPRINT = "A1" * 32
OTHER_FINGERPRINT = "B2" * 32


class ApkIdentityEvidenceTests(unittest.TestCase):
    def test_parses_exact_source_identity(self):
        self.assertEqual(mod.source_identity(EXAMPLE_GRADLE), {
            "application_id": "com.edenpractical.comms",
            "version_code": 11,
            "version_name": "0.2.12-approval-review-only",
        })

    def test_rejects_ambiguous_source_field(self):
        with self.assertRaises(mod.IdentityAuditError):
            mod.source_identity(EXAMPLE_GRADLE + 'versionCode = 12\n')

    def test_parses_realistic_aapt_line(self):
        self.assertEqual(mod.parse_apk_badging(EXAMPLE_AAPT), mod.source_identity(EXAMPLE_GRADLE))

    def test_internal_test_override_is_monotonic_and_derives_version_name(self):
        self.assertEqual(mod.expected_identity(EXAMPLE_GRADLE, 10001), {
            "application_id": "com.edenpractical.comms",
            "version_code": 10001,
            "version_name": "0.2.12-internal-10001",
        })
        self.assertEqual(mod.expected_identity(EXAMPLE_GRADLE),
                         mod.source_identity(EXAMPLE_GRADLE))
        for bad in (-1, 0, 11, 2_100_000_001, True):
            with self.subTest(bad=bad):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.expected_identity(EXAMPLE_GRADLE, bad)

    def test_offline_preview_is_isolated_and_strictly_versioned(self):
        preview = mod.expected_identity(
            EXAMPLE_GRADLE, offline_preview_version_code=200001
        )
        self.assertEqual(preview, {
            "application_id": "com.edenpractical.comms.preview",
            "version_code": 200001,
            "version_name": "0.2.12-preview-200001",
        })
        self.assertEqual(
            mod.expected_identity(EXAMPLE_GRADLE), mod.source_identity(EXAMPLE_GRADLE)
        )
        for bad in (-1, 0, 11, 200000, 2_100_000_001, True, "200001"):
            with self.subTest(bad=bad):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.expected_identity(
                        EXAMPLE_GRADLE, offline_preview_version_code=bad
                    )
        with self.assertRaises(mod.IdentityAuditError):
            mod.expected_identity(
                EXAMPLE_GRADLE, 10001, offline_preview_version_code=200001
            )

    def test_public_preview_signer_is_pinned_and_never_classified_as_private_signing(self):
        from unittest.mock import patch
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp, "preview.apk")
            apk.write_bytes(b"synthetic test APK")
            aapt = ("package: name='com.edenpractical.comms.preview' "
                    "versionCode='200001' versionName='0.2.12-preview-200001'")
            cert = "Signer #1 certificate SHA-256 digest: " + FINGERPRINT + "\\n"
            with patch.object(mod, "tool_output", side_effect=[aapt, cert]), \\
                 patch.object(mod, "sdk_tool", return_value="fake"):
                report = mod.inspect(
                    apk, EXAMPLE_GRADLE, "f" * 40,
                    offline_preview_version_code=200001,
                    expected_signer_sha256=FINGERPRINT
                )
        self.assertEqual(report["signing_profile"], "public-offline-preview")
        self.assertFalse(report["signer_continuity_verified"])
        self.assertTrue(report["configured_signer_fingerprint_matched"])
        self.assertEqual(report["application_id"], "com.edenpractical.comms.preview")

        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp, "wrong-package.apk")
            apk.write_bytes(b"synthetic")
            wrong_aapt = ("package: name='com.edenpractical.comms' "
                          "versionCode='200001' versionName='0.2.12-preview-200001'")
            with patch.object(mod, "tool_output", return_value=wrong_aapt), \\
                 patch.object(mod, "sdk_tool", return_value="fake"):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.inspect(
                        apk, EXAMPLE_GRADLE, "f" * 40,
                        offline_preview_version_code=200001,
                        expected_signer_sha256=FINGERPRINT
                    )

    def test_wrong_pinned_signer_rejected_before_any_report_is_written(self):
        from unittest.mock import patch
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp, "test.apk")
            apk.write_bytes(b"not a real APK; tools are mocked")
            aapt = "package: name='com.edenpractical.comms' versionCode='10001' versionName='0.2.12-internal-10001'"
            cert = "Signer #1 certificate SHA-256 digest: " + FINGERPRINT + "\n"
            with patch.object(mod, "tool_output", side_effect=[aapt, cert]):
                with patch.object(mod, "sdk_tool", return_value="mock-sdk-tool"):
                    with self.assertRaises(mod.IdentityAuditError):
                        mod.inspect(apk, EXAMPLE_GRADLE, "test",
                                    internal_test_version_code=10001,
                                    expected_signer_sha256=OTHER_FINGERPRINT)

    def test_matching_pinned_signer_is_still_not_an_upgrade_receipt(self):
        from unittest.mock import patch
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp, "test.apk")
            apk.write_bytes(b"not real, use mocked tools")
            aapt = "package: name='com.edenpractical.comms' versionCode='10001' versionName='0.2.12-internal-10001'"
            cert = "Signer #1 certificate SHA-256 digest: " + FINGERPRINT + "\n"
            with patch.object(mod, "tool_output", side_effect=[aapt, cert]):
                with patch.object(mod, "sdk_tool", return_value="mock-sdk-tool"):
                    report = mod.inspect(apk, EXAMPLE_GRADLE, "test",
                                         internal_test_version_code=10001,
                                         expected_signer_sha256=FINGERPRINT)
        self.assertTrue(report["configured_signer_fingerprint_matched"])
        self.assertFalse(report["signer_continuity_verified"])
        self.assertEqual("pinned-internal-test", report["signing_profile"])

    def test_rejects_missing_package_line(self):
        with self.assertRaises(mod.IdentityAuditError):
            mod.parse_apk_badging("application-label:'Eden Comms'")

    def test_extracts_single_verified_signer(self):
        output = "Signer #1 certificate SHA-256 digest: " + FINGERPRINT + "\n"
        self.assertEqual(mod.parse_signer_fingerprint(output), FINGERPRINT.lower())

    def test_rejects_missing_or_multiple_signers(self):
        with self.assertRaises(mod.IdentityAuditError):
            mod.parse_signer_fingerprint("No signer")
        two_signers = (
            "Signer #1 certificate SHA-256 digest: " + FINGERPRINT + "\n" +
            "Signer #2 certificate SHA-256 digest: " + OTHER_FINGERPRINT + "\n"
        )
        with self.assertRaises(mod.IdentityAuditError):
            mod.parse_signer_fingerprint(two_signers)

    def test_rejects_malformed_signer_digest(self):
        with self.assertRaises(mod.IdentityAuditError):
            mod.parse_signer_fingerprint("Signer #1 certificate SHA-256 digest: aa\n")

    def test_only_future_same_signer_candidate_passes(self):
        previous = {
            "schema": "eden-comms.debug-apk-identity.v1",
            "application_id": "com.edenpractical.comms",
            "version_code": 11,
            "signer_certificate_sha256": FINGERPRINT.lower(),
        }
        next_build = {**previous, "version_code": 12}
        self.assertTrue(mod.compare_to_previous(next_build, previous))

        for candidate in (
            {**next_build, "version_code": 11},
            {**next_build, "version_code": 10},
            {**next_build, "signer_certificate_sha256": OTHER_FINGERPRINT.lower()},
            {**next_build, "application_id": "com.edenpractical.other"},
            {**next_build, "schema": "other"},
        ):
            with self.subTest(candidate=candidate):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.compare_to_previous(candidate, previous)

    def test_never_labels_uncompared_apk_upgrade_safe(self):
        record = {
            "source_commit": "test",
            "application_id": "com.edenpractical.comms",
            "version_name": "test",
            "version_code": 11,
            "apk_sha256": "0" * 64,
            "signer_certificate_sha256": FINGERPRINT.lower(),
        }
        summary = mod.markdown(record)
        self.assertIn("NOT VERIFIED", summary)
        self.assertIn("Do **not** uninstall", summary)


if __name__ == "__main__":
    unittest.main()
