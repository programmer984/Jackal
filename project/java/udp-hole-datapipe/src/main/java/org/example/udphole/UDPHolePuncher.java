package org.example.udphole;

import org.example.ByteUtils;
import org.example.TimeUtils;
import org.example.tools.Base64Tool;
import org.example.udphole.sync.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

class UDPHolePuncher {

    private String thisName;
    private String thatName;

    private static final Logger logger
            = LoggerFactory.getLogger(UDPHolePuncher.class);
    private DatagramSocket inSocket = null;
    private final Object sendLock = new Object();
    private static final int public_port_min = 49152;
    private static final int public_port_max = 65530;

    private boolean shouldWork = true;
    private boolean connectionPacketReceived;
    private long connectionPacketReceivedTimestamp = 0;
    private static final int AWAIT_ECHO_MS = 500;
    private boolean echoReceived = false;
    //received packets from this endpoint
    private final Set<UDPEndPoint> remoteEndpoints = Collections.synchronizedSet(new HashSet<>());


    //remote addresses
    private final List<InetAddress> remoteLocalAddresses;
    private final int remoteLocalPort;
    private final InetAddress remotePublicAddress;
    private final int remotePublicPort;
    private Thread receiveAwaitThread;
    private byte[] remoteFingerprint;
    private byte[] thisFingerprint;

