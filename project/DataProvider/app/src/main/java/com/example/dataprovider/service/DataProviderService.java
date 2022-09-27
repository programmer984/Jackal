package com.example.dataprovider.service;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dataprovider.MainActivity;
import com.example.dataprovider.factory.AndroidFactory;
import com.example.dataprovider.hw.Hm10Pipe;

import org.example.ConnectionManager;
import org.example.ConnectionStateListener;
import org.example.FactoryHolder;
import org.example.PacketOut;
import org.example.PacketsBunchIn;
import org.example.PacketsProviderAndAcceptor;
import org.example.Utils;
import org.example.serviceComponents.hw.HardwarePipe;
import org.example.serviceComponents.imageCreating.BitmapParts;
import org.example.serviceComponents.imageCreating.ImagePart;
import org.example.serviceComponents.packets.AbstractPacket;
import org.example.serviceComponents.packets.HWDoMove;
import org.example.serviceComponents.packets.HWKeepAlive;
import org.example.serviceComponents.packets.ImagePartPacket;
import org.example.serviceComponents.packets.KeepAlive;
import org.example.serviceComponents.packets.LacksRequest;
import org.example.serviceComponents.packets.PacketTypes;
import org.example.serviceComponents.packets.VideoFramePacket;
import org.example.serviceComponents.packets.VideoHeaderPacket;
import org.example.serviceComponents.DataConsumer;
import org.example.serviceComponents.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

public class DataProviderService extends Service implements ConnectionStateListener, PacketsProviderAndAcceptor, PacketAcceptor {

    private final Logger logger = LoggerFactory.getLogger(DataProviderService.class);
    public static final ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
    private Thread mainThread;
    private volatile boolean shouldWork = true;
    private ConnectionManager connectionManager;
    private Binder binder;
    private Object lock = new Object();
    private AtomicBoolean awaiting = new AtomicBoolean(false);
    private VideoProducer videoProducer;
    private VideoConfig videoConfig = new VideoConfig(320, 240);

