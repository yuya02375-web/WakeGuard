package jp.wakeguard.alarm;

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
    private androidx.media3.ui.PlayerView videoView;
    private androidx.media3.exoplayer.ExoPlayer player;
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
        // Use exactly the same TextureView-based PlayerView layout as alarm video. PlayerView contains
        // Media3's SubtitleView, so embedded subtitle/caption tracks are rendered over the video.
        videoView=(androidx.media3.ui.PlayerView)getLayoutInflater().inflate(R.layout.view_alarm_video_texture,root,false);
        videoView.setBackgroundColor(Color.BLACK);videoView.setShutterBackgroundColor(Color.BLACK);videoView.setUseController(false);videoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);videoView.setKeepScreenOn(true);
        androidx.media3.ui.SubtitleView subtitleView=videoView.getSubtitleView();
        if(subtitleView!=null){subtitleView.setVisibility(View.VISIBLE);subtitleView.setApplyEmbeddedStyles(true);subtitleView.setApplyEmbeddedFontSizes(true);subtitleView.setBottomPaddingFraction(0.12f);}
        FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=Gravity.CENTER;root.addView(videoView,vp);

        TextView title=new TextView(this);title.setText(label);title.setTextColor(Color.WHITE);title.setTextSize(18);title.setGravity(Gravity.CENTER);title.setPadding(Ui.dp(this,18),Ui.dp(this,14),Ui.dp(this,18),Ui.dp(this,14));title.setBackgroundColor(0x99000000);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(title,tp);
        Button stop=Ui.button(this,"停止",true);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,48),Gravity.TOP|Gravity.END);sp.setMargins(0,Ui.dp(this,28),Ui.dp(this,16),0);root.addView(stop,sp);stop.bringToFront();stop.setOnClickListener(v->stopAndFinish());
        setContentView(root);

        try{
            player=new androidx.media3.exoplayer.ExoPlayer.Builder(this).build();
            androidx.media3.common.AudioAttributes aa=new androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_ALARM).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build();
            player.setAudioAttributes(aa,true);player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);player.setPlayWhenReady(false);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,false).build());
            videoView.setPlayer(player);
            player.addListener(new androidx.media3.common.Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(state==androidx.media3.common.Player.STATE_READY){prepared=true;if(resumed&&!stopped){silenceFallback();try{player.play();}catch(Throwable ignored){}}}}
                @Override public void onPlayerError(androidx.media3.common.PlaybackException error){prepared=false;Toast.makeText(TimerVideoActivity.this,I18n.tr(TimerVideoActivity.this,"動画を再生できません"),Toast.LENGTH_LONG).show();resumeFallback();finish();}
            });
            Uri u=rawUri.startsWith("/")?Uri.fromFile(new java.io.File(rawUri)):Uri.parse(rawUri);player.setMediaItem(androidx.media3.common.MediaItem.fromUri(u));player.prepare();
        }catch(Throwable t){Toast.makeText(this,I18n.tr(this,"動画を再生できません"),Toast.LENGTH_LONG).show();resumeFallback();finish();}
    }

    private void serviceAction(String action){try{Intent i=new Intent(this,TimerRingService.class).setAction(action);startService(i);}catch(Throwable ignored){}}
    private void silenceFallback(){serviceAction(TimerRingService.ACTION_SILENCE);}
    private void resumeFallback(){if(!stopped)serviceAction(TimerRingService.ACTION_RESUME);}
    private void stopAndFinish(){stopped=true;releasePlayer();serviceAction(TimerRingService.ACTION_STOP);finish();}

    @Override protected void onResume(){super.onResume();resumed=true;if(prepared&&!stopped){silenceFallback();try{player.play();}catch(Throwable ignored){}}}
    @Override protected void onPause(){resumed=false;if(prepared&&!stopped){try{player.pause();}catch(Throwable ignored){}resumeFallback();}super.onPause();}
    @Override public void onBackPressed(){stopAndFinish();}
    private void releasePlayer(){prepared=false;if(videoView!=null)try{videoView.setPlayer(null);}catch(Throwable ignored){}if(player!=null){try{player.release();}catch(Throwable ignored){}player=null;}}
    @Override protected void onDestroy(){releasePlayer();super.onDestroy();}
}
