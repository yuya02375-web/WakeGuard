package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.Locale;

public class AlarmEditorActivity extends Activity {
    private static final int REQ_AUDIO=81,REQ_VIDEO=82;
    private static final String[] MISSION_KEYS={"NONE","STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD","SWIPE","ORDER","REVERSE","RANDOM"};
    private static final String[] MISSION_LABELS={"なし","歩数","計算","連打","コード入力","シェイク","記憶","文章入力","長押し","スワイプ","順番タップ","逆順入力","ランダム"};
    private long alarmId; private AlarmStore.Entry draft;
    private EditText label,steps,missionCount; private Button time,sound,missionPicker,ringDuration,fullStopDuration,preNotify,snooze,preview; private CheckBox[] days=new CheckBox[7];
    private Switch enabled,ringAutoStop,fullAutoStop; private SeekBar volume; private TextView volumeText,soundStatus,stepsLabel,countLabel,missionNote;
    private Spinner vibration; private String selectedMission="STEPS"; private MediaPlayer previewPlayer;

    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);alarmId=getIntent().getLongExtra("alarmId",-1L);draft=alarmId<0?AlarmProfiles.defaults(this):AlarmProfiles.get(this,alarmId);selectedMission=AlarmStore.normalizeMission(draft.missionType);build();render();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,10));
        ImageButton back=Ui.iconButton(this,R.drawable.ic_v292_action_back,false,"戻る");back.setOnClickListener(v->finish());top.addView(back);
        TextView heading=Ui.title(this,alarmId<0?"アラームを追加":"アラームを編集",22);heading.setPadding(Ui.dp(this,8),0,0,0);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button saveTop=Ui.ghostButton(this,"保存");saveTop.setTextColor(Ui.ACCENT);saveTop.setOnClickListener(v->{if(saveDraft(true)!=null)finish();});top.addView(saveTop);root.addView(top);

        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,48));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        time=Ui.ghostButton(this,"07:00");time.setTextSize(56);time.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,22,this));time.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));time.setGravity(Gravity.CENTER);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.fixedAt=0L;draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());body.addView(time,new LinearLayout.LayoutParams(-1,Ui.dp(this,112)));

        enabled=new Switch(this);enabled.setText(I18n.tr(this,"アラームを有効にする"));enabled.setTextColor(Ui.TEXT);Ui.styleSwitch(enabled,this);body.addView(enabled,Ui.gapTop(this,8));
        body.addView(Ui.divider(this),Ui.dividerTop(this,14));

        body.addView(Ui.sectionHeader(this,"基本設定"),Ui.gapTop(this,18));
        body.addView(Ui.text(this,"名前",13,Ui.MUTED));label=input("学校、休日など",InputType.TYPE_CLASS_TEXT);body.addView(label,Ui.gapTop(this,6));
        TextView repeat=Ui.text(this,"繰り返し",13,Ui.MUTED);body.addView(repeat,Ui.gapTop(this,18));
        LinearLayout dayRow=new LinearLayout(this);String[]dn={"月","火","水","木","金","土","日"};for(int i=0;i<7;i++){
            CheckBox day=new CheckBox(this);days[i]=day;
            day.setText(I18n.tr(this,dn[i]));day.setButtonDrawable(null);
            day.setGravity(Gravity.CENTER);day.setTextSize(13);day.setMinHeight(Ui.dp(this,48));
            day.setPadding(0,0,0,0);
            day.setTextColor(Ui.MUTED);
            day.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
            day.setOnCheckedChangeListener((v,checked)->{
                day.setTextColor(checked?Ui.BG:Ui.MUTED);
                day.setBackground(checked?Ui.round(Ui.ACCENT_2,14,this):Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));
            });
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f);
            cell.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),0);dayRow.addView(day,cell);
        }body.addView(dayRow,Ui.gapTop(this,10));body.addView(Ui.text(this,"曜日を選ばなければ1回だけ鳴ります",12,Ui.MUTED),Ui.gapTop(this,4));
        LinearLayout preRow=Ui.row(this);preRow.setPadding(0,Ui.dp(this,16),0,0);TextView preLabel=Ui.text(this,"事前通知",15,Ui.TEXT);preRow.addView(preLabel,new LinearLayout.LayoutParams(0,-2,1));preNotify=Ui.ghostButton(this,"30分前  ›");preNotify.setTextColor(Ui.ACCENT);preNotify.setOnClickListener(v->showPreNotifyDialog());preRow.addView(preNotify);body.addView(preRow);
        body.addView(Ui.text(this,"アラームの前に音・振動なしの通知で知らせます",12,Ui.MUTED),Ui.gapTop(this,2));
        LinearLayout snoozeRow=Ui.row(this);snoozeRow.setPadding(0,Ui.dp(this,14),0,0);TextView snoozeLabel=Ui.text(this,"スヌーズ",15,Ui.TEXT);snoozeRow.addView(snoozeLabel,new LinearLayout.LayoutParams(0,-2,1));snooze=Ui.ghostButton(this,"5分  ›");snooze.setTextColor(Ui.ACCENT);snooze.setOnClickListener(v->showSnoozeDialog());snoozeRow.addView(snooze);body.addView(snoozeRow);

        body.addView(Ui.divider(this),Ui.dividerTop(this,22));
        body.addView(Ui.sectionHeader(this,"ミッション"),Ui.gapTop(this,18));
        LinearLayout missionRow=Ui.row(this);TextView ml=Ui.text(this,"解除方法",16,Ui.TEXT);missionRow.addView(ml,new LinearLayout.LayoutParams(0,-2,1));missionPicker=Ui.ghostButton(this,I18n.tr(this,"歩数")+"  ›");missionPicker.setTextColor(Ui.ACCENT);missionPicker.setOnClickListener(v->chooseMission());missionRow.addView(missionPicker);body.addView(missionRow);
        missionNote=Ui.text(this,"",13,Ui.MUTED);body.addView(missionNote,Ui.gapTop(this,2));
        stepsLabel=Ui.text(this,"歩数",13,Ui.MUTED);body.addView(stepsLabel,Ui.gapTop(this,16));steps=input("50",InputType.TYPE_CLASS_NUMBER);body.addView(steps,Ui.gapTop(this,6));
        countLabel=Ui.text(this,"回数",13,Ui.MUTED);body.addView(countLabel,Ui.gapTop(this,16));missionCount=input("3",InputType.TYPE_CLASS_NUMBER);body.addView(missionCount,Ui.gapTop(this,6));

        body.addView(Ui.divider(this),Ui.dividerTop(this,22));
        body.addView(Ui.sectionHeader(this,"音と振動"),Ui.gapTop(this,18));
        LinearLayout soundRow=Ui.row(this);TextView sl=Ui.text(this,"音声 / 動画",16,Ui.TEXT);soundRow.addView(sl,new LinearLayout.LayoutParams(0,-2,1));sound=Ui.ghostButton(this,I18n.tr(this,"選択")+"  ›");sound.setTextColor(Ui.ACCENT);sound.setOnClickListener(v->pickSound());soundRow.addView(sound);body.addView(soundRow);
        soundStatus=Ui.text(this,"",13,Ui.MUTED);body.addView(soundStatus,Ui.gapTop(this,2));
        preview=Ui.ghostButton(this,"▶  試聴");preview.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);preview.setOnClickListener(v->togglePreview());body.addView(preview,Ui.gapTop(this,4));
        Button defaultSound=Ui.ghostButton(this,"標準音に戻す");defaultSound.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);defaultSound.setOnClickListener(v->{draft.soundUri="";draft.soundName="";draft.soundBytes=-1;draft.soundDurationMs=-1;renderSound();});body.addView(defaultSound,Ui.gapTop(this,4));
        volumeText=Ui.text(this,"音量 15%",15,Ui.TEXT);body.addView(volumeText,Ui.gapTop(this,14));volume=new SeekBar(this);volume.setMax(100);body.addView(volume);volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){volumeText.setText(I18n.tr(AlarmEditorActivity.this,"音量 "+p+"%"));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        TextView vib=Ui.text(this,"振動",13,Ui.MUTED);body.addView(vib,Ui.gapTop(this,14));vibration=new Spinner(this);String[]modes={I18n.tr(this,"オフ"),I18n.tr(this,"不規則"),I18n.tr(this,"強い連続パルス")};ArrayAdapter<String>va=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes){@Override public View getView(int p,View c,android.view.ViewGroup g){View v=super.getView(p,c,g);if(v instanceof TextView){((TextView)v).setTextColor(Ui.TEXT);((TextView)v).setPadding(Ui.dp(AlarmEditorActivity.this,8),Ui.dp(AlarmEditorActivity.this,10),Ui.dp(AlarmEditorActivity.this,8),Ui.dp(AlarmEditorActivity.this,10));}return v;}};vibration.setAdapter(va);body.addView(vibration,Ui.gapTop(this,4));
        body.addView(Ui.divider(this),Ui.dividerTop(this,20));
        body.addView(Ui.sectionHeader(this,"自動停止"),Ui.gapTop(this,18));
        ringAutoStop=new Switch(this);ringAutoStop.setText(I18n.tr(this,"音・振動だけ自動停止"));ringAutoStop.setTextColor(Ui.TEXT);Ui.styleSwitch(ringAutoStop,this);ringAutoStop.setOnCheckedChangeListener((v,on)->{if(!on){draft.ringDurationSec=0;renderAutoStop();}else if(draft.ringDurationSec<=0){showDurationDialog(false,true);}});body.addView(ringAutoStop,Ui.gapTop(this,6));
        LinearLayout ringRow=Ui.row(this);ringRow.setPadding(0,Ui.dp(this,4),0,0);TextView ringLabel=Ui.text(this,"音・振動を止めるまで",14,Ui.MUTED);ringRow.addView(ringLabel,new LinearLayout.LayoutParams(0,-2,1));ringDuration=Ui.ghostButton(this,I18n.tr(this,"無限")+"  ›");ringDuration.setTextColor(Ui.ACCENT);ringDuration.setOnClickListener(v->showDurationDialog(false,false));ringRow.addView(ringDuration);body.addView(ringRow);
        body.addView(Ui.text(this,"オンにすると指定時間後に音と振動だけ停止します。ミッション画面は残ります。",12,Ui.MUTED),Ui.gapTop(this,0));

        fullAutoStop=new Switch(this);fullAutoStop.setText(I18n.tr(this,"アラームを完全自動停止"));fullAutoStop.setTextColor(Ui.TEXT);Ui.styleSwitch(fullAutoStop,this);fullAutoStop.setOnCheckedChangeListener((v,on)->{if(!on){draft.fullStopDurationSec=0;renderAutoStop();}else if(draft.fullStopDurationSec<=0){showDurationDialog(true,true);}});body.addView(fullAutoStop,Ui.gapTop(this,16));
        LinearLayout fullRow=Ui.row(this);fullRow.setPadding(0,Ui.dp(this,4),0,0);TextView fullLabel=Ui.text(this,"全部止めるまで",14,Ui.MUTED);fullRow.addView(fullLabel,new LinearLayout.LayoutParams(0,-2,1));fullStopDuration=Ui.ghostButton(this,I18n.tr(this,"無限")+"  ›");fullStopDuration.setTextColor(Ui.ACCENT);fullStopDuration.setOnClickListener(v->showDurationDialog(true,false));fullRow.addView(fullStopDuration);body.addView(fullRow);
        body.addView(Ui.text(this,"オンにすると指定時間後に音・振動・ミッション画面を含む現在のアラームをすべて終了します。次回以降の予約は消えません。",12,Ui.MUTED),Ui.gapTop(this,0));

        body.addView(Ui.divider(this),Ui.dividerTop(this,22));
        Button test=Ui.ghostButton(this,"このアラームをテスト");test.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);test.setOnClickListener(v->{AlarmStore.Entry saved=saveDraft(false);if(saved==null)return;Intent s=new Intent(this,AlarmService.class).setAction(AlarmService.ACTION_FIRE_TEST).putExtra(AlarmService.EXTRA_ALARM_ID,saved.id);try{if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"テストを開始できません"),Toast.LENGTH_LONG).show();}});body.addView(test,Ui.gapTop(this,10));
        if(alarmId>=1000){Button del=Ui.ghostButton(this,"アラームを削除");del.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);del.setTextColor(Ui.DANGER);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(I18n.tr(this,"アラームを削除")).setMessage(I18n.tr(this,"このアラームだけ削除します。")).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"削除"),(d,w)->{AlarmScheduler.cancelExtraAlarm(this,alarmId);AlarmProfiles.delete(this,alarmId);AlarmScheduler.reschedule(this);finish();}).show());body.addView(del,Ui.gapTop(this,4));}
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
    }

    private void chooseMission(){int checked=missionIndex(selectedMission);String[] labels=new String[MISSION_LABELS.length];for(int i=0;i<labels.length;i++)labels[i]=I18n.tr(this,MISSION_LABELS[i]);new AlertDialog.Builder(this).setTitle(I18n.tr(this,"ミッションを選択")).setSingleChoiceItems(labels,checked,(d,which)->{selectedMission=MISSION_KEYS[which];missionPicker.setText(labels[which]+"  ›");refreshMissionFields();d.dismiss();}).show();}
    private int missionIndex(String t){for(int i=0;i<MISSION_KEYS.length;i++)if(MISSION_KEYS[i].equals(t))return i;return 0;}

    private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(I18n.tr(this,hint));e.setHintTextColor(Ui.MUTED_2);e.setTextColor(Ui.TEXT);e.setTextSize(17);e.setInputType(type);e.setSingleLine(true);e.setMinHeight(Ui.dp(this,54));e.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,9));e.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,14,this));return e;}

    private void refreshMissionFields(){if(steps==null)return;String t=selectedMission;boolean needSteps="STEPS".equals(t)||"RANDOM".equals(t);boolean needCount=!("NONE".equals(t)||"STEPS".equals(t));stepsLabel.setVisibility(needSteps?View.VISIBLE:View.GONE);steps.setVisibility(needSteps?View.VISIBLE:View.GONE);countLabel.setVisibility(needCount?View.VISIBLE:View.GONE);missionCount.setVisibility(needCount?View.VISIBLE:View.GONE);
        if("NONE".equals(t)){missionNote.setText(I18n.tr(this,"追加の解除操作なし。停止ボタンを押すだけで解除できます。"));}
        else if("STEPS".equals(t)){stepsLabel.setText(I18n.tr(this,"解除に必要な歩数"));missionNote.setText(I18n.tr(this,"実際に歩いて解除します。"));}
        else if("MATH".equals(t)){countLabel.setText(I18n.tr(this,"正解する問題数"));missionNote.setText(I18n.tr(this,"足し算・引き算・掛け算を解きます。"));}
        else if("TAP".equals(t)){countLabel.setText(I18n.tr(this,"タップ回数"));missionNote.setText(I18n.tr(this,"画面のボタンを指定回数タップします。"));}
        else if("CODE".equals(t)){countLabel.setText(I18n.tr(this,"入力するコード数"));missionNote.setText(I18n.tr(this,"表示された6桁コードを正確に入力します。"));}
        else if("SHAKE".equals(t)){countLabel.setText(I18n.tr(this,"シェイク回数"));missionNote.setText(I18n.tr(this,"スマホを大きく振って解除します。"));}
        else if("MEMORY".equals(t)){countLabel.setText(I18n.tr(this,"記憶問題の数"));missionNote.setText(I18n.tr(this,"数秒だけ表示される数字を覚えて入力します。"));}
        else if("TYPE".equals(t)){countLabel.setText(I18n.tr(this,"入力する文章数"));missionNote.setText(I18n.tr(this,"表示された短い文章をそのまま入力します。"));}
        else if("HOLD".equals(t)){countLabel.setText(I18n.tr(this,"長押しする秒数"));missionNote.setText(I18n.tr(this,"ボタンを離さず長押しします。2〜30秒がおすすめです。"));}
        else if("SWIPE".equals(t)){countLabel.setText(I18n.tr(this,"スワイプ回数"));missionNote.setText(I18n.tr(this,"画面を左右に大きくスワイプして解除します。"));}
        else if("ORDER".equals(t)){countLabel.setText(I18n.tr(this,"順番タップの回数"));missionNote.setText(I18n.tr(this,"ばらばらに並んだ数字を1から6まで順番にタップします。"));}
        else if("REVERSE".equals(t)){countLabel.setText(I18n.tr(this,"逆順入力の問題数"));missionNote.setText(I18n.tr(this,"表示された数字を逆の順番で入力します。"));}
        else {stepsLabel.setText(I18n.tr(this,"歩数が選ばれた場合の歩数"));countLabel.setText(I18n.tr(this,"その他の回数 / 秒数"));missionNote.setText(I18n.tr(this,"鳴るたびに11種類のミッションから1つ選びます。"));}
    }



    private void renderPreNotify(){if(preNotify==null)return;preNotify.setText((draft.preNotifyMin<=0?I18n.tr(this,"オフ"):draft.preNotifyMin+I18n.tr(this,"分前"))+"  ›");}
    private void showPreNotifyDialog(){
        final int[] values={0,5,10,15,30,60,120,-1};
        final String[] labels={I18n.tr(this,"オフ"),"5"+I18n.tr(this,"分前"),"10"+I18n.tr(this,"分前"),"15"+I18n.tr(this,"分前"),"30"+I18n.tr(this,"分前"),"60"+I18n.tr(this,"分前"),"120"+I18n.tr(this,"分前"),I18n.tr(this,"カスタム")+"…"};
        int checked=-1;for(int i=0;i<values.length-1;i++)if(values[i]==draft.preNotifyMin)checked=i;
        final AlertDialog d=new AlertDialog.Builder(this).setTitle(I18n.tr(this,"事前通知")).setSingleChoiceItems(labels,checked,null).setNegativeButton(I18n.tr(this,"キャンセル"),null).create();
        d.setOnShowListener(x->{ListView list=d.getListView();list.setOnItemClickListener((a,v,pos,id)->{if(values[pos]>=0){draft.preNotifyMin=values[pos];renderPreNotify();d.dismiss();}else{d.dismiss();showCustomPreNotifyDialog();}});});d.show();
    }
    private void renderSnooze(){if(snooze!=null)snooze.setText(draft.snoozeMinutes+I18n.tr(this,"分")+"  ›");}
    private void showSnoozeDialog(){final int[] values={1,3,5,10,15,20,30,45,60};String[] labels=new String[values.length];int checked=0;for(int i=0;i<values.length;i++){labels[i]=values[i]+I18n.tr(this,"分");if(values[i]==draft.snoozeMinutes)checked=i;}new AlertDialog.Builder(this).setTitle(I18n.tr(this,"スヌーズ")).setSingleChoiceItems(labels,checked,(d,w)->{draft.snoozeMinutes=values[w];renderSnooze();d.dismiss();}).setNegativeButton(I18n.tr(this,"キャンセル"),null).show();}
    private void togglePreview(){if(previewPlayer!=null){stopPreview();return;}try{String raw=draft.soundUri==null?"":draft.soundUri.trim();Uri uri;if(raw.isEmpty())uri=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);else if(raw.startsWith("content:")||raw.startsWith("file:"))uri=Uri.parse(raw);else uri=Uri.fromFile(new File(raw));previewPlayer=new MediaPlayer();previewPlayer.setAudioStreamType(android.media.AudioManager.STREAM_ALARM);previewPlayer.setDataSource(this,uri);previewPlayer.setLooping(false);float v=Math.max(0f,Math.min(1f,draft.volume/100f));previewPlayer.setVolume(v,v);previewPlayer.setOnCompletionListener(x->stopPreview());previewPlayer.prepare();previewPlayer.start();if(preview!=null)preview.setText("■  "+I18n.tr(this,"停止"));}catch(Throwable t){stopPreview();Toast.makeText(this,I18n.tr(this,"この音源は試聴できません"),Toast.LENGTH_SHORT).show();}}
    private void stopPreview(){MediaPlayer p=previewPlayer;previewPlayer=null;if(p!=null){try{p.stop();}catch(Throwable ignored){}try{p.release();}catch(Throwable ignored){}}if(preview!=null)preview.setText("▶  "+I18n.tr(this,"試聴"));}
    @Override protected void onStop(){stopPreview();super.onStop();}

    private void showCustomPreNotifyDialog(){
        final EditText input=input(String.valueOf(Math.max(1,draft.preNotifyMin)),InputType.TYPE_CLASS_NUMBER);input.setSelectAllOnFocus(true);input.setHint("1–1440 "+I18n.tr(this,"分前").trim());int pad=Ui.dp(this,20);FrameLayout wrap=new FrameLayout(this);wrap.setPadding(pad,0,pad,0);wrap.addView(input,new FrameLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle(I18n.tr(this,"事前通知")).setView(wrap).setNegativeButton(I18n.tr(this,"キャンセル"),null).setPositiveButton(I18n.tr(this,"保存"),(d,w)->{try{int v=Integer.parseInt(input.getText().toString().trim());draft.preNotifyMin=Math.max(1,Math.min(1440,v));renderPreNotify();}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"設定値を確認してください"),Toast.LENGTH_SHORT).show();}}).show();
    }

    private void renderAutoStop(){
        if(ringAutoStop!=null){ringAutoStop.setOnCheckedChangeListener(null);ringAutoStop.setChecked(draft.ringDurationSec>0);ringAutoStop.setOnCheckedChangeListener((v,on)->{if(!on){draft.ringDurationSec=0;renderAutoStop();}else if(draft.ringDurationSec<=0){showDurationDialog(false,true);}});}
        if(fullAutoStop!=null){fullAutoStop.setOnCheckedChangeListener(null);fullAutoStop.setChecked(draft.fullStopDurationSec>0);fullAutoStop.setOnCheckedChangeListener((v,on)->{if(!on){draft.fullStopDurationSec=0;renderAutoStop();}else if(draft.fullStopDurationSec<=0){showDurationDialog(true,true);}});}
        if(ringDuration!=null){ringDuration.setEnabled(draft.ringDurationSec>0);ringDuration.setAlpha(draft.ringDurationSec>0?1f:.45f);ringDuration.setText(I18n.tr(this,formatDuration(draft.ringDurationSec))+"  ›");}
        if(fullStopDuration!=null){fullStopDuration.setEnabled(draft.fullStopDurationSec>0);fullStopDuration.setAlpha(draft.fullStopDurationSec>0?1f:.45f);fullStopDuration.setText(I18n.tr(this,formatDuration(draft.fullStopDurationSec))+"  ›");}
    }
    private String formatDuration(int sec){
        if(sec<=0)return "無限";
        int h=sec/3600,m=(sec%3600)/60,s=sec%60;
        if(h>0)return s>0?h+"時間 "+m+"分 "+s+"秒":m>0?h+"時間 "+m+"分":h+"時間";
        if(m>0)return s>0?m+"分 "+s+"秒":m+"分";
        return s+"秒";
    }
    private void showDurationDialog(boolean full,boolean disableOnCancel){
        final Dialog d=new Dialog(this);
        final int current=full?draft.fullStopDurationSec:draft.ringDurationSec;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,18));box.setBackground(Ui.round(Ui.SURFACE,18,this));
        box.addView(Ui.title(this,full?"全部を自動停止する時間":"音・振動を自動停止する時間",20));
        box.addView(Ui.text(this,"プリセット、または1秒単位で細かく設定できます。オフにすると無限です。",12,Ui.MUTED),Ui.gapTop(this,4));

        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout presets=new LinearLayout(this);
        int[] sec={30,60,180,300,600,900,1800,3600};String[] labels={"30秒","1分","3分","5分","10分","15分","30分","1時間"};
        for(int i=0;i<sec.length;i++){final int value=sec[i];Button b=Ui.ghostButton(this,labels[i]);b.setBackground(Ui.round(Ui.SURFACE_2,12,this));b.setOnClickListener(v->{if(full)draft.fullStopDurationSec=value;else draft.ringDurationSec=value;renderAutoStop();d.dismiss();});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,Ui.dp(this,44));bp.setMargins(0,0,Ui.dp(this,6),0);presets.addView(b,bp);}
        hsv.addView(presets);box.addView(hsv,Ui.gapTop(this,12));

        box.addView(Ui.sectionHeader(this,"カスタム"),Ui.gapTop(this,10));
        LinearLayout pickers=Ui.row(this);pickers.setGravity(Gravity.CENTER);
        NumberPicker minutes=new NumberPicker(this);minutes.setMinValue(0);minutes.setMaxValue(1440);minutes.setWrapSelectorWheel(false);minutes.setValue(Math.min(1440,Math.max(0,current/60)));
        NumberPicker seconds=new NumberPicker(this);seconds.setMinValue(0);seconds.setMaxValue(59);seconds.setWrapSelectorWheel(true);seconds.setValue(Math.max(0,current%60));
        TextView minLabel=Ui.text(this,"分",14,Ui.MUTED);TextView secLabel=Ui.text(this,"秒",14,Ui.MUTED);
        pickers.addView(minutes,new LinearLayout.LayoutParams(Ui.dp(this,100),Ui.dp(this,120)));pickers.addView(minLabel);pickers.addView(seconds,new LinearLayout.LayoutParams(Ui.dp(this,100),Ui.dp(this,120)));pickers.addView(secLabel);box.addView(pickers);

        LinearLayout actions=Ui.row(this);actions.setPadding(0,Ui.dp(this,8),0,0);Button cancel=Ui.button(this,"キャンセル",false);Button save=Ui.button(this,"設定",true);
        cancel.setOnClickListener(v->{if(disableOnCancel){if(full)draft.fullStopDurationSec=0;else draft.ringDurationSec=0;renderAutoStop();}d.dismiss();});
        save.setOnClickListener(v->{int value=minutes.getValue()*60+seconds.getValue();if(value<=0){Toast.makeText(this,I18n.tr(this,"1秒以上に設定してください"),Toast.LENGTH_SHORT).show();return;}value=Math.min(86400,value);if(full)draft.fullStopDurationSec=value;else draft.ringDurationSec=value;renderAutoStop();d.dismiss();});
        actions.addView(cancel,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));View gap=new View(this);actions.addView(gap,new LinearLayout.LayoutParams(Ui.dp(this,8),1));actions.addView(save,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));box.addView(actions);
        d.setOnCancelListener(x->{if(disableOnCancel){if(full)draft.fullStopDurationSec=0;else draft.ringDurationSec=0;renderAutoStop();}});
        d.setContentView(box);d.show();android.view.Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));w.setDimAmount(.65f);w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.92f),-2);}
    }

    private void render(){enabled.setChecked(draft.enabled);label.setText(draft.label);time.setText(String.format(Locale.JAPAN,"%02d:%02d",draft.hour,draft.minute));for(int i=0;i<7;i++)days[i].setChecked((draft.dayMask&(1<<i))!=0);steps.setText(String.valueOf(draft.steps));missionCount.setText(String.valueOf(draft.missionCount));selectedMission=AlarmStore.normalizeMission(draft.missionType);missionPicker.setText(I18n.tr(this,AlarmProfiles.missionName(selectedMission))+"  ›");volume.setProgress(draft.volume);vibration.setSelection("OFF".equals(draft.vibration)?0:("STRONG".equals(draft.vibration)?2:1));renderSound();renderPreNotify();renderSnooze();renderAutoStop();refreshMissionFields();}
    private void renderSound(){
        if(draft.soundName==null||draft.soundName.isEmpty()){soundStatus.setText(I18n.tr(this,"標準アラーム音"));return;}
        String size=draft.soundBytes>0?String.format(Locale.JAPAN,"%.1f MB",draft.soundBytes/1048576.0):I18n.tr(this,"サイズ不明");
        String dur=draft.soundDurationMs>0?String.format(Locale.JAPAN,"%d:%02d",draft.soundDurationMs/60000,(draft.soundDurationMs/1000)%60):I18n.tr(this,"長さ不明");
        soundStatus.setText(I18n.tr(this,isVideoMedia(draft.soundUri,draft.soundName)?"動画":"音声")+"  ·  "+draft.soundName+"  ·  "+size+"  ·  "+dur);
    }
    private AlarmStore.Entry saveDraft(boolean toast){try{draft.enabled=enabled.isChecked();draft.label=label.getText().toString().trim();draft.dayMask=0;for(int i=0;i<7;i++)if(days[i].isChecked())draft.dayMask|=1<<i;if(draft.dayMask!=0)draft.fixedAt=0L;if(draft.fixedAt>0L){if(draft.fixedAt<=System.currentTimeMillis()+1000L)draft.fixedAt=0L;else{java.time.ZonedDateTime z=java.time.Instant.ofEpochMilli(draft.fixedAt).atZone(java.time.ZoneId.systemDefault());draft.hour=z.getHour();draft.minute=z.getMinute();}}draft.missionType=selectedMission;draft.steps=Math.max(1,Math.min(9999,Integer.parseInt(steps.getText().toString().trim().isEmpty()?"50":steps.getText().toString().trim())));draft.missionCount=Math.max(1,Math.min(500,Integer.parseInt(missionCount.getText().toString().trim().isEmpty()?"3":missionCount.getText().toString().trim())));draft.volume=volume.getProgress();int vp=vibration.getSelectedItemPosition();draft.vibration=vp==0?"OFF":(vp==2?"STRONG":"IRREGULAR");draft=AlarmProfiles.save(this,draft);alarmId=draft.id;if(!draft.enabled)SnoozeScheduler.cancel(this,alarmId);AlarmScheduler.reschedule(this);if(toast)Toast.makeText(this,I18n.tr(this,"保存しました"),Toast.LENGTH_SHORT).show();return draft;}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"設定値を確認してください"),Toast.LENGTH_LONG).show();return null;}}
    private void pickSound(){
        new AlertDialog.Builder(this).setTitle(I18n.tr(this,"アラームの音・動画")).setItems(new String[]{I18n.tr(this,"音声を選択"),I18n.tr(this,"動画を選択"),I18n.tr(this,"標準に戻す")},(d,w)->{if(w==0)pickMedia(false);else if(w==1)pickMedia(true);else{draft.soundUri="";draft.soundName="";draft.soundBytes=-1;draft.soundDurationMs=-1;renderSound();}}).show();
    }
    private void pickMedia(boolean video){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(video?"video/*":"audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,video?REQ_VIDEO:REQ_AUDIO);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,video?"動画ファイルを開けません":"音声ファイルを開けません"),Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if((requestCode!=REQ_AUDIO&&requestCode!=REQ_VIDEO)||resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}if(requestCode==REQ_VIDEO)useVideo(u);else copySound(u);}
    private void copySound(Uri uri){File out=null;try{String name=fileName(uri);File dir=new File(getFilesDir(),"alarm_sounds");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+".audio");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){if(in==null)throw new IOException("open");byte[]buf=new byte[65536];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}MediaPlayer mp=new MediaPlayer();mp.setDataSource(out.getAbsolutePath());mp.prepare();long dur=mp.getDuration();mp.release();draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;renderSound();}catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"この音源は読み込めません"),Toast.LENGTH_LONG).show();}}
    private void useVideo(Uri uri){
        File out=null;
        try{
            String name=fileName(uri);String ext=videoExtension(uri,name);
            File dir=new File(getFilesDir(),"alarm_videos");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");
            out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+ext);
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){
                if(in==null)throw new IOException("open");byte[]buf=new byte[131072];int n;while((n=in.read(buf))>0)os.write(buf,0,n);
            }
            long dur=-1;android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever();
            try{m.setDataSource(out.getAbsolutePath());String x=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(x!=null)dur=Long.parseLong(x);}finally{try{m.release();}catch(Throwable ignored){}}
            String old=draft.soundUri;draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;cleanupOwnedVideo(old);renderSound();promptFullScreenVideoPermissionIfNeeded();
        }catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();}
    }
    private String videoExtension(Uri uri,String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);String[] exts={".mp4",".m4v",".3gp",".3gpp",".webm",".mkv",".ts"};for(String e:exts)if(n.endsWith(e))return e;
        try{String t=getContentResolver().getType(uri);if(t!=null){t=t.toLowerCase(Locale.ROOT);if(t.contains("webm"))return ".webm";if(t.contains("3gpp"))return ".3gp";if(t.contains("matroska"))return ".mkv";}}catch(Throwable ignored){}
        return ".mp4";
    }
    private void cleanupOwnedVideo(String raw){
        try{if(raw==null||raw.isEmpty())return;File f=new File(raw);File dir=new File(getFilesDir(),"alarm_videos");if(f.getParentFile()!=null&&f.getParentFile().equals(dir)&&f.exists())f.delete();}catch(Throwable ignored){}
    }

    private void promptFullScreenVideoPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT<34)return;
        boolean ok=false;try{NotificationManager nm=getSystemService(NotificationManager.class);ok=nm!=null&&nm.canUseFullScreenIntent();}catch(Throwable ignored){}
        if(ok)return;
        new AlertDialog.Builder(this)
                .setTitle(I18n.tr(this,"ロック画面の動画表示"))
                .setMessage(I18n.tr(this,"ロック中に動画アラームを全画面表示するには、端末設定で「全画面アラーム」を許可してください。"))
                .setPositiveButton(I18n.tr(this,"設定を開く"),(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"設定 → アプリ → IGNIDO Wake → 全画面アラーム を許可してください"),Toast.LENGTH_LONG).show();}})
                .setNegativeButton(I18n.tr(this,"後で"),null).show();
    }

    private boolean isVideoMedia(String raw,String name){
        if(raw!=null&&!raw.trim().isEmpty()){
            try{if(raw.startsWith("content:")){String t=getContentResolver().getType(Uri.parse(raw));if(t!=null&&t.toLowerCase(Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}
            String x=raw.toLowerCase(Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;
        }
        String n=name==null?"":name.toLowerCase(Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");
    }
    private String fileName(Uri uri){String name=I18n.tr(this,"選択した音源");try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.isEmpty())name=x;}}catch(Throwable ignored){}return name;}

}
