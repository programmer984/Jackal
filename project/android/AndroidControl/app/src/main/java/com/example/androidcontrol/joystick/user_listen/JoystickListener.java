package com.example.androidcontrol.joystick.user_listen;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import com.example.androidcontrol.joystick.Area;
import com.example.androidcontrol.joystick.Point;
import com.example.androidcontrol.joystick.value.HorizontalDirection;
import com.example.androidcontrol.joystick.value.JoystickValue;
import com.example.androidcontrol.joystick.value.VerticalDirection;

import org.example.endpoint.OutgoingPacketCarrier;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoystickListener implements View.OnTouchListener {

    private static final int IDLE_PIXELS_VALUE = 20;
    private PressState state = PressState.idle;
    private int pointerIndex;
    private Area startArea;
    private Point startPoint;
    private Point currentPoint;
    private final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
    private final Logger logger = LoggerFactory.getLogger(JoystickListener.class);
    private final JoystickValue currentValue;
    private int currentValueSerialized;

    public JoystickListener(Context context, Area startArea, TimersManager timersManager, int samplePeriodMs, CurrentValueListener currentValueListener) {
        this.startArea = startArea;
        currentValue = new JoystickValue(startArea.getMaxDimention()/2);
        resetCurrentValue();
        serializeCurrentValue();
        timersManager.addTimer(samplePeriodMs, true, () -> {
            currentValueListener.onValueChanged(getCurrentValueSerialized());
        });
    }

    private synchronized int getCurrentValueSerialized(){
        return currentValueSerialized;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (PressState.idle.equals(state) && event.getActionMasked() == ACTION_DOWN) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                event.getPointerCoords(i, pointerCoords);
                Point point = new Point(pointerCoords.getAxisValue(MotionEvent.AXIS_X),
                        pointerCoords.getAxisValue(MotionEvent.AXIS_Y));
                if (startArea.containPoint(point)) {
                    synchronized (this) {
                        startMoveTracking(point, i);
                    }
                    return true;
                }
            }
        } else if (PressState.pressedAndTracking.equals(state)) {
            if (event.getActionMasked() == ACTION_MOVE) {
                event.getPointerCoords(pointerIndex, pointerCoords);
                currentPoint.setX(pointerCoords.getAxisValue(MotionEvent.AXIS_X));
                currentPoint.setY(pointerCoords.getAxisValue(MotionEvent.AXIS_Y));
                updateCurrentValue();
                synchronized (this) {
                    serializeCurrentValue();
                }
                return true;
            } else {
                resetCurrentValue();
                synchronized (this) {
                    state = PressState.idle;
                    serializeCurrentValue();
                }
                return true;
            }
        }

        return false;
    }

    private void updateCurrentValue() {
        int deltaX = currentPoint.getX() - startPoint.getX();
        int deltaY = currentPoint.getY() - startPoint.getY();

        if (Math.abs(deltaX) < IDLE_PIXELS_VALUE) {
            currentValue.setHorizontalDirection(HorizontalDirection.None);
        } else if (deltaX > IDLE_PIXELS_VALUE) {
            currentValue.setHorizontalValue(deltaX, HorizontalDirection.Right);
        } else {
            currentValue.setHorizontalValue(deltaX, HorizontalDirection.Left);
        }

        if (Math.abs(deltaY) < IDLE_PIXELS_VALUE) {
            currentValue.setVerticalDirection(VerticalDirection.None);
        } else if (deltaY > IDLE_PIXELS_VALUE) {
            currentValue.setVerticalValue(deltaY, VerticalDirection.Down);
        } else {
            currentValue.setVerticalValue(deltaY, VerticalDirection.Up);
        }
    }

    private void resetCurrentValue() {
        currentValue.setVerticalDirection(VerticalDirection.None);
        currentValue.setHorizontalDirection(HorizontalDirection.None);
    }

    private void serializeCurrentValue() {
        currentValueSerialized = currentValue.toInt();
    }

    private void startMoveTracking(Point startPoint, int pointerIndex) {
        this.startPoint = startPoint;
        this.currentPoint = new Point(startPoint.getX(), startPoint.getY());
        this.pointerIndex = pointerIndex;
        state = PressState.pressedAndTracking;
    }

}
