package org.example.packets;

import org.junit.Assert;
import org.junit.Test;

public class KeepAliveTest {

    @Test
    public void SerializationTest(){

        KeepAlive packet=new KeepAlive();
        byte buf[] = packet.toArray(false);
        Assert.assertTrue(AbstractPacket.checkCRC(buf, 0));
    }
}
