#!/usr/bin/env python3
"""Smoke-check two separately signed APKs on the Android emulator."""

from io import BytesIO
import re
import subprocess
import time
from datetime import date, timedelta
from pathlib import Path
from xml.etree import ElementTree
from PIL import Image, ImageChops

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


def verify_portrait_motion(node, name):
    bounds = tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))
    assert bounds[2] > bounds[0] and bounds[3] > bounds[1]
    frames = []
    for _ in range(10):
        raw = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=15)
        with Image.open(BytesIO(raw)) as screen:
            frames.append(screen.crop(bounds).convert("RGB"))
        time.sleep(0.11)
    first, *rest = frames
    differences = [ImageChops.difference(first, frame).getbbox() for frame in rest]
    assert any(diff and (diff[2] - diff[0]) * (diff[3] - diff[1]) >= 24 for diff in differences), name
    first.save(f"{name}.gif", save_all=True, append_images=rest, duration=180, loop=0)
    print(f"Verified changing sprite pixels in {name}", flush=True)


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


def seed_wake_history():
    # Synthetic wakeups on the private CI emulator. No REP LIFT workouts exist yet.
    adb("root")
    adb("wait-for-device")
    assert "uid=0" in shell("id")
    today = date.fromisoformat(shell("date", "+%Y-%m-%d"))
    offsets = [*range(1, 9), *range(44, 65), *range(70, 112, 2)]
    prefs = ElementTree.Element("map")
    dates = ElementTree.SubElement(prefs, "set", name="success_days")
    for offset in offsets:
        ElementTree.SubElement(dates, "string").text = (today - timedelta(days=offset)).isoformat()
    ElementTree.SubElement(prefs, "int", name="total_wakeups", value=str(len(offsets)))
    file = Path("wake-verify-fixture.xml")
    ElementTree.ElementTree(prefs).write(file, encoding="utf-8", xml_declaration=True)
    try:
        app_dir = f"/data/user/0/{WAKE}"
        prefs_dir = f"{app_dir}/shared_prefs"
        prefs_file = f"{prefs_dir}/wake_guard.xml"
        owner = shell("stat", "-c", "%u:%g", app_dir)
        shell("am", "force-stop", WAKE)
        shell("mkdir", "-p", prefs_dir)
        shell("chown", owner, prefs_dir)
        shell("chmod", "770", prefs_dir)
        adb("push", str(file), prefs_file)
        shell("chown", owner, prefs_file)
        shell("chmod", "660", prefs_file)
        shell("restorecon", "-RF", prefs_dir)
    finally:
        file.unlink(missing_ok=True)
    return today


def main():
    shell("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        shell("settings", "put", "global", key, "1")
    adb("install", "-r", "android-release/REP-LIFT-1.0.6.apk")
    launch(TRAINING)
    tap_label("ラン")
    read_node("手入力で記録")

    adb("install", "-r", "android-release/WakeGuard-2.17.49-link.apk")
    # Installing WakeGuard after REP LIFT covers connecting a previously standalone installation.
    for permission in ("POST_NOTIFICATIONS", "ACTIVITY_RECOGNITION"):
        shell("pm", "grant", WAKE, f"android.permission.{permission}")
    assert f"package:{WAKE}" in shell("pm", "list", "packages", WAKE)
    assert f"package:{TRAINING}" in shell("pm", "list", "packages", TRAINING)
    seed_wake_history()

    launch(TRAINING)
    tap_label("設定")
    scroll_to_settings_bottom()
    connect = read_node("WakeGuard と連携", scroll=True)
    print(f"Connect button at {connect.attrib['bounds']}", flush=True)
    tap(connect)
    read_node("連携中", scroll=True)
    scroll_to_settings_bottom()
    read_node("REP LIFTの運動ストリーク", exact=False)
    read_node("保存した筋トレ・ランはまだありません。", exact=False)
    read_node(("0", "0日", "0 日"))
    read_node("ドラゴンの成長", exact=False, scroll=True)
    read_node("Lv. 51", exact=False)
    verify_portrait_motion(read_node("成長段階のドラゴン", scroll=True), "rep-lift-original-dragon")
    nodes = visible_nodes()
    assert not any(label_matches(node, "共有の現在", False) for node in nodes)
    assert not any(label_matches(node, "ブランドアートを見る", False) for node in nodes)
    with Path("rep-lift-linked-zero-workouts.png").open("wb") as screenshot:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=screenshot, check=True, timeout=15)
    print("Verified exercise 0 with a closed shared summary and a Lv. 51 dragon", flush=True)
    tap_label("WakeGuardとの共有ストリーク", scroll=True)
    read_node("共有の現在", exact=False, scroll=True)
    read_node(("8", "8日", "8 日"))
    read_node("共有の最長", exact=False)
    read_node(("21", "21日", "21 日"))

    # The shared summary must also be visible in WakeGuard before the first workout.
    launch(WAKE)
    verify_portrait_motion(read_node(", Lv.51", exact=False), "wakeguard-original-dragon")
    read_node(("共通ストリーク", "Shared streak", "shared streak"), exact=False, scroll=True)
    read_node(("現在の連続 8日", "Current 8 days"), exact=False, scroll=True)

    launch(TRAINING)
    save_run(10)
    save_run(11)
    saved_day = date.fromisoformat(shell("date", "+%Y-%m-%d"))
    day_ja = f"{saved_day.month}月{saved_day.day}日"
    day_en = f"{saved_day.strftime('%b')} {saved_day.day}"
    tap_label("設定")
    scroll_to_settings_bottom()
    read_node("この端末で運動した日", exact=False, scroll=True)
    read_node(("1", "1日", "1 日"))
    read_node("Lv. 52", exact=False, scroll=True)

    launch(WAKE)
    read_node(("現在の連続 9日", "Current 9 days"), exact=False, scroll=True)
    read_node(("過去最長 21日", "Best 21 days"), exact=False, scroll=True)
    read_node((day_ja, day_en), exact=False, scroll=True)
    assert not any(label_matches(node, label, False)
                   for node in visible_nodes()
                   for label in ("ブランドアートを見る", "View brand artwork"))


if __name__ == "__main__":
    main()
