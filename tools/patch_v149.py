from pathlib import Path
import re, runpy
runpy.run_path('tools/patch_v148_fix.py',run_name='__main__')
app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'
def sub(s,a,b,name,flags=0):
    out,n=re.subn(a,b,s,count=1,flags=flags)
    if n!=1: raise SystemExit(name+' anchor missing')
    return out

# Alarm editor: audio OR video.
p=j/'AlarmEditorActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('private static final int REQ_AUDIO=81;','private static final int REQ_AUDIO=81,REQ_VIDEO=82;',1)
s=s.replace('TextView sl=Ui.text(this,"アラーム音",16,Ui.TEXT);','TextView sl=Ui.text(this,"音声 / 動画",16,Ui.TEXT);',1)
s=sub(s,r'    private void renderSound\(\)\{.*?\n    private AlarmStore.Entry saveDraft', '''    private void renderSound(){
        if(draft.soundName==null||draft.soundName.isEmpty()){soundStatus.setText(I18n.tr(this,"標準アラーム音"));return;}
        String size=draft.soundBytes>0?String.format(Locale.JAPAN,"%.1f MB",draft.soundBytes/1048576.0):I18n.tr(this,"サイズ不明");
        String dur=draft.soundDurationMs>0?String.format(Locale.JAPAN,"%d:%02d",draft.soundDurationMs/60000,(draft.soundDurationMs/1000)%60):I18n.tr(this,"長さ不明");
        soundStatus.setText(I18n.tr(this,isVideoMedia(draft.soundUri)?"動画":"音声")+"  ·  "+draft.soundName+"  ·  "+size+"  ·  "+dur);
    }
    private AlarmStore.Entry saveDraft''','editor render',re.S)
start=s.index('    private void pickSound()'); end=s.index('\n}',start)
media='''    private void pickSound(){
        new AlertDialog.Builder(this).setTitle(I18n.tr(this,"アラームの音・動画")).setItems(new String[]{I18n.tr(this,"音声を選択"),I18n.tr(this,"動画を選択"),I18n.tr(this,"標準に戻す")},(d,w)->{if(w==0)pickMedia(false);else if(w==1)pickMedia(true);else{draft.soundUri="";draft.soundName="";draft.soundBytes=-1;draft.soundDurationMs=-1;renderSound();}}).show();
    }
    private void pickMedia(boolean video){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(video?"video/*":"audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,video?REQ_VIDEO:REQ_AUDIO);}catch(Throwable t){Toast.makeText(this,I18n.tr(this,video?"動画ファイルを開けません":"音声ファイルを開けません"),Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if((requestCode!=REQ_AUDIO&&requestCode!=REQ_VIDEO)||resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}if(requestCode==REQ_VIDEO)useVideo(u);else copySound(u);}
    private void copySound(Uri uri){File out=null;try{String name=fileName(uri);File dir=new File(getFilesDir(),"alarm_sounds");if(!dir.exists()&&!dir.mkdirs())throw new IOException("mkdir");out=new File(dir,"alarm_"+(alarmId<0?"new":alarmId)+"_"+System.currentTimeMillis()+".audio");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){if(in==null)throw new IOException("open");byte[]buf=new byte[65536];int n;while((n=in.read(buf))>0)os.write(buf,0,n);}MediaPlayer mp=new MediaPlayer();mp.setDataSource(out.getAbsolutePath());mp.prepare();long dur=mp.getDuration();mp.release();draft.soundUri=out.getAbsolutePath();draft.soundName=name;draft.soundBytes=out.length();draft.soundDurationMs=dur;renderSound();}catch(Throwable t){if(out!=null)try{out.delete();}catch(Throwable ignored){}Toast.makeText(this,I18n.tr(this,"この音源は読み込めません"),Toast.LENGTH_LONG).show();}}
    private void useVideo(Uri uri){try{String name=fileName(uri);long bytes=-1,dur=-1;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))bytes=c.getLong(0);}catch(Throwable ignored){}android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever();try{m.setDataSource(this,uri);String x=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(x!=null)dur=Long.parseLong(x);}finally{try{m.release();}catch(Throwable ignored){}}draft.soundUri=uri.toString();draft.soundName=name;draft.soundBytes=bytes;draft.soundDurationMs=dur;renderSound();}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();}}
    private boolean isVideoMedia(String raw){if(raw==null||raw.trim().isEmpty())return false;try{if(raw.startsWith("content:")){String t=getContentResolver().getType(Uri.parse(raw));if(t!=null&&t.toLowerCase(Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}String x=raw.toLowerCase(Locale.ROOT);return x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts");}
    private String fileName(Uri uri){String name=I18n.tr(this,"選択した音源");try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String x=c.getString(0);if(x!=null&&!x.isEmpty())name=x;}}catch(Throwable ignored){}return name;}
'''
s=s[:start]+media+s[end:]; p.write_text(s,encoding='utf-8')

