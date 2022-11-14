package org.example.udphole;

import org.example.ByteUtils;
import org.example.communication.PipeDataConsumer;
import org.example.encryption.AesConfig;
import org.example.encryption.AesPair;
import org.example.encryption.RsaEncryption;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.example.tools.AesEncryptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;

/**
 * send our sync key
 * receive their sync key, set echo marker, send back with out sync key
 * send our sync key until receive echo
 */
class AesInitialization implements PipeDataConsumer { //using RSA
    private static final Logger logger
            = LoggerFactory.getLogger(AesInitialization.class);

    private final TimersManager timersManager;
    private final AesConfig forwardEncryption;
    private AesConfig synchronizedReceiveDecryption;
    private final AesEncryptionTool encryptionTool;
    private final Consumer<AesPair> initCompleteConsumer;

    private static final int SEND_INTERVAL = 200;
    private static final int TIMES_TO_SEND = 10;
    private SoftTimer sendTimer;
    private int sentTimes;
    private RsaEncryption socket;
    private boolean initComplete = false;


    AesInitialization(AesEncryptionTool encryptionTool, TimersManager timersManager, Consumer<AesPair> initCompleteConsumer,
                      Consumer<Object> initializationFailed) {
        this.encryptionTool = encryptionTool;
        this.timersManager = timersManager;
        this.initCompleteConsumer = initCompleteConsumer;
        forwardEncryption = new AesConfig(encryptionTool);
    }

    synchronized RsaEncryption getSocket() {
        return socket;
    }

    synchronized void setSocket(RsaEncryption socket) {
        this.socket = socket;
    }

    public void startAsyncInitialization(RsaEncryption socket) {
        setSocket(socket);
        setInitComplete(false);
        sendTimer = timersManager.addTimer(SEND_INTERVAL, true, this::sendInvoke);
    }

    public void resetTimer() {
        if (sendTimer != null) {
            timersManager.removeTimer(sendTimer);
            sendTimer = null;
        }
        sentTimes = 0;
    }

    private void sendInvoke() {
        try {
            RsaEncryption socket = getSocket();
            AesConfigurationPacket forwardPacket = new AesConfigurationPacket(forwardEncryption.getKey(), forwardEncryption.getIv());
            byte[] bytes = forwardPacket.createPacket();
            byte[] forwardPacketBuf = socket.encrypt(bytes, 0, bytes.length);
            socket.sendEncryptedData(forwardPacketBuf, 0, forwardPacketBuf.length);
            sentTimes++;
            logger.debug("sent info about our symmetric key {}", sentTimes);

            if (sentTimes > TIMES_TO_SEND || isInitComplete()) {
                resetTimer();
            }
        } catch (IOException | GeneralSecurityException e) {
            logger.error("send data", e);
        }
    }

    @Override
    public void onDataReceived(byte[] bytes, int offset, int length, Integer logIdNull) {
        if (!isInitComplete()) {
            RsaEncryption socket = getSocket();
            try {
                logger.debug("received asymmetrically encrypted packet ");
                boolean isConfigPacket = AesConfigurationPacket.is(bytes, offset);
                boolean isEcho = AesConfigurationPacket.isEcho(bytes);

                if (isConfigPacket && !isEcho) {
                    logger.info("received info about theirs symmetric key");
                    if (getReceiveDecryption() == null) {
                        setReceiveDecryption(AesConfigurationPacket.createAesConfig(encryptionTool,
                                bytes, AesConfigurationPacket.myKeyOffset));
                        initComplete();
                    }
                    //send echo
                    AesConfigurationPacket.setEchoReply(bytes, forwardEncryption);
                    bytes = socket.encrypt(bytes, 0, bytes.length);
                    socket.sendEncryptedData(bytes, 0, bytes.length);
                } else if (isEcho) {
                    logger.info("received echo symmetric key packet");
                    //we are sure that client received our key
                    boolean forwardEchoReceived = true;
                    if (getReceiveDecryption() == null) {
                        setReceiveDecryption(AesConfigurationPacket.createAesConfig(encryptionTool,
                                bytes, AesConfigurationPacket.theirKeyOffset));
                        initComplete();
                    }
                } else {
                    logger.warn("strange content isConfig {}, isEcho {}, original {}, decrypted {}", isConfigPacket, isEcho,
                            ByteUtils.toHexString(bytes, length), ByteUtils.toHexString(bytes, bytes.length));
                }
            } catch (GeneralSecurityException | IOException e) {
                //ignore
                logger.error("onReceive", e);
            }
        }
    }

    private void initComplete() {
        setInitComplete(true);
        logger.info("decryption key initialized ");
        initCompleteConsumer.accept(createPair());
    }

    private synchronized boolean isInitComplete() {
        return initComplete;
    }

    private synchronized void setInitComplete(boolean initComplete) {
        this.initComplete = initComplete;
    }

    private synchronized AesConfig getReceiveDecryption() {
        return synchronizedReceiveDecryption;
    }

    private synchronized void setReceiveDecryption(AesConfig receiveDecryption) {
        this.synchronizedReceiveDecryption = receiveDecryption;
    }

    private AesPair createPair() {
        return new AesPair(forwardEncryption, getReceiveDecryption());
    }


}
