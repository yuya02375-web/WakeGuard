from pathlib import Path
root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p="build.gradle.kts"; s=read(p)
s=s.replace('versionCode = 181','versionCode = 182',1).replace('versionName = "2.8.1"','versionName = "2.8.2"',1)
write(p,s)

p="src/main/java/jp/wakeguard/alarm/StreakCompanionView.java"; s=read(p)
if 'import com.google.android.filament.Filament;' not in s:
    s=s.replace('import com.google.android.filament.Engine;','import com.google.android.filament.Engine;\nimport com.google.android.filament.Filament;',1)

s=s.replace('''public final class StreakCompanionView extends FrameLayout {
    static { Utils.INSTANCE.init(); }
''','''public final class StreakCompanionView extends FrameLayout {
    private static volatile boolean filamentReady = false;
    private static volatile Throwable filamentInitError = null;

    private static synchronized boolean ensureFilamentReady() {
        if (filamentReady) return true;
        if (filamentInitError != null) return false;
        try {
            // Required by Filament before Engine / ModelViewer / JNI-backed API use.
            Filament.init();
            Utils.INSTANCE.init();
            filamentReady = true;
            return true;
        } catch (Throwable t) {
            filamentInitError = t;
            return false;
        }
    }
''',1)

old='''        initializeFilament();

        frameCallback = new Choreographer.FrameCallback() {'''
new='''        if (ensureFilamentReady()) {
            initializeFilament();
        } else {
            showRendererFailure(filamentInitError);
        }

        frameCallback = new Choreographer.FrameCallback() {'''
if old not in s: raise SystemExit("constructor marker missing")
s=s.replace(old,new,1)

old='''                } catch (Throwable t) {
                    loadError.setText("3D描画エラー");
                    loadError.setVisibility(View.VISIBLE);
                }
                aura.invalidate();
                choreographer.postFrameCallback(this);
'''
new='''                } catch (Throwable t) {
                    showRendererFailure(t);
                    return;
                }
                aura.invalidate();
                if (rendering) choreographer.postFrameCallback(this);
'''
if old not in s: raise SystemExit("frame callback marker missing")
s=s.replace(old,new,1)

old='''        try {
            engine = Engine.create();
            uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
'''
new='''        try {
            engine = Engine.create();
            if (engine == null) throw new IllegalStateException("Filament Engine unavailable");
            uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
'''
if old not in s: raise SystemExit("engine marker missing")
s=s.replace(old,new,1)

old='''        } catch (Throwable t) {
            loadError.setText("3Dモデルを読み込めませんでした: "+t.getClass().getSimpleName());
            loadError.setVisibility(View.VISIBLE);
            surface.setVisibility(View.GONE);
        }
    }
'''
new='''        } catch (Throwable t) {
            showRendererFailure(t);
        }
    }

    private void showRendererFailure(Throwable t) {
        rendering = false;
        try { choreographer.removeFrameCallback(frameCallback); } catch (Throwable ignored) {}
        surface.setVisibility(View.GONE);
        aura.setAlpha(1f);
        loadError.setText("炎竜を表示できませんでした\\nストリーク画面はそのまま使えます");
        loadError.setVisibility(View.VISIBLE);
        try {
            getContext().getSharedPreferences("wake_guard", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_streak_render_error",
                            t == null ? "unknown" : t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()))
                    .apply();
        } catch (Throwable ignored) {}
    }
'''
if old not in s: raise SystemExit("failure marker missing")
s=s.replace(old,new,1)

s=s.replace('''    public void onResume() {
        if (rendering) return;
        rendering = true;
        choreographer.postFrameCallback(frameCallback);
    }
''','''    public void onResume() {
        if (rendering || viewer == null || surface.getVisibility() != View.VISIBLE) return;
        rendering = true;
        choreographer.postFrameCallback(frameCallback);
    }
''',1)

write(p,s)

assert 'versionName = "2.8.2"' in read("build.gradle.kts")
assert 'Filament.init();' in read(p)
assert 'showRendererFailure' in read(p)
assert 'viewer == null' in read(p)
print("Android 2.8.2 streak crash fix applied")
