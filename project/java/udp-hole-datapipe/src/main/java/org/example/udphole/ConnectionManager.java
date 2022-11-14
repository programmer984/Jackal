package org.example.udphole;

import org.example.ByteUtils;
import org.example.TimeUtils;
import org.example.communication.PipeDataConsumer;
import org.example.communication.StateChangeListener;
import org.example.encryption.AesEncryption;
import org.example.encryption.EncryptionProxy;
import org.example.encryption.RsaEncryption;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.example.tools.UdpHoleDataPipeFactory;
import org.example.tools.HttpException;
import org.example.tools.RsaEncryptionTool;
import org.example.udphole.sync.ClientInfo;
import org.example.udphole.sync.ClientPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.security.GeneralSecurityException;
import java.util.List;

class ConnectionManager {
    private final int TIMEOUT_CONNECTION_SECONDS = 20;
    private int OLDNESS_SECONDS = 30;

    private String thisName;
    private String thatName;
    private PipeDataConsumer clientDataConsumer;
    private StateChangeListener stateChangeListener;
    private DatagramSocket socket;
    private InetSocketAddress successRemote;
    private final UdpHoleDataPipeFactory factory;
    private EncryptionProxy encryptionProxy;
    private Thread connectionEstablishThread;
    private Thread receiveThread;
    private TimersManager timersManager;
    private SoftTimer overallTimeoutTimer;

    public static final int maxPacketSize = 2000;
    private AesInitialization aesInitialization;

    private static final Logger logger
            = LoggerFactory.getLogger(ConnectionManager.class);


    public ConnectionManager(String thisName, String thatName,
                             PipeDataConsumer clientDataConsumer, StateChangeListener stateChangeListener,
                             UdpHoleDataPipeFactory factory) {
        this.thisName = thisName;
        this.thatName = thatName;
        this.clientDataConsumer = clientDataConsumer;
        this.stateChangeListener = stateChangeListener;
        this.factory = factory;
        this.timersManager = factory.getTimersManager();
    }


    public void reset() {
        logger.info("Reset called");
        if (overallTimeoutTimer != null) {
            timersManager.removeTimer(overallTimeoutTimer);
            overallTimeoutTimer = null;
        }
        AesInitialization aesInitialization = getAesInitialization();
        if (aesInitialization != null) {
            aesInitialization.resetTimer();
        }
        setAesInitialization(null);
        setEncryptionProxy(null);


        stopThreadIfAlive(receiveThread);
        receiveThread = null;
        stopThreadIfAlive(connectionEstablishThread);
        connectionEstablishThread = null;

        if (getSocket() != null) {
            getSocket().close();
        }
        setSocket(null);
    }


    public synchronized boolean isHolePunched() {
        return socket != null && !socket.isClosed();
    }

    synchronized DatagramSocket getSocket() {
        return socket;
    }

    synchronized void setSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    public void startAndKeepAlive() {
        reset();

        overallTimeoutTimer = factory.getTimersManager()
                .addTimer(TIMEOUT_CONNECTION_SECONDS * 1000, false, () -> {
                    //assume if encryption proxy exists connection good
                    if (getEncryptionProxy() == null) {
                        reset();
                        stateChangeListener.onConnectFailed();
                    }
                });

        connectionEstablishThread = new Thread(connectionEstablishProcess, "udp-hole-connecting-" + thisName);
        connectionEstablishThread.setDaemon(true);
        connectionEstablishThread.start();

    }


    public byte[] encrypt(byte[] semicolonedData, int offset, int length) throws GeneralSecurityException {
        return encryptionProxy.encrypt(semicolonedData, offset, length);
    }

    public void sendEncrypted(byte[] encryptedData) throws IOException {
        encryptionProxy.sendEncryptedData(encryptedData, 0, encryptedData.length);
    }

