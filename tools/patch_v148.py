from pathlib import Path
import runpy

runpy.run_path("tools/patch_v147.py", run_name="__main__")

app = Path("WakeGuard/app")
java = app / 'src/main/java/jp/wakeguard/alarm'

# ClockActivity: timer media picker supports both audio and video.
p = java / 'ClockActivity.java'
s = p.read_text(encoding='utf-8')
s = s.replace('    private static final int REQ_TIMER_SOUND = 7116;\n    private static final int REQ_TIMER_NOTIFICATION = 7117;',
'''    private static final int REQ_TIMER_SOUND = 7116;\n    private static final int REQ_TIMER_NOTIFICATION = 7117;\n    private static final int REQ_TIMER_VIDEO = 7118;''',1)
old = r'''    private String timerSoundText(TimerStore.Entry e){
        String n=e==null?"":e.soundName;
        if(n==null||n.trim().isEmpty())return "音: 標準";
        n=n.trim(); if(n.length()>14)n=n.substring(0,13)+"…"; return "音: "+n;
    }

    private void pickTimerSound(long id){
        pendingTimerSoundId=id;
        android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,REQ_TIMER_SOUND);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"音声ファイルを開けません"),Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_TIMER_SOUND||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        long id=pendingTimerSoundId; pendingTimerSoundId=-1L; if(id<1000)return;
        android.net.Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        String display=I18n.tr(this,"選択した音");
        android.database.Cursor cur=null;
        try{cur=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);if(cur!=null&&cur.moveToFirst()){int ix=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0){String x=cur.getString(ix);if(x!=null&&!x.trim().isEmpty())display=x.trim();}}}catch(Throwable ignored){}finally{if(cur!=null)try{cur.close();}catch(Throwable ignored){}}
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        e.soundUri=uri.toString(); e.soundName=display; TimerStore.update(this,e); renderTimers();
        Toast.makeText(this,I18n.tr(this,"タイマー音: "+display),Toast.LENGTH_SHORT).show();
    }
'''
new = r'''    private String timerSoundText(TimerStore.Entry e){
        String n=e==null?"":e.soundName;
        if(e==null||e.soundUri==null||e.soundUri.trim().isEmpty()||n==null||n.trim().isEmpty())return "音: 標準";
        n=n.trim(); if(n.length()>14)n=n.substring(0,13)+"…";
        return isVideoMedia(e.soundUri)?"動画: "+n:"音: "+n;
    }

    private boolean isVideoMedia(String raw){
        if(raw==null||raw.trim().isEmpty())return false;
        try{String type=getContentResolver().getType(android.net.Uri.parse(raw));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}
        String x=raw.toLowerCase(java.util.Locale.ROOT);return x.matches(".*\\.(mp4|m4v|3gp|3gpp|webm|mkv|ts)([?#].*)?$");
    }

    private void pickTimerSound(long id){
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;
        new AlertDialog.Builder(this)
            .setTitle(I18n.tr(this,"タイマーの音・動画"))
            .setItems(new String[]{I18n.tr(this,"音声を選択"),I18n.tr(this,"動画を選択"),I18n.tr(this,"標準に戻す")},(d,which)->{
                if(which==0)pickTimerMedia(id,false);
                else if(which==1)pickTimerMedia(id,true);
                else {e.soundUri="";e.soundName="";TimerStore.update(this,e);renderTimers();}
            }).show();
    }

    private void pickTimerMedia(long id,boolean video){
        pendingTimerSoundId=id;
        android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType(video?"video/*":"audio/*");
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,video?REQ_TIMER_VIDEO:REQ_TIMER_SOUND);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,video?"動画ファイルを開けません":"音声ファイルを開けません"),Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        boolean video=requestCode==REQ_TIMER_VIDEO;
        if((requestCode!=REQ_TIMER_SOUND&&!video)||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        long id=pendingTimerSoundId; pendingTimerSoundId=-1L; if(id<1000)return;
        android.net.Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        String display=I18n.tr(this,video?"選択した動画":"選択した音");
        android.database.Cursor cur=null;
        try{cur=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);if(cur!=null&&cur.moveToFirst()){int ix=cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(ix>=0){String x=cur.getString(ix);if(x!=null&&!x.trim().isEmpty())display=x.trim();}}}catch(Throwable ignored){}finally{if(cur!=null)try{cur.close();}catch(Throwable ignored){}}
        TimerStore.Entry e=TimerStore.find(this,id); if(e==null)return;
        e.soundUri=uri.toString(); e.soundName=display; TimerStore.update(this,e); renderTimers();
        Toast.makeText(this,I18n.tr(this,(video?"タイマー動画: ":"タイマー音: ")+display),Toast.LENGTH_SHORT).show();
    }
'''
if old not in s:
    raise SystemExit('ClockActivity timer picker anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# TimerRingService: video timer uses full-screen intent; keep a fallback alarm until the video surface is ready.
p=java/'TimerRingService.java'
s=p.read_text(encoding='utf-8')
s=s.replace('    public static final String ACTION_START="jp.wakeguard.alarm.TIMER_RING_START",ACTION_STOP="jp.wakeguard.alarm.TIMER_RING_STOP";',
'''    public static final String ACTION_START="jp.wakeguard.alarm.TIMER_RING_START",ACTION_STOP="jp.wakeguard.alarm.TIMER_RING_STOP",ACTION_SILENCE="jp.wakeguard.alarm.TIMER_RING_SILENCE",ACTION_RESUME="jp.wakeguard.alarm.TIMER_RING_RESUME";''',1)
s=s.replace('    private MediaPlayer player;private ToneGenerator fallback;private Handler toneHandler;private Vibrator vibrator;private PowerManager.WakeLock wakeLock;',
'''    private MediaPlayer player;private ToneGenerator fallback;private Handler toneHandler;private Vibrator vibrator;private PowerManager.WakeLock wakeLock;\n    private String currentUri="",currentLabel="";private boolean currentVideo=false;''',1)
old='    @Override public int onStartCommand(Intent i,int flags,int startId){if(i!=null&&ACTION_STOP.equals(i.getAction())){stopRing();return START_NOT_STICKY;}String label=i==null?"":i.getStringExtra("label");String uri=i==null?"":i.getStringExtra("soundUri");startForeground(NOTIFY,buildNotification(label));startOutputs(uri);return START_NOT_STICKY;}'
new='    @Override public int onStartCommand(Intent i,int flags,int startId){if(i!=null&&ACTION_STOP.equals(i.getAction())){stopRing();return START_NOT_STICKY;}if(i!=null&&ACTION_SILENCE.equals(i.getAction())){stopOutputs();return START_NOT_STICKY;}if(i!=null&&ACTION_RESUME.equals(i.getAction())){startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}String label=i==null?"":i.getStringExtra("label");String uri=i==null?"":i.getStringExtra("soundUri");currentLabel=label==null?"":label;currentUri=uri==null?"":uri;currentVideo=isVideoUri(currentUri);startForeground(NOTIFY,buildNotification(currentLabel,currentUri));startOutputs(currentUri,currentVideo);return START_NOT_STICKY;}'
if old not in s: raise SystemExit('TimerRingService onStart anchor missing')
s=s.replace(old,new,1)
old='    private Notification buildNotification(String label){String title=(label==null||label.trim().isEmpty())?I18n.tr(this,"タイマー終了"):label.trim();Intent open=new Intent(this,ClockActivity.class).putExtra("mode","timer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent openPi=PendingIntent.getActivity(this,118002,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Intent stop=new Intent(this,TimerRingService.class).setAction(ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(this,118003,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(I18n.tr(this,"設定した時間になりました")).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_MAX).setOngoing(true).setContentIntent(openPi).addAction(new Notification.Action.Builder(null,I18n.tr(this,"タイマーを停止"),stopPi).build());return b.build();}'
new='    private Notification buildNotification(String label,String uri){String title=(label==null||label.trim().isEmpty())?I18n.tr(this,"タイマー終了"):label.trim();boolean video=isVideoUri(uri);Intent open=video?new Intent(this,TimerVideoActivity.class).putExtra("videoUri",uri).putExtra("label",title):new Intent(this,ClockActivity.class).putExtra("mode","timer");open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent openPi=PendingIntent.getActivity(this,118002,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Intent stop=new Intent(this,TimerRingService.class).setAction(ACTION_STOP);PendingIntent stopPi=PendingIntent.getService(this,118003,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(I18n.tr(this,video?"タップして動画を再生":"設定した時間になりました")).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setPriority(Notification.PRIORITY_MAX).setOngoing(true).setContentIntent(openPi).addAction(new Notification.Action.Builder(null,I18n.tr(this,"タイマーを停止"),stopPi).build());if(video)b.setFullScreenIntent(openPi,true);return b.build();}'
if old not in s: raise SystemExit('TimerRingService notification anchor missing')
s=s.replace(old,new,1)
old='    private void startOutputs(String custom){stopOutputs();try{PowerManager pm=getSystemService(PowerManager.class);if(pm!=null){wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WakeGuard:TimerRing");wakeLock.setReferenceCounted(false);wakeLock.acquire(30*60*1000L);}}catch(Throwable ignored){}\n        Uri u=null;try{if(custom!=null&&!custom.isEmpty())u=Uri.parse(custom);}catch(Throwable ignored){}if(u==null)u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);player=createPlayer(u);if(player==null)player=createPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));boolean playing=false;try{playing=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!playing)startFallback();try{vibrator=getSystemService(Vibrator.class);if(vibrator!=null&&vibrator.hasVibrator()){long[] pattern={0,700,250,700,250,1200};if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createWaveform(pattern,0));else vibrator.vibrate(pattern,0);}}catch(Throwable ignored){}\n    }'
new='    private void startOutputs(String custom,boolean video){stopOutputs();try{PowerManager pm=getSystemService(PowerManager.class);if(pm!=null){wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WakeGuard:TimerRing");wakeLock.setReferenceCounted(false);wakeLock.acquire(30*60*1000L);}}catch(Throwable ignored){}\n        Uri u=null;try{if(!video&&custom!=null&&!custom.isEmpty())u=Uri.parse(custom);}catch(Throwable ignored){}if(u==null)u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);player=createPlayer(u);if(player==null)player=createPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));boolean playing=false;try{playing=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!playing)startFallback();try{vibrator=getSystemService(Vibrator.class);if(vibrator!=null&&vibrator.hasVibrator()){long[] pattern={0,700,250,700,250,1200};if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createWaveform(pattern,0));else vibrator.vibrate(pattern,0);}}catch(Throwable ignored){}\n    }\n    private boolean isVideoUri(String raw){if(raw==null||raw.trim().isEmpty())return false;try{String type=getContentResolver().getType(Uri.parse(raw));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}String x=raw.toLowerCase(java.util.Locale.ROOT);return x.matches(".*\\\\.(mp4|m4v|3gp|3gpp|webm|mkv|ts)([?#].*)?$");}'
if old not in s: raise SystemExit('TimerRingService outputs anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# Dedicated local video alarm screen. Video loops until explicit stop. Leaving the screen resumes the fallback alarm.
(java/'TimerVideoActivity.java').write_text(r'''package jp.wakeguard.alarm;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Full-screen local video playback for a completed timer. No network is required. */
public class TimerVideoActivity extends Activity {
    private VideoView video;
    private boolean prepared=false,stopped=false,resumed=false;
    private String rawUri="";

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);Ui.prepareActivity(this);setVolumeControlStream(AudioManager.STREAM_ALARM);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        rawUri=getIntent()==null?"":getIntent().getStringExtra("videoUri");if(rawUri==null)rawUri="";
        String label=getIntent()==null?"":getIntent().getStringExtra("label");if(label==null||label.trim().isEmpty())label=I18n.tr(this,"タイマー終了");
        if(rawUri.trim().isEmpty()){finish();return;}

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);video.setBackgroundColor(Color.BLACK);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=Gravity.CENTER;root.addView(video,vp);

        TextView title=new TextView(this);title.setText(label);title.setTextColor(Color.WHITE);title.setTextSize(18);title.setGravity(Gravity.CENTER);title.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,14));title.setBackgroundColor(0x99000000);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(title,tp);
        Button stop=Ui.button(this,"タイマーを停止",true);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,Ui.dp(this,58),Gravity.BOTTOM);sp.setMargins(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,24));root.addView(stop,sp);stop.setOnClickListener(v->stopAndFinish());
        setContentView(root);

        video.setOnPreparedListener(mp->{
            prepared=true;
            try{mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());}catch(Throwable ignored){}
            try{mp.setLooping(true);mp.setVolume(1f,1f);}catch(Throwable ignored){}
            silenceFallback();try{video.start();}catch(Throwable ignored){}
        });
        video.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();prepared=false;resumeFallback();finish();return true;});
        try{video.setVideoURI(Uri.parse(rawUri));video.requestFocus();}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();resumeFallback();finish();}
    }

    private void serviceAction(String action){try{Intent i=new Intent(this,TimerRingService.class).setAction(action);startService(i);}catch(Throwable ignored){}}
    private void silenceFallback(){serviceAction(TimerRingService.ACTION_SILENCE);}
    private void resumeFallback(){if(!stopped)serviceAction(TimerRingService.ACTION_RESUME);}
    private void stopAndFinish(){stopped=true;try{video.stopPlayback();}catch(Throwable ignored){}serviceAction(TimerRingService.ACTION_STOP);finish();}

    @Override protected void onResume(){super.onResume();resumed=true;if(prepared&&!stopped){silenceFallback();try{video.start();}catch(Throwable ignored){}}}
    @Override protected void onPause(){resumed=false;if(prepared&&!stopped){try{video.pause();}catch(Throwable ignored){}resumeFallback();}super.onPause();}
    @Override public void onBackPressed(){stopAndFinish();}
    @Override protected void onDestroy(){try{if(stopped&&video!=null)video.stopPlayback();}catch(Throwable ignored){}super.onDestroy();}
}
''',encoding='utf-8')

# TimerReceiver fallback: a video uses default alarm sound and opens the video screen.
p=java/'TimerReceiver.java'
s=p.read_text(encoding='utf-8')
s=s.replace('    private static Uri chosenSound(TimerStore.Entry e){if(e!=null&&e.soundUri!=null&&!e.soundUri.isEmpty())try{return Uri.parse(e.soundUri);}catch(Throwable ignored){}return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);}',
'''    private static boolean isVideo(Context c,TimerStore.Entry e){if(e==null||e.soundUri==null||e.soundUri.isEmpty())return false;try{String type=c.getContentResolver().getType(Uri.parse(e.soundUri));if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}catch(Throwable ignored){}String x=e.soundUri.toLowerCase(java.util.Locale.ROOT);return x.matches(".*\\.(mp4|m4v|3gp|3gpp|webm|mkv|ts)([?#].*)?$");}
    private static Uri chosenSound(Context c,TimerStore.Entry e){if(!isVideo(c,e)&&e!=null&&e.soundUri!=null&&!e.soundUri.isEmpty())try{return Uri.parse(e.soundUri);}catch(Throwable ignored){}return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);}
    private static PendingIntent openCompleted(Context c,TimerStore.Entry e){if(isVideo(c,e)){Intent open=new Intent(c,TimerVideoActivity.class).putExtra("videoUri",e.soundUri).putExtra("label",e.label==null?"":e.label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(c,requestCode(e.id)+2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}return openTimer(c,e.id);}''',1)
s=s.replace('ch.setSound(chosenSound(e),aa);','ch.setSound(chosenSound(c,e),aa);',1)
old='String label=ring.label==null||ring.label.isEmpty()?I18n.tr(c,"タイマー終了"):ring.label;String channel=soundChannelId(ring);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label).setContentText(I18n.tr(c,"設定した時間になりました")).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(openTimer(c,id)).setAutoCancel(true);if(Build.VERSION.SDK_INT<26)b.setSound(chosenSound(ring));NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(notifyId(id),b.build());'
new='String label=ring.label==null||ring.label.isEmpty()?I18n.tr(c,"タイマー終了"):ring.label;String channel=soundChannelId(ring);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel):new Notification.Builder(c);PendingIntent open=openCompleted(c,ring);b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label).setContentText(I18n.tr(c,isVideo(c,ring)?"タップして動画を再生":"設定した時間になりました")).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open).setAutoCancel(true);if(isVideo(c,ring))b.setFullScreenIntent(open,true);if(Build.VERSION.SDK_INT<26)b.setSound(chosenSound(c,ring));NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.notify(notifyId(id),b.build());'
if old not in s: raise SystemExit('TimerReceiver completion anchor missing')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')

# Manifest: register the video timer activity as a lock-screen-capable alarm surface.
p=app/'src/main/AndroidManifest.xml'
s=p.read_text(encoding='utf-8')
anchor='        <activity android:name=".ClockSettingsActivity" android:exported="false" />\n'
addition=anchor+'''        <activity\n            android:name=".TimerVideoActivity"\n            android:excludeFromRecents="true"\n            android:exported="false"\n            android:launchMode="singleTop"\n            android:showWhenLocked="true"\n            android:turnScreenOn="true" />\n'''
if anchor not in s: raise SystemExit('Manifest activity anchor missing')
s=s.replace(anchor,addition,1)
p.write_text(s,encoding='utf-8')

# I18n additions, including dynamic video labels.
p=java/'I18n.java'
s=p.read_text(encoding='utf-8')
anchor='        put("音: 標準","Sound: Default","소리: 기본"); put("選択した音","Selected sound","선택한 소리"); put("音声ファイルを開けません","Unable to open the audio file","오디오 파일을 열 수 없습니다");'
addition=anchor+'\n        put("タイマーの音・動画","Timer sound / video","타이머 소리 / 동영상"); put("音声を選択","Choose audio","오디오 선택"); put("動画を選択","Choose video","동영상 선택"); put("標準に戻す","Use default","기본값으로 되돌리기");\n        put("選択した動画","Selected video","선택한 동영상"); put("動画ファイルを開けません","Unable to open the video file","동영상 파일을 열 수 없습니다"); put("動画を再生できません","Unable to play this video","이 동영상을 재생할 수 없습니다"); put("タップして動画を再生","Tap to play the video","탭하여 동영상 재생");'
if anchor not in s: raise SystemExit('I18n timer media anchor missing')
s=s.replace(anchor,addition,1)
anchor='        m=Pattern.compile("^音: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"소리: ":"Sound: ")+m.group(1);\n        m=Pattern.compile("^タイマー音: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"타이머 소리: ":"Timer sound: ")+m.group(1);'
addition='        m=Pattern.compile("^音: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"소리: ":"Sound: ")+m.group(1);\n        m=Pattern.compile("^動画: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"동영상: ":"Video: ")+m.group(1);\n        m=Pattern.compile("^タイマー音: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"타이머 소리: ":"Timer sound: ")+m.group(1);\n        m=Pattern.compile("^タイマー動画: (.+)$",Pattern.DOTALL).matcher(s);if(m.matches())return (ko?"타이머 동영상: ":"Timer video: ")+m.group(1);'
if anchor not in s: raise SystemExit('I18n dynamic label anchor missing')
s=s.replace(anchor,addition,1)
p.write_text(s,encoding='utf-8')

# Version bump from v1.4.7.
p=app/'build.gradle.kts'
s=p.read_text(encoding='utf-8')
if 'versionCode = 58' not in s or 'versionName = "1.4.7"' not in s: raise SystemExit('v1.4.7 version markers missing')
s=s.replace('versionCode = 58','versionCode = 59',1).replace('versionName = "1.4.7"','versionName = "1.4.8"',1)
p.write_text(s,encoding='utf-8')

# Verification
clock=(java/'ClockActivity.java').read_text(encoding='utf-8')
ring=(java/'TimerRingService.java').read_text(encoding='utf-8')
video=(java/'TimerVideoActivity.java').read_text(encoding='utf-8')
receiver=(java/'TimerReceiver.java').read_text(encoding='utf-8')
manifest=(app/'src/main/AndroidManifest.xml').read_text(encoding='utf-8')
for needle in ['REQ_TIMER_VIDEO','setType(video?"video/*":"audio/*")','"動画: "+n','タイマーの音・動画']:
    if needle not in clock: raise SystemExit('Missing ClockActivity media marker: '+needle)
for needle in ['ACTION_SILENCE','ACTION_RESUME','setFullScreenIntent(openPi,true)','startOutputs(currentUri,currentVideo)']:
    if needle not in ring: raise SystemExit('Missing TimerRingService video marker: '+needle)
for needle in ['extends Activity','VideoView','setVideoURI','setLooping(true)','AudioAttributes.USAGE_ALARM','resumeFallback()']:
    if needle not in video: raise SystemExit('Missing TimerVideoActivity marker: '+needle)
for needle in ['openCompleted','TimerVideoActivity.class','setFullScreenIntent(open,true)','chosenSound(c,ring)']:
    if needle not in receiver: raise SystemExit('Missing TimerReceiver video marker: '+needle)
if '.TimerVideoActivity' not in manifest: raise SystemExit('TimerVideoActivity manifest entry missing')
print('WakeGuard v1.4.8 local timer video playback patch applied')
