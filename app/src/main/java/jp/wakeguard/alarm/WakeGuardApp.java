package jp.wakeguard.alarm;

import android.app.Application;

public class WakeGuardApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        BackupVault.installAutoBackup(this);
        new Thread(() -> {
            try { Thread.currentThread().setPriority(Thread.MIN_PRIORITY); WorldCityCatalog.preload(getApplicationContext()); }
            catch (Throwable ignored) {}
        }, "IGNIDO-CityIndex").start();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String msg = error == null ? "Unknown" : error.getClass().getSimpleName();
                if (error != null && error.getMessage() != null && !error.getMessage().isEmpty())
                    msg += ": " + error.getMessage();
                if (error != null && error.getStackTrace() != null && error.getStackTrace().length > 0) {
                    StackTraceElement f = error.getStackTrace()[0];
                    msg += " @ " + f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber();
                }
                Prefs.lastFatalError(this, msg);
            } catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
