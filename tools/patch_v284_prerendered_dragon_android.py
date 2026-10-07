from pathlib import Path

root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s):
    q=root/p
    q.parent.mkdir(parents=True,exist_ok=True)
    q.write_text(s)

p="build.gradle.kts"
s=read(p)
s=s.replace('versionCode = 183','versionCode = 184',1)
s=s.replace('versionName = "2.8.3"','versionName = "2.8.4"',1)
write(p,s)

java=r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.Random;

/**
 * 2.8.4: pre-rendered hero dragon + lightweight Canvas fire.
 * No 3D/GPU context or native model loader is created on the device.
 */
public final class StreakCompanionView extends FrameLayout {
    private final AuraView aura;
    private final ImageView dragon;
    private final EmberView embers;
    private boolean animationEnabled=true;
    private boolean attached=false;
    private long level=1L;
    private int streak=0;

    private final Runnable animateTick=new Runnable(){
        @Override public void run(){
            if(!animationEnabled || !attached)return;
            long now=SystemClock.uptimeMillis();
            float breathe=(float)Math.sin(now/760.0);
            float drift=(float)Math.sin(now/1280.0);
            float growth=Math.max(0f,Math.min(1f,(level-1f)/70f));
            float base=0.955f+0.045f*growth;
            dragon.setScaleX(base + breathe*.009f);
            dragon.setScaleY(base + breathe*.012f);
            dragon.setTranslationY(dp(2.5f)*drift);
            dragon.setRotation(drift*.32f);
            aura.setPulse(breathe,growth);
            embers.setGrowth(growth,streak);
            postDelayed(this,48);
        }
    };

    public StreakCompanionView(Context context){
        super(context);
        setBackgroundColor(0xff0b0d13);
        setClipChildren(false);
        setClipToPadding(false);

        aura=new AuraView(context);
        addView(aura,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        dragon=new ImageView(context);
        dragon.setImageResource(R.drawable.ignido_dragon_v284);
        dragon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        dragon.setAdjustViewBounds(true);
        dragon.setContentDescription("ストリーク炎竜");
        dragon.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        LayoutParams imageLp=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER);
        int pad=(int)dp(8);
        dragon.setPadding(pad,pad,pad,pad);
        addView(dragon,imageLp);

        embers=new EmberView(context);
        embers.setClickable(false);
        addView(embers,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}

    public void setGrowth(long lv,int currentStreak){
        level=Math.max(1L,lv);
        streak=Math.max(0,currentStreak);
        float growth=Math.max(0f,Math.min(1f,(level-1f)/70f));
        aura.setPulse(0f,growth);
        embers.setGrowth(growth,streak);
        dragon.setAlpha(.90f+.10f*growth);
    }

    public void setAnimationEnabled(boolean enabled){
        animationEnabled=enabled;
        aura.setAnimationEnabled(enabled);
        embers.setAnimationEnabled(enabled);
        removeCallbacks(animateTick);
        if(enabled && attached)post(animateTick);
    }

    public void onResume(){setAnimationEnabled(true);}
    public void onPause(){setAnimationEnabled(false);}

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        attached=true;
        if(animationEnabled)post(animateTick);
    }

    @Override protected void onDetachedFromWindow(){
        attached=false;
        removeCallbacks(animateTick);
        super.onDetachedFromWindow();
    }

    private static final class AuraView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path flame=new Path();
        private boolean animation=true;
        private float pulse=0f,growth=0f;

        AuraView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setPulse(float p,float g){pulse=p;growth=g;invalidate();}
        void setAnimationEnabled(boolean e){animation=e;if(e)postInvalidateOnAnimation();}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();if(w<=1||h<=1)return;
            float cx=w*.5f,cy=h*.56f,min=Math.min(w,h);

            float halo=min*(.37f+.028f*growth+.008f*pulse);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx,cy,halo,
                    new int[]{0x22ffd45a,0x36ff4b12,0x1cff1900,0x080c2b73,0x00000000},
                    new float[]{0f,.28f,.58f,.79f,1f},Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,halo,paint);
            paint.setShader(null);

            float baseY=h*.83f;
            for(int i=0;i<7;i++){
                float x=cx+(i-3)*min*.055f;
                float tall=min*(.12f+.025f*((i+2)%3)+.018f*pulse);
                float wide=min*(.026f+.006f*(i%2));
                flame.reset();
                flame.moveTo(x-wide,baseY);
                flame.cubicTo(x-wide*.65f,baseY-tall*.38f,x-wide*.30f,baseY-tall*.64f,x,baseY-tall);
                flame.cubicTo(x+wide*.25f,baseY-tall*.62f,x+wide*.82f,baseY-tall*.35f,x+wide,baseY);
                flame.close();
                int a=(int)(58+55*growth);
                paint.setColor((a<<24)|0x00ff3b08);
                c.drawPath(flame,paint);
            }

            paint.setShader(new RadialGradient(cx,baseY,min*.27f,
                    new int[]{0x42ff5b12,0x1bff2100,0x00000000},
                    new float[]{0f,.5f,1f},Shader.TileMode.CLAMP));
            c.save();c.scale(1f,.24f,cx,baseY);
            c.drawCircle(cx,baseY,min*.27f,paint);
            c.restore();paint.setShader(null);

            if(animation)postInvalidateDelayed(60);
        }
    }

    private static final class EmberView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random=new Random(284L);
        private final float[] xs=new float[42],phase=new float[42],speed=new float[42],size=new float[42];
        private boolean animation=true;
        private float growth=0f;
        private int streak=0;

        EmberView(Context c){
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            for(int i=0;i<xs.length;i++){
                xs[i]=random.nextFloat();
                phase[i]=random.nextFloat();
                speed[i]=.14f+random.nextFloat()*.18f;
                size[i]=.7f+random.nextFloat()*1.8f;
            }
        }
        void setGrowth(float g,int s){growth=g;streak=s;invalidate();}
        void setAnimationEnabled(boolean e){animation=e;if(e)postInvalidateOnAnimation();}

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight();if(w<=1||h<=1)return;
            float density=getResources().getDisplayMetrics().density;
            long now=SystemClock.uptimeMillis();
            for(int i=0;i<xs.length;i++){
                float t=((now/1000f)*speed[i]+phase[i])%1f;
                float sway=(float)Math.sin((t*6.283f)+(i*.77f))*w*.018f;
                float x=w*(.17f+.66f*xs[i])+sway;
                float y=h*(.90f-.83f*t);
                int alpha=(int)(255f*(1f-t)*(.18f+.46f*growth));
                int rgb=(i%4==0)?0x00ffd35a:0x00ff5c18;
                paint.setColor((alpha<<24)|rgb);
                c.drawCircle(x,y,size[i]*density,paint);
            }
            if(streak>0){
                float pulse=.5f+.5f*(float)Math.sin(now/420.0);
                paint.setColor(0x70ffd45a);
                c.drawCircle(w*.84f,h*.16f,(5.5f+2.5f*pulse)*density,paint);
            }
            if(animation)postInvalidateDelayed(48);
        }
    }
}
''';
write("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java",java)

assert 'versionName = "2.8.4"' in read("build.gradle.kts")
src=read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
assert 'ignido_dragon_v284' in src
assert 'ImageView' in src
assert 'android.opengl' not in src
assert 'com.google.android.filament' not in src
print("Android 2.8.4 prerendered dragon patch applied")
