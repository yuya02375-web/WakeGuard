package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ClockFaceActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String mode="world",zoneId=""; private long timerId=-1L;
    private TextView digital,detail; private AnalogFace face;
    private boolean analog=false;
    private final Runnable tick=new Runnable(){@Override public void run(){update();handler.postDelayed(this,"stopwatch".equals(mode)?50L:200L);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this);
        Intent i=getIntent(); if(i!=null){String m=i.getStringExtra("clock_mode");if(m!=null)mode=m;String z=i.getStringExtra("zone");if(z!=null)zoneId=z;timerId=i.getLongExtra("timer_id",-1L);}
        analog=prefs().getBoolean(facePrefKey(),false);build(); update();
    }
    @Override protected void onResume(){super.onResume();applyPrefs();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(tick);}
    @Override public void onBackPressed(){Ui.finishNoAnimation(this);}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,18));
        LinearLayout top=Ui.row(this);top.setPadding(0,0,0,0);Button back=Ui.ghostButton(this,"‹");back.setTextSize(28);back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,50)));
        TextView title=Ui.title(this,title(),23);title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        Button settings=Ui.ghostButton(this,"⚙");settings.setTextSize(23);settings.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,ClockSettingsActivity.class)));top.addView(settings,new LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,50)));root.addView(top);

        detail=Ui.text(this,"",13,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,4));root.addView(detail);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        digital=Ui.text(this,"--:--:--",55,Ui.TEXT);digital.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);digital.setGravity(Gravity.CENTER);stage.addView(digital,new FrameLayout.LayoutParams(-1,-1));
        face=new AnalogFace(this);stage.addView(face,new FrameLayout.LayoutParams(-1,-1));
        final float[] downX={0f};stage.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){downX[0]=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-downX[0];if(Math.abs(dx)>Ui.dp(this,55))setAnalog(dx<0);else setAnalog(!analog);return true;}return true;});
        TextView hint=Ui.text(this,"時計をタップでデジタル / アナログ切替  ·  左右スワイプも対応",11,Ui.MUTED_2);hint.setGravity(Gravity.CENTER);root.addView(hint);
        setContentView(root);Ui.applySystemBarInsets(this,root);applyMode();applyPrefs();
    }
    private String title(){return "stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"時計";}
    private String facePrefKey(){return "stopwatch".equals(mode)?"stopwatch_face_analog":"timer".equals(mode)?"timer_face_analog":"clock_face_analog";}
    private void setAnalog(boolean value){analog=value;prefs().edit().putBoolean(facePrefKey(),analog).apply();applyMode();}
    private void applyMode(){
        digital.setVisibility(analog?View.GONE:View.VISIBLE);face.setVisibility(analog?View.VISIBLE:View.GONE);
    }
    private void applyPrefs(){
        boolean keep=prefs().getBoolean("clock_keep_on",false);if(keep)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(detail!=null)detail.setVisibility(prefs().getBoolean("clock_show_detail",true)?View.VISIBLE:View.GONE);
        if(face!=null)face.configure(prefs().getBoolean("analog_numbers",true),prefs().getBoolean("analog_ticks",true),prefs().getBoolean("analog_seconds",true),prefs().getInt("analog_number_scale",1));
        boolean stored=prefs().getBoolean(facePrefKey(),analog);if(stored!=analog){analog=stored;applyMode();}
    }
    private android.content.SharedPreferences prefs(){return getSharedPreferences("clock_tools",MODE_PRIVATE);}
    private long stopwatchElapsed(){android.content.SharedPreferences p=prefs();long saved=p.getLong("sw_elapsed",0L);if(!p.getBoolean("sw_running",false))return saved;long base=p.getLong("sw_base",SystemClock.elapsedRealtime());return saved+Math.max(0L,SystemClock.elapsedRealtime()-base);}
    private String fmtStopwatch(long ms){long h=ms/3600000L,m=(ms/60000L)%60,s=(ms/1000L)%60,cs=(ms/10L)%100;return String.format(Locale.US,"%02d:%02d:%02d.%02d",h,m,s,cs);}
    private String fmtTimer(long ms){long total=Math.max(0L,ms)/1000L,h=total/3600L,m=(total/60L)%60,s=total%60L;return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);}
    private void update(){
        try{
            if("stopwatch".equals(mode)){
                long ms=stopwatchElapsed();digital.setText(fmtStopwatch(ms));detail.setText(I18n.tr(this,"経過時間"));
                double sec=(ms/1000d)%60d,min=(ms/60000d)%60d,hr=(ms/3600000d)%12d;face.setHands(hr,min,sec);
            }else if("timer".equals(mode)){
                TimerStore.Entry e=TimerStore.find(this,timerId);long ms=e==null?0L:e.remaining();digital.setText(fmtTimer(ms));detail.setText(e==null?I18n.tr(this,"タイマー"):((e.label==null||e.label.isEmpty())?I18n.tr(this,"残り時間"):e.label+"  ·  "+I18n.tr(this,"残り時間")));
                double sec=(ms/1000d)%60d,min=(ms/60000d)%60d,hr=(ms/3600000d)%12d;face.setHands(hr,min,sec);
            }else{
                ZoneId z=(zoneId==null||zoneId.isEmpty())?ZoneId.systemDefault():ZoneId.of(zoneId);ZonedDateTime now=ZonedDateTime.now(z);boolean h24=prefs().getBoolean("use_24h",true);Locale loc=I18n.locale(this);
                digital.setText(now.format(DateTimeFormatter.ofPattern(h24?"HH:mm:ss":"hh:mm:ss a",loc)));detail.setText(now.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"full"),loc))+"  ·  "+z.getId());
                face.setHands(now.getHour()%12+now.getMinute()/60d,now.getMinute()+now.getSecond()/60d,now.getSecond()+now.getNano()/1_000_000_000d);
            }
        }catch(Throwable ignored){}
    }

    public static class AnalogFace extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private double h,m,s;private boolean numbers=true,ticks=true,seconds=true;private int numberScale=1;
        public AnalogFace(Context c){super(c);p.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(Color.TRANSPARENT);}
        public void configure(boolean n,boolean t,boolean sec,int scale){numbers=n;ticks=t;seconds=sec;numberScale=Math.max(0,Math.min(2,scale));invalidate();}
        public void setHands(double hh,double mm,double ss){h=hh;m=mm;s=ss;invalidate();}
        private float dp(float value){return value*getResources().getDisplayMetrics().density;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),hh=getHeight(),cx=w/2f,cy=hh/2f,r=Math.min(w,hh)*0.40f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Ui.BORDER);c.drawCircle(cx,cy,r,p);
            if(ticks)for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float len=i%5==0?r*0.105f:r*0.045f;p.setStrokeWidth(i%5==0?dp(2.4f):dp(1));p.setColor(i%5==0?Ui.MUTED:Ui.MUTED_2);c.drawLine(cx+(float)Math.cos(a)*(r-len),cy+(float)Math.sin(a)*(r-len),cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,p);}
            if(numbers){float[] sizes={17f,21f,25f};p.setStyle(Paint.Style.FILL);p.setColor(Ui.TEXT);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));p.setTextSize(dp(sizes[numberScale]));Paint.FontMetrics fm=p.getFontMetrics();float off=-(fm.ascent+fm.descent)/2f;for(int i=1;i<=12;i++){double a=Math.toRadians(i*30-90);float nr=r*0.79f;c.drawText(String.valueOf(i),cx+(float)Math.cos(a)*nr,cy+(float)Math.sin(a)*nr+off,p);}}
            hand(c,cx,cy,r*0.48f,h*30,dp(7),Ui.TEXT);hand(c,cx,cy,r*0.68f,m*6,dp(5),Ui.TEXT);if(seconds)hand(c,cx,cy,r*0.78f,s*6,dp(2),Ui.ACCENT);
            p.setStyle(Paint.Style.FILL);p.setColor(Ui.ACCENT);c.drawCircle(cx,cy,dp(5),p);
        }
        private void hand(Canvas c,float cx,float cy,float len,double deg,float stroke,int color){double a=Math.toRadians(deg-90);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(color);c.drawLine(cx,cy,cx+(float)Math.cos(a)*len,cy+(float)Math.sin(a)*len,p);}
    }
}
