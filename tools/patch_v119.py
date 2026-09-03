from pathlib import Path
import re, runpy

runpy.run_path("tools/patch_v118.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
res=root/"app/src/main/res"

# --- Large, glanceable running timer notification ---
(res/"layout").mkdir(parents=True, exist_ok=True)
(res/"layout"/"notification_timer_running.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/timer_notification_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingLeft="14dp"
    android:paddingTop="8dp"
    android:paddingRight="14dp"
    android:paddingBottom="8dp"
    android:background="#FFF3E0">
    <TextView
        android:id="@+id/timer_notification_label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="タイマー"
        android:textColor="#5D4037"
        android:textStyle="bold"
        android:textSize="13sp" />
    <Chronometer
        android:id="@+id/timer_notification_chronometer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:fontFamily="monospace"
        android:textColor="#111111"
        android:textStyle="bold"
        android:textSize="36sp" />
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:text="残り時間  ·  タップしてタイマーを開く"
        android:textColor="#6D4C41"
        android:textSize="11sp" />
</LinearLayout>
''',encoding="utf-8")

(res/"layout"/"notification_timer_running_big.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/timer_notification_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingLeft="18dp"
    android:paddingTop="14dp"
    android:paddingRight="18dp"
    android:paddingBottom="14dp"
    android:background="#FFF3E0">
    <TextView
        android:id="@+id/timer_notification_label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="タイマー"
        android:textColor="#5D4037"
        android:textStyle="bold"
        android:textSize="15sp" />
    <Chronometer
        android:id="@+id/timer_notification_chronometer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:fontFamily="monospace"
        android:textColor="#111111"
        android:textStyle="bold"
        android:textSize="48sp" />
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:text="残り時間"
        android:textColor="#6D4C41"
        android:textStyle="bold"
        android:textSize="13sp" />
</LinearLayout>
''',encoding="utf-8")

p=java/"TimerReceiver.java"
s=p.read_text(encoding="utf-8")
s=s.replace('import android.os.Build;','import android.os.Build;\nimport android.os.SystemClock;\nimport android.widget.RemoteViews;',1)
old='''    public static void showRunning(Context c,TimerStore.Entry e){if(e==null||!e.running)return;ensureRunningChannel(c);String label=e.label==null||e.label.isEmpty()?"タイマー":e.label;Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,RUN_CHANNEL):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label).setContentText("タイマー実行中").setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(openTimer(c,e.id)).setOnlyAlertOnce(true).setOngoing(true).setWhen(e.endMs).setUsesChronometer(true);if(Build.VERSION.SDK_INT>=24)b.setChronometerCountDown(true);b.setPriority(Notification.PRIORITY_DEFAULT);NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)try{nm.notify(runningId(e.id),b.build());}catch(SecurityException ignored){}}'''
new='''    public static void showRunning(Context c,TimerStore.Entry e){
        if(e==null||!e.running)return;
        ensureRunningChannel(c);
        String label=e.label==null||e.label.isEmpty()?"タイマー":e.label;
        long remaining=Math.max(0L,e.endMs-System.currentTimeMillis());
        long chronoBase=SystemClock.elapsedRealtime()+remaining;
        PendingIntent open=openTimer(c,e.id);

        RemoteViews compact=new RemoteViews(c.getPackageName(),R.layout.notification_timer_running);
        compact.setTextViewText(R.id.timer_notification_label,label);
        compact.setChronometer(R.id.timer_notification_chronometer,chronoBase,null,true);
        if(Build.VERSION.SDK_INT>=24)compact.setChronometerCountDown(R.id.timer_notification_chronometer,true);
        compact.setOnClickPendingIntent(R.id.timer_notification_root,open);

        RemoteViews big=new RemoteViews(c.getPackageName(),R.layout.notification_timer_running_big);
        big.setTextViewText(R.id.timer_notification_label,label);
        big.setChronometer(R.id.timer_notification_chronometer,chronoBase,null,true);
        if(Build.VERSION.SDK_INT>=24)big.setChronometerCountDown(R.id.timer_notification_chronometer,true);
        big.setOnClickPendingIntent(R.id.timer_notification_root,open);

        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,RUN_CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
         .setContentTitle(label).setContentText("残り時間")
         .setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PUBLIC)
         .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
         .setWhen(e.endMs).setUsesChronometer(true)
         .setCustomContentView(compact).setCustomBigContentView(big)
         .setPriority(Notification.PRIORITY_DEFAULT);
        if(Build.VERSION.SDK_INT>=24){b.setChronometerCountDown(true);b.setStyle(new Notification.DecoratedCustomViewStyle());}
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)try{nm.notify(runningId(e.id),b.build());}catch(SecurityException ignored){}
    }'''
if old not in s: raise SystemExit("TimerReceiver showRunning block not found")
s=s.replace(old,new,1)
p.write_text(s,encoding="utf-8")

# --- Full-screen clock face that can switch digital / analog ---
(java/"ClockFaceActivity.java").write_text(r'''package jp.wakeguard.alarm;

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
    private TextView digital,detail; private AnalogFace face; private Button toggle;
    private boolean analog=false;
    private final Runnable tick=new Runnable(){@Override public void run(){update();handler.postDelayed(this,"stopwatch".equals(mode)?50L:200L);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this);
        Intent i=getIntent(); if(i!=null){String m=i.getStringExtra("clock_mode");if(m!=null)mode=m;String z=i.getStringExtra("zone");if(z!=null)zoneId=z;timerId=i.getLongExtra("timer_id",-1L);}
        build(); update();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(tick);}
    @Override public void onBackPressed(){Ui.finishNoAnimation(this);}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,20));
        LinearLayout top=Ui.row(this);Button back=Ui.ghostButton(this,"‹");back.setTextSize(28);back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)));
        TextView title=Ui.title(this,title(),24);title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        toggle=Ui.ghostButton(this,"アナログ");toggle.setOnClickListener(v->{analog=!analog;applyMode();});top.addView(toggle,new LinearLayout.LayoutParams(Ui.dp(this,100),Ui.dp(this,52)));root.addView(top);
        detail=Ui.text(this,"",13,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,6));root.addView(detail);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        digital=Ui.text(this,"--:--:--",55,Ui.TEXT);digital.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);digital.setGravity(Gravity.CENTER);stage.addView(digital,new FrameLayout.LayoutParams(-1,-1));
        face=new AnalogFace(this);stage.addView(face,new FrameLayout.LayoutParams(-1,-1));
        TextView hint=Ui.text(this,"デジタル / アナログは右上で切り替え",12,Ui.MUTED);hint.setGravity(Gravity.CENTER);hint.setPadding(0,Ui.dp(this,8),0,0);root.addView(hint);
        setContentView(root);Ui.applySystemBarInsets(this,root);applyMode();
    }
    private String title(){return "stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"時計";}
    private void applyMode(){digital.setVisibility(analog?View.GONE:View.VISIBLE);face.setVisibility(analog?View.VISIBLE:View.GONE);toggle.setText(analog?"デジタル":"アナログ");}
    private android.content.SharedPreferences prefs(){return getSharedPreferences("clock_tools",MODE_PRIVATE);}
    private long stopwatchElapsed(){android.content.SharedPreferences p=prefs();long saved=p.getLong("sw_elapsed",0L);if(!p.getBoolean("sw_running",false))return saved;long base=p.getLong("sw_base",SystemClock.elapsedRealtime());return saved+Math.max(0L,SystemClock.elapsedRealtime()-base);}
    private String fmtStopwatch(long ms){long h=ms/3600000L,m=(ms/60000L)%60,s=(ms/1000L)%60,cs=(ms/10L)%100;return String.format(Locale.US,"%02d:%02d:%02d.%02d",h,m,s,cs);}
    private String fmtTimer(long ms){long total=Math.max(0L,ms)/1000L,h=total/3600L,m=(total/60L)%60,s=total%60L;return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);}
    private void update(){
        try{
            if("stopwatch".equals(mode)){
                long ms=stopwatchElapsed();digital.setText(fmtStopwatch(ms));detail.setText("経過時間");
                double sec=(ms/1000d)%60d,min=(ms/60000d)%60d,hr=(ms/3600000d)%12d;face.setHands(hr,min,sec);
            }else if("timer".equals(mode)){
                TimerStore.Entry e=TimerStore.find(this,timerId);long ms=e==null?0L:e.remaining();digital.setText(fmtTimer(ms));detail.setText(e==null?"タイマー":((e.label==null||e.label.isEmpty())?"残り時間":e.label+"  ·  残り時間"));
                double sec=(ms/1000d)%60d,min=(ms/60000d)%60d,hr=(ms/3600000d)%12d;face.setHands(hr,min,sec);
            }else{
                ZoneId z=(zoneId==null||zoneId.isEmpty())?ZoneId.systemDefault():ZoneId.of(zoneId);ZonedDateTime now=ZonedDateTime.now(z);boolean h24=prefs().getBoolean("use_24h",true);
                digital.setText(now.format(DateTimeFormatter.ofPattern(h24?"HH:mm:ss":"hh:mm:ss a",Locale.JAPAN)));detail.setText(now.format(DateTimeFormatter.ofPattern("yyyy年M月d日 (E)",Locale.JAPAN))+"  ·  "+z.getId());
                face.setHands(now.getHour()%12+now.getMinute()/60d,now.getMinute()+now.getSecond()/60d,now.getSecond()+now.getNano()/1_000_000_000d);
            }
        }catch(Throwable ignored){}
    }

    public static class AnalogFace extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private double h,m,s;
        public AnalogFace(Context c){super(c);p.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(Ui.BG);}
        public void setHands(double hh,double mm,double ss){h=hh;m=mm;s=ss;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),hh=getHeight(),cx=w/2f,cy=hh/2f,r=Math.min(w,hh)*0.39f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),3));p.setColor(Ui.MUTED_2);c.drawCircle(cx,cy,r,p);
            for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float len=i%5==0?r*0.11f:r*0.055f;p.setStrokeWidth(i%5==0?Ui.dp(getContext(),3):Ui.dp(getContext(),1));p.setColor(i%5==0?Ui.TEXT:Ui.MUTED_2);c.drawLine(cx+(float)Math.cos(a)*(r-len),cy+(float)Math.sin(a)*(r-len),cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,p);}
            hand(c,cx,cy,r*0.48,h*30,Ui.dp(getContext(),7),Ui.TEXT);hand(c,cx,cy,r*0.68,m*6,Ui.dp(getContext(),5),Ui.TEXT);hand(c,cx,cy,r*0.78,s*6,Ui.dp(getContext(),2),Ui.ACCENT);
            p.setStyle(Paint.Style.FILL);p.setColor(Ui.ACCENT);c.drawCircle(cx,cy,Ui.dp(getContext(),6),p);
        }
        private void hand(Canvas c,float cx,float cy,float len,double deg,float stroke,int color){double a=Math.toRadians(deg-90);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(color);c.drawLine(cx,cy,cx+(float)Math.cos(a)*len,cy+(float)Math.sin(a)*len,p);}
    }
}
''',encoding="utf-8")

# Add activity to manifest.
p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text(encoding="utf-8")
anchor='<activity android:name=".StatsActivity" android:exported="false" />'
if anchor not in s: raise SystemExit("StatsActivity manifest anchor not found")
s=s.replace(anchor,anchor+'\n        <activity android:name=".ClockFaceActivity" android:exported="false" />',1)
p.write_text(s,encoding="utf-8")

# ClockActivity: one-tap full clock view from world clock, stopwatch and timer displays.
p=java/"ClockActivity.java"
s=p.read_text(encoding="utf-8")
# Fix bottom navigation truncation.
s=s.replace('stopwatchTab=Ui.bottomTab(this,"ストップウォッチ","stopwatch".equals(mode));','stopwatchTab=Ui.bottomTab(this,"ストップウォッチ","stopwatch".equals(mode)); stopwatchTab.setTextSize(10f); stopwatchTab.setPadding(0,0,0,0);',1)
# Local world clock tap.
s=s.replace('body.addView(localClock,new LinearLayout.LayoutParams(-1,-2));','localClock.setClickable(true); localClock.setOnClickListener(v->openClockFace("world",ZoneId.systemDefault().getId(),-1L));\n        body.addView(localClock,new LinearLayout.LayoutParams(-1,-2));',1)
# Registered world card tap.
s=s.replace('TextView t=text("--:--:--",33,Ui.TEXT); t.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); row.addView(t,Ui.gapTop(this,3));','TextView t=text("--:--:--",33,Ui.TEXT); t.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); t.setClickable(true); t.setOnClickListener(v->openClockFace("world",zoneId,-1L)); row.addView(t,Ui.gapTop(this,3));',1)
# Stopwatch display tap.
s=s.replace('stopwatchDisplay.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); stopwatchDisplay.setGravity(Gravity.CENTER); stopwatchDisplay.setPadding(0,Ui.dp(this,34),0,Ui.dp(this,28)); body.addView(stopwatchDisplay);','stopwatchDisplay.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); stopwatchDisplay.setGravity(Gravity.CENTER); stopwatchDisplay.setPadding(0,Ui.dp(this,34),0,Ui.dp(this,28)); stopwatchDisplay.setClickable(true); stopwatchDisplay.setOnClickListener(v->openClockFace("stopwatch",null,-1L)); body.addView(stopwatchDisplay);',1)
# Timer remaining tap.
s=s.replace('TextView remain=text(formatTimer(e.remaining()),38,Ui.TEXT);remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);remain.setTag("timer_remaining_"+e.id);row.addView(remain,Ui.gapTop(this,2));','TextView remain=text(formatTimer(e.remaining()),38,Ui.TEXT);remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);remain.setTag("timer_remaining_"+e.id);remain.setClickable(true);remain.setOnClickListener(v->openClockFace("timer",null,e.id));row.addView(remain,Ui.gapTop(this,2));',1)
# Helper method before showMode.
anchor='    private void showMode(String next) {'
helper='''    private void openClockFace(String clockMode,String zone,long timerId){\n        Intent i=new Intent(this,ClockFaceActivity.class).putExtra("clock_mode",clockMode);\n        if(zone!=null)i.putExtra("zone",zone); if(timerId>=0)i.putExtra("timer_id",timerId);\n        startActivity(i); overridePendingTransition(0,0);\n    }\n\n'''
if anchor not in s: raise SystemExit("showMode anchor not found")
s=s.replace(anchor,helper+anchor,1)
p.write_text(s,encoding="utf-8")

# Version bump.
p=root/"app/build.gradle.kts"
s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode = \d+','versionCode = 38',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.1.9"',s)
p.write_text(s,encoding="utf-8")

print("WakeGuard v1.1.9: large timer notification + tap-to-open digital/analog clock face + stopwatch nav label fix")
