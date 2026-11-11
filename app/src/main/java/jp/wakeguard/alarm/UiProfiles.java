package jp.wakeguard.alarm;

import android.content.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independent catalog of UI structure and component treatments. */
public final class UiProfiles {
    private static final String PREFS = "wake_guard";
    private static final String KEY_ID = "app_ui_profile_id";
    private static final String DEFAULT_ID = "classic";

    public static final class Design {
        public final String id;
        public final String nameJa;
        public final String nameEn;
        public final String nameKo;
        public final String descriptionJa;
        public final String descriptionEn;
        public final String descriptionKo;
        public final String categoryJa;
        public final String categoryEn;
        public final String categoryKo;
        public final String layoutMode;
        public final float densityScale;
        public final float cornerScale;
        public final String navMode;
        public final String cardMode;

        private Design(String id, String[] names, String[] descriptions, String[] categories,
                       String layoutMode, float densityScale, float cornerScale,
                       String navMode, String cardMode) {
            this.id = id;
            this.nameJa = names[0]; this.nameEn = names[1]; this.nameKo = names[2];
            this.descriptionJa = descriptions[0]; this.descriptionEn = descriptions[1]; this.descriptionKo = descriptions[2];
            this.categoryJa = categories[0]; this.categoryEn = categories[1]; this.categoryKo = categories[2];
            this.layoutMode = layoutMode; this.densityScale = densityScale; this.cornerScale = cornerScale;
            this.navMode = navMode; this.cardMode = cardMode;
        }

        public String label(Context context) {
            String language = I18n.language(context);
            return "en".equals(language) ? nameEn : "ko".equals(language) ? nameKo : nameJa;
        }

        public String description(Context context) {
            String language = I18n.language(context);
            return "en".equals(language) ? descriptionEn : "ko".equals(language) ? descriptionKo : descriptionJa;
        }

        public String category(Context context) {
            String language = I18n.language(context);
            return "en".equals(language) ? categoryEn : "ko".equals(language) ? categoryKo : categoryJa;
        }
    }

    private static Design p(String id, String[] names, String[] descriptions, String[] categories,
                            String layout, float density, float corners, String nav, String cards) {
        return new Design(id, names, descriptions, categories, layout, density, corners, nav, cards);
    }

