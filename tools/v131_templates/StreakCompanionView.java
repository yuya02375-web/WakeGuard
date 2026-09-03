package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.Locale;
import java.util.Set;

/**
 * High-detail procedural companion renderer.
 * Each species has a genuinely different silhouette, face and motion; rarity and
 * decorations are layered separately so the same character does not look like a recolor.
 */
public class StreakCompanionView extends View {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fx=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private StreakGame.CharacterDef def;
    private int level=1;
    private Set<String> decors;
    private boolean animate=true;

    private static final int[] PALETTE={
        0xffff6737,0xff58c8ff,0xff8bd450,0xffffd84d,0xffbd8cff,0xffff7fb4,0xff62dfbd,0xfff38a55,
        0xff92a7ff,0xffe6edf7,0xff3ea4ff,0xffafff72,0xffff5668,0xff8377ff,0xffffa338,0xff4cd8ff
    };

    public StreakCompanionView(Context c){
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setCompanion(StreakGame.CharacterDef d,int lv,Set<String> ds){
        def=d; level=Math.max(1,lv); decors=ds; invalidate();
    }
    public void setAnimationEnabled(boolean enabled){animate=enabled;invalidate();}

    private int base(){return PALETTE[(def==null?0:Math.floorMod(def.theme,PALETTE.length))];}
    private int blend(int a,int b,float t){
        t=Math.max(0f,Math.min(1f,t));
        int r=(int)(Color.red(a)*(1-t)+Color.red(b)*t);
        int g=(int)(Color.green(a)*(1-t)+Color.green(b)*t);
        int bl=(int)(Color.blue(a)*(1-t)+Color.blue(b)*t);
        return Color.rgb(r,g,bl);
    }
    private int alpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}
    private int dark(int c){return blend(c,Color.BLACK,.50f);}
    private int light(int c){return blend(c,Color.WHITE,.35f);}
    private int rarityColor(StreakGame.CharacterDef d){
        int t=d==null?0:d.tier();
        if(t>=4)return 0xffffcc4d;
        if(t==3)return 0xffff5fd0;
        if(t==2)return 0xffa979ff;
        if(t==1)return 0xff4cb8ff;
        return d!=null&&d.starter()?0xffff8b3d:0xff8e9cab;
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        StreakGame.CharacterDef d=def==null?StreakGame.allCharacters().get(0):def;
        float w=getWidth(),h=getHeight(); if(w<=1||h<=1)return;
        long now=SystemClock.uptimeMillis();
        float phase=(now%4200L)/4200f*(float)(Math.PI*2.0);
        float bob=animate?(float)Math.sin(phase)*h*.012f:0f;
        float wing=animate?(float)Math.sin(phase*1.7f)*7f:0f;

        drawBackdrop(c,w,h,d,phase);
        c.save();
        c.translate(w*.5f,h*.56f+bob);
        float s=Math.min(w,h)*.00915f;
        c.scale(s,s);
        setupStroke();
        drawDecorBack(c,d,wing);
        if(d.starter()) drawStarter(c,starterStage(level),wing);
        else drawArchetype(c,d,wing);
        drawDecorFront(c,d);
        c.restore();
        drawRarityBadge(c,w,h,d);

        if(animate&&isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33L);
    }

    private void setupStroke(){
        line.setShader(null); line.setColor(0xff171b24); line.setStrokeWidth(2.0f); line.setStyle(Paint.Style.STROKE);
        fill.setStyle(Paint.Style.FILL); fill.setShader(null); fill.clearShadowLayer();
        fx.setShader(null); fx.setStyle(Paint.Style.FILL); fx.clearShadowLayer();
    }

