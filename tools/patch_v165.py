from pathlib import Path
import re

app = Path('WakeGuard/app')
j = app / 'src/main/java/jp/wakeguard/alarm'

def read(p): return p.read_text(encoding='utf-8')
def write(p,s): p.write_text(s,encoding='utf-8')
def replace_once(s, old, new, name):
    if old not in s:
        raise SystemExit(name+' anchor missing')
    return s.replace(old,new,1)
def sub_once(s, pattern, repl, name, flags=0):
    out,n=re.subn(pattern,repl,s,count=1,flags=flags)
    if n!=1: raise SystemExit(name+' anchor missing')
    return out

# v1.6.5: fix v1.6.4 compile issue and make selected videos reliable.
p=app/'build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+','versionCode = 76',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.6.5"',s)
write(p,s)

# Notification silence is controlled by the Android 8+ notification channel.
for name in ['StopwatchNotification.java','AlarmReminderReceiver.java']:
    p=j/name; s=read(p)
    s=s.replace('        if(Build.VERSION.SDK_INT>=26)b.setSilent(true);\n','')
    write(p,s)

# Alarm editor: copy selected video into app-private storage, preserving a video extension.
# This removes dependence on a document-provider URI permission and gives deterministic video detection.
p=j/'AlarmEditorActivity.java'; s=read(p)
s=replace_once(s,
'''        soundStatus.setText(I18n.tr(this,isVideoMedia(draft.soundUri)?"動画":"音声")+"  ·  "+draft.soundName+"  ·  "+size+"  ·  "+dur);''',
'''        soundStatus.setText(I18n.tr(this,isVideoMedia(draft.soundUri,draft.soundName)?"動画":"音声")+"  ·  "+draft.soundName+"  ·  "+size+"  ·  "+dur);''','editor render media type')
s=sub_once(s,
    r'    private void useVideo\(Uri uri\)\{.*?\n\n    private void promptFullScreenVideoPermissionIfNeeded\(\)\{',
'''    private void useVideo(Uri uri){
        File out=null;
        try{
            String name=fileName(uri);String ext=videoExtension(uri,name);
            File dir=new File(getFilesDir(),"alarm_videos");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");
            out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+ext);
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){
                if(in==null)throw new IOException("open");byte[]buf=new byte[131072];int n;while((n=in.read(buf))>0)os.write(buf,0,n);
            }
            long dur=-1;android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever();
            try{m.setDataSource(out.getAbsolutePath());String x=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(x!=null)dur=Long.parseLong(x);}finally{try{m.release();}catch(Throwable ignored){}}
            String old=draft.soundUri;draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;cleanupOwnedVideo(old);renderSound();promptFullScreenVideoPermissionIfNeeded();
        }catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();}
    }
    private String videoExtension(Uri uri,String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);String[] exts={".mp4",".m4v",".3gp",".3gpp",".webm",".mkv",".ts"};for(String e:exts)if(n.endsWith(e))return e;
        try{String t=getContentResolver().getType(uri);if(t!=null){t=t.toLowerCase(Locale.ROOT);if(t.contains("webm"))return ".webm";if(t.contains("3gpp"))return ".3gp";if(t.contains("matroska"))return ".mkv";}}catch(Throwable ignored){}
        return ".mp4";
    }
    private void cleanupOwnedVideo(String raw){
        try{if(raw==null||raw.isEmpty())return;File f=new File(raw);File dir=new File(getFilesDir(),"alarm_videos");if(f.getParentFile()!=null&&f.getParentFile().equals(dir)&&f.exists())f.delete();}catch(Throwable ignored){}
    }

    private void promptFullScreenVideoPermissionIfNeeded(){''','editor copy video',re.S)
