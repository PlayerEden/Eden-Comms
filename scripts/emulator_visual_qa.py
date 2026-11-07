#!/usr/bin/env python3
"""M4-UI07 manual emulator-only screenshot/navigation smoke test.
No actual QR scans, network sessions, typed messages, audio or peer identity.
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
        "mInputShown=true", "isInputViewShown=true", "mShowRequested=true"
    ))


def run(apk, output):
    require_emulator()
    output.mkdir(parents=True, exist_ok=True)
    report = {
        "mode": "manual emulator only",
        "commit": os.environ.get("GITHUB_SHA", "local-unverified"),
        "api": adb("shell", "getprop", "ro.build.version.sdk").strip(),
        "screens": [],
        "not_proven": ["real-device accessibility", "real two-phone behavior",
                       "independent security", "Bluetooth", "Wi-Fi Direct", "voice"]
    }
    adb("install", "-r", str(apk), timeout=90)
    try:
        configure()
        launch()
        report["screens"].append(capture(output, "01-home", ["CONNECT NEARBY"]))
        tap("CONNECT NEARBY")
        seek("One phone shows a code", timeout=35)
        report["screens"].append(capture(output, "02-idle-qr-choice",
                                         ["Show a code", "Scan a code"]))
        # Never tap Show / Scan: no live QR data or camera permissions.
        back()
        tap("How it works", scroll=True)
        report["screens"].append(capture(output, "03-help", ["Connect. Talk. Done."]))
        back()
        tap("Settings", scroll=True)
        report["screens"].append(capture(output, "04-settings", ["Dark appearance"]))
        tap("Privacy & data", scroll=True)
        report["screens"].append(capture(output, "05-privacy", ["Current preview"]))
        back()
        tap("Device and developer tools", scroll=True)
        tap("Text layout sandbox", scroll=True)
        report["screens"].append(capture(output, "06-local-sandbox", ["LOCAL ONLY"]))
        # Only focus the empty local-only preview. Never type or send anything.
        tap("Write a local preview", scroll=True)
        if not keyboard_visible():
            raise AssertionError("Emulator IME was not confirmed visible")
        report["screens"].append(capture(output, "07-empty-keyboard",
                                         ["Write a local preview"]))
        back()
        for name, size, density, font in (
            ("compact", "720x1280", 320, "1.0"),
            ("large-font", "720x1280", 320, "2.0"),
            ("landscape", "1280x720", 320, "1.0"),
        ):
            configure(size, density, font)
            launch()
            report["screens"].append(capture(output, "08-home-" + name, ["EDEN COMMS"]))
            if name == "compact":
                tap("CONNECT NEARBY", scroll=True)
                seek("One phone shows a code", timeout=35)
                report["screens"].append(capture(output, "09-idle-compact",
                                                 ["Show a code"]))
            if name == "landscape":
                tap("Voice / PTT", scroll=True)
                report["screens"].append(capture(output, "10-voice-disabled",
                                                 ["COMING SOON"]))
        report["result"] = "PASS"
        return report
    finally:
        adb("shell", "am", "force-stop", PACKAGE)
        configure()
        adb("shell", "pm", "clear", PACKAGE)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    opts = parser.parse_args()
    if not opts.apk.is_file():
        parser.error("APK missing")
    report = run(opts.apk.resolve(), opts.out.resolve())
    (opts.out / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
    print("M4-UI07 screenshots:", len(report["screens"]))


if __name__ == "__main__":
    main()
