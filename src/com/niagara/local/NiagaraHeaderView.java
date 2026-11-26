package com.niagara.local;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

/**
 * 原生 Niagara 一体流顶部一览组件 (NiagaraHeaderView v2.8.0)：
 * - 作为 ListView HeaderView 融入首页纵向滚动流（Unified Stream），内容少时自动留白下沉至单手拇指区，
 *   内容多或贴满微件/播放音乐时与常用列表整屏丝滑同滚；
 * - 支持 3 种原生时钟排版风格（经典纤巧 / 现代粗体圆润 / 极简居中）；
 * - 日程、便签、无边框极简媒体条全面去 Emoji，改用纯矢量微图标与 2dp 细进度线；
 * - 提供 applySearchFadeProgress(p) 与搜索过渡 220ms 动画完整联动，由动画回调控制显隐。
 */
final class NiagaraHeaderView extends LinearLayout {

    static final int CLOCK_CLASSIC = 0;
    static final int CLOCK_BOLD = 1;
    static final int CLOCK_CENTERED = 2;

    static final String[] CLOCK_STYLE_NAMES = {
            "经典纤巧 · 左对齐",
            "现代圆润 · 粗体",
            "极简居中 · 壁纸构图"
    };

    interface Callbacks {
        void onClockClick();
        void onDateClick();
        void onWeatherClick();
        void onDeviceStatusClick();
        void onAgendaClick();
        void onNoteClick();
        void onMediaClick();
        void onMediaTransport(int which);
        void onLongPress();
    }

    private final float dp;
    private final AuroraTheme theme;
    private final Typeface typeface;
    private float textScale;
    private int clockStyle = CLOCK_CLASSIC;

    private final LinearLayout contentBox;
    private final TextClock mainClockView;
    private final TextView dateLunarView;
    private final HeroStatusBarView heroStatusBar;

    private final LinearLayout agendaRow;
    private final TextView agendaText;

    private final LinearLayout noteRow;
    private final TextView noteText;

    private final LinearLayout mediaBar;
    private final ImageView mediaArtView;
    private final TextView mediaTitleView;
    private final TextView mediaSubtitleView;
    private final HeaderGlyphView mediaPlayGlyph;
    private final View mediaProgressFill;
    private final TextView mediaTimeView;

    private final LinearLayout homeWidgetsBox;
    private final View gravitySpacer;

