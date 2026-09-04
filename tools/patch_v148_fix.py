from pathlib import Path
import runpy

# Apply the full v1.4.8 feature patch first.
runpy.run_path("tools/patch_v148.py", run_name="__main__")

# Compile fix: avoid Java-regex escaping entirely when falling back from MIME type
# to a filename extension check for a timer video URI.
java = Path("WakeGuard/app/src/main/java/jp/wakeguard/alarm")
p = java / "TimerReceiver.java"
s = p.read_text(encoding="utf-8")
start = s.index("    private static boolean isVideo(Context c,TimerStore.Entry e){")
end = s.index("\n    private static Uri chosenSound", start)
replacement = '''    private static boolean isVideo(Context c,TimerStore.Entry e){
        if(e==null||e.soundUri==null||e.soundUri.isEmpty())return false;
        try{String type=c.getContentResolver().getType(Uri.parse(e.soundUri));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}
        String x=e.soundUri.toLowerCase(java.util.Locale.ROOT);
        int q=x.indexOf('?');if(q>=0)x=x.substring(0,q);int h=x.indexOf('#');if(h>=0)x=x.substring(0,h);
        return x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts");
    }'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding="utf-8")

# Verify the broken escape is gone and the intended fallback remains.
out = p.read_text(encoding="utf-8")
for needle in ['startsWith("video/")','x.endsWith(".mp4")','x.endsWith(".webm")','openCompleted']:
    if needle not in out:
        raise SystemExit("Missing v1.4.8 compile-fix marker: "+needle)
if 'x.matches(".*\\.' in out:
    raise SystemExit("Illegal/fragile TimerReceiver video regex still present")
print("WakeGuard v1.4.8 TimerReceiver compile fix applied")
