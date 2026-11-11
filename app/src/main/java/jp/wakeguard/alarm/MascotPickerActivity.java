package jp.wakeguard.alarm;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Chooses a character independently from a design set. */
public final class MascotPickerActivity extends Activity {
    private static final int CHARACTER_ART_DP = 88;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        body.addView(Ui.overline(this, "IGNIDO  /  WAKE"));
        TextView title = Ui.title(this, localized("キャラクター", "Characters", "캐릭터"), 29);
        title.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 4));
        body.addView(title);
        body.addView(Ui.text(this, localized(
                "ホーム画面のアラームカードに表示するキャラクターを選びます。",
                "Choose a little companion for the home screen.",
                "홈 화면 알람 카드에 표시할 캐릭터를 선택하세요."),
                13, Ui.MUTED));

        for (MascotCatalog.Mascot mascot : MascotCatalog.all()) addChoice(body, mascot);
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
    }

    private void addChoice(LinearLayout body, MascotCatalog.Mascot mascot) {
        boolean selected = mascot.id.equals(MascotCatalog.id(this));
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        card.setMinimumHeight(Ui.dp(this, 104));
        card.setBackground(Ui.roundStroke(Ui.SURFACE,
                selected ? Ui.ACCENT_2 : Ui.BORDER, 16, this));
        if ("none".equals(mascot.id)) {
            TextView icon = Ui.title(this, "✦", 34);
            icon.setTextColor(Ui.ACCENT_2);
            icon.setGravity(Gravity.CENTER);
            card.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dp(this, CHARACTER_ART_DP), Ui.dp(this, CHARACTER_ART_DP)));
        } else {
            MascotArtView art = new MascotArtView(this, mascot.id);
            card.addView(art, new LinearLayout.LayoutParams(
                    Ui.dp(this, CHARACTER_ART_DP), Ui.dp(this, CHARACTER_ART_DP)));
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(this, 11), 0, 0, 0);
        labels.addView(Ui.title(this, mascot.label(this), 17));
        String subtitle = selected ? localized("選択中", "Selected", "선택됨")
                : localized("タップして選択", "Tap to select", "탭하여 선택");
        labels.addView(Ui.text(this, subtitle, 12,
                selected ? Ui.ACCENT_2 : Ui.MUTED), Ui.gapTop(this, 3));
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView check = Ui.title(this, selected ? "✓" : "○", 22);
        check.setTextColor(selected ? Ui.ACCENT_2 : Ui.MUTED);
        card.addView(check);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            MascotCatalog.select(this, mascot.id);
            setResult(Activity.RESULT_OK);
            finish();
        });
        body.addView(card, Ui.gapTop(this, 10));
    }

    private String localized(String ja, String en, String ko) {
        String language = I18n.language(this);
        return "ko".equals(language) ? ko : "en".equals(language) ? en : ja;
    }
}
