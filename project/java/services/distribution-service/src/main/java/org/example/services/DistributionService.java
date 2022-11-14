package org.example.services;

import org.example.endpoint.IncomingPacketAcceptor;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.PacketTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class DistributionService implements IncomingPacketAcceptor {
    private static final Logger logger = LoggerFactory.getLogger(DistributionService.class);

    private Map<PacketTypes, ServicePacketAcceptor> acceptors = new HashMap<>();

    public void registerService(ServicePacketAcceptor service) {
        service.getAcceptPacketTypes().forEach(type -> {
            acceptors.put(type, service);
        });
    }

    public void removeRegistration(ServicePacketAcceptor service) {
        service.getAcceptPacketTypes().forEach(type -> {
            acceptors.remove(type);
        });
    }

    @Override
    public void accept(PacketReference packet, Integer logId) {
        try {
            ServicePacketAcceptor service = acceptors.get(packet.getPacketType());
            if (service != null) {
                service.accept(packet, logId);
            } else if (packet.getPacketType() == PacketTypes.KeepAlive) {
                logger.debug("Keep alive in logId {}", logId);
            } else {
                logger.warn("There is no acceptor for {}", packet.getPacketType());
            }
        } catch (Exception ex) {
            logger.error("During packet accept {} {}", packet.getPacketType(), logId, ex);
        }
    }
}
