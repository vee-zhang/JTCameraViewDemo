package com.laikang.jtcameraviewdemo;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Printer;
import android.view.MotionEvent;
import android.view.View;

import com.laikang.jtcameraview.JTCameraView;

/**
 * TODO: document your custom view class.
 */
public class CoverView extends View {

    private JTCameraView.Face[] faces;

    private Paint mPaint = new Paint();

    private Listener listener;

    private Rect focusArea = new Rect();
    private float focusWidth = 100;
    private float focusHeight = 100;
    private float fozusRadius = 100;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public CoverView(Context context) {
        super(context);
        init(null, 0);
    }

    public CoverView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public CoverView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }


    private void init(AttributeSet attrs, int defStyle) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.RED);
        mPaint.setTextSize(100);
    }

    public void update(JTCameraView.Face[] faces) {
        this.faces = faces;
        invalidate();
    }

    Matrix matrix;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        matrix = new Matrix();
        matrix.postScale(getWidth() / 2000f, getHeight() / 2000f);
        matrix.postTranslate(getWidth() / 2f, getHeight() / 2f);
    }

    public void clean() {
        this.faces = null;
        invalidate();
    }

    private float clickX;
    private float clickY;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (clickX != 0 && clickY != 0) {
            mPaint.setColor(Color.RED);
            canvas.drawCircle(clickX, clickY, fozusRadius, mPaint);
        }
        if (faces == null) {
            return;
        }
        mPaint.setColor(Color.GREEN);
        for (JTCameraView.Face face : faces) {
            RectF r = new RectF();
            matrix.mapRect(r, face.getRectF());
            canvas.drawRect(r, mPaint);
            canvas.drawText("可信度：" + face.getScore(), r.left, r.bottom, mPaint);
        }
        if (clickX != 0 && clickY != 0) {
            mPaint.setColor(Color.BLUE);
            canvas.drawRect(clickX - focusWidth / 2, clickY - focusHeight / 2, clickX + focusWidth / 2, clickY + focusHeight / 2, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            clickX = event.getX();
            clickY = event.getY();
            invalidate();
//            focusArea.left = (int) (clickX - focusWidth / 2 < 0 ? 0 : clickX - focusWidth / 2);
//            focusArea.top = (int) (clickY - focusHeight / 2 < 0 ? 0 : clickY - focusHeight / 2);
//            focusArea.right = (int) (clickX + focusWidth / 2 > getRight() ? getRight() : clickX + focusWidth / 2);
//            focusArea.bottom = (int) (clickY + focusHeight / 2 > getBottom() ? getBottom() : clickY + focusHeight / 2);

            focusArea.left = Math.max((int) (clickX - focusWidth / 2), 0);
            focusArea.top = Math.max((int) (clickY - focusHeight / 2), 0);
            focusArea.right = Math.min((int) (clickX + focusWidth / 2), getRight());
            focusArea.bottom = Math.min((int) (clickY + focusHeight / 2), getBottom());
            if (listener != null) {
                listener.onFocus(focusArea);
            }
        }
        return true;
    }

    public interface Listener {
        void onFocus(Rect rect);
    }
}
