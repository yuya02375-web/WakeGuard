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
import java.util.Locale;

public class AlarmEditorActivity extends Activity {
    private static final int REQ_AUDIO=81;
    private static final String[] MISSION_KEYS={"STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD","RANDOM"};
    private static final String[] MISSION_LABELS={"歩数","計算","連打","コード入力","シェイク","記憶","文章入力","長押し","ランダム"};
    private long alarmId; private AlarmStore.Entry draft;
    private EditText label,steps,missionCount; private Button time,sound,missionPicker; private CheckBox[] days=new CheckBox[7];
    private Switch enabled; private SeekBar volume; private TextView volumeText,soundStatus,stepsLabel,countLabel,missionNote;
    private Spinner vibration; private String selectedMission="STEPS";

    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.statusBar(this);alarmId=getIntent().getLongExtra("alarmId",-1L);draft=alarmId<0?AlarmProfiles.defaults(this):AlarmProfiles.get(this,alarmId);selectedMission=AlarmStore.normalizeMission(draft.missionType);build();render();}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,6));
        Button back=Ui.ghostButton(this,"←");back.setOnClickListener(v->finish());top.addView(back);
        TextView heading=Ui.title(this,alarmId<0?"アラームを追加":"アラームを編集",22);heading.setPadding(Ui.dp(this,8),0,0,0);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button saveTop=Ui.ghostButton(this,"保存");saveTop.setTextColor(Ui.ACCENT);saveTop.setOnClickListener(v->{if(saveDraft(true)!=null)finish();});top.addView(saveTop);root.addView(top);

        ScrollView sv=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,48));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        time=Ui.ghostButton(this,"07:00");time.setTextSize(52);time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);time.setGravity(Gravity.CENTER);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());body.addView(time,new LinearLayout.LayoutParams(-1,Ui.dp(this,86)));

        enabled=new Switch(this);enabled.setText("アラームを有効にする");enabled.setTextColor(Ui.TEXT);body.addView(enabled,Ui.gapTop(this,8));
        body.addView(Ui.divider(this),Ui.gapTop(this,14));

        body.addView(Ui.sectionHeader(this,"基本設定"),Ui.gapTop(this,18));
        body.addView(Ui.text(this,"名前",13,Ui.MUTED));label=input("学校、休日など",InputType.TYPE_CLASS_TEXT);body.addView(label,Ui.gapTop(this,6));
        TextView repeat=Ui.text(this,"繰り返し",13,Ui.MUTED);body.addView(repeat,Ui.gapTop(this,18));
        LinearLayout dayRow=new LinearLayout(this);String[]dn={"月","火","水","木","金","土","日"};for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(dn[i]);days[i].setTextColor(Ui.TEXT);days[i].setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));days[i].setGravity(Gravity.CENTER);dayRow.addView(days[i],new LinearLayout.LayoutParams(0,-2,1));}body.addView(dayRow,Ui.gapTop(this,4));body.addView(Ui.text(this,"曜日を選ばなければ1回だけ鳴ります",12,Ui.MUTED),Ui.gapTop(this,4));

        body.addView(Ui.divider(this),Ui.gapTop(this,22));
        body.addView(Ui.sectionHeader(this,"ミッション"),Ui.gapTop(this,18));
        LinearLayout missionRow=Ui.row(this);TextView ml=Ui.text(this,"解除方法",16,Ui.TEXT);missionRow.addView(ml,new LinearLayout.LayoutParams(0,-2,1));missionPicker=Ui.ghostButton(this,"歩数  ›");missionPicker.setTextColor(Ui.ACCENT);missionPicker.setOnClickListener(v->chooseMission());missionRow.addView(missionPicker);body.addView(missionRow);
        missionNote=Ui.text(this,"",13,Ui.MUTED);body.addView(missionNote,Ui.gapTop(this,2));
        stepsLabel=Ui.text(this,"歩数",13,Ui.MUTED);body.addView(stepsLabel,Ui.gapTop(this,16));steps=input("50",InputType.TYPE_CLASS_NUMBER);body.addView(steps,Ui.gapTop(this,6));
        countLabel=Ui.text(this,"回数",13,Ui.MUTED);body.addView(countLabel,Ui.gapTop(this,16));missionCount=input("3",InputType.TYPE_CLASS_NUMBER);body.addView(missionCount,Ui.gapTop(this,6));

        body.addView(Ui.divider(this),Ui.gapTop(this,22));
        body.addView(Ui.sectionHeader(this,"音と振動"),Ui.gapTop(this,18));
        LinearLayout soundRow=Ui.row(this);TextView sl=Ui.text(this,"アラーム音",16,Ui.TEXT);soundRow.addView(sl,new LinearLayout.LayoutParams(0,-2,1));sound=Ui.ghostButton(this,"選択  ›");sound.setTextColor(Ui.ACCENT);sound.setOnClickListener(v->pickSound());soundRow.addView(sound);body.addView(soundRow);
        soundStatus=Ui.text(this,"",13,Ui.MUTED);body.addView(soundStatus,Ui.gapTop(this,2));
        Button defaultSound=Ui.ghostButton(this,"標準音に戻す");defaultSound.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);defaultSound.setOnClickListener(v->{draft.soundUri="";draft.soundName="";draft.soundBytes=-1;draft.soundDurationMs=-1;renderSound();});body.addView(defaultSound,Ui.gapTop(this,4));
        volumeText=Ui.text(this,"音量 15%",15,Ui.TEXT);body.addView(volumeText,Ui.gapTop(this,14));volume=new SeekBar(this);volume.setMax(100);body.addView(volume);volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){volumeText.setText("音量 "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        TextView vib=Ui.text(this,"振動",13,Ui.MUTED);body.addView(vib,Ui.gapTop(this,14));vibration=new Spinner(this);String[]modes={"不規則","強い連続パルス"};ArrayAdapter<String>va=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes){@Override public View getView(int p,View c,android.view.ViewGroup g){View v=super.getView(p,c,g);if(v instanceof TextView){((TextView)v).setTextColor(Ui.TEXT);((TextView)v).setPadding(Ui.dp(AlarmEditorActivity.this,8),Ui.dp(AlarmEditorActivity.this,10),Ui.dp(AlarmEditorActivity.this,8),Ui.dp(AlarmEditorActivity.this,10));}return v;}};vibration.setAdapter(va);body.addView(vibration,Ui.gapTop(this,4));

        body.addView(Ui.divider(this),Ui.gapTop(this,22));
        Button test=Ui.ghostButton(this,"このアラームをテスト");test.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);test.setOnClickListener(v->{AlarmStore.Entry saved=saveDraft(false);if(saved==null)return;Intent s=new Intent(this,AlarmService.class).setAction(AlarmService.ACTION_FIRE_TEST).putExtra(AlarmService.EXTRA_ALARM_ID,saved.id);try{if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);}catch(Throwable t){Toast.makeText(this,"テストを開始できません",Toast.LENGTH_LONG).show();}});body.addView(test,Ui.gapTop(this,10));
        if(alarmId>=1000){Button del=Ui.ghostButton(this,"アラームを削除");del.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);del.setTextColor(Ui.DANGER);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("アラームを削除").setMessage("このアラームだけ削除します。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{AlarmScheduler.cancelExtraAlarm(this,alarmId);AlarmProfiles.delete(this,alarmId);AlarmScheduler.reschedule(this);finish();}).show());body.addView(del,Ui.gapTop(this,4));}
        setContentView(root);
    }

    private void chooseMission(){int checked=missionIndex(selectedMission);new AlertDialog.Builder(this).setTitle("ミッションを選択").setSingleChoiceItems(MISSION_LABELS,checked,(d,which)->{selectedMission=MISSION_KEYS[which];missionPicker.setText(MISSION_LABELS[which]+"  ›");refreshMissionFields();d.dismiss();}).show();}
    private int missionIndex(String t){for(int i=0;i<MISSION_KEYS.length;i++)if(MISSION_KEYS[i].equals(t))return i;return 0;}

    private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED_2);e.setTextColor(Ui.TEXT);e.setTextSize(17);e.setInputType(type);e.setSingleLine(true);e.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));e.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));return e;}

    private void refreshMissionFields(){if(steps==null)return;String t=selectedMission;boolean needSteps="STEPS".equals(t)||"RANDOM".equals(t);boolean needCount=!"STEPS".equals(t);stepsLabel.setVisibility(needSteps?View.VISIBLE:View.GONE);steps.setVisibility(needSteps?View.VISIBLE:View.GONE);countLabel.setVisibility(needCount?View.VISIBLE:View.GONE);missionCount.setVisibility(needCount?View.VISIBLE:View.GONE);
        if("STEPS".equals(t)){stepsLabel.setText("解除に必要な歩数");missionNote.setText("実際に歩いて解除します。");}
        else if("MATH".equals(t)){countLabel.setText("正解する問題数");missionNote.setText("足し算・引き算・掛け算を解きます。");}
        else if("TAP".equals(t)){countLabel.setText("タップ回数");missionNote.setText("画面のボタンを指定回数タップします。");}
        else if("CODE".equals(t)){countLabel.setText("入力するコード数");missionNote.setText("表示された6桁コードを正確に入力します。");}
        else if("SHAKE".equals(t)){countLabel.setText("シェイク回数");missionNote.setText("スマホを大きく振って解除します。");}
        else if("MEMORY".equals(t)){countLabel.setText("記憶問題の数");missionNote.setText("数秒だけ表示される数字を覚えて入力します。");}
        else if("TYPE".equals(t)){countLabel.setText("入力する文章数");missionNote.setText("表示された短い文章をそのまま入力します。");}
        else if("HOLD".equals(t)){countLabel.setText("長押しする秒数");missionNote.setText("ボタンを離さず長押しします。2〜30秒がおすすめです。");}
        else {stepsLabel.setText("歩数が選ばれた場合の歩数");countLabel.setText("その他の回数 / 秒数");missionNote.setText("鳴るたびに8種類のミッションから1つ選びます。");}
    }

    private void render(){enabled.setChecked(draft.enabled);label.setText(draft.label);time.setText(String.format(Locale.JAPAN,"%02d:%02d",draft.hour,draft.minute));for(int i=0;i<7;i++)days[i].setChecked((draft.dayMask&(1<<i))!=0);steps.setText(String.valueOf(draft.steps));missionCount.setText(String.valueOf(draft.missionCount));selectedMission=AlarmStore.normalizeMission(draft.missionType);missionPicker.setText(AlarmProfiles.missionName(selectedMission)+"  ›");volume.setProgress(draft.volume);vibration.setSelection("STRONG".equals(draft.vibration)?1:0);renderSound();refreshMissionFields();}
    private void renderSound(){if(draft.soundName==null||draft.soundName.isEmpty())soundStatus.setText("標準アラーム音");else{String size=draft.soundBytes>0?String.format(Locale.JAPAN,"%.1f MB",draft.soundBytes/1048576.0):"サイズ不明";String dur=draft.soundDurationMs>0?String.format(Locale.JAPAN,"%d:%02d",draft.soundDurationMs/60000,(draft.soundDurationMs/1000)%60):"長さ不明";soundStatus.setText(draft.soundName+"  ·  "+size+"  ·  "+dur);}}
    private AlarmStore.Entry saveDraft(boolean toast){try{draft.enabled=enabled.isChecked();draft.label=label.getText().toString().trim();draft.dayMask=0;for(int i=0;i<7;i++)if(days[i].isChecked())draft.dayMask|=1<<i;draft.missionType=selectedMission;draft.steps=Math.max(1,Math.min(9999,Integer.parseInt(steps.getText().toString().trim().isEmpty()?"50":steps.getText().toString().trim())));draft.missionCount=Math.max(1,Math.min(500,Integer.parseInt(missionCount.getText().toString().trim().isEmpty()?"3":missionCount.getText().toString().trim())));draft.volume=volume.getProgress();draft.vibration=vibration.getSelectedItemPosition()==1?"STRONG":"IRREGULAR";draft=AlarmProfiles.save(this,draft);alarmId=draft.id;AlarmScheduler.reschedule(this);if(toast)Toast.makeText(this,"保存しました",Toast.LENGTH_SHORT).show();return draft;}catch(Throwable t){Toast.makeText(this,"設定値を確認してください",Toast.LENGTH_LONG).show();return null;}}
    private void pickSound(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_AUDIO);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_AUDIO||resultCode!=RESULT_OK||data==null||data.getData()==null)return;copySound(data.getData());}
    private void copySound(Uri uri){File out=null;try{String name=fileName(uri);File dir=new File(getFilesDir(),"alarm_sounds");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+".audio");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){if(in==null)throw new IOException("open");byte[]buf=new byte[65536];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}MediaPlayer p=new MediaPlayer();p.setDataSource(out.getAbsolutePath());p.prepare();long dur=p.getDuration();p.release();draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;renderSound();}catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,"この音源は読み込めません",Toast.LENGTH_LONG).show();}}
    private String fileName(Uri uri){String name="選択した音源";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.isEmpty())name=x;}}catch(Throwable ignored){}return name;}
}
