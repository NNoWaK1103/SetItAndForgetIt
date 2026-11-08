"""Capture the real APK using live provider data, without injecting sample scores."""
import json
import os
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
        result = adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/preview-window.xml", timeout=18)
        if b"dumped to:" not in result:
            return None
        contents = adb("shell", "cat", "/sdcard/preview-window.xml")
        (OUTPUT / "last-ui.xml").write_bytes(contents)
        return ET.fromstring(contents)
    except (subprocess.SubprocessError, ET.ParseError):
        return None


def tap_text(label, optional=False, header=False):
    inspected = False
    for _ in range(3):
        root = tree()
        if root is not None:
            inspected = True
            for node in root.iter("node"):
                if label.casefold() in (node.get("text", "").casefold(), node.get("content-desc", "").casefold()):
                    numbers = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
                    if len(numbers) == 4:
                        x1, y1, x2, y2 = numbers
                        if header and y2 > 100:
                            continue
                        adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                        return True
        time.sleep(.3)
    if not inspected:
        raise RuntimeError("Android UI hierarchy unavailable after three fresh dumps")
    if not optional:
        raise RuntimeError(f"Missing UI control: {label}")
    return False


def screenshot(name, required=(), dashboard=None):
    (OUTPUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    if required or dashboard:
        root = tree()
        visible = {node.get("text") for node in root.iter("node")} if root is not None else set()
        if dashboard and (root is None or not any(node.get("content-desc") == f"{dashboard} dashboard" for node in root.iter("node"))):
            raise RuntimeError(f"{name}: {dashboard} dashboard is not visible")
        missing = set(required) - visible
        if missing:
            raise RuntimeError(f"{name}: expected dashboard labels not visible: {sorted(missing)}")
    print(f"Captured {name}", flush=True)


try:
    # Let Android finish first-boot work before installing and driving the dashboard.
    time.sleep(15)
    adb("shell", "wm", "size", "1280x800")
    adb("shell", "wm", "density", "160")
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk", timeout=90)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(4)
    # Android's first immersive launch displays a system help window over the app.
    tap_text("Got it", optional=True)
    time.sleep(1)
    tap_text("Pause automatic slideshow")
    # Give the public feeds time to return. No fixture data is substituted on failure.
    time.sleep(22)
    tap_text("Weather")
    time.sleep(2)
    screenshot("01-weather-with-sports-ticker", required=("●  LOCAL WEATHER CENTER",), dashboard="Weather")
    captured_sports = []
    hidden_sports = []
    # Confirmed empty schedules intentionally remove their tabs. Capture the actual
    # available leagues rather than assuming all sports are scheduled today.
    for label, name in [("NFL / College", "football"), ("NFL", "nfl"), ("College", "college"), ("NHL", "nhl"), ("MLB", "mlb"), ("NBA", "nba")]:
        if not tap_text(label, optional=True, header=True):
            hidden_sports.append(label)
            continue
        time.sleep(2)
        screenshot(f"02-sports-{name}", dashboard="Sports")
        captured_sports.append(label)
    root = tree()
    if root is not None:
        (OUTPUT / "sports-ui.xml").write_bytes(ET.tostring(root, encoding="utf-8"))
    tap_text("Stocks")
    time.sleep(2)
    screenshot("04-stocks-unconfigured", required=("The Home Depot",), dashboard="Stocks")
    # Record actual schedule-aware rotation; Sports is skipped on a confirmed no-games day.
    tap_text("Weather")
    tap_text("Resume automatic slideshow")
    video = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "50", "--bit-rate", "2000000", "--size", "1280x800", "/sdcard/openticker-preview.mp4"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    video.wait(timeout=65)
    adb("pull", "/sdcard/openticker-preview.mp4", str(OUTPUT / "slideshow.mp4"), timeout=40)
    logs = adb("logcat", "-d", "-v", "threadtime").decode("utf-8", errors="replace")
    (OUTPUT / "runtime.log").write_text(logs, encoding="utf-8")
    if "FATAL EXCEPTION" in logs and PACKAGE in logs:
        raise RuntimeError("Android runtime crash detected; inspect runtime.log")
    (OUTPUT / "capture-info.json").write_text(json.dumps({
        "source": "Actual OpenTicker APK running on an Android 11 AOSP tablet emulator",
        "source_commit": os.environ.get("GITHUB_SHA"),
        "resolution": "1280x800", "density_dpi": 160,
        "data": "ESPN and Open-Meteo responses, or actual loading/error states. Stocks unconfigured: no mock prices.",
        "screens": ["weather", *captured_sports, "stocks-unconfigured"],
        "sports_tabs_not_visible": hidden_sports,
    }, indent=2), encoding="utf-8")
finally:
    try:
        (OUTPUT / "runtime.log").write_bytes(adb("logcat", "-d", "-v", "threadtime"))
        screenshot("last-screen")
    except subprocess.SubprocessError:
        pass
