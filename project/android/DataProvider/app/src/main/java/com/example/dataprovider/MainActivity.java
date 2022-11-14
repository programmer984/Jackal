package com.example.dataprovider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.example.dataprovider.service.DataProviderService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainActivity extends AppCompatActivity {


    private Logger logger = LoggerFactory.getLogger("Service");

    private Surface surface;
    private SurfaceView surfaceView;


    private Intent intent;
    DataProviderService service;
    private volatile boolean stopServiceOnExit = false;
    private final static int REQUEST_CODE = 50;
    private final static String[] permissions = new String[]{
        Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE};

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler = null;


    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        intent = new Intent(MainActivity.this, DataProviderService.class);


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
                DataProviderService.Binder b = (DataProviderService.Binder) binder;
                service = b.getService();
                service.setActivityContext(MainActivity.this);

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
            stopService(new Intent(this, DataProviderService.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}