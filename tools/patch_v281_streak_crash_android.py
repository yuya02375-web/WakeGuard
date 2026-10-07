from pathlib import Path
root=Path("WakeGuard/app")
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p="build.gradle.kts"; s=read(p)
s=s.replace('versionCode = 180','versionCode = 181',1).replace('versionName = "2.8.0"','versionName = "2.8.1"',1)
for dep in [
    '    implementation("com.google.android.filament:filament-android:1.75.1")\n',
    '    implementation("com.google.android.filament:gltfio-android:1.75.1")\n',
    '    implementation("com.google.android.filament:filament-utils-android:1.75.1")\n',
]: s=s.replace(dep,'')
write(p,s)

java=r'''package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** 2.8.1: crash-safe real 3D mesh renderer using only Android OpenGL ES 2.0. */
public final class StreakCompanionView extends FrameLayout {
    private final GLSurfaceView glView;
    private final DragonRenderer renderer;
    private final FlameAuraView aura;
    private final TextView error;
    private boolean resumed;

    public StreakCompanionView(Context context) {
        super(context);
        setBackgroundColor(0xff0b0d13);
        setClipChildren(false);
        setClipToPadding(false);

        GLSurfaceView v=null; DragonRenderer r=null;
        try{
            r=new DragonRenderer(context.getApplicationContext());
            v=new GLSurfaceView(context);
            v.setEGLContextClientVersion(2);
            v.setPreserveEGLContextOnPause(true);
            v.setRenderer(r);
            v.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            addView(v,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        }catch(Throwable ignored){}
        glView=v; renderer=r;

        aura=new FlameAuraView(context);
        aura.setClickable(false);
        addView(aura,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        error=new TextView(context);
        error.setTextColor(0xffaaaab2); error.setTextSize(12f);
        error.setGravity(android.view.Gravity.CENTER);
        error.setText("3D表示を準備できませんでした\nストリーク画面はそのまま使えます");
        error.setVisibility(glView==null?View.VISIBLE:View.GONE);
        addView(error,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    }

    public void setGrowth(long level,int currentStreak){
        long lv=Math.max(1L,level); int st=Math.max(0,currentStreak);
        aura.setGrowth(lv,st);
        if(renderer!=null)renderer.setGrowth(lv,st);
        float emergence=clamp((lv-7f)/20f);
        if(glView!=null){
            glView.setAlpha(.16f+.84f*emergence);
            float scale=.88f+.12f*emergence+Math.min(.08f,Math.max(0f,lv-30f)/600f);
            glView.setScaleX(scale);glView.setScaleY(scale);
        }
        aura.setAlpha(.93f-.28f*emergence);
    }
    public void setAnimationEnabled(boolean enabled){if(enabled)onResume();else onPause();}
    public void onResume(){if(resumed)return;resumed=true;if(glView!=null)try{glView.onResume();}catch(Throwable ignored){}}
    public void onPause(){if(!resumed)return;resumed=false;if(glView!=null)try{glView.onPause();}catch(Throwable ignored){}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();onResume();}
    @Override protected void onDetachedFromWindow(){onPause();super.onDetachedFromWindow();}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    private static final class DragonRenderer implements GLSurfaceView.Renderer {
        private static final int FPV=6;
        private static final String VS=
                "uniform mat4 uMVP;uniform mat4 uModel;attribute vec3 aPos;attribute vec3 aNormal;varying vec3 vN;varying vec3 vP;"+
                "void main(){vec4 wp=uModel*vec4(aPos,1.0);vP=wp.xyz;vN=normalize(mat3(uModel)*aNormal);gl_Position=uMVP*vec4(aPos,1.0);}";
        private static final String FS=
                "precision mediump float;varying vec3 vN;varying vec3 vP;uniform float uHeat;"+
                "void main(){vec3 N=normalize(vN);vec3 L=normalize(vec3(-.55,.82,.48));vec3 B=normalize(vec3(.72,.18,-.66));"+
                "float d=max(dot(N,L),0.0);float f=max(dot(N,B),0.0);float rim=pow(1.0-abs(N.z),2.5);"+
                "float n=.5+.5*sin(vP.x*47.0+sin(vP.y*39.0)+vP.z*53.0);"+
                "vec3 c=vec3(.075,.010,.006)*(.55+1.45*d)+vec3(.055,.075,.13)*f;"+
                "c+=vec3(.62,.075,.012)*rim*(.30+.55*uHeat);c+=vec3(.16,.018,.004)*n*(.12+.20*uHeat);"+
                "c+=vec3(1.0,.22,.015)*smoothstep(.93,.995,n)*(.10+.45*uHeat);gl_FragColor=vec4(c,1.0);}";
        private final FloatBuffer mesh; private final int vertexCount;
        private final float[] projection=new float[16],view=new float[16],model=new float[16],pv=new float[16],mvp=new float[16];
        private int program,aPos,aNormal,uMvp,uModel,uHeat; private volatile long level=1; private long startNanos;

        DragonRenderer(Context c)throws Exception{MeshData d=readMesh(c);mesh=d.buffer;vertexCount=d.vertexCount;}
        void setGrowth(long l,int s){level=Math.max(1,l);}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
            GLES20.glClearColor(.043f,.051f,.075f,1f);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
            program=createProgram(VS,FS);if(program==0)return;
            aPos=GLES20.glGetAttribLocation(program,"aPos");aNormal=GLES20.glGetAttribLocation(program,"aNormal");
            uMvp=GLES20.glGetUniformLocation(program,"uMVP");uModel=GLES20.glGetUniformLocation(program,"uModel");uHeat=GLES20.glGetUniformLocation(program,"uHeat");
            startNanos=System.nanoTime();
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){
            GLES20.glViewport(0,0,Math.max(1,w),Math.max(1,h));float aspect=w/(float)Math.max(1,h);
            Matrix.perspectiveM(projection,0,31f,aspect,.1f,20f);Matrix.setLookAtM(view,0,0f,.10f,4.0f,0f,.05f,0f,0f,1f,0f);Matrix.multiplyMM(pv,0,projection,0,view,0);
        }
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);if(program==0||vertexCount<=0)return;
            float sec=(System.nanoTime()-startNanos)/1_000_000_000f;float growth=clamp((level-7f)/20f);
            Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,-8f,1f,0f,0f);Matrix.rotateM(model,0,18f+(float)Math.sin(sec*.22f)*5.5f,0f,1f,0f);
            float sc=1.30f+.12f*growth;Matrix.scaleM(model,0,sc,sc,sc);Matrix.multiplyMM(mvp,0,pv,0,model,0);
            GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,model,0);
            GLES20.glUniform1f(uHeat,Math.min(1f,Math.max(0f,(level-20f)/80f)));
            mesh.position(0);GLES20.glEnableVertexAttribArray(aPos);GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,FPV*4,mesh);
            mesh.position(3);GLES20.glEnableVertexAttribArray(aNormal);GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,FPV*4,mesh);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertexCount);
            GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aNormal);
        }
        private static MeshData readMesh(Context c)throws Exception{
            try(InputStream raw=c.getAssets().open("dragon_mesh.bin");DataInputStream in=new DataInputStream(new BufferedInputStream(raw))){
                int magic=Integer.reverseBytes(in.readInt()),count=Integer.reverseBytes(in.readInt());
                if(magic!=0x49474452||count<=0||count>2_000_000)throw new IllegalArgumentException("dragon mesh");
                byte[] packed=new byte[count*FPV*4];in.readFully(packed);
                ByteBuffer src=ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
                FloatBuffer fb=ByteBuffer.allocateDirect(packed.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
                while(src.remaining()>=4)fb.put(src.getFloat());fb.position(0);return new MeshData(fb,count);
            }
        }
        private static int createProgram(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);if(v==0||f==0)return 0;int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);if(ok[0]==0){GLES20.glDeleteProgram(p);return 0;}return p;}
        private static int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){GLES20.glDeleteShader(s);return 0;}return s;}
        private static final class MeshData{final FloatBuffer buffer;final int vertexCount;MeshData(FloatBuffer b,int c){buffer=b;vertexCount=c;}}
    }

    private static final class FlameAuraView extends View{
        private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG),spark=new Paint(Paint.ANTI_ALIAS_FLAG);private final Random random=new Random(281L);
        private final float[] sparkX=new float[30],sparkPhase=new float[30];private long level=1;
        FlameAuraView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);for(int i=0;i<sparkX.length;i++){sparkX[i]=random.nextFloat();sparkPhase[i]=random.nextFloat();}}
        void setGrowth(long l,int s){level=l;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;float power=Math.min(1f,Math.max(0f,(level-3f)/40f));
            float cx=w*.5f,cy=h*.56f,radius=Math.min(w,h)*(.43f+.04f*power);glow.setShader(new RadialGradient(cx,cy,radius,new int[]{0x00ff5a16,0x18ff4a10,0x27ff2600,0x00ff1800},new float[]{0f,.48f,.76f,1f},Shader.TileMode.CLAMP));c.drawCircle(cx,cy,radius,glow);glow.setShader(null);
            long now=android.os.SystemClock.uptimeMillis();for(int i=0;i<sparkX.length;i++){float t=((now/1000f)*(.21f+.016f*i)+sparkPhase[i])%1f;float x=w*(.18f+.64f*sparkX[i]),y=h*(.90f-.82f*t);int alpha=(int)(255f*(1f-t)*(.20f+.42f*power));spark.setColor((alpha<<24)|0x00ff7a18);float rr=(1.1f+(i%4)*.52f)*getResources().getDisplayMetrics().density;c.drawCircle(x,y,rr,spark);}postInvalidateDelayed(42);
        }
    }
}
''';
write("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java",java)

