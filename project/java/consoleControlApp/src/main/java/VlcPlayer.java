import org.example.CommonConfig;
import org.example.DataReference;
import org.example.services.videoconsumer.VideoStreamAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class VlcPlayer implements VideoStreamAcceptor {
    private static final Logger logger = LoggerFactory.getLogger(VlcPlayer.class);
    private Process process;
    private OutputStream pipedOutputStream;
    private InputStream processErrorStream;
    private int width;
    private int height;


    private Runnable errorPrinter = () -> {
        InputStreamReader streamReader = new InputStreamReader(processErrorStream);
        BufferedReader bufferedReader = new BufferedReader(streamReader);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String nextline = bufferedReader.readLine();
                if (nextline != null) {
                    System.out.println(nextline);
                } else {
                    return;
                }
            } catch (IOException e) {
                return;
            }
        }
    };

    @Override
    public void configureVideoAcceptor(int width, int height) {
        if (width != this.width || height != this.height) {
            Runtime rt = Runtime.getRuntime();

            try {
                if (process != null) {
                    process.destroy();
                }
                process = rt.exec(CommonConfig.videoSink);
                pipedOutputStream = process.getOutputStream();
                processErrorStream = process.getErrorStream();
                Thread errorPrintThread = new Thread(errorPrinter);
                errorPrintThread.setDaemon(true);
                errorPrintThread.start();
                this.width = width;
                this.height = height;
            } catch (IOException e) {
                logger.error("During process initialization ", e);
            }
        }
    }

    @Override
    public void writeVideoHeader(DataReference data) throws Exception {
        pipedOutputStream.write(data.getBuf(), data.getOffset(), data.getLength());
    }

    @Override
    public void writeVideoFrame(int id, int partIndex, int partsCount, DataReference data) throws Exception {
        pipedOutputStream.write(data.getBuf(), data.getOffset(), data.getLength());
    }
}