s=sub_once(s,
    r'    private boolean isVideoMedia\(String raw\)\{.*?\n    private String fileName\(Uri uri\)',
'''    private boolean isVideoMedia(String raw,String name){
        if(raw!=null&&!raw.trim().isEmpty()){
            try{if(raw.startsWith("content:")){String t=getContentResolver().getType(Uri.parse(raw));if(t!=null&&t.toLowerCase(Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}
            String x=raw.toLowerCase(Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;
        }
        String n=name==null?"":name.toLowerCase(Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");
    }
    private String fileName(Uri uri)''','editor isVideo',re.S)
write(p,s)

# Alarm service: video selection must be detected by URI OR stored display name.
# Do not immediately play the ordinary alarm sound for a selected video. Give the video screen
# a short window to start, then fall back only if it cannot render/play.
p=j/'AlarmService.java'; s=read(p)
s=replace_once(s,'    private Handler fullStopHandler;','    private Handler fullStopHandler;\n    private Handler videoFallbackHandler;\n    private boolean videoPlaybackReady=false;','service fields')
s=replace_once(s,'        fullStopHandler = new Handler(Looper.getMainLooper());','        fullStopHandler = new Handler(Looper.getMainLooper());\n        videoFallbackHandler = new Handler(Looper.getMainLooper());','service onCreate')
s=replace_once(s,
'''        if(ACTION_VIDEO_READY.equals(action)){if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))silenceServiceAudioForVideo();return START_STICKY;}
        if(ACTION_VIDEO_FALLBACK.equals(action)){if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))startVideoFallbackAudio();return START_STICKY;}''',
'''        if(ACTION_VIDEO_READY.equals(action)){videoPlaybackReady=true;cancelVideoFallback();if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))silenceServiceAudioForVideo();return START_STICKY;}
        if(ACTION_VIDEO_FALLBACK.equals(action)){videoPlaybackReady=false;cancelVideoFallback();if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))startVideoFallbackAudio();return START_STICKY;}''','service video actions')
s=replace_once(s,'            Prefs.sessionSilenced(this,false);','            Prefs.sessionSilenced(this,false);\n            videoPlaybackReady=false;\n            cancelVideoFallback();','service reset video state')
s=sub_once(s,
    r'    private boolean isActiveVideo\(\)\{.*?\n    private void startAlarmOutputs\(\) \{',
'''    private boolean isActiveVideo(){return isVideoMedia(AlarmProfiles.soundUri(this,Prefs.activeAlarmId(this)),AlarmProfiles.soundName(this,Prefs.activeAlarmId(this)));}
    private boolean isVideoMedia(String raw,String name){
        if(raw!=null&&!raw.trim().isEmpty()){
            try{if(raw.startsWith("content:")){String t=getContentResolver().getType(Uri.parse(raw));if(t!=null&&t.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}
            String x=raw.toLowerCase(java.util.Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;
        }
        String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");
    }
    private void cancelVideoFallback(){try{if(videoFallbackHandler!=null)videoFallbackHandler.removeCallbacksAndMessages(null);}catch(Throwable ignored){}}
    private void scheduleVideoFallback(){cancelVideoFallback();if(videoFallbackHandler==null)videoFallbackHandler=new Handler(Looper.getMainLooper());videoFallbackHandler.postDelayed(()->{if(Prefs.active(AlarmService.this)&&isActiveVideo()&&!Prefs.sessionSilenced(AlarmService.this)&&!videoPlaybackReady)startVideoFallbackAudio();},5000L);}
    private void silenceServiceAudioForVideo(){if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}stopFallbackTone();}
    private void startVideoFallbackAudio(){if(videoPlaybackReady)return;prepareAlarmVolume();if(player==null)player=createPlayer("");boolean ok=false;try{ok=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!ok)startFallbackTone();}

    private void startAlarmOutputs() {''','service video helpers',re.S)
