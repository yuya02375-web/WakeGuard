# アラーム編集画面（AlarmEditorActivity）再設計メモ

- 担当: Yuri（Androidエンジニア）
- 対象: app/src/main/java/jp/wakeguard/alarm/AlarmEditorActivity.java（2.9.9 の現行。274行、41,482バイト）
- 状態: 2.9.9時点の設計案。編集画面の大幅刷新は2.10.0（versionCode 200）に実装済み。個別の最終仕様は現行コードを参照。

## 1. 現行2.9.9の確認

- 入口は2つ。MainActivity.edit(id)（MainActivity.java:83）と、事前通知の通知タップ（AlarmReminderReceiver.java:26）。どちらも extra の alarmId を渡す（新規は -1）。
- 画面は1本の ScrollView に積んだカード。時刻ヒーロー（固定の青グラデーション）→ 名前 → 解除方法（ダイアログで13種から選ぶ）→ 通知とスヌーズ → 音と振動 → 自動停止 → テスト・削除 → 下部の保存バー。
- 色は Ui の暗色定数に直接依存。ヒーローだけ 0xFF2B465D 系の固定色。
- 保存は saveDraft()（:223）に集約。保存後に SnoozeScheduler と AlarmScheduler.reschedule を呼ぶ。
- 既知の不具合: 削除済みの追加アラーム（id 1000以上）を開くと、AlarmProfiles.get が主アラーム（id=1）を返す（AlarmProfiles.java:30-34）。保存すると主アラームの設定が書き換わる。

## 2. 変えないもの（不変条件）

| # | 項目 | 現行の規則（参照） |
|---|---|---|
| 1 | 保存先 | 主アラーム（id=1）は Prefs、追加アラームは AlarmStore（新規は add、既存は update）（AlarmProfiles.save） |
| 2 | 曜日 | dayMask の bit0=月 … bit6=日。曜日を1つでも選ぶと fixedAt は 0 になる（saveDraft） |
| 3 | 固定日時 fixedAt | 時刻ピッカーで時刻を変えると fixedAt=0（:44）。保存時、過去（1秒の余裕を含む）なら 0、未来なら時刻を fixedAt の時分に合わせる（saveDraft） |
| 4 | 1回設定 | 曜日なしは1回だけ鳴る（文言「曜日を選ばなければ1回だけ鳴ります」） |
| 5 | ミッション | 13キー（MISSION_KEYS :21）と表示名（:22）。歩数 steps は 1〜9999（既定10）、回数 missionCount は 1〜500（既定3）。欄の出し分けと説明文は refreshMissionFields（:126-140）どおり。RANDOM は 11種から鳴るたびに選ぶ（AlarmProfiles.resolveSessionMission） |
| 6 | 事前通知 | オフ・5/10/15/30/60/120分・カスタム 1〜1440分（:144-161）。新規の既定は30分 |
| 7 | スヌーズ | オン時の初期値は5分。間隔は 1/3/5/10/15/20/30/45/60分。オフなら 0 で保存し、SnoozeScheduler.cancel（:223） |
| 8 | 音 | 空なら標準音。音声・動画は filesDir の alarm_sounds / alarm_videos にコピー（copySound・useVideo）。差し替えた旧動画は消す（cleanupOwnedVideo）。動画は全画面表示の許可を案内（promptFullScreenVideoPermissionIfNeeded） |
| 9 | 音量・振動 | 音量 0〜100（既定15）。振動は OFF / IRREGULAR（既定。ほかの値もこれ）/ STRONG |
| 10 | 自動停止（2系統） | 「音・振動だけ自動停止」= ringDurationSec、「アラームを完全自動停止」= fullStopDurationSec。スイッチは「時間が0より大きい」と対応。プリセットは 30秒・1分・3分・5分・10分・15分・30分・1時間。カスタムは 分 0〜1440 と 秒 0〜59。0秒は「1秒以上に設定」で不可。上限は 86400 秒。両方オンで完全停止≦音停止のとき警告。スイッチをオンにして時間を決めずに閉じると、スイッチは戻る（disableOnCancel） |
| 11 | テスト | 今の内容を保存してから ACTION_FIRE_TEST を AlarmService に送る |
| 12 | 削除 | 追加アラーム（id 1000以上）だけ表示。確認のあと cancelExtraAlarm → delete → reschedule → 閉じる。主アラームは削除できない |
| 13 | 保存 | 失敗時は「設定値を確認してください」で画面に残る。成功で閉じる |
| 14 | 試聴 | 画面を離れたら止める（onStop）。STREAM_ALARM で再生 |
| 15 | 新規の既定値 | AlarmProfiles.defaults（07:00、曜日なし、有効、名前「アラーム」、歩数10、ミッションなし、回数・音量・振動・音は主アラームの値、事前通知30分、スヌーズなし、自動停止なし） |

