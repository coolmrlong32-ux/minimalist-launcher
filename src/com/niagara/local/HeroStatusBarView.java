package com.niagara.local;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * 首页顶部无边框极简状态栏 (HeroStatusBarView v2.3.1 · 方案 A)：
 * - 彻底去除毛玻璃外框与生硬竖线，与上方大时钟、日期/农历自然左对齐；
 * - 默认精简模式：仅保留「天气图标 + 温度天气 · 高质感矢量电池 + 电量%」，清爽通透；
 * - 可在设置中切换为完整模式（追加电池温度与可用运存），点击电量区域直达 2x2 本机仪表盘；
 * - 电池图标重绘为圆润比例（22x11dp），正常态与主题文字同色协调，充电态高亮翡翠绿并叠加矢量闪电；
 * - v2.6.0：彻底移除「·」字符分割线，改用 8dp 负空间间隙；次级文本降字号/降对比度（Medium 字重 + 半透明），
 *   与上方大号 Light 时钟形成清晰的字重层次；支持 applySearchFadeProgress 随搜索进度渐隐淡出。
 */
final class HeroStatusBarView extends LinearLayout {

    interface Callbacks {
        void onWeatherClick();
        void onDeviceStatusClick();
        void onLongPress();
    }

    private final float dp;
    private final AuroraTheme theme;
    private final Typeface typeface;
    private float textScale;

    private final WeatherGlyphView weatherIcon;
    private final TextView weatherLabel;
    private final View gap1;
    private final LinearLayout batterySeg;
    private final BatteryGlyphView batteryIcon;
    private final TextView batteryLabel;
    private final View gap2;
    private final TextView extraLabel;

    static final int MODE_SMART = 0;
    static final int MODE_ALWAYS_BATTERY = 1;
    static final int MODE_FULL = 2;
    static final String[] STATUS_MODE_NAMES = {
            "极简按需（充电/低电显电量）",
            "常显电量（天气 + 电量）",
            "完整监控（含温度与运存）"
    };
    private static final float GAP_DP = 8f;

    private String lastWeatherRaw = "";
    private boolean fullMode = false;
    private boolean smartBatteryOnly = true;
    private int lastBatteryPct = 80;
    private boolean lastCharging = false;
    private float lastBatteryTempC = 32.0f;
    private double lastRamAvailGb = 4.0;

