package com.example.dataprovider.service;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.example.rollingHeap.RollingHeap;
import org.example.services.videoproducer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FromCameraImageProducer implements ImageProducer, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FromCameraImageProducer.class);

    private AppCompatActivity context;
    private ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture;

    private AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile ProcessCameraProvider cameraProvider;
    private volatile boolean shouldWork = true;

    private RollingHeap<YUVImage> rollingBuffer;
    private YUVImage lastImage;
    private byte[] tmpBuf;

    private int producingPeriod;
    private long lastProducedTimestamp = 0;

    private ImageSize desiredImageSize;

    public FromCameraImageProducer(@NonNull AppCompatActivity context, ImageSize desiredImageSize,
                                   int producingPeriod) {
        this.desiredImageSize = desiredImageSize;
        this.context = context;
        this.producingPeriod = producingPeriod;
        initCamera();

        logger.debug("ImageProducer created");
    }


    private void initCamera() {
        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderListenableFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderListenableFuture.get();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(desiredImageSize.getWidth(), desiredImageSize.getHeight()))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), image -> {
                    long nextInvoke = lastProducedTimestamp + producingPeriod;
                    long awaitMs = nextInvoke - System.currentTimeMillis();
                    if (awaitMs > 0) {
                        image.close();
                        return;
                    }
                    lastProducedTimestamp = System.currentTimeMillis();
                    if (shouldWork) {
                        if (!initialized.get()) {
                            int width = image.getWidth();
                            int height = image.getHeight();
                            try (@SuppressLint("UnsafeOptInUsageError") Image img = image.getImage()) {
                                tmpBuf = new byte[img.getPlanes()[0].getBuffer().capacity()];
                                rollingBuffer = new RollingHeap(() -> {
                                    return new YUVImage(width, height);
                                });
                                putImage(img);
                            }
                            initialized.set(true);
                        } else {
                            try (@SuppressLint("UnsafeOptInUsageError") Image img = image.getImage()) {
                                putImage(img);
                            }
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


    private void putImage(Image cameraImage) {
        final int w = cameraImage.getWidth();
        final int h = cameraImage.getHeight();

        int wh = w * h; //размер черно/белого блока
        int halfWidth = w / 2;
        int halfHeight = h / 2;
        int whUV = wh / 4; //размер блока цввета
        int y = 0;
        int uPlaneOffset = wh;
        int vPlaneOffset = wh + whUV;
        YUVImage image = rollingBuffer.getItemForWriting();

        if (cameraImage.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer byteBuffer = cameraImage.getPlanes()[0].getBuffer();
            final byte[] dstBuf = image.getBuffer();
            //картинка может быть шириной 480, а шаг больше - 512. соответственно
            //надо выфильтровывать хвостики
            int stride = cameraImage.getPlanes()[0].getRowStride();
            byteBuffer.get(tmpBuf, 0, byteBuffer.capacity());
            for (int rowIndex = 0; rowIndex < h; rowIndex++) {
                int srcRowOffset = rowIndex * stride;
                int dstRowOffset = rowIndex * w;
                System.arraycopy(tmpBuf, srcRowOffset, dstBuf, y + dstRowOffset, w);
            }

            byteBuffer = cameraImage.getPlanes()[1].getBuffer();
            stride = cameraImage.getPlanes()[1].getRowStride();
            int pixelStride = cameraImage.getPlanes()[1].getPixelStride();
            byteBuffer.get(tmpBuf, 0, byteBuffer.capacity());
            for (int rowIndex = 0; rowIndex < halfHeight; rowIndex++) {
                int srcRowOffset = rowIndex * stride;
                int dstRowOffset = rowIndex * halfWidth;
                for (int inRowPixelOffset = 0; inRowPixelOffset < halfWidth; inRowPixelOffset++) {
                    dstBuf[uPlaneOffset + dstRowOffset + inRowPixelOffset] = tmpBuf[srcRowOffset + (inRowPixelOffset * pixelStride)];
                }
                //System.arraycopy(tmpBuf, srcOoffset, image.buffer, u + dstOffset, halfWidth);
            }

            byteBuffer = cameraImage.getPlanes()[2].getBuffer();
            byteBuffer.get(tmpBuf, 0, byteBuffer.capacity());
            pixelStride = cameraImage.getPlanes()[2].getPixelStride();
            for (int rowIndex = 0; rowIndex < halfHeight; rowIndex++) {
                int srcRowOffset = rowIndex * stride;
                int dstOffset = rowIndex * halfWidth;
                for (int inRowPixelOffset = 0; inRowPixelOffset < halfWidth; inRowPixelOffset++) {
                    dstBuf[vPlaneOffset + dstOffset + inRowPixelOffset] = tmpBuf[srcRowOffset + (inRowPixelOffset * pixelStride)];
                }
                //System.arraycopy(tmpBuf, srcOoffset, image.buffer, v + dstOffset, halfWidth);
            }
            logger.debug("New image encoded to YUV format");
        } else {
            logger.error("Not implemented format {}", cameraImage.getFormat());
        }
        long nanoTimestampSinceBoot = cameraImage.getTimestamp();
        //codec consumes milliseconds
        image.setTimestamp(nanoTimestampSinceBoot / 10000000);// added one extra zero...
        synchronized (this) {
            if (lastImage != null) {
                rollingBuffer.freeItemAfterUsing(lastImage);
            }
            lastImage = image;
        }
    }

    /**
     * Get last Image from ready images queue
     *
     * @return
     */
    @Override
    public synchronized YUVImage getFreshImageOrNull() {
        if (lastImage != null) {
            logger.debug("Fresh image is grabbed");
            YUVImage image = lastImage;
            lastImage = null;
            return image;
        }
        return null;
    }

    @Override
    public void freeImage(YUVImage image) {
        rollingBuffer.freeItemAfterUsing(image);
    }

    @Override
    public void close() {
        shouldWork = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
            lastImage = null;
        }
        logger.debug("ImageProducer disposed");
    }
}
