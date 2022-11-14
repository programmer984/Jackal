package org.example.services.videoproducer.codec;

import org.example.packetsReceiver.CommunicationDriver;
import org.example.packetsReceiver.OnePacketConsumer;
import org.example.packetsReceiver.PacketsReceiverStreamCollector;
import org.example.softTimer.TimersManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class NativeInteraction {
    private final static int RX_BUF_SIZE = 100;
    private CodecNativeProtocolHandler protocolHandler = new CodecNativeProtocolHandler(100);
    private int packetsReceived = 0;
    private TimersManager timersManager = new TimersManager();
    private PacketsReceiverStreamCollector packetsReceiver;
    static final byte[] PACKET = {0x45, 0x45, 0x47, 0x01, 10, 0, 0, 0,  //header
            0, 0, 0, 1, 0x67, 0x42, (byte) 0xC0, 0x0D, (byte) 0x92,
            0x54, 0x0A, 0x0F, (byte) 0xD0, 0x0F, 0x14, 0x2A};

    private CommunicationDriver communicationDriver = () -> {
        return 100; //100 byte/ms
    };

    private OnePacketConsumer packetConsumer = (data, offset, size, logId) -> {
        if (data[12] == PACKET[12]) {
            packetsReceived++;
        }
    };

    public NativeInteraction() {
        packetsReceiver = new PacketsReceiverStreamCollector(timersManager, protocolHandler, communicationDriver, packetConsumer, RX_BUF_SIZE);
    }


    @Before
    public void beforeTest() {
        packetsReceived = 0;
    }

    @Test
    public void TestReceiving() {
        packetsReceiver.onNewDataReceived(PACKET, 0, 8);
        packetsReceiver.onNewDataReceived(PACKET, 8, 2);
        packetsReceiver.onNewDataReceived(PACKET, 10, 10);
        Assert.assertEquals(packetsReceived, 1);
    }

    @Test
    public void TestReceivingOneByOne() {
        for (int i=0;i<PACKET.length;i++){
            packetsReceiver.onNewDataReceived(PACKET, i, 1);
        }
        Assert.assertEquals(packetsReceived, 1);
    }

    @Test
    public void RealDataTest() throws IOException {
        byte[] buffer =new byte[100];

        int size = getClass().getClassLoader()
                .getResourceAsStream("00000043 - IN.bin").read(buffer, 0, buffer.length);
        packetsReceiver.onNewDataReceived(buffer, 0, size);

        size = getClass().getClassLoader()
                .getResourceAsStream("00000044 - IN.bin").read(buffer, 0, buffer.length);
        packetsReceiver.onNewDataReceived(buffer, 0, size);

        Assert.assertEquals(packetsReceived, 1);
    }

}
