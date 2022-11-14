package org.example.packetsReceiver;

import org.example.ByteUtils;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimerCallback;
import org.example.softTimer.TimersManager;

/**
 * Non-Threadsafe
 * It collects small pieces of data to internal buffer
 */
public class PacketsReceiverStreamCollector extends AbstractPacketsReceiver {

    private TimersManager timerManager;
    private boolean timerUsing;
    private boolean timerIgnoring;
    private SoftTimer receivingTimeoutTimer;
    private final Object timerLock = new Object();

    protected CommunicationDriver communicationDriver;
    private int rxIndex;
    private final int rxBufSize;
    private final int minimumAwaitMs;
    private byte[] commonRxBuf;


    public PacketsReceiverStreamCollector(TimersManager timerManager, ProtocolHandler protocolHandler, CommunicationDriver communicationDriver, OnePacketConsumer packetConsumer, int rxBufSize){
        this(timerManager, protocolHandler, communicationDriver, packetConsumer, rxBufSize, 2);
    }

    public PacketsReceiverStreamCollector(TimersManager timerManager, ProtocolHandler protocolHandler, CommunicationDriver communicationDriver, OnePacketConsumer packetConsumer, int rxBufSize, int minimumAwaitMs) {
        this.timerManager = timerManager;
        this.minimumAwaitMs = minimumAwaitMs;
        this.protocolHandler = protocolHandler;
        this.communicationDriver = communicationDriver;
        this.packetConsumer = packetConsumer;
        this.rxBufSize = rxBufSize;
        this.commonRxBuf = new byte[rxBufSize];
    }


    public void onNewDataReceived(final byte[] data, int offsetFinal, int sizeFinal, Integer logId) {

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

            //accepting ready packet by packetConsumer could take a time
            //so set flag to ignore timeout during packet accepting
            ignoreTimer();
            PacketsPushingResult result = searchPacketsAndPush(commonRxBuf, 0, rxIndex, logId);
            if (result.packetsPushed > 0) {
                cancelTimer();
            }else{
                reactOnTimer();
            }
            if (result.pushingResult == PacketsPushingResultStates.EVERYTHING_SENT ||
                    result.pushingResult == PacketsPushingResultStates.NOT_FOUND) {
                reset();
                //last loop
                if (size == 0) {
                    break;
                }
            } else if (result.pushingResult == PacketsPushingResultStates.PACKET_INCOMPLETE
                    || result.pushingResult == PacketsPushingResultStates.TAIL_PRESENT) {
                int tail = rxIndex - result.offset;
                //shift to start
                copyToRxBuf(commonRxBuf, result.offset, 0, tail);
                rxIndex = tail;

                //last loop
                if (size == 0) {
                    if (result.pushingResult == PacketsPushingResultStates.PACKET_INCOMPLETE) {
                        createAndStartTimer();
                    }
                    break;
                }
            }
        }
    }


    private final TimerCallback timerCallback = () -> {
        synchronized (timerLock){
            if (timerUsing) {
                timerUsing = false;
                int usedPerios = receivingTimeoutTimer.getPeriod();
                receivingTimeoutTimer = null;
                if (!timerIgnoring) {
                    logger.warn("Timer invoked rxIndex {} period {} ms data {}", rxIndex, usedPerios, ByteUtils.toHexString(commonRxBuf, Math.min(rxIndex, 20)));
                    rxIndex = 0;
                    protocolHandler.resetReceivingState();
                }
            }
        }
    };

    private void createAndStartTimer() {
        synchronized (timerLock) {
            if (!timerUsing) {
                int approxSize = protocolHandler.getApproximatePacketSize(commonRxBuf, 0, rxIndex);
                int currentSpeed = communicationDriver.getCurrentSpeed();
                int timeoutMs = approxSize / currentSpeed;
                timeoutMs += timeoutMs / 10; //add 10% to await time
                timeoutMs = Math.max(timeoutMs, minimumAwaitMs);

                logger.debug("Timer started with timeout {}", timeoutMs);
                receivingTimeoutTimer = timerManager.addTimer(timeoutMs, false, timerCallback);
                timerUsing = true;
                timerIgnoring = false;
            }
        }
    }

    private void ignoreTimer() {
        synchronized (timerLock) {
            timerIgnoring = true;
        }
    }
    private void reactOnTimer() {
        synchronized (timerLock) {
            timerIgnoring = false;
        }
    }

    private void cancelTimer() {
        synchronized (timerLock) {
            if (timerUsing) {
                logger.debug("Timer cancelled");
                timerManager.removeTimer(receivingTimeoutTimer);
                timerUsing = false;
                timerIgnoring = false;
            }
        }
    }


    private void copyToRxBuf(byte[] src, int srcOffset, int dstOffset, int length) {
        System.arraycopy(src, srcOffset, commonRxBuf, dstOffset, length);
    }

    public void reset() {
        cancelTimer();
        rxIndex = 0;
    }

}
