"""M3b read-only ADB upgrade preflight: pure and mocked command tests.

Never connects to a device, installs an APK, or reads Android Keystore data.
"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import preflight_device_apk_update as mod


SIGNER = "a1" * 32
OTHER = "b2" * 32


def identity(version_code=11, signer=SIGNER, package=mod.APP_ID):
    return {
        "application_id": package,
        "version_code": version_code,
        "version_name": f"0.2.12-test-{version_code}",
        "signer_certificate_sha256": signer,
    }


class DeviceUpgradePreflightTests(unittest.TestCase):
    def test_one_authorized_device_may_be_automatically_selected(self):
        devices = "List of devices attached\nPHONE_A\tdevice product:a model:b\n"
        self.assertEqual(mod.choose_device(devices), "PHONE_A")
        self.assertEqual(mod.choose_device(devices, "PHONE_A"), "PHONE_A")

    def test_multiple_offline_and_unauthorized_devices_fail_closed(self):
        for listing in (
            "List of devices attached\n",
            "List of devices attached\nA\tdevice\nB\tdevice\n",
            "List of devices attached\nA\tdevice\nB\toffline\n",
            "List of devices attached\nA\tunauthorized\n",
            "Malformed\nA\tdevice\n",
            "List of devices attached\nA\tdevice\nA\tdevice\n",
        ):
            with self.subTest(listing=listing):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.choose_device(listing)
        two = "List of devices attached\nA\tdevice\nB\tdevice\n"
        self.assertEqual(mod.choose_device(two, "B"), "B")
        with self.assertRaises(mod.IdentityAuditError):
            mod.choose_device(two, "C")

    def test_installed_base_path_requires_one_strict_base_apk(self):
        path = "/data/app/~~abc/com.edenpractical.comms-xyz/base.apk"
        self.assertEqual(mod.parse_installed_base_path(
            "package:" + path + "\npackage:/data/app/split_config.en.apk\n"
        ), path)
        for raw in (
            "", "Error: unknown package",
            "package:/data/app/x/split_config.en.apk",
            "package:/data/app/x/base.apk\npackage:/data/app/y/base.apk",
            "package:/data/app/../user/0/base.apk",
            "package:/data/app/my folder/base.apk",
        ):
            with self.subTest(raw=raw):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.parse_installed_base_path(raw)

    def test_only_same_package_verified_signer_and_higher_version_pass(self):
        passed = mod.compare_update(identity(11), identity(10014))
        self.assertEqual(passed["candidate_version_code"], 10014)
        self.assertEqual(passed["installed_version_code"], 11)
        self.assertEqual(passed["certificate_sha256"], SIGNER)

        for old, new in (
            (identity(11), identity(11)),
            (identity(11), identity(10)),
            (identity(10014), identity(11)),
            (identity(11), identity(10014, OTHER)),
            (identity(11, OTHER), identity(10014)),
            (identity(11, package="fake.app"), identity(10014)),
            (identity(11), identity(10014, package="fake.app")),
            (identity("11"), identity(10014)),
            (identity(11), identity(True)),
            (identity(11, "bad-fingerprint"), identity(10014)),
        ):
            with self.subTest(old=old, new=new):
                with self.assertRaises(mod.IdentityAuditError):
                    mod.compare_update(old, new)

    def test_fake_adb_success_only_reads_metadata_and_pulls_base_apk(self):
        with tempfile.TemporaryDirectory() as folder:
            candidate = Path(folder) / "new.apk"
            candidate.write_bytes(b"dummy signed APK content for mock")
            commands = []
            pulled = []

            def fake_runner(args):
                commands.append(args)
                if args == ["adb", "devices"]:
                    return "List of devices attached\nPHONE_A\tdevice\n"
                if args[:5] == ["adb", "-s", "PHONE_A", "shell", "pm"]:
                    self.assertEqual(args[-2:], ["path", mod.APP_ID])
                    return "package:/data/app/com.edenpractical.comms/base.apk\n"
                if args[:4] == ["adb", "-s", "PHONE_A", "pull"]:
                    Path(args[-1]).write_bytes(b"existing installed base APK")
                    pulled.append(Path(args[-1]))
                    return "1 file pulled\n"
                if args[0] == "aapt":
                    code = 11 if "installed-base.apk" in args[-1] else 10014
                    return ("package: name='com.edenpractical.comms' " +
                            f"versionCode='{code}' versionName='0.2.12-test-{code}'\n")
                if args[0] == "apksigner":
                    return "Signer #1 certificate SHA-256 digest: " + SIGNER + "\n"
                self.fail("Unexpected command: " + repr(args))

            result, digest = mod.preflight(
                candidate, runner=fake_runner, find_sdk=lambda name: name
            )
            self.assertEqual(result["installed_version_code"], 11)
            self.assertEqual(result["candidate_version_code"], 10014)
            self.assertEqual(len(digest), 64)
            self.assertEqual(len(pulled), 1)
            self.assertFalse(pulled[0].exists(), "Temporary installed APK must be deleted")
            self.assertTrue(candidate.exists(), "Read-only preflight must not alter candidate")
            self.assertFalse(any("install" in a or "uninstall" in a
                                 for command in commands for a in command),
                             "ADB must not run install/uninstall commands")

    def test_wrong_candidate_package_refused_before_connecting_to_adb(self):
        with tempfile.TemporaryDirectory() as folder:
            candidate = Path(folder) / "new.apk"
            candidate.write_bytes(b"mock")
            commands = []

            def fake_runner(args):
                commands.append(args)
                if args[0] == "aapt":
                    return "package: name='com.unknown.test' versionCode='22' versionName='wrong'\n"
                if args[0] == "apksigner":
                    return "Signer #1 certificate SHA-256 digest: " + SIGNER + "\n"
                self.fail("Should not invoke ADB")

            with self.assertRaises(mod.IdentityAuditError):
                mod.preflight(candidate, runner=fake_runner, find_sdk=lambda name: name)
            self.assertFalse(any(command[0] == "adb" for command in commands))


if __name__ == "__main__":
    unittest.main()
