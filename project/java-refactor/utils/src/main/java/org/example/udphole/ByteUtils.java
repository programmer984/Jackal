package org.example.udphole;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ByteUtils {

    public static boolean bufsEquals(byte[] big, int bigOffset, byte[] small) {
        if (big == null || small == null) {
            return false;
        }
        if (small.length > big.length) {
            return false;
        }
        for (int i = 0; i < small.length; i++) {
            if (big[bigOffset + i] != small[i]) {
                return false;
            }
        }
        return true;
    }

    public static String toHexString(final byte[] buf, int length) {
        return IntStream.range(0, length).mapToObj(i -> String.format("%02x", buf[i])).collect(Collectors.joining(","));
    }

    public static String toHexString(final byte[] buf, int offset, int length) {
        return IntStream.range(0, length).mapToObj(i -> String.format("%02x", buf[offset + i])).collect(Collectors.joining(","));
    }

    public static void u8ToBuf(int value, final byte[] buf, int offset) {
        buf[offset] = (byte) (value & 0xFF);
    }

    public static void u16ToBuf(final int value, final byte[] buf, final int offset) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public static void i32ToBuf(int value, final byte[] buf, int offset) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }


    public static void u64ToBuf(long value, final byte[] buf, int offset) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
        buf[offset + 4] = (byte) ((value >> 32) & 0xFF);
        buf[offset + 5] = (byte) ((value >> 40) & 0xFF);
        buf[offset + 6] = (byte) ((value >> 48) & 0xFF);
        buf[offset + 7] = (byte) ((value >> 56) & 0xFF);
    }

    public static int bufToU8(final byte[] buf, int offset) {
        int value = buf[offset] & 0xFF;
        return value;
    }

    public static int bufToU16(final byte[] buf, int offset) {
        int value = buf[offset] & 0xFF;
        value |= (buf[offset + 1] & 0xFF) << 8;
        return value;
    }

    public static int bufToI32(final byte[] buf, int offset) {
        int value = buf[offset] & 0xFF;
        value |= (buf[offset + 1] & 0xFF) << 8;
        value |= (buf[offset + 2] & 0xFF) << 16;
        value |= (buf[offset + 3] & 0xFF) << 24;
        return value;
    }

    public static void bufToBuf(byte[] src, byte[] dst, int dstOffset) {
        for (int i = 0; i < src.length; i++) {
            dst[dstOffset + i] = src[i];
        }
    }

    public static void bufToBuf(byte[] src, int srcOffset, int count, byte[] dst, int dstOffset) {
        for (int i = 0; i < count; i++) {
            dst[dstOffset + i] = src[i + srcOffset];
        }
    }



    // calculates sum except tail byte
    public static byte calculateCrc(final byte[] buf, int packetSize) {
        return (byte) IntStream.range(0, packetSize - 1).map(i -> buf[i])
                .reduce(0, Integer::sum);
    }

    public static byte calculateCrc(final byte[] buf, int offset, int packetSize) {
        return (byte) IntStream.range(offset, packetSize - 1).map(i -> buf[i])
                .reduce(0, Integer::sum);
    }



    public static int random(int from, int to) {
        return (int) (from + (Math.random() * (to - from)));
    }

    public static byte[] copyBytes(byte[] buf, int offset, int length) {
        return Arrays.copyOfRange(buf, offset, offset + length);
    }

    public static int searchSequense(byte[] src, int srcOffset, byte[] target) {
        int srcSize=src.length-srcOffset;
        return searchSequense(src, srcOffset, srcSize, target, 0, target.length);
    }

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
