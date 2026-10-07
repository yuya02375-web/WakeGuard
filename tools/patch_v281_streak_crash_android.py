from pathlib import Path
root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p="build.gradle.kts"; s=read(p)
s=s.replace('versionCode = 180','versionCode = 181',1).replace('versionName = "2.8.0"','versionName = "2.8.1"',1)
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
            // Filament's Android API requires the JNI runtime to be initialized before Engine.create().
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
if old not in s: raise SystemExit("constructor initialize marker missing")
s=s.replace(old,new,1)

old='''    private void initializeFilament() {
        try {
            engine = Engine.create();
            uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
            viewer = new ModelViewer(surface, engine, uiHelper, null);
'''
new='''    private void initializeFilament() {
        try {
            engine = Engine.create();
            if (engine == null) throw new IllegalStateException("Filament Engine unavailable");
            uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
            viewer = new ModelViewer(surface, engine, uiHelper, null);
'''
if old not in s: raise SystemExit("initializeFilament marker missing")
s=s.replace(old,new,1)

old='''        } catch (Throwable t) {
            loadError.setText("3Dモデルを読み込めませんでした");
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
if old not in s: raise SystemExit("renderer failure marker missing")
s=s.replace(old,new,1)

# Never start render loop if renderer failed.
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

assert 'versionName = "2.8.1"' in read("build.gradle.kts")
assert 'Filament.init();' in read(p)
assert 'showRendererFailure' in read(p)
assert 'viewer == null' in read(p)
print("Android 2.8.1 streak crash fix applied")
