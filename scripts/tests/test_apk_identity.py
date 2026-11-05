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
