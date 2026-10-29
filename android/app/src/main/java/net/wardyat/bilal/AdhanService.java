package net.wardyat.bilal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/** يشغّل الأذان كخدمة أمامية (تعمل والشاشة مقفلة)، ثم يُدخل الصامت للمدة المحددة. */
public class AdhanService extends Service {
    private MediaPlayer mp;
    private int prevVol = -1;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        if (in != null && "STOP".equals(in.getAction())) { finish(null); return START_NOT_STICKY; }
        String label = in == null ? "" : in.getStringExtra("label"); if (label == null || label.isEmpty()) label = "الأذان";
        boolean play = in == null || in.getBooleanExtra("play", true);
        boolean preview = in != null && in.getBooleanExtra("preview", false);
        int silent = in == null ? 0 : in.getIntExtra("silent", 0);
        long t = in == null ? 0 : in.getLongExtra("t", 0);
        String key = in == null ? "" : in.getStringExtra("n");

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("adhan") == null) {
            NotificationChannel ch = new NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null); nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Intent ai = new Intent(this, AdhanActivity.class).putExtra("label", label).putExtra("n", key)
            .putExtra("t", t == 0 ? System.currentTimeMillis() : t).putExtra("preview", preview)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full = PendingIntent.getActivity(this, 5, ai, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 3, new Intent(this, AdhanService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        final boolean pl = play0(in);
        final String title = preview ? Store.ui(this, "test", "تجربة الأذان") : Store.ui(this, "now", "حان وقت {n}").replace("{n}", label);
        final String text = pl ? Store.ui(this, "open", "اضغط لفتح شاشة الأذان") : Store.ui(this, "hint", "اضغط إغلاق لإنهاء الأذان");
        // إشعار أساسي بلا صور أولاً: يضمن ظهور الإشعار حتى لو فشلت الصورة، ثم يُستبدل بنسخة فيها صورة بلال
        Notification n = base(title, text, full, pl, stop).build();
        if (pl) { try { startActivity(ai); } catch (Exception ignored) {} }   // والتطبيق ظاهر (التجربة) يُفتح مباشرة
        if (Build.VERSION.SDK_INT >= 29) startForeground(4401, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(4401, n);
        try {
            NotificationCompat.Builder nb = base(title, text, full, pl, stop);
            android.graphics.Bitmap th = thumb(key); NotificationCompat.Style st = pic(key);
            if (th != null) nb.setLargeIcon(th);
            if (st != null) nb.setStyle(st);
            nm.notify(4401, nb.build());
        } catch (Throwable e) { android.util.Log.e("Adhan", "picture notification failed", e); }

        final int sMin = preview ? 0 : silent; final long fireT = t;
        if (!play) { finish(sMin > 0 ? new long[]{fireT, sMin} : null); return START_NOT_STICKY; }
        try {   // إن كان صوت المنبّه منخفضاً جداً يفوت الأذان: نرفعه مؤقتاً ثم نعيده
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM), cur = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
            if (Store.sp(this).getBoolean("boost", true) && cur < max * 0.4) {
                prevVol = cur; am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, (int) Math.ceil(max * 0.6), 0);
            }
        } catch (Exception ignored) {}
        try { if (mp != null) { mp.stop(); mp.release(); mp = null; } } catch (Exception ignored) {}   // تجربة متكررة: لا نتركه يتداخل
        try {
            int res = getResources().getIdentifier(Store.sound(this), "raw", getPackageName());
            if (res == 0) res = getResources().getIdentifier("takbir_aaqib", "raw", getPackageName());
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            // create() بالسمات: setAudioAttributes بعد create() (الحالة Prepared) ترمي IllegalStateException فيصمت الأذان
            mp = MediaPlayer.create(this, res, aa, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (mp == null) throw new IllegalStateException("MediaPlayer.create returned null for res " + res);
            mp.setOnCompletionListener(m -> finish(sMin > 0 ? new long[]{fireT, sMin} : null));
            mp.setOnErrorListener((m, w, e) -> { android.util.Log.e("Adhan", "MediaPlayer error " + w + "/" + e); finish(sMin > 0 ? new long[]{fireT, sMin} : null); return true; });
            mp.start();
            android.util.Log.i("Adhan", "playing " + Store.sound(this) + " res=" + res + " dur=" + mp.getDuration());
        } catch (Exception e) {
            android.util.Log.e("Adhan", "playback failed", e);
            finish(sMin > 0 ? new long[]{fireT, sMin} : null);
        }
        return START_NOT_STICKY;
    }

    private NotificationCompat.Builder base(String title, String text, PendingIntent full, boolean pl, PendingIntent stop) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, "adhan").setSmallIcon(R.drawable.ic_stat_adhan).setColor(0xFFE0B45A)
            .setContentTitle(title).setContentText(text).setContentIntent(full).addAction(0, Store.ui(this, "close", "إغلاق"), stop).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (pl) b.setFullScreenIntent(full, true);
        return b;
    }

    /** صورة بلال (مشهد الصلاة) في الإشعار حين لا تُفتح الشاشة الكاملة (الجوال مستعمل) */
    private android.graphics.Bitmap scene(String key, int maxW) {
        try {
            String k = (key == null || key.isEmpty()) ? "fajr" : key;
            int id = getResources().getIdentifier("scene_" + k + "_tall", "drawable", getPackageName());
            if (id == 0) id = getResources().getIdentifier("scene_fajr_tall", "drawable", getPackageName());
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeResource(getResources(), id, o);
            int ss = 1; while (o.outWidth / (ss * 2) >= maxW) ss *= 2;   // العرض ≥ maxW وحجم صغير (الاشعار يمرّ عبر Binder)
            o = new android.graphics.BitmapFactory.Options(); o.inSampleSize = ss; o.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeResource(getResources(), id, o);
        } catch (Throwable t) { return null; }
    }
    private NotificationCompat.Style pic(String key) {
        android.graphics.Bitmap b = scene(key, 480);
        if (b == null) return null;
        int h = Math.min(b.getHeight(), b.getWidth() * 9 / 16);   // شريط عريض من أعلى المشهد حيث بلال
        android.graphics.Bitmap c = android.graphics.Bitmap.createBitmap(b, 0, Math.max(0, (b.getHeight() - h) / 3), b.getWidth(), h);
        return new NotificationCompat.BigPictureStyle().bigPicture(c).bigLargeIcon((android.graphics.Bitmap) null);
    }
    private android.graphics.Bitmap thumb(String key) {
        android.graphics.Bitmap b = scene(key, 160);
        if (b == null) return null;
        int w = b.getWidth(), h = Math.min(b.getHeight(), w);
        return android.graphics.Bitmap.createBitmap(b, 0, Math.max(0, (b.getHeight() - h) / 3), w, h);
    }

    private static boolean play0(Intent in) { return in == null || in.getBooleanExtra("play", true); }

    private void finish(long[] silence) {
        sendBroadcast(new Intent(AdhanActivity.DONE).setPackage(getPackageName()));
        try { if (mp != null) { mp.stop(); mp.release(); mp = null; } } catch (Exception ignored) {}
        try { if (prevVol >= 0) { ((android.media.AudioManager) getSystemService(AUDIO_SERVICE)).setStreamVolume(android.media.AudioManager.STREAM_ALARM, prevVol, 0); prevVol = -1; } } catch (Exception ignored) {}
        if (silence != null) SilenceManager.enter(this, silence[0], (int) silence[1]);
        stopForeground(true); stopSelf();
    }

    @Override public void onDestroy() { try { if (mp != null) mp.release(); } catch (Exception ignored) {} super.onDestroy(); }
}
