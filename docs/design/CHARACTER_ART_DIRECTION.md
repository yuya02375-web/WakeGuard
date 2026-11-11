# キャラクター・アートディレクション（WakeGuard 2.13.0 案）

担当: character_artist / 状態: 提案（合意後に実装）

## 1. 確認した素材

- 場所: app/src/main/res/drawable-nodpi/ の mascot_dragon_premium_v1.png、mascot_fox_premium_v1.png、mascot_owl_premium_v1.png、mascot_robot_premium_v1.png（計約 6.0MB）
- 4 体とも、青〜銀色の本体、シアンの発光、光る目で統一されている。ドラゴンは額に三日月、ロボットは星の光点を持つ。これは図柄の三日月・星と同じ語彙。
- 縁の問題: ドラゴンの頭上とロボットの外周に白いもや、フォックスの左縁に赤紫の小さな点が数か所。暗い面では光として見えるが、明るい面では汚れに見えるおそれがある。
- 未確認: 透明（α）の有無と寸法。画像ビューアでは背景が黒く見えたが、透明なのか黒い不透明背景なのかはこの環境では判定できない。見た目では約 1254px 四方。

## 2. 方向性: 「夜明けの相棒」

1. 素材は加工しない。ColorFilter や再着色はしない。キャラの色はキャラのもの。テーマ色は、光と影にだけ使う。
2. 箱に入れない。枠・カード・四角い背景は付けない。テーマ色の低い不透明度の放射グラデーションと、足元の楕円の影だけで「置かれている」感じを出す。
3. 大きく見せ、操作は邪魔しない。キャラは装飾で、タップ対象にしない。スイッチ・ボタン・時刻の文字と重ならない余白を取る。
4. 図柄と語彙をそろえる。三日月と四つ角の星を、キャラとパターンの共通の形にする（パターン描画の一本化と同じ形状定義を使う）。

## 3. 表示サイズ（dp、正方形の箱）

| 場所 | 現在 | 提案 | 配置 |
|---|---|---|---|
| ホームのヒーロー（MainActivity） | 46 | 120 | 右上。ヘッダー帯へ約 1/3 はみ出す。ヒーローの高さは増やさない。時刻・ステータス・水平線の領域には入れない |
| キャラ選択の行（MascotPickerActivity） | 64 | 88 | 行の左 |
| キャラ選択の上の見本 | なし | 176（任意） | 画面上部の中央 |
| 図柄セットのカード（DesignSetPickerActivity） | 56 | 72 | 見出しの左。ミニホーム画面の中に入れる |
| アラームが 0 件の空の状態 | なし | 72 | 空の状態カードの左 |
| なし（none） | ✦ の記号 | ✦ のまま | キャラは出さない |

ホームのヒーローは、ヘッダー帯の右側へ重ねて描く（FrameLayout で重ねる）。これで高さを増やさずに大きく見せる。

## 4. 実装方針（MascotArtView.java を全面置換）

- 公開 API は維持: コンストラクタ（Context, String）、create、supportedCharacterIds、SUPPORTED_CHARACTER_IDS、Mascot.fromId、setMascot、setMascotColors、clearMascotColors。
- 3 言語の contentDescription（既存の文言）はそのまま。
- ベクターの描画（drawDragon、drawFox、drawOwl、drawRobot と補助の図形関数）は削除する。
- 対応: dragon → R.drawable.mascot_dragon_premium_v1、fox → mascot_fox_premium_v1、owl → mascot_owl_premium_v1、robot → mascot_robot_premium_v1。
- 1 体につき 1 回だけデコードする（inSampleSize = 2、約 627px、4 体で約 6MB）。静的キャッシュに置き、onDraw では読まない。キャッシュするのは無色の画像だけ。
- 描画順: 舞台（放射グラデーション、テーマ色の光）→ 足元の影（楕円）→ 画像（縦横比を保って中央に収める）。
- テーマ色は描画時に参照する（テーマ変更後の invalidate で反映。パレット更新の修正を壊さない）。
- 静的な描画関数を追加: MascotArtView.drawCharacter(Canvas, String characterId, RectF box, int accent)。図柄セットのミニホーム画面は、この関数を使って本物のホームと同じ見え方にする。
- 「none」を MascotArtView に渡したときは何も描かない。現在は既定でドラゴンになる。呼び出し側は既に none を分けているので、影響はない。

## 5. パターンと共通の形（寸法の仕様）

- 三日月: 外円の半径を r とする。内側の円（半径 0.82r）を、(+0.30r, -0.12r) ずらして差し引く。
- 四つ角の星: 中心から先端までを r、くびれ（内側の頂点）を 0.22r。線は使わず、塗りだけにする。

## 6. 承認してほしい点

1. 箱を付けない（光と影だけで置く）。
2. ホームのヒーローは、ヘッダー帯へのはみ出しで高さを増やさない。
3. PNG のまま出す（APK は約 6MB 増える）。WebP 化は、見た目を承認した後の別作業にする。前例: 既存の ignido_stage_*.webp。
4. サイズ: 図柄セット 72dp、キャラ選択 88dp、空の状態 72dp。

## 7. 未確認・要確認

- α の有無と寸法: 端末で BitmapFactory の hasAlpha を確認する。
- 白いもや・赤紫の点: 素材の元データで直すのが望ましい。コード側の閾値処理は最後の手段。
- 出典とライセンス: 4 体の PNG の出典・権利が未記載。ユーザーに確認し、必要なら assets に THIRD_PARTY_* 相当の記載を足す。
- 最終確認: android_screenshot（端末の画面撮影は承認不要）で、明るいテーマと暗いテーマの両方を撮る。インストールには承認が要る。

## 8. 担当ファイル（衝突を避けるため）

- character_artist: app/src/main/java/jp/wakeguard/alarm/MascotArtView.java（全面置換）、このファイル。
- 寸法の変更だけ依頼する: MainActivity.java の 57〜62 行（ヒーロー）、MascotPickerActivity.java の 56〜57 行（キャラ選択）、DesignSetPickerActivity.java の 85〜93 行（図柄セット）。各担当の方の作業に合わせて入れる。
