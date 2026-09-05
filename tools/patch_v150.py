from pathlib import Path
import re, runpy
runpy.run_path('tools/patch_v149.py', run_name='__main__')
app=Path('WakeGuard/app'); j=app/'src/main/java/jp/wakeguard/alarm'

def rep(path, old, new, name):
    p=path; s=p.read_text(encoding='utf-8')
    if old not in s: raise SystemExit(name+' anchor missing')
    s=s.replace(old,new,1); p.write_text(s,encoding='utf-8')

def sub(path, pattern, repl, name, flags=0):
    p=path; s=p.read_text(encoding='utf-8')
    out,n=re.subn(pattern,repl,s,count=1,flags=flags)
    if n!=1: raise SystemExit(name+' anchor missing')
    p.write_text(out,encoding='utf-8')

# Mission model: add NONE + 3 new interaction missions.
p=j/'AlarmStore.java'; s=p.read_text(encoding='utf-8')
s=s.replace('if("MATH".equals(s)||"TAP".equals(s)||"CODE".equals(s)||"SHAKE".equals(s)||"MEMORY".equals(s)||"TYPE".equals(s)||"HOLD".equals(s)||"RANDOM".equals(s)) return s;',
'''if("NONE".equals(s)||"MATH".equals(s)||"TAP".equals(s)||"CODE".equals(s)||"SHAKE".equals(s)||"MEMORY".equals(s)||"TYPE".equals(s)||"HOLD".equals(s)||"SWIPE".equals(s)||"ORDER".equals(s)||"REVERSE".equals(s)||"RANDOM".equals(s)) return s;''',1)
p.write_text(s,encoding='utf-8')

p=j/'AlarmProfiles.java'; s=p.read_text(encoding='utf-8')
s=s.replace('String[] pool={"STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD"};',
            'String[] pool={"STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD","SWIPE","ORDER","REVERSE"};',1)
s=s.replace('''            case "MATH": return "計算";''','''            case "NONE": return "なし";
            case "MATH": return "計算";''',1)
s=s.replace('''            case "HOLD": return "長押し";
            case "RANDOM": return "ランダム";''','''            case "HOLD": return "長押し";
            case "SWIPE": return "スワイプ";
            case "ORDER": return "順番タップ";
            case "REVERSE": return "逆順入力";
            case "RANDOM": return "ランダム";''',1)
s=s.replace('''            case "MATH": return "＋";''','''            case "NONE": return "✓";
            case "MATH": return "＋";''',1)
s=s.replace('''            case "HOLD": return "◉";
            case "RANDOM": return "?";''','''            case "HOLD": return "◉";
            case "SWIPE": return "↔";
            case "ORDER": return "123";
            case "REVERSE": return "⇄";
            case "RANDOM": return "?";''',1)
s=s.replace('''        if("STEPS".equals(t)) return e.steps+"歩";''','''        if("NONE".equals(t)) return "そのまま停止";
        if("STEPS".equals(t)) return e.steps+"歩";''',1)
s=s.replace('''        if("HOLD".equals(t)) return Math.max(2,Math.min(30,e.missionCount))+"秒";
        return "毎回変更";''','''        if("HOLD".equals(t)) return Math.max(2,Math.min(30,e.missionCount))+"秒";
        if("SWIPE".equals(t)) return e.missionCount+"回";
        if("ORDER".equals(t)) return e.missionCount+"回";
        if("REVERSE".equals(t)) return e.missionCount+"問";
        return "毎回変更";''',1)
p.write_text(s,encoding='utf-8')

# Editor: more dismiss methods + vibration off.
p=j/'AlarmEditorActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('private static final String[] MISSION_KEYS={"STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD","RANDOM"};',
            'private static final String[] MISSION_KEYS={"NONE","STEPS","MATH","TAP","CODE","SHAKE","MEMORY","TYPE","HOLD","SWIPE","ORDER","REVERSE","RANDOM"};',1)
s=s.replace('private static final String[] MISSION_LABELS={"歩数","計算","連打","コード入力","シェイク","記憶","文章入力","長押し","ランダム"};',
            'private static final String[] MISSION_LABELS={"なし","歩数","計算","連打","コード入力","シェイク","記憶","文章入力","長押し","スワイプ","順番タップ","逆順入力","ランダム"};',1)
