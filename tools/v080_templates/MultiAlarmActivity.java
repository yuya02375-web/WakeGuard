package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public class MultiAlarmActivity extends Activity {
    private LinearLayout list;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.statusBar(this);build();}
    @Override protected void onResume(){super.onResume();AlarmScheduler.reschedule(this);render();}

    private void build(){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,18));outer.setBackgroundColor(Ui.BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.button(this,"←",false);back.setOnClickListener(v->finish());top.addView(back);TextView title=Ui.title(this,"アラーム",28);title.setPadding(Ui.dp(this,14),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button add=Ui.button(this,"＋",true);add.setMinWidth(Ui.dp(this,58));add.setOnClickListener(v->edit(-1));top.addView(add);outer.addView(top);
        TextView hint=Ui.text(this,"時刻だけでなく、歩数・音・音量・振動まで1件ずつ別設定できます。",13,Ui.MUTED);hint.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,12));outer.addView(hint);
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,Ui.dp(this,60));sv.addView(list);outer.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(outer);
    }

    private void render(){list.removeAllViews();List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));for(AlarmStore.Entry e:alarms)addCard(e);}
    private void addCard(AlarmStore.Entry e){
        LinearLayout card=Ui.card(this);card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->edit(e.id));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),36);time.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);top.addView(time,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(e.enabled);top.addView(sw);card.addView(top);
        TextView label=Ui.title(this,(e.label==null||e.label.isBlank())?(e.id==1?"メインアラーム":"アラーム"):e.label,17);label.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,9));card.addView(label);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.addView(Ui.pill(this,repeatText(e.dayMask)));chips.addView(gapPill(e.steps+"歩"));chips.addView(gapPill(e.volume+"%"));card.addView(chips);
        String vib="STRONG".equals(e.vibration)?"強い連続":"不規則";String sound=(e.soundName==null||e.soundName.isEmpty())?"標準アラーム音":e.soundName;
        TextView detail=Ui.text(this,"🔊 "+sound+"  •  📳 "+vib,13,Ui.MUTED);detail.setSingleLine(true);detail.setPadding(0,Ui.dp(this,10),0,0);card.addView(detail);
        sw.setOnClickListener(v->{e.enabled=sw.isChecked();AlarmProfiles.save(this,e);if(!e.enabled&&e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);AlarmScheduler.reschedule(this);});
        list.addView(card,Ui.cardParams(this));
    }
    private TextView gapPill(String s){TextView v=Ui.pill(this,s);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(Ui.dp(this,7),0,0,0);v.setLayoutParams(p);return v;}
    private String repeatText(int m){if(m==0)return"1回のみ";if((m&127)==127)return"毎日";String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(n[i]);}return b.toString();}
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}
}
