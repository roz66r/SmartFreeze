package com.golu.smartfreeze;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Small optional overlay for the active resettable grace countdown. */
final class GraceTimerDisplay {
    private static WindowManager windowManager;
    private static TextView badge;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static long deadline;
    private static final Runnable tick=new Runnable(){
        @Override public void run(){
            if(badge==null)return;
            long left=Math.max(0L,deadline-System.currentTimeMillis());
            long sec=(left+999L)/1000L;
            badge.setText(String.format(java.util.Locale.US,"GRACE  %02d:%02d",sec/60L,sec%60L));
            if(left<=0L){hideInternal();return;}
            handler.postDelayed(this,1000L);
        }
    };
    private GraceTimerDisplay(){}

    static synchronized void show(Context context,long until){
        Context c=context.getApplicationContext();
        if(Prefs.get(c).getInt("grace_display_mode",0)!=1)return;
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c)){
            Prefs.log(c,"Floating grace countdown unavailable: Display over other apps permission not granted; in-app countdown remains available.");
            return;
        }
        deadline=until;
        handler.post(()->showOnMain(c));
    }
    private static void showOnMain(Context c){
        try{
            if(badge==null){
                windowManager=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
                badge=new TextView(c);
                badge.setTextColor(Color.WHITE);badge.setTextSize(12f);badge.setPadding(18,10,18,10);badge.setGravity(Gravity.CENTER);
                GradientDrawable bg=new GradientDrawable();bg.setColor(0xff145c3b);bg.setCornerRadius(24f);bg.setStroke(2,0xff37e889);badge.setBackground(bg);
                badge.setElevation(8f);
            }
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.END;lp.x=16;lp.y=48;
            if(badge.getParent()==null)windowManager.addView(badge,lp);else windowManager.updateViewLayout(badge,lp);
            handler.removeCallbacks(tick);tick.run();
        }catch(Throwable t){Prefs.log(c,"Floating countdown display failed: "+t.getClass().getSimpleName());hideInternal();}
    }
    static synchronized void hide(Context context){handler.post(GraceTimerDisplay::hideInternal);}
    private static void hideInternal(){
        handler.removeCallbacks(tick);
        try{if(badge!=null&&badge.getParent()!=null&&windowManager!=null)windowManager.removeView(badge);}catch(Throwable ignored){}
        badge=null;windowManager=null;
    }
}
