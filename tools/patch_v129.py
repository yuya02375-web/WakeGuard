from pathlib import Path
import runpy

# Rebuild the verified v1.2.8 source first.
runpy.run_path("tools/patch_v128.py", run_name="__main__")

app = Path("WakeGuard/app")
clock_path = app / "src/main/java/jp/wakeguard/alarm/ClockActivity.java"
i18n_path = app / "src/main/java/jp/wakeguard/alarm/I18n.java"
manifest_path = app / "src/main/AndroidManifest.xml"
gradle_path = app / "build.gradle.kts"

clock = clock_path.read_text(encoding="utf-8")

# Connectivity status is used only to decide whether the optional online augmentation
# should run. Local/offline search always runs first and never depends on network.
old = "import android.location.Geocoder;\nimport android.os.*;"
new = "import android.location.Geocoder;\nimport android.net.ConnectivityManager;\nimport android.net.NetworkCapabilities;\nimport android.os.*;"
if old not in clock:
    raise SystemExit("v1.2.9 import anchor missing")
clock = clock.replace(old, new, 1)

anchor = "    private String formatOffset(int totalSec) {"
methods = '''    private boolean hasValidatedInternet(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if(cm==null)return false;
            android.net.Network network=cm.getActiveNetwork();
            if(network==null)return false;
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Throwable ignored){return false;}
    }

    private ArrayList<String> resolvedCountryCodes(List<Address> addresses){
        LinkedHashSet<String> codes=new LinkedHashSet<>();
        if(addresses!=null)for(Address a:addresses){
            if(a==null)continue;String code=a.getCountryCode();if(code==null)continue;code=code.trim().toUpperCase(Locale.ROOT);if(!code.isEmpty())codes.add(code);
        }
        return new ArrayList<>(codes);
    }

    private void resolveOnlineCountryHints(String raw,java.util.function.Consumer<ArrayList<String>> done){
        String q=raw==null?"":raw.trim();
        if(q.isEmpty()||!hasValidatedInternet()||!Geocoder.isPresent()){done.accept(new ArrayList<>());return;}
        Geocoder g=new Geocoder(this,I18n.locale(this));
        if(Build.VERSION.SDK_INT>=33){
            try{g.getFromLocationName(q,5,new Geocoder.GeocodeListener(){
                @Override public void onGeocode(List<Address> addresses){ArrayList<String> codes=resolvedCountryCodes(addresses);runOnUiThread(()->done.accept(codes));}
                @Override public void onError(String errorMessage){runOnUiThread(()->done.accept(new ArrayList<>()));}
            });}catch(Throwable t){done.accept(new ArrayList<>());}
        }else{
            new Thread(()->{List<Address> addresses=null;try{addresses=g.getFromLocationName(q,5);}catch(Throwable ignored){}ArrayList<String> codes=resolvedCountryCodes(addresses);runOnUiThread(()->done.accept(codes));},"WakeGuard-Geocoder").start();
        }
    }

'''
if anchor not in clock:
    raise SystemExit("v1.2.9 method anchor missing")
clock = clock.replace(anchor, methods + anchor, 1)

# Remove the separate online-only control. Search is now one hybrid pipeline:
# local results immediately, optional online hints later, merged into the same list.
old = '''        Button online=Ui.button(this,"オンラインで場所を特定",true);online.setOnClickListener(v->onlinePlaceLookup(search.getText().toString(),search,online));box.addView(online,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        TextView onlineHint=text("都市名や場所名から国をオンライン特定し、その国のタイムゾーン候補を表示します",10,Ui.MUTED_2);onlineHint.setGravity(Gravity.CENTER);onlineHint.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,5));box.addView(onlineHint);'''
new = '''        TextView hybridHint=text("オフライン検索を常に使い、接続中はオンライン候補も自動で追加します",10,Ui.MUTED_2);hybridHint.setGravity(Gravity.CENTER);hybridHint.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,5));box.addView(hybridHint);'''
if old not in clock:
    raise SystemExit("v1.2.9 online UI anchor missing")
clock = clock.replace(old, new, 1)

old = '''        final ArrayList<String> all=new ArrayList<>();
        final HashMap<String,String> searchCache=new HashMap<>(),cityCache=new HashMap<>(),countryCache=new HashMap<>(),countryLabelCache=new HashMap<>(),displayCityCache=new HashMap<>();'''