    private long lastSentHeaderTimestamp = Utils.nowMs();
    private static int HEADER_INTERVAL = 1000;
    private volatile byte[] header;
    private int videoFramesVersion = 0;
    private Queue<VideoFramePacket> videoFrames = new ConcurrentLinkedDeque<>();
    //private byte[] initialVideoFrame;
    private int mtu;
    private final TimersManager timersManager = new TimersManager();
    HardwarePipe hm10Pipe;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onCreate() {
        super.onCreate();
        binder = new Binder();
        logger.debug("Service created");
        mtu = Utils.getPaddedSize(1000 + VideoFramePacket.HEADER_LENGTH + VideoFramePacket.TLC_LENGTH);
        try {
            FactoryHolder.setFactory(new AndroidFactory(this));
        } catch (IOException e) {
            logger.error("Service initialization", e);
            return;
        }
        mainThread = new Thread(mainProcess, "service");
        mainThread.setDaemon(true);
        mainThread.start();

        hm10Pipe = new Hm10Pipe(this, hardwarePacketsConsumer, timersManager);
        timersManager.addTimer(2000, true, () -> {
           if  (hm10Pipe.isOpen()){
               byte[] keepAlive = new HWKeepAlive().toArray();
               hm10Pipe.sendPacket(keepAlive);
           }
        });
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

    DataConsumer hardwarePacketsConsumer = (data, offset, size) -> {
        logger.info("hardware incoming {}", data[0]);
    };


    Runnable mainProcess = () -> {
        connectionManager = new ConnectionManager("camera", "desktop",
                DataProviderService.this, DataProviderService.this);
        connectionManager.startAndKeepAlive();
        Queue<PacketOut> outputQueue = connectionManager.getOutPackets();
        while (shouldWork) {
            try {
                if (header != null && Utils.elapsed(HEADER_INTERVAL, lastSentHeaderTimestamp) && outputQueue.isEmpty()) {
                    outputQueue.add(new PacketOut(new VideoHeaderPacket(header).toArray(), (logId) ->
                            logger.debug("LogId {} Header packet", logId)));
                    lastSentHeaderTimestamp = Utils.nowMs();
                }
                while (!videoFrames.isEmpty()) {
                    VideoFramePacket vf = videoFrames.poll();
                    if (outputQueue.size() < 20) {
                        outputQueue.add(new PacketOut(vf.toArray(), (logId) ->
                                logger.debug("LogId {} {}", logId, vf.getDescription())));
                    } else {
                       /* logger.warn("Output queue size {}, videoFrames size {}",
                                outputQueue.size(), videoFrames.size());*/
                    }
                }
                synchronized (lock) {
                    awaiting.set(true);
                    lock.wait(5);
                    awaiting.set(false);
                }
            } catch (InterruptedException e) {
                logger.error("main process", e);
                shouldWork = false;
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

    Map<Integer, VideoFramePacket[]> lacks = new HashMap<>();
    private final int lacksSize = 5;
    private DataAcceptor newVideoDataAcceptor = (videoFrameType, data, offset, size) -> {
        byte[] buf = new byte[size];
        System.arraycopy(data, offset, buf, 0, size);
        onVideoFrame(videoFrameType, buf);
    };

    private void onVideoFrame(VideoFrameTypes videoFrameType, byte[] h264Frame) {
        if ((videoFrameType == VideoFrameTypes.VideoFrameTypeIDR || videoFrameType == VideoFrameTypes.Unknown)
                && header == null) {
            if (h264Frame.length <= 28 && VideoHeaderPacket.h264HeaderLikeRight(h264Frame, 0)) {
                header = h264Frame;
                return;
            }
            if (header == null) {
                try {
                    header = VideoHeaderPacket.searchH264Header(h264Frame);
                    return;
                } catch (Exception ex) {
                    logger.error("Header search ", ex);
                }
            }
        }

        if (videoFrameType == VideoFrameTypes.VideoFrameTypeI
                || videoFrameType == VideoFrameTypes.VideoFrameTypeIDR) {
            logger.debug("I-frame");
        }

        if (videoFrames.size() < 100) {
            VideoFramePacket[] videoFramePackets = VideoFramePacket.split(h264Frame, videoFramesVersion, mtu,
                    VideoFrameTypes.VideoFrameTypeIDR.equals(videoFrameType));
            lacks.put(videoFramesVersion, videoFramePackets);
            Set<Integer> old = new HashSet<>();
            for (int id : lacks.keySet()) {
                if (id < videoFramesVersion - lacksSize) {
                    old.add(id);
                }
            }
            for (int id : old) {
                lacks.remove(id);
            }
            videoFrames.addAll(Arrays.asList(videoFramePackets));
            videoFramesVersion++;
            notifyIfRequired();
        }
    }


    @Override
    public void onConnected() {

    }

    @Override
    public void onConnectionLost() {

    }

    @Override
    public void onIncomingPacket() {
        Queue<PacketOut> outputQueue = connectionManager.getOutPackets();
        PacketsBunchIn in = connectionManager.getInputPackets().poll();
        byte[] packet = in.getBunch();
        if (packet != null) {
            int type = AbstractPacket.getPacketType(packet);
            if (type == PacketTypes.VideoLacksRequest) {
                executorService.submit(() -> {
                    Map<Integer, Set<Integer>> lacksRequest = LacksRequest.getLacks(packet, 0);
                    for (int id : lacksRequest.keySet()) {
                        Set<Integer> parts = lacksRequest.get(id);
                        VideoFramePacket[] frames = lacks.get(id);
                        if (frames != null) {
                            for (int part : parts) {
                                outputQueue.add(new PacketOut(frames[part].toArray(), (logId) ->
                                {
                                    logger.debug("LogID: {}. lack resend for id {} part {}", logId, id, part);
                                }
                                ));
                            }
                        }
                    }
                    notifyIfRequired();
                });
            }else if (PacketTypes.isHwThroughPacket(type) && hm10Pipe.isOpen()){
                int hwdomovesize=13;
                if (type==PacketTypes.HWDoMove && packet.length>=hwdomovesize){
                    hm10Pipe.sendPacket(Arrays.copyOfRange(packet, 0, hwdomovesize-1));
                }
            }
        }
    }

    @Override
    public PacketOut getKeepAlive() {
        KeepAlive ka = new KeepAlive();
        return new PacketOut(ka.toArray(), (logId) -> logger.debug("LogId {} {}", logId, ka.getDescription()));
    }

    @Override
    public boolean fastCheck(byte[] buf) {
        return true;
    }

    public void notifyResume() {

    }

    public void notifyPause() {

    }

    public void notifyActivityDestroied() {
        if (videoProducer != null) {
            videoProducer.dispose();
            videoProducer = null;
        }
        logger.debug("GC Call");
        System.gc();
        logger.debug("GC Done");
    }

    public void setActivityContext(AppCompatActivity mainActivity) {
        if (videoProducer != null) {
            videoProducer.dispose();
        }
        ImageProducer imageProducer = new ImageProducer(mainActivity, videoConfig.getWidth(), videoConfig.getHeight());
        videoProducer = new VideoProducer(imageProducer, videoConfig, newVideoDataAcceptor);
    }

    @Override
    public void takePacket(AbstractPacket packet) {
        if (hm10Pipe.isOpen()){
            hm10Pipe.sendPacket(packet.toArray());
        }
    }

    public TimersManager getTimersManager(){
        return timersManager;
    }

    public class Binder extends android.os.Binder {
        public DataProviderService getService() {
            return DataProviderService.this;
        }
    }
}
