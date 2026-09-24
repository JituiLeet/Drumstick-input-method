package com.jituileet.inputmethod;
import android.content.Context;
import android.content.SharedPreferences;
public final class UsageTracker {
    private final SharedPreferences prefs;
    public UsageTracker(Context c){prefs=c.getSharedPreferences("candidate_usage",Context.MODE_PRIVATE);}
    public void record(String s){if(s==null||s.isEmpty())return;int n=prefs.getInt(s,0);prefs.edit().putInt(s,Math.min(Integer.MAX_VALUE-1,n+1)).apply();}
    public int count(String s){return prefs.getInt(s,0);}
    public long score(String s){int n=count(s); return ((long)n*1000L)+Math.min(999,s==null?0:s.length());}
    public void clear(){prefs.edit().clear().apply();}
}
