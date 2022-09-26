package org.example.udphole.stun;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

public class EndPoint {
    List<InetAddress> localAddress;
    int localPort;
    InetSocketAddress publicAddress;
    DatagramSocket openedSocket;

    public EndPoint(List<InetAddress> localAddresses, int localPort, InetSocketAddress publicAddress, DatagramSocket openedSocket) {
        this.localAddress = localAddresses;
        this.localPort = localPort;
        this.publicAddress = publicAddress;
        this.openedSocket = openedSocket;
    }

    @Override
    public String toString() {
        return "EndPoint{" +
                "localAddress=" + localAddress +
                ", localPort=" + localPort +
                ", publicAddress=" + publicAddress +
                ", openedSocket=" + openedSocket +
                '}';
    }

    public List<InetAddress> getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(List<InetAddress> localAddress) {
        this.localAddress = localAddress;
    }


    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public InetSocketAddress getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(InetSocketAddress publicAddress) {
        this.publicAddress = publicAddress;
    }

    public DatagramSocket getOpenedSocket() {
        return openedSocket;
    }

    public void setOpenedSocket(DatagramSocket openedSocket) {
        this.openedSocket = openedSocket;
    }


}