s=replace_once(s,
'''        prepareAlarmVolume();

        if (player == null) {
            String chosen=AlarmProfiles.soundUri(this, Prefs.activeAlarmId(this));
            player = createPlayer(isVideoMedia(chosen)?"":chosen);
            if (player == null) player = createPlayer("");
        }
        boolean playing = false;
        try { playing = player != null && player.isPlaying(); } catch (Throwable ignored) {}
        if (!playing) startFallbackTone();
''',
'''        prepareAlarmVolume();

        if(isActiveVideo()){
            videoPlaybackReady=false;
            silenceServiceAudioForVideo();
            scheduleVideoFallback();
        }else{
            cancelVideoFallback();
            if (player == null) {
                String chosen=AlarmProfiles.soundUri(this, Prefs.activeAlarmId(this));
                player = createPlayer(chosen);
                if (player == null) player = createPlayer("");
            }
            boolean playing = false;
            try { playing = player != null && player.isPlaying(); } catch (Throwable ignored) {}
            if (!playing) startFallbackTone();
        }
''','service start video audio')
s=replace_once(s,'        try { if (fullStopHandler != null) fullStopHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}','        try { if (fullStopHandler != null) fullStopHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}\n        cancelVideoFallback();\n        videoPlaybackReady=false;','service cleanup video fallback')
write(p,s)

# Alarm screen: support pre-1.6.5 content:// selections even when provider MIME is missing,
# using the stored display name as a second signal.
p=j/'AlarmActivity.java'; s=read(p)
s=sub_once(s,
    r'    private boolean isVideoAlarm\(\)\{.*?\n    private void startAlarmVideo\(\)\{',
'''    private boolean isVideoAlarm(){String raw=alarmMediaRaw();String name=AlarmProfiles.soundName(this,Prefs.activeAlarmId(this));if(raw.trim().isEmpty())return false;try{if(raw.startsWith("content:")){String t=getContentResolver().getType(android.net.Uri.parse(raw));if(t!=null&&t.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}String x=raw.toLowerCase(java.util.Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");}
    private void startAlarmVideo(){''','alarm activity classify',re.S)
write(p,s)

# Timer picker: copy selected videos locally too. Existing content:// videos still remain supported.
p=j/'ClockActivity.java'; s=read(p)
s=replace_once(s,
'''        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        e.soundUri=uri.toString(); e.soundName=display; TimerStore.update(this,e); renderTimers();
        Toast.makeText(this,I18n.tr(this,(video?"タイマー動画: ":"タイマー音: ")+display),Toast.LENGTH_SHORT).show();''',
'''        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        if(video){if(!storeTimerVideo(e,uri,display))return;}else{e.soundUri=uri.toString();e.soundName=display;TimerStore.update(this,e);}
        renderTimers();Toast.makeText(this,I18n.tr(this,(video?"タイマー動画: ":"タイマー音: ")+display),Toast.LENGTH_SHORT).show();''','timer result store')
insert='''
    private boolean storeTimerVideo(TimerStore.Entry e,android.net.Uri uri,String display){
        java.io.File out=null;try{
            String lower=display==null?"":display.toLowerCase(java.util.Locale.ROOT);String ext=".mp4";String[] xs={".mp4",".m4v",".3gp",".3gpp",".webm",".mkv",".ts"};for(String x:xs)if(lower.endsWith(x)){ext=x;break;}
            java.io.File dir=new java.io.File(getFilesDir(),"timer_videos");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("mkdir");out=new java.io.File(dir,"timer_"+e.id+"_"+System.currentTimeMillis()+ext);
            try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.OutputStream os=new java.io.FileOutputStream(out)){if(in==null)throw new java.io.IOException("open");byte[]buf=new byte[131072];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}
            String old=e.soundUri;e.soundUri=out.getAbsolutePath();e.soundName=display;TimerStore.update(this,e);cleanupTimerVideo(old);return true;
        }catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();return false;}
    }
    private void cleanupTimerVideo(String raw){try{if(raw==null||raw.isEmpty())return;java.io.File f=new java.io.File(raw),dir=new java.io.File(getFilesDir(),"timer_videos");if(f.getParentFile()!=null&&f.getParentFile().equals(dir)&&f.exists())f.delete();}catch(Throwable ignored){}}
'''
anchor='''    private String formatStopwatch(long ms) {'''
if anchor not in s: raise SystemExit('timer helper insert anchor missing')
s=s.replace(anchor,insert+'\n'+anchor,1)
write(p,s)

