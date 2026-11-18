package com.yg.hidecard.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 右侧圆形勾选指示：选中=实心强调色+白勾，未选=细描边圆环。 */
public final class CheckDot extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tick = new Path();
    private final int sizePx;
    private boolean checked;
    private int accent = 0xFFBE3455;
    private int outline = 0xFFE3D6D9;
    private int tickColor = 0xFFFFFFFF;

    public CheckDot(Context c, float sizeDp) {
        super(c);
        sizePx = Ui.dp(c, sizeDp);
    }

    public void setColors(int accent, int outline, int tickColor) {
        this.accent = accent;
        this.outline = outline;
        this.tickColor = tickColor;
        invalidate();
    }

    public void setChecked(boolean v) {
        if (checked != v) {
            checked = v;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(sizePx, sizePx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float stroke = Ui.dp(getContext(), 1.5f);
        float r = Math.min(w, h) / 2f - stroke;
        if (checked) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            canvas.drawCircle(cx, cy, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(tickColor);
            paint.setStrokeWidth(r * 0.30f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            tick.reset();
            tick.moveTo(cx - r * 0.42f, cy + r * 0.03f);
            tick.lineTo(cx - r * 0.10f, cy + r * 0.34f);
            tick.lineTo(cx + r * 0.45f, cy - r * 0.32f);
            canvas.drawPath(tick, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(outline);
            canvas.drawCircle(cx, cy, r, paint);
        }
    }
}
