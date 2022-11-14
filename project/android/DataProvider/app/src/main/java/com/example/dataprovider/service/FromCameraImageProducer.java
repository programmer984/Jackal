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
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.example.YUVUtils;
import org.example.services.videoproducer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FromCameraImageProducer implements ImageProducer, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FromCameraImageProducer.class);

    private YUVImage writingImage;
    private YUVImage temporaryImage;
    private byte[] tmpBuf;
    private AppCompatActivity context;
    private ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture;

    private AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile ProcessCameraProvider cameraProvider;
    private volatile boolean shouldWork = true;

    private ImageSize desiredImageSize;

    public FromCameraImageProducer(@NonNull AppCompatActivity context, ImageSize desiredImageSize) {
        this.desiredImageSize = desiredImageSize;
        this.context = context;
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
                    if (shouldWork) {
                        if (!initialized.get()) {
                            int width = image.getWidth();
                            int height = image.getHeight();
                            try (@SuppressLint("UnsafeOptInUsageError") Image img = image.getImage()) {
                                tmpBuf = new byte[img.getPlanes()[0].getBuffer().capacity()];
                                writingImage = new YUVImage(width, height);
                                temporaryImage = new YUVImage(width, height);
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


    private synchronized void putImage(Image cameraImage) {
        final int w = cameraImage.getWidth();
        final int h = cameraImage.getHeight();
        //получем следующую не занятый буфер


        int wh = w * h; //размер черно/белого блока
        int halfWidth = w / 2;
        int halfHeight = h / 2;
        int whUV = wh / 4; //размер блока цввета
        int y = 0;
        int uPlaneOffset = wh;
        int vPlaneOffset = wh + whUV;

        if (cameraImage.getFormat() == ImageFormat.YUV_420_888) {
            ByteBuffer byteBuffer = cameraImage.getPlanes()[0].getBuffer();
            final byte[] dstBuf = writingImage.getBuffer();
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

        } else {
            logger.error("Not implemented format {}", cameraImage.getFormat());
        }
        writingImage.setTimestamp(cameraImage.getTimestamp());
    }

    /**
     * Get last Image from ready images queue
     *
     * @return
     */
    @Override
    public synchronized YUVImage getFreshImageOrNull() {
        YUVImage image = writingImage;
        writingImage = temporaryImage;
        temporaryImage = null;
        return image;
    }

    @Override
    public void freeImage(YUVImage image) {
        temporaryImage = image;
    }

    @Override
    public void close() {
        shouldWork = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
            writingImage = null;
            temporaryImage = null;
        }
        logger.debug("ImageProducer disposed");
    }
}
