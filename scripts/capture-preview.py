"""Capture the real APK using live provider data, without injecting sample scores."""
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUTPUT = Path("preview-output")
OUTPUT.mkdir(exist_ok=True)
PACKAGE = "com.openticker.app"


def adb(*args, timeout=30):
    return subprocess.run(["adb", *args], check=True, capture_output=True, timeout=timeout).stdout


def tree():
    try:
        adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/preview-window.xml", timeout=18)
        return ET.fromstring(adb("shell", "cat", "/sdcard/preview-window.xml"))
    except (subprocess.SubprocessError, ET.ParseError):
        return None


def tap_text(label, fallback=None):
    root = tree()
    if root is not None:
        for node in root.iter("node"):
            if node.get("text") == label:
                numbers = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
                if len(numbers) == 4:
                    x1, y1, x2, y2 = numbers
                    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    return True
    if fallback:
        adb("shell", "input", "tap", str(fallback[0]), str(fallback[1]))
    return False


def screenshot(name, required=()):
    (OUTPUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    if required:
        root = tree()
        visible = {node.get("text") for node in root.iter("node")} if root is not None else set()
        missing = set(required) - visible
        if missing:
            raise RuntimeError(f"{name}: expected dashboard labels not visible: {sorted(missing)}")
    print(f"Captured {name}", flush=True)


try:
    adb("shell", "wm", "size", "1280x800")
    adb("shell", "wm", "density", "160")
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk", timeout=90)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(4)
    # Android's first immersive launch displays a system help window over the app.
    tap_text("Got it", fallback=(880, 190))
    time.sleep(1)
    tap_text("Pause", fallback=(970, 30))
    # Give the public feeds time to return. No fixture data is substituted on failure.
    time.sleep(22)
    tap_text("Weather", fallback=(810, 30))
    time.sleep(2)
    screenshot("01-weather-with-sports-ticker", required=("NEXT 12 HOURS",))
    tap_text("Sports", fallback=(885, 30))
    time.sleep(2)
    screenshot("02-sports-nfl-ncaaf-nhl", required=("NFL", "NCAAF", "NHL"))
    tap_text("NBA / MLB", fallback=(260, 120))
    time.sleep(2)
    screenshot("03-sports-nba-mlb", required=("NBA", "MLB"))
    root = tree()
    if root is not None:
        (OUTPUT / "sports-ui.xml").write_bytes(ET.tostring(root, encoding="utf-8"))
    # Restore defaults, then record an automatic weather/sports/weather cycle.
    tap_text("NFL / NCAAF / NHL", fallback=(100, 120))
    tap_text("Weather", fallback=(810, 30))
    tap_text("Resume")
    video = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "25", "--bit-rate", "2000000", "--size", "1280x800", "/sdcard/openticker-preview.mp4"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    video.wait(timeout=40)
    adb("pull", "/sdcard/openticker-preview.mp4", str(OUTPUT / "slideshow.mp4"), timeout=40)
    logs = adb("logcat", "-d", "-v", "threadtime").decode("utf-8", errors="replace")
    (OUTPUT / "runtime.log").write_text(logs, encoding="utf-8")
    if "FATAL EXCEPTION" in logs and PACKAGE in logs:
        raise RuntimeError("Android runtime crash detected; inspect runtime.log")
    (OUTPUT / "capture-info.json").write_text(json.dumps({
        "source": "Actual OpenTicker APK running on an Android 15 tablet emulator",
        "resolution": "1280x800", "density_dpi": 160,
        "data": "Live ESPN and Open-Meteo responses, or the app's actual loading/error states",
        "screens": ["weather", "sports-group-a", "sports-group-b"],
    }, indent=2), encoding="utf-8")
finally:
    try:
        (OUTPUT / "runtime.log").write_bytes(adb("logcat", "-d", "-v", "threadtime"))
        screenshot("last-screen")
    except subprocess.SubprocessError:
        pass
