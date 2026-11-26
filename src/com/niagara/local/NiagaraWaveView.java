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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

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
 * 4. 【模式切换冷却锁】从搜索返回常用应用时开启短暂冷却锁，避免残余触控误触；
 * 5. 【v2.6.0 高斯正态波浪包络】波浪形变因数 f(y) = exp(-(y-y0)^2 / (2σ^2))，替代原分段折线，边缘无折角；
 * 6. 【v2.6.0 精准手势排除区】触点上下各 90dp 的系统边缘手势排除区实时跟随，松手 300ms 后回到拇指常驻区；
 * 7. 【v2.6.0 防抖微震】跨越字母临界点触发 EFFECT_TICK / CLOCK_TICK，12ms 最小窗口防止马达粘连；
 * 8. 【v2.6.0 主列表联动】通过 OnWaveScrubListener 向外同步擦拭状态，驱动主列表横向内推与回弹。
 */
final class NiagaraWaveView extends View {

    interface Listener {
        void onWaveScrub(int index, char letter, float normX, float normY, float strength, boolean pullArmed);
        void onWaveRelease(int index, char letter, boolean triggerPullLaunch);
        void onWaveBackSwipe();
    }

    /** 波浪擦拭状态监听：供主列表做高斯横向内推联动（仅回调主线程）。 */
    interface OnWaveScrubListener {
        void onWaveScrubStart();
        /**
         * @param currentLetter  当前命中的字母/符号
         * @param progress       触点在导轨内的归一化纵向进度 [0,1]
         * @param touchScreenY   触点的屏幕绝对 Y 坐标（px）
         */
        void onWaveScrub(char currentLetter, float progress, float touchScreenY);
        void onWaveScrubEnd();
    }

    /** 高斯波峰最大横向偏移 (dp) 与标准差 σ (dp)。 */
    private static final float MAX_WAVE_PEAK_DP = 46f;
    private static final float WAVE_SIGMA_DP = 48f;
    /** 系统手势排除区：跟随时触点上下各 90dp（总高 180dp ≤ 系统 200dp 上限），常驻区 160dp。 */
    private static final float EXCLUSION_TRACK_HALF_DP = 90f;
    private static final float EXCLUSION_IDLE_HEIGHT_DP = 160f;
    private static final long EXCLUSION_RESET_DELAY_MS = 300L;
    /** 触觉防抖：相邻两次微震的最小间隔。 */
    private static final long HAPTIC_MIN_INTERVAL_MS = 12L;

    private String symbols;
    private final float dp;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private AuroraTheme theme;
    private Typeface typeface = Typeface.DEFAULT;
    private Set<Character> availableLetters = Collections.emptySet();
    private Map<Character, String> topAppPreview = Collections.emptyMap();

    private boolean alignLeft = true;
    private boolean backSwipePreferred = false;
    private boolean pullLaunchEnabled = true;
    private Listener listener;
    private OnWaveScrubListener scrubListener;
    private boolean scrubActive = false;
    private long lastHapticUptimeMs = 0L;
    private Vibrator vibrator;
    private final int[] screenLoc = new int[2];

    private float touchY = -1f;
    private float smoothY = -1f;
    private float velocityY = 0f;
    private float pullDepthPx = 0f;
    private float waveStrength = 0f;
    private int activeIdx = -1;
    private boolean pullArmed = false;
    private float pullExpandFrac = 0f;
    private boolean idleDotsMode = true;
    private boolean silentAtRest = false;
    private ValueAnimator releaseAnim;
    private ValueAnimator pullExpandAnim;

    private float downY = 0f;
    private float downDepthPx = 0f;
    private long downTimeMs = 0L;
    private boolean waveEngaged = false;
    private boolean backSwipeHandled = false;
    private long cooldownUntilMs = 0L;

    private final Runnable resetExclusionTask = new Runnable() {
        @Override
        public void run() {
            restoreIdleExclusions();
        }
    };

    private final Runnable delayedEngageTask = new Runnable() {
        @Override
        public void run() {
            if (!waveEngaged && !backSwipeHandled && touchY >= 0 && SystemClock.uptimeMillis() >= cooldownUntilMs) {
                engageWaveNow(touchY, pullDepthPx, alignLeft);
            }
        }
    };

