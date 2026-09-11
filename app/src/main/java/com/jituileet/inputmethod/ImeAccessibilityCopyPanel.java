package com.jituileet.inputmethod;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** First-use consent screen for the optional accessibility-based copy feature. */
public final class ImeAccessibilityCopyPanel extends LinearLayout {
    private final DrumstickImeService service;
    private final boolean zh;

    public ImeAccessibilityCopyPanel(DrumstickImeService s) {
        super(s);
        service = s;
        zh = s.isChineseLanguagePublic();
        setOrientation(VERTICAL);
        setPadding(24, 18, 24, 18);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Prefs.dark(s) ? 0xFF202124 : Color.WHITE);

        TextView title = new TextView(s);
        title.setText(zh ? "复制需要无障碍权限" : "Accessibility access is required");
        title.setTextSize(22);
        title.setTextColor(Prefs.dark(s) ? 0xFFF1F3F4 : 0xFF202124);
        title.setPadding(8, 8, 8, 16);
        addView(title, new LayoutParams(-1, 70));

        TextView body = new TextView(s);
        body.setText(zh
                ? "鸡腿输入法需要使用无障碍服务读取当前其他应用界面中可访问的文字，供你选择后复制。只有你主动点击“复制”时才会读取文字，不用于输入、按键监听或控制其他应用。"
                : "Drumstick Input Method uses an accessibility service to read accessible text from other app screens when you explicitly use Copy. It is not used for typing, key interception, or controlling other apps.");
        body.setTextSize(16);
        body.setTextColor(Prefs.dark(s) ? 0xFFE3E6EA : 0xFF303238);
        body.setPadding(8, 8, 8, 20);
        addView(body, new LayoutParams(-1, 0, 1));

        LinearLayout row = new LinearLayout(s);
        row.setOrientation(HORIZONTAL);

        Button open = new Button(s);
        open.setText(zh ? "去开启" : "Open settings");
        open.setAllCaps(false);
        open.setFocusable(true);
        open.setOnClickListener(v -> openAccessibilitySettings());
        row.addView(open, new LayoutParams(0, 64, 1));

        Button cancel = new Button(s);
        cancel.setText(zh ? "取消" : "Cancel");
        cancel.setAllCaps(false);
        cancel.setFocusable(true);
        cancel.setOnClickListener(v -> service.restoreKeyboardView());
        row.addView(cancel, new LayoutParams(0, 64, 1));

        addView(row);
        postDelayed(() -> open.requestFocus(), 80);
    }

    private void openAccessibilitySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(i);
            Toast.makeText(service, zh ? "请在无障碍设置中开启鸡腿输入法屏幕文字复制服务" : "Enable Drumstick screen-text copy service", Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(service, zh ? "无法打开无障碍设置" : "Unable to open Accessibility settings", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (e.getAction() != android.view.KeyEvent.ACTION_DOWN) return true;
        int k = e.getKeyCode();
        if (k == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k == android.view.KeyEvent.KEYCODE_ENTER) {
            View f = findFocus();
            if (f != null && f.isClickable()) { f.performClick(); return true; }
            return true;
        }
        int dir = k == android.view.KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP :
                k == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN :
                k == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT :
                k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
        if (dir != 0) {
            View f = findFocus();
            if (f == null) f = this;
            View n = f.focusSearch(dir);
            if (n != null) { n.requestFocus(); return true; }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
