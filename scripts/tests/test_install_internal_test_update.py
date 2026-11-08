"""F6-U1 simulated owner-approved in-place APK update. Never use a real phone."""
import hashlib
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import install_internal_test_update as update
from audit_apk_identity import IdentityAuditError

CERT = "c3" * 32
WRONG_CERT = "d4" * 32
APP = "com.edenpractical.comms"


class SimulatedPhone:
    def __init__(self, *, post_version=10027, post_cert=CERT,
                 install_output="Performing Streamed Install\nSuccess\n"):
        self.installed_version = 11
        self.post_version = post_version
        self.post_cert = post_cert
        self.install_output = install_output
        self.calls = []
        self.install_count = 0
        self.devices_calls = 0

    def runner(self, args):
        self.calls.append(args)
        if args == ["adb", "devices"]:
            self.devices_calls += 1
            return "List of devices attached\nMOCK_PHONE\tdevice\n"
        if args[:5] == ["adb", "-s", "MOCK_PHONE", "install", "-r"]:
            self.install_count += 1
            if self.install_output.strip().endswith("Success"):
                self.installed_version = self.post_version
            return self.install_output
        if args == ["adb", "-s", "MOCK_PHONE", "shell", "pm", "path", APP]:
            return "package:/data/app/com.edenpractical.comms/base.apk\n"
        if args[:4] == ["adb", "-s", "MOCK_PHONE", "pull"]:
            Path(args[-1]).write_bytes(b"public installed base mock bytes")
            return "1 file pulled\n"
        if args[0] == "aapt":
            return ("package: name='com.edenpractical.comms' " +
                    f"versionCode='{self.installed_version}' " +
                    "versionName='0.2.12-internal'\n")
        if args[0] == "apksigner":
            return "Signer #1 certificate SHA-256 digest: " + self.post_cert + "\n"
        raise AssertionError("Unexpected simulated command: " + repr(args))

    def preflight(self, apk, requested_serial=None, runner=None, find_sdk=None):
        assert requested_serial == "MOCK_PHONE"
        assert runner == self.runner
        assert callable(find_sdk)
        if self.install_count:
            raise AssertionError("Preflight must never recur after install")
        return ({
            "package": APP,
            "installed_version_code": 11,
            "candidate_version_code": 10027,
            "certificate_sha256": CERT,
        }, hashlib.sha256(Path(apk).read_bytes()).hexdigest())


