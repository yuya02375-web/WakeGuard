package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class SystemSettingsActivity extends Activity {
    private LinearLayout body;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.statusBar(this);build();}
    @Override protected void onResume(){super.onResume();renderStatus();}
    private void build(){ScrollView sv=new ScrollView(this);sv.setBackgroundColor(Ui.BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,50));sv.addView(body);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.button(this,"←",false);back.setOnClickListener(v->finish());top.addView(back);TextView title=Ui.title(this,"端末設定",26);title.setPadding(Ui.dp(this,14),0,0,0);top.addView(title);body.addView(top);setContentView(sv);}
    private void renderStatus(){while(body.getChildCount()>1)body.removeViewAt(1);TextView note=Ui.text(this,"WakeGuardがロック画面で確実に鳴るためのAndroid / realme側設定です。",14,Ui.MUTED);body.addView(note,Ui.gapTop(this,14));
        addStatus("正確なアラーム",AlarmScheduler.canScheduleExact(this),this::openExact);
        boolean full=true;if(Build.VERSION.SDK_INT>=34){try{NotificationManager nm=getSystemService(NotificationManager.class);full=nm!=null&&nm.canUseFullScreenIntent();}catch(Throwable ignored){full=false;}}addStatus("全画面アラーム",full,this::openFull);
        addStatus("他のアプリの上に表示",Settings.canDrawOverlays(this),this::openOverlay);
        addButton("通知 / ロック画面表示の設定",this::openNotifications);addButton("バッテリー最適化から除外",this::openBattery);addButton("アプリ情報（realmeの追加権限）",this::openDetails);
        TextView realme=Ui.text(this,"realmeではアプリ情報内の「ロック画面に表示」「バックグラウンドでポップアップ」、さらにバックグラウンドアクティビティ許可も確認してください。",13,Ui.MUTED);body.addView(realme,Ui.gapTop(this,16));}
    private void addStatus(String name,boolean ok,Runnable r){LinearLayout c=Ui.card(this);TextView t=Ui.title(this,(ok?"✅ ":"⚠ ")+name,17);t.setTextColor(ok?Ui.ACCENT:Ui.DANGER);c.addView(t);Button b=Ui.button(this,ok?"設定を開く":"許可する / 設定を開く",false);b.setOnClickListener(v->r.run());c.addView(b,Ui.gapTop(this,10));body.addView(c,Ui.gapTop(this,12));}
    private void addButton(String s,Runnable r){Button b=Ui.button(this,s,false);b.setOnClickListener(v->r.run());body.addView(b,Ui.gapTop(this,10));}
    private void safe(Intent i){try{startActivity(i);}catch(Throwable t){Toast.makeText(this,"この設定画面を端末が提供していません",Toast.LENGTH_SHORT).show();}}
    private void openExact(){if(Build.VERSION.SDK_INT>=31)safe(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else openDetails();}
    private void openFull(){if(Build.VERSION.SDK_INT>=34)safe(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));else openNotifications();}
    private void openOverlay(){safe(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void openNotifications(){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());safe(i);}
    private void openBattery(){safe(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}
    private void openDetails(){safe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
}
