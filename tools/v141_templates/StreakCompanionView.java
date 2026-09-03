package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/**
 * One permanent living flame. No collectible/game rendering.
 * The body itself is fire: a stable low-frequency silhouette with layered high-frequency
 * tongues, a white-hot core, embers and expanding flame streams as growth rises.
 */
public class StreakCompanionView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF oval=new RectF();
    private long level=1L;
    private int streak=0;
    private boolean animate=true;

    public StreakCompanionView(Context c){
        super(c);
        p.setDither(true);
        stroke.setDither(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }

    public void setGrowth(long growthLevel,int currentStreak){
        level=Math.max(1L,growthLevel);
        streak=Math.max(0,currentStreak);
        invalidate();
    }
    public void setAnimationEnabled(boolean enabled){animate=enabled;invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<2||h<2)return;
        float scale=Math.min(w/360f,h/410f);
        float cx=w*.5f,cy=h*.53f;
        double power=StreakGrowth.visualPower(level);
        float phase=animate?(SystemClock.uptimeMillis()%10000L)/10000f:0f;
        float breath=animate?(float)Math.sin(phase*Math.PI*2.0)*1.8f:0f;

        c.save();
        c.translate(cx,cy+breath);
        c.scale(scale,scale);
        drawAmbient(c,power,phase);
        drawLivingFlame(c,power,phase);
        c.restore();

        if(animate&&isAttachedToWindow())postInvalidateOnAnimation();
    }

    private int alpha(int col,int a){
        return Color.argb(Math.max(0,Math.min(255,a)),Color.red(col),Color.green(col),Color.blue(col));
    }
    private int blend(int a,int b,float t){
        t=Math.max(0f,Math.min(1f,t));
        return Color.argb(
            (int)(Color.alpha(a)+(Color.alpha(b)-Color.alpha(a))*t),
            (int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),
            (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),
            (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
    }
    private int outerColor(double power){
        float t=(float)Math.min(1.0,power/14.0);
        return blend(0xffb60c05,0xff8c0dff,t*.55f);
    }
    private int midColor(double power){
        float t=(float)Math.min(1.0,power/13.0);
        return blend(0xffff4a05,0xffff177d,t*.34f);
    }
    private int hotColor(double power){
        float t=(float)Math.min(1.0,power/12.0);
        return blend(0xffffb000,0xffffe66d,t*.72f);
    }
    private int coreColor(double power){
        float t=(float)Math.min(1.0,Math.max(0.0,(power-6.0)/10.0));
        return blend(0xfffffff1,0xffd8fbff,t);
    }
    private int highEnergy(double power){
        float t=(float)Math.min(1.0,Math.max(0.0,(power-7.0)/11.0));
        return blend(0xffffd54f,0xff6ee9ff,t);
    }

    private void drawAmbient(Canvas c,double power,float phase){
        int outer=outerColor(power),hot=hotColor(power),energy=highEnergy(power);
        float growth=(float)Math.log1p(Math.max(1.0,power));
        float auraR=112f+growth*13f;
        int glowA=48+(int)Math.min(72,power*4.0);
        p.setShader(new RadialGradient(0,2,auraR,
            new int[]{alpha(coreColor(power),26),alpha(hot,glowA),alpha(outer,32),0x00000000},
            new float[]{0f,.28f,.58f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(0,3,auraR,p);p.setShader(null);

        // Heat halos remain subtle at low level and become more architectural at high level.
        int halos=Math.max(0,Math.min(7,(int)(power/2.7)));
        for(int i=0;i<halos;i++){
            float rr=93f+i*10.5f+(float)(power%1.0)*2f;
            float rot=phase*360f*(i%2==0?1f:-.68f)+i*37f;
            oval.set(-rr,-rr*.86f,rr,rr*.86f);
            stroke.setStrokeWidth(.8f+(i%3)*.35f);
            stroke.setColor(alpha(i%2==0?energy:hot,28+Math.max(0,58-i*6)));
            for(int k=0;k<5+i%3;k++)c.drawArc(oval,rot+k*(360f/(5+i%3)),13f+(k%3)*7f,false,stroke);
        }

        // Embers. Count is bounded for frame rate, but positions/style keep evolving with uncapped power.
        int particles=18+Math.min(54,(int)(power*3.2))+Math.min(18,streak);
        Random rng=new Random(level*6364136223846793005L+1442695040888963407L);
        for(int i=0;i<particles;i++){
            float a=(float)(rng.nextDouble()*Math.PI*2.0)+phase*(i%2==0?2.2f:-1.4f);
            float rr=70f+rng.nextFloat()*(80f+(float)Math.min(34.0,power*2.2));
            float x=(float)Math.cos(a)*rr;
            float y=(float)Math.sin(a)*rr*.88f-rng.nextFloat()*20f;
            float sz=.7f+rng.nextFloat()*(1.6f+(float)Math.min(1.7,power*.08));
            int col=(i%5==0)?energy:(i%2==0?hot:outer);
            p.setColor(alpha(col,70+rng.nextInt(165)));p.setShader(null);
            c.drawCircle(x,y,sz,p);
            if(i%6==0){stroke.setColor(alpha(col,90));stroke.setStrokeWidth(Math.max(.7f,sz*.55f));c.drawLine(x,y+sz*3f,x,y-sz*5f,stroke);}
        }
    }

    private void drawLivingFlame(Canvas c,double power,float phase){
        float rise=(float)Math.min(38.0,power*3.2);
        float bodyH=205f+rise;
        float bodyW=70f+(float)Math.min(22.0,power*1.6);

        // At higher levels, wide flame streams appear behind the central body. They are still fire,
        // not solid wings or armor.
        if(power>4.2)drawSideStreams(c,power,phase,bodyH,bodyW);
        if(power>7.0)drawFlameCrown(c,power,phase,bodyH);

        drawFlameLayer(c,power,phase,bodyH+20f,bodyW+18f,0,outerColor(power),alpha(outerColor(power),25));
        drawFlameLayer(c,power,phase,bodyH,bodyW,1,midColor(power),outerColor(power));
        drawFlameLayer(c,power,phase,bodyH*.80f,bodyW*.69f,2,hotColor(power),midColor(power));
        drawFlameLayer(c,power,phase,bodyH*.57f,bodyW*.42f,3,coreColor(power),hotColor(power));

        drawInnerCurrent(c,power,phase,bodyH);
        drawPeripheralTongues(c,power,phase,bodyH,bodyW);
        drawCorePulse(c,power,phase);
    }

    private void drawFlameLayer(Canvas c,double power,float phase,float height,float width,int layer,int topColor,int bottomColor){
        int lobes=7+layer*2+Math.min(11,(int)(power*.72));
        buildFlamePath(path,height,width,lobes,phase,layer);
        int top=alpha(topColor,layer==0?150:245);
        int bottom=alpha(bottomColor,layer==0?65:235);
        p.setShader(new LinearGradient(0,-height*.68f,0,115f,
            new int[]{top,layer>=2?topColor:blend(topColor,bottomColor,.35f),bottom},
            new float[]{0f,.47f,1f},Shader.TileMode.CLAMP));
        c.drawPath(path,p);p.setShader(null);
        if(layer==1){stroke.setColor(alpha(highEnergy(power),70));stroke.setStrokeWidth(.8f);c.drawPath(path,stroke);}
    }

    /** Builds one coherent flame silhouette with smooth low-frequency motion and sharper cusp detail. */
    private void buildFlamePath(Path out,float height,float maxWidth,int lobes,float phase,int layer){
        out.reset();
        int n=Math.max(12,lobes*2);
        float[] ly=new float[n+1],lw=new float[n+1],rw=new float[n+1];
        for(int i=0;i<=n;i++){
            float t=i/(float)n;          // 0 = bottom, 1 = tip
            float y=112f-height*t;
            float profile=(float)Math.pow(Math.sin(Math.PI*Math.min(.985f,t*.93f+.035f)),.63);
            profile*=1f-.23f*t;
            float wave1=(float)Math.sin((t*lobes+phase*1.18f+layer*.17f)*Math.PI*2f);
            float wave2=(float)Math.sin((t*(lobes*.47f)+phase*.73f+layer*.31f)*Math.PI*2f);
            float cusp=(wave1*.11f+wave2*.065f)*(1f-.35f*t);
            float asym=(float)Math.sin((t*3.2f+phase*.46f+layer)*Math.PI*2f)*.055f;
            float base=maxWidth*(profile+cusp);
            if(i==0)base=maxWidth*.46f;
            if(i==n)base=0f;
            ly[i]=y;
            lw[i]=-Math.max(0f,base*(1f+asym));
            rw[i]= Math.max(0f,base*(1f-asym));
        }
        out.moveTo(lw[0],ly[0]);
        for(int i=1;i<=n;i++){
            float mx=(lw[i-1]+lw[i])*.5f,my=(ly[i-1]+ly[i])*.5f;
            out.quadTo(lw[i-1],ly[i-1],mx,my);
        }
        out.lineTo(0,ly[n]-4f-(layer==0?8f:0f));
        for(int i=n;i>=1;i--){
            float mx=(rw[i]+rw[i-1])*.5f,my=(ly[i]+ly[i-1])*.5f;
            out.quadTo(rw[i],ly[i],mx,my);
        }
        out.lineTo(rw[0],ly[0]);
        out.quadTo(0,130f,lw[0],ly[0]);
        out.close();
    }

    private void drawPeripheralTongues(Canvas c,double power,float phase,float bodyH,float bodyW){
        int count=5+Math.min(17,(int)(power*1.25));
        int outer=outerColor(power),mid=midColor(power),hot=hotColor(power);
        for(int i=0;i<count;i++){
            int side=(i%2==0)?-1:1;
            float t=(i+1f)/(count+1f);
            float y=94f-bodyH*(.10f+.60f*t);
            float x=side*(bodyW*(.52f+.23f*(float)Math.sin(t*Math.PI)));
            float len=24f+(i%5)*7f+(float)Math.min(34.0,power*1.7);
            float sway=animate?(float)Math.sin((phase*2f+t*1.7f)*Math.PI*2f)*5f:0f;
            path.reset();path.moveTo(x-side*7f,y+18f);
            path.cubicTo(x-side*18f,y+3f,x+side*(8f+sway),y-len*.56f,x+side*(12f+sway),y-len);
            path.cubicTo(x+side*(26f+sway),y-len*.51f,x+side*17f,y+2f,x+side*8f,y+19f);path.close();
            int col=i%3==0?hot:(i%3==1?mid:outer);
            p.setShader(new LinearGradient(x,y-len,x,y+20f,alpha(col,210),alpha(outer,18),Shader.TileMode.CLAMP));
            c.drawPath(path,p);p.setShader(null);
        }
    }

    private void drawSideStreams(Canvas c,double power,float phase,float bodyH,float bodyW){
        int outer=outerColor(power),mid=midColor(power),energy=highEnergy(power);
        float span=84f+(float)Math.min(66.0,(power-4.0)*6.2);
        int streams=2+Math.min(6,(int)((power-4.0)/1.5));
        for(int side=-1;side<=1;side+=2){
            for(int i=0;i<streams;i++){
                float y=20f+i*14f;
                float tipY=-35f-i*18f-(animate?(float)Math.sin((phase+i*.13f)*Math.PI*2f)*4f:0f);
                float tipX=side*(bodyW*.55f+span*(.54f+i*.08f));
                path.reset();path.moveTo(side*(bodyW*.45f),y+25f);
                path.cubicTo(side*(bodyW*.75f),y-18f,tipX-side*28f,tipY+24f,tipX,tipY);
                path.cubicTo(tipX-side*5f,tipY+25f,side*(bodyW*.72f),y+24f,side*(bodyW*.44f),y+34f);path.close();
                int col=i%2==0?mid:energy;
                p.setShader(new LinearGradient(0,tipY,0,y+40f,alpha(col,125),alpha(outer,8),Shader.TileMode.CLAMP));
                c.drawPath(path,p);p.setShader(null);
            }
        }
    }

    private void drawFlameCrown(Canvas c,double power,float phase,float bodyH){
        int hot=hotColor(power),energy=highEnergy(power),outer=outerColor(power);
        int rays=5+Math.min(10,(int)((power-7.0)*1.15));
        float top=-bodyH+112f;
        for(int i=0;i<rays;i++){
            float q=(i-(rays-1)/2f)/Math.max(1f,(rays-1)/2f);
            float x=q*(50f+(float)Math.min(26.0,power));
            float len=28f+(1f-Math.abs(q))*38f+(i%3)*7f;
            float sway=animate?(float)Math.sin((phase*1.4f+i*.17f)*Math.PI*2f)*4f:0f;
            path.reset();path.moveTo(x-7f,top+28f);path.cubicTo(x-13f,top+10f,x+sway-4f,top-len*.55f,x+sway,top-len);path.cubicTo(x+sway+10f,top-len*.50f,x+13f,top+9f,x+7f,top+28f);path.close();
            int col=i%3==0?energy:(i%2==0?hot:outer);
            p.setShader(new LinearGradient(0,top-len,0,top+30f,alpha(col,185),alpha(outer,12),Shader.TileMode.CLAMP));c.drawPath(path,p);p.setShader(null);
        }
    }

    private void drawInnerCurrent(Canvas c,double power,float phase,float bodyH){
        int energy=highEnergy(power),hot=hotColor(power);
        int threads=3+Math.min(9,(int)(power*.75));
        for(int i=0;i<threads;i++){
            float q=(i-(threads-1)/2f)/Math.max(1f,(threads-1)/2f);
            float baseX=q*28f;
            path.reset();path.moveTo(baseX,89f);
            float sway=(float)Math.sin((phase+i*.19f)*Math.PI*2f)*8f;
            path.cubicTo(baseX+sway,40f,baseX-sway*.8f,-bodyH*.30f,baseX+sway*.4f,-bodyH*.53f);
            stroke.setStrokeWidth(i%3==0?2.1f:1.15f);
            stroke.setColor(alpha(i%2==0?energy:hot,70+Math.min(90,(int)(power*5))));
            c.drawPath(path,stroke);
        }
    }

    private void drawCorePulse(Canvas c,double power,float phase){
        int hot=hotColor(power),core=coreColor(power),energy=highEnergy(power);
        float pulse=1f+(animate?(float)Math.sin(phase*Math.PI*2f)*.055f:0f);
        float rr=(24f+(float)Math.min(13.0,power*.75))*pulse;
        p.setShader(new RadialGradient(0,51f,rr,new int[]{Color.WHITE,core,alpha(hot,160),0x00ffffff},new float[]{0f,.24f,.62f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(0,51f,rr,p);p.setShader(null);
        if(power>8.0){
            stroke.setStrokeWidth(1.1f);stroke.setColor(alpha(energy,95));
            float r2=rr+8f+(float)Math.sin(phase*Math.PI*2f)*2f;
            c.drawCircle(0,51f,r2,stroke);
        }
    }
}
