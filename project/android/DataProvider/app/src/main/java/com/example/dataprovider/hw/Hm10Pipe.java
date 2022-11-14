package com.example.dataprovider.hw;

import android.content.Context;

import org.example.ByteUtils;
import org.example.communication.DataPipe;
import org.example.communication.DataPipeStates;
import org.example.communication.PipeDataConsumer;
import org.example.communication.logging.PostLogger;
import org.example.endpoint.IncomingPacketAcceptor;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.packets.AbstractPacket;
import org.example.packets.HWKeepAlive;
import org.example.packetsReceiver.CommunicationDriver;
import org.example.packetsReceiver.OnePacketConsumer;
import org.example.packetsReceiver.PacketsReceiverStreamCollector;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * converts AbstractPacket (from UDP)
 * type(1), length(2), body(length), crc(1)
 * to
 * //start(1), type(1), length(2), body(length), crc(1)
 * start = 0x69
 */
public class Hm10Pipe implements OutgoingPacketCarrier, DataPipe, AutoCloseable {

    private static final int MAXIMUM_PACKET_SIZE = 20;

    private Logger logger = LoggerFactory.getLogger(Hm10Pipe.class);
    private CommunicationManagerBLE communicationManagerBLE;
    private PacketsReceiverStreamCollector packetsReceiver;
    private UartWrappingProtocolHandler protocolHandler = new UartWrappingProtocolHandler();
    private IncomingPacketAcceptor packetConsumer;
    private TimersManager timersManager;
    private SoftTimer keepAliveSendTimer;

    public Hm10Pipe(Context context, IncomingPacketAcceptor packetConsumer, TimersManager timersManager) {
        this.packetConsumer = packetConsumer;
        packetsReceiver = new PacketsReceiverStreamCollector(timersManager, protocolHandler, communicationDriver,
                onePacketConsumer,
                MAXIMUM_PACKET_SIZE);
        communicationManagerBLE = new CommunicationManagerBLE(context, packetsReceiver::onNewDataReceived);
        communicationManagerBLE.connect();
        keepAliveSendTimer = timersManager.addTimer(2000, true, () -> {
            packetWasBorn(new HWKeepAlive(), null);
        });
    }


    private CommunicationDriver communicationDriver = () -> {
        return 11;//115200  - 11 byte per 1 ms
    };

    private OnePacketConsumer onePacketConsumer = (data, offset, size, logId) -> {
        //push to upper level packet without START
        packetConsumer.accept(new PacketReference(data, offset + UartWrappingProtocolHandler.START_TOKEN_SIZE,
                size - UartWrappingProtocolHandler.START_TOKEN_SIZE), logId);
    };

    @Override
    public void packetWasBorn(AbstractPacket abstractPacket, PostLogger postLogger) {
        byte[] packet = abstractPacket.toArray(false);
        sendData(packet, 0, packet.length, postLogger);
    }

    @Override
    public DataPipeStates getCurrentState() {
        return communicationManagerBLE.isReady() ? DataPipeStates.Alive : DataPipeStates.Idle;
    }

    @Override
    public synchronized void sendData(byte[] packet, int offset, int length, PostLogger postLogger) {
        if (communicationManagerBLE.isReady()) {
            try {
                byte[] outData = new byte[length + 1];
                if (outData.length > MAXIMUM_PACKET_SIZE) {
                    throw new RuntimeException("Too large packet. Split implementation required");
                }
                outData[0] = protocolHandler.getStartToken();
                ByteUtils.bufToBuf(packet, offset, length, outData, 1);
                //TODO: control amount of sending data (HM10 can accept on speed 115200 only)
                communicationManagerBLE.sendData(outData, 0, outData.length);
            } catch (Exception e) {
                logger.error("Bluetooth send packet", e);
            }
        } else {
            logger.warn("Hardware device is not readey yet");
        }
    }

    @Override
    public void setIncomingDataConsumer(PipeDataConsumer pipeDataConsumer) {
        throw new RuntimeException("Set up in constructor");
    }

    @Override
    public void startConnectAsync() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void stop() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void close() throws Exception {
        if (keepAliveSendTimer != null) {
            timersManager.removeTimer(keepAliveSendTimer);
            keepAliveSendTimer = null;
        }
        if (communicationManagerBLE!=null){
            communicationManagerBLE.closeSocket();
            communicationManagerBLE = null;
        }
    }
}
