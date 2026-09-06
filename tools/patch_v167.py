from pathlib import Path
import re

app=Path('WakeGuard/app')
j=app/'src/main/java/jp/wakeguard/alarm'

def read(p): return p.read_text(encoding='utf-8')
def write(p,s): p.write_text(s,encoding='utf-8')
def rep(s,old,new,name):
    if old not in s: raise SystemExit(name+' anchor missing')
    return s.replace(old,new,1)

# v1.6.7
p=app/'build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+','versionCode = 78',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.6.7"',s)
write(p,s)

# Timer UI: tap toggles digital/analog inline, long press opens large face.
p=j/'ClockActivity.java'; s=read(p)
s=rep(s,
'''        if("world".equals(mode))applyWorldDisplayMode();
        handler.removeCallbacks(ticker);''',
'''        if("world".equals(mode))applyWorldDisplayMode();
        if("timer".equals(mode))renderTimers();
        handler.removeCallbacks(ticker);''','timer resume refresh')

anchor='''    private void renderTimers() {'''
helpers='''    private boolean timerAnalog(){ return p().getBoolean("timer_face_analog",false); }
    private void toggleTimerFace(){ p().edit().putBoolean("timer_face_analog",!timerAnalog()).apply(); renderTimers(); }

'''+anchor
s=rep(s,anchor,helpers,'timer face helpers')

old='''        TextView remain=text(formatTimer(e.remaining()),38,Ui.TEXT);remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);remain.setTag("timer_remaining_"+e.id);remain.setClickable(true);remain.setOnClickListener(v->openClockFace("timer",null,e.id));row.addView(remain,Ui.gapTop(this,2));'''
new='''        boolean analogMode=timerAnalog();
        FrameLayout timerStage=new FrameLayout(this);timerStage.setTag("timer_stage_"+e.id);timerStage.setClickable(true);timerStage.setLongClickable(true);
        TextView remain=text(formatTimer(e.remaining()),38,Ui.TEXT);remain.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);remain.setGravity(Gravity.CENTER_VERTICAL);remain.setTag("timer_remaining_"+e.id);
        ClockFaceActivity.AnalogFace timerFace=new ClockFaceActivity.AnalogFace(this);timerFace.configure(p().getBoolean("analog_numbers",true),p().getBoolean("analog_ticks",true),p().getBoolean("analog_seconds",true),p().getInt("analog_number_scale",1));timerFace.setTag("timer_analog_"+e.id);
        timerStage.addView(remain,new FrameLayout.LayoutParams(-1,-1));timerStage.addView(timerFace,new FrameLayout.LayoutParams(-1,-1));remain.setVisibility(analogMode?View.GONE:View.VISIBLE);timerFace.setVisibility(analogMode?View.VISIBLE:View.GONE);
        timerStage.setOnClickListener(v->toggleTimerFace());timerStage.setOnLongClickListener(v->{openClockFace("timer",null,e.id);return true;});
        LinearLayout.LayoutParams timerStageParams=new LinearLayout.LayoutParams(-1,Ui.dp(this,analogMode?220:72));timerStageParams.setMargins(0,Ui.dp(this,2),0,0);row.addView(timerStage,timerStageParams);'''
s=rep(s,old,new,'timer row gesture')

old='''            TextView time=timerList.findViewWithTag("timer_remaining_"+e.id),state=timerList.findViewWithTag("timer_state_"+e.id);
            if(time!=null)time.setText(formatTimer(rem));if(state!=null)state.setText(timerStateText(e));'''
new='''            TextView time=timerList.findViewWithTag("timer_remaining_"+e.id),state=timerList.findViewWithTag("timer_state_"+e.id);
            ClockFaceActivity.AnalogFace analog=timerList.findViewWithTag("timer_analog_"+e.id);
            if(time!=null)time.setText(formatTimer(rem));if(analog!=null){double sec=(rem/1000d)%60d,min=(rem/60000d)%60d,hr=(rem/3600000d)%12d;analog.setHands(hr,min,sec);}if(state!=null)state.setText(timerStateText(e));'''
