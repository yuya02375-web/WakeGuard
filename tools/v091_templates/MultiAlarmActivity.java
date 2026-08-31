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
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(Ui.dp(this,22),Ui.dp(this,20),Ui.dp(this,22),Ui.dp(this,18));outer.setBackground(Ui.screenGradient(this));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=Ui.ghostButton(this,"←");back.setMinWidth(Ui.dp(this,56));back.setOnClickListener(v->finish());top.addView(back);
        TextView title=Ui.title(this,"アラーム",28);title.setPadding(Ui.dp(this,16),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button add=Ui.button(this,"＋",true);add.setMinWidth(Ui.dp(this,58));add.setOnClickListener(v->edit(-1));top.addView(add);outer.addView(top);
        TextView hint=Ui.text(this,"アラームごとにミッション・音・振動を設定できます",13,Ui.MUTED);outer.addView(hint,Ui.gapTop(this,12));
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,70));sv.addView(list);outer.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(outer);
    }

    private void render(){list.removeAllViews();List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));for(AlarmStore.Entry e:alarms)addCard(e);}
    private void addCard(AlarmStore.Entry e){
        LinearLayout card=Ui.card(this);card.setClickable(true);card.setFocusable(true);card.setAlpha(e.enabled?1f:.52f);card.setOnClickListener(v->edit(e.id));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),40);time.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);top.addView(time,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);sw.setChecked(e.enabled);top.addView(sw);card.addView(top);

        String labelText=(e.label==null||e.label.isBlank())?(e.id==1?"メインアラーム":"アラーム"):e.label;
        TextView label=Ui.title(this,labelText,17);card.addView(label,Ui.gapTop(this,4));
        TextView repeat=Ui.text(this,repeatText(e.dayMask),13,Ui.MUTED);card.addView(repeat,Ui.gapTop(this,3));

        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView mission=Ui.pill(this,AlarmProfiles.missionIcon(e.missionType)+"  "+AlarmProfiles.missionName(e.missionType));bottom.addView(mission);
        TextView summary=Ui.text(this,AlarmProfiles.missionSummary(this,e.id),13,Ui.MUTED);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.setMargins(Ui.dp(this,10),0,0,0);bottom.addView(summary,sp);
        TextView arrow=Ui.title(this,"›",25);arrow.setTextColor(Ui.ACCENT);bottom.addView(arrow);card.addView(bottom,Ui.gapTop(this,14));

        sw.setOnClickListener(v->{e.enabled=sw.isChecked();AlarmProfiles.save(this,e);if(!e.enabled&&e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);AlarmScheduler.reschedule(this);render();});
        list.addView(card,Ui.cardParams(this));
    }
    private String repeatText(int m){if(m==0)return"1回のみ";if((m&127)==127)return"毎日";String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(n[i]);}return b.toString();}
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}
}
