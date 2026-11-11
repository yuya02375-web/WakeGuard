package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Catalog of app-wide color themes and their light/dark palettes. */
public final class AppThemes {
    private static final String PREFS = "wake_guard";
    private static final String KEY_ID = "app_theme_id";
    private static final String KEY_MODE = "app_theme_mode";
    private static final String DEFAULT_ID = "dawn-ink";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private AppThemes() {}

    /** A fully resolved palette. All colors are ARGB values. */
    public static final class Theme {
        public final String id;
        public final String nameJa;
        public final String nameEn;
        public final String nameKo;
        public final int bg;
        public final int bg2;
        public final int surface;
        public final int surface2;
        public final int surface3;
        public final int border;
        public final int borderHot;
        public final int accent;
        public final int accent2;
        public final int cyan;
        public final int blue;
        public final int violet;
        public final int pink;
        public final int text;
        public final int muted;
        public final int muted2;
        public final int success;
        public final int danger;
        public final int heroStart;
        public final int heroEnd;
        public final int heroText;
        public final int horizonLine;
        public final int navHighlight;
        public final int onAccent;
        public final boolean dark;

        private Theme(ThemeSpec spec, Scheme scheme, boolean isDark) {
            id = spec.id;
            nameJa = spec.nameJa;
            nameEn = spec.nameEn;
            nameKo = spec.nameKo;
            dark = isDark;
            bg = scheme.bg;
            surface = scheme.surface;
            text = scheme.text;
            accent = scheme.accent;
            accent2 = scheme.accent2 != 0 ? scheme.accent2
                    : (isDark ? mix(scheme.accent, Color.WHITE, .16f) : mix(scheme.accent, Color.BLACK, .12f));

            if ("dawn-ink".equals(id) && !isDark) {
                // Keep Dawn Ink's paper surfaces, while deriving emphasis from the
                // selected accent so a blue palette never inherits coral.
                bg2 = 0xFFEDE5DA;
                surface2 = 0xFFF2EAE1;
                surface3 = 0xFFE8DED2;
                border = 0xFFD4C8BA;
                borderHot = withAlpha(accent2, 0x70);
                cyan = 0xFF277C83;
                blue = 0xFF3E66A0;
                violet = 0xFF71599C;
                pink = 0xFFAA526C;
                muted = 0xFF56636D;
                muted2 = 0xFF5B5F69;
                success = 0xFF2E745A;
                danger = 0xFFAD3F36;
                heroStart = accent;
                heroEnd = accent2;
                heroText = bestForeground(heroStart, heroEnd);
                horizonLine = mix(bg, accent2, .43f);
                navHighlight = mix(bg, accent2, .13f);
                onAccent = contrastForeground(accent2);
            } else {
                bg2 = mix(bg, text, isDark ? .10f : .075f);
                surface2 = mix(surface, bg, isDark ? .52f : .42f);
                surface3 = mix(surface, bg, isDark ? .32f : .24f);
                border = mix(surface, text, isDark ? .28f : .20f);
                borderHot = withAlpha(accent2, 0x70);

                // Keep semantic hues recognizable while gently carrying each theme's accent hue.
                cyan = mix(isDark ? 0xFF62CED3 : 0xFF247E86, accent, .12f);
                blue = mix(isDark ? 0xFF8EB6F7 : 0xFF3D66A0, accent, .12f);
                violet = mix(isDark ? 0xFFC6A9F0 : 0xFF71599C, accent, .12f);
                pink = mix(isDark ? 0xFFE99DB5 : 0xFFAA526C, accent, .12f);

                muted = mix(text, bg, .23f);
                muted2 = mix(text, bg, .35f);
                success = isDark ? 0xFF79D59B : 0xFF286B4D;
                danger = isDark ? 0xFFFF8F98 : 0xFFA52C39;

                heroStart = isDark ? mix(bg2, accent, .26f) : accent;
                heroEnd = isDark ? mix(bg2, accent2, .34f) : accent2;
                heroText = bestForeground(heroStart, heroEnd);
                horizonLine = mix(bg, accent2, isDark ? .58f : .43f);
                navHighlight = mix(bg, accent2, isDark ? .24f : .13f);
                onAccent = contrastForeground(accent2);
            }
        }

        /** Localized short name for theme selectors. */
        public String label(Context context) {
            String language = "ja";
            if (context != null) {
                try { language = I18n.language(context); } catch (RuntimeException ignored) {}
            }
            if ("ko".equals(language)) return nameKo;
            if ("en".equals(language)) return nameEn;
            return nameJa;
        }
    }

