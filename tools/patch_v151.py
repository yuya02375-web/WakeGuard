from pathlib import Path
import re, runpy
runpy.run_path('tools/patch_v150.py', run_name='__main__')
app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'

def rep(path, old, new, name):
    s=path.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(name+' anchor missing')
    path.write_text(s.replace(old,new,1),encoding='utf-8')

def sub(path, pattern, repl, name, flags=0):
    s=path.read_text(encoding='utf-8')
    out,n=re.subn(pattern,repl,s,count=1,flags=flags)
    if n!=1:
        raise SystemExit(name+' anchor missing')
    path.write_text(out,encoding='utf-8')

# Use AndroidX Media3 for reliable full-screen video playback and proper 9:16 crop/loop.
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8')
if 'androidx.media3:media3-exoplayer:' not in s:
    s=s.replace('dependencies {', 'dependencies {\n    implementation("androidx.media3:media3-exoplayer:1.11.0")\n    implementation("androidx.media3:media3-ui:1.11.0")', 1)
s=re.sub(r'versionCode = \d+','versionCode = 62',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.5.1"',s)
p.write_text(s,encoding='utf-8')

p=j/'AlarmActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('private VideoView alarmVideo; private boolean alarmVideoMode=false,alarmVideoPrepared=false;',
'''private androidx.media3.ui.PlayerView alarmVideoView; private androidx.media3.exoplayer.ExoPlayer alarmVideoPlayer; private boolean alarmVideoMode=false,alarmVideoPrepared=false;''',1)

s=s.replace('''        if(alarmVideoMode){alarmVideo=new VideoView(this);alarmVideo.setBackgroundColor(Color.BLACK);frame.addView(alarmVideo,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(0x7A000000);frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));}''',
'''        if(alarmVideoMode){alarmVideoView=new androidx.media3.ui.PlayerView(this);alarmVideoView.setBackgroundColor(Color.BLACK);alarmVideoView.setUseController(false);alarmVideoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);alarmVideoView.setKeepScreenOn(true);frame.addView(alarmVideoView,new FrameLayout.LayoutParams(-1,-1));}''',1)

# Make direct-stop mode unmistakable and make all over-video text readable without dimming the whole video.
s=s.replace('''        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 60);''',
'''        TextView now = Ui.title(this, java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), 60);if(alarmVideoMode)now.setShadowLayer(10f,0,2,Color.BLACK);''',1)
s=s.replace('''        TextView title = Ui.text(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 16, Ui.MUTED); title.setGravity(Gravity.CENTER); root.addView(title,Ui.gapTop(this,4));''',
'''        TextView title = Ui.text(this, AlarmProfiles.label(this, Prefs.activeAlarmId(this)), 16, alarmVideoMode?Ui.TEXT:Ui.MUTED);if(alarmVideoMode)title.setShadowLayer(8f,0,2,Color.BLACK); title.setGravity(Gravity.CENTER); root.addView(title,Ui.gapTop(this,4));''',1)
s=s.replace('''        missionName=Ui.title(this,AlarmProfiles.missionName(type),24);missionName.setGravity(Gravity.CENTER);root.addView(missionName,Ui.gapTop(this,28));
        TextView instruction=Ui.text(this,"このミッションを完了するとアラームを解除できます",13,Ui.MUTED);instruction.setGravity(Gravity.CENTER);root.addView(instruction,Ui.gapTop(this,6));''',
'''        missionName=Ui.title(this,AlarmProfiles.missionName(type),24);if(alarmVideoMode)missionName.setShadowLayer(8f,0,2,Color.BLACK);missionName.setGravity(Gravity.CENTER);root.addView(missionName,Ui.gapTop(this,28));
        TextView instruction=Ui.text(this,"NONE".equals(type)?"停止ボタンを押すとアラームを停止します":"このミッションを完了するとアラームを解除できます",13,alarmVideoMode?Ui.TEXT:Ui.MUTED);if(alarmVideoMode)instruction.setShadowLayer(7f,0,2,Color.BLACK);instruction.setGravity(Gravity.CENTER);root.addView(instruction,Ui.gapTop(this,6));''',1)
s=s.replace('''        stop = Ui.button(this, "ミッションを完了してください", true); stop.setEnabled(false);''',
'''        stop = Ui.button(this, "NONE".equals(type)?"アラームを停止":"ミッションを完了してください", true); stop.setEnabled("NONE".equals(type));stop.setVisibility(View.VISIBLE);stop.setMinHeight(Ui.dp(this,"NONE".equals(type)?72:58));stop.setTextSize("NONE".equals(type)?19:16);''',1)
s=s.replace('''        root.addView(stop);''','''        root.addView(stop);stop.bringToFront();''',1)
s=s.replace('''        TextView footer = Ui.text(this, "戻る・ホームでは停止しません", 12, Ui.MUTED); footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,12));''',
'''        TextView footer = Ui.text(this, "戻る・ホームでは停止しません", 12, alarmVideoMode?Ui.TEXT:Ui.MUTED);if(alarmVideoMode)footer.setShadowLayer(7f,0,2,Color.BLACK); footer.setGravity(Gravity.CENTER); root.addView(footer, Ui.gapTop(this,12));''',1)
p.write_text(s,encoding='utf-8')

