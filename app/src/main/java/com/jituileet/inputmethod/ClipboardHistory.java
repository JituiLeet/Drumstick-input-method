package com.jituileet.inputmethod;

import android.content.Context;
import java.util.*;

public final class ClipboardHistory {
    private static final String PREF = "drumstick_clipboard";
    private ClipboardHistory() {}
    public static synchronized void add(Context c, String text) {
        if (text == null || text.isEmpty()) return;
        ArrayList<String> list = load(c); list.remove(text); list.add(0,text); while(list.size()>500) list.remove(list.size()-1); save(c,list);
    }
    public static synchronized ArrayList<String> load(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); int n=p.getInt("count",0); ArrayList<String> out=new ArrayList<>();
        for(int i=0;i<n;i++){String s=p.getString("item_"+i,null); if(s!=null&&!s.isEmpty()) out.add(s);} return out;
    }
    public static synchronized void delete(Context c,int index){ArrayList<String> l=load(c); if(index>=0&&index<l.size()){l.remove(index);save(c,l);}}
    public static synchronized void update(Context c,int index,String text){ArrayList<String> l=load(c); if(index>=0&&index<l.size()&&text!=null&&!text.isEmpty()){l.set(index,text);save(c,l);}}
    public static synchronized void clear(Context c){save(c,new ArrayList<String>());}
    private static void save(Context c,List<String> l){android.content.SharedPreferences.Editor e=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear();e.putInt("count",l.size());for(int i=0;i<l.size();i++)e.putString("item_"+i,l.get(i));e.apply();}
}
