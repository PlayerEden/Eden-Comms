"""M4-UI07 non-emulator regression checks run on ordinary PR CI."""
from pathlib import Path
import importlib.util
import struct
import unittest
import zlib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/emulator_visual_qa.py"
WORKFLOW = ROOT / ".github/workflows/m4-ui07-manual-emulator.yml"
spec = importlib.util.spec_from_file_location("m4_ui07", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def image(rows):
    import binascii
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", binascii.crc32(tag + data) & 0xffffffff))
    raw = b"".join(b"\0" + row for row in rows)
    return (module.PNG +
            chunk(b"IHDR", struct.pack(">IIBBBBB", 400, 400, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw, 0)) +
            chunk(b"IEND", b""))


class ManualEmulatorHarnessContract(unittest.TestCase):
    def test_workflow_is_manual_only_without_private_signing(self):
        s = WORKFLOW.read_text()
        self.assertIn("  workflow_dispatch:", s)
        self.assertIn('test "$GITHUB_REF" = "refs/heads/main"', s)
        self.assertIn('test "$GITHUB_REPOSITORY" = "PlayerEden/Eden-Comms"', s)
        for bad in ("\n  push:", "\n  pull_request:", "\n  workflow_run:",
                    "\n  schedule:", "secrets.", "EC_INTERNAL_KEYSTORE_",
                    "contents: write"):
            self.assertNotIn(bad, s)
        for good in ("reactivecircus/android-emulator-runner@v2",
                     "scripts/check_security_review_boundary.sh",
                     "scripts/check_preview_permissions.sh",
                     "scripts/audit_apk_identity.py", "retention-days: 7"):
            self.assertIn(good, s)

    def test_safe_routes_only_and_physical_device_guard(self):
        s = SCRIPT.read_text()
        for good in ("ro.kernel.qemu", "Exactly one emulator",
                     "tap(\"Text layout sandbox\", scroll=True)", "keyboard_visible()",
                     "png_evidence(png)", "uiautomator", "screencap"):
            self.assertIn(good, s)
        for bad in ('tap("Show a code")', 'tap("Scan a code")',
                    'tap("Approve this debug test")', 'adb("connect"',
                    'adb("tcpip"', 'adb("shell", "input", "text"',
                    'sendDebugText(', 'approveForTest(', 'createIdentity('):
            self.assertNotIn(bad, s)

    def test_accessibility_label_parser_checks_bounds(self):
        tree = ET.fromstring('<hierarchy><node text="CONNECT NEARBY" bounds="[10,20][210,60]"/>'
                             '<node text="Invisible" bounds="[0,0][0,0]"/></hierarchy>')
        self.assertEqual((110, 40), module.find(tree, "connect nearby"))
        self.assertIsNone(module.find(tree, "Invisible"))
        self.assertIsNone(module.find(tree, "scan code"))

    def test_idle_pairing_accepts_network_ready_and_no_wifi_states(self):
        # The app intentionally hides role buttons when there is no usable Wi-Fi.
        unavailable = ET.fromstring(
            '<hierarchy><node text="No usable Wi-Fi connection found on this phone." '
            'bounds="[10,20][310,70]"/></hierarchy>'
        )
        ready = ET.fromstring(
            '<hierarchy>'
            '<node text="Show a code" bounds="[10,20][150,70]"/>'
            '<node text="Scan a code" bounds="[160,20][310,70]"/>'
            '</hierarchy>'
        )
        incomplete = ET.fromstring(
            '<hierarchy><node text="Show a code" bounds="[10,20][150,70]"/></hierarchy>'
        )
        self.assertEqual("wifi-unavailable", module.idle_choice_mode(unavailable))
        self.assertEqual("wifi-ready", module.idle_choice_mode(ready))
        self.assertIsNone(module.idle_choice_mode(incomplete))

    def test_visual_smoke_never_requires_wifi_role_buttons(self):
        source = SCRIPT.read_text()
        self.assertIn('["One phone shows a code"]', source)
        self.assertIn('report["idle_wifi_modes"]["normal"] = inspect_idle_choice()', source)
        self.assertIn('report["idle_wifi_modes"]["compact"] = inspect_idle_choice()', source)
        self.assertNotIn('["Show a code", "Scan a code"]', source)
        self.assertNotIn('seek("Show a code"', source)

    def test_failed_scene_retains_summary_without_raw_errors(self):
        source = SCRIPT.read_text()
        self.assertIn('report["result"] = "FAILED"', source)
        self.assertIn('report["failure_type"] = type(exc).__name__', source)
        self.assertIn('(output / "summary.json").write_text(', source)
        self.assertNotIn('str(exc)', source)
        self.assertNotIn("traceback.format_exc", source)

    def test_png_rejects_blank_and_corrupt_images(self):
        rows = [bytes(i % 253 for i in range(1200))] * 400
        self.assertEqual(400, module.png_evidence(image(rows))["width"])
        with self.assertRaises(AssertionError):
            module.png_evidence(b"broken")
        with self.assertRaises(AssertionError):
            module.png_evidence(image([b"\0" * 1200] * 400))


if __name__ == "__main__":
    unittest.main()
