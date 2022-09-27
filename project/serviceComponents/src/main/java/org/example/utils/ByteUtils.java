package org.example.utils;

public class ByteUtils {

    public static int searchSequense(byte[] src, int srcOffset, int srcSize, byte[] target, int targetOffset, int targetSize) {
        int found = -1;
        if (targetSize <= srcSize) {
            for (int i = 0; i < srcSize; i++) {
                if (src[i + srcOffset] == target[targetOffset] && i + targetSize <= srcSize) {
                    found = i;
                    for (int j = 1; j < targetSize; j++) {
                        if (src[i + srcOffset + j] != target[j + targetOffset]) {
                            found = -1;
                            break;
                        }
                    }
                    if (found >= 0) {
                        break;
                    }
                }
            }
        }
        return found;
    }
}
