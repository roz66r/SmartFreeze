package com.golu.smartfreeze;

import android.content.Context;
import android.os.Handler;
import java.util.Set;

final class FreezeEngine {
    
    private static Runnable pendingFreeze;
    private static Handler pendingFreezeHandler;
    private static long freezeGeneration=0L;
    private static boolean triggerActive;

    private static String activeTrigger="";
    private static volatile String restartInProgress="";
    private static volatile long restartGuardUntil=0L;

    static synchronized void onForeground(Context c, String pkg, Handler handler) {
        Set<String> triggers=Prefs.set(c,Prefs.TRIGGER);
        boolean isTrigger=pkg!=null && !pkg.isEmpty() && triggers.contains(pkg)
            && Prefs.get(c).getBoolean("trigger_enabled:"+pkg,true);
        if(isTrigger) {
            if(pendingFreeze!=null) {
                if(pendingFreezeHandler!=null) pendingFreezeHandler.removeCallbacks(pendingFreeze);
                pendingFreeze=null; pendingFreezeHandler=null; freezeGeneration++;
                Prefs.get(c).edit().remove("grace_deadline_ms").apply();
                GraceTimerDisplay.hide(c);
                Prefs.logTrigger(c,pkg,"Resettable Grace Timer cancelled and reset: trigger resumed; full selected duration will apply next time it backgrounds.");
                Prefs.log(c,"RESTORE COUNTDOWN CANCELLED AND RESET");
            }
            if(!pkg.equals(activeTrigger)) {
                String previous=activeTrigger;
                activeTrigger=pkg;
                Prefs.log(c,"TRIGGER SESSION OPENED "+pkg+(previous.isEmpty()?"":" (switch from "+previous+")"));
                Prefs.logTrigger(c,pkg,"Trigger launched");
                applyTriggerRule(c,pkg);
            }
        } else if(!activeTrigger.isEmpty()) {
            // A controlled one-time relaunch is part of dependency warm-up. Do not
            // interpret the brief force-stop gap as the user ending the session.
            if(!restartInProgress.isEmpty() && System.currentTimeMillis()<restartGuardUntil) return;
            String previous=activeTrigger; activeTrigger="";
            Prefs.log(c,"TRIGGER SESSION ENDED "+previous);
            Prefs.logTrigger(c,previous,"Trigger no longer in foreground; checking dependencies");
            scheduleRestore(c,handler,previous);
        }
    }

    private static void applyTriggerRule(Context c,String triggerPkg) {
        if(!ShizukuShell.ready()) { Prefs.log(c,"TRIGGER RULE FAILED: Shizuku unavailable"); Prefs.logTrigger(c,triggerPkg,"Could not start: Shizuku is not connected"); return; }
        // The trigger app is NOT one of its own dependencies. It may also be
        // present in another rule's Freeze List (for example Play Store under
        // Gmail). Keep its ownership record and never adopt/unfreeze it here.
        Set<String> required=Prefs.set(c,"trigger_rule:"+triggerPkg);
        required.remove(triggerPkg);
        Set<String> owned=Prefs.set(c,Prefs.OWNED);
        int up=0,down=0;
        boolean dependencyBecameEnabled=false;
        for(String p:required) {
            String friendly=appLabel(c,p);
            {
                boolean wasEnabled=ShizukuShell.isActuallyEnabled(c,p);
                boolean enabled=ShizukuShell.adoptAndUnfreeze(c,p);
                if(enabled && ShizukuShell.isActuallyEnabled(c,p)) {
                    up++;
                    if(!wasEnabled) dependencyBecameEnabled=true;
                    Prefs.logTrigger(c,triggerPkg,friendly+" enabled and verified");
                } else Prefs.logTrigger(c,triggerPkg,friendly+" could not be enabled");
            }
        }
        // Anything Smart Freeze owns but this trigger does not require stays frozen.
        for(String p:new java.util.HashSet<>(owned)) if(!required.contains(p) && !p.equals(triggerPkg)) {
            boolean ok=ShizukuShell.freeze(c,p) && ShizukuShell.isActuallyDisabled(c,p);
            if(ok) { down++; Prefs.logTrigger(c,triggerPkg,appLabel(c,p)+" frozen and verified (not needed by this trigger)"); }
            else Prefs.logTrigger(c,triggerPkg,appLabel(c,p)+" could not be frozen");
        }
        Prefs.log(c,"TRIGGER RULE "+triggerPkg+" requiredDependencies="+required.size()+" unfrozen="+up+" unrelated-refrozen="+down+" triggerPackagePreserved=true");
        Prefs.logTrigger(c,triggerPkg,"Trigger package itself was excluded from dependency processing; existing Freeze List ownership was preserved.");
        Prefs.logTrigger(c,triggerPkg,"Activation result: "+up+" selected apps enabled and verified; "+(required.size()-up)+" not confirmed");
        // If this launch had to enable a dependency, the app may already have
        // shown a transient "Play services unavailable" error. Restart it once,
        // after Android has had time to bring the dependency online.
        if(dependencyBecameEnabled && !triggerPkg.equals(c.getPackageName())) {
            scheduleOneTimeDependencyRestart(c,triggerPkg);
        }
    }

