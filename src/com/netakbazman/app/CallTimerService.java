package com.netakbazman.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telecom.TelecomManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Runs during a call: counts down, warns before the end and hangs up. */
public class CallTimerService extends Service {

    public static final String ACTION_CALL_START = "com.netakbazman.START";
    public static final String ACTION_CALL_END = "com.netakbazman.END";
    public static final String ACTION_SNOOZE = "com.netakbazman.SNOOZE";
    public static final String ACTION_KEEP = "com.netakbazman.KEEP";

    static final String CH_ONGOING = "ongoing";
    static final String CH_WARN = "warn";
    static final String CH_DONE = "done";
    static final int ID_ONGOING = 1, ID_WARN = 2, ID_DONE = 3;
    static final int COLOR = 0xFFE8A33D;

    public static volatile boolean running = false;

    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private boolean active = false;
    private boolean warned = false;
    private boolean userAdjusted = false;
    private long startElapsed;
    private long deadline;          // elapsedRealtime, -1 = never
    private int plannedMinutes;
    private String number;
    private String label;
    private Bitmap photo;

    private final Runnable tick = new Runnable() {
        @Override public void run() { onTick(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        handler = new Handler(Looper.getMainLooper());
        createChannels(this);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_CALL_START.equals(a)) {
            handleStart(intent.getStringExtra("number"));
        } else if (ACTION_CALL_END.equals(a)) {
            finish();
        } else if (ACTION_SNOOZE.equals(a)) {
            if (active && deadline >= 0) snooze();
            else cancelWarn(this);
            if (!active) stopSelf();
        } else if (ACTION_KEEP.equals(a)) {
            cancelWarn(this);
            finish();
        } else if (!active) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void handleStart(String num) {
        if (!active) {
            active = true;
            warned = false;
            userAdjusted = false;
            startElapsed = SystemClock.elapsedRealtime();
            number = num;
            compute();
            // must go foreground right away
            startForeground(ID_ONGOING, buildOngoing());
            if (deadline < 0) { finish(); return; }
            acquireWakeLock();
            handler.removeCallbacks(tick);
            handler.post(tick);
        } else if ((number == null || number.isEmpty()) && num != null && !num.isEmpty() && !userAdjusted) {
            number = num;
            compute();
            if (deadline < 0) { finish(); return; }
            updateOngoing();
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
    }

    private void compute() {
        Prefs.Rule r = Prefs.match(this, number);
        int m = r != null ? r.minutes : Prefs.defaultMinutes(this);
        plannedMinutes = m;
        ContactUtil.Contact k = ContactUtil.lookup(this, number);
        if (r != null && r.name != null && !r.name.trim().isEmpty()) label = r.name.trim();
        else if (k != null && !k.name.isEmpty()) label = k.name;
        else if (number != null && !number.isEmpty()) label = number;
        else label = "שיחה";
        photo = null;
        try {
            int size = (int) (64 * getResources().getDisplayMetrics().density);
            if (k != null) photo = ContactUtil.avatar(this, k.photoUri, label, size);
            else if (r != null) photo = ContactUtil.letter(label, size);
        } catch (Exception ignored) {}
        deadline = m <= 0 ? -1 : startElapsed + m * 60000L;
        // a very short limit (shorter than the warning time) gets no warning
        warned = m > 0 && m * 60000L <= Prefs.warnMinutes(this) * 60000L;
    }

    private void onTick() {
        if (!active || deadline < 0) return;
        long now = SystemClock.elapsedRealtime();
        long rem = deadline - now;
        long warnMs = Prefs.warnMinutes(this) * 60000L;
        if (rem <= 0) { hangUp(); return; }
        if (!warned && rem <= warnMs) {
            warned = true;
            postWarning(this, label, rem, false, photo);
        }
        long next = Math.min(15000L, rem);
        if (!warned && rem - warnMs > 0) next = Math.min(next, rem - warnMs);
        handler.postDelayed(tick, Math.max(500L, next));
    }

    private void snooze() {
        long now = SystemClock.elapsedRealtime();
        int s = Prefs.snoozeMinutes(this);
        deadline = Math.max(deadline, now) + s * 60000L;
        userAdjusted = true;
        warned = (deadline - now) <= Prefs.warnMinutes(this) * 60000L;
        cancelWarn(this);
        updateOngoing();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void hangUp() {
        boolean ok = false;
        try {
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                ok = tm != null && tm.endCall();
            }
        } catch (Exception ignored) {}
        long mins = (SystemClock.elapsedRealtime() - startElapsed) / 60000L;
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        Notification.Builder b = new Notification.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(COLOR)
                .setAutoCancel(true)
                .setContentIntent(openApp(this));
        if (photo != null) b.setLargeIcon(photo);
        if (ok) {
            b.setContentTitle("השיחה עם " + label + " נותקה ב-" + time)
             .setContentText("אחרי " + Prefs.fmt((int) Math.max(1, mins)) + " · ניתוק אוטומטי");
        } else {
            b.setContentTitle("לא הצלחתי לנתק את השיחה")
             .setContentText("פתח את האפליקציה ובדוק שכל ההרשאות אושרו");
        }
        nm(this).notify(ID_DONE, b.build());
        finish();
    }

    private void finish() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        cancelWarn(this);
        try { stopForeground(true); } catch (Exception ignored) {}
        releaseWakeLock();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        releaseWakeLock();
        super.onDestroy();
    }

    // ---------- wake lock ----------

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "netakbazman:call");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(12 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        wakeLock = null;
    }

    // ---------- notifications ----------

    private Notification buildOngoing() {
        Notification.Builder b = new Notification.Builder(this, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(COLOR)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openApp(this))
                .setContentTitle("טיימר שיחה · " + label);
        if (photo != null) b.setLargeIcon(photo);
        if (deadline >= 0) {
            long rem = deadline - SystemClock.elapsedRealtime();
            long endWall = System.currentTimeMillis() + rem;
            String at = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(endWall));
            b.setContentText("השיחה תתנתק בשעה " + at)
             .setShowWhen(true)
             .setWhen(endWall)
             .setUsesChronometer(true)
             .setChronometerCountDown(true)
             .addAction(action(this, ACTION_SNOOZE, "הוסף " + Prefs.snoozeMinutes(this) + " דק'"))
             .addAction(action(this, ACTION_KEEP, "אל תנתק"));
        } else {
            b.setContentText("ללא ניתוק אוטומטי בשיחה זו");
        }
        return b.build();
    }

