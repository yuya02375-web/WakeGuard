#!/usr/bin/env python3
"""Apply saved design sets in the installed release APK and capture Android screenshots."""
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree

PACKAGE = "jp.wakeguard.alarm"
OUT = Path(sys.argv[2])
SCREENS = {
    "alarms": ("MainActivity", ()),
    "world-clock": ("ClockActivity", ("--es", "mode", "world")),
    "timer": ("ClockActivity", ("--es", "mode", "timer")),
    "stopwatch": ("ClockActivity", ("--es", "mode", "stopwatch")),
    "records": ("StatsActivity", ()),
    "time-log": ("TimeLogActivity", ()),
    "settings": ("SystemSettingsActivity", ()),
}
PRESETS = {
    "sky-chart": ("天空の星図", "Sky Chart"),
    "mountain-morning": ("山並みの朝", "Mountain Morning"),
}
SETTINGS_ROW = ("デザインセット", "Design set")
APPLY = ("セット全体を使う", "Apply full set")
DEVICE_WIDTH = 900
DEVICE_HEIGHT = 1980


def adb(*args, timeout=40):
    return subprocess.run(["adb", *args], check=True, capture_output=True, timeout=timeout).stdout


def start(activity, extras=()):
    result = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.{activity}", *extras).decode()
    if "Error:" in result or "Exception" in result:
        raise RuntimeError(f"Could not open {activity}: {result}")
    for _ in range(16):
        windows = adb("shell", "dumpsys", "activity", "activities").decode(errors="replace")
        focused = [
            line for line in windows.splitlines()
            if "ResumedActivity" in line or "resumedActivity" in line
        ]
        if any(f"{PACKAGE}/.{activity} " in line for line in focused):
            return
        time.sleep(0.5)
    raise RuntimeError(f"{activity} did not become the active screen")


def hierarchy():
    path = "/data/local/tmp/wakeguard-visual-qa.xml"
    for _ in range(3):
        dumped = adb("shell", "uiautomator", "dump", path, timeout=25)
        if b"ERROR" not in dumped and b"dumped to" in dumped:
            return ElementTree.fromstring(adb("exec-out", "cat", path))
        time.sleep(0.5)
    raise RuntimeError("Could not read the on-device UI hierarchy")


def tap(node):
    x0, y0, x1, y1 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    if x1 - x0 < 8 or y1 - y0 < 30:
        return False
    adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
    time.sleep(0.7)
    return True


def scroll_small():
    x = str(DEVICE_WIDTH // 2)
    adb("shell", "input", "swipe", x, str(DEVICE_HEIGHT * 4 // 5),
        x, str(DEVICE_HEIGHT * 5 // 8), "400")
    time.sleep(0.35)


def wanted(node, names):
    return node.get("text") in names or node.get("content-desc") in names


def open_picker():
    start("SystemSettingsActivity")
    for _ in range(10):
        tree = hierarchy()
        for node in tree.iter("node"):
            if node.get("clickable") == "true" and any(
                node.get("content-desc", "").startswith(name) for name in SETTINGS_ROW
            ) and tap(node):
                return
            if node.get("text") in SETTINGS_ROW and tap(node):
                return
        scroll_small()
    raise RuntimeError("Could not find the design set row in Settings")


def select_preset(names):
    open_picker()
    for _ in range(70):
        tree = hierarchy()
        parents = {child: parent for parent in tree.iter() for child in parent}
        for node in tree.iter("node"):
            if node.get("text") not in names:
                continue
            card = node
            while card in parents:
                card = parents[card]
                buttons = [child for child in card.iter("node") if wanted(child, APPLY)]
                named_cards = [child for child in card.iter("node") if child.get("text") in names]
                # The list container may currently show only one button from a
                # neighboring card. The preset and its action must share a card.
                parent = parents.get(card)
                if card.get("scrollable") == "true" or (
                    parent is not None and parent.get("scrollable") == "true"
                ) or len(buttons) > 1:
                    break
                if len(buttons) == 1 and len(named_cards) == 1 and tap(buttons[0]):
                    verify_applied(names)
                    return
        scroll_small()
    raise RuntimeError(f"Could not apply design set {names[0]}")


def verify_applied(names):
    start("SystemSettingsActivity")
    for _ in range(10):
        for node in hierarchy().iter("node"):
            if wanted(node, names) or any(name in node.get("content-desc", "") for name in names):
                return
        scroll_small()
    raise RuntimeError(f"Settings does not report {names[0]} as applied")


def capture(preset, label, activity, extras):
    adb("shell", "am", "force-stop", PACKAGE)
    start(activity, extras)
    # Let native atlas decoding and the first draw finish on this cold start.
    time.sleep(2)
    if label == "time-log":
        labels = {node.get("text") for node in hierarchy().iter("node")}
        if not ({"0分", "0m"} & labels):
            raise RuntimeError("The empty time log does not show a localized zero duration")
    image = adb("exec-out", "screencap", "-p")
    if not image.startswith(b"\x89PNG\r\n\x1a\n") or len(image) < 20000:
        raise RuntimeError(f"Invalid PNG for {preset}/{label}")
    (OUT / f"{preset}-{label}.png").write_bytes(image)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    adb("root")
    adb("wait-for-device")
    adb("shell", "wm", "size", f"{DEVICE_WIDTH}x{DEVICE_HEIGHT}")
    adb("shell", "wm", "density", "384")
    adb("shell", "cmd", "uimode", "night", "yes")
    adb("shell", "input", "keyevent", "82")
    adb("install", "-r", sys.argv[1], timeout=75)
    for permission in ("POST_NOTIFICATIONS", "ACTIVITY_RECOGNITION"):
        adb("shell", "pm", "grant", PACKAGE, f"android.permission.{permission}")
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", "ja-JP")
    adb("logcat", "-b", "crash", "-c")
    for preset, names in PRESETS.items():
        select_preset(names)
        for label, (activity, extras) in SCREENS.items():
            capture(preset, label, activity, extras)
        print(f"Captured {preset} on {len(SCREENS)} screens.", flush=True)
    log = adb("logcat", "-b", "crash", "-d").decode(errors="replace")
    if f"Process: {PACKAGE}," in log:
        raise RuntimeError(f"WakeGuard crashed during the Android review:\n{log}")
    if len(list(OUT.glob("*.png"))) != len(PRESETS) * len(SCREENS):
        raise RuntimeError("Android review screenshots are incomplete")


if __name__ == "__main__":
    main()
