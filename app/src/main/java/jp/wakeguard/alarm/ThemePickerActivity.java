package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Lets the user preview and choose a color palette and display mode. */
public class ThemePickerActivity extends Activity {
    private String selectedId;
    private String selectedMode;
    private String paletteKey;
    private LinearLayout themeGrid;
    private AppearanceChrome.ApplyBar applyBar;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        selectedId = state == null ? AppThemes.id(this) : state.getString("pendingTheme", AppThemes.id(this));
        selectedMode = normalizeMode(state == null ? AppThemes.mode(this)
                : state.getString("pendingMode", AppThemes.mode(this)));
        ensureSelectionInMode();
        build();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("pendingTheme", selectedId);
        out.putString("pendingMode", selectedMode);
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume() {
        super.onResume();
        String current = AppThemes.key(this);
        if (paletteKey != null && !paletteKey.equals(current)) {
            paletteKey = current;
            recreate();
        }
    }

    @Override public void onBackPressed() { Ui.finishNoAnimation(this); }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));
        root.addView(AppearanceChrome.header(this, AppearanceChrome.COLOR,
                local("好きな色と雰囲気を選べます", "Choose a color palette and mood",
                        "원하는 색상과 분위기를 선택하세요")));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        modes.addView(modeButton("system"), modeParams());
        modes.addView(modeButton("light"), modeParams());
        modes.addView(modeButton("dark"), modeParams());
        root.addView(modes);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        themeGrid = new LinearLayout(this);
        themeGrid.setOrientation(LinearLayout.VERTICAL);
        themeGrid.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 18));
        scroll.addView(themeGrid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        applyBar = new AppearanceChrome.ApplyBar(this,
                local("色・モードを適用", "Apply color & mode", "색상·모드 적용"), this::applySelection);
        root.addView(applyBar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 72)));
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        paletteKey = AppThemes.key(this);
        renderThemes();
    }

    private LinearLayout.LayoutParams modeParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        params.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        return params;
    }

    private Button modeButton(String mode) {
        Button button = Ui.button(this, modeLabel(mode), mode.equals(selectedMode));
        button.setTextSize(13);
        button.setMinHeight(Ui.dp(this, 48));
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
        themeGrid.removeAllViews();
        List<AppThemes.Theme> themes = AppThemes.all(this, selectedMode);
        if (themes == null) return;
        for (int start = 0; start < themes.size(); start += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = Ui.dp(this, 12);
            themeGrid.addView(row, rowParams);
            addThemeCard(row, themes.get(start), true);
            if (start + 1 < themes.size()) addThemeCard(row, themes.get(start + 1), false);
            else row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
        AppThemes.Theme selected = AppThemes.get(this, selectedId, selectedMode);
        if (selected != null) applyBar.setSelection(themeName(selected) + " · " + modeLabel(selectedMode));
    }

    private void addThemeCard(LinearLayout row, AppThemes.Theme listedTheme, boolean firstColumn) {
        if (listedTheme == null) return;
        AppThemes.Theme candidate = AppThemes.get(this, listedTheme.id, selectedMode);
        final AppThemes.Theme theme = candidate == null ? listedTheme : candidate;
        boolean selected = theme.id.equals(selectedId);
        boolean saved = theme.id.equals(AppThemes.id(this)) && selectedMode.equals(normalizeMode(AppThemes.mode(this)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surface);
        background.setCornerRadius(Ui.dp(this, 20));
        background.setStroke(Ui.dp(this, selected ? 2 : 1), selected ? theme.accent2 : theme.border);
        card.setBackground(background);
        card.setFocusable(true);
        card.setClickable(true);
        card.setSelected(selected);
        String savedLabel = local("使用中", "In use", "사용 중");
        String selectedLabel = local("選択中", "Selected", "선택됨");
        String status = saved && selected ? savedLabel + " · " + selectedLabel
                : saved ? savedLabel : selected ? selectedLabel : "";
        card.setContentDescription(themeName(theme) + (status.isEmpty() ? "" : ", " + status));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (firstColumn) cardParams.rightMargin = Ui.dp(this, 6);
        else cardParams.leftMargin = Ui.dp(this, 6);
        row.addView(card, cardParams);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        addSwatch(swatches, theme.bg);
        addSwatch(swatches, theme.surface2);
        addSwatch(swatches, theme.accent);
        addSwatch(swatches, theme.accent2);
        card.addView(swatches, new LinearLayout.LayoutParams(-1, Ui.dp(this, 25)));

        TextView name = new TextView(this);
        name.setText(themeName(theme));
        name.setTextSize(17);
        name.setTextColor(theme.text);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, Ui.dp(this, 9), 0, 0);
        card.addView(name);

        TextView state = new TextView(this);
        state.setText(status.isEmpty() ? local("タップして選択", "Tap to select", "탭하여 선택") : status);
        state.setTextSize(11);
        state.setTextColor(selected ? theme.accent2 : theme.muted);
        state.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(state);
        card.setOnClickListener(v -> {
            selectedId = theme.id;
            renderThemes();
        });
    }

    private void addSwatch(LinearLayout parent, int color) {
        View swatch = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(Ui.dp(this, 7));
        swatch.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        parent.addView(swatch, params);
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
        setResult(RESULT_OK);
        Ui.finishNoAnimation(this);
    }

    private String modeLabel(String mode) {
        if ("light".equals(mode)) return local("ライト", "Light", "라이트");
        if ("dark".equals(mode)) return local("ダーク", "Dark", "다크");
        return local("システム", "System", "시스템");
    }

    private String themeName(AppThemes.Theme theme) {
        return local(theme.nameJa, theme.nameEn, theme.nameKo);
    }

    private String normalizeMode(String mode) {
        if ("light".equals(mode) || "dark".equals(mode)) return mode;
        return "system";
    }

    private String local(String ja, String en, String ko) {
        String language = I18n.language(this);
        return "en".equals(language) ? en : "ko".equals(language) ? ko : ja;
    }
}
