package com.golu.smartfreeze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if(ScheduleManager.enabled(context)) {
                ScheduleManager.scheduleNext(context, ScheduleReceiver.ACTION_START);
                ScheduleManager.scheduleNext(context, ScheduleReceiver.ACTION_END);
            }
        }
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Prefs.get(context).getBoolean(Prefs.ACTIVE,false)){
            Intent service=new Intent(context,MonitorService.class);
            try { if(Build.VERSION.SDK_INT>=26) context.startForegroundService(service); else context.startService(service); }
            catch(Exception e){ Prefs.log(context,"BOOT START FAILED: "+e.getClass().getSimpleName()); }
        }
    }
}
