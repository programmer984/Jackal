package org.example.udphole;

import org.example.packetsReceiver.*;
import org.example.udphole.packetsReceiver.*;
import org.example.udphole.softTimer.TimersManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

import static org.example.udphole.ByteUtils.searchSequense;


public class PacketReceiverTest {


    private static final Logger logger
            = LoggerFactory.getLogger(PacketReceiverTest.class);
    static final byte[] packetStart = {0x10, 0x11, 0x12, 0x13};
    static final int START_TOKEN_SIZE = 4;
    static final int LENGTH_OFFSET = 5;
    static final int BODY_OFFSET = 7;
    static final int CRC_LENGTH = 1;
    static final int MINIMUM_PACKET_SIZE = 8;
    static final int MAXIMUM_PACKET_SIZE = 15;
    static final int RX_BUF_SIZE = 15;

    //ONE packet size MUST be less or equal to RX_BUF_SIZE
    //RX_BUF_SIZE = 15
    //ONE_PACKET_SIZE  = 10 (in this test)
    //packet format start[4], type[1], length[2], crc[1], data[n]
    static final byte[] PACKET_1 = {0x10, 0x11, 0x12, 0x13, (byte) 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00};
    //one packet with incorrect crc
    static final byte[] PACKET_1_INCORRECT = {0x10, 0x11, 0x12, 0x13, (byte) 0xF5, 0x0A, 0x00, 0x02, 0x00, 0x00};
    static final byte[] PACKET_2 = {0x10, 0x11, 0x12, 0x13, (byte) 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00,
            0x10, 0x11, 0x12, 0x13, (byte) 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00};
    static final byte[] PACKET_000_1 = {0, 0, 0, 0, 0, 0, 0, 0x10, 0x11, 0x12, 0x13,
            (byte) 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00};

    static final int onePacketSize = PACKET_1.length;
    static final int twoPacketsSize = onePacketSize * 2;
    static final int oneAndHalfSize = (int) (onePacketSize * 1.6);
    static final int halfSize = (onePacketSize * 2) - oneAndHalfSize;
    static final int headerSize = 4;

    private int packetsReceived = 0;
    private TimersManager timersManager = new TimersManager();
    private PacketsReceiver packetsReceiver;

    public PacketReceiverTest() {
        packetsReceiver = new PacketsReceiver(timersManager, protocolHandler, communicationDriver, packetConsumer, RX_BUF_SIZE);
    }

    @Before
    public void beforeTest() {
        packetsReceived = 0;
    }

    private void log(String text) {
        logger.info(text);
    }

    @Test
    public void oneCorrectPacket() throws Exception {
        packetsReceiver.onNewDataReceived(PACKET_1, 0, onePacketSize).get();
        Assert.assertEquals(packetsReceived, 1);
    }

    @Test
    public void halfAndHalf() throws Exception {
        log("Half (start, no size) and half");
        packetsReceiver.onNewDataReceived(PACKET_1, 0, 5);
        packetsReceiver.onNewDataReceived(PACKET_1, 5, onePacketSize - 5).get();
        Assert.assertEquals(packetsReceived, 1);
    }


    @Test
    public void byteByByte() throws Exception {
        log("Byte by byte");
        Future lastFuture = null;
        for (int i = 0; i < onePacketSize; i++) {
            lastFuture = packetsReceiver.onNewDataReceived(PACKET_1, i, 1);
        }
        lastFuture.get();
        Assert.assertEquals(packetsReceived, 1);
    }

    @Test
    public void oneIncorrect() throws Exception {
        packetsReceiver.onNewDataReceived(PACKET_1_INCORRECT, 0, onePacketSize).get();
        Assert.assertEquals(packetsReceived, 0);
    }

    @Test
    public void twoPackets() throws Exception {
        packetsReceiver.onNewDataReceived(PACKET_2, 0, oneAndHalfSize);
        packetsReceiver.onNewDataReceived(PACKET_2, oneAndHalfSize, halfSize).get();
        Assert.assertEquals(packetsReceived, 2);
    }

    @Test
    public void zeroesThenPacket() throws Exception {
        packetsReceiver.onNewDataReceived(PACKET_000_1, 0, PACKET_000_1.length).get();
        Assert.assertEquals(packetsReceived, 1);
    }

    @Test
    public void timeout() throws Exception {
        log("1.5 packets, then timeout, then 0.5");
        packetsReceiver.onNewDataReceived(PACKET_2, 0, oneAndHalfSize);
        Thread.sleep(100);
        packetsReceiver.onNewDataReceived(PACKET_2, oneAndHalfSize, halfSize).get();
        Assert.assertEquals(packetsReceived, 1);
    }

    @Test
    public void timeout2() throws Exception {
        log("header, timeout, next packet");
        packetsReceiver.onNewDataReceived(PACKET_1, 0, headerSize);
        Thread.sleep(100);
        packetsReceiver.onNewDataReceived(PACKET_1, headerSize, onePacketSize-headerSize).get();
        Assert.assertEquals(packetsReceived, 0);
    }


    private ProtocolHandler protocolHandler = new ProtocolHandler() {
        @Override
        public int findStartPosition(byte[] data, int offset, int length) {
            int foundPosition = searchSequense(data, offset, length, packetStart, 0, START_TOKEN_SIZE);
            return foundPosition;
        }

        @Override
        public int getBytesCountForRequiredForStartSearch() {
            return START_TOKEN_SIZE;
        }

        @Override
        public PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length) {
            PacketRecevingResult result = new PacketRecevingResult();
            int foundPacketSize = -1;


            if (length < BODY_OFFSET) { //we can not calculate packetSize at this moment
                result.setResultState(PacketRecevingResultStates.INCOMPLETE);
            } else {
                int packetSize = ByteUtils.bufToU16(data, offset + 5);
                if (packetSize > MAXIMUM_PACKET_SIZE || packetSize < MINIMUM_PACKET_SIZE) {
                    result.setResultState(PacketRecevingResultStates.TRASH);
                } else if (length >= packetSize) {
                    foundPacketSize = packetSize;

                    int calculatedCRC = 1;
                    int packetCRC = data[7];

                    if (calculatedCRC == packetCRC) {
                        result.setResultState(PacketRecevingResultStates.COMPLETE);
                    } else {
                        result.setResultState(PacketRecevingResultStates.TRASH);
                    }

                    result.setSize(foundPacketSize);
                } else {
                    result.setResultState(PacketRecevingResultStates.INCOMPLETE);
                }
            }
            return result;
        }

        @Override
        public int getApproximatePacketSize(byte[] data, int offset, int length) {
            if (length >= BODY_OFFSET) {
                int packetSize = ByteUtils.bufToU16(data, LENGTH_OFFSET);
                return packetSize;
            }
            return MAXIMUM_PACKET_SIZE;
        }

        @Override
        public void resetReceivingState() {

        }
    };

    private CommunicationDriver communicationDriver = new CommunicationDriver() {
        @Override
        public int getCurrentSpeed() {
            return 1; //1 byte/ms - 1KB/s
        }
    };

    private DataConsumer packetConsumer = new DataConsumer() {
        @Override
        public void accept(byte[] data, int offset, int size) {
            packetsReceived++;
        }
    };


}
