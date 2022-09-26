package org.example.udphole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class ConnectionManager {
    private String thisName;
    private String thatName;
    volatile DatagramSocket socket;
    volatile InetSocketAddress successRemote;
    volatile byte[] remoteFingerprint;
    private EncryptionTool encryptionTool;
    private ConnectionStateListener connectionStateListener;
    private PacketsProviderAndAcceptor packetsProviderAndAcceptor;
    private Queue<PacketsBunchIn> inputPackets = new ConcurrentLinkedDeque<>();
    private Queue<PacketOut> outPackets = new ConcurrentLinkedDeque<>();
    private BlockingQueue<byte[]> encryptedOutPackets = new LinkedBlockingDeque<>(maxQueueSize);
    private Thread mainThread;
    private Thread receiveThread;
    private Thread sendingThread;
    private volatile boolean shouldWork = true;
    private volatile boolean receiveError = false;
    private volatile long lastReceiveTime;
    private volatile long lastSentTime;
    private static final int maxQueueSize = 100;
    private static final int connectionLost = 10;
    private static final int timeToSendKeepAlive = 2;
    private final int TIMEOUT_CONNECTION_SECONDS = 20;
    private int OLDNESS_SECONDS = 30;
    public static final int maxPacketSize = 65500;
    static final ExecutorService executorService = java.util.concurrent.Executors.newWorkStealingPool();
    private AesInitialization aesInitialization;
    private volatile AesPair aesPair;
    ThreadLocal<AesConfig> encrypt = new ThreadLocal<>();
    ThreadLocal<AesConfig> decrypt = new ThreadLocal<>();

    private PacketsLogger packetsLogger = new PacketsLogger();

    private BeansFactory factory;
    private static final Logger logger
            = LoggerFactory.getLogger("ConnectionLib");

    private Supplier<AesConfig> encSupplier = new Supplier<AesConfig>() {
        @Override
        public AesConfig get() {
            AesConfig enc = aesPair.getForwardEncryption();
            if (encrypt.get() == null) {
                encrypt.set(enc.clone());
            } else if (!encrypt.get().equals(enc)) {
                encrypt.set(enc.clone());
            }
            return encrypt.get();
        }
    };

    private Supplier<AesConfig> decSupplier = new Supplier<AesConfig>() {
        @Override
        public AesConfig get() {
            AesConfig dec = aesPair.getReceiveDecryption();
            if (decrypt.get() == null) {
                decrypt.set(dec.clone());
            } else if (!decrypt.get().equals(dec)) {
                decrypt.set(dec.clone());
            }
            return decrypt.get();
        }
    };


    public ConnectionManager(String thisName, String thatName,
                             ConnectionStateListener connectionStateListener,
                             PacketsProviderAndAcceptor packetsProviderAndAcceptor) {
        this.thisName = thisName;
        this.thatName = thatName;
        this.connectionStateListener = connectionStateListener;
        this.packetsProviderAndAcceptor = packetsProviderAndAcceptor;
        factory = FactoryHolder.getFactory();
        encryptionTool = factory.createAsymmetric();
        aesInitialization = new AesInitialization(encryptionTool, this::sendBytes);
    }

    public synchronized boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    private boolean aesInited() {
        return aesPair != null;
    }

    private void closeConnection() {
        if (socket != null) {
            socket.close();
        }
        socket = null;
    }


    private void stopThread(Thread thread) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startAndKeepAlive() {
        closeConnection();
        stopThread(receiveThread);
        stopThread(mainThread);
        stopThread(sendingThread);

        mainThread = new Thread(mainProcess, "ConnectionManager " + thisName);
        mainThread.setDaemon(true);
        mainThread.start();

        sendingThread = new Thread(sendingProcess, "SendingProcess " + thisName);
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    private void sleep(int amount) {
        try {
            Thread.sleep(amount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private boolean tryDecryptSymmetric(byte[] buf, int length) {
        //check old RSA asymmetric packet
        try {
            byte[] bytes = decSupplier.get().getCipher().doFinal(buf, 0, length);

            if (packetsProviderAndAcceptor.fastCheck(bytes)) {
                int logId = packetsLogger.addIncomingBunch(bytes);
                inputPackets.add(new PacketsBunchIn(logId, bytes));
                logger.debug("received packet size {}, logId {}, timestamp reset to {}", bytes.length, logId, lastReceiveTime);
                packetsProviderAndAcceptor.onIncomingPacket();
                return true;
            } else {
                packetsLogger.addIncomingBunch(bytes);
            }
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            //ignore
        }
        return false;
    }

    private void resetReceiveWatchdog() {
        lastReceiveTime = Utils.nowMs();
    }

    private final Runnable readProcess = () -> {
        final byte[] receiveBuf = new byte[maxPacketSize];
        while (shouldWork) {
            if (isConnected()) {
                try {
                    DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);
                    socket.receive(packet);
                    int remotePort = packet.getPort();
                    if (remotePort != successRemote.getPort()) {
                        logger.warn("Success remote {}, but received packet from {}", successRemote, packet.getSocketAddress());
                    }

                    final int length = packet.getLength();
                    final boolean aesInited = aesInited();
                    if (!aesInited && length == Configuration.RSA_MinimumSize) {
                        aesInitialization.onReceive(receiveBuf, 0, length, inited -> {
                            aesPair = aesInitialization.createPair();
                            aesInitialization = null;
                            logger.info("Aes initialized");
                        });
                        resetReceiveWatchdog();
                    } else if (aesInited && length % Configuration.AES_SIZE == 0) {
                        resetReceiveWatchdog();
                        final byte[] receivedEncryptedData = Utils.copyBytes(receiveBuf, 0, length);
                        executorService.submit(() -> {
                            tryDecryptSymmetric(receivedEncryptedData, length);
                        });
                    } else {
                        logger.warn(String.format("Strange case: aes inited = %s, length = %d, %s",
                                aesInited, length, Utils.toHexString(receiveBuf, Math.min(length, 30))));
                    }
                } catch (IOException e) {
                    receiveError = true;
                    return;
                } catch (Exception e) {
                    logger.error("error", e);
                }
            } else {
                return;
            }
        }
    };

    private final Runnable mainProcess = () -> {
        while (shouldWork) {
            if (!isConnected()) {
                try {
                    stopThread(receiveThread);
                    if (connect()) {
                        connectionStateListener.onConnected();
                        receiveThread = new Thread(readProcess, "receiveDataThread " + thisName);
                        receiveThread.setDaemon(true);
                        lastReceiveTime = Utils.nowMs();
                        lastSentTime = lastReceiveTime;
                        receiveError = false;
                        receiveThread.start();
                    }
                } catch (Exception e) {
                    logger.error("ConnectionManager connect {}", e.getMessage());
                }
            } else {
                //Connected - check no receive data timeout
                if (Utils.elapsedSeconds(connectionLost, lastReceiveTime)) {
                    closeConnection();
                    logger.debug("socket closed due receive timeout ");
                    connectionStateListener.onConnectionLost();
                } else if (!aesInited()) {
                    final AesInitialization aesInit = aesInitialization;
                    if (aesInit != null) {
                        aesInit.sendInvoke();
                    }
                } else if (aesInited() && Utils.elapsedSeconds(timeToSendKeepAlive, lastSentTime)
                        && outPackets.size() == 0) {
                    sendClientLevelPacket(packetsProviderAndAcceptor.getKeepAlive());
                    logger.debug("keep alive sent by " + thisName);
                    sleep(100);
                } else {
                    PacketOut nextPacket = outPackets.poll();
                    if (nextPacket != null) {
                        if (aesInited()) {
                            executorService.submit(() -> {
                                sendClientLevelPacket(nextPacket);
                            });
                        }
                    } else {
                        sleep(20);
                    }
                }
            }
        }
    };

    private void sendClientLevelPacket(PacketOut outPacket) {
        byte[] notEncrypted = outPacket.getData();
        if (notEncrypted.length > maxPacketSize) {
            throw new RuntimeException(String.format("Too large packet size %d", notEncrypted.length));
        }
        if (isConnected()) {
            try {
                packetsLogger.addOutgoingBunch(outPacket);
                notEncrypted = Utils.makeAesPadding(notEncrypted);
                byte[] encryptedBytes = encSupplier.get().getCipher().doFinal(notEncrypted);
                encryptedOutPackets.add(encryptedBytes);
            } catch (BadPaddingException | IllegalBlockSizeException e) {
                logger.error("During encrypt", e);
            }
            lastSentTime = Utils.nowMs();
        }
    }

    private final Runnable sendingProcess = () -> {
        while (shouldWork) {
            try {
                if (isConnected()) {
                    byte[] encryptedBytes = encryptedOutPackets.poll(2, TimeUnit.SECONDS);
                    if (encryptedBytes != null) {
                        sendBytes(encryptedBytes, 0, encryptedBytes.length);
                    }
                } else {
                    encryptedOutPackets.clear();
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                logger.error("During send", e);
                break;
            } catch (Exception e) {
                logger.error("During send", e);
            }
        }
    };

    private void sendBytes(byte[] buf, int offset, int length) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, offset, length, successRemote);
        socket.send(packet);
    }

    /*
    0. retrieve info about itself
    1. push info about itself every 10 seconds
    2. during 20 seconds await other
    3. if found, start connecting during 30
    */
    private boolean connect() throws Exception {
        ClientInfo thisClient;
        ClientInfo thatClient;
        UDPHolePuncher connection;

        this.aesInitialization = new AesInitialization(encryptionTool, this::sendBytes);
        this.aesPair = null;

        ClientPublisher cp = new ClientPublisher(thisName);

        thisClient = cp.retrievePublicAddress(factory.getStunServers());
        if (thisClient == null) {
            return false;
        }
        try {
            if (cp.sendInfo(thisClient)) {
                logger.debug("Sent info to sync server about {}", thisClient);
                long start = Utils.nowMs();
                int oldness = OLDNESS_SECONDS - TIMEOUT_CONNECTION_SECONDS;
                //start from 10 seconds to 30 seconds
                while (true) {
                    try {
                        thatClient = cp.retrieveInfoAbout(thatName, (int) (oldness + (Utils.nowMs() - start) / 1000));
                    } catch (Exception ignored) {
                        logger.error(String.format("Error during receiving info about ", thatName),
                                ignored.getMessage());
                        continue;
                    }
                    if (thatClient != null) {
                        logger.debug("retrieved info about {}\n", thatClient);
                        break;
                    }
                    sleep(500);
                    if (Utils.elapsedSeconds(TIMEOUT_CONNECTION_SECONDS, start)) {
                        //remove all rows which older than 3 minutes
                        executorService.submit(cp::removeOldRecords);
                        return false;
                    }
                }
                logger.info("starting connecting between \n{} \n{}", thisClient, thatClient);
                connection = new UDPHolePuncher(thisClient, thatClient);
                try {
                    if (connection.connect(TIMEOUT_CONNECTION_SECONDS)) {
                        socket = connection.getSocket();
                        encryptionTool.applyKeys(thisClient.getKeyForEncrypt(), thatClient.getKeyForDecrypt());
                        successRemote = getBestConnection(connection.getRemoteEndpoints(), thisClient);
                        remoteFingerprint = connection.getRemoteFingerprint();
                        logger.info("received echo from  {}", successRemote);
                        //remove all rows which older than 3 minutes
                        executorService.submit(cp::removeOldRecords);
                        return true;
                    } else {
                        logger.info("connection timeout");
                    }
                } catch (Exception exception) {
                    logger.error("Udp Connection {}", exception.getMessage());
                }
            }

        } catch (HttpException ex) {
            logger.error("during sync tool", ex);
            //some Http error
            sleep(1500);
        }
        return false;
    }

    InetSocketAddress getBestConnection(List<UDPEndPoint> remoteEndPoints, ClientInfo thisClient) {
        InetSocketAddress result = remoteEndPoints.get(0).getSocketAddress();
        //switching between local addresses
        if (!remoteEndPoints.isEmpty() && remoteEndPoints.get(0).getAddressType() == UDPEndPoint.AddressTypes.LOCAL) {
            //use the same subnet
            List<InetAddress> localAddresses = ClientInfo.stringToAddresses(thisClient.getLocalIP());
            for (UDPEndPoint endPoint : remoteEndPoints) {
                logger.debug("Remote endpoint candidate {}", endPoint);
                byte[] endPointBytes = endPoint.getSocketAddress().getAddress().getAddress();
                for (InetAddress localAddress : localAddresses) {
                    logger.debug("compare remote endpoint {} with local address {}", endPoint, localAddress);
                    byte[] localAddressBytes = localAddress.getAddress();
                    if (endPointBytes[0] == localAddressBytes[0] && endPointBytes[1] == localAddressBytes[1]
                            && endPointBytes[2] == localAddressBytes[2]) {
                        result = endPoint.getSocketAddress();
                        break;
                    }
                }
            }
        }

        logger.debug("For this {} chosen {} ", thisClient, result);
        return result;
    }


    public Queue<PacketsBunchIn> getInputPackets() {
        return inputPackets;
    }

    public Queue<PacketOut> getOutPackets() {
        return outPackets;
    }


    public void stopAndJoin() throws InterruptedException {
        shouldWork = false;
        join();
    }

    public void join() throws InterruptedException {
        mainThread.join();
    }

    public boolean isWorking() {
        return shouldWork;
    }
}
