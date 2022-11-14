package org.example.endpoint;

import org.example.communication.DataPipe;
import org.example.communication.DataPipeStates;
import org.example.communication.PipeDataConsumer;
import org.example.communication.logging.PostLogger;
import org.example.packets.HWDoMove;
import org.example.packets.VideoFramePacket;
import org.junit.Assert;
import org.junit.Test;

public class OutgoingLogicTests {
    int bunchReceivedTimes;

    DataPipe dataPipe = new DataPipe() {
        @Override
        public void startConnectAsync() {

        }

        @Override
        public void stop() {

        }

        @Override
        public DataPipeStates getCurrentState() {
            return null;
        }

        @Override
        public void sendData(byte[] data, int offset, int length, PostLogger postLogger) {
            bunchReceivedTimes++;
            postLogger.logAttempHappen(bunchReceivedTimes);
        }

        @Override
        public void setIncomingDataConsumer(PipeDataConsumer incomingDataConsumer) {

        }
    };


    @Test
    public void OutgoingLogicTest() throws Exception {
        OutgoingLogic outgoingLogic = new OutgoingLogic(dataPipe, true);

        //int id, byte[] videFrame, int partIndex, int partsCount, int partOffset, int partSize, boolean isIFrame
        //assume videoframe size 1MByte
        VideoFramePacket videoFramePacket = new VideoFramePacket(0, new byte[1 * 1024 * 1024], 0, 200,
                0, VideoFramePacket.MTU, false);
        HWDoMove move = new HWDoMove(new HWDoMove.HorizontalCommand(HWDoMove.HorizontalDirection.Left, (byte) 30),
                new HWDoMove.VerticalCommand(HWDoMove.VerticalDirection.Idle, (byte) 0), (byte) 50, 0);


        outgoingLogic.packetWasBorn(videoFramePacket, logId -> System.out.println("Videoframe sent " + logId));
        outgoingLogic.packetWasBorn(move, logId -> System.out.println("move command sent " + logId));
        outgoingLogic.packetWasBorn(videoFramePacket, logId -> System.out.println("Videoframe sent second time " + logId));

        Thread.sleep(5000);
        Assert.assertTrue(bunchReceivedTimes >= 2);
        outgoingLogic.close();
    }

    @Test
    public void NotifyNotWaitingMonitorTest() {
        synchronized (dataPipe) {
            dataPipe.notify();
        }
    }
}
