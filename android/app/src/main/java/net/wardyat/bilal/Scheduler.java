package net.wardyat.bilal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** يضبط المنبّه الدقيق التالي فقط؛ وعند رنينه يُضبط الذي يليه (سلسلة)، وتُعاد السلسلة عند الإقلاع. */
final class Scheduler {
    static final int REQ = 7001;

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    static PendingIntent pi(Context c, long t) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction("net.wardyat.bilal.ALARM").putExtra("t", t);
        return PendingIntent.getBroadcast(c, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** حارس مستقل: يعمل بعد المنبّه التالي بعشر دقائق ليعيد بناء السلسلة إن انقطعت لأي سبب. */
    static PendingIntent watchdogPi(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction("net.wardyat.bilal.WATCHDOG");
        return PendingIntent.getBroadcast(c, 7003, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, 0));
    }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        JSONArray a = Store.items(c); long now = System.currentTimeMillis();
        JSONObject next = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String k = o.optString("k");
            if (("p".equals(k) || "imsak".equals(k)) && o.optLong("t") > now + 1000) { next = o; break; }
        }
        cancel(c);
        if (next == null) return;
        long t = next.optLong("t");
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t + 10 * 60_000L, watchdogPi(c)); } catch (Exception ignored) {}
        PendingIntent p = pi(c, t);
        try {
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent sp = PendingIntent.getActivity(c, 0, show, PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, sp), p);
        } catch (SecurityException e) {
            // لا إذن منبّه دقيق: أقرب بديل (قد يتأخر دقائق) — الواجهة تنبّه المستخدم لمنح الإذن
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
        }
    }
}
