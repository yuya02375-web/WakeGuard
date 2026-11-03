package jp.wakeguard.alarm;

import android.content.*;
import android.os.SystemClock;
import java.util.*;

public class WidgetActionReceiver extends BroadcastReceiver {
    public static final String TIMER_PRESET="jp.wakeguard.widget.TIMER_PRESET";
    public static final String TIMER_TOGGLE="jp.wakeguard.widget.TIMER_TOGGLE";
    public static final String TIMER_PLUS="jp.wakeguard.widget.TIMER_PLUS";
    public static final String SW_TOGGLE="jp.wakeguard.widget.SW_TOGGLE";
    public static final String SW_LAP_RESET="jp.wakeguard.widget.SW_LAP_RESET";
    @Override public void onReceive(Context c,Intent i){if(i==null)return;String a=i.getAction();long v=i.getLongExtra("value",0L);
        try{
            if(TIMER_PRESET.equals(a)){long dur=Math.max(1000L,v);TimerStore.Entry e=TimerStore.addTemporary(c,"",dur);e.remainingMs=dur;e.endMs=System.currentTimeMillis()+dur;e.running=true;TimerStore.update(c,e);TimerReceiver.schedule(c,e.id,e.endMs);}
            else if(TIMER_TOGGLE.equals(a)){TimerStore.Entry e=TimerStore.find(c,v);if(e!=null){if(e.running){e.remainingMs=e.remaining();e.running=false;e.endMs=0;TimerStore.update(c,e);TimerReceiver.cancel(c,e.id);}else{long rem=e.remainingMs>0?e.remainingMs:e.durationMs;e.remainingMs=rem;e.endMs=System.currentTimeMillis()+rem;e.running=true;TimerStore.update(c,e);TimerReceiver.schedule(c,e.id,e.endMs);}}}
            else if(TIMER_PLUS.equals(a)){TimerStore.Entry e=TimerStore.find(c,v);if(e!=null){e.durationMs+=60000L;if(e.running){e.endMs+=60000L;e.remainingMs=e.remaining();TimerReceiver.schedule(c,e.id,e.endMs);}else e.remainingMs=Math.max(0,e.remainingMs)+60000L;TimerStore.update(c,e);}}
            else if(SW_TOGGLE.equals(a)){toggleStopwatch(c);syncStopwatchNotification(c);}
            else if(SW_LAP_RESET.equals(a))lapOrReset(c);
        }catch(Throwable ignored){}
        WidgetSuite.refreshAll(c);
    }
    private static android.content.SharedPreferences p(Context c){return c.getSharedPreferences("clock_tools",Context.MODE_PRIVATE);}
    private static long elapsed(Context c){android.content.SharedPreferences s=p(c);long saved=s.getLong("sw_elapsed",0);if(!s.getBoolean("sw_running",false))return saved;long base=s.getLong("sw_base",SystemClock.elapsedRealtime()),now=SystemClock.elapsedRealtime();return saved+Math.max(0,now-base);}
    private static void toggleStopwatch(Context c){android.content.SharedPreferences s=p(c);if(s.getBoolean("sw_running",false)){s.edit().putBoolean("sw_running",false).putLong("sw_elapsed",elapsed(c)).putLong("sw_base",0).apply();}else{s.edit().putBoolean("sw_running",true).putLong("sw_base",SystemClock.elapsedRealtime()).apply();}}
    private static void lapOrReset(Context c){android.content.SharedPreferences s=p(c);if(s.getBoolean("sw_running",false)){String raw=s.getString("sw_laps","");String x=String.valueOf(elapsed(c));s.edit().putString("sw_laps",raw==null||raw.isEmpty()?x:raw+","+x).apply();}else{s.edit().putLong("sw_elapsed",0).putLong("sw_base",0).remove("sw_laps").apply();StopwatchNotification.cancel(c);}}
    private static void syncStopwatchNotification(Context c){if(p(c).getBoolean("sw_running",false))StopwatchNotification.show(c,elapsed(c));else StopwatchNotification.cancel(c);}
}
