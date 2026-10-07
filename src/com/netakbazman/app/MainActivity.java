package com.netakbazman.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
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
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

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
        TextView f = text("עובד בלי אינטרנט · שום מידע לא יוצא מהמכשיר · גרסה 1.0", 13, MUTED, false);
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
        p.add(Manifest.permission.ANSWER_PHONE_CALLS);
        p.add(Manifest.permission.PROCESS_OUTGOING_CALLS);
        if (Build.VERSION.SDK_INT >= 33) p.add("android.permission.POST_NOTIFICATIONS");
        return p.toArray(new String[0]);
    }

    private boolean hasPhonePerms() {
        String[] need = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.PROCESS_OUTGOING_CALLS};
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
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    private boolean flag(String k) { return Prefs.sp(this).getBoolean(k, false); }
    private void setFlag(String k) { Prefs.sp(this).edit().putBoolean(k, true).apply(); }

    private void setupCard() {
        boolean p = hasPhonePerms(), n = notifOk(), bat = batteryOk();
        boolean xi = isXiaomi();
        boolean auto = !xi || flag("autostartVisited");
        boolean miBat = !xi || flag("miBatteryVisited");
        boolean all = p && n && bat && auto && miBat;

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

        card.addView(step(1, p, "הרשאות שיחה", "כדי לזהות שיחה ולנתק אותה בזמן", "אשר", new View.OnClickListener() {
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
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startSafe(i, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        if (xi) {
            card.addView(step(4, auto, "הפעלה אוטומטית (שיאומי)", "הדלק את המתג ליד \"נתק בזמן\"", "פתח", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setFlag("autostartVisited");
                    Intent i = new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                    startSafe(i, appDetails());
                }
            }));
            card.addView(step(5, miBat, "חיסכון בסוללה (שיאומי)", "בחר: חיסכון בסוללה ← ללא הגבלות", "פתח", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setFlag("miBatteryVisited");
                    startSafe(appDetails(), null);
                }
            }));
        }
        root.addView(card, cardLp());
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

    private void rulesCard() {
        LinearLayout card = card();
        card.addView(title("קווים ואנשי קשר מיוחדים"));
        card.addView(desc("למשל קו נייעס: 20 דקות. אם שמעת יותר מזה, כנראה נרדמת."));
        final List<Prefs.Rule> rules = Prefs.rules(this);
        if (rules.isEmpty()) {
            TextView e = text("עדיין לא הוגדרו. הוסף את הקווים ששומעים לפני השינה.", 15, MUTED, false);
            e.setPadding(dp(14), dp(14), dp(14), dp(14));
            e.setBackground(round(FIELD, dp(14), LINE, dp(1)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(10), 0, dp(4));
            card.addView(e, lp);
        }
        for (int i = 0; i < rules.size(); i++) {
            final int idx = i;
            Prefs.Rule r = rules.get(i);
            LinearLayout row = hrow();
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(ripple(round(FIELD, dp(14), LINE, dp(1))));
            row.setClickable(true);
            LinearLayout col = vcol();
            String name = r.name == null || r.name.trim().isEmpty() ? "ללא שם" : r.name.trim();
            col.addView(text(name, 17, TEXT, true));
            TextView num = text((r.prefix ? "מתחיל ב: " : "") + "⁦" + r.number + "⁩", 14, MUTED, false);
            col.addView(num);
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
        LinearLayout btns = hrow();
        btns.setPadding(0, dp(14), 0, 0);
        TextView pick = button("＋ מאנשי הקשר", true);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                try { startActivityForResult(i, REQ_CONTACT); }
                catch (ActivityNotFoundException e) { toast("לא נמצאו אנשי קשר במכשיר. הוסף מספר ידנית."); }
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

    private void editRule(final int index, Prefs.Rule preset) {
        final List<Prefs.Rule> rules = Prefs.rules(this);
        final Prefs.Rule r = index >= 0 ? rules.get(index) : (preset != null ? preset : new Prefs.Rule());
        final int[] minutes = {r.minutes};

        LinearLayout box = vcol();
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));

        TextView head = text(index >= 0 ? "עריכת קו" : "קו חדש", 22, TEXT, true);
        head.setTypeface(Typeface.create("serif", Typeface.BOLD));
        box.addView(head);

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
        box.addView(prefix);

        box.addView(fieldLabel("לנתק אחרי"));
        final LinearLayout holder = vcol();
        box.addView(holder);
        final Runnable[] redraw = new Runnable[1];
        redraw[0] = new Runnable() {
            @Override public void run() {
                holder.removeAllViews();
                IntCb cb = new IntCb() {
                    @Override public void on(int v) { minutes[0] = v; redraw[0].run(); }
                };
                holder.addView(stepper(minutes[0], 0, 600, 5, cb));
                holder.addView(chips(new int[]{0, 10, 15, 20, 30, 45, 60, 90, 120}, minutes[0], cb));
            }
        };
        redraw[0].run();

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder bld = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setView(sv)
                .setPositiveButton("שמור", null)
                .setNegativeButton("ביטול", null);
        if (index >= 0) {
            bld.setNeutralButton("מחק", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    rules.remove(index);
                    Prefs.saveRules(MainActivity.this, rules);
                    render();
                }
            });
        }
        final AlertDialog d = bld.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface di) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
                if (d.getButton(AlertDialog.BUTTON_NEUTRAL) != null) d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DANGER);
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String n = num.getText().toString().trim();
                        if (Prefs.normalize(n).isEmpty()) {
                            num.setError("צריך מספר טלפון");
                            return;
                        }
                        r.name = name.getText().toString().trim();
                        r.number = n;
                        r.prefix = prefix.isChecked();
                        r.minutes = minutes[0];
                        if (index < 0) {
                            // replace an existing rule for the same number
                            for (int i = rules.size() - 1; i >= 0; i--) {
                                Prefs.Rule o = rules.get(i);
                                if (o.prefix == r.prefix && Prefs.normalize(o.number).equals(Prefs.normalize(n))) rules.remove(i);
                            }
                            rules.add(r);
                        }
                        Prefs.saveRules(MainActivity.this, rules);
                        d.dismiss();
                        render();
                    }
                });
            }
        });
        d.show();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(round(CARD, dp(22), LINE, dp(1)));
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
                        Prefs.warnMinutes(MainActivity.this) * 60000L, true);
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
