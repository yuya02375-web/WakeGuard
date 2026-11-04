package jp.wakeguard.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout list, nextCard;
    private TextView nextText,nextMeta,statsText;
    private final Handler countdownHandler=new Handler(Looper.getMainLooper());
    private final Runnable countdownTick=new Runnable(){@Override public void run(){render();countdownHandler.postDelayed(this,30000L);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); AlarmService.ensureNotificationChannel(this);
        buildUi(); requestRuntimePermissions(); AlarmScheduler.reschedule(this);
    }
    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);render();countdownHandler.removeCallbacks(countdownTick);countdownHandler.postDelayed(countdownTick,30000L);}
    @Override protected void onPause(){countdownHandler.removeCallbacks(countdownTick);super.onPause();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,14));
        top.addView(Ui.overline(this,"IGNIDO  /  WAKE"));
        LinearLayout titleRow=Ui.row(this);titleRow.setPadding(0,Ui.dp(this,5),0,0);
        TextView title=Ui.title(this,"アラーム",30);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton settings=Ui.iconButton(this,R.drawable.ic_v292_action_settings,false,"アプリ設定");settings.setOnClickListener(v->startActivity(new Intent(this,SystemSettingsActivity.class)));
        ImageButton add=Ui.iconButton(this,R.drawable.ic_v292_action_add,true,"アラームを追加");add.setOnClickListener(v->edit(-1));titleRow.addView(add);
        titleRow.addView(settings);top.addView(titleRow);root.addView(top);

        nextCard=new LinearLayout(this);nextCard.setOrientation(LinearLayout.HORIZONTAL);nextCard.setGravity(Gravity.CENTER_VERTICAL);
        nextCard.setPadding(Ui.dp(this,22),Ui.dp(this,19),Ui.dp(this,18),Ui.dp(this,19));
        nextCard.setBackground(Ui.gradientRound(this,26,0xFF2B465D,0xFF1B3043));
        LinearLayout nextCopy=new LinearLayout(this);nextCopy.setOrientation(LinearLayout.VERTICAL);
        TextView nextLabel=Ui.overline(this,"次のアラーム");nextLabel.setTextColor(Ui.ACCENT);nextCopy.addView(nextLabel);
        nextText=Ui.title(this,"--:--",46);nextText.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        nextText.setPadding(0,Ui.dp(this,3),0,0);nextCopy.addView(nextText);
        nextMeta=Ui.text(this,"",13,Ui.MUTED);nextMeta.setPadding(0,Ui.dp(this,2),0,0);nextCopy.addView(nextMeta);
        nextCard.addView(nextCopy,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.ImageView mark=new android.widget.ImageView(this);mark.setImageResource(R.drawable.ic_ignido_v299_foreground);mark.setContentDescription(null);mark.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);mark.setAlpha(.95f);
        nextCard.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,70),Ui.dp(this,70)));
        LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(-1,-2);nextLp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,12));root.addView(nextCard,nextLp);

        LinearLayout statsRow=Ui.row(this);statsRow.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,18,this));
        statsRow.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        TextView statsLabel=Ui.title(this,"ストリーク",15);
        statsRow.addView(statsLabel,new LinearLayout.LayoutParams(0,-2,1));
        statsText=Ui.text(this,"",13,Ui.MUTED);
        statsRow.addView(statsText);
        TextView statsArrow=Ui.text(this,"  ›",22,Ui.ACCENT);
        statsRow.addView(statsArrow);
        statsRow.setContentDescription("ストリークの記録を見る");
        statsRow.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));
        LinearLayout.LayoutParams statsLp=new LinearLayout.LayoutParams(-1,-2);statsLp.setMargins(Ui.dp(this,18),0,Ui.dp(this,18),Ui.dp(this,5));root.addView(statsRow,statsLp);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setVerticalScrollBarEnabled(false);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,28));sv.addView(list);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        root.addView(bottomNav());
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,9),Ui.dp(this,7),Ui.dp(this,9),Ui.dp(this,8));nav.setBackgroundColor(Ui.SURFACE);
        Button alarms=Ui.bottomTab(this,"アラーム",true);Button world=Ui.bottomTab(this,"時計",false);Button timer=Ui.bottomTab(this,"タイマー",false);Button sw=Ui.bottomTab(this,"ストップウォッチ",false);Button streak=Ui.bottomTab(this,"ストリーク",false);
        nav.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(world,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(timer,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(sw,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(streak,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        world.setOnClickListener(v->openClock("world"));timer.setOnClickListener(v->openClock("timer"));sw.setOnClickListener(v->openClock("stopwatch"));streak.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));return nav;
    }

    private void openClock(String mode){
        Ui.launchNoAnimation(this, new Intent(this,ClockActivity.class).putExtra("mode",mode));
    }
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}

    private void render(){
        list.removeAllViews();
        list.setGravity(Gravity.TOP);
        list.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,28));
        refreshNext();
        refreshStats();
        List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        if(alarms.isEmpty()){
            list.setGravity(Gravity.CENTER_VERTICAL);
            list.setPadding(Ui.dp(this,18),Ui.dp(this,4),Ui.dp(this,18),Ui.dp(this,12));
            LinearLayout empty=Ui.card(this);empty.setGravity(Gravity.CENTER);empty.setPadding(Ui.dp(this,24),Ui.dp(this,26),Ui.dp(this,24),Ui.dp(this,26));
            android.widget.ImageView illustration=new android.widget.ImageView(this);illustration.setImageResource(R.drawable.ic_ignido_v299_foreground);illustration.setContentDescription(null);
            LinearLayout.LayoutParams illustrationLp=new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,68));illustrationLp.bottomMargin=Ui.dp(this,6);empty.addView(illustration,illustrationLp);
            TextView large=Ui.title(this,"アラームがありません",22);large.setGravity(Gravity.CENTER);empty.addView(large);
            TextView note=Ui.text(this,"時間と解除方法を設定して、最初のアラームを作成しましょう。",14,Ui.MUTED);
            note.setGravity(Gravity.CENTER);note.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,12));empty.addView(note);
            Button create=Ui.button(this,"アラームを追加",true);create.setOnClickListener(v->edit(-1));empty.addView(create);
            list.addView(empty);return;
        }
        LinearLayout listHeading=Ui.row(this);listHeading.setPadding(Ui.dp(this,2),Ui.dp(this,9),Ui.dp(this,2),Ui.dp(this,8));
        TextView listTitle=Ui.sectionHeader(this,"アラーム一覧");listHeading.addView(listTitle,new LinearLayout.LayoutParams(0,-2,1));
        listHeading.addView(Ui.pill(this,String.valueOf(alarms.size())));list.addView(listHeading);
        Map<Long,Long> snoozes=SnoozeScheduler.active(this);
        if(snoozes.size()>1){
            LinearLayout allSnoozes=Ui.row(this);allSnoozes.setGravity(Gravity.CENTER_VERTICAL);allSnoozes.setPadding(Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8));
            TextView count=Ui.text(this,"スヌーズ中  "+snoozes.size()+"件",14,Ui.TEXT);allSnoozes.addView(count,new LinearLayout.LayoutParams(0,-2,1));
            Button cancelAll=Ui.ghostButton(this,"すべて取消");cancelAll.setTextColor(Ui.ACCENT_2);cancelAll.setOnClickListener(v->{SnoozeScheduler.cancelAll(this);render();});allSnoozes.addView(cancelAll);
            list.addView(allSnoozes);list.addView(Ui.divider(this));
        }
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));}
    }

    private void addAlarmRow(AlarmStore.Entry e){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,18));row.setBackground(Ui.burnCard(this,e.enabled));if(Build.VERSION.SDK_INT>=21)row.setElevation(0);row.setAlpha(1f);row.setOnClickListener(v->edit(e.id));if(e.id>=1000)row.setOnLongClickListener(v->{confirmDelete(e);return true;});
        LinearLayout first=new LinearLayout(this);first.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),42);time.setTextColor(e.enabled?Ui.ACCENT:Ui.MUTED);time.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));first.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        if(e.id>=1000){
            Button del=Ui.ghostButton(this,"削除");del.setTextSize(12);del.setTextColor(Ui.DANGER);del.setContentDescription(I18n.tr(this,"アラームを削除"));del.setOnClickListener(v->confirmDelete(e));first.addView(del);
        }
        Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);first.addView(on);row.addView(first);
        String label=(e.label==null||e.label.trim().isEmpty())?"アラーム":e.label.trim();TextView name=Ui.title(this,label,17);name.setTextColor(e.enabled?Ui.TEXT:Ui.MUTED);row.addView(name,Ui.gapTop(this,5));
        String scheduleText=repeatText(e.dayMask);long now=System.currentTimeMillis();long next=AlarmScheduler.nextTriggerMillis(this,e);String remaining=(next>now)?"あと "+relativeText(next-now):"";
        LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER_VERTICAL);chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(Ui.accentPill(this,scheduleText));
        if(!remaining.isEmpty()){View gap=new View(this);chips.addView(gap,new LinearLayout.LayoutParams(Ui.dp(this,7),1));chips.addView(Ui.pill(this,remaining));}
        row.addView(chips,Ui.gapTop(this,9));
        String missionText=I18n.tr(this,AlarmProfiles.missionName(e.missionType))+"  ·  "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id));
        TextView detail=Ui.text(this,missionText,13,Ui.MUTED);detail.setMaxLines(2);row.addView(detail,Ui.gapTop(this,8));
        long snoozeAt=SnoozeScheduler.scheduledAt(this,e.id);
        if(snoozeAt>now){
            LinearLayout snoozeRow=new LinearLayout(this);snoozeRow.setGravity(Gravity.CENTER_VERTICAL);snoozeRow.setPadding(0,Ui.dp(this,10),0,0);snoozeRow.setOnClickListener(v->{});
            TextView snoozeState=Ui.text(this,"スヌーズ中  ·  あと "+relativeText(snoozeAt-now),13,Ui.ACCENT_2);snoozeRow.addView(snoozeState,new LinearLayout.LayoutParams(0,-2,1));
            Button awake=Ui.ghostButton(this,"起きた");awake.setTextSize(12);awake.setTextColor(Ui.SUCCESS);awake.setOnClickListener(v->{SnoozeScheduler.markAwake(this,e.id);render();});snoozeRow.addView(awake);
            Button cancel=Ui.ghostButton(this,"取消");cancel.setTextSize(12);cancel.setTextColor(Ui.ACCENT_2);cancel.setOnClickListener(v->{SnoozeScheduler.cancel(this,e.id);render();});snoozeRow.addView(cancel);
            row.addView(snoozeRow);
        }
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        list.addView(row,Ui.cardParams(this));
    }

    private void confirmDelete(AlarmStore.Entry e){
        if(e==null||e.id<1000)return;
        new AlertDialog.Builder(this)
                .setTitle(I18n.tr(this,"アラームを削除"))
                .setMessage(I18n.tr(this,"このアラームだけ削除します。"))
                .setNegativeButton(I18n.tr(this,"キャンセル"),null)
                .setPositiveButton(I18n.tr(this,"削除"),(d,w)->{
                    AlarmScheduler.cancelExtraAlarm(this,e.id);
                    AlarmProfiles.delete(this,e.id);
                    AlarmScheduler.reschedule(this);
                    render();
                }).show();
    }

    private void refreshNext(){long regular=AlarmScheduler.nextTriggerMillis(this),snooze=SnoozeScheduler.nextAt(this);long ms=regular;if(snooze>0&&(ms<=0||snooze<ms))ms=snooze;if(ms<=0){nextCard.setVisibility(android.view.View.GONE);return;}nextCard.setVisibility(android.view.View.VISIBLE);ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());nextText.setText(z.format(DateTimeFormatter.ofPattern("HH:mm",I18n.locale(this))));String when=z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this)));String prefix=(snooze>0&&ms==snooze)?"スヌーズ中  ·  ":"";nextMeta.setText(I18n.tr(this,when+"  ·  "+prefix+"あと "+relativeText(ms-System.currentTimeMillis())));}
    private void refreshStats(){if(statsText!=null)statsText.setText(I18n.tr(this,StreakTracker.displayCurrent(this)+"日  ·  最高 "+Prefs.bestStreak(this)+"日"));}
    private String relativeText(long ms){long mins=Math.max(1,(long)Math.ceil(ms/60000.0));long h=mins/60,m=mins%60;if(h==0)return m+"分";if(m==0)return h+"時間";return h+"時間"+m+"分";}
    private String repeatText(int m){if(m==0)return I18n.tr(this,"1回のみ");if((m&127)==127)return I18n.tr(this,"毎日");String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(I18n.tr(this,n[i]));}return b.toString();}

    private void requestRuntimePermissions(){try{ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}}
}