    private static final Design[] ITEMS = new Design[]{
        p("classic", n("クラシック", "Classic", "클래식"), d("標準のリストと均整の取れた余白。", "A balanced list with familiar spacing.", "익숙한 간격의 균형 잡힌 목록."), c("ベーシック", "Essentials", "기본"), "list", 1.00f, 1.00f, "pill", "elevated"),
        p("compact", n("ポケット", "Pocket", "포켓"), d("情報を詰めた小さめの行で一覧性を高めます。", "Compact rows fit more information on screen.", "촘촘한 행으로 더 많은 정보를 보여 줍니다."), c("ベーシック", "Essentials", "기본"), "list", 0.84f, 0.78f, "compact", "flat"),
        p("spacious", n("余白", "Airy", "여백"), d("ゆったりした間隔と大きなカードで読みやすく。", "Generous spacing and large cards improve readability.", "넉넉한 간격과 큰 카드로 읽기 쉽습니다."), c("ベーシック", "Essentials", "기본"), "list", 1.18f, 1.22f, "labels", "elevated"),
        p("timeline", n("タイムライン", "Timeline", "타임라인"), d("時系列の流れを強調する縦型レイアウト。", "A vertical layout emphasizes chronological flow.", "시간 순서를 강조하는 세로형 레이아웃."), c("ベーシック", "Essentials", "기본"), "timeline", 1.02f, 0.92f, "compact", "outlined"),
        p("tiles", n("タイル", "Tiles", "타일"), d("項目をタイル状に並べて、ひと目で選べます。", "Arrange items as tiles for quick scanning.", "항목을 타일로 배치해 빠르게 살펴봅니다."), c("ベーシック", "Essentials", "기본"), "tiles", 1.04f, 1.18f, "pill", "elevated"),
        p("editorial", n("エディトリアル", "Editorial", "에디토리얼"), d("大きな見出しと余白を生かした雑誌風。", "Magazine-like hierarchy with generous headlines.", "큰 제목과 여백을 살린 잡지 스타일."), c("読みやすさ", "Readability", "가독성"), "list", 1.12f, 0.82f, "labels", "flat"),
        p("ledger", n("台帳", "Ledger", "레저"), d("整った列と細い区切りで記録帳のように整理。", "Neat rows and fine rules evoke a well-kept ledger.", "정돈된 행과 얇은 구분선의 장부 스타일."), c("読みやすさ", "Readability", "가독성"), "list", 0.96f, 0.72f, "compact", "outlined"),
        p("typewriter", n("タイプライター", "Typewriter", "타자기"), d("小さな行と輪郭カードで機械的なリズムに。", "Tight rows and outlined cards create a mechanical rhythm.", "촘촘한 행과 테두리 카드로 기계적인 리듬을 만듭니다."), c("読みやすさ", "Readability", "가독성"), "list", 0.94f, 0.76f, "compact", "outlined"),
        p("book", n("ブックページ", "Book Page", "책 페이지"), d("ゆったりした一覧とフラットカードで静かなページに。", "An airy list and flat cards create a calm page-like layout.", "여유 있는 목록과 플랫 카드로 차분한 페이지를 만듭니다."), c("読みやすさ", "Readability", "가독성"), "list", 1.06f, 1.08f, "labels", "flat"),
        p("ticker", n("ティッカー", "Ticker", "티커"), d("短いタイムラインとコンパクトナビで素早く確認。", "A tight timeline and compact navigation make scanning fast.", "짧은 타임라인과 간결한 탐색으로 빠르게 훑어봅니다."), c("読みやすさ", "Readability", "가독성"), "timeline", 0.88f, 0.68f, "compact", "flat"),
        p("dashboard", n("ダッシュボード", "Dashboard", "대시보드"), d("タイルと大きなタップ領域で道具箱のように。", "Tile-based sections and large targets feel like a control panel.", "타일과 넓은 터치 영역으로 제어판처럼 구성합니다."), c("ワークスペース", "Workspaces", "워크스페이스"), "tiles", 1.12f, 1.08f, "labels", "elevated"),
        p("focus", n("フォーカス", "Focus", "포커스"), d("装飾を抑えて、主要な操作を前に出します。", "Reduced chrome keeps the main actions in focus.", "장식을 줄여 주요 동작에 집중합니다."), c("ワークスペース", "Workspaces", "워크스페이스"), "list", 0.94f, 0.90f, "compact", "flat"),
        p("command", n("コマンドデッキ", "Command Deck", "커맨드 덱"), d("小さなタイルとコンパクトナビで操作卓のように。", "Compact tiles and navigation create a command-deck layout.", "작은 타일과 간결한 탐색으로 제어판처럼 구성합니다."), c("ワークスペース", "Workspaces", "워크스페이스"), "tiles", 0.92f, 0.70f, "compact", "outlined"),
        p("planner", n("プランナー", "Planner", "플래너"), d("時刻を縦の流れに並べて予定を見通せます。", "A timeline layout makes the day's schedule easy to scan.", "타임라인으로 하루 일정을 한눈에 살펴봅니다."), c("ワークスペース", "Workspaces", "워크스페이스"), "timeline", 1.08f, 1.12f, "labels", "outlined"),
        p("magazine", n("マガジン", "Magazine", "매거진"), d("大きなタイルと広い間隔で大胆に見せます。", "Large tiles and generous spacing create an expressive layout.", "큰 타일과 넓은 간격으로 개성 있게 보여 줍니다."), c("ワークスペース", "Workspaces", "워크스페이스"), "tiles", 1.16f, 1.34f, "labels", "elevated"),
        p("field-notes", n("フィールドノート", "Field Notes", "필드 노트"), d("細い罫線と軽い間隔で観察ノート風に。", "Fine rules and light spacing resemble field notes.", "얇은 선과 가벼운 간격의 관찰 노트 스타일."), c("ワークスペース", "Workspaces", "워크스페이스"), "timeline", 0.98f, 0.72f, "compact", "outlined"),
        p("orbit", n("オービット", "Orbit", "오비트"), d("丸みのあるカードをタイル状に広げます。", "Rounded surfaces and tiles suggest a small orbiting system.", "둥근 표면과 타일로 작은 궤도 시스템을 표현합니다."), c("プレイ", "Play", "플레이"), "tiles", 1.08f, 1.40f, "pill", "elevated"),
        p("capsule", n("カプセル", "Capsule", "캡슐"), d("丸い形と余裕のある操作領域でやわらかな印象。", "Soft rounded forms and roomy controls feel tactile.", "둥근 모양과 넉넉한 조작 영역이 부드러운 느낌을 줍니다."), c("プレイ", "Play", "플레이"), "list", 1.14f, 1.40f, "pill", "elevated"),
        p("arcade", n("アーケード", "Arcade", "아케이드"), d("高密度タイルとコンパクトナビでゲーム機のように。", "Dense tiles and compact navigation evoke an arcade panel.", "촘촘한 타일과 간결한 탐색으로 아케이드 패널을 연상시킵니다."), c("プレイ", "Play", "플레이"), "tiles", 0.90f, 0.64f, "compact", "outlined"),
        p("stickerbook", n("ステッカーブック", "Sticker Book", "스티커북"), d("大きく丸いタイルを並べた遊び心のある配置。", "Playful, oversized tiles resemble arranged stickers.", "크고 둥근 타일을 스티커처럼 배치합니다."), c("プレイ", "Play", "플레이"), "tiles", 1.18f, 1.36f, "labels", "elevated"),
        p("metro", n("メトロ", "Metro", "메트로"), d("角張った輪郭とタイムラインで標識のように整理。", "Crisp outlines and a timeline borrow from transit signage.", "선명한 윤곽과 타임라인으로 교통 표지처럼 정리합니다."), c("幾何学", "Geometry", "기하학"), "timeline", 0.96f, 0.66f, "compact", "outlined"),
        p("brutalist", n("ブルータリスト", "Brutalist", "브루탈리스트"), d("角の立ったカードと強い輪郭で構造を見せます。", "Hard edges and strong outlines put structure first.", "각진 카드와 강한 윤곽으로 구조를 강조합니다."), c("幾何学", "Geometry", "기하학"), "tiles", 1.02f, 0.64f, "labels", "outlined"),
        p("column", n("カラム", "Column", "칼럼"), d("一覧を軽く保ち、見出しと内容をすっきり分離。", "A clean list keeps headings and details clearly separated.", "간결한 목록으로 제목과 내용을 명확히 나눕니다."), c("幾何学", "Geometry", "기하학"), "list", 0.90f, 0.78f, "labels", "flat"),
        p("panels", n("パネルグリッド", "Panel Grid", "패널 그리드"), d("情報を独立したパネルに分けて一覧します。", "Independent panels make a dense dashboard feel organized.", "독립된 패널로 정보를 나누어 정돈된 대시보드를 만듭니다."), c("幾何学", "Geometry", "기하학"), "tiles", 1.06f, 0.88f, "pill", "flat")
    };

    private static final List<Design> ALL = Collections.unmodifiableList(Arrays.asList(ITEMS));

    private static String[] n(String ja, String en, String ko) { return new String[]{ja, en, ko}; }
    private static String[] d(String ja, String en, String ko) { return new String[]{ja, en, ko}; }
    private static String[] c(String ja, String en, String ko) { return new String[]{ja, en, ko}; }

    private UiProfiles() {}

    public static List<Design> all() { return ALL; }

    public static Design current(Context context) {
        Design selected = get(id(context));
        return selected == null ? ITEMS[0] : selected;
    }

    public static Design get(String id) {
        if (id != null) for (Design design : ITEMS) if (design.id.equals(id)) return design;
        return null;
    }

    public static String id(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ID, DEFAULT_ID);
        return get(saved) == null ? DEFAULT_ID : saved;
    }

    public static void select(Context context, String id) {
        if (get(id) != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ID, id).apply();
    }
}
