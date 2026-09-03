from pathlib import Path
import runpy, re

runpy.run_path("tools/patch_v119_fix2.py", run_name="__main__")
root=Path("WakeGuard")
java=root/"app/src/main/java/jp/wakeguard/alarm"
res=root/"app/src/main/res"

# ---------- Runtime localization layer (system / JA / EN / KO) ----------
(java/"I18n.java").write_text(r'''package jp.wakeguard.alarm;

import android.app.LocaleManager;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public final class I18n {
    private static final String PREF="wakeguard_i18n", KEY="language";
    private static final HashMap<String,String[]> MAP=new HashMap<>();
    static {
        // Japanese source text -> English, Korean. Japanese remains the canonical source text.
        put("アラーム","Alarm","알람"); put("時計","Clock","시계"); put("世界時計","World clock","세계 시계");
        put("タイマー","Timer","타이머"); put("ストップウォッチ","Stopwatch","스톱워치"); put("設定","Settings","설정");
        put("端末設定","Device settings","기기 설정"); put("アプリ設定","App settings","앱 설정"); put("その他","Other","기타");
        put("アプリの言語","App language","앱 언어"); put("システムに合わせる","Use system language","시스템 언어 사용");
        put("日本語","Japanese","일본어"); put("英語","English","영어"); put("韓国語","Korean","한국어");
        put("時計の詳細設定","Clock settings","시계 상세 설정"); put("表示","Display","표시"); put("アナログ時計","Analog clock","아날로그 시계");
        put("24時間表示","24-hour time","24시간 표시"); put("文字盤に数字を表示","Show dial numbers","문자판 숫자 표시");
        put("分の目盛りを表示","Show minute marks","분 눈금 표시"); put("秒針を表示","Show second hand","초침 표시");
        put("日付・タイムゾーン情報を表示","Show date and time-zone details","날짜·시간대 정보 표시");
        put("画面を点灯したままにする","Keep screen on","화면 계속 켜기"); put("数字の大きさ","Number size","숫자 크기");
        put("小","Small","작게"); put("中","Medium","보통"); put("大","Large","크게");
        put("初期表示","Default view","기본 표시"); put("デジタル","Digital","디지털"); put("アナログ","Analog","아날로그");
        put("時計設定をリセット","Reset clock settings","시계 설정 초기화"); put("リセットしました","Settings reset","설정을 초기화했습니다");
        put("デジタル / アナログ","Digital / Analog","디지털 / 아날로그"); put("左右スワイプでも切り替えできます","You can also switch by swiping left or right","좌우 스와이프로도 전환할 수 있습니다");
        put("現在のタイムゾーン","Current time zone","현재 시간대"); put("登録した都市","Saved cities","등록한 도시");
        put("都市 / タイムゾーンを追加","Add city / time zone","도시 / 시간대 추가"); put("例: Seoul / London / Asia/Tokyo","Example: Seoul / London / Asia/Tokyo","예: Seoul / London / Asia/Tokyo");
        put("閉じる","Close","닫기"); put("削除","Delete","삭제"); put("保存","Save","저장"); put("キャンセル","Cancel","취소");
        put("開始","Start","시작"); put("スタート","Start","시작"); put("一時停止","Pause","일시정지"); put("再開","Resume","재개");
        put("リセット","Reset","초기화"); put("ラップ","Lap","랩"); put("ラップはまだありません","No laps yet","아직 랩이 없습니다");
        put("経過時間","Elapsed time","경과 시간"); put("残り時間","Time remaining","남은 시간"); put("終了","Finished","종료");
        put("一時停止中","Paused","일시정지 중"); put("もう一度","Again","다시"); put("今回だけ","One-time","이번만");
        put("保存したタイマーはありません","No saved timers","저장된 타이머가 없습니다"); put("タイマーを作成","Create timer","타이머 만들기");
        put("名前（任意）","Name (optional)","이름(선택)"); put("時間","Time","시간"); put("このタイマーを保存","Save this timer","이 타이머 저장");
        put("この時間を保存","Save this duration","이 시간 저장"); put("すぐ使う","Quick start","바로 사용"); put("よく使う時間","Common durations","자주 쓰는 시간");
        put("時間を設定してください","Set a duration","시간을 설정하세요"); put("時間を入力してください","Enter a duration","시간을 입력하세요");
        put("音: 標準","Sound: Default","소리: 기본"); put("選択した音","Selected sound","선택한 소리"); put("音声ファイルを開けません","Unable to open the audio file","오디오 파일을 열 수 없습니다");
        put("タイマー通知を表示できません","Timer notifications are disabled","타이머 알림을 표시할 수 없습니다");
        put("タイマー実行中の残り時間を通知に常時表示するには、WakeGuardの通知を許可してください。","Allow WakeGuard notifications to keep the running timer visible in notifications.","실행 중인 타이머의 남은 시간을 알림에 계속 표시하려면 WakeGuard 알림을 허용하세요.");
        put("通知設定を開く","Open notification settings","알림 설정 열기");
        put("実行中のタイマー","Running timers","실행 중인 타이머"); put("タイマーの残り時間を常に表示します","Keeps timer time remaining visible","타이머 남은 시간을 계속 표시합니다");
        put("タイマー終了","Timer finished","타이머 종료"); put("WakeGuardのタイマー終了音","WakeGuard timer sound","WakeGuard 타이머 종료음");
        put("設定した時間になりました","Time is up","설정한 시간이 되었습니다");
        put("アラームはありません。右上の＋から追加できます。","No alarms. Tap + at the top right to add one.","알람이 없습니다. 오른쪽 위 +에서 추가할 수 있습니다.");
        put("次のアラームはありません","No upcoming alarms","다음 알람이 없습니다"); put("毎日","Every day","매일"); put("1回のみ","Once","한 번만");
        put("🔥  ストリーク","🔥  Streak","🔥  스트릭"); put("ストリーク","Streak","스트릭"); put("カレンダー","Calendar","캘린더"); put("記録","History","기록");
        put("成功した起床","Successful wake-ups","성공한 기상"); put("最高ストリーク","Best streak","최고 스트릭");
        put("成功した本番アラームの日だけ記録します。テストアラームはストリークに入りません。","Only successful real alarms count. Test alarms do not affect your streak.","성공한 실제 알람만 기록됩니다. 테스트 알람은 스트릭에 포함되지 않습니다.");
        put("伝説の炎を維持中","Legendary flame active","전설의 불꽃 유지 중"); put("最初の1日で火が灯る","The flame starts on day 1","첫날부터 불꽃이 켜집니다");
        put("成長段階  1日 → 3日 → 7日 → 14日 → 30日","Growth: day 1 → 3 → 7 → 14 → 30","성장 단계  1일 → 3일 → 7일 → 14일 → 30일");
        put("アラームを追加","Add alarm","알람 추가"); put("アラームを編集","Edit alarm","알람 편집"); put("アラームを有効にする","Enable alarm","알람 사용");
        put("基本設定","Basic settings","기본 설정"); put("名前","Name","이름"); put("繰り返し","Repeat","반복"); put("曜日を選ばなければ1回だけ鳴ります","If no days are selected, it rings once","요일을 선택하지 않으면 한 번만 울립니다");
        put("解除方法","Dismiss method","해제 방법"); put("ミッション","Mission","미션"); put("ミッションを選択","Choose mission","미션 선택");
        put("歩数","Steps","걸음 수"); put("計算","Math","계산"); put("記憶","Memory","기억"); put("コード入力","Code entry","코드 입력");
        put("文章入力","Text entry","문장 입력"); put("連打","Rapid tap","연타"); put("長押し","Press and hold","길게 누르기"); put("シェイク","Shake","흔들기"); put("ランダム","Random","랜덤");
        put("音と振動","Sound & vibration","소리 및 진동"); put("アラーム音","Alarm sound","알람 소리"); put("標準アラーム音","Default alarm sound","기본 알람 소리");
        put("標準音に戻す","Use default sound","기본 소리로 되돌리기"); put("選択した音源","Selected audio","선택한 오디오"); put("振動","Vibration","진동");
        put("アラームを削除","Delete alarm","알람 삭제"); put("このアラームだけ削除します。","Only this alarm will be deleted.","이 알람만 삭제됩니다.");
        put("このアラームをテスト","Test this alarm","이 알람 테스트"); put("保存しました","Saved","저장했습니다"); put("設定値を確認してください","Check the settings","설정값을 확인하세요");
        put("この音源は読み込めません","Unable to load this audio","이 오디오를 불러올 수 없습니다"); put("テストを開始できません","Unable to start the test","테스트를 시작할 수 없습니다");
        put("アラームを停止","Dismiss alarm","알람 해제"); put("今日も起きる","Wake up today","오늘도 일어나기"); put("起きて一日を始める","Get up and start the day","일어나 하루 시작하기");
        put("目を覚まして動く","Wake up and move","잠을 깨고 움직이기"); put("朝の準備を始める","Start getting ready","아침 준비 시작하기"); put("二度寝しない","Don't go back to sleep","다시 자지 않기");
        put("ミッションを完了してください","Complete the mission","미션을 완료하세요"); put("このミッションを完了するとアラームを解除できます","Complete this mission to dismiss the alarm","이 미션을 완료하면 알람을 해제할 수 있습니다");
        put("戻る・ホームでは停止しません","Back/Home will not stop the alarm","뒤로/홈 버튼으로는 알람이 멈추지 않습니다"); put("完了","Done","완료"); put("完了しました","Completed","완료했습니다");
        put("確認","Confirm","확인"); put("回答","Answer","답"); put("違います。もう一度。","Incorrect. Try again.","틀렸습니다. 다시 시도하세요.");
        put("コードが違います","Incorrect code","코드가 다릅니다"); put("文章が一致しません","Text does not match","문장이 일치하지 않습니다"); put("答えを入力してください","Enter the answer","답을 입력하세요");
        put("スマホを持って歩いてください","Walk while carrying your phone","휴대폰을 들고 걸으세요"); put("スマホを大きく振ってください","Shake the phone firmly","휴대폰을 크게 흔드세요");
        put("歩数センサーを利用できません","Step sensor is unavailable","걸음 수 센서를 사용할 수 없습니다"); put("加速度センサーを利用できません","Accelerometer is unavailable","가속도 센서를 사용할 수 없습니다");
        put("身体活動権限を確認してください","Check physical activity permission","신체 활동 권한을 확인하세요"); put("シェイクを検出できません","Unable to detect shaking","흔들기를 감지할 수 없습니다");
        put("正確なアラーム","Exact alarms","정확한 알람"); put("全画面アラーム","Full-screen alarms","전체 화면 알람"); put("他のアプリの上に表示","Display over other apps","다른 앱 위에 표시");
        put("通知とロック画面表示","Notifications & lock screen","알림 및 잠금 화면 표시"); put("バッテリー最適化から除外","Exclude from battery optimization","배터리 최적화 제외"); put("アプリ情報","App info","앱 정보");
        put("設定済み","Configured","설정됨"); put("確認が必要です","Needs attention","확인 필요"); put("この設定画面を端末が提供していません","This settings page is unavailable on this device","이 설정 화면은 기기에서 제공하지 않습니다");
        put("ロック画面で確実に鳴らすために必要な端末側の設定です。","Device settings required for reliable lock-screen alarms.","잠금 화면에서 안정적으로 울리기 위해 필요한 기기 설정입니다.");
        put("realmeではアプリ情報内の「ロック画面に表示」「バックグラウンドでポップアップ」「バックグラウンドアクティビティ」も許可してください。","On realme, also allow lock-screen display, background pop-ups, and background activity in App info.","realme에서는 앱 정보에서 잠금 화면 표시, 백그라운드 팝업, 백그라운드 활동도 허용하세요.");
        put("月","Mon","월"); put("火","Tue","화"); put("水","Wed","수"); put("木","Thu","목"); put("金","Fri","금"); put("土","Sat","토"); put("日","Sun","일");
        put("時","h","시"); put("分","min","분"); put("秒","sec","초"); put("歩","steps","걸음"); put("回","times","회"); put("問","questions","문제"); put("文","sentences","문장");
        put("東京","Tokyo","도쿄"); put("ソウル","Seoul","서울"); put("ニューヨーク","New York","뉴욕"); put("ロサンゼルス","Los Angeles","로스앤젤레스");
        put("シカゴ","Chicago","시카고"); put("ロンドン","London","런던"); put("パリ","Paris","파리"); put("ベルリン","Berlin","베를린"); put("ドバイ","Dubai","두바이");
        put("デリー / インド","Delhi / India","델리 / 인도"); put("バンコク","Bangkok","방콕"); put("シンガポール","Singapore","싱가포르"); put("香港","Hong Kong","홍콩"); put("上海","Shanghai","상하이"); put("シドニー","Sydney","시드니"); put("ホノルル","Honolulu","호놀룰루");
    }
    private static void put(String ja,String en,String ko){MAP.put(ja,new String[]{en,ko});}
    private I18n(){}

    public static String language(Context c){
        String pref=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"system");
        if(pref!=null&&!"system".equals(pref))return pref;
        try{Locale l=c.getResources().getConfiguration().getLocales().get(0);String x=l.getLanguage();if("ja".equals(x)||"ko".equals(x)||"en".equals(x))return x;}catch(Throwable ignored){}
        String x=Locale.getDefault().getLanguage(); return "ja".equals(x)||"ko".equals(x)?x:"en";
    }
    public static Locale locale(Context c){String l=language(c);return "ja".equals(l)?Locale.JAPAN:"ko".equals(l)?Locale.KOREA:Locale.ENGLISH;}
    public static void setLanguage(Context c,String code){
        if(code==null)code="system";c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,code).apply();
        if(Build.VERSION.SDK_INT>=33){try{LocaleManager lm=c.getSystemService(LocaleManager.class);if(lm!=null)lm.setApplicationLocales("system".equals(code)?LocaleList.getEmptyLocaleList():LocaleList.forLanguageTags(code));}catch(Throwable ignored){}}
    }
    public static String selectedLanguageLabel(Context c){String x=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"system");if("ja".equals(x))return tr(c,"日本語");if("ko".equals(x))return tr(c,"韓国語");if("en".equals(x))return tr(c,"英語");return tr(c,"システムに合わせる");}

    public static String tr(Context c,String s){
        if(s==null||s.isEmpty())return s;String lang=language(c);if("ja".equals(lang))return s;String[] v=MAP.get(s);if(v!=null)return "ko".equals(lang)?v[1]:v[0];
        // Dynamic UI strings. Only known app-generated patterns are transformed; user-entered labels are left alone.
        if(s.startsWith("次は "))return ("ko".equals(lang)?"다음: ":"Next: ")+s.substring(3);
        if(s.startsWith("音: "))return ("ko".equals(lang)?"소리: ":"Sound: ")+s.substring(3);
        if(s.startsWith("タイマー音: "))return ("ko".equals(lang)?"타이머 소리: ":"Timer sound: ")+s.substring(7);
        if(s.startsWith("終了予定  "))return ("ko".equals(lang)?"종료 예정  ":"Ends at  ")+s.substring(6);
        if(s.startsWith("今回だけ  ·  "))return ("ko".equals(lang)?"이번만  ·  ":"One-time  ·  ")+tr(c,s.substring(8));
        if(s.startsWith("あと "))return ("ko".equals(lang)?"남은 ":"Remaining ")+s.substring(3);
        String out=s;
        if(out.contains("端末との差 "))out=out.replace("端末との差 ","ko".equals(lang)?"기기와 차이 ":"Difference ");
        if(out.contains("夏時間 "))out=out.replace("夏時間 ","ko".equals(lang)?"서머타임 ":"DST ");
        if(out.contains("合計 "))out=out.replace("合計 ","ko".equals(lang)?"합계 ":"Total ");
        Matcher m=Pattern.compile("^(\\d+)時間(?: (\\d+)分)?$").matcher(out);if(m.matches())return m.group(1)+("ko".equals(lang)?"시간":"h")+(m.group(2)==null?"":" "+m.group(2)+("ko".equals(lang)?"분":"m"));
        m=Pattern.compile("^(\\d+)分(?: (\\d+)秒)?$").matcher(out);if(m.matches())return m.group(1)+("ko".equals(lang)?"분":"m")+(m.group(2)==null?"":" "+m.group(2)+("ko".equals(lang)?"초":"s"));
        m=Pattern.compile("^(\\d+)秒$").matcher(out);if(m.matches())return m.group(1)+("ko".equals(lang)?"초":"s");
        m=Pattern.compile("^(\\d+)日$").matcher(out);if(m.matches())return m.group(1)+("ko".equals(lang)?"일":" days");
        m=Pattern.compile("^🔥 (\\d+)日  ·  最高 (\\d+)日$").matcher(out);if(m.matches())return "ko".equals(lang)?"🔥 "+m.group(1)+"일  ·  최고 "+m.group(2)+"일":"🔥 "+m.group(1)+" days  ·  Best "+m.group(2)+" days";
        m=Pattern.compile("^([0-9.]+) / (\\d+) 秒$").matcher(out);if(m.matches())return m.group(1)+" / "+m.group(2)+("ko".equals(lang)?"초":" sec");
        return out;
    }
    public static String datePattern(Context c,String kind){String l=language(c);if("month".equals(kind))return "ja".equals(l)?"yyyy年 M月":"ko".equals(l)?"yyyy년 M월":"MMMM yyyy";if("next".equals(kind))return "ja".equals(l)?"M月d日(E) H:mm":"ko".equals(l)?"M월 d일(E) H:mm":"EEE, MMM d, H:mm";if("short".equals(kind))return "ja".equals(l)?"yyyy/MM/dd (E)":"ko".equals(l)?"yyyy.MM.dd (E)":"EEE, MMM d, yyyy";return "ja".equals(l)?"yyyy年M月d日 (E)":"ko".equals(l)?"yyyy년 M월 d일 (E)":"EEE, MMM d, yyyy";}

    public static void localizeTree(View v){
        if(v==null)return;
        if(v instanceof EditText){EditText e=(EditText)v;CharSequence h=e.getHint();if(h!=null){String x=tr(e.getContext(),String.valueOf(h));if(!x.contentEquals(h))e.setHint(x);}}
        else if(v instanceof TextView){TextView t=(TextView)v;CharSequence old=t.getText();if(old!=null){String x=tr(t.getContext(),String.valueOf(old));if(!x.contentEquals(old))t.setText(x);}}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)localizeTree(g.getChildAt(i));}
    }
}
''',encoding="utf-8")