    private static final class Scheme {
        final int bg, surface, text, accent, accent2;
        Scheme(int bg, int surface, int text, int accent) { this(bg, surface, text, accent, 0); }
        Scheme(int bg, int surface, int text, int accent, int accent2) {
            this.bg = bg;
            this.surface = surface;
            this.text = text;
            this.accent = accent;
            this.accent2 = accent2;
        }
    }

    private static final class ThemeSpec {
        final String id, nameJa, nameEn, nameKo;
        final Scheme light, dark;
        ThemeSpec(String id, String nameJa, String nameEn, String nameKo, Scheme light, Scheme dark) {
            this.id = id;
            this.nameJa = nameJa;
            this.nameEn = nameEn;
            this.nameKo = nameKo;
            this.light = light;
            this.dark = dark;
        }
    }

    // 25 themes: the existing Dawn Ink default plus 24 distinct visual directions.
    private static final ThemeSpec[] SPECS = {
        new ThemeSpec("dawn-ink", "夜明けのインク", "Dawn Ink", "새벽 잉크",
            new Scheme(0xFFF6F2EB, 0xFFFFFCF7, 0xFF252E36, 0xFF3E5C70),
            new Scheme(0xFF101820, 0xFF1B272E, 0xFFEDF1F0, 0xFF9AB6C6)),
        new ThemeSpec("aurora", "オーロラ", "Aurora", "오로라",
            new Scheme(0xFFEEF7FA, 0xFFFFFFFF, 0xFF18313B, 0xFF007E9E),
            new Scheme(0xFF0C1A20, 0xFF152A33, 0xFFE7F7FA, 0xFF55C9D9)),
        new ThemeSpec("evergreen", "深い森", "Evergreen", "깊은 숲",
            new Scheme(0xFFF1F6EF, 0xFFFFFFFF, 0xFF203128, 0xFF367349),
            new Scheme(0xFF101C15, 0xFF1A2B20, 0xFFE8F3E9, 0xFF79C78A)),
        new ThemeSpec("dune", "砂丘", "Dune", "사구",
            new Scheme(0xFFF7F2E8, 0xFFFFFDF8, 0xFF392F21, 0xFF9A651F),
            new Scheme(0xFF20190F, 0xFF302519, 0xFFF6EBDD, 0xFFE2B45D)),
        new ThemeSpec("abyss", "深海", "Abyss", "심해",
            new Scheme(0xFFEDF5F7, 0xFFFFFFFF, 0xFF17303A, 0xFF126B81),
            new Scheme(0xFF0B1C22, 0xFF152B34, 0xFFE2F3F6, 0xFF51BCD2)),
        new ThemeSpec("glacier", "氷河", "Glacier", "빙하",
            new Scheme(0xFFF2F8FE, 0xFFFFFFFF, 0xFF1A2D43, 0xFF2877B9),
            new Scheme(0xFF101A27, 0xFF192B3E, 0xFFE7F2FF, 0xFF7CBDF7)),
        new ThemeSpec("sage", "セージ", "Sage", "세이지",
            new Scheme(0xFFF1F4EC, 0xFFFFFFFF, 0xFF283329, 0xFF6A7951),
            new Scheme(0xFF171C12, 0xFF252B1E, 0xFFEAF0DF, 0xFFC1D28B)),
        new ThemeSpec("wisteria", "藤の花", "Wisteria", "등나무꽃",
            new Scheme(0xFFF5F1FA, 0xFFFFFFFF, 0xFF30263C, 0xFF7354A2),
            new Scheme(0xFF191321, 0xFF281E34, 0xFFF1EAF9, 0xFFC09AE9)),
        new ThemeSpec("sakura", "桜", "Sakura", "벚꽃",
            new Scheme(0xFFFCF3F6, 0xFFFFFFFF, 0xFF3C2630, 0xFFB14B72),
            new Scheme(0xFF25141D, 0xFF351F2A, 0xFFF8EAF0, 0xFFF38EB5)),
        new ThemeSpec("solar", "太陽", "Solar", "태양",
            new Scheme(0xFFFFF8E8, 0xFFFFFFFF, 0xFF3B2E13, 0xFFA66E00),
            new Scheme(0xFF231B09, 0xFF352B12, 0xFFFFF2CC, 0xFFF4C741)),
        new ThemeSpec("lunar", "月夜", "Lunar", "달빛",
            new Scheme(0xFFF1F3F8, 0xFFFFFFFF, 0xFF242A3B, 0xFF6672A8),
            new Scheme(0xFF11141E, 0xFF1C2130, 0xFFE9ECF8, 0xFFAEBBF9)),
        new ThemeSpec("carbon", "カーボン", "Carbon", "카본",
            new Scheme(0xFFF0F2F3, 0xFFFFFFFF, 0xFF20272C, 0xFF444F57),
            new Scheme(0xFF101417, 0xFF1B2226, 0xFFE8EDF0, 0xFFAFBCC4)),
        new ThemeSpec("mint", "ミント", "Mint", "민트",
            new Scheme(0xFFEDF7F3, 0xFFFFFFFF, 0xFF1C332C, 0xFF25816A),
            new Scheme(0xFF10201B, 0xFF19332B, 0xFFE2F5EC, 0xFF69D6A8)),
        new ThemeSpec("ruby", "ルビー", "Ruby", "루비",
            new Scheme(0xFFFBF0F2, 0xFFFFFFFF, 0xFF3B1C23, 0xFFAA273D),
            new Scheme(0xFF240E12, 0xFF34181D, 0xFFFBE9EB, 0xFFFF737F)),
        new ThemeSpec("plum", "プラム", "Plum", "플럼",
            new Scheme(0xFFF6F0F7, 0xFFFFFFFF, 0xFF34253B, 0xFF804C91),
            new Scheme(0xFF1D1321, 0xFF2E1D33, 0xFFF4E7F6, 0xFFD297E0)),
        new ThemeSpec("nordic", "北欧", "Nordic", "북유럽",
            new Scheme(0xFFF1F4F6, 0xFFFFFFFF, 0xFF24323A, 0xFF4B7380),
            new Scheme(0xFF151D22, 0xFF202D33, 0xFFE6EFF1, 0xFF8ABDC5)),
        new ThemeSpec("terminal", "レトロ端末", "Retro Terminal", "레트로 단말기",
            new Scheme(0xFFECF7EF, 0xFFF8FFF8, 0xFF11271B, 0xFF20764C),
            new Scheme(0xFF08170E, 0xFF10251A, 0xFFDBF9E4, 0xFF5DDD94)),
        new ThemeSpec("arcade", "ネオンアーケード", "Neon Arcade", "네온 아케이드",
            new Scheme(0xFFF7F0FB, 0xFFFFFFFF, 0xFF291C34, 0xFFA6169B),
            new Scheme(0xFF160D1E, 0xFF251431, 0xFFF6E8FF, 0xFFF466DB)),
        new ThemeSpec("mono", "モノクロ", "Monochrome", "흑백",
            new Scheme(0xFFF4F4F4, 0xFFFFFFFF, 0xFF202020, 0xFF505050),
            new Scheme(0xFF121212, 0xFF1E1E1E, 0xFFF1F1F1, 0xFFD0D0D0)),
        new ThemeSpec("blueprint", "青写真", "Blueprint", "청사진",
            new Scheme(0xFFEDF4FC, 0xFFFFFFFF, 0xFF172A43, 0xFF2461A6),
            new Scheme(0xFF0C1826, 0xFF172B43, 0xFFE4F0FF, 0xFF6EB7FF)),
        new ThemeSpec("lagoon", "ラグーン", "Lagoon", "라군",
            new Scheme(0xFFEAF7F7, 0xFFFBFFFF, 0xFF173333, 0xFF16857E),
            new Scheme(0xFF0B211F, 0xFF17332F, 0xFFE2F7F4, 0xFF68D6C6)),
        new ThemeSpec("coffee", "コーヒー", "Coffee", "커피",
            new Scheme(0xFFF5F0E9, 0xFFFFFDF9, 0xFF302820, 0xFF745039),
            new Scheme(0xFF1D1813, 0xFF2C231C, 0xFFF2EAE0, 0xFFD6AB80)),
        new ThemeSpec("storm", "嵐", "Storm", "폭풍",
            new Scheme(0xFFEDF0F4, 0xFFFFFFFF, 0xFF252D3A, 0xFF5B6982),
            new Scheme(0xFF141820, 0xFF222A36, 0xFFE9EEF5, 0xFFA4B4CE)),
        new ThemeSpec("poppy", "ポピー", "Poppy", "양귀비",
            new Scheme(0xFFFFF1EC, 0xFFFFFFFF, 0xFF3A2520, 0xFFB9382F),
            new Scheme(0xFF24120F, 0xFF351F1A, 0xFFFAE8E2, 0xFFFF8771)),
        new ThemeSpec("wax", "蜜蝋", "Beeswax", "밀랍",
            new Scheme(0xFFF7F5E8, 0xFFFFFFF8, 0xFF303019, 0xFF748421),
            new Scheme(0xFF191A0D, 0xFF272A18, 0xFFF0F2DC, 0xFFCBD86E))
    };

