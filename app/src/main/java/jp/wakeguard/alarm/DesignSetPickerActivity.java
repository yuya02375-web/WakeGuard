package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Layout;
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
    private String displayedCombination;
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
        root.addView(AppearanceChrome.header(this, AppearanceChrome.SETS,
                localized("セット全体を使うか、UI・デザイン・色から一つだけ選べます。",
                        "Apply a whole set, or choose just its UI, design, or color.",
                        "세트 전체 또는 UI, 디자인, 색상 중 하나만 선택할 수 있습니다.")));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 28));
        scrollView.addView(body);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

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
        displayedCombination = combinationKey();
    }

    @Override protected void onResume() {
        super.onResume();
        String current = combinationKey();
        if (displayedCombination == null || displayedCombination.equals(current)) return;
        scrollY = scrollView == null ? scrollY : scrollView.getScrollY();
        Ui.prepareActivity(this);
        build();
    }

    private String combinationKey() {
        return UiProfiles.id(this) + ":" + VisualDesigns.id(this) + ":"
                + AppThemes.id(this) + ":" + AppThemes.mode(this);
    }

    private void addPreset(DesignSets.Preset preset) {
        boolean active = preset == DesignSets.current(this);
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackground(Ui.roundStroke(Ui.SURFACE, active ? Ui.ACCENT_2 : Ui.BORDER, 18, this));
        AppThemes.Theme setTheme = AppThemes.get(this, preset.themeId);

        LinearLayout heading = Ui.row(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.title(this, preset.label(this), 17);
        name.setAccessibilityHeading(true);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (active) heading.addView(Ui.accentPill(this, localized("適用中", "Active", "사용 중")));
        card.addView(heading);

        TextView components = Ui.text(this, preset.components(this), 11.5f, Ui.MUTED);
        components.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        card.addView(components, Ui.gapTop(this, 4));

        TextView description = Ui.text(this, preset.description(this), 13, Ui.MUTED);
        card.addView(description, Ui.gapTop(this, 7));
        VisualDesigns.Design visual = VisualDesigns.get(preset.visualDesignId);
        if (visual != null) {
            TextView sampleLabel = Ui.text(this, localized("模様見本", "Pattern sample", "무늬 견본"), 11, Ui.MUTED);
            card.addView(sampleLabel, Ui.gapTop(this, 10));
            View preview = new View(this);
            preview.setBackground(Ui.artworkPreview(this, visual, setTheme));
            preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 106));
            previewParams.topMargin = Ui.dp(this, 4);
            card.addView(preview, previewParams);
        }
        addSwatches(card, preset);

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        Button apply = Ui.button(this, localized("セット全体を使う", "Apply full set", "세트 전체 적용"), true);
        apply.setTextSize(13);
        apply.setMinHeight(Ui.dp(this, 48));
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
        parts.setTextSize(13);
        parts.setMinHeight(Ui.dp(this, 48));
        LinearLayout.LayoutParams partsLp = new LinearLayout.LayoutParams(0, -2, 1);
        partsLp.setMargins(Ui.dp(this, 7), 0, 0, 0);
        actions.addView(parts, partsLp);
        parts.setOnClickListener(v -> {
            scrollY = scrollView.getScrollY();
            expandedId = preset.id.equals(expandedId) ? null : preset.id;
            build();
        });
        card.addView(actions);

        if (active) {
            Button openLive = Ui.ghostButton(this, localized("実際の画面を見る", "Open live screen", "실제 화면 보기"));
            openLive.setContentDescription(localized("実際の画面を見る。適用中のデザインでアラーム画面を開きます",
                    "Open live screen. View the alarm screen with the applied design.",
                    "실제 화면 보기. 적용 중인 디자인으로 알람 화면을 엽니다."));
            openLive.setOnClickListener(v -> Ui.launchNoAnimation(this, new Intent(this, MainActivity.class)));
            card.addView(openLive, Ui.gapTop(this, 8));
        }

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
            card.addView(detail);
        }
        body.addView(card, Ui.gapTop(this, 14));
    }

    private void addSwatches(LinearLayout card, DesignSets.Preset preset) {
        AppThemes.Theme theme = AppThemes.get(this, preset.themeId);
        LinearLayout swatches = Ui.row(this);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors = {theme.bg, theme.accent, theme.accent2, theme.surface};
        for (int color : colors) {
            View swatch = new View(this);
            swatch.setBackground(Ui.round(color, 8, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
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
