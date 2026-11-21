package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Lets the user preview and choose a color palette and display mode. */
public class ThemePickerActivity extends Activity {
    private String selectedId;
    private String selectedMode;
    private String appliedThemeId;
    private String appliedMode;
    private String paletteKey;
    private LinearLayout themeGrid;
    private ScrollView themeScroll;
    private AppearanceChrome.ApplyBar applyBar;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        appliedThemeId = AppThemes.id(this);
        appliedMode = AppThemes.mode(this);
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
        String currentId = AppThemes.id(this);
        String currentMode = AppThemes.mode(this);
        boolean selectionChanged = appliedThemeId != null && (!appliedThemeId.equals(currentId)
                || !appliedMode.equals(currentMode));
        boolean paletteChanged = paletteKey != null && !paletteKey.equals(current);
        if (selectionChanged || paletteChanged) {
            int scrollY = themeScroll == null ? 0 : themeScroll.getScrollY();
            if (selectionChanged) {
                selectedId = currentId;
                selectedMode = normalizeMode(currentMode);
                appliedThemeId = currentId;
                appliedMode = currentMode;
                ensureSelectionInMode();
            }
            paletteKey = current;
            build();
            if (themeScroll != null) themeScroll.post(() -> themeScroll.scrollTo(0, scrollY));
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
        themeScroll = scroll;
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
        TextView count = Ui.text(this, local(themes.size() + "種類のカラーパレット",
                themes.size() + " color palettes", themes.size() + "개 색상 팔레트"), 11.5f, Ui.MUTED);
        count.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 9));
        themeGrid.addView(count);
        for (AppThemes.Theme theme : themes) addThemeCard(theme);
        AppThemes.Theme selected = AppThemes.get(this, selectedId, selectedMode);
        if (selected != null) applyBar.setSelection(themeName(selected) + " · " + modeLabel(selectedMode));
    }

    private void addThemeCard(AppThemes.Theme listedTheme) {
        if (listedTheme == null) return;
        AppThemes.Theme candidate = AppThemes.get(this, listedTheme.id, selectedMode);
        final AppThemes.Theme theme = candidate == null ? listedTheme : candidate;
        boolean selected = theme.id.equals(selectedId);
        boolean saved = theme.id.equals(AppThemes.id(this)) && selectedMode.equals(normalizeMode(AppThemes.mode(this)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surface);
        background.setCornerRadius(Ui.dp(this, 20));
        background.setStroke(Ui.dp(this, selected ? 2 : 1), selected ? theme.accent2 : theme.border);
        card.setBackground(background);
        card.setForeground(Ui.rippleForeground(this, 20));
        card.setFocusable(true);
        card.setClickable(true);
        card.setSelected(selected);
        String savedLabel = local("使用中", "In use", "사용 중");
        String selectedLabel = local("選択中", "Selected", "선택됨");
        String status = saved && selected ? savedLabel + " · " + selectedLabel
                : saved ? savedLabel : selected ? selectedLabel : "";
        card.setContentDescription(themeName(theme) + (status.isEmpty() ? "" : ", " + status));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = Ui.dp(this, 12);
        themeGrid.addView(card, cardParams);

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(themeName(theme));
        name.setTextSize(16);
        name.setTextColor(theme.text);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = new TextView(this);
        state.setText(status.isEmpty() ? local("選択", "Select", "선택") : status);
        state.setTextSize(11);
        state.setTextColor(selected ? theme.onAccent : theme.accent2);
        state.setTypeface(null, Typeface.BOLD);
        state.setGravity(Gravity.CENTER);
        state.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        state.setBackground(Ui.round(selected ? theme.accent2 : theme.bg, 14, this));
        nameRow.addView(state, new LinearLayout.LayoutParams(-2, -2));
        card.addView(nameRow);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 114));
        previewParams.topMargin = Ui.dp(this, 10);
        card.addView(themePreview(theme), previewParams);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        addSwatch(swatches, theme.bg);
        addSwatch(swatches, theme.surface2);
        addSwatch(swatches, theme.accent);
        addSwatch(swatches, theme.accent2);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 10));
        swatchParams.topMargin = Ui.dp(this, 9);
        card.addView(swatches, swatchParams);
        card.setOnClickListener(v -> {
            int scrollY = themeScroll == null ? 0 : themeScroll.getScrollY();
            selectedId = theme.id;
            renderThemes();
            if (themeScroll != null) themeScroll.post(() -> themeScroll.scrollTo(0, scrollY));
        });
    }

    private View themePreview(AppThemes.Theme theme) {
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(Ui.dp(this, 9), Ui.dp(this, 7), Ui.dp(this, 9), Ui.dp(this, 7));
        GradientDrawable page = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.bg, theme.surface});
        page.setCornerRadius(Ui.dp(this, 14));
        preview.setBackground(page);

        LinearLayout masthead = new LinearLayout(this);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("WAKE  /  ALARM");
        brand.setTextSize(8.5f);
        brand.setTextColor(theme.muted);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(.08f);
        masthead.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView day = new TextView(this);
        day.setText(local("毎日", "DAILY", "매일"));
        day.setTextSize(8.5f);
        day.setTextColor(theme.accent2);
        masthead.addView(day);
        preview.addView(masthead);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        GradientDrawable heroFill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{theme.heroStart, theme.heroEnd});
        heroFill.setCornerRadius(Ui.dp(this, 12));
        hero.setBackground(heroFill);
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView next = new TextView(this);
        next.setText(local("次の起床", "NEXT ALARM", "다음 알람"));
        next.setTextSize(8.5f);
        next.setTextColor(theme.heroText);
        next.setAlpha(.82f);
        heroText.addView(next);
        TextView time = new TextView(this);
        time.setText("06:30");
        time.setTextSize(27);
        time.setTextColor(theme.heroText);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-.025f);
        heroText.addView(time);
        TextView meta = new TextView(this);
        meta.setText(local("平日  ·  あと 8時間", "WEEKDAYS  ·  IN 8 HRS", "평일  ·  8시간 후"));
        meta.setTextSize(8.5f);
        meta.setTextColor(theme.heroText);
        meta.setAlpha(.88f);
        heroText.addView(meta);
        hero.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        View marker = new View(this);
        GradientDrawable markerFill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{theme.accent, theme.accent2});
        markerFill.setShape(GradientDrawable.OVAL);
        marker.setBackground(markerFill);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
        markerParams.leftMargin = Ui.dp(this, 6);
        hero.addView(marker, markerParams);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, 0, 1);
        heroParams.topMargin = Ui.dp(this, 5);
        preview.addView(hero, heroParams);

        LinearLayout alarmRow = new LinearLayout(this);
        alarmRow.setGravity(Gravity.CENTER_VERTICAL);
        alarmRow.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        GradientDrawable rowFill = new GradientDrawable();
        rowFill.setColor(theme.surface);
        rowFill.setCornerRadius(Ui.dp(this, 9));
        rowFill.setStroke(Ui.dp(this, 1), theme.border);
        alarmRow.setBackground(rowFill);
        TextView alarmTime = new TextView(this);
        alarmTime.setText("06:30");
        alarmTime.setTextSize(13);
        alarmTime.setTextColor(theme.accent2);
        alarmTime.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        alarmRow.addView(alarmTime);
        TextView alarmLabel = new TextView(this);
        alarmLabel.setText(local("平日の起床", "Weekday wake-up", "평일 기상"));
        alarmLabel.setTextSize(9.5f);
        alarmLabel.setTextColor(theme.text);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = Ui.dp(this, 9);
        alarmRow.addView(alarmLabel, labelParams);
        FrameLayout toggle = new FrameLayout(this);
        GradientDrawable toggleFill = new GradientDrawable();
        toggleFill.setColor(theme.accent2);
        toggleFill.setCornerRadius(Ui.dp(this, 8));
        toggle.setBackground(toggleFill);
        View thumb = new View(this);
        GradientDrawable thumbFill = new GradientDrawable();
        thumbFill.setColor(theme.onAccent);
        thumbFill.setShape(GradientDrawable.OVAL);
        thumb.setBackground(thumbFill);
        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10), Gravity.END | Gravity.CENTER_VERTICAL);
        thumbParams.rightMargin = Ui.dp(this, 2);
        toggle.addView(thumb, thumbParams);
        alarmRow.addView(toggle, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 14)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 25));
        rowParams.topMargin = Ui.dp(this, 5);
        preview.addView(alarmRow, rowParams);
        return preview;
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
