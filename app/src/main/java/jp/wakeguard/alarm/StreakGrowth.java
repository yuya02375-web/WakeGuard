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

    private static final long[] MILESTONES={1L,4L,12L,30L,80L,180L,400L,1000L};

    /** The artwork tier advances from egg to ember, flame, hatchling, then adult. */
    public static int artStage(long level){
        long lv=Math.max(1L,level);
        if(lv<4L)return 0;
        if(lv<12L)return 1;
        if(lv<30L)return 2;
        if(lv<80L)return 3;
        return 4;
    }

    /** Scales continuously with permanent wake-ups, including after the adult form appears. */
    public static float visualScale(long level){
        double progress=Math.log(Math.max(1L,level))/Math.log(1000.0);
        return (float)(0.78+0.46*Math.max(0.0,Math.min(1.0,progress)));
    }

    public static long nextMilestone(long level){
        long lv=Math.max(1L,level);
        for(long milestone:MILESTONES)if(milestone>lv)return milestone;
        return 0L;
    }

    public static long remainingToNext(long level){
        long next=nextMilestone(level);
        return next==0L?0L:next-Math.max(1L,level);
    }

    /** Progress through the current permanent-growth milestone, from 0.0 to 1.0. */
    public static float progressToNext(long level){
        long lv=Math.max(1L,level),next=nextMilestone(lv);
        if(next==0L)return 1f;
        long start=1L;
        for(long milestone:MILESTONES){
            if(milestone>lv)break;
            start=milestone;
        }
        return Math.max(0f,Math.min(1f,(lv-start)/(float)(next-start)));
    }

    /** Appearance is permanent: a broken streak never shrinks the dragon. */
    public static long appearanceLevel(long permanentLevel,int currentStreak){
        return Math.max(1L,permanentLevel);
    }

    public static String appearanceDescriptor(Context c,long permanentLevel,int currentStreak){
        return descriptor(c,permanentLevel);
    }

    public static String growthDescriptor(Context c){return descriptor(c,level(c));}

    public static String growthDescriptor(Context c,long level){return descriptor(c,level);}

    private static String descriptor(Context c,long level){
        long lv=Math.max(1L,level);
        if(lv<4L)return I18n.tr(c,"竜の卵");
        if(lv<12L)return I18n.tr(c,"火種");
        if(lv<30L)return I18n.tr(c,"小さな炎");
        if(lv<80L)return I18n.tr(c,"幼炎竜");
        if(lv<180L)return I18n.tr(c,"炎竜");
        if(lv<400L)return I18n.tr(c,"天炎竜");
        if(lv<1000L)return I18n.tr(c,"恒星炎竜");
        return I18n.tr(c,"無限炎竜");
    }
}