s=rep(s,old,new,'timer analog live update')

# Full-screen access prompt for video timers on Android 14+.
old='''        if(video){if(!storeTimerVideo(e,uri,display))return;}else{e.soundUri=uri.toString();e.soundName=display;TimerStore.update(this,e);}
        renderTimers();Toast.makeText(this,I18n.tr(this,(video?"タイマー動画: ":"タイマー音: ")+display),Toast.LENGTH_SHORT).show();'''
new='''        if(video){if(!storeTimerVideo(e,uri,display))return;promptTimerVideoFullScreenPermissionIfNeeded();}else{e.soundUri=uri.toString();e.soundName=display;TimerStore.update(this,e);}
        renderTimers();Toast.makeText(this,I18n.tr(this,(video?"タイマー動画: ":"タイマー音: ")+display),Toast.LENGTH_SHORT).show();'''
s=rep(s,old,new,'timer video permission after select')

old='''    private void startTimerEntry(TimerStore.Entry e){
        if(e==null)return;
        e.remainingMs=e.durationMs; e.endMs=System.currentTimeMillis()+e.durationMs; e.running=true;'''
new='''    private void startTimerEntry(TimerStore.Entry e){
        if(e==null)return;
        if(isVideoMedia(e.soundUri))promptTimerVideoFullScreenPermissionIfNeeded();
        e.remainingMs=e.durationMs; e.endMs=System.currentTimeMillis()+e.durationMs; e.running=true;'''
s=rep(s,old,new,'timer permission on start')

anchor='''    private boolean timerNotificationsAllowed(){'''
permission='''    private boolean canUseTimerVideoFullScreen(){
        if(Build.VERSION.SDK_INT<34)return true;try{android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);return nm==null||nm.canUseFullScreenIntent();}catch(Throwable ignored){return false;}
    }
    private void promptTimerVideoFullScreenPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT<34||canUseTimerVideoFullScreen())return;
        try{new AlertDialog.Builder(this).setTitle(I18n.tr(this,"動画タイマーの全画面表示"))
            .setMessage(I18n.tr(this,"動画をタイマー終了時に画面いっぱいに表示するには、全画面通知の許可をオンにしてください。"))
            .setNegativeButton(I18n.tr(this,"あとで"),null)
            .setPositiveButton(I18n.tr(this,"設定を開く"),(d,w)->{try{android.content.Intent x=new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,android.net.Uri.parse("package:"+getPackageName()));startActivity(x);}catch(Throwable ignored){}}).show();}catch(Throwable ignored){}
    }

'''+anchor
s=rep(s,anchor,permission,'timer full screen helper')
write(p,s)

# Fullscreen timer face uses its own preference, not the world clock preference.
p=j/'ClockFaceActivity.java'; s=read(p)
s=rep(s,
'''    private String facePrefKey(){return "stopwatch".equals(mode)?"stopwatch_face_analog":"clock_face_analog";}''',
'''    private String facePrefKey(){return "stopwatch".equals(mode)?"stopwatch_face_analog":"timer".equals(mode)?"timer_face_analog":"clock_face_analog";}''','clock face timer pref')
write(p,s)

