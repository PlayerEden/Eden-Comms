#!/usr/bin/env python3
"""Eden Comms real-app emulator-only screenshot/navigation smoke.
No actual QR scans, network sessions, typed messages, audio or peer identity.
The same driver can inspect the isolated installable Preview APK.
"""
import argparse
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
import zlib

PACKAGE = "com.edenpractical.comms"
ACTIVITY = PACKAGE + "/.MainActivity"
EMULATOR = re.compile(r"emulator-\d+")
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
PNG = b"\x89PNG\r\n\x1a\n"


def adb(*args, timeout=45, binary=False):
    return subprocess.run(
        ["adb", *args], check=True, capture_output=True,
        text=not binary, timeout=timeout
    ).stdout


def require_emulator():
    rows = subprocess.run(["adb", "devices"], check=True,
                          capture_output=True, text=True).stdout.splitlines()[1:]
    attached = [v.split()[0] for v in rows if len(v.split()) > 1 and
                v.split()[1] == "device"]
    if len(attached) != 1 or not EMULATOR.fullmatch(attached[0]):
        raise RuntimeError("Exactly one emulator is required; physical devices are forbidden.")
    serial = attached[0]
    if adb("-s", serial, "shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Refusing non-emulator Android device")
    os.environ["ANDROID_SERIAL"] = serial


def dump_ui():
    last = None
    for _ in range(4):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/eden_m4_ui07.xml", timeout=40)
            root = ET.fromstring(adb("exec-out", "cat", "/sdcard/eden_m4_ui07.xml"))
            if root.tag == "hierarchy":
                return root
        except (ET.ParseError, subprocess.CalledProcessError,
                subprocess.TimeoutExpired) as error:
            last = error
        time.sleep(1)
    raise RuntimeError("Emulator accessibility hierarchy unavailable") from last


