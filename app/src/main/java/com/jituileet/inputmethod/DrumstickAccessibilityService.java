package com.jituileet.inputmethod;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explicitly user-enabled accessibility service used only by the Copy feature.
 * It does not intercept keys, gestures, or navigation. Text is read only when
 * DrumstickImeService asks for a copy snapshot.
 */
public final class DrumstickAccessibilityService extends AccessibilityService {
    private static volatile DrumstickAccessibilityService instance;

    @Override public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
    }

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String wanted = context.getPackageName() + "/" + DrumstickAccessibilityService.class.getName();
        for (String item : enabled.split(":")) {
            if (wanted.equalsIgnoreCase(item)) return true;
        }
        return false;
    }

    public static String captureVisibleText(Context context) {
        DrumstickAccessibilityService s = instance;
        if (s == null) return "";
        AccessibilityNodeInfo root = null;
        try {
            // On Android 9+ getRootInActiveWindow normally gives the focused app.
            root = s.getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                if (pkg != null && context.getPackageName().contentEquals(pkg)) root = null;
            }
            if (root == null && Build.VERSION.SDK_INT >= 21) {
                List<AccessibilityWindowInfo> windows = s.getWindows();
                for (AccessibilityWindowInfo w : windows) {
                    if (w == null) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r == null) continue;
                    CharSequence pkg = r.getPackageName();
                    if (pkg != null && context.getPackageName().contentEquals(pkg)) continue;
                    root = r;
                    break;
                }
            }
            if (root == null) return "";
            StringBuilder out = new StringBuilder();
            Set<String> seen = new HashSet<>();
            collect(root, out, seen);
            return normalize(out.toString());
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (root != null) {
                try { root.recycle(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void collect(AccessibilityNodeInfo node, StringBuilder out, Set<String> seen) {
        if (node == null) return;
        try {
            CharSequence pkg = node.getPackageName();
            if (pkg != null && pkg.toString().equals("com.jituileet.inputmethod")) return;

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String value = text != null ? text.toString().trim()
                    : (desc != null ? desc.toString().trim() : "");
            if (!value.isEmpty() && seen.add(value)) {
                if (out.length() > 0) out.append('\n');
                out.append(value);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) collect(child, out, seen);
            }
        } catch (Throwable ignored) {}
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String[] lines = s.replace('\u00a0',' ').split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String x = line.trim();
            if (x.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(x);
        }
        return out.toString().trim();
    }
}
