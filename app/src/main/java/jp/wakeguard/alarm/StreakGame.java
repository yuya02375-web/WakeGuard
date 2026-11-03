package jp.wakeguard.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.*;

/** Free, offline-first companion progression for successful wake-ups. No paid currency. */
public final class StreakGame {
    private static final String FILE="wakeguard_streak_game_v1";
    private static final SecureRandom RNG=new SecureRandom();
    private StreakGame(){}

    public static final class CharacterDef {
        public final String id,name,rarity; public final int theme,shape;
        CharacterDef(String id,String name,String rarity,int theme,int shape){this.id=id;this.name=name;this.rarity=rarity;this.theme=theme;this.shape=shape;}
        public boolean starter(){return "starter".equals(id);} public int tier(){return rarityTier(rarity);}
    }
    public static final class DecorDef {
        public final String id,name,kind; public final int tier;
        DecorDef(String id,String name,String kind,int tier){this.id=id;this.name=name;this.kind=kind;this.tier=tier;}
    }
    public static final class GachaResult {
        public final CharacterDef character; public final boolean duplicate; public final int essence;
        GachaResult(CharacterDef c,boolean d,int e){character=c;duplicate=d;essence=e;}
    }

    private static final ArrayList<CharacterDef> CHARS=new ArrayList<>();
    private static final ArrayList<DecorDef> DECORS=new ArrayList<>();
    static {
        CHARS.add(new CharacterDef("starter","EMBERKIN","",0,7));
        addChars("N",new String[]{
            "Mochi Puff","Pebble Pup","Leaflet","Puddlefin","Tin Beetle","Cloudlet","Acorn Cub","Breeze Bat","Moss Bun","Dewdrop","Chirpkin","Candle Mouse",
            "Berry Blob","Snow Peep","Copper Crab","Dust Fox","Bubble Toad","Noodle Serpent","Sproutling","Pocket Owl","Cocoa Mole","Ripple Cat","Button Ram","Lemon Gecko"
        },0);
        addChars("R",new String[]{
            "Comet Hare","Volt Ferret","Frost Lynx","Coral Drake","Maple Tanuki","Echo Raven","Quartz Turtle","Storm Pup","Bloom Mantis","Night Moth",
            "Amber Jackal","Glacier Seal","Neon Koi","Cinder Boar","Sage Alpaca","Orbit Lemur","Rune Badger","Marble Cobra","Solar Finch","Tide Otter"
        },3);
        addChars("SR",new String[]{
            "Astra Wolf","Pyro Griffin","Luna Kirin","Tempest Roc","Crystal Stag","Nova Panther","Lotus Naga","Ember Samurai","Aurora Fox","Thunder Oni",
            "Mirage Manta","Verdant Golem","Frost Wyvern","Celestial Crane","Magma Rex","Abyss Tiger"
        },6);
        addChars("SSR",new String[]{
            "Solaris Dragon","Nocturne Dragon","Raijin Beast","Phoenix Crown","Leviathan Blue","Fenrir Prime","Seraph Lion","Titan Hydra","Eclipse Kitsune","Astral Behemoth","Chrono Sphinx","Inferno Garuda"
        },9);
        addChars("UR",new String[]{
            "Aetherion","Void Emperor","Genesis Dragon","Omega Phoenix","Cosmic Leviathan","Eternal Kirin","World Serpent","Dawn Sovereign"
        },12);

        String[][] ds={
            {"sprout_pin","Sprout Pin","pin"},{"star_clip","Star Clip","pin"},{"moon_charm","Moon Charm","charm"},{"ember_scarf","Ember Scarf","scarf"},
            {"cloud_hat","Cloud Hat","hat"},{"tiny_crown","Tiny Crown","crown"},{"blue_halo","Blue Halo","halo"},{"leaf_cape","Leaf Cape","cape"},
            {"spark_aura","Spark Aura","aura"},{"crystal_badge","Crystal Badge","pin"},{"fox_mask","Fox Mask","mask"},{"comet_tail","Comet Tail","tail"},
            {"silver_crown","Silver Crown","crown"},{"frost_wings","Frost Wings","wings"},{"rose_aura","Rose Aura","aura"},{"night_frame","Night Frame","frame"},
            {"storm_halo","Storm Halo","halo"},{"rune_scarf","Rune Scarf","scarf"},{"golden_bell","Golden Bell","charm"},{"meteor_cape","Meteor Cape","cape"},
            {"dragon_horns","Dragon Horns","horns"},{"blaze_wings","Blaze Wings","wings"},{"aurora_frame","Aurora Frame","frame"},{"royal_crown","Royal Crown","crown"},
            {"nebula_aura","Nebula Aura","aura"},{"chrono_halo","Chrono Halo","halo"},{"titan_armor","Titan Armor","armor"},{"prism_tail","Prism Tail","tail"},
            {"solar_wings","Solar Wings","wings"},{"lunar_wings","Lunar Wings","wings"},{"eclipse_crown","Eclipse Crown","crown"},{"galaxy_frame","Galaxy Frame","frame"},
            {"phoenix_aura","Phoenix Aura","aura"},{"hydra_horns","Hydra Horns","horns"},{"king_scarf","King Scarf","scarf"},{"mythic_badge","Mythic Badge","pin"},
            {"dragon_halo","Dragon Halo","halo"},{"world_wings","World Wings","wings"},{"eternal_crown","Eternal Crown","crown"},{"legend_frame","Legend Frame","frame"}
        };
        for(int i=0;i<ds.length;i++)DECORS.add(new DecorDef(ds[i][0],ds[i][1],ds[i][2],1+i/8));
    }
    private static void addChars(String rarity,String[] names,int base){for(int i=0;i<names.length;i++){String id=rarity.toLowerCase(Locale.ROOT)+"_"+String.format(Locale.US,"%02d",i+1);CHARS.add(new CharacterDef(id,names[i],rarity,(base+i)%16,i%8));}}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    private static Set<String> set(Context c,String key){return new HashSet<>(p(c).getStringSet(key,new HashSet<>()));}
    private static void putSet(Context c,String key,Set<String> s){p(c).edit().putStringSet(key,new HashSet<>(s)).apply();}
    public static void ensure(Context c){
        if(p(c).getBoolean("init",false))return;
        HashSet<String> owned=new HashSet<>();owned.add("starter");
        p(c).edit().putBoolean("init",true).putInt("player_level",1).putInt("player_xp",0).putInt("tickets",3).putInt("essence",0).putInt("pity",0).putInt("ur_pity",0).putString("equipped","starter").putStringSet("owned_chars",owned).putStringSet("owned_decors",new HashSet<>()).putStringSet("equipped_decors",new HashSet<>()).apply();
        setCharLevel(c,"starter",1);setCharXp(c,"starter",0);
    }

