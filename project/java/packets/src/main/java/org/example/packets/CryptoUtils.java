package org.example.packets;

import org.example.CommonConfig;

class CryptoUtils {

    public static int getPaddedSize(int size) {
        int remainder = size % CommonConfig.AES_SIZE;
        if (remainder != 0) {
            int padding = CommonConfig.AES_SIZE - remainder;
            return size + padding;
        }
        return size;
    }

}
