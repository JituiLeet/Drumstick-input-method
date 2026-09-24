package com.jituileet.inputmethod;

import android.view.InputDevice;
import android.view.KeyEvent;

/** Detects keys coming from a real hardware keyboard, not the TV remote. */
public final class PhysicalKeyboardDetector {
    private PhysicalKeyboardDetector() {}

    public static boolean isHardwareKeyboard(KeyEvent event) {
        if (event == null) return false;
        InputDevice device = event.getDevice();
        if (device == null) return false;
        int sources = device.getSources();
        if ((sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) return true;
        return device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    public static boolean isTypingKey(KeyEvent event) {
        if (!isHardwareKeyboard(event)) return false;
        int k = event.getKeyCode();
        return (k >= KeyEvent.KEYCODE_A && k <= KeyEvent.KEYCODE_Z)
                || (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9)
                || k == KeyEvent.KEYCODE_SPACE
                || k == KeyEvent.KEYCODE_DEL || k == KeyEvent.KEYCODE_TAB
                || k == KeyEvent.KEYCODE_COMMA || k == KeyEvent.KEYCODE_PERIOD
                || k == KeyEvent.KEYCODE_MINUS || k == KeyEvent.KEYCODE_EQUALS
                || k == KeyEvent.KEYCODE_SEMICOLON || k == KeyEvent.KEYCODE_APOSTROPHE
                || k == KeyEvent.KEYCODE_SLASH || k == KeyEvent.KEYCODE_BACKSLASH
                || k == KeyEvent.KEYCODE_LEFT_BRACKET || k == KeyEvent.KEYCODE_RIGHT_BRACKET
                || k == KeyEvent.KEYCODE_GRAVE || k == KeyEvent.KEYCODE_STAR
                || k == KeyEvent.KEYCODE_NUMPAD_ADD || k == KeyEvent.KEYCODE_NUMPAD_SUBTRACT
                || k == KeyEvent.KEYCODE_NUMPAD_MULTIPLY || k == KeyEvent.KEYCODE_NUMPAD_DIVIDE
                || k == KeyEvent.KEYCODE_NUMPAD_DOT || k == KeyEvent.KEYCODE_NUMPAD_0
                || k == KeyEvent.KEYCODE_NUMPAD_1 || k == KeyEvent.KEYCODE_NUMPAD_2
                || k == KeyEvent.KEYCODE_NUMPAD_3 || k == KeyEvent.KEYCODE_NUMPAD_4
                || k == KeyEvent.KEYCODE_NUMPAD_5 || k == KeyEvent.KEYCODE_NUMPAD_6
                || k == KeyEvent.KEYCODE_NUMPAD_7 || k == KeyEvent.KEYCODE_NUMPAD_8
                || k == KeyEvent.KEYCODE_NUMPAD_9;
    }
}
