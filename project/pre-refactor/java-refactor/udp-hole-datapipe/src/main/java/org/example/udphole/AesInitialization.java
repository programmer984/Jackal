package org.example.udphole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * send our sync key
 * receive their sync key, set echo marker, send back with out sync key
 * send our sync key until receive echo
 */
public class AesInitialization { //using RSA
    //we are sure that client received our key
    private volatile boolean forwardEchoReceived = false;
    private volatile long lastSentTime = Utils.nowMs();
    private static final int SEND_INTERVAL = 200;
    private final AesConfig forwardEncryption;
    private volatile AesConfig receiveDecryption;
    private EncryptionTool encryptionTool;
    private byte[] forwardPacketBuf;
    private static final int ENOUGH_SEND_TIMES = 4;
    private volatile int sentTimes = 0;
    private ISocket socket;
    private static final Logger logger
            = LoggerFactory.getLogger("ConnectionLib/AesInitialization");

    AesInitialization(EncryptionTool encryptionTool, ISocket socket) {
        this.encryptionTool = encryptionTool;
        this.socket = socket;
        forwardEncryption = new AesConfig(encryptionTool);
    }

    public boolean isInited() {
        return receiveDecryption != null && (forwardEchoReceived || sentTimes >= ENOUGH_SEND_TIMES);
    }

    public void sendInvoke() {
        if (Utils.elapsed(SEND_INTERVAL, lastSentTime)) {
            try {
                if (forwardPacketBuf == null) {
                    try {
                        AesConfigurationPacket forwardPacket = new AesConfigurationPacket(forwardEncryption.getKey(), forwardEncryption.getIv());
                        byte[] bytes = forwardPacket.createPacket();
                        forwardPacketBuf = encryptionTool.do_RSAEncryption(bytes, 0, bytes.length);
                    } catch (Exception e) {
                        logger.error("AesInitialization constructor", e);
                    }
                }
                socket.send(forwardPacketBuf, 0, forwardPacketBuf.length);
                logger.debug("sent info about our symmetric key {}", ++sentTimes);
                lastSentTime = Utils.nowMs();
            } catch (IOException e) {
                logger.error("send data", e);
            }
        } else {
            sleep(10);
        }
    }

    public void onReceive(byte[] buf, int offset, int length, Consumer<Boolean> initedConsumer) {
        try {
            byte[] bytes = encryptionTool.do_RSADecryption(buf, 0, length);
            logger.debug("received asymmetrically encrypted packet ");
            boolean isConfigPacket = AesConfigurationPacket.is(bytes, offset);
            boolean isEcho = AesConfigurationPacket.isEcho(bytes);

            if (isConfigPacket && !isEcho) {
                logger.info("received info about theirs symmetric key");
                if (receiveDecryption == null) {
                    receiveDecryption = AesConfigurationPacket.createAesConfig(encryptionTool,
                            bytes, AesConfigurationPacket.myKeyOffset);
                    logger.info("decryption key initialized ");
                    initedConsumer.accept(true);
                }
                //send echo
                AesConfigurationPacket.setEchoReply(bytes, forwardEncryption);
                bytes = encryptionTool.do_RSAEncryption(bytes, 0, bytes.length);
                socket.send(bytes, 0, bytes.length);
            } else if (isEcho) {
                logger.info("received echo symmetric key packet");
                forwardEchoReceived = true;
                if (receiveDecryption == null) {
                    receiveDecryption = AesConfigurationPacket.createAesConfig(encryptionTool,
                            bytes, AesConfigurationPacket.theirKeyOffset);
                    logger.info("decryption key initialized ");
                    initedConsumer.accept(true);
                }
            } else {
                logger.warn("strange content isConfig {}, isEcho {}, original {}, decrypted {}", isConfigPacket, isEcho,
                        Utils.toHexString(buf, length), Utils.toHexString(bytes, bytes.length));
            }
        } catch (BadPaddingException | IllegalBlockSizeException | IOException e) {
            //ignore
            logger.error("onReceive", e);
        }
    }

    private void sleep(int amount) {
        try {
            Thread.sleep(amount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public AesPair createPair() {
        return new AesPair(forwardEncryption, receiveDecryption);
    }
}
