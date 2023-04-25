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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.example.androidcontrol.joystick.display.JoystickValueView;
import com.example.androidcontrol.joystick.user_listen.CurrentValueListener;
import com.example.androidcontrol.joystick.user_listen.JoystickListener;
import com.example.androidcontrol.joystick.RectArea;
import com.example.androidcontrol.joystick.value.HorizontalDirection;
import com.example.androidcontrol.joystick.value.JoystickValue;
import com.example.androidcontrol.joystick.value.VerticalDirection;
import com.example.androidcontrol.service.ControlService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.example.endpoint.OutgoingPacketCarrier;
import org.example.packets.HWDoMove;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private VideoDirector director;
    private JoystickValueView joystickValueView;
    private Intent intent;
    private ControlService service;
    private volatile boolean stopServiceOnExit;
    private final static int REQUEST_CODE = 51;

    private final static String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private static final boolean isModern(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    private final static String[] permissions_30 = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

    public static String[] getPermissions(){
        if (isModern()) {
            return permissions_30;
        }else{
            return permissions;
        }
    }

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
        joystickValueView = findViewById(R.id.js);
        intent = new Intent(MainActivity.this, ControlService.class);

        if (isModern()){
            if (Environment.isExternalStorageManager() && checkEveryPermission()){
                startService();
            }
        }else{
            if (!checkEveryPermission()) {
                ActivityCompat.requestPermissions(this, getPermissions(), REQUEST_CODE);
            } else {
                startService();
            }
        }


        FloatingActionButton fab = findViewById(R.id.fab1);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopServiceOnExit = true;
            }
        });
    }

    private boolean checkEveryPermission() {
        for (String permission : getPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE && grantResults.length >= getPermissions().length
                && Arrays.stream(grantResults).allMatch(code-> code == PackageManager.PERMISSION_GRANTED)) {
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



    private class JoystickValueRouter implements CurrentValueListener
    {

        private final OutgoingPacketCarrier outgoingPacketCarrier;
        public final static int StepMs = 100;
        private final static int TimeDoMoveMs = 130;
        private final static int HW_MAX_VALUE = 255;
        private long version;

        private JoystickValueRouter(OutgoingPacketCarrier outgoingPacketCarrier) {
            this.outgoingPacketCarrier = outgoingPacketCarrier;
        }

        //works in background thread
        @Override
        public void onValueChanged(int value) {
            if (joystickValueView != null) {
                joystickValueView.updateValue(value);
            }

            HorizontalDirection horizontalDirection = JoystickValue.getHorizontalDirection(HW_MAX_VALUE);
            VerticalDirection verticalDirection = JoystickValue.getVerticalDirection(HW_MAX_VALUE);

            HWDoMove.HorizontalCommand horizontalCommand;
            if (horizontalDirection==HorizontalDirection.None) {
                horizontalCommand = new HWDoMove.HorizontalCommand(HWDoMove.HorizontalDirection.Idle, (byte) 0);
            } else {
                horizontalCommand = new HWDoMove.HorizontalCommand(
                        horizontalDirection==HorizontalDirection.Left ? HWDoMove.HorizontalDirection.Left : HWDoMove.HorizontalDirection.Right,
                        (byte) JoystickValue.getHorizontalValue(HW_MAX_VALUE, value));
            }

            HWDoMove.VerticalCommand verticalCommand;
            if (verticalDirection==VerticalDirection.None) {
                verticalCommand = new HWDoMove.VerticalCommand(HWDoMove.VerticalDirection.Idle, (byte) 0);
            } else {
                verticalCommand = new HWDoMove.VerticalCommand(
                        verticalDirection==VerticalDirection.Up ? HWDoMove.VerticalDirection.Up : HWDoMove.VerticalDirection.Down,
                        (byte) JoystickValue.getVerticalValue(HW_MAX_VALUE, value));
            }

            HWDoMove movePacket = new HWDoMove(horizontalCommand, verticalCommand, (byte) TimeDoMoveMs, version++);
            outgoingPacketCarrier.packetWasBorn(movePacket, null);
        }
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

                //rootView.setOnTouchListener(new HWMoveGestureListener(MainActivity.this, service.getFactory().getTimersManager(), service.getOutgoingPacketCarrier()));
                int halfWidth = layout.getMeasuredWidth() / 2;
                int halfHeight = layout.getMeasuredHeight() / 2;
                //left bottom part for movement consuming
                RectArea touchArea = new RectArea(0, halfHeight, halfWidth, halfHeight);
                OutgoingPacketCarrier packetCarrier = service.getOutgoingPacketCarrier();
                rootView.setOnTouchListener(
                        new JoystickListener(MainActivity.this,
                                touchArea,
                                service.getFactory().getTimersManager(),
                                JoystickValueRouter.StepMs,
                                new JoystickValueRouter(service.getOutgoingPacketCarrier()))
                );
            } catch (Exception ex) {
                System.err.println(ex);
                stopService();
            }
        }
    }



    private void stopService() {
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