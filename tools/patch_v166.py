from pathlib import Path
import re

app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8')
s=re.sub(r'versionCode = \d+','versionCode = 77',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.6.6"',s)
p.write_text(s,encoding='utf-8')

p=j/'TimerRingService.java'; s=p.read_text(encoding='utf-8')
old='''    private String currentUri="",currentLabel="",currentMediaName="";private boolean currentVideo=false;'''
new='''    private String currentUri="",currentLabel="",currentMediaName="";private boolean currentVideo=false;private Handler videoDelayHandler;private boolean timerVideoReady=false;'''
if old not in s: raise SystemExit('timer fields anchor missing')
s=s.replace(old,new,1)
old='''    @Override public void onCreate(){super.onCreate();ensureChannel();toneHandler=new Handler(Looper.getMainLooper());}'''
new='''    @Override public void onCreate(){super.onCreate();ensureChannel();toneHandler=new Handler(Looper.getMainLooper());videoDelayHandler=new Handler(Looper.getMainLooper());}'''
if old not in s: raise SystemExit('timer onCreate anchor missing')
s=s.replace(old,new,1)
old='''    @Override public int onStartCommand(Intent i,int flags,int startId){if(i!=null&&ACTION_STOP.equals(i.getAction())){stopRing();return START_NOT_STICKY;}if(i!=null&&ACTION_SILENCE.equals(i.getAction())){stopOutputs();return START_NOT_STICKY;}if(i!=null&&ACTION_RESUME.equals(i.getAction())){startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}String label=i==null?"":i.getStringExtra("label");String uri=i==null?"":i.getStringExtra("soundUri");String mediaName=i==null?"":i.getStringExtra("mediaName");currentLabel=label==null?"":label;currentUri=uri==null?"":uri;currentMediaName=mediaName==null?"":mediaName;currentVideo=isVideoUri(currentUri,currentMediaName);startForeground(NOTIFY,buildNotification(currentLabel,currentUri,currentMediaName));startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}'''
new='''    @Override public int onStartCommand(Intent i,int flags,int startId){if(i!=null&&ACTION_STOP.equals(i.getAction())){stopRing();return START_NOT_STICKY;}if(i!=null&&ACTION_SILENCE.equals(i.getAction())){timerVideoReady=true;cancelVideoDelay();stopServiceAudioOnly();stopVibrationOnly();return START_NOT_STICKY;}if(i!=null&&ACTION_RESUME.equals(i.getAction())){timerVideoReady=false;cancelVideoDelay();if(currentVideo)startVideoFallbackNow();else startOutputs(currentUri,false);return START_NOT_STICKY;}String label=i==null?"":i.getStringExtra("label");String uri=i==null?"":i.getStringExtra("soundUri");String mediaName=i==null?"":i.getStringExtra("mediaName");currentLabel=label==null?"":label;currentUri=uri==null?"":uri;currentMediaName=mediaName==null?"":mediaName;currentVideo=isVideoUri(currentUri,currentMediaName);timerVideoReady=false;startForeground(NOTIFY,buildNotification(currentLabel,currentUri,currentMediaName));startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}'''
if old not in s: raise SystemExit('timer onStart anchor missing')
s=s.replace(old,new,1)
pat=r'''    private void startOutputs\(String custom,boolean video\)\{.*?\n    \}\n'''
repl='''    private void startOutputs(String custom,boolean video){
        stopOutputs();acquireRingWakeLock();startRingVibration();
        if(video){timerVideoReady=false;scheduleVideoDelay();return;}
        startAudioNow(custom);
    }
    private void acquireRingWakeLock(){try{if(wakeLock!=null&&wakeLock.isHeld())return;PowerManager pm=getSystemService(PowerManager.class);if(pm!=null){wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WakeGuard:TimerRing");wakeLock.setReferenceCounted(false);wakeLock.acquire(30*60*1000L);}}catch(Throwable ignored){}}
    private void startRingVibration(){try{if(vibrator!=null)return;vibrator=getSystemService(Vibrator.class);if(vibrator!=null&&vibrator.hasVibrator()){long[] pattern={0,700,250,700,250,1200};if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createWaveform(pattern,0));else vibrator.vibrate(pattern,0);}}catch(Throwable ignored){}}
    private void stopVibrationOnly(){if(vibrator!=null){try{vibrator.cancel();}catch(Throwable ignored){}vibrator=null;}}
    private void startAudioNow(String custom){Uri u=null;try{if(custom!=null&&!custom.isEmpty())u=Uri.parse(custom);}catch(Throwable ignored){}if(u==null)u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);player=createPlayer(u);if(player==null)player=createPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));boolean playing=false;try{playing=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!playing)startFallback();}
    private void stopServiceAudioOnly(){if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}if(fallback!=null){try{fallback.stopTone();fallback.release();}catch(Throwable ignored){}fallback=null;}if(toneHandler!=null)toneHandler.removeCallbacksAndMessages(null);}
    private void cancelVideoDelay(){try{if(videoDelayHandler!=null)videoDelayHandler.removeCallbacksAndMessages(null);}catch(Throwable ignored){}}
    private void scheduleVideoDelay(){cancelVideoDelay();if(videoDelayHandler==null)videoDelayHandler=new Handler(Looper.getMainLooper());videoDelayHandler.postDelayed(()->{if(currentVideo&&!timerVideoReady)startVideoFallbackNow();},5000L);}
    private void startVideoFallbackNow(){cancelVideoDelay();if(timerVideoReady)return;stopServiceAudioOnly();acquireRingWakeLock();startRingVibration();startAudioNow("");}
'''
s,n=re.subn(pat,repl,s,count=1,flags=re.S)
if n!=1: raise SystemExit('timer startOutputs anchor missing')
old='''    private void stopOutputs(){if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}if(fallback!=null){try{fallback.stopTone();fallback.release();}catch(Throwable ignored){}fallback=null;}if(toneHandler!=null)toneHandler.removeCallbacksAndMessages(null);if(vibrator!=null){try{vibrator.cancel();}catch(Throwable ignored){}vibrator=null;}if(wakeLock!=null){try{if(wakeLock.isHeld())wakeLock.release();}catch(Throwable ignored){}wakeLock=null;}}'''
new='''    private void stopOutputs(){stopServiceAudioOnly();cancelVideoDelay();timerVideoReady=false;stopVibrationOnly();if(wakeLock!=null){try{if(wakeLock.isHeld())wakeLock.release();}catch(Throwable ignored){}wakeLock=null;}}'''
if old not in s: raise SystemExit('timer stopOutputs anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

assert 'versionName = "1.6.6"' in (app/'build.gradle.kts').read_text(encoding='utf-8')
assert 'scheduleVideoDelay' in s
assert 'startVideoFallbackNow' in s
assert 'if(video){timerVideoReady=false;scheduleVideoDelay();return;}' in s
print('WakeGuard v1.6.6 timer video no-default-audio delay patch applied')
