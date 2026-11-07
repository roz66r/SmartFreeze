package com.golu.smartfreeze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Legacy manifest receiver intentionally left inert; screen events are registered dynamically
 * by the running foreground MonitorService because these broadcasts are not reliable as
 * manifest-declared receivers on modern Android versions. */
public class ScreenStateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs.log(context, "IGNORED LEGACY SCREEN RECEIVER; dynamic service receiver is used");
    }
}
