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
    private long alarmId; private AlarmStore.Entry draft;
    private EditText label,steps,missionCount; private Button time,sound; private CheckBox[] days=new CheckBox[7];
    private Switch enabled; private SeekBar volume; private TextView volumeText,soundStatus,stepsLabel,countLabel,missionNote;
    private Spinner vibration,missionType;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.statusBar(this);
        alarmId=getIntent().getLongExtra("alarmId",-1L);
        draft=alarmId<0?AlarmProfiles.defaults(this):AlarmProfiles.get(this,alarmId);
        build(); render();
    }

    private void build(){
        ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,56));root.setBackground(Ui.screenGradient(this));sv.addView(root);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=Ui.ghostButton(this,"←");back.setMinWidth(Ui.dp(this,54));back.setOnClickListener(v->finish());top.addView(back);
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(Ui.dp(this,14),0,0,0);
        heading.addView(Ui.overline(this,"ALARM PROFILE"));heading.addView(Ui.title(this,alarmId<0?"新しいアラーム":"アラームを編集",25));top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        LinearLayout basic=Ui.glowCard(this);root.addView(basic,Ui.gapTop(this,18));
        enabled=new Switch(this);enabled.setText("このアラームを有効にする");enabled.setTextColor(Ui.TEXT);basic.addView(enabled);
        label=input("名前  例：学校 / 休日",InputType.TYPE_CLASS_TEXT);basic.addView(label,Ui.gapTop(this,10));
        time=Ui.button(this,"07:00",false);time.setTextSize(38);time.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());basic.addView(time,Ui.gapTop(this,10));
        TextView rt=Ui.text(this,"REPEAT",11,Ui.CYAN);rt.setTypeface(null,Typeface.BOLD);basic.addView(rt,Ui.gapTop(this,14));
        LinearLayout dayRow=new LinearLayout(this);String[]dn={"月","火","水","木","金","土","日"};
        for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(dn[i]);days[i].setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));days[i].setTextColor(Ui.TEXT);days[i].setGravity(Gravity.CENTER);dayRow.addView(days[i],new LinearLayout.LayoutParams(0,-2,1));}basic.addView(dayRow);
        TextView repeatHint=Ui.text(this,"曜日を選ばない場合は1回だけ鳴ります",12,Ui.MUTED);basic.addView(repeatHint);

        root.addView(sectionTitle("起床ミッション","MISSION ENGINE"),Ui.gapTop(this,24));
        LinearLayout mission=Ui.card(this);root.addView(mission,Ui.gapTop(this,8));
        missionType=new Spinner(this);String[]types={"歩数  •  実際に歩く","計算  •  問題を解く","連打  •  指定回数タップ","コード入力  •  表示コードを入力","ランダム  •  毎回どれか1つ"};
        ArrayAdapter<String>ma=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types){@Override public View getView(int p,View c,android.view.ViewGroup g){View v=super.getView(p,c,g);if(v instanceof TextView){((TextView)v).setTextColor(Ui.TEXT);((TextView)v).setTextSize(16);}return v;}};missionType.setAdapter(ma);mission.addView(missionType);
        missionType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>a,View v,int p,long id){refreshMissionFields();}public void onNothingSelected(android.widget.AdapterView<?>a){}});
        stepsLabel=Ui.text(this,"歩数",13,Ui.MUTED);mission.addView(stepsLabel,Ui.gapTop(this,14));steps=input("50",InputType.TYPE_CLASS_NUMBER);steps.setTextSize(28);mission.addView(steps);
        countLabel=Ui.text(this,"回数",13,Ui.MUTED);mission.addView(countLabel,Ui.gapTop(this,12));missionCount=input("3",InputType.TYPE_CLASS_NUMBER);missionCount.setTextSize(28);mission.addView(missionCount);
        missionNote=Ui.text(this,"",12,Ui.MUTED);missionNote.setPadding(0,Ui.dp(this,10),0,0);mission.addView(missionNote);

        root.addView(sectionTitle("音と振動","ALARM OUTPUT"),Ui.gapTop(this,24));
        LinearLayout audio=Ui.card(this);root.addView(audio,Ui.gapTop(this,8));
        sound=Ui.button(this,"音源を選ぶ",false);sound.setOnClickListener(v->pickSound());audio.addView(sound);
        soundStatus=Ui.text(this,"",13,Ui.MUTED);soundStatus.setPadding(0,Ui.dp(this,10),0,0);audio.addView(soundStatus);
        Button defaultSound=Ui.ghostButton(this,"標準アラーム音に戻す");defaultSound.setOnClickListener(v->{draft.soundUri="";draft.soundName="";draft.soundBytes=-1;draft.soundDurationMs=-1;renderSound();});audio.addView(defaultSound,Ui.gapTop(this,10));
        volumeText=Ui.title(this,"音量 15%",16);audio.addView(volumeText,Ui.gapTop(this,16));volume=new SeekBar(this);volume.setMax(100);audio.addView(volume);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){volumeText.setText("音量  "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        TextView vibLabel=Ui.text(this,"振動パターン",13,Ui.MUTED);audio.addView(vibLabel,Ui.gapTop(this,14));
        vibration=new Spinner(this);String[]modes={"不規則・最大強度","強い連続パルス"};ArrayAdapter<String>va=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes){@Override public View getView(int p,View c,android.view.ViewGroup g){View v=super.getView(p,c,g);if(v instanceof TextView)((TextView)v).setTextColor(Ui.TEXT);return v;}};vibration.setAdapter(va);audio.addView(vibration);

        Button test=Ui.ghostButton(this,"▶  今すぐテスト");test.setOnClickListener(v->{AlarmStore.Entry saved=saveDraft(false);if(saved==null)return;Intent s=new Intent(this,AlarmService.class).setAction(AlarmService.ACTION_FIRE_TEST).putExtra(AlarmService.EXTRA_ALARM_ID,saved.id);try{if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);}catch(Throwable t){Toast.makeText(this,"テストを開始できません",Toast.LENGTH_LONG).show();}});root.addView(test,Ui.gapTop(this,22));
        Button save=Ui.button(this,"変更を保存",true);save.setOnClickListener(v->{if(saveDraft(true)!=null)finish();});root.addView(save,Ui.gapTop(this,10));
        if(alarmId>=1000){Button del=Ui.ghostButton(this,"このアラームを削除");del.setTextColor(Ui.DANGER);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("削除しますか？").setMessage("このアラームだけ削除します。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{AlarmScheduler.cancelExtraAlarm(this,alarmId);AlarmProfiles.delete(this,alarmId);AlarmScheduler.reschedule(this);finish();}).show());root.addView(del,Ui.gapTop(this,10));}
        setContentView(sv);
    }

    private LinearLayout sectionTitle(String title,String over){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.addView(Ui.overline(this,over));l.addView(Ui.title(this,title,20));return l;}
    private EditText input(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Ui.MUTED_2);e.setTextColor(Ui.TEXT);e.setInputType(type);e.setSingleLine(true);e.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));e.setBackground(Ui.roundStroke(Ui.SURFACE_2,0xFF344363,16,this));return e;}

    private int typeIndex(String t){if("MATH".equals(t))return 1;if("TAP".equals(t))return 2;if("CODE".equals(t))return 3;if("RANDOM".equals(t))return 4;return 0;}
    private String selectedType(){int p=missionType.getSelectedItemPosition();return p==1?"MATH":p==2?"TAP":p==3?"CODE":p==4?"RANDOM":"STEPS";}
    private void refreshMissionFields(){if(steps==null)return;String t=selectedType();boolean needSteps="STEPS".equals(t)||"RANDOM".equals(t);boolean needCount=!"STEPS".equals(t);stepsLabel.setVisibility(needSteps?View.VISIBLE:View.GONE);steps.setVisibility(needSteps?View.VISIBLE:View.GONE);countLabel.setVisibility(needCount?View.VISIBLE:View.GONE);missionCount.setVisibility(needCount?View.VISIBLE:View.GONE);if("STEPS".equals(t)){stepsLabel.setText("解除に必要な歩数");missionNote.setText("専用歩数センサーでカウント。目標に達するまで解除できません。");}else if("MATH".equals(t)){countLabel.setText("正解する問題数");missionNote.setText("足し算・引き算・掛け算をランダム出題します。");}else if("TAP".equals(t)){countLabel.setText("必要なタップ回数");missionNote.setText("大きなボタンを指定回数タップすると解除できます。");}else if("CODE".equals(t)){countLabel.setText("正しく入力するコード数");missionNote.setText("表示された6桁コードを正確に入力します。");}else{stepsLabel.setText("歩数が選ばれた場合の歩数");countLabel.setText("その他ミッションの回数");missionNote.setText("鳴るたびに歩数・計算・連打・コード入力から1つ選ばれます。");}}

    private void render(){enabled.setChecked(draft.enabled);label.setText(draft.label);time.setText(String.format(Locale.JAPAN,"%02d:%02d",draft.hour,draft.minute));for(int i=0;i<7;i++)days[i].setChecked((draft.dayMask&(1<<i))!=0);steps.setText(String.valueOf(draft.steps));missionCount.setText(String.valueOf(draft.missionCount));missionType.setSelection(typeIndex(draft.missionType));volume.setProgress(draft.volume);vibration.setSelection("STRONG".equals(draft.vibration)?1:0);renderSound();refreshMissionFields();}
    private void renderSound(){if(draft.soundName==null||draft.soundName.isEmpty())soundStatus.setText("標準アラーム音");else{String size=draft.soundBytes>0?String.format(Locale.JAPAN,"%.1f MB",draft.soundBytes/1048576.0):"サイズ不明";String dur=draft.soundDurationMs>0?String.format(Locale.JAPAN,"%d:%02d",draft.soundDurationMs/60000,(draft.soundDurationMs/1000)%60):"長さ不明";soundStatus.setText("✓ "+draft.soundName+"\n"+size+"  •  "+dur);}}
    private AlarmStore.Entry saveDraft(boolean toast){try{draft.enabled=enabled.isChecked();draft.label=label.getText().toString().trim();draft.dayMask=0;for(int i=0;i<7;i++)if(days[i].isChecked())draft.dayMask|=1<<i;draft.missionType=selectedType();draft.steps=Math.max(1,Math.min(9999,Integer.parseInt(steps.getText().toString().trim().isEmpty()?"50":steps.getText().toString().trim())));draft.missionCount=Math.max(1,Math.min(500,Integer.parseInt(missionCount.getText().toString().trim().isEmpty()?"3":missionCount.getText().toString().trim())));draft.volume=volume.getProgress();draft.vibration=vibration.getSelectedItemPosition()==1?"STRONG":"IRREGULAR";draft=AlarmProfiles.save(this,draft);alarmId=draft.id;AlarmScheduler.reschedule(this);if(toast)Toast.makeText(this,"保存しました",Toast.LENGTH_SHORT).show();return draft;}catch(Throwable t){Toast.makeText(this,"設定値を確認してください",Toast.LENGTH_LONG).show();return null;}}
    private void pickSound(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_AUDIO);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_AUDIO||resultCode!=RESULT_OK||data==null||data.getData()==null)return;copySound(data.getData());}
    private void copySound(Uri uri){File out=null;try{String name=fileName(uri);File dir=new File(getFilesDir(),"alarm_sounds");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+".audio");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){if(in==null)throw new IOException("open");byte[]buf=new byte[65536];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}MediaPlayer p=new MediaPlayer();p.setDataSource(out.getAbsolutePath());p.prepare();long dur=p.getDuration();p.release();draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;renderSound();}catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,"この音源は読み込めません",Toast.LENGTH_LONG).show();}}
    private String fileName(Uri uri){String name="選択した音源";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.isEmpty())name=x;}}catch(Throwable ignored){}return name;}
}
