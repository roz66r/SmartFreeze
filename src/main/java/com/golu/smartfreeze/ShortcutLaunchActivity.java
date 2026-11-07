package com.golu.smartfreeze;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.Set;

public final class ShortcutLaunchActivity extends Activity {
    private static final String EXTRA_PACKAGE = "smartfreeze.shortcut.package";
    private static final String EXTRA_TRIGGER = "smartfreeze.launch_gate.trigger";
    private static final String EXTRA_LABEL = "smartfreeze.launch_gate.label";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent source=getIntent();
        String trigger=source==null?null:source.getStringExtra(EXTRA_TRIGGER);
        String shortcutPkg=source==null?null:source.getStringExtra(EXTRA_PACKAGE);
        if(trigger!=null && !trigger.trim().isEmpty()) {
            runTriggerGate(trigger);
        } else if(shortcutPkg!=null && !shortcutPkg.trim().isEmpty()) {
            runLegacyShortcut(shortcutPkg);
        } else finish();
    }

    private void runTriggerGate(String trigger) {
        if(!Prefs.get(this).getBoolean("trigger_enabled:"+trigger,true)) { show("This trigger is temporarily disabled"); finish(); return; }
        if(!ShizukuShell.ready()) { show("Shizuku is not connected"); finish(); return; }
        new Thread(() -> {
            boolean allReady=true;
            Set<String> deps=Prefs.set(this,"trigger_rule:"+trigger);
            boolean rotationOff=ShizukuShell.forceAutoRotationOff();
            Prefs.logTrigger(this,trigger,rotationOff?"Auto-Rotation forced OFF before dependency preparation":"Auto-Rotation could not be changed (Shizuku command failed)");
            Prefs.logTrigger(this,trigger,"Launch requested. Preparing selected apps first.");
            for(String pkg:deps) {
                String name=label(pkg);
                boolean ok=ShizukuShell.adoptAndUnfreeze(this,pkg) && ShizukuShell.isActuallyEnabled(this,pkg);
                Prefs.logTrigger(this,trigger,ok?name+" enabled and verified.":name+" could not be enabled; launch stopped.");
                if(!ok) allReady=false;
            }
            boolean triggerReady=ShizukuShell.isActuallyEnabled(this,trigger);
            if(!triggerReady) {
                Prefs.logTrigger(this,trigger,"Trigger app is disabled. Enable it before using Launch.");
                allReady=false;
            }
            final boolean ready=allReady;
            new Handler(Looper.getMainLooper()).post(() -> {
                if(!ready) {
                    show("Some required apps are not ready. Check this trigger's Logs.");
                    finish();
                    return;
                }
                Toast.makeText(this,"Preparing required apps…",Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try { Thread.sleep(3000L); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                    boolean launched=ShizukuShell.launchPackage(trigger);
                    boolean rotationOffAfter=ShizukuShell.forceAutoRotationOff();
                    Prefs.logTrigger(this,trigger,launched?"Trigger launched after dependencies were enabled.":"Trigger launch failed.");
                    Prefs.logTrigger(this,trigger,rotationOffAfter?"Auto-Rotation verified OFF after launch":"Auto-Rotation OFF command failed after launch");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        show(launched?"Dependencies ready · app launched":"App launch failed");
                        finish();
                    });
                },"SmartFreeze-GatedLaunch").start();
            });
        },"SmartFreeze-PrepareDependencies").start();
    }

    private void runLegacyShortcut(String pkg) {
        new Thread(() -> {
            Set<String> owned=Prefs.set(this,Prefs.OWNED);
            boolean ok=true;
            for(String p:owned) if(!ShizukuShell.unfreeze(this,p)) ok=false;
            final boolean ready=ok && ShizukuShell.isActuallyEnabled(this,pkg);
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if(!ready) { show("Required app could not be enabled"); finish(); return; }
                    boolean launched=ShizukuShell.launchPackage(pkg);
                    show(launched?"App launched":"App launch failed");
                } catch(Throwable t) { show("Unable to open selected app"); }
                finish();
            });
        },"SmartFreeze-ShortcutLaunch").start();
    }

    private String label(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)).toString(); }
        catch(Throwable t) { return "Selected app"; }
    }
    private void show(String msg) {
        Toast.makeText(this,msg,Toast.LENGTH_LONG).show();
    }
}
