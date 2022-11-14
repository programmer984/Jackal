package com.example.dataprovider.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.dataprovider.hw.FromHwPacketsAcceptor;
import com.example.dataprovider.hw.Hm10Pipe;
import com.example.dataprovider.hw.HwRemoteControlPacketsAcceptor;
import com.example.factory.AndroidFactory;

import org.example.CommonConfig;
import org.example.communication.DataPipe;
import org.example.communication.KeepAlivePacketProducer;
import org.example.communication.logging.PostLogger;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.packets.AbstractPacket;
import org.example.packets.KeepAlive;
import org.example.services.DistributionService;
import org.example.services.videoproducer.CachedVideoProducer;
import org.example.services.videoproducer.ImageSize;
import org.example.services.videoproducer.VideoConfig;
import org.example.services.videoproducer.codec.CodecCreator;
import org.example.tools.UdpHoleDataPipeFactory;
import org.example.udphole.UdpHoleDataPipe;
import org.example.udphole.UdpHoleEndPoint;
import org.example.udpplain.PlainUdpEndPoint;
import org.example.udpplain.UdpPlainDataPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DataProviderService extends Service {

    private final Logger logger = LoggerFactory.getLogger(DataProviderService.class);
    private static KeepAlivePacketProducer keepAlivePacketProducer = () -> new KeepAlive().toArray(true);

    private Binder binder;

    //actual image size will be chosen by CameraX library
    private VideoConfig videoConfig;
    private UdpHoleDataPipeFactory factory;
    private CachedVideoProducer videoProducer;
    private DistributionService distributionService;
    private OutgoingPacketCarrier endPoint;
    private DataPipe dataPipe;
    private Hm10Pipe localHwEndpoint;

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
            videoConfig = new VideoConfig(new ImageSize(320, 240));
            videoConfig.setMaxFrameRate(25);
            videoConfig.setTargetBitrate(15000000); // ~1.5MB/s

            distributionService = new DistributionService();

            if (CommonConfig.mainChannel.equals(CommonConfig.CHANNEL_TYPE_HOLE_UDP)) {
                dataPipe = new UdpHoleDataPipe("camera", "desktop", factory, keepAlivePacketProducer);
                //main outgoing packets acceptor, it will use UdpHoleDataPipe
                endPoint = new UdpHoleEndPoint(dataPipe, distributionService, factory.getTimersManager());
            } else {
                dataPipe = new UdpPlainDataPipe(keepAlivePacketProducer, factory.getTimersManager());
                endPoint = new PlainUdpEndPoint(dataPipe, distributionService, factory.getTimersManager());
            }

            dataPipe.startConnectAsync();

            localHwEndpoint = new Hm10Pipe(this, distributionService, factory.getTimersManager());
            //from remote control to local bluetooth hardware forwarding
            distributionService.registerService(new HwRemoteControlPacketsAcceptor(localHwEndpoint));
            //from local bluetooth hardware to something forwarding dummy
            distributionService.registerService(new FromHwPacketsAcceptor());

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
        if (videoProducer != null) {
            try {
                distributionService.removeRegistration(videoProducer);
                videoProducer.close();
            } catch (Exception e) {
                logger.error("dispose videoProducer ", e);
            }
            videoProducer = null;
        }
        logger.debug("GC Call");
        System.gc();
        logger.debug("GC Done");
    }

    private CodecCreator codecCreator = (width, height, videoFrameConsumer) -> {
        try {
            //new VideoRecorderDecorator(videoFrameConsumer)
            return new H264Codec(width, height, videoConfig.getMaxFrameRate(), videoConfig.getTargetBitrate(),
                    videoFrameConsumer, factory.getTimersManager());
        } catch (Exception e) {
            logger.error("openH264 codec initialization", e);
            throw new RuntimeException(e);
        }
    };


    public void setActivityContext(AppCompatActivity mainActivity) {
        if (videoProducer != null) {
            try {
                videoProducer.close();
            } catch (Exception e) {
                logger.error("video producer disposing", e);
            }
        }
        FromCameraImageProducer imageProducer = new FromCameraImageProducer(mainActivity, videoConfig.getDesiredSize(), videoConfig.getMaxFrameRate());
        videoProducer = new CachedVideoProducer(outgoingPacketCarrier, imageProducer, videoConfig, factory.getTimersManager(), codecCreator);
        distributionService.registerService(videoProducer);
    }


    public class Binder extends android.os.Binder {
        public DataProviderService getService() {
            return DataProviderService.this;
        }
    }

}
