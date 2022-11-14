package com.example.androidcontrol;

import androidx.appcompat.app.AppCompatActivity;
import androidx.multidex.MultiDex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.androidcontrol.service.ControlService;
import com.example.androidcontrol.video.VideoRenderer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private VideoDirector director;
    private Intent intent;
    private ControlService service;
    private volatile boolean stopServiceOnExit;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout layout = findViewById(R.id.container);
        director = new VideoDirector(this);
        layout.addView(director.getVideoRenderer());


        intent = new Intent(MainActivity.this, ControlService.class);

        startService();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopServiceOnExit = true;
            }
        });
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (service != null) {
            service.notifyPause();
        }
    }

    private void startService() {
        startService(intent);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                ControlService.Binder b = (ControlService.Binder) binder;
                service = b.getService();
                service.setVideoDirector(director);
                findViewById(android.R.id.content).getRootView()
                        .setOnTouchListener(new HWMoveGestureListener(MainActivity.this, service.getTimersManager(), service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        }, BIND_IMPORTANT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (service != null) {
            service.notifyResume();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (service != null) {
            service.notifyActivityDestroied();
        }
        if (this.stopServiceOnExit) {
            stopService(new Intent(this, ControlService.class));
        }
    }

}