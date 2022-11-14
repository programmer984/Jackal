package com.example.dataprovider.hw;

import com.example.dataprovider.service.DataProviderService;

import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.PacketTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Accept packets from local bluetooth connected device
 */
public class FromHwPacketsAcceptor implements ServicePacketAcceptor {
    private final Logger logger = LoggerFactory.getLogger(FromHwPacketsAcceptor.class);
    private static Set<PacketTypes> packetTypes=new HashSet<>();
    static {
        packetTypes.add(PacketTypes.HWKeepAlive);
    }

    @Override
    public Set<PacketTypes> getAcceptPacketTypes() {
        return packetTypes;
    }

    @Override
    public void accept(PacketReference packetReference, Integer integer) {
        logger.info("Keep alive from hardware received");
    }
}
