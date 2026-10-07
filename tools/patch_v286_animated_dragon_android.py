from pathlib import Path

root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s):
    q=root/p
    q.parent.mkdir(parents=True,exist_ok=True)
    q.write_text(s)

p="build.gradle.kts"
s=read(p)
s=s.replace('versionCode = 185','versionCode = 186',1)
s=s.replace('versionName = "2.8.5"','versionName = "2.8.6"',1)
write(p,s)

java=r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.IOException;

/**
 * 2.8.6: real frame animation for the dragon body.
 * The bundled Animated WebP changes the wings, neck, chest and tail across frames.
 * There is still no OpenGL/EGL/Filament/JNI renderer on the device.
 */
public final class StreakCompanionView extends FrameLayout {
    private final GrowthBackdrop backdrop;
    private final ImageView glow;
    private final ImageView dragon;
    private AnimatedImageDrawable animatedDragon;

    private long level=1L;
    private int streak=0;
    private float growth=0f;
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
        addView(glow,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));

        dragon=new ImageView(context);
        dragon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        dragon.setAdjustViewBounds(true);
        dragon.setContentDescription("ストリーク炎竜");
        loadAnimatedDragon();
        addView(dragon,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER));

        setGrowth(1L,0);
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    private void loadAnimatedDragon(){
        try{
            ImageDecoder.Source source=ImageDecoder.createSource(getResources(),R.drawable.ignido_dragon_v286);
            Drawable drawable=ImageDecoder.decodeDrawable(source);
            dragon.setImageDrawable(drawable);
            if(drawable instanceof AnimatedImageDrawable){
                animatedDragon=(AnimatedImageDrawable)drawable;
                animatedDragon.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            }
        }catch(IOException | RuntimeException e){
            animatedDragon=null;
            dragon.setImageResource(R.drawable.ignido_dragon_v284);
        }
    }

    public void setGrowth(long lv,int currentStreak){
        level=Math.max(1L,lv);
        streak=Math.max(0,currentStreak);
        double power=StreakGrowth.visualPower(level);
        growth=clamp((float)((power-1.0)/9.0));

        float scale=.69f+.35f*growth;
        float restY=dp(32f*(1f-growth)-6f*growth);

        dragon.setScaleX(scale);
        dragon.setScaleY(scale);
        dragon.setTranslationY(restY);
        dragon.setAlpha(.78f+.22f*growth);

        float glowScale=scale*(1.03f+.018f*growth);
        glow.setScaleX(glowScale);
        glow.setScaleY(glowScale);
        glow.setTranslationY(restY+dp(2f));
        glow.setAlpha(.05f+.22f*growth);

        ColorMatrix saturation=new ColorMatrix();
        saturation.setSaturation(.72f+.36f*growth);
        ColorMatrix heat=new ColorMatrix(new float[]{
                1.00f+.10f*growth,0,0,0,9f*growth,
                0,1.00f+.025f*growth,0,0,1f*growth,
                0,0,.97f-.08f*growth,0,0,
                0,0,0,1,0
        });
        saturation.postConcat(heat);
        dragon.setColorFilter(new ColorMatrixColorFilter(saturation));

        backdrop.setGrowth(growth,streak);
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        if(animatedDragon==null)return;
        if(enabled && attached){
            if(!animatedDragon.isRunning())animatedDragon.start();
        }else if(animatedDragon.isRunning()){
            animatedDragon.stop();
        }
    }

    public void onResume(){setAnimationEnabled(true);}
    public void onPause(){setAnimationEnabled(false);}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        if(animationEnabled && animatedDragon!=null && !animatedDragon.isRunning())animatedDragon.start();
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        if(animatedDragon!=null && animatedDragon.isRunning())animatedDragon.stop();
        super.onDetachedFromWindow();
    }

    private static final class GrowthBackdrop extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float growth=0f;
        private int streak=0;

        GrowthBackdrop(Context c){super(c);}

        void setGrowth(float g,int s){growth=g;streak=s;invalidate();}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();
            if(w<=1f||h<=1f)return;
            float cx=w*.5f,cy=h*.56f,min=Math.min(w,h);

            float halo=min*(.30f+.10f*growth);
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

assert 'versionCode = 186' in read("build.gradle.kts")
assert 'versionName = "2.8.6"' in read("build.gradle.kts")
src=read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert 'AnimatedImageDrawable' in src
assert 'ImageDecoder' in src
assert 'ignido_dragon_v286' in src
assert 'ValueAnimator' not in src
assert 'android.opengl' not in src
assert 'com.google.android.filament' not in src
assert 'StreakGrowth.visualPower(level)' in src
print("Android 2.8.6 animated dragon patch applied")