    private void sendBytes(byte[] buf, int offset, int length) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, offset, length, successRemote);
        getSocket().send(packet);
    }

    private synchronized EncryptionProxy getEncryptionProxy() {
        return encryptionProxy;
    }

    private synchronized void setEncryptionProxy(EncryptionProxy encryptionProxy) {
        this.encryptionProxy = encryptionProxy;
    }

    private synchronized AesInitialization getAesInitialization() {
        return aesInitialization;
    }

    private synchronized void setAesInitialization(AesInitialization aesInitialization) {
        this.aesInitialization = aesInitialization;
    }


    private void stopThreadIfAlive(Thread thread) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(10);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void attachProxyToReading(EncryptionProxy proxy) {
        stopThreadIfAlive(receiveThread);
        receiveThread = new Thread(new encryptedDataReceiver(proxy), "udp-hole-" + proxy.getName() + "-" + thisName);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private final Runnable connectionEstablishProcess = () -> {
        try {
            connect();
            connectionEstablishThread = null;
        } catch (Exception e) {
            logger.error("ConnectionManager connect {}", e.getMessage());
            stateChangeListener.onConnectFailed();
        }

    };

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

        setEncryptionProxy(null);

        ClientPublisher cp = new ClientPublisher(thisName, factory.createSynchronizationTool(), factory.createRsaEncryptionTool());

        thisClient = cp.retrievePublicAddress();
        if (thisClient == null) {
            return false;
        }
        try {
            if (cp.sendInfo(thisClient)) {
                logger.debug("Sent info to sync server about {}", thisClient);
                long start = TimeUtils.nowMs();
                int oldness = OLDNESS_SECONDS - TIMEOUT_CONNECTION_SECONDS;
                //start from 10 seconds to 30 seconds
                while (true) {
                    try {
                        thatClient = cp.retrieveInfoAbout(thatName, (int) (oldness + (TimeUtils.nowMs() - start) / 1000));
                    } catch (Exception ignored) {
                        logger.error(String.format("Error during receiving info about ", thatName),
                                ignored.getMessage());
                        continue;
                    }
                    if (thatClient != null) {
                        logger.debug("retrieved info about {}\n", thatClient);
                        break;
                    }
                    Thread.sleep(500);
                    if (TimeUtils.elapsedSeconds(TIMEOUT_CONNECTION_SECONDS, start)) {
                        return false;
                    }
                }
                logger.info("starting connecting between \n{} \n{}", thisClient, thatClient);
                connection = new UDPHolePuncher(thisClient, thatClient, factory.createBase64Tool());
                try {
                    if (connection.connect(TIMEOUT_CONNECTION_SECONDS)) {
                        setSocket(connection.getSocket());
                        if (getSocket() == null) {
                            throw new RuntimeException("No socket");
                        }
                        successRemote = getBestConnection(connection.getRemoteEndpoints(), thisClient);
                        byte[] remoteFingerprint = connection.getRemoteFingerprint();
                        logger.info("received echo from  {}", successRemote);

                        AesInitialization aesInitialization = new AesInitialization(factory.createAesEncryptionTool(),
                                factory.getTimersManager(),
                                aesPair -> {
                                    //kill current receiving Thread and start another
                                    //to avoid synchronization in loop
                                    timersManager.addTimer(1, false, () -> {
                                        AesEncryption aesEncryption = new AesEncryption(clientDataConsumer, this::sendBytes, aesPair);
                                        setEncryptionProxy(aesEncryption);
                                        attachProxyToReading(aesEncryption);
                                        setAesInitialization(null);
                                        stateChangeListener.onConnected();
                                    });
                                },
                                ignored -> {
                                    timersManager.addTimer(1, false, () -> {
                                        reset();
                                        stateChangeListener.onConnectFailed();
                                    });
                                });
                        setAesInitialization(aesInitialization);

                        //we use RSA for AES keys exchange
                        RsaEncryptionTool encryptionTool = factory.createRsaEncryptionTool();
                        encryptionTool.applyKeys(thisClient.getKeyForEncrypt(), encryptionTool.getPublicKey(thatClient.getPublicKey()));
                        //set temporary encryption layer
                        RsaEncryption rsaEncryption = new RsaEncryption(aesInitialization, this::sendBytes, encryptionTool);
                        setEncryptionProxy(rsaEncryption);

                        aesInitialization.startAsyncInitialization(rsaEncryption);
                        attachProxyToReading(rsaEncryption);
                        return true;
                    } else {
                        logger.info("connection timeout");
                    }
                } catch (Exception exception) {
                    logger.error("Udp Connection {}", exception.getMessage(), exception);
                }
            }

        } catch (HttpException ex) {
            logger.error("during sync tool", ex);
        }
        return false;
    }

    private class encryptedDataReceiver implements Runnable {
        final EncryptionProxy proxy;
        final byte[] receiveBuf = new byte[maxPacketSize];

        private encryptedDataReceiver(EncryptionProxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public void run() {
            while (true) {
                DatagramSocket socket = getSocket();
                try {
                    DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);
                    socket.receive(packet);
                    int remotePort = packet.getPort();
                    if (remotePort != successRemote.getPort()) {
                        logger.warn("Success remote {}, but received packet from {}", successRemote, packet.getSocketAddress());
                    }
                    final int length = packet.getLength();
                    final byte[] receivedEncryptedData = ByteUtils.copyBytes(receiveBuf, 0, length);
                    proxy.putIncomingDataFromSocket(receivedEncryptedData, 0, receivedEncryptedData.length);
                } catch (IOException e) {
                    return;
                } catch (Exception e) {
                    logger.error("error", e);
                }
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
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

}
