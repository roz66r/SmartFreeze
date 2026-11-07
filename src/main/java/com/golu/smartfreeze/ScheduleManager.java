package com.golu.smartfreeze;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

public final class ScheduleManager {
    private static final int START_ID = 17021;
    private static final int END_ID = 17022;

    public static boolean enabled(Context c) {
        return Prefs.get(c).getBoolean("schedule_enabled", false);
    }
    public static int startMinute(Context c) { return Prefs.get(c).getInt("schedule_start_minute", 120); }
    public static int endMinute(Context c) { return Prefs.get(c).getInt("schedule_end_minute", 150); }

    public static void save(Context c, boolean enabled, int start, int end) {
        Prefs.get(c).edit().putBoolean("schedule_enabled", enabled)
            .putInt("schedule_start_minute", start).putInt("schedule_end_minute", end).apply();
        if (enabled) { scheduleNext(c, ScheduleReceiver.ACTION_START); scheduleNext(c, ScheduleReceiver.ACTION_END); }
        else { cancel(c, ScheduleReceiver.ACTION_START); cancel(c, ScheduleReceiver.ACTION_END); }
    }
    public static void scheduleNext(Context c, String action) {
        if (!enabled(c)) return;
        int minute = action.equals(ScheduleReceiver.ACTION_START) ? startMinute(c) : endMinute(c);
        Calendar now = Calendar.getInstance();
        Calendar at = (Calendar) now.clone();
        at.set(Calendar.HOUR_OF_DAY, minute / 60);
        at.set(Calendar.MINUTE, minute % 60);
        at.set(Calendar.SECOND, 0); at.set(Calendar.MILLISECOND, 0);
        if (!at.after(now)) at.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi=pending(c, action);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                // Fall back to idle-allowed inexact alarm; UI explains exact alarm permission.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
            }
        } else am.setExact(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
    }
    private static void cancel(Context c,String action) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am!=null) am.cancel(pending(c,action));
    }
    private static PendingIntent pending(Context c,String action) {
        int id=action.equals(ScheduleReceiver.ACTION_START)?START_ID:END_ID;
        Intent i=new Intent(c,ScheduleReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private ScheduleManager() {}
}
