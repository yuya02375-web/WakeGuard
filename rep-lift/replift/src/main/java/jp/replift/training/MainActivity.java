package jp.replift.training;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private static final String LOCAL_ROOT = "/replift/";
    private static final String INDEX = "https://" + LOCAL_HOST + LOCAL_ROOT + "index.html";
    private static final String SCRIPT_BRIDGE = "RepLiftNative";
    private static final String CONTENT_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; "
            + "base-uri 'none'; form-action 'none'; frame-src 'none'";
    private static final Pattern ASSET_PATH = Pattern.compile("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\\.[a-zA-Z0-9]+");
    private static final int GPS_PERMISSION = 7301;
    private static final int IMPORT_BACKUP = 7302;
    private static final int EXPORT_BACKUP = 7303;
    private static final int MAX_BACKUP_BYTES = 20 * 1024 * 1024;
    private static final ExecutorService FILE_WORKER = Executors.newSingleThreadExecutor();

    private final Set<Integer> requestedWatches = new HashSet<>();
    private final Map<Integer, LocationListener> nativeWatches = new HashMap<>();
    private final AtomicReference<Backup> stagedImport = new AtomicReference<>();
    private LocationManager locations;
    private WakeGuardLink link;
    private WebView webView;
    private boolean running;
    private boolean requestingLocationPermission;
    private byte[] stagedExport;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        locations = getSystemService(LocationManager.class);
        link = new WakeGuardLink(this, progress -> runOnUiThread(() ->
                evaluate("window.__repliftWakeGuardState?.(" + progress + ");")));
        configureWindow();
        configureWebView();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        if (state == null || webView.restoreState(state) == null) webView.loadUrl(INDEX);
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void configureWebView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 19, 16));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets space = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(space.left, space.top, space.right, space.bottom);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(16, 19, 16));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        webView.addJavascriptInterface(new NativeBridge(), SCRIPT_BRIDGE);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.isForMainFrame() || !isIndex(request.getUrl());
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveLocal(request);
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (isIndex(Uri.parse(url))) link.refresh();
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.requestApplyInsets();
    }

    private static boolean trustedUri(Uri uri) {
        return "https".equals(uri.getScheme()) && LOCAL_HOST.equals(uri.getHost())
                && uri.getPort() == -1 && uri.getUserInfo() == null;
    }

    private static boolean isIndex(Uri uri) {
        return trustedUri(uri) && (LOCAL_ROOT + "index.html").equals(uri.getPath());
    }

    private WebResourceResponse serveLocal(WebResourceRequest request) {
        Uri uri = request.getUrl();
        String path = uri.getPath();
        if (!"GET".equals(request.getMethod()) || !trustedUri(uri) || path == null
                || !path.startsWith(LOCAL_ROOT) || (request.isForMainFrame() && !isIndex(uri))) {
            return missingResource();
        }
        String asset = path.substring(LOCAL_ROOT.length());
        Backup backup = stagedImport.get();
        if (backup != null && backup.path.equals(asset) && stagedImport.compareAndSet(backup, null)) {
            return response("application/json", new ByteArrayInputStream(backup.bytes), false);
        }
        if (!ASSET_PATH.matcher(asset).matches()) return missingResource();
        String mime = mimeType(asset);
        if (mime == null) return missingResource();
        try {
            return response(mime, getAssets().open("rep-lift/" + asset), "text/html".equals(mime));
        } catch (IOException e) {
            return missingResource();
        }
    }

    private static WebResourceResponse response(String mime, InputStream stream, boolean html) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Content-Type-Options", "nosniff");
        if (html) headers.put("Content-Security-Policy", CONTENT_POLICY);
        if ("application/json".equals(mime)) headers.put("Cache-Control", "no-store");
        return new WebResourceResponse(mime, mime.startsWith("image/") ? null : "UTF-8",
                200, "OK", headers, stream);
    }

    private static WebResourceResponse missingResource() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private static String mimeType(String asset) {
        return switch (asset.substring(asset.lastIndexOf('.') + 1)) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "svg" -> "image/svg+xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> null;
        };
    }

    private void evaluate(String code) {
        if (webView != null) webView.evaluateJavascript(code, null);
    }

    private final class NativeBridge {
        @JavascriptInterface public void refreshWakeGuard() { link.refresh(); }
        @JavascriptInterface public void setWakeGuardLinked(boolean enabled, String dates) { link.change(enabled, dates); }
        @JavascriptInterface public void recordActivity(String date) { link.record(date); }
        @JavascriptInterface public void restoreActivityDays(String dates) { link.restore(dates); }

        @JavascriptInterface public void requestLocation(int id) {
            runOnUiThread(() -> beginWatch(id));
        }

        @JavascriptInterface public void stopLocation(int id) {
            runOnUiThread(() -> {
                requestedWatches.remove(id);
                stopNativeWatch(id);
            });
        }

        @JavascriptInterface public void keepRunScreenOn(boolean active) {
            runOnUiThread(() -> {
                if (active) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface public void openBackup() {
            runOnUiThread(MainActivity.this::pickBackup);
        }

        @JavascriptInterface public void saveBackup(String json) {
            if (json != null && json.length() <= MAX_BACKUP_BYTES) {
                runOnUiThread(() -> pickBackupDestination(json));
            } else {
                runOnUiThread(() -> toast("バックアップのサイズが上限を超えています。"));
            }
        }
    }

    private boolean preciseLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return preciseLocation() || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void beginWatch(int id) {
        if (id <= 0 || requestedWatches.size() >= 4) return;
        requestedWatches.add(id);
        if (hasLocationPermission()) {
            if (running) startNativeWatch(id);
        } else if (!requestingLocationPermission) {
            requestingLocationPermission = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, GPS_PERMISSION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != GPS_PERMISSION) return;
        requestingLocationPermission = false;
        if (!hasLocationPermission()) {
            for (int id : requestedWatches) locationError(id, 1);
            requestedWatches.clear();
        } else if (running) {
            for (int id : requestedWatches) startNativeWatch(id);
        }
    }

    private void startNativeWatch(int id) {
        if (nativeWatches.containsKey(id)) return;
        if (locations == null) { locationError(id, 2); return; }
        String provider = preciseLocation() ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        LocationListener listener = new LocationListener() {
            private boolean first = true;

            @Override public void onLocationChanged(Location location) {
                if (nativeWatches.get(id) != this || !running) return;
                float accuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
                if (!Float.isFinite(accuracy) || !Double.isFinite(location.getLatitude())
                        || !Double.isFinite(location.getLongitude())) return;
                evaluate("window.__repliftLocation?.(" + id + "," + location.getLatitude() + ","
                        + location.getLongitude() + "," + accuracy + "," + location.getTime() + "," + first + ");");
                first = false;
            }

            @Override public void onProviderDisabled(String provider) {
                if (nativeWatches.get(id) == this) locationError(id, 2);
                first = true;
            }

            @Override public void onProviderEnabled(String provider) { first = true; }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {
                // Android 10's LocationListener requires this callback.
            }
        };
        nativeWatches.put(id, listener);
        try {
            locations.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
            if (!locations.isProviderEnabled(provider)) locationError(id, 2);
        } catch (SecurityException | IllegalArgumentException e) {
            stopNativeWatch(id);
            locationError(id, 2);
        }
    }

    private void locationError(int id, int code) {
        evaluate("window.__repliftLocationError?.(" + id + "," + code + ");");
    }

    private void stopNativeWatch(int id) {
        LocationListener listener = nativeWatches.remove(id);
        if (listener != null && locations != null) {
            try { locations.removeUpdates(listener); }
            catch (SecurityException ignored) { /* Permission changed; no update can be delivered. */ }
        }
    }

    private void stopNativeWatches() {
        for (int id : new ArrayList<>(nativeWatches.keySet())) stopNativeWatch(id);
    }

    private void pickBackup() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("application/json");
        try { startActivityForResult(pick, IMPORT_BACKUP); }
        catch (ActivityNotFoundException e) { toast("ファイルアプリを開けません。"); }
    }

    private void pickBackupDestination(String json) {
        if (stagedExport != null) return;
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BACKUP_BYTES) { toast("バックアップのサイズが上限を超えています。"); return; }
        stagedExport = bytes;
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/json");
        create.putExtra(Intent.EXTRA_TITLE, "rep-lift-" + LocalDate.now() + ".json");
        try { startActivityForResult(create, EXPORT_BACKUP); }
        catch (ActivityNotFoundException e) {
            stagedExport = null;
            toast("保存先を開けません。");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (requestCode == EXPORT_BACKUP) {
            byte[] bytes = stagedExport;
            stagedExport = null;
            if (uri != null && bytes != null) FILE_WORKER.execute(() -> writeBackup(uri, bytes));
        } else if (requestCode == IMPORT_BACKUP && uri != null) {
            FILE_WORKER.execute(() -> readBackup(uri));
        }
    }

    private void readBackup(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("No input stream");
            byte[] block = new byte[8192];
            int read;
            while ((read = input.read(block)) != -1) {
                if (buffer.size() + read > MAX_BACKUP_BYTES) throw new IOException("Backup exceeds limit");
                buffer.write(block, 0, read);
            }
            Backup backup = new Backup("import-" + UUID.randomUUID() + ".json", buffer.toByteArray());
            runOnUiThread(() -> {
                if (webView == null) return;
                stagedImport.set(backup);
                evaluate("window.__repliftImportBackup?.('" + backup.path + "');");
            });
        } catch (IOException | SecurityException e) {
            runOnUiThread(() -> toast("バックアップファイルを読み込めませんでした。"));
        }
    }

    private void writeBackup(Uri uri, byte[] bytes) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("No output stream");
            output.write(bytes);
        } catch (IOException | SecurityException e) {
            runOnUiThread(() -> toast("バックアップを保存できませんでした。"));
            return;
        }
        runOnUiThread(() -> toast("バックアップを保存しました。"));
    }

    private void toast(String message) {
        if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        if (webView != null) webView.onResume();
        if (hasLocationPermission()) for (int id : requestedWatches) startNativeWatch(id);
        link.refresh();
    }

    @Override protected void onPause() {
        running = false;
        stopNativeWatches();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        requestedWatches.clear();
        stopNativeWatches();
        stagedImport.set(null);
        if (webView != null) {
            webView.removeJavascriptInterface(SCRIPT_BRIDGE);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() { handleBack(); }

    private void handleBack() {
        if (webView == null) { finish(); return; }
        webView.evaluateJavascript("window.__repliftCloseDialog?.() ?? false", closed -> {
            if (webView == null || "true".equals(closed)) return;
            if (webView.canGoBack()) webView.goBack();
            else finish();
        });
    }

    private static final class Backup {
        private final String path;
        private final byte[] bytes;

        private Backup(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }
}
