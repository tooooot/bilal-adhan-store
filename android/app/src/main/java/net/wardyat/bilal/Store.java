package net.wardyat.bilal;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** تخزين الجدول والإعدادات (SharedPreferences). الجدول يرسله التطبيق (JS) لعدة أيام قادمة. */
final class Store {
    static final String PREFS = "bilal";
    static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** عناصر: t=وقت بالملّي ثانية، n=مفتاح (fajr..)، l=الاسم العربي، k=p|sun|imsak، a=1 يشغّل الأذان، s=دقائق الصامت */
    static JSONArray items(Context c) {
        try { return new JSONArray(sp(c).getString("items", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    static void saveItems(Context c, String json) { sp(c).edit().putString("items", json).apply(); }
    static String fajrSound(Context c) { return sp(c).getString("fajrSound", ""); }
    static String sound(Context c) { return sp(c).getString("sound", "takbir_haram"); }
    static String theme(Context c) { return sp(c).getString("theme", "auto"); }

    /** أول عنصر صلاة (k=p) بعد الآن، أو null */
    static JSONObject nextPrayer(Context c) {
        JSONArray a = items(c); long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && "p".equals(o.optString("k")) && o.optLong("t") > now) return o;
        }
        return null;
    }
    static JSONObject find(Context c, long t) {
        JSONArray a = items(c);
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && o.optLong("t") == t) return o; }
        return null;
    }
    static String ui(android.content.Context c, String k, String def) { return sp(c).getString("ui_" + k, def); }
}
