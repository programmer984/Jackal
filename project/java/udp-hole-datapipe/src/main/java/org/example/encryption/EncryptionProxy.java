package org.example.encryption;

import org.example.communication.PipeDataConsumer;

import java.io.IOException;
import java.security.GeneralSecurityException;

public abstract class EncryptionProxy {
    protected final PipeDataConsumer incomingDataConsumer;
    protected final OutgoingDataAcceptor outgoingDataAcceptor;

    public EncryptionProxy(PipeDataConsumer incomingDataConsumer, OutgoingDataAcceptor outgoingDataAcceptor) {
        this.incomingDataConsumer = incomingDataConsumer;
        this.outgoingDataAcceptor = outgoingDataAcceptor;
    }

    /**
     * non thread-safe
     * @param data
     * @param offset
     * @param length
     * @throws IOException
     */
    public abstract void sendEncryptedData(byte[] data, int offset, int length) throws IOException;

    /**
     * put incoming data from udp packet here
     * @param data
     * @param offset
     * @param length
     */
    public abstract void putIncomingDataFromSocket(byte[] data, int offset, int length) throws GeneralSecurityException;

    /**
     * thread-safe encrypt function
     * @param semicolonedData
     * @param offset
     * @param length
     * @return
     * @throws GeneralSecurityException
     */
    public abstract byte[] encrypt(byte[] semicolonedData, int offset, int length) throws GeneralSecurityException;

    public abstract String getName();
}
