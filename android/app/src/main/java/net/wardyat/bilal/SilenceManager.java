package net.wardyat.bilal;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** الصامت وقت الصلاة عبر «عدم الإزعاج» (يسمح بالمنبّهات فقط)، مع استعادة الحالة السابقة بعد المدة. يحتاج إذن المستخدم الخاص. */
final class SilenceManager {
    static boolean granted(Context c) {
        return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted();
    }

    static void enter(Context c, long fireT, int minutes) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) return;
        long end = (fireT > 0 ? fireT : System.currentTimeMillis()) + minutes * 60_000L;
        if (end <= System.currentTimeMillis()) return;
        int cur = nm.getCurrentInterruptionFilter();
        // إن كان صامتاً أصلاً (المستخدم بنفسه) لا نغيّر شيئاً ولا نستعيد شيئاً
        if (cur == NotificationManager.INTERRUPTION_FILTER_ALARMS || cur == NotificationManager.INTERRUPTION_FILTER_NONE) return;
        Store.sp(c).edit().putInt("prevFilter", cur).putBoolean("silenced", true).apply();
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = PendingIntent.getBroadcast(c, 7002, new Intent(c, Restore.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, p); }
        catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, p); }
    }

    static void restore(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!Store.sp(c).getBoolean("silenced", false)) return;
        int prev = Store.sp(c).getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL);
        // لا نستعيد إلا إن كانت الحالة ما زالت التي وضعناها (إن غيّرها المستخدم فاختياره أولى)
        if (nm.isNotificationPolicyAccessGranted() && nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALARMS)
            nm.setInterruptionFilter(prev);
        Store.sp(c).edit().putBoolean("silenced", false).apply();
    }

    public static class Restore extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) { restore(c); }
    }
}
