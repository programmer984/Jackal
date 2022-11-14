package org.example.udphole;

import org.example.ByteUtils;
import org.example.CommonConfig;
import org.example.encryption.AesConfig;
import org.example.tools.AesEncryptionTool;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class AesConfigurationPacket {
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
        ByteUtils.u16ToBuf(passwordCrc, buf, 3);
        ByteUtils.bufToBuf(forwardEncryption.getKey().getEncoded(), buf, theirKeyOffset);
        ByteUtils.bufToBuf(forwardEncryption.getIv().getIV(), buf,  theirIvOffset);
        buf[packetSize - 1] = ByteUtils.calculateCrc(buf, 0, packetSize - 1);
    }

    public static boolean is(byte[] buf, int offset) {
        return buf[offset] == startByte;
    }

    public byte[] createPacket() {
        byte[] buf = new byte[packetSize];
        buf[0] = startByte;
        ByteUtils.u16ToBuf(packetSize, buf, 1);
        ByteUtils.bufToBuf(secretKeySpec, buf, myKeyOffset);
        ByteUtils.bufToBuf(ivParameterSpec, buf,  myIvOffset);
        buf[packetSize - 1] = ByteUtils.calculateCrc(buf, 0, packetSize - 1);
        return buf;
    }


    public static boolean containsKey(byte[] buf, byte[] key) {
        return ByteUtils.bufsEquals(buf, 5, key);
    }

    public static boolean isEcho(byte[] buf) {
        int crc = ByteUtils.bufToU16(buf, 3);
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
        byte crc = ByteUtils.calculateCrc(buf, 0, packetSize - 1);
        return crc == buf[packetSize - 1];
    }

    public static AesConfig createAesConfig(AesEncryptionTool encryptionTool, byte[] buf, int keyOffset) {
        SecretKeySpec fromBytesKey = new SecretKeySpec(ByteUtils.copyBytes(buf, keyOffset, CommonConfig.AES_SIZE), CommonConfig.AES);
        IvParameterSpec ivfromBytes = new IvParameterSpec(ByteUtils.copyBytes(buf, keyOffset + CommonConfig.AES_SIZE, CommonConfig.AES_SIZE));
        AesConfig config = new AesConfig(encryptionTool, fromBytesKey, ivfromBytes);
        if (config.getCipher() != null) {
            return config;
        }
        return null;
    }


}