    NiagaraWaveView(Context ctx, String symbols) {
        super(ctx);
        this.symbols = (symbols == null || symbols.isEmpty()) ? "★" : symbols;
        this.dp = ctx.getResources().getDisplayMetrics().density;
        letterPaint.setTextAlign(Paint.Align.CENTER);
        crestTextPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** 动态更新自适应字母表序列（如仅包含已安装应用实际存在的首字母）。 */
    void setSymbols(String activeSymbols) {
        String next = (activeSymbols == null || activeSymbols.isEmpty()) ? "★" : activeSymbols;
        if (next.equals(this.symbols)) return;
        this.symbols = next;
        if (activeIdx >= this.symbols.length()) {
            activeIdx = this.symbols.length() - 1;
        }
        invalidate();
    }

    String getSymbols() {
        return symbols;
    }

    void setPullLaunchEnabled(boolean enabled) {
        this.pullLaunchEnabled = enabled;
        if (!enabled && pullArmed) {
            pullArmed = false;
            animatePullExpand(0f);
        }
    }

    void bindStyle(AuroraTheme theme, Typeface tf) {
        this.theme = theme;
        this.typeface = tf != null ? tf : Typeface.DEFAULT;
        invalidate();
    }

    void setAlignLeft(boolean left) {
        this.alignLeft = left;
        invalidate();
    }

    /** 原生 Niagara 风格：静默态渲染极简圆点，触碰滑动时平滑形变为波浪字母。 */
    void setIdleDotsMode(boolean dots) {
        if (this.idleDotsMode != dots) {
            this.idleDotsMode = dots;
            invalidate();
        }
    }

    /** 静止态完全隐形、仅在触碰本侧边缘时在本侧展开镜像波浪（用于左手边缘导轨）。 */
    void setSilentAtRest(boolean silent) {
        if (this.silentAtRest != silent) {
            this.silentAtRest = silent;
            if (Build.VERSION.SDK_INT >= 29 && isLaidOut()) {
                updateSystemGestureExclusion(-1f, false);
            }
            invalidate();
        }
    }

    void setBackSwipePreferred(boolean preferred) {
        this.backSwipePreferred = preferred;
        if (Build.VERSION.SDK_INT >= 29 && isLaidOut()) {
            restoreIdleExclusions();
        }
    }

    void cancelAndCooldown(long durationMs) {
        cooldownUntilMs = SystemClock.uptimeMillis() + durationMs;
        removeCallbacks(delayedEngageTask);
        removeCallbacks(resetExclusionTask);
        dispatchScrubEnd();
        waveEngaged = false;
        backSwipeHandled = false;
        pullArmed = false;
        pullDepthPx = 0f;
        activeIdx = -1;
        touchY = -1f;
        smoothY = -1f;
        velocityY = 0f;
        restoreIdleExclusions();
        if (releaseAnim != null) releaseAnim.cancel();
        if (pullExpandAnim != null) pullExpandAnim.cancel();
        pullExpandFrac = 0f;
        waveStrength = 0f;
        invalidate();
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    void setOnWaveScrubListener(OnWaveScrubListener l) {
        this.scrubListener = l;
    }

    private void dispatchScrubEnd() {
        if (scrubActive) {
            scrubActive = false;
            if (scrubListener != null) scrubListener.onWaveScrubEnd();
        }
    }

    private float toScreenY(float localY) {
        getLocationOnScreen(screenLoc);
        return screenLoc[1] + localY;
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

    /** 高斯正态包络：f = exp(-(Δy)^2 / (2σ^2))，返回 [0,1]。 */
    private float calculateGaussianFactor(float itemCenterY, float touchY, float sigma) {
        float d = itemCenterY - touchY;
        return (float) Math.exp(-(d * d) / (2.0f * sigma * sigma));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= 29) {
            updateSystemGestureExclusion(-1f, false);
        }
    }

    /**
     * 更新系统边缘手势排除区。
     * 跟随态：以触点 Y 为中心上下各 90dp（总高 180dp ≤ 200dp 系统上限），到边界时整体平移以保持高度；
     * 常驻态：覆盖拇指黄金停留区（约 45% 高度处起 160dp）。坐标系为本 View 局部坐标。
     */
    void updateSystemGestureExclusion(float touchLocalY, boolean isTracking) {
        if (Build.VERSION.SDK_INT < 29) return;
        int h = getHeight();
        int w = getWidth();
        if (w <= 0 || h <= 0) return;
        if (silentAtRest && !isTracking) {
            setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }
        int rw = Math.min(w, (int) railWidth());
        int span = (int) Math.min(h, (isTracking ? EXCLUSION_TRACK_HALF_DP * 2f : EXCLUSION_IDLE_HEIGHT_DP) * dp);
        int maxTop = Math.max(0, h - span);
        int topY = isTracking
                ? Math.round(touchLocalY - EXCLUSION_TRACK_HALF_DP * dp)
                : (int) (h * 0.45f);
        topY = Math.max(0, Math.min(maxTop, topY));
        Rect r = alignLeft
                ? new Rect(0, topY, rw, topY + span)
                : new Rect(w - rw, topY, w, topY + span);
        setSystemGestureExclusionRects(Collections.singletonList(r));
    }

    private void applyDynamicExclusion(float currentY, boolean fromLeft) {
        removeCallbacks(resetExclusionTask);
        updateSystemGestureExclusion(currentY, true);
    }

    private void restoreIdleExclusions() {
        updateSystemGestureExclusion(-1f, false);
    }

    /** 松手后延时 300ms 平滑回到常驻排除区，避免边缘手势死锁或瞬间放开造成误触返回。 */
    private void scheduleExclusionReset() {
        removeCallbacks(resetExclusionTask);
        postDelayed(resetExclusionTask, EXCLUSION_RESET_DELAY_MS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (theme == null || symbols == null || symbols.isEmpty()) return;
        if (silentAtRest && waveStrength <= 0.01f) return;

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
                // 过阻尼弹簧跟随：v' = 0.20v + 0.30Δ，特征根均为实数（≈0.5/0.4），无过冲、无抖动，跟手且顺滑
                float diff = touchY - smoothY;
                velocityY = velocityY * 0.20f + diff * 0.30f;
                smoothY += velocityY;
                if (Math.abs(diff) > 0.35f || Math.abs(velocityY) > 0.25f) {
                    postInvalidateOnAnimation();
                } else {
                    smoothY = touchY;
                    velocityY = 0f;
                }
            }
        }

        float sigma = WAVE_SIGMA_DP * dp;
        float extraPull = Math.min(20f * dp, Math.max(0f, pullDepthPx - railWidth()) * 0.24f);
        float maxShiftPx = (MAX_WAVE_PEAK_DP * dp + extraPull) * waveStrength;
        boolean waving = waveStrength > 0.01f && smoothY >= 0;

        boolean useDots = idleDotsMode && !silentAtRest;
        float dotVis = useDots ? Math.max(0f, 1f - waveStrength * 2.4f) : 0f;
        float letterVis = useDots ? Math.min(1f, waveStrength * 1.9f) : 1f;

        for (int i = 0; i < symbols.length(); i++) {
            char ch = symbols.charAt(i);
            float drawY = slot * i + slot * 0.5f;
            float factor = waving ? calculateGaussianFactor(drawY, smoothY, sigma) : 0f;
            float focus = Math.min(1f, factor * waveStrength);
            float drawX = baseX + dir * maxShiftPx * factor;
            boolean isActive = (i == activeIdx && waveStrength > 0.10f);

            boolean hasApp = ch == '★' || availableLetters.contains(ch);

            if (dotVis > 0.01f) {
                int baseDotCol = (ch == '★')
                        ? theme.accent
                        : (hasApp ? theme.textPrimary : theme.textSecondary);
                int dotAlpha = hasApp ? (theme.isLight ? 0x9E : 0xA8) : (theme.isLight ? 0x2C : 0x30);
                dotPaint.setColor(baseDotCol);
                dotPaint.setAlpha(Math.round(dotAlpha * dotVis));
                float dotR = (ch == '★') ? (2.25f * dp) : (hasApp ? 1.65f * dp : 0.90f * dp);
                canvas.drawCircle(drawX, drawY, dotR, dotPaint);
            }

            if (letterVis <= 0.01f) continue;

            int color;
            if (isActive) {
                color = theme.accent;
            } else if (hasApp) {
                int alpha = waveStrength > 0.1f ? 0xEE000000 : (theme.isLight ? 0xCC000000 : 0xB8000000);
                color = (theme.textPrimary & 0x00FFFFFF) | alpha;
            } else {
                color = (theme.textSecondary & 0x00FFFFFF) | (waveStrength > 0.1f ? 0x58000000 : 0x30000000);
            }
            // 原生 Niagara 纯字母流体涌出：焦点字母沿高斯曲线顺滑放大至 1.85x + 主题高亮 + 微投影，无大圆球遮挡
            float alphaMul = (1f - 0.55f * waveStrength * (1f - factor)) * letterVis;
            letterPaint.setColor(color);
            letterPaint.setAlpha(Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alphaMul))));
            letterPaint.setTextSize(10.5f * dp * (1f + 0.85f * focus));
            letterPaint.setTypeface(isActive ? Typeface.create(typeface, Typeface.BOLD) : typeface);
            if (isActive) {
                letterPaint.setShadowLayer(3.2f * dp, 0f, 1.0f * dp, theme.isLight ? 0x24000000 : 0x55000000);
            } else {
                letterPaint.clearShadowLayer();
            }

