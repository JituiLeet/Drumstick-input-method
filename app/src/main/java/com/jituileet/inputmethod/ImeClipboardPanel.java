package com.jituileet.inputmethod;
import android.content.*;import android.view.*;import android.widget.*;import java.util.*;
/** Persistent clipboard history with TV/keyboard/touch controls. */
public final class ImeClipboardPanel extends ScrollView {
    private final DrumstickImeService service; private final boolean zh; private final LinearLayout box;
    public ImeClipboardPanel(DrumstickImeService s, ArrayList<String> items){super(s);service=s;zh=s.isChineseLanguagePublic();setFillViewport(true);setFocusable(true);setFocusableInTouchMode(true);setBackgroundColor(Prefs.dark(s)?0xFF202124:0xFFFFFFFF);box=new LinearLayout(s);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(18,8,18,18);addView(box);build(items);}
    private TextView title(String t){TextView v=new TextView(service);v.setText(t);v.setTextSize(21);v.setTextColor(Prefs.dark(service)?0xFFF1F3F4:0xFF202124);v.setPadding(18,18,18,12);return v;}
    private Button action(String t,View.OnClickListener l){Button b=new Button(service);b.setText(t);b.setAllCaps(false);b.setFocusable(true);b.setOnClickListener(l);return b;}
    private void build(ArrayList<String> items){box.addView(title(zh?"最近复制的内容":"Recent clipboard"));
        if(items.isEmpty()) box.addView(title(zh?"没有剪贴板内容":"No clipboard content"));
        for(int i=0;i<items.size();i++){final int index=i;final String text=items.get(i);LinearLayout row=new LinearLayout(service);row.setOrientation(LinearLayout.HORIZONTAL);Button use=action(shortText(text),v->{service.commitClipboardText(text);service.restoreKeyboardView();});row.addView(use,new LinearLayout.LayoutParams(0,64,1));Button edit=action(zh?"编辑":"Edit",v->ImeClipboardEditActivity.open(service,index,text));row.addView(edit,new LinearLayout.LayoutParams(110,64));Button del=action(zh?"删除":"Delete",v->{
                ClipboardHistory.delete(service,index);
                Toast.makeText(service,zh?"已删除":"Deleted",Toast.LENGTH_SHORT).show();
                service.showClipboardHistoryPublic();
            });row.addView(del,new LinearLayout.LayoutParams(110,64));box.addView(row);}
        Button clear=action(zh?"清空剪贴板历史":"Clear clipboard history",v->{ClipboardHistory.clear(service);service.showClipboardHistoryPublic();});box.addView(clear,new LinearLayout.LayoutParams(-1,64));
        Button back=action(zh?"返回输入法":"Back to keyboard",v->service.restoreKeyboardView());box.addView(back,new LinearLayout.LayoutParams(-1,64));
        requestFocus();postDelayed(()->{if(box.getChildCount()>1)box.getChildAt(1).requestFocus();},70);
    }
    private String shortText(String text){String label=text.replace("\n"," ");return label.length()>70?label.substring(0,70)+"…":label;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){View f=findFocus();if(f!=null&&f.isClickable()){f.performClick();return true;}return true;}if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){View f=findFocus();if(f==null)f=this;int d=k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:k==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:k==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:View.FOCUS_RIGHT;View n=f.focusSearch(d);if(n!=null){n.requestFocus();return true;}return true;}return super.dispatchKeyEvent(e);}
}
