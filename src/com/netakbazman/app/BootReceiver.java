package com.netakbazman.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone switched on (or the app was updated): get ready for the next call without opening the app. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // a timer left over from before the restart is meaningless now
        CallTimerService.clearLeftovers(c);
        WatchService.sync(c);
    }
}
