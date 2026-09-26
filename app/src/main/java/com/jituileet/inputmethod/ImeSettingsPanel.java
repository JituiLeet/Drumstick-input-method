package com.jituileet.inputmethod;

import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Settings rendered inside the IME window. No Activity/Dialog is used for the settings UI. */
public final class ImeSettingsPanel extends ScrollView {
    private final DrumstickImeService service;
    private final LinearLayout box;
    private final boolean zh;

    public ImeSettingsPanel(DrumstickImeService s) {
        super(s); service=s; zh=s.isChineseLanguagePublic();
        setFillViewport(true); setFocusable(true); setFocusableInTouchMode(true);
        box=new LinearLayout(s); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(18,8,18,18);
        box.setBackgroundColor(Prefs.dark(s)?0xFF202124:Color.WHITE); addView(box);
        build();
    }
    private TextView title(String t){ TextView v=new TextView(service); v.setText(t); v.setTextSize(21); v.setTextColor(Prefs.dark(service)?0xFFF1F3F4:0xFF202124); v.setPadding(18,18,18,12); return v; }
    private Button btn(String text, View.OnClickListener l){ Button b=new Button(service); b.setAllCaps(false); b.setText(text); b.setTextSize(16); b.setFocusable(true); b.setOnClickListener(l); box.addView(b,new LinearLayout.LayoutParams(-1,64)); return b; }
    private void build(){
        box.addView(title(zh?"鸡腿输入法设置":"Drumstick Input Method Settings"));
        btn(zh?"更改外观":"Appearance",v->appearance());
        btn(zh?"更改词库":"Dictionary",v->dictionary());
        btn(zh?"语言":"Language",v->language());
        btn(zh?"键盘":"Keyboard",v->keyboard());
        btn(zh?"使用手机输入":"Use phone input",v->phone());
        btn(zh?"打开系统输入法设置":"Open system IME settings",v->service.openSystemImeSettings());
        btn(zh?"返回输入法":"Back to keyboard",v->service.restoreKeyboardView());
        box.addView(title("\n"+(zh?"鸡腿输入法":"Drumstick Input Method")+"\ncom.jituileet.inputmethod\nAndroid 4.4–14 · Rime"));
        requestFocus();
        postDelayed(()->{ if(box.getChildCount()>1) box.getChildAt(1).requestFocus(); },80);
    }
    private void appearance(){
        box.removeAllViews(); box.addView(title(zh?"外观":"Appearance"));
        int[] vals={0xFFEBECF0,0xFFE6EDF5,0xFFE6F1EE,0xFFECE7F5,0xFF202124};
        String[] zn={"默认浅灰","蓝灰","绿色","紫色","黑色"}; String[] en={"Default light gray","Blue gray","Green","Purple","Black"};
        for(int i=0;i<vals.length;i++){final int x=i;btn(zh?zn[i]:en[i],v->{Prefs.color(service,vals[x]);service.refreshImeView();});}
        Switch dark=new Switch(service); dark.setText(zh?"深色模式":"Dark mode"); dark.setTextSize(16); dark.setFocusable(true); dark.setChecked(Prefs.dark(service)); dark.setOnCheckedChangeListener((v,on)->{Prefs.dark(service,on);service.refreshImeView();}); box.addView(dark,new LinearLayout.LayoutParams(-1,64));
        btn(zh?"恢复默认外观":"Restore default appearance",v->{Prefs.color(service,0xFFEBECF0);Prefs.dark(service,false);service.refreshImeView();});
        btn(zh?"返回设置":"Back to settings",v->service.showSettingsPanel());
        postDelayed(()->box.getChildAt(1).requestFocus(),60);
    }
    private void dictionary(){
        box.removeAllViews(); box.addView(title(zh?"词库":"Dictionary"));
        btn(zh?"输入法自带词库":"Built-in dictionary",v->{Prefs.dict(service,"内置词库");service.reloadRime();});
        File d=new File(service.getFilesDir(),"dicts"); File[] fs=d.listFiles();
        if(fs!=null) for(File f:fs) if(f.getName().endsWith(".dict.yaml")){ final String n=f.getName(); btn(n,v->{Prefs.dict(service,n);try{RimeData.selectDictionary(service,n);}catch(Throwable ignored){}service.reloadRime();}); }
        btn(zh?"上传词库文件（.dict.yaml）":"Upload dictionary (.dict.yaml)",v->service.openDictionaryPicker());
        btn(zh?"清除候选词使用记录":"Clear candidate learning",v->{service.clearUsage();Toast.makeText(service,zh?"已清除候选词使用记录":"Candidate learning data cleared",Toast.LENGTH_SHORT).show();});
        btn(zh?"返回设置":"Back to settings",v->service.showSettingsPanel());
        postDelayed(()->box.getChildAt(1).requestFocus(),60);
    }
    private void language(){
        box.removeAllViews(); box.addView(title(zh?"语言":"Language"));
        btn("中文（简体）",v->{Prefs.language(service,"zh");service.reloadRime();service.showSettingsPanel();});
        btn("English",v->{Prefs.language(service,"en");service.reloadRime();service.showSettingsPanel();});
        btn(zh?"跟随系统":"Follow system",v->{Prefs.language(service,"auto");service.reloadRime();service.showSettingsPanel();});
        btn(zh?"返回设置":"Back to settings",v->service.showSettingsPanel());
        postDelayed(()->box.getChildAt(1).requestFocus(),60);
    }
    private void keyboard(){
        box.removeAllViews(); box.addView(title(zh?"键盘":"Keyboard"));
        Switch sw=new Switch(service); sw.setText(zh?"键盘输入":"Keyboard input"); sw.setChecked(Prefs.keyboard(service)); sw.setFocusable(true); sw.setOnCheckedChangeListener((v,on)->Prefs.keyboard(service,on)); box.addView(sw,new LinearLayout.LayoutParams(-1,64));
        Switch notice=new Switch(service); notice.setText(zh?"实体键盘提示":"Physical keyboard notice"); notice.setChecked(Prefs.hardwareNotice(service)); notice.setFocusable(true); notice.setOnCheckedChangeListener((v,on)->Prefs.hardwareNotice(service,on)); box.addView(notice,new LinearLayout.LayoutParams(-1,64));
        btn(zh?"切换键盘模式":"Switch keyboard mode",v->service.showKeyboardModePanel());
        btn(zh?"返回设置":"Back to settings",v->service.showSettingsPanel());
        postDelayed(()->sw.requestFocus(),60);
    }
    private void phone(){
        box.removeAllViews(); box.addView(title(zh?"手机输入":"Phone input"));
        Switch sw=new Switch(service); sw.setText(zh?"使用手机输入":"Use phone input"); sw.setChecked(Prefs.phone(service)); sw.setFocusable(true); box.addView(sw,new LinearLayout.LayoutParams(-1,64));
        TextView info=title(zh?"电视和手机连接同一 Wi‑Fi 后，在手机浏览器打开下面地址：":"Connect phone and TV to the same Wi‑Fi, then open this address in the phone browser:"); info.setTextSize(14); box.addView(info);
        TextView url=title(""); url.setTextSize(19); url.setTextColor(Prefs.dark(service)?0xFF8FD3FF:0xFF1677B7); box.addView(url);
        Runnable refresh=()->{ if(Prefs.phone(service)){service.startPhoneInput(); url.setText(service.phoneUrl());} else url.setText(""); };
        refresh.run();
        sw.setOnCheckedChangeListener((v,on)->{service.setPhoneInput(on);refresh.run();});
        btn(zh?"返回设置":"Back to settings",v->service.showSettingsPanel());
        postDelayed(()->sw.requestFocus(),60);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN) return true;
        int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){
            View f=findFocus();
            if(f!=null && f.isClickable()){f.performClick();return true;}
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

}
