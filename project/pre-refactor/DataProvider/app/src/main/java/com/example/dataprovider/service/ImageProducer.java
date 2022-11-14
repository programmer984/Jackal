package com.example.dataprovider.service;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageProducer {

    private final Queue<YUVImage> dummies = new ConcurrentLinkedQueue<>();
    private final Queue<YUVImage> product = new ConcurrentLinkedQueue<>();
    final byte[] tmpBuf = new byte[1024 * 1024];
    private int desiredWidth;
    private int desiredHeight;
    private AppCompatActivity context;
    private ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture;
    private Logger logger = LoggerFactory.getLogger("Service");
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile ProcessCameraProvider cameraProvider;
    private volatile boolean shouldWork = true;

    private int width;
    private int height;

    public ImageProducer(@NonNull AppCompatActivity context, int desiredWidth, int desiredHeight) {
        this.desiredWidth = desiredWidth;
        this.desiredHeight = desiredHeight;
        this.context = context;
        initCamera();

        dummies.add(new YUVImage());
        dummies.add(new YUVImage());
        dummies.add(new YUVImage());
        logger.debug("ImageProducer created");
    }


    private void initCamera() {
        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderListenableFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderListenableFuture.get();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(desiredWidth, desiredHeight))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), image -> {
                    if (!initialized.get()) {
                        width = image.getWidth();
                        height = image.getHeight();
                        initialized.set(true);
                    }
                    if (shouldWork) {
                        try (@SuppressLint("UnsafeOptInUsageError") Image img = image.getImage()) {
                            putImage(img);
                        }
                    }
                    image.close();
                });
                cameraProvider.bindToLifecycle(context, cameraSelector, imageAnalysis);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }, ContextCompat.getMainExecutor(context));
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void putImage(Image cameraImage) {
        //получем следующую не занятый буфер
        YUVImage image = dummies.poll();
        if (image == null) {
            logger.error("No dummies");
            return;
        }
        int bufSize = cameraImage.getWidth() * cameraImage.getHeight() * 3 / 2;
        if (image.buffer == null) {
            image.buffer = new byte[bufSize];
        }

        int w = cameraImage.getWidth();
        int h = cameraImage.getHeight();
        int wh = w * h; //размер черно/белого блока
        int halfWidth = w / 2;
        int halfHeight = h / 2;
        int whUV = wh / 4; //размер блока цввета
        int y = 0;
        int u = wh;
        int v = wh + whUV;

        if (cameraImage.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer byteBuffer = cameraImage.getPlanes()[0].getBuffer();
            //картинка может быть шириной 480, а шаг больше - 512. соответственно
            //надо выфильтровывать хвостики
            int stride = cameraImage.getPlanes()[0].getRowStride();
            byteBuffer.get(tmpBuf, 0, byteBuffer.capacity());
            for (int i = 0; i < h; i++) {
                int srcOoffset = i * stride;
                int dstOffset = i * w;
                System.arraycopy(tmpBuf, srcOoffset, image.buffer, y + dstOffset, w);
            }

            byteBuffer = cameraImage.getPlanes()[1].getBuffer();
            stride = cameraImage.getPlanes()[1].getRowStride();
            int pixelStride = cameraImage.getPlanes()[1].getPixelStride();
            byteBuffer.get(tmpBuf, 0, byteBuffer.capacity());
            for (int i = 0; i < halfHeight; i++) {
                int srcOoffset = i * stride;
                int dstOffset = i * halfWidth;
                for (int j = 0; j < halfWidth; j++) {
                    image.buffer[u + dstOffset + j] = tmpBuf[srcOoffset + (j * pixelStride)];
                }
                //System.arraycopy(tmpBuf, srcOoffset, image.buffer, u + dstOffset, halfWidth);
            }

            byteBuffer = cameraImage.getPlanes()[2].getBuffer();
            byteBuffer.get(tmpBuf, 0, byteBuffer.capacity());
            for (int i = 0; i < halfHeight; i++) {
                int srcOoffset = i * stride;
                int dstOffset = i * halfWidth;
                for (int j = 0; j < halfWidth; j++) {
                    image.buffer[v + dstOffset + j] = tmpBuf[srcOoffset + (j * pixelStride)];
                }
                //System.arraycopy(tmpBuf, srcOoffset, image.buffer, v + dstOffset, halfWidth);
            }

        } else {
            logger.error("Not implemented format {}", cameraImage.getFormat());
        }
        image.timestamp = cameraImage.getTimestamp();
        product.add(image);
    }

    /**
     * Get last Image from ready images queue
     *
     * @return
     */
    public YUVImage getNextImage() {
        YUVImage image = null;
        while (!product.isEmpty()) {
            image = product.poll();
            if (!product.isEmpty()) {
                freeImage(image);
            }
        }
        return image;
    }

    public void freeImage(YUVImage image) {
        dummies.add(image);
    }

    public void dispose() {
        shouldWork = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
            dummies.clear();
        }
        logger.debug("ImageProducer disposed");
    }
}
