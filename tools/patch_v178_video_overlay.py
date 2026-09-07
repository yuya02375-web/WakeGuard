from pathlib import Path
import re

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Version bump.
p = app / "build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = re.sub(r'versionCode = \d+', 'versionCode = 89', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.8"', s)
p.write_text(s, encoding="utf-8")

svc = java / "AlarmService.java"
s = svc.read_text(encoding="utf-8")

# The TYPE_APPLICATION_OVERLAY step guard is deliberately opaque. In v1.7.7 it was
# created even while AlarmActivity was rendering a video, so it covered the entire
# PlayerView. Keep the overlay only as a fallback when the real alarm Activity is not
# visible, and never place it over a video alarm.
old = '''                if (Settings.canDrawOverlays(AlarmService.this)) {
                    if (!AlarmActivity.visible) {
                        try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}
                    }
                    ensureOverlay();
                }'''
new = '''                if (Settings.canDrawOverlays(AlarmService.this)) {
                    if (!AlarmActivity.visible) {
                        try { AlarmActivity.launch(AlarmService.this); } catch (Throwable ignored) {}
                    }
                    if (isActiveVideo() || AlarmActivity.visible) removeOverlay();
                    else ensureOverlay();
                }'''
assert old in s, "guard overlay block not found"
s = s.replace(old, new, 1)

old = '''        if(ACTION_VIDEO_READY.equals(action)){videoPlaybackReady=true;cancelVideoFallback();if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))silenceServiceAudioForVideo();return START_STICKY;}'''
new = '''        if(ACTION_VIDEO_READY.equals(action)){videoPlaybackReady=true;cancelVideoFallback();removeOverlay();if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))silenceServiceAudioForVideo();return START_STICKY;}'''
assert old in s, "video ready action not found"
s = s.replace(old, new, 1)

old = '''    private void ensureOverlay() {
        if (!"STEPS".equals(Prefs.sessionMissionType(this))) { removeOverlay(); return; }
        if (!Prefs.active(this) || !Settings.canDrawOverlays(this) || overlayView != null) return;'''
new = '''    private void ensureOverlay() {
        // Do not cover AlarmActivity. It already renders the mission UI, and for video
        // alarms its PlayerView must stay visible behind that UI.
        if (!"STEPS".equals(AlarmStore.normalizeMission(Prefs.sessionMissionType(this)))
                || isActiveVideo() || AlarmActivity.visible) { removeOverlay(); return; }
        if (!Prefs.active(this) || !Settings.canDrawOverlays(this) || overlayView != null) return;'''
assert old in s, "ensureOverlay header not found"
s = s.replace(old, new, 1)

svc.write_text(s, encoding="utf-8")

# Validation.
build = p.read_text(encoding="utf-8")
assert 'versionName = "1.7.8"' in build
assert 'versionCode = 89' in build
ss = svc.read_text(encoding="utf-8")
assert 'if (isActiveVideo() || AlarmActivity.visible) removeOverlay();' in ss
assert 'ACTION_VIDEO_READY.equals(action)){videoPlaybackReady=true;cancelVideoFallback();removeOverlay();' in ss
assert '|| isActiveVideo() || AlarmActivity.visible' in ss
assert 'PixelFormat.OPAQUE' in ss
print("IGNIDO Wake Android v1.7.8 video mission overlay fix applied")
