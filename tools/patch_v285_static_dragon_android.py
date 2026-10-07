from pathlib import Path

root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s):
    q=root/p
    q.parent.mkdir(parents=True,exist_ok=True)
    q.write_text(s)

p="build.gradle.kts"
s=read(p)
s=s.replace('versionCode = 184','versionCode = 185',1)
s=s.replace('versionName = "2.8.4"','versionName = "2.8.5"',1)
write(p,s)

java=r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Color;
import android.widget.ImageView;

/**
 * 2.8.5: static hero dragon only.
 * The streak screen simply displays the bundled dragon image.
 */
public final class StreakCompanionView extends ImageView {
    public StreakCompanionView(Context context){
        super(context);
        setImageResource(R.drawable.ignido_dragon_v284);
        setScaleType(ScaleType.FIT_CENTER);
        setAdjustViewBounds(true);
        setBackgroundColor(Color.TRANSPARENT);
        setContentDescription("ストリーク炎竜");
    }

    public void setGrowth(long level,int currentStreak){
        // Intentionally static. Growth values do not alter the artwork.
    }

    public void setAnimationEnabled(boolean enabled){
        // Intentionally static. No animation.
    }

    public void onResume(){
        // No renderer or animation to resume.
    }

    public void onPause(){
        // No renderer or animation to pause.
    }
}
''';

write("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java",java)

assert 'versionCode = 185' in read("build.gradle.kts")
assert 'versionName = "2.8.5"' in read("build.gradle.kts")
src=read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert 'extends ImageView' in src
assert 'setImageResource(R.drawable.ignido_dragon_v284)' in src
for forbidden in ['Canvas','Paint','Shader','postDelayed','postInvalidate','GLSurfaceView','android.opengl','com.google.android.filament']:
    assert forbidden not in src, forbidden
print("Android 2.8.5 static dragon patch applied")
