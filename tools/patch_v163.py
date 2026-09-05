from pathlib import Path

ROOT=Path('WakeGuard')
JAVA=ROOT/'app/src/main/java/jp/wakeguard/alarm'
LAYOUT=ROOT/'app/src/main/res/layout'
GRADLE=ROOT/'app/build.gradle.kts'

# AlarmActivity: use a TextureView-backed PlayerView and keep playback alive while the
# lock-screen activity is STARTED (some OEM keyguards transiently pause it).
p=JAVA/'AlarmActivity.java'
s=p.read_text(encoding='utf-8')
s=s.replace(
'private androidx.media3.ui.PlayerView alarmVideoView; private androidx.media3.exoplayer.ExoPlayer alarmVideoPlayer; private boolean alarmVideoMode=false,alarmVideoPrepared=false;',
'private androidx.media3.ui.PlayerView alarmVideoView; private androidx.media3.exoplayer.ExoPlayer alarmVideoPlayer; private boolean alarmVideoMode=false,alarmVideoPrepared=false,activityStarted=false;')
old='if(alarmVideoMode){alarmVideoView=new androidx.media3.ui.PlayerView(this);alarmVideoView.setBackgroundColor(Color.TRANSPARENT);alarmVideoView.setShutterBackgroundColor(Color.TRANSPARENT);alarmVideoView.setUseController(false);alarmVideoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);alarmVideoView.setKeepScreenOn(true);frame.addView(alarmVideoView,new FrameLayout.LayoutParams(-1,-1));}'
new='if(alarmVideoMode){alarmVideoView=(androidx.media3.ui.PlayerView)getLayoutInflater().inflate(R.layout.view_alarm_video_texture,frame,false);alarmVideoView.setBackgroundColor(Color.TRANSPARENT);alarmVideoView.setShutterBackgroundColor(Color.TRANSPARENT);alarmVideoView.setUseController(false);alarmVideoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);alarmVideoView.setKeepScreenOn(true);frame.addView(alarmVideoView,new FrameLayout.LayoutParams(-1,-1));}'
if old not in s: raise SystemExit('AlarmActivity video view anchor not found')
s=s.replace(old,new)
old_sync='private void syncVideoState(){if(!alarmVideoMode||alarmVideoPlayer==null||!alarmVideoPrepared)return;if(!Prefs.active(this)||Prefs.sessionSilenced(this)){try{alarmVideoPlayer.pause();}catch(Throwable ignored){}return;}if(visible){try{alarmVideoPlayer.play();}catch(Throwable ignored){}notifyVideoService(AlarmService.ACTION_VIDEO_READY);}}'
new_sync='private void syncVideoState(){if(!alarmVideoMode||alarmVideoPlayer==null||!alarmVideoPrepared)return;if(!Prefs.active(this)||Prefs.sessionSilenced(this)){try{alarmVideoPlayer.pause();}catch(Throwable ignored){}return;}if(activityStarted){try{alarmVideoPlayer.setPlayWhenReady(true);alarmVideoPlayer.play();}catch(Throwable ignored){}notifyVideoService(AlarmService.ACTION_VIDEO_READY);}}'
if old_sync not in s: raise SystemExit('AlarmActivity sync anchor not found')
s=s.replace(old_sync,new_sync)
old_life='''    @Override protected void onResume() { super.onResume(); if(!Prefs.active(this)){try{finishAndRemoveTask();}catch(Throwable ignored){finish();}return;} visible = true; render(); }\n    @Override protected void onPause() { if(alarmVideoMode&&alarmVideoPlayer!=null){try{alarmVideoPlayer.pause();}catch(Throwable ignored){}if(Prefs.active(this)&&!Prefs.sessionSilenced(this))notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);} visible = false; super.onPause(); }\n    @Override protected void onDestroy() { visible = false; releaseAlarmVideo(); missionHandler.removeCallbacksAndMessages(null); if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){} if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} } super.onDestroy(); }'''
new_life='''    @Override protected void onStart() { super.onStart(); activityStarted=true;visible=true;if(Prefs.active(this))syncVideoState(); }\n    @Override protected void onResume() { super.onResume(); if(!Prefs.active(this)){try{finishAndRemoveTask();}catch(Throwable ignored){finish();}return;} visible = true; render();syncVideoState(); }\n    @Override protected void onPause() { /* Keep alarm video alive across OEM/keyguard pause transitions. */ super.onPause(); }\n    @Override protected void onStop() { activityStarted=false;visible=false;if(alarmVideoMode&&alarmVideoPlayer!=null){try{alarmVideoPlayer.setPlayWhenReady(false);alarmVideoPlayer.pause();}catch(Throwable ignored){}if(Prefs.active(this)&&!Prefs.sessionSilenced(this))notifyVideoService(AlarmService.ACTION_VIDEO_FALLBACK);}super.onStop(); }\n    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus&&activityStarted)syncVideoState();}\n    @Override protected void onDestroy() { activityStarted=false;visible = false; releaseAlarmVideo(); missionHandler.removeCallbacksAndMessages(null); if(sensorManager!=null&&shakeListener!=null)try{sensorManager.unregisterListener(shakeListener);}catch(Throwable ignored){} if (updates != null) { try { unregisterReceiver(updates); } catch (Throwable ignored) {} } super.onDestroy(); }'''
if old_life not in s: raise SystemExit('AlarmActivity lifecycle anchor not found')
s=s.replace(old_life,new_life)
p.write_text(s,encoding='utf-8')

