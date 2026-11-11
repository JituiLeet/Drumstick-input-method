package com.jituileet.inputmethod;

import android.content.Context;
import java.util.*;

/** Real Rime-backed Chinese engine with a small fallback only when the native runtime is unavailable. */
public final class ChineseEngine {
    public interface Listener { void onCandidates(List<String> candidates, String preedit); }
    private final Context context;
    private final Listener listener;
    private final ArrayList<String> candidates = new ArrayList<>();
    private final ArrayList<Integer> candidateRimeIndices = new ArrayList<>();
    private final UsageTracker usage;
    private volatile long session;
    private volatile boolean english;
    private volatile boolean rime;
    private String composing = "";

    public ChineseEngine(Context c, Listener l) {
        context = c.getApplicationContext(); listener = l; usage = new UsageTracker(context);
        init();
    }

    private void init() {
        initializing = true;
        Thread worker = new Thread(() -> {
            boolean ready = false;
            long newSession = 0;
            try {
                RimeData.ensure(context);
                ready = RimeNative.available()
                        && RimeNative.initialize(RimeData.sharedDir(context).getAbsolutePath(),
                                                 RimeData.userDir(context).getAbsolutePath());
                if (ready) {
                    newSession = RimeNative.createSession();
                    if (newSession != 0) {
                        if (!RimeNative.selectSchema(newSession, "luna_pinyin_simp")) {
                            android.util.Log.e("DrumstickRime", "Could not select luna_pinyin_simp");
                            RimeNative.destroySession(newSession);
                            newSession = 0;
                            ready = false;
                        }
                    } else {
                        ready = false;
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("DrumstickRime", "Asynchronous Rime initialization failed", t);
                ready = false;
            }
            synchronized (ChineseEngine.this) {
                session = newSession;
                rime = ready && newSession != 0;
                initializing = false;
                if (rime) {
                    try {
                        RimeNative.setAsciiMode(session, english);
                        if (!composing.isEmpty()) RimeNative.setInput(session, composing);
                        refresh();
                    } catch (Throwable t) {
                        android.util.Log.e("DrumstickRime", "Rime session activation failed", t);
                        try { RimeNative.destroySession(session); } catch (Throwable ignored) {}
                        session = 0;
                        rime = false;
                        candidates.clear(); candidateRimeIndices.clear(); publish();
                    }
                } else {
                    candidates.clear(); candidateRimeIndices.clear(); composing = ""; publish();
                }
            }
        }, "drumstick-rime-init");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean probeChineseCandidates() {
        if (session == 0) return false;
        try {
            RimeNative.setInput(session, "ni");
            String[] ctx = RimeNative.context(session);
            boolean ok=false;
            for(int i=1;i<ctx.length;i++){String x=ctx[i]; if(x!=null&&!x.isEmpty()){ boolean nonAscii=false; for(int j=0;j<x.length();j++) if(x.charAt(j)>127){nonAscii=true;break;} if(nonAscii){ok=true;break;}}}
            RimeNative.setInput(session, "");
            return ok;
        } catch(Throwable t){ try{RimeNative.setInput(session, "");}catch(Throwable ignored){} return false; }
    }
    public synchronized void setEnglish(boolean e) {
        english = e; composing = ""; candidates.clear(); candidateRimeIndices.clear();
        if (rime && session != 0) RimeNative.setAsciiMode(session, e);
        publish();
    }
    public boolean isEnglish() { return english; }
    public boolean isRimeActive() { return rime && session != 0; }
    public String getComposing() { return composing; }
    public synchronized void resetComposition() {
        if (rime && session != 0) { try { RimeNative.setInput(session, ""); } catch (Throwable ignored) {} }
        composing = ""; candidates.clear(); candidateRimeIndices.clear(); publish();
    }
    public synchronized boolean hasComposing() { return composing != null && !composing.isEmpty(); }
    public synchronized int candidateCount() { return candidates.size(); }

    public synchronized String input(String s) {
        if (english || s == null || s.isEmpty()) return null;
        if (rime && session != 0) {
            // Android TV sends the soft-keyboard letters through our View rather than
            // as hardware key events.  For this path, set_input is deliberately used
            // instead of simulate_key_sequence: it updates Rime's composition directly
            // and avoids depending on a platform-specific key-code translation table.
            // This also fixes the case where the TV showed the typed Latin letter but
            // never produced a Chinese preedit/candidate list.
            RimeNative.setAsciiMode(session, false);
            String[] ctx = RimeNative.context(session);
            String raw = (ctx.length > 0 && ctx[0] != null) ? ctx[0] : composing;
            String lower = s.toLowerCase(Locale.US);
            if (!RimeNative.setInput(session, raw + lower)) {
                // Candidate data must come from Rime only. If Rime rejects the
                // composition update, clear the stale menu instead of inventing results.
                composing = raw;
                candidates.clear();
                candidateRimeIndices.clear();
                publish();
                return null;
            }
            refresh();
            return null;
        }
        // Rime is not ready: do not synthesize candidates from an approximate
        // prefix dictionary. Keep the IME responsive and show no false suggestions.
        publish();
        return null;
    }

    public synchronized void splitPinyin() {
        if (english || composing == null || composing.isEmpty()) return;
        if (rime && session != 0) {
            try {
                String[] ctx = RimeNative.context(session);
                String raw = ctx.length > 0 && ctx[0] != null ? ctx[0] : composing;
                if (!raw.isEmpty() && raw.charAt(raw.length()-1) != '\'') {
                    RimeNative.setInput(session, raw + "'");
                    refresh();
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (composing.charAt(composing.length()-1) != '\'') {
            composing += "'";
            publish();
        }
    }

    public synchronized String backspace() {
        if (rime && session != 0) {
            String[] ctx = RimeNative.context(session);
            String raw = ctx.length > 0 && ctx[0] != null ? ctx[0] : "";
            if (!raw.isEmpty()) {
                raw = raw.substring(0, raw.length()-1);
                RimeNative.setInput(session, raw);
            }
            refresh();
            return null;
        }
        if (!composing.isEmpty()) composing = composing.substring(0, composing.length()-1);
        publishFallback(); return null;
    }

    public synchronized String choose(int index) {
        if (rime && session != 0) {
            int rimeIndex = (index >= 0 && index < candidateRimeIndices.size()) ? candidateRimeIndices.get(index) : index;
            String commit = RimeNative.selectCandidate(session, rimeIndex);
            if (commit != null && !commit.isEmpty()) usage.record(commit);
            refresh();
            return commit;
        }
        if (index < 0 || index >= candidates.size()) return null;
        String out = candidates.get(index); usage.record(out); composing = ""; publishFallback(); return out;
    }

    public synchronized String commitFirst() {
        if (rime && session != 0) {
            String commit = RimeNative.selectCandidate(session, 0);
            refresh(); return commit;
        }
        return null;
    }

    public synchronized void refresh() {
        if (!rime || session == 0) { candidates.clear(); candidateRimeIndices.clear(); publish(); return; }
        String[] ctx = RimeNative.context(session);
        composing = ctx.length > 0 && ctx[0] != null ? ctx[0] : "";
        candidates.clear();
        candidateRimeIndices.clear();
        ArrayList<String> raw = new ArrayList<>();
        ArrayList<Integer> rawIndices = new ArrayList<>();
        for (int i=1;i<ctx.length;i++) if (ctx[i] != null && !ctx[i].isEmpty()) { raw.add(ctx[i]); rawIndices.add(i-1); }
        for (int pass=0; pass<raw.size(); pass++) for (int j=0; j+1<raw.size(); j++) {
            if (usage.count(raw.get(j+1)) > usage.count(raw.get(j))) {
                String t=raw.get(j); raw.set(j,raw.get(j+1)); raw.set(j+1,t);
                Integer ti=rawIndices.get(j); rawIndices.set(j,rawIndices.get(j+1)); rawIndices.set(j+1,ti);
            }
        }
        candidates.addAll(raw); candidateRimeIndices.addAll(rawIndices);
        listener.onCandidates(new ArrayList<>(candidates), composing);
    }

    public synchronized boolean changePage(boolean backward){
        if(rime && session != 0){
            boolean ok=RimeNative.changePage(session, backward);
            if(ok) refresh();
            return ok;
        }
        return false;
    }

    public synchronized void clearUsage(){ usage.clear(); refresh(); }

    public synchronized void destroy() {
        if (rime && session != 0) RimeNative.destroySession(session);
        session = 0;
        if (rime) RimeNative.finalizeEngine();
        rime = false;
    }

    private void publish() { listener.onCandidates(new ArrayList<>(candidates), composing); }
}
