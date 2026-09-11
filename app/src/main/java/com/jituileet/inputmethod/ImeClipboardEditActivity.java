package com.jituileet.inputmethod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Independent TV-friendly editor for clipboard history. */
public final class ImeClipboardEditActivity extends Activity {
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_TEXT = "text";
    private int index;
    private EditText editor;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        index = getIntent().getIntExtra(EXTRA_INDEX, -1);
        String text = getIntent().getStringExtra(EXTRA_TEXT);
        boolean zh = Prefs.language(this).equals("zh") || (Prefs.language(this).equals("auto") && java.util.Locale.getDefault().getLanguage().equals("zh"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 24, 28, 24);
        root.setBackgroundColor(Prefs.dark(this) ? 0xFF202124 : Color.WHITE);

        TextView title = new TextView(this);
        title.setText(zh ? "编辑剪贴板内容" : "Edit clipboard content");
        title.setTextSize(24);
        title.setTextColor(Prefs.dark(this) ? 0xFFF1F3F4 : 0xFF202124);
        root.addView(title, new LinearLayout.LayoutParams(-1, 64));

        editor = new EditText(this);
        editor.setText(text == null ? "" : text);
        editor.setTextSize(21);
        editor.setSingleLine(false);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setFocusable(true);
        editor.setFocusableInTouchMode(true);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button(zh ? "保存" : "Save");
        Button cancel = button(zh ? "取消" : "Cancel");
        save.setOnClickListener(v -> saveAndFinish());
        cancel.setOnClickListener(v -> finish());
        bar.addView(save, new LinearLayout.LayoutParams(0, 68, 1));
        bar.addView(cancel, new LinearLayout.LayoutParams(0, 68, 1));
        root.addView(bar);
        setContentView(root);

        editor.requestFocus();
        editor.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(17); b.setFocusable(true);
        return b;
    }

    private void saveAndFinish() {
        String value = editor == null ? "" : editor.getText().toString();
        if (index >= 0) ClipboardHistory.update(this, index, value);
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Drumstick", value));
        } catch (Throwable ignored) {}
        finish();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish(); return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public static void open(Context context, int index, String text) {
        Intent i = new Intent(context, ImeClipboardEditActivity.class)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}
