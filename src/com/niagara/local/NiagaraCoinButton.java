package com.niagara.local;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * 右下角 Niagara 灵动悬浮按钮 (NiagaraCoinButton v2.5.0)：
 * - 消除 onDraw 内 Paint 对象分配，零 GC 压力；
 * - 支持多任务/搜索/助手多种图标形态自适应切换；
 * - 四向单手手势：轻点（可配置：极速搜索/多任务后台/小布语音）、上滑切换全部应用、下滑通知栏、左滑搜索/速记、长按打开控制中心。
 */
final class NiagaraCoinButton extends View {

    static final int ICON_RECENTS = 0;
    static final int ICON_SEARCH = 1;
    static final int ICON_ASSIST = 2;

    interface ActionCallback {
        void onTap();
        void onSwipeUp();
        void onSwipeDown();
        void onSwipeLeft();
        void onLongPress();
    }

    private final float dp;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private AuroraTheme theme;
    private ActionCallback callback;
    private int iconMode = ICON_RECENTS;
    private float downX, downY;
    private boolean pressedState = false;
    private boolean gestureTriggered = false;

    private final Runnable longPressTask = new Runnable() {
        @Override
        public void run() {
            if (pressedState && !gestureTriggered) {
                gestureTriggered = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(150).start();
                if (callback != null) callback.onLongPress();
            }
        }
    };

    NiagaraCoinButton(Context ctx) {
        super(ctx);
        this.dp = ctx.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        setElevation(6f * dp);
    }

    void bindTheme(AuroraTheme theme) {
        this.theme = theme;
        invalidate();
    }

    void setIconMode(int mode) {
        if (this.iconMode != mode) {
            this.iconMode = mode;
            invalidate();
        }
    }

    void setCallback(ActionCallback cb) {
        this.callback = cb;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (theme == null) return;
        float w = getWidth(), h = getHeight();
        float cx = w * 0.5f, cy = h * 0.5f;
        float r = Math.min(w, h) * 0.5f - 2f * dp;

        int[] cols = theme.coinButtonGradientColors();
        bgPaint.setShader(new LinearGradient(0, 0, w, h, cols[0], cols[1], Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, bgPaint);

        ringPaint.setStrokeWidth(1.0f * dp);
        ringPaint.setColor(theme.coinButtonRingColor());
        canvas.drawCircle(cx, cy, r - 0.5f * dp, ringPaint);

        if (iconMode == ICON_SEARCH) {
            float glassR = 6.8f * dp;
            float gx = cx - 1.5f * dp;
            float gy = cy - 1.5f * dp;
            fillPaint.setColor(theme.accent);
            fillPaint.setAlpha(theme.isLight ? 36 : 64);
            canvas.drawCircle(gx, gy, glassR, fillPaint);

            iconPaint.setAlpha(245);
            iconPaint.setStrokeWidth(1.9f * dp);
            iconPaint.setColor(theme.textPrimary);
            canvas.drawCircle(gx, gy, glassR, iconPaint);
            float handleStart = glassR * 0.72f;
            canvas.drawLine(gx + handleStart, gy + handleStart, cx + 7.5f * dp, cy + 7.5f * dp, iconPaint);
            return;
        }

        if (iconMode == ICON_ASSIST) {
            float u = 8.5f * dp;
            fillPaint.setColor(theme.accent);
            fillPaint.setAlpha(theme.isLight ? 45 : 75);
            canvas.drawCircle(cx, cy, u * 0.9f, fillPaint);

            iconPaint.setAlpha(245);
            iconPaint.setStrokeWidth(1.8f * dp);
            iconPaint.setColor(theme.textPrimary);
            canvas.drawLine(cx, cy - u, cx, cy + u, iconPaint);
            canvas.drawLine(cx - u, cy, cx + u, cy, iconPaint);
            float d = u * 0.62f;
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, iconPaint);
            canvas.drawLine(cx - d, cy + d, cx + d, cy - d, iconPaint);
            return;
        }

        // ICON_RECENTS: 双层叠放多任务卡片 ⊞ 矢量质感
        float cardW = 12.0f * dp;
        float cardH = 14.0f * dp;
        float corner = 3.6f * dp;
        float offset = 4.0f * dp;

        iconPaint.setStrokeWidth(1.65f * dp);
        iconPaint.setColor(theme.textPrimary);
        iconPaint.setAlpha(125);
        float bLeft = cx - cardW * 0.5f + offset;
        float bTop = cy - cardH * 0.5f - offset;
        canvas.drawRoundRect(bLeft, bTop, bLeft + cardW, bTop + cardH, corner, corner, iconPaint);

        float fLeft = cx - cardW * 0.5f - offset * 0.5f;
        float fTop = cy - cardH * 0.5f + offset * 0.5f;

        fillPaint.setColor(theme.accent);
        fillPaint.setAlpha(theme.isLight ? 45 : 75);
        canvas.drawRoundRect(fLeft, fTop, fLeft + cardW, fTop + cardH, corner, corner, fillPaint);

        iconPaint.setAlpha(255);
        iconPaint.setColor(theme.textPrimary);
        canvas.drawRoundRect(fLeft, fTop, fLeft + cardW, fTop + cardH, corner, corner, iconPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int a = event.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            pressedState = true;
            gestureTriggered = false;
            animate().scaleX(0.90f).scaleY(0.90f).setDuration(100).start();
            postDelayed(longPressTask, 420);
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE && pressedState && !gestureTriggered) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            setTranslationX(Math.max(-16f * dp, Math.min(16f * dp, dx * 0.26f)));
            setTranslationY(Math.max(-16f * dp, Math.min(16f * dp, dy * 0.26f)));

            float thresh = 38f * dp;
            if (Math.hypot(dx, dy) > thresh) {
                gestureTriggered = true;
                removeCallbacks(longPressTask);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(180).start();
                if (callback != null) {
                    if (Math.abs(dy) > Math.abs(dx)) {
                        if (dy < 0) callback.onSwipeUp();
                        else callback.onSwipeDown();
                    } else if (dx < 0) {
                        callback.onSwipeLeft();
                    } else {
                        callback.onTap();
                    }
                }
            }
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPressTask);
            animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(160).start();
            if (a == MotionEvent.ACTION_UP && pressedState && !gestureTriggered) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                if (callback != null) callback.onTap();
            }
            pressedState = false;
            return true;
        }
        return false;
    }
}
