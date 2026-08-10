package cn.longer233.gamenarrator.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public final class WaveformView extends View {
    private final Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playhead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] points = new float[0];
    private float playheadRatio;
    public WaveformView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(17,17,17));
        bars.setColor(Color.rgb(54,201,255));
        playhead.setColor(Color.rgb(255,79,163));
        playhead.setStrokeWidth(getResources().getDisplayMetrics().density * 2);
        setContentDescription("当前片段音频活动波形");
    }
    public void setPoints(float[] value) { points = value == null ? new float[0] : value.clone(); invalidate(); }
    public void setPlayheadRatio(float value) { playheadRatio = Math.max(0, Math.min(1, value)); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.length > 0) {
            float step = getWidth() / (float)points.length;
            float center = getHeight() / 2f;
            for (int i = 0; i < points.length; i++) {
                float half = Math.max(1, points[i] * (getHeight() * .44f));
                float x = (i + .5f) * step;
                canvas.drawRect(x, center - half, x + Math.max(1, step * .55f), center + half, bars);
            }
        }
        float x = playheadRatio * getWidth();
        canvas.drawLine(x, 0, x, getHeight(), playhead);
    }
}
