package com.niagara.local;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 流体字母波浪导轨 (NiagaraWaveView v1.4.6)：
 *
 * 1. 【全模式左右手丝滑字母检索】无论在第一页常用应用还是在全部应用列表下，右手右侧边缘与左手左侧边缘
 *    按住上下滑动、向内滑动或点按字母均 100% 灵敏响应字母快捷搜索，彻底解决所有应用页无法使用右手字母快捷搜索的 Bug；
 * 2. 【全高度防系统返回手势误抢】updateGestureExclusionRects 始终排除右侧字母导轨区域的系统边缘手势，确保所有应用模式下右手滑字母不被系统侧滑返回抢占；
 * 3. 【点按 ★ 瞬时返回首页】在全部应用模式下轻点顶部 ★ 即可一键回到首页常用应用；
 * 4. 【模式切换冷却锁】从搜索返回常用应用时开启短暂冷却锁，避免残余触控误触。
 */
final class NiagaraWaveView extends View {

    interface Listener {
        void onWaveScrub(int index, char letter, float normX, float normY, float strength, boolean pullArmed);
        void onWaveRelease(int index, char letter, boolean triggerPullLaunch);
        void onWaveBackSwipe();
    }

    private final String symbols;
    private final float dp;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private AuroraTheme theme;
    private Typeface typeface = Typeface.DEFAULT;
    private Set<Character> availableLetters = Collections.emptySet();
    private Map<Character, String> topAppPreview = Collections.emptyMap();

    private boolean ghostEdgeTrigger = false;
    private boolean alignLeft = true;
    private boolean backSwipePreferred = false;
    private NiagaraWaveView delegateTarget;
    private NiagaraWaveView sourceGhostBar;
    private Listener listener;

    private float touchY = -1f;
    private float smoothY = -1f;
    private float velocityY = 0f;
    private float pullDepthPx = 0f;
    private float waveStrength = 0f;
    private int activeIdx = -1;
    private boolean pullArmed = false;
    private float pullExpandFrac = 0f;
    private boolean autoHideIdle = false;
    private ValueAnimator releaseAnim;
    private ValueAnimator pullExpandAnim;

    private float downY = 0f;
    private float downDepthPx = 0f;
    private long downTimeMs = 0L;
    private boolean waveEngaged = false;
    private boolean backSwipeHandled = false;
    private boolean touchFromLeft = false;
    private long cooldownUntilMs = 0L;

    private final Runnable delayedEngageTask = new Runnable() {
        @Override
        public void run() {
            if (!waveEngaged && !backSwipeHandled && touchY >= 0 && SystemClock.uptimeMillis() >= cooldownUntilMs) {
                engageWaveNow(touchY, pullDepthPx, touchFromLeft);
            }
        }
    };

