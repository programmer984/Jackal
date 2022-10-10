package com.example.dataprovider.hw;

import android.content.Context;

import org.example.serviceComponents.DataConsumer;
import org.example.serviceComponents.hw.HardwarePipe;
import org.example.serviceComponents.hw.UartWrappingProtocolHandler;
import org.example.serviceComponents.packetsReceiver.CommunicationDriver;
import org.example.serviceComponents.packetsReceiver.PacketsReceiver;
import org.example.serviceComponents.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * converts AbstractPacket (from UDP)
 * type(1), length(2), body(length), crc(1)
 * to
 * //start(1), type(1), length(2), body(length), crc(1)
 * start = 0x69
 */
public class Hm10Pipe implements HardwarePipe {

    private static final int MAXIMUM_PACKET_SIZE = 20;

    private Logger logger = LoggerFactory.getLogger(Hm10Pipe.class);
    private CommunicationManagerBLE communicationManagerBLE;
    private PacketsReceiver packetsReceiver;
    private UartWrappingProtocolHandler protocolHandler=new UartWrappingProtocolHandler();

    public Hm10Pipe(Context context, DataConsumer packetConsumer, TimersManager timersManager) {

        packetsReceiver = new PacketsReceiver(timersManager, protocolHandler, communicationDriver,
                //push to upper level packet without START
                (data, offset, size) -> packetConsumer.accept(data,
                        offset + UartWrappingProtocolHandler.START_TOKEN_SIZE,
                        size - UartWrappingProtocolHandler.START_TOKEN_SIZE),
                MAXIMUM_PACKET_SIZE);
        communicationManagerBLE = new CommunicationManagerBLE(context, packetsReceiver::onNewDataReceived);
        communicationManagerBLE.connect();
    }

    @Override
    public boolean isOpen() {
        return communicationManagerBLE.isReady();
    }

    @Override
    public void sendPacket(byte[] packet) {
        if (communicationManagerBLE.isReady()) {
            try {
                byte[] outData = new byte[packet.length + 1];
                outData[0] = protocolHandler.getStartToken();
                System.arraycopy(packet, 0, outData, 1, packet.length);
                communicationManagerBLE.sendData(outData, 0, outData.length);
            } catch (Exception e) {
                logger.error("Bluetooth send packet", e);
            }
        }else{
            logger.warn("Hardware device is not readey yet");
        }
    }


    private CommunicationDriver communicationDriver = () -> {
        return 11;//115200  - 11 byte per 1 ms
    };


}
