from pathlib import Path
root=Path('WakeGuard/app')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='build.gradle.kts'; s=read(p)
s=s.replace('versionCode = 130','versionCode = 140',1).replace('versionName = "2.3.0"','versionName = "2.4.0"',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/StatsActivity.java'; s=read(p)
old='''        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.CENTER);hero.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,20));hero.setBackground(Ui.roundStroke(0xff0b0d13,0xff2c3445,24,this));
        companion=new StreakCompanionView(this);hero.addView(companion,new LinearLayout.LayoutParams(-1,Ui.dp(this,390)));
        growthLevel=Ui.text(this,"成長 Lv.1",20,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setGravity(Gravity.CENTER);hero.addView(growthLevel);
        growthForm=Ui.text(this,"火種",12,Ui.ACCENT);growthForm.setGravity(Gravity.CENTER);growthForm.setPadding(0,Ui.dp(this,3),0,0);hero.addView(growthForm);
        currentValue=Ui.text(this,"0日",40,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);currentValue.setGravity(Gravity.CENTER);currentValue.setPadding(0,Ui.dp(this,10),0,0);hero.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",12,Ui.MUTED);streakLabel.setGravity(Gravity.CENTER);hero.addView(streakLabel);body.addView(hero);

        TextView growHeader=Ui.sectionHeader(this,"成長");growHeader.setPadding(0,Ui.dp(this,22),0,Ui.dp(this,6));body.addView(growHeader);
        LinearLayout growth=Ui.card(this);
        TextView rule=Ui.text(this,"起床に成功するたびに、同じ1体が1段階成長します。火種から始まり、炎の中に竜の姿が形成され、炎竜として上限なく成長し続けます。ガチャ・レア度・通貨・装備・アイテムはありません。",14,Ui.TEXT);growth.addView(rule);
        TextView quality=Ui.text(this,"炎は固定画像ではなく、端末内のシェーダーで白熱コア・赤橙の外炎・揺らぐ輪郭・上昇する炎・火の粉・発光を毎フレーム生成します。炎竜の成長と表示はオフラインでも完全に動作します。",12,Ui.MUTED);quality.setPadding(0,Ui.dp(this,8),0,0);growth.addView(quality);body.addView(growth,Ui.cardParams(this));
'''
new='''        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,14));
        companion=new StreakCompanionView(this);LinearLayout.LayoutParams creatureLp=new LinearLayout.LayoutParams(Ui.dp(this,170),Ui.dp(this,220));hero.addView(companion,creatureLp);
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(Ui.dp(this,18),Ui.dp(this,8),0,0);
        currentValue=Ui.text(this,"0日",48,Ui.TEXT);currentValue.setTypeface(null,Typeface.BOLD);summary.addView(currentValue);
        TextView streakLabel=Ui.text(this,"現在のストリーク",13,Ui.MUTED);summary.addView(streakLabel);
        growthLevel=Ui.text(this,"Lv.1",16,Ui.TEXT);growthLevel.setTypeface(null,Typeface.BOLD);growthLevel.setPadding(0,Ui.dp(this,18),0,0);summary.addView(growthLevel);
        growthForm=Ui.text(this,"火種",12,Ui.ACCENT_2);growthForm.setPadding(0,Ui.dp(this,2),0,0);summary.addView(growthForm);
        hero.addView(summary,new LinearLayout.LayoutParams(0,-2,1));body.addView(hero);
        body.addView(Ui.divider(this));
'''
if old not in s: raise SystemExit('Stats hero/growth block not found')
s=s.replace(old,new,1)
s=s.replace('growthLevel.setText(I18n.tr(this,"成長")+" Lv."+lv);','growthLevel.setText("Lv."+lv);',1)
s=s.replace('TextView protectRule=Ui.text(this,"毎月1日に2日分回復し、最大3日まで持てます。起きられなかった対象日に1日だけ自動使用してストリークを守ります。保護日は成功回数や成長Lvには加算されません。",12,Ui.MUTED);',
            'TextView protectRule=Ui.text(this,"最大3日。起きられなかった対象日に1日使ってストリークを維持します。",12,Ui.MUTED);',1)
s=s.replace('TextView bonusRule=Ui.text(this,"30日連続で実際に起床成功するごとに、保護日を1日追加します（最大3日）。",12,Ui.MUTED);bonusRule.setPadding(0,Ui.dp(this,5),0,0);protect.addView(bonusRule);',
            'TextView bonusRule=Ui.text(this,"30日連続の成功で1日追加。",12,Ui.MUTED);bonusRule.setPadding(0,Ui.dp(this,5),0,0);protect.addView(bonusRule);',1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/ClockActivity.java'; s=read(p)
s=s.replace('private static final String KEY_SW_LAPS = "sw_laps";',
'''private static final String KEY_SW_LAPS = "sw_laps";
    private static final String KEY_SW_LAP_WALLS = "sw_lap_walls";
    private static final String KEY_SW_STOPPED_WALL = "sw_stopped_wall";''',1)
s=s.replace('private Button stopwatchStartPause, stopwatchLapReset;',
            'private Button stopwatchStartPause, stopwatchLapReset, stopwatchImport, stopwatchLapImport;',1)
old='''        Button timeLog=Ui.button(this,"時間記録・集計",false);timeLog.setOnClickListener(v->startActivity(new Intent(this,TimeLogActivity.class)));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,Ui.dp(this,50));tlp.setMargins(0,Ui.dp(this,12),0,0);body.addView(timeLog,tlp);
        lapList=text("",15,Ui.MUTED); lapList.setTypeface(Typeface.MONOSPACE); lapList.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,8)); body.addView(lapList); renderLaps();
'''
new='''        LinearLayout importRow=new LinearLayout(this);importRow.setGravity(Gravity.CENTER_VERTICAL);importRow.setPadding(0,Ui.dp(this,10),0,0);
        stopwatchImport=Ui.button(this,"時間記録に追加",false);stopwatchImport.setOnClickListener(v->importStoppedStopwatch());importRow.addView(stopwatchImport,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        stopwatchLapImport=Ui.ghostButton(this,"ラップから追加");stopwatchLapImport.setOnClickListener(v->showLapImportPicker());importRow.addView(stopwatchLapImport,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));body.addView(importRow);
        Button timeLog=Ui.ghostButton(this,"時間記録を開く");timeLog.setOnClickListener(v->startActivity(new Intent(this,TimeLogActivity.class)));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));tlp.setMargins(0,Ui.dp(this,4),0,0);body.addView(timeLog,tlp);
        lapList=text("",15,Ui.MUTED); lapList.setTypeface(Typeface.MONOSPACE); lapList.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,8)); body.addView(lapList); renderLaps();
'''
if old not in s: raise SystemExit('stopwatch time-log block not found')
s=s.replace(old,new,1)
old='''        if (sp.getBoolean(KEY_SW_RUNNING, false)) {
            long elapsed = stopwatchElapsed();
            sp.edit().putBoolean(KEY_SW_RUNNING,false).putLong(KEY_SW_ELAPSED,elapsed).putLong(KEY_SW_BASE,0L).apply();
        } else {
            sp.edit().putBoolean(KEY_SW_RUNNING,true).putLong(KEY_SW_BASE,SystemClock.elapsedRealtime()).apply();
        }'''
new='''        if (sp.getBoolean(KEY_SW_RUNNING, false)) {
            long elapsed = stopwatchElapsed();
            sp.edit().putBoolean(KEY_SW_RUNNING,false).putLong(KEY_SW_ELAPSED,elapsed).putLong(KEY_SW_BASE,0L).putLong(KEY_SW_STOPPED_WALL,System.currentTimeMillis()).apply();
        } else {
            sp.edit().putBoolean(KEY_SW_RUNNING,true).putLong(KEY_SW_BASE,SystemClock.elapsedRealtime()).apply();
        }'''
if old not in s: raise SystemExit('toggle stopwatch block not found')
s=s.replace(old,new,1)
old='''        if (sp.getBoolean(KEY_SW_RUNNING,false)) {
            ArrayList<Long> laps = loadLaps(); laps.add(stopwatchElapsed()); saveLaps(laps); renderLaps();
        } else {
            sp.edit().putLong(KEY_SW_ELAPSED,0L).putLong(KEY_SW_BASE,0L).remove(KEY_SW_LAPS).apply(); renderLaps();
        }'''
new='''        if (sp.getBoolean(KEY_SW_RUNNING,false)) {
            ArrayList<Long> laps = loadLaps(); laps.add(stopwatchElapsed()); saveLaps(laps);
            ArrayList<Long> walls=loadLapWalls();walls.add(System.currentTimeMillis());saveLapWalls(walls);renderLaps();
        } else {
            sp.edit().putLong(KEY_SW_ELAPSED,0L).putLong(KEY_SW_BASE,0L).remove(KEY_SW_LAPS).remove(KEY_SW_LAP_WALLS).remove(KEY_SW_STOPPED_WALL).apply(); renderLaps();
        }'''
if old not in s: raise SystemExit('lap/reset block not found')
s=s.replace(old,new,1)
marker='''    private void renderLaps() {
'''
helpers='''    private ArrayList<Long> loadLapWalls() {
        ArrayList<Long> out=new ArrayList<>();String raw=p().getString(KEY_SW_LAP_WALLS,"");
        if(raw!=null&&!raw.isEmpty())for(String x:raw.split(","))try{out.add(Long.parseLong(x));}catch(Exception ignored){}
        return out;
    }
    private void saveLapWalls(ArrayList<Long> values){StringBuilder b=new StringBuilder();for(long v:values){if(b.length()>0)b.append(',');b.append(v);}p().edit().putString(KEY_SW_LAP_WALLS,b.toString()).apply();}
    private void openTimeLogImport(long durationMs,long endWallMs,String label){
        if(durationMs<=0L)return;Intent i=new Intent(this,TimeLogActivity.class).putExtra("importDurationMs",durationMs).putExtra("importEndWallMs",endWallMs>0L?endWallMs:System.currentTimeMillis()).putExtra("importLabel",label==null?"ストップウォッチ":label);startActivity(i);
    }
    private void importStoppedStopwatch(){
        if(p().getBoolean(KEY_SW_RUNNING,false)){Toast.makeText(this,"ストップウォッチを停止してから追加してください",Toast.LENGTH_SHORT).show();return;}
        long elapsed=stopwatchElapsed();if(elapsed<=0L)return;long end=p().getLong(KEY_SW_STOPPED_WALL,System.currentTimeMillis());openTimeLogImport(elapsed,end,"ストップウォッチ");
    }
    private void showLapImportPicker(){
        ArrayList<Long> laps=loadLaps();if(laps.isEmpty()){Toast.makeText(this,"ラップがありません",Toast.LENGTH_SHORT).show();return;}
        ArrayList<Long> walls=loadLapWalls();String[] items=new String[laps.size()];
        for(int i=0;i<laps.size();i++){long total=laps.get(i),prev=i==0?0L:laps.get(i-1),split=Math.max(0L,total-prev);items[i]="ラップ "+(i+1)+"   "+formatStopwatch(split);}
        new AlertDialog.Builder(this).setTitle("時間記録に追加するラップ").setItems(items,(d,which)->{
            long total=laps.get(which),prev=which==0?0L:laps.get(which-1),split=Math.max(0L,total-prev);long end=which<walls.size()?walls.get(which):0L;
            if(end<=0L){long ref=p().getBoolean(KEY_SW_RUNNING,false)?System.currentTimeMillis():p().getLong(KEY_SW_STOPPED_WALL,System.currentTimeMillis());end=ref-Math.max(0L,stopwatchElapsed()-total);}
            openTimeLogImport(split,end,"ラップ "+(which+1));
        }).show();
    }

'''
if marker not in s: raise SystemExit('renderLaps marker not found')
s=s.replace(marker,helpers+marker,1)
old='''        stopwatchStartPause.setText(I18n.tr(this,running?"一時停止":"スタート"));
        stopwatchLapReset.setText(I18n.tr(this,running?"ラップ":"リセット"));
'''
new='''        stopwatchStartPause.setText(I18n.tr(this,running?"一時停止":"スタート"));
        stopwatchLapReset.setText(I18n.tr(this,running?"ラップ":"リセット"));
        if(stopwatchImport!=null){stopwatchImport.setEnabled(!running&&elapsed>0L);stopwatchImport.setAlpha((!running&&elapsed>0L)?1f:.45f);}
        if(stopwatchLapImport!=null){boolean hasLap=!loadLaps().isEmpty();stopwatchLapImport.setEnabled(hasLap);stopwatchLapImport.setAlpha(hasLap?1f:.45f);}
'''
if old not in s: raise SystemExit('update stopwatch controls block not found')
s=s.replace(old,new,1)
write(p,s)

p='src/main/java/jp/wakeguard/alarm/TimeLogActivity.java'; s=read(p)
s=s.replace('private Runnable trackingTick;',
'''private Runnable trackingTick;
    private long pendingImportDurationMs=0L;
    private long pendingImportEndWallMs=0L;
    private String pendingImportLabel="ストップウォッチ";''',1)
old='''    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); load(); buildShell(); showFolders();
    }'''
new='''    @Override protected void onCreate(Bundle b){
        super.onCreate(b); Ui.prepareActivity(this); load();
        Intent intent=getIntent();if(intent!=null){pendingImportDurationMs=Math.max(0L,intent.getLongExtra("importDurationMs",0L));pendingImportEndWallMs=intent.getLongExtra("importEndWallMs",0L);pendingImportLabel=intent.getStringExtra("importLabel");if(pendingImportLabel==null||pendingImportLabel.trim().isEmpty())pendingImportLabel="ストップウォッチ";}
        buildShell(); showFolders();if(pendingImportDurationMs>0L)new Handler(Looper.getMainLooper()).post(this::showImportFolderPicker);
    }'''
if old not in s: raise SystemExit('TimeLog onCreate block not found')
s=s.replace(old,new,1)
marker='''    private void showFolderDialog(Folder existing){
'''
helpers='''    private void showImportFolderPicker(){
        if(pendingImportDurationMs<=0L)return;
        if(folders.isEmpty()){
            new AlertDialog.Builder(this).setTitle("時間記録に追加").setMessage("追加先のフォルダーを作成してください。").setNegativeButton("キャンセル",(d,w)->clearPendingImport()).setPositiveButton("フォルダー作成",(d,w)->showFolderDialog(null)).show();return;
        }
        ArrayList<Folder> sorted=new ArrayList<>(folders);sorted.sort((a,b)->Long.compare(b.createdAt,a.createdAt));String[] names=new String[sorted.size()+1];for(int i=0;i<sorted.size();i++)names[i]=sorted.get(i).name;names[sorted.size()]="＋ 新しいフォルダー";
        new AlertDialog.Builder(this).setTitle(pendingImportLabel+"を追加").setItems(names,(d,which)->{if(which==sorted.size())showFolderDialog(null);else addPendingImport(sorted.get(which));}).setNegativeButton("キャンセル",(d,w)->clearPendingImport()).show();
    }
    private void addPendingImport(Folder folder){
        if(folder==null||pendingImportDurationMs<=0L)return;long end=pendingImportEndWallMs>0L?pendingImportEndWallMs:System.currentTimeMillis();long start=end-pendingImportDurationMs;if(start>=end)return;entries.add(new Entry(UUID.randomUUID().toString(),folder.id,start,end));save();String label=pendingImportLabel;clearPendingImport();Toast.makeText(this,label+"を「"+folder.name+"」に追加しました",Toast.LENGTH_SHORT).show();showFolder(folder.id);
    }
    private void clearPendingImport(){pendingImportDurationMs=0L;pendingImportEndWallMs=0L;pendingImportLabel="ストップウォッチ";}

'''
if marker not in s: raise SystemExit('showFolderDialog marker missing')
s=s.replace(marker,helpers+marker,1)
old='''d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("名前を入力してください");return;}if(existing==null)folders.add(new Folder(UUID.randomUUID().toString(),n,System.currentTimeMillis()));else existing.name=n;save();d.dismiss();if(existing==null)showFolders();else showFolder(existing.id);}));d.show();'''
new='''d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("名前を入力してください");return;}if(existing==null){Folder created=new Folder(UUID.randomUUID().toString(),n,System.currentTimeMillis());folders.add(created);save();d.dismiss();if(pendingImportDurationMs>0L)addPendingImport(created);else showFolders();}else{existing.name=n;save();d.dismiss();showFolder(existing.id);}}));d.show();'''
if old not in s: raise SystemExit('showFolderDialog save block not found')
s=s.replace(old,new,1)
write(p,s)

assert 'versionName = "2.4.0"' in read('build.gradle.kts')
assert 'Ui.dp(this,170),Ui.dp(this,220)' in read('src/main/java/jp/wakeguard/alarm/StatsActivity.java')
assert '起床に成功するたびに、同じ1体' not in read('src/main/java/jp/wakeguard/alarm/StatsActivity.java')
assert 'KEY_SW_LAP_WALLS' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
assert '時間記録に追加' in read('src/main/java/jp/wakeguard/alarm/ClockActivity.java')
assert 'showImportFolderPicker' in read('src/main/java/jp/wakeguard/alarm/TimeLogActivity.java')
print('Android 2.4.0 patch applied')
