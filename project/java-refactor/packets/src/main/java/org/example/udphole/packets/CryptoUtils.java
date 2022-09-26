package org.example.udphole.packets;

class CryptoUtils {
    private static final int AES_SIZE = 16;
    public static int getPaddedSize(int size) {
        int remainder = size % AES_SIZE;
        if (remainder != 0) {
            int padding = AES_SIZE - remainder;
            return size + padding;
        }
        return size;
    }

    public static int getAesSize(){
        return AES_SIZE;
    }
}
