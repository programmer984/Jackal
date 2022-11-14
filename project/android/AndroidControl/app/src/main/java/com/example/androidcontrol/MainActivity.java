package com.example.androidcontrol;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.multidex.MultiDex;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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
    private final static int REQUEST_CODE = 50;
    private final static String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};
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

        intent = new Intent(MainActivity.this, ControlService.class);

        if (!checkEveryPermission()) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
        } else {
            startService();
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopServiceOnExit = true;
            }
        });
    }

    private boolean checkEveryPermission(){
        for (String permission : permissions){
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE && grantResults.length >= this.permissions.length && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startService();
        }
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
                setServiceEtc();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service.clearVideoDirector();
            }
        }, BIND_IMPORTANT);
    }

    private void setServiceEtc() {
        if (service != null) {
            try {
                LinearLayout layout = findViewById(R.id.container);
                director = new VideoDirector(MainActivity.this, layout.getMeasuredWidth(), layout.getMeasuredHeight(),
                        service.getFactory().getTimersManager());
                layout.addView(director.getVideoRenderer());
                service.setVideoDirector(director);
                View rootView = findViewById(android.R.id.content).getRootView();
                rootView.setOnTouchListener(new HWMoveGestureListener(MainActivity.this, service.getFactory().getTimersManager(), service.getOutgoingPacketCarrier()));
            }catch (Exception ex){
                System.err.println(ex);
                stopService();
            }
        }
    }

    private void stopService(){
        stopService(new Intent(this, ControlService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setServiceEtc();
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
            stopService();
        }
    }

}