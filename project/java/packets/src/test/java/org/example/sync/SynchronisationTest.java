package org.example.sync;

import org.example.ByteUtils;
import org.example.TimeUtils;
import org.example.softTimer.TimersManager;
import org.junit.Assert;
import org.junit.Test;

public class SynchronisationTest {
    private final Object resultLock = new Object();
    private static final int INVOKE_PERIOD = 50;
    private final byte[] block1 = new byte[]{0x01, 0x02, 0x03};
    private final byte[] block2 = new byte[]{0x11, 0x12, 0x13};

    private long sync1SuccessTime;
    private long sync2SuccessTime;


    TestSynchronizer instance1 = new TestSynchronizer(new ResultAcceptor() {
        @Override
        public void synchronised(byte[] thisBlock, byte[] thatBlock) {
            Assert.assertArrayEquals(thisBlock, block1);
            Assert.assertArrayEquals(thatBlock, block2);
            synchronized (resultLock) {
                sync1SuccessTime = TimeUtils.nowMs();
            }
        }

        @Override
        public void notSynchronized() {

        }
    }, new TimersManager(), "s1");

    TestSynchronizer instance2 = new TestSynchronizer(new ResultAcceptor() {
        @Override
        public void synchronised(byte[] thisBlock, byte[] thatBlock) {
            Assert.assertArrayEquals(thisBlock, block2);
            Assert.assertArrayEquals(thatBlock, block1);
            synchronized (resultLock) {
                sync2SuccessTime = TimeUtils.nowMs();
            }
        }

        @Override
        public void notSynchronized() {

        }
    }, new TimersManager(), "s2");

    @Test
    public void Test() throws Exception {
        instance1.setSendingDataAcceptor(dataReference -> {
            byte[] copy = ByteUtils.copyBytes(dataReference.getBuf(), dataReference.getOffset(), dataReference.getLength());
            instance2.onPacketReceived(copy, 0, copy.length);
        });
        instance2.setSendingDataAcceptor(dataReference -> {
            byte[] copy = ByteUtils.copyBytes(dataReference.getBuf(), dataReference.getOffset(), dataReference.getLength());
            instance1.onPacketReceived(copy, 0, copy.length);
        });

        instance1.startSynchronization(block1, INVOKE_PERIOD, 10);
        Thread.sleep(INVOKE_PERIOD / 2);
        instance2.startSynchronization(block2, INVOKE_PERIOD, 10);

        instance1.join();
        instance2.join();
        synchronized (resultLock) {
            //synchronous success invoking
            long delta = Math.abs(sync1SuccessTime - sync2SuccessTime);
            Assert.assertTrue("Delta " + delta, delta <= INVOKE_PERIOD);
        }
    }


}
