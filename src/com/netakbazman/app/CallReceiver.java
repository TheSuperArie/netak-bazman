package com.netakbazman.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

/** Listens to call state changes and starts / stops the timer service. */
public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !Prefs.enabled(c)) return;
        String action = i.getAction();
        SharedPreferences sp = Prefs.sp(c);
        long now = System.currentTimeMillis();

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            String n = i.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (n != null) sp.edit().putString("lastOut", n).putLong("lastOutAt", now).apply();
            return;
        }
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) return;

        String state = i.getStringExtra(TelephonyManager.EXTRA_STATE);
        String num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            SharedPreferences.Editor e = sp.edit().putBoolean("wasRinging", true);
            if (num != null && !num.isEmpty()) e.putString("lastIn", num).putLong("lastInAt", now);
            e.apply();
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            if (num == null || num.isEmpty()) {
                if (sp.getBoolean("wasRinging", false) && now - sp.getLong("lastInAt", 0) < 10 * 60000L) {
                    num = sp.getString("lastIn", null);
                } else if (now - sp.getLong("lastOutAt", 0) < 2 * 60000L) {
                    num = sp.getString("lastOut", null);
                }
            }
            Intent s = new Intent(c, CallTimerService.class)
                    .setAction(CallTimerService.ACTION_CALL_START)
                    .putExtra("number", num);
            try {
                c.startForegroundService(s);
            } catch (Exception ignored) {}
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            sp.edit().putBoolean("wasRinging", false).apply();
            if (CallTimerService.running) {
                try {
                    c.startService(new Intent(c, CallTimerService.class).setAction(CallTimerService.ACTION_CALL_END));
                } catch (Exception ignored) {}
            }
        }
    }
}
