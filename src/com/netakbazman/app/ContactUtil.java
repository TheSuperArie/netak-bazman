package com.netakbazman.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.ContactsContract;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the phone's contacts (names + photos). Everything stays on the device. */
public final class ContactUtil {
    private ContactUtil() {}

    public static final class Contact {
        public String name = "";
        public String number = "";
        public String photoUri;
    }

    public static boolean hasPerm(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    /** All contacts with a phone number, sorted by name, one row per number. */
    public static List<Contact> all(Context c) {
        List<Contact> out = new ArrayList<Contact>();
        if (!hasPerm(c)) return out;
        Set<String> seen = new HashSet<String>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext()) {
                Contact k = new Contact();
                k.name = cur.getString(0) != null ? cur.getString(0) : "";
                k.number = cur.getString(1) != null ? cur.getString(1) : "";
                k.photoUri = cur.getString(2);
                String key = k.name + "|" + Prefs.normalize(k.number);
                if (Prefs.normalize(k.number).isEmpty() || !seen.add(key)) continue;
                out.add(k);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    /** Finds the contact saved for a phone number, or null. */
    public static Contact lookup(Context c, String number) {
        if (number == null || Prefs.normalize(number).isEmpty() || !hasPerm(c)) return null;
        Cursor cur = null;
        try {
            Uri u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cur = c.getContentResolver().query(u, new String[]{
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                Contact k = new Contact();
                k.name = cur.getString(0) != null ? cur.getString(0) : "";
                k.number = number;
                k.photoUri = cur.getString(1);
                return k;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }

    /** Round contact photo, or a round letter avatar when there is no photo. */
    public static Bitmap avatar(Context c, String photoUri, String name, int size) {
        Bitmap src = null;
        if (photoUri != null) {
            InputStream in = null;
            try {
                ContentResolver cr = c.getContentResolver();
                in = cr.openInputStream(Uri.parse(photoUri));
                src = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
            }
        }
        return src != null ? circle(src, size) : letter(name, size);
    }

    static Bitmap circle(Bitmap src, int size) {
        int s = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
        Bitmap scaled = Bitmap.createScaledBitmap(sq, size, size, true);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return out;
    }

    static Bitmap letter(String name, int size) {
        String l = "?";
        if (name != null) {
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (Character.isLetterOrDigit(ch)) { l = String.valueOf(ch).toUpperCase(); break; }
            }
        }
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFFE8A33D);
        cv.drawCircle(size / 2f, size / 2f, size / 2f, bg);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFF2A1E0C);
        t.setTextSize(size * 0.46f);
        t.setTypeface(Typeface.create("serif", Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        Rect r = new Rect();
        t.getTextBounds(l, 0, l.length(), r);
        cv.drawText(l, size / 2f, size / 2f + r.height() / 2f, t);
        return out;
    }
}
