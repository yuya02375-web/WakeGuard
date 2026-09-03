from pathlib import Path
import runpy

runpy.run_path("tools/patch_v143.py", run_name="__main__")

app = Path("WakeGuard/app")
java = app / "src/main/java/jp/wakeguard/alarm"

# Timer UX fixes requested from the on-device screenshot:
# - add +1 second adjustment
# - normalize overflowing minute/second input into canonical H:M:S before starting
# - make the timer overflow menu explicitly dark so its text cannot blend into the background
p = java / "ClockActivity.java"
s = p.read_text(encoding="utf-8")

s = s.replace(
    '    private EditText quickHours, quickMinutes, quickSeconds;\n    private Switch quickSave;',
    '    private EditText quickHours, quickMinutes, quickSeconds;\n    private Switch quickSave;\n    private boolean normalizingQuickDuration = false;',
    1,
)

s = s.replace(
    '        quickHours=quickNumber("時"); quickMinutes=quickNumber("分"); quickSeconds=quickNumber("秒");',
    '        quickHours=quickNumber("時"); quickMinutes=quickNumber("分"); quickSeconds=quickNumber("秒");\n        installQuickDurationNormalization();',
    1,
)

old_adjust = '        String[] labels={"−1分","＋10秒","＋30秒","＋1分","＋5分","＋10分","＋1時間"}; long[] deltas={-60000L,10000L,30000L,60000L,300000L,600000L,3600000L};'
new_adjust = '        String[] labels={"−1分","＋1秒","＋10秒","＋30秒","＋1分","＋5分","＋10分","＋1時間"}; long[] deltas={-60000L,1000L,10000L,30000L,60000L,300000L,600000L,3600000L};'
if old_adjust not in s:
    raise SystemExit("v1.4.4 adjustment row anchor missing")
s = s.replace(old_adjust, new_adjust, 1)

needle = '''    private TextView timerTextAction(String label,float size,int color){'''
helpers = r'''    private void installQuickDurationNormalization(){
        View.OnFocusChangeListener onBlur=(v,hasFocus)->{if(!hasFocus)normalizeQuickDurationFields();};
        quickHours.setOnFocusChangeListener(onBlur); quickMinutes.setOnFocusChangeListener(onBlur); quickSeconds.setOnFocusChangeListener(onBlur);
        quickHours.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        quickMinutes.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        quickSeconds.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        quickSeconds.setOnEditorActionListener((v,actionId,event)->{
            boolean enter=event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_UP;
            if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_DONE||enter){
                normalizeQuickDurationFields();
                try{android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(v.getWindowToken(),0);}catch(Throwable ignored){}
                v.clearFocus(); return true;
            }
            return false;
        });
    }

    private boolean normalizeQuickDurationFields(){
        if(normalizingQuickDuration||quickHours==null||quickMinutes==null||quickSeconds==null)return false;
        long h=parseLong(quickHours),m=parseLong(quickMinutes),sec=parseLong(quickSeconds);
        if(m<60L&&sec<60L)return false;
        long totalSeconds;
        try{totalSeconds=Math.addExact(Math.addExact(Math.multiplyExact(h,3600L),Math.multiplyExact(m,60L)),sec);}catch(ArithmeticException ex){totalSeconds=Long.MAX_VALUE/1000L;}
        totalSeconds=Math.max(0L,Math.min(totalSeconds,Long.MAX_VALUE/1000L));
        normalizingQuickDuration=true;
        try{setQuickDuration(totalSeconds*1000L);}finally{normalizingQuickDuration=false;}
        return true;
    }

''' + needle
if needle not in s:
    raise SystemExit("v1.4.4 timer helper insertion point missing")
s = s.replace(needle, helpers, 1)

old_start = '''    private void startQuickEditor(){
        long total=quickDuration(); if(total<=0){Toast.makeText(this,I18n.tr(this,"時間を入力してください"),Toast.LENGTH_SHORT).show();return;}
        boolean saved=quickSave!=null&&quickSave.isChecked(); TimerStore.Entry e=saved?TimerStore.addSaved(this,"",total):TimerStore.addTemporary(this,"",total); startTimerEntry(e); renderTimers();
    }'''
new_start = '''    private void startQuickEditor(){
        if(normalizeQuickDurationFields()){
            Toast.makeText(this,I18n.tr(this,"時間表記に直しました。確認してから開始してください"),Toast.LENGTH_SHORT).show();
            return;
        }
        long total=quickDuration(); if(total<=0){Toast.makeText(this,I18n.tr(this,"時間を入力してください"),Toast.LENGTH_SHORT).show();return;}
        boolean saved=quickSave!=null&&quickSave.isChecked(); TimerStore.Entry e=saved?TimerStore.addSaved(this,"",total):TimerStore.addTemporary(this,"",total); startTimerEntry(e); renderTimers();
    }'''