# ---------- UI primitives: translate every programmatic TextView/Button and keep dynamic labels localized ----------
p=java/"Ui.java"; s=p.read_text(encoding="utf-8")
s=s.replace('v.setText(s); v.setTextSize(sp); v.setTextColor(color);','v.setText(I18n.tr(a,s)); v.setTextSize(sp); v.setTextColor(color);')
s=s.replace('b.setText(s); b.setAllCaps(false); b.setTextSize(15);','b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(15);')
s=s.replace('b.setText(s); b.setAllCaps(false); b.setTextSize(14);','b.setText(I18n.tr(a,s)); b.setAllCaps(false); b.setTextSize(14);')
s=s.replace('b.setAllCaps(false); b.setText(text); b.setTextSize(11.5f);','b.setAllCaps(false); b.setText(I18n.tr(a,text)); b.setTextSize(11.5f);')
needle='''        try { root.requestApplyInsets(); } catch (Throwable ignored) {}\n    }'''
repl='''        try { root.requestApplyInsets(); } catch (Throwable ignored) {}\n        try {\n            I18n.localizeTree(root);\n            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> I18n.localizeTree(root));\n        } catch (Throwable ignored) {}\n    }'''
if needle not in s: raise SystemExit('Ui applySystemBarInsets anchor not found')
s=s.replace(needle,repl,1)
p.write_text(s,encoding="utf-8")