    private void updateOngoing() {
        if (active) nm(this).notify(ID_ONGOING, buildOngoing());
    }

    static void postWarning(Context c, String label, long remMs, boolean test, Bitmap photo) {
        createChannels(c);
        int mins = (int) Math.max(1, Math.round(remMs / 60000.0));
        Notification.Builder nb = new Notification.Builder(c, CH_WARN);
        if (photo != null) nb.setLargeIcon(photo);
        Notification n = nb
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(COLOR)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentTitle("השיחה תתנתק בעוד " + Prefs.fmt(mins))
                .setContentText(label + " · עדיין מקשיב? אפשר להוסיף זמן")
                .setAutoCancel(true)
                .setTimeoutAfter(remMs)
                .setContentIntent(openApp(c))
                .addAction(test ? testAction(c, 21, "אני ער, אל תנתק") : action(c, ACTION_KEEP, "אני ער, אל תנתק"))
                .addAction(test ? testAction(c, 22, "הוסף " + Prefs.fmt(Prefs.snoozeMinutes(c)))
                                : action(c, ACTION_SNOOZE, "הוסף " + Prefs.fmt(Prefs.snoozeMinutes(c))))
                .build();
        nm(c).notify(ID_WARN, n);
    }

    static void cancelWarn(Context c) { nm(c).cancel(ID_WARN); }

    static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static Notification.Action action(Context c, String act, String title) {
        Intent i = new Intent(c, CallTimerService.class).setAction(act);
        int code = ACTION_SNOOZE.equals(act) ? 11 : 12;
        PendingIntent pi = PendingIntent.getService(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notif), title, pi).build();
    }

    /** Buttons of the sample notification just open the app and confirm they work. */
    static Notification.Action testAction(Context c, int code, String title) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("testButton", title);
        PendingIntent pi = PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notif), title, pi).build();
    }

    static void createChannels(Context c) {
        NotificationManager m = nm(c);
        if (m.getNotificationChannel(CH_ONGOING) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ONGOING, "שיחה פעילה", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("ספירה לאחור בזמן שיחה");
            ch.setShowBadge(false);
            m.createNotificationChannel(ch);
        }
        if (m.getNotificationChannel(CH_WARN) == null) {
            NotificationChannel ch = new NotificationChannel(CH_WARN, "התראה לפני ניתוק", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("מופיעה כמה דקות לפני שהשיחה מתנתקת");
            ch.setSound(null, null); // gentle: vibration only, so a sleeping listener is not woken up
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 220, 180, 220});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            m.createNotificationChannel(ch);
        }
        if (m.getNotificationChannel(CH_DONE) == null) {
            NotificationChannel ch = new NotificationChannel(CH_DONE, "שיחה נותקה", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("סיכום אחרי ניתוק אוטומטי");
            m.createNotificationChannel(ch);
        }
    }
}