# Timer service: classify a video using URI/path OR its saved display name.
p=j/'TimerRingService.java'; s=read(p)
s=replace_once(s,'    private String currentUri="",currentLabel="";private boolean currentVideo=false;','    private String currentUri="",currentLabel="",currentMediaName="";private boolean currentVideo=false;','timer service fields')
s=replace_once(s,
'''    public static boolean start(Context c,TimerStore.Entry e){if(e==null)return false;Intent i=new Intent(c,TimerRingService.class).setAction(ACTION_START).putExtra("timerId",e.id).putExtra("label",e.label==null?"":e.label).putExtra("soundUri",e.soundUri==null?"":e.soundUri);try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);return true;}catch(Throwable ignored){return false;}}''',
'''    public static boolean start(Context c,TimerStore.Entry e){if(e==null)return false;Intent i=new Intent(c,TimerRingService.class).setAction(ACTION_START).putExtra("timerId",e.id).putExtra("label",e.label==null?"":e.label).putExtra("soundUri",e.soundUri==null?"":e.soundUri).putExtra("mediaName",e.soundName==null?"":e.soundName);try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);return true;}catch(Throwable ignored){return false;}}''','timer service start intent')
s=replace_once(s,
'''String label=i==null?"":i.getStringExtra("label");String uri=i==null?"":i.getStringExtra("soundUri");currentLabel=label==null?"":label;currentUri=uri==null?"":uri;currentVideo=isVideoUri(currentUri);startForeground(NOTIFY,buildNotification(currentLabel,currentUri));startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}''',
'''String label=i==null?"":i.getStringExtra("label");String uri=i==null?"":i.getStringExtra("soundUri");String mediaName=i==null?"":i.getStringExtra("mediaName");currentLabel=label==null?"":label;currentUri=uri==null?"":uri;currentMediaName=mediaName==null?"":mediaName;currentVideo=isVideoUri(currentUri,currentMediaName);startForeground(NOTIFY,buildNotification(currentLabel,currentUri,currentMediaName));startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}''','timer service onStart')
s=replace_once(s,'    private Notification buildNotification(String label,String uri){String title=(label==null||label.trim().isEmpty())?I18n.tr(this,"タイマー終了"):label.trim();boolean video=isVideoUri(uri);','    private Notification buildNotification(String label,String uri,String mediaName){String title=(label==null||label.trim().isEmpty())?I18n.tr(this,"タイマー終了"):label.trim();boolean video=isVideoUri(uri,mediaName);','timer service notification signature')
s=sub_once(s,
    r'    private boolean isVideoUri\(String raw\)\{.*?\n    private MediaPlayer createPlayer',
'''    private boolean isVideoUri(String raw,String name){if(raw!=null&&!raw.trim().isEmpty()){try{String type=getContentResolver().getType(Uri.parse(raw));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}String x=raw.toLowerCase(java.util.Locale.ROOT);if(x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts"))return true;}String n=name==null?"":name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".mp4")||n.endsWith(".m4v")||n.endsWith(".3gp")||n.endsWith(".3gpp")||n.endsWith(".webm")||n.endsWith(".mkv")||n.endsWith(".ts");}
    private MediaPlayer createPlayer''','timer service classify',re.S)
write(p,s)

