#!/usr/bin/env python3
"""One-time, local-only setup for Eden Comms protected internal-test signing.

IMPORTANT: Run on the repository owner's TRUSTED COMPUTER, never in public CI.
The tool cannot read existing GitHub secrets. It refuses any existing/partial
secret set rather than silently rotating a signing key and breaking updates.

Secrets go to 'gh secret set' over stdin, never command arguments, stdout, a
dotenv file, this repository, or GitHub job artifacts. This is not a phone QA
test, and it does not trigger a workflow or create signing keys.
"""
import argparse
import base64
import getpass
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
from pathlib import Path

REPOSITORY = "PlayerEden/Eden-Comms"
ENVIRONMENT = "internal-test"
ALIAS_PATTERN = re.compile(r"[a-z][a-z0-9_-]{0,63}\Z")
SECRET_NAMES = (
    "EC_INTERNAL_KEYSTORE_BASE64",
    "EC_INTERNAL_KEYSTORE_PASSWORD",
    "EC_INTERNAL_KEY_ALIAS",
    "EC_INTERNAL_KEY_PASSWORD",
    "EC_INTERNAL_CERT_SHA256",
)
ROOT = Path(__file__).resolve().parent.parent


class SetupError(ValueError):
    pass


def run_safely(argv, *, secret_input=None, extra_env=None):
    """Do not surface command stderr: some host CLI settings can echo inputs."""
    env = os.environ.copy()
    for variable in ("GH_DEBUG", "GH_TRACE", "GIT_TRACE", "GIT_TRACE_PACKET"):
        env.pop(variable, None)
    if extra_env:
        env.update(extra_env)
    try:
        result = subprocess.run(
            argv,
            input=secret_input,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=env,
            timeout=90,
            check=True,
        )
    except (subprocess.CalledProcessError, OSError, subprocess.TimeoutExpired) as exc:
        command = Path(argv[0]).name if argv else "tool"
        raise SetupError(
            command + " command failed. Check local tools/auth/environment "
            "privately; no passwords or raw command output will be printed."
        ) from exc
    if len(result.stdout) > 2_000_000:
        raise SetupError("Unexpectedly large tool output; refusing to continue")
    return result.stdout


def gh_json(path, runner=run_safely):
    data = runner(["gh", "api", path])
    try:
        return json.loads(data)
    except (json.JSONDecodeError, UnicodeError) as exc:
        raise SetupError("GitHub returned malformed environment metadata") from exc


def check_environment(runner=run_safely):
    """Enforce owner + exact selected-branch policy before ANY secret upload."""
    owner = gh_json("user", runner)
    if not isinstance(owner, dict) or owner.get("login") != "PlayerEden":
        raise SetupError(
            "Sign in with the PlayerEden GitHub account; this flow is owner-only."
        )

    path = "repos/" + REPOSITORY + "/environments/" + ENVIRONMENT
    environment = gh_json(path, runner)
    if not isinstance(environment, dict) or environment.get("name") != ENVIRONMENT:
        raise SetupError(
            "Create GitHub Settings > Environments > internal-test first."
        )
    policy = environment.get("deployment_branch_policy")
    if not isinstance(policy, dict) or policy.get("custom_branch_policies") is not True:
        raise SetupError(
            "Protect the internal-test environment using Selected branches/tags "
            "and allow ONLY the exact branch main."
        )

    policies = gh_json(path + "/deployment-branch-policies", runner)
    if not isinstance(policies, dict) or not isinstance(policies.get("branch_policies"), list):
        raise SetupError("Cannot verify allowed deployment branch policies")
    names = [p.get("name") for p in policies["branch_policies"]
             if isinstance(p, dict)]
    if sorted(names) != ["main"]:
        raise SetupError(
            "Selected deployment branch policies must contain exactly main, "
            "not a wildcard or other branch."
        )