    private void drawBackdrop(Canvas c,float w,float h,StreakGame.CharacterDef d,float phase){
        int rc=rarityColor(d),bc=base();
        float cx=w*.5f,cy=h*.57f,r=Math.min(w,h)*.41f;
        fx.setShader(new RadialGradient(cx,cy,r,alpha(bc,d.tier()>=3?80:50),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,r,fx); fx.setShader(null);

        if(d.tier()>=2||d.starter()){
            fx.setStyle(Paint.Style.STROKE); fx.setStrokeWidth(Math.max(2f,w*.008f)); fx.setColor(alpha(rc,d.tier()>=4?150:90));
            c.drawCircle(cx,cy,r*.82f,fx);
            if(d.tier()>=3){
                fx.setStrokeWidth(Math.max(1.2f,w*.0045f));
                for(int i=0;i<8;i++){
                    float a=phase*.15f+(float)(i*Math.PI/4.0);float x1=cx+(float)Math.cos(a)*r*.72f,y1=cy+(float)Math.sin(a)*r*.72f;
                    float x2=cx+(float)Math.cos(a)*r*.82f,y2=cy+(float)Math.sin(a)*r*.82f;c.drawLine(x1,y1,x2,y2,fx);
                }
            }
            fx.setStyle(Paint.Style.FILL);
        }
        if(d.tier()>=4){
            fx.setColor(alpha(rc,55));
            for(int i=0;i<12;i++){
                float a=phase*.08f+(float)(i*Math.PI/6.0);float rr=r*(.88f+.05f*(i%2));
                float x=cx+(float)Math.cos(a)*rr,y=cy+(float)Math.sin(a)*rr;c.drawCircle(x,y,Math.max(1.5f,w*.007f),fx);
            }
        }
        fx.setShader(new LinearGradient(0,h*.78f,0,h*.92f,alpha(bc,110),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawOval(new RectF(w*.20f,h*.79f,w*.80f,h*.92f),fx);fx.setShader(null);
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(Math.max(1f,w*.004f));fx.setColor(alpha(light(bc),95));
        c.drawOval(new RectF(w*.23f,h*.80f,w*.77f,h*.89f),fx);fx.setStyle(Paint.Style.FILL);
    }

    private void drawRarityBadge(Canvas c,float w,float h,StreakGame.CharacterDef d){
        String label=d.starter()?"EX":(d.rarity==null||d.rarity.isEmpty()?"N":d.rarity);
        int rc=rarityColor(d);
        rect.set(w*.07f,h*.07f,w*.25f,h*.16f);
        fx.setColor(alpha(blend(rc,Color.BLACK,.62f),230));c.drawRoundRect(rect,h*.025f,h*.025f,fx);
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(Math.max(1f,w*.005f));fx.setColor(alpha(rc,220));c.drawRoundRect(rect,h*.025f,h*.025f,fx);fx.setStyle(Paint.Style.FILL);
        fx.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));fx.setTextAlign(Paint.Align.CENTER);fx.setTextSize(h*.045f);fx.setColor(0xfff7f8fb);
        Paint.FontMetrics fm=fx.getFontMetrics();float y=rect.centerY()-(fm.ascent+fm.descent)/2f;c.drawText(label,rect.centerX(),y,fx);
    }

    private int starterStage(int lv){if(lv>=80)return 5;if(lv>=50)return 4;if(lv>=25)return 3;if(lv>=12)return 2;if(lv>=5)return 1;return 0;}

    private int archetype(StreakGame.CharacterDef d){
        String n=d.name.toLowerCase(Locale.ROOT);
        if(has(n,"dragon","drake","wyvern","kirin","naga","serpent","cobra","hydra","gecko"))return 4;
        if(has(n,"wolf","fox","lynx","panther","tiger","pup","jackal","tanuki","cat","ferret","badger","otter","mole","cub","boar","ram","alpaca","lemur","hare"))return 2;
        if(has(n,"owl","raven","finch","crane","roc","phoenix","garuda","griffin","bat","moth"))return 3;
        if(has(n,"koi","seal","crab","toad","manta","turtle","leviathan","puddle","ripple","dewdrop"))return 5;
        if(has(n,"golem","titan","behemoth","rex","sphinx"))return 6;
        if(has(n,"beetle","mantis"))return 7;
        if(has(n,"samurai","oni","emperor","sovereign"))return 8;
        if(has(n,"blob","puff","cloud","bubble","mochi","sprout","leaf","acorn","candle","berry","snow","breeze","chirp","cocoa","button","lemon"))return 1;
        return 0;
    }
    private boolean has(String n,String... terms){for(String s:terms)if(n.contains(s))return true;return false;}

    private void drawArchetype(Canvas c,StreakGame.CharacterDef d,float wing){
        switch(archetype(d)){
            case 1: drawSpirit(c,d);break;
            case 2: drawBeast(c,d);break;
            case 3: drawBird(c,d,wing);break;
            case 4: drawDragon(c,d,Math.min(4,1+d.tier()),wing);break;
            case 5: drawAquatic(c,d);break;
            case 6: drawGolem(c,d);break;
            case 7: drawInsect(c,d,wing);break;
            case 8: drawGuardian(c,d);break;
            default: drawMascot(c,d);break;
        }
    }

    private void setBodyShader(int top,int bottom,float y1,float y2){
        fill.setShader(new LinearGradient(0,y1,0,y2,top,bottom,Shader.TileMode.CLAMP));
    }
    private void clearShader(){fill.setShader(null);}
    private void oval(Canvas c,float l,float t,float r,float b,int color){fill.setShader(null);fill.setColor(color);rect.set(l,t,r,b);c.drawOval(rect,fill);c.drawOval(rect,line);}
    private void circle(Canvas c,float x,float y,float rad,int color){fill.setShader(null);fill.setColor(color);c.drawCircle(x,y,rad,fill);c.drawCircle(x,y,rad,line);}
    private void roundRect(Canvas c,float l,float t,float r,float b,float rr,int color){fill.setShader(null);fill.setColor(color);rect.set(l,t,r,b);c.drawRoundRect(rect,rr,rr,fill);c.drawRoundRect(rect,rr,rr,line);}
    private void pathFill(Canvas c,int color){fill.setShader(null);fill.setColor(color);c.drawPath(path,fill);c.drawPath(path,line);}
    private void pathGradient(Canvas c,int top,int bottom,float y1,float y2){setBodyShader(top,bottom,y1,y2);c.drawPath(path,fill);clearShader();c.drawPath(path,line);}
    private void ovalGradient(Canvas c,float l,float t,float r,float b,int top,int bottom){rect.set(l,t,r,b);setBodyShader(top,bottom,t,b);c.drawOval(rect,fill);clearShader();c.drawOval(rect,line);}

    private void drawEyes(Canvas c,float x,float y,float gap,float size,boolean fierce){
        int eye=0xfff7fbff,iris=light(base());
        if(fierce){
            path.reset();path.moveTo(x-gap-size*1.2f,y-size*.6f);path.lineTo(x-gap+size*1.2f,y);path.lineTo(x-gap-size*.8f,y+size*.8f);path.close();pathFill(c,eye);
            path.reset();path.moveTo(x+gap+size*1.2f,y-size*.6f);path.lineTo(x+gap-size*1.2f,y);path.lineTo(x+gap+size*.8f,y+size*.8f);path.close();pathFill(c,eye);
        }else{
            circle(c,x-gap,y,size,eye);circle(c,x+gap,y,size,eye);
        }
        fill.setShader(null);fill.setColor(dark(iris));c.drawCircle(x-gap,y,size*.42f,fill);c.drawCircle(x+gap,y,size*.42f,fill);
        fill.setColor(Color.WHITE);c.drawCircle(x-gap-size*.16f,y-size*.18f,size*.15f,fill);c.drawCircle(x+gap-size*.16f,y-size*.18f,size*.15f,fill);
    }

    private void drawMascot(Canvas c,StreakGame.CharacterDef d){
        int a=base(),b=dark(a),hi=light(a);
        path.reset();path.moveTo(18,12);path.cubicTo(35,8,38,-8,26,-15);path.cubicTo(43,-13,46,16,24,24);path.close();pathFill(c,b);
        path.reset();path.moveTo(-15,-20);path.lineTo(-24,-39);path.lineTo(-5,-28);path.close();pathFill(c,b);
        path.reset();path.moveTo(15,-20);path.lineTo(24,-39);path.lineTo(5,-28);path.close();pathFill(c,b);
        ovalGradient(c,-23,-23,23,27,hi,b);
        circle(c,0,-22,22,a);
        oval(c,-11,-6,11,6,blend(a,Color.WHITE,.52f));
        drawEyes(c,0,-23,7,3.5f,d.tier()>=3);
        fill.setColor(0xff231d29);c.drawCircle(0,-12,1.8f,fill);
        oval(c,-19,20,-3,31,b);oval(c,3,20,19,31,b);
        drawMarkings(c,d);
    }

    private void drawSpirit(Canvas c,StreakGame.CharacterDef d){
        int a=base(),hi=light(a),lo=dark(a);
        path.reset();path.moveTo(-23,12);path.cubicTo(-30,-3,-25,-27,-10,-34);path.cubicTo(-7,-46,-1,-52,2,-39);path.cubicTo(15,-34,27,-19,25,2);path.cubicTo(24,18,15,29,5,31);path.cubicTo(2,38,-5,41,-4,29);path.cubicTo(-13,28,-20,22,-23,12);path.close();
        fill.setShadowLayer(8,0,3,alpha(a,100));pathGradient(c,hi,lo,-45,34);fill.clearShadowLayer();
        drawEyes(c,0,-12,8,3.6f,false);
        fx.setColor(alpha(Color.WHITE,120));c.drawOval(new RectF(-13,-29,-4,-18),fx);
        if(d.shape%2==0){path.reset();path.moveTo(-18,-30);path.lineTo(-27,-42);path.lineTo(-7,-34);path.close();pathFill(c,lo);}
        if(d.tier()>=2){fx.setColor(alpha(light(a),150));for(int i=0;i<3+d.tier();i++){float ang=(float)(i*6.283/(3+d.tier()));c.drawCircle((float)Math.cos(ang)*32,(float)Math.sin(ang)*25,1.6f,fx);}}
    }

    private void drawBeast(Canvas c,StreakGame.CharacterDef d){
        int a=base(),hi=light(a),lo=dark(a),cream=blend(a,Color.WHITE,.68f);
        path.reset();path.moveTo(18,11);path.cubicTo(38,2,42,-14,27,-18);path.cubicTo(49,-20,52,9,31,23);path.cubicTo(24,28,18,25,14,21);path.close();pathGradient(c,hi,lo,-20,28);
        ovalGradient(c,-28,-6,23,25,hi,lo);
        roundRect(c,-20,14,-8,34,5,lo);roundRect(c,8,14,20,34,5,lo);
        path.reset();path.moveTo(-18,-9);path.lineTo(-24,2);path.lineTo(-15,1);path.lineTo(-18,11);path.lineTo(-5,4);path.lineTo(4,11);path.lineTo(4,-9);path.close();pathFill(c,cream);
        circle(c,-4,-25,21,a);
        path.reset();path.moveTo(-18,-38);path.lineTo(-24,-55);path.lineTo(-7,-43);path.close();pathFill(c,lo);
        path.reset();path.moveTo(10,-39);path.lineTo(21,-53);path.lineTo(17,-35);path.close();pathFill(c,lo);
        oval(c,-15,-24,10,-7,cream);drawEyes(c,-4,-29,7,3.2f,d.tier()>=3);
        fill.setColor(0xff1a1b20);c.drawCircle(-3,-17,2.2f,fill);
        if(d.tier()>=2){path.reset();path.moveTo(-4,-42);path.lineTo(1,-34);path.lineTo(-4,-28);path.lineTo(-9,-34);path.close();pathFill(c,rarityColor(d));}
        drawMarkings(c,d);
    }

    private void drawBird(Canvas c,StreakGame.CharacterDef d,float wing){
        int a=base(),hi=light(a),lo=dark(a),rc=rarityColor(d);
        for(int i=-1;i<=1;i++){path.reset();path.moveTo(i*5,18);path.lineTo(i*9+(i==0?0:i*6),43);path.lineTo(i*2,25);path.close();pathFill(c,blend(lo,rc,.25f));}
        ovalGradient(c,-17,-18,17,28,hi,lo);
        c.save();c.rotate(wing*.25f,-14,-6);path.reset();path.moveTo(-12,-8);path.cubicTo(-30,-17,-42,-10,-43,4);path.lineTo(-28,0);path.lineTo(-38,12);path.lineTo(-23,8);path.lineTo(-31,20);path.cubicTo(-18,17,-12,8,-8,1);path.close();pathGradient(c,hi,lo,-20,22);c.restore();
        c.save();c.rotate(-wing*.25f,14,-6);path.reset();path.moveTo(12,-8);path.cubicTo(30,-17,42,-10,43,4);path.lineTo(28,0);path.lineTo(38,12);path.lineTo(23,8);path.lineTo(31,20);path.cubicTo(18,17,12,8,8,1);path.close();pathGradient(c,hi,lo,-20,22);c.restore();
        circle(c,0,-30,17,a);drawEyes(c,0,-32,6,2.7f,d.tier()>=3);
        path.reset();path.moveTo(-3,-23);path.lineTo(8,-20);path.lineTo(-2,-17);path.close();pathFill(c,0xffffc85a);
        path.reset();path.moveTo(-7,-45);path.cubicTo(-3,-55,1,-55,0,-43);path.cubicTo(6,-55,11,-51,7,-40);path.close();pathFill(c,rc);
        drawMarkings(c,d);
    }

    private void drawDragon(Canvas c,StreakGame.CharacterDef d,int stage,float wing){
        int a=base(),hi=light(a),lo=dark(a),rc=d.starter()?0xffffd35c:rarityColor(d);
        boolean majestic=stage>=4;
        path.reset();path.moveTo(18,12);path.cubicTo(38,14,47,4,40,-7);path.cubicTo(54,-1,50,23,26,29);path.cubicTo(20,30,15,27,13,22);path.close();pathGradient(c,hi,lo,-10,30);
        if(stage>=3){
            float flap=wing*.15f;
            c.save();c.rotate(flap,-15,-5);path.reset();path.moveTo(-12,-10);path.cubicTo(-32,-31,-49,-28,-51,-8);path.lineTo(-36,-14);path.lineTo(-42,4);path.lineTo(-27,-4);path.lineTo(-26,13);path.cubicTo(-17,8,-12,2,-8,-3);path.close();pathGradient(c,blend(hi,rc,.18f),lo,-31,15);c.restore();
            c.save();c.rotate(-flap,15,-5);path.reset();path.moveTo(12,-10);path.cubicTo(32,-31,49,-28,51,-8);path.lineTo(36,-14);path.lineTo(42,4);path.lineTo(27,-4);path.lineTo(26,13);path.cubicTo(17,8,12,2,8,-3);path.close();pathGradient(c,blend(hi,rc,.18f),lo,-31,15);c.restore();
        }
        ovalGradient(c,-22,-8,22,29,hi,lo);
        path.reset();path.moveTo(-9,-18);path.cubicTo(-13,-6,-12,9,-7,20);path.lineTo(7,20);path.cubicTo(12,7,12,-7,8,-21);path.close();pathFill(c,blend(a,Color.WHITE,.60f));
        ovalGradient(c,-20,-41,20,-10,hi,lo);
        path.reset();path.moveTo(-15,-23);path.cubicTo(-20,-14,-16,-7,-3,-8);path.lineTo(15,-10);path.cubicTo(23,-13,22,-22,14,-26);path.close();pathFill(c,blend(a,Color.WHITE,.32f));
        path.reset();path.moveTo(-14,-38);path.lineTo(-26,-57-(majestic?8:0));path.lineTo(-7,-43);path.close();pathFill(c,blend(rc,Color.WHITE,.18f));
        path.reset();path.moveTo(14,-38);path.lineTo(26,-57-(majestic?8:0));path.lineTo(7,-43);path.close();pathFill(c,blend(rc,Color.WHITE,.18f));
        if(majestic){
            path.reset();path.moveTo(-5,-44);path.lineTo(0,-63);path.lineTo(6,-44);path.close();pathFill(c,rc);
            for(int i=-1;i<=1;i+=2){path.reset();path.moveTo(i*15,-15);path.lineTo(i*25,-7);path.lineTo(i*11,-6);path.close();pathFill(c,lo);}
        }
        drawEyes(c,0,-31,7.2f,3.4f,true);
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.15f);fx.setColor(alpha(light(a),120));
        for(int y=0;y<21;y+=7)for(int x=-12+(y/7%2)*4;x<=12;x+=8)c.drawArc(new RectF(x-4,y-4,x+4,y+3),0,180,false,fx);
        fx.setStyle(Paint.Style.FILL);
        if(d.tier()>=3||d.starter()){circle(c,0,6,4.2f,rc);fx.setColor(alpha(Color.WHITE,180));c.drawCircle(-1.3f,4.7f,1.1f,fx);}
    }

