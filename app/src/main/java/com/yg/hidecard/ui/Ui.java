package com.yg.hidecard.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

/**
 * 配色与圆角，取自 PUIInstaller 的 Material3 色板：
 * 背景 #F0F1F3 / 卡片 #FFFFFF / 强调 #BE3455，深色 背景 #000 / 卡片 #1A1A1A / 强调 #FFB1C1。
 */
public final class Ui {

    public final boolean dark;
    public final int bg;
    public final int card;
    public final int cardAlt;
    public final int text;
    public final int sub;
    public final int accent;
    public final int onAccent;
    public final int outline;
    public final int chipBg;
    public final int chipText;

    public Ui(Context ctx) {
        boolean night = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = night;
        if (night) {
            bg = 0xFF000000;
            card = 0xFF1A1A1A;
            cardAlt = 0xFF262628;
            text = 0xFFE6E1E2;
            sub = 0xFF9A8F92;
            accent = 0xFFFFB1C1;
            onAccent = 0xFF45001A;
            outline = 0xFF4E4447;
            chipBg = 0xFF5B3F45;
            chipText = 0xFFFFD9E0;
        } else {
            bg = 0xFFF0F1F3;
            card = 0xFFFFFFFF;
            cardAlt = 0xFFE7E8EB;
            text = 0xFF1C1B1C;
            sub = 0xFF7C7275;
            accent = 0xFFBE3455;
            onAccent = 0xFFFFFFFF;
            outline = 0xFFE3D6D9;
            chipBg = 0xFFFFD9E0;
            chipText = 0xFF7A1230;
        }
    }

    public static int dp(Context c, float v) {
        return (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()) + 0.5f);
    }

    public static GradientDrawable solid(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable stroked(Context c, int fill, float radiusDp,
                                          int strokeColor, float strokeDp) {
        GradientDrawable d = solid(c, fill, radiusDp);
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }
}
