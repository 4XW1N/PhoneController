package com.example.phonecontroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {

    public interface Listener {
        void onJoystickMoved(String axis, float x, float y);
    }

    public static final int SIDE_ANY = 0;
    public static final int SIDE_LEFT = 1;
    public static final int SIDE_RIGHT = 2;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private int side = SIDE_ANY;
    private boolean touched = false;
    private float baseX, baseY;
    private float radius;
    private float knobX, knobY;
    private int activePointer = -1;
    private Listener listener;

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setColor(0x33000000);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        ringPaint.setColor(0x66FFFFFF);
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(0xCCFFFFFF);
    }

    public void setSide(int side) {
        this.side = side;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!touched) return;
        canvas.drawCircle(baseX, baseY, radius, basePaint);
        canvas.drawCircle(baseX, baseY, radius, ringPaint);
        canvas.drawCircle(knobX, knobY, radius * 0.4f, knobPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return startTracking(event);
            case MotionEvent.ACTION_POINTER_DOWN:
                if (touched || !inOurHalf(event)) return false;
                return startTracking(event);
            case MotionEvent.ACTION_MOVE:
                if (touched) update(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touched = false;
                activePointer = -1;
                emit(0f, 0f);
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int idx = event.getActionIndex();
                if (idx >= 0 && idx < event.getPointerCount()
                        && event.getPointerId(idx) == activePointer) {
                    touched = false;
                    activePointer = -1;
                    emit(0f, 0f);
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean startTracking(MotionEvent event) {
        if (!inOurHalf(event)) return false;
        touched = true;
        activePointer = event.getPointerId(event.getActionIndex());
        radius = 70f * density;
        float x = event.getX(event.getActionIndex());
        float y = event.getY(event.getActionIndex());
        baseX = clamp(x, radius, getWidth() - radius);
        baseY = clamp(y, radius, getHeight() - radius);
        knobX = baseX;
        knobY = baseY;
        emit(0f, 0f);
        invalidate();
        return true;
    }

    private boolean inOurHalf(MotionEvent event) {
        if (side == SIDE_ANY) return true;
        float x = event.getX(event.getActionIndex());
        if (side == SIDE_LEFT) return x <= getWidth() / 2f;
        return x > getWidth() / 2f;
    }

    private void emit(float x, float y) {
        if (listener != null) listener.onJoystickMoved(null, x, y);
    }

    private void update(MotionEvent event) {
        int idx = event.findPointerIndex(activePointer);
        if (idx < 0) return;
        float x = event.getX(idx);
        float y = event.getY(idx);
        float dx = x - baseX;
        float dy = y - baseY;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > radius) {
            dx = dx * radius / dist;
            dy = dy * radius / dist;
        }
        knobX = baseX + dx;
        knobY = baseY + dy;
        emit(dx / radius, dy / radius);
        invalidate();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