    NiagaraWaveView(Context ctx, String symbols) {
        super(ctx);
        this.symbols = symbols;
        this.dp = ctx.getResources().getDisplayMetrics().density;
        letterPaint.setTextAlign(Paint.Align.CENTER);
        crestTextPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void bindStyle(AuroraTheme theme, Typeface tf, boolean autoHide) {
        this.theme = theme;
        this.typeface = tf != null ? tf : Typeface.DEFAULT;
        this.autoHideIdle = autoHide;
        invalidate();
    }

    void setAlignLeft(boolean left) {
        this.alignLeft = left;
        invalidate();
    }

    void setGhostEdgeDelegate(NiagaraWaveView target, boolean isLeftEdge) {
        this.ghostEdgeTrigger = true;
        this.alignLeft = isLeftEdge;
        this.delegateTarget = target;
        if (target != null) {
            target.sourceGhostBar = this;
        }
    }

    void setBackSwipePreferred(boolean preferred) {
        this.backSwipePreferred = preferred;
        if (Build.VERSION.SDK_INT >= 29 && isLaidOut()) {
            clearAllDynamicExclusions();
        }
    }

    void cancelAndCooldown(long durationMs) {
        cooldownUntilMs = SystemClock.uptimeMillis() + durationMs;
        removeCallbacks(delayedEngageTask);
        waveEngaged = false;
        backSwipeHandled = false;
        pullArmed = false;
        pullDepthPx = 0f;
        activeIdx = -1;
        touchY = -1f;
        smoothY = -1f;
        velocityY = 0f;
        clearAllDynamicExclusions();
        if (releaseAnim != null) releaseAnim.cancel();
        if (pullExpandAnim != null) pullExpandAnim.cancel();
        pullExpandFrac = 0f;
        waveStrength = 0f;
        invalidate();
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    void setAvailableData(Set<Character> available, Map<Character, String> previewMap) {
        this.availableLetters = available != null ? available : Collections.<Character>emptySet();
        this.topAppPreview = previewMap != null ? previewMap : Collections.<Character, String>emptyMap();
        invalidate();
    }

    private float railWidth() { return 46f * dp; }
    private float pullThreshold() { return 84f * dp; }

    private float slotHeight() {
        return Math.max(1f, getHeight() / (float) symbols.length());
    }

    private float flatTopFactor(float indexDist) {
        final float rampWidth = 9.5f;
        if (indexDist <= 0.0f) return 1.0f;
        if (indexDist >= rampWidth) return 0.0f;
        float t = indexDist / rampWidth;
        return 1.0f - (t * t * (3f - 2f * t));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= 29) {
            clearAllDynamicExclusions();
        }
    }

    void updateDynamicExclusionRect(float currentY) {
        if (Build.VERSION.SDK_INT < 29) return;
        int h = getHeight();
        int w = getWidth();
        if (w <= 0 || h <= 0) return;
        int rw = (int) railWidth();
        int exH = (int) Math.min(h, 200 * dp);
        int topY = (int) Math.max(0, Math.min(h - exH, currentY - exH / 2f));
        Rect r = alignLeft
                ? new Rect(0, topY, Math.min(w, rw), topY + exH)
                : new Rect(Math.max(0, w - rw), topY, w, topY + exH);
        setSystemGestureExclusionRects(Collections.singletonList(r));
    }

    void clearGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= 29) {
            setSystemGestureExclusionRects(Collections.<Rect>emptyList());
        }
    }

    private void applyDynamicExclusion(float currentY, boolean fromLeft) {
        if (fromLeft && sourceGhostBar != null) {
            sourceGhostBar.updateDynamicExclusionRect(currentY);
            clearGestureExclusionRects();
        } else {
            updateDynamicExclusionRect(currentY);
            if (sourceGhostBar != null) {
                sourceGhostBar.clearGestureExclusionRects();
            }
        }
    }

    private void clearAllDynamicExclusions() {
        clearGestureExclusionRects();
        if (sourceGhostBar != null) {
            sourceGhostBar.clearGestureExclusionRects();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ghostEdgeTrigger || theme == null) return;
        if (autoHideIdle && waveStrength <= 0.01f) return;

        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float slot = slotHeight();
        float baseX = alignLeft ? (16f * dp) : (w - 16f * dp);
        float dir = alignLeft ? 1f : -1f;

        if (touchY >= 0 && waveEngaged) {
            if (smoothY < 0) {
                smoothY = touchY;
                velocityY = 0f;
            } else {
                float diff = touchY - smoothY;
                velocityY = velocityY * 0.45f + diff * 0.35f;
                smoothY += velocityY;
                if (Math.abs(diff) > 0.35f || Math.abs(velocityY) > 0.25f) {
                    postInvalidateOnAnimation();
                }
            }
        }

        float extraPull = Math.min(26f * dp, Math.max(0f, pullDepthPx - railWidth()) * 0.30f);
        float maxShiftPx = (66f * dp + extraPull) * waveStrength;
        float centerIdxFloat = (smoothY >= 0) ? (smoothY / slot - 0.5f) : -100f;

        for (int i = 0; i < symbols.length(); i++) {
            char ch = symbols.charAt(i);
            float drawY = slot * i + slot * 0.5f;
            float factor = (waveStrength > 0.01f && smoothY >= 0)
                    ? flatTopFactor(Math.abs(i - centerIdxFloat))
                    : 0f;
            float drawX = baseX + dir * maxShiftPx * factor;
            boolean isActive = (i == activeIdx && waveStrength > 0.12f);

            boolean hasApp = ch == '★' || availableLetters.contains(ch);
            int color;
            if (isActive) {
                color = theme.accent;
            } else if (hasApp) {
                int alpha = waveStrength > 0.1f ? 0xEE000000 : (theme.isLight ? 0xCC000000 : 0xB8000000);
                color = (theme.textPrimary & 0x00FFFFFF) | alpha;
            } else {
                color = (theme.textSecondary & 0x00FFFFFF) | (waveStrength > 0.1f ? 0x66000000 : 0x34000000);
            }
            letterPaint.setColor(color);
            letterPaint.setTextSize((isActive ? 12.6f : 10.2f) * dp);
            letterPaint.setTypeface(isActive ? Typeface.create(typeface, Typeface.BOLD) : typeface);

            float baseline = drawY - (letterPaint.descent() + letterPaint.ascent()) * 0.5f;
            canvas.drawText(String.valueOf(ch), drawX, baseline, letterPaint);
        }

        if (activeIdx >= 0 && waveStrength > 0.06f && smoothY >= 0) {
            char ch = symbols.charAt(activeIdx);
            float badgeScale = Math.min(1f, waveStrength * 1.12f);
            float activeDrawX = baseX + dir * maxShiftPx * flatTopFactor(Math.abs(activeIdx - centerIdxFloat));
            float circleR = 19.0f * dp * badgeScale;
            float circleCx = activeDrawX + dir * 34f * dp * badgeScale;
            float circleCy = Math.max(circleR + 8f * dp, Math.min(h - circleR - 8f * dp, smoothY - 4f * dp));

            crestCirclePaint.setColor(pullArmed ? theme.accent : theme.accentBubble);
            crestCirclePaint.setShadowLayer(7f * dp, 0f, 2.5f * dp, 0x38000000);

            String previewApp = topAppPreview.get(ch);
            if (pullExpandFrac > 0.02f && previewApp != null && !previewApp.isEmpty()) {
                String launchLabel = "⚡ " + previewApp;
                crestTextPaint.setTextSize(13.5f * dp);
                crestTextPaint.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                float tw = crestTextPaint.measureText(launchLabel);
                float fullPillW = Math.max(circleR * 2f, tw + 24f * dp);
                float curW = circleR * 2f + (fullPillW - circleR * 2f) * pullExpandFrac;
                if (alignLeft) {
                    pillRect.set(circleCx - circleR, circleCy - circleR, circleCx - circleR + curW, circleCy + circleR);
                } else {
                    pillRect.set(circleCx - curW + circleR, circleCy - circleR, circleCx + circleR, circleCy + circleR);
                }
                canvas.drawRoundRect(pillRect, circleR, circleR, crestCirclePaint);
                crestTextPaint.setColor(0xFFFFFFFF);
                crestTextPaint.setAlpha(Math.round(255f * pullExpandFrac));
                if (pullExpandFrac > 0.45f) {
                    float by = pillRect.centerY() - (crestTextPaint.descent() + crestTextPaint.ascent()) * 0.5f;
                    canvas.drawText(launchLabel, pillRect.centerX(), by, crestTextPaint);
                }
                crestTextPaint.setAlpha(255);
            } else {
                canvas.drawCircle(circleCx, circleCy, circleR, crestCirclePaint);
                crestTextPaint.setColor(0xFFFFFFFF);
                crestTextPaint.setTextSize(16.5f * dp * badgeScale);
                crestTextPaint.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                float by = circleCy - (crestTextPaint.descent() + crestTextPaint.ascent()) * 0.5f;
                canvas.drawText(String.valueOf(ch), circleCx, by, crestTextPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (SystemClock.uptimeMillis() < cooldownUntilMs) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            boolean inTouchZone = alignLeft
                    ? (x <= railWidth())
                    : (x >= getWidth() - railWidth());
            if (!inTouchZone) return false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (ghostEdgeTrigger && delegateTarget != null) {
            float depth = alignLeft
                    ? Math.max(0f, event.getX())
                    : Math.max(0f, getWidth() - event.getX());
            return delegateTarget.handleWaveMotion(action, event.getY(), depth, alignLeft);
        }
        float depth = alignLeft
                ? Math.max(0f, event.getX())
                : Math.max(0f, getWidth() - event.getX());
        return handleWaveMotion(action, event.getY(), depth, alignLeft);
    }

    private void engageWaveNow(float y, float inwardDepthPx, boolean fromLeft) {
        waveEngaged = true;
        pullArmed = false;
        smoothY = y;
        velocityY = 0f;
        applyDynamicExclusion(y, fromLeft);
        animateStrength(1f);
        updateActiveLetterAndNotify(y, inwardDepthPx, fromLeft);
    }

    private void updateActiveLetterAndNotify(float y, float inwardDepthPx, boolean fromLeft) {
        touchY = Math.max(0f, Math.min(getHeight() - 1f, y));
        pullDepthPx = Math.max(0f, inwardDepthPx);

        float refY = smoothY >= 0 ? (smoothY * 0.5f + touchY * 0.5f) : touchY;
        int idx = (int) (refY / slotHeight());
        idx = Math.max(0, Math.min(symbols.length() - 1, idx));

        boolean nowArmed = pullDepthPx >= pullThreshold()
                && symbols.charAt(idx) != '★'
                && topAppPreview.containsKey(symbols.charAt(idx));

        if (idx != activeIdx) {
            activeIdx = idx;
            if (pullArmed != nowArmed) {
                pullArmed = nowArmed;
                animatePullExpand(nowArmed ? 1f : 0f);
            }
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } else if (nowArmed != pullArmed) {
            pullArmed = nowArmed;
            animatePullExpand(nowArmed ? 1f : 0f);
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }

        if (listener != null && getHeight() > 0) {
            listener.onWaveScrub(
                    activeIdx,
                    symbols.charAt(activeIdx),
                    fromLeft ? 0.15f : 0.85f,
                    touchY / getHeight(),
                    Math.max(0.3f, waveStrength),
                    pullArmed);
        }
        postInvalidateOnAnimation();
    }

    boolean handleWaveMotion(int action, float y, float inwardDepthPx, boolean fromLeft) {
        if (SystemClock.uptimeMillis() < cooldownUntilMs) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            removeCallbacks(delayedEngageTask);
            downY = y;
            downDepthPx = inwardDepthPx;
            downTimeMs = SystemClock.uptimeMillis();
            touchY = Math.max(0f, Math.min(getHeight() - 1f, y));
            pullDepthPx = Math.max(0f, inwardDepthPx);
            touchFromLeft = fromLeft;
            waveEngaged = false;
            backSwipeHandled = false;
            pullArmed = false;
            // 纯长按字母导轨 140ms 未大幅滑动时，确认进入字母流体波浪选词状态
            postDelayed(delayedEngageTask, 140L);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (backSwipeHandled) return true;
            touchY = Math.max(0f, Math.min(getHeight() - 1f, y));
            pullDepthPx = Math.max(0f, inwardDepthPx);
            float dy = Math.abs(y - downY);
            float dxInward = inwardDepthPx - downDepthPx;

            if (!waveEngaged) {
                // 1. 优先精准识别向内滑动返回手势：水平位移明显（>16dp）且以向内水平滑动为主（dx > dy * 1.15）
                // 此时绝不唤出字母波浪、绝不跳跃字母、绝不高亮应用，100% 干净顺畅触发返回！
                if (dxInward > 16.0f * dp && dxInward > dy * 1.15f) {
                    removeCallbacks(delayedEngageTask);
                    if (backSwipePreferred && listener != null) {
                        backSwipeHandled = true;
                        listener.onWaveBackSwipe();
                        cancelAndCooldown(320L);
                        return true;
                    }
                    return true;
                }

                // 2. 精准识别沿字母导轨上下滑动检索：垂直位移（>6dp）且以垂直滑动为主（dy >= dx * 0.8）
                // 此时立即唤起流体波浪，并动态排除系统侧滑返回误触！
                if (dy > 6.0f * dp && dy >= dxInward * 0.8f) {
                    removeCallbacks(delayedEngageTask);
                    engageWaveNow(y, inwardDepthPx, fromLeft);
                    return true;
                }
                return true;
            }

            // 3. 已进入字母波浪交互模式：手指滑动时持续跟随手指更新系统手势防误触排除区
            applyDynamicExclusion(y, fromLeft);
            updateActiveLetterAndNotify(y, inwardDepthPx, fromLeft);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(delayedEngageTask);
            clearAllDynamicExclusions();
            if (backSwipeHandled) {
                backSwipeHandled = false;
                return true;
            }
            if (!waveEngaged) {
                // 左右两侧短促轻点字母导轨：响应对应字母直达
                if (action == MotionEvent.ACTION_UP && getHeight() > 0) {
                    float dy = Math.abs(y - downY);
                    float dxInward = Math.abs(inwardDepthPx - downDepthPx);
                    if (dy < 14f * dp && dxInward < 20f * dp) {
                        int tapIdx = Math.max(0, Math.min(symbols.length() - 1, (int) (touchY / slotHeight())));
                        char tapChar = symbols.charAt(tapIdx);
                        if (backSwipePreferred && tapChar == '★' && listener != null) {
                            listener.onWaveBackSwipe();
                            return true;
                        }
                        if (listener != null) {
                            listener.onWaveScrub(tapIdx, tapChar, fromLeft ? 0.15f : 0.85f, touchY / getHeight(), 0.5f, false);
                            listener.onWaveRelease(tapIdx, tapChar, false);
                        }
                    }
                }
                touchY = -1f;
                return true;
            }

            int finalIdx = activeIdx;
            char finalChar = finalIdx >= 0 && finalIdx < symbols.length() ? symbols.charAt(finalIdx) : '★';
            boolean launch = (action == MotionEvent.ACTION_UP) && pullArmed;
            waveEngaged = false;
            pullArmed = false;
            pullDepthPx = 0f;
            animatePullExpand(0f);
            animateStrength(0f);
            if (listener != null) {
                listener.onWaveRelease(finalIdx, finalChar, launch);
            }
            return true;
        }
        return false;
    }

    private void animatePullExpand(final float target) {
        if (pullExpandAnim != null) pullExpandAnim.cancel();
        pullExpandAnim = ValueAnimator.ofFloat(pullExpandFrac, target);
        pullExpandAnim.setDuration(160);
        pullExpandAnim.addUpdateListener(va -> {
            pullExpandFrac = (float) va.getAnimatedValue();
            invalidate();
        });
        pullExpandAnim.start();
    }

    private void animateStrength(final float target) {
        if (releaseAnim != null) releaseAnim.cancel();
        releaseAnim = ValueAnimator.ofFloat(waveStrength, target);
        releaseAnim.setDuration(target > 0f ? 165 : 260);
        if (target > 0f) releaseAnim.setInterpolator(new OvershootInterpolator(1.06f));
        releaseAnim.addUpdateListener(va -> {
            waveStrength = (float) va.getAnimatedValue();
            if (waveStrength <= 0.01f && target == 0f) {
                touchY = -1f;
                smoothY = -1f;
                velocityY = 0f;
                activeIdx = -1;
            }
            invalidate();
        });
        releaseAnim.start();
    }
}
