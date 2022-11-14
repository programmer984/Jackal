package org.example.udphole;

import org.example.CommonConfig;
import org.example.PathUtils;
import org.example.communication.DataPipe;
import org.example.communication.DataPipeStates;
import org.example.communication.PipeDataConsumer;
import org.example.communication.StateChangeListener;
import org.example.communication.logging.DataLogger;
import org.example.communication.logging.FileSystemPacketsLogger;
import org.example.communication.logging.NoLogger;
import org.example.communication.logging.PostLogger;
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
    private DataPipeStates state;
    private ConnectionManager connectionManager;
    private static final int maxQueueSize = 5;
    private final BlockingQueue<Future<semicolonedData>> packetsQueue = new LinkedBlockingDeque<>(maxQueueSize);
    private final ExecutorService executorService = java.util.concurrent.Executors.newWorkStealingPool();
    private final String thisName;
    private Thread sendingEncryptedDataThread;
    private DataLogger packetsLogger;

    public UdpHoleDataPipe(String thisName, String thatName,
                           UdpHoleDataPipeFactory factory) {
        this.thisName = thisName;
        this.connectionManager = new ConnectionManager(thisName, thatName,
                incomingDataConsumerProxy, stateChangeListener,
                factory);
        if (CommonConfig.logPackets) {
            packetsLogger = new FileSystemPacketsLogger(PathUtils.resolve(CommonConfig.packetsDir, thisName));
        } else {
            packetsLogger = new NoLogger();
        }
    }

    @Override
    public void setIncomingDataConsumer(PipeDataConsumer incomingDataConsumer) {
        this.incomingDataConsumer = incomingDataConsumer;
    }

    @Override
    public void startConnectAsync() {
        setState(DataPipeStates.Connecting);
        if (sendingEncryptedDataThread == null) {
            sendingEncryptedDataThread = new Thread(sendingProcess, "udphole-send-" + this.thisName);
            sendingEncryptedDataThread.setDaemon(true);
            sendingEncryptedDataThread.start();
        }
        connectionManager.startAndKeepAlive();
    }

    @Override
    public void stop() {
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
        }
    }


    private PipeDataConsumer incomingDataConsumerProxy = (data, offset, size, logIdNull) -> {
        Integer logId = packetsLogger.addIncomingBunch(data);
        incomingDataConsumer.onDataReceived(data, offset, size, logId);
    };

    private StateChangeListener stateChangeListener = new StateChangeListener() {
        @Override
        public void onConnected() {
            setState(DataPipeStates.Alive);
        }

        @Override
        public void onConnectFailed() {
            setState(DataPipeStates.Idle);
        }

        @Override
        public void onDisconnected() {
            setState(DataPipeStates.Idle);
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