    private void drawStarter(Canvas c,int stage,float wing){
        if(stage==0){
            int hi=0xffffcf55,lo=0xffb62b24;
            path.reset();path.moveTo(0,-50);path.cubicTo(15,-33,26,-25,22,-7);path.cubicTo(35,10,22,33,0,35);path.cubicTo(-22,33,-35,10,-22,-7);path.cubicTo(-26,-25,-15,-33,0,-50);path.close();pathGradient(c,hi,lo,-50,35);
            path.reset();path.moveTo(0,-32);path.cubicTo(9,-20,13,-11,10,1);path.cubicTo(8,12,-8,12,-10,1);path.cubicTo(-13,-11,-9,-20,0,-32);path.close();pathFill(c,0xffffe878);
            drawEyes(c,0,-5,7,3.4f,false);
        }else if(stage==1){
            drawBeast(c,def);
            path.reset();path.moveTo(23,12);path.cubicTo(41,3,38,-13,29,-17);path.cubicTo(47,-12,49,10,31,24);path.close();pathFill(c,0xffffd85a);
        }else if(stage==2){
            drawDragon(c,def,2,wing);
            fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(2.4f);fx.setColor(0xffffed66);path.reset();path.moveTo(31,14);path.lineTo(43,8);path.lineTo(37,1);path.lineTo(49,-4);c.drawPath(path,fx);fx.setStyle(Paint.Style.FILL);
        }else drawDragon(c,def,stage,wing);
    }

