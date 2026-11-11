package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.io.OutputStream;

public class SystemSettingsActivity extends Activity {
    private LinearLayout body;
    private String appearanceKey;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.prepareActivity(this);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        String current = appearanceKey();
        if (appearanceKey != null && !appearanceKey.equals(current)) {
            appearanceKey = current;
            recreate();
            return;
        }
        renderStatus();
    }

    @Override public void onBackPressed() {
        navigateRoot("alarm");
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 13), Ui.dp(this, 20), Ui.dp(this, 24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(rootNavigation());

        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        renderStatus();
    }

    private void renderStatus() {
        if (body == null) return;
        body.removeAllViews();

        body.addView(Ui.overline(this, "IGNIDO  /  WAKE"));
        TextView title = Ui.title(this, "設定", 31);
        title.setPadding(0, Ui.dp(this, 4), 0, 0);
        body.addView(title);
        body.addView(Ui.text(this, localize("アラームと時計を自分に合わせて整えます。",
                "Tune alarms and clocks to fit your routine.", "알람과 시계를 생활 방식에 맞게 설정하세요."), 14, Ui.MUTED), Ui.gapTop(this, 4));

        addAppCard();
        addBackupCard();
        addPermissionsCard();
        addOtherSettingsCard();
        appearanceKey = appearanceKey();
    }

    private void addAppCard() {
        LinearLayout card = sectionCard("アプリ設定", null);
        addActionRow(card, localize("アプリの言語", "App language", "앱 언어"), I18n.selectedLanguageLabel(this), this::showLanguageDialog);
        addDivider(card);
        addActionRow(card, localize("デザインセット", "Design set", "디자인 세트"), DesignSets.currentLabel(this),
                () -> Ui.launchNoAnimation(this, new Intent(this, DesignSetPickerActivity.class)));
        addDivider(card);
        addActionRow(card, localize("UIスタイル", "UI style", "UI 스타일"),
                UiProfiles.current(this).label(this),
                () -> Ui.launchNoAnimation(this, new Intent(this, UiProfilePickerActivity.class)));
        addDivider(card);
        addActionRow(card, localize("デザインと配色", "Design and color", "디자인과 색상"),
                VisualDesigns.current(this).label(this) + "  ·  " + AppThemes.current(this).label(this),
                () -> Ui.launchNoAnimation(this, new Intent(this, VisualDesignPickerActivity.class)));
        addDivider(card);
        addActionRow(card, localize("キャラクター", "Character", "캐릭터"), MascotCatalog.current(this).label(this),
                () -> Ui.launchNoAnimation(this, new Intent(this, MascotPickerActivity.class)));
        addDivider(card);
        addActionRow(card, localize("時計の詳細設定", "Clock settings", "시계 설정"), localize("表示と文字盤を調整", "Customize clock display and face", "시계 표시와 문자판을 설정"), () ->
                Ui.launchNoAnimation(this, new Intent(this, ClockSettingsActivity.class)));
    }

    private String themeModeLabel(String mode) {
        if (AppThemes.MODE_DARK.equals(mode)) return localize("ダーク", "Dark", "다크");
        if (AppThemes.MODE_LIGHT.equals(mode)) return localize("ライト", "Light", "라이트");
        return localize("システム", "System", "시스템");
    }

    private String appearanceKey() {
        return UiProfiles.id(this) + ":" + VisualDesigns.id(this) + ":" + AppThemes.key(this)
                + ":" + MascotCatalog.id(this);
    }

    private void addBackupCard() {
        LinearLayout card = sectionCard(localize("バックアップ", "Backup", "백업"),
                localize("アラーム・時間記録・ストリークはAndroidのバックアップへ自動保存します。手動ファイルも作成・復元できます。",
                        "Android backs up alarms, time logs, and streaks automatically. You can also export or restore a file.",
                        "Android가 알람, 시간 기록, 스트릭을 자동 백업합니다. 파일 백업도 내보내거나 복원할 수 있습니다."));

        Button export = Ui.button(this, localize("バックアップを書き出す", "Export backup", "백업 내보내기"), true);
        export.setOnClickListener(v -> exportBackup());
        card.addView(export, Ui.gapTop(this, 14));

        Button restore = Ui.button(this, localize("バックアップを読み込む", "Restore backup", "백업 복원"), false);
        restore.setOnClickListener(v -> importBackup());
        card.addView(restore, Ui.gapTop(this, 9));
    }

    private void addPermissionsCard() {
        LinearLayout card = sectionCard("端末設定", "ロック画面で確実に鳴らすために必要な端末側の設定です。");

        addStatus(card, "正確なアラーム", AlarmScheduler.canScheduleExact(this), this::openExact);
        addDivider(card);

        boolean fullScreen = true;
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                NotificationManager manager = getSystemService(NotificationManager.class);
                fullScreen = manager != null && manager.canUseFullScreenIntent();
            } catch (Throwable ignored) {
                fullScreen = false;
            }
        }
        addStatus(card, "全画面アラーム", fullScreen, this::openFull);
        addDivider(card);
        addStatus(card, "他のアプリの上に表示", Settings.canDrawOverlays(this), this::openOverlay);
    }

    private void addOtherSettingsCard() {
        LinearLayout card = sectionCard("その他", null);
        addActionRow(card, "通知とロック画面表示", null, this::openNotifications);
        addDivider(card);
        addActionRow(card, "バッテリー最適化から除外", null, this::openBattery);
        addDivider(card);
        addActionRow(card, "アプリ情報", null, this::openDetails);

        TextView realme = Ui.text(this,
                "realmeではアプリ情報内の「ロック画面に表示」「バックグラウンドでポップアップ」「バックグラウンドアクティビティ」も許可してください。",
                12, Ui.MUTED);
        realme.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        realme.setBackground(Ui.round(Ui.SURFACE_2, 14, this));
        card.addView(realme, Ui.gapTop(this, 10));
    }

    private LinearLayout sectionCard(String title, String note) {
        LinearLayout card = Ui.card(this);
        TextView heading = Ui.title(this, localizeSection(title), 18);
        card.addView(heading);
        if (note != null && !note.isEmpty()) {
            TextView description = Ui.text(this, note, 13, Ui.MUTED);
            description.setPadding(0, Ui.dp(this, 5), 0, 0);
            card.addView(description);
        }
        body.addView(card, Ui.cardParams(this));
        return card;
    }

    private void addActionRow(LinearLayout parent, String label, String detail, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setMinimumHeight(Ui.dp(this, 56));
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, label, 15, Ui.TEXT));
        if (detail != null && !detail.isEmpty()) {
            copy.addView(Ui.text(this, detail, 12, Ui.MUTED), Ui.gapTop(this, 3));
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = Ui.text(this, "›", 24, Ui.MUTED);
        chevron.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 2), 0);
        row.addView(chevron);
        String spoken = String.valueOf(((TextView) copy.getChildAt(0)).getText());
        if (copy.getChildCount() > 1) spoken += ", " + String.valueOf(((TextView) copy.getChildAt(1)).getText());
        row.setContentDescription(spoken);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
    }

    private void addStatus(LinearLayout parent, String label, boolean ok, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setMinimumHeight(Ui.dp(this, 58));
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7));
        row.addView(Ui.text(this, label, 15, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));

        TextView state = Ui.text(this, ok ? "設定済み" : "確認が必要です", 11, ok ? Ui.SUCCESS : Ui.DANGER);
        state.setTypeface(null, android.graphics.Typeface.BOLD);
        state.setGravity(Gravity.CENTER);
        state.setPadding(Ui.dp(this, 9), Ui.dp(this, 6), Ui.dp(this, 9), Ui.dp(this, 6));
        state.setBackground(Ui.round(ok ? 0xFFE6F0E9 : 0xFFF6E6E1, 12, this));
        row.addView(state);
        TextView chevron = Ui.text(this, "›", 22, Ui.MUTED);
        chevron.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 2), 0);
        row.addView(chevron);
        row.setContentDescription(I18n.tr(this, label) + ", " + I18n.tr(this, ok ? "設定済み" : "確認が必要です"));
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
    }

    private void addDivider(LinearLayout parent) {
        View divider = Ui.divider(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, 1));
        params.leftMargin = Ui.dp(this, 2);
        params.rightMargin = Ui.dp(this, 2);
        parent.addView(divider, params);
    }

    private String localizeSection(String japanese) {
        if ("バックアップ".equals(japanese)) return localize(japanese, "Backup", "백업");
        return I18n.tr(this, japanese);
    }

    private String localize(String japanese, String english, String korean) {
        String language = I18n.language(this);
        if ("en".equals(language)) return english;
        if ("ko".equals(language)) return korean;
        return japanese;
    }

    private LinearLayout rootNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        nav.setBackgroundColor(Ui.SURFACE);

        Button alarms = Ui.bottomTab(this, "アラーム", false);
        Button clock = Ui.bottomTab(this, "時計", false);
        Button records = Ui.bottomTab(this, "記録", false);
        Button settings = Ui.bottomTab(this, "設定", true);
        Button[] tabs = {alarms, clock, records, settings};
        for (Button tab : tabs) nav.addView(tab, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1));

        alarms.setOnClickListener(v -> navigateRoot("alarm"));
        clock.setOnClickListener(v -> navigateRoot("clock"));
        records.setOnClickListener(v -> navigateRoot("records"));
        return nav;
    }

    private void navigateRoot(String destination) {
        if ("alarm".equals(destination)) {
            Intent home = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Ui.launchNoAnimation(this, home);
            Ui.finishNoAnimation(this);
            return;
        }
        Intent next;
        if ("clock".equals(destination)) next = new Intent(this, ClockActivity.class).putExtra("mode", "world")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else if ("records".equals(destination)) next = new Intent(this, StatsActivity.class).putExtra("record_mode", "streak")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        else return;
        Ui.launchNoAnimation(this, next);
        Ui.finishNoAnimation(this);
    }

    private static final int REQ_EXPORT_BACKUP = 801;
    private static final int REQ_IMPORT_BACKUP = 802;

    private void exportBackup() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "IGNIDO-Wake-backup.json");
            startActivityForResult(i, REQ_EXPORT_BACKUP);
        } catch (Throwable t) {
            Toast.makeText(this, localize("バックアップ画面を開けませんでした", "Could not open the backup screen", "백업 화면을 열 수 없습니다."), Toast.LENGTH_SHORT).show();
        }
    }

    private void importBackup() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json");
            startActivityForResult(i, REQ_IMPORT_BACKUP);
        } catch (Throwable t) {
            Toast.makeText(this, localize("バックアップ画面を開けませんでした", "Could not open the backup screen", "백업 화면을 열 수 없습니다."), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_BACKUP) {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IllegalStateException();
                BackupVault.writeBackup(this, out);
                Toast.makeText(this, localize("バックアップを書き出しました", "Backup exported", "백업을 내보냈습니다."), Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, localize("バックアップを書き出せませんでした", "Could not export the backup", "백업을 내보내지 못했습니다."), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_IMPORT_BACKUP) {
            new AlertDialog.Builder(this)
                    .setTitle(localize("バックアップを復元", "Restore backup", "백업 복원"))
                    .setMessage(localize("現在のアラーム・時間記録・ストリークをバックアップ内容で置き換えます。",
                            "Current alarms, time logs, and streak records will be replaced with the backup contents.",
                            "현재 알람, 시간 기록, 스트릭이 백업 내용으로 대체됩니다."))
                    .setNegativeButton(I18n.tr(this, "キャンセル"), null)
                    .setPositiveButton(localize("復元", "Restore", "복원"), (d, w) -> {
                        try (InputStream in = getContentResolver().openInputStream(uri)) {
                            if (in == null) throw new IllegalStateException();
                            BackupVault.restore(this, in);
                            BackupVault.afterRestore(this);
                            Toast.makeText(this, localize("復元しました", "Backup restored", "복원했습니다."), Toast.LENGTH_SHORT).show();
                            renderStatus();
                        } catch (Throwable t) {
                            Toast.makeText(this, localize("バックアップを復元できませんでした", "Could not restore the backup", "백업을 복원하지 못했습니다."), Toast.LENGTH_LONG).show();
                        }
                    }).show();
        }
    }

    private void showLanguageDialog() {
        final Dialog dialog = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        box.setBackground(Ui.round(Ui.SURFACE, 18, this));
        box.addView(Ui.title(this, "アプリの言語", 20));
        String[][] items = {
                {"system", "システムに合わせる"}, {"ja", "日本語"}, {"en", "英語"}, {"ko", "韓国語"}
        };
        for (String[] item : items) {
            Button option = Ui.ghostButton(this, item[1]);
            option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            option.setTextSize(16);
            option.setOnClickListener(v -> {
                I18n.setLanguage(this, item[0]);
                dialog.dismiss();
                recreate();
            });
            box.addView(option, new LinearLayout.LayoutParams(-1, Ui.dp(this, 50)));
        }
        dialog.setContentView(box);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * .88f), -2);
        }
    }

    private void safe(Intent intent) {
        try {
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, I18n.tr(this, "この設定画面を端末が提供していません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void openExact() {
        if (Build.VERSION.SDK_INT >= 31) safe(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName())));
        else openDetails();
    }

    private void openFull() {
        if (Build.VERSION.SDK_INT >= 34) safe(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:" + getPackageName())));
        else openNotifications();
    }

    private void openOverlay() {
        safe(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void openNotifications() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        safe(i);
    }

    private void openBattery() {
        safe(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openDetails() {
        safe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
}
