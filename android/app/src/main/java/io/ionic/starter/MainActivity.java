package io.ionic.starter;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onResume() {
        super.onResume();

        // 🔥 Forzar actualización del widget
        GameWidget.forceUpdate(getApplicationContext());
    }
}