s=s.replace('String[]modes={I18n.tr(this,"不規則"),I18n.tr(this,"強い連続パルス")};',
            'String[]modes={I18n.tr(this,"オフ"),I18n.tr(this,"不規則"),I18n.tr(this,"強い連続パルス")};',1)
p.write_text(s,encoding='utf-8')
sub(p,r'    private void refreshMissionFields\(\)\{.*?\n    \}\n\n\n\n    private void renderAutoStop\(\)\{',r'''    private void refreshMissionFields(){if(steps==null)return;String t=selectedMission;boolean needSteps="STEPS".equals(t)||"RANDOM".equals(t);boolean needCount=!("NONE".equals(t)||"STEPS".equals(t));stepsLabel.setVisibility(needSteps?View.VISIBLE:View.GONE);steps.setVisibility(needSteps?View.VISIBLE:View.GONE);countLabel.setVisibility(needCount?View.VISIBLE:View.GONE);missionCount.setVisibility(needCount?View.VISIBLE:View.GONE);
        if("NONE".equals(t)){missionNote.setText(I18n.tr(this,"追加の解除操作なし。停止ボタンを押すだけで解除できます。"));}
        else if("STEPS".equals(t)){stepsLabel.setText(I18n.tr(this,"解除に必要な歩数"));missionNote.setText(I18n.tr(this,"実際に歩いて解除します。"));}
        else if("MATH".equals(t)){countLabel.setText(I18n.tr(this,"正解する問題数"));missionNote.setText(I18n.tr(this,"足し算・引き算・掛け算を解きます。"));}
        else if("TAP".equals(t)){countLabel.setText(I18n.tr(this,"タップ回数"));missionNote.setText(I18n.tr(this,"画面のボタンを指定回数タップします。"));}
        else if("CODE".equals(t)){countLabel.setText(I18n.tr(this,"入力するコード数"));missionNote.setText(I18n.tr(this,"表示された6桁コードを正確に入力します。"));}
        else if("SHAKE".equals(t)){countLabel.setText(I18n.tr(this,"シェイク回数"));missionNote.setText(I18n.tr(this,"スマホを大きく振って解除します。"));}
        else if("MEMORY".equals(t)){countLabel.setText(I18n.tr(this,"記憶問題の数"));missionNote.setText(I18n.tr(this,"数秒だけ表示される数字を覚えて入力します。"));}
        else if("TYPE".equals(t)){countLabel.setText(I18n.tr(this,"入力する文章数"));missionNote.setText(I18n.tr(this,"表示された短い文章をそのまま入力します。"));}
        else if("HOLD".equals(t)){countLabel.setText(I18n.tr(this,"長押しする秒数"));missionNote.setText(I18n.tr(this,"ボタンを離さず長押しします。2〜30秒がおすすめです。"));}
        else if("SWIPE".equals(t)){countLabel.setText(I18n.tr(this,"スワイプ回数"));missionNote.setText(I18n.tr(this,"画面を左右に大きくスワイプして解除します。"));}
        else if("ORDER".equals(t)){countLabel.setText(I18n.tr(this,"順番タップの回数"));missionNote.setText(I18n.tr(this,"ばらばらに並んだ数字を1から6まで順番にタップします。"));}
        else if("REVERSE".equals(t)){countLabel.setText(I18n.tr(this,"逆順入力の問題数"));missionNote.setText(I18n.tr(this,"表示された数字を逆の順番で入力します。"));}
        else {stepsLabel.setText(I18n.tr(this,"歩数が選ばれた場合の歩数"));countLabel.setText(I18n.tr(this,"その他の回数 / 秒数"));missionNote.setText(I18n.tr(this,"鳴るたびに11種類のミッションから1つ選びます。"));}
    }



    private void renderAutoStop(){''','editor mission fields',re.S)
s=p.read_text(encoding='utf-8')
s=s.replace('vibration.setSelection("STRONG".equals(draft.vibration)?1:0);',
            'vibration.setSelection("OFF".equals(draft.vibration)?0:("STRONG".equals(draft.vibration)?2:1));',1)
