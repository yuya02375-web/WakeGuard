from pathlib import Path

p = Path('WakeGuard/app/src/main/java/jp/wakeguard/alarm/WorldCityCatalog.java')
s = p.read_text(encoding='utf-8')
s = s.replace('import java.util.Map;\n', '')
old = '''    private static final LinkedHashMap<String,ArrayList<Entry>> QUERY_CACHE=new LinkedHashMap<String,ArrayList<Entry>>(48,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,ArrayList<Entry>> e){return size()>48;}
    };'''
new = '''    private static final LinkedHashMap<String,ArrayList<Entry>> QUERY_CACHE=new LinkedHashMap<>(48,0.75f,true);'''
assert old in s, 'query cache declaration anchor missing'
s = s.replace(old, new, 1)
old = '        synchronized(QUERY_LOCK){QUERY_CACHE.put(key,new ArrayList<>(out));}\n'
new = '''        synchronized(QUERY_LOCK){
            QUERY_CACHE.put(key,new ArrayList<>(out));
            while(QUERY_CACHE.size()>48){String oldest=QUERY_CACHE.keySet().iterator().next();QUERY_CACHE.remove(oldest);}
        }
'''
assert old in s, 'query cache put anchor missing'
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
assert 'removeEldestEntry' not in s
assert 'QUERY_CACHE.size()>48' in s
print('IGNIDO Wake Android v1.7.9 compile fix applied')