# ---------- Clock face: numbered analog dial, segmented one-tap switch, swipe, detailed display preferences ----------
(java/"ClockFaceActivity.java").write_text(r'''package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ClockFaceActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String mode="world",zoneId=""; private long timerId=-1L;
    private TextView digital,detail; private AnalogFace face; private Button digitalBtn,analogBtn;
    private boolean analog=false;
    private final Runnable tick=new Runnable(){@Override public void run(){update();handler.postDelayed(this,"stopwatch".equals(mode)?50L:200L);}};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this);
        Intent i=getIntent(); if(i!=null){String m=i.getStringExtra("clock_mode");if(m!=null)mode=m;String z=i.getStringExtra("zone");if(z!=null)zoneId=z;timerId=i.getLongExtra("timer_id",-1L);}
        analog=prefs().getBoolean("clock_face_analog",false);build(); update();
    }
    @Override protected void onResume(){super.onResume();applyPrefs();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(tick);}
    @Override public void onBackPressed(){Ui.finishNoAnimation(this);}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);root.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,18));
        LinearLayout top=Ui.row(this);top.setPadding(0,0,0,0);Button back=Ui.ghostButton(this,"‹");back.setTextSize(28);back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,50)));
        TextView title=Ui.title(this,title(),23);title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        Button settings=Ui.ghostButton(this,"⚙");settings.setTextSize(23);settings.setOnClickListener(v->Ui.launchNoAnimation(this,new Intent(this,ClockSettingsActivity.class)));top.addView(settings,new LinearLayout.LayoutParams(Ui.dp(this,54),Ui.dp(this,50)));root.addView(top);

        LinearLayout segmented=new LinearLayout(this);segmented.setPadding(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));segmented.setBackground(Ui.round(Ui.SURFACE_2,14,this));
        digitalBtn=Ui.ghostButton(this,"デジタル");analogBtn=Ui.ghostButton(this,"アナログ");digitalBtn.setOnClickListener(v->setAnalog(false));analogBtn.setOnClickListener(v->setAnalog(true));
        segmented.addView(digitalBtn,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));segmented.addView(analogBtn,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));root.addView(segmented,new LinearLayout.LayoutParams(-1,-2));

        detail=Ui.text(this,"",13,Ui.MUTED);detail.setGravity(Gravity.CENTER);detail.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,4));root.addView(detail);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        digital=Ui.text(this,"--:--:--",55,Ui.TEXT);digital.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);digital.setGravity(Gravity.CENTER);stage.addView(digital,new FrameLayout.LayoutParams(-1,-1));
        face=new AnalogFace(this);stage.addView(face,new FrameLayout.LayoutParams(-1,-1));
        final float[] downX={0f};stage.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){downX[0]=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-downX[0];if(Math.abs(dx)>Ui.dp(this,55))setAnalog(dx<0);return true;}return true;});
        TextView hint=Ui.text(this,"左右スワイプでも切り替えできます",11,Ui.MUTED_2);hint.setGravity(Gravity.CENTER);root.addView(hint);
        setContentView(root);Ui.applySystemBarInsets(this,root);applyMode();applyPrefs();
    }
    private String title(){return "stopwatch".equals(mode)?"ストップウォッチ":"timer".equals(mode)?"タイマー":"時計";}
    private void setAnalog(boolean value){analog=value;prefs().edit().putBoolean("clock_face_analog",analog).apply();applyMode();}
    private void applyMode(){
        digital.setVisibility(analog?View.GONE:View.VISIBLE);face.setVisibility(analog?View.VISIBLE:View.GONE);
        digitalBtn.setBackground(analog?Ui.round(Ui.SURFACE_2,11,this):Ui.round(Ui.ACCENT,11,this));analogBtn.setBackground(analog?Ui.round(Ui.ACCENT,11,this):Ui.round(Ui.SURFACE_2,11,this));
        digitalBtn.setTextColor(analog?Ui.MUTED:0xFF0D1B2A);analogBtn.setTextColor(analog?0xFF0D1B2A:Ui.MUTED);
    }
    private void applyPrefs(){
        boolean keep=prefs().getBoolean("clock_keep_on",false);if(keep)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(detail!=null)detail.setVisibility(prefs().getBoolean("clock_show_detail",true)?View.VISIBLE:View.GONE);
        if(face!=null)face.configure(prefs().getBoolean("analog_numbers",true),prefs().getBoolean("analog_ticks",true),prefs().getBoolean("analog_seconds",true),prefs().getInt("analog_number_scale",1));
        boolean stored=prefs().getBoolean("clock_face_analog",analog);if(stored!=analog){analog=stored;applyMode();}
    }
    private android.content.SharedPreferences prefs(){return getSharedPreferences("clock_tools",MODE_PRIVATE);}
    private long stopwatchElapsed(){android.content.SharedPreferences p=prefs();long saved=p.getLong("sw_elapsed",0L);if(!p.getBoolean("sw_running",false))return saved;long base=p.getLong("sw_base",SystemClock.elapsedRealtime());return saved+Math.max(0L,SystemClock.elapsedRealtime()-base);}
    private String fmtStopwatch(long ms){long h=ms/3600000L,m=(ms/60000L)%60,s=(ms/1000L)%60,cs=(ms/10L)%100;return String.format(Locale.US,"%02d:%02d:%02d.%02d",h,m,s,cs);}
    private String fmtTimer(long ms){long total=Math.max(0L,ms)/1000L,h=total/3600L,m=(total/60L)%60,s=total%60L;return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);}
    private void update(){
        try{
            if("stopwatch".equals(mode)){
                long ms=stopwatchElapsed();digital.setText(fmtStopwatch(ms));detail.setText(I18n.tr(this,"経過時間"));
                double sec=(ms/1000d)%60d,min=(ms/60000d)%60d,hr=(ms/3600000d)%12d;face.setHands(hr,min,sec);
            }else if("timer".equals(mode)){
                TimerStore.Entry e=TimerStore.find(this,timerId);long ms=e==null?0L:e.remaining();digital.setText(fmtTimer(ms));detail.setText(e==null?I18n.tr(this,"タイマー"):((e.label==null||e.label.isEmpty())?I18n.tr(this,"残り時間"):e.label+"  ·  "+I18n.tr(this,"残り時間")));
                double sec=(ms/1000d)%60d,min=(ms/60000d)%60d,hr=(ms/3600000d)%12d;face.setHands(hr,min,sec);
            }else{
                ZoneId z=(zoneId==null||zoneId.isEmpty())?ZoneId.systemDefault():ZoneId.of(zoneId);ZonedDateTime now=ZonedDateTime.now(z);boolean h24=prefs().getBoolean("use_24h",true);Locale loc=I18n.locale(this);
                digital.setText(now.format(DateTimeFormatter.ofPattern(h24?"HH:mm:ss":"hh:mm:ss a",loc)));detail.setText(now.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"full"),loc))+"  ·  "+z.getId());
                face.setHands(now.getHour()%12+now.getMinute()/60d,now.getMinute()+now.getSecond()/60d,now.getSecond()+now.getNano()/1_000_000_000d);
            }
        }catch(Throwable ignored){}
    }

    public static class AnalogFace extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private double h,m,s;private boolean numbers=true,ticks=true,seconds=true;private int numberScale=1;
        public AnalogFace(Context c){super(c);p.setStrokeCap(Paint.Cap.ROUND);setBackgroundColor(Ui.BG);}
        public void configure(boolean n,boolean t,boolean sec,int scale){numbers=n;ticks=t;seconds=sec;numberScale=Math.max(0,Math.min(2,scale));invalidate();}
        public void setHands(double hh,double mm,double ss){h=hh;m=mm;s=ss;invalidate();}
        private float dp(float value){return value*getResources().getDisplayMetrics().density;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),hh=getHeight(),cx=w/2f,cy=hh/2f,r=Math.min(w,hh)*0.40f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Ui.BORDER);c.drawCircle(cx,cy,r,p);
            if(ticks)for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float len=i%5==0?r*0.105f:r*0.045f;p.setStrokeWidth(i%5==0?dp(2.4f):dp(1));p.setColor(i%5==0?Ui.MUTED:Ui.MUTED_2);c.drawLine(cx+(float)Math.cos(a)*(r-len),cy+(float)Math.sin(a)*(r-len),cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,p);}
            if(numbers){float[] sizes={17f,21f,25f};p.setStyle(Paint.Style.FILL);p.setColor(Ui.TEXT);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));p.setTextSize(dp(sizes[numberScale]));Paint.FontMetrics fm=p.getFontMetrics();float off=-(fm.ascent+fm.descent)/2f;for(int i=1;i<=12;i++){double a=Math.toRadians(i*30-90);float nr=r*0.79f;c.drawText(String.valueOf(i),cx+(float)Math.cos(a)*nr,cy+(float)Math.sin(a)*nr+off,p);}}
            hand(c,cx,cy,r*0.48f,h*30,dp(7),Ui.TEXT);hand(c,cx,cy,r*0.68f,m*6,dp(5),Ui.TEXT);if(seconds)hand(c,cx,cy,r*0.78f,s*6,dp(2),Ui.ACCENT);
            p.setStyle(Paint.Style.FILL);p.setColor(Ui.ACCENT);c.drawCircle(cx,cy,dp(5),p);
        }
        private void hand(Canvas c,float cx,float cy,float len,double deg,float stroke,int color){double a=Math.toRadians(deg-90);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(color);c.drawLine(cx,cy,cx+(float)Math.cos(a)*len,cy+(float)Math.sin(a)*len,p);}
    }
}
''',encoding="utf-8")

