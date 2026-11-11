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
    private String builtAppearanceKey;
    private LinearLayout list, nextCard;
    private android.widget.GridLayout tileGrid;
    private TextView nextText,nextMeta,nextStatus;
    private FrameLayout horizonTrack;
    private View horizonDot;
    private final Handler countdownHandler=new Handler(Looper.getMainLooper());
    private final Runnable countdownTick=new Runnable(){@Override public void run(){render();countdownHandler.postDelayed(this,30000L);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); builtAppearanceKey=appearanceKey(); AlarmService.ensureNotificationChannel(this);
        buildUi(); requestRuntimePermissions(); AlarmScheduler.reschedule(this);
    }
    @Override protected void onResume(){
        super.onResume();
        String currentAppearance=appearanceKey();
        if(builtAppearanceKey!=null&&!builtAppearanceKey.equals(currentAppearance)){recreate();return;}
        AlarmScheduler.reschedule(this);
        for(TimerStore.Entry e:TimerStore.all(this))if(e.running&&e.remaining()>1500L)TimerReceiver.schedule(this,e.id,e.endMs);
        render();countdownHandler.removeCallbacks(countdownTick);countdownHandler.postDelayed(countdownTick,30000L);
    }
    @Override protected void onPause(){countdownHandler.removeCallbacks(countdownTick);super.onPause();}

    private String appearanceKey(){
        return UiProfiles.id(this)+":"+VisualDesigns.id(this)+":"+AppThemes.key(this)+":"+MascotCatalog.id(this);
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(Ui.screenGradient(this));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(Ui.dp(this,22),Ui.dp(this,17),Ui.dp(this,22),Ui.dp(this,11));
        top.addView(Ui.overline(this,"IGNIDO  /  WAKE"));
        TextView title=Ui.title(this,"アラーム",32);title.setPadding(0,Ui.dp(this,5),0,0);top.addView(title);root.addView(top);

        // The top of the home screen is now a single glanceable wake-up horizon.
        nextCard=new LinearLayout(this);nextCard.setOrientation(LinearLayout.VERTICAL);
        nextCard.setPadding(Ui.dp(this,22),Ui.dp(this,18),Ui.dp(this,22),Ui.dp(this,17));
        nextCard.setBackground(Ui.gradientRound(this,24,Ui.HERO_START,Ui.HERO_END));
        LinearLayout heroTop=Ui.row(this);heroTop.setPadding(0,0,0,0);
        TextView nextLabel=Ui.overline(this,"次の起床");nextLabel.setTextColor(Ui.HERO_TEXT);heroTop.addView(nextLabel,new LinearLayout.LayoutParams(0,-2,1));
        nextStatus=Ui.text(this,"有効なアラーム  0",12,Ui.HERO_TEXT);heroTop.addView(nextStatus);
        String mascotId=MascotCatalog.id(this);
        if(!"none".equals(mascotId)){
            MascotArtView mascot=new MascotArtView(this,mascotId);
            LinearLayout.LayoutParams mascotLp=new LinearLayout.LayoutParams(Ui.dp(this,46),Ui.dp(this,46));
            mascotLp.setMargins(Ui.dp(this,8),0,0,0);heroTop.addView(mascot,mascotLp);
        }
        nextCard.addView(heroTop);
        nextText=Ui.title(this,"--:--",60);nextText.setTextColor(Ui.HERO_TEXT);
        nextText.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));
        nextText.setIncludeFontPadding(false);nextText.setAutoSizeTextTypeUniformWithConfiguration(34,60,1,android.util.TypedValue.COMPLEX_UNIT_SP);nextText.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,4));nextCard.addView(nextText);
        nextMeta=Ui.text(this,"次のアラームはありません",14,Ui.HERO_TEXT);nextCard.addView(nextMeta);
        horizonTrack=new FrameLayout(this);horizonTrack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View horizonLine=new View(this);horizonLine.setBackgroundColor(Ui.HORIZON_LINE);horizonLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lineLp=new FrameLayout.LayoutParams(-1,Ui.dp(this,1),Gravity.CENTER_VERTICAL);horizonTrack.addView(horizonLine,lineLp);
        horizonDot=new View(this);horizonDot.setBackground(Ui.round(Ui.ACCENT_2,8,this));horizonDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams dotLp=new FrameLayout.LayoutParams(Ui.dp(this,8),Ui.dp(this,8),Gravity.START|Gravity.CENTER_VERTICAL);horizonTrack.addView(horizonDot,dotLp);
        LinearLayout.LayoutParams trackLp=new LinearLayout.LayoutParams(-1,Ui.dp(this,14));trackLp.setMargins(0,Ui.dp(this,7),0,0);nextCard.addView(horizonTrack,trackLp);
        LinearLayout horizonScale=Ui.row(this);TextView startOfDay=Ui.text(this,"00:00",10,Ui.HERO_TEXT);TextView endOfDay=Ui.text(this,"24:00",10,Ui.HERO_TEXT);horizonScale.addView(startOfDay,new LinearLayout.LayoutParams(0,-2,1));endOfDay.setGravity(Gravity.END);horizonScale.addView(endOfDay,new LinearLayout.LayoutParams(0,-2,1));nextCard.addView(horizonScale);
        LinearLayout.LayoutParams nextLp=new LinearLayout.LayoutParams(-1,-2);nextLp.setMargins(Ui.dp(this,18),Ui.dp(this,4),Ui.dp(this,18),Ui.dp(this,3));root.addView(nextCard,nextLp);

        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setVerticalScrollBarEnabled(false);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,16));sv.addView(list);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        Button add=Ui.button(this,"アラームを追加",true);add.setOnClickListener(v->edit(-1));
        LinearLayout action=new LinearLayout(this);action.setPadding(Ui.dp(this,18),Ui.dp(this,5),Ui.dp(this,18),Ui.dp(this,7));action.addView(add,new LinearLayout.LayoutParams(-1,-2));root.addView(action);
        root.addView(bottomNav());
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
    }

    private LinearLayout bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(Ui.dp(this,9),Ui.dp(this,5),Ui.dp(this,9),Ui.dp(this,5));nav.setBackgroundColor(Ui.SURFACE);
        Button alarms=Ui.bottomTab(this,"アラーム",true);Button clock=Ui.bottomTab(this,"時計",false);Button records=Ui.bottomTab(this,"記録",false);Button settings=Ui.bottomTab(this,"設定",false);
        nav.addView(alarms,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(clock,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(records,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));nav.addView(settings,new LinearLayout.LayoutParams(0,Ui.dp(this,63),1));
        clock.setOnClickListener(v->openClock("world"));records.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,StatsActivity.class)));settings.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,SystemSettingsActivity.class)));return nav;
    }

    private void openClock(String mode){
        Ui.launchNoAnimation(this, new Intent(this,ClockActivity.class).putExtra("mode",mode));
    }
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}

    private void render(){
        list.removeAllViews();
        list.setGravity(Gravity.TOP);
        list.setPadding(Ui.dp(this,18),Ui.dp(this,6),Ui.dp(this,18),Ui.dp(this,16));
        refreshNext();
        List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);
        alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));
        if(alarms.isEmpty()){
            LinearLayout empty=Ui.card(this);empty.setOrientation(LinearLayout.HORIZONTAL);empty.setGravity(Gravity.CENTER_VERTICAL);empty.setPadding(Ui.dp(this,18),Ui.dp(this,17),Ui.dp(this,18),Ui.dp(this,17));
            TextView symbol=Ui.title(this,"—",28);symbol.setTextColor(Ui.ACCENT_2);symbol.setGravity(Gravity.CENTER);empty.addView(symbol,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(Ui.dp(this,12),0,0,0);
            TextView large=Ui.title(this,"アラームはまだありません",17);copy.addView(large);
            TextView note=Ui.text(this,"次のアラームはありません",13,Ui.MUTED);note.setPadding(0,Ui.dp(this,4),0,0);copy.addView(note);
            empty.addView(copy,new LinearLayout.LayoutParams(0,-2,1));list.addView(empty,Ui.cardParams(this));return;
        }
        LinearLayout listHeading=Ui.row(this);listHeading.setPadding(Ui.dp(this,2),Ui.dp(this,3),Ui.dp(this,2),Ui.dp(this,4));
        TextView listTitle=Ui.sectionHeader(this,I18n.tr(this,"アラーム一覧"));listHeading.addView(listTitle,new LinearLayout.LayoutParams(0,-2,1));
        listHeading.addView(Ui.pill(this,String.valueOf(alarms.size())));list.addView(listHeading);
        Map<Long,Long> snoozes=SnoozeScheduler.active(this);
        if(snoozes.size()>1){
            LinearLayout allSnoozes=Ui.row(this);allSnoozes.setGravity(Gravity.CENTER_VERTICAL);allSnoozes.setPadding(Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8));
            String countText=String.format(I18n.locale(this),I18n.tr(this,"スヌーズ中  %d件"),snoozes.size());
            TextView count=Ui.text(this,countText,14,Ui.TEXT);allSnoozes.addView(count,new LinearLayout.LayoutParams(0,-2,1));
            Button cancelAll=Ui.ghostButton(this,I18n.tr(this,"すべて取消"));cancelAll.setTextColor(Ui.ACCENT_2);cancelAll.setOnClickListener(v->{SnoozeScheduler.cancelAll(this);render();});allSnoozes.addView(cancelAll);
            list.addView(allSnoozes);list.addView(Ui.divider(this));
        }
        if("tiles".equals(Ui.LAYOUT_MODE)){
            tileGrid=new android.widget.GridLayout(this);tileGrid.setColumnCount(2);tileGrid.setUseDefaultMargins(false);
            tileGrid.setPadding(0,Ui.dp(this,2),0,0);list.addView(tileGrid,new LinearLayout.LayoutParams(-1,-2));
            for(int i=0;i<alarms.size();i++)addAlarmTile(alarms.get(i),i);
            return;
        }
        tileGrid=null;
        for(int i=0;i<alarms.size();i++){addAlarmRow(alarms.get(i));}
    }

    private void addAlarmTile(AlarmStore.Entry e,int index){
        LinearLayout tile=Ui.card(this);tile.setPadding(Ui.dp(this,12),Ui.dp(this,11),Ui.dp(this,12),Ui.dp(this,11));
        tile.setMinimumHeight(Ui.dp(this,154));tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(v->edit(e.id));
        if(e.id>=1000)tile.setOnLongClickListener(v->{confirmDelete(e);return true;});
        LinearLayout head=Ui.row(this);head.setPadding(0,0,0,Ui.dp(this,4));
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),28);
        time.setTextColor(e.enabled?Ui.ACCENT_2:Ui.MUTED_2);time.setIncludeFontPadding(false);head.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);head.addView(on);tile.addView(head);
        String label=(e.label==null||e.label.trim().isEmpty())?I18n.tr(this,"アラーム"):e.label.trim();
        TextView name=Ui.title(this,label,15);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name);
        TextView schedule=Ui.text(this,repeatText(e.dayMask),12,e.enabled?Ui.ACCENT:Ui.MUTED);schedule.setSingleLine(true);schedule.setEllipsize(android.text.TextUtils.TruncateAt.END);schedule.setPadding(0,Ui.dp(this,3),0,0);tile.addView(schedule);
        TextView mission=Ui.text(this,AlarmProfiles.missionIcon(e.missionType)+"  "+I18n.tr(this,AlarmProfiles.missionName(e.missionType)),11,Ui.MUTED);mission.setSingleLine(true);mission.setEllipsize(android.text.TextUtils.TruncateAt.END);mission.setPadding(0,Ui.dp(this,5),0,0);tile.addView(mission);
        long snoozeAt=SnoozeScheduler.scheduledAt(this,e.id);
        if(snoozeAt>System.currentTimeMillis()){
            TextView snooze=Ui.text(this,I18n.tr(this,"スヌーズ中")+"  ·  "+relativeText(snoozeAt-System.currentTimeMillis()),11,Ui.ACCENT_2);
            snooze.setSingleLine(true);snooze.setEllipsize(android.text.TextUtils.TruncateAt.END);snooze.setPadding(0,Ui.dp(this,4),0,0);tile.addView(snooze);
        }
        on.setContentDescription(I18n.tr(this,label)+"  "+I18n.tr(this,"アラーム"));
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(index/2),android.widget.GridLayout.spec(index%2,1f));
        lp.width=0;lp.height=-2;lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,7));tileGrid.addView(tile,lp);
    }

    private void addAlarmRow(AlarmStore.Entry e){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,10),Ui.dp(this,10));row.setBackground(Ui.burnCard(this,e.enabled));if(Build.VERSION.SDK_INT>=21)row.setElevation(0);row.setAlpha(1f);row.setOnClickListener(v->edit(e.id));if(e.id>=1000)row.setOnLongClickListener(v->{confirmDelete(e);return true;});
        LinearLayout first=new LinearLayout(this);first.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),38);time.setTextColor(e.enabled?Ui.ACCENT_2:Ui.MUTED_2);time.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));time.setIncludeFontPadding(false);
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(Ui.dp(this,12),0,Ui.dp(this,6),0);
        String label=(e.label==null||e.label.trim().isEmpty())?"アラーム":e.label.trim();TextView name=Ui.title(this,label,16);name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.BOLD));name.setTextColor(e.enabled?Ui.TEXT:Ui.MUTED);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(name);
        String scheduleText=repeatText(e.dayMask);long now=System.currentTimeMillis();long next=AlarmScheduler.nextTriggerMillis(this,e);String remaining=(e.enabled&&next>now)?String.format(I18n.locale(this),I18n.tr(this,"あと %s"),relativeText(next-now)):"";
        String scheduleLine=scheduleText+(remaining.isEmpty()?"":"  ·  "+remaining);TextView schedule=Ui.text(this,scheduleLine,12,e.enabled?Ui.ACCENT:Ui.MUTED_2);schedule.setSingleLine(true);schedule.setEllipsize(android.text.TextUtils.TruncateAt.END);schedule.setPadding(0,Ui.dp(this,3),0,0);details.addView(schedule);
        first.addView(time,new LinearLayout.LayoutParams(-2,-2));first.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        Switch on=new Switch(this);on.setChecked(e.enabled);Ui.styleSwitch(on,this);first.addView(on);row.addView(first);
        String missionText=I18n.tr(this,AlarmProfiles.missionName(e.missionType))+"  ·  "+I18n.tr(this,AlarmProfiles.missionSummary(this,e.id));
        LinearLayout missionRow=new LinearLayout(this);missionRow.setGravity(Gravity.CENTER_VERTICAL);missionRow.setPadding(Ui.dp(this,4),Ui.dp(this,6),0,0);
        TextView missionIcon=Ui.text(this,AlarmProfiles.missionIcon(e.missionType),12,e.enabled?Ui.ACCENT_2:Ui.MUTED);
        missionIcon.setGravity(Gravity.CENTER);missionIcon.setTypeface(null,android.graphics.Typeface.BOLD);missionIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        missionIcon.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,50,this));
        missionRow.addView(missionIcon,new LinearLayout.LayoutParams(Ui.dp(this,23),Ui.dp(this,23)));
        TextView detail=Ui.text(this,missionText,12,Ui.MUTED);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(0,-2,1);detailLp.setMargins(Ui.dp(this,8),0,0,0);missionRow.addView(detail,detailLp);row.addView(missionRow);
        long snoozeAt=SnoozeScheduler.scheduledAt(this,e.id);
        if(snoozeAt>now){
            LinearLayout snoozeRow=new LinearLayout(this);snoozeRow.setGravity(Gravity.CENTER_VERTICAL);snoozeRow.setPadding(0,Ui.dp(this,10),0,0);snoozeRow.setOnClickListener(v->{});
            String snoozeText=String.format(I18n.locale(this),I18n.tr(this,"スヌーズ中  ·  あと %s"),relativeText(snoozeAt-now));
            TextView snoozeState=Ui.text(this,snoozeText,13,Ui.ACCENT_2);snoozeRow.addView(snoozeState,new LinearLayout.LayoutParams(0,-2,1));
            Button awake=Ui.ghostButton(this,I18n.tr(this,"起きた"));awake.setTextSize(12);awake.setTextColor(Ui.SUCCESS);awake.setOnClickListener(v->{SnoozeScheduler.markAwake(this,e.id);render();});snoozeRow.addView(awake);
            Button cancel=Ui.ghostButton(this,I18n.tr(this,"取消"));cancel.setTextSize(12);cancel.setTextColor(Ui.ACCENT_2);cancel.setOnClickListener(v->{SnoozeScheduler.cancel(this,e.id);render();});snoozeRow.addView(cancel);
            row.addView(snoozeRow);
        }
        if(e.id>=1000){
            LinearLayout deleteRow=new LinearLayout(this);deleteRow.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);deleteRow.setPadding(0,Ui.dp(this,3),0,0);
            Button del=Ui.ghostButton(this,I18n.tr(this,"削除"));del.setTextSize(12);del.setTextColor(Ui.DANGER);del.setContentDescription(I18n.tr(this,"アラームを削除"));del.setOnClickListener(v->confirmDelete(e));deleteRow.addView(del);row.addView(deleteRow);
        }
        on.setOnClickListener(v->{e.enabled=on.isChecked();AlarmProfiles.save(this,e);if(!e.enabled){SnoozeScheduler.cancel(this,e.id);if(e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);}AlarmScheduler.reschedule(this);render();});
        if("timeline".equals(Ui.LAYOUT_MODE)){
            LinearLayout event=new LinearLayout(this);event.setOrientation(LinearLayout.HORIZONTAL);event.setGravity(Gravity.TOP);event.setPadding(Ui.dp(this,5),0,0,0);
            View marker=new View(this);marker.setBackground(Ui.round(Ui.ACCENT_2,9,this));
            LinearLayout.LayoutParams markerLp=new LinearLayout.LayoutParams(Ui.dp(this,9),Ui.dp(this,9));markerLp.setMargins(0,Ui.dp(this,18),Ui.dp(this,8),0);event.addView(marker,markerLp);
            LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(0,-2,1);cardLp.bottomMargin=Ui.dp(this,10);event.addView(row,cardLp);
            list.addView(event,new LinearLayout.LayoutParams(-1,-2));
        }else list.addView(row,Ui.cardParams(this));
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

    private void refreshNext(){
        long regular=AlarmScheduler.nextTriggerMillis(this),snooze=SnoozeScheduler.nextAt(this);long ms=regular;if(snooze>0&&(ms<=0||snooze<ms))ms=snooze;
        int enabled=0;for(AlarmStore.Entry e:AlarmProfiles.all(this))if(e.enabled)enabled++;
        nextStatus.setText(I18n.tr(this,"有効なアラーム")+"  "+enabled);
        if(ms<=0){nextText.setText("--:--");nextMeta.setText(I18n.tr(this,"次のアラームはありません"));horizonDot.setVisibility(View.INVISIBLE);return;}
        ZonedDateTime z=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());nextText.setText(z.format(DateTimeFormatter.ofPattern("HH:mm",I18n.locale(this))));
        horizonDot.setVisibility(View.VISIBLE);
        int minuteOfDay=z.getHour()*60+z.getMinute();
        horizonTrack.post(()->{
            int usable=Math.max(0,horizonTrack.getWidth()-horizonDot.getWidth());
            android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams)horizonDot.getLayoutParams();
            params.leftMargin=Math.round(usable*(minuteOfDay/1440f));
            params.gravity=Gravity.START|Gravity.CENTER_VERTICAL;
            horizonDot.setLayoutParams(params);
        });
        String when=z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"),I18n.locale(this)));
        String prefix=(snooze>0&&ms==snooze)?I18n.tr(this,"スヌーズ中")+"  ·  ":"";
        String remaining=String.format(I18n.locale(this),I18n.tr(this,"あと %s"),relativeText(ms-System.currentTimeMillis()));nextMeta.setText(when+"  ·  "+prefix+remaining);
    }
    private String relativeText(long ms){long mins=Math.max(1,(long)Math.ceil(ms/60000.0));long h=mins/60,m=mins%60;String duration;if(h==0)duration=m+"分";else if(m==0)duration=h+"時間";else duration=h+"時間"+m+"分";return I18n.tr(this,duration);}
    private String repeatText(int m){if(m==0)return I18n.tr(this,"1回のみ");if((m&127)==127)return I18n.tr(this,"毎日");String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(I18n.tr(this,n[i]));}return b.toString();}

    private void requestRuntimePermissions(){try{ArrayList<String> req=new ArrayList<>();if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)req.add(Manifest.permission.POST_NOTIFICATIONS);if(!req.isEmpty())requestPermissions(req.toArray(new String[0]),41);}catch(Throwable ignored){}}
}