    NiagaraHeaderView(Context ctx, AuroraTheme theme, Typeface typeface, float textScale,
                      int clockStyle, boolean use24h, boolean statusFull, boolean smartBatteryOnly,
                      boolean heroHighContrast, final Callbacks cb) {
        super(ctx);
        this.dp = ctx.getResources().getDisplayMetrics().density;
        this.theme = theme;
        this.typeface = typeface != null ? typeface : Typeface.DEFAULT;
        this.textScale = textScale;
        this.clockStyle = clockStyle;

        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        contentBox = new LinearLayout(ctx);
        contentBox.setOrientation(VERTICAL);
        contentBox.setClipChildren(false);
        addView(contentBox, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. 主时钟
        mainClockView = new TextClock(ctx);
        setUse24Hour(use24h);
        mainClockView.setTextColor(theme.textPrimary);
        mainClockView.setFontFeatureSettings("tnum");
        mainClockView.setIncludeFontPadding(false);
        mainClockView.setShadowLayer(theme.isLight ? dp(1.4f) : dp(3.2f), 0f, dp(1f),
                theme.isLight ? 0x16000000 : 0x52000000);
        if (cb != null) {
            mainClockView.setOnClickListener(v -> cb.onClockClick());
            mainClockView.setOnLongClickListener(v -> { cb.onLongPress(); return true; });
        }
        contentBox.addView(mainClockView, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. 日期 · 星期 · 农历（自然字距，无冗余符号）
        dateLunarView = new TextView(ctx);
        dateLunarView.setTextColor(theme.isLight ? 0xD9141824 : 0xD9F1F5F9);
        dateLunarView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f * textScale);
        dateLunarView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateLunarView.setLetterSpacing(0.02f);
        dateLunarView.setPadding(dp(2), dp(5), dp(2), 0);
        dateLunarView.setShadowLayer(theme.isLight ? dp(1.0f) : dp(2.0f), 0f, dp(0.6f),
                theme.isLight ? 0x14000000 : 0x44000000);
        if (cb != null) {
            dateLunarView.setOnClickListener(v -> cb.onDateClick());
            dateLunarView.setOnLongClickListener(v -> { cb.onLongPress(); return true; });
        }
        contentBox.addView(dateLunarView, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 3. 无边框极简天气 + 按需电量状态条
        heroStatusBar = new HeroStatusBarView(ctx, theme, this.typeface, textScale, statusFull,
                new HeroStatusBarView.Callbacks() {
                    @Override public void onWeatherClick() { if (cb != null) cb.onWeatherClick(); }
                    @Override public void onDeviceStatusClick() { if (cb != null) cb.onDeviceStatusClick(); }
                    @Override public void onLongPress() { if (cb != null) cb.onLongPress(); }
                });
        heroStatusBar.setSmartBatteryOnly(smartBatteryOnly);
        heroStatusBar.setAestheticStyle(heroHighContrast
                ? HeroStatusBarView.STYLE_HIGH_CONTRAST : HeroStatusBarView.STYLE_AIRY);
        LayoutParams hsLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsLp.topMargin = dp(4);
        contentBox.addView(heroStatusBar, hsLp);

        // 4. 矢量日程微行（去 Emoji，无厚框）
        agendaRow = new LinearLayout(ctx);
        agendaRow.setOrientation(HORIZONTAL);
        agendaRow.setGravity(Gravity.CENTER_VERTICAL);
        agendaRow.setPadding(dp(2), dp(5), dp(4), dp(2));
        agendaRow.setVisibility(View.GONE);
        HeaderGlyphView agendaIc = new HeaderGlyphView(ctx, HeaderGlyphView.GLYPH_CALENDAR,
                theme.isLight ? 0xAA141824 : 0xCCF1F5F9, dp);
        LayoutParams aicLp = new LayoutParams(dp(14), dp(14));
        aicLp.rightMargin = dp(6);
        agendaRow.addView(agendaIc, aicLp);
        agendaText = makeSubText(ctx, 12.6f, theme.isLight ? 0xCC141824 : 0xDCF1F5F9, false);
        agendaRow.addView(agendaText, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (cb != null) {
            agendaRow.setOnClickListener(v -> cb.onAgendaClick());
            agendaRow.setOnLongClickListener(v -> { cb.onLongPress(); return true; });
        }
        LayoutParams agLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        agLp.topMargin = dp(2);
        contentBox.addView(agendaRow, agLp);

        // 5. 矢量便签微行（去 Emoji，无厚框）
        noteRow = new LinearLayout(ctx);
        noteRow.setOrientation(HORIZONTAL);
        noteRow.setGravity(Gravity.CENTER_VERTICAL);
        noteRow.setPadding(dp(2), dp(4), dp(4), dp(2));
        noteRow.setVisibility(View.GONE);
        HeaderGlyphView noteIc = new HeaderGlyphView(ctx, HeaderGlyphView.GLYPH_NOTE, theme.accent, dp);
        LayoutParams nicLp = new LayoutParams(dp(14), dp(14));
        nicLp.rightMargin = dp(6);
        noteRow.addView(noteIc, nicLp);
        noteText = makeSubText(ctx, 12.6f, theme.isLight ? 0xCC141824 : 0xDCF1F5F9, false);
        noteRow.addView(noteText, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (cb != null) {
            noteRow.setOnClickListener(v -> cb.onNoteClick());
            noteRow.setOnLongClickListener(v -> { cb.onLongPress(); return true; });
        }
        LayoutParams ntLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ntLp.topMargin = dp(2);
        contentBox.addView(noteRow, ntLp);

        // 6. 原生极简无边框媒体微条（圆角小封面 + 矢量播放控制 + 2dp 细进度线）
        mediaBar = new LinearLayout(ctx);
        mediaBar.setOrientation(VERTICAL);
        mediaBar.setPadding(dp(2), dp(8), dp(2), dp(4));
        mediaBar.setVisibility(View.GONE);

        LinearLayout mediaTop = new LinearLayout(ctx);
        mediaTop.setOrientation(HORIZONTAL);
        mediaTop.setGravity(Gravity.CENTER_VERTICAL);

        mediaArtView = new ImageView(ctx);
        mediaArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtView.setVisibility(View.GONE);
        mediaArtView.setClipToOutline(true);
        mediaArtView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
            }
        });
        LayoutParams artLp = new LayoutParams(dp(36), dp(36));
        artLp.rightMargin = dp(10);
        mediaTop.addView(mediaArtView, artLp);

        LinearLayout mediaMeta = new LinearLayout(ctx);
        mediaMeta.setOrientation(VERTICAL);
        mediaTitleView = makeSubText(ctx, 13.8f, theme.textPrimary, true);
        mediaSubtitleView = makeSubText(ctx, 11.6f, theme.textSecondary, false);
        mediaMeta.addView(mediaTitleView);
        mediaMeta.addView(mediaSubtitleView);
        mediaTop.addView(mediaMeta, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int ctrlColor = theme.isLight ? 0xD9141824 : 0xEDF8FAFC;
        mediaTop.addView(makeMediaBtn(ctx, HeaderGlyphView.GLYPH_PREV, ctrlColor, () -> {
            if (cb != null) cb.onMediaTransport(0);
        }));
        FrameLayout playWrap = makeMediaBtn(ctx, HeaderGlyphView.GLYPH_PLAY, theme.accent, () -> {
            if (cb != null) cb.onMediaTransport(1);
        });
        mediaPlayGlyph = (HeaderGlyphView) playWrap.getChildAt(0);
        mediaTop.addView(playWrap);
        mediaTop.addView(makeMediaBtn(ctx, HeaderGlyphView.GLYPH_NEXT, ctrlColor, () -> {
            if (cb != null) cb.onMediaTransport(2);
        }));

        mediaBar.addView(mediaTop, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2dp 细进度线
        FrameLayout track = new FrameLayout(ctx);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(theme.isLight ? 0x1E141824 : 0x2AFFFFFF);
        trackBg.setCornerRadius(dp(1f));
        track.setBackground(trackBg);

        mediaProgressFill = new View(ctx);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(theme.accent);
        fillBg.setCornerRadius(dp(1f));
        mediaProgressFill.setBackground(fillBg);
        mediaProgressFill.setPivotX(0f);
        track.addView(mediaProgressFill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        LayoutParams trLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        trLp.topMargin = dp(7);
        mediaBar.addView(track, trLp);

        mediaTimeView = makeSubText(ctx, 10.5f, theme.textSecondary, false);
        mediaTimeView.setFontFeatureSettings("tnum");
        mediaTimeView.setPadding(0, dp(3), 0, 0);
        mediaTimeView.setVisibility(View.GONE);
        mediaBar.addView(mediaTimeView);

        if (cb != null) {
            mediaBar.setOnClickListener(v -> cb.onMediaClick());
            mediaBar.setOnLongClickListener(v -> { cb.onLongPress(); return true; });
        }
        LayoutParams mbLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.topMargin = dp(4);
        contentBox.addView(mediaBar, mbLp);

        // 7. 原生桌面微件宿主容器
        homeWidgetsBox = new LinearLayout(ctx);
        homeWidgetsBox.setOrientation(VERTICAL);
        homeWidgetsBox.setVisibility(View.GONE);
        LayoutParams hwLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hwLp.topMargin = dp(4);
        contentBox.addView(homeWidgetsBox, hwLp);

        // 8. 单手黄金区呼吸留白垫片（内容少时自然下沉常用列表，贴微件或多应用时自动收紧同滚）
        gravitySpacer = new View(ctx);
        addView(gravitySpacer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)));

        applyClockStyle(clockStyle);
    }

    private FrameLayout makeMediaBtn(Context ctx, int glyphType, int color, final Runnable onClick) {
        FrameLayout wrap = new FrameLayout(ctx);
        HeaderGlyphView gv = new HeaderGlyphView(ctx, glyphType, color, dp);
        wrap.addView(gv, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        wrap.setLayoutParams(new LayoutParams(dp(34), dp(34)));
        wrap.setClickable(true);
        wrap.setOnTouchListener((v, ev) -> {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.86f).scaleY(0.86f).alpha(0.65f).setDuration(70).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
            }
            return false;
        });
        wrap.setOnClickListener(v -> { if (onClick != null) onClick.run(); });
        return wrap;
    }

    private TextView makeSubText(Context ctx, float spSize, int color, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setTextColor(color);
        tv.setTypeface(bold ? Typeface.create(typeface, Typeface.BOLD) : typeface);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize * textScale);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setShadowLayer(theme.isLight ? dp(0.9f) : dp(2.0f), 0f, dp(0.6f),
                theme.isLight ? 0x16000000 : 0x48000000);
        return tv;
    }

