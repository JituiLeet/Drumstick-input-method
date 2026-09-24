package com.jituileet.inputmethod;

/** Direct JNI bridge to librime statically linked into liblibrime.so. */
public final class RimeNative {
    private static final boolean LOADED;
    static {
        boolean ok = false;
        try {
            System.loadLibrary("librime");
            ok = true;
        } catch (Throwable ignored) {}
        LOADED = ok;
    }
    private RimeNative() {}

    public static boolean available() { return LOADED && nativeAvailable(); }
    public static String version() { return LOADED ? nativeVersion() : null; }
    public static boolean initialize(String sharedDir, String userDir) { return LOADED && nativeInitialize(sharedDir, userDir); }
    public static void finalizeEngine() { if (LOADED) nativeFinalize(); }
    public static long createSession() { return LOADED ? nativeCreateSession() : 0L; }
    public static void destroySession(long id) { if (LOADED) nativeDestroySession(id); }
    public static String input(long id, String sequence) { return LOADED ? nativeInput(id, sequence) : null; }
    public static String processKey(long id, int keycode, int mask) { return LOADED ? nativeProcessKey(id, keycode, mask) : null; }
    public static String simulateKeySequence(long id, String sequence) { return LOADED ? nativeSimulateKeySequence(id, sequence) : null; }
    public static String[] context(long id) { return LOADED ? nativeGetContext(id) : new String[]{""}; }
    public static String selectCandidate(long id, int index) { return LOADED ? nativeSelectCandidate(id, index) : null; }
    public static void setAsciiMode(long id, boolean enabled) { if (LOADED) nativeSetAsciiMode(id, enabled); }
    public static boolean getAsciiMode(long id) { return LOADED && nativeGetAsciiMode(id); }
    public static boolean setInput(long id, String input) { return LOADED && nativeSetInput(id, input); }
    public static boolean deploy() { return LOADED && nativeDeploy(); }
    public static boolean changePage(long id, boolean backward) { return LOADED && nativeChangePage(id, backward); }
    public static boolean selectSchema(long id, String schemaId) { return LOADED && nativeSelectSchema(id, schemaId); }

    private static native boolean nativeAvailable();
    private static native String nativeVersion();
    private static native boolean nativeInitialize(String sharedDir, String userDir);
    private static native void nativeFinalize();
    private static native long nativeCreateSession();
    private static native void nativeDestroySession(long id);
    private static native String nativeInput(long id, String sequence);
    private static native String nativeProcessKey(long id, int keycode, int mask);
    private static native String nativeSimulateKeySequence(long id, String sequence);
    private static native String[] nativeGetContext(long id);
    private static native String nativeSelectCandidate(long id, int index);
    private static native void nativeSetAsciiMode(long id, boolean enabled);
    private static native boolean nativeGetAsciiMode(long id);
    private static native boolean nativeSetInput(long id, String input);
    private static native boolean nativeDeploy();
    private static native boolean nativeChangePage(long id, boolean backward);
    private static native boolean nativeSelectSchema(long id, String schemaId);
}