    public static List<CharacterDef> allCharacters(){return Collections.unmodifiableList(CHARS);} public static List<DecorDef> allDecorations(){return Collections.unmodifiableList(DECORS);}
    public static CharacterDef character(String id){for(CharacterDef d:CHARS)if(d.id.equals(id))return d;return CHARS.get(0);} public static DecorDef decoration(String id){for(DecorDef d:DECORS)if(d.id.equals(id))return d;return null;}
    public static Set<String> ownedCharacters(Context c){ensure(c);Set<String>s=set(c,"owned_chars");s.add("starter");return s;} public static Set<String> ownedDecorations(Context c){ensure(c);return set(c,"owned_decors");}
    public static String equippedId(Context c){ensure(c);String id=p(c).getString("equipped","starter");return character(id).id;} public static CharacterDef equipped(Context c){return character(equippedId(c));}
    public static void equip(Context c,String id){if(ownedCharacters(c).contains(id))p(c).edit().putString("equipped",id).apply();}
    public static Set<String> equippedDecorations(Context c){ensure(c);return set(c,"equipped_decors");}
    public static boolean toggleDecoration(Context c,String id){if(!ownedDecorations(c).contains(id))return false;Set<String>s=equippedDecorations(c);if(s.contains(id)){s.remove(id);putSet(c,"equipped_decors",s);return true;}if(s.size()>=3)return false;s.add(id);putSet(c,"equipped_decors",s);return true;}

