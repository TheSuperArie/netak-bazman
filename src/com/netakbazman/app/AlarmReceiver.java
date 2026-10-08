package com.netakbazman.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

/**
 * System alarm at warning time / disconnect time.
 * It does the job by itself (warning or hang-up) from the saved timer state, so it works even when
 * the screen is off, the phone is in deep sleep, or the system closed the app during the call.
 * Then it wakes the timer service, if one is still needed.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i != null ? i.getAction() : null;
        SharedPreferences sp = Prefs.sp(c);
        boolean handledHangUp = false;
        try {
            boolean active = sp.getBoolean("t_active", false);
            boolean inCall = CallTimerService.callState(c) == TelephonyManager.CALL_STATE_OFFHOOK;
            if (active && inCall) {
                long nowW = System.currentTimeMillis();
                long deadlineWall = sp.getLong("t_deadlineWall", Long.MAX_VALUE);
                String label = sp.getString("t_label", "שיחה");
                if ((CallTimerService.ACTION_ALARM + "1").equals(a) && nowW >= deadlineWall - 5000L) {
                    boolean ok = CallTimerService.endCallNow(c);
                    long startWall = sp.getLong("t_startWall", nowW);
                    long startElapsed = SystemClock.elapsedRealtime() - (nowW - startWall);
                    CallTimerService.postDone(c, ok, label, startElapsed, null);
                    CallTimerService.cancelWarn(c);
                    if (ok) {
                        sp.edit().putBoolean("t_active", false).apply();
                        handledHangUp = true;
                    }
                } else if ((CallTimerService.ACTION_ALARM + "2").equals(a) && !sp.getBoolean("t_warned", false)) {
                    sp.edit().putBoolean("t_warned", true).apply();
                    CallTimerService.postWarning(c, label, Math.max(60000L, deadlineWall - nowW), false, null);
                }
            }
        } catch (Exception ignored) {}

        try {
            if (handledHangUp) {
                // the call is over: just let a still-running timer close itself
                if (CallTimerService.running) {
                    c.startService(new Intent(c, CallTimerService.class).setAction(CallTimerService.ACTION_CALL_END));
                }
            } else {
                c.startForegroundService(new Intent(c, CallTimerService.class).setAction(CallTimerService.ACTION_ALARM));
            }
        } catch (Exception ignored) {}
    }
}
