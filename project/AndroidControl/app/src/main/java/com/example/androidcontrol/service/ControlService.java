package com.example.androidcontrol.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


import com.example.androidcontrol.PacketAcceptor;
import com.example.androidcontrol.VideoDirector;
import com.example.androidcontrol.factory.AndroidFactory;

import org.example.ConnectionManager;
import org.example.ConnectionStateListener;
import org.example.FactoryHolder;
import org.example.PacketOut;
import org.example.PacketsBunchIn;
import org.example.PacketsProviderAndAcceptor;
import org.example.Utils;
import org.example.serviceComponents.OutgoingSender;
import org.example.serviceComponents.VideoFramesReader;
import org.example.serviceComponents.VideoStreamAcceptor;
import org.example.serviceComponents.packets.AbstractPacket;
import org.example.serviceComponents.packets.KeepAlive;
import org.example.serviceComponents.packets.LacksRequest;
import org.example.serviceComponents.packets.PacketTypes;
import org.example.serviceComponents.packets.VideoFramePacket;
import org.example.serviceComponents.packets.VideoHeaderPacket;
import org.example.serviceComponents.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class ControlService extends Service implements ConnectionStateListener, PacketsProviderAndAcceptor, OutgoingSender, PacketAcceptor {


    private Logger logger = LoggerFactory.getLogger("Service");
    public static final ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
    private Thread mainThread;
    private volatile boolean shouldWork = true;
    private ConnectionManager connectionManager;
    private Binder binder;
    private volatile boolean connected = false;
    private Object lock = new Object();
    private AtomicBoolean awaiting = new AtomicBoolean(false);
    private Queue<PacketOut> outgoingQueue;
    private volatile VideoDirector director;
    private final TimersManager timersManager = new TimersManager();

    private VideoStreamAcceptor videoStreamAcceptor = new VideoStreamAcceptor() {
        byte[] tmpBuf = new byte[1024 * 1024];
        int tmpOffset = 0;

        @Override
        public void configureVideoAcceptor(int i, int i1) {

        }

        @Override
        public void writeVideoHeader(byte[] bytes, int i, int i1) throws Exception {

        }

        @Override
        public void writeVideoFrame(int id, int partIndex, int partsCount, byte[] bytes, int offset, int length) throws Exception {
            if (partIndex == 0) {
                tmpOffset = 0;
            }
            System.arraycopy(bytes, offset, tmpBuf, tmpOffset, length);
            tmpOffset += length;

            final VideoDirector videoDirector = director;
            if (videoDirector != null && partIndex == partsCount - 1) {
                videoDirector.enqueueVideoFrame(tmpBuf, 0, tmpOffset);
            }
        }
    };

    private final VideoFramesReader framesReader = new VideoFramesReader(320, 240, videoStreamAcceptor, this);


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onCreate() {
        super.onCreate();
        binder = new Binder();
        logger.debug("Service created");

        try {
            FactoryHolder.setFactory(new AndroidFactory(this));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        connectionManager = new ConnectionManager("desktop", "camera",
                this, this);
        outgoingQueue = connectionManager.getOutPackets();

        mainThread = new Thread(mainProcess, "service");
        mainThread.setDaemon(true);
        mainThread.start();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("Service started");
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        logger.debug("Service destroyed");
        shouldWork = false;
        super.onDestroy();
    }


    Runnable mainProcess = () -> {
        connectionManager.startAndKeepAlive();

        while (shouldWork) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            connectionManager.stopAndJoin();
        } catch (InterruptedException e) {
            logger.error("can not stop cm ", e);
        }
        stopSelf();
    };

    private void notifyIfRequired() {
        if (awaiting.get()) {
            synchronized (lock) {
                if (awaiting.get()) {
                    lock.notify();
                }
            }
        }
    }


    @Override
    public void onConnected() {
        connected = true;
    }

    @Override
    public void onConnectionLost() {
        connected = false;
    }

    @Override
    public void onIncomingPacket() {
        Queue<PacketOut> outputQueue = connectionManager.getOutPackets();
        PacketsBunchIn packetsBunch = connectionManager.getInputPackets().poll();
        byte[] packets = packetsBunch.getBunch();
        if (packets != null) {
            int packetType = AbstractPacket.getPacketType(packets);

            switch (packetType) {
                case PacketTypes.VideoFrame:
                    executorService.submit(() -> {
                        try {
                            framesReader.addFrame(packets, 0, packetsBunch.getLogId());
                        } catch (IOException e) {
                            logger.error("input frame", e);
                        }
                    });
                    break;
                case PacketTypes.VideoHeader:
                    framesReader.setHeader(packets, 0, packetsBunch.getLogId());
                    break;
                case PacketTypes.KeepAlive:
                    break;
                default:
                    logger.error("unknown packet {}, {}", packetType, packetsBunch.getLogId());
            }

        }
    }

    /*
    if (type == PacketTypes.VideoLacksRequest) {
        executorService.submit(() -> {

            notifyIfRequired();
        });
    }
    */


    @Override
    public PacketOut getKeepAlive() {
        KeepAlive ka = new KeepAlive();
        return new PacketOut(ka.toArray(), i -> {
        });
    }

    @Override
    public boolean fastCheck(byte[] buf) {
        return true;
    }

    public void notifyResume() {

    }

    public void notifyPause() {
    }

    public void setVideoDirector(VideoDirector director) {
        this.director = director;
    }
    public TimersManager getTimersManager(){
        return timersManager;
    }


    @Override
    public void takePacket(AbstractPacket packet) {
        send(new PacketOut(packet.toArray(), logId -> {}));
    }

    public void notifyActivityDestroied() {
        this.director = null;
    }

    @Override
    public void send(PacketOut packetOut) {
        outgoingQueue.add(packetOut);
    }


    public class Binder extends android.os.Binder {
        public ControlService getService() {
            return ControlService.this;
        }
    }
}