def existing_secrets(runner=run_safely):
    raw = runner([
        "gh", "secret", "list", "--repo", REPOSITORY,
        "--env", ENVIRONMENT, "--json", "name"
    ])
    try:
        records = json.loads(raw)
    except (json.JSONDecodeError, UnicodeError) as exc:
        raise SetupError("Could not inspect secret NAMES (not values)") from exc
    if not isinstance(records, list) or any(
        not isinstance(v, dict) or not isinstance(v.get("name"), str)
        for v in records
    ):
        raise SetupError("Unexpected GitHub secret-name metadata")
    return {v["name"] for v in records}


def check_state(runner=run_safely):
    check_environment(runner)
    present = existing_secrets(runner) & set(SECRET_NAMES)
    missing = set(SECRET_NAMES) - present
    return present, missing


def safe_keystore_file(path):
    """Keys must stay outside this public git checkout, without symlinks."""
    path = Path(path).expanduser()
    if path.is_symlink():
        raise SetupError("Use a private regular keystore file, never a symlink")
    resolved = path.resolve()
    try:
        resolved.relative_to(ROOT)
    except ValueError:
        pass
    else:
        raise SetupError(
            "Private keystore is INSIDE the public repository. Move it outside first."
        )
    if not resolved.is_file():
        raise SetupError("Specified keystore is missing or not a regular file")
    size = resolved.stat().st_size
    if not 128 <= size <= 5_000_000:
        raise SetupError("Unexpected keystore size; do not upload this file")
    if os.name == "posix":
        mode = stat.S_IMODE(resolved.stat().st_mode)
        if mode & 0o077:
            raise SetupError(
                "Private keystore is readable by other users. Run chmod 600 on it."
            )
    return resolved


def inspect_keystore(keystore_path, alias, storepass, keypass,
                     runner=run_safely):
    """Public DER fingerprint + private-key presence/password verification."""
    if not ALIAS_PATTERN.fullmatch(alias):
        raise SetupError("Alias must be 1–64 lower-case letters/digits/-/_")
    secret_env = {
        "EDEN_SETUP_STOREPASS": storepass,
        "EDEN_SETUP_KEYPASS": keypass,
    }
    common = [
        "-keystore", str(keystore_path), "-storetype", "PKCS12",
        "-alias", alias, "-storepass:env", "EDEN_SETUP_STOREPASS",
    ]
    verbose = runner(["keytool", "-J-Duser.language=en", "-J-Duser.country=US",
                      "-list", "-v", *common], extra_env=secret_env)
    if b"Entry type: PrivateKeyEntry" not in verbose:
        raise SetupError(
            "The selected alias is not a private-key entry; no secrets uploaded."
        )
    # certreq forces keytool to actually sign with the private key, checking
    # that both passphrases work (a certificate export alone cannot do this).
    csr = runner(["keytool", "-certreq", *common,
                  "-keypass:env", "EDEN_SETUP_KEYPASS"], extra_env=secret_env)
    if len(csr) < 80:
        raise SetupError("Could not verify private key access")
    # -exportcert without -rfc writes binary DER of the public leaf cert.
    der = runner(["keytool", "-exportcert", *common], extra_env=secret_env)
    if not (100 <= len(der) < 100_000 and der[0] == 0x30):
        raise SetupError("Certificate export is not a plausible DER certificate")
    return hashlib.sha256(der).hexdigest()


def upload_secret(name, value, runner=run_safely):
    """Value only over stdin: no --body, argv password, log or temp .env."""
    if name not in SECRET_NAMES or not isinstance(value, str) or not value:
        raise SetupError("Invalid internal-test signing credential input")
    runner([
        "gh", "secret", "set", name,
        "--repo", REPOSITORY, "--env", ENVIRONMENT
    ], secret_input=value.encode("utf-8"))


