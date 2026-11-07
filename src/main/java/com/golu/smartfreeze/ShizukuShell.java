package com.golu.smartfreeze;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import rikka.shizuku.Shizuku;
import java.lang.reflect.Method;
import java.util.Set;

final class ShizukuShell {
    static boolean ready() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }
    static boolean run(String action, String pkg) {
        if (!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) return false;
        try {
            // Shizuku 13.x keeps newProcess private in its public API.
            // Match the reflection-based invocation used by the supplied V28 source.
            Class<?> sh=Class.forName("rikka.shizuku.Shizuku");
            Method m=sh.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            m.setAccessible(true);
            Object process=m.invoke(null,new Object[]{
                new String[]{"pm",action,"--user","0",pkg},null,null
            });
            int code=(Integer)process.getClass().getMethod("waitFor").invoke(process);
            return code==0;
        } catch(Throwable t) { Log.e("SmartFreeze","pm "+action+" failed: "+pkg,t); return false; }
    }
    static boolean freeze(Context c, String pkg) {
        Set<String> owned=Prefs.set(c,Prefs.OWNED);
        // A package already owned by Smart Freeze is expected to be disabled.
        // Verify it rather than treating our own disabled state as external.
        if(owned.contains(pkg)) {
            if(isActuallyDisabled(c,pkg)) return true;
            // A managed package may currently be enabled during an active trigger session.
            boolean commandOk=run("disable-user",pkg);
            boolean verified=false;
            for(int i=0;i<8;i++){ if(isActuallyDisabled(c,pkg)){verified=true;break;} try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();break;} }
            if(commandOk && verified){ Prefs.log(c,"FROZEN VERIFIED "+pkg); return true; }
            Prefs.log(c,"FREEZE FAILED "+pkg+" — "+(!commandOk?"disable command failed":"Android still reports enabled"));
            return false;
        }
        try {
            PackageManager pm=c.getPackageManager();
            ApplicationInfo ai=pm.getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
            int state=pm.getApplicationEnabledSetting(pkg);
            boolean disabled=!ai.enabled ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            if(Build.VERSION.SDK_INT>=24 && pm.isPackageSuspended(pkg)) disabled=true;
            if(disabled) { Prefs.log(c,"NOT FROZEN (already disabled externally): "+pkg); return false; }
        } catch(PackageManager.NameNotFoundException e) { Prefs.log(c,"NOT FROZEN (package not found): "+pkg); return false; }
        owned.add(pkg); Prefs.putSet(c,Prefs.OWNED,owned); // journal before command
        boolean commandOk=run("disable-user",pkg);
        boolean verified=false;
        for(int i=0;i<6;i++) {
            if(isActuallyDisabled(c,pkg)) { verified=true; break; }
            try { Thread.sleep(150); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }
        if(commandOk && verified) { Prefs.log(c,"FROZEN VERIFIED "+pkg); return true; }
        owned.remove(pkg); Prefs.putSet(c,Prefs.OWNED,owned);
        String why=!commandOk?"pm disable-user command failed":"package still enabled after disable command";
        Prefs.log(c,"NOT FROZEN "+pkg+" — "+why);
        return false;
    }
    /** Explicitly selected trigger dependency: take over management even if another
     * freezer disabled it earlier. We enable it, verify Android state, then journal it. */
    static boolean adoptAndUnfreeze(Context c,String pkg) {
        Set<String> owned=Prefs.set(c,Prefs.OWNED);
        if(owned.contains(pkg)) {
            if(isActuallyEnabled(c,pkg)) return true; // already enabled for this managed session
            return unfreeze(c,pkg) && isActuallyEnabled(c,pkg);
        }
        if(!run("enable",pkg)) { Prefs.log(c,"ENABLE FAILED "+pkg+" — pm enable command failed"); return false; }
        boolean enabled=false;
        for(int i=0;i<8;i++){ if(isActuallyEnabled(c,pkg)){enabled=true;break;} try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();break;} }
        if(!enabled){ Prefs.log(c,"ENABLE FAILED "+pkg+" — Android still reports disabled"); return false; }
        owned.add(pkg); Prefs.putSet(c,Prefs.OWNED,owned);
        Prefs.log(c,"APP ADOPTED AND ENABLED "+pkg);
        return true;
    }
    static boolean isActuallyEnabled(Context c,String pkg) {
        try {
            PackageManager pm=c.getPackageManager();
            ApplicationInfo ai=pm.getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
            int state=pm.getApplicationEnabledSetting(pkg);
            boolean disabled=!ai.enabled || state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED || state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            if(Build.VERSION.SDK_INT>=24 && pm.isPackageSuspended(pkg)) disabled=true;
            return !disabled;
        } catch(Throwable t){ return false; }
    }

    static boolean isActuallyDisabled(Context c,String pkg) {
        try {
            PackageManager pm=c.getPackageManager();
            ApplicationInfo ai=pm.getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
            int state=pm.getApplicationEnabledSetting(pkg);
            boolean disabled=!ai.enabled || state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            if(Build.VERSION.SDK_INT>=24 && pm.isPackageSuspended(pkg)) disabled=true;
            return disabled;
        } catch(Throwable t) { return false; }
    }
    static boolean unfreeze(Context c, String pkg) {
        Set<String> owned=Prefs.set(c,Prefs.OWNED);
        if(!owned.contains(pkg)) return true;
        if(isActuallyEnabled(c,pkg)) { owned.remove(pkg); Prefs.putSet(c,Prefs.OWNED,owned); return true; }
        if(run("enable",pkg)) {
            boolean verified=false; for(int i=0;i<8;i++){ if(isActuallyEnabled(c,pkg)){verified=true;break;} try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();break;} }
            if(verified){ owned.remove(pkg); Prefs.putSet(c,Prefs.OWNED,owned); Prefs.log(c,"UNFROZEN VERIFIED "+pkg); return true; }
        }
        Prefs.log(c,"UNFREEZE FAILED "+pkg+" — Android still reports disabled"); return false;
    }


    /** Force-stop and relaunch a trigger once after a dependency was enabled. */
    static boolean restartPackage(String pkg) {
        if (!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) return false;
        try {
            Class<?> sh=Class.forName("rikka.shizuku.Shizuku");
            Method m=sh.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            m.setAccessible(true);
            Object process=m.invoke(null,new Object[]{
                new String[]{"sh","-c","am force-stop --user 0 "+pkg+"; sleep 0.5; monkey -p "+pkg+" 1"},null,null
            });
            int code=(Integer)process.getClass().getMethod("waitFor").invoke(process);
            return code==0;
        } catch(Throwable t) { Log.e("SmartFreeze","restart failed: "+pkg,t); return false; }
    }

    static boolean launchPackage(String pkg) {
        if (!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) return false;
        try {
            Class<?> sh=Class.forName("rikka.shizuku.Shizuku");
            Method m=sh.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            m.setAccessible(true);
            Object process=m.invoke(null,new Object[]{
                new String[]{"monkey","-p",pkg,"1"},null,null
            });
            int code=(Integer)process.getClass().getMethod("waitFor").invoke(process);
            return code==0;
        } catch(Throwable t) { Log.e("SmartFreeze","launch failed: "+pkg,t); return false; }
    }

    static boolean forceAutoRotationOff() {
        try {
            Class<?> sh=Class.forName("rikka.shizuku.Shizuku");
            Method m=sh.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            m.setAccessible(true);
            Object process=m.invoke(null,new Object[]{new String[]{"settings","put","system","accelerometer_rotation","0"},null,null});
            int code=(Integer)process.getClass().getMethod("waitFor").invoke(process);
            return code==0;
        } catch(Throwable t) { Log.e("SmartFreeze","Could not force auto-rotation off",t); return false; }
    }

    private ShizukuShell() {}
}
