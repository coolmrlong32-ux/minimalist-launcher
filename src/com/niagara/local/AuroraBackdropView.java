package com.niagara.local;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;

/**
 * 日出/日落护眼极光与 ColorOS 风格锁屏/桌面同源壁纸渲染引擎 (v2.4.2)：
 * 1. 桌面背景与系统锁屏同源渲染 6 套自然光影壁纸（山岚叠翠、流光水韵、静谧沙丘、云海天际、宣纸墨韵、纯色柔光）；
 * 2. 夜间拉大 6 套色相与前后景对比度（深林青 / 幽海蓝 / 暖暮棕 / 星夜紫 / 黛墨灰 / 暖炭黑），护眼不刺眼且辨识度鲜明；
 * 3. 太阳/月亮与星云主视觉避开 ColorOS 锁屏顶部大时钟区域（移至中上部留白区），不被数字遮挡。
 */
final class AuroraBackdropView extends View {
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint searchDimPaint = new Paint();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final Rect dstRect = new Rect();

    private AuroraTheme theme;
    private RadialGradient[] unitOrbShaders;
    private RadialGradient unitGlowShader;
    private Bitmap desktopSceneBmp;
    private int desktopSceneStyle = -1;
    private int desktopScenePhase = -1;
    private int desktopSceneW = 0;
    private int desktopSceneH = 0;

    private int userDimPercent = 0;
    private boolean animateFlow = true;
    private boolean visible = true;

    private float touchParallaxX = 0.85f;
    private float touchParallaxY = 0.5f;
    private float touchGlowStrength = 0f;
    /** 搜索过渡进度：0.0（首屏）→ 1.0（完全搜索态）。 */
    private float searchProgress = 0f;
    private static final float SEARCH_BASE_SCALE = 1.045f;
    private static final float SEARCH_MAX_DIM = 0.35f;
    private final long t0 = SystemClock.uptimeMillis();
    private int cachedSolarPhase = WeatherSyncEngine.SOLAR_DAY;
    private long lastPhaseCheckMs = 0L;
    private final Runnable frameTick = this::postInvalidateOnAnimation;

    private static volatile Boolean cachedBright;
    private static volatile Integer cachedTopColor;
    private static volatile Integer cachedBotColor;
    private static volatile boolean syncingDayNightWall = false;

    private static final float[][] ORB_ANCHORS = {
        {0.18f, 0.20f, 0.72f},
        {0.84f, 0.42f, 0.68f},
        {0.26f, 0.78f, 0.74f},
        {0.82f, 0.84f, 0.64f}
    };

    AuroraBackdropView(Context ctx) {
        super(ctx);
    }

    void configure(AuroraTheme theme, int userDimPercent, boolean animateFlow) {
        this.theme = theme;
        this.userDimPercent = userDimPercent;
        this.animateFlow = animateFlow;
        this.lastPhaseCheckMs = 0L;
        removeCallbacks(frameTick);
        if (theme.useSystemWallpaper) {
            this.unitOrbShaders = null;
        } else {
            rebuildUnitShaders(theme);
        }
        rebuildGlowShader(theme);
        invalidate();
    }

    void updateDimPercent(int dimPercent) {
        this.userDimPercent = Math.max(0, Math.min(80, dimPercent));
        invalidate();
    }

    void updateAnimateFlow(boolean flow) {
        this.animateFlow = flow;
        removeCallbacks(frameTick);
        invalidate();
    }

    private void rebuildUnitShaders(AuroraTheme t) {
        if (t == null || t.auroraOrbs == null || t.auroraOrbs.length == 0) {
            unitOrbShaders = null;
            return;
        }
        int n = Math.min(t.auroraOrbs.length, ORB_ANCHORS.length);
        unitOrbShaders = new RadialGradient[n];
        for (int i = 0; i < n; i++) {
            int col = t.auroraOrbs[i];
            int cCenter = (col & 0x00FFFFFF) | (t.isLight ? 0x36000000 : 0x32000000);
            int cMid = (col & 0x00FFFFFF) | (t.isLight ? 0x16000000 : 0x14000000);
            int cEdge = col & 0x00FFFFFF;
            unitOrbShaders[i] = new RadialGradient(
                    0f, 0f, 1f,
                    new int[]{cCenter, cMid, cEdge},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP);
        }
    }