# Timer video screen: move from legacy VideoView to the same Media3 stack used by alarm video.
p=j/'TimerVideoActivity.java'; s=read(p)
s=s.replace('    private VideoView video;\n    private boolean prepared=false,stopped=false,resumed=false;', '    private androidx.media3.ui.PlayerView videoView;\n    private androidx.media3.exoplayer.ExoPlayer player;\n    private boolean prepared=false,stopped=false,resumed=false;',1)
s=replace_once(s,
'''        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);video.setBackgroundColor(Color.BLACK);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=Gravity.CENTER;root.addView(video,vp);
''',
'''        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        videoView=new androidx.media3.ui.PlayerView(this);videoView.setBackgroundColor(Color.BLACK);videoView.setShutterBackgroundColor(Color.BLACK);videoView.setUseController(false);videoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);videoView.setKeepScreenOn(true);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=Gravity.CENTER;root.addView(videoView,vp);
''','timer video view')
s=sub_once(s,
    r'        video\.setOnPreparedListener\(mp->\{.*?\n        try\{video\.setVideoURI\(Uri\.parse\(rawUri\)\);video\.requestFocus\(\);\}catch\(Throwable t\)\{.*?\n    \}',
'''        try{
            player=new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            androidx.media3.common.AudioAttributes aa=new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_ALARM).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build();
            player.setAudioAttributes(aa,true);player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);player.setPlayWhenReady(false);videoView.setPlayer(player);
            player.addListener(new androidx.media3.common.Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(state==androidx.media3.common.Player.STATE_READY){prepared=true;if(resumed&&!stopped){silenceFallback();try{player.play();}catch(Throwable ignored){}}}}
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error){prepared=false;Toast.makeText(TimerVideoActivity.this,I18n.tr(TimerVideoActivity.this,"動画を再生できません"),Toast.LENGTH_LONG).show();resumeFallback();finish();}
            });
            Uri u=rawUri.startsWith("/")?Uri.fromFile(new java.io.File(rawUri)):Uri.parse(rawUri);player.setMediaItem(androidx.media3.common.MediaItem.fromUri(u));player.prepare();
        }catch(Throwable t){Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();resumeFallback();finish();}
    }''','timer media3 player',re.S)
s=s.replace('private void stopAndFinish(){stopped=true;try{video.stopPlayback();}catch(Throwable ignored){}serviceAction(TimerRingService.ACTION_STOP);finish();}', 'private void stopAndFinish(){stopped=true;releasePlayer();serviceAction(TimerRingService.ACTION_STOP);finish();}',1)
s=s.replace('''    @Override protected void onResume(){super.onResume();resumed=true;if(prepared&&!stopped){silenceFallback();try{video.start();}catch(Throwable ignored){}}}
    @Override protected void onPause(){resumed=false;if(prepared&&!stopped){try{video.pause();}catch(Throwable ignored){}resumeFallback();}super.onPause();}
    @Override public void onBackPressed(){stopAndFinish();}
    @Override protected void onDestroy(){try{if(stopped&&video!=null)video.stopPlayback();}catch(Throwable ignored){}super.onDestroy();}''',
'''    @Override protected void onResume(){super.onResume();resumed=true;if(prepared&&!stopped){silenceFallback();try{player.play();}catch(Throwable ignored){}}}
    @Override protected void onPause(){resumed=false;if(prepared&&!stopped){try{player.pause();}catch(Throwable ignored){}resumeFallback();}super.onPause();}
    @Override public void onBackPressed(){stopAndFinish();}
    private void releasePlayer(){prepared=false;if(videoView!=null)try{videoView.setPlayer(null);}catch(Throwable ignored){}if(player!=null){try{player.release();}catch(Throwable ignored){}player=null;}}
    @Override protected void onDestroy(){releasePlayer();super.onDestroy();}''',1)
write(p,s)

# Validation guards.
assert 'versionName = "1.6.5"' in read(app/'build.gradle.kts')
assert 'setSilent(true)' not in read(j/'StopwatchNotification.java')
assert 'setSilent(true)' not in read(j/'AlarmReminderReceiver.java')
assert 'alarm_videos' in read(j/'AlarmEditorActivity.java')
assert 'scheduleVideoFallback' in read(j/'AlarmService.java')
assert 'AlarmProfiles.soundName' in read(j/'AlarmActivity.java')
assert 'timer_videos' in read(j/'ClockActivity.java')
assert 'androidx.media3.exoplayer.ExoPlayer' in read(j/'TimerVideoActivity.java')
print('WakeGuard v1.6.5 reliable local alarm/timer video playback patch applied')
