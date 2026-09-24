package com.jituileet.inputmethod;

import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Modal switcher shown by the globe key; it never changes layout silently. */
public final class ImeKeyboardModePanel extends ScrollView {
    private final DrumstickImeService service;
    private final LinearLayout box;
    private final boolean zh;

    public ImeKeyboardModePanel(DrumstickImeService s) {
        super(s); service=s; zh=s.isChineseLanguagePublic();
        setFillViewport(true); setFocusable(true); setFocusableInTouchMode(true);
        setBackgroundColor(Prefs.dark(s)?0xDD202124:0xE6FFFFFF);
        box=new LinearLayout(s); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(80,32,80,32); addView(box);
        TextView title=new TextView(s); title.setText(zh?"切换输入法模式":"Keyboard mode"); title.setTextSize(24); title.setTextColor(Prefs.dark(s)?0xFFF1F3F4:0xFF202124); title.setPadding(12,12,12,20); box.addView(title,new LinearLayout.LayoutParams(-1,72));
        add(zh?"26键":"26-key",0); add(zh?"9键":"9-key",1); add(zh?"英文":"English",2);
        Button cancel=new Button(s); cancel.setText(zh?"取消":"Cancel"); cancel.setAllCaps(false); cancel.setFocusable(true); cancel.setOnClickListener(v->service.restoreKeyboardView()); box.addView(cancel,new LinearLayout.LayoutParams(-1,68));
        postDelayed(()->box.getChildAt(1).requestFocus(),300);
    }
    private void add(String text,int mode){Button b=new Button(service);b.setText(text);b.setAllCaps(false);b.setTextSize(20);b.setFocusable(true);b.setOnClickListener(v->{service.selectKeyboardMode(mode);});box.addView(b,new LinearLayout.LayoutParams(-1,76));}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
        int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){View f=findFocus();if(f!=null&&f.isClickable()){f.performClick();return true;}return true;}
        if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){View f=findFocus();if(f==null)f=this;View n=f.focusSearch(k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN);if(n!=null){n.requestFocus();return true;}return true;}
        return super.dispatchKeyEvent(e);
    }
    @Override public View focusSearch(int direction){
        View f=findFocus();
        View n=null;
        if(f!=null){
            n=super.focusSearch(direction);
        }
        if(n!=null) return n;
        return f==null?this:f;
    }

}