class F6OwnerInstallOverTests(unittest.TestCase):
    def run_case(self, phone, *, consent=None, before_second=None, path=None):
        with tempfile.TemporaryDirectory() as folder:
            apk = path or Path(folder) / "new.apk"
            Path(apk).write_bytes(b"synthetic APK - this is not a real signing key")
            calls = [0]
            def preflight(apk, requested_serial=None, runner=None, find_sdk=None):
                calls[0] += 1
                if calls[0] == 2 and before_second is not None:
                    before_second(Path(apk))
                return phone.preflight(apk, requested_serial, runner, find_sdk)

            def authorize(_):
                return consent if consent is not None else "INSTALL OVER " + APP + " 10027"

            with redirect_stdout(io.StringIO()):
                result = update.confirmed_install(
                    apk, runner=phone.runner, find_sdk=lambda n: n,
                    preflight_check=preflight, ask=authorize
                )
            return result, calls[0]

    def test_authorized_install_uses_exact_selected_device_and_verifies_public_apk(self):
        phone = SimulatedPhone()
        result, count = self.run_case(phone)
        self.assertEqual(count, 2)
        self.assertEqual(phone.install_count, 1)
        self.assertEqual(result["package"], APP)
        self.assertEqual(result["previous_version_code"], 11)
        self.assertEqual(result["installed_version_code"], 10027)
        self.assertEqual(result["signer_certificate_sha256"], CERT)
        self.assertEqual(len(result["candidate_apk_sha256"]), 64)
        installs = [cmd for cmd in phone.calls if len(cmd) > 3 and
                    cmd[:3] == ["adb", "-s", "MOCK_PHONE"] and
                    "install" in cmd]
        self.assertEqual(len(installs), 1)
        self.assertEqual(installs[0][:5],
                         ["adb", "-s", "MOCK_PHONE", "install", "-r"])
        self.assertTrue(any(cmd[:4] == ["adb", "-s", "MOCK_PHONE", "pull"]
                            for cmd in phone.calls))
        self.assertFalse(any("uninstall" in part or "clear" in part
                             for cmd in phone.calls for part in cmd))

    def test_missing_or_ambiguous_confirmation_never_calls_install(self):
        for consent in ("", "YES", "INSTALL OVER " + APP + " 10026",
                        "INSTALL OVER other.app 10027"):
            phone = SimulatedPhone()
            with self.subTest(consent=consent):
                with self.assertRaisesRegex(IdentityAuditError, "approval"):
                    self.run_case(phone, consent=consent)
                self.assertEqual(phone.install_count, 0)

    def test_modified_apk_between_preflight_and_install_fails_closed(self):
        phone = SimulatedPhone()
        # The read-only preflight's digest changes; the user did not approve
        # the new bytes and nothing may be installed.
        with self.assertRaisesRegex(IdentityAuditError, "changed"):
            self.run_case(phone, before_second=lambda apk: apk.write_bytes(b"replaced"))
        self.assertEqual(phone.install_count, 0)

    def test_preflight_failure_cannot_be_overridden_by_user_confirmation(self):
        phone = SimulatedPhone()
        def wrong_preflight(apk, requested_serial=None, runner=None, find_sdk=None):
            raise IdentityAuditError("BLOCKED: signer mismatch")
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "new.apk"
            apk.write_bytes(b"mock")
            with self.assertRaisesRegex(IdentityAuditError, "signer mismatch"):
                with redirect_stdout(io.StringIO()):
                    update.confirmed_install(
                        apk, runner=phone.runner, find_sdk=lambda n: n,
                        preflight_check=wrong_preflight,
                        ask=lambda _: "INSTALL OVER " + APP + " 10027"
                    )
        self.assertEqual(phone.install_count, 0)

    def test_post_install_wrong_version_or_signer_does_not_report_success(self):
        for params in ({"post_version": 10026}, {"post_cert": WRONG_CERT}):
            phone = SimulatedPhone(**params)
            with self.subTest(params=params):
                with self.assertRaisesRegex(IdentityAuditError, "Post-install"):
                    self.run_case(phone)
                self.assertEqual(phone.install_count, 1)
                self.assertFalse(any("uninstall" in part
                                     for cmd in phone.calls for part in cmd))

    def test_unsuccessful_adb_install_does_not_fall_back_to_uninstall(self):
        phone = SimulatedPhone(install_output="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n")
        with self.assertRaisesRegex(IdentityAuditError, "did not confirm install"):
            self.run_case(phone)
        self.assertEqual(phone.install_count, 1)
        self.assertFalse(any("uninstall" in part
                             for cmd in phone.calls for part in cmd))

    def test_no_second_phone_and_no_fresh_install_without_installed_base(self):
        phone = SimulatedPhone()
        original = phone.runner
        def two_phones(args):
            if args == ["adb", "devices"]:
                return "List of devices attached\nA\tdevice\nB\tdevice\n"
            return original(args)
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / "new.apk"
            apk.write_bytes(b"mock")
            with self.assertRaisesRegex(IdentityAuditError, "exactly one"):
                with redirect_stdout(io.StringIO()):
                    update.confirmed_install(
                        apk, runner=two_phones, find_sdk=lambda n: n,
                        preflight_check=phone.preflight,
                        ask=lambda _: "INSTALL OVER " + APP + " 10027",
                    )
        self.assertEqual(phone.install_count, 0)

    def test_missing_apk_fails_without_even_reading_adb_devices(self):
        phone = SimulatedPhone()
        with self.assertRaisesRegex(IdentityAuditError, "Missing"):
            update.confirmed_install(
                Path("/not-a-real-candidate.apk"), runner=phone.runner,
                preflight_check=phone.preflight, ask=lambda _: "YES"
            )
        self.assertFalse(phone.calls)


if __name__ == "__main__":
    unittest.main()
