package com.jituileet.inputmethod;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Button;
import java.util.*;

public class DrumstickImeService extends InputMethodService {
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public boolean onEvaluateInputViewShown() { return true; }
    private DrumstickKeyboardView view;
    private ChineseEngine engine;
    private PhoneInputServer phoneServer;
    private boolean physicalMode;
    private boolean shift;
    private boolean caps;
    private boolean numericMode;
    private boolean physicalDetectionAnnounced;
    private View activePanel;
    // Hardware D-pad is consumed only while the IME window is actually visible.
    private boolean imeWindowActive;
    private final BroadcastReceiver rimeReloadReceiver = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){ reloadRime(); } };

    @Override public void onCreate(){ super.onCreate();
        engine = new ChineseEngine(this, (cs, pre) -> { if(view!=null) view.setCandidates(cs, pre); InputConnection ic=getCurrentInputConnection(); if(ic!=null && !engine.isEnglish()){ ic.setComposingText(pre==null?"":pre,1); } });
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rimeReloadReceiver, new IntentFilter("com.jituileet.inputmethod.RELOAD_RIME"), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(rimeReloadReceiver, new IntentFilter("com.jituileet.inputmethod.RELOAD_RIME"));
        engine.setEnglish(!isChineseLanguage());
        phoneServer = new PhoneInputServer(this, text -> new Handler(Looper.getMainLooper()).post(() -> commitText(text)));
        if(Prefs.phone(this)) phoneServer.start();
    }
    @Override public void onFinishInputView(boolean finishingInput){
        try{ InputConnection ic=getCurrentInputConnection(); if(ic!=null) ic.finishComposingText(); }catch(Throwable ignored){}
        super.onFinishInputView(finishingInput);
    }
    @Override public void onWindowShown(){
        super.onWindowShown();
        imeWindowActive=true;
        if(view!=null) view.setVisibility(View.VISIBLE);
        if(activePanel!=null) activePanel.setVisibility(View.VISIBLE);
    }
    @Override public void onWindowHidden(){
        imeWindowActive=false;
        activePanel=null;
        if(view!=null){ view.clearFocus(); view.setVisibility(View.GONE); }
        super.onWindowHidden();
    }
    @Override public void onDestroy(){ try{ unregisterReceiver(rimeReloadReceiver); if(phoneServer!=null)phoneServer.stop(); if(engine!=null)engine.destroy(); }catch(Throwable ignored){} super.onDestroy(); }
    private boolean isChineseLanguage(){ String l=Prefs.language(this); return l.equals("zh") || (l.equals("auto") && Locale.getDefault().getLanguage().equals("zh")); }
    @Override public void onStartInputView(EditorInfo info, boolean restarting){
        super.onStartInputView(info,restarting);
        // A new IME window starts from the persisted base keyboard mode only.
        // Never restore transient UI such as Emoji, symbols, or the previous focus.
        int layout=Prefs.keyboardLayout(this);
        if(engine!=null){
            engine.resetComposition();
            engine.setEnglish(!isChineseLanguage());
        }
        if(view!=null){
            view.setVisibility(View.VISIBLE);
            view.resetForInputSession();
        }
    }
    @Override public View onCreateInputView(){
        view=new DrumstickKeyboardView(this);
        view.setColors(Prefs.color(this), Prefs.dark(this));
        view.setMicVisible(hasMicrophone());
        activePanel=null;
        return view;
    }
    @Override public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting){
        super.onStartInput(attribute,restarting); setExtractViewShown(false);
        physicalMode=false; physicalDetectionAnnounced=false; shift=false; caps=false; numericMode=false;
        try{InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.finishComposingText();}catch(Throwable ignored){}
        if(engine!=null){ engine.resetComposition(); engine.setEnglish(!isChineseLanguage()); }
        if(view!=null){ view.setPhysical(false); view.setCandidates(java.util.Collections.<String>emptyList(),""); view.resetForInputSession(); }
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event){
        // The IME only owns D-pad navigation while its own input/panel UI is shown.
        // Never consume D-pad events globally when the IME is hidden; doing so steals
        // DOWN/UP/LEFT/RIGHT from the foreground TV application.
        if(keyCode==KeyEvent.KEYCODE_BACK){
            if(activePanel!=null){ restoreKeyboardView(); return true; }
        }
        boolean dpad = keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode==KeyEvent.KEYCODE_DPAD_UP || keyCode==KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode==KeyEvent.KEYCODE_DPAD_CENTER;
        if(dpad){
            // Do not consume a single D-pad event unless the IME window is known to be shown.
            // This is what lets the foreground TV app regain its remote immediately after Hide.
            if(!imeWindowActive || !isInputViewShown()) return false;
            if(activePanel!=null && activePanel.getVisibility()==View.VISIBLE) return dispatchPanelKey(event);
            if(view!=null && view.getVisibility()==View.VISIBLE) return view.dispatchKeyEvent(event);
            return super.onKeyDown(keyCode,event);
        }

        // A standalone ENTER from a TV remote/editor must never establish physical-keyboard mode.
        // Only a real typing key may establish that mode; once already active, ENTER is handled normally.
        if(keyCode==KeyEvent.KEYCODE_ENTER && !physicalMode) return super.onKeyDown(keyCode,event);
        boolean hardware = PhysicalKeyboardDetector.isHardwareKeyboard(event);
        // ENTER must never be used to trigger the physical-keyboard detector.
        // It is a normal editor action and is handled only after physical keyboard mode
        // has already been established by an actual typing key.
        boolean hardwareDetectKey = PhysicalKeyboardDetector.isTypingKey(event)
                || (!engine.isEnglish() && (keyCode==KeyEvent.KEYCODE_SHIFT_LEFT || keyCode==KeyEvent.KEYCODE_SHIFT_RIGHT || keyCode==KeyEvent.KEYCODE_CAPS_LOCK));
        if(Prefs.keyboard(this) && activePanel==null && isInputViewShown() && hardware && hardwareDetectKey){
            if(!physicalDetectionAnnounced){
                physicalDetectionAnnounced=true;
                if(Prefs.hardwareNotice(this)) Toast.makeText(this, isChineseLanguage()?"检测到实体键盘，已切换键盘输入模式":"Physical keyboard detected, switched to keyboard input mode", Toast.LENGTH_SHORT).show();
            }
            if(!physicalMode){ physicalMode=true; if(view!=null)view.setPhysical(true); }
            // English mode is deliberately left to Android/editor handling. The IME only
            // consumes alphabetic input while Chinese composition is active.
            if(engine.isEnglish()) return super.onKeyDown(keyCode,event);
            if(keyCode==KeyEvent.KEYCODE_SHIFT_LEFT||keyCode==KeyEvent.KEYCODE_SHIFT_RIGHT){
                try{InputConnection x=getCurrentInputConnection();if(x!=null)x.finishComposingText();}catch(Throwable ignored){}
                engine.setEnglish(true); return super.onKeyDown(keyCode,event);
            }
            if(keyCode==KeyEvent.KEYCODE_CAPS_LOCK){ return super.onKeyDown(keyCode,event); }
            if(keyCode==KeyEvent.KEYCODE_PAGE_UP || keyCode==KeyEvent.KEYCODE_PAGE_DOWN){
                return view!=null && view.dispatchKeyEvent(event);
            }
            if((event.isCtrlPressed()) && (keyCode==KeyEvent.KEYCODE_C || keyCode==KeyEvent.KEYCODE_X || keyCode==KeyEvent.KEYCODE_A || keyCode==KeyEvent.KEYCODE_V)){
                InputConnection ic=getCurrentInputConnection();
                if(ic!=null){
                    if(keyCode==KeyEvent.KEYCODE_C) ic.performContextMenuAction(android.R.id.copy);
                    else if(keyCode==KeyEvent.KEYCODE_X) ic.performContextMenuAction(android.R.id.cut);
                    else if(keyCode==KeyEvent.KEYCODE_A) ic.performContextMenuAction(android.R.id.selectAll);
                    else if(keyCode==KeyEvent.KEYCODE_V) ic.performContextMenuAction(android.R.id.paste);
                }
                return true;
            }
            // Only letters enter Rime. Editing keys are handled locally so Chinese preedit
            // can be deleted/committed without stealing ordinary punctuation handling.
            if((keyCode>=KeyEvent.KEYCODE_A && keyCode<=KeyEvent.KEYCODE_Z)) return handlePhysicalKey(keyCode,event);
            if(keyCode==KeyEvent.KEYCODE_DEL || keyCode==KeyEvent.KEYCODE_SPACE || keyCode==KeyEvent.KEYCODE_ENTER) return handlePhysicalKey(keyCode,event);
            return super.onKeyDown(keyCode,event);
        }
        return super.onKeyDown(keyCode,event);
    }

    private boolean dispatchPanelKey(KeyEvent event){
        if(activePanel==null) return false;
        int k=event.getKeyCode();
        if(activePanel instanceof ImeCopySelectorPanel && (k==KeyEvent.KEYCODE_DPAD_LEFT || k==KeyEvent.KEYCODE_DPAD_RIGHT || k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER)){
            return activePanel.dispatchKeyEvent(event);
        }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){
            View f=activePanel.findFocus();
            if(f!=null && f.isClickable()){ f.performClick(); return true; }
            return true;
        }
        if(k==KeyEvent.KEYCODE_DPAD_UP || k==KeyEvent.KEYCODE_DPAD_DOWN || k==KeyEvent.KEYCODE_DPAD_LEFT || k==KeyEvent.KEYCODE_DPAD_RIGHT){
            int dir=k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:k==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:k==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:View.FOCUS_RIGHT;
            View f=activePanel.findFocus(); if(f==null) f=activePanel;
            View next=spatialPanelFocus(activePanel,f,dir);
            if(next!=null){ next.requestFocus(); ensurePanelVisible(next); return true; }
            if((dir==View.FOCUS_UP||dir==View.FOCUS_DOWN) && activePanel instanceof android.widget.ScrollView){
                activePanel.scrollBy(0,dir==View.FOCUS_DOWN?180:-180); return true;
            }
            return true;
        }
        return false;
    }

    private View spatialPanelFocus(View root, View current, int direction){
        java.util.ArrayList<View> list=new java.util.ArrayList<>();
        collectFocusable(root,list,root);
        android.graphics.Rect a=new android.graphics.Rect(); current.getGlobalVisibleRect(a);
        float ax=a.centerX(), ay=a.centerY();
        View best=null; double bestScore=Double.MAX_VALUE;
        for(View v:list){
            if(v==current || v.getVisibility()!=View.VISIBLE || !v.isFocusable()) continue;
            android.graphics.Rect b=new android.graphics.Rect(); v.getGlobalVisibleRect(b);
            float bx=b.centerX(), by=b.centerY(); float dx=bx-ax, dy=by-ay;
            boolean ok=direction==View.FOCUS_LEFT?dx<-2:direction==View.FOCUS_RIGHT?dx>2:direction==View.FOCUS_UP?dy<-2:dy>2;
            if(!ok) continue;
            double primary=(direction==View.FOCUS_LEFT||direction==View.FOCUS_RIGHT)?Math.abs(dx):Math.abs(dy);
            double cross=(direction==View.FOCUS_LEFT||direction==View.FOCUS_RIGHT)?Math.abs(dy):Math.abs(dx);
            // Strongly prefer a button in the same visual row/column.
            double score=primary*primary+cross*cross*0.18;
            if(score<bestScore){bestScore=score;best=v;}
        }
        if(best!=null) return best;

        // Horizontal navigation wraps within the nearest visual row.
        if(direction==View.FOCUS_LEFT || direction==View.FOCUS_RIGHT){
            double bestRow=Double.MAX_VALUE; View wrap=null;
            for(View v:list){
                if(v==current || v.getVisibility()!=View.VISIBLE || !v.isFocusable()) continue;
                android.graphics.Rect b=new android.graphics.Rect(); v.getGlobalVisibleRect(b);
                float dy=Math.abs(b.centerY()-ay);
                if(dy>Math.max(48f,a.height()*1.8f)) continue;
                float bx=b.centerX();
                boolean edge=direction==View.FOCUS_LEFT?bx>ax:bx<ax;
                if(!edge) continue;
                double score=dy*dy+Math.abs(bx-ax)*0.05;
                if(score<bestRow){bestRow=score;wrap=v;}
            }
            if(wrap!=null) return wrap;
        }
        return null;
    }

    private void collectFocusable(View v, java.util.ArrayList<View> out, View root){
        if(v!=null && v!=root && v.isFocusable()) out.add(v);
        if(v instanceof android.view.ViewGroup){
            android.view.ViewGroup g=(android.view.ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collectFocusable(g.getChildAt(i),out,root);
        }
    }
    private void ensurePanelVisible(View v){
        if(activePanel instanceof android.widget.ScrollView){
            android.graphics.Rect r=new android.graphics.Rect(); v.getDrawingRect(r);
            ((android.widget.ScrollView)activePanel).requestChildRectangleOnScreen(v,r,true);
        }
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event){
        return super.onKeyUp(keyCode,event);
    }
    private boolean isPhysicalTextKey(int k, KeyEvent e){ return (k>=KeyEvent.KEYCODE_A && k<=KeyEvent.KEYCODE_Z) || k==KeyEvent.KEYCODE_SPACE || k==KeyEvent.KEYCODE_DEL || k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_SHIFT_LEFT || k==KeyEvent.KEYCODE_SHIFT_RIGHT || k==KeyEvent.KEYCODE_CAPS_LOCK || k==KeyEvent.KEYCODE_COMMA || k==KeyEvent.KEYCODE_PERIOD || k==KeyEvent.KEYCODE_APOSTROPHE || k==KeyEvent.KEYCODE_SEMICOLON || k==KeyEvent.KEYCODE_SLASH || k==KeyEvent.KEYCODE_MINUS || k==KeyEvent.KEYCODE_EQUALS || (k>=KeyEvent.KEYCODE_0 && k<=KeyEvent.KEYCODE_9); }
    private boolean handlePhysicalKey(int k, KeyEvent e){
        InputConnection ic=getCurrentInputConnection();
        if(k==KeyEvent.KEYCODE_DEL){
            if(engine.hasComposing()) engine.backspace(); else if(ic!=null) ic.deleteSurroundingText(1,0);
            return true;
        }
        if(k==KeyEvent.KEYCODE_ENTER){
            if(engine.hasComposing()){ String out=engine.commitFirst(); if(out!=null&&!out.isEmpty()) commitText(out); else if(ic!=null) sendEnter(ic); }
            else if(ic!=null) sendEnter(ic);
            return true;
        }
        if(k==KeyEvent.KEYCODE_SPACE){
            if(engine.hasComposing()){ String out=engine.commitFirst(); if(out!=null&&!out.isEmpty()) commitText(out); }
            else if(ic!=null) commitText(" ");
            return true;
        }
        if(k>=KeyEvent.KEYCODE_A && k<=KeyEvent.KEYCODE_Z){
            String letter=String.valueOf((char)('a'+(k-KeyEvent.KEYCODE_A)));
            engine.input(letter);
            return true;
        }
        return false;
    }
    private void sendEnter(InputConnection ic){ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));}

    public void moveCursor(int direction){
        InputConnection ic=getCurrentInputConnection();
        if(ic==null)return;
        try{
            android.view.inputmethod.ExtractedTextRequest req=new android.view.inputmethod.ExtractedTextRequest();
            req.token=1;
            android.view.inputmethod.ExtractedText et=ic.getExtractedText(req,0);
            if(et!=null){
                int pos=et.selectionStart + direction;
                pos=Math.max(0, Math.min(et.text==null?0:et.text.length(), pos));
                ic.setSelection(pos,pos);
            }else{
                if(direction<0) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));
                else ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
            }
        }catch(Throwable ignored){}
    }

    private void showPunctuationChooser(){
        activePanel=new ImePunctuationPanel(this);
        setInputView(activePanel);
    }

    public boolean isChineseInputMode(){ return engine!=null && !engine.isEnglish(); }
    public boolean engineHasComposingPublic(){ return engine!=null && engine.hasComposing(); }
    public String commitFirstPublic(){ return engine==null?null:engine.commitFirst(); }
    public void commitTextPublic(String text){ commitText(text); }

    public void splitPinyin(){
        if(engine==null || engine.isEnglish()) return;
        engine.splitPinyin();
    }

    public void press(String label){
        InputConnection ic=getCurrentInputConnection(); if(ic==null)return;
        if(label.equals("Settings")) label="设置"; if(label.equals("Copy")) label="复制"; if(label.equals("Clipboard")) label="剪贴板"; if(label.equals("Voice")) label="语音"; if(label.equals("Hide")) label="隐藏"; if(label.equals("Space")) label="空格"; if(label.equals("Enter")) label="回车"; if(label.equals("Backspace")) label="⌫"; if(label.equals("Enter")) label="↵"; if(label.equals("ZH/EN")) label="中/英"; if(label.equals("EN/ZH")) label="英/中";
        if(label.equals("设置")){ Toast.makeText(this,isChineseLanguage()?"已打开设置":"Settings opened",Toast.LENGTH_SHORT).show(); showSettingsPanel(); return; }
        if(label.equals("隐藏")){ hideInputViewTemporarily(); return; }
        if(label.equals("剪贴板")){ Toast.makeText(this,isChineseLanguage()?"已打开剪贴板":"Clipboard opened",Toast.LENGTH_SHORT).show(); showClipboardHistory(); return; }
        if(label.equals("复制")){ Toast.makeText(this,isChineseLanguage()?"正在读取可复制文字":"Reading copyable text",Toast.LENGTH_SHORT).show(); showCopyDialog(); return; }
        if(label.equals("语音")){ try {
            final android.speech.SpeechRecognizer sr=android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new android.speech.RecognitionListener(){
                public void onResults(Bundle r){ java.util.ArrayList<String> a=r.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()) commitText(a.get(0)); sr.destroy(); }
                public void onError(int e){ sr.destroy(); } public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float v){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}
            });
            Intent i=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); sr.startListening(i);
        } catch(Exception e){ Toast.makeText(this,"设备不支持语音输入",Toast.LENGTH_SHORT).show(); } return; }
        if(label.equals("←")){ moveCursor(-1); return; }
        if(label.equals("→")){ moveCursor(1); return; }
        if(label.equals("⌫")){ if(!engine.isEnglish()&&engine.hasComposing()) engine.backspace(); else ic.deleteSurroundingText(1,0); return; }
        if(label.equals("空格")){ if(engine.isEnglish()) commitText(" "); else if(engine.hasComposing()){ String out=engine.commitFirst(); if(out!=null&&!out.isEmpty()) commitText(out); } else commitText(" "); return; }
        if(label.equals("回车") || label.equals("↵")){ if(!engine.isEnglish()&&engine.hasComposing()){String out=engine.commitFirst();if(out!=null&&!out.isEmpty())commitText(out);} ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER)); return; }
        if(label.equals("中/英")){
            // One shared language state for both 26-key and 9-key layouts.
            // Switching is always a switch; it must not become a different action
            // just because a pinyin composition is currently visible.
            try{InputConnection x=getCurrentInputConnection();if(x!=null)x.finishComposingText();}catch(Throwable ignored){}
            engine.setEnglish(!engine.isEnglish());
            if(view!=null) view.invalidate();
            return;
        }
        if(label.equals("分词")){ splitPinyin(); return; }
        if(label.equals("？！。")){ showPunctuationChooser(); return; }
        if(label.equals("拼音")){ engine.setEnglish(false); return; }
        if(label.equals("英123")){ engine.setEnglish(true); numericMode=true; if(view!=null)view.setNumericMode(true); return; }
        if(label.equals("符号")){ numericMode=true; if(view!=null)view.setNumericMode(true); return; }
        if(label.equals("搜索")){ if(!engine.isEnglish()&&engine.hasComposing()){String out=engine.commitFirst();if(out!=null&&!out.isEmpty())commitText(out);} return; }
        if(label.equals("🌐")){ showKeyboardModePanel(); return; }
        if(label.equals("?123")){
            numericMode=true;
            if(view!=null)view.setNumericMode(true);
            return;
        }
        if(label.equals("ABC") && view!=null && view.isNumericMode()){
            numericMode=false;
            view.setNumericMode(false);
            return;
        }
        if(label.equals("ABC")||label.equals("DEF")||label.equals("GHI")||label.equals("JKL")||label.equals("MNO")||label.equals("PQRS")||label.equals("TUV")||label.equals("WXYZ")){
            if(!engine.isEnglish()){ engine.input(label.substring(0,1).toLowerCase(java.util.Locale.ROOT)); } else { commitText(label.substring(0,1)); }
            return;
        }
        if(label.equals("表情")||label.equals("Emoji")){ if(view!=null)view.setEmojiMode(true); return; }
        if(label.equals("，")||label.equals("。")||label.equals(",")||label.equals(".")){ if(!engine.isEnglish()&&engine.hasComposing()){String out=engine.commitFirst();if(out!=null&&!out.isEmpty())commitText(out);} commitText(label); return; }
        if(label.equals("Shift")){ engine.setEnglish(!engine.isEnglish()); view.invalidate(); return; }
        if(label.equals("Caps")){ caps=!caps; engine.setEnglish(true); view.invalidate(); return; }
        if(label.matches("[0-9]")){
            if(Prefs.keyboardLayout(this)==1 && !engine.isEnglish()){
                String[] groups={"0","abc","def","ghi","jkl","mno","pqrs","tuv","wxyz","0"};
                int n=label.charAt(0)-'0'; if(n>=1&&n<=9){ engine.input(String.valueOf(groups[n].charAt(0))); return; }
            }
            commitText(label); return;
        }
        if(label.equals("abc")||label.equals("def")||label.equals("ghi")||label.equals("jkl")||label.equals("mno")||label.equals("pqrs")||label.equals("tuv")||label.equals("wxyz")){ if(!engine.isEnglish()){ char first=label.charAt(0); engine.input(String.valueOf(first)); } else commitText(label); return; }
        if(label.length()==1 && Character.isLetter(label.charAt(0))){ if(engine.isEnglish()){ commitText(String.valueOf(caps?Character.toUpperCase(label.charAt(0)):label.charAt(0))); } else { String out=engine.input(label); if(out!=null&&!out.isEmpty()) commitText(out); } return; }
        commitText(label);
    }
    private void commitText(String text){
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null && text!=null) ic.commitText(text,1);
    }
    public void commitEngineResult(String text){ if(text!=null&&!text.isEmpty()) commitText(text); }
    private boolean hasMicrophone(){ AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE); return a!=null && getPackageManager().hasSystemFeature("android.hardware.microphone"); }
    private void hideInputViewTemporarily(){
        try {
            activePanel=null;
            imeWindowActive=false;
            if(view!=null){ view.clearFocus(); }
            // Hiding the View alone leaves the InputMethodService window active.
            // Ask Android to dismiss the IME window so D-pad/key events return to the foreground app.
            requestHideSelf(0);
        } catch(Throwable ignored) {}
    }

    private void showClipboardHistory(){
        ArrayList<String> items=ClipboardHistory.load(this);
        try { android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip()!=null&&cm.getPrimaryClip().getItemCount()>0){ CharSequence cs=cm.getPrimaryClip().getItemAt(0).coerceToText(this); if(cs!=null&&cs.length()>0){ClipboardHistory.add(this,cs.toString());items=ClipboardHistory.load(this);} } } catch(Throwable ignored) {}
        activePanel=new ImeClipboardPanel(this,items); setInputView(activePanel);
    }

    private void showCopyDialog(){
        // Copy intentionally reads screen content through the optional accessibility
        // service. InputConnection is not used as a fallback because it cannot read
        // arbitrary text outside the current editor.
        try {
            if(!DrumstickAccessibilityService.isEnabled(this)){
                activePanel=new ImeAccessibilityCopyPanel(this);
                setInputView(activePanel);
                return;
            }
            String text=DrumstickAccessibilityService.captureVisibleText(this);
            if(text==null || text.trim().isEmpty()){
                Toast.makeText(this,isChineseLanguage()?"没有检测到可复制的文字，请尝试更换页面。":"No copyable text was detected. Try another screen.",Toast.LENGTH_SHORT).show();
                return;
            }
            activePanel=new ImeCopySelectorPanel(this,text);
            setInputView(activePanel);
        }catch(Throwable e){
            Toast.makeText(this,isChineseLanguage()?"读取屏幕文字失败":"Failed to read screen text",Toast.LENGTH_SHORT).show();
        }
    }

    public void showKeyboardModePanel(){ activePanel=new ImeKeyboardModePanel(this); setInputView(activePanel); }
    public void selectKeyboardMode(int mode){
        // Only 26-key and 9-key are persistent base layouts. English is a temporary mode
        // and must not replace the selected base layout when the IME is reopened.
        if(mode==0 || mode==1) Prefs.keyboardLayout(this,mode);
        try{InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.finishComposingText();}catch(Throwable ignored){}
        if(mode==2) engine.setEnglish(true); else engine.setEnglish(false);
        restoreKeyboardView();
        if(view!=null) view.setKeyboardLayout(mode);
        Toast.makeText(this, mode==0?(isChineseLanguage()?"已切换到26键":"26-key selected"):mode==1?(isChineseLanguage()?"已切换到9键":"9-key selected"):(isChineseLanguage()?"已切换到英文键盘":"English keyboard selected"), Toast.LENGTH_SHORT).show();
    }
    public void showSettingsPanel(){ activePanel=new ImeSettingsPanel(this); setInputView(activePanel); }
    public void showClipboardHistoryPublic(){ showClipboardHistory(); }
    public void showClipboardEdit(int index,String text){ ImeClipboardEditActivity.open(this,index,text); }
    public void restoreKeyboardView(){ activePanel=null; if(view==null){view=new DrumstickKeyboardView(this);view.setColors(Prefs.color(this),Prefs.dark(this));view.setMicVisible(hasMicrophone());} setInputView(view); view.setVisibility(View.VISIBLE); imeWindowActive=true; view.requestFocus(); }
    public void refreshImeView(){ if(view!=null){view.setColors(Prefs.color(this),Prefs.dark(this));view.invalidate();} }
    public void openSystemImeSettings(){
        try{
            startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch(Throwable t){
            Toast.makeText(this,isChineseLanguage()?"无法打开系统设置":"Unable to open system settings",Toast.LENGTH_SHORT).show();
        }
    }
    public void commitClipboardText(String text){ if(text==null)return; InputConnection ic=getCurrentInputConnection(); if(ic!=null){ic.commitText(text,1);} }
    public String engineChoose(int index){ return engine.choose(index); }
    public boolean engineChangePage(boolean backward){ return engine.changePage(backward); }
    public void setPhoneInput(boolean on){ Prefs.phone(this,on); if(on)phoneServer.start();else phoneServer.stop(); }
    public String phoneUrl(){ return phoneServer.getUrl(); }
    public boolean isChineseLanguagePublic(){ return isChineseLanguage(); }
    public DrumstickKeyboardView keyboardView(){ return view; }
    public void reloadRime(){ if(engine!=null) engine.destroy(); engine=new ChineseEngine(this,(cs,pre)->{if(view!=null)view.setCandidates(cs,pre); InputConnection ic=getCurrentInputConnection(); if(ic!=null&&!engine.isEnglish()) ic.setComposingText(pre==null?"":pre,1);}); engine.setEnglish(!isChineseLanguage()); }
    public void clearUsage(){ if(engine!=null) engine.clearUsage(); }
    public void startPhoneInput(){ if(phoneServer!=null) phoneServer.start(); }
    public void stopPhoneInput(){ if(phoneServer!=null) phoneServer.stop(); }
    public void openDictionaryPicker(){ startActivity(new Intent(this, SettingsActivity.class).putExtra("open_dictionary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
}
