package com.example.androidcontrol.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;

import com.example.androidcontrol.Size;

import org.example.YUVUtils;

import java.util.concurrent.atomic.AtomicLong;

public class YUVImage {
    private int scaledWidth;
    private int scaledHeight;
    private int offsetX;
    private int offsetY;
    private final Bitmap bitmap;
    private final BitmapShader bitmapShader;
    private final Paint paint;
    private float scale;
    private final RenderScript rs;
    private final Allocation allocationRgb;
    private final Allocation allocationYuv;
    private final ScriptIntrinsicYuvToRGB scriptYuvToRgb;
    private static AtomicLong versionCounter = new AtomicLong(0);
    long currentVersion;
    int yuvSize;

    public YUVImage(Context ctx, Size imageSize, Size containerSize) {
        rs = RenderScript.create(ctx);

        yuvSize = YUVUtils.calculateBufferSize(imageSize.getWidth(), imageSize.getHeight());

        calculateAndSetScale(imageSize, containerSize);

        bitmap = Bitmap.createBitmap(imageSize.getWidth(), imageSize.getHeight(), Bitmap.Config.ARGB_8888);
        allocationRgb = Allocation.createFromBitmap(rs, bitmap);
        allocationYuv = Allocation.createSized(rs, Element.U8(rs), yuvSize);
        scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        bitmapShader = new BitmapShader(bitmap,
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        this.bitmapShader.setLocalMatrix(matrix);


        paint = new Paint(Paint.ANTI_ALIAS_FLAG);// | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        paint.setShader(bitmapShader);
    }

    private void calculateAndSetScale(Size imageSize, Size containerSize) {
        //растягиваем, сохраняя пропорции, либо по ширине либо по высоте
        float widthScale = (float) containerSize.getWidth() / (float) imageSize.getWidth();
        float heightScale = (float) containerSize.getHeight() / (float) imageSize.getHeight();
        boolean widthStretch = widthScale < heightScale;

        //растягиваем по ширине, смещаем по вертикали
        if (widthStretch) {
            scale = widthScale;
        } else {
            scale = heightScale;
        }
        this.scaledWidth = (int) (scale * imageSize.getWidth());
        this.scaledHeight = (int) (scale * imageSize.getHeight());

        //сдвигаем по высоте
        if (widthStretch) {
            offsetY = (containerSize.getHeight() - scaledHeight) / 2;
        } else {
            //сдвигаем вправо
            offsetX = (containerSize.getWidth() - scaledWidth) / 2;
        }
    }

    public void transformYuvToBitmap(byte[] source, int offset) {
        //allocationYuv.copyFrom(source);
        allocationYuv.copy1DRangeFrom(offset, yuvSize, source);
        scriptYuvToRgb.setInput(allocationYuv);
        scriptYuvToRgb.forEach(allocationRgb);
        allocationRgb.copyTo(bitmap);
        currentVersion = versionCounter.incrementAndGet();
    }

    public void destroy() {
        // Release
        allocationYuv.destroy();
        allocationRgb.destroy();
        rs.destroy();
    }

    @Override
    public String toString() {
        return "YUV Image{" +
                "currentVersion=" + currentVersion +
                '}';
    }

    public int getScaledHeight() {
        return scaledHeight;
    }

    public int getScaledWidth() {
        return scaledWidth;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public Paint getPaint() {
        return paint;
    }

    public long getVersion() {
        return currentVersion;
    }
}
