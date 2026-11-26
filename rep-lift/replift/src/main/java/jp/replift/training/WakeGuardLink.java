package jp.replift.training;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

final class WakeGuardLink {
    private static final String WAKEGUARD_PACKAGE = "jp.wakeguard.alarm";
    private static final String AUTHORITY = WAKEGUARD_PACKAGE + ".replift";
    private static final Uri ENDPOINT = Uri.parse("content://" + AUTHORITY);
    private static final String ENABLED = "enabled";
    private static final String PENDING_RESTORE = "pending_restore";
    private static final String PENDING_TODAY = "pending_today";
    private static final int RESTORE_BATCH = 512;
    private static final int MAX_DATE_JSON_LENGTH = 2_000_000;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final Context app;
    private final SharedPreferences prefs;
    private final Consumer<JSONObject> onState;

    WakeGuardLink(Context context, Consumer<JSONObject> onState) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences("wakeguard_link", Context.MODE_PRIVATE);
        this.onState = onState;
    }

    void refresh() {
        WORKER.execute(this::publish);
    }

    void change(boolean enabled, String datesJson) {
        WORKER.execute(() -> {
            boolean wasEnabled = isEnabled();
            SharedPreferences.Editor edit = prefs.edit().putBoolean(ENABLED, enabled);
            if (enabled) {
                edit.apply();
                queueDates(datesJson);
            } else {
                edit.remove(PENDING_RESTORE).remove(PENDING_TODAY).apply();
                if (wasEnabled) unlink();
            }
            publish();
        });
    }

    void restore(String datesJson) {
        WORKER.execute(() -> {
            if (!isEnabled()) return;
            queueDates(datesJson);
            publish();
        });
    }

    void record(String day) {
        WORKER.execute(() -> {
            if (!isEnabled() || !LocalDate.now().toString().equals(day)) return;
            String prior = prefs.getString(PENDING_TODAY, null);
            if (prior != null && !day.equals(prior)) queueDate(prior);
            prefs.edit().putString(PENDING_TODAY, day).apply();
            publish();
        });
    }

    private boolean isEnabled() {
        return prefs.getBoolean(ENABLED, false);
    }

    private String availability() {
        PackageManager packages = app.getPackageManager();
        ProviderInfo provider = packages.resolveContentProvider(AUTHORITY, 0);
        if (provider != null) {
            boolean signed = packages.checkSignatures(app.getPackageName(), WAKEGUARD_PACKAGE)
                    == PackageManager.SIGNATURE_MATCH;
            return WAKEGUARD_PACKAGE.equals(provider.packageName) && provider.exported && signed
                    ? "connected" : "incompatible";
        }
        try {
            packages.getPackageInfo(WAKEGUARD_PACKAGE, 0);
            return "incompatible";
        } catch (PackageManager.NameNotFoundException e) {
            return "missing";
        }
    }

    private void publish() {
        boolean enabled = isEnabled();
        String status = availability();
        Bundle progress = null;
        if ("connected".equals(status)) {
            if (enabled) {
                try {
                    progress = sync();
                } catch (SecurityException e) {
                    status = "incompatible";
                } catch (RuntimeException e) {
                    status = "unavailable";
                }
            } else {
                status = "disconnected";
            }
        }
        onState.accept(state(enabled, status, progress));
    }

    private void unlink() {
        if (!"connected".equals(availability())) return;
        try {
            app.getContentResolver().call(ENDPOINT, "unlink", null, null);
        } catch (RuntimeException ignored) {
            // Older providers may reject unlink. Sharing stays disabled locally if the call fails.
        }
    }

    private Bundle sync() {
        String today = LocalDate.now().toString();
        String pending = prefs.getString(PENDING_TODAY, null);
        Bundle progress = null;
        if (pending != null) {
            if (today.equals(pending)) {
                Bundle date = new Bundle();
                date.putString("day", pending);
                progress = call("record", date);
            } else {
                queueDate(pending);
            }
            prefs.edit().remove(PENDING_TODAY).apply();
        }
        TreeSet<String> dates = pendingDates();
        while (!dates.isEmpty()) {
            ArrayList<String> batch = new ArrayList<>(RESTORE_BATCH);
            for (String day : dates) {
                if (batch.size() == RESTORE_BATCH) break;
                batch.add(day);
            }
            Bundle extras = new Bundle();
            extras.putStringArrayList("days", batch);
            progress = call("restore", extras);
            dates.removeAll(batch);
            persistDates(dates);
        }
        return progress == null ? call("progress", null) : progress;
    }

    private Bundle call(String method, Bundle extras) {
        Bundle result = app.getContentResolver().call(ENDPOINT, method, null, extras);
        if (result == null || !result.containsKey("level")) {
            throw new IllegalStateException("Missing WakeGuard progress");
        }
        return result;
    }

    private TreeSet<String> pendingDates() {
        return new TreeSet<>(prefs.getStringSet(PENDING_RESTORE, Collections.emptySet()));
    }

    private void persistDates(Set<String> dates) {
        prefs.edit().putStringSet(PENDING_RESTORE, new TreeSet<>(dates)).apply();
    }

    private void queueDate(String day) {
        TreeSet<String> dates = pendingDates();
        if (dates.add(day)) persistDates(dates);
    }

    private void queueDates(String json) {
        if (json == null || json.length() > MAX_DATE_JSON_LENGTH) return;
        try {
            JSONArray incoming = new JSONArray(json);
            TreeSet<String> dates = pendingDates();
            LocalDate today = LocalDate.now();
            for (int index = 0; index < incoming.length(); index += 1) {
                String day = incoming.optString(index);
                if (day.length() != 10) continue;
                try {
                    LocalDate parsed = LocalDate.parse(day);
                    if (!parsed.isAfter(today) && parsed.toString().equals(day)) dates.add(day);
                } catch (DateTimeParseException ignored) {
                    // Discard unparseable dates; continue restoring the remaining history.
                }
            }
            persistDates(dates);
        } catch (JSONException ignored) {
            // The app can still connect when a backup contains no valid date array.
        }
    }

    private static JSONObject state(boolean enabled, String status, Bundle progress) {
        JSONObject state = new JSONObject();
        try {
            state.put("enabled", enabled);
            state.put("available", "connected".equals(status) || "disconnected".equals(status));
            state.put("status", status);
            state.put("current", progress == null ? 0 : progress.getInt("current"));
            state.put("best", progress == null ? 0 : progress.getInt("best"));
            state.put("currentStart", progress == null ? "" : progress.getString("currentStart", ""));
            state.put("currentEnd", progress == null ? "" : progress.getString("currentEnd", ""));
            state.put("bestStart", progress == null ? "" : progress.getString("bestStart", ""));
            state.put("bestEnd", progress == null ? "" : progress.getString("bestEnd", ""));
            state.put("level", progress == null ? 0 : progress.getLong("level"));
            state.put("form", progress == null ? "" : progress.getString("form", ""));
            state.put("remaining", progress == null ? 0 : progress.getLong("remaining"));
            state.put("stage", progress == null ? 0 : progress.getInt("stage"));
            state.put("updatedAt", progress == null ? JSONObject.NULL : System.currentTimeMillis());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return state;
    }
}
