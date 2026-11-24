package com.firelauncher.home;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Memory-friendly bitmap helpers (the Fire TV Stick only has 1 GB RAM). */
public final class BitmapUtil {

    private BitmapUtil() {
    }

    /** Largest power-of-two sample size that still keeps the image at least reqW x reqH. */
    public static int sampleSize(int srcW, int srcH, int reqW, int reqH) {
        int sample = 1;
        if (srcW <= 0 || srcH <= 0) {
            return 1;
        }
        while ((srcW / (sample * 2)) >= reqW && (srcH / (sample * 2)) >= reqH) {
            sample *= 2;
        }
        return sample;
    }

    /** Decodes a photo from a file, downsampled and in RGB_565 (half the RAM of ARGB_8888). */
    public static Bitmap decodeFile(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            o.inSampleSize = sampleSize(o.outWidth, o.outHeight, reqW, reqH);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Same as decodeFile but for an image bundled in res/drawable-nodpi. */
    public static Bitmap decodeResource(Resources res, int resId, int reqW, int reqH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, o);
            o.inSampleSize = sampleSize(o.outWidth, o.outHeight, reqW, reqH);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(res, resId, o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Draws any drawable (even a huge 512px app icon) into a small square ARGB bitmap. */
    public static Bitmap drawableToBitmap(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int iw = d.getIntrinsicWidth();
        int ih = d.getIntrinsicHeight();
        int w = size;
        int h = size;
        int left = 0;
        int top = 0;
        if (iw > 0 && ih > 0) {
            float s = Math.min((float) size / iw, (float) size / ih);
            w = Math.max(1, Math.round(iw * s));
            h = Math.max(1, Math.round(ih * s));
            left = (size - w) / 2;
            top = (size - h) / 2;
        }
        d.setBounds(left, top, left + w, top + h);
        d.draw(c);
        return b;
    }
}
