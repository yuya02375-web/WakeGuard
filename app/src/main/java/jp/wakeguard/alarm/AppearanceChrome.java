package jp.wakeguard.alarm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared navigation and commit bar for the five appearance screens. */
public final class AppearanceChrome {
    public static final int SETS = 0;
    public static final int UI_STYLE = 1;
    public static final int MOTIF = 2;
    public static final int COLOR = 3;
    public static final int CHARACTER = 4;

    private static final String[][] TITLES = {
            {"デザインセット", "Design sets", "디자인 세트"},
            {"UIスタイル", "UI style", "UI 스타일"},
            {"模様・文字", "Motif & type", "무늬·글꼴"},
            {"色・モード", "Color & mode", "색상·모드"},
            {"キャラクター", "Character", "캐릭터"}
    };
    @SuppressWarnings("unchecked")
    private static final Class<? extends Activity>[] SCREENS = new Class[] {
            DesignSetPickerActivity.class, UiProfilePickerActivity.class,
            VisualDesignPickerActivity.class, ThemePickerActivity.class, MascotPickerActivity.class
    };

    private AppearanceChrome() {}

    private static String local(Activity activity, String ja, String en, String ko) {
        String language = I18n.language(activity);
        return "en".equals(language) ? en : "ko".equals(language) ? ko : ja;
    }

    public static String title(Activity activity, int tab) {
        String[] title = TITLES[tab];
        return local(activity, title[0], title[1], title[2]);
    }

    public static LinearLayout header(Activity activity, int selectedTab, String subtitle) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(activity);
        Drawable arrow = activity.getDrawable(R.drawable.ic_v293_chevron_left);
        if (arrow != null) {
            arrow = arrow.mutate();
            arrow.setTint(Ui.TEXT);
            back.setImageDrawable(arrow);
        }
        back.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12));
        back.setBackground(Ui.round(Ui.SURFACE_2, 24, activity));
        back.setContentDescription(I18n.tr(activity, "戻る"));
        back.setOnClickListener(v -> Ui.finishNoAnimation(activity));
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        TextView heading = Ui.title(activity, title(activity, selectedTab), 26);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(Ui.dp(activity, 12), 0, 0, 0);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -1, 1));
        header.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 56)));

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = Ui.text(activity, subtitle, 13, Ui.MUTED);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        }

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        View selectedView = null;
        for (int index = 0; index < TITLES.length; index++) {
            final int destination = index;
            boolean active = index == selectedTab;
            LinearLayout target = new LinearLayout(activity);
            target.setGravity(Gravity.CENTER);
            target.setPadding(0, 0, Ui.dp(activity, 6), 0);
            TextView chip = Ui.text(activity, title(activity, index), 12, active ? Ui.ON_ACCENT : Ui.TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            chip.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
            chip.setBackground(Ui.round(active ? Ui.ACCENT_2 : Ui.SURFACE_2, 18, activity));
            target.addView(chip, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 36)));
            target.setContentDescription(title(activity, index));
            target.setSelected(active);
            target.setFocusable(true);
            target.setOnClickListener(v -> {
                if (destination == selectedTab) return;
                Ui.launchNoAnimation(activity, new Intent(activity, SCREENS[destination]));
                Ui.finishNoAnimation(activity);
            });
            tabs.addView(target, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 48)));
            if (active) selectedView = target;
        }
        scroll.addView(tabs);
        header.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        final View activeView = selectedView;
        if (activeView != null) scroll.post(() -> scroll.scrollTo(
                Math.max(0, activeView.getLeft() - (scroll.getWidth() - activeView.getWidth()) / 2), 0));
        return header;
    }

    /** A sticky bar whose label can be refreshed when the pending selection changes. */
    public static final class ApplyBar extends LinearLayout {
        private final Activity activity;
        private final TextView summary;

        public ApplyBar(Activity activity, String action, Runnable apply) {
            super(activity);
            this.activity = activity;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
            setBackgroundColor(Ui.SURFACE);
            summary = Ui.text(activity, "", 12, Ui.MUTED);
            summary.setMaxLines(2);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
            Button button = Ui.button(activity, action, true);
            button.setTextSize(13);
            button.setMinHeight(Ui.dp(activity, 48));
            button.setOnClickListener(v -> apply.run());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, Ui.dp(activity, 48));
            buttonParams.leftMargin = Ui.dp(activity, 8);
            addView(button, buttonParams);
        }

        public void setSelection(String name) {
            summary.setText(local(activity, "選択中: {0}", "Selected: {0}", "선택: {0}")
                    .replace("{0}", name));
        }
    }
}
