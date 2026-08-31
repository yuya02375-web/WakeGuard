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
import android.widget.*;
import java.io.*;
import java.util.Locale;

public class AlarmEditorActivity extends Activity {
    private static final int REQ_AUDIO=81;
    private long alarmId; private AlarmStore.Entry draft;
    private EditText label,steps; private Button time,sound; private CheckBox[] days=new CheckBox[7]; private Switch enabled; private SeekBar volume; private TextView volumeText,soundStatus; private Spinner vibration;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.statusBar(this);alarmId=getIntent().getLongExtra("alarmId",-1L);draft=alarmId<0?AlarmProfiles.defaults(this):AlarmProfiles.get(this,alarmId);build();render();}

    private void build(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,48));sv.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.button(this,"←",false);back.setOnClickListener(v->finish());top.addView(back);TextView title=Ui.title(this,alarmId<0?"新しいアラーム":"アラームを編集",25);title.setPadding(Ui.dp(this,14),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);

        LinearLayout basic=Ui.card(this);root.addView(basic,Ui.gapTop(this,18));
        enabled=new Switch(this);enabled.setText("このアラームを有効にする");enabled.setTextColor(Ui.TEXT);basic.addView(enabled);
        label=new EditText(this);label.setHint("名前  例：学校 / 休日");label.setHintTextColor(Ui.MUTED);label.setTextColor(Ui.TEXT);label.setSingleLine(true);basic.addView(label,Ui.gapTop(this,8));
        time=Ui.button(this,"07:00",false);time.setTextSize(36);time.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);time.setOnClickListener(v->new TimePickerDialog(this,(x,h,m)->{draft.hour=h;draft.minute=m;time.setText(String.format(Locale.JAPAN,"%02d:%02d",h,m));},draft.hour,draft.minute,true).show());basic.addView(time,Ui.gapTop(this,8));
        TextView rt=Ui.text(this,"繰り返し（未選択なら1回のみ）",13,Ui.MUTED);basic.addView(rt,Ui.gapTop(this,10));LinearLayout dayRow=new LinearLayout(this);String[]dn={"月","火","水","木","金","土","日"};for(int i=0;i<7;i++){days[i]=new CheckBox(this);days[i].setText(dn[i]);days[i].setTextColor(Ui.TEXT);dayRow.addView(days[i],new LinearLayout.LayoutParams(0,-2,1));}basic.addView(dayRow);

        TextView missionTitle=Ui.title(this,"起床ミッション",18);root.addView(missionTitle,Ui.gapTop(this,20));LinearLayout mission=Ui.card(this);root.addView(mission,Ui.gapTop(this,8));TextView stepLabel=Ui.text(this,"解除に必要な歩数",14,Ui.MUTED);mission.addView(stepLabel);steps=new EditText(this);steps.setInputType(InputType.TYPE_CLASS_NUMBER);steps.setTextColor(Ui.TEXT);steps.setTextSize(30);mission.addView(steps);
        TextView note=Ui.text(this,"歩数センサーで判定。設定した歩数に達するまで停止ボタンは有効になりません。",12,Ui.MUTED);mission.addView(note);

        TextView soundTitle=Ui.title(this,"音と振動",18);root.addView(soundTitle,Ui.gapTop(this,20));LinearLayout audio=Ui.card(this);root.addView(audio,Ui.gapTop(this,8));
        sound=Ui.button(this,"音源を選ぶ",false);sound.setOnClickListener(v->pickSound());audio.addView(sound);soundStatus=Ui.text(this,"",13,Ui.MUTED);soundStatus.setPadding(0,Ui.dp(this,8),0,0);audio.addView(soundStatus);
        Button defaultSound=Ui.button(this,"標準アラーム音に戻す",false);defaultSound.setOnClickListener(v->{draft.soundUri="";draft.soundName="";draft.soundBytes=-1;draft.soundDurationMs=-1;renderSound();});audio.addView(defaultSound,Ui.gapTop(this,8));
        volumeText=Ui.title(this,"音量 15%",16);audio.addView(volumeText,Ui.gapTop(this,14));volume=new SeekBar(this);volume.setMax(100);audio.addView(volume);volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){volumeText.setText("音量 "+p+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        TextView vibLabel=Ui.text(this,"振動パターン",14,Ui.MUTED);audio.addView(vibLabel,Ui.gapTop(this,12));vibration=new Spinner(this);String[]modes={"不規則・最大強度","強い連続パルス"};ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,modes){@Override public android.view.View getView(int p,android.view.View c,android.view.ViewGroup g){android.view.View v=super.getView(p,c,g);if(v instanceof TextView)((TextView)v).setTextColor(Ui.TEXT);return v;}};vibration.setAdapter(a);audio.addView(vibration);

        Button test=Ui.button(this,"▶ このアラームを今すぐテスト",false);test.setOnClickListener(v->{AlarmStore.Entry saved=saveDraft(false);if(saved==null)return;Intent s=new Intent(this,AlarmService.class).setAction(AlarmService.ACTION_FIRE_TEST).putExtra(AlarmService.EXTRA_ALARM_ID,saved.id);try{if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);}catch(Throwable t){Toast.makeText(this,"テスト開始失敗: "+t.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}});root.addView(test,Ui.gapTop(this,20));
        Button save=Ui.button(this,"保存",true);save.setOnClickListener(v->{if(saveDraft(true)!=null)finish();});root.addView(save,Ui.gapTop(this,10));
        if(alarmId>=1000){Button del=Ui.button(this,"このアラームを削除",false);del.setTextColor(Ui.DANGER);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("削除しますか？").setMessage("このアラームだけ削除します。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{AlarmScheduler.cancelExtraAlarm(this,alarmId);AlarmProfiles.delete(this,alarmId);AlarmScheduler.reschedule(this);finish();}).show());root.addView(del,Ui.gapTop(this,10));}
        setContentView(sv);
    }

    private void render(){enabled.setChecked(draft.enabled);label.setText(draft.label);time.setText(String.format(Locale.JAPAN,"%02d:%02d",draft.hour,draft.minute));for(int i=0;i<7;i++)days[i].setChecked((draft.dayMask&(1<<i))!=0);steps.setText(String.valueOf(draft.steps));volume.setProgress(draft.volume);vibration.setSelection("STRONG".equals(draft.vibration)?1:0);renderSound();}
    private void renderSound(){if(draft.soundName==null||draft.soundName.isEmpty())soundStatus.setText("標準アラーム音");else{String size=draft.soundBytes>0?String.format(Locale.JAPAN,"%.1f MB",draft.soundBytes/1048576.0):"サイズ不明";String dur=draft.soundDurationMs>0?String.format(Locale.JAPAN,"%d:%02d",draft.soundDurationMs/60000,(draft.soundDurationMs/1000)%60):"長さ不明";soundStatus.setText("✅ "+draft.soundName+"\n"+size+"  •  "+dur);}}
    private AlarmStore.Entry saveDraft(boolean toast){try{draft.enabled=enabled.isChecked();draft.label=label.getText().toString().trim();draft.dayMask=0;for(int i=0;i<7;i++)if(days[i].isChecked())draft.dayMask|=1<<i;draft.steps=Math.max(1,Math.min(9999,Integer.parseInt(steps.getText().toString().trim())));draft.volume=volume.getProgress();draft.vibration=vibration.getSelectedItemPosition()==1?"STRONG":"IRREGULAR";draft=AlarmProfiles.save(this,draft);alarmId=draft.id;AlarmScheduler.reschedule(this);if(toast)Toast.makeText(this,"保存しました",Toast.LENGTH_SHORT).show();return draft;}catch(Throwable t){Toast.makeText(this,"設定を確認してください",Toast.LENGTH_LONG).show();return null;}}
    private void pickSound(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_AUDIO);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_AUDIO||resultCode!=RESULT_OK||data==null||data.getData()==null)return;copySound(data.getData());}
    private void copySound(Uri uri){File out=null;try{String name=fileName(uri);File dir=new File(getFilesDir(),"alarm_sounds");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+".audio");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){if(in==null)throw new IOException("open");byte[]buf=new byte[65536];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}MediaPlayer p=new MediaPlayer();p.setDataSource(out.getAbsolutePath());p.prepare();long dur=p.getDuration();p.release();draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;renderSound();}catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,"この音源は読み込めません: "+t.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}}
    private String fileName(Uri uri){String name="選択した音源";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.isEmpty())name=x;}}catch(Throwable ignored){}return name;}
}
