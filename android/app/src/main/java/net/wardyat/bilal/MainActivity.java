package net.wardyat.bilal;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle b) {
        registerPlugin(BilalPlugin.class);
        super.onCreate(b);
    }

    /** للاختبار الآلي فقط: --ez skip_wizard true يتجاوز معالج أول تشغيل ويعيد تحميل الواجهة. */
    @Override public void onStart() {
        super.onStart();
        if (getIntent() != null && getIntent().getBooleanExtra("skip_wizard", false) && getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().postDelayed(() -> getBridge().getWebView().evaluateJavascript(
                "if(!localStorage.getItem('bilal_wiz')){localStorage.setItem('bilal_wiz','1');location.reload();}", null), 2500);
        }
    }
}