s=s.replace('draft.vibration=vibration.getSelectedItemPosition()==1?"STRONG":"IRREGULAR";',
            'int vp=vibration.getSelectedItemPosition();draft.vibration=vp==0?"OFF":(vp==2?"STRONG":"IRREGULAR");',1)
p.write_text(s,encoding='utf-8')

# Alarm screen mission implementations.
p=j/'AlarmActivity.java'; s=p.read_text(encoding='utf-8')
s=s.replace('private long lastShakeMs=0L, holdStartMs=0L;', 'private long lastShakeMs=0L, holdStartMs=0L; private float swipeStartX=0f; private int orderNext=1;',1)
p.write_text(s,encoding='utf-8')
sub(p,r'    private void buildMission\(String type\)\{.*?\n    \}\n\n    private void newMath\(\)',r'''    private void buildMission(String type){
        missionCard.removeAllViews(); count=Ui.title(this,"",38);count.setGravity(Gravity.CENTER);missionCard.addView(count);
        prompt=Ui.text(this,"",19,Ui.TEXT);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,Ui.dp(this,18),0,0);missionCard.addView(prompt);
        feedback=Ui.text(this,"",13,Ui.MUTED);feedback.setGravity(Gravity.CENTER);feedback.setPadding(0,Ui.dp(this,10),0,0);missionCard.addView(feedback); action=null;answer=null;
        if("NONE".equals(type)){Prefs.missionComplete(this,true);prompt.setText(I18n.tr(this,"追加の解除操作はありません"));feedback.setText(I18n.tr(this,"下の停止ボタンを押してください"));}
        else if("STEPS".equals(type)){ prompt.setText(I18n.tr(this,"スマホを持って歩いてください")); }
        else if("TAP".equals(type)){ action=Ui.button(this,"タップ",false);action.setTextSize(22);action.setOnClickListener(v->{if(missionDone())return;int p=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,p);if(p>=targetCount())completeMission();render();});missionCard.addView(action,Ui.gapTop(this,22));prompt.setText(I18n.tr(this,"ボタンを繰り返しタップ")); }
        else if("SHAKE".equals(type)){ prompt.setText(I18n.tr(this,"スマホを大きく振ってください")); startShakeMission(); }
        else if("HOLD".equals(type)){ prompt.setText(I18n.tr(this,"ボタンを離さず押し続けてください")); action=Ui.button(this,"長押し",false);action.setTextSize(20);action.setOnTouchListener((v,e)->{if(missionDone())return true;if(e.getAction()==MotionEvent.ACTION_DOWN){holdStartMs=SystemClock.elapsedRealtime();startHoldTicker();feedback.setText(I18n.tr(this,"そのまま押し続けてください"));return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){holdStartMs=0;if(holdTicker!=null)missionHandler.removeCallbacks(holdTicker);feedback.setText(I18n.tr(this,"離すと最初からです"));render();return true;}return true;});missionCard.addView(action,Ui.gapTop(this,22)); }
        else if("SWIPE".equals(type)){prompt.setText(I18n.tr(this,"下のボタンを左右に大きくスワイプ"));action=Ui.button(this,"↔  スワイプ  ↔",false);action.setTextSize(20);action.setOnTouchListener((v,e)->{if(missionDone())return true;if(e.getAction()==MotionEvent.ACTION_DOWN){swipeStartX=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getX()-swipeStartX;if(Math.abs(dx)>=Ui.dp(this,100)){int q=Prefs.missionProgress(this)+1;Prefs.missionProgress(this,q);feedback.setText(I18n.tr(this,"スワイプを認識しました"));if(q>=targetCount())completeMission();render();}else feedback.setText(I18n.tr(this,"もっと大きくスワイプしてください"));return true;}return true;});missionCard.addView(action,new LinearLayout.LayoutParams(-1,Ui.dp(this,80)));}
        else if("ORDER".equals(type)){prompt.setText(I18n.tr(this,"1 → 6 の順番でタップ"));buildOrderRound();}
        else {
            answer=new EditText(this);answer.setTextColor(Ui.TEXT);answer.setHintTextColor(Ui.MUTED_2);answer.setGravity(Gravity.CENTER);answer.setTextSize(22);answer.setSingleLine(true);answer.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));answer.setBackground(Ui.roundStroke(Ui.SURFACE,Ui.BORDER,10,this));
            if("MATH".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);action=Ui.button(this,"回答",false);action.setOnClickListener(v->submitMath());newMath();}
            else if("CODE".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitCode());newCode();}
            else if("MEMORY".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"回答",false);action.setOnClickListener(v->submitMemory());newMemory();}
            else if("REVERSE".equals(type)){answer.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitReverse());newReverse();}
            else {answer.setInputType(android.text.InputType.TYPE_CLASS_TEXT);action=Ui.button(this,"確認",false);action.setOnClickListener(v->submitType());newPhrase();}
            missionCard.addView(answer,Ui.gapTop(this,22));missionCard.addView(action,Ui.gapTop(this,10));
        }
        render();
    }

    private void newMath()''','activity build mission',re.S)
