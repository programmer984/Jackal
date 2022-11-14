package org.example.udphole;

import org.example.CommonConfig;
import org.example.communication.DataPipeStates;
import org.example.communication.KeepAlivePacketProducer;
import org.example.communication.PipeDataConsumer;
import org.example.endpoint.IncomingPacketAcceptor;
import org.example.packets.KeepAlive;
import org.example.softTimer.TimersManager;
import org.example.tools.*;
import org.example.udphole.sync.NodeTool;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;


public class PeerToPeerConnectTestIT {

    private final UdpHoleDataPipeFactory factory;

    public PeerToPeerConnectTestIT() throws IOException {
        CommonConfig.loadPropsAndClose();
        this.factory = new UdpHoleDataPipeFactory() {
            final TimersManager timersManager = new TimersManager();

            @Override
            public Base64Tool createBase64Tool() {
                return new Base64Encoder();
            }

            @Override
            public SynchronizationTool createSynchronizationTool() {
                return new NodeTool(createJsonTool(), createHttpTool());
            }

            @Override
            public RsaEncryptionTool createRsaEncryptionTool() {
                try {
                    return new RsaEncryption(createBase64Tool());
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public AesEncryptionTool createAesEncryptionTool() {
                try {
                    return new AesEncryption();
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public JsonTool createJsonTool() {
                return new Jackson();
            }

            @Override
            public HttpTool createHttpTool() {
                return new ApacheHttpClient();
            }

            @Override
            public TimersManager getTimersManager() {
                return timersManager;
            }
        };
    }

    private AtomicBoolean testSuccess = new AtomicBoolean(false);
    private Thread awaitThread = new Thread(() -> {
        try {
            Thread.sleep(30000);
        } catch (InterruptedException ignored) {

        }
    });

    UdpHoleEndPoint udpHoleEndPoint1;
    UdpHoleEndPoint udpHoleEndPoint2;

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
        String client1 = "client1";
        String client2 = "client2";

        UdpHoleDataPipe dataPipe1 = new UdpHoleDataPipe(client1, client2, factory, keepAlivePacketProducer);
        UdpHoleDataPipe dataPipe2 = new UdpHoleDataPipe(client2, client1, factory, keepAlivePacketProducer);
        udpHoleEndPoint1 = new UdpHoleEndPoint(dataPipe1, acceptor1, factory.getTimersManager());
        udpHoleEndPoint2 = new UdpHoleEndPoint(dataPipe2, acceptor2, factory.getTimersManager());

        factory.getTimersManager().addTimer(500, true, () ->
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

    private KeepAlivePacketProducer keepAlivePacketProducer = () -> {
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
