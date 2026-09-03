package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class StreakFlameView extends View {
    private int streak=0;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outer=new Path(),inner=new Path();

    public StreakFlameView(Context c){ super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
    public void setStreak(int value){ streak=Math.max(0,value); invalidate(); }
    public int level(){ if(streak>=30)return 5; if(streak>=14)return 4; if(streak>=7)return 3; if(streak>=3)return 2; if(streak>=1)return 1; return 0; }
    public String levelName(){ switch(level()){case 5:return "LEGEND FLAME";case 4:return "INFERNO";case 3:return "BLAZE";case 2:return "GROWING FLAME";case 1:return "BABY FLAME";default:return "EMBER";} }

    @Override protected void onDraw(Canvas c){ super.onDraw(c); float w=getWidth(),h=getHeight(),cx=w/2f; int lv=level();
        float scale=0.70f+lv*0.055f; float top=h*(0.08f-(lv*0.006f)); float bottom=h*0.91f; float half=w*0.34f*scale;
        p.setShadowLayer(22+lv*7,0,4,Color.argb(105+lv*20,255,82,16));
        p.setShader(new LinearGradient(0,top,0,bottom,Color.rgb(255,222,55),Color.rgb(255,62,12),Shader.TileMode.CLAMP));
        outer.reset(); outer.moveTo(cx,bottom);
        outer.cubicTo(cx-half*1.08f,bottom-h*.12f,cx-half*1.00f,bottom-h*.32f,cx-half*.56f,bottom-h*.45f);
        outer.cubicTo(cx-half*.28f,bottom-h*.58f,cx-half*.50f,top+h*.16f,cx,top);
        outer.cubicTo(cx+half*.12f,top+h*.16f,cx+half*.70f,bottom-h*.49f,cx+half*.52f,bottom-h*.34f);
        outer.cubicTo(cx+half*1.08f,bottom-h*.25f,cx+half*1.10f,bottom-h*.10f,cx,bottom); outer.close(); c.drawPath(outer,p);
        p.clearShadowLayer(); p.setShader(new LinearGradient(0,h*.39f,0,bottom,Color.rgb(255,250,130),Color.rgb(255,139,19),Shader.TileMode.CLAMP));
        inner.reset(); inner.moveTo(cx,bottom-h*.07f); inner.cubicTo(cx-half*.55f,bottom-h*.17f,cx-half*.38f,bottom-h*.34f,cx,bottom-h*.46f); inner.cubicTo(cx+half*.40f,bottom-h*.31f,cx+half*.45f,bottom-h*.16f,cx,bottom-h*.07f); inner.close(); c.drawPath(inner,p); p.setShader(null);
        if(lv>=3){ p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(Color.argb(160,255,225,92)); for(int i=0;i<lv-1;i++){float r=w*(.32f+i*.035f);c.drawArc(cx-r,h*.46f-r*.45f,cx+r,h*.46f+r*.55f,208,124,false,p);} p.setStyle(Paint.Style.FILL); }
        if(lv>=5){ p.setColor(Color.rgb(255,219,64)); Path crown=new Path(); float y=h*.16f; crown.moveTo(cx-w*.16f,y); crown.lineTo(cx-w*.10f,y-h*.08f); crown.lineTo(cx,y-h*.025f); crown.lineTo(cx+w*.10f,y-h*.08f); crown.lineTo(cx+w*.16f,y); crown.close(); c.drawPath(crown,p); }
        float faceY=h*.60f; p.setColor(Color.rgb(42,24,23)); c.drawCircle(cx-w*.075f,faceY,w*.021f,p); c.drawCircle(cx+w*.075f,faceY,w*.021f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND); RectF mouth=new RectF(cx-w*.07f,faceY+h*.025f,cx+w*.07f,faceY+h*.095f); c.drawArc(mouth,15,150,false,p);p.setStyle(Paint.Style.FILL);
        if(streak==0){ p.setColor(Color.argb(110,60,40,40)); c.drawCircle(cx,faceY,w*.19f,p); }
    }
}