def configure(path, alias, runner=run_safely, password_prompt=getpass.getpass,
              confirm=input):
    present, _ = check_state(runner)
    if present:
        raise SetupError(
            "One or more internal-test signing secrets ALREADY EXIST. "
            "Refusing overwrite/rotation. Verify the existing signer setup "
            "and resolve any partial configuration manually."
        )
    if shutil.which("keytool") is None or shutil.which("gh") is None:
        raise SetupError("Install Java JDK (keytool) and authenticated GitHub CLI (gh)")
    keystore_path = safe_keystore_file(path)
    if not ALIAS_PATTERN.fullmatch(alias):
        raise SetupError("Invalid signing alias")

    print("New dedicated test-only signer: " + str(keystore_path.name))
    print("Keep a SECURE OFF-REPO backup of this file and both passwords.")
    print("Do not replace existing keys to solve an APK upgrade failure.")
    storepass = password_prompt("Keystore password (hidden): ")
    keypass = password_prompt("Private key password (hidden; PKCS12 often matches store): ")
    if len(storepass) < 12 or len(keypass) < 12:
        raise SetupError("Use a strong password of at least 12 characters")
    fingerprint = inspect_keystore(keystore_path, alias, storepass,
                                   keypass, runner)
    print("PUBLIC certificate SHA-256: " + fingerprint)
    print("Five environment secrets will be added to PlayerEden/Eden-Comms "
          "under the protected internal-test environment.")
    if confirm("Type UPLOAD NEW TEST KEY to continue: ").strip() != "UPLOAD NEW TEST KEY":
        raise SetupError("Owner did not authorize the secret upload")

    # Re-check immediately before any write; do not rotate on concurrent setup.
    if existing_secrets(runner) & set(SECRET_NAMES):
        raise SetupError("Signing secret names appeared before upload; refusing")
    values = {
        "EC_INTERNAL_KEYSTORE_BASE64": base64.b64encode(
            keystore_path.read_bytes()).decode("ascii"),
        "EC_INTERNAL_KEYSTORE_PASSWORD": storepass,
        "EC_INTERNAL_KEY_ALIAS": alias,
        "EC_INTERNAL_KEY_PASSWORD": keypass,
        "EC_INTERNAL_CERT_SHA256": fingerprint,
    }
    try:
        for name in SECRET_NAMES:
            upload_secret(name, values[name], runner)
        if not set(SECRET_NAMES).issubset(existing_secrets(runner)):
            raise SetupError("All five secret names are not visible after upload")
    except SetupError as exc:
        raise SetupError(
            "Some secret writes may have completed. DO NOT run the signing "
            "workflow or rerun this setup automatically. Check the five "
            "secret NAMES in GitHub, reconcile with the original backed-up "
            "keystore, and repair manually; never silently rotate a used signer."
        ) from exc

    print("SETUP UPLOADED: all five secret NAMES are present in the protected "
          "environment. GitHub does not reveal their values.")
    print("NOT YET VERIFIED: successful pinned APK signing, second compatible "
          "build, or actual identity-preserving in-place phone upgrade.")
    print("Next: manually trigger the Internal signed test APK workflow on main; "
          "check signer SHA-256 and CI artifact evidence.")
    return fingerprint


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Local-only Eden Comms internal APK signing setup"
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true",
                      help="Read only: inspect GitHub environment and secret NAMES")
    mode.add_argument("--configure", action="store_true",
                      help="Owner-approved initial secret upload; no overwrite")
    parser.add_argument("--keystore", type=Path,
                        help="Existing backed-up PKCS12 test keystore OUTSIDE checkout")
    parser.add_argument("--alias", default="eden-comms-internal-test",
                        help="Existing private-key alias")
    args = parser.parse_args(argv)
    try:
        if args.check:
            present, missing = check_state()
            print("Protected environment: internal-test; owner PlayerEden; exact main policy")
            print("Configured secret NAMES: " + str(len(present)) + "/5")
            if missing:
                print("Missing names: " + ", ".join(sorted(missing)))
                print("No credential values can be read through this tool.")
                return 1
            print("Secret names present; NOT proof key values/signing are valid.")
            return 0
        if args.keystore is None:
            parser.error("--configure requires --keystore")
        configure(args.keystore, args.alias)
        return 0
    except SetupError as exc:
        print("STOP: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
