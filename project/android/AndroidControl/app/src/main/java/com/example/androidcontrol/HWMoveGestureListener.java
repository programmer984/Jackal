package com.example.androidcontrol;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;


import org.example.TimeUtils;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.packets.HWDoMove;
import org.example.softTimer.TimerCallback;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Deprecated
public class HWMoveGestureListener implements View.OnTouchListener {

    private final Logger logger = LoggerFactory.getLogger(HWMoveGestureListener.class);
    private final GestureDetector gestureDetector;

    private final int MaxVelocity;
    private final static int MaxPower = 255;

    private final static int StepMs = 100;
    private final static int TimeDoMoveMs = 130;
    private final static int GeneratingTime = 250;

    private final static int SLOW_MOVEMENT_THRESHOLD = 10;


    private final AtomicInteger lastPowerX = new AtomicInteger(0);
    private final AtomicInteger lastPowerY = new AtomicInteger(0);
    private final AtomicLong lastActionTime = new AtomicLong(0);
    private long version;

    private OutgoingPacketCarrier packetAcceptor;

    public HWMoveGestureListener(Context context, TimersManager timersManager, OutgoingPacketCarrier packetAcceptor) {
        MaxVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity() / 2;
        this.packetAcceptor = packetAcceptor;
        gestureDetector = new GestureDetector(context, new GestureListener());
        timersManager.addTimer(StepMs, true, oneStepCommandCreator);
        updateActionTime();
    }

    private final TimerCallback oneStepCommandCreator = () -> {
        //if it is not elapsed 0.5 second since last action
        if (!TimeUtils.elapsed(GeneratingTime, lastActionTime.get())) {
            int lX = lastPowerX.get();
            int lY = lastPowerY.get();
            int pX = Math.abs(lX);
            int pY = Math.abs(lY);

            HWDoMove.HorizontalCommand horizontalCommand;
            if (pX > SLOW_MOVEMENT_THRESHOLD) {
                horizontalCommand = new HWDoMove.HorizontalCommand(
                        lX < 0 ? HWDoMove.HorizontalDirection.Left : HWDoMove.HorizontalDirection.Right,
                        (byte) pX);
            } else {
                horizontalCommand = new HWDoMove.HorizontalCommand(HWDoMove.HorizontalDirection.Idle, (byte) 0);
            }

            HWDoMove.VerticalCommand verticalCommand;
            if (pY > SLOW_MOVEMENT_THRESHOLD) {
                verticalCommand = new HWDoMove.VerticalCommand(
                        lY < 0 ? HWDoMove.VerticalDirection.Up : HWDoMove.VerticalDirection.Down,
                        (byte) pY);
            } else {
                verticalCommand = new HWDoMove.VerticalCommand(HWDoMove.VerticalDirection.Idle, (byte) 0);
            }

            HWDoMove movePacket = new HWDoMove(horizontalCommand, verticalCommand, (byte) TimeDoMoveMs, version++);
            if (logger.isDebugEnabled()) {
                logger.debug(movePacket.toString());
            }
            packetAcceptor.packetWasBorn(movePacket, null);
        }
    };

    private void updateActionTime() {
        lastActionTime.set(TimeUtils.nowMs());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //float diffY = e2.getY() - e1.getY();
            //float diffX = e2.getX() - e1.getX();
            //logger.debug("x:{} y:{}", velocityX, velocityY);
            boolean highVelocityX = Math.abs(velocityX) > MaxVelocity;
            boolean highVelocityY = Math.abs(velocityY) > MaxVelocity;
            boolean xPositive = velocityX > 0;
            boolean yPositive = velocityY > 0;

            int vX = highVelocityX ? (xPositive ? MaxVelocity : -MaxVelocity) : (int) velocityX;
            int vY = highVelocityY ? (yPositive ? MaxVelocity : -MaxVelocity) : (int) velocityY;

            // 255 - 16000
            // ?   - vX
            lastPowerX.set(vX * MaxPower / MaxVelocity);
            lastPowerY.set(vY * MaxPower / MaxVelocity);
            updateActionTime();

            return true;
        }

    }
}
