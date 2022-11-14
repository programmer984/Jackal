package org.example.encryption;

import org.example.CommonConfig;
import org.example.communication.PipeDataConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class AesEncryption extends EncryptionProxy {
    private static final Logger logger
            = LoggerFactory.getLogger(AesEncryption.class);
    private AesPair aesPair;
    private ThreadLocal<AesConfig> encrypt = new ThreadLocal<>();
    private ThreadLocal<AesConfig> decrypt = new ThreadLocal<>();
    private final ExecutorService executorService = java.util.concurrent.Executors.newWorkStealingPool();

    private final Supplier<AesConfig> encSupplier = () -> {
        AesConfig enc = aesPair.getForwardEncryption();
        if (encrypt.get() == null) {
            encrypt.set(enc.clone());
        }
        return encrypt.get();
    };

    private final Supplier<AesConfig> decSupplier = () -> {
        AesConfig dec = aesPair.getReceiveDecryption();
        if (decrypt.get() == null) {
            decrypt.set(dec.clone());
        }
        return decrypt.get();
    };

    public AesEncryption(PipeDataConsumer incomingDataConsumer, OutgoingDataAcceptor outgoingDataAcceptor, AesPair pair) {
        super(incomingDataConsumer, outgoingDataAcceptor);
        this.aesPair = pair;
    }

    @Override
    public void sendEncryptedData(byte[] data, int offset, int length) throws IOException {
        outgoingDataAcceptor.sendEncryptedData(data, offset, length);
    }

    @Override
    public void putIncomingDataFromSocket(byte[] data, int offset, int length) throws GeneralSecurityException {
        if (length % CommonConfig.AES_SIZE == 0) {
            executorService.submit(() -> {
                try {
                    byte[] decrypted = decSupplier.get().getCipher().doFinal(data, offset, length);
                    incomingDataConsumer.onDataReceived(decrypted, 0, decrypted.length);
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            logger.error("Wrong size trying to decrypt {}", length);
        }
    }

    @Override
    public byte[] encrypt(byte[] semicolonedData, int offset, int length) throws GeneralSecurityException {
        return encSupplier.get().getCipher().doFinal(semicolonedData, offset, length);
    }

    @Override
    public String getName() {
        return "aes";
    }


}
