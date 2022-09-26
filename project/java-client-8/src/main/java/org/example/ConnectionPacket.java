package org.example;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.stream.IntStream;

public final class ConnectionPacket {

    public final static byte startByte = 0x69;
    //marker indicates that packet is sent through localAddress
    public final static byte localChannel = 0x70;
    public final static byte internetChannel = 0x71;
    //start byte, size (low), size (high), channelType, echo, IP, port, remote public key fingerprint
    private final static int mainPartLength = 11;//without fingerprint
    public final static int maximumSize = 100;
    public final static int echoFlag = 1;
    private final boolean local;
    private final byte[] remoteAddress;
    private final int remotePort;
    byte[] remoteFingerprint;

    public ConnectionPacket(boolean local, InetAddress remoteAddress, int remotePort, byte[] remoteFingerprint) {
        this.local = local;
        this.remoteAddress = remoteAddress.getAddress();
        this.remotePort = remotePort;
        this.remoteFingerprint = remoteFingerprint;
    }


    public static boolean fingerPrintEquals(byte[] connectionBuf, byte[] remoteFingerprint) {
        int packetSize = getPacketSize(connectionBuf);
        if (remoteFingerprint != null && remoteFingerprint.length > 0 &&
                remoteFingerprint.length + mainPartLength + 1 == packetSize) {
            return Utils.bufsEquals(connectionBuf, mainPartLength, remoteFingerprint);
        }
        return false;
    }

    public byte[] createPacket() {
        int packetSize = mainPartLength + remoteFingerprint.length + 1;
        byte[] buf = new byte[packetSize];
        buf[0] = startByte;
        Utils.u16ToBuf(packetSize, buf, 1);
        // 0
        buf[3] = local ? localChannel : internetChannel;
        //echo - false
        //The address which we send packet
        buf[5] = remoteAddress[0];
        buf[6] = remoteAddress[2];
        Utils.u16ToBuf(remotePort, buf, 7);
        buf[9] = remoteAddress[3];
        buf[10] = remoteAddress[1];
        for (int i = 0; i < remoteFingerprint.length; i++) {
            buf[mainPartLength + i] = remoteFingerprint[i];
        }
        //last byte - crc
        buf[packetSize - 1] = Utils.calculateCrc(buf, packetSize);
        return buf;
    }


    public static byte calculateAndApplyCrc(final byte[] buf, int packetSize) {
        return buf[packetSize - 1] = Utils.calculateCrc(buf, packetSize);
    }

    public static boolean isEcho(final byte[] buf) {
        return buf[4] == ConnectionPacket.echoFlag;
    }

    public static boolean isLocal(final byte[] buf) {
        return buf[3] == localChannel;
    }

    public static boolean isValidCrc(final byte[] buf) {
        if (buf == null || buf.length < 4)
            return false;
        int packetSize = Utils.bufToU16(buf, 1);
        if (buf.length < packetSize) {
            return false;
        }
        byte crc = Utils.calculateCrc(buf, packetSize);
        return crc == buf[packetSize - 1];
    }


    public static InetSocketAddress getFromPacketBody(final byte[] buf) throws UnknownHostException {
        int port = getPort(buf);
        InetAddress address = InetAddress.getByAddress(new byte[]{buf[5], buf[10], buf[6], buf[9]});
        return new InetSocketAddress(address, port);
    }

    public static Integer getPacketSize(final byte[] buf) {
        int result = Utils.bufToU16(buf, 1);
        if (result > maximumSize) {
            return 0;
        }
        return result;
    }

    public static Integer getPort(final byte[] buf) {
        return Utils.bufToU16(buf, 7);
    }


}