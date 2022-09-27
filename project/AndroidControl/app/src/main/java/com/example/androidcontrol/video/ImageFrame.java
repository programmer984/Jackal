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

import java.util.concurrent.atomic.AtomicLong;

public class ImageFrame {
    public final int width;
    public final int height;
    public final Bitmap bitmap;
    public final BitmapShader bitmapShader;
    public final Paint paint;
    public final static int scale = 2;
    final RenderScript rs;
    final Allocation allocationRgb;
    final Allocation allocationYuv;
    final ScriptIntrinsicYuvToRGB scriptYuvToRgb;
    private static AtomicLong versionCounter = new AtomicLong(0);
    long currentVersion;
    int yuvSize;

    public ImageFrame(Context ctx, int width, int height) {
        rs = RenderScript.create(ctx);
        this.width = width;
        this.height = height;
        yuvSize = width * height * 3 / 2;

        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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
/*
    public void copyFromBitmap() {
        //buffer = IntBuffer.allocate(width * height);
        //this.bitmap.copyPixelsFromBuffer(buffer);
    }
*/

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

    public int getScaledHeight() {
        return scale * height;
    }

    public int getScaledWidth() {
        return scale * width;
    }

    public long getVersion() {
        return currentVersion;
    }
}
