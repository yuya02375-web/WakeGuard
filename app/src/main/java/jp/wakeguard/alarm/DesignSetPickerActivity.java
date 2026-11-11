package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Lets the user apply a complete appearance set or only one of its parts. */
public final class DesignSetPickerActivity extends Activity {
    private LinearLayout body;
    private ScrollView scrollView;
    private String expandedId;
    private int scrollY;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        if (state != null) {
            expandedId = state.getString("expandedSet");
            scrollY = state.getInt("scrollY");
        }
        build();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("expandedSet", expandedId);
        out.putInt("scrollY", scrollView == null ? scrollY : scrollView.getScrollY());
        super.onSaveInstanceState(out);
    }

    private void build() {
        Ui.prepareActivity(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 28));
        scrollView.addView(body);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        body.addView(Ui.overline(this, "IGNIDO  /  WAKE"));
        TextView title = Ui.title(this, localized("デザインセット", "Design sets", "디자인 세트"), 29);
        title.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 4));
        body.addView(title);
        body.addView(Ui.text(this,
                localized("セット全体を使うか、セットに入っているUI・デザイン・色・キャラクターから一つだけ選べます。",
                        "Apply a whole set, or use just its UI, design, color, or character.",
                        "세트 전체를 적용하거나 UI, 디자인, 색상, 캐릭터 중 하나만 선택할 수 있습니다."),
                13, Ui.MUTED));

        LinearLayout current = Ui.card(this);
        current.setOrientation(LinearLayout.VERTICAL);
        current.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        TextView currentLabel = Ui.overline(this, localized("現在の組み合わせ", "CURRENT COMBINATION", "현재 조합"));
        currentLabel.setTextColor(Ui.ACCENT_2);
        current.addView(currentLabel);
        current.addView(Ui.title(this, DesignSets.currentLabel(this), 17), Ui.gapTop(this, 3));
        body.addView(current, Ui.gapTop(this, 14));

        for (DesignSets.Preset preset : DesignSets.all()) addPreset(preset);
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));
    }

    private void addPreset(DesignSets.Preset preset) {
        boolean active = preset == DesignSets.current(this);
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        card.setBackground(Ui.roundStroke(Ui.SURFACE, active ? Ui.ACCENT_2 : Ui.BORDER, 18, this));

        LinearLayout heading = Ui.row(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        if (!"none".equals(preset.mascotId)) {
            MascotArtView mascot = new MascotArtView(this, preset.mascotId);
            heading.addView(mascot, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        } else {
            TextView mark = Ui.title(this, "✦", 30);
            mark.setTextColor(Ui.ACCENT_2);
            mark.setGravity(Gravity.CENTER);
            heading.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        }
        LinearLayout nameStack = new LinearLayout(this);
        nameStack.setOrientation(LinearLayout.VERTICAL);
        nameStack.setPadding(Ui.dp(this, 10), 0, 0, 0);
        nameStack.addView(Ui.title(this, preset.label(this), 17));
        nameStack.addView(Ui.text(this, preset.components(this), 10.5f, Ui.MUTED), Ui.gapTop(this, 3));
        heading.addView(nameStack, new LinearLayout.LayoutParams(0, -2, 1));
        if (active) heading.addView(Ui.accentPill(this, localized("適用中", "Active", "사용 중")));
        card.addView(heading);

        TextView description = Ui.text(this, preset.description(this), 12, Ui.MUTED);
        card.addView(description, Ui.gapTop(this, 7));
        addSwatches(card, preset);

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        Button apply = Ui.button(this, localized("セット全体を使う", "Apply full set", "세트 전체 적용"), true);
        apply.setTextSize(12);
        apply.setOnClickListener(v -> {
            if (DesignSets.apply(this, preset.id)) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
        actions.addView(apply, new LinearLayout.LayoutParams(0, -2, 1));
        Button parts = Ui.ghostButton(this, expandedId != null && expandedId.equals(preset.id)
                ? localized("パーツを閉じる", "Hide parts", "구성 닫기")
                : localized("パーツを選ぶ", "Choose parts", "구성 선택"));
        parts.setTextSize(12);
        LinearLayout.LayoutParams partsLp = new LinearLayout.LayoutParams(0, -2, 1);
        partsLp.setMargins(Ui.dp(this, 7), 0, 0, 0);
        actions.addView(parts, partsLp);
        parts.setOnClickListener(v -> {
            scrollY = scrollView.getScrollY();
            expandedId = preset.id.equals(expandedId) ? null : preset.id;
            build();
        });
        card.addView(actions);

        if (preset.id.equals(expandedId)) {
            LinearLayout detail = new LinearLayout(this);
            detail.setOrientation(LinearLayout.VERTICAL);
            detail.setPadding(0, Ui.dp(this, 9), 0, 0);
            addPartButton(detail, preset, DesignSets.Part.UI,
                    localized("このセットのUIだけを使う", "Use only this set's UI", "이 세트의 UI만 사용"));
            addPartButton(detail, preset, DesignSets.Part.DESIGN,
                    localized("このセットのデザインだけを使う", "Use only this set's design", "이 세트의 디자인만 사용"));
            addPartButton(detail, preset, DesignSets.Part.COLOR,
                    localized("このセットの色だけを使う", "Use only this set's colors", "이 세트의 색상만 사용"));
            addPartButton(detail, preset, DesignSets.Part.CHARACTER,
                    localized("このセットのキャラクターだけを使う", "Use only this set's character", "이 세트의 캐릭터만 사용"));
            card.addView(detail);
        }
        body.addView(card, Ui.gapTop(this, 12));
    }

    private void addSwatches(LinearLayout card, DesignSets.Preset preset) {
        AppThemes.Theme theme = AppThemes.get(this, preset.themeId);
        LinearLayout swatches = Ui.row(this);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors = {theme.bg, theme.accent, theme.accent2, theme.surface};
        for (int color : colors) {
            View swatch = new View(this);
            swatch.setBackground(Ui.round(color, 8, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            lp.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 6), 0);
            swatches.addView(swatch, lp);
        }
        TextView palette = Ui.text(this, theme.label(this), 11, Ui.MUTED);
        palette.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, 0);
        swatches.addView(palette);
        card.addView(swatches);
    }

    private void addPartButton(LinearLayout parent, DesignSets.Preset preset, DesignSets.Part part, String text) {
        Button button = Ui.ghostButton(this, text);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextSize(12);
        button.setOnClickListener(v -> {
            if (DesignSets.applyPart(this, preset.id, part)) {
                scrollY = scrollView.getScrollY();
                Ui.prepareActivity(this);
                build();
            }
        });
        parent.addView(button, Ui.gapTop(this, 5));
    }

    private String localized(String ja, String en, String ko) {
        String language = I18n.language(this);
        return "ko".equals(language) ? ko : "en".equals(language) ? en : ja;
    }
}
