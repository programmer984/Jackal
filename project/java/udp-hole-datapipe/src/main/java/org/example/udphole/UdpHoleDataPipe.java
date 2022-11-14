package org.example.udphole;

import org.example.CommonConfig;
import org.example.PathUtils;
import org.example.TimeUtils;
import org.example.communication.*;
import org.example.communication.logging.DataLogger;
import org.example.communication.logging.FileSystemPacketsLogger;
import org.example.communication.logging.NoLogger;
import org.example.communication.logging.PostLogger;
import org.example.softTimer.TimerCallback;
import org.example.tools.UdpHoleDataPipeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * outgoing - collect, combine, in-parallel cipher
 * control pending count - notify if too much
 * incoming - push in-parallel
 */
public class UdpHoleDataPipe implements DataPipe {
    private static final Logger logger
            = LoggerFactory.getLogger(UdpHoleDataPipe.class);
    private PipeDataConsumer incomingDataConsumer;
    private DataPipeStates state = DataPipeStates.Idle;
    private ConnectionManager connectionManager;
    private static final int maxQueueSize = 20; //should keep 1 splitted videoframe
    private static final int KEEP_ALIVE_RECEIVING_PERIOD = 4;//seconds
    private static final int KEEP_ALIVE_SENDING_PERIOD = 1;//we must to send something every second
    private static final int WATCHDOG_PERIOD=1000;//ms
    private final BlockingQueue<Future<semicolonedData>> packetsQueue = new LinkedBlockingDeque<>(maxQueueSize);
    private final ExecutorService executorService = java.util.concurrent.Executors.newWorkStealingPool();
    private final String thisName;
    private Thread sendingEncryptedDataThread;
    private DataLogger packetsLogger;
    private boolean connectCommandWasInvoked = false;
    private Object sentLock = new Object();
    private Object receiveLock = new Object();
    private long lastReceivedTimestamp;
    private long lastSentTimestamp;
    private KeepAlivePacketProducer keepAlivePacketProducer;

    public UdpHoleDataPipe(String thisName, String thatName,
                           UdpHoleDataPipeFactory factory, KeepAlivePacketProducer keepAlivePacketProducer) {
        this.thisName = thisName;
        this.keepAlivePacketProducer = keepAlivePacketProducer;
        this.connectionManager = new ConnectionManager(thisName, thatName,
                incomingDataConsumerProxy, stateChangeListener,
                factory);
        if (CommonConfig.logPackets) {
            packetsLogger = new FileSystemPacketsLogger(PathUtils.resolve(CommonConfig.packetsDir, thisName));
        } else {
            packetsLogger = new NoLogger();
        }
        factory.getTimersManager().addTimer(WATCHDOG_PERIOD, true, keepWatchdog);
    }

    synchronized boolean isConnectCommandWasInvoked() {
        return connectCommandWasInvoked;
    }

    synchronized void setConnectCommandWasInvoked(boolean connectCommandWasInvoked) {
        this.connectCommandWasInvoked = connectCommandWasInvoked;
    }

    long getLastReceivedTimestamp() {
        synchronized (receiveLock) {
            return lastReceivedTimestamp;
        }
    }