s=p.read_text(encoding='utf-8')
anchor='''    private void submitType(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedPhrase.equals(v)){feedback.setText(I18n.tr(this,"文章が一致しません"));answer.setText("");return;}advanceRound();if(!missionDone())newPhrase();render();}\n\n'''
extra='''    private void submitType(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedPhrase.equals(v)){feedback.setText(I18n.tr(this,"文章が一致しません"));answer.setText("");return;}advanceRound();if(!missionDone())newPhrase();render();}\n\n    private void newReverse(){String shown=String.format(java.util.Locale.US,"%05d",new java.util.Random().nextInt(100000));expectedCode=new StringBuilder(shown).reverse().toString();prompt.setText(I18n.tr(this,"逆の順番で入力")+"\\n"+shown);if(answer!=null){answer.setText("");answer.setHint(I18n.tr(this,"逆順の5桁を入力"));}}\n    private void submitReverse(){if(answer==null||missionDone())return;String v=answer.getText().toString().trim();if(!expectedCode.equals(v)){feedback.setText(I18n.tr(this,"逆順が違います"));answer.setText("");return;}advanceRound();if(!missionDone())newReverse();render();}\n\n    private void buildOrderRound(){\n        final LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,Ui.dp(this,12),0,0);orderNext=1;java.util.ArrayList<Integer> nums=new java.util.ArrayList<>();for(int i=1;i<=6;i++)nums.add(i);java.util.Collections.shuffle(nums);for(int r=0;r<2;r++){LinearLayout row=Ui.row(this);for(int c=0;c<3;c++){final int n=nums.get(r*3+c);Button b=Ui.button(this,String.valueOf(n),false);b.setTextSize(22);b.setOnClickListener(v->tapOrder(n,box));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,60),1);if(c>0)lp.setMargins(Ui.dp(this,6),0,0,0);row.addView(b,lp);}box.addView(row,Ui.gapTop(this,r==0?0:6));}missionCard.addView(box,Ui.gapTop(this,10));feedback.setText(I18n.tr(this,"まず1をタップ"));}\n    private void tapOrder(int n,LinearLayout box){if(missionDone())return;if(n!=orderNext){feedback.setText(I18n.tr(this,"順番が違います。1からやり直し"));missionCard.removeView(box);buildOrderRound();return;}if(orderNext<6){orderNext++;feedback.setText(I18n.tr(this,"次は")+" "+orderNext);return;}advanceRound();missionCard.removeView(box);if(!missionDone())buildOrderRound();render();}\n\n'''
if anchor not in s: raise SystemExit('activity helper anchor missing')
s=s.replace(anchor,extra,1)
s=s.replace('private boolean missionDone(){ if("STEPS".equals(sessionType()))return Prefs.stepSensorAvailable(this)&&Prefs.currentSteps(this)>=AlarmProfiles.steps(this,Prefs.activeAlarmId(this)); return Prefs.missionComplete(this); }',
'''private boolean missionDone(){ String t=sessionType();if("NONE".equals(t)){if(!Prefs.missionComplete(this))Prefs.missionComplete(this,true);return true;} if("STEPS".equals(t))return Prefs.stepSensorAvailable(this)&&Prefs.currentSteps(this)>=AlarmProfiles.steps(this,Prefs.activeAlarmId(this)); return Prefs.missionComplete(this); }''',1)
s=s.replace('''        if("STEPS".equals(type)){ int s=Prefs.currentSteps(this);''','''        if("NONE".equals(type)){count.setText(I18n.tr(this,"解除できます"));if(feedback!=null)feedback.setText(I18n.tr(this,"下の停止ボタンを押してください"));}
        else if("STEPS".equals(type)){ int s=Prefs.currentSteps(this);''',1)