# ---------- Dedicated fine-grained clock settings ----------
(java/"ClockSettingsActivity.java").write_text(r'''package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public class ClockSettingsActivity extends Activity {
    private LinearLayout body; private android.content.SharedPreferences p;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);p=getSharedPreferences("clock_tools",MODE_PRIVATE);build();}
    @Override public void onBackPressed(){Ui.finishNoAnimation(this);}
    private void build(){
        ScrollView sv=new ScrollView(this);sv.setBackgroundColor(Ui.BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,46));sv.addView(body);
        LinearLayout top=Ui.row(this);top.setPadding(0,0,0,Ui.dp(this,4));Button back=Ui.ghostButton(this,"‹");back.setTextSize(28);back.setOnClickListener(v->Ui.finishNoAnimation(this));top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,50)));TextView title=Ui.title(this,"時計の詳細設定",24);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));body.addView(top);
        section("表示");addSwitch("24時間表示","use_24h",true);addSwitch("日付・タイムゾーン情報を表示","clock_show_detail",true);addSwitch("画面を点灯したままにする","clock_keep_on",false);
        TextView init=Ui.sectionHeader(this,"初期表示");body.addView(init,Ui.gapTop(this,18));LinearLayout seg=new LinearLayout(this);Button d=Ui.button(this,"デジタル",!p.getBoolean("clock_face_analog",false));Button a=Ui.button(this,"アナログ",p.getBoolean("clock_face_analog",false));d.setOnClickListener(v->{p.edit().putBoolean("clock_face_analog",false).apply();recreate();});a.setOnClickListener(v->{p.edit().putBoolean("clock_face_analog",true).apply();recreate();});seg.addView(d,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));seg.addView(a,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));body.addView(seg);
        section("アナログ時計");addSwitch("文字盤に数字を表示","analog_numbers",true);addSwitch("分の目盛りを表示","analog_ticks",true);addSwitch("秒針を表示","analog_seconds",true);addNumberScale();
        Button reset=Ui.button(this,"時計設定をリセット",false);reset.setOnClickListener(v->{p.edit().remove("use_24h").remove("clock_show_detail").remove("clock_keep_on").remove("clock_face_analog").remove("analog_numbers").remove("analog_ticks").remove("analog_seconds").remove("analog_number_scale").apply();Toast.makeText(this,I18n.tr(this,"リセットしました"),Toast.LENGTH_SHORT).show();recreate();});body.addView(reset,Ui.gapTop(this,28));
        setContentView(sv);Ui.applySystemBarInsets(this,sv);
    }
    private void section(String s){TextView h=Ui.title(this,s,18);body.addView(h,Ui.gapTop(this,22));}
    private void addSwitch(String label,String key,boolean def){LinearLayout row=Ui.row(this);TextView t=Ui.text(this,label,15,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(p.getBoolean(key,def));sw.setOnCheckedChangeListener((v,x)->p.edit().putBoolean(key,x).apply());row.addView(sw);body.addView(row);body.addView(Ui.divider(this));}
    private void addNumberScale(){LinearLayout row=Ui.row(this);TextView t=Ui.text(this,"数字の大きさ",15,Ui.TEXT);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));String[] raw={"小","中","大"};int cur=Math.max(0,Math.min(2,p.getInt("analog_number_scale",1)));Button size=Ui.ghostButton(this,raw[cur]+"  ›");size.setOnClickListener(v->{int n=(p.getInt("analog_number_scale",1)+1)%3;p.edit().putInt("analog_number_scale",n).apply();size.setText(I18n.tr(this,raw[n])+"  ›");});row.addView(size);body.addView(row);body.addView(Ui.divider(this));}
}
''',encoding="utf-8")

