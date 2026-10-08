package com.netakbazman.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * Keeps "נתק בזמן" awake in the background, from the moment the phone is switched on.
 * Some phones (Xiaomi / Qin and others) don't deliver call broadcasts to an app that isn't running,
 * so after a restart the app would only work once it was opened again. This small service is started
 * at boot and listens to the call state itself. It uses almost no battery: it only waits for calls.
 */
public class WatchService extends Service {

    static final String CH_WATCH = "watch";
    static final int ID_WATCH = 10;

    private TelephonyManager tm;
    private PhoneStateListener listener;

    /** Start or stop the watcher according to the settings. Safe to call any time. */
    static void sync(Context c) {
        try {
            Intent i = new Intent(c, WatchService.class);
            if (Prefs.enabled(c) && Prefs.watch(c)) c.startForegroundService(i);
            else c.stopService(i);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel(this);
        startForeground(ID_WATCH, build());
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        listener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String number) {
                String s;
                if (state == TelephonyManager.CALL_STATE_RINGING) s = TelephonyManager.EXTRA_STATE_RINGING;
                else if (state == TelephonyManager.CALL_STATE_OFFHOOK) s = TelephonyManager.EXTRA_STATE_OFFHOOK;
                else s = TelephonyManager.EXTRA_STATE_IDLE;
                // same handling as the system call broadcast (duplicates are harmless)
                Intent i = new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                        .putExtra(TelephonyManager.EXTRA_STATE, s);
                if (number != null && !number.isEmpty()) i.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number);
                try { new CallReceiver().onReceive(WatchService.this, i); } catch (Exception ignored) {}
            }
        };
        try {
            if (tm != null) tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.enabled(this) || !Prefs.watch(this)) { stopSelf(); return START_NOT_STICKY; }
        startForeground(ID_WATCH, build());
        return START_STICKY; // if the system closes it, it comes back by itself
    }

    @Override
    public void onDestroy() {
        try { if (tm != null && listener != null) tm.listen(listener, PhoneStateListener.LISTEN_NONE); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification build() {
        return new Notification.Builder(this, CH_WATCH)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(CallTimerService.COLOR)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(CallTimerService.openApp(this))
                .setContentTitle("נתק בזמן פועל ברקע")
                .setContentText("השיחות יתנתקו לפי ההגדרות, גם אחרי הפעלה מחדש של הטלפון")
                .build();
    }

    static void createChannel(Context c) {
        NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (m.getNotificationChannel(CH_WATCH) == null) {
            NotificationChannel ch = new NotificationChannel(CH_WATCH, "פועל ברקע", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("התראה שקטה שמראה שהאפליקציה מוכנה לשיחה הבאה");
            ch.setShowBadge(false);
            m.createNotificationChannel(ch);
        }
    }
}