p.write_text(s,encoding='utf-8')

# Service: NONE is immediately dismissible, notifications know new missions, OFF means no vibration.
p=j/'AlarmService.java'; s=p.read_text(encoding='utf-8')
s=s.replace('Prefs.missionComplete(this, false);','Prefs.missionComplete(this, "NONE".equals(AlarmStore.normalizeMission(chosenMission)));',1)
s=s.replace('''        if ("STEPS".equals(t)) return I18n.tr(this,"あと " + Math.max(0, AlarmProfiles.steps(this,id) - Prefs.currentSteps(this)) + " 歩で解除");''','''        if ("NONE".equals(t)) return I18n.tr(this,"そのまま停止できます");
        if ("STEPS".equals(t)) return I18n.tr(this,"あと " + Math.max(0, AlarmProfiles.steps(this,id) - Prefs.currentSteps(this)) + " 歩で解除");''',1)
s=s.replace('''        if ("HOLD".equals(t)) return I18n.tr(this,"長押し " + Math.max(2,Math.min(30,n)) + "秒で解除");
        return I18n.tr(this,"ミッションを完了すると解除できます");''','''        if ("HOLD".equals(t)) return I18n.tr(this,"長押し " + Math.max(2,Math.min(30,n)) + "秒で解除");
        if ("SWIPE".equals(t)) return I18n.tr(this,"スワイプ " + n + "回で解除");
        if ("ORDER".equals(t)) return I18n.tr(this,"順番タップ " + n + "回で解除");
        if ("REVERSE".equals(t)) return I18n.tr(this,"逆順入力 " + n + "問で解除");
        return I18n.tr(this,"ミッションを完了すると解除できます");''',1)
s=s.replace('''                String mode = AlarmProfiles.vibration(this, Prefs.activeAlarmId(this));
                long[] timings;''','''                String mode = AlarmProfiles.vibration(this, Prefs.activeAlarmId(this));
                if ("OFF".equals(mode)) return;
                long[] timings;''',1)
p.write_text(s,encoding='utf-8')

