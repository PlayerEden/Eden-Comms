"""Integrated Preview: one real APK, current navigation and safe emulator evidence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/java/com/edenpractical/comms/MainActivity.kt"
DRIVER = ROOT / "scripts/emulator_visual_qa.py"
WORKFLOW = ROOT / ".github/workflows/offline-preview-apk.yml"
OLD_MANUAL = ROOT / ".github/workflows/m4-ui07-manual-emulator.yml"
GUIDE = ROOT / "docs/TEST_BUILD_QUICKSTART.md"
FLAGS = ROOT / "app/src/main/java/com/edenpractical/comms/model/ReleaseCapabilities.kt"


class IntegratedPreviewVisual(unittest.TestCase):
    def test_one_preview_artifact_is_same_apk_installed_in_emulator(self):
        w = WORKFLOW.read_text()
        self.assertIn("build-preview:", w)
        self.assertIn("integrated-visual:", w)
        self.assertIn("needs: build-preview", w)
        self.assertIn("actions/download-artifact@v4", w)
        self.assertIn("pattern: eden-comms-offline-preview-*-", w)
        self.assertIn("merge-multiple: true", w)
        self.assertIn("eden-comms-preview-*.apk", w)
        self.assertIn("scripts/emulator_visual_qa.py --apk", w)
        self.assertIn('script: python3 scripts/emulator_visual_qa.py --apk "$(find visual-preview', w)
        self.assertNotIn('script: |\\n            apk="$(find visual-preview', w)
        self.assertIn("--package com.edenpractical.comms.preview", w)
        self.assertIn("reactivecircus/android-emulator-runner@v2", w)
        self.assertIn("qa-results/integrated-preview/summary.json", w)
        self.assertIn("qa-results/integrated-preview/*.png", w)
        self.assertIn("refs/heads/main", w)
        self.assertNotIn("${{ secrets.", w)
        self.assertNotIn("pull_request_target", w)
        self.assertNotIn("app:assembleRelease", w)

    def test_driver_checks_actual_chooser_not_retired_routes(self):
        s = DRIVER.read_text()
        for required in (
            'tap("CONNECT NEARBY")',
            'capture(output, "02-method-chooser"',
            '["Choose how the phones connect"]',
            'tap("USE SAME WI-FI", scroll=True)',
            'report["idle_wifi_modes"]["normal"] = inspect_idle_choice()',
            'tap("Voice / PTT", scroll=True)',
            '"12-voice-connect-chooser"',
            '["Connect. Text or talk. Disconnect."]',
            'tap("Text layout sandbox", scroll=True)',
            'keyboard_visible()',
            '"large-font", "720x1280", 320, "2.0"',
            '["No usable Wi-Fi connection found"]' if False else
                'if find(root, "No usable Wi-Fi connection found")',
            'ro.kernel.qemu',
            'choices=(\n        "com.edenpractical.comms", "com.edenpractical.comms.preview"',
        'ACTIVITY = PACKAGE + "/com.edenpractical.comms.MainActivity"',
        'report["result"] = "FAILED"',
        'report["failure_type"] = type(exc).__name__',
        'png_evidence(png)',
        'report["screens"]',
        ):
            self.assertIn(required, s, required)
        # applicationIdSuffix changes the APK identity, not Kotlin class name.
        # The original .MainActivity shortcut points to a non-existent
        # com.edenpractical.comms.preview.MainActivity at runtime.
        self.assertEqual(
            s.count('ACTIVITY = PACKAGE + "/com.edenpractical.comms.MainActivity"'),
            2,
        )
        self.assertNotIn('ACTIVITY = PACKAGE + "/.MainActivity"', s)
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('namespace = "com.edenpractical.comms"', gradle)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('android:name=".MainActivity"', manifest)
        self.assertNotIn('["COMING SOON"]', s)
        for forbidden in (
            'tap("Show a code")', 'tap("Scan a code")',
            'tap("Approve this debug test")', 'adb("connect"',
            'adb("tcpip"', 'sendDebugText(', 'approveForTest(',
        ):
            self.assertNotIn(forbidden, s, forbidden)

    def test_all_three_real_connection_routes_are_used_without_test_fakes(self):
        s = APP.read_text()
        for required in (
            '"home" -> HomeScreen(',
            'onConnect = { navigate("connect") }',
            '"connect" -> NearbyScreen(',
            'onLocalWifi = { navigate("pairing-test") }',
            'onBluetooth = { launchDebugMethod(ConnectionMethod.BLUETOOTH) }',
            'onWifiDirect = { launchDebugMethod(ConnectionMethod.WIFI_DIRECT) }',
            'actionLabel = "USE SAME WI-FI"',
            'actionLabel = "USE BLUETOOTH"',
            'actionLabel = "USE WI-FI DIRECT"',
            'ConnectionStatus("Not connected"',
        ):
            self.assertIn(required, s, required)
        for key in ("CAN_CONNECT", "CAN_SEND_MESSAGES", "CAN_TRANSMIT_VOICE"):
            self.assertIn("const val " + key + " = false", FLAGS.read_text())
        self.assertNotIn("\n  push:", OLD_MANUAL.read_text())  # Preserve the manual-only workflow.

    def test_included_install_guide_has_current_connect_communicate_disconnect(self):
        g = GUIDE.read_text()
        for required in (
            "Home → CONNECT NEARBY",
            "USE SAME WI-FI",
            "USE BLUETOOTH",
            "USE WI-FI DIRECT",
            "independently approve",
            "real connected conversation",
            "DISCONNECT THIS TEST SESSION",
            "cannot prove",
            "actual debug-only encrypted text/ACK",
        ):
            self.assertIn(required, g, required)
        self.assertNotIn("The Bluetooth diagnostic still CLOSES", WORKFLOW.read_text())


if __name__ == "__main__":
    unittest.main()
