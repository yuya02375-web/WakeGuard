from pathlib import Path

p = Path('WakeGuard/app/src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
s = p.read_text()
old = '''        LinkedHashMap<LocalDate,ArrayList<Entry>> groups=new LinkedHashMap<>();
        for(Entry e:list){LocalDate d=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate();groups.computeIfAbsent(d,k->new ArrayList<>()).add(e);}
        DateTimeFormatter df=DateTimeFormatter.ofPattern("yyyy/MM/dd (E)",Locale.JAPAN);
        for(Map.Entry<LocalDate,ArrayList<Entry>> g:groups.entrySet()){
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,7));
            TextView d=Ui.title(this,g.getKey().format(df),16);head.addView(d,new LinearLayout.LayoutParams(0,-2,1));
            TextView sum=text("合計 "+fmt(durationForDay(folderId,g.getKey())),13,Ui.ACCENT_2);head.addView(sum);body.addView(head);
            for(Entry e:g.getValue()) addEntryRow(f,e);
        }
'''
new = '''        ArrayList<LocalDate> days=daysWithEntries(folderId);
        DateTimeFormatter df=DateTimeFormatter.ofPattern("yyyy/MM/dd (E)",Locale.JAPAN);
        for(LocalDate day:days){
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,7));
            TextView d=Ui.title(this,day.format(df),16);head.addView(d,new LinearLayout.LayoutParams(0,-2,1));
            TextView sum=text("合計 "+fmt(durationForDay(folderId,day)),13,Ui.ACCENT_2);head.addView(sum);body.addView(head);
            for(Entry e:entriesForDay(folderId,day)) addEntryRow(f,e,day);
        }
'''
if old not in s: raise SystemExit('Android grouping block not found')
s=s.replace(old,new)
old = '''    private void addEntryRow(Folder f,Entry e){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,8),Ui.dp(this,11));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));
        ZonedDateTime a=Instant.ofEpochMilli(e.start).atZone(zone),b=Instant.ofEpochMilli(e.end).atZone(zone);DateTimeFormatter tf=DateTimeFormatter.ofPattern("HH:mm");
        String times=a.format(tf)+" – "+b.format(tf)+(a.toLocalDate().equals(b.toLocalDate())?"":"  翌日");
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);TextView t=Ui.title(this,times,17);info.addView(t);TextView dur=text(fmt(e.end-e.start),14,Ui.ACCENT_2);dur.setPadding(0,Ui.dp(this,3),0,0);info.addView(dur);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
'''
new = '''    private void addEntryRow(Folder f,Entry e,LocalDate displayDay){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,8),Ui.dp(this,11));card.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));
        long dayStart=displayDay.atStartOfDay(zone).toInstant().toEpochMilli(),dayEnd=displayDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long segStart=Math.max(e.start,dayStart),segEnd=Math.min(e.end,dayEnd);DateTimeFormatter tf=DateTimeFormatter.ofPattern("HH:mm");
        ZonedDateTime a=Instant.ofEpochMilli(segStart).atZone(zone),b=Instant.ofEpochMilli(segEnd).atZone(zone);
        String endLabel=segEnd==dayEnd?"24:00":b.format(tf);String times=a.format(tf)+" – "+endLabel;
        boolean split=e.start<dayStart||e.end>dayEnd;
        String durationLabel=split?"この日 "+fmt(segEnd-segStart)+"  ・  記録全体 "+fmt(e.end-e.start):fmt(e.end-e.start);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);TextView t=Ui.title(this,times,17);info.addView(t);TextView dur=text(durationLabel,14,Ui.ACCENT_2);dur.setPadding(0,Ui.dp(this,3),0,0);info.addView(dur);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
'''
if old not in s: raise SystemExit('Android row block not found')
s=s.replace(old,new)
marker='    private Folder findFolder(String id){for(Folder f:folders)if(f.id.equals(id))return f;return null;}\n'
helpers='''    private ArrayList<LocalDate> daysWithEntries(String folderId){TreeSet<LocalDate> days=new TreeSet<>(Comparator.reverseOrder());for(Entry e:entries){if(!folderId.equals(e.folderId)||e.end<=e.start)continue;LocalDate a=Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate(),b=Instant.ofEpochMilli(e.end-1).atZone(zone).toLocalDate();for(LocalDate d=a;!d.isAfter(b);d=d.plusDays(1))days.add(d);}return new ArrayList<>(days);}\n    private ArrayList<Entry> entriesForDay(String folderId,LocalDate day){long a=day.atStartOfDay(zone).toInstant().toEpochMilli(),b=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();ArrayList<Entry> out=new ArrayList<>();for(Entry e:entries)if(folderId.equals(e.folderId)&&e.end>a&&e.start<b)out.add(e);out.sort((x,y)->Long.compare(y.start,x.start));return out;}\n'''
if marker not in s: raise SystemExit('Android helper marker not found')
s=s.replace(marker,helpers+marker)
p.write_text(s)

g=Path('WakeGuard/app/build.gradle.kts')
t=g.read_text().replace('versionCode = 110','versionCode = 111').replace('versionName = "2.1.0"','versionName = "2.1.1"')
g.write_text(t)
print('Android 2.1.1 midnight time-log fix applied')