# Service: fallback tone until video is ready; preserve vibration while video plays.
p=j/'AlarmService.java'; s=p.read_text(encoding='utf-8')
s=s.replace('public static final String ACTION_STOP = "jp.wakeguard.alarm.STOP";','public static final String ACTION_STOP = "jp.wakeguard.alarm.STOP";\n    public static final String ACTION_VIDEO_READY = "jp.wakeguard.alarm.VIDEO_READY";\n    public static final String ACTION_VIDEO_FALLBACK = "jp.wakeguard.alarm.VIDEO_FALLBACK";',1)
anchor='''        boolean scheduledSession = ACTION_FIRE_NEW.equals(action);'''
insert='''        if(ACTION_VIDEO_READY.equals(action)){if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))silenceServiceAudioForVideo();return START_STICKY;}
        if(ACTION_VIDEO_FALLBACK.equals(action)){if(Prefs.active(this)&&isActiveVideo()&&!Prefs.sessionSilenced(this))startVideoFallbackAudio();return START_STICKY;}

'''+anchor
if anchor not in s: raise SystemExit('service action anchor missing')
s=s.replace(anchor,insert,1)
old='''    private void startAlarmOutputs() {'''
extra='''    private boolean isActiveVideo(){return isVideoMedia(AlarmProfiles.soundUri(this,Prefs.activeAlarmId(this)));}
    private boolean isVideoMedia(String raw){if(raw==null||raw.trim().isEmpty())return false;try{if(raw.startsWith("content:")){String t=getContentResolver().getType(Uri.parse(raw));if(t!=null&&t.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}String x=raw.toLowerCase(java.util.Locale.ROOT);return x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts");}
    private void silenceServiceAudioForVideo(){if(player!=null){try{player.stop();}catch(Throwable ignored){}try{player.release();}catch(Throwable ignored){}player=null;}stopFallbackTone();}
    private void startVideoFallbackAudio(){prepareAlarmVolume();if(player==null)player=createPlayer("");boolean ok=false;try{ok=player!=null&&player.isPlaying();}catch(Throwable ignored){}if(!ok)startFallbackTone();}

'''+old
if old not in s: raise SystemExit('service output anchor missing')
s=s.replace(old,extra,1)
s=s.replace('player = createPlayer(AlarmProfiles.soundUri(this, Prefs.activeAlarmId(this)));','String chosen=AlarmProfiles.soundUri(this, Prefs.activeAlarmId(this));\n            player = createPlayer(isVideoMedia(chosen)?"":chosen);',1)
s=s.replace('''        restoreAlarmVolume();
    }

    private boolean isActiveVideo()''','''        restoreAlarmVolume();
        try{sendBroadcast(new Intent(ACTION_UPDATE).setPackage(getPackageName()));}catch(Throwable ignored){}
    }

    private boolean isActiveVideo()''',1)
p.write_text(s,encoding='utf-8')

# Alarm screen: loop selected video behind mission UI; fallback resumes if screen leaves.
p=j/'AlarmActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('private String expectedCode="";','private String expectedCode="";\n    private VideoView alarmVideo; private boolean alarmVideoMode=false,alarmVideoPrepared=false;',1)
s=s.replace('''        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,24), Ui.dp(this,36), Ui.dp(this,24), Ui.dp(this,30));
        root.setBackgroundColor(Ui.BG);''','''        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Ui.BG);alarmVideoMode=isVideoAlarm();
        if(alarmVideoMode){alarmVideo=new VideoView(this);alarmVideo.setBackgroundColor(Color.BLACK);frame.addView(alarmVideo,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(0x7A000000);frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));}
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,24), Ui.dp(this,36), Ui.dp(this,24), Ui.dp(this,30));
        root.setBackgroundColor(alarmVideoMode?Color.TRANSPARENT:Ui.BG);''',1)
