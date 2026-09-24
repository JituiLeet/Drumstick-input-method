package com.jituileet.inputmethod;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real Rime-backed Chinese engine with a small fallback only when the native runtime is unavailable. */
public final class ChineseEngine {
    public interface Listener { void onCandidates(List<String> candidates, String preedit); }
    private final Context context;
    private final Listener listener;
    private final ArrayList<String> candidates = new ArrayList<>();
    private final ArrayList<Integer> candidateRimeIndices = new ArrayList<>();
    private final UsageTracker usage;
    private long session;
    private boolean english;
    private boolean rime;
    private String composing = "";
    private final HashMap<String, ArrayList<String>> localDictionary = new HashMap<>();
    private boolean localDictionaryLoaded;

    public ChineseEngine(Context c, Listener l) {
        context = c.getApplicationContext(); listener = l; usage = new UsageTracker(context);
        loadLocalDictionary();
        init();
    }

    private void loadLocalDictionary() {
        if(localDictionaryLoaded) return;
        localDictionaryLoaded=true;
        try(InputStream in=context.getAssets().open("rime/luna_pinyin.dict.yaml");
            BufferedReader r=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 32768)){
            String line; boolean data=false;
            while((line=r.readLine())!=null){
                String t=line.trim();
                if(t.equals("---")){data=true;continue;}
                if(t.equals("..."))break;
                if(!data||t.isEmpty()||t.startsWith("#"))continue;
                String[] a=line.split("\\t",2); if(a.length<2)continue;
                String word=a[0].trim(); String py=a[1].trim().toLowerCase(Locale.US);
                if(word.isEmpty()||py.isEmpty()||py.startsWith("#"))continue;
                ArrayList<String> list=localDictionary.get(py);
                if(list==null){list=new ArrayList<>();localDictionary.put(py,list);}
                if(list.size()<64&&!list.contains(word))list.add(word);
            }
        }catch(Throwable ignored){}
    }

    private void localCandidates(String input){
        candidates.clear(); candidateRimeIndices.clear();
        if(input==null||input.trim().isEmpty())return;
        String q=input.trim().toLowerCase(Locale.US);
        for(Map.Entry<String,ArrayList<String>> e:localDictionary.entrySet()){
            if(!e.getKey().startsWith(q))continue;
            for(String word:e.getValue()){
                if(!candidates.contains(word)){
                    candidates.add(word); candidateRimeIndices.add(-1);
                    if(candidates.size()>=48)break;
                }
            }
            if(candidates.size()>=48)break;
        }
        for(int i=0;i<candidates.size();i++)for(int j=i+1;j<candidates.size();j++){
            if(usage.score(candidates.get(j))>usage.score(candidates.get(i))){
                String t=candidates.get(i);candidates.set(i,candidates.get(j));candidates.set(j,t);
                Integer ti=candidateRimeIndices.get(i);candidateRimeIndices.set(i,candidateRimeIndices.get(j));candidateRimeIndices.set(j,ti);
            }
        }
    }

    private void init() {
        try {
            RimeData.ensure(context);
            rime = RimeNative.available() && RimeNative.initialize(RimeData.sharedDir(context).getAbsolutePath(), RimeData.userDir(context).getAbsolutePath());
            if (rime) {
                // Always bind this session to the bundled simplified Luna Pinyin schema.
                // Without an explicit schema selection a fresh Rime session may use the
                // distribution default and never expose the expected Chinese candidates.
                RimeNative.deploy();
                session = RimeNative.createSession();
                if (session != 0) {
                    RimeNative.selectSchema(session, "luna_pinyin_simp");
                    if (!probeChineseCandidates()) {
                        RimeNative.destroySession(session);
                        RimeNative.deploy();
                        session = RimeNative.createSession();
                        if (session != 0) RimeNative.selectSchema(session, "luna_pinyin_simp");
                    }
                }
            }
        } catch (Throwable ignored) { rime = false; }
        publish();
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
    public void setEnglish(boolean e) {
        english = e; composing = ""; candidates.clear(); candidateRimeIndices.clear();
        if (rime && session != 0) RimeNative.setAsciiMode(session, e);
        publish();
    }
    public boolean isEnglish() { return english; }
    public boolean isRimeActive() { return rime && session != 0; }
    public String getComposing() { return composing; }
    public void resetComposition() {
        if (rime && session != 0) { try { RimeNative.setInput(session, ""); } catch (Throwable ignored) {} }
        composing = ""; candidates.clear(); candidateRimeIndices.clear(); publish();
    }
    public boolean hasComposing() { return composing != null && !composing.isEmpty(); }

    public String input(String s) {
        if (english) { return null; }
        if (rime && session != 0) {
            String lower = s.toLowerCase(Locale.US);
            // Production path: process_key is the real Android/Rime key boundary.
            // Do not call simulate_key_sequence here; doing both can advance the same
            // composition twice and is the reason candidates previously disappeared.
            RimeNative.setAsciiMode(session, false);
            String lastCommit = null;
            // Android TV alphabet keys arrive as text input. Feeding them through
            // Rime's sequence interface keeps composition and candidate generation
            // in the same path.
            if (lower.length() > 0) {
                lastCommit = RimeNative.simulateKeySequence(session, lower);
            }
            refresh();
            return lastCommit;
        }
        composing += s.toLowerCase(Locale.US); publishFallback();
        return null;
    }

    public void splitPinyin() {
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
            publishFallback();
        }
    }

    public String backspace() {
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

    public String choose(int index) {
        if (index >= 0 && index < candidateRimeIndices.size() && candidateRimeIndices.get(index) < 0) {
            String out=candidates.get(index); usage.record(out); if(rime&&session!=0) RimeNative.setInput(session,""); refresh(); return out;
        }
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

    public String commitFirst() {
        if (rime && session != 0) {
            String commit = RimeNative.selectCandidate(session, 0);
            refresh(); return commit;
        }
        String s = candidates.isEmpty()?composing:candidates.get(0); usage.record(s); composing = ""; publishFallback(); return s;
    }

    public void refresh() {
        if (!rime || session == 0) { candidates.clear(); candidateRimeIndices.clear(); publish(); return; }
        String[] ctx = RimeNative.context(session);
        composing = ctx.length > 0 && ctx[0] != null ? ctx[0] : "";
        candidates.clear();
        candidateRimeIndices.clear();
        ArrayList<String> raw = new ArrayList<>();
        ArrayList<Integer> rawIndices = new ArrayList<>();
        for (int i=1;i<ctx.length;i++) if (ctx[i] != null && !ctx[i].isEmpty()) { raw.add(ctx[i]); rawIndices.add(i-1); }
        boolean chineseCandidate=false; for(String x:raw){for(int q=0;q<x.length();q++){if(x.charAt(q)>127){chineseCandidate=true;break;}}if(chineseCandidate)break;}
        if(!chineseCandidate && !composing.isEmpty()) { localCandidates(composing); listener.onCandidates(new ArrayList<>(candidates), composing); return; }
        for (int pass=0; pass<raw.size(); pass++) for (int j=0; j+1<raw.size(); j++) {
            if (usage.count(raw.get(j+1)) > usage.count(raw.get(j))) {
                String t=raw.get(j); raw.set(j,raw.get(j+1)); raw.set(j+1,t);
                Integer ti=rawIndices.get(j); rawIndices.set(j,rawIndices.get(j+1)); rawIndices.set(j+1,ti);
            }
        }
        candidates.addAll(raw); candidateRimeIndices.addAll(rawIndices);
        listener.onCandidates(new ArrayList<>(candidates), composing);
    }

    public boolean changePage(boolean backward){
        if(rime && session != 0){
            boolean ok=RimeNative.changePage(session, backward);
            if(ok) refresh();
            return ok;
        }
        return false;
    }

    public void clearUsage(){ usage.clear(); refresh(); }

    public void destroy() {
        if (rime && session != 0) RimeNative.destroySession(session);
        session = 0;
        if (rime) RimeNative.finalizeEngine();
        rime = false;
    }

    private void publish() { listener.onCandidates(new ArrayList<>(candidates), composing); }
    private void publishFallback() {
        candidates.clear();
        // Never present the raw Latin composition as a fake Chinese candidate.
        // A real Chinese candidate must come from librime.
        listener.onCandidates(new ArrayList<>(candidates), composing);
    }
}