# ---------- Main settings: app language + clock settings + original device checks ----------
(java/"SystemSettingsActivity.java").write_text(r'''package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class SystemSettingsActivity extends Activity {
    private LinearLayout body;
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.prepareActivity(this);build();}
    @Override protected void onResume(){super.onResume();renderStatus();}
    private void build(){ScrollView sv=new ScrollView(this);sv.setBackground(Ui.screenGradient(this));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,60));sv.addView(body);LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button back=Ui.ghostButton(this,"←");back.setMinWidth(Ui.dp(this,56));back.setOnClickListener(v->finish());top.addView(back);TextView title=Ui.title(this,"設定",27);title.setPadding(Ui.dp(this,16),0,0,0);top.addView(title);body.addView(top);setContentView(sv);Ui.applySystemBarInsets(this,sv);}
    private void renderStatus(){
        while(body.getChildCount()>1)body.removeViewAt(1);
        TextView app=Ui.title(this,"アプリ設定",19);body.addView(app,Ui.gapTop(this,20));addLanguageRow();addButton("時計の詳細設定",()->Ui.launchNoAnimation(this,new Intent(this,ClockSettingsActivity.class)));
        TextView device=Ui.title(this,"端末設定",19);body.addView(device,Ui.gapTop(this,28));TextView note=Ui.text(this,"ロック画面で確実に鳴らすために必要な端末側の設定です。",13,Ui.MUTED);body.addView(note,Ui.gapTop(this,8));
        addStatus("正確なアラーム",AlarmScheduler.canScheduleExact(this),this::openExact);boolean full=true;if(Build.VERSION.SDK_INT>=34){try{NotificationManager nm=getSystemService(NotificationManager.class);full=nm!=null&&nm.canUseFullScreenIntent();}catch(Throwable ignored){full=false;}}addStatus("全画面アラーム",full,this::openFull);addStatus("他のアプリの上に表示",Settings.canDrawOverlays(this),this::openOverlay);
        TextView other=Ui.title(this,"その他",19);body.addView(other,Ui.gapTop(this,28));addButton("通知とロック画面表示",this::openNotifications);addButton("バッテリー最適化から除外",this::openBattery);addButton("アプリ情報",this::openDetails);TextView realme=Ui.text(this,"realmeではアプリ情報内の「ロック画面に表示」「バックグラウンドでポップアップ」「バックグラウンドアクティビティ」も許可してください。",13,Ui.MUTED);body.addView(realme,Ui.gapTop(this,18));
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
''',encoding="utf-8")

