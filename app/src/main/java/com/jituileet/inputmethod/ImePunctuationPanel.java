package com.jituileet.inputmethod;

import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** TV-safe punctuation chooser. It stays inside the IME window and is fully D-pad focusable. */
public final class ImePunctuationPanel extends ScrollView {
    private final DrumstickImeService service;
    private final LinearLayout box;
    public ImePunctuationPanel(DrumstickImeService s){
        super(s); service=s; setFillViewport(true); setFocusable(true); setFocusableInTouchMode(true);
        setBackgroundColor(Prefs.dark(s)?0xFF202124:0xFFF6F7F9);
        box=new LinearLayout(s); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(80,32,80,32); addView(box);
        TextView title=new TextView(s); title.setText(s.isChineseLanguagePublic()?"选择标点":"Choose punctuation"); title.setTextSize(24); title.setTextColor(Prefs.dark(s)?0xFFF1F3F4:0xFF202124); title.setPadding(12,12,12,20); box.addView(title,new LinearLayout.LayoutParams(-1,72));
        add("？"); add("！"); add("。");
        Button back=new Button(s); back.setText(s.isChineseLanguagePublic()?"返回输入法":"Back to keyboard"); back.setAllCaps(false); back.setFocusable(true); back.setOnClickListener(v->service.restoreKeyboardView()); box.addView(back,new LinearLayout.LayoutParams(-1,72));
        postDelayed(()->box.getChildAt(1).requestFocus(),80);
    }
    private void add(String text){ Button b=new Button(service); b.setText(text); b.setTextSize(22); b.setAllCaps(false); b.setFocusable(true); b.setOnClickListener(v->{
        android.view.inputmethod.InputConnection ic=service.getCurrentInputConnection();
        if(ic!=null){
            if(!service.isChineseInputMode() || !service.engineHasComposingPublic()) { service.commitTextPublic(text); }
            else { String out=service.commitFirstPublic(); if(out!=null&&!out.isEmpty())service.commitTextPublic(out); service.commitTextPublic(text); }
        }
        service.restoreKeyboardView();
    }); box.addView(b,new LinearLayout.LayoutParams(-1,76)); }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true; int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){View f=findFocus();if(f!=null&&f.isClickable()){f.performClick();return true;}return true;}
        if(k==KeyEvent.KEYCODE_BACK){service.restoreKeyboardView();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){View f=findFocus();if(f==null)f=this;View n=f.focusSearch(k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN);if(n!=null){n.requestFocus();return true;}return true;}
        return super.dispatchKeyEvent(e);
    }
}
