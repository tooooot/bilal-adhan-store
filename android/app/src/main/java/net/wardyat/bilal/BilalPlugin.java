package net.wardyat.bilal;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** جسر الواجهة (JS) بالنظام: جدولة المنبّه، حالة الأذونات، فتح الإعدادات، تجربة الأذان، تحديث الودّجت. */
@CapacitorPlugin(name = "Bilal")
public class BilalPlugin extends Plugin {

    @PluginMethod public void schedule(PluginCall call) {
        Context c = getContext();
        Store.saveItems(c, call.getArray("items").toString());
        Store.sp(c).edit().putString("sound", call.getString("sound", "takbir_haram"))
            .putString("fajrSound", call.getString("fajrSound", ""))
            .putString("theme", call.getString("theme", "auto"))
            .putString("wbg", call.getString("wbg", "")).putString("wfg", call.getString("wfg", "")).putString("wacc", call.getString("wacc", ""))
            .putInt("walpha", call.getInt("walpha", -1))
            .putBoolean("boost", Boolean.TRUE.equals(call.getBoolean("boost", true))).apply();
        Scheduler.scheduleNext(c);
        BilalWidget.updateAll(c);
        call.resolve();
    }

    @PluginMethod public void status(PluginCall call) {
        Context c = getContext();
        JSObject o = new JSObject();
        o.put("exact", Scheduler.canExact(c));
        o.put("dnd", SilenceManager.granted(c));
        o.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        o.put("battery", pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        o.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(o);
    }

    @PluginMethod public void openSettings(PluginCall call) {
        Context c = getContext(); String type = call.getString("type", "");
        Intent i;
        switch (type) {
            case "exact":
                i = Build.VERSION.SDK_INT >= 31 ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + c.getPackageName()))
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName())); break;
            case "dnd": i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS); break;
            case "battery": i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS); break;
            default:
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); call.resolve(); }
        catch (Exception e) {
            try { c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); call.resolve(); }
            catch (Exception e2) { call.reject("تعذّر فتح الإعدادات"); }
        }
    }

    @PluginMethod public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null)
            androidx.core.app.ActivityCompat.requestPermissions(getActivity(), new String[]{"android.permission.POST_NOTIFICATIONS"}, 9);
        call.resolve();
    }

    /** أشرطة النظام بهوية التطبيق: خلف شريط الحالة لون الصفحة، وخلف شريط الإيماءات لون شريط التبويب؛ والأيقونات تتبع الفاتح/الداكن. */
    @PluginMethod public void setBars(PluginCall call) {
        final int top = android.graphics.Color.parseColor(call.getString("top", "#120d08"));
        final int bottom = android.graphics.Color.parseColor(call.getString("bottom", "#120b05"));
        final boolean lightTop = Boolean.TRUE.equals(call.getBoolean("lightTop", false));
        final android.app.Activity a = getActivity();
        if (a != null) a.runOnUiThread(() -> {
            try {
                android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    new android.graphics.drawable.ColorDrawable(top), new android.graphics.drawable.ColorDrawable(bottom)});
                int h = (int) (140 * a.getResources().getDisplayMetrics().density);
                ld.setLayerGravity(1, android.view.Gravity.BOTTOM); ld.setLayerHeight(1, h);
                a.getWindow().setBackgroundDrawable(ld);
                a.getWindow().setStatusBarColor(top); a.getWindow().setNavigationBarColor(bottom);
                android.view.View dv = a.getWindow().getDecorView();
                int f = dv.getSystemUiVisibility();
                dv.setSystemUiVisibility(lightTop ? (f | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (f & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
            } catch (Exception ignored) {}
        });
        call.resolve();
    }

    @PluginMethod public void preview(PluginCall call) {
        Context c = getContext();
        Store.sp(c).edit().putString("sound", call.getString("sound", Store.sound(c))).apply();
        Intent s = new Intent(c, AdhanService.class).putExtra("preview", true).putExtra("play", true);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        call.resolve();
    }

    @PluginMethod public void testAlarm(PluginCall call) {
        Context c = getContext();
        Store.sp(c).edit().putString("sound", call.getString("sound", Store.sound(c))).apply();
        long at = call.getLong("at", 0L) > 0 ? call.getLong("at", 0L) : System.currentTimeMillis() + call.getInt("delay", 0) * 1000L;
        Intent i = new Intent(c, AlarmReceiver.class).setAction("net.wardyat.bilal.TEST")
            .putExtra("n", call.getString("n", "fajr")).putExtra("label", call.getString("label", "الفجر"));
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 7010, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            android.app.PendingIntent sp = android.app.PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new android.app.AlarmManager.AlarmClockInfo(at, sp), pi);
        } catch (SecurityException e) { am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pi); }
        boolean fsi = true;   // أندرويد 14+: إذن الشاشة الكاملة فوق القفل قد يكون مغلقاً: نفتح صفحته
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                android.app.NotificationManager nmg = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (!nmg.canUseFullScreenIntent()) { fsi = false; c.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            }
        } catch (Exception ignored) {}
        call.resolve(new JSObject().put("fsi", fsi));
    }

    @PluginMethod public void setUi(PluginCall call) {   // نصوص الإشعار/الشاشة بلغة المستخدم (تأتي من الواجهة)
        android.content.SharedPreferences.Editor e = Store.sp(getContext()).edit();
        java.util.Iterator<String> it = call.getData().keys();
        while (it.hasNext()) { String k = it.next(); e.putString("ui_" + k, call.getString(k, "")); }
        e.apply(); call.resolve();
    }

    @PluginMethod public void stop(PluginCall call) {
        Context c = getContext();
        c.startService(new Intent(c, AdhanService.class).setAction("STOP"));
        call.resolve();
    }

    /** يفتح تطبيقاً آخر إن كان مثبّتاً، وإلا صفحته في متجر Play، وإلا رابط ويب. */
    @PluginMethod public void openApp(PluginCall call) {
        Context c = getContext(); String pkg = call.getString("package", ""); String url = call.getString("url", "");
        try {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) { c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); call.resolve(new JSObject().put("opened", "app")); return; }
        } catch (Exception ignored) {}
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); call.resolve(new JSObject().put("opened", "store")); return; }
        catch (Exception ignored) {}
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); call.resolve(new JSObject().put("opened", "web")); }
        catch (Exception e) { call.reject("تعذّر الفتح"); }
    }

    @PluginMethod public void isInstalled(PluginCall call) {
        boolean ok = false;
        try { ok = getContext().getPackageManager().getLaunchIntentForPackage(call.getString("package", "")) != null; } catch (Exception ignored) {}
        call.resolve(new JSObject().put("installed", ok));
    }

    /** الانحراف المغناطيسي (درجات) في الموقع: الشمال الحقيقي = المغناطيسي + الانحراف. من نموذج نظام أندرويد نفسه. */
    @PluginMethod public void declination(PluginCall call) {
        try {
            android.hardware.GeomagneticField g = new android.hardware.GeomagneticField(
                (float) (double) call.getDouble("lat", 0.0), (float) (double) call.getDouble("lon", 0.0), 0f, System.currentTimeMillis());
            call.resolve(new JSObject().put("deg", g.getDeclination()));
        } catch (Exception e) { call.resolve(new JSObject().put("deg", 0)); }
    }

    @PluginMethod public void openUrl(PluginCall call) {
        try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); call.resolve(); }
        catch (Exception e) { call.reject("تعذّر الفتح"); }
    }
}