    public static int playerLevel(Context c){ensure(c);return p(c).getInt("player_level",1);} public static int playerXp(Context c){ensure(c);return p(c).getInt("player_xp",0);} public static int playerNextXp(Context c){return 120+playerLevel(c)*45;}
    public static int tickets(Context c){ensure(c);return p(c).getInt("tickets",0);} public static int essence(Context c){ensure(c);return p(c).getInt("essence",0);} public static int pity(Context c){ensure(c);return p(c).getInt("pity",0);} public static int urPity(Context c){ensure(c);return p(c).getInt("ur_pity",0);}
    private static void addTickets(Context c,int n){p(c).edit().putInt("tickets",Math.max(0,tickets(c)+n)).apply();} private static void addEssence(Context c,int n){p(c).edit().putInt("essence",Math.max(0,essence(c)+n)).apply();}
    public static String lastReward(Context c){ensure(c);return p(c).getString("last_reward","");} public static void clearLastReward(Context c){p(c).edit().putString("last_reward","").apply();}
    private static void rewardLog(Context c,String msg){if(msg==null||msg.isEmpty())return;String old=lastReward(c);String next=old==null||old.isEmpty()?msg:old+"\n"+msg;p(c).edit().putString("last_reward",next).apply();}

    public static int charLevel(Context c,String id){ensure(c);return Math.max(1,p(c).getInt("cl_"+id,1));} public static int charXp(Context c,String id){ensure(c);return Math.max(0,p(c).getInt("cx_"+id,0));} private static void setCharLevel(Context c,String id,int v){p(c).edit().putInt("cl_"+id,Math.max(1,v)).apply();} private static void setCharXp(Context c,String id,int v){p(c).edit().putInt("cx_"+id,Math.max(0,v)).apply();}
    public static int charNextXp(Context c,String id){return 90+charLevel(c,id)*35;}
    public static int addCharXp(Context c,String id,int amount){int lv=charLevel(c,id),xp=charXp(c,id)+Math.max(0,amount);while(lv<200){int need=90+lv*35;if(xp<need)break;xp-=need;lv++;}setCharLevel(c,id,lv);setCharXp(c,id,xp);return lv;}
    public static String displayName(Context c,CharacterDef d){if(d==null)d=equipped(c);if(!d.starter())return d.name;int lv=charLevel(c,"starter");if(lv>=80)return "AURELION DRAGON";if(lv>=50)return "INFERNO DRAKE";if(lv>=25)return "PYROWING";if(lv>=12)return "SPARKTAIL";if(lv>=5)return "FLARELING";return "EMBERKIN";}
    public static String rarityLabel(CharacterDef d){return d==null||d.rarity==null||d.rarity.isEmpty()?"EXCLUSIVE":d.rarity;}

    public static void onWakeSuccess(Context c,int streak){ensure(c);clearLastReward(c);String active=equippedId(c);int beforeChar=charLevel(c,active);int afterChar=addCharXp(c,active,80+Math.min(220,Math.max(0,streak)*8));if(afterChar>beforeChar)rewardLog(c,"COMPANION Lv."+afterChar);
        int lv=playerLevel(c),xp=playerXp(c)+120+Math.min(300,Math.max(0,streak)*12);int old=lv;while(lv<200){int need=120+lv*45;if(xp<need)break;xp-=need;lv++;handleLevelReward(c,lv);}p(c).edit().putInt("player_level",lv).putInt("player_xp",xp).apply();if(lv>old)rewardLog(c,"PLAYER Lv."+lv);
    }
    private static void handleLevelReward(Context c,int lv){
        if(lv%5==0){addTickets(c,1);rewardLog(c,"+1 GACHA TICKET");}
        if(lv%4==0&&RNG.nextInt(100)<35)awardRandomDecoration(c,false);
        if(lv==7)awardNamedDecoration(c,"tiny_crown");
        if(lv==15)awardNamedDecoration(c,"silver_crown");
        if(lv==30)awardNamedDecoration(c,"blaze_wings");
        if(lv==50)awardNamedDecoration(c,"dragon_halo");
        if(lv==75)awardNamedDecoration(c,"aurora_frame");
        if(lv==100)awardNamedDecoration(c,"eternal_crown");
        if(lv==10)awardCharacter(c,1); if(lv==20)awardCharacter(c,1); if(lv==30)awardCharacter(c,2); if(lv==50)awardCharacter(c,3); if(lv==80)awardCharacter(c,4);
        else if(lv>=8&&lv%8==0&&RNG.nextInt(100)<35)awardCharacter(c,0);
    }
    private static void awardNamedDecoration(Context c,String id){DecorDef d=decoration(id);if(d==null)return;Set<String>s=ownedDecorations(c);if(s.add(id)){putSet(c,"owned_decors",s);rewardLog(c,"DECOR: "+d.name);}else addEssence(c,8);}
    private static void awardRandomDecoration(Context c,boolean guaranteed){ArrayList<DecorDef> pool=new ArrayList<>(DECORS);Collections.shuffle(pool,RNG);Set<String>s=ownedDecorations(c);for(DecorDef d:pool)if(!s.contains(d.id)){s.add(d.id);putSet(c,"owned_decors",s);rewardLog(c,"DECOR: "+d.name);return;}if(guaranteed)addEssence(c,12);}
    private static void awardCharacter(Context c,int minTier){CharacterDef d=rollDef(minTier);Set<String>s=ownedCharacters(c);if(s.add(d.id)){putSet(c,"owned_chars",s);setCharLevel(c,d.id,1);setCharXp(c,d.id,0);rewardLog(c,"CHARACTER: "+d.name+" ["+d.rarity+"]");}else{int e=duplicateEssence(d);addEssence(c,e);rewardLog(c,"DUPLICATE +"+e+" ESSENCE");}}

