package org.example.udphole.sync;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.tools.RsaEncryptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ClientInfo {

    private static final Logger logger
            = LoggerFactory.getLogger(ClientInfo.class);

    private String clientName;
    private String publicIP;
    //like 172.27.0.1,192.168.12.3
    private String localIP;
    private Integer publicPort;
    private Integer localPort;
    private String publicKey;
    private DatagramSocket socket;
    private KeyPair keyPair;


    public ClientInfo() {
    }

    public ClientInfo(String clientName, Iterable<InetAddress> localAddresses, int localPort,
                      InetSocketAddress internetAddress, DatagramSocket socket, RsaEncryptionTool asymmetric) {
        if (clientName == null || localAddresses == null || internetAddress == null
                || localPort <= 0) {
            throw new IllegalArgumentException();
        }

        this.clientName = clientName;
        this.publicIP = internetAddress.getAddress().toString().replace("/", "");
        this.publicPort = internetAddress.getPort();
        this.localPort = localPort;
        this.localIP = StreamSupport.stream(localAddresses.spliterator(), false)
                .filter(ip -> !ip.isLoopbackAddress())
                .map(address -> address.toString().replace("/", ""))
                .collect(Collectors.joining(","));
        this.socket = socket;
        try {
            keyPair = asymmetric.generateRSAKkeyPair();
            publicKey = asymmetric.keyToString(keyPair.getPublic());
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("constructor {} localPort:{} inetPort:{}", clientName, localPort, publicPort);
    }

    @JsonProperty("Name")
    public String getClientName() {
        return clientName;
    }

    @JsonProperty("Name")
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    @JsonProperty("PublicKey")
    public String getPublicKey() {
        return publicKey;
    }

    @JsonProperty("PublicKey")
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }


    @JsonProperty("PublicIP")
    public String getPublicIP() {
        return publicIP;
    }

    @JsonProperty("PublicIP")
    public void setPublicIP(String publicIP) {
        this.publicIP = publicIP;
    }

    @JsonProperty("PublicPort")
    public Integer getPublicPort() {
        return publicPort;
    }

    @JsonProperty("PublicPort")
    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }

    @JsonProperty("LocalIP")
    public String getLocalIP() {
        return localIP;
    }


    @JsonProperty("LocalIP")
    public void setLocalIP(String localIP) {
        this.localIP = localIP;
    }

    @JsonProperty("LocalPort")
    public Integer getLocalPort() {
        return localPort;
    }

    @JsonProperty("LocalPort")
    public void setLocalPort(Integer localPort) {
        this.localPort = localPort;
    }

    @JsonIgnore
    public DatagramSocket getSocket() {
        return socket;
    }

    @JsonIgnore
    public void setSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    @JsonIgnore
    public PrivateKey getKeyForEncrypt() {
        return keyPair.getPrivate();
    }

    @JsonIgnore
    @Override
    public String toString() {
        return "ClientInfo{" +
                "clientName='" + clientName + '\'' +
                ", publicIP='" + publicIP + '\'' +
                ", localIP='" + localIP + '\'' +
                ", publicPort=" + publicPort +
                ", localPort=" + localPort +
                '}';
    }

    public static List<InetAddress> getIntersectedLocalIp(String remoteAddresses, String localAddresses) {
        List<InetAddress> remotes = stringToAddresses(remoteAddresses);
        List<byte[]> locals = stringToAddresses(localAddresses).stream().map(InetAddress::getAddress).collect(Collectors.toList());
        return remotes.stream()
                .filter(r -> {
                    byte[] remote = r.getAddress();
                    for (byte[] local : locals) {
                        if (remote[0] == local[0] && remote[1] == local[1] && remote[2] == local[2]) {
                            return true;
                        }
                    }
                    return false;
                }).collect(Collectors.toList());
    }


    public static List<InetAddress> stringToAddresses(String ip) {
        List<InetAddress> result = Arrays.stream(ip.split(","))
                .map(ipString -> {
                    String[] bytes = ipString.split("[.]");
                    return new byte[]{(byte) Integer.parseInt(bytes[0]), (byte) Integer.parseInt(bytes[1]),
                            (byte) Integer.parseInt(bytes[2]), (byte) Integer.parseInt(bytes[3])};
                })
                .map(ipByteArray ->
                        {
                            try {
                                return InetAddress.getByAddress(ipByteArray);
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                ).filter(Objects::nonNull)
                .collect(Collectors.toList());
        return result;
    }


}
