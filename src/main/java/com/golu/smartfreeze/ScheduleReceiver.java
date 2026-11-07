package com.golu.smartfreeze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.Set;

public final class ScheduleReceiver extends BroadcastReceiver {
    public static final String ACTION_START = "com.golu.smartfreeze.SCHEDULE_START";
    public static final String ACTION_END = "com.golu.smartfreeze.SCHEDULE_END";

    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                if (ACTION_START.equals(intent.getAction())) {
                    if (ShizukuShell.ready()) {
                        FreezeEngine.unfreezeAll(context);
                        // Launch each selected app so its content can refresh while unfrozen.
                        for (String pkg : Prefs.set(context, Prefs.FREEZE)) {
                            ShizukuShell.launchPackage(pkg);
                            try { Thread.sleep(1800L); } catch (InterruptedException ignored) {}
                        }
                        Prefs.log(context, "SCHEDULE START: apps unfrozen and launch requested");
                    } else {
                        Prefs.log(context, "SCHEDULE START FAILED: Shizuku unavailable");
                    }
                } else if (ACTION_END.equals(intent.getAction())) {
                    FreezeEngine.freezeAll(context);
                    Prefs.log(context, "SCHEDULE END: freeze requested");
                }
            } finally {
                ScheduleManager.scheduleNext(context, ACTION_START);
                ScheduleManager.scheduleNext(context, ACTION_END);
                pending.finish();
            }
        }, "SmartFreeze-Schedule").start();
    }
}