    private int dp(float v) {
        return Math.round(v * dp);
    }

    void applyClockStyle(int style) {
        this.clockStyle = Math.max(CLOCK_CLASSIC, Math.min(CLOCK_CENTERED, style));
        boolean centered = (this.clockStyle == CLOCK_CENTERED);
        contentBox.setGravity(centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        heroStatusBar.setCentered(centered);

        LayoutParams cLp = (LayoutParams) mainClockView.getLayoutParams();
        cLp.gravity = centered ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        mainClockView.setLayoutParams(cLp);

        LayoutParams dLp = (LayoutParams) dateLunarView.getLayoutParams();
        dLp.gravity = centered ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        dateLunarView.setLayoutParams(dLp);
        dateLunarView.setGravity(centered ? Gravity.CENTER : Gravity.START);

        LayoutParams aLp = (LayoutParams) agendaRow.getLayoutParams();
        aLp.gravity = centered ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        agendaRow.setLayoutParams(aLp);

        LayoutParams nLp = (LayoutParams) noteRow.getLayoutParams();
        nLp.gravity = centered ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        noteRow.setLayoutParams(nLp);

        if (this.clockStyle == CLOCK_BOLD) {
            mainClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            mainClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f * textScale);
            mainClockView.setLetterSpacing(-0.01f);
        } else if (this.clockStyle == CLOCK_CENTERED) {
            mainClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mainClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f * textScale);
            mainClockView.setLetterSpacing(0.02f);
        } else {
            mainClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mainClockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f * textScale);
            mainClockView.setLetterSpacing(0.01f);
        }
    }

    void setUse24Hour(boolean use24h) {
        String fmt = use24h ? "HH:mm" : "h:mm";
        mainClockView.setFormat24Hour(fmt);
        mainClockView.setFormat12Hour(fmt);
    }

    void setSmartBatteryOnly(boolean smartOnly) {
        heroStatusBar.setSmartBatteryOnly(smartOnly);
    }

    void setStatusBarFullMode(boolean full) {
        heroStatusBar.setFullMode(full);
    }

    void setHeroHighContrast(boolean highContrast) {
        heroStatusBar.setAestheticStyle(highContrast
                ? HeroStatusBarView.STYLE_HIGH_CONTRAST : HeroStatusBarView.STYLE_AIRY);
    }

    void applyTextScale(float scale) {
        this.textScale = scale;
        applyClockStyle(this.clockStyle);
        dateLunarView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f * textScale);
        heroStatusBar.setTextScale(textScale);
        agendaText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f * textScale);
        noteText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.6f * textScale);
        mediaTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.8f * textScale);
        mediaSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.6f * textScale);
    }

    void updateDateLunar(String dateStr, String lunar) {
        String safeDate = dateStr == null ? "" : dateStr;
        String safeLunar = (lunar != null && !lunar.isEmpty()) ? lunar : "清风徐来";
        SpannableStringBuilder dsb = new SpannableStringBuilder(safeDate);
        dsb.append("   ");
        int lunarStart = dsb.length();
        dsb.append(safeLunar);
        dsb.setSpan(new TypefaceSpan("sans-serif"), lunarStart, dsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dsb.setSpan(new ForegroundColorSpan(theme.isLight ? 0x99141824 : 0x99F1F5F9),
                lunarStart, dsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        dateLunarView.setText(dsb);
    }

    void updateMetrics(int batteryPct, boolean charging, float batteryTempC, double ramAvailGb) {
        heroStatusBar.updateMetrics(batteryPct, charging, batteryTempC, ramAvailGb);
    }

    void updateWeather(String summary) {
        heroStatusBar.updateWeather(summary);
    }

    void updateAgenda(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            agendaRow.setVisibility(View.GONE);
        } else {
            agendaText.setText(summary.trim());
            agendaRow.setVisibility(View.VISIBLE);
        }
    }

    void updateStickyNote(String note) {
        if (note == null || note.trim().isEmpty()) {
            noteRow.setVisibility(View.GONE);
        } else {
            noteText.setText(note.trim());
            noteRow.setVisibility(View.VISIBLE);
        }
    }

    void updateMedia(Bitmap art, String title, String artist, boolean playing, long posMs, long durMs) {
        if (TextUtils.isEmpty(title)) {
            mediaBar.setVisibility(View.GONE);
            return;
        }
        if (art != null) {
            mediaArtView.setImageBitmap(art);
            mediaArtView.setVisibility(View.VISIBLE);
        } else {
            mediaArtView.setVisibility(View.GONE);
        }
        mediaTitleView.setText(title);
        mediaSubtitleView.setText(TextUtils.isEmpty(artist) ? "正在播放" : artist);
        mediaPlayGlyph.setGlyphType(playing ? HeaderGlyphView.GLYPH_PAUSE : HeaderGlyphView.GLYPH_PLAY);
        if (durMs > 0) {
            long clamped = Math.max(0L, Math.min(posMs, durMs));
            mediaProgressFill.setScaleX(clamped / (float) durMs);
            mediaTimeView.setText(formatMmSs(clamped) + " / " + formatMmSs(durMs));
            mediaTimeView.setVisibility(View.VISIBLE);
        } else {
            mediaProgressFill.setScaleX(0f);
            mediaTimeView.setVisibility(View.GONE);
        }
        mediaBar.setVisibility(View.VISIBLE);
    }

    private static String formatMmSs(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    /** 随搜索过渡进度 0→1 平滑上移淡出，不抢先设 GONE，让动画完整呈现。 */
    void applySearchFadeProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        contentBox.setAlpha(1f - p);
        contentBox.setTranslationY(-dp(28) * p);
    }

    void resetSearchFade() {
        contentBox.setAlpha(1f);
        contentBox.setTranslationY(0f);
    }

    LinearLayout getWidgetsContainer() {
        return homeWidgetsBox;
    }

    int getClockHeight() {
        return mainClockView.getHeight();
    }

    int measureContentHeightWithoutSpacer(int widthPx) {
        if (widthPx <= 0) return contentBox.getHeight();
        contentBox.measure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return contentBox.getMeasuredHeight();
    }

    void setGravitySpacerHeight(int px) {
        int clamped = Math.max(dp(10), px);
        ViewGroup.LayoutParams lp = gravitySpacer.getLayoutParams();
        if (lp != null && lp.height != clamped) {
            lp.height = clamped;
            gravitySpacer.setLayoutParams(lp);
        }
    }

    boolean isMediaVisible() {
        return mediaBar.getVisibility() == View.VISIBLE;
    }

    boolean hasPinnedWidgetsVisible() {
        return homeWidgetsBox.getVisibility() == View.VISIBLE && homeWidgetsBox.getChildCount() > 0;
    }

    // ---------- 顶部极简矢量图标（零 Emoji） ----------
    private static final class HeaderGlyphView extends View {
        static final int GLYPH_CALENDAR = 0;
        static final int GLYPH_NOTE = 1;
        static final int GLYPH_PREV = 2;
        static final int GLYPH_PLAY = 3;
        static final int GLYPH_PAUSE = 4;
        static final int GLYPH_NEXT = 5;

        private int glyphType;
        private final float dp;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        HeaderGlyphView(Context ctx, int type, int color, float dp) {
            super(ctx);
            this.glyphType = type;
            this.dp = dp;
            strokePaint.setColor(color);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.35f * dp);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            fillPaint.setColor(color);
            fillPaint.setStyle(Paint.Style.FILL);
        }

        void setGlyphType(int type) {
            if (this.glyphType != type) {
                this.glyphType = type;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w * 0.5f, cy = h * 0.5f;
            float u = Math.min(w, h) * 0.36f;

            switch (glyphType) {
                case GLYPH_CALENDAR:
                    rect.set(cx - u, cy - u * 0.78f, cx + u, cy + u * 0.92f);
                    c.drawRoundRect(rect, 2f * dp, 2f * dp, strokePaint);
                    c.drawLine(cx - u, cy - u * 0.22f, cx + u, cy - u * 0.22f, strokePaint);
                    c.drawLine(cx - u * 0.45f, cy - u * 1.05f, cx - u * 0.45f, cy - u * 0.55f, strokePaint);
                    c.drawLine(cx + u * 0.45f, cy - u * 1.05f, cx + u * 0.45f, cy - u * 0.55f, strokePaint);
                    break;
                case GLYPH_NOTE:
                    rect.set(cx - u * 0.85f, cy - u * 0.92f, cx + u * 0.85f, cy + u * 0.92f);
                    c.drawRoundRect(rect, 2f * dp, 2f * dp, strokePaint);
                    c.drawLine(cx - u * 0.45f, cy - u * 0.28f, cx + u * 0.45f, cy - u * 0.28f, strokePaint);
                    c.drawLine(cx - u * 0.45f, cy + u * 0.26f, cx + u * 0.22f, cy + u * 0.26f, strokePaint);
                    break;
                case GLYPH_PREV:
                    c.drawLine(cx - u * 0.72f, cy - u * 0.72f, cx - u * 0.72f, cy + u * 0.72f, strokePaint);
                    path.reset();
                    path.moveTo(cx + u * 0.72f, cy - u * 0.72f);
                    path.lineTo(cx - u * 0.35f, cy);
                    path.lineTo(cx + u * 0.72f, cy + u * 0.72f);
                    path.close();
                    c.drawPath(path, fillPaint);
                    break;
                case GLYPH_NEXT:
                    c.drawLine(cx + u * 0.72f, cy - u * 0.72f, cx + u * 0.72f, cy + u * 0.72f, strokePaint);
                    path.reset();
                    path.moveTo(cx - u * 0.72f, cy - u * 0.72f);
                    path.lineTo(cx + u * 0.35f, cy);
                    path.lineTo(cx - u * 0.72f, cy + u * 0.72f);
                    path.close();
                    c.drawPath(path, fillPaint);
                    break;
                case GLYPH_PAUSE:
                    rect.set(cx - u * 0.68f, cy - u * 0.78f, cx - u * 0.18f, cy + u * 0.78f);
                    c.drawRoundRect(rect, 1.2f * dp, 1.2f * dp, fillPaint);
                    rect.set(cx + u * 0.18f, cy - u * 0.78f, cx + u * 0.68f, cy + u * 0.78f);
                    c.drawRoundRect(rect, 1.2f * dp, 1.2f * dp, fillPaint);
                    break;
                case GLYPH_PLAY:
                default:
                    path.reset();
                    path.moveTo(cx - u * 0.55f, cy - u * 0.82f);
                    path.lineTo(cx + u * 0.82f, cy);
                    path.lineTo(cx - u * 0.55f, cy + u * 0.82f);
                    path.close();
                    c.drawPath(path, fillPaint);
                    break;
            }
        }
    }
}
