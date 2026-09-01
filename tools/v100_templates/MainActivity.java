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
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView nextText;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.statusBar(this); AlarmService.ensureNotificationChannel(this);
        buildUi(); requestRuntimePermissions(); AlarmScheduler.reschedule(this);
    }
    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);render();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);

        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,20),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,8));
        TextView title=Ui.title(this,"アラーム",30);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=Ui.ghostButton(this,"設定");settings.setOnClickListener(v->startActivity(new Intent(this,SystemSettingsActivity.class)));top.addView(settings);
        Button add=Ui.ghostButton(this,"＋");add.setTextSize(25);add.setOnClickListener(v->edit(-1));top.addView(add);root.addView(top);

        nextText=Ui.text(this,"",13,Ui.MUTED);nextText.setPadding(Ui.dp(this,22),0,Ui.dp(this,22),Ui.dp(this,10));root.addView(nextText);

        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this,22),Ui.dp(this,4),Ui.dp(this,22),Ui.dp(this,36));sv.addView(list);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        root.addView(Ui.divider(this));
        root.addView(bottomNav());
        setContentView(root);
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),Ui.dp(this,6));
        Button alarms=Ui.bottomTab(this,"アラーム",true);Button world=Ui.bottomTab(this,"世界時計",false);Button timer=Ui.bottomTab(this,"タイマー",false);Button sw=Ui.bottomTab(this,"ストップウォッチ",false);
        nav.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(world,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(timer,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));nav.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));
        world.setOnClickListener(v->openClock("world"));timer.setOnClickListener(v->openClock("timer"));sw.setOnClickListener(v->openClock("stopwatch"));return nav;
    }

    private void openClock(String mode){startActivity(new Intent(this,ClockActivity.class).putExtra("mode",mode));}
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}

    private void render(){
        list.removeAllViews();List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        if(alarms.isEmpty()){
            TextView empty=Ui.text(this,"アラームはありません。右上の＋から追加できます。",15,Ui.MUTED);empty.setPadding(0,Ui.dp(this,40),0,0);list.addView(empty);return;
        }
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));if(i<alarms.size()-1)list.addView(Ui.divider(this));}
        refreshNext();
    }

    private void addAlarmRow(AlarmStore.Entry e){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,18));row.setAlpha(e.enabled?1f:.45f);row.setOnClickListener(v->edit(e.id));
        LinearLayout first=new LinearLayout(this);first.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),43);time.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.NORMAL);first.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        Switch on=new Switch(this);on.setChecked(e.enabled);first.addView(on);row.addView(first);
        String label=(e.label==null||e.label.trim().isEmpty())?"アラーム":e.label.trim();TextView name=Ui.text(this,label,16,Ui.TEXT);row.addView(name,Ui.gapTop(this,3));
        TextView detail=Ui.text(this,repeatText(e.dayMask)+"   ·   "+AlarmProfiles.missionName(e.missionType)+" "+AlarmProfiles.missionSummary(this,e.id),13,Ui.MUTED);row.addView(detail,Ui.gapTop(this,4));
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled&&e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);AlarmScheduler.reschedule(this);render();});
        list.addView(row);
    }

    private void refreshNext(){long ms=AlarmScheduler.nextTriggerMillis(this);if(ms<=0){nextText.setText("次のアラームはありません");return;}ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());nextText.setText("次は "+z.format(DateTimeFormatter.ofPattern("M月d日(E) H:mm",Locale.JAPAN)));}
    private String repeatText(int m){if(m==0)return"1回のみ";if((m&127)==127)return"毎日";String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(n[i]);}return b.toString();}

    private void requestRuntimePermissions(){try{ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}}
}
