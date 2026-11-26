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
        onWakeSuccess(c,java.time.LocalDate.now().toEpochDay());
    }

    public static void onWakeSuccess(Context c,long epochDay){
        if(epochDay<=0L||Prefs.wasSuccessful(c,java.time.LocalDate.ofEpochDay(epochDay)))return;
        onActivitySuccess(c,epochDay);
    }

    /** Shared activity grows Ignido at most once per calendar day. */
    public static void onActivitySuccess(Context c,long epochDay){
        ensure(c);
        if(epochDay<=0L)return;
        SharedPreferences sp=p(c);
        long lastGrowthDay=sp.getLong("last_growth_epoch_day",Prefs.lastSuccessEpochDay(c));
        if(epochDay<=lastGrowthDay)return;
        long now=level(c);
        SharedPreferences.Editor edit=sp.edit().putLong("last_growth_epoch_day",epochDay);
        if(now<Long.MAX_VALUE)edit.putLong("level",now+1L);
        edit.apply();
    }

    // Keep form labels, progress, and sprite transitions on one timeline.
    private static final long[] MILESTONES={1L,7L,14L,30L,60L,180L,600L};
    private static final String[] FORM_NAMES={"竜の卵","火種","小さな炎","幼炎竜","天炎竜","恒星炎竜","無限炎竜"};

    static long finalFormLevel(){return MILESTONES[MILESTONES.length-1];}

    /** Form reached at this permanent level, shared by labels and artwork. */
    public static int artStage(long level){
        long lv=Math.max(1L,level);
        for(int i=0;i<MILESTONES.length-1;i++)if(lv<MILESTONES[i+1])return i;
        return MILESTONES.length-1;
    }

    /** Fraction of the transition from the current illustration to the next one. */
    public static float artTransitionProgress(long level){
        long lv=Math.max(1L,level);
        int stage=artStage(lv);
        if(stage>=MILESTONES.length-1)return 0f;
        long start=MILESTONES[stage],end=MILESTONES[stage+1];
        return Math.max(0f,Math.min(1f,(lv-start)/(float)(end-start)));
    }

    // Taller raised-wing poses use more of the portrait than the resting hatchling.
    // Reserve space for this movement while keeping the visible seated dragon growing.
    private static final float[] PORTRAIT_SIZE = {.55f, .59f, .62f, .66f, .84f, .89f, .94f};

    public static float visualScale(long level){
        int stage = artStage(level);
        if (stage < PORTRAIT_SIZE.length - 1) {
            float t = artTransitionProgress(level);
            return PORTRAIT_SIZE[stage] + (PORTRAIT_SIZE[stage + 1] - PORTRAIT_SIZE[stage]) * t;
        }
        double days = (double)level - finalFormLevel();
        return (float)(.94 + .02 * days / (days + 365.0));
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
        return I18n.tr(c,FORM_NAMES[artStage(lv)]);
    }
}
