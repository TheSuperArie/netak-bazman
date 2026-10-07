package com.netakbazman.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** System alarm at warning time / disconnect time. Wakes the timer even if the app was closed. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            c.startForegroundService(new Intent(c, CallTimerService.class).setAction(CallTimerService.ACTION_ALARM));
        } catch (Exception ignored) {}
    }
}
