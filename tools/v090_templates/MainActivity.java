package jp.wakeguard.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView liveClock,liveDate,nextTime,nextDetail,streak,status;
    private final Runnable ticker=new Runnable(){public void run(){refreshLive();handler.postDelayed(this,1000);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.statusBar(this); AlarmService.ensureNotificationChannel(this);
        Prefs.lastFatalError(this,""); Prefs.lastAlarmError(this,"");
        buildUi(); requestRuntimePermissions(); AlarmScheduler.reschedule(this);
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(ticker);handler.post(ticker);refreshDashboard();}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}

    private void buildUi(){
        ScrollView sv=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,48));root.setBackground(Ui.screenGradient(this));sv.addView(root);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ui.overline(this,"WAKEGUARD // CORE"));
        names.addView(Ui.title(this,"WakeGuard",31));
        TextView sub=Ui.text(this,"起きるまで止めない、起床プロトコル。",13,Ui.MUTED);names.addView(sub);
        head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=Ui.ghostButton(this,"⚙");settings.setMinWidth(Ui.dp(this,54));settings.setOnClickListener(v->startActivity(new Intent(this,SystemSettingsActivity.class)));head.addView(settings);root.addView(head);

        LinearLayout hero=Ui.glowCard(this);hero.setGravity(Gravity.CENTER_HORIZONTAL);root.addView(hero,Ui.gapTop(this,20));
        liveClock=Ui.title(this,"--:--:--",50);liveClock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);liveClock.setGravity(Gravity.CENTER);hero.addView(liveClock);
        liveDate=Ui.text(this,"",14,Ui.MUTED);liveDate.setGravity(Gravity.CENTER);hero.addView(liveDate,Ui.gapTop(this,4));
        status=Ui.text(this,"● SYSTEM READY",11,Ui.SUCCESS);status.setTypeface(null,Typeface.BOLD);status.setGravity(Gravity.CENTER);hero.addView(status,Ui.gapTop(this,14));

        TextView nextLabel=Ui.overline(this,"NEXT WAKE EVENT");root.addView(nextLabel,Ui.gapTop(this,22));
        LinearLayout nextCard=Ui.card(this);nextCard.setOnClickListener(v->startActivity(new Intent(this,MultiAlarmActivity.class)));root.addView(nextCard,Ui.gapTop(this,8));
        LinearLayout nextRow=new LinearLayout(this);nextRow.setGravity(Gravity.CENTER_VERTICAL);nextTime=Ui.title(this,"--:--",44);nextTime.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);nextRow.addView(nextTime,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=Ui.title(this,"›",32);arrow.setTextColor(Ui.CYAN);nextRow.addView(arrow);nextCard.addView(nextRow);
        nextDetail=Ui.text(this,"アラームなし",14,Ui.MUTED);nextCard.addView(nextDetail,Ui.gapTop(this,4));

        TextView tools=Ui.overline(this,"TOOLS");root.addView(tools,Ui.gapTop(this,22));
        LinearLayout grid1=new LinearLayout(this);Button alarms=tile("⏰","アラーム","個別ミッション");Button world=tile("🌐","世界時計","秒まで表示");grid1.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,106),1));LinearLayout.LayoutParams g=new LinearLayout.LayoutParams(0,Ui.dp(this,106),1);g.setMargins(Ui.dp(this,10),0,0,0);grid1.addView(world,g);root.addView(grid1,Ui.gapTop(this,8));
        LinearLayout grid2=new LinearLayout(this);Button sw=tile("◴","ストップウォッチ","ラップ対応");Button timer=tile("◷","タイマー","通知・振動");grid2.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,106),1));LinearLayout.LayoutParams g2=new LinearLayout.LayoutParams(0,Ui.dp(this,106),1);g2.setMargins(Ui.dp(this,10),0,0,0);grid2.addView(timer,g2);root.addView(grid2,Ui.gapTop(this,10));
        alarms.setOnClickListener(v->startActivity(new Intent(this,MultiAlarmActivity.class)));world.setOnClickListener(v->openClock("world"));sw.setOnClickListener(v->openClock("stopwatch"));timer.setOnClickListener(v->openClock("timer"));

        LinearLayout stat=Ui.card(this);root.addView(stat,Ui.gapTop(this,22));
        LinearLayout sr=new LinearLayout(this);sr.setGravity(Gravity.CENTER_VERTICAL);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.addView(Ui.overline(this,"STREAK"));left.addView(Ui.title(this,"起床ストリーク",18));sr.addView(left,new LinearLayout.LayoutParams(0,-2,1));streak=Ui.title(this,"0日",30);streak.setTextColor(Ui.CYAN);sr.addView(streak);stat.addView(sr);
        TextView statSub=Ui.text(this,"本番ミッション成功だけを記録します。",12,Ui.MUTED);stat.addView(statSub,Ui.gapTop(this,6));

        Button add=Ui.button(this,"＋  新しいアラームを作る",true);add.setOnClickListener(v->startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",-1L)));root.addView(add,Ui.gapTop(this,20));
        TextView offline=Ui.text(this,"OFFLINE • NO ADS • NO ACCOUNT",11,Ui.MUTED_2);offline.setLetterSpacing(0.12f);offline.setGravity(Gravity.CENTER);root.addView(offline,Ui.gapTop(this,26));
        setContentView(sv);
    }

    private Button tile(String icon,String title,String sub){Button b=Ui.ghostButton(this,icon+"\n"+title+"\n"+sub);b.setTextSize(14);b.setGravity(Gravity.CENTER);b.setLines(3);return b;}
    private void openClock(String mode){startActivity(new Intent(this,ClockActivity.class).putExtra("mode",mode));}

    private void refreshLive(){ZonedDateTime now=ZonedDateTime.now();liveClock.setText(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));liveDate.setText(now.format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.JAPAN)));refreshNext();}
    private void refreshDashboard(){refreshNext();streak.setText(StreakTracker.displayCurrent(this)+"日");}
    private void refreshNext(){
        long n=AlarmScheduler.nextTriggerMillis(this);if(n<=0){nextTime.setText("OFF");nextDetail.setText("有効なアラームはありません  •  タップして追加");return;}
        ZonedDateTime z=Instant.ofEpochMilli(n).atZone(ZoneId.systemDefault());nextTime.setText(z.format(DateTimeFormatter.ofPattern("HH:mm")));
        AlarmStore.Entry best=null;long bestMs=Long.MAX_VALUE;for(AlarmStore.Entry e:AlarmProfiles.all(this)){long x=nextFor(e);if(x>0&&x<bestMs){bestMs=x;best=e;}}
        if(best!=null)nextDetail.setText(AlarmProfiles.label(this,best.id)+"  •  "+AlarmProfiles.missionName(best.missionType)+"  "+AlarmProfiles.missionSummary(this,best.id)+"  •  "+repeatText(best.dayMask));
        else nextDetail.setText(z.format(DateTimeFormatter.ofPattern("M/d (E)",Locale.JAPAN)));
    }
    private long nextFor(AlarmStore.Entry e){if(!e.enabled)return -1;ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.now(zone);Instant now=Instant.now();for(int i=0;i<120;i++){LocalDate d=today.plusDays(i);if(e.dayMask!=0&&((e.dayMask&(1<<(d.getDayOfWeek().getValue()-1)))==0))continue;Instant x=d.atTime(e.hour,e.minute).atZone(zone).toInstant();if(x.isAfter(now))return x.toEpochMilli();if(e.dayMask==0&&i>0)break;}return -1;}
    private String repeatText(int m){if(m==0)return"1回";if((m&127)==127)return"毎日";String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0)b.append(n[i]);return b.toString();}

    private void requestRuntimePermissions(){try{ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}}
}
