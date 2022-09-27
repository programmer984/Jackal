package org.example;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesConfigurationPacket {
    public final static byte startByte = 0x55;
    //start byte, size (low), size (high), echo src (2 bytes), myIv, myKey, theirIv, theirKey, crc
    private final static int packetSize = 5 + 32 + 32 + 1;
    public final static int keySize = 16;

    public static final int myKeyOffset = 5;
    private static final int myIvOffset = myKeyOffset + keySize;
    public static final int theirKeyOffset = myIvOffset + keySize;
    private static final int theirIvOffset = theirKeyOffset + keySize;

    final byte[] secretKeySpec;
    final byte[] ivParameterSpec;

    public AesConfigurationPacket(SecretKeySpec secretKeySpec, IvParameterSpec ivParameterSpec) {
        this.secretKeySpec = secretKeySpec.getEncoded();
        this.ivParameterSpec = ivParameterSpec.getIV();
    }

    //create echo reply from original packet
    public static void setEchoReply(byte[] buf, AesConfig forwardEncryption) {
        int passwordCrc = calculatePasswordCrc(buf);
        Utils.u16ToBuf(passwordCrc, buf, 3);
        Utils.bufToBuf(forwardEncryption.getKey().getEncoded(), buf, theirKeyOffset);
        Utils.bufToBuf(forwardEncryption.getIv().getIV(), buf,  theirIvOffset);
        buf[packetSize - 1] = Utils.calculateCrc(buf, packetSize);
    }

    public static boolean is(byte[] buf, int offset) {
        return buf[offset] == startByte;
    }

    public byte[] createPacket() {
        byte[] buf = new byte[packetSize];
        buf[0] = startByte;
        Utils.u16ToBuf(packetSize, buf, 1);
        Utils.bufToBuf(secretKeySpec, buf, myKeyOffset);
        Utils.bufToBuf(ivParameterSpec, buf,  myIvOffset);
        buf[packetSize - 1] = Utils.calculateCrc(buf, packetSize);
        return buf;
    }


    public static boolean containsKey(byte[] buf, byte[] key) {
        return Utils.bufsEquals(buf, 5, key);
    }

    public static boolean isEcho(byte[] buf) {
        int crc = Utils.bufToU16(buf, 3);
        int calculatedCrc = calculatePasswordCrc(buf);
        return crc == calculatedCrc;
    }

    public static int calculatePasswordCrc(byte[] buf) {
        int crcCalculated = 0;
        for (int i = myKeyOffset; i < theirKeyOffset; i++) {
            crcCalculated += buf[i] & 0xFF;
        }
        return crcCalculated;
    }


    public static boolean crcCorrect(byte[] buf) {
        if (buf.length < packetSize) {
            return false;
        }
        byte crc = Utils.calculateCrc(buf, packetSize);
        return crc == buf[packetSize - 1];
    }

    public static AesConfig createAesConfig(EncryptionTool encryptionTool, byte[] buf, int keyOffset) {
        SecretKeySpec fromBytesKey = new SecretKeySpec(Utils.copyBytes(buf, keyOffset, Configuration.AES_SIZE), Configuration.AES);
        IvParameterSpec ivfromBytes = new IvParameterSpec(Utils.copyBytes(buf, keyOffset + Configuration.AES_SIZE, Configuration.AES_SIZE));
        AesConfig config = new AesConfig(encryptionTool, fromBytesKey, ivfromBytes);
        if (config.getCipher() != null) {
            return config;
        }
        return null;
    }


}
