package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
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
        LinearLayout nameStack = new LinearLayout(this);
        nameStack.setOrientation(LinearLayout.VERTICAL);
        nameStack.setPadding(Ui.dp(this, 10), 0, 0, 0);
        nameStack.addView(Ui.title(this, preset.label(this), 17));
        nameStack.addView(Ui.text(this, preset.components(this), 11.5f, Ui.MUTED), Ui.gapTop(this, 4));
        heading.addView(nameStack, new LinearLayout.LayoutParams(0, -2, 1));
        if (active) heading.addView(Ui.accentPill(this, localized("適用中", "Active", "사용 중")));
        card.addView(heading);

        TextView description = Ui.text(this, preset.description(this), 13, Ui.MUTED);
        card.addView(description, Ui.gapTop(this, 7));
        VisualDesigns.Design visual = VisualDesigns.get(preset.visualDesignId);
        if (visual != null) {
            View preview = new PresetPreview(this, visual, setTheme);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 82));
            previewParams.topMargin = Ui.dp(this, 10);
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

    private final class LandscapePreview extends View {
        private final String pattern;
        private final AppThemes.Theme theme;
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);

        LandscapePreview(Context context, String pattern, AppThemes.Theme theme) {
            super(context);
            this.pattern = pattern;
            this.theme = theme;
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{theme.bg, theme.surface});
            fill.setCornerRadius(Ui.dp(DesignSetPickerActivity.this, 13));
            setBackground(fill);
            setContentDescription(pattern.replace('-', ' ') + " landscape preview");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF bounds = new RectF(0, 0, getWidth(), getHeight());
            if (bounds.isEmpty()) return;
            float radius = Ui.dp(DesignSetPickerActivity.this, 13);
            int save = canvas.save();
            Path clip = new Path();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            String scene = VisualDesigns.landscapeSceneId(pattern);
            LandscapeArt.Palette palette = LandscapeArt.paletteFromTheme(
                    theme.heroStart, theme.heroEnd, theme.surface,
                    theme.accent, theme.accent2, theme.text, theme.heroText);
            if (!LandscapeArt.drawImage(DesignSetPickerActivity.this, canvas, scene,
                    bounds, theme.heroText, false)) {
                LandscapeArt.draw(canvas, scene, bounds, palette,
                        getResources().getDisplayMetrics().density);
            }
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Ui.dp(DesignSetPickerActivity.this, 1));
            border.setColor(VisualDesigns.withAlpha(theme.accent, 0x66));
            canvas.drawRoundRect(bounds, radius, radius, border);
            canvas.restoreToCount(save);
        }
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

    private final class PresetPreview extends View {
        private final VisualDesigns.Design visual;
        private final AppThemes.Theme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PresetPreview(Context context, VisualDesigns.Design visual, AppThemes.Theme theme) {
            super(context);
            this.visual = visual;
            this.theme = theme;
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{theme.bg, theme.surface});
            fill.setCornerRadius(Ui.dp(DesignSetPickerActivity.this, 14));
            setBackground(fill);
            setContentDescription(visual.label(DesignSetPickerActivity.this) + " " +
                    localized("プレビュー", "preview", "미리보기"));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            RectF bounds = new RectF(0, 0, getWidth(), getHeight());
            if (bounds.isEmpty()) return;
            float pad = 12 * density;
            float radius = 14 * density;
            int save = canvas.save();
            Path clip = new Path();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.accent);
            paint.setAlpha(28);
            canvas.drawCircle(bounds.right - pad * 2, pad, 38 * density, paint);
            paint.setAlpha(255);

            paint.setColor(theme.muted);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(8 * density);
            canvas.drawText(localized("毎日のアラーム", "DAILY ALARM", "매일 알람"), pad, 21 * density, paint);

            paint.setColor(theme.text);
            paint.setTypeface(VisualDesigns.typeface(DesignSetPickerActivity.this, visual, true));
            paint.setTextSize(25 * density);
            canvas.drawText("06:30", pad, 50 * density, paint);

            RectF pill = new RectF(pad, 58 * density, pad + 55 * density, 73 * density);
            paint.setColor(theme.accent);
            canvas.drawRoundRect(pill, 8 * density, 8 * density, paint);
            paint.setColor(theme.heroText);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(7 * density);
            canvas.drawText(localized("起床", "WAKE", "기상"), pill.left + 9 * density, pill.top + 10.5f * density, paint);

            float artWidth = Math.min(100 * density, getWidth() * .34f);
            RectF art = new RectF(bounds.right - pad - artWidth, pad, bounds.right - pad, bounds.bottom - pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.surface);
            canvas.drawRoundRect(art, 10 * density, 10 * density, paint);
            Path artClip = new Path();
            artClip.addRoundRect(art, 10 * density, 10 * density, Path.Direction.CW);
            int artSave = canvas.save();
            canvas.clipPath(artClip);
            String scene = VisualDesigns.landscapeSceneId(visual.pattern);
            if (scene != null) {
                LandscapeArt.Palette palette = LandscapeArt.paletteFromTheme(
                        theme.heroStart, theme.heroEnd, theme.surface,
                        theme.accent, theme.accent2, theme.text, theme.heroText);
                if (!LandscapeArt.drawImage(DesignSetPickerActivity.this, canvas, scene,
                        art, theme.heroText, false)) {
                    LandscapeArt.draw(canvas, scene, art, palette, density);
                }
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, density));
                paint.setColor(VisualDesigns.withAlpha(theme.accent2, 255));
                VisualDesigns.drawPattern(DesignSetPickerActivity.this, canvas, visual.pattern,
                        visual.id, art, paint, density);
            }
            canvas.restoreToCount(artSave);
            canvas.restoreToCount(save);
        }
    }

    private String localized(String ja, String en, String ko) {
        String language = I18n.language(this);
        return "ko".equals(language) ? ko : "en".equals(language) ? en : ja;
    }
}
