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
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,18));outer.setBackground(Ui.screenGradient(this));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.ghostButton(this,"←");back.setMinWidth(Ui.dp(this,54));back.setOnClickListener(v->finish());top.addView(back);
        LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.VERTICAL);h.setPadding(Ui.dp(this,14),0,0,0);h.addView(Ui.overline(this,"ALARM MATRIX"));h.addView(Ui.title(this,"アラーム",28));top.addView(h,new LinearLayout.LayoutParams(0,-2,1));Button add=Ui.button(this,"＋",true);add.setMinWidth(Ui.dp(this,58));add.setOnClickListener(v->edit(-1));top.addView(add);outer.addView(top);
        TextView hint=Ui.text(this,"時刻・ミッション・音源・音量・振動をアラームごとに独立設定。",13,Ui.MUTED);hint.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,14));outer.addView(hint);
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,0,0,Ui.dp(this,60));sv.addView(list);outer.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(outer);
    }

    private void render(){list.removeAllViews();List<AlarmStore.Entry> alarms=AlarmProfiles.all(this);alarms.sort(Comparator.comparingInt((AlarmStore.Entry e)->e.hour).thenComparingInt(e->e.minute));for(AlarmStore.Entry e:alarms)addCard(e);}
    private void addCard(AlarmStore.Entry e){
        LinearLayout card=e.enabled?Ui.glowCard(this):Ui.card(this);card.setClickable(true);card.setFocusable(true);card.setAlpha(e.enabled?1f:.58f);card.setOnClickListener(v->edit(e.id));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView time=Ui.title(this,String.format(Locale.JAPAN,"%02d:%02d",e.hour,e.minute),38);time.setTypeface(android.graphics.Typeface.MONOSPACE,android.graphics.Typeface.BOLD);top.addView(time,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(e.enabled);top.addView(sw);card.addView(top);
        String labelText=(e.label==null||e.label.isBlank())?(e.id==1?"メインアラーム":"アラーム"):e.label;TextView label=Ui.title(this,labelText,17);card.addView(label);
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);TextView mission=Ui.accentPill(this,AlarmProfiles.missionIcon(e.missionType)+"  "+AlarmProfiles.missionName(e.missionType));meta.addView(mission);TextView rep=Ui.pill(this,repeatText(e.dayMask));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);rp.setMargins(Ui.dp(this,8),0,0,0);meta.addView(rep,rp);card.addView(meta,Ui.gapTop(this,12));
        TextView missionDetail=Ui.text(this,AlarmProfiles.missionSummary(this,e.id)+"  •  音量 "+e.volume+"%",13,Ui.TEXT);card.addView(missionDetail,Ui.gapTop(this,11));
        String vib="STRONG".equals(e.vibration)?"強い連続":"不規則";String sound=(e.soundName==null||e.soundName.isEmpty())?"標準アラーム音":e.soundName;TextView detail=Ui.text(this,"🔊 "+sound+"\n📳 "+vib,12,Ui.MUTED);detail.setMaxLines(2);card.addView(detail,Ui.gapTop(this,6));
        TextView tap=Ui.text(this,"タップして詳細を編集  ›",12,Ui.CYAN);tap.setGravity(Gravity.END);card.addView(tap,Ui.gapTop(this,12));
        sw.setOnClickListener(v->{e.enabled=sw.isChecked();AlarmProfiles.save(this,e);if(!e.enabled&&e.id>=1000)AlarmScheduler.cancelExtraAlarm(this,e.id);AlarmScheduler.reschedule(this);render();});
        list.addView(card,Ui.cardParams(this));
    }
    private String repeatText(int m){if(m==0)return"1回のみ";if((m&127)==127)return"毎日";String[]n={"月","火","水","木","金","土","日"};StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)if((m&(1<<i))!=0){if(b.length()>0)b.append(" ");b.append(n[i]);}return b.toString();}
    private void edit(long id){startActivity(new Intent(this,AlarmEditorActivity.class).putExtra("alarmId",id));}
}
