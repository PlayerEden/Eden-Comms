"""Test-only build packaging contract: field guide is bundled, no secrets."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/offline-preview-apk.yml"
GUIDE = ROOT / "docs/TEST_BUILD_QUICKSTART.md"


class PreviewPackContract(unittest.TestCase):
    def test_apk_artifact_includes_hands_on_instructions(self):
        yaml = WORKFLOW.read_text()
        self.assertIn("Include test quick start alongside APK", yaml)
        self.assertIn("test -s docs/TEST_BUILD_QUICKSTART.md", yaml)
        self.assertIn("cp docs/TEST_BUILD_QUICKSTART.md dist/START-HERE-TEST.md", yaml)
        self.assertIn("dist/START-HERE-TEST.md", yaml)
        self.assertIn("Exact source revision:", yaml)
        self.assertIn("Build versionCode:", yaml)
        self.assertIn("Package: com.edenpractical.comms.preview", yaml)
        self.assertIn("scripts/audit_apk_identity.py", yaml)
        self.assertIn("scripts/check_preview_permissions.sh", yaml)
        self.assertIn("GITHUB_RUN_NUMBER", yaml)
        self.assertNotIn("${{ secrets.", yaml)

    def test_guide_describes_real_test_capabilities_without_overclaims(self):
        guide = GUIDE.read_text()
        for phrase in (
            "One-phone checks", "Two-phone LAN chat",
            "Eden Comms Preview", "30-minute", "keyboard Send",
            "reciprocal QR exchange",
        ):
            self.assertIn(phrase, guide)
        self.assertIn("NOT a", guide)
        self.assertIn("actual Bluetooth and Wi-Fi Direct messaging", guide)
        self.assertIn("not", guide.lower())
        self.assertNotIn("upload a key", guide.lower())
        self.assertNotIn("read receipt is guaranteed", guide.lower())


if __name__ == "__main__":
    unittest.main()