# ---------- ClockActivity: dark city picker, hide technical short aliases, localize cities/dates ----------
p=java/"ClockActivity.java"; s=p.read_text(encoding="utf-8")
s=s.replace('v.setText(s); v.setTextSize(sp); v.setTextColor(color);','v.setText(I18n.tr(this,s)); v.setTextSize(sp); v.setTextColor(color);',1)
s=s.replace('DateTimeFormatter tf = DateTimeFormatter.ofPattern(h24 ? "HH:mm:ss" : "hh:mm:ss a", Locale.JAPAN);','DateTimeFormatter tf = DateTimeFormatter.ofPattern(h24 ? "HH:mm:ss" : "hh:mm:ss a", I18n.locale(this));')
s=s.replace('DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPAN)','DateTimeFormatter.ofPattern(I18n.datePattern(this,"full"), I18n.locale(this))')
s=s.replace('DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy/MM/dd (E)", Locale.JAPAN);','DateTimeFormatter df = DateTimeFormatter.ofPattern(I18n.datePattern(this,"short"), I18n.locale(this));')
old_friendly='''    private String friendlyZoneName(String id) {
        String last = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        last = last.replace('_', ' ');
        Map<String,String> names = new HashMap<>();
        names.put("Asia/Tokyo", "東京"); names.put("Asia/Seoul", "ソウル");
        names.put("America/New_York", "ニューヨーク"); names.put("America/Los_Angeles", "ロサンゼルス");
        names.put("America/Chicago", "シカゴ"); names.put("Europe/London", "ロンドン");
        names.put("Europe/Paris", "パリ"); names.put("Europe/Berlin", "ベルリン");
        names.put("Asia/Dubai", "ドバイ"); names.put("Asia/Kolkata", "デリー / インド");
        names.put("Asia/Bangkok", "バンコク"); names.put("Asia/Singapore", "シンガポール");
        names.put("Asia/Hong_Kong", "香港"); names.put("Asia/Shanghai", "上海");
        names.put("Australia/Sydney", "シドニー"); names.put("Pacific/Honolulu", "ホノルル");
        return names.containsKey(id) ? names.get(id) : last;
    }'''
