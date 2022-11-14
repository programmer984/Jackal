package org.example.encryption;

import org.example.CommonConfig;
import org.example.communication.PipeDataConsumer;
import org.example.tools.RsaEncryptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;


public class RsaEncryption extends EncryptionProxy {
    private static final Logger logger
            = LoggerFactory.getLogger(RsaEncryption.class);
    private final RsaEncryptionTool encryptionTool;


    public RsaEncryption(PipeDataConsumer incomingDataConsumer, OutgoingDataAcceptor outgoingDataAcceptor,
                         RsaEncryptionTool encryptionTool) {
        super(incomingDataConsumer, outgoingDataAcceptor);
        this.encryptionTool = encryptionTool;
    }


    @Override
    public void sendEncryptedData(byte[] data, int offset, int length) throws IOException {
        outgoingDataAcceptor.sendEncryptedData(data, offset, length);
    }

    @Override
    public void putIncomingDataFromSocket(byte[] data, int offset, int length) throws GeneralSecurityException {
        if (length % CommonConfig.RSA_MinimumSize == 0){
            byte[] decrypted = encryptionTool.do_RSADecryption(data, offset, length);
            incomingDataConsumer.onDataReceived(decrypted, 0, decrypted.length);
        }else{
            logger.error("Wrong size trying to decrypt {}", length);
        }
    }

    @Override
    public byte[] encrypt(byte[] semicolonedData, int offset, int length) throws GeneralSecurityException {
        return encryptionTool.do_RSAEncryption(semicolonedData, offset, length);
    }

    @Override
    public String getName() {
        return "rsa";
    }


}
