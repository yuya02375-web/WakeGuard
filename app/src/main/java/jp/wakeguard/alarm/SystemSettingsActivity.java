package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;

public class SystemSettingsActivity extends Activity {
    private LinearLayout body;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);build();}
    @Override protected void onResume(){super.onResume();renderStatus();}
    private void build(){ScrollView sv=new ScrollView(this);sv.setBackground(Ui.screenGradient(this));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,60));sv.addView(body);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.ghostButton(this,"←");back.setMinWidth(Ui.dp(this,56));back.setOnClickListener(v->finish());top.addView(back);TextView title=Ui.title(this,"設定",27);title.setPadding(Ui.dp(this,16),0,0,0);top.addView(title);body.addView(top);setContentView(sv);Ui.applySystemBarInsets(this,sv);}
    private void renderStatus(){
        while(body.getChildCount()>1)body.removeViewAt(1);
        TextView app=Ui.title(this,"アプリ設定",19);body.addView(app,Ui.gapTop(this,20));addLanguageRow();addButton("時計の詳細設定",()->Ui.launchNoAnimation(this,new Intent(this,ClockSettingsActivity.class)));
        TextView backup=Ui.title(this,"バックアップ",19);body.addView(backup,Ui.gapTop(this,28));
        TextView backupNote=Ui.text(this,"アラーム・時間記録・ストリークはAndroidのバックアップへ自動保存します。手動ファイルも作成できます。",13,Ui.MUTED);body.addView(backupNote,Ui.gapTop(this,8));
        addButton("バックアップを書き出す",this::exportBackup);addButton("バックアップを読み込む",this::importBackup);
        TextView device=Ui.title(this,"端末設定",19);body.addView(device,Ui.gapTop(this,28));TextView note=Ui.text(this,"ロック画面でアラーム画面・動画を確実に表示するために必要な端末側の設定です。",13,Ui.MUTED);body.addView(note,Ui.gapTop(this,8));
        addStatus("正確なアラーム",AlarmScheduler.canScheduleExact(this),this::openExact);boolean full=true;if(Build.VERSION.SDK_INT>=34){try{NotificationManager nm=getSystemService(NotificationManager.class);full=nm!=null&&nm.canUseFullScreenIntent();}catch(Throwable ignored){full=false;}}addStatus("全画面アラーム",full,this::openFull);addStatus("他のアプリの上に表示",Settings.canDrawOverlays(this),this::openOverlay);
        TextView other=Ui.title(this,"その他",19);body.addView(other,Ui.gapTop(this,28));addButton("通知とロック画面表示",this::openNotifications);addButton("バッテリー最適化から除外",this::openBattery);addButton("アプリ情報",this::openDetails);TextView realme=Ui.text(this,"realmeではアプリ情報内の「ロック画面に表示」「バックグラウンドでポップアップ」「バックグラウンドアクティビティ」も許可してください。",13,Ui.MUTED);body.addView(realme,Ui.gapTop(this,18));
    }
    private static final int REQ_EXPORT_BACKUP=801;
    private static final int REQ_IMPORT_BACKUP=802;

    private void exportBackup(){
        try{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"IGNIDO-Wake-backup.json");
            startActivityForResult(i,REQ_EXPORT_BACKUP);
        }catch(Throwable t){Toast.makeText(this,"バックアップ画面を開けませんでした",Toast.LENGTH_SHORT).show();}
    }
    private void importBackup(){
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
            startActivityForResult(i,REQ_IMPORT_BACKUP);
        }catch(Throwable t){Toast.makeText(this,"バックアップ画面を開けませんでした",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_EXPORT_BACKUP){
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IllegalStateException();BackupVault.writeBackup(this,out);Toast.makeText(this,"バックアップを書き出しました",Toast.LENGTH_SHORT).show();}
            catch(Throwable t){Toast.makeText(this,"バックアップを書き出せませんでした",Toast.LENGTH_LONG).show();}
        }else if(requestCode==REQ_IMPORT_BACKUP){
            new AlertDialog.Builder(this).setTitle("バックアップを復元").setMessage("現在のアラーム・時間記録・ストリークをバックアップ内容で置き換えます。")
                    .setNegativeButton("キャンセル",null).setPositiveButton("復元",(d,w)->{
                        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IllegalStateException();BackupVault.restore(this,in);BackupVault.afterRestore(this);Toast.makeText(this,"復元しました",Toast.LENGTH_SHORT).show();renderStatus();}
                        catch(Throwable t){Toast.makeText(this,"バックアップを復元できませんでした",Toast.LENGTH_LONG).show();}
                    }).show();
        }
    }

    private void addLanguageRow(){LinearLayout row=Ui.row(this);row.setOnClickListener(v->showLanguageDialog());LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);texts.addView(Ui.text(this,"アプリの言語",16,Ui.TEXT));texts.addView(Ui.text(this,I18n.selectedLanguageLabel(this),12,Ui.MUTED),Ui.gapTop(this,3));row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,"›",24,Ui.MUTED));body.addView(row);body.addView(Ui.divider(this));}
    private void showLanguageDialog(){final Dialog d=new Dialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18));box.setBackground(Ui.round(Ui.SURFACE,18,this));box.addView(Ui.title(this,"アプリの言語",20));String[][] items={{"system","システムに合わせる"},{"ja","日本語"},{"en","英語"},{"ko","韓国語"}};for(String[] x:items){Button b=Ui.ghostButton(this,x[1]);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(16);b.setOnClickListener(v->{I18n.setLanguage(this,x[0]);d.dismiss();recreate();});box.addView(b,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));}d.setContentView(box);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.88f),-2);}}
    private void addStatus(String name,boolean ok,Runnable r){LinearLayout row=Ui.row(this);row.setOnClickListener(v->r.run());LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.addView(Ui.text(this,name,16,Ui.TEXT));text.addView(Ui.text(this,ok?"設定済み":"確認が必要です",12,ok?Ui.SUCCESS:Ui.MUTED),Ui.gapTop(this,3));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,"›",24,ok?Ui.MUTED:Ui.ACCENT));body.addView(row,Ui.gapTop(this,8));body.addView(Ui.divider(this));}
    private void addButton(String s,Runnable r){LinearLayout row=Ui.row(this);row.setOnClickListener(v->r.run());TextView t=Ui.text(this,s,16,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,"›",24,Ui.MUTED));body.addView(row,Ui.gapTop(this,2));body.addView(Ui.divider(this));}
    private void safe(Intent i){try{startActivity(i);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"この設定画面を端末が提供していません"),Toast.LENGTH_SHORT).show();}}
    private void openExact(){if(Build.VERSION.SDK_INT>=31)safe(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));else openDetails();}
    private void openFull(){if(Build.VERSION.SDK_INT>=34)safe(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));else openNotifications();}
    private void openOverlay(){safe(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void openNotifications(){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());safe(i);}
    private void openBattery(){safe(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}
    private void openDetails(){safe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
}
