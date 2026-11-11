package jp.wakeguard.alarm;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Lets the user choose an app layout and component style independently of its palette. */
public class UiProfilePickerActivity extends Activity {
    private boolean gridMode = true;
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
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
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 10));

        LinearLayout titleRow = Ui.row(this);
        titleRow.setPadding(0, 0, 0, Ui.dp(this, 3));
        Button back = Ui.ghostButton(this, "‹");
        back.setTextSize(30);
        back.setContentDescription(localized("戻る", "Back", "뒤로"));
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 50)));
        titleRow.addView(label(localized("UIスタイル", "UI style", "UI 스타일"), 25, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));
        header.addView(titleRow);
        header.addView(label(localized("画面の並び方、密度、ナビゲーション、カード形状を選びます。",
                "Choose layout, spacing, navigation, and card treatment.",
                "레이아웃, 간격, 탐색, 카드 표현을 선택하세요."), 13, Ui.MUTED, false));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, Ui.dp(this, 10), 0, 0);
        Button grid = Ui.button(this, localized("グリッド", "Grid", "그리드"), gridMode);
        Button list = Ui.button(this, localized("リスト", "List", "목록"), !gridMode);
        grid.setTextSize(13); list.setTextSize(13);
        grid.setMinHeight(Ui.dp(this, 42)); list.setMinHeight(Ui.dp(this, 42));
        controls.addView(grid, controlParams());
        controls.addView(list, controlParams());
        grid.setOnClickListener(v -> { if (!gridMode) { gridMode = true; build(); } });
        list.setOnClickListener(v -> { if (gridMode) { gridMode = false; build(); } });
        header.addView(controls);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 18), Ui.dp(this, 20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        renderProfiles();
    }

    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
        params.setMargins(0, 0, Ui.dp(this, 7), 0);
        return params;
    }

    private void renderProfiles() {
        if (content == null) return;
        content.removeAllViews();
        List<UiProfiles.Design> profiles = UiProfiles.all();
        String lastCategory = null;
        LinearLayout row = null;
        int inRow = 0;
        for (UiProfiles.Design profile : profiles) {
            String category = profile.category(this);
            if (!category.equals(lastCategory)) {
                if (row != null && inRow == 1 && gridMode) addSpacer(row);
                row = null;
                inRow = 0;
                TextView heading = label(category, 13, Ui.ACCENT_2, true);
                heading.setPadding(Ui.dp(this, 2), Ui.dp(this, 13), 0, Ui.dp(this, 8));
                content.addView(heading);
                lastCategory = category;
            }
            if (!gridMode) {
                LinearLayout.LayoutParams params = Ui.cardParams(this);
                content.addView(profileCard(profile), params);
                continue;
            }
            if (row == null || inRow == 2) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = Ui.dp(this, 9);
                content.addView(row, rowParams);
                inRow = 0;
            }
            LinearLayout card = profileCard(profile);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (inRow == 0) params.rightMargin = Ui.dp(this, 5);
            else params.leftMargin = Ui.dp(this, 5);
            row.addView(card, params);
            inRow++;
        }
        if (gridMode && row != null && inRow == 1) addSpacer(row);
    }

    private void addSpacer(LinearLayout row) {
        row.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 1), 1));
    }

    private LinearLayout profileCard(UiProfiles.Design profile) {
        boolean selected = profile.id.equals(UiProfiles.id(this));
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, gridMode ? 11 : 14), Ui.dp(this, 12), Ui.dp(this, gridMode ? 11 : 14), Ui.dp(this, 12));
        card.setBackground(cardBackground(profile, selected));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(profile.label(this) + (selected ? ", " + localized("選択中", "Selected", "선택됨") : ""));

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(profile.label(this), 16, Ui.TEXT, true);
        titleLine.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (selected) titleLine.addView(Ui.accentPill(this, localized("使用中", "Active", "사용 중")),
                new LinearLayout.LayoutParams(-2, -2));
        card.addView(titleLine);

        TextView description = label(profile.description(this), 11.5f, Ui.MUTED, false);
        description.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 7));
        card.addView(description);
        card.addView(preview(profile));

        TextView detail = label(layoutLabel(profile.layoutMode) + "  ·  " + densityLabel(profile.densityScale)
                + "  ·  " + navLabel(profile.navMode) + "  ·  " + cardLabel(profile.cardMode), 10.5f, Ui.MUTED, false);
        detail.setPadding(0, Ui.dp(this, 7), 0, 0);
        detail.setSingleLine(true);
        card.addView(detail);
        card.setOnClickListener(v -> {
            UiProfiles.select(this, profile.id);
            setResult(RESULT_OK);
            finish();
        });
        return card;
    }

    private View preview(UiProfiles.Design profile) {
        LinearLayout demo = new LinearLayout(this);
        demo.setOrientation(LinearLayout.VERTICAL);
        demo.setPadding(Ui.dp(this, 7), Ui.dp(this, 7), Ui.dp(this, 7), Ui.dp(this, 7));
        demo.setBackground(Ui.round(Ui.BG_2, profile.cornerScale * 10f, this));

        if ("timeline".equals(profile.layoutMode)) {
            addTimelineSample(demo, profile);
        } else if ("tiles".equals(profile.layoutMode)) {
            addTileSample(demo, profile);
        } else {
            addListSample(demo, profile);
        }
        demo.addView(navPreview(profile), new LinearLayout.LayoutParams(-1, Ui.dp(this, 24)));
        return demo;
    }

    private void addListSample(LinearLayout demo, UiProfiles.Design profile) {
        for (int i = 0; i < 2; i++) {
            LinearLayout item = sampleCard(profile, i == 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, Math.round(25 * profile.densityScale)));
            if (i > 0) params.topMargin = Ui.dp(this, 4);
            demo.addView(item, params);
            addSampleLines(item, i == 0);
        }
    }

    private void addTimelineSample(LinearLayout demo, UiProfiles.Design profile) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        View rail = new View(this);
        rail.setBackgroundColor(Ui.ACCENT_2);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(Ui.dp(this, 2), -1);
        railParams.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 7), 0);
        line.addView(rail, railParams);
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        line.addView(items, new LinearLayout.LayoutParams(0, -1, 1));
        for (int i = 0; i < 2; i++) {
            LinearLayout item = sampleCard(profile, i == 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1);
            if (i > 0) params.topMargin = Ui.dp(this, 3);
            items.addView(item, params);
            addSampleLines(item, i == 0);
        }
        demo.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
    }

    private void addTileSample(LinearLayout demo, UiProfiles.Design profile) {
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1);
            if (i > 0) params.leftMargin = Ui.dp(this, 4);
            tiles.addView(tile, params);
            LinearLayout face = sampleCard(profile, i == 0);
            tile.addView(face, new LinearLayout.LayoutParams(-1, Ui.dp(this, Math.round(35 * profile.densityScale))));
            addSampleLines(face, i == 0);
            View dot = new View(this);
            dot.setBackground(Ui.round(i == 0 ? Ui.ACCENT_2 : Ui.BORDER, profile.cornerScale * 5f, this));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 5));
            dotParams.gravity = Gravity.CENTER_HORIZONTAL;
            dotParams.topMargin = Ui.dp(this, 8);
            tile.addView(dot, dotParams);
        }
        demo.addView(tiles, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
    }

    private LinearLayout sampleCard(UiProfiles.Design profile, boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        item.setBackground(cardBackground(profile, active));
        return item;
    }

    private void addSampleLines(LinearLayout item, boolean active) {
        View marker = new View(this);
        marker.setBackground(Ui.round(active ? Ui.ACCENT_2 : Ui.BORDER, 5, this));
        item.addView(marker, new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6)));
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(0, -2, 1);
        linesParams.leftMargin = Ui.dp(this, 6);
        item.addView(lines, linesParams);
        View main = new View(this);
        main.setBackgroundColor(active ? Ui.TEXT : Ui.MUTED);
        lines.addView(main, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 3)));
        View sub = new View(this);
        sub.setBackgroundColor(Ui.BORDER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 2));
        subParams.topMargin = Ui.dp(this, 3);
        lines.addView(sub, subParams);
    }

    private View navPreview(UiProfiles.Design profile) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        for (int i = 0; i < 3; i++) {
            TextView item = label("labels".equals(profile.navMode) ? new String[]{"HOME", "TIME", "LOG"}[i]
                    : new String[]{"•", "◷", "▤"}[i], "labels".equals(profile.navMode) ? 7.5f : 11f,
                    i == 0 ? Ui.ACCENT_2 : Ui.MUTED, i == 0);
            item.setGravity(Gravity.CENTER);
            if ("pill".equals(profile.navMode) && i == 0) item.setBackground(Ui.round(Ui.NAV_HIGHLIGHT, profile.cornerScale * 7f, this));
            if ("compact".equals(profile.navMode) && i != 0) item.setAlpha(.65f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            nav.addView(item, params);
        }
        return nav;
    }

    private GradientDrawable cardBackground(UiProfiles.Design profile, boolean selected) {
        int radius = Math.round(14f * profile.cornerScale);
        int stroke = selected ? Ui.ACCENT_2 : Ui.BORDER;
        if ("flat".equals(profile.cardMode)) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(Ui.SURFACE_2);
            background.setCornerRadius(Ui.dp(this, radius));
            if (selected) background.setStroke(Ui.dp(this, 2), stroke);
            return background;
        }
        if ("outlined".equals(profile.cardMode)) return Ui.roundStroke(Ui.SURFACE, stroke, radius, this);
        GradientDrawable background = Ui.gradientRound(this, radius, Ui.SURFACE, Ui.SURFACE_2);
        if (selected) background.setStroke(Ui.dp(this, 2), stroke);
        return background;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private String layoutLabel(String mode) {
        if ("timeline".equals(mode)) return localized("時系列", "Timeline", "타임라인");
        if ("tiles".equals(mode)) return localized("タイル", "Tiles", "타일");
        return localized("一覧", "List", "목록");
    }

    private String densityLabel(float scale) {
        if (scale < .92f) return localized("高密度", "Dense", "고밀도");
        if (scale > 1.10f) return localized("ゆったり", "Spacious", "넉넉함");
        return localized("標準密度", "Balanced", "균형");
    }

    private String navLabel(String mode) {
        if ("compact".equals(mode)) return localized("コンパクトナビ", "Compact nav", "간결 탐색");
        if ("labels".equals(mode)) return localized("ラベル付き", "Labeled nav", "라벨 탐색");
        return localized("ピルナビ", "Pill nav", "알약 탐색");
    }

    private String cardLabel(String mode) {
        if ("flat".equals(mode)) return localized("フラット", "Flat", "플랫");
        if ("outlined".equals(mode)) return localized("アウトライン", "Outlined", "윤곽선");
        return localized("浮き上がり", "Raised", "떠오른 카드");
    }

    private String localized(String japanese, String english, String korean) {
        String language = I18n.language(this);
        return "en".equals(language) ? english : "ko".equals(language) ? korean : japanese;
    }
}
