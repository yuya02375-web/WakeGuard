package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Small, non-gamey safety net for an established wake-up streak. */
public final class StreakProtection {
    private static final String FILE="wakeguard_streak_protection_v1";
    public static final int MAX_DAYS=3;
    public static final int MONTHLY_DAYS=2;
    private StreakProtection() {}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}

    public static synchronized void ensure(Context c){
        SharedPreferences sp=p(c);
        if(!sp.getBoolean("init",false)){
            sp.edit()
                .putBoolean("init",true)
                .putInt("balance",0)
                .putBoolean("auto",true)
                .putString("month",YearMonth.now().toString())
                .putLong("start_epoch_day",LocalDate.now().toEpochDay())
                .putInt("bonus_milestone",0)
                .commit();
        }
        if(!sp.getBoolean("v176_initial_balance_fixed",false)){
            boolean pristine=sp.getInt("balance",0)==MONTHLY_DAYS
                    && sp.getInt("bonus_milestone",0)==0
                    && sp.getStringSet("protected_days",new HashSet<>()).isEmpty();
            YearMonth started=YearMonth.now();
            try{started=YearMonth.from(LocalDate.ofEpochDay(sp.getLong("start_epoch_day",LocalDate.now().toEpochDay())));}catch(Throwable ignored){}
            if(pristine && started.equals(YearMonth.now())) sp.edit().putInt("balance",0).putBoolean("v176_initial_balance_fixed",true).commit();
            else sp.edit().putBoolean("v176_initial_balance_fixed",true).commit();
        }
        refreshMonthlyLocked(c);
    }

    private static void refreshMonthlyLocked(Context c){
        SharedPreferences sp=p(c);
        YearMonth now=YearMonth.now();
        YearMonth old=now;
        try{old=YearMonth.parse(sp.getString("month",now.toString()));}catch(Throwable ignored){}
        long months=ChronoUnit.MONTHS.between(old,now);
        if(months>0){
            long grant=months*(long)MONTHLY_DAYS;
            int next=(int)Math.min(MAX_DAYS,Math.max(0L,sp.getInt("balance",0))+grant);
            sp.edit().putInt("balance",next).putString("month",now.toString()).commit();
        }else if(months<0){
            sp.edit().putString("month",now.toString()).commit();
        }
    }

    public static synchronized int available(Context c){ensure(c);return Math.max(0,Math.min(MAX_DAYS,p(c).getInt("balance",0)));}
    public static synchronized boolean autoEnabled(Context c){ensure(c);return p(c).getBoolean("auto",true);}
    public static synchronized void setAutoEnabled(Context c,boolean enabled){ensure(c);p(c).edit().putBoolean("auto",enabled).apply();}
    public static synchronized long startEpochDay(Context c){ensure(c);return p(c).getLong("start_epoch_day",LocalDate.now().toEpochDay());}

    public static synchronized Set<String> protectedDays(Context c){
        ensure(c);
        return new HashSet<>(p(c).getStringSet("protected_days",new HashSet<>()));
    }

    public static synchronized boolean isProtected(Context c,LocalDate day){return protectedDays(c).contains(day.toString());}

    /** All-or-nothing protection so several missed days never waste a partial balance if the streak cannot be saved. */
    public static synchronized boolean protectDaysIfPossible(Context c,Collection<LocalDate> requested){
        ensure(c);
        Set<String> saved=new HashSet<>(p(c).getStringSet("protected_days",new HashSet<>()));
        ArrayList<LocalDate> needed=new ArrayList<>();
        long start=startEpochDay(c);
        for(LocalDate day:requested){
            if(day==null||Prefs.wasSuccessful(c,day)||saved.contains(day.toString()))continue;
            if(day.toEpochDay()<start)return false; // Never retroactively protect days from before this feature existed.
            needed.add(day);
        }
        if(needed.isEmpty())return true;
        if(!p(c).getBoolean("auto",true))return false;
        refreshMonthlyLocked(c);
        int balance=Math.max(0,Math.min(MAX_DAYS,p(c).getInt("balance",0)));
        if(balance<needed.size())return false;
        for(LocalDate day:needed)saved.add(day.toString());
        return p(c).edit().putStringSet("protected_days",saved).putInt("balance",balance-needed.size()).commit();
    }

    public static synchronized boolean protectDayIfPossible(Context c,LocalDate day){
        ArrayList<LocalDate> one=new ArrayList<>();one.add(day);return protectDaysIfPossible(c,one);
    }

    /** Reward consistency without adding a game economy: every 30 actual streak successes adds one protection day. */
    public static synchronized void onSuccessfulWake(Context c,int newStreak){
        ensure(c);
        if(newStreak<30||newStreak%30!=0)return;
        int milestone=newStreak/30;
        SharedPreferences sp=p(c);
        int awarded=sp.getInt("bonus_milestone",0);
        if(milestone<=awarded)return;
        int next=Math.min(MAX_DAYS,sp.getInt("balance",0)+1);
        sp.edit().putInt("balance",next).putInt("bonus_milestone",milestone).apply();
    }
}
