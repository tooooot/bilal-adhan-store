package net.wardyat.bilal;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** معاينة الودّجات الثلاثة بأحجامها الفعلية (للاختبار الآلي وللتحقق البصري بلا مشغّل شاشة). غير مُصدَّرة. */
public class WidgetPreviewActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;
        android.content.SharedPreferences.Editor ed = Store.sp(this).edit();   // للاختبار الآلي: يمرَّر الوضع واللون بإضافات النية
        for (String k : new String[]{"theme", "wbg", "wfg", "wacc"}) if (getIntent().hasExtra(k)) ed.putString(k, getIntent().getStringExtra(k));
        if (getIntent().hasExtra("walpha")) ed.putInt("walpha", getIntent().getIntExtra("walpha", -1));
        ed.apply();
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding((int) (16 * d), (int) (40 * d), (int) (16 * d), (int) (16 * d));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF5B6B7B, 0xFF2B3A4A}));
        BilalWidget[] ws = {new BilalWidget.Row(), new BilalWidget.Small(), new BilalWidget.Prayers(), new BilalWidget.Scene()};
        int[][] sz = {{300, 56}, {150, 150}, {300, 220}, {300, 146}};   // أحجام الخلايا التقريبية 2×2 و4×3 و4×2
        String[] names = {"صف 1×4", "صغير 2×2", "المواقيت 4×3", "مشهد بلال 4×2"};
        for (int i = 0; i < ws.length; i++) {
            TextView t = new TextView(this); t.setText(names[i]); t.setTextColor(Color.WHITE); t.setPadding(0, (int) (10 * d), 0, (int) (4 * d)); col.addView(t);
            FrameLayout box = new FrameLayout(this);
            try { View v = ws[i].build(this).apply(this, box); box.addView(v, new FrameLayout.LayoutParams(-1, -1)); } catch (Exception e) { android.util.Log.e("WidgetPreview", "apply failed", e); }
            col.addView(box, new LinearLayout.LayoutParams((int) (sz[i][0] * d), (int) (sz[i][1] * d)));
        }
        setContentView(col);
    }
}
