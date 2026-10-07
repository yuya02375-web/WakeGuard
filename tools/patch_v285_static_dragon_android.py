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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * 2.8.5: the prerendered dragon is integrated with the permanent streak growth system.
 * The dragon remains a normal drawable/ImageView: no OpenGL, EGL, Filament, JNI model
 * loader, or device-side 3D renderer is used.
 */
public final class StreakCompanionView extends FrameLayout {
    private final GrowthBackdrop backdrop;
    private final ImageView glow;
    private final ImageView dragon;
    private final ValueAnimator breatheAnimator;

    private long level=1L;
    private int streak=0;
    private float growth=0f;
    private float baseScale=.68f;
    private boolean animationEnabled=true;
    private boolean attached=false;

    public StreakCompanionView(Context context){
        super(context);
        setBackgroundColor(0xff0b0d13);
        setClipChildren(false);
        setClipToPadding(false);

        backdrop=new GrowthBackdrop(context);
        addView(backdrop,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        glow=new ImageView(context);
        glow.setImageResource(R.drawable.ignido_dragon_v284);
        glow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        glow.setAdjustViewBounds(true);
        glow.setColorFilter(0xffff3b10, PorterDuff.Mode.SRC_IN);
        glow.setAlpha(.08f);
        addView(glow,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));

        dragon=new ImageView(context);
        dragon.setImageResource(R.drawable.ignido_dragon_v284);
        dragon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        dragon.setAdjustViewBounds(true);
        dragon.setContentDescription("ストリーク炎竜");
        addView(dragon,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));

        breatheAnimator=ValueAnimator.ofFloat(0f,1f);
        breatheAnimator.setDuration(2600L);
        breatheAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breatheAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breatheAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breatheAnimator.addUpdateListener(a->applyPose((float)a.getAnimatedValue()));

        setGrowth(1L,0);
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    public void setGrowth(long lv,int currentStreak){
        level=Math.max(1L,lv);
        streak=Math.max(0,currentStreak);

        // Match the app's unbounded logarithmic growth model. Around Lv.1 the companion
        // is small and subdued; at long streak histories it fills the hero area and burns brighter.
        double power=StreakGrowth.visualPower(level);
        growth=clamp((float)((power-1.0)/9.0));
        baseScale=.68f+.36f*growth;

        float restY=dp(34f*(1f-growth)-6f*growth);
        dragon.setTranslationY(restY);
        glow.setTranslationY(restY+dp(2f));
        dragon.setAlpha(.76f+.24f*growth);
        glow.setAlpha(.055f+.20f*growth);

        // Keep the same high-detail artwork, but reveal its heat/contrast progressively.
        ColorMatrix saturation=new ColorMatrix();
        saturation.setSaturation(.70f+.38f*growth);
        ColorMatrix heat=new ColorMatrix(new float[]{
                1.00f+.10f*growth,0,0,0,10f*growth,
                0,1.00f+.025f*growth,0,0,1f*growth,
                0,0,.96f-.08f*growth,0,0,
                0,0,0,1,0
        });
        saturation.postConcat(heat);
        dragon.setColorFilter(new ColorMatrixColorFilter(saturation));

        backdrop.setGrowth(growth,streak);
        applyPose(.5f);
    }

    private void applyPose(float phase){
        float breathe=(phase-.5f)*2f;
        float liveScale=baseScale*(1f+.010f*breathe);
        dragon.setScaleX(liveScale);
        dragon.setScaleY(liveScale*(1f+.004f*breathe));
        dragon.setRotation(.22f*breathe);

        float glowScale=liveScale*(1.025f+.014f*growth);
        glow.setScaleX(glowScale);
        glow.setScaleY(glowScale);
        glow.setAlpha((.055f+.20f*growth)*(1f+.14f*breathe));

        float restY=dp(34f*(1f-growth)-6f*growth);
        dragon.setTranslationY(restY-dp(2.2f)*breathe);
        glow.setTranslationY(restY+dp(2f)-dp(2.0f)*breathe);
        backdrop.setPulse(breathe);
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        if(enabled && attached){
            if(!breatheAnimator.isStarted())breatheAnimator.start();
            else if(breatheAnimator.isPaused())breatheAnimator.resume();
        }else{
            if(breatheAnimator.isStarted() && !breatheAnimator.isPaused())breatheAnimator.pause();
        }
    }

