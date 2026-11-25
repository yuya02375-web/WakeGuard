package jp.wakeguard.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.Instant;
import java.time.ZoneId;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID,-1L);
        long at = intent.getLongExtra(AlarmService.EXTRA_EXPECTED_AT,-1L);
        if (id < 0 || !SnoozeScheduler.isExpectedDelivery(context,id,at)) return;
        if (id >= 1000 && AlarmStore.find(context,id) == null) {
            SnoozeScheduler.clearDelivered(context,id,at);
            return;
        }

        long originDay = SnoozeScheduler.occurrenceEpochDay(context,id);
        if (originDay < 0) {
            originDay = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        }
        Intent service = new Intent(context,AlarmService.class)
                .setAction(AlarmService.ACTION_FIRE_NEW)
                .putExtra(AlarmService.EXTRA_ALARM_ID,id)
                .putExtra(AlarmService.EXTRA_EPOCH_DAY,originDay)
                .putExtra(AlarmService.EXTRA_EXPECTED_AT,at);
        try {
            context.startForegroundService(service);
            SnoozeScheduler.clearDelivered(context,id,at);
        } catch (RuntimeException failure) {
            Prefs.lastAlarmError(context,"SnoozeStart: " + failure.getClass().getSimpleName());
        }
    }
}
