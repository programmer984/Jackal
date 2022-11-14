import org.example.CommonConfig;
import org.example.packets.KeepAlive;
import org.example.packetsReceiver.CommunicationDriver;
import org.example.packetsReceiver.OnePacketConsumer;
import org.example.packetsReceiver.PacketReceiverOneShot;
import org.example.packetsReceiver.PacketsReceiverStreamCollector;
import org.example.protocol.InternalProtocolHandler;
import org.example.softTimer.TimersManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class InternalProtocolTest {
    private static final int BUFFER_SIZE = 2 * 1024;
    int packetsFound = 0;

    @Before
    public void beforeTest() {
        packetsFound = 0;
    }

    @Test
    public void TestKeepAliveReceiving() throws InterruptedException, ExecutionException {
        KeepAlive keepAlivePacket = new KeepAlive();
        byte[] keepAliveBuf = keepAlivePacket.toArray(false);
        byte[] keepAliveBufSemicoloned = keepAlivePacket.toArray(true);
        PacketsReceiverStreamCollector packetsReceiver = new PacketsReceiverStreamCollector(new TimersManager(), new InternalProtocolHandler(), communicationDriver, onePacketConsumer, BUFFER_SIZE);
        packetsReceiver.onNewDataReceived(keepAliveBuf, 0, keepAliveBuf.length, 445453);
        packetsReceiver.onNewDataReceived(keepAliveBufSemicoloned, 0, keepAliveBufSemicoloned.length, 445454);
        Assert.assertEquals(packetsFound, 2);
    }

    @Test
    public void RealUdpPacketTest() throws Exception {
        try(InputStream inputStream = CommonConfig.class.getClassLoader()
                .getResourceAsStream("00030231 - IN.bin")){
            byte[] buf = new byte[1500];
            int readSize = inputStream.read(buf, 0, buf.length);
            PacketReceiverOneShot packetsReceiver = new PacketReceiverOneShot(new InternalProtocolHandler(), onePacketConsumer);
            packetsReceiver.onNewDataReceived(buf, 0, readSize, 30231);
            Assert.assertEquals(packetsFound, 2);
        }
    }

    private final OnePacketConsumer onePacketConsumer = (data, offset, size, logId) -> {
        packetsFound++;
    };
    private CommunicationDriver communicationDriver = () -> 1;
}