if old_start not in s:
    raise SystemExit("v1.4.4 quick start block missing")
s = s.replace(old_start, new_start, 1)

old_menu = '''    private void showTimerMenu(View anchor,long id){
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;PopupMenu menu=new PopupMenu(this,anchor);
        String saveText=I18n.tr(this,"保存"),resetText=I18n.tr(this,"リセット"),deleteText=I18n.tr(this,"削除");if(!e.saved)menu.getMenu().add(saveText);menu.getMenu().add(resetText);menu.getMenu().add(deleteText);
        menu.setOnMenuItemClickListener(item->{String x=String.valueOf(item.getTitle());if(saveText.equals(x)){TimerStore.makeSaved(this,id);renderTimers();return true;}if(resetText.equals(x)){resetTimer(id);return true;}if(deleteText.equals(x)){TimerReceiver.cancel(this,id);TimerStore.delete(this,id);renderTimers();return true;}return false;});menu.show();
    }'''
new_menu = '''    private void showTimerMenu(View anchor,long id){
        TimerStore.Entry e=TimerStore.find(this,id);if(e==null)return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6),Ui.dp(this,6));box.setBackground(Ui.roundStroke(Ui.SURFACE_2,Ui.BORDER,12,this));
        PopupWindow popup=new PopupWindow(box,Ui.dp(this,176),WindowManager.LayoutParams.WRAP_CONTENT,true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setFocusable(true);if(Build.VERSION.SDK_INT>=21)popup.setElevation(Ui.dp(this,10));
        if(!e.saved)addTimerMenuItem(box,popup,"保存",()->{TimerStore.makeSaved(this,id);renderTimers();});
        addTimerMenuItem(box,popup,"リセット",()->resetTimer(id));
        addTimerMenuItem(box,popup,"削除",()->{TimerReceiver.cancel(this,id);TimerStore.delete(this,id);renderTimers();});
        popup.showAsDropDown(anchor,-Ui.dp(this,146),-Ui.dp(this,8));
    }

    private void addTimerMenuItem(LinearLayout box,PopupWindow popup,String label,Runnable action){
        TextView item=timerTextAction(label,15,Ui.TEXT);item.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);item.setMinHeight(Ui.dp(this,48));item.setPadding(Ui.dp(this,16),0,Ui.dp(this,16),0);
        item.setOnClickListener(v->{popup.dismiss();action.run();});box.addView(item,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
    }'''
if old_menu not in s:
    raise SystemExit("v1.4.4 timer menu block missing")
s = s.replace(old_menu, new_menu, 1)

p.write_text(s, encoding="utf-8")

# Localize the one new confirmation message.
p = java / "I18n.java"
i = p.read_text(encoding="utf-8")
anchor = '        put("時間を設定してください","Set a duration","시간을 설정하세요"); put("時間を入力してください","Enter a duration","시간을 입력하세요");'
addition = anchor + '\n        put("時間表記に直しました。確認してから開始してください","Converted to normal time format. Check it, then tap Start.","일반 시간 형식으로 변환했습니다. 확인한 뒤 시작을 눌러 주세요.");'
if anchor not in i:
    raise SystemExit("v1.4.4 i18n anchor missing")
i = i.replace(anchor, addition, 1)
p.write_text(i, encoding="utf-8")

# Version bump.
gradle_path = app / "build.gradle.kts"
gradle = gradle_path.read_text(encoding="utf-8")
if 'versionCode = 54' not in gradle or 'versionName = "1.4.3"' not in gradle:
    raise SystemExit("v1.4.3 version markers missing")
gradle = gradle.replace('versionCode = 54', 'versionCode = 55', 1)
gradle = gradle.replace('versionName = "1.4.3"', 'versionName = "1.4.4"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

# Verification.
clock = (java / "ClockActivity.java").read_text(encoding="utf-8")
for needle in [
    '"＋1秒"',
    "installQuickDurationNormalization",
    "normalizeQuickDurationFields",
    "Math.multiplyExact(m,60L)",
    "時間表記に直しました。確認してから開始してください",
    "new PopupWindow",
    "Ui.SURFACE_2",
    "addTimerMenuItem",
]:
    if needle not in clock:
        raise SystemExit(f"Missing v1.4.4 timer marker: {needle}")
if "new PopupMenu(this,anchor)" in clock:
    raise SystemExit("Old theme-dependent timer PopupMenu still present")
if 'versionCode = 55' not in gradle_path.read_text(encoding="utf-8") or 'versionName = "1.4.4"' not in gradle_path.read_text(encoding="utf-8"):
    raise SystemExit("v1.4.4 version bump missing")

print("WakeGuard v1.4.4 timer UX patch applied")
print("+1 second fine adjustment: PASS")
print("Overflowing H:M:S input normalization without immediate start: PASS")
print("Explicit dark timer overflow menu: PASS")
