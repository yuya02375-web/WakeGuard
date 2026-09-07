from pathlib import Path
import re

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Version bump.
p = app / "build.gradle.kts"
s = p.read_text(encoding="utf-8")
s = re.sub(r'versionCode = \d+', 'versionCode = 87', s)
s = re.sub(r'versionName = "[^"]+"', 'versionName = "1.7.6"', s)
p.write_text(s, encoding="utf-8")

# Fresh installs must not materialize the old implicit 05:30 legacy primary alarm.
prefs = java / "Prefs.java"
s = prefs.read_text(encoding="utf-8")
s = s.replace('public static boolean enabled(Context c) { return p(c).getBoolean("enabled", true); }',
              'public static boolean enabled(Context c) { return p(c).getBoolean("enabled", false); }')
s = s.replace('public static int hour(Context c) { return p(c).getInt("hour", 5); }',
              'public static int hour(Context c) { return p(c).getInt("hour", 7); }')
s = s.replace('public static int minute(Context c) { return p(c).getInt("minute", 30); }',
              'public static int minute(Context c) { return p(c).getInt("minute", 0); }')
s = s.replace('public static int dayMask(Context c) { return p(c).getInt("days", 0b1111111); }',
              'public static int dayMask(Context c) { return p(c).getInt("days", 0); }')
needle = '    public static void dayMask(Context c, int v) { p(c).edit().putInt("days", v).apply(); }\n'
insert = needle + '''    /** True only when the legacy primary alarm was actually configured by an older build.\n     * Fresh installs must not show or schedule the historical 05:30 placeholder. */\n    public static boolean primaryConfigured(Context c) {\n        SharedPreferences sp=p(c);\n        return sp.contains("hour") || sp.contains("minute") || sp.contains("days") || sp.contains("enabled")\n                || sp.contains("primary_label") || sp.contains("primary_mission_type") || sp.contains("primary_mission_count");\n    }\n'''
assert needle in s, "Prefs dayMask insertion point not found"
s = s.replace(needle, insert, 1)
prefs.write_text(s, encoding="utf-8")

profiles = java / "AlarmProfiles.java"
s = profiles.read_text(encoding="utf-8")
old = '        ArrayList<AlarmStore.Entry> out = new ArrayList<>();\n        out.add(primary(c)); out.addAll(AlarmStore.all(c)); return out;'
new = '        ArrayList<AlarmStore.Entry> out = new ArrayList<>();\n        if(Prefs.primaryConfigured(c)) out.add(primary(c));\n        out.addAll(AlarmStore.all(c)); return out;'
assert old in s, "AlarmProfiles.all legacy primary insertion not found"
s = s.replace(old, new, 1)
profiles.write_text(s, encoding="utf-8")

scheduler = java / "AlarmScheduler.java"
s = scheduler.read_text(encoding="utf-8")
old = '            scheduleSpec(c,am,PRIMARY_ALARM_ID,Prefs.hour(c),Prefs.minute(c),Prefs.dayMask(c),Prefs.enabled(c),true,Prefs.preNotifyMin(c),zone,today,now);'
new = '            if(Prefs.primaryConfigured(c)) scheduleSpec(c,am,PRIMARY_ALARM_ID,Prefs.hour(c),Prefs.minute(c),Prefs.dayMask(c),Prefs.enabled(c),true,Prefs.preNotifyMin(c),zone,today,now);'
assert old in s, "AlarmScheduler primary scheduling line not found"
s = s.replace(old, new, 1)
old = '        long p=nextFor(Prefs.hour(c),Prefs.minute(c),Prefs.dayMask(c),Prefs.enabled(c),zone,today,now); if(p>0)best=p;'
new = '        if(Prefs.primaryConfigured(c)){long p=nextFor(Prefs.hour(c),Prefs.minute(c),Prefs.dayMask(c),Prefs.enabled(c),zone,today,now); if(p>0)best=p;}'
assert old in s, "AlarmScheduler nextTrigger primary line not found"
s = s.replace(old, new, 1)
scheduler.write_text(s, encoding="utf-8")

# Fresh streak protection starts at 0/3. Future month boundaries still grant +2 up to 3.
prot = java / "StreakProtection.java"
s = prot.read_text(encoding="utf-8")
s = s.replace('.putInt("balance",MONTHLY_DAYS)', '.putInt("balance",0)', 1)
needle = '        }\n        refreshMonthlyLocked(c);\n    }\n\n    private static void refreshMonthlyLocked(Context c){'
insert = '''        }\n        if(!sp.getBoolean("v176_initial_balance_fixed",false)){\n            boolean pristine=sp.getInt("balance",0)==MONTHLY_DAYS\n                    && sp.getInt("bonus_milestone",0)==0\n                    && sp.getStringSet("protected_days",new HashSet<>()).isEmpty();\n            YearMonth started=YearMonth.now();\n            try{started=YearMonth.from(LocalDate.ofEpochDay(sp.getLong("start_epoch_day",LocalDate.now().toEpochDay())));}catch(Throwable ignored){}\n            if(pristine && started.equals(YearMonth.now())) sp.edit().putInt("balance",0).putBoolean("v176_initial_balance_fixed",true).commit();\n            else sp.edit().putBoolean("v176_initial_balance_fixed",true).commit();\n        }\n        refreshMonthlyLocked(c);\n    }\n\n    private static void refreshMonthlyLocked(Context c){'''
assert needle in s, "StreakProtection ensure insertion point not found"
s = s.replace(needle, insert, 1)
s = s.replace('sp.getInt("balance",MONTHLY_DAYS))+grant', 'sp.getInt("balance",0))+grant')
s = s.replace('p(c).getInt("balance",MONTHLY_DAYS)))', 'p(c).getInt("balance",0)))')
s = s.replace('sp.getInt("balance",MONTHLY_DAYS)+1', 'sp.getInt("balance",0)+1')
prot.write_text(s, encoding="utf-8")

stats = java / "StatsActivity.java"
s = stats.read_text(encoding="utf-8")
s = s.replace('protectionValue=Ui.text(this,"2 / 3",19,PROTECTED_COLOR);',
              'protectionValue=Ui.text(this,"0 / 3",19,PROTECTED_COLOR);')
stats.write_text(s, encoding="utf-8")

# Validation.
assert 'versionName = "1.7.6"' in p.read_text(encoding="utf-8")
assert 'versionCode = 87' in p.read_text(encoding="utf-8")
ps = prefs.read_text(encoding="utf-8")
assert 'getBoolean("enabled", false)' in ps
assert 'getInt("hour", 7)' in ps and 'getInt("minute", 0)' in ps
assert 'getInt("days", 0)' in ps
assert 'primaryConfigured(Context c)' in ps
assert 'if(Prefs.primaryConfigured(c)) out.add(primary(c));' in profiles.read_text(encoding="utf-8")
assert 'if(Prefs.primaryConfigured(c)) scheduleSpec' in scheduler.read_text(encoding="utf-8")
pr = prot.read_text(encoding="utf-8")
assert '.putInt("balance",0)' in pr
assert 'v176_initial_balance_fixed' in pr
assert 'protectionValue=Ui.text(this,"0 / 3"' in stats.read_text(encoding="utf-8")
print("IGNIDO Wake v1.7.6 clean initial state patch applied")
