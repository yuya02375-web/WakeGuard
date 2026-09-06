from pathlib import Path
import re

app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'

def read(p): return p.read_text(encoding='utf-8')
def write(p,s): p.write_text(s,encoding='utf-8')
def rep(s,old,new,name):
    if old not in s: raise SystemExit(name+' anchor missing')
    return s.replace(old,new,1)

# v1.6.9: make timer video captions render the same way as alarm video.
p=app/'build.gradle.kts'; s=read(p)
s=re.sub(r'versionCode = \d+','versionCode = 80',s)
s=re.sub(r'versionName = "[^"]+"','versionName = "1.6.9"',s)
write(p,s)

p=j/'TimerVideoActivity.java'; s=read(p)
old='''        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        videoView=new androidx.media3.ui.PlayerView(this);videoView.setBackgroundColor(Color.BLACK);videoView.setShutterBackgroundColor(Color.BLACK);videoView.setUseController(false);videoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);videoView.setKeepScreenOn(true);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=Gravity.CENTER;root.addView(videoView,vp);
'''
new='''        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        // Use exactly the same TextureView-based PlayerView layout as alarm video. PlayerView contains
        // Media3's SubtitleView, so embedded subtitle/caption tracks are rendered over the video.
        videoView=(androidx.media3.ui.PlayerView)getLayoutInflater().inflate(R.layout.view_alarm_video_texture,root,false);
        videoView.setBackgroundColor(Color.BLACK);videoView.setShutterBackgroundColor(Color.BLACK);videoView.setUseController(false);videoView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);videoView.setKeepScreenOn(true);
        androidx.media3.ui.SubtitleView subtitleView=videoView.getSubtitleView();
        if(subtitleView!=null){subtitleView.setVisibility(View.VISIBLE);subtitleView.setApplyEmbeddedStyles(true);subtitleView.setApplyEmbeddedFontSizes(true);subtitleView.setBottomPaddingFraction(0.12f);}
        FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=Gravity.CENTER;root.addView(videoView,vp);
'''
s=rep(s,old,new,'timer video use alarm PlayerView')

# The old full-width bottom stop button could cover subtitle lines. Match the alarm video pattern and
# keep the action in the top-right so the subtitle safe area stays clear.
old='''        Button stop=Ui.button(this,"タイマーを停止",true);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,Ui.dp(this,58),Gravity.BOTTOM);sp.setMargins(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,24));root.addView(stop,sp);stop.setOnClickListener(v->stopAndFinish());
'''
new='''        Button stop=Ui.button(this,"停止",true);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(Ui.dp(this,86),Ui.dp(this,48),Gravity.TOP|Gravity.END);sp.setMargins(0,Ui.dp(this,28),Ui.dp(this,16),0);root.addView(stop,sp);stop.bringToFront();stop.setOnClickListener(v->stopAndFinish());
'''
s=rep(s,old,new,'timer subtitle safe stop button')

# Explicitly keep the text track type enabled. PlayerView then renders the selected embedded captions.
old='''            player.setAudioAttributes(aa,true);player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);player.setPlayWhenReady(false);videoView.setPlayer(player);
'''
new='''            player.setAudioAttributes(aa,true);player.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);player.setPlayWhenReady(false);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,false).build());
            videoView.setPlayer(player);
'''
s=rep(s,old,new,'timer enable subtitle track')
write(p,s)

assert 'versionName = "1.6.9"' in read(app/'build.gradle.kts')
ts=read(j/'TimerVideoActivity.java')
assert 'inflate(R.layout.view_alarm_video_texture' in ts
assert 'getSubtitleView()' in ts and 'setApplyEmbeddedStyles(true)' in ts
assert 'TRACK_TYPE_TEXT,false' in ts
assert 'Gravity.TOP|Gravity.END' in ts
print('WakeGuard v1.6.9 timer video subtitle parity patch applied')