new_friendly='''    private String friendlyZoneName(String id) {
        String last=id.contains("/")?id.substring(id.lastIndexOf('/')+1):id;last=last.replace('_',' ');
        Map<String,String> names=new HashMap<>();names.put("Asia/Tokyo","東京");names.put("Asia/Seoul","ソウル");names.put("America/New_York","ニューヨーク");names.put("America/Los_Angeles","ロサンゼルス");names.put("America/Chicago","シカゴ");names.put("Europe/London","ロンドン");names.put("Europe/Paris","パリ");names.put("Europe/Berlin","ベルリン");names.put("Asia/Dubai","ドバイ");names.put("Asia/Kolkata","デリー / インド");names.put("Asia/Bangkok","バンコク");names.put("Asia/Singapore","シンガポール");names.put("Asia/Hong_Kong","香港");names.put("Asia/Shanghai","上海");names.put("Australia/Sydney","シドニー");names.put("Pacific/Honolulu","ホノルル");
        return names.containsKey(id)?I18n.tr(this,names.get(id)):last;
    }'''
if old_friendly not in s: raise SystemExit('friendlyZoneName anchor not found')
s=s.replace(old_friendly,new_friendly,1)
start=s.index('    private void showZonePicker() {')
end=s.index('\n    private void buildStopwatch()',start)
new_picker=r'''    private void showZonePicker() {
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,12));box.setBackgroundColor(Ui.SURFACE);
        TextView title=text("都市 / タイムゾーンを追加",20,Ui.TEXT);title.setTypeface(null,Typeface.BOLD);box.addView(title);
        EditText search=new EditText(this);search.setHint(I18n.tr(this,"例: Seoul / London / Asia/Tokyo"));search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED_2);search.setSingleLine(true);try{search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));}catch(Throwable ignored){}box.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(this,56)));
        ListView list=new ListView(this);list.setBackgroundColor(Ui.SURFACE);list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.BORDER));list.setDividerHeight(Ui.dp(this,1));box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button cancel=Ui.button(this,"閉じる",false);cancel.setOnClickListener(v->dialog.dismiss());box.addView(cancel,new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        dialog.setContentView(box);
        ArrayList<String> all=new ArrayList<>();for(String id:ZoneId.getAvailableZoneIds()){if(!id.contains("/"))continue;if(id.startsWith("Etc/")||id.startsWith("SystemV/")||id.startsWith("posix/")||id.startsWith("right/"))continue;all.add(id);}Collections.sort(all,Comparator.comparing(this::friendlyZoneName,String.CASE_INSENSITIVE_ORDER));
        ArrayList<String> filtered=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_2,android.R.id.text1,filtered){@Override public View getView(int position,View convertView,android.view.ViewGroup parent){LinearLayout row=new LinearLayout(ClockActivity.this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(Ui.dp(ClockActivity.this,18),Ui.dp(ClockActivity.this,12),Ui.dp(ClockActivity.this,18),Ui.dp(ClockActivity.this,12));row.setBackgroundColor(Ui.SURFACE);String id=getItem(position);TextView t1=Ui.text(ClockActivity.this,friendlyZoneName(id),17,Ui.TEXT);t1.setTypeface(null,Typeface.BOLD);row.addView(t1);TextView t2=Ui.text(ClockActivity.this,id,12,Ui.MUTED);try{ZonedDateTime z=ZonedDateTime.now(ZoneId.of(id));t2.setText(z.format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  ·  UTC"+formatOffset(z.getOffset().getTotalSeconds())+"  ·  "+id);}catch(Throwable ignored){}row.addView(t2,Ui.gapTop(ClockActivity.this,2));return row;}};
        list.setAdapter(adapter);
        Runnable refilter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);filtered.clear();for(String id:all){String label=friendlyZoneName(id).toLowerCase(Locale.ROOT);if(q.isEmpty()||id.toLowerCase(Locale.ROOT).contains(q)||label.contains(q)){filtered.add(id);if(filtered.size()>=120)break;}}adapter.notifyDataSetChanged();};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refilter.run();}public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((a,v,pos,id)->{String z=filtered.get(pos);LinkedHashSet<String> zones=loadZones();zones.add(z);saveZones(zones);dialog.dismiss();showMode("world");});
        refilter.run();dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.SURFACE));w.setDimAmount(.65f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),(int)(getResources().getDisplayMetrics().heightPixels*.84f));}
    }
'''
s=s[:start]+new_picker+s[end:]
p.write_text(s,encoding="utf-8")

