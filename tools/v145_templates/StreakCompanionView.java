package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * WakeGuard v1.4.5 realistic flame-dragon renderer.
 * A local dragon silhouette mask is animated by an OpenGL ES 2.0 procedural
 * flame shader. Everything ships inside the APK and remains fully offline.
 */
public final class StreakCompanionView extends GLSurfaceView {
    private final FlameRenderer flameRenderer;
    private volatile long growthLevel = 1L;
    private volatile int currentStreak = 0;

    public StreakCompanionView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        flameRenderer = new FlameRenderer();
        setRenderer(flameRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }

    public void setGrowth(long level, int streak) {
        growthLevel = Math.max(1L, level);
        currentStreak = Math.max(0, streak);
        flameRenderer.setGrowth(growthLevel, currentStreak);
        requestRender();
    }

    public void setAnimationEnabled(boolean enabled) {
        flameRenderer.animate = enabled;
        setRenderMode(enabled ? GLSurfaceView.RENDERMODE_CONTINUOUSLY : GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        requestRender();
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static final class FlameRenderer implements GLSurfaceView.Renderer {
        private static final int MASK_W = 512;
        private static final int MASK_H = 564;

        private final FloatBuffer positions = floatBuffer(new float[]{
            -1f, -1f,  1f, -1f,  -1f, 1f,  1f, 1f
        });
        private final FloatBuffer texCoords = floatBuffer(new float[]{
             0f,  1f,  1f,  1f,   0f, 0f,  1f, 0f
        });

        private int program;
        private int aPosition;
        private int aTexCoord;
        private int uMask;
        private int uTime;
        private int uPower;
        private int uStreak;
        private int uMythic;
        private int uInfinite;
        private int uTexel;
        private int texture;
        private long level = 1L;
        private int streak = 0;
        private volatile Bitmap pendingMask;
        private volatile boolean animate = true;
        private long frozenAt = SystemClock.uptimeMillis();

        synchronized void setGrowth(long newLevel, int newStreak) {
            level = Math.max(1L, newLevel);
            streak = Math.max(0, newStreak);
            Bitmap next = buildMask(level);
            Bitmap old = pendingMask;
            pendingMask = next;
            if (old != null && old != next && !old.isRecycled()) old.recycle();
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.043f, 0.051f, 0.075f, 1f);
            program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            aPosition = GLES20.glGetAttribLocation(program, "aPosition");
            aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
            uMask = GLES20.glGetUniformLocation(program, "uMask");
            uTime = GLES20.glGetUniformLocation(program, "uTime");
            uPower = GLES20.glGetUniformLocation(program, "uPower");
            uStreak = GLES20.glGetUniformLocation(program, "uStreak");
            uMythic = GLES20.glGetUniformLocation(program, "uMythic");
            uInfinite = GLES20.glGetUniformLocation(program, "uInfinite");
            uTexel = GLES20.glGetUniformLocation(program, "uTexel");

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            texture = tex[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            pendingMask = buildMask(level);
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override public void onDrawFrame(GL10 gl) {
            uploadPendingMask();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (program == 0 || texture == 0) return;

            GLES20.glUseProgram(program);
            positions.position(0);
            texCoords.position(0);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glEnableVertexAttribArray(aTexCoord);
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, positions);
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(uMask, 0);

            double p = StreakGrowth.visualPower(level);
            float power = (float)Math.min(18.0, p);
            float mythic = smooth((float)((p - 6.65) / 2.75));
            float infinite = (float)Math.log1p(Math.max(0L, level - 999L)) / 8.0f;
            infinite = Math.max(0f, Math.min(2.6f, infinite));
            long now = SystemClock.uptimeMillis();
            if (!animate) frozenAt = now;
            float time = (animate ? now : frozenAt) / 1000f;

            GLES20.glUniform1f(uTime, time);
            GLES20.glUniform1f(uPower, power);
            GLES20.glUniform1f(uStreak, Math.min(30f, streak));
            GLES20.glUniform1f(uMythic, mythic);
            GLES20.glUniform1f(uInfinite, infinite);
            GLES20.glUniform2f(uTexel, 1f / MASK_W, 1f / MASK_H);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aTexCoord);
        }

        private void uploadPendingMask() {
            Bitmap bitmap;
            synchronized (this) { bitmap = pendingMask; pendingMask = null; }
            if (bitmap == null || bitmap.isRecycled() || texture == 0) return;
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
        }

        private static FloatBuffer floatBuffer(float[] values) {
            ByteBuffer bytes = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder());
            FloatBuffer out = bytes.asFloatBuffer();
            out.put(values).position(0);
            return out;
        }

        private static int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }

        private static int linkProgram(String vertex, String fragment) {
            int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
            if (vs == 0 || fs == 0) return 0;
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs);
            GLES20.glAttachShader(p, fs);
            GLES20.glLinkProgram(p);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            if (ok[0] == 0) {
                GLES20.glDeleteProgram(p);
                return 0;
            }
            return p;
        }

        private static Bitmap buildMask(long level) {
            Bitmap bitmap = Bitmap.createBitmap(MASK_W, MASK_H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xffffffff);
            double power = StreakGrowth.visualPower(level);
            float dragon = smooth((float)((power - 3.15) / 3.55));
            float wings = smooth((float)((power - 4.45) / 2.25));
            float mythic = smooth((float)((power - 6.65) / 2.75));
            float infinite = (float)Math.log1p(Math.max(0L, level - 999L)) / 8.0f;
            infinite = Math.max(0f, Math.min(2.6f, infinite));

            c.save();
            float sx = MASK_W / 390f;
            float sy = MASK_H / 430f;
            c.translate(MASK_W * .5f, MASK_H * .55f);
            c.scale(sx, sy);
            drawFlameMask(c, p, power);
            if (dragon > .02f) drawDragonMask(c, p, power, dragon, wings, mythic, infinite);
            c.restore();
            return bitmap;
        }

        private static void drawFlameMask(Canvas c, Paint p, double power) {
            float grow = (float)Math.min(44.0, power * 3.5);
            Path fire = buildFlamePath(214f + grow, 86f + (float)Math.min(24.0, power * 1.8));
            p.setAlpha(255);
            c.drawPath(fire, p);

            int tongues = 8 + Math.min(10, (int)(power * .75));
            for (int i = 0; i < tongues; i++) {
                float q = (i - (tongues - 1) * .5f) / Math.max(1f, (tongues - 1) * .5f);
                float x = q * (70f + (float)Math.min(18.0, power));
                float top = -102f - (i % 4) * 13f - (float)Math.min(35.0, power * 2.1);
                Path t = new Path();
                t.moveTo(x - 7f, -35f);
                t.cubicTo(x - 16f, -62f, x - 8f, top + 28f, x + q * 9f, top);
                t.cubicTo(x + 14f, top + 32f, x + 15f, -62f, x + 7f, -35f);
                t.close();
                p.setAlpha(205);
                c.drawPath(t, p);
            }
        }

        private static Path buildFlamePath(float height, float maxWidth) {
            Path out = new Path();
            int n = 28;
            float[] y = new float[n + 1], l = new float[n + 1], r = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float t = i / (float)n;
                float yy = 112f - height * t;
                float profile = (float)Math.pow(Math.sin(Math.PI * Math.min(.985f, t * .93f + .035f)), .66);
                profile *= 1f - .18f * t;
                float wave = (float)Math.sin(t * 9.0f * Math.PI * 2f) * .055f;
                float detail = (float)Math.sin((t * 4.7f + .18f) * Math.PI * 2f) * .035f;
                float asym = (float)Math.sin((t * 3.2f + .31f) * Math.PI * 2f) * .045f;
                float width = maxWidth * Math.max(0f, profile + wave + detail);
                if (i == 0) width = maxWidth * .43f;
                if (i == n) width = 0f;
                y[i] = yy;
                l[i] = -width * (1f + asym);
                r[i] = width * (1f - asym);
            }
            out.moveTo(l[0], y[0]);
            for (int i = 1; i <= n; i++) out.quadTo(l[i - 1], y[i - 1], (l[i - 1] + l[i]) * .5f, (y[i - 1] + y[i]) * .5f);
            out.lineTo(0f, y[n] - 8f);
            for (int i = n; i >= 1; i--) out.quadTo(r[i], y[i], (r[i] + r[i - 1]) * .5f, (y[i] + y[i - 1]) * .5f);
            out.quadTo(0f, 132f, l[0], y[0]);
            out.close();
            return out;
        }

        private static void drawDragonMask(Canvas c, Paint p, double power, float dragon, float wings, float mythic, float infinite) {
            p.setAlpha(Math.max(18, (int)(255f * dragon)));
            if (wings > .02f) drawWingMask(c, p, -1f, wings, mythic, infinite);
            if (wings > .02f) drawWingMask(c, p, 1f, wings, mythic, infinite);
            drawBodyMask(c, p, dragon, mythic);
            drawHeadMask(c, p, dragon, mythic);
            if (dragon > .18f) drawTailMask(c, p, dragon, mythic);
            if (mythic > .15f) drawMythicMask(c, p, mythic, infinite);
        }

        private static void drawBodyMask(Canvas c, Paint p, float dragon, float mythic) {
            float shoulder = 34f + dragon * 10f + mythic * 6f;
            float waist = 19f + dragon * 6f;
            Path body = new Path();
            body.moveTo(-shoulder, -28f);
            body.cubicTo(-49f, 5f, -40f, 62f, -waist, 94f);
            body.cubicTo(-14f, 116f, -8f, 126f, 0f, 132f);
            body.cubicTo(8f, 126f, 14f, 116f, waist, 94f);
            body.cubicTo(40f, 62f, 49f, 5f, shoulder, -28f);
            body.cubicTo(23f, -48f, 12f, -55f, 0f, -57f);
            body.cubicTo(-12f, -55f, -23f, -48f, -shoulder, -28f);
            body.close();
            c.drawPath(body, p);

            if (mythic > .2f) {
                for (int side = -1; side <= 1; side += 2) {
                    Path spike = new Path();
                    spike.moveTo(side * 28f, 28f);
                    spike.lineTo(side * (42f + mythic * 11f), 8f);
                    spike.lineTo(side * 31f, 47f);
                    spike.close();
                    c.drawPath(spike, p);
                }
            }
        }

        private static void drawHeadMask(Canvas c, Paint p, float dragon, float mythic) {
            c.save();
            float s = .78f + dragon * .22f + mythic * .08f;
            c.scale(s, s);
            c.translate(0f, -56f);

            Path neck = new Path();
            neck.moveTo(-25f, 26f);
            neck.cubicTo(-33f, 1f, -33f, -28f, -23f, -46f);
            neck.cubicTo(-16f, -59f, -9f, -64f, 0f, -65f);
            neck.cubicTo(9f, -64f, 16f, -59f, 23f, -46f);
            neck.cubicTo(33f, -28f, 33f, 1f, 25f, 26f);
            neck.close();
            c.drawPath(neck, p);

            Path head = new Path();
            head.moveTo(-31f, -52f);
            head.cubicTo(-38f, -67f, -35f, -84f, -23f, -96f);
            head.lineTo(-11f, -103f);
            head.lineTo(-6f, -116f);
            head.lineTo(0f, -105f);
            head.lineTo(6f, -116f);
            head.lineTo(11f, -103f);
            head.lineTo(23f, -96f);
            head.cubicTo(35f, -84f, 38f, -67f, 31f, -52f);
            head.lineTo(22f, -43f);
            head.lineTo(28f, -36f);
            head.lineTo(12f, -32f);
            head.lineTo(0f, -37f);
            head.lineTo(-12f, -32f);
            head.lineTo(-28f, -36f);
            head.lineTo(-22f, -43f);
            head.close();
            c.drawPath(head, p);

            for (int side = -1; side <= 1; side += 2) {
                Path horn = new Path();
                horn.moveTo(side * 18f, -92f);
                horn.cubicTo(side * 35f, -110f, side * (48f + mythic * 9f), -127f, side * (42f + mythic * 12f), -145f - mythic * 10f);
                horn.cubicTo(side * 28f, -126f, side * 21f, -111f, side * 14f, -96f);
                horn.close();
                c.drawPath(horn, p);
            }
            c.restore();
        }

        private static void drawWingMask(Canvas c, Paint p, float side, float wings, float mythic, float infinite) {
            float span = 86f + wings * 74f + mythic * 16f + infinite * 5f;
            float lift = 58f + wings * 43f + mythic * 12f;
            Path wing = new Path();
            wing.moveTo(side * 34f, -27f);
            wing.cubicTo(side * 59f, -58f, side * (93f + wings * 18f), -93f, side * span, -lift);
            wing.cubicTo(side * (span + 10f), -33f, side * (span - 10f), -5f, side * (span - 28f), 16f);
            wing.lineTo(side * (span - 53f), 0f);
            wing.lineTo(side * (span - 72f), 31f);
            wing.lineTo(side * (span - 94f), 12f);
            wing.lineTo(side * 62f, 38f);
            wing.lineTo(side * 42f, 15f);
            wing.close();
            c.drawPath(wing, p);

            int tips = 3 + (int)(mythic * 3f);
            for (int i = 0; i < tips; i++) {
                float y = -42f + i * 29f;
                Path tip = new Path();
                tip.moveTo(side * (span - 18f - i * 14f), y + 18f);
                tip.lineTo(side * (span + 10f + mythic * 12f), y - 12f - i * 5f);
                tip.lineTo(side * (span - 34f - i * 12f), y + 2f);
                tip.close();
                p.setAlpha(Math.max(12, (int)(230f * wings)));
                c.drawPath(tip, p);
            }
        }

        private static void drawTailMask(Canvas c, Paint p, float dragon, float mythic) {
            Path tail = new Path();
            tail.moveTo(-15f, 79f);
            tail.cubicTo(-48f, 98f, -89f, 86f, -126f - mythic * 18f, 112f);
            tail.cubicTo(-101f, 74f, -63f, 58f, -21f, 54f);
            tail.close();
            c.drawPath(tail, p);

            Path tip = new Path();
            tip.moveTo(-118f - mythic * 18f, 102f);
            tip.lineTo(-153f - mythic * 19f, 91f);
            tip.lineTo(-130f - mythic * 18f, 122f);
            tip.close();
            c.drawPath(tip, p);
        }

        private static void drawMythicMask(Canvas c, Paint p, float mythic, float infinite) {
            int crown = 5 + (int)(mythic * 4f) + Math.min(3, (int)infinite);
            p.setAlpha(140 + (int)(mythic * 95f));
            for (int i = 0; i < crown; i++) {
                float q = (i - (crown - 1) * .5f) / Math.max(1f, (crown - 1) * .5f);
                float x = q * (44f + mythic * 12f);
                float len = 25f + (1f - Math.abs(q)) * 34f + (i % 3) * 6f + infinite * 3f;
                Path flame = new Path();
                flame.moveTo(x - 6f, -130f);
                flame.cubicTo(x - 12f, -143f, x - 5f, -130f - len * .55f, x, -130f - len);
                flame.cubicTo(x + 10f, -130f - len * .52f, x + 12f, -142f, x + 6f, -130f);
                flame.close();
                c.drawPath(flame, p);
            }
        }

        private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vUv;\n" +
            "void main(){ vUv=aTexCoord; gl_Position=vec4(aPosition,0.0,1.0); }\n";

        private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vUv;\n" +
            "uniform sampler2D uMask;\n" +
            "uniform float uTime;\n" +
            "uniform float uPower;\n" +
            "uniform float uStreak;\n" +
            "uniform float uMythic;\n" +
            "uniform float uInfinite;\n" +
            "uniform vec2 uTexel;\n" +
            "float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123); }\n" +
            "float noise(vec2 p){ vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f); float a=hash(i), b=hash(i+vec2(1.0,0.0)), c=hash(i+vec2(0.0,1.0)), d=hash(i+vec2(1.0,1.0)); return mix(mix(a,b,f.x),mix(c,d,f.x),f.y); }\n" +
            "float fbm(vec2 p){ float f=0.0; float a=0.52; for(int i=0;i<4;i++){ f+=a*noise(p); p=p*2.03+vec2(17.13,9.27); a*=0.5; } return f; }\n" +
            "float maskAt(vec2 uv){ return texture2D(uMask,clamp(uv,vec2(0.001),vec2(0.999))).a; }\n" +
            "float sparkLayer(vec2 uv,float scale,float speed,vec2 seed){ vec2 p=uv*vec2(scale,scale*1.35); p.y+=uTime*speed; vec2 id=floor(p); vec2 f=fract(p)-0.5; float r=hash(id+seed); f.x+=(r-0.5)*0.72; float d=length(f*vec2(1.35,2.9)); return (1.0-smoothstep(0.0,0.075,d))*step(0.82,r); }\n" +
            "void main(){\n" +
            "  vec2 uv=vUv;\n" +
            "  float t=uTime;\n" +
            "  float n1=fbm(vec2(uv.x*5.2,uv.y*7.0-t*0.42));\n" +
            "  float n2=fbm(vec2(uv.x*12.5+13.7,uv.y*14.5-t*0.93));\n" +
            "  float sway=sin(t*1.15+uv.y*13.0+n1*3.2)*0.0048;\n" +
            "  vec2 warp=vec2((n1-0.5)*(0.011+min(uPower,12.0)*0.00065)+sway,(n2-0.5)*0.0075);\n" +
            "  float m=maskAt(uv+warp);\n" +
            "  float plume=0.0;\n" +
            "  for(int i=1;i<=4;i++){ float fi=float(i); float drift=(noise(vec2(uv.y*11.0+t*0.6,fi*4.7))-0.5)*0.026; vec2 puv=uv+vec2(drift,fi*0.0125+(n1-0.5)*0.005); float src=maskAt(puv); plume=max(plume,src-fi*0.135+n2*0.055); }\n" +
            "  float shape=clamp(max(m,plume),0.0,1.0);\n" +
            "  vec2 r=uTexel*5.0;\n" +
            "  float blur=(maskAt(uv+vec2(r.x,0.0))+maskAt(uv-vec2(r.x,0.0))+maskAt(uv+vec2(0.0,r.y))+maskAt(uv-vec2(0.0,r.y))+maskAt(uv+r)+maskAt(uv-r)+maskAt(uv+vec2(r.x,-r.y))+maskAt(uv+vec2(-r.x,r.y)))*0.125;\n" +
            "  vec2 r2=uTexel*11.0;\n" +
            "  float blur2=(maskAt(uv+vec2(r2.x,0.0))+maskAt(uv-vec2(r2.x,0.0))+maskAt(uv+vec2(0.0,r2.y))+maskAt(uv-vec2(0.0,r2.y)))*0.25;\n" +
            "  float halo=max(blur*0.82+blur2*0.42-shape*0.48,0.0);\n" +
            "  float center=1.0-smoothstep(0.02,0.34,abs(uv.x-0.5));\n" +
            "  float lower=smoothstep(0.24,0.88,uv.y);\n" +
            "  float core=shape*center*lower*(0.68+0.42*n2);\n" +
            "  float heat=clamp(shape*(0.46+0.54*(0.58*n1+0.42*n2))+core*0.55,0.0,1.0);\n" +
            "  vec3 deep=vec3(0.34,0.008,0.002);\n" +
            "  vec3 red=vec3(0.94,0.045,0.006);\n" +
            "  vec3 orange=vec3(1.0,0.26,0.015);\n" +
            "  vec3 gold=vec3(1.0,0.67,0.08);\n" +
            "  vec3 white=vec3(1.0,0.96,0.72);\n" +
            "  vec3 hot=mix(white,vec3(0.78,0.96,1.0),uMythic*0.38);\n" +
            "  vec3 flame=mix(deep,red,smoothstep(0.05,0.35,heat));\n" +
            "  flame=mix(flame,orange,smoothstep(0.25,0.60,heat));\n" +
            "  flame=mix(flame,gold,smoothstep(0.52,0.82,heat));\n" +
            "  flame=mix(flame,hot,smoothstep(0.76,1.0,heat+core*0.18));\n" +
            "  float alpha=smoothstep(0.035,0.27,shape);\n" +
            "  float edge=clamp((blur-shape)*2.2+halo,0.0,1.0);\n" +
            "  vec3 bg=vec3(0.043,0.051,0.075);\n" +
            "  vec3 color=bg+flame*alpha*(0.90+0.18*n2)+vec3(1.0,0.19,0.018)*halo*0.70+vec3(1.0,0.52,0.06)*edge*0.28;\n" +
            "  float region=(1.0-smoothstep(0.18,0.50,abs(uv.x-0.5)))*(1.0-smoothstep(0.78,1.03,uv.y));\n" +
            "  float sparks=sparkLayer(uv,18.0,0.72,vec2(3.1,7.9))+sparkLayer(uv+vec2(0.17,0.0),27.0,1.12,vec2(11.2,2.7));\n" +
            "  sparks*=region*clamp(0.32+uPower*0.045+uStreak*0.012+uInfinite*0.08,0.0,1.35);\n" +
            "  color+=mix(vec3(1.0,0.28,0.02),vec3(1.0,0.88,0.32),hash(floor(uv*43.0)))*sparks;\n" +
            "  float pulse=0.96+0.04*sin(t*2.35+n1*6.283);\n" +
            "  gl_FragColor=vec4(clamp(color*pulse,0.0,1.0),1.0);\n" +
            "}\n";
    }
}
