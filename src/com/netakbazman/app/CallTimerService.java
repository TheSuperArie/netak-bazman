package com.netakbazman.app;

import android.Manifest;
import android.app.AlarmManager;
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
    public static final String ACTION_ALARM = "com.netakbazman.ALARM";

    static final String CH_ONGOING = "ongoing";
    static final String CH_ONGOING_ECO = "ongoing_eco";
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
    private boolean eco = false;
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
        // a repeated startForegroundService() on a running timer must confirm foreground again
        if (active && (ACTION_CALL_START.equals(a) || ACTION_ALARM.equals(a))) {
            startForeground(ID_ONGOING, buildOngoing());
        }
        if (ACTION_CALL_START.equals(a)) {
            handleStart(intent.getStringExtra("number"));
        } else if (ACTION_CALL_END.equals(a)) {
            finish();
        } else if (ACTION_SNOOZE.equals(a)) {
            if (!active && Prefs.sp(this).getBoolean("t_active", false)) restore();
            if (active && deadline >= 0) snooze();
            else cancelWarn(this);
            if (!active) stopSelf();
        } else if (ACTION_ALARM.equals(a)) {
            if (active) {
                // the alarm receiver may already have shown the warning
                if (Prefs.sp(this).getBoolean("t_warned", false)) warned = true;
                onTick();
            } else restore();
        } else if (ACTION_KEEP.equals(a)) {
            cancelWarn(this);
            finish();
        } else if (!active) {
            // restarted by the system after being killed (sticky): continue a running timer
            if (intent == null && Prefs.sp(this).getBoolean("t_active", false)) restore();
            else stopSelf();
        }
        return active ? START_STICKY : START_NOT_STICKY;
    }

    private void handleStart(String num) {
        if (!active) {
            active = true;
            warned = false;
            userAdjusted = false;
            eco = Prefs.eco(this);
            startElapsed = SystemClock.elapsedRealtime();
            number = num;
            compute();
            // must go foreground right away
            startForeground(ID_ONGOING, buildOngoing());
            if (deadline < 0) { finish(); return; }
            if (!eco) acquireWakeLock();
            scheduleAlarms();
            saveState();
            handler.removeCallbacks(tick);
            handler.post(tick);
        } else if ((number == null || number.isEmpty()) && num != null && !num.isEmpty() && !userAdjusted) {
            number = num;
            compute();
            if (deadline < 0) { finish(); return; }
            updateOngoing();
            scheduleAlarms();
            saveState();
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
            if (eco) photo = null;
            else if (k != null) photo = ContactUtil.avatar(this, k.photoUri, label, size);
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
        if (!warned && Prefs.sp(this).getBoolean("t_warned", false)) warned = true; // receiver already warned
        if (!warned && rem <= warnMs) {
            warned = true;
            saveState();
            postWarning(this, label, rem, false, photo);
        }
        if (eco) return; // eco: the system alarms wake us at the right moments
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
        scheduleAlarms();
        saveState();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void hangUp() {
        boolean ok = endCallNow(this);
        postDone(this, ok, label, startElapsed, photo);
        finish();
    }

    /** Last successful hang-up, so the service and the alarm receiver never hang up twice. */
    private static volatile long hungUpAt = 0;

    static int callState(Context c) {
        try {
            android.telephony.TelephonyManager tm =
                    (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null ? tm.getCallState() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Hangs up the current call. Works from the service or straight from the alarm receiver. */
    static synchronized boolean endCallNow(Context c) {
        if (SystemClock.elapsedRealtime() - hungUpAt < 8000L && hungUpAt != 0) return true;
        if (callState(c) == android.telephony.TelephonyManager.CALL_STATE_IDLE) return true; // already over
        boolean ok = false;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                if (c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
                    ok = tm != null && tm.endCall();
                }
            } else {
                ok = legacyEndCall(c);
            }
        } catch (Throwable ignored) {}
        if (ok) hungUpAt = SystemClock.elapsedRealtime();
        return ok;
    }

    /** Summary notification after an automatic hang-up. */
    static void postDone(Context c, boolean ok, String label, long startElapsed, Bitmap photo) {
        createChannels(c);
        long mins = (SystemClock.elapsedRealtime() - startElapsed) / 60000L;
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        Notification.Builder b = new Notification.Builder(c, CH_DONE)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(COLOR)
                .setAutoCancel(true)
                .setContentIntent(openApp(c));
        if (photo != null) b.setLargeIcon(photo);
        if (ok) {
            b.setContentTitle("השיחה עם " + (label != null ? label : "שיחה") + " נותקה ב-" + time)
             .setContentText("אחרי " + Prefs.fmt((int) Math.max(1, mins)) + " · ניתוק אוטומטי");
        } else {
            b.setContentTitle("לא הצלחתי לנתק את השיחה")
             .setContentText("פתח את האפליקציה ובדוק שכל ההרשאות אושרו");
        }
        nm(c).notify(ID_DONE, b.build());
    }

    /** Android 8.x (e.g. older keypad phones): the classic internal telephony call. */
    private static boolean legacyEndCall(Context c) {
        try {
            android.telephony.TelephonyManager tm =
                    (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            java.lang.reflect.Method get = tm.getClass().getDeclaredMethod("getITelephony");
            get.setAccessible(true);
            Object it = get.invoke(tm);
            java.lang.reflect.Method end = it.getClass().getMethod("endCall");
            Object r = end.invoke(it);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    private void finish() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        cancelAlarms();
        clearState();
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

    // ---------- system alarms + saved state (survive the app being closed) ----------

    private PendingIntent alarmPI(int code) { return alarmPI(this, code); }

    static PendingIntent alarmPI(Context c, int code) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_ALARM + code);
        return PendingIntent.getBroadcast(c, 100 + code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Called when a call ends, even if the timer process is gone: drop leftover alarms and state. */
    static void clearLeftovers(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) { am.cancel(alarmPI(c, 1)); am.cancel(alarmPI(c, 2)); }
        } catch (Exception ignored) {}
        Prefs.sp(c).edit().putBoolean("t_active", false).apply();
        cancelWarn(c);
    }

    private void scheduleAlarms() {
        cancelAlarms();
        if (deadline < 0) return;
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = SystemClock.elapsedRealtime();
        long warnMs = Prefs.warnMinutes(this) * 60000L;
        try {
            // alarm-clock alarms fire on time even with the screen off, in deep sleep (Doze)
            // and in power saving, and they wake the phone up. The alarm receiver hangs up by itself,
            // so the disconnect does not depend on this service still being alive.
            long wall = System.currentTimeMillis() + (deadline - now);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(wall, openApp(this)), alarmPI(1));
            if (!warned && deadline - warnMs > now) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline - warnMs, alarmPI(2));
            }
        } catch (Exception ignored) {}
    }

    private void cancelAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(alarmPI(1));
        am.cancel(alarmPI(2));
    }

    private void saveState() {
        long nowE = SystemClock.elapsedRealtime(), nowW = System.currentTimeMillis();
        Prefs.sp(this).edit()
                .putBoolean("t_active", true)
                .putLong("t_startWall", nowW - (nowE - startElapsed))
                .putLong("t_deadlineWall", nowW + (deadline - nowE))
                .putString("t_number", number)
                .putString("t_label", label)
                .putBoolean("t_warned", warned)
                .putBoolean("t_adjusted", userAdjusted)
                .apply();
    }

    private void clearState() {
        Prefs.sp(this).edit().putBoolean("t_active", false).apply();
    }

    /** The app was closed during a call and a system alarm woke it: continue where it stopped. */
    private void restore() {
        android.content.SharedPreferences sp = Prefs.sp(this);
        boolean inCall = callState(this) == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK;
        if (!sp.getBoolean("t_active", false) || !inCall) {
            label = "שיחה";
            deadline = -1;
            startForeground(ID_ONGOING, buildOngoing());
            finish();
            return;
        }
        long nowE = SystemClock.elapsedRealtime(), nowW = System.currentTimeMillis();
        active = true;
        eco = Prefs.eco(this);
        number = sp.getString("t_number", null);
        label = sp.getString("t_label", "שיחה");
        warned = sp.getBoolean("t_warned", false);
        userAdjusted = sp.getBoolean("t_adjusted", false);
        startElapsed = nowE - (nowW - sp.getLong("t_startWall", nowW));
        deadline = nowE + (sp.getLong("t_deadlineWall", nowW) - nowW);
        photo = null;
        startForeground(ID_ONGOING, buildOngoing());
        if (!eco) acquireWakeLock();
        scheduleAlarms();
        onTick();
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
        if (eco) {
            String text = "ללא ניתוק אוטומטי בשיחה זו";
            if (deadline >= 0) {
                long endWall = System.currentTimeMillis() + (deadline - SystemClock.elapsedRealtime());
                text = "תתנתק בשעה " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(endWall));
            }
            return new Notification.Builder(this, CH_ONGOING_ECO)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setColor(COLOR)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setContentIntent(openApp(this))
                    .setContentTitle("נתק בזמן · מצב חיסכון")
                    .setContentText(text)
                    .build();
        }
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
        if (m.getNotificationChannel(CH_ONGOING_ECO) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ONGOING_ECO, "שיחה פעילה (מצב חיסכון)", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("התראה מינימלית בזמן שיחה, בלי ספירה לאחור");
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
