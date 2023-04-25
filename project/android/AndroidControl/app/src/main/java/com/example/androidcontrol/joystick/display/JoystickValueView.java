package com.example.androidcontrol.joystick.display;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.example.androidcontrol.joystick.value.HorizontalDirection;
import com.example.androidcontrol.joystick.value.JoystickValue;
import com.example.androidcontrol.joystick.value.VerticalDirection;

public class JoystickValueView extends View implements CoordinatorLayout.AttachedBehavior {

    private final Handler mainHandler;
    private final CoordinatorLayout.Behavior behavior;
    private final Paint paintHorizontal = new Paint();
    private final Paint paintVertical = new Paint();
    private static final int maxValue = 100;
    private static final int xOffset = 10;
    int fontSize = 24;
    int horizontalTextYOffset;
    int verticalTextYOffset;

    private Integer currentValue;

    public JoystickValueView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mainHandler = new Handler(context.getMainLooper());
        behavior = new CoordinatorLayout.Behavior<AppCompatButton>() {

        };
        paintHorizontal.setColor(Color.BLUE);
        paintHorizontal.setStyle(Paint.Style.STROKE);
        paintHorizontal.setTextSize(fontSize);

        paintVertical.setColor(Color.YELLOW);
        paintVertical.setStyle(Paint.Style.STROKE);
        paintVertical.setTextSize(fontSize);

    }

    Runnable invalidator = () -> {
        invalidate();
    };

    public void updateValue(int value) {
        synchronized (this) {
            currentValue = value;
        }
        mainHandler.post(invalidator);
    }

    @NonNull
    @Override
    public CoordinatorLayout.Behavior getBehavior() {
        return behavior;
    }

    private synchronized Integer getValue() {
        return currentValue;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Integer currentValue = getValue();
        if (currentValue != null) {
            int middleY = getHeight() / 2;
            int size = (int) paintHorizontal.getTextSize();
            horizontalTextYOffset = middleY - size - 4;
            verticalTextYOffset = middleY + size + 4;

            HorizontalDirection horizontalDirection = JoystickValue.getHorizontalDirection(currentValue);
            if (horizontalDirection != HorizontalDirection.None) {
                int percents = JoystickValue.getHorizontalValue(maxValue, currentValue);
                canvas.drawText(String.format("%s:%d%%", horizontalDirection, percents),
                        xOffset, horizontalTextYOffset, paintHorizontal);
            }
            VerticalDirection verticalDirection = JoystickValue.getVerticalDirection(currentValue);
            if (verticalDirection != VerticalDirection.None) {
                int percents = JoystickValue.getVerticalValue(maxValue, currentValue);
                canvas.drawText(String.format("%s:%d%%", verticalDirection, percents),
                        xOffset, verticalTextYOffset, paintVertical);
            }
        }
        //canvas.drawRect(10, 10, 40, 40, paintHorizontal);
    }
}