converter=r'''from pathlib import Path
import struct, numpy as np, trimesh
root=Path("WakeGuard/app/src/main/assets")
scene=trimesh.load(root/"dragon_realistic.glb",force="scene")
mesh=scene.to_geometry()
if isinstance(mesh,trimesh.Scene): mesh=trimesh.util.concatenate(tuple(mesh.dump()))
v=np.asarray(mesh.vertices,dtype=np.float32);f=np.asarray(mesh.faces,dtype=np.int64);n=np.asarray(mesh.vertex_normals,dtype=np.float32)
mn=v.min(0);mx=v.max(0);v=(v-(mn+mx)*.5)/(float(np.max(mx-mn))*.5)
fv=v[f].reshape(-1,3);fn=n[f].reshape(-1,3);fn=fn/np.maximum(np.linalg.norm(fn,axis=1,keepdims=True),1e-8)
packed=np.concatenate([fv,fn],axis=1).astype("<f4")
with (root/"dragon_mesh.bin").open("wb") as out: out.write(struct.pack("<II",0x49474452,packed.shape[0]));out.write(packed.tobytes())
print("mesh",packed.shape,(root/"dragon_mesh.bin").stat().st_size)
'''
Path("tools/build_dragon_mesh_v281.py").write_text(converter)

assert 'versionName = "2.8.1"' in read("build.gradle.kts")
assert 'filament-android' not in read("build.gradle.kts")
assert 'GLSurfaceView.Renderer' in read("src/main/java/jp/wakeguard/alarm/StreakCompanionView.java")
print("Android 2.8.1 crash-safe streak renderer patch applied")