    private void rebuildGlowShader(AuroraTheme t) {
        if (t == null) return;
        int gc = (t.accent & 0x00FFFFFF) | 0x48000000;
        int gm = (t.accentBubble & 0x00FFFFFF) | 0x18000000;
        int ge = t.accent & 0x00FFFFFF;
        unitGlowShader = new RadialGradient(
                0f, 0f, 1f,
                new int[]{gc, gm, ge},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
    }

    void setScreenVisible(boolean v) {
        this.visible = v;
        if (v) {
            this.lastPhaseCheckMs = 0L;
            invalidate();
        } else {
            removeCallbacks(frameTick);
        }
    }

    private static boolean zoomMethodChecked = false;
    private static java.lang.reflect.Method wallpaperZoomOutMethod = null;

    /**
     * 搜索呼出景深联动：自绘壁纸由常态预放大 1.045 → 1.0 平滑退后（单次绘制零重影、不露黑边），
     * 并叠加最高 35% 压暗遮罩；系统壁纸模式在 Android 11+ (API 30+) 安全尝试原生 setWallpaperZoomOut。
     */
    void setSearchTransitionProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(p - searchProgress) < 0.0005f) return;
        searchProgress = p;
        if (theme != null && theme.useSystemWallpaper && android.os.Build.VERSION.SDK_INT >= 30 && getWindowToken() != null) {
            try {
                if (!zoomMethodChecked) {
                    zoomMethodChecked = true;
                    wallpaperZoomOutMethod = WallpaperManager.class.getMethod(
                            "setWallpaperZoomOut", android.os.IBinder.class, float.class);
                }
                if (wallpaperZoomOutMethod != null) {
                    wallpaperZoomOutMethod.invoke(WallpaperManager.getInstance(getContext()),
                            getWindowToken(), p * 0.65f);
                }
            } catch (Throwable ignored) {}
        }
        invalidate();
    }

    void onWaveInteract(float normX, float normY, float strength) {
        this.touchParallaxX = normX;
        this.touchParallaxY = normY;
        this.touchGlowStrength = strength;
        invalidate();
    }

    private Bitmap ensureDesktopSceneBitmap(int w, int h, int styleIdx, int phase) {
        int bw = Math.max(360, w / 2);
        int bh = Math.max(720, h / 2);
        if (desktopSceneBmp != null && !desktopSceneBmp.isRecycled()
                && desktopSceneStyle == styleIdx && desktopScenePhase == phase
                && desktopSceneW == bw && desktopSceneH == bh) {
            return desktopSceneBmp;
        }
        Bitmap old = desktopSceneBmp;
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        drawSceneByStyle(cv, bw, bh, styleIdx, phase);
        desktopSceneBmp = bmp;
        desktopSceneStyle = styleIdx;
        desktopScenePhase = phase;
        desktopSceneW = bw;
        desktopSceneH = bh;
        if (old != null && old != bmp && !old.isRecycled()) {
            old.recycle();
        }
        return bmp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || theme == null) return;

        long nowMs = SystemClock.uptimeMillis();
        if (lastPhaseCheckMs == 0L || (nowMs - lastPhaseCheckMs) > 30000L) {
            cachedSolarPhase = AuroraTheme.resolveSolarPhase(getContext());
            lastPhaseCheckMs = nowMs;
        }
        int phase = cachedSolarPhase;
        boolean sysNight = (phase == WeatherSyncEngine.SOLAR_NIGHT);

        if (theme.useSystemWallpaper) {
            int autoNightDim = (sysNight && isWallpaperBright(getContext())) ? 26 : 0;
            int totalDim = Math.max(userDimPercent, autoNightDim);
            if (totalDim > 0) {
                int alpha = Math.min(210, (int) (totalDim / 100f * 255));
                dimPaint.setColor((alpha << 24) | 0x060913);
                canvas.drawRect(0, 0, w, h, dimPaint);
            }
        } else {
            int styleIdx = Math.max(0, Math.min(5, theme.presetIdx));
            Bitmap scene = ensureDesktopSceneBitmap(w, h, styleIdx, phase);
            dstRect.set(0, 0, w, h);
            // 常态预放大 1.045 → 搜索态缩回 1.0：单次绘制实现平滑后退景深，彻底消除双层重影与露边
            float sc = SEARCH_BASE_SCALE - (SEARCH_BASE_SCALE - 1.0f) * searchProgress;
            int sceneSave = canvas.save();
            canvas.scale(sc, sc, w * 0.5f, h * 0.5f);
            canvas.drawBitmap(scene, null, dstRect, bmpPaint);

            // 叠加轻微流动极光光晕，让桌面保持柔和生命力
            if (unitOrbShaders != null && unitOrbShaders.length > 0 && animateFlow) {
                float sec = (SystemClock.uptimeMillis() - t0) / 1000f;
                float maxDim = Math.max(w, h);
                orbPaint.setAlpha(150);
                for (int i = 0; i < Math.min(2, unitOrbShaders.length); i++) {
                    float ax = ORB_ANCHORS[i][0] + 0.08f * (float) Math.sin(sec * (0.14f + i * 0.04f) + i * 1.6f);
                    float ay = ORB_ANCHORS[i][1] + 0.06f * (float) Math.cos(sec * (0.12f + i * 0.03f) + i * 2.1f);
                    ax += (touchParallaxX - 0.5f) * 0.05f * touchGlowStrength * (i % 2 == 0 ? 1 : -1);
                    ay += (touchParallaxY - 0.5f) * 0.06f * touchGlowStrength;

                    float cx = ax * w;
                    float cy = ay * h;
                    float rad = ORB_ANCHORS[i][2] * maxDim * 0.72f;
                    RadialGradient shader = unitOrbShaders[i];
                    shaderMatrix.setScale(rad, rad);
                    shaderMatrix.postTranslate(cx, cy);
                    shader.setLocalMatrix(shaderMatrix);
                    orbPaint.setShader(shader);
                    canvas.drawCircle(cx, cy, rad, orbPaint);
                }
                orbPaint.setAlpha(255);
            }

            if (sceneSave >= 0) canvas.restoreToCount(sceneSave);

            if (userDimPercent > 0) {
                int alpha = Math.min(200, (int) (userDimPercent / 100f * 255));
                dimPaint.setColor((alpha << 24) | (theme.isLight ? 0xFFF7F2E8 : 0x090D16));
                canvas.drawRect(0, 0, w, h, dimPaint);
            }
        }

        if (searchProgress > 0.001f) {
            searchDimPaint.setColor(Color.BLACK);
            searchDimPaint.setAlpha(Math.round(255f * SEARCH_MAX_DIM * searchProgress));
            canvas.drawRect(0, 0, w, h, searchDimPaint);
        }

        if (touchGlowStrength > 0.02f && unitGlowShader != null) {
            float gx = touchParallaxX * w;
            float gy = touchParallaxY * h;
            float gr = Math.max(w, h) * (0.28f + 0.10f * touchGlowStrength);
            shaderMatrix.setScale(gr, gr);
            shaderMatrix.postTranslate(gx, gy);
            unitGlowShader.setLocalMatrix(shaderMatrix);
            orbPaint.setShader(unitGlowShader);
            orbPaint.setAlpha(Math.min(255, Math.round(255f * Math.min(1f, touchGlowStrength))));
            canvas.drawCircle(gx, gy, gr, orbPaint);
            orbPaint.setAlpha(255);
        }

        // 仅在触摸波浪交互期间按帧刷新光晕，待机静止时 0 后台唤醒
        if (visible && animateFlow && !theme.useSystemWallpaper && unitOrbShaders != null && touchGlowStrength > 0.02f) {
            removeCallbacks(frameTick);
            postDelayed(frameTick, 28L);
        }
    }

    static void syncDayNightWallpaperIfNeeded(final Context ctx, final SharedPreferences prefs) {
        if (syncingDayNightWall) return;
        int style = prefs.getInt("wall_style_idx", -1);
        if (style < 0) {
            style = prefs.getInt("theme_idx", 0);
        }
        if (style < 0 || style > 5) style = 0;
        final int styleIdx = style;
        final int phase = AuroraTheme.resolveSolarPhase(ctx);
        int lastPhase = prefs.getInt("wall_applied_phase_v3", -1);
        if (phase == lastPhase) return;

        syncingDayNightWall = true;
        final Context appCtx = ctx.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Bitmap bmp = renderAuroraWallpaper(appCtx, styleIdx);
                saveAndApplySystemWallpaper(appCtx, bmp);
                prefs.edit().putInt("wall_applied_phase_v3", phase).apply();
            } catch (Throwable ignored) {
            } finally {
                syncingDayNightWall = false;
            }
        });
    }

    static Bitmap decodeDownsampledBitmap(Context ctx, Uri uri) throws Exception {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int reqW = Math.max(1080, dm.widthPixels);
        int reqH = Math.max(2400, dm.heightPixels);

        BitmapFactory.Options boundsOpt = new BitmapFactory.Options();
        boundsOpt.inJustDecodeBounds = true;
        try (InputStream in1 = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in1, null, boundsOpt);
        }
        int inSampleSize = 1;
        if (boundsOpt.outHeight > reqH || boundsOpt.outWidth > reqW) {
            final int halfH = boundsOpt.outHeight / 2;
            final int halfW = boundsOpt.outWidth / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2;
            }
        }
        BitmapFactory.Options decodeOpt = new BitmapFactory.Options();
        decodeOpt.inSampleSize = inSampleSize;
        decodeOpt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in2 = ctx.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in2, null, decodeOpt);
        }
    }

    static File wallCacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), "niagara_wall.jpg");
    }

    static boolean isWallpaperBright(Context ctx) {
        Boolean b = cachedBright;
        if (b != null) return b;
        return ctx.getSharedPreferences("niagara_local", Context.MODE_PRIVATE).getBoolean("wall_bright", true);
    }

    static int getWallpaperTopColor(Context ctx, int fallback) {
        Integer c = cachedTopColor;
        if (c != null) return c;
        return ctx.getSharedPreferences("niagara_local", Context.MODE_PRIVATE).getInt("wall_top_color", fallback);
    }

    static int getWallpaperBottomColor(Context ctx, int fallback) {
        Integer c = cachedBotColor;
        if (c != null) return c;
        return ctx.getSharedPreferences("niagara_local", Context.MODE_PRIVATE).getInt("wall_bot_color", fallback);
    }

    static void saveAndApplySystemWallpaper(Context ctx, Bitmap bmp) throws Exception {
        saveWallpaperCache(ctx, bmp);
        WallpaperManager wm = WallpaperManager.getInstance(ctx);
        boolean lockOnly = ctx.getSharedPreferences("niagara_local", Context.MODE_PRIVATE)
                .getBoolean("wall_lock_only_v1", false);
        try {
            if (!lockOnly) {
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM);
            }
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
        } catch (Throwable t) {
            wm.setBitmap(bmp, null, true, lockOnly ? WallpaperManager.FLAG_LOCK : (WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK));
        }
    }

    private static void saveWallpaperCache(Context ctx, Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return;
        boolean bright = computeBright(bmp);
        int topCol = computeRegionColor(bmp, 0.0f, 0.30f);
        int botCol = computeRegionColor(bmp, 0.70f, 1.0f);
        cachedBright = bright;
        cachedTopColor = topCol;
        cachedBotColor = botCol;
        ctx.getSharedPreferences("niagara_local", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("wall_bright", bright)
                .putInt("wall_top_color", topCol)
                .putInt("wall_bot_color", botCol)
                .apply();
        try (FileOutputStream out = new FileOutputStream(wallCacheFile(ctx))) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out);
        } catch (Throwable ignored) {}
    }

    static Bitmap renderAuroraWallpaper(Context ctx, int styleIdx) {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        int w = Math.min(dm.widthPixels, dm.heightPixels);
        int h = Math.max(dm.widthPixels, dm.heightPixels);
        if (w <= 0) { w = 1080; h = 2400; }

        int phase = AuroraTheme.resolveSolarPhase(ctx);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        drawSceneByStyle(cv, w, h, styleIdx, phase);
        return bmp;
    }

    static void drawSceneByStyle(Canvas cv, int w, int h, int styleIdx, int phase) {
        switch (styleIdx) {
            case 0:
                drawMountainRidgeWallpaper(cv, w, h, phase);
                break;
            case 1:
                drawAquamorphicWaveWallpaper(cv, w, h, phase);
                break;
            case 2:
                drawSilentDunesWallpaper(cv, w, h, phase);
                break;
            case 3:
                drawCloudHorizonWallpaper(cv, w, h, phase);
                break;
            case 4:
                drawOrientalInkWallpaper(cv, w, h, phase);
                break;
            default:
                drawEyeCareSolidWallpaper(cv, w, h, phase);
                break;
        }
    }

    private static void drawSkyGradient(Canvas cv, int w, int h, int top, int mid, int bot) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{top, mid, bot}, new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
    }

    private static void drawCelestialDisc(Canvas cv, float cx, float cy, float r, int coreColor, int haloColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, r * 3.8f,
                new int[]{haloColor, AuroraTheme.withAlpha(haloColor, 0x44), haloColor & 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r * 3.8f, p);
        p.setShader(null);
        p.setColor(coreColor);
        cv.drawCircle(cx, cy, r, p);
    }

    private static void drawSoftStars(Canvas cv, int w, int h, int seed, int count, int color) {
        Random rnd = new Random(seed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < count; i++) {
            float x = rnd.nextFloat() * w;
            float y = h * (0.04f + rnd.nextFloat() * 0.52f);
            float rad = 1.5f + rnd.nextFloat() * 2.4f;
            int alpha = 85 + rnd.nextInt(145);
            p.setColor(AuroraTheme.withAlpha(color, alpha));
            cv.drawCircle(x, y, rad, p);
        }
    }

    // 0: 山岚叠翠 · 大地（夜间深林松青 + 翡翠极光，避开时钟区）
    private static void drawMountainRidgeWallpaper(Canvas cv, int w, int h, int phase) {
        int skyTop, skyMid, skyBot, ridge1, ridge2, ridge3, sunCol, haloCol;
        float sunX = w * 0.72f, sunY;
        if (phase == WeatherSyncEngine.SOLAR_SUNRISE) {
            skyTop = 0xFFD4E5E2; skyMid = 0xFFF3DEC4; skyBot = 0xFFF7C39B;
            ridge1 = 0xFF6B938B; ridge2 = 0xFF476F68; ridge3 = 0xFF2B4B46;
            sunCol = 0xFFFFE7B8; haloCol = 0x99FAAC6B; sunY = h * 0.46f;
        } else if (phase == WeatherSyncEngine.SOLAR_SUNSET) {
            skyTop = 0xFF4F667A; skyMid = 0xFFD5967B; skyBot = 0xFFECB886;
            ridge1 = 0xFF655D70; ridge2 = 0xFF444257; ridge3 = 0xFF28293A;
            sunCol = 0xFFFFD392; haloCol = 0xAAEF7D52; sunY = h * 0.48f;
        } else if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            skyTop = 0xFF0C221E; skyMid = 0xFF153B34; skyBot = 0xFF235C4F;
            ridge1 = 0xFF2B6B5C; ridge2 = 0xFF1B4A40; ridge3 = 0xFF102E28;
            sunCol = 0xFFE6FFF5; haloCol = 0x664FD1A5; sunY = h * 0.43f;
        } else {
            skyTop = 0xFFD5EBE0; skyMid = 0xFFE5F2EB; skyBot = 0xFFF2EFE5;
            ridge1 = 0xFF83B39D; ridge2 = 0xFF568B75; ridge3 = 0xFF33604E;
            sunCol = 0xFFFFF8E8; haloCol = 0x66FCE4B0; sunY = h * 0.43f;
        }
        drawSkyGradient(cv, w, h, skyTop, skyMid, skyBot);
        if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            drawSoftStars(cv, w, h, 2026, 56, 0xFFD1FAE5);
        }
        drawCelestialDisc(cv, sunX, sunY, w * 0.052f, sunCol, haloCol);

        drawRidgeLayer(cv, w, h, 0.54f, 0.07f, 0.48f, 0.58f, ridge1);
        drawRidgeLayer(cv, w, h, 0.65f, 0.08f, 0.59f, 0.71f, ridge2);
        drawRidgeLayer(cv, w, h, 0.77f, 0.06f, 0.72f, 0.82f, ridge3);
    }

    private static void drawRidgeLayer(Canvas cv, int w, int h, float baseY, float amp, float peak1, float peak2, int color) {
        Path path = new Path();
        path.moveTo(0, h);
        path.lineTo(0, h * baseY);
        path.cubicTo(w * 0.25f, h * peak1, w * 0.45f, h * (baseY + amp), w * 0.62f, h * peak2);
        path.cubicTo(w * 0.80f, h * (peak2 - amp), w * 0.92f, h * (baseY - amp * 0.5f), w, h * (baseY + 0.03f));
        path.lineTo(w, h);
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, h * (baseY - 0.10f), 0, h,
                color, AuroraTheme.blendColors(color, 0xFF0A1014, 0.28f), Shader.TileMode.CLAMP));
        cv.drawPath(path, p);
    }

    // 1: 流光水韵 · 灵动（夜间幽海湛蓝 + 流动青蓝波光）
    private static void drawAquamorphicWaveWallpaper(Canvas cv, int w, int h, int phase) {
        int bgTop, bgMid, bgBot, w1, w2, w3, glow;
        if (phase == WeatherSyncEngine.SOLAR_SUNRISE) {
            bgTop = 0xFFD2E8F5; bgMid = 0xFFF3E0D6; bgBot = 0xFFE3D5E8;
            w1 = 0x88F4B494; w2 = 0x9979BEE0; w3 = 0xBB4B8AB5; glow = 0x55FFDAB8;
        } else if (phase == WeatherSyncEngine.SOLAR_SUNSET) {
            bgTop = 0xFF344870; bgMid = 0xFFB8848D; bgBot = 0xFF5D628C;
            w1 = 0x88EC997A; w2 = 0x996F7EB2; w3 = 0xBB32446E; glow = 0x66F5A07E;
        } else if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            bgTop = 0xFF0B1C36; bgMid = 0xFF13335E; bgBot = 0xFF1D4E85;
            w1 = 0xAA2972B5; w2 = 0xBB1E558C; w3 = 0xCC133B66; glow = 0x5538BDF8;
        } else {
            bgTop = 0xFFD9EEF9; bgMid = 0xFFC5E3F5; bgBot = 0xFFAED6F0;
            w1 = 0x998ACDF0; w2 = 0xAA5BAEE0; w3 = 0xBB3B88BD; glow = 0x66FFFFFF;
        }
        drawSkyGradient(cv, w, h, bgTop, bgMid, bgBot);
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        gp.setShader(new RadialGradient(w * 0.28f, h * 0.44f, w * 0.68f, glow, glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(w * 0.28f, h * 0.44f, w * 0.68f, gp);
        drawWaveRibbon(cv, w, h, 0.44f, 0.09f, w1);
        drawWaveRibbon(cv, w, h, 0.60f, -0.08f, w2);
        drawWaveRibbon(cv, w, h, 0.75f, 0.07f, w3);
    }

    private static void drawWaveRibbon(Canvas cv, int w, int h, float yRatio, float curveAmp, int color) {
        Path p = new Path();
        p.moveTo(0, h);
        p.lineTo(0, h * yRatio);
        p.cubicTo(w * 0.30f, h * (yRatio - curveAmp), w * 0.68f, h * (yRatio + curveAmp), w, h * (yRatio - curveAmp * 0.4f));
        p.lineTo(w, h);
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        cv.drawPath(p, paint);
    }

    // 2: 静谧沙丘 · 光影（夜间暖暮琥珀棕，暖调护眼辨识度极强）
    private static void drawSilentDunesWallpaper(Canvas cv, int w, int h, int phase) {
        int top, mid, bot, d1, d2, d3, sunCol, haloCol;
        if (phase == WeatherSyncEngine.SOLAR_SUNRISE) {
            top = 0xFFEDE0CE; mid = 0xFFF4D3B2; bot = 0xFFE8BC96;
            d1 = 0xFFD9A682; d2 = 0xFFBA815E; d3 = 0xFF915D42;
            sunCol = 0xFFFFE8C2; haloCol = 0x88F4AE72;
        } else if (phase == WeatherSyncEngine.SOLAR_SUNSET) {
            top = 0xFF5D4652; mid = 0xFFC98265; bot = 0xFFDF9E76;
            d1 = 0xFFA36450; d2 = 0xFF754538; d3 = 0xFF4F2E27;
            sunCol = 0xFFFFCA8C; haloCol = 0x99EA7A4E;
        } else if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            top = 0xFF261815; mid = 0xFF3E261E; bot = 0xFF5A3728;
            d1 = 0xFF6E4330; d2 = 0xFF4D2E22; d3 = 0xFF321E17;
            sunCol = 0xFFFFDFB0; haloCol = 0x66E6985C;
        } else {
            top = 0xFFF4ECDF; mid = 0xFFEBDDC8; bot = 0xFFE0CCA9;
            d1 = 0xFFD4B790; d2 = 0xFFBA966C; d3 = 0xFF9C7852;
            sunCol = 0xFFFFF8EC; haloCol = 0x55F7DFB6;
        }
        drawSkyGradient(cv, w, h, top, mid, bot);
        drawCelestialDisc(cv, w * 0.28f, h * 0.44f, w * 0.050f, sunCol, haloCol);
        drawWaveRibbon(cv, w, h, 0.56f, 0.09f, d1);
        drawWaveRibbon(cv, w, h, 0.69f, -0.08f, d2);
        drawWaveRibbon(cv, w, h, 0.81f, 0.06f, d3);
    }

    // 3: 云海天际 · 晴空（夜间星夜靛紫 + 柔雾云层）
    private static void drawCloudHorizonWallpaper(Canvas cv, int w, int h, int phase) {
        int top, mid, bot, c1, c2, c3;
        if (phase == WeatherSyncEngine.SOLAR_SUNRISE || phase == WeatherSyncEngine.SOLAR_SUNSET) {
            top = 0xFF4A5985; mid = 0xFFC88A8C; bot = 0xFFF0BE97;
            c1 = 0x88FFE4CC; c2 = 0x77F3B8A0; c3 = 0x66D99795;
        } else if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            top = 0xFF191330; mid = 0xFF2B2052; bot = 0xFF433278;
            c1 = 0x667B62BA; c2 = 0x775A4496; c3 = 0x88382866;
        } else {
            top = 0xFFCBDDF5; mid = 0xFFE0EBF9; bot = 0xFFF2F0EA;
            c1 = 0xAAFFFFFF; c2 = 0x88DCE8F8; c3 = 0x77C8D9F2;
        }
        drawSkyGradient(cv, w, h, top, mid, bot);
        if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            drawSoftStars(cv, w, h, 3099, 72, 0xFFEDE9FE);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w * 0.24f, h * 0.56f, w * 0.68f, c1, c1 & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(w * 0.24f, h * 0.56f, w * 0.68f, p);
        p.setShader(new RadialGradient(w * 0.78f, h * 0.66f, w * 0.72f, c2, c2 & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(w * 0.78f, h * 0.66f, w * 0.72f, p);
        drawWaveRibbon(cv, w, h, 0.74f, 0.06f, c3);
    }

    // 4: 宣纸墨韵 · 东方（夜间黛墨水墨 + 朱砂印月）
    private static void drawOrientalInkWallpaper(Canvas cv, int w, int h, int phase) {
        int bgTop, bgMid, bgBot, ink1, ink2, sealCol, haloCol;
        if (phase == WeatherSyncEngine.SOLAR_SUNRISE || phase == WeatherSyncEngine.SOLAR_SUNSET) {
            bgTop = 0xFFF0E4D3; bgMid = 0xFFEBDAC5; bgBot = 0xFFE2CCB2;
            ink1 = 0xAA877567; ink2 = 0xDD584A40;
            sealCol = 0xFFD96446; haloCol = 0x55E89B73;
        } else if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            bgTop = 0xFF1E2229; bgMid = 0xFF2A303B; bgBot = 0xFF363E4D;
            ink1 = 0xCC4B5566; ink2 = 0xEE2C3340;
            sealCol = 0xFFE07A5F; haloCol = 0x55E07A5F;
        } else {
            bgTop = 0xFFF4EFE4; bgMid = 0xFFECE5D6; bgBot = 0xFFE3DAC7;
            ink1 = 0xAAABA496; ink2 = 0xDD70695E;
            sealCol = 0xFFCF5C45; haloCol = 0x44DF957F;
        }
        drawSkyGradient(cv, w, h, bgTop, bgMid, bgBot);
        drawCelestialDisc(cv, w * 0.74f, h * 0.44f, w * 0.044f, sealCol, haloCol);
        drawRidgeLayer(cv, w, h, 0.60f, 0.06f, 0.54f, 0.65f, ink1);
        drawRidgeLayer(cv, w, h, 0.74f, 0.05f, 0.69f, 0.78f, ink2);
    }

    // 5: 纯色柔光 · 护眼（极简低饱和暖炭灰，无山峦纯质感）
    private static void drawEyeCareSolidWallpaper(Canvas cv, int w, int h, int phase) {
        int bgTop, bgMid, bgBot, glow;
        if (phase == WeatherSyncEngine.SOLAR_SUNRISE || phase == WeatherSyncEngine.SOLAR_SUNSET) {
            bgTop = 0xFFF0E3D4; bgMid = 0xFFEEDCC8; bgBot = 0xFFE6CEB7;
            glow = 0x44E8A878;
        } else if (phase == WeatherSyncEngine.SOLAR_NIGHT) {
            bgTop = 0xFF1A1C22; bgMid = 0xFF21242C; bgBot = 0xFF282C36;
            glow = 0x44505A70;
        } else {
            bgTop = 0xFFF2F1EC; bgMid = 0xFFECEBE5; bgBot = 0xFFE5E4DD;
            glow = 0x38D6DEE8;
        }
        drawSkyGradient(cv, w, h, bgTop, bgMid, bgBot);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w * 0.5f, h * 0.42f, w * 0.9f, glow, glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(w * 0.5f, h * 0.42f, w * 0.9f, p);
    }

    private static boolean computeBright(Bitmap b) {
        if (b == null || b.isRecycled()) return true;
        int w = b.getWidth(), h = b.getHeight();
        int sx = Math.max(1, w / 16), sy = Math.max(1, h / 16);
        double sum = 0;
        int n = 0;
        for (int y = sy / 2; y < h; y += sy) {
            for (int x = sx / 2; x < w; x += sx) {
                int c = b.getPixel(x, y);
                sum += ((c >> 16) & 0xFF) * 0.299 + ((c >> 8) & 0xFF) * 0.587 + (c & 0xFF) * 0.114;
                n++;
            }
        }
        return n > 0 && (sum / n) > 165;
    }

    private static int computeRegionColor(Bitmap b, float yStartRatio, float yEndRatio) {
        if (b == null || b.isRecycled()) return 0xFF182032;
        int w = b.getWidth(), h = b.getHeight();
        int y0 = Math.max(0, Math.min(h - 1, Math.round(h * yStartRatio)));
        int y1 = Math.max(y0 + 1, Math.min(h, Math.round(h * yEndRatio)));
        int sx = Math.max(1, w / 16);
        int sy = Math.max(1, (y1 - y0) / 8);
        long rSum = 0, gSum = 0, bSum = 0;
        int n = 0;
        for (int y = y0; y < y1; y += sy) {
            for (int x = sx / 2; x < w; x += sx) {
                int c = b.getPixel(x, y);
                rSum += (c >> 16) & 0xFF;
                gSum += (c >> 8) & 0xFF;
                bSum += c & 0xFF;
                n++;
            }
        }
        if (n <= 0) return 0xFF182032;
        return 0xFF000000 | ((int) (rSum / n) << 16) | ((int) (gSum / n) << 8) | (int) (bSum / n);
    }
}
