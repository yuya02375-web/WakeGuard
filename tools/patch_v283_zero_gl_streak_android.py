from pathlib import Path

root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

# Version bump from the tested Android 2.8.1 base.
p="build.gradle.kts"
s=read(p)
s=s.replace('versionCode = 181','versionCode = 183',1)
s=s.replace('versionName = "2.8.1"','versionName = "2.8.3"',1)
# Remove any Filament dependencies if an older base slips in.
for dep in [
    '    implementation("com.google.android.filament:filament-android:1.75.1")\n',
    '    implementation("com.google.android.filament:gltfio-android:1.75.1")\n',
    '    implementation("com.google.android.filament:filament-utils-android:1.75.1")\n',
]:
    s=s.replace(dep,'')
write(p,s)

java=r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import java.util.Random;

/**
 * 2.8.3: zero-GL streak companion.
 *
 * Deliberately uses only Canvas drawing. No Filament, EGL, GLSurfaceView,
 * native model loader, JNI renderer, or GPU context is created when the
 * streak screen opens. This keeps the streak screen usable even on devices
 * where the previous 3D renderer crashed during initialization.
 */
public final class StreakCompanionView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dragon=new Path();
    private final Path wingLeft=new Path();
    private final Path wingRight=new Path();
    private final Random random=new Random(283L);
    private final float[] sparkX=new float[34];
    private final float[] sparkPhase=new float[34];
    private long level=1L;
    private int streak=0;
    private boolean animationEnabled=true;
    private boolean attached=false;

    public StreakCompanionView(Context context){
        super(context);
        setBackgroundColor(0xff0b0d13);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        for(int i=0;i<sparkX.length;i++){
            sparkX[i]=random.nextFloat();
            sparkPhase[i]=random.nextFloat();
        }
        setContentDescription("ストリーク炎竜");
    }

    public void setGrowth(long lv,int currentStreak){
        level=Math.max(1L,lv);
        streak=Math.max(0,currentStreak);
        invalidate();
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        if(enabled && attached) postInvalidateOnAnimation();
    }

    public void onResume(){
        animationEnabled=true;
        if(attached) postInvalidateOnAnimation();
    }

    public void onPause(){
        animationEnabled=false;
    }

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        if(animationEnabled) postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        super.onDetachedFromWindow();
    }

    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        final float w=getWidth(),h=getHeight();
        if(w<=1f||h<=1f)return;

        final float cx=w*.5f;
        final float cy=h*.55f;
        final float min=Math.min(w,h);
        final float growth=clamp((level-1f)/70f);
        final float pulse=animationEnabled?(float)(.5+.5*Math.sin(android.os.SystemClock.uptimeMillis()/620.0)):.5f;

        // Warm halo behind the companion.
        float halo=min*(.34f+.045f*growth+.012f*pulse);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                cx,cy,halo,
                new int[]{0x55ff5a16,0x32ff2600,0x120f3dff,0x00000000},
                new float[]{0f,.45f,.76f,1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,halo,paint);
        paint.setShader(null);

        // Ground glow.
        paint.setShader(new RadialGradient(
                cx,h*.83f,min*.30f,
                new int[]{0x38ff3a08,0x120a2a7f,0x00000000},
                new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f,.30f,cx,h*.83f);
        c.drawCircle(cx,h*.83f,min*.30f,paint);
        c.restore();
        paint.setShader(null);

        float scale=min*(.00215f+.00016f*growth);
        c.save();
        c.translate(cx,cy+min*.045f);
        c.scale(scale,scale);

        // Body / neck / head / tail silhouette in a stable normalized coordinate system.
        dragon.reset();
        dragon.moveTo(-34,72);
        dragon.cubicTo(-48,50,-45,20,-28,-2);
        dragon.cubicTo(-20,-14,-13,-28,-14,-44);
        dragon.cubicTo(-14,-68,1,-87,24,-91);
        dragon.cubicTo(38,-94,56,-87,63,-77);
        dragon.cubicTo(53,-76,48,-69,45,-61);
        dragon.cubicTo(62,-57,71,-48,76,-37);
        dragon.cubicTo(64,-41,54,-39,46,-33);
        dragon.cubicTo(37,-26,34,-14,38,-3);
        dragon.cubicTo(49,22,45,53,28,75);
        dragon.cubicTo(15,91,-7,95,-24,83);
        dragon.cubicTo(-48,106,-78,108,-96,94);
        dragon.cubicTo(-69,96,-50,86,-34,72);
        dragon.close();

        paint.setShader(new LinearGradient(-70,-95,72,95,
                new int[]{0xff16264f,0xff3a1010,0xff8b1b0b,0xff20090a},
                null,Shader.TileMode.CLAMP));
        c.drawPath(dragon,paint);
        paint.setShader(null);

        // Wings.
        wingLeft.reset();
        wingLeft.moveTo(-22,-2);
        wingLeft.cubicTo(-58,-47,-111,-62,-148,-48);
        wingLeft.cubicTo(-119,-26,-111,8,-117,39);
        wingLeft.cubicTo(-87,17,-62,15,-39,28);
        wingLeft.close();
        paint.setColor(0xff42100e);
        c.drawPath(wingLeft,paint);

        wingRight.reset();
        wingRight.moveTo(34,0);
        wingRight.cubicTo(65,-45,117,-55,146,-36);
        wingRight.cubicTo(116,-18,104,13,107,43);
        wingRight.cubicTo(83,19,58,18,39,31);
        wingRight.close();
        paint.setColor(0xff2b1326);
        c.drawPath(wingRight,paint);

        // Wing membranes / scale lines.
        stroke.setStrokeWidth(3.2f);
        stroke.setColor(0x88ff4b18);
        c.drawLine(-27,-3,-119,-42,stroke);
        c.drawLine(-33,8,-111,19,stroke);
        c.drawLine(36,-2,119,-33,stroke);
        c.drawLine(39,10,102,24,stroke);

        // Horns.
        Path horn=new Path();
        horn.moveTo(19,-88);horn.lineTo(7,-126);horn.lineTo(31,-94);horn.close();
        paint.setColor(0xffd7a36a);c.drawPath(horn,paint);
        horn.reset();horn.moveTo(41,-84);horn.lineTo(51,-120);horn.lineTo(51,-82);horn.close();
        c.drawPath(horn,paint);

        // Eye.
        paint.setColor(0xffffcf52);
        c.drawCircle(47,-70,4.7f+1.2f*pulse,paint);
        paint.setColor(0xffff4b0b);
        c.drawCircle(48,-70,2.1f,paint);

        // Chest fire becomes stronger as the companion grows.
        float chestAlpha=.35f+.55f*growth;
        paint.setShader(new RadialGradient(7,18,48,
                new int[]{((int)(255*chestAlpha)<<24)|0x00ffb02e,0x99ff3b0b,0x00100000},
                new float[]{0f,.38f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(7,18,48,paint);
        paint.setShader(null);

        // Growth marks.
        stroke.setStrokeWidth(2.4f);
        stroke.setColor(0x88ff8a2a);
        int marks=3+(int)Math.min(9,level/18);
        for(int i=0;i<marks;i++){
            float y=-15+i*10f;
            c.drawLine(-16,y,18,y-3f,stroke);
        }
        c.restore();

        // Sparks are Canvas-only and intentionally lightweight.
        long now=android.os.SystemClock.uptimeMillis();
        float density=getResources().getDisplayMetrics().density;
        for(int i=0;i<sparkX.length;i++){
            float t=((now/1000f)*(.15f+.0065f*i)+sparkPhase[i])%1f;
            float x=w*(.16f+.68f*sparkX[i]);
            float y=h*(.90f-.78f*t);
            int alpha=(int)(255f*(1f-t)*(.20f+.42f*growth));
            sparkPaint.setColor((alpha<<24)|0x00ff7a18);
            float r=(.8f+(i%4)*.38f)*density;
            c.drawCircle(x,y,r,sparkPaint);
        }

        // Small streak pulse marker; no text allocation in the draw loop.
        if(streak>0){
            paint.setColor(0x66ffc857);
            c.drawCircle(w*.84f,h*.16f,(7f+3f*pulse)*density,paint);
        }

        if(animationEnabled && attached) postInvalidateDelayed(48);
    }
}
''';
write("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java",java)

assert 'versionName = "2.8.3"' in read("build.gradle.kts")
assert 'GLSurfaceView' not in read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert 'android.opengl' not in read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert 'com.google.android.filament' not in read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
print("Android 2.8.3 zero-GL streak fix applied")
