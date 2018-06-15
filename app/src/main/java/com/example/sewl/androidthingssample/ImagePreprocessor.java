package com.example.sewl.androidthingssample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;

import junit.framework.Assert;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Created by mderrick on 10/2/17.
 */

public class ImagePreprocessor {

    private Bitmap rgbFrameBitmap;
    private Bitmap croppedBitmap;

    public ImagePreprocessor() {
        this.croppedBitmap = Bitmap.createBitmap(TensorflowImageOperations.IMAGE_SIZE, TensorflowImageOperations.IMAGE_SIZE,
                Bitmap.Config.ARGB_8888);
        this.rgbFrameBitmap = Bitmap.createBitmap(CameraHandler.IMAGE_WIDTH,
                CameraHandler.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
    }

    public Bitmap preprocessImage(final Image image) {
        if (image == null) {
            return null;
        }

        Assert.assertEquals("Invalid size width", rgbFrameBitmap.getWidth(), image.getWidth());
        Assert.assertEquals("Invalid size height", rgbFrameBitmap.getHeight(), image.getHeight());

        if (croppedBitmap != null && rgbFrameBitmap != null) {
            ByteBuffer bb = image.getPlanes()[0].getBuffer();
            rgbFrameBitmap = BitmapFactory.decodeStream(new ByteBufferBackedInputStream(bb));
            TensorflowImageOperations.cropAndRescaleBitmap(rgbFrameBitmap, croppedBitmap, 0);
        }

        image.close();

        return croppedBitmap;
    }

    private static class ByteBufferBackedInputStream extends InputStream {

        ByteBuffer buf;

        public ByteBufferBackedInputStream(ByteBuffer buf) {
            this.buf = buf;
        }

        public int read() {
            if (!buf.hasRemaining()) {
                return -1;
            }
            return buf.get() & 0xFF;
        }

        public int read(byte[] bytes, int off, int len) {
            if (!buf.hasRemaining()) {
                return -1;
            }

            len = Math.min(len, buf.remaining());
            buf.get(bytes, off, len);
            return len;
        }
    }
}