    /** Stable cache key combining the selected theme ID with its stored mode. */
    public static String key(Context context) { return id(context) + ":" + mode(context); }

    /** Selected theme ID, falling back to Dawn Ink when the preference is missing or unknown. */
    public static String id(Context context) {
        String value = readString(context, KEY_ID, DEFAULT_ID);
        ThemeSpec spec = findSpec(value);
        return spec == null ? DEFAULT_ID : spec.id;
    }

    /** Stored mode, always one of system, light, or dark. */
    public static String mode(Context context) {
        return normalizeMode(readString(context, KEY_MODE, MODE_SYSTEM));
    }

    /** Catalog resolved to the current stored/system mode. */
    public static List<Theme> all(Context context) { return all(context, mode(context)); }

    /** Catalog resolved to the requested mode without changing preferences. */
    public static List<Theme> all(Context context, String requestedMode) {
        boolean dark = resolveDark(context, requestedMode);
        List<Theme> themes = new ArrayList<>(SPECS.length);
        for (ThemeSpec spec : SPECS) themes.add(new Theme(spec, dark ? spec.dark : spec.light, dark));
        return Collections.unmodifiableList(themes);
    }

    /** Selected palette, resolved to the current stored/system mode. */
    public static Theme current(Context context) { return get(context, id(context), mode(context)); }

