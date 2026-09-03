from pathlib import Path
import runpy

runpy.run_path("tools/patch_v120.py", run_name="__main__")
res=Path("WakeGuard/app/src/main/res/layout")
(res/"notification_timer_running.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/timer_notification_root"
    android:layout_width="match_parent"
    android:layout_height="48dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingLeft="10dp"
    android:paddingRight="10dp"
    android:background="#FFF3E0">
    <TextView
        android:id="@+id/timer_notification_label"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxWidth="92dp"
        android:ellipsize="end"
        android:singleLine="true"
        android:text="タイマー"
        android:textColor="#5D4037"
        android:textStyle="bold"
        android:textSize="11sp" />
    <Chronometer
        android:id="@+id/timer_notification_chronometer"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center_vertical|end"
        android:fontFamily="monospace"
        android:textColor="#111111"
        android:textStyle="bold"
        android:textSize="28sp" />
</LinearLayout>
''',encoding="utf-8")
print("WakeGuard v1.2.0 final: retains Android 12 compact 48dp timer notification")
