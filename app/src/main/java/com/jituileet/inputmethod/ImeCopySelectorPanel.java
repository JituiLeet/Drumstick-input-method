package com.jituileet.inputmethod;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;

/** TV copy selector: D-pad left/right moves a cursor, OK starts/finishes selection. */
public final class ImeCopySelectorPanel extends LinearLayout {
    private final DrumstickImeService service;
    private final String text;
    private final boolean zh;
    private int cursor=0, anchor=0;
    private boolean selecting=false;
    private final TextView preview;
    private final TextView status;

    public ImeCopySelectorPanel(DrumstickImeService s,String t){
        super(s); service=s; text=t==null?"":t; zh=s.isChineseLanguagePublic();
        setOrientation(VERTICAL); setPadding(18,8,18,18); setFocusable(true); setFocusableInTouchMode(true);
        setBackgroundColor(Prefs.dark(s)?0xFF202124:Color.WHITE);
        TextView title=new TextView(s); title.setText(zh?"选择要复制的文字":"Select text to copy"); title.setTextSize(21); title.setTextColor(Prefs.dark(s)?0xFFF1F3F4:0xFF202124); title.setPadding(18,18,18,8); addView(title,new LayoutParams(-1,60));
        status=new TextView(s); status.setTextSize(14); status.setTextColor(Prefs.dark(s)?0xFFB9C8FF:0xFF405070); status.setPadding(18,4,18,8); addView(status,new LayoutParams(-1,42));
        preview=new TextView(s); preview.setTextSize(20); preview.setTextColor(Prefs.dark(s)?0xFFF1F3F4:0xFF202124); preview.setPadding(18,10,18,10); addView(preview,new LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(s); row.setOrientation(HORIZONTAL);
        Button all=button(zh?"全选":"All"); Button copy=button(zh?"复制":"Copy"); Button cancel=button(zh?"取消":"Cancel");
        all.setOnClickListener(v->{anchor=0;cursor=text.length();selecting=true;update();});
        copy.setOnClickListener(v->copy());
        cancel.setOnClickListener(v->service.restoreKeyboardView());
        row.addView(all,new LayoutParams(0,64,1)); row.addView(copy,new LayoutParams(0,64,1)); row.addView(cancel,new LayoutParams(0,64,1)); addView(row);
        postDelayed(()->{requestFocus();update();},70);
    }

    private Button button(String t){Button b=new Button(service);b.setText(t);b.setAllCaps(false);b.setFocusable(true);return b;}
    private void update(){
        int a=Math.min(anchor,cursor),b=Math.max(anchor,cursor);
        preview.setText(text.substring(0,a)+"["+text.substring(a,b)+"]"+text.substring(b));
        if(selecting)status.setText(zh?"← / → 移动到结束位置，确认后复制":"← / → move to the end, OK to copy");
        else status.setText(zh?"← / → 移动光标，确认后开始选择":"← / → move cursor, OK to start selection");
    }
    private void copy(){int a=Math.min(anchor,cursor),b=Math.max(anchor,cursor);String out=text.substring(a,b);if(out.isEmpty())out=text;try{android.content.ClipboardManager cm=(android.content.ClipboardManager)service.getSystemService(Context.CLIPBOARD_SERVICE);if(cm==null)throw new IllegalStateException();cm.setPrimaryClip(ClipData.newPlainText("Drumstick",out));ClipboardHistory.add(service,out);Toast.makeText(service,zh?"已复制":"Copied",Toast.LENGTH_SHORT).show();service.restoreKeyboardView();}catch(Throwable e){Toast.makeText(service,zh?"复制失败":"Copy failed",Toast.LENGTH_SHORT).show();}}

    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return true; int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_DPAD_LEFT){cursor=Math.max(0,cursor-1);update();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_RIGHT){cursor=Math.min(text.length(),cursor+1);update();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){if(!selecting){anchor=cursor;selecting=true;update();}else copy();return true;}
        if(k==KeyEvent.KEYCODE_DPAD_DOWN){View f=findFocus();if(f!=null){View n=f.focusSearch(View.FOCUS_DOWN);if(n!=null){n.requestFocus();return true;}}}
        if(k==KeyEvent.KEYCODE_DPAD_UP){View f=findFocus();if(f!=null){View n=f.focusSearch(View.FOCUS_UP);if(n!=null){n.requestFocus();return true;}}}
        return true;
    }
}
