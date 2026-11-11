package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Curated combinations of layout, visual treatment, palette, and character. */
public final class DesignSets {
    private static final String PREFS = "wake_guard";
    private static final String KEY_UI = "app_ui_profile_id";
    private static final String KEY_DESIGN = "app_visual_design_id";
    private static final String KEY_THEME = "app_theme_id";
    private static final String KEY_MASCOT = "app_mascot_id";

    public enum Part { UI, DESIGN, COLOR, CHARACTER }

    public static final class Preset {
        public final String id;
        public final String uiProfileId;
        public final String visualDesignId;
        public final String themeId;
        public final String mascotId;
        private final String ja, en, ko;
        private final String jaDescription, enDescription, koDescription;

        private Preset(String id, String ja, String en, String ko,
                       String jaDescription, String enDescription, String koDescription,
                       String uiProfileId, String visualDesignId, String themeId, String mascotId) {
            this.id = id;
            this.ja = ja;
            this.en = en;
            this.ko = ko;
            this.jaDescription = jaDescription;
            this.enDescription = enDescription;
            this.koDescription = koDescription;
            this.uiProfileId = uiProfileId;
            this.visualDesignId = visualDesignId;
            this.themeId = themeId;
            this.mascotId = mascotId;
        }

        public String label(Context context) {
            String language = I18n.language(context);
            return "ko".equals(language) ? ko : "en".equals(language) ? en : ja;
        }

        public String description(Context context) {
            String language = I18n.language(context);
            return "ko".equals(language) ? koDescription : "en".equals(language) ? enDescription : jaDescription;
        }

        public String components(Context context) {
            return UiProfiles.get(uiProfileId).label(context) + "  ·  "
                    + VisualDesigns.get(visualDesignId).label(context) + "  ·  "
                    + AppThemes.get(context, themeId).label(context) + "  ·  "
                    + MascotCatalog.get(mascotId).label(context);
        }
    }

    private static final List<Preset> ALL = Collections.unmodifiableList(Arrays.asList(
            p("classic-paper", "紙の朝", "Paper Morning", "종이 아침",
                    "読みやすい一覧と紙の質感。最初に試しやすいセットです。", "A readable list with a handcrafted paper finish.", "읽기 쉬운 목록과 손으로 만든 종이 질감입니다.",
                    "classic", "paper-craft", "dawn-ink", "none"),
            p("sky-dragon", "天空のドラゴン", "Sky Dragon", "하늘 드래곤",
                    "星図と青い配色、時刻を追いやすいタイムライン。", "A blue star chart with a timeline that tracks the next wake-up.", "푸른 별자리 지도와 기상 시간을 따라가는 타임라인입니다.",
                    "timeline", "moonlit-stars", "glacier", "dragon"),
            p("deep-dragon", "深海ドラゴン", "Abyss Dragon", "심해 드래곤",
                    "海流の模様と深い青緑。タイル型の操作パネルです。", "Ocean-current lines and deep teal panels with a dashboard layout.", "해류 무늬와 짙은 청록색의 대시보드입니다.",
                    "dashboard", "ocean-current", "abyss", "dragon"),
            p("forest-owl", "森のフクロウ", "Forest Owl", "숲 부엉이",
                    "植物標本の線画と自然な緑。観察ノート風の一覧です。", "Botanical sketches, forest green, and field-note rhythm.", "식물 스케치와 숲의 녹색을 담은 현장 노트 스타일입니다.",
                    "field-notes", "herbarium", "evergreen", "owl"),
            p("comic-dragon", "コミック・ドラゴン", "Comic Dragon", "코믹 드래곤",
                    "網点と赤いアクセントで、コミックの表紙のように。", "Halftone marks and red accents make a bold comic-book cover.", "망점과 붉은 강조색으로 만화 표지처럼 표현합니다.",
                    "stickerbook", "halftone-pop", "ruby", "dragon"),
            p("arcade-robot", "アーケード・ロボ", "Arcade Robot", "아케이드 로봇",
                    "走査線と端末風の緑。ゲーム機のような高密度表示です。", "Scanlines and terminal green in a compact arcade layout.", "주사선과 단말기 녹색을 사용한 아케이드 스타일입니다.",
                    "arcade", "pixel-arcade", "terminal", "robot"),
            p("moon-owl", "月夜の観測所", "Moon Observatory", "달밤 관측소",
                    "月の色と星の模様。予定を縦に眺めるプランナーです。", "Moonlit colors and a star field in a planner layout.", "달빛 색상과 별 무늬를 담은 플래너입니다.",
                    "planner", "moonlit-stars", "lunar", "owl"),
            p("sakura-fox", "桜キツネ", "Sakura Fox", "벚꽃 여우",
                    "花びら模様と桜色。余白を使ったエディトリアル表示です。", "Petal marks and sakura pink with an editorial layout.", "꽃잎 무늬와 벚꽃 색상의 에디토리얼 화면입니다.",
                    "editorial", "petal-orbit", "sakura", "fox"),
            p("circuit-dragon", "回路のドラゴン", "Circuit Dragon", "회로 드래곤",
                    "青い設計図と回路模様。小さなタイルで素早く操作できます。", "Blueprint blue and circuit traces in a compact command deck.", "청사진 색상과 회로 무늬를 담은 명령 패널입니다.",
                    "command", "circuit-trace", "blueprint", "dragon"),
            p("sun-fox", "太陽のキツネ", "Solar Fox", "태양 여우",
                    "放射状の図形と明るい橙。大きなタイルで見せます。", "Radiating geometry and warm solar color in large tiles.", "방사형 도형과 따뜻한 태양색의 큰 타일입니다.",
                    "magazine", "solar-orbit", "solar", "fox"),
            p("storm-robot", "嵐のロボット", "Storm Robot", "폭풍 로봇",
                    "機械のメーター模様と嵐色。角ばったパネルが並びます。", "Mechanical meter marks and storm gray in crisp panels.", "기계식 계기 무늬와 폭풍 회색의 패널입니다.",
                    "brutalist", "cassette-deck", "storm", "robot"),
            p("tidal-owl", "潮だまりのフクロウ", "Tidepool Owl", "조수 웅덩이 부엉이",
                    "小さなモザイクと青緑。ゆったりしたカードでまとめます。", "Tidepool mosaic and sea-glass tones with roomy cards.", "조수 웅덩이 모자이크와 바다 유리빛의 여유로운 카드입니다.",
                    "spacious", "tidepool", "lagoon", "owl")
    ));

