package net.wardyat.bilal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in) {
        if ("net.wardyat.bilal.WATCHDOG".equals(in.getAction())) {   // إعادة بناء السلسلة فقط
            Scheduler.scheduleNext(c); BilalWidget.updateAll(c); warnIfExpiring(c); return;
        }
        if ("net.wardyat.bilal.TEST".equals(in.getAction())) {   // اختبار يدوي: المسار نفسه الحقيقي (خدمة أمامية + شاشة كاملة)
            Intent s = new Intent(c, AdhanService.class).putExtra("label", in.getStringExtra("label")).putExtra("n", in.getStringExtra("n"))
                .putExtra("play", true).putExtra("silent", 0).putExtra("t", System.currentTimeMillis());
            try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s); } catch (Exception ignored) {}
            return;
        }
        long t = in.getLongExtra("t", 0);
        JSONObject o = Store.find(c, t);
        try {
            if (o != null && System.currentTimeMillis() - t < 10 * 60 * 1000) {   // لا نُشغّل أذاناً فات أكثر من 10 دقائق
                String k = o.optString("k");
                if ("imsak".equals(k)) {
                    notifyImsak(c, o.optString("l"));
                } else {
                    Intent s = new Intent(c, AdhanService.class)
                        .putExtra("label", o.optString("l")).putExtra("n", o.optString("n")).putExtra("play", o.optInt("a", 1) == 1)
                        .putExtra("silent", o.optInt("s", 0)).putExtra("t", t);
                    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
                }
            }
        } catch (Exception ignored) {}
        Scheduler.scheduleNext(c);
        BilalWidget.updateAll(c);
        warnIfExpiring(c);
    }

    /** إن بقي في الجدول أقل من 5 أيام: تنبيه (مرة يومياً) ليفتح المستخدم التطبيق فيتجدد الجدول — بدونه يتوقف الأذان بصمت. */
    static void warnIfExpiring(Context c) {
        try {
            org.json.JSONArray a = Store.items(c);
            if (a.length() == 0) return;
            long last = a.getJSONObject(a.length() - 1).optLong("t");
            if (last - System.currentTimeMillis() > 5L * 86400_000L) return;
            long day = System.currentTimeMillis() / 86400_000L;
            if (Store.sp(c).getLong("warnDay", -1) == day) return;
            Store.sp(c).edit().putLong("warnDay", day).apply();
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("info") == null)
                nm.createNotificationChannel(new NotificationChannel("info", "تنبيهات التطبيق", NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open = PendingIntent.getActivity(c, 6, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            nm.notify(4403, new NotificationCompat.Builder(c, "info").setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("افتح أذان بلال لتجديد المواقيت")
                .setContentText("اقترب انتهاء جدول الأذان. افتح التطبيق مرة حتى لا يفوتك الأذان.")
                .setContentIntent(open).setAutoCancel(true).build());
        } catch (Exception ignored) {}
    }

    static void notifyImsak(Context c, String label) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("imsak") == null)
            nm.createNotificationChannel(new NotificationChannel("imsak", "تنبيه الإمساك", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(c, "imsak").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اقترب وقت الإمساك").setContentText(label).setContentIntent(open).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build();
        nm.notify(4402, n);
    }
}
