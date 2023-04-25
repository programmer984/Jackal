package org.example.sync;

import org.example.ByteUtils;
import org.example.DataReference;
import org.example.Dispatcher;
import org.example.TimeUtils;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimerCallback;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * periodically send forward SB (syncro block)
 * receive reply with incoming SB   or   receiving incoming SB directly and periodically send reply with out Block
 * if we have reply, start count down
 */
public abstract class Synchronizer implements AutoCloseable {

    private static final Logger logger
            = LoggerFactory.getLogger(Synchronizer.class);

    //if 10ms or left to finalize, finalize
    public static final int LEFT_TIME_TO_FINALIZE = 10;
    private SyncBlock thisBlock;
    private SyncBlock theirBlock;
    private DirectPacket thisDirectPacket;
    private ResultAcceptor resultAcceptor;
    private Dispatcher dispatcher;
    private final Object dispatcherLock = new Object();
    private String threadName;
    private TimersManager timersManager;
    private SoftTimer softTimer;
    private int invokesCountLeft;
    private int sendingPeriodMs;
    private boolean finishMethodCalled;
    private Long countDownTimestampTarget;
    private Integer therLeftMs;
    private SyncStates syncState = SyncStates.SENDING_FORWARD_BLOCK;

    public Synchronizer(TimersManager timersManager, String threadName) {
        this(null, timersManager, threadName);
    }
    public Synchronizer(ResultAcceptor resultAcceptor, TimersManager timersManager, String threadName) {
        this.resultAcceptor = resultAcceptor;
        this.timersManager = timersManager;
        this.threadName = threadName;
    }

    public void setResultAcceptor(ResultAcceptor resultAcceptor) {
        this.resultAcceptor = resultAcceptor;
    }

    protected void startSynchronization(byte[] thisBlock, int sendingPeriod, int invokesCount) {
        if (this.thisBlock != null) {
            throw new RuntimeException("One time using support only");
        }
        this.thisBlock = new SyncBlock(generateRandom(), thisBlock);
        this.thisDirectPacket = new DirectPacket(this.thisBlock.getId(), new DataReference(thisBlock));
        this.invokesCountLeft = invokesCount;
        this.sendingPeriodMs = sendingPeriod;
        countDownTimestampTarget = TimeUtils.nowMs() + ((long) sendingPeriodMs * invokesCountLeft);

        synchronized (dispatcherLock) {
            this.dispatcher = new Dispatcher(1, threadName);
        }
        dispatcher.submitBlocking(() -> {
            this.softTimer = timersManager.addTimer(sendingPeriod, true, timerMethod, threadName);
        });
    }

    private static int generateRandom() {
        return ThreadLocalRandom.current().nextInt(1, Short.MAX_VALUE);
    }

    private TimerCallback timerMethod = () -> {
        if (dispatcher.isEmpty()) {
            dispatcher.submitBlocking(() -> {
                if (invokesCountLeft-- <= 0) {
                    finish();
                    return;
                }
                if (syncState == SyncStates.SENDING_FORWARD_BLOCK) {
                    sendForward();
                } else if (syncState == SyncStates.SENDING_REPLY_BLOCK) {
                    sendReply();
                } else {
                    analyzeAndSendCountDown();
                }
            });
        } else {
            logger.warn("{} dispatcher is full!", threadName);
        }
    };

    protected abstract void sendPacket(BasePacket packet);

    protected void onPacketReceived(byte[] buf, int offset, int length) {
        DataReference dataReference = new DataReference(buf, offset, length);
        PacketTypes packetType = BasePacket.checkAndGetPacketType(dataReference);
        logger.debug("Incoming packet {}", packetType);
        if (dispatcher != null && !dispatcher.isFull()) {
            dispatcher.submitBlocking(() -> {
                if (!finishMethodCalled) {
                    //their Direct packet
                    if (packetType == PacketTypes.DIRECT) {
                        if (theirBlock == null) {
                            //setup andwitch state to replying
                            DirectPacket theirDirect = DirectPacket.fromIncomingData(buf, offset);
                            theirBlock = new SyncBlock(theirDirect.getId(), theirDirect.getBlock().extractBuf());
                            syncState = SyncStates.SENDING_REPLY_BLOCK;
                        } else {
                            logger.debug("ignoring direct incoming packet, it already set");
                        }
                    } else if (packetType == PacketTypes.REPLY) {
                        ReplyPacket replyPacket = ReplyPacket.fromIncomingData(buf, offset);
                        if (replyPacket.getTheirId() == thisBlock.getId()) {
                            if (theirBlock == null) {
                                theirBlock = new SyncBlock(replyPacket.getId(), replyPacket.getBlock().extractBuf());
                            }
                            syncState = SyncStates.COUNT_DOWN;
                        } else {
                            logger.error("Returned not our Id!");
                        }
                    } else if (packetType == PacketTypes.COUNTDOWN) {
                        FinalizationPacket finalizationPacket = FinalizationPacket.fromIncomingData(buf, offset);
                        therLeftMs = finalizationPacket.getMsLeft();
                        syncState = SyncStates.COUNT_DOWN;
                        synchronizeTimers();
                    } else {
                        logger.error("Unknown packet {}", ByteUtils.toHexString(buf, offset, length));
                    }
                }
            });
        } else {
            logger.warn("dispatcher full!");
        }
    }

    private void sendForward() {
        sendPacket(thisDirectPacket);
        logger.debug("Sent forward packet");
    }

    private void sendReply() {
        sendPacket(new ReplyPacket(theirBlock.getId(), thisBlock.getId(), new DataReference(thisBlock.getData())));
        logger.debug("Sent reply packet");
    }

    private void analyzeAndSendCountDown() throws Exception {
        long ourLeftMs = getOurLeftMs();
        if (ourLeftMs > LEFT_TIME_TO_FINALIZE) {
            sendPacket(new FinalizationPacket((int) ourLeftMs));
            logger.debug("Sent countDown {} ms packet", ourLeftMs);
        } else if (ourLeftMs <= 0) {
            finish();
        } else {
            logger.warn("wrong state");
        }
    }

    private long getOurLeftMs() {
        return countDownTimestampTarget - TimeUtils.nowMs();
    }

    private void synchronizeTimers() {
        long ourLeftMs = getOurLeftMs();
        //reduce if we can
        if (ourLeftMs != therLeftMs) {
            long leftMs = Math.min(ourLeftMs, therLeftMs) - 2; // -2 - time required to send and parse packet
            countDownTimestampTarget = TimeUtils.nowMs() + leftMs;
            softTimer.setNextInvoke(countDownTimestampTarget);
            logger.debug("Left ms was reduced from {} to {}", Math.max(ourLeftMs, therLeftMs), leftMs);
        }
    }

    private void finish() throws Exception {
        if (!finishMethodCalled) {
            finishMethodCalled = true;
            try {
                if (theirBlock != null) {
                    resultAcceptor.synchronised(thisBlock.getData(), theirBlock.getData());
                } else {
                    resultAcceptor.notSynchronized();
                }
            } finally {
                close();
            }
        }
    }

    void join() throws InterruptedException {
        synchronized (dispatcherLock) {
            dispatcher.join();
        }
    }

    @Override
    public void close() throws Exception {
        if (softTimer != null) {
            timersManager.removeTimer(softTimer);
            softTimer = null;
        }
        dispatcher.close();
    }
}