def find(root, label):
    needle = label.casefold()
    for node in root.iter("node"):
        text = (node.attrib.get("text", "") + " " +
                node.attrib.get("content-desc", "")).casefold()
        if needle in text:
            m = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
            if m:
                l, t, r, b = map(int, m.groups())
                if r > l and b > t:
                    return ((l + r) // 2, (t + b) // 2)
    return None


def swipe_up():
    v = adb("shell", "wm", "size")
    matches = re.findall(r"(\d+)x(\d+)", v)
    if not matches:
        raise RuntimeError("Could not read emulator screen size")
    w, h = map(int, matches[-1])
    adb("shell", "input", "swipe", str(w // 2), str(int(h * .78)),
        str(w // 2), str(int(h * .26)), "400")


def seek(label, *, scroll=False, timeout=30):
    until, scrolls = time.monotonic() + timeout, 0
    while time.monotonic() < until:
        pos = find(dump_ui(), label)
        if pos:
            return pos
        if scroll and scrolls < 7:
            swipe_up()
            scrolls += 1
        time.sleep(.6)
    raise AssertionError("Emulator label missing: " + label)


def idle_choice_mode(root):
    """Observe real idle UI: emulator Wi-Fi may or may not be usable."""
    if find(root, "No usable Wi-Fi connection found"):
        return "wifi-unavailable"
    if find(root, "Show a code") and find(root, "Scan a code"):
        return "wifi-ready"
    return None


def inspect_idle_choice(timeout=25):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        mode = idle_choice_mode(dump_ui())
        if mode is not None:
            return mode
        swipe_up()
        time.sleep(.7)
    raise AssertionError("Idle pairing showed neither Wi-Fi-ready nor Wi-Fi-unavailable guidance")


def tap(label, *, scroll=False):
    x, y = seek(label, scroll=scroll)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(.5)


def back():
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(.7)


def launch():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=40)
    seek("EDEN COMMS")


def png_evidence(png):
    if len(png) < 2000 or not png.startswith(PNG):
        raise AssertionError("Screenshot is absent or too small")
    pos, image_data, width, height, ended = 8, [], 0, 0, False
    while pos + 12 <= len(png):
        n = struct.unpack(">I", png[pos:pos + 4])[0]
        kind = png[pos + 4:pos + 8]
        end = pos + 12 + n
        if end > len(png):
            raise AssertionError("Corrupt PNG")
        payload = png[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            width, height = struct.unpack(">II", payload[:8])
        elif kind == b"IDAT":
            image_data.append(payload)
        elif kind == b"IEND":
            ended = True
            break
        pos = end
    if not (ended and image_data and width >= 320 and height >= 320):
        raise AssertionError("Invalid PNG or screenshot dimensions")
    raw = zlib.decompress(b"".join(image_data))
    if len(set(raw[:400000])) < 10:
        raise AssertionError("Screenshot resembles a blank display")
    return {"width": width, "height": height, "bytes": len(png)}


def capture(folder, name, required):
    for label in required:
        seek(label, timeout=15)
    png = adb("exec-out", "screencap", "-p", binary=True, timeout=30)
    evidence = png_evidence(png)
    (folder / (name + ".png")).write_bytes(png)
    return {"scene": name, "labels": required, **evidence}


def configure(size=None, density=None, font="1.0"):
    adb("shell", "wm", "size", "reset")
    adb("shell", "wm", "density", "reset")
    if size:
        adb("shell", "wm", "size", size)
    if density:
        adb("shell", "wm", "density", str(density))
    adb("shell", "settings", "put", "system", "font_scale", font)
    time.sleep(1)


def keyboard_visible():
    text = adb("shell", "dumpsys", "input_method")
    return any(s in text for s in (
        "mInputShown=true", "isInputViewShown=true"
    ))


def run(apk, output):
    require_emulator()
    output.mkdir(parents=True, exist_ok=True)
    report = {
        "mode": "offline preview emulator (disconnected UI only)",
        "commit": os.environ.get("GITHUB_SHA", "local-unverified"),
        "api": adb("shell", "getprop", "ro.build.version.sdk").strip(),
        "screens": [], "idle_wifi_modes": {},
        "result": "INCOMPLETE",
        "not_proven": ["real-device accessibility", "real two-phone behavior",
                       "independent security", "Bluetooth", "Wi-Fi Direct", "voice"]
    }
    adb("install", "-r", str(apk), timeout=90)
    try:
        configure()
        launch()
        report["screens"].append(capture(output, "01-home", ["CONNECT NEARBY"]))
        tap("CONNECT NEARBY")
        # The actual app first asks for Same Wi-Fi, Bluetooth or Wi-Fi
        # Direct. Do NOT assume Home goes straight to Wi-Fi QR anymore.
        report["screens"].append(capture(output, "02-method-chooser",
                                         ["Choose how the phones connect"]))
        tap("USE SAME WI-FI", scroll=True)
        seek("One phone shows a code", timeout=35)
        report["screens"].append(capture(output, "03-idle-qr-choice",
                                         ["One phone shows a code"]))
        report["idle_wifi_modes"]["normal"] = inspect_idle_choice()
        # Never tap Show / Scan: no live QR data or camera permissions.
        back()  # Pairing -> method chooser
        back()  # Method chooser -> Home
        tap("How it works", scroll=True)
        report["screens"].append(capture(output, "04-help", ["Connect. Talk. Done."]))
        back()
        tap("Settings", scroll=True)
        report["screens"].append(capture(output, "05-settings", ["Dark appearance"]))
        tap("Privacy & data", scroll=True)
        report["screens"].append(capture(output, "06-privacy", ["Current preview"]))
        back()
        tap("Device and developer tools", scroll=True)
        tap("Text layout sandbox", scroll=True)
        report["screens"].append(capture(output, "07-local-sandbox", ["LOCAL ONLY"]))
        # Only focus the empty local-only preview. Never type or send anything.
        tap("Write a local preview", scroll=True)
        if not keyboard_visible():
            raise AssertionError("Emulator IME was not confirmed visible")
        report["screens"].append(capture(output, "08-empty-keyboard",
                                         ["Write a local preview"]))
        back()
        for name, size, density, font in (
            ("compact", "720x1280", 320, "1.0"),
            ("large-font", "720x1280", 320, "2.0"),
            ("landscape", "1280x720", 320, "1.0"),
        ):
            configure(size, density, font)
            launch()
            report["screens"].append(capture(output, "09-home-" + name, ["EDEN COMMS"]))
            if name == "compact":
                tap("CONNECT NEARBY", scroll=True)
                report["screens"].append(capture(output, "10-chooser-compact",
                                                 ["Choose how the phones connect"]))
                tap("USE SAME WI-FI", scroll=True)
                seek("One phone shows a code", timeout=35)
                report["screens"].append(capture(output, "11-idle-compact",
                                                 ["One phone shows a code"]))
                report["idle_wifi_modes"]["compact"] = inspect_idle_choice()
            if name == "landscape":
                tap("Voice / PTT", scroll=True)
                # Voice, like Text, first routes to real method selection.
                # Never claim an unpaired emulator can show an approved mic.
                report["screens"].append(capture(output, "12-voice-connect-chooser",
                                                 ["Choose how the phones connect"]))
        report["result"] = "PASS"
        return report
    except Exception as exc:
        # Never expose arbitrary exception text: it may include OS details.
        report["result"] = "FAILED"
        report["failure_type"] = type(exc).__name__
        raise
    finally:
        try:
            adb("shell", "am", "force-stop", PACKAGE)
            configure()
            adb("shell", "pm", "clear", PACKAGE)
        finally:
            # Always retain completed safe scenes and failure category.
            (output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--package", choices=(
        "com.edenpractical.comms", "com.edenpractical.comms.preview"
    ), default=PACKAGE)
    opts = parser.parse_args()
    # Only these two exact in-repo application IDs are accepted; ADB still
    # refuses physical devices before any install or screen interaction.
    global PACKAGE, ACTIVITY
    PACKAGE = opts.package
    ACTIVITY = PACKAGE + "/.MainActivity"
    if not opts.apk.is_file():
        parser.error("APK missing")
    report = run(opts.apk.resolve(), opts.out.resolve())
    (opts.out / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    print("M4-UI07 screenshots:", len(report["screens"]))


if __name__ == "__main__":
    main()
