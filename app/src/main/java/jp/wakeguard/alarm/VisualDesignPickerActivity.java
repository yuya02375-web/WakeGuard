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
    private String appliedDesignId;
    private String selectedCategory = "all";
    private String paletteKey;
    private LinearLayout cards;
    private HorizontalScrollView categoryScroll;
    private ScrollView designScroll;
    private AppearanceChrome.ApplyBar applyBar;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        selectedId = state == null ? VisualDesigns.id(this)
                : VisualDesigns.get(state.getString(STATE_SELECTED_ID, VisualDesigns.id(this))).id;
        appliedDesignId = VisualDesigns.id(this);
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
        String appliedNow = VisualDesigns.id(this);
        boolean designChanged = appliedDesignId != null && !appliedDesignId.equals(appliedNow);
        boolean paletteChanged = paletteKey != null && !paletteKey.equals(current);
        if (designChanged || paletteChanged) {
            int scrollY = designScroll == null ? 0 : designScroll.getScrollY();
            int categoryX = categoryScroll == null ? 0 : categoryScroll.getScrollX();
            if (designChanged) selectedId = appliedNow;
            appliedDesignId = appliedNow;
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

        root.addView(AppearanceChrome.header(this, AppearanceChrome.MOTIF,
                local("模様・文字・カードの形を選びます。色は別に選べます。",
                        "Choose patterns, typography, and card shape. Color is selected separately.",
                        "무늬·글꼴·카드 모양을 고릅니다. 색상은 따로 선택할 수 있습니다.")));

        categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(14), 0, dp(14), 0);
        for (String category : CATEGORIES) chips.addView(categoryChip(category));
        categoryScroll.addView(chips);
        root.addView(categoryScroll, new LinearLayout.LayoutParams(-1, dp(48)));

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

        applyBar = new AppearanceChrome.ApplyBar(this,
                local("このデザインを適用", "Apply design", "디자인 적용"), this::applySelection);
        root.addView(applyBar, new LinearLayout.LayoutParams(-1, dp(72)));

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
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setBackground(category.equals(selectedCategory) ? ripple(Ui.ACCENT_2, 18)
                : Ui.roundStroke(Ui.SURFACE_2, Ui.BORDER, 18, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.setMargins(0, dp(4), dp(7), dp(4)); chip.setLayoutParams(params);
        chip.setSelected(category.equals(selectedCategory));
        chip.setContentDescription(categoryLabel(category)
                + (category.equals(selectedCategory) ? ", " + local("選択中", "Selected", "선택됨") : ""));
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
        VisualDesigns.Design selectedDesign = VisualDesigns.get(selectedId);
        if (applyBar != null && selectedDesign != null) applyBar.setSelection(selectedDesign.label(this));
        TextView count = new TextView(this);
        count.setText(local(shown.size() + " 種類", shown.size() + " styles", shown.size() + "개 스타일"));
        count.setTextSize(11.5f); count.setTextColor(Ui.MUTED);
        count.setPadding(dp(3), 0, 0, dp(8));
        cards.addView(count);
        for (VisualDesigns.Design design : shown) addDesignCard(design);
    }

    private void addDesignCard(VisualDesigns.Design design) {
        boolean selected = design.id.equals(selectedId);
        float corner = Math.max(.65f, Math.min(1.25f, design.cornerScale));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(12));
        card.setBackground(Ui.roundStroke(selected ? Ui.SURFACE_2 : Ui.SURFACE,
                selected ? Ui.ACCENT_2 : Ui.BORDER, 18, this));
        card.setForeground(Ui.rippleForeground(this, 18));
        if ("raised".equals(design.cardTreatment) && android.os.Build.VERSION.SDK_INT >= 21)
            card.setElevation(dp(3));
        card.setFocusable(true);
        card.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        cards.addView(card, params);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(design.label(this));
        name.setTextSize(17);
        name.setTextColor(Ui.TEXT);
        name.setTypeface(VisualDesigns.typeface(this, design, true));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        boolean active = design.id.equals(VisualDesigns.id(this));
        if (selected || active) {
            TextView badge = new TextView(this);
            badge.setText(selected && active ? local("使用中", "In use", "사용 중")
                    : selected ? local("選択中", "Selected", "선택됨")
                    : local("使用中", "In use", "사용 중"));
            badge.setTextSize(10);
            badge.setTextColor(Ui.ON_ACCENT);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(9), dp(5), dp(9), dp(5));
            badge.setBackground(Ui.round(Ui.ACCENT_2, 11, this));
            titleRow.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        }
        card.addView(titleRow);

        TextView description = new TextView(this);
        description.setText(design.description(this));
        description.setTextSize(12.5f);
        description.setTextColor(Ui.MUTED);
        description.setMaxLines(2);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setPadding(0, dp(4), 0, 0);
        card.addView(description);

        LinearLayout sample = new LinearLayout(this);
        sample.setOrientation(LinearLayout.VERTICAL);
        sample.setGravity(Gravity.CENTER);
        sample.setPadding(dp(8), dp(7), dp(8), dp(7));
        GradientDrawable sampleBackground = new GradientDrawable();
        sampleBackground.setColor(Ui.BG_2);
        sampleBackground.setCornerRadius(dp(13 * corner));
        sample.setBackground(sampleBackground);
        PatternSample pattern = new PatternSample(design);
        sample.addView(pattern, new LinearLayout.LayoutParams(-1, dp(140)));

        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView type = new TextView(this);
        type.setText("07:30   Aa");
        type.setTextSize(14);
        type.setTextColor(Ui.TEXT);
        type.setTypeface(VisualDesigns.typeface(this, design, true));
        if (android.os.Build.VERSION.SDK_INT >= 21) type.setLetterSpacing(design.letterSpacing);
        typeRow.addView(type, new LinearLayout.LayoutParams(0, -2, 1));
        TreatmentSample treatment = new TreatmentSample(design.cardTreatment, design.cornerScale);
        LinearLayout.LayoutParams treatmentParams = new LinearLayout.LayoutParams(dp(30), dp(22));
        treatmentParams.leftMargin = dp(6);
        typeRow.addView(treatment, treatmentParams);
        sample.addView(typeRow, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(-1, dp(196));
        sampleParams.topMargin = dp(10);
        card.addView(sample, sampleParams);

        TextView category = new TextView(this);
        category.setText(categoryLabel(design.categoryId()) + "  ·  " + fontLabel(design.fontFamily)
                + "  ·  " + treatmentLabel(design.cardTreatment));
        category.setTextSize(11);
        category.setTextColor(Ui.MUTED);
        category.setPadding(0, dp(8), 0, 0);
        card.addView(category);

        card.setContentDescription(design.label(this) + ", " + design.description(this)
                + ", " + fontLabel(design.fontFamily) + ", " + treatmentLabel(design.cardTreatment)
                + (selected ? ", " + local("選択中", "Selected", "선택됨") : ""));
        card.setOnClickListener(v -> {
            int scrollY = designScroll == null ? 0 : designScroll.getScrollY();
            selectedId = design.id;
            renderDesigns();
            if (designScroll != null) designScroll.post(() -> designScroll.scrollTo(0, scrollY));
        });
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

    private String fontLabel(String family) {
        switch (family) {
            case "serif": return local("セリフ体", "Serif", "세리프");
            case "mono": return local("等幅", "Monospaced", "고정폭");
            case "condensed": return local("コンデンス体", "Condensed", "압축형");
            default: return local("サンセリフ", "Sans serif", "산세리프");
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
        private final VisualDesigns.Design design;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PatternSample(VisualDesigns.Design design) { super(VisualDesignPickerActivity.this); this.design = design; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF bounds = new RectF(0, 0, getWidth(), getHeight());
            float radius = dp(7);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.SURFACE_2);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            Path clip = new Path(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int save = canvas.save(); canvas.clipPath(clip);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(0.8f));
            paint.setColor(VisualDesigns.withAlpha(Ui.ACCENT_2, 255));
            String scene = VisualDesigns.landscapeSceneId(design.pattern);
            if (scene == null) {
                VisualDesigns.drawPattern(VisualDesignPickerActivity.this, canvas, design.pattern, design.id, bounds, paint,
                        getResources().getDisplayMetrics().density);
            } else {
                AppThemes.Theme theme = AppThemes.current(VisualDesignPickerActivity.this);
                LandscapeArt.Palette palette = LandscapeArt.paletteFromTheme(
                        theme.heroStart, theme.heroEnd, theme.surface,
                        theme.accent, theme.accent2, theme.text, theme.heroText);
                if (!LandscapeArt.drawImage(VisualDesignPickerActivity.this, canvas, scene,
                        bounds, theme.heroText, false)) {
                    LandscapeArt.draw(canvas, scene, bounds, palette,
                            getResources().getDisplayMetrics().density);
                }
            }
            drawAlarmPreview(canvas, bounds);
            canvas.restoreToCount(save);
        }

        private void drawAlarmPreview(Canvas canvas, RectF bounds) {
            float density = getResources().getDisplayMetrics().density;
            float inset = dp(9);
            float panelHeight = dp(55);
            RectF panel = new RectF(bounds.left + inset, bounds.bottom - inset - panelHeight,
                    bounds.right - inset, bounds.bottom - inset);
            Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
            overlay.setColor(0xE6121A22);
            canvas.drawRoundRect(panel, dp(12), dp(12), overlay);
            overlay.setStyle(Paint.Style.STROKE);
            overlay.setStrokeWidth(Math.max(1f, density * .7f));
            overlay.setColor(0x55FFFFFF);
            canvas.drawRoundRect(panel, dp(12), dp(12), overlay);

            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            text.setColor(Color.WHITE);
            text.setTypeface(VisualDesigns.typeface(VisualDesignPickerActivity.this, design, true));
            text.setTextSize(dp(20));
            canvas.drawText("07:30", panel.left + dp(11), panel.bottom - dp(10), text);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setTextSize(dp(9));
            text.setColor(0xFFDCE7EC);
            canvas.drawText(local("毎日  ·  アラーム", "Every day  ·  Alarm", "매일  ·  알람"),
                    panel.left + dp(77), panel.bottom - dp(15), text);

            float trackRight = panel.right - dp(11);
            float trackCenterY = panel.centerY();
            RectF track = new RectF(trackRight - dp(29), trackCenterY - dp(8),
                    trackRight, trackCenterY + dp(8));
            overlay.setStyle(Paint.Style.FILL);
            overlay.setColor(Ui.ACCENT_2);
            canvas.drawRoundRect(track, dp(8), dp(8), overlay);
            overlay.setColor(Color.WHITE);
            canvas.drawCircle(track.right - dp(8), trackCenterY, dp(5.5f), overlay);

            RectF tag = new RectF(bounds.left + inset, bounds.top + inset,
                    bounds.left + inset + dp(96), bounds.top + inset + dp(23));
            overlay.setColor(0xD9F5F7F6);
            canvas.drawRoundRect(tag, dp(12), dp(12), overlay);
            text.setColor(0xFF17232A);
            text.setTextSize(dp(9));
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            canvas.drawText(local("画面プレビュー", "SCREEN PREVIEW", "화면 미리보기"),
                    tag.left + dp(9), tag.centerY() + dp(3), text);
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
