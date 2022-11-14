package org.example;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class IpUtils {

    public static InetSocketAddress parseIpAndPort(String ipAndPort) throws UnknownHostException {
        String[] values = ipAndPort.split(":");
        return new InetSocketAddress(parseIp(values[0]), Integer.parseInt(values[1]));
    }

    public static InetAddress parseIp(String ip) throws UnknownHostException {
        String[] bytes = ip.split("[.]");
        return InetAddress.getByAddress(new byte[]{parseByte(bytes[0]), parseByte(bytes[1]), parseByte(bytes[2]), parseByte(bytes[3])});
    }


    private static byte parseByte(String unsignedByte) {
        int i = Integer.parseUnsignedInt(unsignedByte);
        return (byte) i;
    }
}
