package jp.wakeguard.alarm;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public class MultiAlarmActivity extends Activity {
    @Override protected void onCreate(Bundle b){super.onCreate(b);Ui.launchNoAnimation(this,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));Ui.finishNoAnimation(this);}
}
