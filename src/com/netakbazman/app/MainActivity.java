package com.netakbazman.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    // warm "vintage radio" palette
    static final int BG = 0xFF1B1A17;
    static final int CARD = 0xFF26241F;
    static final int FIELD = 0xFF1F1D19;
    static final int LINE = 0xFF3D382E;
    static final int TEXT = 0xFFF3E9D2;
    static final int MUTED = 0xFFB9AE98;
    static final int ACCENT = 0xFFE8A33D;
    static final int ON_ACCENT = 0xFF2A1E0C;
    static final int OK = 0xFF8CC7A8;
    static final int DANGER = 0xFFE0795C;

    static final int REQ_PERMS = 1;
    static final int REQ_CONTACT = 2;
    static final int REQ_CONTACTS_FOR_PICKER = 3;

    interface IntCb { void on(int v); }

    private LinearLayout root;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CallTimerService.createChannels(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));
        setContentView(sv);
        handleTestButton(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleTestButton(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void handleTestButton(Intent i) {
        if (i == null) return;
        String t = i.getStringExtra("testButton");
        if (t == null) return;
        i.removeExtra("testButton");
        CallTimerService.cancelWarn(this);
        toast("הכפתור \"" + t + "\" עובד. בשיחה אמיתית הוא יפעל מיד.");
    }

    // ---------------------------------------------------------------- screen

    private void render() {
        root.removeAllViews();
        header();
        setupCard();
        enableCard();
        defaultCard();
        rulesCard();
        warnCard();
        watchCard();
        ecoCard();
        WatchService.sync(this);
        TextView f = text("עובד בלי אינטרנט · שום מידע לא יוצא מהמכשיר · גרסה 1.4", 13, MUTED, false);
        f.setGravity(Gravity.CENTER);
        f.setPadding(0, dp(18), 0, 0);
        root.addView(f, new LinearLayout.LayoutParams(-1, -2));
    }

    private void header() {
        LinearLayout row = hrow();
        row.setPadding(dp(4), 0, dp(4), dp(6));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getDrawable(R.mipmap.ic_launcher));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout col = vcol();
        col.setPadding(dp(12), 0, dp(12), 0);
        TextView t = text("נתק בזמן", 30, TEXT, true);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        col.addView(t);
        col.addView(text("שיחות שמתנתקות לבד כשנרדמים", 15, MUTED, false));
        col.addView(text("✓ כל שינוי נשמר אוטומטית, אין צורך בכפתור שמירה", 13, OK, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row);
        View rule = new View(this);
        rule.setBackgroundColor(ACCENT);
        rule.setAlpha(0.55f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(2));
        lp.setMargins(dp(4), dp(6), dp(4), dp(6));
        root.addView(rule, lp);
    }

    // ---- setup / permissions

    private String[] phonePerms() {
        List<String> p = new ArrayList<String>();
        p.add(Manifest.permission.READ_PHONE_STATE);
        p.add(Manifest.permission.READ_CALL_LOG);
        p.add(Manifest.permission.PROCESS_OUTGOING_CALLS);
        p.add(Manifest.permission.READ_CONTACTS);
        // hanging up: Android 9+ uses ANSWER_PHONE_CALLS, Android 8 needs CALL_PHONE
        p.add(Build.VERSION.SDK_INT >= 28 ? Manifest.permission.ANSWER_PHONE_CALLS : Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= 33) p.add("android.permission.POST_NOTIFICATIONS");
        return p.toArray(new String[0]);
    }

    private boolean hasPhonePerms() {
        String[] need = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.PROCESS_OUTGOING_CALLS, Manifest.permission.READ_CONTACTS,
                Build.VERSION.SDK_INT >= 28 ? Manifest.permission.ANSWER_PHONE_CALLS : Manifest.permission.CALL_PHONE};
        for (String s : need) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    private boolean notifOk() {
        NotificationManager m = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return m != null && m.areNotificationsEnabled();
    }

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean isXiaomi() {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") || m.contains("qin");
    }

    private boolean flag(String k) { return Prefs.sp(this).getBoolean(k, false); }
    private void setFlag(String k) { Prefs.sp(this).edit().putBoolean(k, true).apply(); }

    private void setupCard() {
        boolean p = hasPhonePerms(), n = notifOk(), bat = batteryOk();
        boolean xi = isXiaomi();
        boolean auto = !xi || flag("autostartVisited");
        boolean miBat = !xi || flag("miBatteryVisited");
        boolean lock = !xi || flag("recentsLockSeen");
        boolean all = p && n && bat && auto && miBat && lock;

        LinearLayout card = card();
        if (all) {
            LinearLayout row = hrow();
            row.addView(badge("✓", OK, true));
            LinearLayout col = vcol();
            col.setPadding(dp(12), 0, dp(12), 0);
            col.addView(text("הכל מוכן", 19, TEXT, true));
            col.addView(text("השיחות יתנתקו לפי ההגדרות שלמטה", 14, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row);
            root.addView(card, cardLp());
            return;
        }
        card.addView(title("כמה צעדים להפעלה"));
        card.addView(desc("בלי האישורים האלה הטלפון לא ייתן לאפליקציה לנתק שיחות."));

        card.addView(step(1, p, "הרשאות שיחה ואנשי קשר", "כדי לזהות שיחה, לנתק אותה ולהציג את שם המתקשר", "אשר", new View.OnClickListener() {
            @Override public void onClick(View v) { askPhonePerms(); }
        }));
        card.addView(step(2, n, "התראות", "כדי להזהיר אותך לפני ניתוק", "פתח", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startSafe(i, appDetails());
            }
        }));
        card.addView(step(3, bat, "סוללה ללא הגבלה", "כדי שהטיימר לא ייעצר באמצע הלילה", "אשר", new View.OnClickListener() {
            @Override public void onClick(View v) {
                final Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                guide("סוללה ללא הגבלה", "תיפתח שאלה של הטלפון. לחץ \"אישור\" או \"התר\".", new Runnable() {
                    @Override public void run() { startSafe(i, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                });
            }
        }));
        if (xi) {
            card.addView(step(4, auto, "הפעלה אוטומטית", "כדי שהאפליקציה תעבוד גם אחרי כיבוי והדלקה של הטלפון", "פתח", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    final Intent i = new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                    guide("הפעלה אוטומטית", "ייפתח מסך של שיאומי עם רשימת אפליקציות.\n\nמצא את \"נתק בזמן\" והדלק את המתג שלידה.\n\nאחר כך לחץ \"חזור\". אין שם כפתור שמירה, השינוי נשמר לבד.", new Runnable() {
                        @Override public void run() { setFlag("autostartVisited"); startSafe(i, appDetails()); }
                    });
                }
            }));
            card.addView(step(5, miBat, "חיסכון בסוללה (שיאומי)", "בחר: חיסכון בסוללה ← ללא הגבלות", "פתח", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    guide("חיסכון בסוללה", "ייפתח מסך ההגדרות של האפליקציה (זה מסך של הטלפון).\n\n1. לחץ על \"חיסכון בסוללה\"\n2. בחר \"ללא הגבלות\"\n3. לחץ \"חזור\" עד שתחזור לכאן.\n\nאין שם כפתור שמירה, השינוי נשמר לבד.", new Runnable() {
                        @Override public void run() { setFlag("miBatteryVisited"); startSafe(appDetails(), null); }
                    });
                }
            }));
            card.addView(step(6, lock, "נעילה ברשימת האחרונות", "כדי שהטלפון לא יסגור את האפליקציה כשהמסך כבוי", "איך?", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    guide("נעילה ברשימת האחרונות", "כך הטלפון לא ינקה את האפליקציה כשהמסך נכבה:\n\n"
                            + "1. פתח את רשימת האפליקציות האחרונות (הכפתור המרובע, או החלקה מלמטה והחזקה)\n"
                            + "2. מצא את \"נתק בזמן\"\n"
                            + "3. החלק את הכרטיס שלה למטה, או לחץ עליו לחיצה ארוכה, ובחר במנעול 🔒\n\n"
                            + "אם אין אצלך מנעול, אפשר לדלג.\n"
                            + "ואם יש בהגדרות האבטחה \"ניקוי זיכרון בנעילת מסך\", כדאי לכבות אותו.", "הבנתי", new Runnable() {
                        @Override public void run() { setFlag("recentsLockSeen"); render(); }
                    });
                }
            }));
        }
        root.addView(card, cardLp());
    }

    /** Explains what to do in a system settings screen before opening it. */
    private void guide(String title, String body, final Runnable open) { guide(title, body, "פתח", open); }

    private void guide(String title, String body, String btn, final Runnable open) {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(btn, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) { open.run(); }
                })
                .setNegativeButton("ביטול", null)
                .create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(round(CARD, dp(22), LINE, dp(1)));
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
    }

    private void askPhonePerms() {
        boolean askedBefore = flag("permAsked");
        boolean canAsk = !askedBefore;
        for (String s : phonePerms()) {
            if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED && shouldShowRequestPermissionRationale(s)) canAsk = true;
        }
        if (!canAsk) {
            toast("אשר את ההרשאות \"טלפון\" ו\"יומן שיחות\" במסך שנפתח");
            startSafe(appDetails(), null);
            return;
        }
        setFlag("permAsked");
        requestPermissions(phonePerms(), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        render();
        if (code == REQ_CONTACTS_FOR_PICKER) {
            if (ContactUtil.hasPerm(this)) openContactPicker();
            else systemPicker();
        }
    }

    private Intent appDetails() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
    }

    private void startSafe(Intent i, Intent fallback) {
        try {
            startActivity(i);
        } catch (Exception e) {
            if (fallback != null) {
                try { startActivity(fallback); return; } catch (Exception ignored) {}
            }
            toast("לא הצלחתי לפתוח את ההגדרות במכשיר הזה");
        }
    }

    private View step(int num, boolean done, String t, String d, String btn, View.OnClickListener l) {
        LinearLayout row = hrow();
        row.setPadding(0, dp(10), 0, dp(4));
        row.addView(badge(done ? "✓" : String.valueOf(num), done ? OK : ACCENT, done));
        LinearLayout col = vcol();
        col.setPadding(dp(12), 0, dp(10), 0);
        col.addView(text(t, 17, done ? MUTED : TEXT, true));
        col.addView(text(d, 14, MUTED, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        if (!done) {
            TextView b = button(btn, true);
            b.setOnClickListener(l);
            b.setContentDescription(btn + ": " + t);
            row.addView(b, new LinearLayout.LayoutParams(-2, dp(44)));
        }
        return row;
    }

    // ---- on / off

    private void enableCard() {
        LinearLayout card = card();
        LinearLayout row = hrow();
        LinearLayout col = vcol();
        final boolean on = Prefs.enabled(this);
        col.addView(text("ניתוק אוטומטי", 19, TEXT, true));
        col.addView(text(on ? "פעיל" : "כבוי, השיחות לא יתנתקו", 14, on ? OK : DANGER, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setContentDescription("ניתוק אוטומטי");
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFF8A8172}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{0x99E8A33D, 0x553D382E}));
        sw.setScaleX(1.25f);
        sw.setScaleY(1.25f);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setEnabled(MainActivity.this, ((Switch) v).isChecked());
                render();
            }
        });
        row.addView(sw);
        card.addView(row);
        root.addView(card, cardLp());
    }

    // ---- default timeout

    private void defaultCard() {
        LinearLayout card = card();
        card.addView(title("זמן ניתוק כללי"));
        card.addView(desc("לכל שיחה שאין לה הגדרה מיוחדת למטה"));
        final int cur = Prefs.defaultMinutes(this);
        card.addView(stepper(cur, 0, 600, 30, new IntCb() {
            @Override public void on(int v) { Prefs.setDefaultMinutes(MainActivity.this, v); render(); }
        }));
        card.addView(chips(new int[]{0, 30, 60, 90, 120, 180}, cur, new IntCb() {
            @Override public void on(int v) { Prefs.setDefaultMinutes(MainActivity.this, v); render(); }
        }));
        root.addView(card, cardLp());
    }

    // ---- per contact / line

    private final Map<String, Bitmap> avatarCache = new HashMap<String, Bitmap>();

    private Bitmap avatarFor(String name, String photoUri, int sizePx) {
        String key = (photoUri != null ? photoUri : "") + "|" + name + "|" + sizePx;
        Bitmap b = avatarCache.get(key);
        if (b == null) {
            b = ContactUtil.avatar(this, photoUri, name, sizePx);
            avatarCache.put(key, b);
        }
        return b;
    }

    private ImageView avatarView(Bitmap b, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(b);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private String displayName(Prefs.Rule r, ContactUtil.Contact k) {
        if (r.name != null && !r.name.trim().isEmpty()) return r.name.trim();
        if (k != null && !k.name.isEmpty()) return k.name;
        return "ללא שם";
    }

    // ---- per contact / line

    private void rulesCard() {
        LinearLayout card = card();
        card.addView(title("קווים ואנשי קשר מיוחדים"));
        card.addView(desc("למשל קו נייעס: 20 דקות. אם שמעת יותר מזה, כנראה נרדמת."));
        final List<Prefs.Rule> rules = Prefs.rules(this);
        if (rules.isEmpty()) {
            TextView e = text("עדיין לא הוגדרו. לחץ \"מאנשי הקשר\" והוסף את הקווים ששומעים לפני השינה.", 15, MUTED, false);
            e.setPadding(dp(14), dp(14), dp(14), dp(14));
            e.setBackground(round(FIELD, dp(14), LINE, dp(1)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(10), 0, dp(4));
            card.addView(e, lp);
        }
        for (int i = 0; i < rules.size(); i++) {
            final int idx = i;
            Prefs.Rule r = rules.get(i);
            ContactUtil.Contact k = r.prefix ? null : ContactUtil.lookup(this, r.number);
            String name = displayName(r, k);
            LinearLayout row = hrow();
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(ripple(round(FIELD, dp(14), LINE, dp(1))));
            row.setClickable(true);
        row.setFocusable(true);
            row.addView(avatarView(avatarFor(name, k != null ? k.photoUri : null, dp(44)), 44));
            LinearLayout col = vcol();
            col.setPadding(dp(12), 0, dp(8), 0);
            col.addView(text(name, 17, TEXT, true));
            col.addView(text((r.prefix ? "מתחיל ב: " : "") + "⁦" + r.number + "⁩", 14, MUTED, false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            TextView b = text(Prefs.fmt(r.minutes), 15, r.minutes > 0 ? ON_ACCENT : TEXT, true);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            b.setBackground(r.minutes > 0 ? round(ACCENT, dp(20), 0, 0) : round(0, dp(20), MUTED, dp(1)));
            row.addView(b);
            row.setContentDescription(name + ", " + Prefs.fmt(r.minutes) + ". לחץ לעריכה");
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { editRule(idx, null); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(10), 0, 0);
            card.addView(row, lp);
        }
        if (!rules.isEmpty()) {
            TextView hint = text("לחץ על שורה כדי לשנות זמן או למחוק", 13, MUTED, false);
            hint.setPadding(0, dp(8), 0, 0);
            card.addView(hint);
        }
        LinearLayout btns = hrow();
        btns.setPadding(0, dp(14), 0, 0);
        TextView pick = button("＋ מאנשי הקשר", true);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ContactUtil.hasPerm(MainActivity.this)) openContactPicker();
                else requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS_FOR_PICKER);
            }
        });
        TextView manual = button("＋ מספר ידני", false);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editRule(-1, null); }
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(50), 1);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(50), 1);
        c.setMarginStart(dp(10));
        btns.addView(pick, a);
        btns.addView(manual, c);
        card.addView(btns);
        root.addView(card, cardLp());
    }

    /** Fallback when contacts permission was refused: the phone's own picker. */
    private void systemPicker() {
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try { startActivityForResult(i, REQ_CONTACT); }
        catch (ActivityNotFoundException e) { toast("לא נמצאו אנשי קשר במכשיר. הוסף מספר ידנית."); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_CONTACT || res != RESULT_OK || data == null || data.getData() == null) return;
        Prefs.Rule r = new Prefs.Rule();
        Cursor cur = null;
        try {
            cur = getContentResolver().query(data.getData(), new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                r.name = cur.getString(0) != null ? cur.getString(0) : "";
                r.number = cur.getString(1) != null ? cur.getString(1) : "";
            }
        } catch (Exception e) {
            toast("לא הצלחתי לקרוא את איש הקשר");
        } finally {
            if (cur != null) cur.close();
        }
        r.minutes = 20;
        editRule(-1, r);
    }

    // ---- full screen sheets

    static final class Sheet {
        Dialog dialog;
        LinearLayout body;
        LinearLayout bottom;
    }

    private Sheet sheet(String titleText) {
        final Sheet sh = new Sheet();
        sh.dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout frame = vcol();
        frame.setBackgroundColor(BG);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top = hrow();
        top.setPadding(dp(16), dp(14), dp(16), dp(10));
        TextView t = text(titleText, 23, TEXT, true);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = roundBtn("✕", "סגור");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sh.dialog.dismiss(); }
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(46), dp(46)));
        frame.addView(top);

        View line = new View(this);
        line.setBackgroundColor(ACCENT);
        line.setAlpha(0.5f);
        frame.addView(line, new LinearLayout.LayoutParams(-1, dp(2)));

        sh.body = vcol();
        sh.body.setPadding(dp(16), dp(8), dp(16), 0);
        frame.addView(sh.body, new LinearLayout.LayoutParams(-1, 0, 1));

        sh.bottom = vcol();
        sh.bottom.setPadding(dp(16), dp(8), dp(16), dp(16));
        frame.addView(sh.bottom);

        sh.dialog.setContentView(frame);
        if (sh.dialog.getWindow() != null) {
            sh.dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            sh.dialog.getWindow().setStatusBarColor(BG);
            sh.dialog.getWindow().setNavigationBarColor(BG);
        }
        return sh;
    }

    private int ruleIndexFor(List<Prefs.Rule> rules, String number) {
        String n = Prefs.normalize(number);
        for (int i = 0; i < rules.size(); i++) {
            Prefs.Rule o = rules.get(i);
            if (!o.prefix && Prefs.normalize(o.number).equals(n)) return i;
        }
        return -1;
    }

    private void openContactPicker() {
        final List<ContactUtil.Contact> all = ContactUtil.all(this);
        final List<ContactUtil.Contact> shown = new ArrayList<ContactUtil.Contact>(all);
        final List<Prefs.Rule> rules = Prefs.rules(this);
        final Sheet sh = sheet("בחר איש קשר");

        final EditText search = field("", "חיפוש לפי שם או מספר", InputType.TYPE_CLASS_TEXT);
        sh.body.addView(search, fieldLp());

        if (all.isEmpty()) {
            TextView e = text("לא נמצאו אנשי קשר עם מספר טלפון. אפשר להוסיף מספר ידנית.", 16, MUTED, false);
            e.setPadding(0, dp(24), 0, 0);
            e.setGravity(Gravity.CENTER);
            sh.body.addView(e);
        }

        final ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View convert, ViewGroup parent) {
                ContactUtil.Contact k = shown.get(p);
                LinearLayout row = hrow();
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setPadding(dp(6), dp(10), dp(6), dp(10));
                row.addView(avatarView(avatarFor(k.name, k.photoUri, dp(48)), 48));
                LinearLayout col = vcol();
                col.setPadding(dp(12), 0, dp(8), 0);
                col.addView(text(k.name.isEmpty() ? "ללא שם" : k.name, 17, TEXT, true));
                col.addView(text("⁦" + k.number + "⁩", 14, MUTED, false));
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
                int ri = ruleIndexFor(rules, k.number);
                if (ri >= 0) {
                    TextView b = text(Prefs.fmt(rules.get(ri).minutes), 13, ON_ACCENT, true);
                    b.setPadding(dp(10), dp(4), dp(10), dp(4));
                    b.setBackground(round(ACCENT, dp(16), 0, 0));
                    row.addView(b);
                }
                return row;
            }
        };
        lv.setAdapter(ad);
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(-1, 0, 1);
        lvp.setMargins(0, dp(6), 0, 0);
        sh.body.addView(lv, lvp);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                String qd = Prefs.normalize(q);
                shown.clear();
                for (ContactUtil.Contact k : all) {
                    if (q.isEmpty() || k.name.toLowerCase().contains(q)
                            || (!qd.isEmpty() && Prefs.normalize(k.number).contains(qd))) shown.add(k);
                }
                ad.notifyDataSetChanged();
            }
        });

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int p, long id) {
                ContactUtil.Contact k = shown.get(p);
                sh.dialog.dismiss();
                int ri = ruleIndexFor(rules, k.number);
                if (ri >= 0) { editRule(ri, null); return; }
                Prefs.Rule r = new Prefs.Rule();
                r.name = k.name;
                r.number = k.number;
                r.minutes = 20;
                editRule(-1, r);
            }
        });

        TextView manual = button("＋ הקלד מספר ידנית", false);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sh.dialog.dismiss(); editRule(-1, null); }
        });
        sh.bottom.addView(manual, new LinearLayout.LayoutParams(-1, dp(50)));
        sh.dialog.show();
    }

    private void editRule(final int index, Prefs.Rule preset) {
        final List<Prefs.Rule> rules = Prefs.rules(this);
        final Prefs.Rule r = index >= 0 ? rules.get(index) : (preset != null ? preset : new Prefs.Rule());
        final int[] minutes = {r.minutes};
        final boolean[] mute = {false};
        final Sheet sh = sheet(index >= 0 ? "עריכת קו" : "קו חדש");

        ScrollView sv = new ScrollView(this);
        LinearLayout box = vcol();
        box.setPadding(0, 0, 0, dp(12));
        sv.addView(box);
        sh.body.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        // who is this
        ContactUtil.Contact k = r.prefix ? null : ContactUtil.lookup(this, r.number);
        if (!Prefs.normalize(r.number).isEmpty()) {
            LinearLayout who = hrow();
            who.setPadding(dp(14), dp(12), dp(14), dp(12));
            who.setBackground(round(CARD, dp(16), LINE, dp(1)));
            String nm = displayName(r, k);
            who.addView(avatarView(avatarFor(nm, k != null ? k.photoUri : null, dp(64)), 64));
            LinearLayout col = vcol();
            col.setPadding(dp(14), 0, dp(8), 0);
            col.addView(text(nm, 20, TEXT, true));
            col.addView(text("⁦" + r.number + "⁩", 15, MUTED, false));
            who.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.setMargins(0, dp(8), 0, 0);
            box.addView(who, wl);
        }

        // the important part first: how long
        TextView q = text("כמה זמן עד הניתוק?", 19, TEXT, true);
        q.setPadding(0, dp(16), 0, 0);
        box.addView(q);
        final LinearLayout holder = vcol();
        box.addView(holder);

        box.addView(fieldLabel("או הקלד מספר דקות"));
        final EditText minField = field(minutes[0] > 0 ? String.valueOf(minutes[0]) : "", "למשל 25", InputType.TYPE_CLASS_NUMBER);
        minField.setTextDirection(View.TEXT_DIRECTION_LTR);
        minField.setGravity(Gravity.CENTER);
        box.addView(minField, fieldLp());

        final IntCb[] cb = new IntCb[1];
        final Runnable redraw = new Runnable() {
            @Override public void run() {
                holder.removeAllViews();
                holder.addView(stepper(minutes[0], 0, 600, 5, cb[0]));
                holder.addView(chips(new int[]{0, 10, 15, 20, 30, 45, 60, 90, 120}, minutes[0], cb[0]));
            }
        };
        cb[0] = new IntCb() {
            @Override public void on(int v) {
                minutes[0] = v;
                redraw.run();
                mute[0] = true;
                minField.setText(v > 0 ? String.valueOf(v) : "");
                mute[0] = false;
            }
        };
        redraw.run();
        minField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (mute[0]) return;
                try {
                    int v = Integer.parseInt(s.toString().trim());
                    minutes[0] = Math.max(0, Math.min(600, v));
                    redraw.run();
                } catch (Exception ignored) {}
            }
        });

        // details
        box.addView(fieldLabel("שם (למשל: קו נייעס)"));
        final EditText name = field(r.name, "שם", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name, fieldLp());

        box.addView(fieldLabel("מספר טלפון"));
        final EditText num = field(r.number, "למשל 0791234567", InputType.TYPE_CLASS_PHONE);
        num.setTextDirection(View.TEXT_DIRECTION_LTR);
        num.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        box.addView(num, fieldLp());

        final CheckBox prefix = new CheckBox(this);
        prefix.setText("כל המספרים שמתחילים כך (למשל כל קווי 079)");
        prefix.setTextColor(MUTED);
        prefix.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        prefix.setButtonTintList(ColorStateList.valueOf(ACCENT));
        prefix.setChecked(r.prefix);
        prefix.setPadding(0, dp(8), 0, 0);
        box.addView(prefix);

        // bottom: big save
        TextView save = button("✓ שמור", true);
        save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = num.getText().toString().trim();
                if (Prefs.normalize(n).isEmpty()) {
                    num.setError("צריך מספר טלפון");
                    num.requestFocus();
                    return;
                }
                r.name = name.getText().toString().trim();
                r.number = n;
                r.prefix = prefix.isChecked();
                r.minutes = minutes[0];
                if (index < 0) {
                    for (int i = rules.size() - 1; i >= 0; i--) {
                        Prefs.Rule o = rules.get(i);
                        if (o.prefix == r.prefix && Prefs.normalize(o.number).equals(Prefs.normalize(n))) rules.remove(i);
                    }
                    rules.add(r);
                }
                Prefs.saveRules(MainActivity.this, rules);
                sh.dialog.dismiss();
                render();
                String nm = r.name.isEmpty() ? n : r.name;
                toast("✓ נשמר: " + nm + " · " + Prefs.fmt(r.minutes));
            }
        });
        sh.bottom.addView(save, new LinearLayout.LayoutParams(-1, dp(56)));
        if (index >= 0) {
            TextView del = text("מחק את הקו", 16, DANGER, true);
            del.setGravity(Gravity.CENTER);
            del.setClickable(true);
        del.setFocusable(true);
            del.setBackground(ripple(round(0, dp(25), 0, 0)));
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    rules.remove(index);
                    Prefs.saveRules(MainActivity.this, rules);
                    sh.dialog.dismiss();
                    render();
                    toast("הקו נמחק");
                }
            });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(48));
            dl.setMargins(0, dp(6), 0, 0);
            sh.bottom.addView(del, dl);
        }
        sh.dialog.show();
    }

    // ---- always ready in the background (also after restarting the phone)

    private void watchCard() {
        LinearLayout card = card();
        LinearLayout row = hrow();
        LinearLayout col = vcol();
        final boolean on = Prefs.watch(this);
        col.addView(title("פועל גם אחרי הפעלה מחדש"));
        col.addView(text(on ? "פעיל" : "כבוי", 14, on ? OK : DANGER, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setContentDescription("פועל גם אחרי הפעלה מחדש");
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFF8A8172}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{0x99E8A33D, 0x553D382E}));
        sw.setScaleX(1.25f);
        sw.setScaleY(1.25f);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setWatch(MainActivity.this, ((Switch) v).isChecked());
                render();
            }
        });
        row.addView(sw);
        card.addView(row);
        card.addView(desc("כשהטלפון נדלק, האפליקציה מתחילה לפעול לבד ומחכה לשיחה הבאה, בלי שתצטרך לפתוח אותה. "
                + "תופיע התראה שקטה \"נתק בזמן פועל ברקע\". היא כמעט לא צורכת סוללה."));
        if (!on) card.addView(desc("כשזה כבוי, בחלק מהטלפונים האפליקציה לא תעבוד אחרי הפעלה מחדש עד שתפתח אותה."));
        root.addView(card, cardLp());
    }

    // ---- energy saving

    private void ecoCard() {
        LinearLayout card = card();
        LinearLayout row = hrow();
        LinearLayout col = vcol();
        final boolean on = Prefs.eco(this);
        TextView t = title("מצב חיסכון בסוללה");
        col.addView(t);
        col.addView(text(on ? "פעיל" : "כבוי", 14, on ? OK : MUTED, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setContentDescription("מצב חיסכון בסוללה");
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFF8A8172}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{0x99E8A33D, 0x553D382E}));
        sw.setScaleX(1.25f);
        sw.setScaleY(1.25f);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean val = ((Switch) v).isChecked();
                Prefs.setEco(MainActivity.this, val);
                toast(val ? "✓ מצב חיסכון הופעל. יחול מהשיחה הבאה" : "✓ מצב חיסכון כובה. יחול מהשיחה הבאה");
                render();
            }
        });
        row.addView(sw);
        card.addView(row);
        card.addView(desc("בזמן שיחה: בלי ספירה לאחור חיה, בלי תמונות, והאפליקציה לא מחזיקה את המעבד ער. "
                + "ההתראה לפני הניתוק והניתוק עצמו ממשיכים לעבוד, דרך שעון מעורר של המערכת."));
        card.addView(desc("שים לב: גם בלי מצב חיסכון ההבדל בסוללה קטן, כי הטיימר פועל רק בזמן שיחה. "
                + "בזמן שיחה עם טיימר ייתכן שיופיע סמל שעון מעורר בשורת המצב."));
        root.addView(card, cardLp());
    }

    // ---- warning settings

    private void warnCard() {
        LinearLayout card = card();
        card.addView(title("התראה לפני ניתוק"));
        card.addView(desc("ההתראה מגיעה ברטט עדין בלי צליל, כדי לא להעיר אותך אם כבר נרדמת."));

        card.addView(fieldLabel("להזהיר לפני הניתוק"));
        card.addView(stepper(Prefs.warnMinutes(this), 1, 30, 1, new IntCb() {
            @Override public void on(int v) { Prefs.setWarnMinutes(MainActivity.this, v); render(); }
        }));

        card.addView(fieldLabel("הכפתור \"הוסף זמן\" מוסיף"));
        card.addView(stepper(Prefs.snoozeMinutes(this), 1, 120, 5, new IntCb() {
            @Override public void on(int v) { Prefs.setSnoozeMinutes(MainActivity.this, v); render(); }
        }));

        LinearLayout btns = hrow();
        btns.setPadding(0, dp(16), 0, 0);
        TextView test = button("הצג התראת דוגמה", true);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!notifOk()) { toast("קודם צריך לאשר התראות"); return; }
                CallTimerService.postWarning(MainActivity.this, "בדיקה · קו נייעס",
                        Prefs.warnMinutes(MainActivity.this) * 60000L, true,
                        ContactUtil.letter("קו נייעס", dp(64)));
                toast("נשלחה התראת דוגמה. משוך את שורת ההתראות למטה.");
            }
        });
        TextView sound = button("צליל ורטט", false);
        sound.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                        .putExtra(Settings.EXTRA_CHANNEL_ID, CallTimerService.CH_WARN);
                startSafe(i, appDetails());
            }
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(50), 1);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(50), 1);
        c.setMarginStart(dp(10));
        btns.addView(test, a);
        btns.addView(sound, c);
        card.addView(btns);
        root.addView(card, cardLp());
    }

    // ---------------------------------------------------------------- widgets

    /** [ + ]  value  [ − ] ; jumps to multiples of step (moves by 1 below 10 for fine steppers). */
    private View stepper(final int value, final int min, final int max, final int step, final IntCb cb) {
        LinearLayout row = hrow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));
        TextView minus = roundBtn("−", "פחות");
        TextView plus = roundBtn("+", "יותר");
        TextView val = text(Prefs.fmt(value), 26, value > 0 ? ACCENT : TEXT, true);
        val.setGravity(Gravity.CENTER);
        val.setTypeface(Typeface.create("serif", Typeface.BOLD));
        // in RTL the first child sits on the right: "+" on the right, "−" on the left
        row.addView(plus, new LinearLayout.LayoutParams(dp(54), dp(54)));
        row.addView(val, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(minus, new LinearLayout.LayoutParams(dp(54), dp(54)));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = (step <= 5 && value < 10) ? value + 1 : (value / step + 1) * step;
                cb.on(Math.min(max, Math.max(min, n)));
            }
        });
        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = (step <= 5 && value <= 10) ? value - 1 : ((value - 1) / step) * step;
                if (step <= 5 && value > 10 && n < 10) n = 10;
                cb.on(Math.min(max, Math.max(min, n)));
            }
        });
        return row;
    }

    private View chips(int[] values, int current, final IntCb cb) {
        LinearLayout wrap = vcol();
        wrap.setPadding(0, dp(6), 0, 0);
        LinearLayout row = null;
        for (int i = 0; i < values.length; i++) {
            if (i % 3 == 0) {
                row = hrow();
                wrap.addView(row);
            }
            final int v = values[i];
            boolean sel = v == current;
            TextView chip = text(Prefs.fmt(v), 15, sel ? ON_ACCENT : TEXT, sel);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(ripple(sel ? round(ACCENT, dp(22), 0, 0) : round(FIELD, dp(22), LINE, dp(1))));
            chip.setClickable(true);
        chip.setFocusable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { cb.on(v); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(chip, lp);
        }
        if (row != null) {
            for (int k = values.length % 3; k != 0 && k < 3; k++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(46), 1));
            }
        }
        return wrap;
    }

    private TextView roundBtn(String s, String a11y) {
        TextView t = text(s, 26, TEXT, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(FIELD);
        g.setStroke(dp(2), ACCENT);
        t.setBackground(ripple(g));
        t.setClickable(true);
        t.setFocusable(true);
        t.setContentDescription(a11y);
        return t;
    }

    private TextView button(String s, boolean primary) {
        TextView t = text(s, 16, primary ? ON_ACCENT : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setMinHeight(dp(44));
        t.setBackground(ripple(primary ? round(ACCENT, dp(25), 0, 0) : round(0, dp(25), ACCENT, dp(2))));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private TextView badge(String s, int color, boolean filled) {
        TextView t = text(s, 17, filled ? ON_ACCENT : color, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (filled) g.setColor(color); else g.setStroke(dp(2), color);
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = vcol();
        c.setPadding(dp(18), dp(16), dp(18), dp(18));
        c.setBackground(round(CARD, dp(20), LINE, dp(1)));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        return lp;
    }

    private TextView title(String s) {
        TextView t = text(s, 20, TEXT, true);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        return t;
    }

    private TextView desc(String s) {
        TextView t = text(s, 14, MUTED, false);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private TextView fieldLabel(String s) {
        TextView t = text(s, 15, MUTED, true);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private EditText field(String value, String hint, int type) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF7D7465);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        e.setSingleLine(true);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setBackground(round(FIELD, dp(12), LINE, dp(1)));
        return e;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(4), 0, 0);
        return lp;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout vcol() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private GradientDrawable round(int fill, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }

    private Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x40F3E9D2), content, null);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
