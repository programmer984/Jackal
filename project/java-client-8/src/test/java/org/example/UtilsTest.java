package org.example;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test for simple App.
 */
public class UtilsTest {

    int U16Max = 65535;
    int U32Max = 70000;

    public UtilsTest() {

    }



    @Test
    public void u16Test() {
        byte[] buf = new byte[2];
        for (int i=0;i<U16Max;i++){
            Utils.u16ToBuf(i, buf, 0);
            int result = Utils.bufToU16(buf, 0);
            assertTrue(i == result);
        }
    }

    @Test
    public void u32Test() {
        byte[] buf = new byte[4];
        for (int i=0;i<U32Max;i++){
            Utils.i32ToBuf(i, buf, 0);
            int result = Utils.bufToI32(buf, 0);
            assertTrue(i == result);
        }
    }



}
