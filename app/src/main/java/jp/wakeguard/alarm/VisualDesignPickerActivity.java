package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Selects the independent illustration, typography, and surface treatment axis. */
public class VisualDesignPickerActivity extends Activity {
    private static final String[] CATEGORIES = {"all", "paper", "nature", "retro", "tech", "editorial", "playful"};
    private static final String STATE_SELECTED_ID = "visual_design_picker.selected_id";
    private static final String STATE_SELECTED_CATEGORY = "visual_design_picker.category";
    private static final String STATE_SCROLL_Y = "visual_design_picker.scroll_y";
    private static final String STATE_CATEGORY_SCROLL_X = "visual_design_picker.category_scroll_x";
    private String selectedId;
    private String selectedCategory = "all";
    private String paletteKey;
    private LinearLayout cards;
    private HorizontalScrollView categoryScroll;
    private ScrollView designScroll;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        selectedId = state == null ? VisualDesigns.id(this)
                : VisualDesigns.get(state.getString(STATE_SELECTED_ID, VisualDesigns.id(this))).id;
        if (state != null) {
            String category = state.getString(STATE_SELECTED_CATEGORY, "all");
            for (String candidate : CATEGORIES) if (candidate.equals(category)) selectedCategory = candidate;
        }
        build();
        if (state != null) restoreScroll(state.getInt(STATE_SCROLL_Y, 0),
                state.getInt(STATE_CATEGORY_SCROLL_X, 0));
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SELECTED_ID, selectedId);
        outState.putString(STATE_SELECTED_CATEGORY, selectedCategory);
        outState.putInt(STATE_SCROLL_Y, designScroll == null ? 0 : designScroll.getScrollY());
        outState.putInt(STATE_CATEGORY_SCROLL_X, categoryScroll == null ? 0 : categoryScroll.getScrollX());
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onResume() {
        super.onResume();
        String current = AppThemes.key(this);
        if (paletteKey != null && !paletteKey.equals(current)) {
            int scrollY = designScroll == null ? 0 : designScroll.getScrollY();
            int categoryX = categoryScroll == null ? 0 : categoryScroll.getScrollX();
            paletteKey = current;
            // Rebuild previews with the new palette, keeping the unapplied design choice.
            build();
            restoreScroll(scrollY, categoryX);
        }
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(5));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹"); back.setTextSize(34); back.setTextColor(Ui.TEXT);
        back.setGravity(Gravity.CENTER); back.setContentDescription(local("戻る", "Back", "뒤로"));
        back.setBackground(ripple(Ui.SURFACE_2, 24));
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));

        TextView title = new TextView(this);
        title.setText(local("デザイン", "Design", "디자인"));
        title.setTextSize(25); title.setTextColor(Ui.TEXT); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(10), 0, dp(5), 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView colors = new TextView(this);
        colors.setText(local("色・モード", "Colors", "색상"));
        colors.setTextSize(12); colors.setTextColor(Ui.ACCENT_2); colors.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        colors.setGravity(Gravity.CENTER); colors.setPadding(dp(10), dp(8), dp(10), dp(8));
        colors.setBackground(Ui.roundStroke(Ui.SURFACE_2, Ui.BORDER, 14, this));
        colors.setContentDescription(local("色パレットとライト・ダーク表示を選ぶ", "Open color palettes and light/dark mode", "색상 팔레트와 라이트·다크 모드 열기"));
        colors.setOnClickListener(v -> Ui.launchNoAnimation(this, new Intent(this, ThemePickerActivity.class)));
        titleRow.addView(colors, new LinearLayout.LayoutParams(-2, -2));
        header.addView(titleRow);

        TextView subtitle = new TextView(this);
        subtitle.setText(local("模様・文字・カードの形を選択。色は右上から別に選べます。",
                "Choose patterns, typography, and card shapes. Pick colors separately above.",
                "무늬·글꼴·카드 모양을 선택하세요. 색상은 위에서 따로 고를 수 있습니다."));
        subtitle.setTextSize(12.5f); subtitle.setTextColor(Ui.MUTED); subtitle.setLineSpacing(dp(2), 1.0f);
        subtitle.setPadding(dp(3), dp(5), dp(3), dp(7));
        header.addView(subtitle);

        categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String category : CATEGORIES) chips.addView(categoryChip(category));
        categoryScroll.addView(chips);
        header.addView(categoryScroll, new LinearLayout.LayoutParams(-1, dp(45)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        designScroll = scroll;
        scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(5), dp(14), dp(16));
        cards = new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL);
        content.addView(cards);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(16), dp(9), dp(16), dp(11));
        footer.setBackgroundColor(Ui.SURFACE);
        TextView apply = new TextView(this);
        apply.setText(local("このデザインを適用", "Apply design", "디자인 적용"));
        apply.setTextSize(15); apply.setTextColor(Ui.ON_ACCENT); apply.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        apply.setGravity(Gravity.CENTER); apply.setMinHeight(dp(50));
        apply.setBackground(ripple(Ui.ACCENT_2, 16));
        apply.setOnClickListener(v -> applySelection());
        footer.addView(apply, new LinearLayout.LayoutParams(-1, -2));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        renderDesigns();
        paletteKey = AppThemes.key(this);
    }

    private void restoreScroll(int designY, int categoryX) {
        if (designScroll != null) designScroll.post(() -> designScroll.scrollTo(0, Math.max(0, designY)));
        if (categoryScroll != null) categoryScroll.post(() -> categoryScroll.scrollTo(Math.max(0, categoryX), 0));
    }

    private TextView categoryChip(String category) {
        TextView chip = new TextView(this);
        chip.setText(categoryLabel(category)); chip.setTextSize(12.5f); chip.setGravity(Gravity.CENTER);
        chip.setTextColor(category.equals(selectedCategory) ? Ui.ON_ACCENT : Ui.TEXT);
        chip.setTypeface(Typeface.DEFAULT, category.equals(selectedCategory) ? Typeface.BOLD : Typeface.NORMAL);
        chip.setPadding(dp(13), dp(6), dp(13), dp(6));
        chip.setBackground(ripple(category.equals(selectedCategory) ? Ui.ACCENT_2 : Ui.SURFACE_2, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.setMargins(dp(2), dp(3), dp(6), dp(3)); chip.setLayoutParams(params);
        chip.setOnClickListener(v -> {
            if (category.equals(selectedCategory)) return;
            int categoryX = categoryScroll == null ? 0 : categoryScroll.getScrollX();
            selectedCategory = category;
            build();
            restoreScroll(0, categoryX);
        });
        return chip;
    }

    private void renderDesigns() {
        cards.removeAllViews();
        List<VisualDesigns.Design> shown = new ArrayList<>();
        for (VisualDesigns.Design design : VisualDesigns.all())
            if ("all".equals(selectedCategory) || selectedCategory.equals(design.categoryId())) shown.add(design);
        for (int i = 0; i < shown.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8); cards.addView(row, rowParams);
            addDesignCard(row, shown.get(i), true);
            if (i + 1 < shown.size()) addDesignCard(row, shown.get(i + 1), false);
            else row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    private void addDesignCard(LinearLayout row, VisualDesigns.Design design, boolean first) {
        boolean selected = design.id.equals(selectedId);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9), dp(9), dp(9), dp(9));
        card.setBackground(Ui.roundStroke(selected ? Ui.NAV_HIGHLIGHT : Ui.SURFACE,
                selected ? Ui.ACCENT_2 : Ui.BORDER, 15 * design.cornerScale, this));
        if ("raised".equals(design.cardTreatment) && android.os.Build.VERSION.SDK_INT >= 21)
            card.setElevation(dp(3));
        card.setFocusable(true); card.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (first) params.rightMargin = dp(4); else params.leftMargin = dp(4);
        row.addView(card, params);

        LinearLayout sampleRow = new LinearLayout(this);
        sampleRow.setGravity(Gravity.CENTER_VERTICAL);
        PatternSample pattern = new PatternSample(design.pattern);
        sampleRow.addView(pattern, new LinearLayout.LayoutParams(0, dp(46), 1));
        TreatmentSample treatment = new TreatmentSample(design.cardTreatment, design.cornerScale);
        LinearLayout.LayoutParams treatmentParams = new LinearLayout.LayoutParams(dp(35), dp(30));
        treatmentParams.leftMargin = dp(7); sampleRow.addView(treatment, treatmentParams);
        card.addView(sampleRow);

        TextView type = new TextView(this);
        type.setText("07:30   Aa"); type.setTextSize(17); type.setTextColor(Ui.TEXT);
        type.setTypeface(VisualDesigns.typeface(this, design, true));
        if (android.os.Build.VERSION.SDK_INT >= 21) type.setLetterSpacing(design.letterSpacing);
        type.setPadding(0, dp(5), 0, 0); card.addView(type);

        TextView name = new TextView(this);
        name.setText(design.label(this)); name.setTextSize(13); name.setTextColor(Ui.TEXT);
        name.setTypeface(VisualDesigns.typeface(this, design, true));
        name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, dp(4), 0, 0); card.addView(name);

        TextView description = new TextView(this);
        description.setText(design.description(this)); description.setTextSize(10.5f);
        description.setTextColor(Ui.MUTED); description.setMaxLines(3);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setPadding(0, dp(3), 0, 0); card.addView(description);

        TextView treatmentLabel = new TextView(this);
        treatmentLabel.setText(categoryLabel(design.categoryId()) + " · " + treatmentLabel(design.cardTreatment));
        treatmentLabel.setTextSize(9.5f); treatmentLabel.setTextColor(Ui.ACCENT_2);
        treatmentLabel.setSingleLine(true); treatmentLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        treatmentLabel.setPadding(0, dp(5), 0, 0); card.addView(treatmentLabel);

        card.setContentDescription(design.label(this) + ", " + design.description(this)
                + (selected ? ", " + local("選択中", "Selected", "선택됨") : ""));
        card.setOnClickListener(v -> { selectedId = design.id; renderDesigns(); });
    }

    private void applySelection() {
        VisualDesigns.select(this, selectedId);
        setResult(Activity.RESULT_OK, new Intent().putExtra("visual_design_id", selectedId));
        finish();
    }

    private Drawable ripple(int color, float radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        if (android.os.Build.VERSION.SDK_INT >= 21)
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x22000000), shape, null);
        return shape;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String categoryLabel(String category) {
        switch (category) {
            case "paper": return local("紙・工芸", "Paper", "종이·공예");
            case "nature": return local("自然", "Nature", "자연");
            case "retro": return local("レトロ", "Retro", "레트로");
            case "tech": return local("テック", "Tech", "테크");
            case "editorial": return local("編集・印刷", "Editorial", "편집·인쇄");
            case "playful": return local("遊び", "Playful", "놀이");
            default: return local("すべて", "All", "전체");
        }
    }

    private String treatmentLabel(String treatment) {
        switch (treatment) {
            case "outline": return local("輪郭", "Outline", "윤곽선");
            case "double-border": return local("二重枠", "Double", "이중 테두리");
            case "raised": return local("浮き上がり", "Raised", "돌출");
            case "dashed": return local("破線", "Stitched", "점선");
            case "wire": return local("ワイヤー", "Wire", "와이어");
            default: return local("フラット", "Flat", "플랫");
        }
    }

    private String local(String ja, String en, String ko) {
        String language = I18n.language(this);
        return "en".equals(language) ? en : "ko".equals(language) ? ko : ja;
    }

    private final class PatternSample extends View {
        private final String pattern;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PatternSample(String pattern) { super(VisualDesignPickerActivity.this); this.pattern = pattern; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF bounds = new RectF(0, 0, getWidth(), getHeight());
            float radius = dp(7);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.SURFACE_2);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            Path clip = new Path(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int save = canvas.save(); canvas.clipPath(clip);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(0.8f));
            paint.setColor(VisualDesigns.withAlpha(Ui.ACCENT_2, 48));
            String scene = VisualDesigns.landscapeSceneId(pattern);
            if (scene == null) {
                VisualDesigns.drawPattern(canvas, pattern, bounds, paint,
                        getResources().getDisplayMetrics().density);
            } else {
                AppThemes.Theme theme = AppThemes.current(VisualDesignPickerActivity.this);
                LandscapeArt.draw(canvas, scene, bounds,
                        LandscapeArt.paletteFromTheme(theme.heroStart, theme.heroEnd, theme.surface,
                                theme.accent, theme.accent2, theme.text, theme.heroText),
                        getResources().getDisplayMetrics().density);
            }
            canvas.restoreToCount(save);
        }
    }

    private final class TreatmentSample extends View {
        private final String treatment;
        private final float cornerScale;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TreatmentSample(String treatment, float cornerScale) {
            super(VisualDesignPickerActivity.this); this.treatment = treatment; this.cornerScale = cornerScale;
            if ("raised".equals(treatment) && android.os.Build.VERSION.SDK_INT >= 21) setElevation(dp(3));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF r = new RectF(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2));
            float radius = dp(7 * Math.max(.2f, Math.min(1.4f, cornerScale)));
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.SURFACE_2); canvas.drawRoundRect(r, radius, radius, paint);
            paint.setColor(Ui.ACCENT_2); paint.setStrokeWidth(dp(1)); paint.setStyle(Paint.Style.STROKE);
            if ("flat".equals(treatment)) { paint.setStyle(Paint.Style.FILL); canvas.drawCircle(r.centerX(), r.centerY(), dp(2), paint); }
            else if ("double-border".equals(treatment)) {
                canvas.drawRoundRect(r, radius, radius, paint);
                RectF inner = new RectF(r.left + dp(3), r.top + dp(3), r.right - dp(3), r.bottom - dp(3));
                canvas.drawRoundRect(inner, Math.max(1, radius - dp(2)), Math.max(1, radius - dp(2)), paint);
            } else if ("dashed".equals(treatment)) {
                PathEffect effect = new android.graphics.DashPathEffect(new float[]{dp(3), dp(2)}, 0);
                paint.setPathEffect(effect); canvas.drawRoundRect(r, radius, radius, paint); paint.setPathEffect(null);
            } else if ("wire".equals(treatment)) {
                Path path = new android.graphics.Path();
                path.moveTo(r.left, r.top + dp(5)); path.lineTo(r.left, r.top); path.lineTo(r.left + dp(5), r.top);
                path.moveTo(r.right - dp(5), r.bottom); path.lineTo(r.right, r.bottom); path.lineTo(r.right, r.bottom - dp(5));
                canvas.drawPath(path, paint);
            } else canvas.drawRoundRect(r, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
