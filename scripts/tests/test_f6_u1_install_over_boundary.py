"""F6-U1 install-over must be manual, checked, non-destructive, not called by CI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/install_internal_test_update.py"


class F6U1InstallOverBoundary(unittest.TestCase):
    def test_owner_must_authorize_exact_version_before_any_device_change(self):
        s = SCRIPT.read_text()
        self.assertIn("CONSENT_PREFIX = \"INSTALL OVER \"", s)
        self.assertIn("if ask(", s)
        self.assertIn("choose_device(runner([\"adb\", \"devices\"]), requested_serial)", s)
        self.assertIn("repeat, repeat_sha = preflight_check(", s)
        self.assertIn("sha256(apk) != initial_sha", s)
        self.assertIn("runner([\"adb\", \"-s\", serial, \"install\", \"-r\", str(apk)])", s)
        self.assertLess(s.index("if ask("), s.index("runner([\"adb\", \"-s\", serial, \"install\""))
        self.assertIn("actual.get(\"signer_certificate_sha256\")", s)
        self.assertIn("actual.get(\"version_code\")", s)

    def test_no_auto_uninstall_reset_downgrade_or_mass_deploy(self):
        s = SCRIPT.read_text()
        # Check *actual command expressions*; explanatory comments may mention
        # intentionally disallowed actions.
        for forbidden in (
            'runner(["adb", "uninstall"',
            'runner(["adb", "-s", serial, "uninstall"',
            'runner(["adb", "shell", "pm", "clear"',
            '"install-multiple",',
            '"-d", str(apk)',
            'subprocess.run(',
            'os.system(',
        ):
            self.assertNotIn(forbidden, s)
        for workflow in (ROOT / ".github/workflows").glob("*.yml"):
            self.assertNotIn("install_internal_test_update.py", workflow.read_text())
        self.assertIn("NOT VERIFIED: local Android Keystore identity", s)
        self.assertIn("Do not uninstall or retry blindly", s)


if __name__ == "__main__":
    unittest.main()