# TextureView is composited inside the Activity window and is selectable by Media3's
# documented PlayerView surface_type attribute.
(LAYOUT/'view_alarm_video_texture.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>\n<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:app="http://schemas.android.com/apk/res-auto"\n    android:layout_width="match_parent"\n    android:layout_height="match_parent"\n    android:background="@android:color/transparent"\n    android:keepScreenOn="true"\n    app:surface_type="texture_view"\n    app:resize_mode="zoom"\n    app:use_controller="false"\n    app:keep_content_on_player_reset="true" />\n''',encoding='utf-8')

# When a video is selected, make Android 14+ full-screen-alarm access impossible to
# miss. This is the system-controlled permission used by full-screen alarm notices.
p=JAVA/'AlarmEditorActivity.java'
s=p.read_text(encoding='utf-8')
old='private void useVideo(Uri uri){try{String name=fileName(uri);long bytes=-1,dur=-1;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))bytes=c.getLong(0);}catch(Throwable ignored){}android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever();try{m.setDataSource(this,uri);String x=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(x!=null)dur=Long.parseLong(x);}finally{try{m.release();}catch(Throwable ignored){}}draft.soundUri=uri.toString();draft.soundName=name;draft.soundBytes=bytes;draft.soundDurationMs=dur;renderSound();}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();}}'
new='private void useVideo(Uri uri){try{String name=fileName(uri);long bytes=-1,dur=-1;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))bytes=c.getLong(0);}catch(Throwable ignored){}android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever();try{m.setDataSource(this,uri);String x=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(x!=null)dur=Long.parseLong(x);}finally{try{m.release();}catch(Throwable ignored){}}draft.soundUri=uri.toString();draft.soundName=name;draft.soundBytes=bytes;draft.soundDurationMs=dur;renderSound();promptFullScreenVideoPermissionIfNeeded();}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"この動画は読み込めません"),Toast.LENGTH_LONG).show();}}'
if old not in s: raise SystemExit('AlarmEditor useVideo anchor not found')
s=s.replace(old,new)
insert='''\n    private void promptFullScreenVideoPermissionIfNeeded(){\n        if(Build.VERSION.SDK_INT<34)return;\n        boolean ok=false;try{NotificationManager nm=getSystemService(NotificationManager.class);ok=nm!=null&&nm.canUseFullScreenIntent();}catch(Throwable ignored){}\n        if(ok)return;\n        new AlertDialog.Builder(this)\n                .setTitle(I18n.tr(this,"ロック画面の動画表示"))\n                .setMessage(I18n.tr(this,"ロック中に動画アラームを全画面表示するには、端末設定で「全画面アラーム」を許可してください。"))\n                .setPositiveButton(I18n.tr(this,"設定を開く"),(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));}catch(Throwable t){Toast.makeText(this,I18n.tr(this,"設定 → アプリ → WakeGuard → 全画面アラーム を許可してください"),Toast.LENGTH_LONG).show();}})\n                .setNegativeButton(I18n.tr(this,"後で"),null).show();\n    }\n'''
anchor='    private boolean isVideoMedia(String raw)'
if anchor not in s: raise SystemExit('AlarmEditor insert anchor not found')
s=s.replace(anchor,insert+'\n'+anchor)
p.write_text(s,encoding='utf-8')

# Make the device-settings explanation explicit about lock-screen video.
p=JAVA/'SystemSettingsActivity.java'
s=p.read_text(encoding='utf-8')
s=s.replace('ロック画面で確実に鳴らすために必要な端末側の設定です。','ロック画面でアラーム画面・動画を確実に表示するために必要な端末側の設定です。')
p.write_text(s,encoding='utf-8')

# Version bump
g=GRADLE.read_text(encoding='utf-8')
g=g.replace('versionCode = 73','versionCode = 74').replace('versionName = "1.6.2"','versionName = "1.6.3"')
GRADLE.write_text(g,encoding='utf-8')
