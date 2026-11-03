package jp.wakeguard.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "jp.wakeguard.alarm.FIRE";
    public static final String ACTION_RECOVER = "jp.wakeguard.alarm.RECOVER";
    public static final String ACTION_BACKUP_FIRE = "jp.wakeguard.alarm.BACKUP_FIRE";

    private void startAlarmService(Context context, Intent s) {
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s);
            else context.startService(s);
        } catch (Throwable t) {
            try {
                Prefs.lastAlarmError(context, "ReceiverStart: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            } catch (Throwable ignored) {}
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_BACKUP_FIRE.equals(action)) {
            long epochDay = intent == null ? -1L : intent.getLongExtra("epochDay", -1L);
            long alarmId = intent == null ? AlarmScheduler.PRIMARY_ALARM_ID : intent.getLongExtra("alarmId", AlarmScheduler.PRIMARY_ALARM_ID);
            long expectedAt = intent == null ? -1L : intent.getLongExtra(AlarmScheduler.EXTRA_EXPECTED_AT, -1L);
            if (!AlarmScheduler.isExpectedFire(context, alarmId, epochDay, expectedAt)
                    || AlarmOccurrenceGuard.wasDelivered(context, alarmId, expectedAt, epochDay)) return;
            try { AlarmStore.markFiredIfOneShot(context, alarmId); } catch (Throwable ignored) {}
            Intent s;
            // A backup trigger represents this concrete occurrence. Never turn it into a
            // generic RESTORE based only on a stale alarm_active flag left by a killed task.
            // AlarmService itself queues a genuinely different alarm if another one is ringing.
            s = new Intent(context, AlarmService.class)
                    .setAction(AlarmService.ACTION_FIRE_NEW)
                    .putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay)
                    .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmService.EXTRA_EXPECTED_AT, expectedAt);
            startAlarmService(context, s);
            return;
        }

        if (ACTION_RECOVER.equals(action)) {
            if (!Prefs.active(context)) return;
            Intent s = new Intent(context, AlarmService.class).setAction(AlarmService.ACTION_RESTORE);
            startAlarmService(context, s);
            return;
        }

        // Legacy broadcast trigger kept for upgrades from older scheduled alarms, but never
        // trust the old PendingIntent blindly. It must still match the current alarm settings.
        long epochDay = intent == null ? -1L : intent.getLongExtra("epochDay", -1L);
        long alarmId = intent == null ? AlarmScheduler.PRIMARY_ALARM_ID : intent.getLongExtra("alarmId", AlarmScheduler.PRIMARY_ALARM_ID);
        long expectedAt = intent == null ? -1L : intent.getLongExtra(AlarmScheduler.EXTRA_EXPECTED_AT, -1L);
        if (!AlarmScheduler.isExpectedFire(context, alarmId, epochDay, expectedAt)
                || AlarmOccurrenceGuard.wasDelivered(context, alarmId, expectedAt, epochDay)) return;
        try { AlarmStore.markFiredIfOneShot(context, alarmId); } catch (Throwable ignored) {}
        try { AlarmScheduler.reschedule(context); } catch (Throwable ignored) {}
        Intent s = new Intent(context, AlarmService.class)
                .setAction(AlarmService.ACTION_FIRE_NEW)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY, epochDay)
                .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmService.EXTRA_EXPECTED_AT, expectedAt);
        startAlarmService(context, s);
    }
}