# Translations: JA canonical + EN/KO.
p=j/'I18n.java'; s=p.read_text(encoding='utf-8')
s=s.replace('put("歩数","Steps","걸음 수"); put("計算","Math","계산");',
'''put("なし","None","없음"); put("歩数","Steps","걸음 수"); put("計算","Math","계산");''',1)
s=s.replace('put("文章入力","Text entry","문장 입력"); put("連打","Rapid tap","연타"); put("長押し","Press and hold","길게 누르기"); put("シェイク","Shake","흔들기"); put("ランダム","Random","랜덤");',
'''put("文章入力","Text entry","문장 입력"); put("連打","Rapid tap","연타"); put("長押し","Press and hold","길게 누르기"); put("シェイク","Shake","흔들기"); put("スワイプ","Swipe","스와이프"); put("順番タップ","Tap in order","순서 탭"); put("逆順入力","Reverse entry","역순 입력"); put("ランダム","Random","랜덤");''',1)
s=s.replace('put("学校、休日など","School, day off, etc.","학교, 휴일 등"); put("不規則","Irregular","불규칙");',
'''put("学校、休日など","School, day off, etc.","학교, 휴일 등"); put("オフ","Off","끄기"); put("不規則","Irregular","불규칙");''',1)
insert='''        put("追加の解除操作なし。停止ボタンを押すだけで解除できます。","No extra challenge. Just tap the stop button to dismiss.","추가 해제 동작 없이 중지 버튼만 누르면 해제됩니다.");
        put("追加の解除操作はありません","No extra dismissal challenge","추가 해제 동작이 없습니다"); put("下の停止ボタンを押してください","Tap the stop button below","아래 중지 버튼을 누르세요"); put("解除できます","Ready to dismiss","해제할 수 있습니다"); put("そのまま停止","Direct stop","바로 중지"); put("そのまま停止できます","You can stop it directly","바로 중지할 수 있습니다");
        put("スワイプ回数","Swipe count","스와이프 횟수"); put("画面を左右に大きくスワイプして解除します。","Swipe widely left or right to dismiss.","화면을 좌우로 크게 스와이프해 해제합니다."); put("下のボタンを左右に大きくスワイプ","Swipe the button widely left or right","아래 버튼을 좌우로 크게 스와이프하세요"); put("スワイプを認識しました","Swipe detected","스와이프를 인식했습니다"); put("もっと大きくスワイプしてください","Swipe farther","더 크게 스와이프하세요");
        put("順番タップの回数","Ordered-tap rounds","순서 탭 횟수"); put("ばらばらに並んだ数字を1から6まで順番にタップします。","Tap the shuffled numbers from 1 through 6 in order.","섞여 있는 숫자를 1부터 6까지 순서대로 탭합니다."); put("1 → 6 の順番でタップ","Tap in order: 1 → 6","1 → 6 순서로 탭"); put("まず1をタップ","Tap 1 first","먼저 1을 탭하세요"); put("順番が違います。1からやり直し","Wrong order. Start again from 1.","순서가 틀렸습니다. 1부터 다시 시작하세요"); put("次は","Next","다음은");
        put("逆順入力の問題数","Reverse-entry questions","역순 입력 문제 수"); put("表示された数字を逆の順番で入力します。","Enter the displayed digits in reverse order.","표시된 숫자를 역순으로 입력합니다."); put("逆の順番で入力","Enter in reverse order","역순으로 입력"); put("逆順の5桁を入力","Enter the 5 digits reversed","5자리를 역순으로 입력"); put("逆順が違います","Incorrect reverse order","역순이 틀렸습니다");
        put("鳴るたびに11種類のミッションから1つ選びます。","One of 11 missions is chosen each time the alarm rings.","알람이 울릴 때마다 11가지 미션 중 하나가 선택됩니다.");
'''
needle='''        put("鳴るたびに8種類のミッションから1つ選びます。","One of 8 missions is chosen each time the alarm rings.","알람이 울릴 때마다 8가지 미션 중 하나가 선택됩니다.");'''
if needle not in s: raise SystemExit('i18n insertion anchor missing')
s=s.replace(needle,needle+'\n'+insert,1)
needle2=next((line for line in s.splitlines() if 'm=Pattern.compile("^長押し (' in line),None)
if not needle2: raise SystemExit('i18n dynamic anchor missing')
extra_dynamic=("\n        m=Pattern.compile(\"^スワイプ (\\\\d+)回で解除$\").matcher(s);if(m.matches())return ko?\"스와이프 \"+m.group(1)+\"회로 해제\":\"Swipe \"+m.group(1)+\" times to dismiss\";" +
               "\n        m=Pattern.compile(\"^順番タップ (\\\\d+)回で解除$\").matcher(s);if(m.matches())return ko?\"순서 탭 \"+m.group(1)+\"회로 해제\":\"Complete \"+m.group(1)+\" ordered-tap rounds\";" +
               "\n        m=Pattern.compile(\"^逆順入力 (\\\\d+)問で解除$\").matcher(s);if(m.matches())return ko?\"역순 입력 \"+m.group(1)+\"문제로 해제\":\"Complete \"+m.group(1)+\" reverse-entry questions\";")
s=s.replace(needle2,needle2+extra_dynamic,1)
p.write_text(s,encoding='utf-8')

# Version.
p=app/'build.gradle.kts'; s=p.read_text(encoding='utf-8'); s=re.sub(r'versionCode = \d+','versionCode = 61',s); s=re.sub(r'versionName = "[^"]+"','versionName = "1.5.0"',s); p.write_text(s,encoding='utf-8')
print('WakeGuard v1.5.0 dismiss missions + vibration-off patch applied')