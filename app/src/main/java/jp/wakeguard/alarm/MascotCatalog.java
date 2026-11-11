package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Saved mascot selection used by appearance sets and the home screen. */
public final class MascotCatalog {
    private static final String PREFS = "wake_guard";
    private static final String KEY_ID = "app_mascot_id";
    private static final String DEFAULT_ID = "none";

    public static final class Mascot {
        public final String id;
        private final String ja, en, ko;

        private Mascot(String id, String ja, String en, String ko) {
            this.id = id;
            this.ja = ja;
            this.en = en;
            this.ko = ko;
        }

        public String label(Context context) {
            String language = I18n.language(context);
            return "ko".equals(language) ? ko : "en".equals(language) ? en : ja;
        }
    }

    private static final List<Mascot> ALL = Collections.unmodifiableList(Arrays.asList(
            new Mascot("none", "なし", "None", "없음"),
            new Mascot("dragon", "ドラゴン", "Dragon", "드래곤"),
            new Mascot("fox", "キツネ", "Fox", "여우"),
            new Mascot("owl", "フクロウ", "Owl", "부엉이"),
            new Mascot("robot", "ロボット", "Robot", "로봇")
    ));

    private MascotCatalog() {}

    public static List<Mascot> all() { return ALL; }

    public static Mascot get(String id) {
        if (id != null) for (Mascot mascot : ALL) if (mascot.id.equals(id)) return mascot;
        return ALL.get(0);
    }

    public static String id(Context context) {
        if (context == null) return DEFAULT_ID;
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ID, DEFAULT_ID);
        return get(saved).id;
    }

    public static Mascot current(Context context) { return get(id(context)); }

    public static void select(Context context, String id) {
        if (context == null) return;
        Mascot mascot = get(id);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ID, mascot.id).apply();
    }
}
