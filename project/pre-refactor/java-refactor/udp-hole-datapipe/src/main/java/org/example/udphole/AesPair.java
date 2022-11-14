package org.example.udphole;

public class AesPair {
    private final AesConfig forwardEncryption;
    private final AesConfig receiveDecryption;

    public AesPair(AesConfig forwardEncryption, AesConfig receiveDecryption) {
        this.forwardEncryption = forwardEncryption;
        this.receiveDecryption = receiveDecryption;
    }

    public AesConfig getForwardEncryption() {
        return forwardEncryption;
    }

    public AesConfig getReceiveDecryption() {
        return receiveDecryption;
    }

}
