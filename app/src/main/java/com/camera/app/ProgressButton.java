package com.pipiqiang.qcamera.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.DashPathEffect;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.appcompat.widget.AppCompatButton;

public class ProgressButton extends AppCompatButton {
    private Paint progressPaint;
    private Paint backgroundPaint;
    private Paint stripePaint;
    private Paint textPaint;
    private float progress = 0f; // 进度值，范围0-1
    private int progressColor = Color.parseColor("#9575CD"); // 进度颜色，使用浅紫色
    private int backgroundColor = Color.parseColor("#673AB7"); // 背景颜色，使用与其它按钮一致的紫色
    private RectF progressRect;
    private Path stripePath;
    private boolean isRunning = false; // 是否正在运行状态

    public ProgressButton(Context context) {
        super(context);
        init();
    }

    public ProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProgressButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        progressPaint = new Paint();
        progressPaint.setAntiAlias(true);
        progressPaint.setStyle(Paint.Style.FILL);

        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStyle(Paint.Style.FILL);

        stripePaint = new Paint();
        stripePaint.setAntiAlias(true);
        stripePaint.setStyle(Paint.Style.FILL);
        stripePaint.setColor(Color.parseColor("#FFFFFF")); // 白色条纹
        stripePaint.setAlpha(100); // 半透明

        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(getTextSize());
        textPaint.setColor(getCurrentTextColor());
        textPaint.setTextAlign(Paint.Align.CENTER);

        progressRect = new RectF();
        stripePath = new Path();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 绘制背景（与其它按钮一致的颜色）
        backgroundPaint.setColor(backgroundColor);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 8, 8, backgroundPaint);

        // 如果正在运行，绘制进度条
        if (isRunning && progress > 0) {
            // 绘制进度（使用浅色）
            progressPaint.setColor(progressColor);
            progressRect.set(0, 0, getWidth() * progress, getHeight());
            canvas.drawRoundRect(progressRect, 8, 8, progressPaint);
            
            // 绘制条纹效果
            drawStripes(canvas);
        }

        // 绘制按钮文字
        textPaint.setColor(getCurrentTextColor());
        textPaint.setTextSize(getTextSize());
        int xPos = (getWidth() / 2);
        int yPos = (int) ((getHeight() / 2) - ((textPaint.descent() + textPaint.ascent()) / 2));
        canvas.drawText(getText().toString(), xPos, yPos, textPaint);
    }

    private void drawStripes(Canvas canvas) {
        int stripeWidth = 10;
        int stripeSpacing = 20;
        int stripeHeight = getHeight();
        
        stripePath.reset();
        float progressWidth = getWidth() * progress;
        
        for (int i = 0; i < progressWidth; i += stripeSpacing) {
            float left = i;
            float right = Math.min(i + stripeWidth, progressWidth);
            
            if (right > left) {
                stripePath.reset();
                stripePath.moveTo(left, 0);
                stripePath.lineTo(right, 0);
                stripePath.lineTo(right, stripeHeight);
                stripePath.lineTo(left, stripeHeight);
                stripePath.close();
                canvas.drawPath(stripePath, stripePaint);
            }
        }
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress)); // 限制在0-1范围内
        invalidate();
    }

    public void setProgressState(boolean isRunning) {
        this.isRunning = isRunning;
        invalidate();
    }

    public void setProgressColor(int color) {
        this.progressColor = color;
        invalidate();
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        invalidate();
    }
}