    HeroStatusBarView(Context ctx, AuroraTheme theme, Typeface typeface, float textScale, int statusMode, final Callbacks cb) {
        super(ctx);
        this.dp = ctx.getResources().getDisplayMetrics().density;
        this.theme = theme;
        this.typeface = typeface;
        this.textScale = textScale;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setBackground(null);
        setPadding(dp(2), dp(2), dp(2), dp(2));
        if (cb != null) {
            setOnClickListener(v -> cb.onDeviceStatusClick());
            setOnLongClickListener(v -> {
                cb.onLongPress();
                return true;
            });
        }

        // 1. 天气交互段
        LinearLayout weatherSeg = makeSegment(cb != null ? cb::onWeatherClick : null, cb != null ? cb::onLongPress : null);
        weatherIcon = new WeatherGlyphView(ctx, theme);
        LayoutParams wiLp = new LayoutParams(dp(15), dp(15));
        wiLp.rightMargin = dp(5);
        weatherSeg.addView(weatherIcon, wiLp);

        weatherLabel = makeLabel(12.6f);
        weatherSeg.addView(weatherLabel, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(weatherSeg, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        gap1 = makeGap();
        addView(gap1, new LayoutParams(dp(GAP_DP), 1));

        // 2. 电量交互段（点击打开 2x2 本机实时状态仪表盘；原生极简态仅充电或低电 ≤20% 出现）
        batterySeg = makeSegment(cb != null ? cb::onDeviceStatusClick : null, cb != null ? cb::onLongPress : null);
        batteryIcon = new BatteryGlyphView(ctx, theme);
        LayoutParams biLp = new LayoutParams(dp(22), dp(11));
        biLp.rightMargin = dp(5);
        batterySeg.addView(batteryIcon, biLp);

        batteryLabel = makeLabel(12.6f);
        batterySeg.addView(batteryLabel, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(batterySeg, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        // 3. 可选扩展段（完整模式：温度 · 可用运存）
        gap2 = makeGap();
        addView(gap2, new LayoutParams(dp(GAP_DP), 1));

        LinearLayout extraSeg = makeSegment(cb != null ? cb::onDeviceStatusClick : null, cb != null ? cb::onLongPress : null);
        extraLabel = makeLabel(12.0f);
        extraSeg.addView(extraLabel, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(extraSeg, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        setStatusBarMode(statusMode);
        applyLabelColors();
        updateWeather("");
        updateMetrics(80, false, 32.0f, 4.0);
    }

    private boolean isHighContrastNeeded() {
        return theme.useSystemWallpaper;
    }

    private void applyLabelColors() {
        boolean hc = isHighContrastNeeded();
        int primary = theme.isLight ? (hc ? 0xE6141824 : 0xBD141824) : (hc ? 0xF2F8FAFC : 0xD2F1F5F9);
        int secondary = theme.isLight ? (hc ? 0xB8141824 : 0x8F141824) : (hc ? 0xD9E2E8F0 : 0xA2E2E8F0);
        weatherLabel.setTextColor(primary);
        batteryLabel.setTextColor(primary);
        extraLabel.setTextColor(secondary);
    }

    private Object weightSpan() {
        return isHighContrastNeeded() ? new StyleSpan(Typeface.BOLD) : new TypefaceSpan("sans-serif-medium");
    }

    void setStatusBarMode(int mode) {
        int clamped = Math.max(MODE_SMART, Math.min(MODE_FULL, mode));
        this.smartBatteryOnly = (clamped == MODE_SMART);
        this.fullMode = (clamped == MODE_FULL);
        applyExtraVisibility();
        renderMetricsText();
    }

    void setCentered(boolean centered) {
        setGravity(centered ? Gravity.CENTER : (Gravity.CENTER_VERTICAL | Gravity.START));
    }

    void setTextScale(float scale) {
        this.textScale = scale;
        weatherLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f * textScale);
        batteryLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f * textScale);
        extraLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.0f * textScale);
    }

    private void applyExtraVisibility() {
        boolean showBattery = fullMode || !smartBatteryOnly || lastCharging || lastBatteryPct <= 20;
        int batVis = showBattery ? View.VISIBLE : View.GONE;
        gap1.setVisibility(batVis);
        batterySeg.setVisibility(batVis);

        int extraVis = fullMode ? View.VISIBLE : View.GONE;
        gap2.setVisibility(extraVis);
        ((View) extraLabel.getParent()).setVisibility(extraVis);
    }

    private int dp(float v) {
        return Math.round(v * dp);
    }

    private LinearLayout makeSegment(final Runnable onClick, final Runnable onLongPress) {
        final LinearLayout seg = new LinearLayout(getContext());
        seg.setOrientation(HORIZONTAL);
        seg.setGravity(Gravity.CENTER_VERTICAL);
        seg.setPadding(0, 0, dp(2), 0);
        seg.setClickable(true);
        seg.setFocusable(true);
        seg.setOnTouchListener((v, event) -> {
            int act = event.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.68f).setDuration(75).start();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
            }
            return false;
        });
        if (onClick != null) {
            seg.setOnClickListener(v -> onClick.run());
        }
        if (onLongPress != null) {
            seg.setOnLongClickListener(v -> {
                onLongPress.run();
                return true;
            });
        }
        return seg;
    }

    /** 负空间间隙：取代原「·」字符分割线。 */
    private View makeGap() {
        return new View(getContext());
    }

    private TextView makeLabel(float spSize) {
        TextView tv = new TextView(getContext());
        tv.setTextColor(theme.isLight ? 0xB8141824 : 0xCCF1F5F9);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setLetterSpacing(0.015f);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize * textScale);
        tv.setFontFeatureSettings("tnum");
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);
        tv.setShadowLayer(theme.isLight ? dp(0.9f) : dp(2.0f), 0f, dp(0.6f), theme.isLight ? 0x16000000 : 0x48000000);
        return tv;
    }

    void updateWeather(String rawSummary) {
        lastWeatherRaw = rawSummary == null ? "" : rawSummary;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isDay = (hour >= 6 && hour < 18);

        if (rawSummary == null || rawSummary.trim().isEmpty()) {
            weatherIcon.setCondition(isDay ? WeatherGlyphView.COND_SUN : WeatherGlyphView.COND_MOON);
            SpannableStringBuilder sb = new SpannableStringBuilder("天气");
            int s = sb.length();
            sb.append(" ›");
            sb.setSpan(new ForegroundColorSpan(theme.textSecondary), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            weatherLabel.setText(sb);
            return;
        }

        String raw = rawSummary.trim();
        int cond = isDay ? WeatherGlyphView.COND_SUN : WeatherGlyphView.COND_MOON;
        if (raw.contains("雷")) cond = WeatherGlyphView.COND_STORM;
        else if (raw.contains("雪")) cond = WeatherGlyphView.COND_SNOW;
        else if (raw.contains("雨")) cond = WeatherGlyphView.COND_RAIN;
        else if (raw.contains("阴") || raw.contains("雾") || raw.contains("霾")) cond = WeatherGlyphView.COND_CLOUD;
        else if (raw.contains("多云")) cond = WeatherGlyphView.COND_PARTLY;
        else if (raw.contains("晴")) cond = isDay ? WeatherGlyphView.COND_SUN : WeatherGlyphView.COND_MOON;
        weatherIcon.setCondition(cond);

        String cleaned = raw.replaceAll("[\\p{So}\\p{Cs}\\uFE0F]", "").trim();
        cleaned = cleaned.replace("°C", "°").replace("℃", "°").replace(" · ", " ").replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) cleaned = "天气";

        int spIdx = cleaned.indexOf(' ');
        if (spIdx > 0 && spIdx < cleaned.length() - 1) {
            SpannableStringBuilder sb = new SpannableStringBuilder(cleaned);
            sb.setSpan(weightSpan(), 0, spIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(theme.isLight ? 0x8C141824 : 0x9EE2E8F0), spIdx, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(0.93f), spIdx, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            weatherLabel.setText(sb);
        } else {
            SpannableStringBuilder sb = new SpannableStringBuilder(cleaned);
            sb.setSpan(weightSpan(), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            weatherLabel.setText(sb);
        }
    }

    void updateMetrics(int batteryPct, boolean charging, float batteryTempC, double ramAvailGb) {
        this.lastBatteryPct = batteryPct;
        this.lastCharging = charging;
        this.lastBatteryTempC = batteryTempC;
        this.lastRamAvailGb = ramAvailGb;
        batteryIcon.setBattery(batteryPct, charging);
        applyExtraVisibility();
        renderMetricsText();
    }

    private void renderMetricsText() {
        String pctPart = String.format(Locale.ROOT, "%d%%", lastBatteryPct);
        SpannableStringBuilder bSb = new SpannableStringBuilder(pctPart);
        bSb.setSpan(weightSpan(), 0, pctPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (lastCharging) {
            bSb.setSpan(new ForegroundColorSpan(theme.isLight ? 0xFF059669 : 0xFF34D399),
                    0, pctPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (lastBatteryPct <= 15) {
            bSb.setSpan(new ForegroundColorSpan(0xFFEF4444), 0, pctPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        batteryLabel.setText(bSb);

        if (fullMode) {
            extraLabel.setText(String.format(Locale.ROOT, "%.1f°  %.1fG", lastBatteryTempC, lastRamAvailGb));
        }
    }

    // ---------- 矢量自绘：天气图标 ----------
    private static final class WeatherGlyphView extends View {
        static final int COND_SUN = 0;
        static final int COND_MOON = 1;
        static final int COND_PARTLY = 2;
        static final int COND_CLOUD = 3;
        static final int COND_RAIN = 4;
        static final int COND_SNOW = 5;
        static final int COND_STORM = 6;

        private final float dp;
        private final AuroraTheme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int condition = COND_SUN;

        WeatherGlyphView(Context ctx, AuroraTheme theme) {
            super(ctx);
            this.dp = ctx.getResources().getDisplayMetrics().density;
            this.theme = theme;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setCondition(int c) {
            if (this.condition != c) {
                this.condition = c;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float cx = w * 0.5f, cy = h * 0.5f;
            int sunColor = theme.isLight ? 0xFFD97706 : 0xFFFBBF24;
            int moonColor = theme.isLight ? 0xFF3B82F6 : 0xFF93C5FD;
            int cloudColor = theme.isLight ? 0xFF64748B : 0xFFCBD5E1;
            int rainColor = theme.isLight ? 0xFF0284C7 : 0xFF38BDF8;

            if (condition == COND_SUN) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(sunColor);
                canvas.drawCircle(cx, cy, 3.4f * dp, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.2f * dp);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float x1 = cx + (float) Math.cos(a) * 4.9f * dp;
                    float y1 = cy + (float) Math.sin(a) * 4.9f * dp;
                    float x2 = cx + (float) Math.cos(a) * 6.4f * dp;
                    float y2 = cy + (float) Math.sin(a) * 6.4f * dp;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            } else if (condition == COND_MOON) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(moonColor);
                path.reset();
                float r = 5.4f * dp;
                rect.set(cx - r, cy - r, cx + r, cy + r);
                path.addArc(rect, 35, 250);
                rect.set(cx - r * 0.55f, cy - r * 1.15f, cx + r * 1.15f, cy + r * 0.55f);
                path.arcTo(rect, 150, -145, false);
                path.close();
                canvas.drawPath(path, paint);
            } else {
                if (condition == COND_PARTLY) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(sunColor);
                    canvas.drawCircle(cx + 3.0f * dp, cy - 2.4f * dp, 2.7f * dp, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(cloudColor);
                float baseY = (condition == COND_RAIN || condition == COND_SNOW || condition == COND_STORM)
                        ? cy - 0.5f * dp : cy + 1.1f * dp;
                canvas.drawCircle(cx - 2.6f * dp, baseY, 2.8f * dp, paint);
                canvas.drawCircle(cx + 0.5f * dp, baseY - 1.4f * dp, 3.4f * dp, paint);
                canvas.drawCircle(cx + 3.4f * dp, baseY + 0.2f * dp, 2.4f * dp, paint);
                rect.set(cx - 4.9f * dp, baseY - 0.5f * dp, cx + 5.3f * dp, baseY + 2.8f * dp);
                canvas.drawRoundRect(rect, 1.7f * dp, 1.7f * dp, paint);

                if (condition == COND_RAIN || condition == COND_STORM) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.2f * dp);
                    paint.setColor(condition == COND_STORM ? sunColor : rainColor);
                    canvas.drawLine(cx - 2.0f * dp, baseY + 4.0f * dp, cx - 2.8f * dp, baseY + 5.9f * dp, paint);
                    canvas.drawLine(cx + 1.3f * dp, baseY + 4.0f * dp, cx + 0.5f * dp, baseY + 5.9f * dp, paint);
                } else if (condition == COND_SNOW) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(rainColor);
                    canvas.drawCircle(cx - 1.9f * dp, baseY + 4.9f * dp, 1.05f * dp, paint);
                    canvas.drawCircle(cx + 1.7f * dp, baseY + 4.9f * dp, 1.05f * dp, paint);
                }
            }
        }
    }

    // ---------- 矢量自绘：极简高质感电池图标（iOS/Niagara 比例 + 充电闪电） ----------
    private static final class BatteryGlyphView extends View {
        private final float dp;
        private final AuroraTheme theme;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bodyRect = new RectF();
        private final RectF fillRect = new RectF();
        private final Path boltPath = new Path();
        private int pct = 80;
        private boolean charging = false;

        BatteryGlyphView(Context ctx, AuroraTheme theme) {
            super(ctx);
            this.dp = ctx.getResources().getDisplayMetrics().density;
            this.theme = theme;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.1f * dp);
            fillPaint.setStyle(Paint.Style.FILL);
            boltPaint.setStyle(Paint.Style.FILL);
        }

        void setBattery(int pct, boolean charging) {
            int clamped = Math.max(0, Math.min(100, pct));
            if (this.pct != clamped || this.charging != charging) {
                this.pct = clamped;
                this.charging = charging;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float nubW = 1.6f * dp;
            float pad = 0.7f * dp;
            float bodyRight = w - nubW - 1.1f * dp;

            // 外框：柔和半透明，充电或低电时与主色呼应
            int frameCol;
            if (charging) {
                frameCol = theme.isLight ? 0xAA059669 : 0xBB34D399;
            } else if (pct <= 15) {
                frameCol = 0xCCEF4444;
            } else {
                frameCol = theme.isLight ? 0x70141824 : 0x80FFFFFF;
            }
            strokePaint.setColor(frameCol);
            bodyRect.set(pad, pad, bodyRight, h - pad);
            canvas.drawRoundRect(bodyRect, 2.8f * dp, 2.8f * dp, strokePaint);

            // 电池正极微圆角凸点
            fillPaint.setColor(frameCol);
            float nubH = h * 0.38f;
            float nubTop = (h - nubH) * 0.5f;
            fillRect.set(bodyRight + 0.7f * dp, nubTop, w - 0.2f * dp, nubTop + nubH);
            canvas.drawRoundRect(fillRect, 0.9f * dp, 0.9f * dp, fillPaint);

            // 内部电量填充条：正常态用克制单色，低电红/琥珀，充电翡翠绿
            int levelColor;
            if (charging) {
                levelColor = theme.isLight ? 0xFF10B981 : 0xFF34D399;
            } else if (pct <= 15) {
                levelColor = 0xFFEF4444;
            } else if (pct <= 20) {
                levelColor = 0xFFF59E0B;
            } else {
                levelColor = theme.isLight ? 0xBB141824 : 0xDDF8FAFC;
            }
            fillPaint.setColor(levelColor);
            float innerPad = 1.75f * dp;
            float maxFillW = Math.max(1f, (bodyRight - pad) - innerPad * 2f);
            float fillW = Math.max(2.0f * dp, maxFillW * (pct / 100f));
            fillRect.set(pad + innerPad, pad + innerPad, pad + innerPad + fillW, h - pad - innerPad);
            canvas.drawRoundRect(fillRect, 1.5f * dp, 1.5f * dp, fillPaint);

            // 充电态：在电池中心叠加精致矢量闪电
            if (charging) {
                float cx = (pad + bodyRight) * 0.5f;
                float cy = h * 0.5f;
                boltPath.reset();
                boltPath.moveTo(cx + 0.9f * dp, cy - 3.6f * dp);
                boltPath.lineTo(cx - 2.0f * dp, cy + 0.4f * dp);
                boltPath.lineTo(cx + 0.1f * dp, cy + 0.4f * dp);
                boltPath.lineTo(cx - 0.9f * dp, cy + 3.6f * dp);
                boltPath.lineTo(cx + 2.0f * dp, cy - 0.4f * dp);
                boltPath.lineTo(cx - 0.1f * dp, cy - 0.4f * dp);
                boltPath.close();
                boltPaint.setColor(theme.isLight ? 0xFFFFFFFF : 0xFF06281E);
                canvas.drawPath(boltPath, boltPaint);
            }
        }
    }
}