## 3. 新しい画面の構造（上から）

1. 上部バー: 戻る（48dp）と見出し（追加／編集）。背景は朝の淡いグラデーション。
2. 時刻ヒーロー（1枚のカード）: 上端は朝焼けの帯（SUNRISE → SHEET）。
   - 「アラームを有効にする」スイッチ
   - 時刻（58sp、タップで変更）と「タップして時刻を変更」
   - 「繰り返す曜日」と 月〜日。選んだ曜日はインク色の塗り。文字拡大が大きいときは 4 + 3 の2段
   - 曜日の要約（既存の文言）
3. 名前（1枚）
4. 解除方法（1枚）: 3列のタイル（なし・歩数・計算／連打・コード入力・シェイク／記憶・文章入力・長押し／スワイプ・順番タップ・逆順入力）と、横長の「ランダム」。選んだタイルは朱の枠と淡い朱の地。下に「ミッションの設定」（歩数欄・回数欄・説明文）。
5. 通知（1枚）: 事前通知の値ボタン
6. スヌーズ（1枚）: スイッチと「再通知まで」（オンのときだけ表示）
7. 音と振動（1枚）: 音声・動画の選択、状態、試聴、標準音に戻す、音量、振動の3択
8. 自動停止（1枚）: 要約文と警告。中に2ブロック
   - 「1 · 音と振動」: スイッチ「音・振動だけ自動停止」、「音・振動を止めるまで」の値ボタン、説明
   - 「2 · 完全終了」: スイッチ「アラームを完全自動停止」、「全部止めるまで」の値ボタン、説明
9. テスト・削除（削除は追加アラームだけ）
10. 下部に固定する保存バー: 「保存」（朱の塗り、白文字、高さ56dp）。キーボードが出ても隠れないよう、SOFT_INPUT_ADJUST_RESIZE をコードで指定する（マニフェストは変えない）。

## 4. パレット（Ui担当へ）

定数名は既存のものを残して値を差し替え、新しい名前を追加する（他の画面が壊れないように）。

| 新しい名前 | 色 | 用途 | 既存名（値を差し替え） |
|---|---|---|---|
| PAPER | #F6EFE6 | 画面の地色 | BG |
| PAPER_DEEP | #EDE3D6 | 沈んだ地、トラック | BG_2 |
| SHEET | #FFFBF6 | カードの面 | SURFACE |
| SHEET_DEEP | #F4EADF | 小さな面（タイルの地） | SURFACE_2 |
| LINE | #E2D4C4 | 罫線と枠 | BORDER |
| INK | #1E2733 | 本文、時刻、選んだ曜日 | TEXT |
| INK_SOFT | #465162 | 補足の文字 | MUTED |
| INK_MUTE | #5F6877 | ヒントと注記 | MUTED_2 |
| DAWN | #C8362A | 主な操作、スイッチ、選択の強調 | ACCENT、ACCENT_2 |
| DAWN_DEEP | #A8291F | 押したとき（新規） | - |
| ON_DAWN | #FFFFFF | DAWN の上の文字（新規） | - |
| CORAL | #FF8A70 | 朝の光。装飾だけで文字には使わない（新規） | - |
| CORAL_WASH | #FFE7DF | 選んだタイルの地（新規） | - |
| SUNRISE | #FFD3C2 | ヒーロー上端の朝焼け（新規） | - |
| DANGER | #B3261E | 削除と警告の文字 | DANGER |
| SUCCESS | #2F7D5C | 完了の表示 | SUCCESS |

