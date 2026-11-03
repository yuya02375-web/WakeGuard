package jp.wakeguard.alarm;

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
        put("都市 / タイムゾーンを追加","Add city / time zone","도시 / 시간대 추가"); put("日本語・English・한국어で都市 / 国 / タイムゾーンを検索","Search cities / countries / time zones in Japanese, English, or Korean","일본어・English・한국어로 도시 / 국가 / 시간대 검색"); put("例: Seoul / London / Asia/Tokyo","Example: Seoul / London / Asia/Tokyo","예: Seoul / London / Asia/Tokyo");
        put("閉じる","Close","닫기"); put("削除","Delete","삭제"); put("保存","Save","저장"); put("キャンセル","Cancel","취소");
        put("開始","Start","시작"); put("スタート","Start","시작"); put("一時停止","Pause","일시정지"); put("再開","Resume","재개");
        put("計測中","Running","측정 중"); put("ストップウォッチの計測中時間を表示します","Shows elapsed stopwatch time while running","스톱워치 측정 시간을 실행 중 알림에 표시합니다");
        put("タップでデジタル / アナログ切替  ·  長押しで大きく表示","Tap to switch digital / analog  ·  Long-press for full screen","탭하여 디지털/아날로그 전환  ·  길게 눌러 크게 보기");
        put("リセット","Reset","초기화"); put("ラップ","Lap","랩"); put("ラップはまだありません","No laps yet","아직 랩이 없습니다");
        put("経過時間","Elapsed time","경과 시간"); put("残り時間","Time remaining","남은 시간"); put("終了","Finished","종료");
        put("一時停止中","Paused","일시정지 중"); put("もう一度","Again","다시"); put("今回だけ","One-time","이번만");
        put("保存したタイマーはありません","No saved timers","저장된 타이머가 없습니다"); put("タイマーを作成","Create timer","타이머 만들기");
        put("名前（任意）","Name (optional)","이름(선택)"); put("時間","Time","시간"); put("このタイマーを保存","Save this timer","이 타이머 저장");
        put("この時間を保存","Save this duration","이 시간 저장"); put("すぐ使う","Quick start","바로 사용"); put("よく使う時間","Common durations","자주 쓰는 시간");
        put("時間を設定してください","Set a duration","시간을 설정하세요"); put("時間を入力してください","Enter a duration","시간을 입력하세요");
        put("時間表記に直しました。確認してから開始してください","Converted to normal time format. Check it, then tap Start.","일반 시간 형식으로 변환했습니다. 확인한 뒤 시작을 눌러 주세요.");
        put("音: 標準","Sound: Default","소리: 기본"); put("選択した音","Selected sound","선택한 소리"); put("音声ファイルを開けません","Unable to open the audio file","오디오 파일을 열 수 없습니다");
        put("タイマーの音・動画","Timer sound / video","타이머 소리 / 동영상"); put("アラームの音・動画","Alarm sound / video","알람 소리 / 동영상"); put("音声 / 動画","Audio / video","오디오 / 동영상"); put("音声","Audio","오디오"); put("動画","Video","동영상"); put("音声を選択","Choose audio","오디오 선택"); put("動画を選択","Choose video","동영상 선택"); put("標準に戻す","Use default","기본값으로 되돌리기");
        put("選択した動画","Selected video","선택한 동영상"); put("動画ファイルを開けません","Unable to open the video file","동영상 파일을 열 수 없습니다"); put("この動画は読み込めません","Unable to load this video","이 동영상을 불러올 수 없습니다"); put("動画を再生できません","Unable to play this video","이 동영상을 재생할 수 없습니다"); put("タップして動画を再生","Tap to play the video","탭하여 동영상 재생");
        put("タイマー通知を表示できません","Timer notifications are disabled","타이머 알림을 표시할 수 없습니다");
        put("タイマー実行中の残り時間を通知に常時表示するには、IGNIDO Wakeの通知を許可してください。","Allow IGNIDO Wake notifications to keep the running timer visible in notifications.","실행 중인 타이머의 남은 시간을 알림에 계속 표시하려면 IGNIDO Wake 알림을 허용하세요.");
        put("時計ツールの通知を表示できません","Clock-tool notifications are disabled","시계 도구 알림을 표시할 수 없습니다");
        put("タイマーとストップウォッチの実行中表示を通知に出すには、IGNIDO Wakeの通知を許可してください。","Allow IGNIDO Wake notifications to show running timers and the stopwatch.","타이머와 스톱워치 실행 상태를 알림에 표시하려면 IGNIDO Wake 알림을 허용하세요.");
        put("通知設定を開く","Open notification settings","알림 설정 열기");
        put("実行中のタイマー","Running timers","실행 중인 타이머"); put("タイマーの残り時間を常に表示します","Keeps timer time remaining visible","타이머 남은 시간을 계속 표시합니다");
        put("タイマー終了","Timer finished","타이머 종료"); put("IGNIDO Wakeのタイマー終了音","IGNIDO Wake timer sound","IGNIDO Wake 타이머 종료음");
        put("設定した時間になりました","Time is up","설정한 시간이 되었습니다");
        put("アラームはありません。右上の＋から追加できます。","No alarms. Tap + at the top right to add one.","알람이 없습니다. 오른쪽 위 +에서 추가할 수 있습니다.");
        put("次のアラームはありません","No upcoming alarms","다음 알람이 없습니다"); put("毎日","Every day","매일"); put("1回のみ","Once","한 번만");
        put("🔥  ストリーク","🔥  Streak","🔥  스트릭"); put("ストリーク","Streak","스트릭"); put("カレンダー","Calendar","캘린더"); put("記録","History","기록");
        put("成功した起床","Successful wake-ups","성공한 기상"); put("最高ストリーク","Best streak","최고 스트릭");
        put("成長","Growth","성장"); put("火種","Ember","불씨"); put("炎の幼体","Flame form","불꽃 유체");
        put("竜の幼体","Young dragon","어린 용"); put("翼竜","Winged dragon","날개 용"); put("成竜","Adult dragon","성룡");
        put("古竜","Ancient dragon","고룡"); put("無限成長体","Infinite growth form","무한 성장체");
        put("起床に成功するたびに、この1体だけが1段階成長します。上限はありません。ガチャ・レア度・通貨・装備・アイテムはありません。","Every successful wake-up grows this one companion by one step. There is no level cap, gacha, rarity, currency, equipment, or items.","기상에 성공할 때마다 이 한 캐릭터만 한 단계 성장합니다. 상한, 가챠, 희귀도, 재화, 장비, 아이템은 없습니다.");
        put("成長するほど、体格・角・翼・鱗・装甲・炎・オーラ・紋様・背景演出が連続的に強くなります。","As it grows, its body, horns, wings, scales, armor, flames, aura, markings, and background effects continuously become more powerful.","성장할수록 체격, 뿔, 날개, 비늘, 갑주, 불꽃, 오라, 문양, 배경 연출이 계속 강해집니다.");
        put("成功した本番アラームの日だけ記録します。テストアラームはストリークに入りません。","Only successful real alarms count. Test alarms do not affect your streak.","성공한 실제 알람만 기록됩니다. 테스트 알람은 스트릭에 포함되지 않습니다.");
        put("伝説の炎を維持中","Legendary flame active","전설의 불꽃 유지 중"); put("最初の1日で火が灯る","The flame starts on day 1","첫날부터 불꽃이 켜집니다");
        put("成長段階  1日 → 3日 → 7日 → 14日 → 30日","Growth: day 1 → 3 → 7 → 14 → 30","성장 단계  1일 → 3일 → 7일 → 14일 → 30일");
        put("アラームを追加","Add alarm","알람 추가"); put("アラームを編集","Edit alarm","알람 편집"); put("アラームを有効にする","Enable alarm","알람 사용");
        put("基本設定","Basic settings","기본 설정"); put("名前","Name","이름"); put("繰り返し","Repeat","반복"); put("曜日を選ばなければ1回だけ鳴ります","If no days are selected, it rings once","요일을 선택하지 않으면 한 번만 울립니다");
        put("事前通知","Pre-alarm notification","사전 알림"); put("分前"," min before","분 전"); put("アラームの前に音・振動なしの通知で知らせます","Shows a silent notification before the alarm","알람 전에 소리·진동 없는 알림으로 알려줍니다");
        put("分後にアラーム"," min until alarm","분 후 알람"); put("アラーム事前通知","Alarm reminders","알람 사전 알림"); put("アラームの前にサイレント通知で知らせます","Shows a silent reminder before the alarm","알람 전에 무음 알림으로 알려줍니다");
        put("解除方法","Dismiss method","해제 방법"); put("ミッション","Mission","미션"); put("ミッションを選択","Choose mission","미션 선택");
        put("なし","None","없음"); put("歩数","Steps","걸음 수"); put("計算","Math","계산"); put("記憶","Memory","기억"); put("コード入力","Code entry","코드 입력");
        put("文章入力","Text entry","문장 입력"); put("連打","Rapid tap","연타"); put("長押し","Press and hold","길게 누르기"); put("シェイク","Shake","흔들기"); put("スワイプ","Swipe","스와이프"); put("順番タップ","Tap in order","순서 탭"); put("逆順入力","Reverse entry","역순 입력"); put("ランダム","Random","랜덤");
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

        put("鳴動時間","Ring duration","알람 울림 시간"); put("無限","Infinite","무제한"); put("無限（停止するまで）","Infinite (until dismissed)","무제한 (직접 해제할 때까지)");
        put("無限、プリセット、または1秒単位で細かく設定できます。","Choose infinite, a preset, or set an exact duration down to the second.","무제한, 프리셋 또는 1초 단위로 세밀하게 설정할 수 있습니다.");
        put("設定時間後は音と振動だけ停止します。ミッション画面は解除するまで残ります。","After this duration, sound and vibration stop. The mission stays until you dismiss it.","설정 시간이 지나면 소리와 진동만 멈추며, 미션 화면은 해제할 때까지 유지됩니다.");
        put("カスタム","Custom","사용자 지정"); put("時計をタップするとデジタル / アナログが一括で切り替わります","Tap any clock to switch all clocks between digital and analog","시계를 탭하면 모든 시계가 디지털/아날로그로 한꺼번에 전환됩니다");
        put("タップで表示切替  ·  長押しで大きく表示","Tap to switch view  ·  Long-press for full screen","탭하여 표시 전환  ·  길게 눌러 크게 보기");
        put("時計をタップでデジタル / アナログ切替  ·  左右スワイプも対応","Tap the clock to switch digital / analog  ·  Swipe also works","시계를 탭하여 디지털/아날로그 전환  ·  좌우 스와이프도 지원");
        put("端末との差","Difference from device","기기와의 차이"); put("夏時間","DST","서머타임");
        put("学校、休日など","School, day off, etc.","학교, 휴일 등"); put("オフ","Off","끄기"); put("不規則","Irregular","불규칙"); put("強い連続パルス","Strong continuous pulses","강한 연속 진동");
        put("回数","Count","횟수"); put("解除に必要な歩数","Steps required to dismiss","해제에 필요한 걸음 수"); put("正解する問題数","Number of correct answers","정답 문제 수");
        put("タップ回数","Tap count","탭 횟수"); put("入力するコード数","Number of codes","입력할 코드 수"); put("シェイク回数","Shake count","흔들기 횟수");
        put("記憶問題の数","Memory questions","기억 문제 수"); put("入力する文章数","Number of texts","입력할 문장 수"); put("長押しする秒数","Hold duration in seconds","길게 누를 초 수");
        put("歩数が選ばれた場合の歩数","Steps when Steps is selected","걸음 수 미션 선택 시 걸음 수"); put("その他の回数 / 秒数","Other count / seconds","기타 횟수 / 초");
        put("実際に歩いて解除します。","Walk to dismiss the alarm.","실제로 걸어서 알람을 해제합니다."); put("足し算・引き算・掛け算を解きます。","Solve addition, subtraction, and multiplication.","덧셈·뺄셈·곱셈 문제를 풉니다.");
        put("画面のボタンを指定回数タップします。","Tap the on-screen button the required number of times.","화면 버튼을 지정 횟수만큼 탭합니다.");
        put("表示された6桁コードを正確に入力します。","Enter the displayed 6-digit code exactly.","표시된 6자리 코드를 정확히 입력합니다.");
        put("スマホを大きく振って解除します。","Shake the phone firmly to dismiss.","휴대폰을 크게 흔들어 해제합니다.");
        put("数秒だけ表示される数字を覚えて入力します。","Memorize the number shown briefly, then enter it.","몇 초간 표시되는 숫자를 기억한 뒤 입력합니다.");
        put("表示された短い文章をそのまま入力します。","Type the short sentence exactly as shown.","표시된 짧은 문장을 그대로 입력합니다.");
        put("ボタンを離さず長押しします。2〜30秒がおすすめです。","Press and hold without releasing. 2–30 seconds is recommended.","버튼을 떼지 않고 길게 누릅니다. 2~30초를 권장합니다.");
        put("鳴るたびに8種類のミッションから1つ選びます。","One of 8 missions is chosen each time the alarm rings.","알람이 울릴 때마다 8가지 미션 중 하나가 선택됩니다.");
        put("追加の解除操作なし。停止ボタンを押すだけで解除できます。","No extra challenge. Just tap the stop button to dismiss.","추가 해제 동작 없이 중지 버튼만 누르면 해제됩니다.");
        put("追加の解除操作はありません","No extra dismissal challenge","추가 해제 동작이 없습니다"); put("停止ボタンを押すとアラームを停止します","Tap the stop button to stop the alarm","중지 버튼을 누르면 알람이 중지됩니다"); put("停止","Stop","중지"); put("世界時計ウィジェット","World clock widget","세계 시계 위젯"); put("正方形・縦長・横長にサイズ変更できます","Resize it as square, tall, or wide","정사각형·세로형·가로형으로 크기를 바꿀 수 있습니다"); put("表示するタイムゾーンを選択","Choose a time zone to display","표시할 시간대를 선택하세요"); put("タイムゾーンを検索","Search time zones","시간대 검색"); put("24時間表示は世界時計の設定と連動します","24-hour display follows World Clock settings","24시간 표시는 세계 시계 설정과 연동됩니다"); put("現在のタイムゾーン","Current time zone","현재 시간대"); put("ウィジェット","Widgets","위젯"); put("世界時計ウィジェットを追加","Add world clock widget","세계 시계 위젯 추가"); put("次のアラームウィジェットを追加","Add next alarm widget","다음 알람 위젯 추가"); put("次のアラーム","Next alarm","다음 알람"); put("アラームなし","No alarms","알람 없음"); put("今日","Today","오늘"); put("明日","Tomorrow","내일"); put("追加画面を開きました","Opened widget add screen","위젯 추가 화면을 열었습니다"); put("このホーム画面では直接追加できません","This launcher cannot add it directly","이 홈 화면에서는 직접 추가할 수 없습니다"); put("ホーム画面を長押し → ウィジェット → IGNIDO Wake から追加してください","Long-press the Home screen → Widgets → IGNIDO Wake","홈 화면 길게 누르기 → 위젯 → IGNIDO Wake에서 추가하세요"); put("下の停止ボタンを押してください","Tap the stop button below","아래 중지 버튼을 누르세요"); put("解除できます","Ready to dismiss","해제할 수 있습니다"); put("そのまま停止","Direct stop","바로 중지"); put("そのまま停止できます","You can stop it directly","바로 중지할 수 있습니다");
        put("スワイプ回数","Swipe count","스와이프 횟수"); put("画面を左右に大きくスワイプして解除します。","Swipe widely left or right to dismiss.","화면을 좌우로 크게 스와이프해 해제합니다."); put("下のボタンを左右に大きくスワイプ","Swipe the button widely left or right","아래 버튼을 좌우로 크게 스와이프하세요"); put("スワイプを認識しました","Swipe detected","스와이프를 인식했습니다"); put("もっと大きくスワイプしてください","Swipe farther","더 크게 스와이프하세요");
        put("順番タップの回数","Ordered-tap rounds","순서 탭 횟수"); put("ばらばらに並んだ数字を1から6まで順番にタップします。","Tap the shuffled numbers from 1 through 6 in order.","섞여 있는 숫자를 1부터 6까지 순서대로 탭합니다."); put("1 → 6 の順番でタップ","Tap in order: 1 → 6","1 → 6 순서로 탭"); put("まず1をタップ","Tap 1 first","먼저 1을 탭하세요"); put("順番が違います。1からやり直し","Wrong order. Start again from 1.","순서가 틀렸습니다. 1부터 다시 시작하세요"); put("次は","Next","다음은");
        put("逆順入力の問題数","Reverse-entry questions","역순 입력 문제 수"); put("表示された数字を逆の順番で入力します。","Enter the displayed digits in reverse order.","표시된 숫자를 역순으로 입력합니다."); put("逆の順番で入力","Enter in reverse order","역순으로 입력"); put("逆順の5桁を入力","Enter the 5 digits reversed","5자리를 역순으로 입력"); put("逆順が違います","Incorrect reverse order","역순이 틀렸습니다");
        put("鳴るたびに11種類のミッションから1つ選びます。","One of 11 missions is chosen each time the alarm rings.","알람이 울릴 때마다 11가지 미션 중 하나가 선택됩니다.");

        put("サイズ不明","Unknown size","크기 알 수 없음"); put("長さ不明","Unknown length","길이 알 수 없음"); put("選択","Choose","선택");
        put("毎回変更","Changes each time","매번 변경"); put("メインアラーム","Main alarm","메인 알람");
        put("起床アラーム","Wake-up alarm","기상 알람"); put("ミッション式の起床アラーム","Mission-based wake-up alarm","미션 방식 기상 알람");
        put("アプリを開いて確認","Open the app to check","앱을 열어 확인"); put("停止する","Stop","중지"); put("ミッション画面で解除","Dismiss in mission screen","미션 화면에서 해제");
        put("歩数ミッション完了までアラームは停止しません","The alarm will not stop until the step mission is complete","걸음 수 미션을 완료할 때까지 알람이 멈추지 않습니다");
        put("タップ","Tap","탭"); put("ボタンを繰り返しタップ","Tap the button repeatedly","버튼을 반복해서 탭하세요"); put("この6桁を入力","Enter these 6 digits","이 6자리를 입력하세요");
        put("同じ文章を入力","Type the same text","같은 문장을 입력하세요"); put("覚えた4桁を入力","Enter the 4 digits you memorized","기억한 4자리를 입력하세요");
        put("そのまま押し続けてください","Keep holding","계속 누르고 있으세요"); put("ボタンを離さず押し続けてください","Keep holding the button without releasing","버튼을 떼지 말고 계속 누르세요");
        put("離すと最初からです","Releasing restarts from the beginning","손을 떼면 처음부터 다시 시작합니다"); put("違います。もう一度表示します","Incorrect. Showing it again.","틀렸습니다. 다시 표시합니다.");
        put("2秒後に消えます","Disappears in 2 seconds","2초 후 사라집니다"); put("調整","Adjust","조정");
        put("無限","Infinite","무제한"); put("鳴動時間","Ring duration","알람 울림 시간"); put("カスタム","Custom","사용자 지정");
        put("無限（停止するまで）","Infinite (until dismissed)","무제한 (해제할 때까지)");
        put("無限、プリセット、または1秒単位で細かく設定できます。","Choose infinite, a preset, or set the duration precisely to the second.","무제한, 프리셋 또는 1초 단위로 세밀하게 설정할 수 있습니다.");
        put("設定時間後は音と振動だけ停止します。ミッション画面は解除するまで残ります。","After this duration, only sound and vibration stop. The mission screen remains until dismissed.","설정 시간이 지나면 소리와 진동만 멈춥니다. 미션 화면은 해제할 때까지 유지됩니다.");
        put("時計をタップするとデジタル / アナログが一括で切り替わります","Tap any clock to switch all clocks between digital and analog","시계를 탭하면 모든 시계가 디지털/아날로그로 한꺼번에 전환됩니다");
        put("現在のタイムゾーンはスクロールしても上部に固定されます","The current time zone stays pinned while you scroll","현재 시간대는 스크롤해도 상단에 고정됩니다");
        put("時","h","시"); put("分","min","분"); put("秒","sec","초"); put("問","questions","문제"); put("文","sentences","문장"); put("歩","steps","걸음");
        put("自動停止","Auto stop","자동 중지");
        put("音・振動だけ自動停止","Auto-stop sound & vibration","소리·진동만 자동 중지");
        put("音・振動を止めるまで","Time until sound & vibration stop","소리·진동 중지까지");
        put("オンにすると指定時間後に音と振動だけ停止します。ミッション画面は残ります。","When enabled, sound and vibration stop after the set time. The mission screen remains.","켜면 설정한 시간이 지난 뒤 소리와 진동만 멈추고 미션 화면은 유지됩니다.");
        put("アラームを完全自動停止","Fully auto-stop alarm","알람 전체 자동 중지");
        put("全部止めるまで","Time until full stop","전체 중지까지");
        put("オンにすると指定時間後に音・振動・ミッション画面を含む現在のアラームをすべて終了します。次回以降の予約は消えません。","When enabled, the current alarm fully ends after the set time, including sound, vibration, and the mission screen. Future scheduled alarms remain.","켜면 설정한 시간이 지난 뒤 소리·진동·미션 화면을 포함한 현재 알람이 모두 종료됩니다. 다음 예약 알람은 유지됩니다.");
        put("全部を自動停止する時間","Full auto-stop time","전체 자동 중지 시간");
        put("音・振動を自動停止する時間","Sound & vibration auto-stop time","소리·진동 자동 중지 시간");
        put("プリセット、または1秒単位で細かく設定できます。オフにすると無限です。","Choose a preset or set the time precisely to the second. Turning it off means unlimited.","프리셋 또는 1초 단위로 세밀하게 설정할 수 있습니다. 끄면 무제한입니다.");
        put("1秒以上に設定してください","Set at least 1 second","1초 이상으로 설정하세요");
        put("国・都市・タイムゾーンを検索","Search country, city, or time zone","국가·도시·시간대 검색");
        put("国名で検索すると、その国の都市候補をまとめて表示します","Search a country to see multiple city and regional time-zone options","국가 이름으로 검색하면 해당 국가의 여러 도시·지역 시간대가 함께 표시됩니다");
        put("国・都市・地域・タイムゾーンを検索","Search country, city, region, or time zone","국가·도시·지역·시간대 검색");
        put("国名が曖昧でも、都市・地域・だいたいの名前から候補を探せます","Even if the country name is unclear, search by city, region, or an approximate name","국가명이 정확하지 않아도 도시·지역·대략적인 이름으로 후보를 찾을 수 있습니다");
        put("おすすめ","Suggestions","추천"); put("もしかして","Did you mean?","혹시"); put("関連する国","Related countries","관련 국가"); put("地域からの候補","Regional suggestions","지역 후보");
        put("オフライン検索を常に使い、接続中はオンライン候補も自動で追加します","Offline search always runs; when connected, online suggestions are added automatically","오프라인 검색은 항상 실행되며, 연결되어 있으면 온라인 후보도 자동으로 추가됩니다");
        put("オンラインで場所を特定","Find place online","온라인으로 장소 찾기");
        put("都市名や場所名から国をオンライン特定し、その国のタイムゾーン候補を表示します","Find the country online from a city or place name, then show that country's time-zone options","도시명이나 장소명으로 국가를 온라인에서 찾고 해당 국가의 시간대 후보를 표시합니다");
        put("オンライン候補が見つかりませんでした","No online matches found","온라인 후보를 찾지 못했습니다");
        put("この端末ではオンライン場所検索を利用できません","Online place lookup is unavailable on this device","이 기기에서는 온라인 장소 검색을 사용할 수 없습니다");
        put("場所を入力してください","Enter a place","장소를 입력하세요"); put("検索中…","Searching…","검색 중…"); put("国を選択","Choose a country","국가 선택");
        put("Google検索は最終手段です。国名をコピーして戻ると、その国の候補へ切り替わります","Google Search is the last fallback. Copy the country name and return to switch to that country's options","Google 검색은 마지막 수단입니다. 국가명을 복사해 돌아오면 해당 국가의 후보로 전환됩니다");
        put("Googleで国名を確認","Check country with Google","Google에서 국가명 확인");
        put("Googleで確認（アプリ内）","Check with Google in app","앱 안에서 Google로 확인");
        put("コピーした文字を使う","Use copied text","복사한 문자 사용"); put("コピーした文字がありません","No copied text","복사한 문자가 없습니다");
        put("貼付","Paste","붙여넣기");
        put("GoogleはIGNIDO Wakeの上に開きます。上の検索欄を残したまま確認でき、文字をコピーしてツールバーのボタンを押すか、選択した文字をIGNIDO Wakeへ直接渡せます","Google opens over IGNIDO Wake. Keep the search field visible, then copy text and tap the toolbar action, or send selected text directly to IGNIDO Wake","Google은 IGNIDO Wake 위에 열립니다. 위 검색창을 남겨 둔 채 확인하고, 문자를 복사한 뒤 툴바 버튼을 누르거나 선택한 문자를 WakeGuard로 바로 보낼 수 있습니다");
        put("Googleで確認した国名をコピーして戻ると、自動でその国の候補を表示します","Copy the country name from Google and return to automatically show that country's options","Google에서 확인한 국가명을 복사한 뒤 돌아오면 해당 국가의 후보가 자동으로 표시됩니다");
        put("国名が分からない場所","place unknown country","국가명을 모르는 장소"); put("国名","country","국가명"); put("ブラウザを開けません","Unable to open browser","브라우저를 열 수 없습니다");
        put("現在のストリーク","Current streak","현재 스트릭");
        put("キャラ","Characters","캐릭터"); put("育てる","Train","육성"); put("キャラガチャ","Character gacha","캐릭터 가챠"); put("装飾","Decorations","장식");
        put("育成","Progression","육성"); put("プレイヤーレベル","Player level","플레이어 레벨"); put("ガチャチケット","Gacha tickets","가챠 티켓"); put("エッセンス","Essence","에센스"); put("次の確定報酬","Next guaranteed reward","다음 확정 보상");
        put("起床成功でプレイヤーと装備中キャラが成長。5レベルごとに無料ガチャチケット、4レベルごとに35%で装飾抽選。節目レベルでは100%報酬があります。","Successful wake-ups grow both your player level and equipped character. Every 5 levels gives a free gacha ticket; every 4 levels has a 35% decoration roll. Milestone levels have guaranteed rewards.","기상 성공 시 플레이어와 장착 캐릭터가 함께 성장합니다. 5레벨마다 무료 가챠 티켓, 4레벨마다 35% 확률로 장식 추첨이 있으며 주요 레벨에는 확정 보상이 있습니다.");
        put("限定スターター","Exclusive starter","한정 스타터"); put("限定","Exclusive","한정"); put("所持キャラ","Owned characters","보유 캐릭터"); put("キャラコレクション","Character collection","캐릭터 컬렉션"); put("装備しました","Equipped","장착했습니다");
        put("25エッセンスで250XP獲得","Gained 250 XP for 25 Essence","에센스 25로 250 XP 획득"); put("エッセンスが足りません","Not enough Essence","에센스가 부족합니다");
        put("無料チケットだけで回せます。課金要素はありません。","Uses free tickets only. There are no paid gacha purchases.","무료 티켓만 사용합니다. 유료 가챠 요소는 없습니다."); put("30回でSSR以上確定、100回でUR確定","SSR+ guaranteed by 30 pulls; UR guaranteed by 100 pulls","30회 이내 SSR 이상 확정, 100회 이내 UR 확정");
        put("ガチャチケットが足りません","Not enough gacha tickets","가챠 티켓이 부족합니다"); put("ガチャチケットが10枚必要です","You need 10 gacha tickets","가챠 티켓 10장이 필요합니다"); put("10連結果","10-pull results","10연속 결과"); put("ガチャ結果","Gacha result","가챠 결과"); put("重複","Duplicate","중복"); put("新しいキャラを獲得","New character obtained","새 캐릭터 획득");
        put("装飾は最大3個まで同時装備できます","You can equip up to 3 decorations at once","장식은 최대 3개까지 동시에 장착할 수 있습니다"); put("所持","Owned","보유"); put("装備中","Equipped","장착 중"); put("タップで装備","Tap to equip","탭하여 장착"); put("装飾は3個までです","You can equip up to 3 decorations","장식은 3개까지 장착할 수 있습니다"); put("装飾コレクション","Decoration collection","장식 컬렉션");
        put("4レベルごとに35%で装飾、5レベルごとにガチャチケット","Every 4 levels: 35% decoration chance; every 5 levels: gacha ticket","4레벨마다 장식 35% 확률, 5레벨마다 가챠 티켓");
        put("ストリーク保護","Streak protection","스트릭 보호"); put("保護日","Protection days","보호일"); put("保護","Protected","보호");
        put("自動で保護日を使う","Use protection days automatically","보호일 자동 사용");
        put("毎月1日に2日分回復し、最大3日まで持てます。起きられなかった対象日に1日だけ自動使用してストリークを守ります。保護日は成功回数や成長Lvには加算されません。","Two protection days are restored on the first of each month, up to three. One is automatically used on an eligible missed wake-up to preserve your streak. Protected days do not increase successful wake-ups or Growth Lv.","매월 1일에 보호일 2일이 회복되며 최대 3일까지 보유할 수 있습니다. 대상 기상일을 놓치면 1일을 자동 사용해 스트릭을 보호합니다. 보호일은 성공 횟수나 성장 Lv에 더해지지 않습니다.");
        put("30日連続で実際に起床成功するごとに、保護日を1日追加します（最大3日）。","Every 30 actual successful wake-ups in the streak adds one protection day, up to three.","실제 기상 성공을 30일 연속 달성할 때마다 보호일 1일이 추가됩니다(최대 3일).");
        put("◆ 成功   ◇ 保護","◆ Success   ◇ Protected","◆ 성공   ◇ 보호"); put("この月の成功","Successful this month","이번 달 성공");
        put("成功した本番アラームの日だけ成長します。保護日はストリークだけを維持し、テストアラームは記録しません。","Growth only increases on successful real alarms. A protected day only preserves the streak, and test alarms are not recorded.","성공한 실제 알람에서만 성장합니다. 보호일은 스트릭만 유지하며 테스트 알람은 기록하지 않습니다.");
        put("タイマーを停止","Stop timer","타이머 정지");
        put("OK","OK","확인"); put("1回","1 pull","1회"); put("10回","10 pulls","10회");
        put("アラームを停止","Dismiss alarm","알람 해제");
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
        if(s==null||s.isEmpty())return s;
        String lang=language(c);if("ja".equals(lang))return s;
        boolean ko="ko".equals(lang);
        String[] v=MAP.get(s);if(v!=null)return ko?v[1]:v[0];

        Matcher m;
        m=Pattern.compile("^次は (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"다음: ":"Next: ")+m.group(1);
        m=Pattern.compile("^音: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"소리: ":"Sound: ")+m.group(1);
        m=Pattern.compile("^動画: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"동영상: ":"Video: ")+m.group(1);
        m=Pattern.compile("^タイマー音: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"타이머 소리: ":"Timer sound: ")+m.group(1);
        m=Pattern.compile("^タイマー動画: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"타이머 동영상: ":"Timer video: ")+m.group(1);
        m=Pattern.compile("^終了予定  (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"종료 예정  ":"Ends at  ")+m.group(1);
        m=Pattern.compile("^今回だけ  ·  (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"이번만  ·  ":"One-time  ·  ")+tr(c,m.group(1));

        m=Pattern.compile("^あと (\\d+) 歩で解除$").matcher(s);if(m.matches())return ko?"앞으로 "+m.group(1)+"걸음이면 해제":m.group(1)+" steps to dismiss";
        m=Pattern.compile("^計算 (\\d+)問で解除$").matcher(s);if(m.matches())return ko?"계산 "+m.group(1)+"문제로 해제":"Solve "+m.group(1)+" math questions";
        m=Pattern.compile("^連打 (\\d+)回で解除$").matcher(s);if(m.matches())return ko?"연타 "+m.group(1)+"회로 해제":"Tap "+m.group(1)+" times to dismiss";
        m=Pattern.compile("^コード入力 (\\d+)回で解除$").matcher(s);if(m.matches())return ko?"코드 입력 "+m.group(1)+"회로 해제":"Enter "+m.group(1)+" codes to dismiss";
        m=Pattern.compile("^シェイク (\\d+)回で解除$").matcher(s);if(m.matches())return ko?"흔들기 "+m.group(1)+"회로 해제":"Shake "+m.group(1)+" times to dismiss";
        m=Pattern.compile("^記憶問題 (\\d+)問で解除$").matcher(s);if(m.matches())return ko?"기억 문제 "+m.group(1)+"개로 해제":"Complete "+m.group(1)+" memory questions";
        m=Pattern.compile("^文章入力 (\\d+)文で解除$").matcher(s);if(m.matches())return ko?"문장 입력 "+m.group(1)+"개로 해제":"Type "+m.group(1)+" sentences to dismiss";
        m=Pattern.compile("^長押し (\\d+)秒で解除$").matcher(s);if(m.matches())return ko?"길게 누르기 "+m.group(1)+"초로 해제":"Hold for "+m.group(1)+" sec to dismiss";
        m=Pattern.compile("^スワイプ (\\d+)回で解除$").matcher(s);if(m.matches())return ko?"스와이프 "+m.group(1)+"회로 해제":"Swipe "+m.group(1)+" times to dismiss";
        m=Pattern.compile("^順番タップ (\\d+)回で解除$").matcher(s);if(m.matches())return ko?"순서 탭 "+m.group(1)+"회로 해제":"Complete "+m.group(1)+" ordered-tap rounds";
        m=Pattern.compile("^逆順入力 (\\d+)問で解除$").matcher(s);if(m.matches())return ko?"역순 입력 "+m.group(1)+"문제로 해제":"Complete "+m.group(1)+" reverse-entry questions";

        m=Pattern.compile("^(\\d+) / (\\d+) 歩$").matcher(s);if(m.matches())return ko?m.group(1)+" / "+m.group(2)+" 걸음":m.group(1)+" / "+m.group(2)+" steps";
        m=Pattern.compile("^あと (\\d+) 歩$").matcher(s);if(m.matches())return ko?"앞으로 "+m.group(1)+"걸음":"Remaining "+m.group(1)+" steps";
        m=Pattern.compile("^タップ  あと (\\d+)回$").matcher(s);if(m.matches())return ko?"탭  앞으로 "+m.group(1)+"회":"Tap  "+m.group(1)+" remaining";
        m=Pattern.compile("^音量 (\\d+)%$").matcher(s);if(m.matches())return (ko?"음량 ":"Volume ")+m.group(1)+"%";

        m=Pattern.compile("^([＋+−-]?)(\\d+)時間(?: (\\d+)分)?(?: (\\d+)秒)?$").matcher(s);
        if(m.matches())return m.group(1)+m.group(2)+(ko?"시간":"h")+(m.group(3)==null?"":" "+m.group(3)+(ko?"분":"m"))+(m.group(4)==null?"":" "+m.group(4)+(ko?"초":"s"));
        m=Pattern.compile("^([＋+−-]?)(\\d+)分(?: (\\d+)秒)?$").matcher(s);
        if(m.matches())return m.group(1)+m.group(2)+(ko?"분":"m")+(m.group(3)==null?"":" "+m.group(3)+(ko?"초":"s"));
        m=Pattern.compile("^([＋+−-]?)(\\d+)秒$").matcher(s);if(m.matches())return m.group(1)+m.group(2)+(ko?"초":"s");

        m=Pattern.compile("^(\\d+)問$").matcher(s);if(m.matches())return m.group(1)+(ko?"문제":" questions");
        m=Pattern.compile("^(\\d+)文$").matcher(s);if(m.matches())return m.group(1)+(ko?"문장":" sentences");
        m=Pattern.compile("^(\\d+)歩$").matcher(s);if(m.matches())return m.group(1)+(ko?"걸음":" steps");
        m=Pattern.compile("^(\\d+)日$").matcher(s);if(m.matches())return m.group(1)+(ko?"일":" days");
        m=Pattern.compile("^(\\d+)回$").matcher(s);if(m.matches())return m.group(1)+(ko?"회":" times");
        m=Pattern.compile("^🔥 (\\d+)日  ·  最高 (\\d+)日$").matcher(s);if(m.matches())return ko?"🔥 "+m.group(1)+"일  ·  최고 "+m.group(2)+"일":"🔥 "+m.group(1)+" days  ·  Best "+m.group(2)+" days";
        m=Pattern.compile("^([0-9.]+) / (\\d+) 秒$").matcher(s);if(m.matches())return m.group(1)+" / "+m.group(2)+(ko?"초":" sec");
        m=Pattern.compile("^この月の成功  (\\d+)回$").matcher(s);if(m.matches())return ko?"이번 달 성공  "+m.group(1)+"회":"Successful this month  "+m.group(1);
        m=Pattern.compile("^あと(\\d+)日で(.+)$").matcher(s);if(m.matches())return ko?"앞으로 "+m.group(1)+"일 후 "+m.group(2):m.group(1)+" days to "+m.group(2);
        m=Pattern.compile("^Lap (\\d+)   (.+)   合計 (.+)$").matcher(s);if(m.matches())return (ko?"랩 ":"Lap ")+m.group(1)+"   "+m.group(2)+"   "+(ko?"합계 ":"Total ")+m.group(3);
        if(s.startsWith("覚えてください\n"))return (ko?"기억하세요\n":"Memorize\n")+s.substring("覚えてください\n".length());

        return s;
    }
    public static String datePattern(Context c,String kind){String l=language(c);if("month".equals(kind))return "ja".equals(l)?"yyyy年 M月":"ko".equals(l)?"yyyy년 M월":"MMMM yyyy";if("next".equals(kind))return "ja".equals(l)?"M月d日(E) H:mm":"ko".equals(l)?"M월 d일(E) H:mm":"EEE, MMM d, H:mm";if("short".equals(kind))return "ja".equals(l)?"yyyy/MM/dd (E)":"ko".equals(l)?"yyyy.MM.dd (E)":"EEE, MMM d, yyyy";return "ja".equals(l)?"yyyy年M月d日 (E)":"ko".equals(l)?"yyyy년 M월 d일 (E)":"EEE, MMM d, yyyy";}

    public static void localizeTree(View v){
        if(v==null)return;
        if(v instanceof EditText){EditText e=(EditText)v;CharSequence h=e.getHint();if(h!=null){String x=tr(e.getContext(),String.valueOf(h));if(!x.contentEquals(h))e.setHint(x);}}
        else if(v instanceof TextView){TextView t=(TextView)v;CharSequence old=t.getText();if(old!=null){String x=tr(t.getContext(),String.valueOf(old));if(!x.contentEquals(old))t.setText(x);}}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)localizeTree(g.getChildAt(i));}
    }
}