    public void onResume(){setAnimationEnabled(true);}
    public void onPause(){setAnimationEnabled(false);}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        if(animationEnabled && !breatheAnimator.isStarted())breatheAnimator.start();
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        breatheAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private static final class GrowthBackdrop extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float growth=0f;
        private float pulse=0f;
        private int streak=0;

        GrowthBackdrop(Context c){super(c);}

        void setGrowth(float g,int s){growth=g;streak=s;invalidate();}
        void setPulse(float p){pulse=p;invalidate();}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            if(w<=1f||h<=1f)return;
            float cx=w*.5f,cy=h*.56f,min=Math.min(w,h);

            float halo=min*(.30f+.10f*growth+.008f*pulse);
            int centerAlpha=(int)(42+72*growth);
            int midAlpha=(int)(26+48*growth);
            paint.setShader(new RadialGradient(
                    cx,cy,halo,
                    new int[]{(centerAlpha<<24)|0x00ff6a20,(midAlpha<<24)|0x00ff2800,0x140b2b64,0x00000000},
                    new float[]{0f,.38f,.70f,1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,halo,paint);
            paint.setShader(null);

            float groundY=h*.86f;
            paint.setShader(new RadialGradient(
                    cx,groundY,min*(.20f+.08f*growth),
                    new int[]{0x3aff5a16,0x16ff2100,0x00000000},
                    new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
            c.save();
            c.scale(1f,.22f,cx,groundY);
            c.drawCircle(cx,groundY,min*(.20f+.08f*growth),paint);
            c.restore();
            paint.setShader(null);

            // A thin energy ring becomes visible only after the dragon is established.
            if(growth>.28f){
                int a=(int)(18+46*(growth-.28f)/.72f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f,min*.003f));
                paint.setColor((a<<24)|0x00ff5a18);
                c.drawCircle(cx,cy,min*(.31f+.035f*growth),paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }
}
''';
write("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java",java)

stats=read("src/main/java/jp/wakeguard/alarm/StatsActivity.java")
stats=stats.replace(
'''        companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,360)));
        LinearLayout summary=Ui.row(this);summary.setGravity(Gravity.BOTTOM);summary.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,12));''',
'''        companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,390)));
        LinearLayout summary=Ui.row(this);summary.setGravity(Gravity.BOTTOM);summary.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,12));'''
)
stats=stats.replace(
'''        growthLevel=Ui.text(this,"Lv.1",21,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setGravity(Gravity.END);summary.addView(growthLevel);
        growthForm=Ui.text(this,"",1,Ui.MUTED);growthForm.setVisibility(View.GONE);
        hero.addView(summary);body.addView(hero);''',
'''        LinearLayout growthBox=new LinearLayout(this);growthBox.setOrientation(LinearLayout.VERTICAL);growthBox.setGravity(Gravity.END);
        growthLevel=Ui.text(this,"Lv.1",21,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setGravity(Gravity.END);growthBox.addView(growthLevel);
        growthForm=Ui.text(this,"火種",13,Ui.ACCENT);growthForm.setGravity(Gravity.END);growthForm.setPadding(0,Ui.dp(this,3),0,0);growthBox.addView(growthForm);
        summary.addView(growthBox);
        hero.addView(summary);body.addView(hero);'''
)
stats=stats.replace(
'''        growthLevel.setText("Lv."+lv);companion.setGrowth(lv,streak);refreshProtection();renderCalendar();''',
'''        growthLevel.setText("Lv."+lv);growthForm.setText(StreakGrowth.growthDescriptor(this));companion.setGrowth(lv,streak);refreshProtection();renderCalendar();'''
)
write("src/main/java/jp/wakeguard/alarm/StatsActivity.java",stats)

assert 'versionCode = 185' in read("build.gradle.kts")
assert 'versionName = "2.8.5"' in read("build.gradle.kts")
src=read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert 'setImageResource(R.drawable.ignido_dragon_v284)' in src
assert 'StreakGrowth.visualPower(level)' in src
assert 'ValueAnimator' in src
assert 'android.opengl' not in src
assert 'com.google.android.filament' not in src
stats=read("src/main/java/jp/wakeguard/alarm/StatsActivity.java")
assert 'growthForm.setText(StreakGrowth.growthDescriptor(this))' in stats
assert 'Ui.dp(this,390)' in stats
print("Android 2.8.5 integrated growth dragon patch applied")
