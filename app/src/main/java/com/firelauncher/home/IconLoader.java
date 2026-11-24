package com.firelauncher.home;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads app icons on two background threads, scales them to the tile size and keeps
 * at most ~8 MB in an LRU cache. Also draws the three built-in system icons.
 */
public class IconLoader {

    public static final int KIND_HOME = 1;
    public static final int KIND_SETTINGS = 2;
    public static final int KIND_OPTIONS = 3;

    private static final int CACHE_BYTES = 8 * 1024 * 1024;

    private final int sizePx;
    private final LruCache<String, Bitmap> cache;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    public IconLoader(int sizePx) {
        this.sizePx = sizePx;
        this.cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    /** Shows the icon of {@code entry} in {@code target}, loading it in the background if needed. */
    public void bind(final PackageManager pm, final AppEntry entry, final ImageView target) {
        final String key = entry.packageName;
        Bitmap hit = cache.get(key);
        target.setTag(key);
        if (hit != null) {
            target.setImageBitmap(hit);
            return;
        }
        target.setImageDrawable(null);
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap b = null;
                    try {
                        Drawable d = entry.activityInfo.loadIcon(pm);
                        if (d != null) {
                            b = BitmapUtil.drawableToBitmap(d, sizePx);
                        }
                    } catch (Throwable t) {
                        b = null;
                    }
                    if (b != null) {
                        cache.put(key, b);
                    }
                    final Bitmap result = b;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (result != null && key.equals(target.getTag())) {
                                target.setImageBitmap(result);
                            }
                        }
                    });
                }
            });
        } catch (Throwable t) {
            // Pool already shut down (activity closing): nothing to do.
        }
    }

    public void clear() {
        cache.evictAll();
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    /** Draws a simple white glyph for the System row tiles. */
    public Bitmap systemIcon(int kind) {
        int s = sizePx;
        float f = s;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFE8EAED);

        if (kind == KIND_HOME) {
            Path path = new Path();
            path.moveTo(0.50f * f, 0.14f * f);
            path.lineTo(0.90f * f, 0.50f * f);
            path.lineTo(0.78f * f, 0.50f * f);
            path.lineTo(0.78f * f, 0.84f * f);
            path.lineTo(0.58f * f, 0.84f * f);
            path.lineTo(0.58f * f, 0.60f * f);
            path.lineTo(0.42f * f, 0.60f * f);
            path.lineTo(0.42f * f, 0.84f * f);
            path.lineTo(0.22f * f, 0.84f * f);
            path.lineTo(0.22f * f, 0.50f * f);
            path.lineTo(0.10f * f, 0.50f * f);
            path.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
        } else if (kind == KIND_SETTINGS) {
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45f, f / 2f, f / 2f);
                c.drawRoundRect(new RectF(0.44f * f, 0.08f * f, 0.56f * f, 0.28f * f),
                        0.02f * f, 0.02f * f, p);
                c.restore();
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.13f * f);
            c.drawCircle(f / 2f, f / 2f, 0.26f * f, p);
        } else {
            // Sliders / "options" glyph
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor(0x99E8EAED);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeWidth(0.055f * f);
            c.drawLine(0.18f * f, 0.30f * f, 0.82f * f, 0.30f * f, line);
            c.drawLine(0.18f * f, 0.50f * f, 0.82f * f, 0.50f * f, line);
            c.drawLine(0.18f * f, 0.70f * f, 0.82f * f, 0.70f * f, line);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(0.38f * f, 0.30f * f, 0.085f * f, p);
            c.drawCircle(0.66f * f, 0.50f * f, 0.085f * f, p);
            c.drawCircle(0.46f * f, 0.70f * f, 0.085f * f, p);
        }
        return b;
    }
}