    private static int rarityTier(String r){if("UR".equals(r))return 4;if("SSR".equals(r))return 3;if("SR".equals(r))return 2;if("R".equals(r))return 1;return 0;}
    private static int duplicateEssence(CharacterDef d){switch(d.tier()){case 4:return 80;case 3:return 40;case 2:return 20;case 1:return 10;default:return 5;}}
    private static CharacterDef rollDef(int minTier){
        int tier;int x=RNG.nextInt(10000);if(x<200)tier=4;else if(x<800)tier=3;else if(x<2200)tier=2;else if(x<5000)tier=1;else tier=0;tier=Math.max(tier,Math.max(0,Math.min(4,minTier)));
        ArrayList<CharacterDef> pool=new ArrayList<>();for(CharacterDef d:CHARS)if(!d.starter()&&d.tier()==tier)pool.add(d);return pool.get(RNG.nextInt(pool.size()));
    }
    public static GachaResult gacha(Context c,int minTier){ensure(c);if(tickets(c)<=0)return null;addTickets(c,-1);int pty=pity(c)+1,ur=urPity(c)+1;int forced=Math.max(minTier,pty>=30?3:0);if(ur>=100)forced=4;CharacterDef d=rollDef(forced);boolean dup=ownedCharacters(c).contains(d.id);int e=0;if(dup){e=duplicateEssence(d);addEssence(c,e);}else{Set<String>s=ownedCharacters(c);s.add(d.id);putSet(c,"owned_chars",s);setCharLevel(c,d.id,1);setCharXp(c,d.id,0);}if(d.tier()>=3)pty=0;if(d.tier()>=4)ur=0;p(c).edit().putInt("pity",pty).putInt("ur_pity",ur).apply();return new GachaResult(d,dup,e);}
    public static List<GachaResult> gachaTen(Context c){ArrayList<GachaResult> out=new ArrayList<>();if(tickets(c)<10)return out;for(int i=0;i<10;i++)out.add(gacha(c,i==9?2:0));return out;}
    public static boolean trainEquipped(Context c){ensure(c);if(essence(c)<25)return false;addEssence(c,-25);addCharXp(c,equippedId(c),250);return true;}
    public static String nextMilestone(Context c){int lv=playerLevel(c);if(lv<7)return "Lv.7  100% Tiny Crown";if(lv<10)return "Lv.10  100% R+ Character";if(lv<15)return "Lv.15  100% Silver Crown";if(lv<20)return "Lv.20  100% R+ Character";if(lv<30)return "Lv.30  100% SR+ Character + Blaze Wings";if(lv<50)return "Lv.50  100% SSR+ Character + Dragon Halo";if(lv<75)return "Lv.75  100% Aurora Frame";if(lv<80)return "Lv.80  100% UR Character";if(lv<100)return "Lv.100  100% Eternal Crown";return I18n.tr(c,"4レベルごとに35%で装飾、5レベルごとにガチャチケット");}
}