    private DesignSets() {}

    private static Preset p(String id, String ja, String en, String ko,
                            String jaDescription, String enDescription, String koDescription,
                            String ui, String design, String theme, String mascot) {
        return new Preset(id, ja, en, ko, jaDescription, enDescription, koDescription,
                ui, design, theme, mascot);
    }

    public static List<Preset> all() { return ALL; }

    public static Preset get(String id) {
        if (id != null) for (Preset preset : ALL) if (preset.id.equals(id)) return preset;
        return null;
    }

    public static Preset current(Context context) {
        String ui = UiProfiles.id(context);
        String design = VisualDesigns.id(context);
        String theme = AppThemes.id(context);
        String mascot = MascotCatalog.id(context);
        for (Preset preset : ALL) {
            if (preset.uiProfileId.equals(ui) && preset.visualDesignId.equals(design)
                    && preset.themeId.equals(theme) && preset.mascotId.equals(mascot)) return preset;
        }
        return null;
    }

    public static String currentLabel(Context context) {
        Preset preset = current(context);
        return preset == null ? localized(context, "カスタム", "Custom", "사용자 설정") : preset.label(context);
    }

    public static boolean apply(Context context, String presetId) {
        return apply(context, presetId, null);
    }

    public static boolean applyPart(Context context, String presetId, Part part) {
        return part != null && apply(context, presetId, part);
    }

    private static boolean apply(Context context, String presetId, Part part) {
        if (context == null) return false;
        Preset preset = get(presetId);
        if (!valid(context, preset)) return false;

        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (part == null || part == Part.UI) edit.putString(KEY_UI, preset.uiProfileId);
        if (part == null || part == Part.DESIGN) edit.putString(KEY_DESIGN, preset.visualDesignId);
        if (part == null || part == Part.COLOR) edit.putString(KEY_THEME, preset.themeId);
        if (part == null || part == Part.CHARACTER) edit.putString(KEY_MASCOT, preset.mascotId);
        edit.apply();
        applyCurrent(context);
        return true;
    }

    private static boolean valid(Context context, Preset preset) {
        if (preset == null || UiProfiles.get(preset.uiProfileId) == null) return false;
        if (!preset.visualDesignId.equals(VisualDesigns.get(preset.visualDesignId).id)) return false;
        if (!preset.themeId.equals(AppThemes.get(context, preset.themeId).id)) return false;
        if (!MascotArtView.SUPPORTED_CHARACTER_IDS.contains(preset.mascotId) && !"none".equals(preset.mascotId)) return false;
        return true;
    }

    public static void applyCurrent(Context context) {
        if (context == null) return;
        AppThemes.apply(context);
        Ui.applyUiProfile(UiProfiles.current(context));
        Ui.applyVisualDesign(VisualDesigns.current(context));
    }

    private static String localized(Context context, String ja, String en, String ko) {
        String language = I18n.language(context);
        return "ko".equals(language) ? ko : "en".equals(language) ? en : ja;
    }
}
