package org.example.serviceComponents.imageCreating;

import org.example.Utils;

import java.util.Arrays;

/**
 * analyze raw images, correct vibration and split big image to small parts
 */
public class ImageProcessor {


    private RawImage rawImage;
    private int version;
    private long lastFrameTime = 0;
    private static final int framesPeriod = 40;

    private volatile BitmapParts bitmapParts;
    private VibrationFilter filter = new VibrationFilter();
    private volatile boolean shouldWork = true;
    private volatile boolean newData = false;
    private Thread mainThread;
    private final Object lock = new Object();
    private volatile boolean awaiting = false;

    public ImageProcessor() {
        mainThread = new Thread(mainProcess, "ImageProcessor");
        mainThread.setDaemon(true);
        mainThread.start();
    }

    /**
     * should work fast non blocking mode
     */
    public void onNv21Frame(byte[] nv21Data, int width, int height) {
        rawImage = new RawImage(nv21Data, width, height);
        newData = true;
        synchronized (lock){
            if (awaiting){
                lock.notify();
            }
        }
    }


    public BitmapParts getBitmapParts() {
        return bitmapParts;
    }


    public void join() throws InterruptedException {
        shouldWork = false;
        mainThread.join(100);
    }


    private final Runnable mainProcess = () -> {
        try {
            while (shouldWork) {
                if (newData && Utils.elapsed(framesPeriod, lastFrameTime)) {
                    newData = false;
                    lastFrameTime = Utils.nowMs();
                    createNewVersion();
                } else {
                    synchronized (lock){
                        awaiting = true;
                        lock.wait(90);
                        awaiting = false;
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    };

    private void createNewVersion() {
        version++;
        RawImage image = rawImage;
        int pixelCount = image.width * image.height;
        byte[] out = new byte[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            out[i] = rawImage.rawData[i];
        }
        image = filter.filter(rawImage);
        bitmapParts = splitImage(image);

    }

    private BitmapParts splitImage(RawImage image) {
        int width = getPartWidthFor(image);
        int height = getPartHeightFor(image);
        int cols = image.width / width;
        int rows = image.height / height;

        ImagePartsConfiguration configuration = new ImagePartsConfiguration(version, 0, 0,
                width, height, image.width);

        ImagePart[][] parts = new ImagePart[rows][cols];


        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                parts[row][col] = new ImagePart(col, row, image.rawData, configuration);
            }
        }

        final BitmapParts result = new BitmapParts(configuration);
        Arrays.stream(parts).flatMap(Arrays::stream).forEach(d ->
                result.getParts().add(d)
        );
        return result;
    }

    private int getPartWidthFor(RawImage image) {
        return image.width / 10;
    }

    private int getPartHeightFor(RawImage image) {
        return image.height / 10;
    }
}
