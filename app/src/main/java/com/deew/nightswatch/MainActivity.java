package com.deew.nightswatch;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;

import com.deew.nightswatch.core.OTGService;
import com.deew.nightswatch.core.WatchService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 23) {
            if (Settings.canDrawOverlays(this)) {
                Intent startMain = new Intent(this, WatchService.class);
                startMain.setAction("start_main");
                startService(startMain);

                new Handler(getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent startOtg = new Intent(MainActivity.this, OTGService.class);
                        startOtg.setAction("start_otg");
                        startService(startOtg);
                    }
                }, 1000);
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivity(intent);
            }
        }

    }

    @Override
    protected void onStop() {
        Intent stopMain = new Intent(this, WatchService.class);
        stopMain.setAction("close_main");
        startService(stopMain);

        Intent stopOtg = new Intent(MainActivity.this, OTGService.class);
        stopOtg.setAction("close_otg");
        startService(stopOtg);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
