package jp.wakeguard.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "jp.wakeguard.alarm.FIRE";
    public static final String ACTION_RECOVER = "jp.wakeguard.alarm.RECOVER";
    public static final String ACTION_BACKUP_FIRE = "jp.wakeguard.alarm.BACKUP_FIRE";

    private void startAlarmService(Context context, Intent service) {
        try {
            context.startForegroundService(service);
        } catch (RuntimeException failure) {
            Prefs.lastAlarmError(context, "ReceiverStart: " + failure.getClass().getSimpleName());
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_RECOVER.equals(action)) {
            if (Prefs.active(context)) {
                startAlarmService(context,new Intent(context,AlarmService.class).setAction(AlarmService.ACTION_RESTORE));
            }
            return;
        }

        // Both the backup broadcast and the legacy direct broadcast identify
        // an individual occurrence. Validate it before starting or queueing it.
        long epochDay = intent.getLongExtra(AlarmService.EXTRA_EPOCH_DAY, -1L);
        long alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, AlarmScheduler.PRIMARY_ALARM_ID);
        long expectedAt = intent.getLongExtra(AlarmScheduler.EXTRA_EXPECTED_AT, -1L);
        if (!AlarmScheduler.isExpectedFire(context, alarmId, epochDay, expectedAt)
                || AlarmOccurrenceGuard.wasDelivered(context, alarmId, expectedAt, epochDay)) return;
        Intent service = new Intent(context,AlarmService.class)
                .setAction(AlarmService.ACTION_FIRE_NEW)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY,epochDay)
                .putExtra(AlarmService.EXTRA_ALARM_ID,alarmId)
                .putExtra(AlarmService.EXTRA_EXPECTED_AT,expectedAt)
                .putExtra(AlarmService.EXTRA_REGULAR_OCCURRENCE,true);
        startAlarmService(context,service);
    }
}
