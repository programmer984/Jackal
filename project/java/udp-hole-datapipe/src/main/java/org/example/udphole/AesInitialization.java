package org.example.udphole;

import org.example.ByteUtils;
import org.example.CommonConfig;
import org.example.communication.PipeDataConsumer;
import org.example.encryption.AesConfig;
import org.example.encryption.AesPair;
import org.example.encryption.RsaEncryption;
import org.example.softTimer.TimersManager;
import org.example.sync.BasePacket;
import org.example.sync.ResultAcceptor;
import org.example.sync.Synchronizer;
import org.example.tools.AesEncryptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;

/**
 * send our sync key
 * receive their sync key, set echo marker, send back with out sync key
 * send our sync key until receive echo
 */
class AesInitialization extends Synchronizer implements PipeDataConsumer { //using RSA
    private static final Logger logger
            = LoggerFactory.getLogger(AesInitialization.class);

    private final AesConfig forwardEncryption;

    private static final int keySize = CommonConfig.AES_SIZE;
    private static final int IV_OFFSET = keySize;
    private static final int BLOCK_SIZE = keySize*2;

    private static final int SEND_INTERVAL = 200;
    private static final int TIMES_TO_SEND = 10;

    private RsaEncryption socket;
    private Consumer<AesPair> initCompleteConsumer;
    private Consumer<Object> initializationFailed;
    private AesEncryptionTool encryptionTool;

    AesInitialization(AesEncryptionTool encryptionTool, TimersManager timersManager,
                      Consumer<AesPair> initCompleteConsumer,
                      Consumer<Object> initializationFailed) {
        super(timersManager, "AesInitialization");
        forwardEncryption = new AesConfig(encryptionTool);
        this.initCompleteConsumer = initCompleteConsumer;
        this.initializationFailed = initializationFailed;
        this.encryptionTool = encryptionTool;
    }

    private ResultAcceptor resultAcceptor = new ResultAcceptor() {
        @Override
        public void synchronised(byte[] thisBlock, byte[] thatBlock) {
            AesConfig thatConfig = blockToAesConfig(encryptionTool, thatBlock);
            initCompleteConsumer.accept(new AesPair(forwardEncryption, thatConfig));
        }

        @Override
        public void notSynchronized() {
            initializationFailed.accept(false);
        }
    };

    synchronized RsaEncryption getSocket() {
        return socket;
    }

    synchronized void setSocket(RsaEncryption socket) {
        this.socket = socket;
    }

    public void startAsyncInitialization(RsaEncryption socket) {
        setSocket(socket);
        setResultAcceptor(resultAcceptor);
        startSynchronization(aesConfigToBlock(forwardEncryption), SEND_INTERVAL, TIMES_TO_SEND);
    }

    @Override
    public void onDataReceived(byte[] buf, int offset, int length, Integer logIdNull) {
        onPacketReceived(buf, offset, length);
    }

    @Override
    protected void sendPacket(BasePacket packet) {
        byte[] rsaBuf=new byte[packet.calculateSize()];
        packet.toArray(rsaBuf, 0);

        RsaEncryption socket = getSocket();
        try {
            rsaBuf = socket.encrypt(rsaBuf, 0, rsaBuf.length);
            socket.sendEncryptedData(rsaBuf, 0, rsaBuf.length);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("During outgoing packet encryption ", e);
        }
    }

    private static byte[] aesConfigToBlock(AesConfig aesConfig){
        byte[] block=new byte[BLOCK_SIZE];
        ByteUtils.bufToBuf(aesConfig.getKey().getEncoded(),0, keySize, block, 0);
        ByteUtils.bufToBuf(aesConfig.getIv().getIV(),0, keySize, block,  keySize);
        return block;
    }

    private static AesConfig blockToAesConfig(AesEncryptionTool encryptionTool, byte[] block){
        SecretKeySpec fromBytesKey = new SecretKeySpec(ByteUtils.copyBytes(block, 0, keySize), CommonConfig.AES);
        IvParameterSpec ivfromBytes = new IvParameterSpec(ByteUtils.copyBytes(block, IV_OFFSET, keySize));
        return new AesConfig(encryptionTool, fromBytesKey, ivfromBytes);
    }


}
