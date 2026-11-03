package jp.wakeguard.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmScheduler.reschedule(context);
        AlarmScheduler.scheduleRecoveryIfActive(context);

        String action=intent==null?null:intent.getAction();
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)){
            restoreRunningTimers(context);
            SnoozeScheduler.restoreAll(context);
        }
    }

    private static void restoreRunningTimers(Context context){
        for(TimerStore.Entry e:TimerStore.all(context)){
            if(e==null||!e.running)continue;
            long remaining=e.remaining();
            if(remaining<=1500L){
                TimerReceiver.complete(context,e.id);
            }else{
                TimerReceiver.schedule(context,e.id,e.endMs);
            }
        }
    }
}