    private static void scheduleOneTimeDependencyRestart(Context c,String triggerPkg) {
        synchronized(FreezeEngine.class) {
            if(triggerPkg.equals(restartInProgress) && System.currentTimeMillis()<restartGuardUntil) return;
            restartInProgress=triggerPkg;
            restartGuardUntil=System.currentTimeMillis()+15000L;
        }
        Prefs.logTrigger(c,triggerPkg,"A required service was just enabled. Restarting this app once so it can reconnect.");
        new Thread(() -> {
            try {
                Thread.sleep(2500L);
                if(!ShizukuShell.ready()) {
                    Prefs.logTrigger(c,triggerPkg,"Automatic restart skipped: Shizuku is not connected.");
                    return;
                }
                boolean ok=ShizukuShell.restartPackage(triggerPkg);
                Prefs.logTrigger(c,triggerPkg,ok?"App restarted after dependency became ready.":"App restart failed; reopen it manually.");
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                Prefs.logTrigger(c,triggerPkg,"Automatic restart interrupted.");
            } finally {
                // Keep a short guard after relaunch for the foreground event gap.
                new Thread(() -> {
                    try { Thread.sleep(12000L); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    synchronized(FreezeEngine.class) { if(triggerPkg.equals(restartInProgress)) { restartInProgress=""; restartGuardUntil=0L; } }
                },"SmartFreeze-RestartGuard").start();
            }
        },"SmartFreeze-DependencyRestart").start();
    }

    static synchronized void cancelTrigger(String triggerPkg, Context c) {
        if(triggerPkg==null) return;
        if(triggerPkg.equals(activeTrigger)) {
            activeTrigger="";
            Prefs.logTrigger(c,triggerPkg,"Trigger disabled while active; restore scheduled with safety grace");
            // Monitoring cycle will not regard this package as a trigger while disabled.
        }
    }

    static synchronized void restoreNow(Context c) {
        if(pendingFreeze!=null && pendingFreezeHandler!=null) pendingFreezeHandler.removeCallbacks(pendingFreeze);
        pendingFreeze=null; pendingFreezeHandler=null; freezeGeneration++;
        Prefs.get(c).edit().remove("grace_deadline_ms").apply();
        GraceTimerDisplay.hide(c);
        activeTrigger="";
        freezeAll(c);
    }
    private static synchronized void scheduleRestore(final Context c, Handler h, final String endedTrigger) {
        int mode=Prefs.get(c).getInt(Prefs.MODE,3);
        if(mode==2) { Prefs.log(c,"MANUAL MODE: waiting for user"); return; }
        if(pendingFreeze!=null && pendingFreezeHandler!=null) pendingFreezeHandler.removeCallbacks(pendingFreeze);
        final long generation=++freezeGeneration;
        final long graceMinutes=Math.max(1L,Math.min(1440L,Prefs.get(c).getLong("background_grace_minutes",5L)));
        final long delay=graceMinutes*60_000L;
        final long deadline=System.currentTimeMillis()+delay;
        Prefs.get(c).edit().putLong("grace_deadline_ms",deadline).putString("grace_trigger_pkg",endedTrigger).apply();
        GraceTimerDisplay.show(c,deadline);
        pendingFreezeHandler=h;
        pendingFreeze=new Runnable(){ public void run(){
            synchronized(FreezeEngine.class){
                if(generation!=freezeGeneration) return;
                pendingFreeze=null; pendingFreezeHandler=null;
            }
            Prefs.get(c).edit().remove("grace_deadline_ms").apply();
            GraceTimerDisplay.hide(c);
            Prefs.logTrigger(c,endedTrigger,"Resettable Grace Timer expired; trigger remained in background. Freezing managed dependencies.");
            freezeAll(c); logTriggerFreezeResults(c,endedTrigger);
        }};
        h.postDelayed(pendingFreeze,delay);
        Prefs.log(c,"RESETTABLE GRACE TIMER STARTED: "+graceMinutes+" MINUTES for "+endedTrigger);
        Prefs.logTrigger(c,endedTrigger,"Background countdown started: "+graceMinutes+" minute(s). Resuming this trigger before expiry cancels and resets the full timer.");
    }
    private static String appLabel(Context c,String pkg){ try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)).toString();}catch(Throwable t){return "Selected app";} }
    static synchronized String freezeForScreenLock(Context c, Handler handler) {
        activeTrigger="";
        if(pendingFreeze!=null && pendingFreezeHandler!=null) pendingFreezeHandler.removeCallbacks(pendingFreeze);
        pendingFreeze=null; pendingFreezeHandler=null; freezeGeneration++;
        Prefs.logSystem(c,"Screen locked. Freezing Smart Freeze managed apps.");
        if(!ShizukuShell.ready()) {
            Prefs.logSystem(c,"Freeze failed: Shizuku is not connected or permission is missing.");
            return "Frozen: 0 · Failed: 0 · Shizuku unavailable";
        }
        Set<String> targets=Prefs.set(c,Prefs.FREEZE);
        targets.addAll(Prefs.set(c,Prefs.OWNED));
        int success=0, failed=0, skipped=0;
        for(String pkg:targets) {
            String label=appLabel(c,pkg);
            boolean wasOwned=Prefs.set(c,Prefs.OWNED).contains(pkg);
            boolean ok=ShizukuShell.freeze(c,pkg);
            boolean owned=Prefs.set(c,Prefs.OWNED).contains(pkg);
            boolean disabled=ShizukuShell.isActuallyDisabled(c,pkg);
            if(ok && owned && disabled) {
                success++;
                Prefs.logSystem(c,label+" — Frozen successfully (verified).");
            } else if(disabled && !owned && !wasOwned) {
                skipped++;
                Prefs.logSystem(c,label+" — Already disabled by another app; left untouched.");
            } else {
                failed++;
                String reason=!ShizukuShell.ready()?"Shizuku disconnected":(disabled?"Disabled state not owned by Smart Freeze":"Android still reports enabled");
                Prefs.logSystem(c,label+" — Freeze failed. "+reason+".");
            }
        }
        String summary="Screen lock complete · Frozen: "+success+" · Failed: "+failed+(skipped>0?" · Skipped: "+skipped:"");
        Prefs.logSystem(c,summary);
        Prefs.log(c,"SCREEN LOCK RESULT: "+summary);
        return summary;
    }
    static void freezeAll(Context c) { freezeAllWithReport(c); }
    private static void logTriggerFreezeResults(Context c,String triggerPkg){
        if(triggerPkg==null || triggerPkg.isEmpty()) return;
        Set<String> required=Prefs.set(c,"trigger_rule:"+triggerPkg);
        for(String pkg:required){
            boolean owned=Prefs.set(c,Prefs.OWNED).contains(pkg);
            boolean disabled=ShizukuShell.isActuallyDisabled(c,pkg);
            String label=appLabel(c,pkg);
            if(owned && disabled) Prefs.logTrigger(c,triggerPkg,label+" frozen successfully (verified)");
            else if(!owned && disabled) Prefs.logTrigger(c,triggerPkg,label+" is disabled, but not managed by Smart Freeze");
            else Prefs.logTrigger(c,triggerPkg,label+" freeze failed — still enabled");
        }
    }

    static String freezeAllWithReport(Context c) {
        if(!ShizukuShell.ready()) {
            Prefs.log(c,"FREEZE FAILED: Shizuku unavailable");
            return "FREEZE FAILED\nShizuku is not connected or permission is missing.";
        }
        Set<String> selected=Prefs.set(c,Prefs.FREEZE);
        selected.addAll(Prefs.set(c,Prefs.OWNED));
        if(selected.isEmpty()) return "Freeze List is empty.";
        int frozen=0, failed=0;
        StringBuilder report=new StringBuilder();
        for(String pkg:selected) {
            boolean ok=ShizukuShell.freeze(c,pkg);
            String label=pkg;
            try { label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)).toString(); } catch(Throwable ignored) {}
            boolean verified=ShizukuShell.isActuallyDisabled(c,pkg) && Prefs.set(c,Prefs.OWNED).contains(pkg);
            if(ok && verified) { frozen++; report.append("✓ FROZEN  ").append(label).append("\n"); Prefs.log(c,"FROZEN SUCCESSFULLY: "+label); }
            else { failed++; report.append("✗ NOT FROZEN  ").append(label).append("\n"); Prefs.log(c,"FREEZE FAILED: "+label+" — command or Android-state verification failed"); }
        }
        String summary="Verified frozen: "+frozen+"\nNot frozen: "+failed+"\n\n"+report;
        Prefs.log(c,"FREEZE REPORT: verified="+frozen+", failed="+failed);
        return summary;
    }
    static void unfreezeAll(Context c) { unfreezeAllWithReport(c); }

    static String unfreezeAllWithReport(Context c) {
        if(!ShizukuShell.ready()) {
            Prefs.log(c,"UNFREEZE FAILED: Shizuku unavailable");
            return "UNFREEZE FAILED: Shizuku unavailable";
        }
        // Restore only packages that Smart Freeze itself journaled as frozen.
        Set<String> owned = Prefs.set(c, Prefs.OWNED);
        int restored=0, failed=0;
        for(String pkg : new java.util.HashSet<>(owned)) {
            if(ShizukuShell.unfreeze(c, pkg)) restored++; else failed++;
        }
        int remaining=Prefs.set(c,Prefs.OWNED).size();
        String result="Unfrozen: "+restored+", failed: "+failed+", still managed: "+remaining;
        Prefs.log(c, "UNFREEZE REPORT: "+result);
        return result;
    }
    static void recover(Context c) {
        if(!ShizukuShell.ready()) { Prefs.log(c,"RECOVERY WAITING: Shizuku unavailable"); return; }
        // A crash may have happened after journaling but before pm disable completed.
        Set<String> owned=Prefs.set(c,Prefs.OWNED);
        for(String pkg:new java.util.HashSet<>(owned)) {
            try {
                boolean enabled=c.getPackageManager().getApplicationInfo(pkg,0).enabled;
                if(enabled) { owned.remove(pkg); }
            } catch(Exception e) { owned.remove(pkg); }
        }
        Prefs.putSet(c,Prefs.OWNED,owned);
        if(!Prefs.get(c).getBoolean(Prefs.ACTIVE,false)) {
            for(String pkg:owned) ShizukuShell.unfreeze(c,pkg);
        }
    }
    private FreezeEngine() {}
}
