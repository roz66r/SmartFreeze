package com.golu.smartfreeze;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static final String FILE = "smart_freeze_v1";
    static final String FREEZE = "freeze_list";
    static final String TRIGGER = "trigger_list";
    static final String OWNED = "frozen_by_us";
    static final String MODE = "restore_mode"; // 0 instant, 30 delayed, 2 manual
    static final String ACTIVE = "monitor_active";
    static SharedPreferences get(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static Set<String> set(Context c, String key) { return new HashSet<>(get(c).getStringSet(key, Collections.<String>emptySet())); }
    static void putSet(Context c, String key, Set<String> values) { get(c).edit().putStringSet(key, new HashSet<>(values)).apply(); }
    static void log(Context c, String line) {
        SharedPreferences p=get(c); String old=p.getString("logs", "");
        String next=System.currentTimeMillis()+" | "+line+"\n"+old;
        if(next.length()>18000) next=next.substring(0,18000);
        p.edit().putString("logs",next).apply();
    }
    static void logSystem(Context c, String line) {
        SharedPreferences p=get(c); String old=p.getString("system_logs", "");
        String stamp=java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date());
        String next=stamp+" · "+line+"\n"+old;
        if(next.length()>14000) next=next.substring(0,14000);
        p.edit().putString("system_logs",next).apply();
    }
    static void logTrigger(Context c,String triggerPkg,String line) {
        String label=triggerPkg;
        try { label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(triggerPkg,android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)).toString(); } catch(Throwable ignored) {}
        String old=get(c).getString("trigger_logs:"+triggerPkg,"");
        String next=java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date())+" · "+line+"\n"+old;
        if(next.length()>10000) next=next.substring(0,10000);
        get(c).edit().putString("trigger_logs:"+triggerPkg,next).apply();
    }
    private Prefs() {}
}
