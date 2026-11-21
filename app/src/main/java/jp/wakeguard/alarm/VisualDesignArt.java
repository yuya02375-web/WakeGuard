package jp.wakeguard.alarm;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Small signature illustrations that make the visual presets recognizable at a glance. */
final class VisualDesignArt {
    private VisualDesignArt() {}

    static boolean draw(Canvas canvas, String id, RectF bounds, Paint source) {
        if (canvas == null || id == null || bounds == null || bounds.isEmpty() || source == null) return false;
        if (VisualDesigns.landscapeSceneId(id) != null) return false;
        int sourceAlpha = (source.getColor() >>> 24) & 0xFF;
        int ink = VisualDesigns.withAlpha(source.getColor(), sourceAlpha * 0x62 / 0xFF);
        int mid = VisualDesigns.withAlpha(source.getColor(), sourceAlpha * 0x3C / 0xFF);
        int wash = VisualDesigns.withAlpha(source.getColor(), sourceAlpha * 0x1D / 0xFF);
        float s = Math.max(.55f, Math.min(bounds.width(), bounds.height()) / 140f);
        float l = bounds.left, t = bounds.top, w = bounds.width(), h = bounds.height();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        int save = canvas.save();
        canvas.clipRect(bounds);

        switch (id) {
            case "paper-craft": drawFold(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "origami-atlas": drawFoldLines(canvas, p, l, t, w, h, s, ink, mid); break;
            case "field-notes": drawCompass(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "ink-etching": drawEtching(canvas, p, l, t, w, h, s, ink, mid); break;
            case "linen-stitch": drawWeave(canvas, p, l, t, w, h, s, ink, mid); break;
            case "herbarium": drawHerbarium(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "petal-orbit": drawPetals(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "moonlit-stars": drawStars(canvas, p, l, t, w, h, s, ink, mid); break;
            case "aurora-contour": drawAurora(canvas, p, l, t, w, h, s, ink, mid); break;
            case "ocean-current": drawOcean(canvas, p, l, t, w, h, s, ink, mid); break;
            case "topographic-map": drawTopography(canvas, p, l, t, w, h, s, ink, mid); break;
            case "tidepool": drawTidepool(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "halftone-pop": drawHalftone(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "scanline-vhs": drawScanline(canvas, p, l, t, w, h, s, ink, mid); break;
            case "vapor-grid": drawPerspectiveGrid(canvas, p, l, t, w, h, s, ink, mid); break;
            case "cassette-deck": drawCassette(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "blueprint": drawBlueprint(canvas, p, l, t, w, h, s, ink, mid); break;
            case "circuit-trace": drawCircuit(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "space-console": drawConsole(canvas, p, l, t, w, h, s, ink, mid); break;
            case "terminal-green": drawTerminal(canvas, p, l, t, w, h, s, ink, mid); break;
            case "pixel-arcade": drawPixel(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "editorial-ink": drawEditorial(canvas, p, l, t, w, h, s, ink, mid); break;
            case "magazine-cover": drawCover(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "swiss-poster": drawSwiss(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "library-classic": drawBookplate(canvas, p, l, t, w, h, s, ink, mid); break;
            case "newspaper-archive": drawNewsprint(canvas, p, l, t, w, h, s, ink, mid); break;
            case "concrete-brutal": drawBrutal(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "bubblegum-soft": drawBubbles(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "geometric-tiles": drawTiles(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "memphis-confetti": drawMemphis(canvas, p, l, t, w, h, s, ink, mid); break;
            case "terrazzo": drawTerrazzo(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "checker-club": drawChecker(canvas, p, l, t, w, h, s, ink, mid); break;
            case "transit-barcode": drawTransit(canvas, p, l, t, w, h, s, ink, mid); break;
            case "tapestry-weave": drawWeave(canvas, p, l, t, w, h, s, ink, mid); break;
            case "ceramic-glaze": drawGlaze(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            case "solar-orbit": drawOrbit(canvas, p, l, t, w, h, s, ink, mid, wash); break;
            default:
                canvas.restoreToCount(save);
                return false;
        }
        canvas.restoreToCount(save);
        return true;
    }

    private static void line(Canvas c, Paint p, float x1, float y1, float x2, float y2, int color, float width) {
        p.setStyle(Paint.Style.STROKE); p.setPathEffect(null); p.setStrokeWidth(width); p.setColor(color);
        c.drawLine(x1, y1, x2, y2, p);
    }
    private static void fill(Canvas c, Paint p, RectF r, int color) {
        p.setStyle(Paint.Style.FILL); p.setPathEffect(null); p.setColor(color); c.drawRoundRect(r, r.width()*.18f, r.width()*.18f, p);
    }
    private static void ring(Canvas c, Paint p, RectF r, int color, float width) {
        p.setStyle(Paint.Style.STROKE); p.setPathEffect(null); p.setStrokeWidth(width); p.setColor(color); c.drawOval(r, p);
    }
    private static void path(Canvas c, Paint p, Path path, int color, float width) {
        p.setStyle(Paint.Style.STROKE); p.setPathEffect(null); p.setStrokeWidth(width); p.setColor(color); c.drawPath(path, p);
    }
    private static Path wave(float x, float y, float width, float amp, int bends) {
        Path p = new Path(); p.moveTo(x, y);
        float dx = width / bends;
        for (int i = 0; i < bends; i++) {
            float x0 = x + i * dx, x1 = x0 + dx;
            float sign = i % 2 == 0 ? -1f : 1f;
            p.cubicTo(x0 + dx*.28f, y + sign*amp, x0 + dx*.72f, y + sign*amp, x1, y);
        }
        return p;
    }
    private static void star(Canvas c, Paint p, float x, float y, float r, int color) {
        Path q = new Path();
        for (int i = 0; i < 8; i++) {
            double a = -Math.PI/2 + Math.PI*i/4;
            float rr = i%2 == 0 ? r : r*.30f;
            float px = x + (float)Math.cos(a)*rr, py = y + (float)Math.sin(a)*rr;
            if (i == 0) q.moveTo(px, py); else q.lineTo(px, py);
        }
        q.close(); p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawPath(q, p);
    }
    private static void polygon(Canvas c, Paint p, float[] xy, int color) {
        Path q = new Path(); q.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) q.lineTo(xy[i], xy[i+1]);
        q.close(); p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawPath(q, p);
    }

    private static void drawFold(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        polygon(c,p,new float[]{x+w*.62f,y,x+w,y,x+w,y+h*.40f},wash);
        polygon(c,p,new float[]{x+w*.62f,y,x+w*.62f,y+h*.40f,x+w,y+h*.40f},b);
        line(c,p,x+w*.62f,y,x+w*.62f,y+h*.40f,a,1.2f*s);
        line(c,p,x+w*.62f,y+h*.40f,x+w,y,a,1.2f*s);
        line(c,p,x+w*.70f,y+h*.10f,x+w*.91f,y+h*.31f,b,.8f*s);
    }
    private static void drawFoldLines(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        line(c,p,x+w*.52f,y+h*.05f,x+w*.52f,y+h*.92f,a,1.1f*s);
        line(c,p,x+w*.07f,y+h*.52f,x+w*.95f,y+h*.52f,a,1.1f*s);
        line(c,p,x+w*.18f,y+h*.10f,x+w*.82f,y+h*.89f,b,.9f*s);
        line(c,p,x+w*.79f,y+h*.10f,x+w*.19f,y+h*.89f,b,.9f*s);
        star(c,p,x+w*.52f,y+h*.52f,4*s,a);
    }
    private static void drawCompass(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        RectF r=new RectF(x+w*.68f,y+h*.10f,x+w*.92f,y+h*.44f); ring(c,p,r,a,1.2f*s);
        line(c,p,r.centerX(),r.top-3*s,r.centerX(),r.bottom+3*s,b,.8f*s);
        line(c,p,r.left-3*s,r.centerY(),r.right+3*s,r.centerY(),b,.8f*s);
        star(c,p,r.centerX(),r.centerY(),5*s,a);
        fill(c,p,new RectF(x+w*.73f,y+h*.62f,x+w*.87f,y+h*.76f),wash);
    }
    private static void drawEtching(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        RectF r=new RectF(x+w*.66f,y+h*.10f,x+w*.98f,y+h*.74f);
        for(int i=0;i<7;i++){RectF q=new RectF(r.left+i*2*s,r.top+i*2*s,r.right-i*2*s,r.bottom-i*2*s);if(q.width()>3*s&&q.height()>3*s)ring(c,p,q,i%3==0?b:a,.7f*s);}
        for(int i=0;i<7;i++)line(c,p,x+w*(.68f+i*.045f),y+h*.77f,x+w*.89f,y+h*(.55f+i*.035f),b,.65f*s);
    }
    private static void drawWeave(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float left=x+w*.64f, top=y+h*.16f, cell=7*s;
        for(int row=0;row<4;row++) for(int col=0;col<4;col++) {
            float xx=left+col*cell, yy=top+row*cell;
            int color=(row+col)%2==0?a:b;
            line(c,p,xx,yy,xx+cell*.62f,yy+cell*.62f,color,1.2f*s);
            line(c,p,xx+cell*.62f,yy,xx,yy+cell*.62f,(row+col)%2==0?b:a,.8f*s);
        }
    }
    private static void drawHerbarium(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float bx=x+w*.81f, by=y+h*.90f;
        Path stem=new Path(); stem.moveTo(bx,by);stem.cubicTo(bx-5*s,y+h*.65f,bx+4*s,y+h*.35f,bx-2*s,y+h*.08f);path(c,p,stem,a,1.2f*s);
        for(int i=0;i<4;i++) {
            float yy=y+h*(.28f+i*.14f), side=i%2==0?-1f:1f, tipX=bx+side*(11+i%2*3)*s, tipY=yy-3*s;
            Path leaf=new Path();leaf.moveTo(bx,yy);leaf.cubicTo(bx+side*4*s,yy-6*s,tipX-2*s,tipY-2*s,tipX,tipY);leaf.cubicTo(tipX,tipY+4*s,bx+side*4*s,yy+3*s,bx,yy);leaf.close();
            p.setStyle(Paint.Style.FILL);p.setColor(wash);c.drawPath(leaf,p);path(c,p,leaf,b,.75f*s);line(c,p,bx,yy,tipX,tipY,a,.6f*s);
        }
    }
    private static void drawPetals(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float cx=x+w*.80f, cy=y+h*.48f;
        for(int i=0;i<6;i++){double an=Math.PI*2*i/6;float px=cx+(float)Math.cos(an)*11*s,py=cy+(float)Math.sin(an)*11*s;RectF r=new RectF(px-3*s,py-5*s,px+3*s,py+5*s);c.save();c.rotate((float)(i*60),px,py);fill(c,p,r,i%2==0?wash:b);ring(c,p,r,a,.7f*s);c.restore();}
        p.setStyle(Paint.Style.FILL);p.setColor(a);c.drawCircle(cx,cy,2.1f*s,p);ring(c,p,new RectF(cx-19*s,cy-19*s,cx+19*s,cy+19*s),b,.6f*s);
    }
    private static void drawStars(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float[][] q={{.67f,.22f,4},{.84f,.34f,3},{.72f,.55f,5},{.94f,.67f,2.8f},{.82f,.84f,3.4f}};
        for(int i=0;i<q.length-1;i++)line(c,p,x+w*q[i][0],y+h*q[i][1],x+w*q[i+1][0],y+h*q[i+1][1],b,.65f*s);
        for(int i=0;i<q.length;i++)star(c,p,x+w*q[i][0],y+h*q[i][1],q[i][2]*s,i%2==0?a:b);
    }
    private static void drawAurora(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        for(int i=0;i<6;i++){Path q=new Path();float yy=y+h*(.22f+i*.105f);q.moveTo(x+w*.55f,yy);q.cubicTo(x+w*.68f,yy-20*s,x+w*.73f,yy+18*s,x+w*.99f,yy-7*s);path(c,p,q,i%2==0?a:b,(i%3==0?1.2f:.75f)*s);}
    }
    private static void drawOcean(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        for(int i=0;i<5;i++){float yy=y+h*(.28f+i*.12f);path(c,p,wave(x+w*.60f,yy,w*.38f,3*s,2),i%2==0?a:b,.9f*s);}
        p.setStyle(Paint.Style.FILL);p.setColor(b);for(int i=0;i<5;i++)c.drawCircle(x+w*(.67f+i*.055f),y+h*(.80f-(i%2)*.08f),.8f*s,p);
    }
    private static void drawTopography(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float cx=x+w*.79f,cy=y+h*.50f;
        for(int i=0;i<8;i++){float rx=(11+i*3)*s,ry=(8+i*2.4f)*s;RectF r=new RectF(cx-rx,cy-ry,cx+rx,cy+ry);ring(c,p,r,i%3==0?b:a,.75f*s);}
    }
    private static void drawTidepool(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float[][] d={{.69f,.28f,4},{.82f,.21f,2.5f},{.91f,.38f,3.5f},{.75f,.56f,2.7f},{.88f,.70f,4.2f},{.68f,.83f,2.4f}};
        for(int i=0;i<d.length;i++){float cx=x+w*d[i][0],cy=y+h*d[i][1],r=d[i][2]*s;RectF q=new RectF(cx-r*1.2f,cy-r,cx+r*1.2f,cy+r);fill(c,p,q,i%2==0?wash:b);ring(c,p,q,a,.65f*s);}
    }
    private static void drawHalftone(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        for(int row=0;row<7;row++)for(int col=0;col<8;col++){float weight=Math.max(0,1-Math.abs(col-5)*.18f-Math.abs(row-3)*.12f);float r=(.45f+weight*1.8f)*s;p.setStyle(Paint.Style.FILL);p.setColor((row+col)%3==0?b:a);c.drawCircle(x+w*.62f+col*3.2f*s,y+h*.20f+row*3.2f*s,r,p);}
        Path burst=new Path();burst.moveTo(x+w*.60f,y+h*.74f);burst.lineTo(x+w*.75f,y+h*.44f);burst.lineTo(x+w*.91f,y+h*.74f);burst.close();p.setStyle(Paint.Style.FILL);p.setColor(wash);c.drawPath(burst,p);path(c,p,burst,b,.8f*s);
    }
    private static void drawScanline(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        for(int i=0;i<12;i++){float yy=y+h*(.20f+i*.035f);line(c,p,x+w*.60f,yy,x+w*.98f,yy,i%4==0?b:a,.55f*s);}
        fill(c,p,new RectF(x+w*.68f,y+h*.72f,x+w*.84f,y+h*.77f),b);fill(c,p,new RectF(x+w*.87f,y+h*.70f,x+w*.96f,y+h*.75f),a);
    }
    private static void drawPerspectiveGrid(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float hx=x+w*.80f,hy=y+h*.46f;
        for(int i=-4;i<=4;i++){Path q=new Path();q.moveTo(hx,hy);q.lineTo(hx+i*24*s,y+h);path(c,p,q,i%2==0?a:b,.7f*s);}
        for(int i=1;i<7;i++){float k=i/7f;float yy=hy+(y+h-hy)*k*k;line(c,p,x+w*.60f,yy,x+w,yy,b,.7f*s);}
    }
    private static void drawCassette(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        RectF body=new RectF(x+w*.62f,y+h*.24f,x+w*.99f,y+h*.72f);fill(c,p,body,wash);ring(c,p,body,a,1*s);
        ring(c,p,new RectF(body.left+5*s,body.top+5*s,body.left+15*s,body.top+15*s),b,.8f*s);
        ring(c,p,new RectF(body.right-15*s,body.top+5*s,body.right-5*s,body.top+15*s),b,.8f*s);
        ring(c,p,new RectF(body.left+8*s,body.top+8*s,body.left+12*s,body.top+12*s),a,.6f*s);
        ring(c,p,new RectF(body.right-12*s,body.top+8*s,body.right-8*s,body.top+12*s),a,.6f*s);
        line(c,p,body.left+9*s,body.top+15*s,body.centerX(),body.bottom-6*s,a,.75f*s);line(c,p,body.right-9*s,body.top+15*s,body.centerX(),body.bottom-6*s,a,.75f*s);
        fill(c,p,new RectF(body.left+8*s,body.bottom-8*s,body.right-8*s,body.bottom-5*s),b);
    }
    private static void drawBlueprint(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float left=x+w*.64f,top=y+h*.12f,right=x+w*.98f,bottom=y+h*.84f;
        line(c,p,left,top,right,top,b,.8f*s);line(c,p,left,bottom,right,bottom,b,.8f*s);
        line(c,p,right,top,right,bottom,a,.8f*s);
        for(int i=0;i<6;i++){float xx=left+(right-left)*i/5f;line(c,p,xx,bottom,xx,bottom-4*s,a,.75f*s);}
        for(int i=0;i<5;i++){float yy=top+(bottom-top)*i/4f;line(c,p,left,yy,left+4*s,yy,a,.75f*s);}
        ring(c,p,new RectF(left+8*s,top+10*s,left+25*s,top+27*s),b,.8f*s);
    }
    private static void drawCircuit(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float[][] n={{.64f,.26f},{.75f,.26f},{.75f,.52f},{.89f,.52f},{.89f,.78f},{.98f,.78f}};
        for(int i=0;i<n.length-1;i++){float x1=x+w*n[i][0],y1=y+h*n[i][1],x2=x+w*n[i+1][0],y2=y+h*n[i+1][1];Path q=new Path();q.moveTo(x1,y1);if(Math.abs(y2-y1)>1)q.lineTo(x2,y1);q.lineTo(x2,y2);path(c,p,q,a,1*s);}
        for(int i=1;i<n.length;i++){float cx=x+w*n[i][0],cy=y+h*n[i][1];ring(c,p,new RectF(cx-2.2f*s,cy-2.2f*s,cx+2.2f*s,cy+2.2f*s),b,.8f*s);p.setStyle(Paint.Style.FILL);p.setColor(wash);c.drawCircle(cx,cy,1.1f*s,p);}
    }
    private static void drawConsole(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float cx=x+w*.80f,cy=y+h*.48f;
        for(int i=0;i<4;i++)ring(c,p,new RectF(cx-(8+i*6)*s,cy-(8+i*6)*s,cx+(8+i*6)*s,cy+(8+i*6)*s),i%2==0?a:b,.8f*s);
        for(int i=0;i<12;i++){double an=Math.PI*2*i/12;float r=28*s;line(c,p,cx+(float)Math.cos(an)*r,cy+(float)Math.sin(an)*r,cx+(float)Math.cos(an)*(r+3*s),cy+(float)Math.sin(an)*(r+3*s),a,.8f*s);}
    }
    private static void drawTerminal(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        for(int row=0;row<5;row++){float yy=y+h*(.26f+row*.105f),len=w*(.12f+((row*7)%5)*.035f);line(c,p,x+w*.65f,yy,x+w*.65f+len,yy,row==4?b:a,.9f*s);}
        fill(c,p,new RectF(x+w*.65f,y+h*.78f,x+w*.70f,y+h*.85f),b);
    }
    private static void drawPixel(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        int[][] map={{0,1,1,1,0,0},{1,2,2,2,1,0},{0,1,2,2,1,0},{0,0,1,1,0,0}};
        float cell=5*s,ox=x+w*.70f,oy=y+h*.26f;
        for(int r=0;r<map.length;r++)for(int col=0;col<map[r].length;col++)if(map[r][col]>0){int color=map[r][col]==2?b:a;fill(c,p,new RectF(ox+col*cell,oy+r*cell,ox+(col+1)*cell-1*s,oy+(r+1)*cell-1*s),color);}
        line(c,p,x+w*.66f,y+h*.64f,x+w*.97f,y+h*.64f,wash,1*s);
    }
    private static void drawEditorial(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float left=x+w*.64f,top=y+h*.17f;
        line(c,p,left,top,left,top+h*.65f,b,.8f*s);line(c,p,left+w*.15f,top,left+w*.15f,top+h*.65f,b,.8f*s);line(c,p,left+w*.31f,top,left+w*.31f,top+h*.65f,b,.8f*s);
        for(int row=0;row<6;row++)line(c,p,left,top+h*(.10f+row*.09f),left+w*(.22f+(row%2)*.06f),top+h*(.10f+row*.09f),row==0?a:b,.75f*s);
    }
    private static void drawCover(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        polygon(c,p,new float[]{x+w*.68f,y+h*.12f,x+w*.97f,y+h*.12f,x+w*.97f,y+h*.52f,x+w*.68f,y+h*.35f},wash);
        line(c,p,x+w*.67f,y+h*.61f,x+w*.97f,y+h*.61f,a,2*s);line(c,p,x+w*.67f,y+h*.70f,x+w*.92f,y+h*.70f,b,1.1f*s);
        for(int i=0;i<5;i++)line(c,p,x+w*(.70f+i*.045f),y+h*.82f,x+w*.72f+i*.045f,y+h*.77f,b,.65f*s);
    }
    private static void drawSwiss(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float cx=x+w*.79f,cy=y+h*.38f,r=13*s;p.setStyle(Paint.Style.FILL);p.setColor(wash);c.drawCircle(cx,cy,r,p);ring(c,p,new RectF(cx-r,cy-r,cx+r,cy+r),a,1*s);
        fill(c,p,new RectF(x+w*.66f,y+h*.62f,x+w*.97f,y+h*.68f),b);fill(c,p,new RectF(x+w*.66f,y+h*.73f,x+w*.84f,y+h*.78f),a);
    }
    private static void drawBookplate(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        RectF r=new RectF(x+w*.65f,y+h*.14f,x+w*.98f,y+h*.82f);ring(c,p,r,a,1*s);r.inset(3*s,3*s);ring(c,p,r,b,.7f*s);
        for(int i=0;i<4;i++){float xx=i%2==0?r.left:r.right,yy=i<2?r.top:r.bottom;star(c,p,xx,yy,2.5f*s,a);}
    }
    private static void drawNewsprint(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float left=x+w*.65f,top=y+h*.17f;
        fill(c,p,new RectF(left,top,left+w*.31f,top+7*s),a);
        for(int row=0;row<8;row++){float yy=top+12*s+row*3.2f*s;float split=left+w*.15f;line(c,p,left,yy,split-1*s,yy,b,.65f*s);line(c,p,split+2*s,yy,left+w*.31f,yy,b,.65f*s);}
    }
    private static void drawBrutal(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        fill(c,p,new RectF(x+w*.66f,y+h*.15f,x+w*.93f,y+h*.43f),wash);
        fill(c,p,new RectF(x+w*.78f,y+h*.48f,x+w*.98f,y+h*.81f),b);
        line(c,p,x+w*.64f,y+h*.45f,x+w*.77f,y+h*.32f,a,2*s);line(c,p,x+w*.65f,y+h*.84f,x+w*.97f,y+h*.84f,a,2*s);
    }
    private static void drawBubbles(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float[][] q={{.68f,.24f,7},{.85f,.19f,4},{.95f,.34f,8},{.75f,.52f,4},{.88f,.68f,7},{.69f,.82f,3}};
        for(int i=0;i<q.length;i++){float cx=x+w*q[i][0],cy=y+h*q[i][1],r=q[i][2]*s;p.setStyle(Paint.Style.FILL);p.setColor(i%2==0?wash:b);c.drawCircle(cx,cy,r,p);ring(c,p,new RectF(cx-r,cy-r,cx+r,cy+r),a,.8f*s);p.setStyle(Paint.Style.FILL);p.setColor(VisualDesigns.withAlpha(0xFFFFFFFF,((a>>>24)&0xFF)*0x42/0x62));c.drawCircle(cx-r*.32f,cy-r*.32f,Math.max(1,s),p);}
    }
    private static void drawTiles(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){float cx=x+w*(.68f+col*.10f),cy=y+h*(.27f+row*.23f),r=4.3f*s;int k=(row+col)%3;if(k==0)polygon(c,p,new float[]{cx,cy-r,cx+r,cy,cx,cy+r,cx-r,cy},wash);else if(k==1){p.setStyle(Paint.Style.FILL);p.setColor(b);c.drawCircle(cx,cy,r*.62f,p);}else fill(c,p,new RectF(cx-r,cy-r,cx+r,cy+r),a);}
    }
    private static void drawMemphis(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        star(c,p,x+w*.69f,y+h*.26f,5*s,a);star(c,p,x+w*.92f,y+h*.71f,4*s,b);
        path(c,p,wave(x+w*.64f,y+h*.54f,w*.30f,4*s,3),a,1.1f*s);
        p.setStyle(Paint.Style.FILL);p.setColor(b);c.drawCircle(x+w*.77f,y+h*.81f,2.5f*s,p);
        polygon(c,p,new float[]{x+w*.91f,y+h*.20f,x+w*.96f,y+h*.30f,x+w*.86f,y+h*.30f},b);
    }
    private static void drawTerrazzo(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float[][] q={{.66f,.25f,3,1},{.81f,.18f,2,0},{.94f,.27f,4,2},{.71f,.48f,2,2},{.86f,.51f,3,1},{.98f,.61f,2,0},{.68f,.77f,4,1},{.84f,.83f,2,0},{.94f,.84f,3,2}};
        for(int i=0;i<q.length;i++){
            float cx=x+w*q[i][0],cy=y+h*q[i][1],r=q[i][2]*s;
            int color=i%3==0?a:i%3==1?b:wash;
            if(q[i][3]==0) fill(c,p,new RectF(cx-r,cy-r,cx+r,cy+r*.55f),color);
            else if(q[i][3]==1) polygon(c,p,new float[]{cx-r,cy,cx-r*.45f,cy-r,cx+r*.7f,cy-r*.5f,cx+r,cy+r*.25f,cx,cy+r},color);
            else { p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawCircle(cx,cy,r*.6f,p); }
        }
    }
    private static void drawChecker(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        float left=x+w*.64f,top=y+h*.22f,cell=6*s;
        for(int row=0;row<5;row++)for(int col=0;col<5;col++)if((row+col)%2==0){p.setStyle(Paint.Style.FILL);p.setColor((row+col)%4==0?a:b);c.drawRect(left+col*cell,top+row*cell,left+(col+1)*cell,top+(row+1)*cell,p);}
    }
    private static void drawTransit(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b) {
        Path route=new Path();route.moveTo(x+w*.64f,y+h*.72f);route.lineTo(x+w*.73f,y+h*.72f);route.lineTo(x+w*.73f,y+h*.38f);route.lineTo(x+w*.88f,y+h*.38f);route.lineTo(x+w*.88f,y+h*.21f);route.lineTo(x+w*.98f,y+h*.21f);path(c,p,route,a,1.2f*s);
        float[][] q={{.64f,.72f},{.73f,.72f},{.73f,.38f},{.88f,.38f},{.88f,.21f},{.98f,.21f}};
        for(int i=0;i<q.length;i++){float cx=x+w*q[i][0],cy=y+h*q[i][1];p.setStyle(Paint.Style.FILL);p.setColor(b);c.drawCircle(cx,cy,2*s,p);ring(c,p,new RectF(cx-3*s,cy-3*s,cx+3*s,cy+3*s),a,.7f*s);}
        for(int i=0;i<7;i++)line(c,p,x+w*(.67f+i*.035f),y+h*.85f,x+w*(.67f+i*.035f),y+h*.94f,i%2==0?a:b,.8f*s);
    }
    private static void drawGlaze(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        Path blob=new Path();blob.moveTo(x+w*.68f,y+h*.50f);blob.cubicTo(x+w*.64f,y+h*.25f,x+w*.84f,y+h*.13f,x+w*.94f,y+h*.30f);blob.cubicTo(x+w*1.02f,y+h*.48f,x+w*.92f,y+h*.77f,x+w*.77f,y+h*.76f);blob.cubicTo(x+w*.67f,y+h*.75f,x+w*.63f,y+h*.62f,x+w*.68f,y+h*.50f);blob.close();p.setStyle(Paint.Style.FILL);p.setColor(wash);c.drawPath(blob,p);path(c,p,blob,a,.85f*s);
        Path shine=new Path();shine.moveTo(x+w*.72f,y+h*.36f);shine.cubicTo(x+w*.76f,y+h*.22f,x+w*.87f,y+h*.22f,x+w*.91f,y+h*.32f);path(c,p,shine,b,1.2f*s);
    }
    private static void drawOrbit(Canvas c, Paint p, float x, float y, float w, float h, float s, int a, int b, int wash) {
        float cx=x+w*.81f,cy=y+h*.52f;
        for(int i=0;i<3;i++){RectF r=new RectF(cx-(12+i*8)*s,cy-(8+i*5)*s,cx+(12+i*8)*s,cy+(8+i*5)*s);c.save();c.rotate(-25+i*23,cx,cy);ring(c,p,r,i%2==0?a:b,.8f*s);c.restore();}
        p.setStyle(Paint.Style.FILL);p.setColor(wash);c.drawCircle(cx,cy,5*s,p);ring(c,p,new RectF(cx-5*s,cy-5*s,cx+5*s,cy+5*s),a,.8f*s);
        p.setColor(b);c.drawCircle(cx+19*s,cy-6*s,2.2f*s,p);star(c,p,cx-20*s,cy+12*s,3*s,a);
    }
}
