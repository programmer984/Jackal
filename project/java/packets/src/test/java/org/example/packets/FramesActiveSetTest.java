package org.example.packets;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FramesActiveSetTest {

    @Test
    public void SerializationTest(){
        Map<Integer, Set<Integer>> frames =new HashMap<>();
        Set<Integer> parts1 = new HashSet<>();
        parts1.add(4);
        parts1.add(44);
        Set<Integer> parts2 = new HashSet<>();
        parts2.add(42);
        parts2.add(14);

        frames.put(333, parts1);
        frames.put(334, parts2);
        //we displayed to user frame 330 and have data from next 333 and 334 frames
        //and have a lack for 331 and 332 frames
        FramesActiveSet packet=new FramesActiveSet(0, 330, frames);
        byte buf[] = packet.toArray(false);

        Map<Integer, Set<Integer>> frames2 = FramesActiveSet.getActiveSet(buf, 0);
        Assert.assertTrue(frames2.get(333).contains(4));
        Assert.assertTrue(frames2.get(333).contains(44));
        Assert.assertTrue(frames2.get(334).contains(42));
        Assert.assertTrue(frames2.get(334).contains(14));

        Assert.assertEquals(330, FramesActiveSet.getLastSuccessId(buf, 0));

        Assert.assertTrue(AbstractPacket.checkCRC(buf, 0));
    }
}
