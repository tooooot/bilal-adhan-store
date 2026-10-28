package net.wardyat.bilal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;

/** أربعة ودّجات: صف 1×4 (الصلوات الست)، صغير 2×2، قائمة المواقيت 4×3، مشهد بلال 4×2.
 *  الألوان: تلقائي (يتبع وضع الجوال ليلي/نهاري) أو داكن/فاتح/شفاف، وللمستخدم تخصيص الخلفية والنص والتمييز والشفافية. */
public abstract class BilalWidget extends AppWidgetProvider {
    abstract int layout();
    abstract void fill(Context c, RemoteViews v, Pal p);

    // ——— الألوان ———
    static final class Pal {
        int bg, bgAlpha, fg, accent, onAccent, boxUp; boolean clear;
    }
    static int parse(String s, int def) { try { return s == null || s.isEmpty() ? def : Color.parseColor(s); } catch (Exception e) { return def; } }
    static double lum(int c) { return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0; }
    static int rgb(int c) { return 0xFF000000 | (c & 0xFFFFFF); }
    static int alpha(int c, int a) { return (a << 24) | (c & 0xFFFFFF); }

    static Pal pal(Context c) {
        String th = Store.theme(c);
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!th.equals("dark") && !th.equals("light") && !th.equals("clear")) th = night ? "dark" : "light";   // تلقائي
        Pal p = new Pal(); p.clear = th.equals("clear");
        p.bg = th.equals("light") ? 0xFFF3E6C8 : p.clear ? 0xFF000000 : 0xFF1A120B;
        p.bgAlpha = th.equals("light") ? 0xF2 : p.clear ? 0x8C : 0xF0;
        p.fg = th.equals("light") ? 0xFF2B1A0C : 0xFFF3E6C8;
        p.accent = th.equals("light") ? 0xFF7A1F14 : 0xFFE0B45A;
        int cbg = parse(Store.sp(c).getString("wbg", ""), 0);
        if (cbg != 0) { p.bg = rgb(cbg); p.clear = false; p.bgAlpha = 0xF0; p.fg = lum(p.bg) > 0.55 ? 0xFF2B1A0C : 0xFFF3E6C8; }   // النص يتبع سطوع الخلفية
        int cal = Store.sp(c).getInt("walpha", -1); if (cal >= 10 && cal <= 100) p.bgAlpha = cal * 255 / 100;
        p.fg = parse(Store.sp(c).getString("wfg", ""), p.fg);
        p.accent = parse(Store.sp(c).getString("wacc", ""), p.accent);
        p.onAccent = lum(p.accent) > 0.6 ? 0xFF2B1A0C : 0xFFFFFFFF;
        p.boxUp = alpha(p.fg, 0x22);
        return p;
    }

    static void paintBg(RemoteViews v, Pal p) {
        v.setInt(R.id.bgimg, "setColorFilter", rgb(p.bg)); v.setInt(R.id.bgimg, "setImageAlpha", p.bgAlpha);
        v.setInt(R.id.ring, "setColorFilter", rgb(p.accent)); v.setInt(R.id.ring, "setImageAlpha", p.clear ? 0 : 0xB0);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (Class<?> k : new Class<?>[]{Row.class, Small.class, Prayers.class, Scene.class}) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
            if (ids.length > 0) {
                try { ((BilalWidget) k.getDeclaredConstructor().newInstance()).onUpdate(c, m, ids); } catch (Exception ignored) {}
            }
        }
    }

    RemoteViews build(Context c) {
        Pal p = pal(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), layout());
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 5, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        fill(c, v, p);
        return v;
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(c)); }

    static void countdown(RemoteViews v, int viewId, JSONObject next, int color) {
        if (next == null) { v.setViewVisibility(viewId, View.GONE); return; }
        v.setViewVisibility(viewId, View.VISIBLE);
        long base = SystemClock.elapsedRealtime() + (next.optLong("t") - System.currentTimeMillis());
        v.setChronometer(viewId, base, null, true);
        v.setChronometerCountDown(viewId, true);
        v.setTextColor(viewId, color);
    }
    /** أرقام لاتينية كما في التطبيق (4:12 م) */
    static String clock(long t) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(t);
        int h = c.get(Calendar.HOUR); if (h == 0) h = 12;
        return h + ":" + String.format(Locale.ENGLISH, "%02d", c.get(Calendar.MINUTE)) + (c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م");
    }

    // ——— صف 1×4: الفجر الشروق الظهر العصر المغرب العشاء (من اليمين لليسار) ———
    public static class Row extends BilalWidget {
        int layout() { return R.layout.widget_row; }
        final int[] H = {R.id.rh1, R.id.rh2, R.id.rh3, R.id.rh4, R.id.rh5, R.id.rh6};
        final int[] N = {R.id.rn1, R.id.rn2, R.id.rn3, R.id.rn4, R.id.rn5, R.id.rn6};
        final int[] T = {R.id.rt1, R.id.rt2, R.id.rt3, R.id.rt4, R.id.rt5, R.id.rt6};
        final int[] C = {R.id.rc1, R.id.rc2, R.id.rc3, R.id.rc4, R.id.rc5, R.id.rc6};
        void fill(Context c, RemoteViews v, Pal p) {
            paintBg(v, p);
            long now = System.currentTimeMillis();
            JSONArray a = Store.items(c);
            // الصف يعرض يوم «الصلاة القادمة»؛ والقادم هنا يشمل الشروق أيضاً
            JSONObject next = null;
            for (int i = 0; i < a.length() && next == null; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && ("p".equals(o.optString("k")) || "sun".equals(o.optString("k"))) && o.optLong("t") > now) next = o;
            }
            long ref = next != null ? next.optLong("t") : now;
            Calendar day = Calendar.getInstance(); day.setTimeInMillis(ref);
            int doy = day.get(Calendar.DAY_OF_YEAR), yr = day.get(Calendar.YEAR), col = 0;
            for (int i = 0; i < a.length() && col < 6; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !("p".equals(o.optString("k")) || "sun".equals(o.optString("k")))) continue;
                Calendar d = Calendar.getInstance(); d.setTimeInMillis(o.optLong("t"));
                if (d.get(Calendar.DAY_OF_YEAR) != doy || d.get(Calendar.YEAR) != yr) continue;
                long t = o.optLong("t"); boolean isNext = next != null && t == next.optLong("t"), past = t <= now;
                v.setTextViewText(N[col], o.optString("l")); v.setTextViewText(T[col], clock(t));
                v.setViewVisibility(H[col], View.VISIBLE);
                if (isNext) {   // الصندوق القادم: لون التمييز + العدّاد التنازلي
                    v.setInt(H[col], "setColorFilter", rgb(p.accent)); v.setInt(H[col], "setImageAlpha", 0xFF);
                    v.setTextColor(N[col], p.onAccent); v.setTextColor(T[col], p.onAccent);
                    countdown(v, C[col], o, p.onAccent);
                } else if (past) {   // مضت: باهتة بلا خلفية
                    v.setViewVisibility(H[col], View.INVISIBLE);
                    v.setTextColor(N[col], alpha(p.fg, 0x80)); v.setTextColor(T[col], alpha(p.fg, 0x80));
                    v.setViewVisibility(C[col], View.GONE);
                } else {   // قادمة: صندوق بلون هادئ
                    v.setInt(H[col], "setColorFilter", rgb(p.fg)); v.setInt(H[col], "setImageAlpha", 0x26);
                    v.setTextColor(N[col], p.fg); v.setTextColor(T[col], p.fg);
                    v.setViewVisibility(C[col], View.GONE);
                }
                col++;
            }
        }
    }

    public static class Small extends BilalWidget {
        int layout() { return R.layout.widget_small; }
        void fill(Context c, RemoteViews v, Pal p) {
            paintBg(v, p); JSONObject next = Store.nextPrayer(c);
            v.setTextColor(R.id.title, p.fg); v.setTextColor(R.id.time, p.fg);
            v.setTextViewText(R.id.title, next == null ? "افتح التطبيق" : next.optString("l"));
            v.setTextViewText(R.id.time, next == null ? "" : clock(next.optLong("t")));
            countdown(v, R.id.cd, next, p.accent);
        }
    }

    public static class Scene extends BilalWidget {
        int layout() { return R.layout.widget_scene; }
        void fill(Context c, RemoteViews v, Pal p) {
            JSONObject next = Store.nextPrayer(c);
            v.setTextViewText(R.id.title, next == null ? "افتح التطبيق" : next.optString("l"));
            v.setTextViewText(R.id.time, next == null ? "" : clock(next.optLong("t")));
            countdown(v, R.id.cd, next, 0xFFFFFFFF);
            String n = next == null ? "fajr" : next.optString("n");
            int res = c.getResources().getIdentifier("scene_" + n, "drawable", c.getPackageName());
            v.setImageViewResource(R.id.scene, res != 0 ? res : R.mipmap.ic_launcher);
        }
    }

    public static class Prayers extends BilalWidget {
        int layout() { return R.layout.widget_list; }
        final int[] NAME = {R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.n5};
        final int[] TIME = {R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5};
        void fill(Context c, RemoteViews v, Pal p) {
            paintBg(v, p); JSONObject next = Store.nextPrayer(c);
            JSONArray a = Store.items(c);
            long ref = next != null ? next.optLong("t") : System.currentTimeMillis();
            Calendar day = Calendar.getInstance(); day.setTimeInMillis(ref);
            int doy = day.get(Calendar.DAY_OF_YEAR), yr = day.get(Calendar.YEAR), row = 0;
            for (int i = 0; i < a.length() && row < 5; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !"p".equals(o.optString("k"))) continue;
                Calendar d = Calendar.getInstance(); d.setTimeInMillis(o.optLong("t"));
                if (d.get(Calendar.DAY_OF_YEAR) != doy || d.get(Calendar.YEAR) != yr) continue;
                boolean isNext = next != null && o.optLong("t") == next.optLong("t");
                int colr = isNext ? p.accent : p.fg;
                v.setTextViewText(NAME[row], o.optString("l")); v.setTextColor(NAME[row], colr);
                v.setTextViewText(TIME[row], clock(o.optLong("t"))); v.setTextColor(TIME[row], colr);
                row++;
            }
            countdown(v, R.id.cd, next, p.accent);
        }
    }
}
