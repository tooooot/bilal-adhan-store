package net.wardyat.bilal;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.widget.VideoView;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** شاشة الأذان: مشهد بلال كاملاً فوق القفل مع اسم الصلاة وزر إيقاف. تُغلق وحدها عند انتهاء الأذان. */
public class AdhanActivity extends Activity {
    static final String DONE = "net.wardyat.bilal.ADHAN_DONE";
    private BroadcastReceiver done;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.BLACK);

        String label = getIntent().getStringExtra("label"); if (label == null) label = "";
        String n = getIntent().getStringExtra("n");
        boolean preview = getIntent().getBooleanExtra("preview", false);
        long t = getIntent().getLongExtra("t", System.currentTimeMillis());
        int d = (int) getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.parseColor("#0d0a07"));
        ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String key = (n == null || n.isEmpty()) ? "fajr" : n;   // التجربة لا تحمل اسم صلاة: الفجر افتراضياً
        int res = getResources().getIdentifier("scene_" + key + "_tall", "drawable", getPackageName());   // صورة طولية من مصنع الصور إن وُجدت
        if (res == 0) res = getResources().getIdentifier("scene_" + key, "drawable", getPackageName());
        if (res == 0) res = getResources().getIdentifier("scene_fajr", "drawable", getPackageName());
        if (res != 0) img.setImageResource(res);
        root.addView(img, new FrameLayout.LayoutParams(-1, -1));
        int vres = getResources().getIdentifier("scene_" + key + "_loop", "raw", getPackageName());   // فيديو صامت يُعاد (9:16) فوق الصورة، لا يغطي إلا بعد جاهزيته
        if (vres != 0) {
            try {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                int vw = Math.max(dm.widthPixels, dm.heightPixels * 9 / 16), vh = Math.max(dm.heightPixels, dm.widthPixels * 16 / 9);   // تغطية كاملة بقصّ الزائد
                final VideoView vv = new VideoView(this);
                vv.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + vres));
                vv.setOnPreparedListener(mp -> { mp.setLooping(true); mp.setVolume(0f, 0f); });
                vv.setOnErrorListener((mp, w, e) -> { vv.setVisibility(View.GONE); return true; });
                FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER);
                root.addView(vv, vl);
                vv.start();
            } catch (Exception ignored) {}
        }
        View shade = new View(this);   // تعتيم خفيف أعلى وأسفل فقط، ويبقى بلال ظاهراً في الوسط
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x99000000, 0x00000000, 0x00000000, 0x00000000, 0x8C000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        Typeface amiri; try { amiri = Typeface.createFromAsset(getAssets(), "fonts/amiri_bold.ttf"); } catch (Exception e) { amiri = Typeface.SERIF; }

        // أعلى الشاشة على طراز شاشة القفل: تاريخ صغير بالإنجليزية ثم ساعة كبيرة رفيعة ثم اسم الصلاة — في السماء بعيداً عن بلال
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL); top.setGravity(Gravity.CENTER_HORIZONTAL);
        java.util.Calendar cal = java.util.Calendar.getInstance(); cal.setTimeInMillis(t);
        TextView date = tv(new java.text.SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(cal.getTime()), 15, "#f3e6c8", false);
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); top.addView(date);
        int h12 = cal.get(java.util.Calendar.HOUR); if (h12 == 0) h12 = 12;
        TextView time = tv(h12 + ":" + String.format(Locale.ENGLISH, "%02d", cal.get(java.util.Calendar.MINUTE)), 70, "#fbf3df", false);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL)); time.setIncludeFontPadding(false);
        time.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); top.addView(time);
        TextView name = tv(label, 22, "#e0b45a", true); name.setTypeface(amiri); name.setPadding(0, 2 * d, 0, 0); top.addView(name);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // أسفل الشاشة: زر واحد صغير هادئ
        LinearLayout bot = new LinearLayout(this); bot.setOrientation(LinearLayout.VERTICAL); bot.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView stop = new TextView(this); stop.setText(Store.ui(this, "close", "إغلاق")); stop.setTextSize(21); stop.setTypeface(amiri);
        stop.setTextColor(Color.parseColor("#f3e6c8")); stop.setGravity(Gravity.CENTER); stop.setIncludeFontPadding(false);
        stop.setClickable(true); stop.setFocusable(true);
        GradientDrawable g = new GradientDrawable(); g.setColor(0x66120c05);
        g.setCornerRadius(30 * d); g.setStroke(d, Color.parseColor("#b3e0b45a")); stop.setBackground(g);
        bot.addView(stop, new LinearLayout.LayoutParams(190 * d, 56 * d));
        stop.setOnClickListener(v -> {
            startService(new Intent(this, AdhanService.class).setAction("STOP"));
            finish();
        });
        root.addView(bot, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        // أندرويد يرسم خلف الأشرطة وقد لا يبلّغ بارتفاع شريط التنقل في وضع ملء الشاشة: نترك هامشاً سفلياً ثابتاً سخياً (72dp) فوق ما يُبلَّغ به، فلا يختفي الزر
        final int topPad = 28 * d, botPad = 96 * d;
        top.setPadding(24 * d, topPad, 24 * d, 0); bot.setPadding(28 * d, 0, 28 * d, botPad);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            top.setPadding(24 * d, topPad + ins.getSystemWindowInsetTop(), 24 * d, 0);
            bot.setPadding(28 * d, 0, 28 * d, botPad + ins.getSystemWindowInsetBottom());
            return ins;
        });
        setContentView(root);
        root.requestApplyInsets();
        // احتياط: إن امتدّ الجذر تحت شريط التنقل رفعنا الزر بمقدار الجزء المحجوب فعلياً
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect(); root.getWindowVisibleDisplayFrame(r);
            int[] loc = new int[2]; root.getLocationOnScreen(loc);
            int hidden = Math.max(0, (loc[1] + root.getHeight()) - r.bottom);
            if (hidden != lastHidden) { lastHidden = hidden; FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) bot.getLayoutParams(); bl.bottomMargin = hidden; bot.setLayoutParams(bl); }
        });

        done = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { finish(); } };
        IntentFilter f = new IntentFilter(DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(done, f);
    }

    private int lastHidden = -1;

    private static String clock(long t) {   // 4:12 م — أرقام لاتينية وص/م عربية كما في التطبيق
        java.util.Calendar c = java.util.Calendar.getInstance(); c.setTimeInMillis(t);
        int h = c.get(java.util.Calendar.HOUR); if (h == 0) h = 12;
        return h + ":" + String.format(Locale.ENGLISH, "%02d", c.get(java.util.Calendar.MINUTE)) + (c.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM ? " ص" : " م");
    }

    private TextView tv(String s, int sp, String color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.parseColor(color));
        v.setGravity(Gravity.CENTER); v.setShadowLayer(14, 0, 2, Color.BLACK);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    @Override protected void onDestroy() { try { unregisterReceiver(done); } catch (Exception ignored) {} super.onDestroy(); }
}
