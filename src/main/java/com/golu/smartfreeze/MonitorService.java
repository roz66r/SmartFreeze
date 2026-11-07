package com.golu.smartfreeze;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import java.util.Set;

public class MonitorService extends Service {
    public static volatile boolean RUNNING=false;
    public static boolean isRunning(){return RUNNING;}
    private static final String CHANNEL="smart_freeze_monitor";
    private static final int NOTIFICATION_ID=8101;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String lastTarget="";
    private long lastProcessedUsageEvent=0L;
    private BroadcastReceiver screenReceiver;
    private boolean screenReceiverRegistered=false;
    private volatile boolean lastScreenLocked=false;
    private volatile boolean screenOffHandled=false;
    private final Runnable task=new Runnable(){ public void run(){ cycle(); pollScreenState(); handler.postDelayed(this,500L); }};

    @Override public void onCreate(){
        super.onCreate(); RUNNING=true;
        // Ignore events that happened before monitoring was started. A trigger being
        // configured while its app is already open must not count as a new launch.
        lastProcessedUsageEvent=System.currentTimeMillis();
        createChannel();
        startForeground(NOTIFICATION_ID,notification("Monitoring active"));
        screenOffHandled=false;
        lastScreenLocked=false;
        registerScreenReceiver();
        Prefs.logSystem(this,"Monitoring service started; screen-lock detector active.");
        pollScreenState();
        handler.post(task);
    }
    private void registerScreenReceiver(){
        if(screenReceiverRegistered) return;
        screenReceiver=new BroadcastReceiver(){
            @Override public void onReceive(Context context, Intent intent){
                if(intent==null) return;
                final String action=intent.getAction();
                if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                    lastScreenLocked=true;
                    handleScreenOff("SCREEN_OFF broadcast");
                } else if(Intent.ACTION_USER_PRESENT.equals(action)) {
                    lastScreenLocked=false;
                    screenOffHandled=false;
                    handleScreenUnlock("USER_PRESENT broadcast");
                }
            }
        };
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if(Build.VERSION.SDK_INT>=33) registerReceiver(screenReceiver,filter,Context.RECEIVER_EXPORTED);
            else registerReceiver(screenReceiver,filter);
            screenReceiverRegistered=true;
            Prefs.log(this,"Screen event listener ready.");
            Prefs.logSystem(this,"Screen lock monitoring started.");
        } catch(Throwable t){
            Prefs.log(this,"Screen event listener failed: "+t.getClass().getSimpleName());
            Prefs.logSystem(this,"Could not start screen event listener: "+t.getClass().getSimpleName());
        }
    }
    private void pollScreenState(){
        try {
            android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);
            android.app.KeyguardManager km=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);
            if(pm==null) return;
            boolean locked=!pm.isInteractive() || (km!=null && km.isKeyguardLocked());
            if(locked && !lastScreenLocked) {
                lastScreenLocked=true;
                handleScreenOff("screen/keyguard state check");
            } else if(!locked && lastScreenLocked) {
                lastScreenLocked=false;
                screenOffHandled=false;
                handleScreenUnlock("screen/keyguard state check");
            }
        } catch(Throwable t) {
            Prefs.logSystem(this,"Screen-state check error: "+t.getClass().getSimpleName());
            Log.w("SmartFreeze","screen state check",t);
        }
    }
    private void handleScreenOff(String source){
        synchronized(this) {
            if(screenOffHandled) return;
            screenOffHandled=true;
        }
        final android.os.PowerManager.WakeLock wl;
        android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        android.os.PowerManager.WakeLock acquired=null;
        try {
            if(pm!=null) {
                acquired=pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"SmartFreeze:ScreenLock");
                acquired.acquire(15000L);
            }
        } catch(Throwable ignored) {}
        final android.os.PowerManager.WakeLock wake=acquired;
        new Thread(()->{
            try {
                Prefs.logSystem(MonitorService.this,"Screen locked ("+source+"). Freezing all Smart Freeze-managed apps.");
                String summary=FreezeEngine.freezeForScreenLock(MonitorService.this,handler);
                saveAutomationStatus(summary);
                Prefs.logSystem(MonitorService.this,summary);
                updateNotification(summary);
            } catch(Throwable t) {
                String summary="Screen lock processing failed: "+t.getClass().getSimpleName();
                saveAutomationStatus(summary); Prefs.logSystem(MonitorService.this,summary); updateNotification(summary);
            } finally {
                try { if(wake!=null && wake.isHeld()) wake.release(); } catch(Throwable ignored) {}
            }
        },"SmartFreeze-ScreenLock").start();
    }
    private void handleScreenUnlock(String source){
        new Thread(()->{
            try {
                String msg="Screen unlocked ("+source+"). Apps remain frozen; no automatic unfreeze.";
                Prefs.logSystem(MonitorService.this,msg);
                Prefs.log(MonitorService.this,"PHONE UNLOCK: no automatic unfreeze (on-demand only)");
                saveAutomationStatus(msg);
                updateNotification(msg);
            } catch(Throwable ignored) {}
        },"SmartFreeze-ScreenUnlock").start();
    }
    private String shortReport(String report){
        if(report==null) return "NO RESULT";
        String one=report.replace('\n',' ').trim();
        return one.length()>100?one.substring(0,100):one;
    }
    private void saveAutomationStatus(String value){
        Prefs.get(this).edit().putString("screen_auto_status",value).putLong("screen_auto_status_time",System.currentTimeMillis()).apply();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){ return START_STICKY; }
    @Override public void onDestroy(){
        RUNNING=false;
        handler.removeCallbacks(task);
        if(screenReceiverRegistered){try{unregisterReceiver(screenReceiver);}catch(Throwable ignored){} screenReceiverRegistered=false;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){ return null; }

    private void cycle(){
        try {
            String foreground=detectForeground();
            if(foreground!=null && !foreground.equals(lastTarget)) {
                lastTarget=foreground;
                Prefs.log(this,"FOREGROUND "+foreground);
                FreezeEngine.onForeground(this,foreground,handler);
                updateNotification("Foreground: "+label(foreground));
            }
        } catch(Throwable t){ Log.e("SmartFreeze","monitor cycle",t); }
    }
    private String detectForeground(){
        UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
        if(usm==null) return null;
        long now=System.currentTimeMillis();
        // Keep a small overlap for OEM event delivery delays, but strictly ignore
        // any event at or before the last consumed timestamp.
        long from=Math.max(now-30000L,lastProcessedUsageEvent-1000L);
        UsageEvents events=usm.queryEvents(from,now);
        if(events==null) return null;
        UsageEvents.Event e=new UsageEvents.Event();
        long latest=lastProcessedUsageEvent; String pkg=null; int type=-1;
        while(events.hasNextEvent()){
            events.getNextEvent(e);
            int t=e.getEventType(); long stamp=e.getTimeStamp();
            if((t==UsageEvents.Event.MOVE_TO_FOREGROUND || t==UsageEvents.Event.MOVE_TO_BACKGROUND
                    || (Build.VERSION.SDK_INT>=29 && (t==UsageEvents.Event.ACTIVITY_RESUMED || t==UsageEvents.Event.ACTIVITY_PAUSED)))
                    && stamp>latest){
                latest=stamp; pkg=e.getPackageName(); type=t;
            }
        }
        if(pkg==null) return null;
        lastProcessedUsageEvent=latest;
        boolean foreground=type==UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT>=29 && type==UsageEvents.Event.ACTIVITY_RESUMED);
        return foreground ? pkg : "";
    }
    private String label(String pkg){
        if(pkg==null || pkg.isEmpty()) return "Home / non-trigger";
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception e){return pkg;}
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){ NotificationChannel ch=new NotificationChannel(CHANNEL,"Smart Freeze Monitoring",NotificationManager.IMPORTANCE_LOW); ch.setDescription("Persistent foreground app monitoring"); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch); }
    }
    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("Smart Freeze V35").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void updateNotification(String text){ ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,notification(text)); }
}
