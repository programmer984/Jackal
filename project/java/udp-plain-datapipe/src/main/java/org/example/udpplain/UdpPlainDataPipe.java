package org.example.udpplain;

import org.example.*;
import org.example.communication.DataPipe;
import org.example.communication.DataPipeStates;
import org.example.communication.KeepAlivePacketProducer;
import org.example.communication.PipeDataConsumer;
import org.example.communication.logging.DataLogger;
import org.example.communication.logging.FileSystemPacketsLogger;
import org.example.communication.logging.NoLogger;
import org.example.communication.logging.PostLogger;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimerCallback;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class UdpPlainDataPipe implements DataPipe {
    private static final Logger logger
            = LoggerFactory.getLogger(UdpPlainDataPipe.class);
    private PipeDataConsumer incomingDataConsumer;
    private final KeepAlivePacketProducer keepAlivePacketProducer;
    private TimersManager timersManager;
    private static final int KEEP_ALIVE_SENDING_PERIOD = 1;//we must to send something every second
    private static final int WATCHDOG_PERIOD = 1000;//ms
    private static final int maxPacketSize = 2000;
    private Object sentLock = new Object();
    private long lastSentTimestamp;
    private Dispatcher dispatcher;
    private DatagramSocket socket;
    private SoftTimer watchDogTimer;
    private Thread readerThread;
    private InetSocketAddress remoteAddress;
    private DataLogger packetsLogger;
    private int plainUdpLocalPort;
    private String plainUdpRemoteAddress;

    public UdpPlainDataPipe(KeepAlivePacketProducer keepAlivePacketProducer, TimersManager timersManager) {
        this(CommonConfig.plainUdpLocalPort, CommonConfig.plainUdpRemoteAddress, keepAlivePacketProducer, timersManager);
    }

    public UdpPlainDataPipe(Integer plainUdpLocalPort, String plainUdpRemoteAddress,
                            KeepAlivePacketProducer keepAlivePacketProducer, TimersManager timersManager) {
        this.plainUdpLocalPort = plainUdpLocalPort;
        this.plainUdpRemoteAddress = plainUdpRemoteAddress;
        this.keepAlivePacketProducer = keepAlivePacketProducer;
        this.timersManager = timersManager;
        dispatcher = new Dispatcher(100, "plain-udp-sender");
        if (CommonConfig.logPackets) {
            packetsLogger = new FileSystemPacketsLogger(PathUtils.resolve(CommonConfig.packetsDir, "plain-udp-"+System.currentTimeMillis()));
        } else {
            packetsLogger = new NoLogger();
        }
    }

    @Override
    public void startConnectAsync() {
        dispatcher.submitBlocking(() -> {
            socket = new DatagramSocket(plainUdpLocalPort);
            remoteAddress = IpUtils.parseIpAndPort(plainUdpRemoteAddress);
            readerThread = new Thread(readerThreadLoop);
            readerThread.setName("plain-udp-receiver");
            readerThread.setDaemon(true);
            readerThread.start();
            if (watchDogTimer == null) {
                watchDogTimer = timersManager.addTimer(WATCHDOG_PERIOD, true, keepWatchdog, "Watchdog");
            }
        });
    }

    @Override
    public void stop() {

    }

    @Override
    public DataPipeStates getCurrentState() {
        return DataPipeStates.Alive;
    }

    @Override
    public void sendData(byte[] data, int offset, int length, PostLogger postLogger) {
        if (!dispatcher.isFull()) {
            dispatcher.submitBlocking(() -> {
                try {
                    socket.send(new DatagramPacket(data, offset, length, remoteAddress));
                    packetsLogger.addOutgoingBunch(data, offset, length, postLogger);
                    setLastSentTimestamp(TimeUtils.nowMs());
                } catch (IOException e) {
                    logger.error("During data sending", e);
                }
            });
        } else {
            logger.warn("Dispatcher is full");
        }
    }

    long getLastSentTimestamp() {
        synchronized (sentLock) {
            return lastSentTimestamp;
        }
    }

    void setLastSentTimestamp(long lastSentTimestamp) {
        synchronized (sentLock) {
            this.lastSentTimestamp = lastSentTimestamp;
        }
    }

    private TimerCallback keepWatchdog = () -> {
        if (getCurrentState() == DataPipeStates.Alive) {
            if (TimeUtils.elapsedSeconds(KEEP_ALIVE_SENDING_PERIOD, getLastSentTimestamp())) {
                sendKeepAlive();
            }
        }
    };


    private void sendKeepAlive() {
        byte[] data = keepAlivePacketProducer.createKeepAlive();
        sendData(data, 0, data.length, logId -> {
            logger.info("Keep alive sent");
        });
    }

    @Override
    public void setIncomingDataConsumer(PipeDataConsumer incomingDataConsumer) {
        this.incomingDataConsumer = incomingDataConsumer;
    }

    Runnable readerThreadLoop = () -> {
        final byte[] receiveBuf = new byte[maxPacketSize];
        DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                socket.receive(packet);
                int length = packet.getLength();
                byte[] copy = ByteUtils.copyBytes(receiveBuf, 0, length);
                Integer logId = packetsLogger.addIncomingBunch(copy);
                incomingDataConsumer.onDataReceived(copy, 0, length, logId);
            } catch (Exception e) {
                logger.error("During data receiving", e);
            }
        }
    };


}
