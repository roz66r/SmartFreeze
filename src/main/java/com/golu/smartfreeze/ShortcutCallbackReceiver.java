package com.golu.smartfreeze;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ShortcutCallbackReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Intent next = new Intent(context, MainActivity.class);
        next.putExtra("shortcut_next", true);
        next.putExtra("shortcut_index", intent.getIntExtra("shortcut_index", -1));
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(next);
    }
}