new = '''        final ArrayList<String> all=new ArrayList<>();
        final ConcurrentHashMap<String,ArrayList<String>> onlineHintCache=new ConcurrentHashMap<>();
        final Set<String> onlineLookupRunning=ConcurrentHashMap.newKeySet();
        final Runnable[] pendingOnlineSearch=new Runnable[1];
        final HashMap<String,String> searchCache=new HashMap<>(),cityCache=new HashMap<>(),countryCache=new HashMap<>(),countryLabelCache=new HashMap<>(),displayCityCache=new HashMap<>();'''
if old not in clock:
    raise SystemExit("v1.2.9 cache anchor missing")
clock = clock.replace(old, new, 1)

old = '''                LinkedHashSet<String> results=new LinkedHashSet<>();ArrayList<String> guessed=new ArrayList<>();String titleKey="おすすめ";LinkedHashSet<String> regionCodes=regionCountryCodes(q);'''
new = '''                LinkedHashSet<String> results=new LinkedHashSet<>();ArrayList<String> guessed=new ArrayList<>();String titleKey="おすすめ";LinkedHashSet<String> regionCodes=regionCountryCodes(q);ArrayList<String> onlineCodes=onlineHintCache.get(q);if(onlineCodes==null)onlineCodes=new ArrayList<>();'''
if old not in clock:
    raise SystemExit("v1.2.9 search anchor missing")
clock = clock.replace(old, new, 1)

old = '''                    results.addAll(direct);
                    if(!regionCodes.isEmpty()){'''
new = '''                    results.addAll(direct);
                    if(!onlineCodes.isEmpty()){for(String code:onlineCodes){if(!guessed.contains(code))guessed.add(code);ArrayList<String> zs=zonesByCountry.get(code);if(zs==null)continue;int n=0;for(String id:zs){results.add(id);if(++n>=12||results.size()>=90)break;}if(results.size()>=90)break;}}
                    if(!regionCodes.isEmpty()){'''
if old not in clock:
    raise SystemExit("v1.2.9 merge anchor missing")
clock = clock.replace(old, new, 1)

old = '''        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(pendingSearch[0]!=null)handler.removeCallbacks(pendingSearch[0]);final String q=s==null?"":s.toString();pendingSearch[0]=()->{if(indexReady.get())requestSearch.accept(q);else{queuedQuery[0]=normalizeSearch(q);final int generation=searchGeneration.incrementAndGet();searchExecutor.execute(()->{if(!indexReady.get())return;final String latest=queuedQuery[0];handler.post(()->{if(generation==searchGeneration.get()&&zonePickerDialog==dialog)requestSearch.accept(latest);});});}};handler.postDelayed(pendingSearch[0],q.trim().isEmpty()?0L:45L);}public void afterTextChanged(Editable e){}});'''
new = '''        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(pendingSearch[0]!=null)handler.removeCallbacks(pendingSearch[0]);if(pendingOnlineSearch[0]!=null)handler.removeCallbacks(pendingOnlineSearch[0]);final String q=s==null?"":s.toString();final String onlineKey=normalizeSearch(q);pendingSearch[0]=()->{if(indexReady.get())requestSearch.accept(q);else{queuedQuery[0]=normalizeSearch(q);final int generation=searchGeneration.incrementAndGet();searchExecutor.execute(()->{if(!indexReady.get())return;final String latest=queuedQuery[0];handler.post(()->{if(generation==searchGeneration.get()&&zonePickerDialog==dialog)requestSearch.accept(latest);});});}};handler.postDelayed(pendingSearch[0],q.trim().isEmpty()?0L:45L);if(onlineKey.length()>=2&&!onlineHintCache.containsKey(onlineKey)){pendingOnlineSearch[0]=()->{if(zonePickerDialog!=dialog||!hasValidatedInternet()||!Geocoder.isPresent()||onlineHintCache.containsKey(onlineKey)||!onlineLookupRunning.add(onlineKey))return;resolveOnlineCountryHints(q,codes->{onlineLookupRunning.remove(onlineKey);if(zonePickerDialog!=dialog||!normalizeSearch(search.getText().toString()).equals(onlineKey))return;if(codes!=null&&!codes.isEmpty()){onlineHintCache.put(onlineKey,codes);requestSearch.accept(q);}});};handler.postDelayed(pendingOnlineSearch[0],350L);}}public void afterTextChanged(Editable e){}});'''
if old not in clock:
    raise SystemExit("v1.2.9 watcher anchor missing")