    private void drawAquatic(Canvas c,StreakGame.CharacterDef d){
        int a=base(),hi=light(a),lo=dark(a),cream=blend(a,Color.WHITE,.65f);
        path.reset();path.moveTo(22,-2);path.cubicTo(38,-17,46,-17,42,1);path.cubicTo(46,17,36,20,22,8);path.close();pathGradient(c,hi,lo,-18,20);
        path.reset();path.moveTo(-5,14);path.lineTo(-19,29);path.lineTo(0,22);path.close();pathFill(c,lo);
        ovalGradient(c,-29,-17,27,18,hi,lo);
        oval(c,-17,3,15,15,cream);
        drawEyes(c,-12,-7,7,3.0f,d.tier()>=3);
        if(d.name.toLowerCase(Locale.ROOT).contains("crab")){
            path.reset();path.moveTo(-24,-3);path.lineTo(-39,-14);path.lineTo(-34,0);path.lineTo(-42,7);path.lineTo(-24,7);path.close();pathFill(c,lo);
            path.reset();path.moveTo(24,-3);path.lineTo(39,-14);path.lineTo(34,0);path.lineTo(42,7);path.lineTo(24,7);path.close();pathFill(c,lo);
        }
        if(d.tier()>=2){fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.3f);fx.setColor(alpha(Color.WHITE,120));for(int i=0;i<3;i++)c.drawArc(new RectF(-5+i*9,-12,-1+i*9,-2),100,150,false,fx);fx.setStyle(Paint.Style.FILL);}
    }

    private void drawGolem(Canvas c,StreakGame.CharacterDef d){
        int a=base(),stone=blend(a,0xff6c7480,.52f),lo=dark(stone),rc=rarityColor(d);
        roundRect(c,-38,-8,-19,21,7,lo);roundRect(c,19,-8,38,21,7,lo);
        roundRect(c,-27,-24,27,28,10,stone);
        roundRect(c,-18,19,-4,38,5,lo);roundRect(c,4,19,18,38,5,lo);
        roundRect(c,-19,-46,19,-20,8,stone);
        drawEyes(c,0,-34,7,2.8f,true);
        fill.setShadowLayer(10,0,0,alpha(rc,180));circle(c,0,2,6.5f,rc);fill.clearShadowLayer();
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.5f);fx.setColor(alpha(light(stone),130));
        path.reset();path.moveTo(-14,-10);path.lineTo(-5,-2);path.lineTo(-10,6);path.lineTo(-2,13);c.drawPath(path,fx);path.reset();path.moveTo(14,-8);path.lineTo(7,-1);path.lineTo(12,7);c.drawPath(path,fx);fx.setStyle(Paint.Style.FILL);
        if(d.tier()>=3){path.reset();path.moveTo(-12,-45);path.lineTo(-4,-58);path.lineTo(0,-45);path.lineTo(7,-59);path.lineTo(13,-43);path.close();pathFill(c,rc);}
    }

    private void drawInsect(Canvas c,StreakGame.CharacterDef d,float wing){
        int a=base(),hi=light(a),lo=dark(a),rc=rarityColor(d);
        fx.setColor(alpha(hi,120));c.save();c.rotate(wing*.35f,-8,-8);c.drawOval(new RectF(-38,-24,-6,5),fx);c.restore();c.save();c.rotate(-wing*.35f,8,-8);c.drawOval(new RectF(6,-24,38,5),fx);c.restore();
        ovalGradient(c,-16,-13,16,25,hi,lo);circle(c,0,-28,14,a);
        for(int y=0;y<20;y+=7){fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.5f);fx.setColor(alpha(dark(a),120));c.drawArc(new RectF(-12,y-4,12,y+5),0,180,false,fx);}fx.setStyle(Paint.Style.FILL);
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.5f);fx.setColor(lo);c.drawLine(-5,-38,-14,-49,fx);c.drawLine(5,-38,14,-49,fx);fx.setStyle(Paint.Style.FILL);c.drawCircle(-14,-49,2,fx);c.drawCircle(14,-49,2,fx);
        drawEyes(c,0,-29,5,2.4f,false);
        if(d.tier()>=2){path.reset();path.moveTo(-4,-13);path.lineTo(0,-21);path.lineTo(4,-13);path.lineTo(0,-5);path.close();pathFill(c,rc);}
    }

    private void drawGuardian(Canvas c,StreakGame.CharacterDef d){
        int a=base(),lo=dark(a),hi=light(a),rc=rarityColor(d);
        path.reset();path.moveTo(-17,-13);path.lineTo(-29,30);path.lineTo(0,22);path.lineTo(29,30);path.lineTo(17,-13);path.close();pathGradient(c,blend(lo,rc,.25f),dark(lo),-15,32);
        path.reset();path.moveTo(-18,-14);path.lineTo(-27,8);path.lineTo(-19,28);path.lineTo(19,28);path.lineTo(27,8);path.lineTo(18,-14);path.close();pathGradient(c,hi,lo,-15,28);
        circle(c,0,-30,17,a);
        path.reset();path.moveTo(-13,-40);path.lineTo(-25,-55);path.lineTo(-7,-44);path.close();pathFill(c,rc);
        path.reset();path.moveTo(13,-40);path.lineTo(25,-55);path.lineTo(7,-44);path.close();pathFill(c,rc);
        drawEyes(c,0,-31,6,2.8f,true);
        path.reset();path.moveTo(-12,-7);path.lineTo(0,-15);path.lineTo(12,-7);path.lineTo(8,12);path.lineTo(0,18);path.lineTo(-8,12);path.close();pathFill(c,blend(a,Color.WHITE,.32f));
        circle(c,0,0,3.8f,rc);
    }

    private void drawMarkings(Canvas c,StreakGame.CharacterDef d){
        int rc=rarityColor(d);
        if(d.tier()>=1){fx.setColor(alpha(rc,150));path.reset();path.moveTo(-6,-39);path.lineTo(0,-34);path.lineTo(6,-39);path.lineTo(0,-30);path.close();c.drawPath(path,fx);}
        if(d.tier()>=3){fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.2f);fx.setColor(alpha(Color.WHITE,110));c.drawArc(new RectF(-18,-5,18,20),15,150,false,fx);fx.setStyle(Paint.Style.FILL);}
    }

    private void drawDecorBack(Canvas c,StreakGame.CharacterDef d,float wing){
        if(decors==null||decors.isEmpty())return;
        for(String id:decors){
            if(id.contains("frame")){
                int col=id.contains("galaxy")?0xffa979ff:id.contains("aurora")?0xff58e6ff:id.contains("legend")?0xffffd05a:0xff4d78bb;
                fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(3.2f);fx.setColor(alpha(col,150));rect.set(-46,-61,46,43);c.drawRoundRect(rect,12,12,fx);fx.setStrokeWidth(1.1f);fx.setColor(alpha(Color.WHITE,80));rect.inset(4,4);c.drawRoundRect(rect,10,10,fx);fx.setStyle(Paint.Style.FILL);
            }
            if(id.contains("aura")){
                int col=id.contains("phoenix")?0xffff693e:id.contains("nebula")?0xffa76cff:id.contains("rose")?0xffff72ba:0xff67dbff;
                fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(2.2f);fx.setColor(alpha(col,115));c.drawCircle(0,-5,42,fx);fx.setStrokeWidth(1.2f);c.drawCircle(0,-5,48,fx);fx.setStyle(Paint.Style.FILL);
            }
            if(id.contains("cape")){
                int col=id.contains("meteor")?0xff6f3fb0:0xff315f49;path.reset();path.moveTo(-15,-9);path.lineTo(-31,36);path.lineTo(0,27);path.lineTo(31,36);path.lineTo(15,-9);path.close();pathFill(c,col);
            }
            if(id.contains("wings")){
                int col=id.contains("solar")?0xffffd25c:id.contains("lunar")?0xff8fb8ff:id.contains("frost")?0xffa8ebff:id.contains("world")?0xffa4ffbf:0xffff834c;
                c.save();c.rotate(wing*.18f,-13,-4);path.reset();path.moveTo(-11,-7);path.cubicTo(-30,-27,-49,-22,-50,-4);path.lineTo(-35,-10);path.lineTo(-43,7);path.lineTo(-27,0);path.lineTo(-27,17);path.cubicTo(-18,10,-13,4,-9,0);path.close();pathFill(c,alphaOpaque(col));c.restore();
                c.save();c.rotate(-wing*.18f,13,-4);path.reset();path.moveTo(11,-7);path.cubicTo(30,-27,49,-22,50,-4);path.lineTo(35,-10);path.lineTo(43,7);path.lineTo(27,0);path.lineTo(27,17);path.cubicTo(18,10,13,4,9,0);path.close();pathFill(c,alphaOpaque(col));c.restore();
            }
        }
    }
    private int alphaOpaque(int c){return Color.rgb(Color.red(c),Color.green(c),Color.blue(c));}

    private void drawDecorFront(Canvas c,StreakGame.CharacterDef d){
        if(decors==null||decors.isEmpty())return;
        for(String id:decors){
            if(id.contains("crown"))drawCrown(c,id);
            else if(id.contains("halo"))drawHalo(c,id);
            else if(id.contains("scarf"))drawScarf(c,id);
            else if(id.contains("armor"))drawArmor(c,id);
            else if(id.contains("horns"))drawExtraHorns(c,id);
            else if(id.contains("mask"))drawMask(c);
            else if(id.contains("pin")||id.contains("badge")||id.contains("charm")||id.contains("bell"))drawCharm(c,id);
        }
    }

    private void drawCrown(Canvas c,String id){
        int col=id.contains("eternal")?0xffffe78e:id.contains("eclipse")?0xffba8cff:id.contains("royal")?0xffffd04b:id.contains("silver")?0xffdce7f2:0xffffcb45;
        path.reset();path.moveTo(-16,-47);path.lineTo(-12,-61);path.lineTo(-4,-52);path.lineTo(0,-65);path.lineTo(5,-52);path.lineTo(13,-61);path.lineTo(16,-47);path.close();pathFill(c,col);
        fx.setColor(Color.WHITE);c.drawCircle(0,-54,2,fx);
    }
    private void drawHalo(Canvas c,String id){
        int col=id.contains("chrono")?0xffffd76b:id.contains("dragon")?0xffff9f4b:0xff7bdcff;
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(2.6f);fx.setColor(alpha(col,210));rect.set(-22,-60,22,-51);c.drawOval(rect,fx);fx.setStrokeWidth(6f);fx.setColor(alpha(col,38));c.drawOval(rect,fx);fx.setStyle(Paint.Style.FILL);
    }
    private void drawScarf(Canvas c,String id){
        int col=id.contains("king")?0xff8b1f3c:id.contains("rune")?0xff5541a8:0xffe24b37;
        path.reset();path.moveTo(-16,-9);path.cubicTo(-8,-2,8,-2,16,-9);path.lineTo(14,-3);path.cubicTo(5,4,-8,4,-14,-3);path.close();pathFill(c,col);
        path.reset();path.moveTo(9,0);path.lineTo(24,16);path.lineTo(14,20);path.lineTo(4,3);path.close();pathFill(c,dark(col));
    }
    private void drawArmor(Canvas c,String id){
        int col=id.contains("titan")?0xff7b8798:0xff6f7990;
        path.reset();path.moveTo(-19,-4);path.lineTo(-23,14);path.lineTo(-12,25);path.lineTo(12,25);path.lineTo(23,14);path.lineTo(19,-4);path.lineTo(8,4);path.lineTo(0,-2);path.lineTo(-8,4);path.close();
        fill.setColor(col);fill.setShader(new LinearGradient(0,-5,0,25,light(col),dark(col),Shader.TileMode.CLAMP));c.drawPath(path,fill);fill.setShader(null);c.drawPath(path,line);
        circle(c,0,10,3.5f,rarityColor(def));
    }
    private void drawExtraHorns(Canvas c,String id){
        int col=id.contains("hydra")?0xffad78ff:0xffffc45d;
        path.reset();path.moveTo(-13,-39);path.cubicTo(-29,-48,-30,-61,-22,-67);path.cubicTo(-23,-55,-14,-54,-8,-45);path.close();pathFill(c,col);
        path.reset();path.moveTo(13,-39);path.cubicTo(29,-48,30,-61,22,-67);path.cubicTo(23,-55,14,-54,8,-45);path.close();pathFill(c,col);
    }
    private void drawMask(Canvas c){
        int col=0xfff3f1ea;path.reset();path.moveTo(-15,-36);path.lineTo(-10,-19);path.lineTo(0,-14);path.lineTo(10,-19);path.lineTo(15,-36);path.lineTo(0,-30);path.close();pathFill(c,col);
        fill.setColor(0xffdb334a);c.drawCircle(-7,-28,2.2f,fill);c.drawCircle(7,-28,2.2f,fill);
    }
    private void drawCharm(Canvas c,String id){
        int col=id.contains("crystal")?0xff8eeaff:id.contains("mythic")?0xffffd65c:id.contains("moon")?0xffb8c7ff:0xffffd35c;
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(1.4f);fx.setColor(dark(col));c.drawLine(13,-2,17,9,fx);fx.setStyle(Paint.Style.FILL);circle(c,18,12,3.5f,col);
    }
}
