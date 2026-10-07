package com.netakbazman.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** All settings, stored locally on the device. */
public final class Prefs {
    private Prefs() {}

    public static final class Rule {
        public String name = "";
        public String number = "";
        public int minutes = 20;      // 0 = never disconnect
        public boolean prefix = false; // match every number that starts with this
    }

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("main", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", true); }
    public static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    public static int defaultMinutes(Context c) { return sp(c).getInt("defMin", 120); }
    public static void setDefaultMinutes(Context c, int v) { sp(c).edit().putInt("defMin", v).apply(); }

    public static int warnMinutes(Context c) { return sp(c).getInt("warnMin", 5); }
    public static void setWarnMinutes(Context c, int v) { sp(c).edit().putInt("warnMin", v).apply(); }

    public static int snoozeMinutes(Context c) { return sp(c).getInt("snoozeMin", 10); }
    public static void setSnoozeMinutes(Context c, int v) { sp(c).edit().putInt("snoozeMin", v).apply(); }

    public static List<Rule> rules(Context c) {
        List<Rule> out = new ArrayList<Rule>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("rules", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Rule r = new Rule();
                r.name = o.optString("name", "");
                r.number = o.optString("number", "");
                r.minutes = o.optInt("minutes", 20);
                r.prefix = o.optBoolean("prefix", false);
                out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveRules(Context c, List<Rule> rules) {
        JSONArray a = new JSONArray();
        try {
            for (Rule r : rules) {
                JSONObject o = new JSONObject();
                o.put("name", r.name);
                o.put("number", r.number);
                o.put("minutes", r.minutes);
                o.put("prefix", r.prefix);
                a.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString("rules", a.toString()).apply();
    }

    /** Keeps digits only and turns +972 / 972 into a local 0 prefix. */
    public static String normalize(String n) {
        if (n == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (ch >= '0' && ch <= '9') b.append(ch);
        }
        String d = b.toString();
        if (d.startsWith("972") && d.length() >= 11) d = "0" + d.substring(3);
        return d;
    }

    private static String tail(String d) {
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    /** Finds the rule for a number: an exact number first, then the longest matching prefix. */
    public static Rule match(Context c, String number) {
        String n = normalize(number);
        if (n.isEmpty()) return null;
        Rule best = null;
        int bestLen = -1;
        for (Rule r : rules(c)) {
            String rn = normalize(r.number);
            if (rn.isEmpty()) continue;
            if (!r.prefix) {
                boolean same = (n.length() >= 9 && rn.length() >= 9) ? tail(n).equals(tail(rn)) : n.equals(rn);
                if (same) return r;
            } else if (n.startsWith(rn) && rn.length() > bestLen) {
                best = r;
                bestLen = rn.length();
            }
        }
        return best;
    }

    /** Human friendly Hebrew duration. */
    public static String fmt(int m) {
        if (m <= 0) return "ללא ניתוק";
        if (m == 1) return "דקה אחת";
        if (m < 60) return m + " דקות";
        int h = m / 60, r = m % 60;
        String hs = h == 1 ? "שעה" : h == 2 ? "שעתיים" : h + " שעות";
        if (r == 0) return hs;
        if (r == 30) return hs + " וחצי";
        if (r == 15) return hs + " ורבע";
        return hs + " ו-" + r + " דק'";
    }
}
