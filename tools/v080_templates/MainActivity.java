package jp.wakeguard.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView liveClock,nextTime,nextDetail,streak,diagnostic;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){refreshLive();handler.postDelayed(this,1000);}};

    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.statusBar(this);AlarmService.ensureNotificationChannel(this);buildUi();requestRuntimePermissions();AlarmScheduler.reschedule(this);}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(ticker);handler.post(ticker);refreshDashboard();}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}

    private void buildUi(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(Ui.BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,44));sv.addView(root);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.title(this,"WakeGuard",30));
        TextView sub=Ui.text(this,"起こすだけじゃなく、起きるまで。",14,Ui.MUTED);names.addView(sub);
        head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=Ui.button(this,"⚙",false);settings.setMinWidth(Ui.dp(this,54));settings.setOnClickListener(v->startActivity(new Intent(this,SystemSettingsActivity.class)));head.addView(settings);
        root.addView(head);

        liveClock=Ui.title(this,"--:--:--",48);liveClock.setGravity(Gravity.CENTER);liveClock.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);
        root.addView(liveClock,Ui.gapTop(this,20));

        LinearLayout nextCard=Ui.card(this);root.addView(nextCard,Ui.gapTop(this,18));
        TextView cap=Ui.text(this,"NEXT ALARM",12,Ui.ACCENT);cap.setTypeface(null,android.graphics.Typeface.BOLD);nextCard.addView(cap);
        nextTime=Ui.title(this,"--:--",42);nextTime.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);nextCard.addView(nextTime);
        nextDetail=Ui.text(this,"アラームなし",15,Ui.MUTED);nextCard.addView(nextDetail);
        nextCard.setOnClickListener(v->startActivity(new Intent(this,MultiAlarmActivity.class)));

        TextView toolsTitle=Ui.title(this,"時計ツール",19);root.addView(toolsTitle,Ui.gapTop(this,22));
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        Button alarms=nav("⏰\nアラーム");Button world=nav("🌍\n世界時計");row1.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,88),1));LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,Ui.dp(this,88),1);wp.setMargins(Ui.dp(this,10),0,0,0);row1.addView(world,wp);root.addView(row1,Ui.gapTop(this,10));
        LinearLayout row2=new LinearLayout(this);Button sw=nav("⏱\nストップウォッチ");Button timer=nav("⏲\nタイマー");row2.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,88),1));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,Ui.dp(this,88),1);tp.setMargins(Ui.dp(this,10),0,0,0);row2.addView(timer,tp);root.addView(row2,Ui.gapTop(this,10));
        alarms.setOnClickListener(v->startActivity(new Intent(this,MultiAlarmActivity.class)));world.setOnClickListener(v->openClock("world"));sw.setOnClickListener(v->openClock("stopwatch"));timer.setOnClickListener(v->openClock("timer"));

        LinearLayout stats=Ui.card(this);root.addView(stats,Ui.gapTop(this,22));
        LinearLayout sr=new LinearLayout(this);sr.setGravity(Gravity.CENTER_VERTICAL);TextView st=Ui.title(this,"起床ストリーク",19);sr.addView(st,new LinearLayout.LayoutParams(0,-2,1));streak=Ui.title(this,"0日",28);streak.setTextColor(Ui.ACCENT);sr.addView(streak);stats.addView(sr);
        TextView statsSub=Ui.text(this,"成功した本番アラームだけ記録。ベストと累計もここに集約。",13,Ui.MUTED);statsSub.setPadding(0,Ui.dp(this,6),0,0);stats.addView(statsSub);

        Button add=Ui.button(this,"＋ 新しいアラームを作る",true);add.setOnClickListener(v->startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",-1L)));root.addView(add,Ui.gapTop(this,18));
        Button device=Ui.button(this,"端末設定・権限を確認",false);device.setOnClickListener(v->startActivity(new Intent(this,SystemSettingsActivity.class)));root.addView(device,Ui.gapTop(this,10));

        diagnostic=Ui.text(this,"",13,Ui.DANGER);diagnostic.setPadding(Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8),0);root.addView(diagnostic);
        TextView offline=Ui.text(this,"広告なし・アカウントなし・インターネット権限なし",12,Ui.MUTED);offline.setGravity(Gravity.CENTER);root.addView(offline,Ui.gapTop(this,24));
        setContentView(sv);
    }

    private Button nav(String text){Button b=Ui.button(this,text,false);b.setTextSize(16);return b;}
    private void openClock(String mode){startActivity(new Intent(this,ClockActivity.class).putExtra("mode",mode));}

    private void refreshLive(){liveClock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));refreshNext();}
    private void refreshDashboard(){
        refreshNext();int cur=StreakTracker.displayCurrent(this);streak.setText(cur+"日");
        String err=Prefs.lastFatalError(this);if(err==null||err.isEmpty())err=Prefs.lastAlarmError(this);diagnostic.setText(err==null||err.isEmpty()?"":"前回のエラー: "+err);
    }
    private void refreshNext(){
        long n=AlarmScheduler.nextTriggerMillis(this);if(n<=0){nextTime.setText("OFF");nextDetail.setText("有効なアラームはありません");return;}
        ZonedDateTime z=Instant.ofEpochMilli(n).atZone(ZoneId.systemDefault());nextTime.setText(z.format(DateTimeFormatter.ofPattern("HH:mm")));
        AlarmStore.Entry best=null;long bestMs=Long.MAX_VALUE;for(AlarmStore.Entry e:AlarmProfiles.all(this)){long x=nextFor(e);if(x>0&&x<bestMs){bestMs=x;best=e;}}
        if(best!=null)nextDetail.setText(AlarmProfiles.label(this,best.id)+"  •  "+best.steps+"歩  •  音量"+best.volume+"%  •  "+repeatText(best.dayMask));
        else nextDetail.setText(z.format(DateTimeFormatter.ofPattern("M/d (E)",Locale.JAPAN)));
    }
    private long nextFor(AlarmStore.Entry e){if(!e.enabled)return -1;ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.now(zone);Instant now=Instant.now();for(int i=0;i<120;i++){LocalDate d=today.plusDays(i);if(e.dayMask!=0&&((e.dayMask&(1<<(d.getDayOfWeek().getValue()-1)))==0))continue;Instant x=d.atTime(e.hour,e.minute).atZone(zone).toInstant();if(x.isAfter(now))return x.toEpochMilli();if(e.dayMask==0&&i>0)break;}return -1;}
    private String repeatText(int m){if(m==0)return"1回";if((m&127)==127)return"毎日";String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0)b.append(n[i]);return b.toString();}

    private void requestRuntimePermissions(){
        try{java.util.ArrayList<String> req=new java.util.ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}
    }
}