主な文字の組み合わせのコントラスト比（目安）: INK/PAPER 約13:1、INK/SHEET 約14:1、INK_SOFT/SHEET 約7.8:1、INK_MUTE/SHEET 約5.5:1、DAWN/SHEET 約5.1:1、ON_DAWN/DAWN 約5.2:1、INK/SUNRISE 約11:1。

Ui担当への依頼:
- Ui のメソッド名と引数は変えない（追加のみ）。エディタは text、title、ghostButton、button、iconButton、styleSwitch、round、roundStroke、dp、applySystemBarInsets、prepareActivity を使う。
- 暗色前提の値を明色用に直す。黒い波紋 0x33FFFFFF（Ui.button、iconButton）、アイコン色 0xFF251811（iconButton）、スイッチのオレンジ 0x99FF5A16（styleSwitch）、ナビの指示色 0xFF503A32、帯の 0x443C2820（accentPill）など。
- ステータスバーとナビゲーションバーを暗色のアイコンにする。現行は styles.xml の windowLightStatusBar=false と windowLightNavigationBar=false、Ui.statusBar の setSystemUiVisibility(0) なので、明色の背景ではアイコンが見えなくなる。
- 起動画面（values-v31/ignido_splash.xml）の色も確認する。
- ランチャーアイコン（mipmap、ic_ignido_*）には触らない。
- Ui.java は一人だけが書く（全文書き換えの衝突を防ぐため）。

## 5. 文言（I18n担当へ）

新規は6件。英語と韓国語の組で I18n の表（MAP）に追加する。tr は日本語以外で表を引く（I18n.java:220-224）。表になければ翻訳されない。

- 選択中 → Selected / 선택됨
- ミッションの設定 → Mission settings / 미션 설정
- 通知 → Notification / 알림
- 1 · 音と振動 → 1 · Sound & vibration / 1 · 소리와 진동
- 2 · 完全終了 → 2 · Full stop / 2 · 완전 종료
- このアラームは削除されています → This alarm has been deleted / 이 알람은 삭제되었습니다

それ以外は、現行にある文言をそのまま使う。

## 6. 現行からの変更（2点）

A. 削除済みの追加アラームを開いたとき、主アラームを表示も保存もしない。「このアラームは削除されています」と出して閉じる（不具合の修正）。
B. 試聴の音量は、スライダーの今の値で鳴らす（現行は保存済みの値で鳴る）。

どちらも、アラームの鳴り方・保存データ・スケジューラには触れない。反対がなければこのまま入れる。

## 7. 分担

- Yuri: AlarmEditorActivity.java を全文で書く。AlarmStore・AlarmProfiles・Prefs・AlarmScheduler・AlarmService・BackupVault は変更しない。
- Ui担当: Ui.java を単独で書く（4節の依頼）。
- I18n担当: I18n.java を単独で書く（5節の6件）。
- ホーム・時計・統計の担当: 同じパレットの定数名を使う。エディタの見た目の部品は、私がエディタの中に private で持つ（共通化は後）。
- 確かめ役（reviewer）: 2節の不変条件を、完成した差分と照らす。

## 8. 検証

- 静的確認: 2節の各項目を AlarmEditorActivity の行と照合する。保存・スケジューラ・データの各ファイルが差分に出ないことを確かめる。
- ビルド: 全員の差分がそろったら project_build（assembleDebug）を実行する（利用者の承認が出てから）。
- 利用者の手で確かめる項目（この作業では端末を使わない）: 日本語・英語・韓国語の表示、文字の拡大（200%）、システムバーの余白、キーボードが出たときの保存バー、TalkBack の読み上げ順。
- 静的プレビュー: 合意後、エディタの部分を design/preview/editor.html にまとめる（私が作る）。
