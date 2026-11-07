package com.golu.smartfreeze;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.view.MotionEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQ_SHIZUKU=8102;
    private static final String EXTRA_SHORTCUT_PACKAGE="smartfreeze.shortcut.package";
    private LinearLayout root, permissionRow, selectorSection, actionGrid;
    private TextView inventoryStatus;
    private Button startStopButton;
    private LinearLayout dualPanel, freezePanelBody, triggerPanelBody;
    private TextView freezePanelTab, triggerPanelTab;
    private float panelDownX;
    private boolean showingFreezePanel = true;
    private final android.os.Handler shortcutHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable shortcutTimeout;
    private String currentSelectorKey=Prefs.FREEZE;
    private static final String APP_CACHE_KEY="app_inventory_json";
    private TextView status, shizukuStatus, usageStatus, screenAutoStatus, graceCountdownView;
    private final android.os.Handler graceUiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable graceUiTick=new Runnable(){ public void run(){ refreshGraceCountdown(); graceUiHandler.postDelayed(this,1000L); }};
    private LinearLayout liveStatusRows;
    private final android.os.Handler liveStatusHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean liveStatusRunning=false;
    private volatile boolean liveStatusQueryRunning=false;
    private final int bg=Color.rgb(5,5,10), panel=Color.rgb(20,19,29), purple=Color.rgb(180,92,255), cyan=Color.rgb(33,212,253), green=Color.rgb(55,232,137), muted=Color.rgb(165,165,180);
    private Shizuku.OnRequestPermissionResultListener permissionListener;

    @Override public void onCreate(Bundle b){ super.onCreate(b); if(getIntent()!=null && getIntent().getBooleanExtra("shortcut_next",false)){advanceShortcutQueue(getIntent());} permissionListener=(code,result)->runOnUiThread(()->{refreshPermissionBadges();toast(result==PackageManager.PERMISSION_GRANTED?"Shizuku permission granted":"Shizuku permission denied");}); Shizuku.addRequestPermissionResultListener(permissionListener); buildUi(); }
    @Override protected void onPause(){stopLiveStatusUpdates();graceUiHandler.removeCallbacks(graceUiTick);super.onPause();}
    @Override protected void onDestroy(){stopLiveStatusUpdates();super.onDestroy();try{if(permissionListener!=null)Shizuku.removeRequestPermissionResultListener(permissionListener);}catch(Throwable ignored){}}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(24)); root.setBackgroundColor(bg);
        scroll.addView(root); setContentView(scroll);
        TextView title=text("Smart Freeze",26,true,Color.WHITE); root.addView(title);
        TextView sub=text("On-demand app automation",12,false,muted); sub.setPadding(0,dp(3),0,dp(4)); root.addView(sub);
        graceCountdownView=text("No active grace countdown",11,true,muted);
        graceCountdownView.setGravity(Gravity.END); graceCountdownView.setPadding(0,0,0,dp(10)); root.addView(graceCountdownView);
        LinearLayout liveCard=new LinearLayout(this); liveCard.setOrientation(LinearLayout.VERTICAL); liveCard.setPadding(dp(12),dp(10),dp(12),dp(10)); liveCard.setBackground(round(panel,dp(14),0xff30303d)); root.addView(liveCard);
        TextView liveTitle=text("LIVE APP STATUS",13,true,cyan); liveCard.addView(liveTitle);
        TextView liveHint=text("Current state · updates automatically",10,false,muted); liveHint.setPadding(0,dp(2),0,dp(7)); liveCard.addView(liveHint);
        // Keep the live list in its own bounded scroll area so multiple dependency
        // rows remain visible/scrollable instead of being clipped by the dashboard.
        ScrollView liveStatusScroll=new ScrollView(this);
        liveStatusScroll.setFillViewport(false);
        liveStatusScroll.setVerticalScrollBarEnabled(true);
        liveStatusRows=new LinearLayout(this); liveStatusRows.setOrientation(LinearLayout.VERTICAL);
        liveStatusScroll.addView(liveStatusRows,new ScrollView.LayoutParams(-1,-2));
        liveCard.addView(liveStatusScroll,new LinearLayout.LayoutParams(-1,dp(174)));
        TextView liveEmpty=text("Add triggers to monitor app status",11,false,muted); liveStatusRows.addView(liveEmpty);
        permissionRow=new LinearLayout(this); permissionRow.setGravity(Gravity.CENTER); permissionRow.setPadding(0,dp(10),0,dp(8)); root.addView(permissionRow);
        shizukuStatus=badge("Shizuku",false); usageStatus=badge("Usage Access",false);
        permissionRow.addView(shizukuStatus); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2); bp.leftMargin=dp(6); permissionRow.addView(usageStatus,bp);
        LinearLayout setup=new LinearLayout(this); setup.setOrientation(LinearLayout.HORIZONTAL); root.addView(setup);
        Button mode=smallButton("Mode");
        LinearLayout.LayoutParams modeLp=new LinearLayout.LayoutParams(0,dp(44),1); modeLp.setMargins(0,0,dp(5),0); setup.addView(mode,modeLp);
        Button monitor=smallButton("Start"); startStopButton=monitor;
        LinearLayout.LayoutParams monitorLp=new LinearLayout.LayoutParams(0,dp(44),1); monitorLp.setMargins(dp(3),0,dp(3),0); setup.addView(monitor,monitorLp);
        Button shizuku=smallButton("Shizuku");
        LinearLayout.LayoutParams shizukuLp=new LinearLayout.LayoutParams(0,dp(44),1); shizukuLp.setMargins(dp(5),0,0,0); setup.addView(shizuku,shizukuLp);
        mode.setOnClickListener(v->pickMode()); monitor.setOnClickListener(v->{toggleMonitoring();v.postDelayed(this::refreshStartStopButton,350);}); shizuku.setOnClickListener(v->connectShizuku()); refreshStartStopButton();
        TextView setupHint=text("1. Allow Shizuku and Usage Access · 2. Select On-Demand / Screen Lock mode · 3. Add triggers",11,false,muted);
        setupHint.setPadding(0,dp(10),0,dp(12)); root.addView(setupHint);
        TextView heading=text("Your Triggers",18,true,Color.WHITE); root.addView(heading);
        LinearLayout triggerCards=new LinearLayout(this); triggerCards.setOrientation(LinearLayout.VERTICAL); root.addView(triggerCards);
        renderTriggerCards(triggerCards);
        Button add=smallButton("＋  Add Trigger"); add.setTextSize(15); add.setTextColor(Color.WHITE); add.setBackground(round(0xff123044,dp(14),cyan));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52)); ap.topMargin=dp(12); root.addView(add,ap);
        add.setOnClickListener(v->showTriggerWizard());
        Button systemLogs=smallButton("System Logs · Screen Lock Results");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(44)); slp.topMargin=dp(8); root.addView(systemLogs,slp);
        systemLogs.setOnClickListener(v->showSystemLogs());
        TextView footer=text("Screen lock freezes managed apps. Unlock does not unfreeze them. Only configured trigger rules request dependencies.",10,false,muted);
        footer.setPadding(0,dp(14),0,dp(8)); root.addView(footer);
        refreshCounts(); refreshPermissionBadges(); refreshLiveStatus();
        // Refresh inventory only when missing or older than one day; wizard itself queries PackageManager live.
        long inventoryAge=System.currentTimeMillis()-Prefs.get(this).getLong("app_inventory_updated",0L);
        if(cachedInventoryCount()==0 || inventoryAge>24L*60L*60L*1000L) refreshAppInventory(null);
    }
    private void polishButton(Button b,int fill,int stroke){
        b.setAllCaps(false); b.setTextColor(0xffEAFBFF); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(fill,dp(13),stroke)); b.setElevation(dp(2));
        b.setStateListAnimator(null);
        b.setOnTouchListener((view,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){view.animate().scaleX(.965f).scaleY(.965f).alpha(.88f).setDuration(85).start();}
            else if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL){view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(135).start();}
            return false;
        });
    }
    private Button smallButton(String label){ Button b=new Button(this); b.setText(label); b.setTextSize(12); b.setMinHeight(dp(38)); polishButton(b,0xff102A3B,0xff21D4FD); return b; }
    private Button compactActionButton(String label){ Button b=new Button(this); b.setText(label); b.setTextSize(11); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(30)); b.setPadding(dp(2),0,dp(2),0); polishButton(b,0xff142333,0xff28566B); return b; }
    private void renderTriggerCards(LinearLayout parent){
        parent.removeAllViews();
        List<String> triggers=new ArrayList<>(Prefs.set(this,Prefs.TRIGGER)); Collections.sort(triggers);
        if(triggers.isEmpty()){ TextView empty=text("No triggers yet. Tap Add Trigger to create your first rule.",12,false,muted); empty.setPadding(dp(8),dp(14),dp(8),dp(14)); parent.addView(empty); return; }
        PackageManager pm=getPackageManager();
        for(String pkg:triggers){
            String label=pkg; try{label=pm.getApplicationLabel(pm.getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS)).toString();}catch(Throwable ignored){}
            final String cardLabel=label;
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(10),dp(12),dp(10)); card.setBackground(round(panel,dp(12),0xff343344));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(8); parent.addView(card,cp);
            // V38: each trigger has an independent, persistent enable switch.
            LinearLayout titleRow=new LinearLayout(this); titleRow.setOrientation(LinearLayout.HORIZONTAL); titleRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
            TextView name=text(label,15,true,Color.WHITE); name.setMaxLines(3); name.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
            android.widget.Switch triggerSwitch=new android.widget.Switch(this);
            triggerSwitch.setTextOn("ON"); triggerSwitch.setTextOff("OFF");
            boolean triggerEnabled=Prefs.get(this).getBoolean("trigger_enabled:"+pkg,true);
            triggerSwitch.setChecked(triggerEnabled);
            triggerSwitch.setShowText(false);
            triggerSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{cyan,0xff91A4B5}));
            triggerSwitch.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff287C91,0xff344657}));
            titleRow.addView(triggerSwitch,new LinearLayout.LayoutParams(-2,-2));
            triggerSwitch.setOnCheckedChangeListener((button,checked)->{
                Prefs.get(this).edit().putBoolean("trigger_enabled:"+pkg,checked).apply();
                Prefs.logTrigger(this,pkg,checked?"Trigger enabled by user":"Trigger temporarily disabled by user");
                if(!checked) FreezeEngine.cancelTrigger(pkg,this);
                Toast.makeText(this,checked?"Trigger enabled":"Trigger disabled",Toast.LENGTH_SHORT).show();
            });
            Set<String> deps=Prefs.set(this,"trigger_rule:"+pkg);
            TextView detail=text(deps.size()+" dependencies",10,false,muted); detail.setPadding(0,dp(4),0,dp(7)); card.addView(detail);
            LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER_VERTICAL); card.addView(actions,new LinearLayout.LayoutParams(-1,dp(34)));
            Button launch=compactActionButton("Launch"); Button edit=compactActionButton("Edit"); Button log=compactActionButton("Logs"); Button del=compactActionButton("Delete");
            Button[] actionButtons={launch,edit,log,del};
            for(int ai=0;ai<actionButtons.length;ai++){
                LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(0,dp(32),1f);
                if(ai>0) actionLp.leftMargin=dp(4);
                actions.addView(actionButtons[ai],actionLp);
            }
            launch.setOnClickListener(v->launchThroughGate(pkg,cardLabel));
            launch.setOnLongClickListener(v->{
                new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme))
                    .setTitle(cardLabel)
                    .setItems(new String[]{"Create Home Screen Shortcut"},(dialog,which)->pinTriggerShortcut(pkg,cardLabel))
                    .setNegativeButton("Cancel",null).show();
                return true;
            });
            edit.setOnClickListener(v->showDependencyPicker(pkg,cardLabel));
            log.setOnClickListener(v->showTriggerLogs(pkg,cardLabel));
            del.setOnClickListener(v->new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Delete trigger?").setMessage(cardLabel+" rule will be removed. Managed apps remain under ownership and can be frozen normally.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{Set<String> ts=Prefs.set(this,Prefs.TRIGGER);ts.remove(pkg);Prefs.putSet(this,Prefs.TRIGGER,ts);Prefs.get(this).edit().remove("trigger_rule:"+pkg).apply();renderTriggerCards(parent);refreshCounts();}).show());
        }
    }
    private void launchThroughGate(String triggerPkg,String label){
        if(!ShizukuShell.ready()){toast("Connect Shizuku first");return;}
        Intent gate=new Intent(this,ShortcutLaunchActivity.class);
        gate.putExtra("smartfreeze.launch_gate.trigger",triggerPkg);
        gate.putExtra("smartfreeze.launch_gate.label",label);
        startActivity(gate);
    }
    private void pinTriggerShortcut(String triggerPkg,String label){
        if(Build.VERSION.SDK_INT<26){toast("Home screen shortcuts require Android 8 or newer");return;}
        try{
            ShortcutManager manager=getSystemService(ShortcutManager.class);
            if(manager==null||!manager.isRequestPinShortcutSupported()){
                toast("Your launcher does not support pinned shortcuts");return;
            }
            ApplicationInfo app=getPackageManager().getApplicationInfo(triggerPkg,PackageManager.MATCH_DISABLED_COMPONENTS);
            android.graphics.drawable.Drawable drawable=getPackageManager().getApplicationIcon(app);
            int size=Math.max(96,(int)(48*getResources().getDisplayMetrics().density));
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
            drawable.setBounds(0,0,size,size);drawable.draw(canvas);
            Intent gate=new Intent(this,ShortcutLaunchActivity.class);
            gate.setAction(Intent.ACTION_VIEW);
            gate.putExtra("smartfreeze.launch_gate.trigger",triggerPkg);
            gate.putExtra("smartfreeze.launch_gate.label",label);
            gate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String shortLabel=label.length()>25?label.substring(0,25):label;
            ShortcutInfo info=new ShortcutInfo.Builder(this,"sf_trigger_"+triggerPkg)
                .setShortLabel(shortLabel)
                .setLongLabel(label+" · Smart Freeze Launch")
                .setIcon(Icon.createWithBitmap(bitmap))
                .setIntent(gate).build();
            if(manager.requestPinShortcut(info,null)) toast("Shortcut request sent to Home Screen");
            else toast("Shortcut could not be added");
        }catch(Throwable t){android.util.Log.e("SmartFreeze","Trigger shortcut failed: "+triggerPkg,t);toast("Shortcut creation failed: "+t.getMessage());}
    }
    private void showTriggerWizard(){
        showPackagePicker("Choose Trigger App",false,selected->{
            if(selected==null)return;
            String label=appLabel(selected);
            showDependencyPicker(selected,label);
        });
    }
    private String appLabel(String pkg){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS)).toString();}catch(Throwable t){return pkg;}}
    private interface PackageSelection { void done(String pkg); }
    private void showDependencyPicker(String triggerPkg,String triggerLabel){
        showPackageMultiPicker("Choose apps to unfreeze when "+triggerLabel+" opens",Prefs.set(this,"trigger_rule:"+triggerPkg),selected->{
            if(selected==null)return;
            Set<String> triggers=Prefs.set(this,Prefs.TRIGGER); triggers.add(triggerPkg); Prefs.putSet(this,Prefs.TRIGGER,triggers);
            Prefs.putSet(this,"trigger_rule:"+triggerPkg,selected);
            Prefs.get(this).edit().putLong("trigger_created_at:"+triggerPkg,System.currentTimeMillis()).apply();
            Prefs.logTrigger(this,triggerPkg,"Trigger rule saved. This save action does not launch or unfreeze the trigger app.");
            Prefs.logTrigger(this,triggerPkg,"Dependencies selected: "+selected.size());
            // Dependencies are managed by this trigger rule. The trigger package
            // itself is never implicitly added or removed from any other Freeze List.
            Set<String> managed=Prefs.set(this,Prefs.FREEZE); managed.addAll(selected); Prefs.putSet(this,Prefs.FREEZE,managed);
            if(ShizukuShell.ready()){
                new Thread(()->{int ok=0,fail=0; for(String pkg:selected){ boolean frozen=ShizukuShell.freeze(this,pkg); if(frozen){ok++;Prefs.logTrigger(this,triggerPkg,appLabel(pkg)+" frozen during rule setup (verified command accepted)");}else{fail++;Prefs.logTrigger(this,triggerPkg,appLabel(pkg)+" freeze failed during rule setup");} }
                    final String msg="Rule saved for "+triggerLabel+"\nDependencies selected: "+selected.size()+"\nOwnership freeze succeeded: "+ok+"\nFailed: "+fail+"\n\nOpen Logs for details.";
                    runOnUiThread(()->{new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Trigger saved").setMessage(msg).setPositiveButton("OK",null).show(); refreshHome();});
                },"SmartFreeze-RuleSetup").start();
            } else { new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Trigger saved").setMessage("Rule saved. Connect Shizuku, then freeze the selected dependencies so Smart Freeze can safely manage them.").setPositiveButton("OK",null).show(); refreshHome(); }
        });
    }
    private void refreshHome(){ buildUi(); }
    private void showTriggerLogs(String triggerPkg,String triggerLabel){
        String raw=Prefs.get(this).getString("trigger_logs:"+triggerPkg,"");
        if(raw.trim().isEmpty()) raw="Abhi tak is trigger ka koi activity record nahi hai.";
        new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle(triggerLabel+" · Activity").setMessage(raw).setPositiveButton("Close",null).setNeutralButton("Clear",(d,w)->Prefs.get(this).edit().remove("trigger_logs:"+triggerPkg).apply()).show();
    }
    private void showPackagePicker(String title,boolean multi,PackageSelection callback){
        final List<SelectorApp> apps=loadAllPackagesForSearch();
        final PackageManager pm=getPackageManager();
        final android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(14));box.setBackground(round(0xff0b1420,dp(20),0xff20c9e8));
        TextView heading=text(title,21,true,Color.WHITE);heading.setPadding(dp(2),dp(2),0,dp(12));box.addView(heading);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff91a7b8);search.setHint("Search app name or package");search.setTextSize(16);
        search.setPadding(dp(10),dp(8),dp(10),dp(8));
        search.setBackground(round(0xff050b14,dp(12),0xff28566b));
        search.setBackgroundTintList(null);
        search.setHintTextColor(0xff91a7b8);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView count=text("Type to search · "+apps.size()+" apps available",11,false,0xff9db4c4);
        count.setPadding(dp(2),dp(8),0,dp(8));box.addView(count);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
        LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,0,1);box.addView(scroll,scrollLp);
        Button cancel=smallButton("Cancel");cancel.setTextColor(0xff8eeaf5);cancel.setBackground(round(0xff14283a,dp(12),0xff24566b));
        LinearLayout.LayoutParams cancelLp=new LinearLayout.LayoutParams(-1,dp(44));cancelLp.topMargin=dp(10);box.addView(cancel,cancelLp);
        dialog.setContentView(box);dialog.setCancelable(true);
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(-1,-1);}
        cancel.setOnClickListener(v->{dialog.dismiss();callback.done(null);});
        final int[] limit={30};final Runnable[] render={null};
        render[0]=()->{
            rows.removeAllViews();String q=norm(search.getText().toString());int matches=0,shown=0;
            for(SelectorApp a:apps){if(!matches(a,q))continue;matches++;if(shown>=limit[0])continue;shown++;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(8),dp(8));
                row.setBackground(round(0xff101f2d,dp(14),0xff20394b));
                ImageView appIcon=new ImageView(this);appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                try{appIcon.setImageDrawable(pm.getApplicationIcon(a.info));}catch(Throwable ignored){appIcon.setImageResource(android.R.drawable.sym_def_app_icon);}
                LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(42),dp(42));iconLp.rightMargin=dp(11);row.addView(appIcon,iconLp);
                LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
                labels.addView(text(a.label,14,true,Color.WHITE));
                labels.addView(text(a.pkg,9,false,0xff91a7b8));
                row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                Button choose=smallButton("Select");choose.setTextSize(12);choose.setTextColor(Color.WHITE);
                choose.setBackground(round(0xff07516a,dp(12),0xff27d5ed));
                LinearLayout.LayoutParams chooseLp=new LinearLayout.LayoutParams(dp(88),dp(42));row.addView(choose,chooseLp);
                View.OnClickListener pick=v->{dialog.dismiss();callback.done(a.pkg);};choose.setOnClickListener(pick);row.setOnClickListener(pick);
                LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.bottomMargin=dp(7);rows.addView(row,rowLp);
            }
            count.setText(q.isEmpty()?"Type to search · "+apps.size()+" apps available":matches+" matching apps · showing "+Math.min(matches,limit[0]));
            if(matches>limit[0]){Button more=smallButton("Load 30 more");more.setTextColor(0xff8eeaf5);more.setBackground(round(0xff14283a,dp(12),0xff24566b));rows.addView(more,new LinearLayout.LayoutParams(-1,dp(42)));more.setOnClickListener(v->{limit[0]+=30;render[0].run();});}
            if(!q.isEmpty()&&matches==0){TextView empty=text("No matching apps found",13,false,0xff9db4c4);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(28),0,dp(28));rows.addView(empty);}
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){limit[0]=30;render[0].run();}public void afterTextChanged(Editable e){}});
        render[0].run();dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.90),dp(600));}
    }
    private void showPackageMultiPicker(String title,Set<String> initial,java.util.function.Consumer<Set<String>> callback){
        final List<SelectorApp> apps=loadAllPackagesForSearch();
        final Set<String> selected=new HashSet<>(initial==null?Collections.emptySet():initial);
        final PackageManager pm=getPackageManager();
        final android.app.Dialog dialog=new android.app.Dialog(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(14));
        box.setBackground(round(0xff0b1420,dp(20),0xff20c9e8));
        TextView heading=text(title,20,true,Color.WHITE);
        heading.setPadding(dp(2),dp(2),0,dp(12));box.addView(heading);
        EditText search=new EditText(this);search.setSingleLine(true);
        search.setTextColor(Color.WHITE);search.setHintTextColor(0xff91a7b8);
        search.setHint("Search app name or package (play / gms / vending)");
        search.setTextSize(16);search.setPadding(dp(10),dp(8),dp(10),dp(8));
        search.setBackground(round(0xff050b14,dp(12),0xff28566b));
        search.setBackgroundTintList(null);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView count=text("Type to search installed apps · "+apps.size()+" available",11,false,0xff9db4c4);
        count.setPadding(dp(2),dp(8),0,dp(4));box.addView(count);
        TextView chosen=text("",11,false,0xff55dff2);chosen.setPadding(dp(2),dp(4),dp(2),dp(8));box.addView(chosen);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel=smallButton("Cancel");cancel.setTextColor(0xff8eeaf5);cancel.setBackground(round(0xff14283a,dp(12),0xff24566b));
        Button finish=smallButton("Finish");finish.setTextColor(Color.WHITE);finish.setBackground(round(0xff07516a,dp(12),0xff27d5ed));
        LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(0,dp(46),1);actionLp.topMargin=dp(10);
        actions.addView(cancel,actionLp);LinearLayout.LayoutParams finishLp=new LinearLayout.LayoutParams(0,dp(46),1);finishLp.topMargin=dp(10);finishLp.leftMargin=dp(8);actions.addView(finish,finishLp);box.addView(actions);
        dialog.setContentView(box);dialog.setCancelable(true);
        final int[] limit={30};final Runnable[] render={null};
        render[0]=()->{
            rows.removeAllViews();String q=norm(search.getText().toString());int matches=0,shown=0;
            for(SelectorApp a:apps){if(!matches(a,q))continue;matches++;if(shown>=limit[0])continue;shown++;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(8),dp(8));row.setBackground(round(0xff101f2d,dp(14),0xff20394b));
                ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                try{icon.setImageDrawable(pm.getApplicationIcon(a.info));}catch(Throwable ignored){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
                LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(42),dp(42));iconLp.rightMargin=dp(10);row.addView(icon,iconLp);
                LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
                labels.addView(text(a.label,14,true,Color.WHITE));labels.addView(text(a.pkg,9,false,0xff91a7b8));
                row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                CheckBox cb=new CheckBox(this);cb.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff32d8ee,0xff78909f}));cb.setChecked(selected.contains(a.pkg));
                cb.setText(" ");row.addView(cb,new LinearLayout.LayoutParams(dp(42),dp(48)));
                cb.setOnCheckedChangeListener((v,on)->{if(on)selected.add(a.pkg);else selected.remove(a.pkg);updateChosenSummary(chosen,selected);});
                row.setOnClickListener(v->cb.setChecked(!cb.isChecked()));
                LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.bottomMargin=dp(7);rows.addView(row,rowLp);
            }
            count.setText(q.isEmpty()?"Type to search installed apps · "+apps.size()+" available":matches+" matching apps · showing "+Math.min(matches,limit[0]));
            updateChosenSummary(chosen,selected);
            if(!q.isEmpty()&&matches>limit[0]){Button more=smallButton("Load 30 more");more.setTextColor(0xff8eeaf5);more.setBackground(round(0xff14283a,dp(12),0xff24566b));rows.addView(more,new LinearLayout.LayoutParams(-1,dp(42)));more.setOnClickListener(v->{limit[0]+=30;render[0].run();});}
            if(!q.isEmpty()&&matches==0){TextView empty=text("No matching apps found",13,false,0xff9db4c4);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(28),0,dp(28));rows.addView(empty);}
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){limit[0]=30;render[0].run();}public void afterTextChanged(Editable e){}});
        cancel.setOnClickListener(v->{dialog.dismiss();callback.accept(null);});
        finish.setOnClickListener(v->{dialog.dismiss();callback.accept(new HashSet<>(selected));});
        render[0].run();dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.90),dp(600));}
    }
    private void updateChosenSummary(TextView view,Set<String> selected){
        if(selected.isEmpty()){view.setText("Selected apps: none");return;}
        List<String> names=new ArrayList<>(); for(String pkg:selected) names.add(appLabel(pkg)); Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
        view.setText("Selected: "+android.text.TextUtils.join(", ",names));
    }
    private String matchesLabel(String q,List<SelectorApp> apps,int selected){int n=0;for(SelectorApp a:apps)if(matches(a,q))n++;return n+" matching · "+selected+" selected";}
    private List<SelectorApp> loadAllPackagesForSearch(){
        List<SelectorApp> out=new ArrayList<>();PackageManager pm=getPackageManager();
        try{List<ApplicationInfo> installed=pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);for(ApplicationInfo ai:installed){if(ai.packageName.equals(getPackageName())||ai.packageName.equals("android"))continue;String label;try{label=pm.getApplicationLabel(ai).toString();}catch(Throwable t){label=ai.packageName;}out.add(new SelectorApp(ai,label));}}
        catch(Throwable t){Prefs.log(this,"SEARCH INVENTORY FAILED: "+t.getClass().getSimpleName());}
        Collections.sort(out,(a,b)->a.label.compareToIgnoreCase(b.label));return out;
    }
    private String norm(String s){return s==null?"":s.trim().toLowerCase(java.util.Locale.ROOT);}
    private boolean matches(SelectorApp a,String q){if(q.isEmpty())return true;return norm(a.label).contains(q)||norm(a.pkg).contains(q)||norm(a.label.replace(" ","")).contains(q.replace(" ",""));}

    private TextView badge(String label,boolean granted){TextView t=text("",12,true,granted?green:muted);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(7),dp(12),dp(7));t.setBackground(round(granted?0xff103522:0xff252431,dp(18),granted?0xff237a4a:0xff555365));return t;}
    private GradientDrawable round(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);d.setStroke(dp(1),stroke);return d;}
    private boolean shizukuGranted(){try{return Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Throwable t){return false;}}
    private void refreshPermissionBadges(){if(shizukuStatus==null)return;boolean s=shizukuGranted(),u=usageGranted();shizukuStatus.setText((s?"✓ ":"○ ")+"Shizuku "+(s?"Connected":"Not connected"));shizukuStatus.setTextColor(s?green:muted);shizukuStatus.setBackground(round(s?0xff103522:0xff252431,dp(18),s?0xff237a4a:0xff555365));usageStatus.setText((u?"✓ ":"○ ")+"Usage Access "+(u?"Granted":"Not granted"));usageStatus.setTextColor(u?green:muted);usageStatus.setBackground(round(u?0xff103522:0xff252431,dp(18),u?0xff237a4a:0xff555365));}
    private void refreshCounts(){
        refreshLiveStatus();
        if(screenAutoStatus!=null){
            String saved=Prefs.get(this).getString("screen_auto_status", "No lock/unlock event recorded yet");
            screenAutoStatus.setText("Screen automation: "+saved);
            screenAutoStatus.setTextColor(saved.startsWith("PHONE UNLOCK")?0xff35e889:saved.startsWith("SCREEN LOCK")?0xff21d4fd:muted);
        }
    }
    private void startLiveStatusUpdates(){ if(liveStatusRunning)return; liveStatusRunning=true; liveStatusHandler.post(liveStatusTick); }
    private void stopLiveStatusUpdates(){ liveStatusRunning=false; liveStatusHandler.removeCallbacks(liveStatusTick); }
    private final Runnable liveStatusTick=new Runnable(){ public void run(){ if(!liveStatusRunning)return; refreshLiveStatus(); liveStatusHandler.postDelayed(this,1800L); }};
    private void refreshLiveStatus(){
        if(liveStatusRows==null || liveStatusQueryRunning)return;
        liveStatusQueryRunning=true;
        new Thread(()->{
            // Live App Status is for managed dependencies only. Trigger apps are launch
            // targets and must never appear as freeze-status rows.
            java.util.LinkedHashSet<String> packages=new java.util.LinkedHashSet<>();
            for(String trigger:Prefs.set(this,Prefs.TRIGGER)) {
                packages.addAll(Prefs.set(this,"trigger_rule:"+trigger));
            }
            // Do not show trigger packages even if a user accidentally selected one
            // as a dependency in another rule.
            packages.removeAll(Prefs.set(this,Prefs.TRIGGER));
            final java.util.ArrayList<String[]> rows=new java.util.ArrayList<>();
            for(String pkg:packages){
                String label=appLabel(pkg); boolean owned=Prefs.set(this,Prefs.OWNED).contains(pkg);
                String state; int color;
                try {
                    getPackageManager().getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
                    if(ShizukuShell.isActuallyDisabled(this,pkg)){
                        if(owned){state="FROZEN";color=0xffff5b68;}else{state="DISABLED · EXTERNAL";color=0xffffb45c;}
                    } else if(ShizukuShell.isActuallyEnabled(this,pkg)){state="UNFROZEN";color=green;}
                    else {state="UNKNOWN";color=muted;}
                } catch(Throwable missing){state="NOT INSTALLED";color=muted;label="Unavailable app";}
                rows.add(new String[]{label,state,Integer.toString(color)});
            }
            rows.sort((a,b)->a[0].compareToIgnoreCase(b[0]));
            runOnUiThread(()->{
                liveStatusQueryRunning=false;
                if(liveStatusRows==null)return; liveStatusRows.removeAllViews();
                if(rows.isEmpty()){TextView empty=text("No trigger dependencies selected yet",11,false,muted);liveStatusRows.addView(empty);return;}
                for(String[] item:rows){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(5),0,dp(5));TextView name=text(item[0],12,true,Color.WHITE);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView badge=text(item[1],10,true,Integer.parseInt(item[2]));badge.setGravity(Gravity.CENTER);badge.setPadding(dp(8),dp(5),dp(8),dp(5));badge.setBackground(round(panel,dp(12),Integer.parseInt(item[2])));line.addView(badge);liveStatusRows.addView(line);View divider=new View(this);divider.setBackgroundColor(0xff292834);liveStatusRows.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
            });
        },"SmartFreeze-LiveStatus").start();
    }
    private String modeLabel(){int m=Prefs.get(this).getInt(Prefs.MODE,3);if(m==0)return "Freeze immediately";
        if(m==2)return "Manual freeze";
        if(m==3)return "Screen lock freezes all; unlock stays frozen; triggers unfreeze their own rules";
        long value=Prefs.get(this).getLong("freeze_delay_value",30L);
        int unit=Prefs.get(this).getInt("freeze_delay_unit",0);
        return "Freeze after "+value+" "+new String[]{"seconds","minutes","hours"}[Math.max(0,Math.min(2,unit))];}
    private void connectShizuku(){try{if(!Shizuku.pingBinder()){toast("Start Shizuku service first");refreshPermissionBadges();return;}if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){toast("Shizuku already connected");refreshPermissionBadges();return;}Shizuku.requestPermission(REQ_SHIZUKU);toast("Approve the Shizuku permission prompt");}catch(Throwable t){toast("Shizuku unavailable: "+t.getClass().getSimpleName());}refreshPermissionBadges();}
    private boolean usageGranted(){try{AppOpsManager a=(AppOpsManager)getSystemService(APP_OPS_SERVICE);int m=Build.VERSION.SDK_INT>=29?a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName()):a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName());return m==AppOpsManager.MODE_ALLOWED;}catch(Exception e){return false;}}
    private void startMonitoring(){if(!shizukuGranted()){toast("Connect Shizuku first");return;}if(!usageGranted()){toast("Grant Usage Access first");startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8103);Prefs.get(this).edit().putBoolean(Prefs.ACTIVE,true).apply();Intent i=new Intent(this,MonitorService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);refreshCounts();refreshStartStopButton();toast("Monitoring started");}
    private void stopMonitoring(){Prefs.get(this).edit().putBoolean(Prefs.ACTIVE,false).apply();stopService(new Intent(this,MonitorService.class));FreezeEngine.restoreNow(this);refreshCounts();refreshStartStopButton();toast("Monitoring stopped; managed apps freeze requested");}
    private void toggleMonitoring(){if(MonitorService.isRunning())stopMonitoring();else startMonitoring();refreshStartStopButton();}
    private void refreshStartStopButton(){
        if(startStopButton==null)return;
        boolean running=MonitorService.isRunning();
        startStopButton.setText(running?"Stop":"Start");
        // High-contrast state colors: red means Start is available; green means Stop is available.
        int fill=running?0xff145c3b:0xff7a2028;
        int stroke=running?0xff37e889:0xffff626d;
        startStopButton.setTextColor(Color.WHITE);
        startStopButton.animate().cancel();
        startStopButton.setBackgroundTintList(null);
        startStopButton.setBackground(round(fill,dp(12),stroke));
        startStopButton.setBackgroundTintList(null);
        startStopButton.setScaleX(.97f); startStopButton.setScaleY(.97f);
        startStopButton.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
    }
    private void pickMode(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(12),dp(20),dp(4));
        RadioGroup modes=new RadioGroup(this); modes.setOrientation(RadioGroup.VERTICAL);
        String[] labels={"Freeze immediately","Freeze after custom time","Manual only","Screen lock: freeze all; unlock: stay frozen (on-demand triggers)"};
        RadioButton[] rb=new RadioButton[4];
        int current=Prefs.get(this).getInt(Prefs.MODE,3);
        for(int i=0;i<4;i++){rb[i]=new RadioButton(this);rb[i].setText(labels[i]);rb[i].setTextColor(Color.WHITE);rb[i].setId(100+i);modes.addView(rb[i]);}
        rb[Math.max(0,Math.min(3,current))].setChecked(true); box.addView(modes);
        EditText amount=new EditText(this);amount.setSingleLine(true);amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amount.setHint("Duration (e.g. 30)");amount.setText(String.valueOf(Prefs.get(this).getLong("freeze_delay_value",30L)));
        box.addView(amount,new LinearLayout.LayoutParams(-1,dp(48)));
        Spinner unit=new Spinner(this);unit.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Seconds","Minutes","Hours"}));
        unit.setSelection(Prefs.get(this).getInt("freeze_delay_unit",0));box.addView(unit);
        TextView graceLabel=text("Resettable Grace Timer (minutes)",12,true,cyan);box.addView(graceLabel);
        EditText graceMinutes=new EditText(this);graceMinutes.setSingleLine(true);graceMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        graceMinutes.setHint("Enter any number of minutes");
        graceMinutes.setText(String.valueOf(Prefs.get(this).getLong("background_grace_minutes",5L)));
        box.addView(graceMinutes,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView graceNote=text("When a trigger goes to Home or another app, this countdown starts. If you return to the trigger before it expires, it is cancelled and reset to the full duration. Applies to all automatic modes; Manual mode waits for you.",10,false,muted);box.addView(graceNote);
        TextView displayLabel=text("Countdown Display",12,true,cyan);box.addView(displayLabel);
        Spinner displayMode=new Spinner(this);
        displayMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"In-App (inside Smart Freeze)","Floating (over other apps)"}));
        displayMode.setSelection(Prefs.get(this).getInt("grace_display_mode",0));
        box.addView(displayMode,new LinearLayout.LayoutParams(-1,dp(48)));
        modes.setOnCheckedChangeListener((g,id)->{boolean custom=id==101;amount.setEnabled(custom);unit.setEnabled(custom);});
        amount.setEnabled(current==1);unit.setEnabled(current==1);
        new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Automation Mode").setView(box).setPositiveButton("Apply",(d,w)->{
            int checked=modes.getCheckedRadioButtonId();int mode=checked==100?0:checked==102?2:checked==103?3:1;
            long val=30;try{val=Long.parseLong(amount.getText().toString().trim());}catch(Exception ignored){}
            val=Math.max(1,Math.min(86400,val));
            long grace=5;try{grace=Long.parseLong(graceMinutes.getText().toString().trim());}catch(Exception ignored){}
            grace=Math.max(1,Math.min(1440,grace));
            int display=displayMode.getSelectedItemPosition();
            if(display==1 && Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) {
                Intent overlay=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
                startActivity(overlay);
            }
            Prefs.get(this).edit().putInt(Prefs.MODE,mode).putLong("freeze_delay_value",val).putInt("freeze_delay_unit",unit.getSelectedItemPosition()).putLong("background_grace_minutes",grace).putInt("grace_display_mode",display).apply();
            toast("Mode, grace timer and countdown display saved");
            if(mode==3 && !Prefs.get(this).getBoolean(Prefs.ACTIVE,false)) toast("Now press Start; lock/unlock detection runs while monitoring is active.");
        }).setNegativeButton("Cancel",null).show();
    }

    private void showTriggerRulePicker(){
        List<String> triggers=new ArrayList<>(Prefs.set(this,Prefs.TRIGGER));
        if(triggers.isEmpty()){toast("Select Trigger apps first");return;}
        Collections.sort(triggers);
        PackageManager pm=getPackageManager(); String[] labels=new String[triggers.size()];
        for(int i=0;i<triggers.size();i++){String p=triggers.get(i);try{labels[i]=pm.getApplicationLabel(pm.getApplicationInfo(p,PackageManager.MATCH_DISABLED_COMPONENTS))+"\n"+p;}catch(Throwable t){labels[i]=p;}}
        new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Choose a trigger to configure")
            .setItems(labels,(d,which)->{String pkg=triggers.get(which);String label=labels[which].split("\\n")[0];showAppSelector("trigger_rule:"+pkg,"Dependencies for "+label);})
            .setNegativeButton("Cancel",null).show();
    }

    private void buildDualPanels(){
        dualPanel=new SwipePanelLayout(this);dualPanel.setOrientation(LinearLayout.VERTICAL);dualPanel.setPadding(0,dp(8),0,dp(14));
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        freezePanelTab=text("Freeze List  ·  "+Prefs.set(this,Prefs.FREEZE).size(),14,true,cyan);
        triggerPanelTab=text("Trigger List  ·  "+Prefs.set(this,Prefs.TRIGGER).size(),14,true,muted);
        freezePanelTab.setGravity(Gravity.CENTER);triggerPanelTab.setGravity(Gravity.CENTER);
        freezePanelTab.setPadding(dp(8),dp(14),dp(8),dp(14));triggerPanelTab.setPadding(dp(8),dp(14),dp(8),dp(14));
        tabs.addView(freezePanelTab,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(50),1);tp.leftMargin=dp(8);tabs.addView(triggerPanelTab,tp);
        dualPanel.addView(tabs);
        TextView hint=text("Swipe right-to-left for Trigger List  ·  Tap a panel to edit",10,false,muted);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(6),0,dp(8));dualPanel.addView(hint);
        freezePanelBody=new LinearLayout(this);freezePanelBody.setOrientation(LinearLayout.VERTICAL);
        triggerPanelBody=new LinearLayout(this);triggerPanelBody.setOrientation(LinearLayout.VERTICAL);
        dualPanel.addView(freezePanelBody);dualPanel.addView(triggerPanelBody);
        refreshDualPanels(true);
        freezePanelTab.setOnClickListener(v->switchDualPanel(true,true));
        triggerPanelTab.setOnClickListener(v->switchDualPanel(false,true));
        dualPanel.setClickable(true);
        root.addView(dualPanel);
    }
    private void refreshDualPanels(boolean freeze){
        if(freezePanelBody==null)return;
        fillPanelSummary(freezePanelBody,Prefs.FREEZE);
        fillPanelSummary(triggerPanelBody,Prefs.TRIGGER);
        switchDualPanel(freeze,false);
    }
    private void switchDualPanel(boolean freeze, boolean animate){
        if(freezePanelBody==null)return;
        if(showingFreezePanel==freeze && freezePanelBody.getVisibility()==View.VISIBLE)return;
        final View outgoing=showingFreezePanel?freezePanelBody:triggerPanelBody;
        final View incoming=freeze?freezePanelBody:triggerPanelBody;
        showingFreezePanel=freeze;
        freezePanelTab.setTextColor(freeze?cyan:muted);
        triggerPanelTab.setTextColor(freeze?muted:cyan);
        freezePanelTab.setBackground(round(panel,dp(12),freeze?cyan:0xff343344));
        triggerPanelTab.setBackground(round(panel,dp(12),freeze?0xff343344:cyan));
        freezePanelTab.setText("Freeze List  ·  "+Prefs.set(this,Prefs.FREEZE).size());
        triggerPanelTab.setText("Trigger List  ·  "+Prefs.set(this,Prefs.TRIGGER).size());
        incoming.setVisibility(View.VISIBLE);
        outgoing.setVisibility(View.GONE);
        if(animate){
            float direction=freeze? -1f:1f;
            incoming.setAlpha(0f);incoming.setTranslationX(direction*dp(28));
            incoming.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            incoming.setAlpha(1f);incoming.setTranslationX(0f);
        }
    }
    private final class SwipePanelLayout extends LinearLayout {
        private float downX,downY,lastX;
        private final int slop;
        SwipePanelLayout(android.content.Context c){
            super(c);
            slop=android.view.ViewConfiguration.get(c).getScaledTouchSlop();
            setOrientation(LinearLayout.VERTICAL);
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();lastX=downX;return false;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dx=e.getX()-downX,dy=e.getY()-downY;
                if(Math.abs(dx)>slop*1.5f && Math.abs(dx)>Math.abs(dy)*1.25f)return true;
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dx=e.getX()-lastX;lastX=e.getX();
                View active=showingFreezePanel?freezePanelBody:triggerPanelBody;
                if(active!=null)active.setTranslationX(Math.max(-dp(60),Math.min(dp(60),active.getTranslationX()+dx*0.35f)));
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                float dx=e.getX()-downX;
                View active=showingFreezePanel?freezePanelBody:triggerPanelBody;
                if(active!=null){active.animate().translationX(0f).setDuration(120).start();}
                if(dx < -dp(45))switchDualPanel(false,true);
                else if(dx > dp(45))switchDualPanel(true,true);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL){
                View active=showingFreezePanel?freezePanelBody:triggerPanelBody;
                if(active!=null)active.animate().translationX(0f).setDuration(120).start();
                return true;
            }
            return true;
        }
    }
    private void fillPanelSummary(LinearLayout parent,String key){
        // Rebuild this panel from the current preference set; never append onto old rows.
        parent.removeAllViews();
        Set<String> selected=Prefs.set(this,key);
        if(selected.isEmpty()){TextView empty=text("No apps selected",12,false,muted);empty.setPadding(dp(8),dp(10),dp(8),dp(10));parent.addView(empty);return;}
        int shown=0;
        for(String pkg:selected){
            if(shown++>=8)break;
            String label=pkg;
            try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS)).toString();}catch(Throwable ignored){}
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(6),dp(10),dp(6));
            TextView name=text("•  "+label,12,false,Color.WHITE);row.addView(name,new LinearLayout.LayoutParams(0,dp(34),1));
            String state; int stateColor;
            if(Prefs.FREEZE.equals(key)){
                boolean managed=Prefs.set(this,Prefs.OWNED).contains(pkg);
                boolean disabled=ShizukuShell.isActuallyDisabled(this,pkg);
                if(managed && disabled){state="✓ FROZEN";stateColor=0xff35d98b;}
                else {state="✗ NOT FROZEN";stateColor=0xffff5252;}
            } else { state="TRIGGER"; stateColor=cyan; }
            TextView badge=text(state,10,true,stateColor);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(7),dp(5),dp(7),dp(5));badge.setBackground(round(panel,dp(8),stateColor));
            row.addView(badge,new LinearLayout.LayoutParams(dp(112),dp(32)));
            parent.addView(row);
        }
        if(selected.size()>8)parent.addView(text("+"+(selected.size()-8)+" more",11,false,muted));
    }

    private JSONArray readInventory(){
        String raw=Prefs.get(this).getString(APP_CACHE_KEY,null);
        if(raw!=null){try{return new JSONArray(raw);}catch(Exception ignored){}}
        // First run only: scan once, persist, then selector reads the saved inventory.
        refreshAppInventory(null);
        raw=Prefs.get(this).getString(APP_CACHE_KEY,"[]");
        try{return new JSONArray(raw);}catch(Exception e){return new JSONArray();}
    }
    private int cachedInventoryCount(){
        try{return new JSONArray(Prefs.get(this).getString(APP_CACHE_KEY,"[]")).length();}catch(Exception e){return 0;}
    }
    private void refreshAppInventory(Button button){ refreshAppInventory(button,null); }
    private void refreshAppInventory(Button button,Runnable afterRefresh){
        if(button!=null){button.setEnabled(false);button.setText("Refreshing…");}
        new Thread(()->{
            JSONArray data=new JSONArray();
            try{
                PackageManager pm=getPackageManager();
                List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
                for(ApplicationInfo ai:apps){
                    JSONObject o=new JSONObject();o.put("pkg",ai.packageName);
                    String label;try{label=pm.getApplicationLabel(ai).toString();}catch(Throwable e){label=ai.packageName;}
                    o.put("label",label);o.put("system",(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0);data.put(o);
                }
                Prefs.get(this).edit().putString(APP_CACHE_KEY,data.toString()).putLong("app_inventory_updated",System.currentTimeMillis()).apply();
            }catch(Throwable e){Prefs.log(this,"APP INVENTORY REFRESH FAILED: "+e.getClass().getSimpleName());}
            runOnUiThread(()->{
                if(inventoryStatus!=null)inventoryStatus.setText("App inventory: "+cachedInventoryCount()+" apps · updated");
                if(button!=null){button.setEnabled(true);button.setText("↻ Refresh Apps");}
                toast("App list updated: "+cachedInventoryCount()+" apps");
                if(afterRefresh!=null)afterRefresh.run();
            });
        },"SmartFreeze-AppInventory").start();
    }

    private static class AppRow { ApplicationInfo info; String label,pkg; boolean system; AppRow(ApplicationInfo i,String l){info=i;label=l;pkg=i.packageName;system=(i.flags&ApplicationInfo.FLAG_SYSTEM)!=0;} }
    private String appState(String pkg, Set<String> owned) {
        try {
            PackageManager pm=getPackageManager();
            boolean suspended=Build.VERSION.SDK_INT >= 24 && pm.isPackageSuspended(pkg);
            int state=pm.getApplicationEnabledSetting(pkg);
            ApplicationInfo ai=pm.getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
            boolean disabled=suspended || state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED || !ai.enabled;
            if(suspended) return "SUSPENDED";
            if(disabled) return owned.contains(pkg)?"MANAGED FREEZE":"FROZEN · EXTERNAL";
            return owned.contains(pkg)?"MANAGED RECORD · ENABLED":"ENABLED";
        } catch(Throwable ignored) { return "UNKNOWN"; }
    }

    private boolean isExternallyFrozen(PackageManager pm,String pkg,ApplicationInfo ai) {
        try {
            int state=pm.getApplicationEnabledSetting(pkg);
            boolean disabled=!ai.enabled ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
            if(Build.VERSION.SDK_INT>=24 && pm.isPackageSuspended(pkg)) disabled=true;
            return disabled;
        } catch(Throwable ignored) { return !ai.enabled; }
    }

    private static class SelectorApp {
        final ApplicationInfo info; final String label,pkg; final boolean system;
        String state;
        SelectorApp(ApplicationInfo i,String l){info=i;label=l;pkg=i.packageName;system=(i.flags&ApplicationInfo.FLAG_SYSTEM)!=0;}
    }

    private void showAppSelector(String key,String pageTitle){
        currentSelectorKey=key;
        if(selectorSection!=null){root.removeView(selectorSection);selectorSection=null;}
        final PackageManager pm=getPackageManager();
        final Set<String> checked=new HashSet<>(Prefs.set(this,key));
        final Set<String> owned=Prefs.set(this,Prefs.OWNED);
        final List<SelectorApp> all=new ArrayList<>();
        // Read the saved inventory and make one PackageManager snapshot, instead of
        // calling getApplicationInfo separately for every package in the saved list.
        JSONArray inventory=readInventory();
        final Map<String,ApplicationInfo> installedByPackage=new HashMap<>();
        try{for(ApplicationInfo item:pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS))installedByPackage.put(item.packageName,item);}catch(Throwable ignored){}
        for(int n=0;n<inventory.length();n++){
            try{
                JSONObject obj=inventory.getJSONObject(n);String pkg=obj.getString("pkg");
                if(pkg.equals(getPackageName())||pkg.equals("android")||pkg.equals("com.android.systemui")||pkg.equals("com.android.settings")||pkg.toLowerCase().contains("shizuku"))continue;
                if(key.equals(Prefs.FREEZE)&&isCritical(pkg))continue;
                ApplicationInfo ai=installedByPackage.get(pkg);
                if(ai==null)continue; // App was uninstalled since the last inventory refresh.
                if(key.equals(Prefs.FREEZE) && !owned.contains(pkg) && isExternallyFrozen(pm,pkg,ai)) {
                    continue; // Never list packages frozen outside Smart Freeze.
                }
                all.add(new SelectorApp(ai,obj.optString("label",pkg)));
            }catch(Throwable ignored){}
        }
        Set<String> activePackages = key.equals(Prefs.FREEZE) ? recentlyActivePackages() : Collections.emptySet();
        Collections.sort(all,(a,b)->{
            boolean aa=activePackages.contains(a.pkg), bb=activePackages.contains(b.pkg);
            if(aa!=bb) return aa?-1:1;
            return a.label.compareToIgnoreCase(b.label);
        });

        selectorSection=new LinearLayout(this);selectorSection.setOrientation(LinearLayout.VERTICAL);
        selectorSection.setPadding(0,dp(14),0,dp(18)); selectorSection.setBackgroundColor(bg);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView heading=text(pageTitle,21,true,Color.WHITE);titleRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button refreshApps=smallButton("↻ Refresh");refreshApps.setTextColor(cyan);refreshApps.setBackground(round(0xff102a36,dp(12),0xff1b829a));
        titleRow.addView(refreshApps,new LinearLayout.LayoutParams(-2,dp(40)));selectorSection.addView(titleRow);
        TextView intro=text("Choose the apps you want to manage. Your selections are saved automatically.",11,false,muted);
        intro.setPadding(0,dp(5),0,dp(12));selectorSection.addView(intro);
        LinearLayout summary=new LinearLayout(this);summary.setGravity(Gravity.CENTER_VERTICAL);summary.setPadding(dp(12),dp(10),dp(12),dp(10));summary.setBackground(round(0xff102632,dp(14),0xff17647a));
        TextView summaryIcon=text("✦",19,true,cyan);summary.addView(summaryIcon);
        TextView summaryText=text(cachedInventoryCount()+" apps available  ·  " + checked.size()+" selected",12,true,Color.WHITE);
        LinearLayout.LayoutParams smp=new LinearLayout.LayoutParams(0,-2,1);smp.leftMargin=dp(10);summary.addView(summaryText,smp);selectorSection.addView(summary);
        TextView selectedHeading=text("YOUR SELECTED APPS",11,true,cyan);selectedHeading.setPadding(0,dp(16),0,dp(7));selectorSection.addView(selectedHeading);
        LinearLayout selectedRows=new LinearLayout(this);selectedRows.setOrientation(LinearLayout.VERTICAL);selectorSection.addView(selectedRows);
        if(key.equals(Prefs.FREEZE) && !activePackages.isEmpty()){
            TextView activeHeading=text("RECENTLY USED",11,true,0xffd5a6ff);
            activeHeading.setPadding(0,dp(12),0,dp(5));selectorSection.addView(activeHeading);
        }
        TextView allHeading=text("Find an app",17,true,Color.WHITE);allHeading.setPadding(0,dp(16),0,dp(7));selectorSection.addView(allHeading);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(Color.WHITE);search.setHintTextColor(0xff9aa8b8);
        search.setHint("⌕   Search by app name or package");search.setPadding(dp(16),0,dp(16),0);search.setBackground(round(0xff111e2b,dp(14),0xff28627a));
        selectorSection.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        refreshApps.setOnClickListener(v->{refreshAppInventory(refreshApps,()->showAppSelector(key,pageTitle));});
        TextView count=text("",11,false,muted);count.setPadding(0,dp(6),0,dp(6));selectorSection.addView(count);
        ScrollView listScroll=new ScrollView(this);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.setNestedScrollingEnabled(true);
        listScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        listScroll.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN ||
               event.getAction()==android.view.MotionEvent.ACTION_MOVE){
                ViewParentTools.disallowParentIntercept(v,true);
            } else if(event.getAction()==android.view.MotionEvent.ACTION_UP ||
                      event.getAction()==android.view.MotionEvent.ACTION_CANCEL){
                ViewParentTools.disallowParentIntercept(v,false);
            }
            return false;
        });
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);listScroll.addView(rows);
        selectorSection.addView(listScroll,new LinearLayout.LayoutParams(-1,dp(330)));
        TextView selectedLabel=text("",12,true,cyan);selectedLabel.setPadding(0,dp(8),0,dp(4));selectorSection.addView(selectedLabel);
        Button close=new Button(this);close.setText("Done  ✓");close.setAllCaps(false);close.setTextSize(13);close.setTextColor(Color.WHITE);close.setMinHeight(dp(42));polishButton(close,0xff17465a,cyan);LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(-1,dp(44));closeLp.topMargin=dp(10);selectorSection.addView(close,closeLp);
        root.addView(selectorSection);

        final int pageSize=20;
        final int[] shown={0};
        final android.os.Handler ui=new android.os.Handler(getMainLooper());
        final Runnable[] pending={null};
        final java.util.Map<String,android.graphics.drawable.Drawable> iconCache=new java.util.LinkedHashMap<String,android.graphics.drawable.Drawable>(96,0.75f,true){ protected boolean removeEldestEntry(java.util.Map.Entry<String,android.graphics.drawable.Drawable> e){ return size()>80; }};
        final Runnable[] refreshSelected={null};
        Runnable updateSelected=()->{
            selectedRows.removeAllViews();
            for(SelectorApp app:all) if(checked.contains(app.pkg)){
                addSelectorRow(selectedRows,app,checked,owned,pm,iconCache,true,refreshSelected[0]);
            }
            if(selectedRows.getChildCount()==0){TextView none=text("No apps selected",13,false,muted);none.setPadding(0,dp(4),0,dp(6));selectedRows.addView(none);}
            selectedLabel.setText(checked.size()+" selected");
            count.setText(countText(all,search.getText().toString(),checked.size(),shown[0]));
        };
        refreshSelected[0]=updateSelected;
        Runnable render=()->{
            rows.removeAllViews();shown[0]=0;
            renderSelectorRows(rows,all,search.getText().toString(),checked,owned,pm,iconCache,shown,pageSize,count,refreshSelected[0]);
            updateSelected.run();
        };
        listScroll.setOnScrollChangeListener((View v,int sx,int sy,int osx,int osy)->{
            View child=listScroll.getChildAt(0);
            if(child!=null && sy+listScroll.getHeight()>=child.getHeight()-dp(80)){
                int before=shown[0];
                renderSelectorRows(rows,all,search.getText().toString(),checked,owned,pm,iconCache,shown,20,count,refreshSelected[0]);
                if(shown[0]!=before) updateSelected.run();
            }
        });
        close.setOnClickListener(v->{root.removeView(selectorSection);selectorSection=null;refreshCounts();refreshDualPanels(showingFreezePanel);});
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence q,int st,int before,int c){
                if(pending[0]!=null)ui.removeCallbacks(pending[0]);
                pending[0]=()->render.run();ui.postDelayed(pending[0],180);
            }
            public void afterTextChanged(Editable e){}
        });
        render.run();
    }

    private Set<String> recentlyActivePackages(){
        Set<String> out=new HashSet<>();
        if(!usageGranted())return out;
        try{
            UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
            long now=System.currentTimeMillis(); UsageEvents events=usm.queryEvents(now-30L*60L*1000L,now);
            UsageEvents.Event e=new UsageEvents.Event(); long cutoff=now-10L*60L*1000L;
            java.util.Map<String,Long> latest=new java.util.HashMap<>();
            while(events!=null&&events.hasNextEvent()){events.getNextEvent(e);if((e.getEventType()==UsageEvents.Event.MOVE_TO_FOREGROUND||e.getEventType()==UsageEvents.Event.ACTIVITY_RESUMED)&&e.getTimeStamp()>=cutoff)latest.put(e.getPackageName(),e.getTimeStamp());}
            out.addAll(latest.keySet());
            java.util.List<String> sorted=new java.util.ArrayList<>(out);java.util.Collections.sort(sorted,(a,b)->Long.compare(latest.get(b),latest.get(a)));
            out.clear();for(int i=0;i<Math.min(8,sorted.size());i++)out.add(sorted.get(i));
        }catch(Throwable ignored){} return out;
    }
    private void showShortcutPicker(){
        Set<String> selected=Prefs.set(this,Prefs.FREEZE);if(selected.isEmpty()){toast("Freeze List is empty");return;}
        List<String> pkgs=new ArrayList<>(selected);Collections.sort(pkgs);String[] labels=new String[pkgs.size()];boolean[] checked=new boolean[pkgs.size()];
        PackageManager pm=getPackageManager();for(int i=0;i<pkgs.size();i++){try{labels[i]=pm.getApplicationLabel(pm.getApplicationInfo(pkgs.get(i),PackageManager.MATCH_DISABLED_COMPONENTS)).toString();}catch(Throwable t){labels[i]=pkgs.get(i);}checked[i]=true;}
        new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Home Screen Shortcuts").setMultiChoiceItems(labels,checked,(d,w,v)->checked[w]=v).setNegativeButton("Not now",null).setPositiveButton("Add selected",(d,w)->{
            JSONArray queue=new JSONArray();for(int i=0;i<pkgs.size();i++)if(checked[i]){JSONObject item=new JSONObject();try{item.put("pkg",pkgs.get(i));item.put("label",labels[i]);queue.put(item);}catch(Exception ignored){}}
            Prefs.get(this).edit().putString("shortcut_queue",queue.toString()).putInt("shortcut_index",0).apply();sendNextShortcut();
        }).show();
    }
    private void sendNextShortcut(){
        if(shortcutTimeout!=null)shortcutHandler.removeCallbacks(shortcutTimeout);
        try{
            JSONArray queue=new JSONArray(Prefs.get(this).getString("shortcut_queue","[]"));int index=Prefs.get(this).getInt("shortcut_index",0);
            if(index>=queue.length()){Prefs.get(this).edit().remove("shortcut_queue").remove("shortcut_index").apply();toast("Shortcut requests completed");return;}
            JSONObject item=queue.getJSONObject(index);String pkg=item.getString("pkg"),label=item.getString("label");
            boolean accepted=pinShortcut(pkg,label,index);
            if(!accepted){Prefs.get(this).edit().putInt("shortcut_index",index+1).apply();shortcutHandler.postDelayed(this::sendNextShortcut,350);return;}
            shortcutTimeout=()->{Prefs.get(this).edit().putInt("shortcut_index",index+1).apply();sendNextShortcut();};
            shortcutHandler.postDelayed(shortcutTimeout,20000);
        }catch(Throwable t){Prefs.get(this).edit().remove("shortcut_queue").remove("shortcut_index").apply();toast("Shortcut creation skipped; Freeze is unaffected");}
    }
    private boolean pinShortcut(String pkg,String label,int index){
        if(Build.VERSION.SDK_INT<26)return false;
        try{
            ShortcutManager sm=getSystemService(ShortcutManager.class);if(sm==null||!sm.isRequestPinShortcutSupported())return false;
            ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
            android.graphics.drawable.Drawable drawable=getPackageManager().getApplicationIcon(ai);
            int size=getResources().getDisplayMetrics().densityDpi>0?Math.max(96,(int)(48*getResources().getDisplayMetrics().density)):144;
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);drawable.setBounds(0,0,size,size);drawable.draw(canvas);
            Icon icon=Icon.createWithBitmap(bitmap);
            Intent launch=new Intent(this,ShortcutLaunchActivity.class);launch.setAction(Intent.ACTION_VIEW);launch.putExtra(EXTRA_SHORTCUT_PACKAGE,pkg);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String shortLabel=label.length()>25?label.substring(0,25):label;
            ShortcutInfo info=new ShortcutInfo.Builder(this,"sf_v11_"+pkg).setShortLabel(shortLabel).setLongLabel(label+" · Unfreeze & Open").setIcon(icon).setIntent(launch).build();
            Intent callback=new Intent(this,ShortcutCallbackReceiver.class);callback.setAction("com.golu.smartfreeze.SHORTCUT_PINNED");callback.putExtra("shortcut_index",index);
            android.app.PendingIntent pi=android.app.PendingIntent.getBroadcast(this,10000+index,callback,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
            return sm.requestPinShortcut(info,pi.getIntentSender());
        }catch(Throwable t){android.util.Log.e("SmartFreeze","Shortcut failed: "+pkg,t);return false;}
    }
    private void advanceShortcutQueue(Intent intent){
        if(shortcutTimeout!=null)shortcutHandler.removeCallbacks(shortcutTimeout);
        int callbackIndex=intent.getIntExtra("shortcut_index",-1);
        int current=Prefs.get(this).getInt("shortcut_index",0);
        if(callbackIndex>=0&&callbackIndex==current)Prefs.get(this).edit().putInt("shortcut_index",current+1).apply();
        sendNextShortcut();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent!=null&&intent.getBooleanExtra("shortcut_next",false))advanceShortcutQueue(intent);}

    private String countText(List<SelectorApp> all,String query,int selected,int shown){
        String q=query==null?"":query.trim().toLowerCase();int matches=0;
        for(SelectorApp a:all)if(q.isEmpty()||a.label.toLowerCase().contains(q)||a.pkg.toLowerCase().contains(q))matches++;
        return matches+" matches · showing "+shown+" · "+selected+" selected";
    }

    private void renderSelectorRows(LinearLayout rows,List<SelectorApp> all,String query,Set<String> checked,Set<String> owned,
            PackageManager pm,java.util.Map<String,android.graphics.drawable.Drawable> iconCache,int[] shown,int pageSize,
            TextView count,Runnable selectedRefresh){
        String q=query==null?"":query.trim().toLowerCase();int added=0;
        for(SelectorApp app:all){
            if(!q.isEmpty()&&!app.label.toLowerCase().contains(q)&&!app.pkg.toLowerCase().contains(q))continue;
            if(added<shown[0]){added++;continue;}
            if(added>=shown[0]+pageSize)break;
            addSelectorRow(rows,app,checked,owned,pm,iconCache,false,selectedRefresh);
            added++;
        }
        shown[0]=Math.min(added,shown[0]+pageSize);
        int matches=0;for(SelectorApp a:all)if(q.isEmpty()||a.label.toLowerCase().contains(q)||a.pkg.toLowerCase().contains(q))matches++;
        count.setText(matches+" matches · showing "+shown[0]+" · "+checked.size()+" selected");
    }

    private String iconCacheName(SelectorApp app){
        long stamp=0L;
        return Integer.toHexString(app.pkg.hashCode())+"_"+stamp+".png";
    }
    private Drawable loadCachedAppIcon(SelectorApp app,PackageManager pm){
        File dir=new File(getFilesDir(),"app_icon_cache");
        if(!dir.exists())dir.mkdirs();
        File file=new File(dir,iconCacheName(app));
        if(file.isFile()){
            Bitmap cached=BitmapFactory.decodeFile(file.getAbsolutePath());
            if(cached!=null)return new BitmapDrawable(getResources(),cached);
        }
        try{
            Drawable source=pm.getApplicationIcon(app.info);
            int size=Math.max(96,(int)(48*getResources().getDisplayMetrics().density));
            Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);source.setBounds(0,0,size,size);source.draw(canvas);
            try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.flush();}
            return new BitmapDrawable(getResources(),bitmap);
        }catch(Throwable ignored){return null;}
    }

    private void addSelectorRow(LinearLayout parent,SelectorApp app,Set<String> checked,Set<String> owned,PackageManager pm,
            java.util.Map<String,android.graphics.drawable.Drawable> iconCache,boolean compact,Runnable afterChange){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10),dp(compact?6:9),dp(8),dp(compact?6:9));
        item.setBackground(round(compact?0xff101f2b:0xff111f2d,dp(13),0xff263d50));
        ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String cacheKey=app.pkg;
        android.graphics.drawable.Drawable drawable=iconCache.get(cacheKey);
        if(drawable==null){drawable=loadCachedAppIcon(app,pm);if(drawable!=null)iconCache.put(cacheKey,drawable);}
        if(drawable!=null)icon.setImageDrawable(drawable);else {icon.setImageResource(android.R.drawable.sym_def_app_icon);}
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(compact?32:44),dp(compact?32:44));ip.rightMargin=dp(12);item.addView(icon,ip);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(app.label,compact?12:13,true,Color.WHITE);
        TextView pkg=text(app.pkg+(app.system?" · SYSTEM":" · USER"),9,false,muted);
        String state=appState(app.pkg,owned);int stateColor=state.equals("ENABLED")?green:state.equals("MANAGED FREEZE")?cyan:state.equals("FROZEN · EXTERNAL")||state.equals("SUSPENDED")?0xffffb66b:muted;
        TextView st=text(state,9,true,stateColor);labels.addView(name);labels.addView(pkg);labels.addView(st);
        item.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox cb=new CheckBox(this);cb.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{cyan,muted}));
        cb.setChecked(checked.contains(app.pkg));
        cb.setOnCheckedChangeListener((button,isChecked)->{
            if(isChecked)checked.add(app.pkg);else checked.remove(app.pkg);
            Prefs.putSet(MainActivity.this, currentSelectorKey, checked); refreshDualPanels(showingFreezePanel);
            if(afterChange!=null)afterChange.run();
        });
        item.addView(cb);item.setOnClickListener(v->cb.setChecked(!cb.isChecked()));
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.bottomMargin=dp(6);parent.addView(item,rowLp);
    }
    private boolean isCritical(String p){return p.equals("com.android.launcher")||p.startsWith("com.google.android.inputmethod")||p.startsWith("com.android.inputmethod")||p.equals("com.google.android.permissioncontroller")||p.equals("com.android.permissioncontroller");}
    private String clockText(int minutes){
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes/60, minutes%60);
    }
    private void showSchedule(){
        final int[] start={ScheduleManager.startMinute(this)};
        final int[] end={ScheduleManager.endMinute(this)};
        final android.widget.CheckBox enabled=new android.widget.CheckBox(this);
        enabled.setText("Enable daily schedule");
        enabled.setTextColor(Color.WHITE);
        enabled.setChecked(ScheduleManager.enabled(this));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView info=text("Start: unfreeze all Smart Freeze-managed apps, then launch each selected app to refresh its data. End: freeze the selected apps again.",12,false,muted);
        info.setPadding(0,0,0,dp(12));box.addView(info);box.addView(enabled);
        TextView startView=text("Unfreeze + Launch: "+clockText(start[0]),15,true,Color.WHITE);
        TextView endView=text("Freeze again: "+clockText(end[0]),15,true,Color.WHITE);
        startView.setPadding(0,dp(14),0,dp(14));endView.setPadding(0,dp(14),0,dp(14));
        box.addView(startView);box.addView(endView);
        startView.setOnClickListener(v->{
            android.app.TimePickerDialog d=new android.app.TimePickerDialog(this,(view,h,m)->{
                start[0]=h*60+m;startView.setText("Unfreeze + Launch: "+clockText(start[0]));
            },start[0]/60,start[0]%60,true);d.show();
        });
        endView.setOnClickListener(v->{
            android.app.TimePickerDialog d=new android.app.TimePickerDialog(this,(view,h,m)->{
                end[0]=h*60+m;endView.setText("Freeze again: "+clockText(end[0]));
            },end[0]/60,end[0]%60,true);d.show();
        });
        new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Daily Schedule").setView(box)
            .setPositiveButton("Save",(dialog,which)->{
                if(start[0]==end[0]){toast("Start and end times must be different");return;}
                ScheduleManager.save(this,enabled.isChecked(),start[0],end[0]);
                toast(enabled.isChecked()?"Daily schedule saved":"Schedule disabled");
            }).setNegativeButton("Cancel",null).show();
    }

    private void showSystemLogs(){
        String logs=Prefs.get(this).getString("system_logs","");
        if(logs.trim().isEmpty()) logs="No screen lock activity recorded yet. Lock the phone while monitoring is active to record verified results.";
        TextView v=text(logs,13,false,Color.WHITE); v.setPadding(dp(14),dp(14),dp(14),dp(14));
        ScrollView sc=new ScrollView(this); sc.addView(v);
        new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("System Logs · Screen Lock").setView(sc).setPositiveButton("Close",null)
            .setNeutralButton("Clear",(d,w)->Prefs.get(this).edit().remove("system_logs").apply()).show();
    }
    private void showLogs(){String logs=Prefs.get(this).getString("logs","No events yet");TextView v=text(logs,12,false,Color.WHITE);v.setPadding(dp(14),dp(14),dp(14),dp(14));new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,R.style.SmartFreezeDialogTheme)).setTitle("Activity Logs").setView(v).setPositiveButton("Close",null).show();}
    private Button addButton(String label,Runnable action){
        if(actionGrid==null)return null;
        LinearLayout row;
        if(actionGrid.getChildCount()==0 || ((LinearLayout)actionGrid.getChildAt(actionGrid.getChildCount()-1)).getChildCount()>=4){
            row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(42));rp.topMargin=dp(3);actionGrid.addView(row,rp);
        }else row=(LinearLayout)actionGrid.getChildAt(actionGrid.getChildCount()-1);
        Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setMinHeight(dp(34));b.setMinimumHeight(dp(34));b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(3),0,dp(3),0);b.setTextColor(Color.WHITE);
        polishButton(b,0xff142333,0xff28566B);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1);lp.leftMargin=dp(3);lp.rightMargin=dp(3);row.addView(b,lp);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private TextView text(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();refreshCounts();refreshPermissionBadges();refreshStartStopButton();startLiveStatusUpdates();graceUiHandler.removeCallbacks(graceUiTick);graceUiHandler.post(graceUiTick);}
    private void refreshGraceCountdown(){
        if(graceCountdownView==null)return;
        long deadline=Prefs.get(this).getLong("grace_deadline_ms",0L);
        if(deadline<=System.currentTimeMillis()){graceCountdownView.setText("No active grace countdown");graceCountdownView.setTextColor(muted);return;}
        long seconds=(deadline-System.currentTimeMillis()+999L)/1000L;
        String value=String.format(java.util.Locale.US,"%02d:%02d",seconds/60L,seconds%60L);
        graceCountdownView.setText("GRACE COUNTDOWN  "+value);
        graceCountdownView.setTextColor(0xff37e889);
    }
}