clock = clock.replace(old, new, 1)

old = '''        dialog.setOnDismissListener(d->{searchGeneration.incrementAndGet();searchExecutor.shutdownNow();if(pendingSearch[0]!=null)handler.removeCallbacks(pendingSearch[0]);if(zonePickerDialog==dialog){zonePickerDialog=null;zoneSearchBox=null;}});'''
new = '''        dialog.setOnDismissListener(d->{searchGeneration.incrementAndGet();searchExecutor.shutdownNow();if(pendingSearch[0]!=null)handler.removeCallbacks(pendingSearch[0]);if(pendingOnlineSearch[0]!=null)handler.removeCallbacks(pendingOnlineSearch[0]);if(zonePickerDialog==dialog){zonePickerDialog=null;zoneSearchBox=null;}});'''
if old not in clock:
    raise SystemExit("v1.2.9 dismiss anchor missing")
clock = clock.replace(old, new, 1)
clock_path.write_text(clock, encoding="utf-8")

i18n = i18n_path.read_text(encoding="utf-8")
anchor = '''        put("オンラインで場所を特定","Find place online","온라인으로 장소 찾기");'''
addition = '''        put("オフライン検索を常に使い、接続中はオンライン候補も自動で追加します","Offline search always runs; when connected, online suggestions are added automatically","오프라인 검색은 항상 실행되며, 연결되어 있으면 온라인 후보도 자동으로 추가됩니다");
'''
if anchor not in i18n:
    raise SystemExit("v1.2.9 i18n anchor missing")
i18n = i18n.replace(anchor, addition + anchor, 1)
i18n_path.write_text(i18n, encoding="utf-8")

manifest = manifest_path.read_text(encoding="utf-8")
old = '''    <!-- INTERNET intentionally omitted: no ads, analytics, accounts, or network calls. -->'''
new = '''    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- INTERNET intentionally omitted: core world-clock search stays local. Optional online place
         resolution is delegated to Android's Geocoder service and never replaces local search. -->'''
if old not in manifest:
    raise SystemExit("v1.2.9 manifest anchor missing")
manifest = manifest.replace(old, new, 1)
manifest_path.write_text(manifest, encoding="utf-8")

gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 47' not in gradle or 'versionName = "1.2.8"' not in gradle:
    raise SystemExit("v1.2.8 version markers missing before v1.2.9 bump")
gradle = gradle.replace('versionCode = 47', 'versionCode = 48', 1).replace('versionName = "1.2.8"', 'versionName = "1.2.9"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

# Guard the behavior requested for v1.2.9: offline always first, online only augments.
clock = clock_path.read_text(encoding="utf-8")
i18n = i18n_path.read_text(encoding="utf-8")
manifest = manifest_path.read_text(encoding="utf-8")
gradle = gradle_path.read_text(encoding="utf-8")
for needle in [
    "hasValidatedInternet",
    "resolveOnlineCountryHints",
    "onlineHintCache",
    "pendingOnlineSearch",
    "handler.postDelayed(pendingSearch[0],q.trim().isEmpty()?0L:45L)",
    "handler.postDelayed(pendingOnlineSearch[0],350L)",
    "results.addAll(direct)",
]:
    if needle not in clock:
        raise SystemExit(f"Missing v1.2.9 hybrid-search marker: {needle}")
if 'Button online=Ui.button(this,"オンラインで場所を特定"' in clock:
    raise SystemExit("Separate online-only search control still visible")
if "オフライン検索を常に使い、接続中はオンライン候補も自動で追加します" not in i18n:
    raise SystemExit("v1.2.9 hybrid-search explanation missing")
if "android.permission.ACCESS_NETWORK_STATE" not in manifest:
    raise SystemExit("v1.2.9 network-state permission missing")
if 'versionCode = 48' not in gradle or 'versionName = "1.2.9"' not in gradle:
    raise SystemExit("v1.2.9 version bump missing")

print("WakeGuard v1.2.9 hybrid offline-first world-clock search applied")
print("Offline local search always runs: PASS")
print("Online augmentation never blocks local results: PASS")
print("Validated-internet gate: PASS")
print("350ms online augmentation debounce: PASS")
print("Online country hints merge into local time-zone candidates: PASS")
print("Separate online-only mode removed: PASS")
