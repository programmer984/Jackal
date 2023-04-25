package com.example.androidcontrol.service;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.example.androidcontrol.R;
import com.example.androidcontrol.VideoDirector;
import com.example.factory.AndroidFactory;

import org.example.CommonConfig;
import org.example.communication.DataPipe;
import org.example.communication.KeepAlivePacketProducer;
import org.example.communication.logging.PostLogger;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.AbstractPacket;
import org.example.packets.KeepAlive;
import org.example.packets.PacketTypes;
import org.example.services.DistributionService;
import org.example.services.videoconsumer.VideoFramesCollector;
import org.example.tools.UdpHoleDataPipeFactory;
import org.example.udphole.UdpHoleDataPipe;
import org.example.udphole.UdpHoleEndPoint;
import org.example.udpplain.PlainUdpEndPoint;
import org.example.udpplain.UdpPlainDataPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;


public class ControlService extends Service {

    private final Logger logger = LoggerFactory.getLogger(ControlService.class);

    private static KeepAlivePacketProducer keepAlivePacketProducer = () -> new KeepAlive().toArray(true);

    private Binder binder;

    //actual image size will be chosen by CameraX library

    private UdpHoleDataPipeFactory factory;
    private VideoDirector director;
    private DistributionService distributionService;
    private OutgoingPacketCarrier endPoint;
    private DataPipe dataPipe;
    private VideoFramesCollector networkFramesReader;
    private InFileFramesReader fileFramesReader;

    private ServicePacketAcceptor netWorkFrameReaderProxy=new ServicePacketAcceptor() {
        @Override
        public Set<PacketTypes> getAcceptPacketTypes() {
            return networkFramesReader.getAcceptPacketTypes();
        }

        @Override
        public void accept(PacketReference packetReference, Integer logId) {
            if (fileFramesReader!=null){
                try {
                    fileFramesReader.close();
                } catch (Exception e) {
                    logger.error("On preload video stop", e);
                }
                fileFramesReader=null;
            }
            networkFramesReader.accept(packetReference, logId);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public void onCreate() {
        super.onCreate();

        binder = new Binder();
        logger.info("Service initialization started");

        try {
            factory = new AndroidFactory(this);

            distributionService = new DistributionService();

            if (CommonConfig.mainChannel.equals(CommonConfig.CHANNEL_TYPE_HOLE_UDP)) {
                dataPipe = new UdpHoleDataPipe("desktop", "camera", factory, keepAlivePacketProducer);
                //main outgoing packets acceptor, it will use UdpHoleDataPipe
                endPoint = new UdpHoleEndPoint(dataPipe, distributionService, factory.getTimersManager());
            } else {
                dataPipe = new UdpPlainDataPipe(keepAlivePacketProducer, factory.getTimersManager());
                endPoint = new PlainUdpEndPoint(dataPipe, distributionService, factory.getTimersManager());
            }

            dataPipe.startConnectAsync();

        } catch (IOException e) {
            logger.error("Service initialization failed", e);
            return;
        }

        logger.info("Service initialization complete");
    }

    private OutgoingPacketCarrier outgoingPacketCarrier = (AbstractPacket abstractPacket, PostLogger postLogger) -> {
        endPoint.packetWasBorn(abstractPacket, postLogger);
    };


    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("Service started");
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        logger.debug("Service destroyed");
        if (dataPipe != null) {
            dataPipe.stop();
        }
        super.onDestroy();
    }


    public void notifyResume() {

    }

    public void notifyPause() {

    }

    public void notifyActivityDestroied() {
        clearVideoDirector();
        logger.debug("GC Call");
        System.gc();
        logger.debug("GC Done");
    }


    public void setVideoDirector(VideoDirector director) {
        this.director = director;
        if (fileFramesReader == null) {
            Resources res = getResources();
            InputStream inputStream = res.openRawResource(R.raw.preload);
            fileFramesReader = new InFileFramesReader(director, inputStream, factory.getTimersManager());
            fileFramesReader.startReading();
        }

        networkFramesReader = new VideoFramesCollector(director, endPoint, factory.getTimersManager());
        //distribution service must redirect video packets to framesReader
        distributionService.registerService(netWorkFrameReaderProxy);
    }

    public void clearVideoDirector() {
        if (networkFramesReader != null) {
            distributionService.removeRegistration(netWorkFrameReaderProxy);
            networkFramesReader = null;
        }
        if (fileFramesReader != null) {
            try {
                fileFramesReader.close();
            } catch (Exception e) {
                logger.error("fileFramesReader disposing", e);
            }
            fileFramesReader = null;
        }
        if (director != null) {
            try {
                director.close();
            } catch (Exception e) {
                logger.error("VideoDirector disposing", e);
            }
            director = null;
        }
    }

    public UdpHoleDataPipeFactory getFactory() {
        return factory;
    }

    public OutgoingPacketCarrier getOutgoingPacketCarrier() {
        return endPoint;
    }


    public class Binder extends android.os.Binder {
        public ControlService getService() {
            return ControlService.this;
        }
    }


}
