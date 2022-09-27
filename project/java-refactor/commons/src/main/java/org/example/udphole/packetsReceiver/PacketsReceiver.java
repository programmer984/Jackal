package org.example.udphole.packetsReceiver;

import org.example.udphole.softTimer.TimerCallback;
import org.example.udphole.softTimer.TimersManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PacketsReceiver {

    private final TimersManager timerManager;
    private final ExecutorService dispatcher = Executors.newFixedThreadPool(1);
    private ProtocolHandler protocolHandler;
    private final CommunicationDriver communicationDriver;
    private final DataConsumer packetConsumer;

    private boolean startStored;
    private boolean timerUsing;
    private Object receivingTimeoutTimer;
    private int rxIndex;
    private final int rxBufSize;
    private byte[] rxBuf;


    public PacketsReceiver(TimersManager timerManager, ProtocolHandler protocolHandler, CommunicationDriver communicationDriver, DataConsumer packetConsumer, int rxBufSize) {
        this.timerManager = timerManager;
        this.protocolHandler = protocolHandler;
        this.communicationDriver = communicationDriver;
        this.packetConsumer = packetConsumer;
        this.rxBufSize = rxBufSize;
        this.rxBuf = new byte[rxBufSize];
    }


    public Future<?> onNewDataReceived(final byte[] data, int offsetFinal, int sizeFinal) {
        return dispatcher.submit(() -> {
            boolean skipFirstSearch = startStored;
            int size = sizeFinal;
            int offset = offsetFinal;
            while (true) {
                int leftSize = rxBufSize - rxIndex;
                int deltaSize = (size <= leftSize) ? size : leftSize;

                //add to dmaRxBuf incoming bytes
                copyToRxBuf(data, offset, rxIndex, deltaSize);
                rxIndex += deltaSize;
                offset += deltaSize;
                size -= deltaSize;

                PacketsPushingResult result = searchPacketsAndPush(rxIndex, skipFirstSearch);
                if (result.packetsPushed > 0 && timerUsing) {
                    cancelTimer();
                }
                if (result.pushingResult == PacketsPushingResultStates.EVERYTHING_SENT) {
                    rxIndex = 0;
                    startStored = false;
                    //last loop
                    if (size == 0) {
                        break;
                    }
                } else if (result.pushingResult == PacketsPushingResultStates.PACKET_INCOMPLETE
                        || result.pushingResult == PacketsPushingResultStates.TAIL_PRESENT) {
                    int tail = rxIndex - result.offset;
                    //shift to start
                    copyToRxBuf(rxBuf, result.offset, 0, tail);
                    rxIndex = tail;
                    skipFirstSearch = result.pushingResult == PacketsPushingResultStates.PACKET_INCOMPLETE;
                    //last loop
                    if (size == 0) {
                        if (result.pushingResult == PacketsPushingResultStates.PACKET_INCOMPLETE) {
                            createAndStartTimer();
                            startStored = true;
                        }
                        break;
                    }
                }
            }
        });
    }

    private PacketsPushingResult searchPacketsAndPush(int size, boolean skipFirstSearch) {
        byte[] data = rxBuf;
        int offset = 0;
        int startTokenSize = protocolHandler.getBytesCountForRequiredForStartSearch();
        PacketsPushingResult result = new PacketsPushingResult();
        if (size < startTokenSize) {
            result.pushingResult = PacketsPushingResultStates.TAIL_PRESENT;
        } else {
            while (offset < size) {
                int tailSize = size - offset;
                if (tailSize >= startTokenSize) {
                    int foundStartOffset = (skipFirstSearch && offset == 0) ? 0 :
                            protocolHandler.findStartPosition(data, offset, tailSize);
                    if (foundStartOffset >= 0) {
                        offset += foundStartOffset; //points to new found packet
                        tailSize -= foundStartOffset;
                        PacketRecevingResult recevingResult =
                                protocolHandler.checkPacketIsComplete(data, offset, tailSize);
                        //if internal structure of packet is wrong
                        if (recevingResult.resultState == PacketRecevingResultStates.TRASH) {
                            offset = offset + foundStartOffset + 1;
                        } else if (recevingResult.resultState == PacketRecevingResultStates.COMPLETE) {
                            packetConsumer.accept(data, offset, recevingResult.size);
                            offset += recevingResult.size;
                            result.packetsPushed++;
                        } else if (recevingResult.resultState == PacketRecevingResultStates.INCOMPLETE) {
                            result.pushingResult = PacketsPushingResultStates.PACKET_INCOMPLETE;
                            result.offset = offset;
                            break;
                        }
                    } else { // start not found
                        //we leave startTokenSize - 1
                        if (startTokenSize > 1) {
                            result.pushingResult = PacketsPushingResultStates.TAIL_PRESENT;
                            result.offset = offset + tailSize - startTokenSize + 1;
                        } else {
                            //there are no unknown tail and start next packet
                            result.pushingResult = PacketsPushingResultStates.EVERYTHING_SENT;
                        }
                        break;
                    }
                } else { // tailSize<startTokenSize
                    result.pushingResult = PacketsPushingResultStates.TAIL_PRESENT;
                    result.offset = offset;
                    break;
                }
            }
            if (offset == size) {
                result.pushingResult = PacketsPushingResultStates.EVERYTHING_SENT;
            }
        }
        return result;
    }

    private final TimerCallback timerCallback = () -> {
        dispatcher.submit(() -> {
            if (timerUsing) {
                rxIndex = 0;
                timerUsing = false;
                receivingTimeoutTimer = null;
                startStored = false;
                protocolHandler.resetReceivingState();
            }
        });
    };

    private void createAndStartTimer() {
        if (!timerUsing) {
            int approxSize = protocolHandler.getApproximatePacketSize(rxBuf, 0, rxIndex);
            int currentSpeed = communicationDriver.getCurrentSpeed();
            int timeoutMs = approxSize / currentSpeed;
            timeoutMs += timeoutMs / 10; //add 10% to await time
            timeoutMs += 2;   //extra milliseconds
            receivingTimeoutTimer = timerManager.addTimer(timeoutMs, false, timerCallback);
            timerUsing = true;
        }
    }

    private void cancelTimer() {
        if (timerUsing) {
            timerManager.removeTimer(receivingTimeoutTimer);
            timerUsing = false;
        }
    }


    private void copyToRxBuf(byte[] src, int srcOffset, int dstOffset, int length) {
        System.arraycopy(src, srcOffset, rxBuf, dstOffset, length);
    }

}
