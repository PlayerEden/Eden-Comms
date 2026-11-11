"""Run the REAL visual driver's argument parsing without Android.

Regression for a previous accidental literal-backslash-n comment that
swallowed both executable PACKAGE/ACTIVITY assignments. A string-contains
source test alone did not detect the error. No phones or APK installs.
"""
from contextlib import redirect_stdout
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
DRIVER = ROOT / "scripts/emulator_visual_qa.py"


class PreviewPackageBindingTest(unittest.TestCase):
    def load_driver(self):
        spec = importlib.util.spec_from_file_location("eden_emulator_driver_under_test", DRIVER)
        driver = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(driver)
        return driver

    def test_preview_launcher_package_is_really_bound_not_commented_out(self):
        for package in ("com.edenpractical.comms.preview",
                        "com.edenpractical.comms"):
            with self.subTest(package=package), tempfile.TemporaryDirectory() as d:
                driver = self.load_driver()
                apk = Path(d) / "dummy.apk"
                apk.write_bytes(b"fake: never installed")
                out = Path(d) / "results"
                out.mkdir()
                argv = ["emulator_visual_qa.py", "--apk", str(apk),
                        "--out", str(out), "--package", package]
                with patch.object(sys, "argv", argv), \
                     patch.object(driver, "run", return_value={"screens": []}) as run, \
                     redirect_stdout(io.StringIO()):
                    driver.main()
                run.assert_called_once()
                self.assertEqual(package, driver.PACKAGE)
                self.assertEqual(
                    package + "/com.edenpractical.comms.MainActivity",
                    driver.ACTIVITY
                )

    def test_source_has_no_literal_escape_comment_trap(self):
        source = DRIVER.read_text()
        self.assertIn("\n    PACKAGE = opts.package\n    ACTIVITY = PACKAGE", source)
        self.assertNotIn("\\n    PACKAGE = opts.package", source)
        self.assertIn("Exactly one emulator is required; physical devices are forbidden.", source)
        self.assertIn("Refusing non-emulator Android device", source)


if __name__ == "__main__":
    unittest.main()
