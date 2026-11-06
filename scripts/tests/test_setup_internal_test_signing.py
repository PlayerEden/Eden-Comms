"""M3d secure local setup tests: only fake GitHub/Java calls, never real secrets."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import setup_internal_test_signing as mod

CERT = "a1" * 32
SECRET_SET = set(mod.SECRET_NAMES)


class FakeGitHub:
    def __init__(self, *, login="PlayerEden", environment="internal-test",
                 branch_names=("main",), custom=True, existing=()):
        self.login = login
        self.environment = environment
        self.branch_names = branch_names
        self.custom = custom
        self.present = set(existing)
        self.calls = []
        self.values = {}

    def __call__(self, args, *, secret_input=None, extra_env=None):
        self.calls.append((args, secret_input, extra_env))
        if args == ["gh", "api", "user"]:
            return json.dumps({"login": self.login}).encode()
        if args == ["gh", "api", "repos/PlayerEden/Eden-Comms/environments/internal-test"]:
            return json.dumps({
                "name": self.environment,
                "deployment_branch_policy": {
                    "custom_branch_policies": self.custom,
                    "protected_branches": False
                }
            }).encode()
        if args == ["gh", "api",
                    "repos/PlayerEden/Eden-Comms/environments/internal-test/deployment-branch-policies"]:
            return json.dumps({
                "branch_policies": [{"name": name} for name in self.branch_names]
            }).encode()
        if args == ["gh", "secret", "list", "--repo", mod.REPOSITORY,
                    "--env", mod.ENVIRONMENT, "--json", "name"]:
            return json.dumps([{"name": name} for name in sorted(self.present)]).encode()
        if (args[:3] == ["gh", "secret", "set"] and
                args[4:] == ["--repo", mod.REPOSITORY, "--env", mod.ENVIRONMENT]):
            name = args[3]
            if not isinstance(secret_input, bytes):
                raise AssertionError("Secret was NOT supplied through stdin")
            self.values[name] = secret_input
            self.present.add(name)
            return b""
        raise AssertionError("Unexpected host call: " + repr(args))


class M3dSigningSetupTests(unittest.TestCase):
    def test_checks_exact_owner_main_policy_and_secret_names_only(self):
        fake = FakeGitHub(existing=mod.SECRET_NAMES)
        present, missing = mod.check_state(fake)
        self.assertEqual(present, SECRET_SET)
        self.assertFalse(missing)
        self.assertTrue(all(v is None for _, v, _ in fake.calls))
        self.assertFalse(any(parts[:3] == ["gh", "secret", "set"]
                             for parts, _, _ in fake.calls))

    def test_unprotected_or_wrong_branch_environment_is_not_accepted(self):
        for kwargs in (
            {"login": "other-user"},
            {"environment": "production"},
            {"custom": False},
            {"branch_names": ()},
            {"branch_names": ("main", "develop")},
            {"branch_names": ("release/*",)},
        ):
            with self.subTest(kwargs=kwargs):
                with self.assertRaises(mod.SetupError):
                    mod.check_state(FakeGitHub(**kwargs))

    def test_no_secret_overwrite_or_partial_repair(self):
        for existing in (
            ["EC_INTERNAL_KEYSTORE_BASE64"],
            list(mod.SECRET_NAMES),
        ):
            fake = FakeGitHub(existing=existing)
            with self.assertRaisesRegex(mod.SetupError, "EXIST"):
                mod.configure("/not/needed.p12", "eden-comms-internal-test",
                              runner=fake, confirm=lambda _: self.fail(
                                  "Should never prompt for risky overwrite"))
            self.assertFalse(any(parts[:3] == ["gh", "secret", "set"]
                                 for parts, _, _ in fake.calls))

    def test_disallows_insecure_or_in_repository_keystores(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "test.p12"
            file.write_bytes(b"x" * 200)
            if os.name == "posix":
                file.chmod(0o644)
                with self.assertRaisesRegex(mod.SetupError, "chmod 600"):
                    mod.safe_keystore_file(file)
                file.chmod(0o600)
            self.assertEqual(mod.safe_keystore_file(file), file.resolve())
            sym = Path(folder) / "alias.p12"
            try:
                sym.symlink_to(file)
                with self.assertRaisesRegex(mod.SetupError, "symlink"):
                    mod.safe_keystore_file(sym)
            except (OSError, NotImplementedError):
                pass
        with self.assertRaises(mod.SetupError):
            mod.safe_keystore_file(mod.ROOT / "bad-key.p12")

    def test_keytool_private_key_proof_and_cert_fingerprint_without_password_in_args(self):
        calls = []
        secretpass = "top-secret-store-password"
        keypass = "different-secret-key-password"
        def fake_java(args, *, secret_input=None, extra_env=None):
            calls.append((args, extra_env))
            self.assertNotIn(secretpass, repr(args))
            self.assertNotIn(keypass, repr(args))
            self.assertEqual(extra_env["EDEN_SETUP_STOREPASS"], secretpass)
            self.assertEqual(extra_env["EDEN_SETUP_KEYPASS"], keypass)
            if "-list" in args:
                return b"Entry type: PrivateKeyEntry\n"
            if "-certreq" in args:
                return b"-" * 200
            if "-exportcert" in args:
                return b"\x30" + b"cert-der" * 30
            self.fail("Unexpected keytool action")
        with tempfile.TemporaryDirectory() as folder:
            fingerprint = mod.inspect_keystore(
                Path(folder) / "mock.p12", "eden-comms-internal-test",
                secretpass, keypass, runner=fake_java)
        import hashlib
        self.assertEqual(fingerprint,
                         hashlib.sha256(b"\x30" + b"cert-der" * 30).hexdigest())
        self.assertEqual(len(calls), 3)
        self.assertTrue(all("EDEN_SETUP_STOREPASS" in str(args) for args, _ in calls))

    def test_full_owner_confirmed_setup_uploads_five_stdin_only_values(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "eden-internal.p12"
            file.write_bytes(b"s" * 200)
            if os.name == "posix":
                file.chmod(0o600)
            fake = FakeGitHub()
            with patch.object(mod.shutil, "which", return_value="/trusted/tool"), \
                 patch.object(mod, "inspect_keystore", return_value=CERT):
                fingerprint = mod.configure(
                    file, "eden-comms-internal-test", runner=fake,
                    password_prompt=lambda _: "a-strong-passphrase-for-test",
                    confirm=lambda _: "UPLOAD NEW TEST KEY"
                )
            self.assertEqual(fingerprint, CERT)
            self.assertEqual(set(fake.values), SECRET_SET)
            self.assertEqual(fake.values["EC_INTERNAL_CERT_SHA256"],
                             CERT.encode())
            self.assertEqual(fake.values["EC_INTERNAL_KEYSTORE_BASE64"],
                             __import__("base64").b64encode(b"s" * 200))
            self.assertTrue(file.exists())
            for args, secret_input, _ in fake.calls:
                for value in fake.values.values():
                    # Secret bytes are never serialized into argv.
                    self.assertNotIn(value.decode(), repr(args))
                if args[:3] == ["gh", "secret", "set"]:
                    self.assertIsNotNone(secret_input)
            self.assertEqual(len([x for x in fake.calls
                                  if x[0][:3] == ["gh", "secret", "set"]]), 5)

    def test_abort_confirmation_never_writes_any_secret(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "eden-internal.p12"
            file.write_bytes(b"s" * 200)
            if os.name == "posix":
                file.chmod(0o600)
            fake = FakeGitHub()
            with patch.object(mod.shutil, "which", return_value="/trusted/tool"), \
                 patch.object(mod, "inspect_keystore", return_value=CERT):
                with self.assertRaises(mod.SetupError):
                    mod.configure(file, "eden-comms-internal-test", runner=fake,
                                  password_prompt=lambda _: "long-private-password",
                                  confirm=lambda _: "NO")
            self.assertFalse(fake.values)

    def test_first_missing_secret_refuses_to_upload_invalid_credential(self):
        fake = FakeGitHub()
        with self.assertRaises(mod.SetupError):
            mod.upload_secret("EC_NOT_ALLOWED", "secret", runner=fake)
        with self.assertRaises(mod.SetupError):
            mod.upload_secret(mod.SECRET_NAMES[0], "", runner=fake)
        self.assertFalse(fake.calls)


if __name__ == "__main__":
    unittest.main()
