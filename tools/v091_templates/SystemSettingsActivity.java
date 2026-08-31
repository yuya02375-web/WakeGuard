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
    private void build(){
        ScrollView sv=new ScrollView(this);sv.setBackground(Ui.screenGradient(this));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,22),Ui.dp(this,20),Ui.dp(this,22),Ui.dp(this,60));sv.addView(body);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.ghostButton(this,"←");back.setMinWidth(Ui.dp(this,56));back.setOnClickListener(v->finish());top.addView(back);TextView title=Ui.title(this,"端末設定",27);title.setPadding(Ui.dp(this,16),0,0,0);top.addView(title);body.addView(top);setContentView(sv);
    }
    private void renderStatus(){
        while(body.getChildCount()>1)body.removeViewAt(1);
        TextView note=Ui.text(this,"ロック画面で確実に鳴らすために必要な端末側の設定です。",13,Ui.MUTED);body.addView(note,Ui.gapTop(this,14));
        addStatus("正確なアラーム",AlarmScheduler.canScheduleExact(this),this::openExact);
        boolean full=true;if(Build.VERSION.SDK_INT>=34){try{NotificationManager nm=getSystemService(NotificationManager.class);full=nm!=null&&nm.canUseFullScreenIntent();}catch(Throwable ignored){full=false;}}addStatus("全画面アラーム",full,this::openFull);
        addStatus("他のアプリの上に表示",Settings.canDrawOverlays(this),this::openOverlay);
        TextView other=Ui.title(this,"その他",19);body.addView(other,Ui.gapTop(this,28));
        addButton("通知とロック画面表示",this::openNotifications);addButton("バッテリー最適化から除外",this::openBattery);addButton("アプリ情報",this::openDetails);
        TextView realme=Ui.text(this,"realmeではアプリ情報内の「ロック画面に表示」「バックグラウンドでポップアップ」「バックグラウンドアクティビティ」も許可してください。",13,Ui.MUTED);body.addView(realme,Ui.gapTop(this,18));
    }
    private void addStatus(String name,boolean ok,Runnable r){
        LinearLayout c=Ui.card(this);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView t=Ui.title(this,name,17);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));TextView mark=Ui.title(this,ok?"✓":"!",21);mark.setTextColor(ok?Ui.SUCCESS:Ui.DANGER);row.addView(mark);c.addView(row);
        TextView state=Ui.text(this,ok?"設定済み":"確認が必要です",12,ok?Ui.SUCCESS:Ui.MUTED);c.addView(state,Ui.gapTop(this,4));Button b=Ui.ghostButton(this,ok?"設定を確認":"設定を開く");b.setOnClickListener(v->r.run());c.addView(b,Ui.gapTop(this,12));body.addView(c,Ui.gapTop(this,14));
    }
    private void addButton(String s,Runnable r){Button b=Ui.ghostButton(this,s);b.setOnClickListener(v->r.run());body.addView(b,Ui.gapTop(this,12));}
    private void safe(Intent i){try{startActivity(i);}catch(Throwable t){Toast.makeText(this,"この設定画面を端末が提供していません",Toast.LENGTH_SHORT).show();}}
    private void openExact(){if(Build.VERSION.SDK_INT>=31)safe(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else openDetails();}
    private void openFull(){if(Build.VERSION.SDK_INT>=34)safe(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));else openNotifications();}
    private void openOverlay(){safe(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void openNotifications(){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());safe(i);}
    private void openBattery(){safe(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}
    private void openDetails(){safe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
}
