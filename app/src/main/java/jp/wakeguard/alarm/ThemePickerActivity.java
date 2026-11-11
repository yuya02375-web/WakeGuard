package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Lets the user preview and choose one of the built-in app color themes. */
public class ThemePickerActivity extends Activity {
    private String selectedId;
    private String selectedMode;
    private LinearLayout themeGrid;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        selectedId = AppThemes.id(this);
        selectedMode = normalizeMode(AppThemes.mode(this));
        build();
    }

    @Override public void onBackPressed() {
        finish();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));

        LinearLayout titleRow = Ui.row(this);
        titleRow.setPadding(0, 0, 0, Ui.dp(this, 3));
        Button back = Ui.ghostButton(this, "‹");
        back.setTextSize(30);
        back.setContentDescription(localize("戻る", "Back", "뒤로"));
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 50)));
        TextView title = Ui.title(this, localize("デザイン", "Design", "디자인"), 27);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        header.addView(titleRow);
        header.addView(Ui.text(this,
                localize("好きな色と雰囲気を選べます", "Choose a color palette and mood", "원하는 색상과 분위기를 선택하세요"),
                13, Ui.MUTED), Ui.gapTop(this, 2));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 3));
        modes.addView(modeButton("system"), modeParams());
        modes.addView(modeButton("light"), modeParams());
        modes.addView(modeButton("dark"), modeParams());
        header.addView(modes);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 18));
        themeGrid = new LinearLayout(this);
        themeGrid.setOrientation(LinearLayout.VERTICAL);
        content.addView(themeGrid);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 12));
        footer.setBackgroundColor(Ui.SURFACE);
        Button apply = Ui.button(this,
                localize("このデザインを適用", "Apply design", "디자인 적용"), true);
        apply.setOnClickListener(v -> applySelection());
        footer.addView(apply, new LinearLayout.LayoutParams(-1, -2));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        renderThemes();
    }

    private LinearLayout.LayoutParams modeParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1);
        params.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        return params;
    }

    private Button modeButton(String mode) {
        Button button = Ui.button(this, modeLabel(mode), mode.equals(selectedMode));
        button.setTextSize(13);
        button.setMinHeight(Ui.dp(this, 44));
        button.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        button.setOnClickListener(v -> {
            if (mode.equals(selectedMode)) return;
            selectedMode = mode;
            ensureSelectionInMode();
            build();
        });
        return button;
    }

    private void renderThemes() {
        if (themeGrid == null) return;
        themeGrid.removeAllViews();
        List<AppThemes.Theme> themes = AppThemes.all(this, selectedMode);
        if (themes == null) return;

        for (int start = 0; start < themes.size(); start += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = Ui.dp(this, 10);
            themeGrid.addView(row, rowParams);

            addThemeCard(row, themes.get(start), true);
            if (start + 1 < themes.size()) addThemeCard(row, themes.get(start + 1), false);
            else row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    private void addThemeCard(LinearLayout row, AppThemes.Theme listedTheme, boolean firstColumn) {
        if (listedTheme == null) return;
        AppThemes.Theme theme = AppThemes.get(this, listedTheme.id, selectedMode);
        if (theme == null) theme = listedTheme;
        boolean selected = theme.id.equals(selectedId);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surface);
        background.setCornerRadius(Ui.dp(this, 16));
        background.setStroke(Ui.dp(this, selected ? 2 : 1), selected ? theme.accent : theme.border);
        card.setBackground(background);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(themeName(theme) + (selected
                ? ", " + localize("選択中", "Selected", "선택됨") : ""));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (firstColumn) cardParams.rightMargin = Ui.dp(this, 5);
        else cardParams.leftMargin = Ui.dp(this, 5);
        row.addView(card, cardParams);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        addSwatch(swatches, theme.bg);
        addSwatch(swatches, theme.surface);
        addSwatch(swatches, theme.accent);
        card.addView(swatches, new LinearLayout.LayoutParams(-1, Ui.dp(this, 25)));

        TextView name = new TextView(this);
        name.setText(themeName(theme));
        name.setTextSize(14);
        name.setTextColor(theme.text);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, Ui.dp(this, 9), 0, 0);
        card.addView(name);

        TextView state = new TextView(this);
        state.setText(selected
                ? localize("✓ 選択中", "✓ Selected", "✓ 선택됨")
                : localize("タップして選択", "Tap to select", "탭하여 선택"));
        state.setTextSize(11);
        state.setTextColor(selected ? theme.accent : theme.muted);
        state.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(state);

        final String themeId = theme.id;
        card.setOnClickListener(v -> {
            selectedId = themeId;
            renderThemes();
        });
    }

    private void addSwatch(LinearLayout parent, int color) {
        View swatch = new View(this);
        swatch.setBackground(round(color, 7));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        parent.addView(swatch, params);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(Ui.dp(this, radiusDp));
        return drawable;
    }

    private void ensureSelectionInMode() {
        List<AppThemes.Theme> themes = AppThemes.all(this, selectedMode);
        if (themes == null || themes.isEmpty()) return;
        for (AppThemes.Theme theme : themes) {
            if (theme != null && theme.id.equals(selectedId)) return;
        }
        selectedId = themes.get(0).id;
    }

    private void applySelection() {
        if (selectedId == null || selectedId.isEmpty()) return;
        AppThemes.select(this, selectedId);
        AppThemes.setMode(this, selectedMode);
        AppThemes.apply(this);
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(home);
        finish();
    }

    private String modeLabel(String mode) {
        if ("light".equals(mode)) return localize("ライト", "Light", "라이트");
        if ("dark".equals(mode)) return localize("ダーク", "Dark", "다크");
        return localize("システム", "System", "시스템");
    }

    private String themeName(AppThemes.Theme theme) {
        String language = I18n.language(this);
        if ("en".equals(language)) return theme.nameEn;
        if ("ko".equals(language)) return theme.nameKo;
        return theme.nameJa;
    }

    private String normalizeMode(String mode) {
        if ("light".equals(mode) || "dark".equals(mode)) return mode;
        return "system";
    }

    private String localize(String japanese, String english, String korean) {
        String language = I18n.language(this);
        if ("en".equals(language)) return english;
        if ("ko".equals(language)) return korean;
        return japanese;
    }
}