    /** Finds a palette for the current stored/system mode; unknown IDs safely fall back to Dawn Ink. */
    public static Theme get(Context context, String themeId) { return get(context, themeId, mode(context)); }

    /** Finds a palette for the requested mode without changing preferences. */
    public static Theme get(Context context, String themeId, String requestedMode) {
        ThemeSpec spec = findSpec(themeId);
        if (spec == null) spec = findSpec(DEFAULT_ID);
        boolean dark = resolveDark(context, requestedMode);
        return new Theme(spec, dark ? spec.dark : spec.light, dark);
    }

    /** Saves a valid theme ID and applies it immediately. */
    public static void select(Context context, String themeId) {
        if (context == null) return;
        ThemeSpec spec = findSpec(themeId);
        writeString(context, KEY_ID, spec == null ? DEFAULT_ID : spec.id);
        apply(context);
    }

    /** Saves a supported mode (invalid values become system) and applies it immediately. */
    public static void setMode(Context context, String requestedMode) {
        if (context == null) return;
        writeString(context, KEY_MODE, normalizeMode(requestedMode));
        apply(context);
    }

    /** Applies the selected palette to the shared mutable UI palette. */
    public static void apply(Context context) {
        if (context == null) return;
        Ui.applyThemePalette(current(context));
        WidgetSuite.refreshPaletteWidgets(context);
    }

    private static ThemeSpec findSpec(String id) {
        if (id == null) return null;
        for (ThemeSpec spec : SPECS) if (spec.id.equals(id.trim())) return spec;
        return null;
    }

    private static String normalizeMode(String value) {
        if (MODE_LIGHT.equalsIgnoreCase(value)) return MODE_LIGHT;
        if (MODE_DARK.equalsIgnoreCase(value)) return MODE_DARK;
        return MODE_SYSTEM;
    }

    private static boolean resolveDark(Context context, String requestedMode) {
        String normalized = normalizeMode(requestedMode);
        if (MODE_DARK.equals(normalized)) return true;
        if (MODE_LIGHT.equals(normalized) || context == null) return false;
        try {
            int mask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mask == Configuration.UI_MODE_NIGHT_YES;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String readString(Context context, String key, String fallback) {
        if (context == null) return fallback;
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String value = preferences.getString(key, fallback);
            return value == null ? fallback : value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void writeString(Context context, String key, String value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply();
    }

    private static int mix(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) * (1f - t) + Color.red(to) * t),
                Math.round(Color.green(from) * (1f - t) + Color.green(to) * t),
                Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t));
    }

    private static int withAlpha(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }

    private static int bestForeground(int first, int second) {
        double whiteScore = Math.min(contrastRatio(Color.WHITE, first), contrastRatio(Color.WHITE, second));
        double blackScore = Math.min(contrastRatio(Color.BLACK, first), contrastRatio(Color.BLACK, second));
        return whiteScore >= blackScore ? Color.WHITE : Color.BLACK;
    }

    private static int contrastForeground(int background) {
        return luminance(background) > .179 ? Color.BLACK : Color.WHITE;
    }

    private static double contrastRatio(int foreground, int background) {
        double l1 = luminance(foreground), l2 = luminance(background);
        return (Math.max(l1, l2) + .05) / (Math.min(l1, l2) + .05);
    }

    private static double luminance(int color) {
        double r = linear(Color.red(color) / 255.0);
        double g = linear(Color.green(color) / 255.0);
        double b = linear(Color.blue(color) / 255.0);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static double linear(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }
}
