package org.example.udpplain;

import org.example.CommonConfig;
import org.example.communication.DataPipeStates;
import org.example.communication.KeepAlivePacketProducer;
import org.example.communication.PipeDataConsumer;
import org.example.endpoint.IncomingPacketAcceptor;
import org.example.packets.KeepAlive;
import org.example.softTimer.TimersManager;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


public class PeerToPeerConnectTestIT {

    final TimersManager timersManager = new TimersManager();

    public PeerToPeerConnectTestIT() throws IOException {
        CommonConfig.loadPropsAndClose();
    }

    private AtomicBoolean testSuccess = new AtomicBoolean(false);
    private Thread awaitThread = new Thread(() -> {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {

        }
    });

    PlainUdpEndPoint udpHoleEndPoint1;
    PlainUdpEndPoint udpHoleEndPoint2;

    /**
     * Before run install node, typescript
     * cd node-connector
     * npm install
     * tsc
     * node dist/index.js
     * you must have running sync server on localhost:3000
     */
    @Test
    public void ConnectAndSendKeepAliveIT() throws InterruptedException {
        UdpPlainDataPipe dataPipe1 = new UdpPlainDataPipe( 50001, "127.0.0.1:50002",  keepAlivePacketProducer, timersManager);
        UdpPlainDataPipe dataPipe2 = new UdpPlainDataPipe( 50002, "127.0.0.1:50001",  keepAlivePacketProducer, timersManager);
        udpHoleEndPoint1 = new PlainUdpEndPoint(dataPipe1, acceptor1, timersManager);
        udpHoleEndPoint2 = new PlainUdpEndPoint(dataPipe2, acceptor2, timersManager);

        timersManager.addTimer(500, true, () ->
        {
            if (dataPipe1.getCurrentState() == DataPipeStates.Alive) {
               udpHoleEndPoint1.packetWasBorn(new KeepAlive(), null);
            }
            if (dataPipe2.getCurrentState() == DataPipeStates.Alive) {
                udpHoleEndPoint2.packetWasBorn(new KeepAlive(), null);
            }
        });

        dataPipe1.startConnectAsync();
        dataPipe2.startConnectAsync();

        awaitThread.setDaemon(true);
        awaitThread.start();
        awaitThread.join();

        Assert.assertTrue(testSuccess.get());
    }

    private KeepAlivePacketProducer keepAlivePacketProducer=() -> {
      return new KeepAlive().toArray(true);
    };

    private PipeDataConsumer rawBytesConsumer1 = (data, offset, size, logId) -> {
        //TODO fix this cross reference
        udpHoleEndPoint1.onDataReceived(data, offset, size, logId);
    };

    private PipeDataConsumer rawBytesConsumer2 = (data, offset, size, logId) -> {
        udpHoleEndPoint2.onDataReceived(data, offset, size, logId);
    };

    //here we receive packets from client2
    private IncomingPacketAcceptor acceptor1 = (packet, logId) -> {
        testSuccess.set(true);
        awaitThread.interrupt();
    };

    //here we receive packets from client1
    private IncomingPacketAcceptor acceptor2 = (packet, logId) -> {
        testSuccess.set(true);
        awaitThread.interrupt();
    };
}
