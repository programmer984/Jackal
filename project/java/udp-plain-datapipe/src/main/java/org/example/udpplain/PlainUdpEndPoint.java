package org.example.udpplain;

import org.example.ByteUtils;
import org.example.communication.DataPipe;
import org.example.communication.DataPipeStates;
import org.example.communication.PipeDataConsumer;
import org.example.communication.logging.PostLogger;
import org.example.endpoint.IncomingPacketAcceptor;
import org.example.endpoint.OutgoingLogic;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.packets.AbstractPacket;
import org.example.packetsReceiver.OnePacketConsumer;
import org.example.packetsReceiver.PacketReceiverOneShot;
import org.example.protocol.InternalProtocolHandler;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PlainUdpEndPoint implements OutgoingPacketCarrier, PipeDataConsumer {
    private static final Logger logger
            = LoggerFactory.getLogger(PlainUdpEndPoint.class);

    private IncomingPacketAcceptor consumer;
    private DataPipe dataPipe;
    private TimersManager timersManager;
    private final InternalProtocolHandler internalProtocolHandler = new InternalProtocolHandler();
    private final PacketReceiverOneShot packetsReceiver;
    private OutgoingLogic outgoingLogic;

    public PlainUdpEndPoint(DataPipe dataPipe, IncomingPacketAcceptor consumer, TimersManager timersManager) {
        this.dataPipe = dataPipe;
        this.consumer = consumer;
        this.timersManager = timersManager;
        dataPipe.setIncomingDataConsumer(this);
        outgoingLogic = new OutgoingLogic(dataPipe, false);
        packetsReceiver = new PacketReceiverOneShot(internalProtocolHandler, onePacketConsumer);
    }


    //receive here checked packet
    private final OnePacketConsumer onePacketConsumer = (data, offset, size, logId) -> {
        try {
            consumer.accept(new PacketReference(data, offset, size), logId);
        } catch (Exception e) {
            logger.error("during incoming packet creation {} LogId {}", ByteUtils.toHexString(data, offset, 10), logId, e);
        }
    };

    /**
     * receive here raw byte stream from data pipe
     * this method could be called from several threads
     */
    @Override
    public void onDataReceived(byte[] data, int offset, int size, Integer logId) {
        packetsReceiver.onNewDataReceived(data, offset, size, logId);
    }

    ;

    @Override
    public void packetWasBorn(AbstractPacket brandNew, PostLogger postLogger) {
        if (dataPipe.getCurrentState() == DataPipeStates.Alive) {
            try {
                outgoingLogic.packetWasBorn(brandNew, postLogger);
            } catch (Exception ex) {
                logger.error("send problem", ex);
            }
        }
    }


}