    public UDPHolePuncher(ClientInfo thisClient, ClientInfo thatClient, Base64Tool base64Tool) throws SocketException {
        this.thisName = thisClient.getClientName();
        this.thatName = thatClient.getClientName();

        if (thisClient.getSocket() != null && !thisClient.getSocket().isClosed()) {
            inSocket = thisClient.getSocket();
        } else {
            inSocket = new DatagramSocket(thisClient.getLocalPort());
            logger.debug("Socket recreation");
        }
        inSocket.setReuseAddress(true);
        //remote addresses
        remoteLocalAddresses = ClientInfo.getIntersectedLocalIp(thatClient.getLocalIP(), thisClient.getLocalIP());
        remoteLocalPort = thatClient.getLocalPort();
        remotePublicAddress = ClientInfo.stringToAddresses(thatClient.getPublicIP()).get(0);
        remotePublicPort = thatClient.getPublicPort();
        byte[] thatKey = base64Tool.decode(thatClient.getPublicKey());
        byte[] thisKey = base64Tool.decode(thisClient.getPublicKey());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            remoteFingerprint = digest.digest(thatKey);
            thisFingerprint = digest.digest(thisKey);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Sha256 problem", e);
            remoteFingerprint = new byte[0];
            thisFingerprint = new byte[0];
        }
    }

    synchronized boolean isEchoReceived() {
        return echoReceived;
    }

    synchronized void setEchoReceived(boolean echoReceived) {
        this.echoReceived = echoReceived;
    }

    synchronized boolean isShouldWork() {
        return shouldWork;
    }

    synchronized void setShouldWork(boolean shouldWork) {
        this.shouldWork = shouldWork;
    }

    synchronized boolean isConnectionPacketReceived() {
        return connectionPacketReceived;
    }

    synchronized void setConnectionPacketReceived(boolean connectionPacketReceived) {
        this.connectionPacketReceived = connectionPacketReceived;
    }

    synchronized long getConnectionPacketReceivedTimestamp() {
        return connectionPacketReceivedTimestamp;
    }

    synchronized void setConnectionPacketReceivedTimestamp(long connectionPacketReceivedTimestamp) {
        this.connectionPacketReceivedTimestamp = connectionPacketReceivedTimestamp;
    }

    public boolean connect(int timeoutSeconds) {
        long startConnectionMs = TimeUtils.nowMs();

        setShouldWork(true);
        remoteEndpoints.clear();

        receiveAwaitThread = new Thread(receiveAwaitProcess, "UdpConnection " + thisName);
        receiveAwaitThread.setDaemon(true);
        receiveAwaitThread.start();

        try {
            //Long someConnectedTimestamp = null;
            logger.debug("{} -> {} remote targets [{}, {}]", thisName, thatName, remoteLocalPort, remotePublicPort);
            for (InetAddress localIp : remoteLocalAddresses) {
                logger.debug("      local {} {}", localIp, remoteLocalPort);
            }
            logger.debug("      public {} {}", remotePublicAddress, remotePublicPort);

            while (isShouldWork()) {
                //send local packets, then 20000 public packets (for example from 1.1.1.1:5005 to 1.1.1.1:25020)
                if (!isConnectionPacketReceived()) {
                    sendForwardLocals();
                    Thread.sleep(50);
                    sendForwardInet();
                }else {
                    if (!isEchoReceived()) {
                        UDPEndPoint endPoint = remoteEndpoints.iterator().next();
                        sendForward(endPoint);
                    } else if (isEchoReceived() ||
                            TimeUtils.elapsed(AWAIT_ECHO_MS, getConnectionPacketReceivedTimestamp())) {
                        setShouldWork(false);
                        break;
                    }
                }

                Thread.sleep(10);

                //main timeout
                if (TimeUtils.elapsedSeconds(timeoutSeconds, startConnectionMs)) {
                    setShouldWork(false);
                    break;
                }

            }
        } catch (IOException e) {
            logger.error("during hole punching ", e);
        } catch (InterruptedException ignored) {
        } finally {
            setShouldWork(false);
            receiveAwaitThread.interrupt();
        }

        return isEchoReceived() || isConnectionPacketReceived();
    }

    private void sendForward(UDPEndPoint endPoint) throws IOException {
        byte[] packet = new ConnectionPacket(endPoint.getAddressType() == UDPEndPoint.AddressTypes.LOCAL,
                endPoint.getSocketAddress().getAddress(), endPoint.getSocketAddress().getPort(), remoteFingerprint).createPacket();
        DatagramPacket p = new DatagramPacket(packet, packet.length, endPoint.getSocketAddress());
        synchronized (sendLock) {
            inSocket.send(p);
        }
    }

    private void sendForwardLocals() throws IOException {
        for (InetAddress localIp : remoteLocalAddresses) {
            byte[] packet = new ConnectionPacket(true, localIp, remoteLocalPort, remoteFingerprint).createPacket();
            DatagramPacket p = new DatagramPacket(packet, packet.length, localIp, remoteLocalPort);
            synchronized (sendLock) {
                inSocket.send(p);
            }
        }
    }

    private void sendForwardInet() throws IOException {
        byte[] publicPacket = new ConnectionPacket(false, remotePublicAddress, remotePublicPort, remoteFingerprint)
                .createPacket();
        synchronized (sendLock) {
            inSocket.send(new DatagramPacket(publicPacket, publicPacket.length, remotePublicAddress, remotePublicPort));
        }

        long startSending = TimeUtils.nowMs();
        int minPort = remotePublicPort < public_port_min ? remotePublicPort - 1000 : public_port_min;
        for (int port = minPort; port < public_port_max; port++) {
            if (isConnectionPacketReceived()) {
                break;
            }
            try {
                byte[] p = new ConnectionPacket(false, remotePublicAddress, port, remoteFingerprint)
                        .createPacket();
                synchronized (sendLock) {
                    inSocket.send(new DatagramPacket(p, p.length, remotePublicAddress, port));
                }
            } catch (Exception e) {
                logger.error("During sending to public port {}", port, e);
            }
        }
        logger.debug("->->->->->->->->-> Parallel sending took {} ms", TimeUtils.nowMs() - startSending);
    }


    private final Runnable receiveAwaitProcess = () -> {
        while (isShouldWork() && !Thread.currentThread().isInterrupted()) {
            try {
                final byte[] connectionBuf = new byte[ConnectionPacket.maximumSize];
                DatagramPacket packet = new DatagramPacket(connectionBuf, ConnectionPacket.maximumSize);
                inSocket.receive(packet);
                if (!isShouldWork()) {
                    return;
                }
                //our connection packets
                int packetSize = ConnectionPacket.getPacketSize(connectionBuf);

                if (connectionBuf[0] == ConnectionPacket.startByte &&
                        ConnectionPacket.isValidCrc(connectionBuf)) {

                    UDPEndPoint.AddressTypes addressType = ConnectionPacket.isLocal(connectionBuf) ?
                            UDPEndPoint.AddressTypes.LOCAL : UDPEndPoint.AddressTypes.INET;

                    if (!ConnectionPacket.isEcho(connectionBuf) &&
                            ConnectionPacket.fingerPrintEquals(connectionBuf, thisFingerprint)) {
                        //send echo
                        byte[] cloneReply = Arrays.copyOf(connectionBuf, packetSize);
                        cloneReply[4] = ConnectionPacket.echoFlag;
                        ConnectionPacket.calculateAndApplyCrc(cloneReply, packetSize);
                        SocketAddress replyAddress = packet.getSocketAddress();
                        DatagramPacket echo = new DatagramPacket(cloneReply, packetSize, replyAddress);
                        remoteEndpoints.add(new UDPEndPoint(addressType, new InetSocketAddress(packet.getAddress(), packet.getPort())));
                        if (!isConnectionPacketReceived()) {
                            setConnectionPacketReceived(true);
                            setConnectionPacketReceivedTimestamp(TimeUtils.nowMs());
                        }
                        synchronized (sendLock) {
                            inSocket.send(echo);
                        }
                        logger.info("{} We are being pinged by our {} address", thisName,
                                ConnectionPacket.getFromPacketBody(cloneReply));
                    } else { // echo packet
                        // -we received packet which we sent in connect method
                        if (ConnectionPacket.fingerPrintEquals(connectionBuf, remoteFingerprint)) {
                            InetSocketAddress successRemote = ConnectionPacket.getFromPacketBody(connectionBuf);
                            setEchoReceived(true);
                            logger.info("{} Success remote found {} ", thisName, successRemote);
                            remoteEndpoints.add(new UDPEndPoint(addressType, successRemote));
                            if (!isConnectionPacketReceived()) {
                                setConnectionPacketReceived(true);
                                setConnectionPacketReceivedTimestamp(TimeUtils.nowMs());
                            }
                        }
                    }
                } else {
                    logger.error("Invalid connection packet {} ", ByteUtils.toHexString(connectionBuf, 20));
                }

            } catch (IOException e) {
                logger.error("during UDPConnection awaiting incoming packet", e);
            }
        }
        logger.debug("UDPConnection stopped receiving the packet");
    };


    public List<UDPEndPoint> getRemoteEndpoints() {
        return new ArrayList<>(remoteEndpoints);
    }


    public DatagramSocket getSocket() {
        return inSocket;
    }


    public byte[] getRemoteFingerprint() {
        return remoteFingerprint;
    }

}