# Replace legacy VideoView with Media3 ExoPlayer. The full-screen black shade is removed;
# the selected video's own audio remains the alarm audio while this screen is visible.
sub(p,
    r'    private void startAlarmVideo\(\)\{.*?\n    private void notifyVideoService\(String a\)\{.*?\n    private void syncVideoState\(\)\{.*?\n',
'''    private void startAlarmVideo(){
        if(alarmVideoView==null)return;String raw=alarmMediaRaw();
        try{
            releaseAlarmVideo();
            alarmVideoPlayer=new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            androidx.media3.common.AudioAttributes aa=new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_ALARM).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build();
            alarmVideoPlayer.setAudioAttributes(aa,true);
            alarmVideoPlayer.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
            alarmVideoPlayer.setPlayWhenReady(false);
            alarmVideoView.setPlayer(alarmVideoPlayer);
            alarmVideoPlayer.addListener(new androidx.media3.common.Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(state==androidx.media3.common.Player.STATE_READY){alarmVideoPrepared=true;syncVideoState();}}
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error){alarmVideoPrepared=false;notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);Toast.makeText(AlarmActivity.this,I18n.tr(AlarmActivity.this,"動画を再生できません"),Toast.LENGTH_LONG).show();}
            });
            android.net.Uri u=raw.startsWith("/")?android.net.Uri.fromFile(new java.io.File(raw)):android.net.Uri.parse(raw);
            alarmVideoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(u));
            alarmVideoPlayer.prepare();
        }catch(Throwable t){alarmVideoPrepared=false;notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();}
    }
    private void releaseAlarmVideo(){alarmVideoPrepared=false;if(alarmVideoView!=null)try{alarmVideoView.setPlayer(null);}catch(Throwable ignored){}if(alarmVideoPlayer!=null){try{alarmVideoPlayer.release();}catch(Throwable ignored){}alarmVideoPlayer=null;}}
    private void notifyVideoService(String a){try{startService(new Intent(this,AlarmService.class).setAction(a));}catch(Throwable ignored){}}
    private void syncVideoState(){if(!alarmVideoMode||alarmVideoPlayer==null||!alarmVideoPrepared)return;if(!Prefs.active(this)||Prefs.sessionSilenced(this)){try{alarmVideoPlayer.pause();}catch(Throwable ignored){}return;}if(visible){try{alarmVideoPlayer.play();}catch(Throwable ignored){}notifyVideoService(AlarmService.ACTION_VIDEO_READY);}}
''', 'media3 video methods', re.S)
s=p.read_text(encoding='utf-8')

# Shadow dynamic mission text too, and always render a usable stop button for NONE.
s=s.replace('''        feedback=Ui.text(this,"",13,Ui.MUTED);feedback.setGravity(Gravity.CENTER);feedback.setPadding(0,Ui.dp(this,10),0,0);missionCard.addView(feedback); action=null;answer=null;''',
'''        feedback=Ui.text(this,"",13,alarmVideoMode?Ui.TEXT:Ui.MUTED);feedback.setGravity(Gravity.CENTER);feedback.setPadding(0,Ui.dp(this,10),0,0);if(alarmVideoMode){count.setShadowLayer(8f,0,2,Color.BLACK);prompt.setShadowLayer(8f,0,2,Color.BLACK);feedback.setShadowLayer(7f,0,2,Color.BLACK);}missionCard.addView(feedback); action=null;answer=null;''',1)
s=s.replace('''        if (count == null || stop == null) return; String type=sessionType();int target=targetCount();boolean done=missionDone();
        if("NONE".equals(type)){count.setText(I18n.tr(this,"解除できます"));if(feedback!=null)feedback.setText(I18n.tr(this,"下の停止ボタンを押してください"));}''',
'''        if (stop == null) return; String type=sessionType();int target=targetCount();boolean done=missionDone();
        if("NONE".equals(type)){Prefs.missionComplete(this,true);stop.setVisibility(View.VISIBLE);stop.setEnabled(true);stop.setAlpha(1f);stop.setText(I18n.tr(this,"アラームを停止"));stop.bringToFront();if(count!=null)count.setText(I18n.tr(this,"解除できます"));if(feedback!=null)feedback.setText(I18n.tr(this,"下の停止ボタンを押してください"));syncVideoState();return;}
        if(count==null)return;''',1)
s=s.replace('        if(count==null)return;\n        else if("STEPS".equals(type)){','        if(count==null)return;\n        if("STEPS".equals(type)){',1)

s=s.replace('''    @Override protected void onPause() { if(alarmVideoMode&&alarmVideo!=null){try{alarmVideo.pause();}catch(Throwable ignored){}if(Prefs.active(this)&&!Prefs.sessionSilenced(this))notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);} visible = false; super.onPause(); }
    @Override protected void onDestroy() { visible = false; if(alarmVideo!=null)try{alarmVideo.stopPlayback();}catch(Throwable ignored){} missionHandler.removeCallbacksAndMessages(null);''',
'''    @Override protected void onPause() { if(alarmVideoMode&&alarmVideoPlayer!=null){try{alarmVideoPlayer.pause();}catch(Throwable ignored){}if(Prefs.active(this)&&!Prefs.sessionSilenced(this))notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);} visible = false; super.onPause(); }
    @Override protected void onDestroy() { visible = false; releaseAlarmVideo(); missionHandler.removeCallbacksAndMessages(null);''',1)
p.write_text(s,encoding='utf-8')

# Translation for direct-stop explanatory text.
p=j/'I18n.java'; s=p.read_text(encoding='utf-8')
needle='put("追加の解除操作はありません","No extra dismissal challenge","추가 해제 동작이 없습니다");'
if needle in s and 'put("停止ボタンを押すとアラームを停止します"' not in s:
    s=s.replace(needle,needle+' put("停止ボタンを押すとアラームを停止します","Tap the stop button to stop the alarm","중지 버튼을 누르면 알람이 중지됩니다");',1)
p.write_text(s,encoding='utf-8')

print('WakeGuard v1.5.1 video playback + NONE dismiss patch applied')
