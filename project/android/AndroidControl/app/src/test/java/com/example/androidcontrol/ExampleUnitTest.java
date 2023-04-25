package com.example.androidcontrol;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.androidcontrol.joystick.value.HorizontalDirection;
import com.example.androidcontrol.joystick.value.JoystickValue;
import com.example.androidcontrol.joystick.value.VerticalDirection;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        int horizontalOriginalValue = 30;
        int verticalOriginalValue = 55;
        JoystickValue value=new JoystickValue(200);
        value.setHorizontalValue(horizontalOriginalValue, HorizontalDirection.Right);
        value.setVerticalValue(verticalOriginalValue, VerticalDirection.Down);

        int serialized = value.toInt();


        int horizontalValue = JoystickValue.getHorizontalValue(400, serialized);
        int verticalValue = JoystickValue.getVerticalValue(400, serialized);

        assertTrue(Math.abs(horizontalOriginalValue - horizontalValue/2)<=1);
        assertTrue(Math.abs(verticalOriginalValue - verticalValue/2)<=1);
        assertEquals(HorizontalDirection.Right, JoystickValue.getHorizontalDirection(serialized));
        assertEquals(VerticalDirection.Down, JoystickValue.getVerticalDirection(serialized));
    }
}