# Timer completion: if ClockActivity is foreground, show video immediately; otherwise rely on urgent notification.
p=j/'TimerReceiver.java'; s=read(p)
old='''    private static PendingIntent openCompleted(Context c,TimerStore.Entry e){if(isVideo(c,e)){Intent open=new Intent(c,TimerVideoActivity.class).putExtra("videoUri",e.soundUri).putExtra("label",e.label==null?"":e.label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(c,requestCode(e.id)+2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}return openTimer(c,e.id);}'''
new='''    private static Intent videoIntent(Context c,TimerStore.Entry e){return new Intent(c,TimerVideoActivity.class).putExtra("videoUri",e.soundUri).putExtra("label",e.label==null?"":e.label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    private static PendingIntent openCompleted(Context c,TimerStore.Entry e){if(isVideo(c,e))return PendingIntent.getActivity(c,requestCode(e.id)+2,videoIntent(c,e),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return openTimer(c,e.id);}'''
s=rep(s,old,new,'timer video intent helper')
old='''public static void complete(Context c,long id){TimerStore.Entry e=TimerStore.find(c,id);if(e==null)return;TimerStore.Entry ring=new TimerStore.Entry(e.id,e.label,e.durationMs,0L,0L,false,e.saved,e.soundUri,e.soundName);e.running=false;e.remainingMs=0L;e.endMs=0L;if(e.saved)TimerStore.update(c,e);else TimerStore.delete(c,id);cancel(c,id);boolean started=TimerRingService.start(c,ring);if(started)return;ensureDoneChannel(c,ring);'''
new='''public static void complete(Context c,long id){TimerStore.Entry e=TimerStore.find(c,id);if(e==null)return;TimerStore.Entry ring=new TimerStore.Entry(e.id,e.label,e.durationMs,0L,0L,false,e.saved,e.soundUri,e.soundName);e.running=false;e.remainingMs=0L;e.endMs=0L;if(e.saved)TimerStore.update(c,e);else TimerStore.delete(c,id);cancel(c,id);boolean started=TimerRingService.start(c,ring);if(started){if(isVideo(c,ring)&&c instanceof Activity)try{((Activity)c).startActivity(videoIntent(c,ring));}catch(Throwable ignored){}return;}ensureDoneChannel(c,ring);'''
s=rep(s,old,new,'timer foreground direct video')
write(p,s)

# Timer ringing service: use a fresh high-importance channel and, if video UI cannot open,
# play the selected video's own audio track instead of the phone's default alarm.
p=j/'TimerRingService.java'; s=read(p)
s=s.replace('private static final String CHANNEL="wakeguard_timer_ring_v1";','private static final String CHANNEL="wakeguard_timer_ring_v2";',1)
s=rep(s,
'''    private void startAudioNow(String custom){Uri u=null;try{if(custom!=null&&!custom.isEmpty())u=Uri.parse(custom);}catch(Throwable ignored){}if(u==null)u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);player=createPlayer(u);if(player==null)player=createPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));boolean playing=false;try{playing=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!playing)startFallback();}''',
'''    private void startAudioNow(String custom){Uri u=null;try{if(custom!=null&&!custom.isEmpty())u=custom.startsWith("/")?Uri.fromFile(new java.io.File(custom)):Uri.parse(custom);}catch(Throwable ignored){}if(u==null)u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);player=createPlayer(u);if(player==null)player=createPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));boolean playing=false;try{playing=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!playing)startFallback();}''','timer raw file uri')
s=s.replace('videoDelayHandler.postDelayed(()->{if(currentVideo&&!timerVideoReady)startVideoFallbackNow();},5000L);','videoDelayHandler.postDelayed(()->{if(currentVideo&&!timerVideoReady)startVideoFallbackNow();},1800L);',1)
s=rep(s,
'''    private void startVideoFallbackNow(){cancelVideoDelay();if(timerVideoReady)return;stopServiceAudioOnly();acquireRingWakeLock();startRingVibration();startAudioNow("");}''',
'''    private void startVideoFallbackNow(){cancelVideoDelay();if(timerVideoReady)return;stopServiceAudioOnly();acquireRingWakeLock();startRingVibration();startAudioNow(currentUri);}''','video own audio fallback')
write(p,s)

# Basic validation.
assert 'versionName = "1.6.7"' in read(app/'build.gradle.kts')
cs=read(j/'ClockActivity.java')
assert 'timer_face_analog' in cs and 'timer_analog_' in cs and 'setOnLongClickListener' in cs
assert 'promptTimerVideoFullScreenPermissionIfNeeded' in cs
assert 'timer".equals(mode)?"timer_face_analog"' in read(j/'ClockFaceActivity.java')
trs=read(j/'TimerRingService.java')
assert 'wakeguard_timer_ring_v2' in trs and 'startAudioNow(currentUri)' in trs and '1800L' in trs
print('WakeGuard v1.6.7 timer UI + video reliability patch applied')