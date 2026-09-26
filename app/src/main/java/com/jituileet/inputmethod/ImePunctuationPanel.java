package com.jituileet.inputmethod;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * TV-safe punctuation chooser.
 *
 * This panel must be usable entirely with a D-pad.  The previous implementation
 * left the ScrollView itself focusable and relied on Android's inferred focus
 * search.  On some TV/IME windows that causes the arrow event to stay on the
 * container instead of moving between the punctuation buttons.  We therefore
 * make only the actual choices focusable and explicitly route D-pad movement.
 */
public final class ImePunctuationPanel extends ScrollView {
    private final DrumstickImeService service;
    private final LinearLayout box;
    private final Button[] choices = new Button[4];
    private int focusIndex = 0;

    public ImePunctuationPanel(DrumstickImeService s) {
        super(s);
        service = s;

        setFillViewport(true);
        // The panel/container must never steal D-pad focus from its buttons.
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setBackgroundColor(Prefs.dark(s) ? 0xFF202124 : 0xFFF6F7F9);

        box = new LinearLayout(s);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(80, 32, 80, 32);
        box.setFocusable(false);
        addView(box, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(s);
        title.setText(s.isChineseLanguagePublic() ? "选择标点" : "Choose punctuation");
        title.setTextSize(24);
        title.setTextColor(Prefs.dark(s) ? 0xFFF1F3F4 : 0xFF202124);
        title.setPadding(12, 12, 12, 20);
        title.setFocusable(false);
        box.addView(title, new LinearLayout.LayoutParams(-1, 72));

        addChoice(0, "？");
        addChoice(1, "！");
        addChoice(2, "。");
        addChoice(3, s.isChineseLanguagePublic() ? "返回输入法" : "Back to keyboard");

        // Explicitly connect the vertical focus chain and make it circular.
        for (int i = 0; i < choices.length; i++) {
            Button b = choices[i];
            b.setNextFocusUpId(choices[(i + choices.length - 1) % choices.length].getId());
            b.setNextFocusDownId(choices[(i + 1) % choices.length].getId());
            // The punctuation chooser is one vertical column, so left/right
            // intentionally stay on the current choice instead of escaping
            // into the underlying keyboard window.
            b.setNextFocusLeftId(b.getId());
            b.setNextFocusRightId(b.getId());
        }

        postDelayed(() -> requestChoiceFocus(0), 80);
    }

    private void addChoice(final int index, String text) {
        Button b = new Button(service);
        b.setId(View.generateViewId());
        b.setText(text);
        b.setTextSize(index < 3 ? 22 : 18);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setOnClickListener(v -> {
            if (index == 3) {
                service.restoreKeyboardView();
                return;
            }

            android.view.inputmethod.InputConnection ic = service.getCurrentInputConnection();
            if (ic != null) {
                if (!service.isChineseInputMode() || !service.engineHasComposingPublic()) {
                    service.commitTextPublic(text);
                } else {
                    String out = service.commitFirstPublic();
                    if (out != null && !out.isEmpty()) service.commitTextPublic(out);
                    service.commitTextPublic(text);
                }
            }
            service.restoreKeyboardView();
        });
        choices[index] = b;
        box.addView(b, new LinearLayout.LayoutParams(-1, 76));
    }

    private void requestChoiceFocus(int index) {
        if (index < 0 || index >= choices.length) return;
        focusIndex = index;
        Button b = choices[index];
        b.requestFocus();
        b.post(() -> b.requestFocus());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int action = e.getAction();
        if (action != KeyEvent.ACTION_DOWN) {
            // Do not consume key-up events; this is important for TV remotes
            // and keeps the IME window's key lifecycle normal.
            return super.dispatchKeyEvent(e);
        }

        int k = e.getKeyCode();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                requestChoiceFocus((focusIndex + choices.length - 1) % choices.length);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                requestChoiceFocus((focusIndex + 1) % choices.length);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // There is no second column in this panel. Keep focus here
                // rather than allowing the system to jump to another window.
                requestChoiceFocus(focusIndex);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (focusIndex >= 0 && focusIndex < choices.length) {
                    choices[focusIndex].performClick();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                service.restoreKeyboardView();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }
}