s=s.replace('''        setContentView(root);
    }

    private String sessionType()''','''        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);if(alarmVideoMode)startAlarmVideo();
    }
    private String alarmMediaRaw(){String x=AlarmProfiles.soundUri(this,Prefs.activeAlarmId(this));return x==null?"":x;}
    private boolean isVideoAlarm(){String raw=alarmMediaRaw();if(raw.trim().isEmpty())return false;try{if(raw.startsWith("content:")){String t=getContentResolver().getType(android.net.Uri.parse(raw));if(t!=null&&t.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return true;}}catch(Throwable ignored){}String x=raw.toLowerCase(java.util.Locale.ROOT);return x.endsWith(".mp4")||x.endsWith(".m4v")||x.endsWith(".3gp")||x.endsWith(".3gpp")||x.endsWith(".webm")||x.endsWith(".mkv")||x.endsWith(".ts");}
    private void startAlarmVideo(){if(alarmVideo==null)return;String raw=alarmMediaRaw();try{alarmVideo.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build());alarmVideo.setOnPreparedListener(mp->{try{mp.setLooping(true);mp.setVolume(1f,1f);}catch(Throwable ignored){}alarmVideoPrepared=true;if(visible&&Prefs.active(AlarmActivity.this)&&!Prefs.sessionSilenced(AlarmActivity.this)){try{alarmVideo.start();}catch(Throwable ignored){}notifyVideoService(AlarmService.ACTION_VIDEO_READY);}});alarmVideo.setOnErrorListener((mp,w,e)->{alarmVideoPrepared=false;Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();return true;});alarmVideo.setVideoURI(raw.startsWith("/")?android.net.Uri.fromFile(new java.io.File(raw)):android.net.Uri.parse(raw));alarmVideo.requestFocus();}catch(Throwable t){alarmVideoPrepared=false;Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();}}
    private void notifyVideoService(String a){try{startService(new Intent(this,AlarmService.class).setAction(a));}catch(Throwable ignored){}}
    private void syncVideoState(){if(!alarmVideoMode||alarmVideo==null||!alarmVideoPrepared)return;if(!Prefs.active(this)||Prefs.sessionSilenced(this)){try{if(alarmVideo.isPlaying())alarmVideo.pause();}catch(Throwable ignored){}return;}if(visible){try{if(!alarmVideo.isPlaying())alarmVideo.start();}catch(Throwable ignored){}notifyVideoService(AlarmService.ACTION_VIDEO_READY);}}

    private String sessionType()''',1)
s=s.replace('stop.setText(I18n.tr(this,done?"アラームを停止":"ミッションを完了してください"));','stop.setText(I18n.tr(this,done?"アラームを停止":"ミッションを完了してください"));syncVideoState();',1)
s=s.replace('''    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override protected void onDestroy() { visible = false; missionHandler.removeCallbacksAndMessages(null);''','''    @Override protected void onPause() { if(alarmVideoMode&&alarmVideo!=null){try{alarmVideo.pause();}catch(Throwable ignored){}if(Prefs.active(this)&&!Prefs.sessionSilenced(this))notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);} visible = false; super.onPause(); }
    @Override protected void onDestroy() { visible = false; if(alarmVideo!=null)try{alarmVideo.stopPlayback();}catch(Throwable ignored){} missionHandler.removeCallbacksAndMessages(null);''',1)
p.write_text(s,encoding='utf-8')

# JA/EN/KO labels.
p=j/'I18n.java'; s=p.read_text(encoding='utf-8')
s=s.replace('put("タイマーの音・動画","Timer sound / video","타이머 소리 / 동영상");','put("タイマーの音・動画","Timer sound / video","타이머 소리 / 동영상"); put("アラームの音・動画","Alarm sound / video","알람 소리 / 동영상"); put("音声 / 動画","Audio / video","오디오 / 동영상"); put("音声","Audio","오디오"); put("動画","Video","동영상");',1)
s=s.replace('put("動画ファイルを開けません","Unable to open the video file","동영상 파일을 열 수 없습니다");','put("動画ファイルを開けません","Unable to open the video file","동영상 파일을 열 수 없습니다"); put("この動画は読み込めません","Unable to load this video","이 동영상을 불러올 수 없습니다");',1)
p.write_text(s,encoding='utf-8')
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8'); s=re.sub(r'versionCode = \d+','versionCode = 60',s); s=re.sub(r'versionName = "[^"]+"','versionName = "1.4.9"',s); p.write_text(s,encoding='utf-8')
print('WakeGuard v1.4.9 alarm video patch applied')