# ---------- Localized date rendering on main/calendar ----------
p=java/"MainActivity.java"; s=p.read_text(encoding="utf-8")
s=s.replace('z.format(DateTimeFormatter.ofPattern("M月d日(E) H:mm",Locale.JAPAN))','z.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"next"),I18n.locale(this)))')
p.write_text(s,encoding="utf-8")
p=java/"StatsActivity.java"; s=p.read_text(encoding="utf-8")
s=s.replace('month.format(DateTimeFormatter.ofPattern("yyyy年 M月",Locale.JAPAN))','month.format(DateTimeFormatter.ofPattern(I18n.datePattern(this,"month"),I18n.locale(this)))')
p.write_text(s,encoding="utf-8")

# ---------- Localize timer notification/channel text ----------
p=java/"TimerReceiver.java"; s=p.read_text(encoding="utf-8")
s=s.replace('new NotificationChannel(RUN_CHANNEL,"実行中のタイマー",NotificationManager.IMPORTANCE_DEFAULT)','new NotificationChannel(RUN_CHANNEL,I18n.tr(c,"実行中のタイマー"),NotificationManager.IMPORTANCE_DEFAULT)')
s=s.replace('ch.setDescription("タイマーの残り時間を常に表示します")','ch.setDescription(I18n.tr(c,"タイマーの残り時間を常に表示します"))')
s=s.replace('String label=e.label==null||e.label.isEmpty()?"タイマー":e.label;','String label=e.label==null||e.label.isEmpty()?I18n.tr(c,"タイマー"):e.label;',1)
s=s.replace('.setContentTitle(label).setContentText("残り時間")','.setContentTitle(label).setContentText(I18n.tr(c,"残り時間"))')
s=s.replace('new NotificationChannel(id,"タイマー終了",NotificationManager.IMPORTANCE_HIGH)','new NotificationChannel(id,I18n.tr(c,"タイマー終了"),NotificationManager.IMPORTANCE_HIGH)')
s=s.replace('ch.setDescription("WakeGuardのタイマー終了音")','ch.setDescription(I18n.tr(c,"WakeGuardのタイマー終了音"))')
s=s.replace('String label=e.label==null||e.label.isEmpty()?"タイマー終了":e.label;','String label=e.label==null||e.label.isEmpty()?I18n.tr(c,"タイマー終了"):e.label;')
s=s.replace('.setContentTitle(label).setContentText("設定した時間になりました")','.setContentTitle(label).setContentText(I18n.tr(c,"設定した時間になりました"))')
p.write_text(s,encoding="utf-8")

# ---------- Dark AlertDialog styling + Android 13 locale configuration ----------
styles=res/"values/wakeguard_v101.xml"
styles.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="WakeGuardAlertDialog" parent="@android:style/Theme.Material.Dialog.Alert">
        <item name="android:windowBackground">#1A1B1E</item>
        <item name="android:colorAccent">#8AB4F8</item>
        <item name="android:textColorPrimary">#F1F1F1</item>
        <item name="android:textColorSecondary">#B0B0B4</item>
    </style>
    <style name="WakeGuardThemeNoFlash" parent="@style/AppTheme">
        <item name="android:windowBackground">#101113</item>
        <item name="android:windowDisablePreview">true</item>
        <item name="android:windowContentTransitions">false</item>
        <item name="android:alertDialogTheme">@style/WakeGuardAlertDialog</item>
        <item name="android:textColorPrimary">#F1F1F1</item>
        <item name="android:textColorSecondary">#B0B0B4</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:navigationBarColor">#101113</item>
        <item name="android:statusBarColor">#101113</item>
    </style>
</resources>
''',encoding="utf-8")
(res/"xml").mkdir(parents=True,exist_ok=True)
(res/"xml/locales_config.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="ja" />
    <locale android:name="en" />
    <locale android:name="ko" />
</locale-config>
''',encoding="utf-8")

p=root/"app/src/main/AndroidManifest.xml"; s=p.read_text(encoding="utf-8")
s=s.replace('android:label="@string/app_name"\n        android:theme=', 'android:label="@string/app_name"\n        android:localeConfig="@xml/locales_config"\n        android:theme=',1)
anchor='<activity android:name=".ClockFaceActivity" android:exported="false" />'
if anchor not in s: raise SystemExit('ClockFaceActivity manifest anchor missing')
s=s.replace(anchor,anchor+'\n        <activity android:name=".ClockSettingsActivity" android:exported="false" />',1)
p.write_text(s,encoding="utf-8")

# Version bump.
p=root/"app/build.gradle.kts"; s=p.read_text(encoding="utf-8")
s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 39',s,count=1)
s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.2.0"',s,count=1)
p.write_text(s,encoding="utf-8")
print("WakeGuard v1.2.0: numbered analog face, segmented switching, dark dialogs, JA/EN/KO localization layer, and fine clock settings")
