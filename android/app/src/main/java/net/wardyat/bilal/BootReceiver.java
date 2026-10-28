package net.wardyat.bilal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** بعد الإقلاع/تحديث التطبيق/تغيير الوقت أو المنطقة: أعد ضبط المنبّه والودّجت، واستعد الصامت العالق. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        SilenceManager.restore(c);
        Scheduler.scheduleNext(c);
        BilalWidget.updateAll(c);
    }
}
