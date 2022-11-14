import org.example.CommonConfig;
import org.example.services.DistributionService;
import org.example.services.videoconsumer.VideoFramesReader;
import org.example.services.videoconsumer.VideoRecorderDecorator;
import org.example.tools.UdpHoleDataPipeFactory;
import org.example.udphole.UdpHoleDataPipe;
import org.example.udphole.UdpHoleEndPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static UdpHoleDataPipeFactory factory = new Java8DesktopFactory();

    static {
        try {
            CommonConfig.loadPropsAndClose();
        } catch (IOException e) {
            logger.error("Main initialization", e);
        }
    }

    //may be run like java -Dproperties=some.app.props -jar consoleControlApp.jar
    public static void main(String[] args) throws Exception {
        DistributionService distributionService = new DistributionService();

        //it will be concat packets (bytes) from different services (such as Joystick for remote control, etc)
        UdpHoleDataPipe dataPipe = new UdpHoleDataPipe("desktop", "camera", factory);

        //main outgoing packets acceptor, it will use UdpHoleDataPipe
        UdpHoleEndPoint endPoint = new UdpHoleEndPoint(dataPipe, distributionService, factory.getTimersManager());

        VideoFramesReader framesReader;
        VideoRecorderDecorator videoRecorderDecorator = null;
        if (CommonConfig.recordVideo) {
            File videoFile = Paths.get(CommonConfig.packetsDir, String.format("video-%d.mp4", System.nanoTime())).toFile();
            videoRecorderDecorator = new VideoRecorderDecorator(new VlcPlayer(), videoFile);

            //it will be build videoframe from udp packets
            framesReader = new VideoFramesReader(videoRecorderDecorator, endPoint);
        } else {
            framesReader = new VideoFramesReader(new VlcPlayer(), endPoint);
        }

        //distribution service must redirect video packets to framesReader
        distributionService.registerService(framesReader);

        dataPipe.startConnectAsync();

        try {
            System.in.read();
            dataPipe.stop();
        } finally {
            if (videoRecorderDecorator != null) {
                videoRecorderDecorator.close();
            }
        }
    }

}
