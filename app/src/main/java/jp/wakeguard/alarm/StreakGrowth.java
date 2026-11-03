package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;

/** One permanent companion that grows one step per successful real wake-up. No gacha, rarity, currency or inventory. */
public final class StreakGrowth {
    private static final String FILE="wakeguard_streak_growth_v1";
    private StreakGrowth() {}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}

    public static void ensure(Context c){
        SharedPreferences sp=p(c);
        if(sp.getBoolean("init",false))return;
        long migrated=1L;
        try{
            SharedPreferences old=c.getSharedPreferences("wakeguard_streak_game_v1",Context.MODE_PRIVATE);
            migrated=Math.max(migrated,(long)old.getInt("cl_starter",1));
        }catch(Throwable ignored){}
        try{migrated=Math.max(migrated,(long)Prefs.totalWakeups(c)+1L);}catch(Throwable ignored){}
        sp.edit().putBoolean("init",true).putLong("level",migrated).apply();
    }

    public static long level(Context c){ensure(c);return Math.max(1L,p(c).getLong("level",1L));}

    public static void onWakeSuccess(Context c){
        ensure(c);
        long now=level(c);
        if(now<Long.MAX_VALUE)p(c).edit().putLong("level",now+1L).apply();
    }

    /** Smooth visual power. It never reaches a hard cap; logarithmic growth keeps very high levels renderable. */
    public static double visualPower(long level){return Math.log1p(Math.max(1L,level))/Math.log(2.0);}

    /**
     * Appearance follows the current consecutive-day streak, without deleting the
     * separately stored permanent experience level. A 3-day streak never shows
     * an adult dragon, even when older imported stats contain many wake-ups.
     */
    public static long appearanceLevel(long permanentLevel,int currentStreak){
        return Math.min(Math.max(1L,permanentLevel),Math.max(1L,(long)currentStreak));
    }

    public static String appearanceDescriptor(Context c,long permanentLevel,int currentStreak){
        long lv=appearanceLevel(permanentLevel,currentStreak);
        if(lv<4L)return I18n.tr(c,"火種");
        if(lv<12L)return I18n.tr(c,"小さな炎");
        if(lv<30L)return I18n.tr(c,"炎竜の兆し");
        if(lv<80L)return I18n.tr(c,"幼炎竜");
        if(lv<180L)return I18n.tr(c,"炎竜");
        if(lv<400L)return I18n.tr(c,"天炎竜");
        if(lv<1000L)return I18n.tr(c,"恒星炎竜");
        return I18n.tr(c,"無限炎竜");
    }

    /** A compact, non-gamey description of how far the permanent form has grown. */
    public static String growthDescriptor(Context c){
        long lv=level(c);
        if(lv<4)return I18n.tr(c,"火種");
        if(lv<12)return I18n.tr(c,"小さな炎");
        if(lv<30)return I18n.tr(c,"炎竜の兆し");
        if(lv<80)return I18n.tr(c,"幼炎竜");
        if(lv<180)return I18n.tr(c,"炎竜");
        if(lv<400)return I18n.tr(c,"天炎竜");
        if(lv<1000)return I18n.tr(c,"恒星炎竜");
        return I18n.tr(c,"無限炎竜");
    }
}
