package com.yg.hidecard.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

/** 应用图标：画到固定尺寸后裁圆角，结果进 LRU 缓存。 */
public final class IconCache {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(256);

    private IconCache() {
    }

    public static Bitmap get(Context ctx, String pkg, int sizePx) {
        String key = pkg + "@" + sizePx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Bitmap out = null;
        try {
            PackageManager pm = ctx.getPackageManager();
            Drawable d = pm.getApplicationIcon(pkg);
            out = render(d, sizePx);
        } catch (Throwable ignored) {
        }
        if (out == null) {
            out = placeholder(sizePx);
        }
        CACHE.put(key, out);
        return out;
    }

    private static Bitmap render(Drawable d, int size) {
        if (d == null) {
            return null;
        }
        Bitmap target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);

        Bitmap rounded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c2 = new Canvas(rounded);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(target, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float radius = size * 0.28f;
        c2.drawRoundRect(new RectF(0, 0, size, size), radius, radius, p);
        return rounded;
    }

    private static Bitmap placeholder(int size) {
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFB9BCC2);
        float radius = size * 0.28f;
        c.drawRoundRect(new RectF(0, 0, size, size), radius, radius, p);
        return bm;
    }
}
