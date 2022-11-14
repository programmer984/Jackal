package org.example;

import java.net.InetSocketAddress;
import java.util.Objects;

public class UDPEndPoint {
    private final AddressTypes addressType;
    private final InetSocketAddress socketAddress;

    public UDPEndPoint(AddressTypes addressType, InetSocketAddress socketAddress) {
        this.addressType = addressType;
        this.socketAddress = socketAddress;
    }


    public AddressTypes getAddressType() {
        return addressType;
    }

    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }



    @Override
    public String toString() {
        return "UDPEndPoint{" +
                "local=" + addressType +
                ", socketAddress=" + socketAddress +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return this.hashCode()==o.hashCode();
    }

    @Override
    public int hashCode() {
        byte[] address = socketAddress.getAddress().getAddress();
        return Objects.hash(addressType, address[0], address[1], address[2], address[3] );
    }

    public enum AddressTypes {
        LOCAL,
        INET
    }
}