            float baseline = drawY - (letterPaint.descent() + letterPaint.ascent()) * 0.5f;
            canvas.drawText(String.valueOf(ch), drawX, baseline, letterPaint);
        }
        letterPaint.clearShadowLayer();

        // 仅在内拉直达触发时展开轻盈的「直达应用名」提示胶囊（去 Emoji）
        if (pullLaunchEnabled && pullExpandFrac > 0.02f && activeIdx >= 0 && activeIdx < symbols.length()
                && waveStrength > 0.08f && smoothY >= 0) {
            char ch = symbols.charAt(activeIdx);
            String previewApp = topAppPreview.get(ch);
            if (previewApp != null && !previewApp.isEmpty()) {
                float activeDrawY = slot * activeIdx + slot * 0.5f;
                float activeDrawX = baseX + dir * maxShiftPx * calculateGaussianFactor(activeDrawY, smoothY, sigma);
                float pillH = 30f * dp;
                float halfH = pillH * 0.5f;
                float anchorX = activeDrawX + dir * 20f * dp;
                float cy = Math.max(halfH + 8f * dp, Math.min(h - halfH - 8f * dp, smoothY - 2f * dp));

                String launchLabel = "→ " + previewApp;
                crestTextPaint.setTextSize(12.8f * dp);
                crestTextPaint.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                float tw = crestTextPaint.measureText(launchLabel);
                float fullPillW = Math.max(pillH, Math.min(w - 58f * dp, tw + 22f * dp));
                float curW = pillH + (fullPillW - pillH) * pullExpandFrac;

                if (alignLeft) {
                    float left = Math.min(w - curW - 8f * dp, anchorX);
                    pillRect.set(left, cy - halfH, left + curW, cy + halfH);
                } else {
                    float right = Math.max(curW + 8f * dp, anchorX);
                    pillRect.set(right - curW, cy - halfH, right, cy + halfH);
                }

                crestCirclePaint.setColor(theme.accent);
                crestCirclePaint.setAlpha(Math.round(235f * pullExpandFrac));
                crestCirclePaint.setShadowLayer(4f * dp, 0f, 1.5f * dp, 0x26000000);
                canvas.drawRoundRect(pillRect, halfH, halfH, crestCirclePaint);
                crestCirclePaint.setAlpha(255);

                if (pullExpandFrac > 0.35f) {
                    crestTextPaint.setColor(0xFFFFFFFF);
                    crestTextPaint.setAlpha(Math.round(255f * Math.min(1f, (pullExpandFrac - 0.35f) / 0.5f)));
                    float by = pillRect.centerY() - (crestTextPaint.descent() + crestTextPaint.ascent()) * 0.5f;
                    canvas.drawText(launchLabel, pillRect.centerX(), by, crestTextPaint);
                    crestTextPaint.setAlpha(255);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (SystemClock.uptimeMillis() < cooldownUntilMs || symbols == null || symbols.isEmpty()) {
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
        if (!scrubActive) {
            scrubActive = true;
            if (scrubListener != null) scrubListener.onWaveScrubStart();
        }
        updateActiveLetterAndNotify(y, inwardDepthPx, fromLeft);
    }

    /**
     * 跨越字母临界点的精准微震：Android 10+ 优先 EFFECT_TICK，8.0/9.0 使用 10ms 微脉冲，
     * 其余降级 CLOCK_TICK；12ms 最小窗口防抖，系统关闭触感反馈时静默。
     */
    void performWaveHapticFeedback() {
        long now = SystemClock.uptimeMillis();
        if (now - lastHapticUptimeMs < HAPTIC_MIN_INTERVAL_MS) return;
        lastHapticUptimeMs = now;
        try {
            if (Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) {
                return;
            }
        } catch (Throwable ignored) {}
        try {
            if (vibrator == null) {
                vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(10L, 85));
                }
                return;
            }
        } catch (Throwable ignored) {}
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    /** 松手后波浪带物理阻尼回弹至静止态（贝塞尔 0.25,1,0.5,1）。 */
    void animateRebound() {
        animateStrength(0f);
    }

    private void updateActiveLetterAndNotify(float y, float inwardDepthPx, boolean fromLeft) {
        if (symbols == null || symbols.isEmpty()) return;
        touchY = Math.max(0f, Math.min(getHeight() - 1f, y));
        pullDepthPx = Math.max(0f, inwardDepthPx);

        float refY = smoothY >= 0 ? (smoothY * 0.5f + touchY * 0.5f) : touchY;
        int idx = (int) (refY / slotHeight());
        idx = Math.max(0, Math.min(symbols.length() - 1, idx));

        char ch = symbols.charAt(idx);
        boolean nowArmed = pullLaunchEnabled
                && pullDepthPx >= pullThreshold()
                && ch != '★'
                && topAppPreview.containsKey(ch);

        if (idx != activeIdx) {
            activeIdx = idx;
            if (pullArmed != nowArmed) {
                pullArmed = nowArmed;
                animatePullExpand(nowArmed ? 1f : 0f);
            }
            if (ch == '★' || availableLetters.contains(ch)) {
                performWaveHapticFeedback();
            }
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
        if (scrubListener != null && scrubActive && getHeight() > 0) {
            scrubListener.onWaveScrub(symbols.charAt(activeIdx), touchY / getHeight(), toScreenY(touchY));
        }
        postInvalidateOnAnimation();
    }

    boolean handleWaveMotion(int action, float y, float inwardDepthPx, boolean fromLeft) {
        if (SystemClock.uptimeMillis() < cooldownUntilMs || symbols == null || symbols.isEmpty()) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            removeCallbacks(delayedEngageTask);
            downY = y;
            downDepthPx = inwardDepthPx;
            downTimeMs = SystemClock.uptimeMillis();
            touchY = Math.max(0f, Math.min(getHeight() - 1f, y));
            pullDepthPx = Math.max(0f, inwardDepthPx);
            waveEngaged = false;
            backSwipeHandled = false;
            pullArmed = false;
            // 缩减停顿判定至 70ms，零迟滞切入流体字母波浪
            postDelayed(delayedEngageTask, 70L);
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

                // 2. 沿导轨垂直移动 > 4dp 立即唤起流体波浪，并动态排除系统侧滑返回误触
                if (dy > 4.0f * dp && dy >= dxInward * 0.75f) {
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
            scheduleExclusionReset();
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
            animateRebound();
            dispatchScrubEnd();
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
        else releaseAnim.setInterpolator(new PathInterpolator(0.25f, 1.0f, 0.5f, 1.0f));
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