    void setLastReceivedTimestamp(long lastReceivedTimestamp) {
        synchronized (receiveLock) {
            this.lastReceivedTimestamp = lastReceivedTimestamp;
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
        if (isConnectCommandWasInvoked() && getCurrentState() == DataPipeStates.Idle) {
            startConnectAsync();
        }
        if (getCurrentState() == DataPipeStates.Alive) {
            if (TimeUtils.elapsedSeconds(KEEP_ALIVE_RECEIVING_PERIOD, getLastReceivedTimestamp())) {
                stop();
                logger.warn("Connection stopped by watchdog");
            }
            if (TimeUtils.elapsedSeconds(KEEP_ALIVE_SENDING_PERIOD, getLastSentTimestamp())) {
                sendKeepAlive();
            }
        }
    };

    @Override
    public void setIncomingDataConsumer(PipeDataConsumer incomingDataConsumer) {
        this.incomingDataConsumer = incomingDataConsumer;
    }

    @Override
    public void startConnectAsync() {
        synchronized (connectionManager) {
            if (getCurrentState() == DataPipeStates.Idle) {
                setState(DataPipeStates.Connecting);
                setConnectCommandWasInvoked(true);
                logger.info("Starting connection manager");
                if (sendingEncryptedDataThread == null) {
                    sendingEncryptedDataThread = new Thread(sendingProcess, "udphole-send-" + this.thisName);
                    sendingEncryptedDataThread.setDaemon(true);
                    sendingEncryptedDataThread.start();
                }
                connectionManager.startAndKeepAlive();
            }
        }
    }

    @Override
    public void stop() {
        setConnectCommandWasInvoked(false);
        setState(DataPipeStates.Idle);
        connectionManager.reset();
    }


    private synchronized void setState(DataPipeStates state) {
        this.state = state;
    }

    @Override
    public synchronized DataPipeStates getCurrentState() {
        return state;
    }

    @Override
    public void sendData(byte[] data, int offset, int length, PostLogger postLogger) {
        if (connectionManager.isHolePunched()) {
            try {
                packetsQueue.put(executorService.submit(() -> {
                    semicolonedData semicolonedData = new semicolonedData(data, offset, length, postLogger);
                    semicolonedData.setEncryptedData(connectionManager.encrypt(data, offset, length));
                    return semicolonedData;
                }));
            } catch (InterruptedException e) {
                logger.error("outgoing queue put", e);
            }
        } else {
            logger.warn("send data skipped. length of the lost data {}", length);
        }
    }

    private void sendKeepAlive(){
        byte[] data =keepAlivePacketProducer.createKeepAlive();
        sendData(data, 0, data.length, logId -> {
            logger.info("Keep alive sent");
        });
    }

    private PipeDataConsumer incomingDataConsumerProxy = (data, offset, size, logIdNull) -> {
        setLastReceivedTimestamp(TimeUtils.nowMs());
        Integer logId = packetsLogger.addIncomingBunch(data);
        incomingDataConsumer.onDataReceived(data, offset, size, logId);
    };

    private StateChangeListener stateChangeListener = new StateChangeListener() {
        @Override
        public void onConnected() {
            setLastReceivedTimestamp(TimeUtils.nowMs());
            setLastSentTimestamp(TimeUtils.nowMs());
            sendKeepAlive();
            setState(DataPipeStates.Alive);
        }

        @Override
        public void onConnectFailed() {
            setState(DataPipeStates.Idle);
            logger.info("Connect failed");
        }

        @Override
        public void onDisconnected() {
            setState(DataPipeStates.Idle);
            logger.info("Disconnected");
        }
    };

    private final Runnable sendingProcess = () -> {
        while (true) {
            try {
                if (connectionManager.isHolePunched()) {
                    Future<semicolonedData> nextBunchFuture = packetsQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (nextBunchFuture != null) {
                        semicolonedData nextBunch = nextBunchFuture.get();
                        connectionManager.sendEncrypted(nextBunch.getEncryptedData());
                        packetsLogger.addOutgoingBunch(nextBunch.data, nextBunch.postLogger);
                        setLastSentTimestamp(TimeUtils.nowMs());
                    }
                } else {
                    if (!packetsQueue.isEmpty()) {
                        logger.debug("Packets to send queue is cleared {}", packetsQueue.size());
                        packetsQueue.clear();
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                packetsQueue.clear();
                break;
            } catch (Exception e) {
                logger.error("During send", e);
            }
        }
    };

    private class semicolonedData {
        private final byte[] data;
        private final int offset;
        private final int length;
        private final PostLogger postLogger;
        private byte[] encryptedData;

        private semicolonedData(byte[] data, int offset, int length, PostLogger postLogger) {
            if (length % CommonConfig.AES_SIZE != 0) {
                throw new RuntimeException("Please align data");
            }
            this.data = data;
            this.offset = offset;
            this.length = length;
            this.postLogger = postLogger;
        }

        public byte[] getEncryptedData() {
            return encryptedData;
        }

        public void setEncryptedData(byte[] encryptedData) {
            this.encryptedData = encryptedData;
        }
    }
}
