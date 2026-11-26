#!/usr/bin/env python3
"""Smoke-check two separately signed APKs on the Android emulator."""

import re
import subprocess
import time
from xml.etree import ElementTree

TRAINING = "jp.replift.training"
WAKE = "jp.wakeguard.alarm"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=45).strip()


def shell(*args):
    return adb("shell", *args)


def launch(package):
    shell("am", "force-stop", package)
    shell("am", "start", "-W", "-n", f"{package}/.MainActivity")


def visible_nodes():
    shell("uiautomator", "dump", "/sdcard/replift-ui.xml")
    xml = shell("cat", "/sdcard/replift-ui.xml")
    return list(ElementTree.fromstring(xml).iter("node"))


def label_matches(node, label, exact):
    return any(
        value == label if exact else label in value
        for value in (node.get("text") or "", node.get("content-desc") or "")
    )


def read_node(label, *, exact=True, scroll=False):
    labels = (label,) if isinstance(label, str) else label
    last = []
    for attempt in range(8 if scroll else 6):
        try:
            nodes = visible_nodes()
        except (subprocess.SubprocessError, ElementTree.ParseError):
            time.sleep(0.6)
            continue
        last = nodes
        for node in nodes:
            if any(label_matches(node, wanted, exact) for wanted in labels):
                return node
        if scroll and attempt in (1, 3, 5):
            size = re.findall(r"\d+x\d+", shell("wm", "size"))[-1].split("x")
            width, height = map(int, size)
            shell("input", "swipe", str(width // 2), str(height * 6 // 8),
                  str(width // 2), str(height * 3 // 8), "400")
        time.sleep(0.6)
    labels = sorted({node.get("text") or node.get("content-desc") or "" for node in last})
    raise AssertionError(f"Missing UI label {label!r}. Visible labels: {labels}")


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap_label(label, *, scroll=False):
    tap(read_node(label, scroll=scroll))


def scroll_to_settings_bottom():
    size = re.findall(r"\d+x\d+", shell("wm", "size"))[-1].split("x")
    width, height = map(int, size)
    for _ in range(3):
        shell("input", "swipe", str(width // 2), str(height * 7 // 8),
              str(width // 2), str(height // 4), "400")
    time.sleep(0.5)


def save_run(minutes):
    tap_label("ラン")
    read_node("手入力で記録")
    tap_label("手入力で記録")
    read_node("ランを手入力")
    fields = [n for n in visible_nodes() if n.get("class") == "android.widget.EditText"]
    if not fields:
        raise AssertionError("Manual run form has no editable duration")
    tap(fields[0])
    shell("input", "keyevent", "KEYCODE_MOVE_END")
    shell("input", "keyevent", "KEYCODE_DEL")
    shell("input", "text", str(minutes))
    shell("input", "keyevent", "KEYCODE_BACK")
    tap_label("保存する")
    read_node(f"{minutes}:00", exact=False)


def main():
    shell("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    adb("install", "-r", "android-release/REP-LIFT-1.0.0.apk")
    launch(TRAINING)
    save_run(10)

    adb("install", "-r", "android-release/WakeGuard-2.17.39-link.apk")
    # Installing WakeGuard after REP LIFT covers connecting a previously standalone installation.
    for permission in ("POST_NOTIFICATIONS", "ACTIVITY_RECOGNITION"):
        shell("pm", "grant", WAKE, f"android.permission.{permission}")
    assert f"package:{WAKE}" in shell("pm", "list", "packages", WAKE)
    assert f"package:{TRAINING}" in shell("pm", "list", "packages", TRAINING)

    launch(TRAINING)
    tap_label("設定")
    scroll_to_settings_bottom()
    connect = read_node("WakeGuard と連携", scroll=True)
    print(f"Connect button at {connect.attrib['bounds']}", flush=True)
    tap(connect)
    read_node("連携中", scroll=True)
    read_node("共有ストリーク", scroll=True)
    read_node("イグニド", exact=False, scroll=True)

    save_run(11)
    tap_label("設定")
    scroll_to_settings_bottom()
    read_node("Lv. 2", exact=False, scroll=True)

    launch(WAKE)
    read_node(("共通ストリーク 1日", "Shared streak · 1 days"), exact=False, scroll=True)


if __name__ == "__main__":
    main()
