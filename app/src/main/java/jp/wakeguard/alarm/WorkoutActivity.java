package jp.wakeguard.alarm;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Offline REP LIFT training log, linked to the existing WakeGuard streak and Ignido dragon. */
public final class WorkoutActivity extends Activity {
    private static final int REQUEST_LOCATION = 7301;
    private static final int REQUEST_IMPORT = 7302;
    private static final int REQUEST_EXPORT = 7303;

    private WebView webView;
    private StreakCompanionView dragon;
    private TextView sharedCount, sharedMeta, dragonLabel;
    private LocationManager locationManager;
    private final Map<Integer, LocationListener> locationListeners = new HashMap<>();
    private int pendingLocationId = -1;
    private String pendingExportJson;
    private android.webkit.ValueCallback<Uri[]> pendingFileChooser;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.prepareActivity(this);
        buildUi();
        refreshSharedHeader();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenGradient(this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 5));
        TextView back = Ui.text(this, "‹  WakeGuard", 15, Ui.MUTED);
        back.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(-2, -2));
        TextView title = Ui.title(this, "REP LIFT", 18);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout streakCard = Ui.card(this);
        streakCard.setOrientation(LinearLayout.HORIZONTAL);
        streakCard.setGravity(Gravity.CENTER_VERTICAL);
        streakCard.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 14), Ui.dp(this, 5));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 112));
        cardParams.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 5));
        root.addView(streakCard, cardParams);

        dragon = new StreakCompanionView(this);
        dragon.setContentDescription("WakeGuardのドラゴン イグニド");
        streakCard.addView(dragon, new LinearLayout.LayoutParams(Ui.dp(this, 92), -1));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(Ui.dp(this, 8), 0, 0, 0);
        streakCard.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        TextView eyebrow = Ui.overline(this, "WAKEGUARD  /  REP LIFT");
        copy.addView(eyebrow);
        TextView label = Ui.title(this, "共通ストリーク", 17);
        label.setPadding(0, Ui.dp(this, 2), 0, 0);
        copy.addView(label);
        sharedCount = Ui.title(this, "0日", 24);
        sharedCount.setPadding(0, Ui.dp(this, 1), 0, 0);
        copy.addView(sharedCount);
        sharedMeta = Ui.text(this, "起床成功・筋トレ・ランを合算", 11, Ui.MUTED);
        copy.addView(sharedMeta);
        dragonLabel = Ui.text(this, "", 11, Ui.MUTED);
        dragonLabel.setPadding(0, Ui.dp(this, 2), 0, 0);
        copy.addView(dragonLabel);

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(16, 19, 16));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        WebView.setWebContentsDebuggingEnabled(false);
        android.webkit.WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.addJavascriptInterface(new FitnessBridge(), "WakeGuard");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file:///android_asset/rep-lift/");
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith("file:///android_asset/rep-lift/");
            }
            @Override public void onPageFinished(WebView view, String url) { refreshSharedHeader(); }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = callback;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("application/json");
                try { startActivityForResult(pick, REQUEST_IMPORT); }
                catch (android.content.ActivityNotFoundException e) {
                    pendingFileChooser = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(WorkoutActivity.this, "ファイルアプリを開けません。", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        webView.loadUrl("file:///android_asset/rep-lift/index.html");
    }

    private void refreshSharedHeader() {
        if (sharedCount == null) return;
        int streak = SharedStreak.current(this);
        int best = SharedStreak.best(this);
        int thisWeek = SharedStreak.thisWeek(this);
        sharedCount.setText(streak + "日");
        sharedMeta.setText("共通最高 " + best + "日  ·  今週 " + thisWeek + "日");
        StreakGrowth.ensure(this);
        long level = StreakGrowth.level(this);
        dragon.setGrowth(level, streak);
        long remaining = StreakGrowth.remainingToNext(level);
        dragonLabel.setText(StreakGrowth.growthDescriptor(this, level) + " · Lv." + level
                + (remaining > 0 ? " / 次の成長まで " + remaining + "日" : " / 最終形態"));
    }

    private final class FitnessBridge {
        @JavascriptInterface public void recordActivity(String day) {
            runOnUiThread(() -> {
                if (SharedStreak.recordActivityDay(WorkoutActivity.this, day)) refreshSharedHeader();
            });
        }

        @JavascriptInterface public void requestLocation(int watchId) {
            runOnUiThread(() -> beginLocation(watchId));
        }

        @JavascriptInterface public void stopLocation(int watchId) {
            runOnUiThread(() -> stopLocationUpdates(watchId));
        }

        @JavascriptInterface public void saveBackup(String json) {
            if (json == null || json.length() > 20 * 1024 * 1024) return;
            runOnUiThread(() -> createBackup(json));
        }
    }

    private void beginLocation(int watchId) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingLocationId = watchId;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        startLocationUpdates(watchId);
    }

    private void startLocationUpdates(int watchId) {
        stopLocationUpdates(watchId);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) { sendLocationError(watchId, 2); return; }
        String provider = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        try {
            if (!locationManager.isProviderEnabled(provider)) {
                sendLocationError(watchId, 2);
                return;
            }
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (locationListeners.get(watchId) != this) return;
                    pushLocation(watchId, location);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {
                    sendLocationError(watchId, 2);
                }
            };
            locationListeners.put(watchId, listener);
            locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
            Location last = locationManager.getLastKnownLocation(provider);
            if (last != null) pushLocation(watchId, last);
        } catch (SecurityException | IllegalArgumentException e) {
            locationListeners.remove(watchId);
            sendLocationError(watchId, 2);
        }
    }

    private void pushLocation(int watchId, Location location) {
        if (webView == null) return;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
        String js = "window.__wakeguardLocation&&window.__wakeguardLocation(" + watchId + ","
                + location.getLatitude() + "," + location.getLongitude() + "," + accuracy + "," + location.getTime() + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void sendLocationError(int watchId, int code) {
        if (webView == null) return;
        String js = "window.__wakeguardLocationError&&window.__wakeguardLocationError(" + watchId + "," + code
                + ",\"Location error\");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void stopLocationUpdates(int watchId) {
        if (pendingLocationId == watchId) pendingLocationId = -1;
        LocationListener listener = locationListeners.remove(watchId);
        if (listener != null && locationManager != null) {
            try { locationManager.removeUpdates(listener); } catch (SecurityException ignored) {}
        }
    }

    private void stopAllLocationUpdates() {
        for (Integer id : new java.util.ArrayList<>(locationListeners.keySet())) stopLocationUpdates(id);
    }

    private void createBackup(String json) {
        pendingExportJson = json;
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/json");
        create.putExtra(Intent.EXTRA_TITLE, "rep-lift-" + LocalDate.now() + ".json");
        try { startActivityForResult(create, REQUEST_EXPORT); }
        catch (android.content.ActivityNotFoundException e) {
            pendingExportJson = null;
            Toast.makeText(this, "保存先を開けません。", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT) {
            String json = pendingExportJson;
            pendingExportJson = null;
            Uri uri = data == null ? null : data.getData();
            if (resultCode == RESULT_OK && uri != null && json != null) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException("No output stream");
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "バックアップを保存しました。", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, "バックアップを保存できませんでした。", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT) {
            if (pendingFileChooser != null) {
                Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
                pendingFileChooser.onReceiveValue(uri == null ? null : new Uri[]{uri});
                pendingFileChooser = null;
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_LOCATION) return;
        int watchId = pendingLocationId;
        pendingLocationId = -1;
        if (watchId < 0) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates(watchId);
        } else {
            sendLocationError(watchId, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (dragon != null) dragon.onResume();
        refreshSharedHeader();
    }

    @Override protected void onPause() {
        stopAllLocationUpdates();
        if (webView != null) webView.onPause();
        if (dragon != null) dragon.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        pendingLocationId = -1;
        stopAllLocationUpdates();
        if (webView != null) {
            webView.removeJavascriptInterface("WakeGuard");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}