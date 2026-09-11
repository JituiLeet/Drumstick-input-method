package com.jituileet.inputmethod;
import android.content.Context;
import android.content.SharedPreferences;
public final class Prefs {
    private static final String P="drumstick";
    private Prefs(){}
    private static SharedPreferences p(Context c){return c.getSharedPreferences(P,Context.MODE_PRIVATE);}
    public static boolean dark(Context c){return p(c).getBoolean("dark",false);} public static void dark(Context c,boolean v){p(c).edit().putBoolean("dark",v).apply();}
    public static int color(Context c){return p(c).getInt("color",0xFFEBECF0);} public static void color(Context c,int v){p(c).edit().putInt("color",v).apply();}
    public static boolean phone(Context c){return p(c).getBoolean("phone",false);} public static void phone(Context c,boolean v){p(c).edit().putBoolean("phone",v).apply();}
    public static boolean keyboard(Context c){return p(c).getBoolean("keyboard",true);} public static void keyboard(Context c,boolean v){p(c).edit().putBoolean("keyboard",v).apply();}
    public static boolean hardwareNotice(Context c){return p(c).getBoolean("hardware_notice",true);} public static void hardwareNotice(Context c,boolean v){p(c).edit().putBoolean("hardware_notice",v).apply();}
    public static String language(Context c){return p(c).getString("language","auto");} public static void language(Context c,String v){p(c).edit().putString("language",v).apply();}
    public static String dict(Context c){return p(c).getString("dict","内置词库");} public static void dict(Context c,String v){p(c).edit().putString("dict",v).apply();}
    public static int keyboardLayout(Context c){int v=p(c).getInt("keyboard_layout",0); return v==1?1:0;} public static void keyboardLayout(Context c,int v){if(v==0||v==1)p(c).edit().putInt("keyboard_layout",v).apply();}
}
