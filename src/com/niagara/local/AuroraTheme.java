package com.niagara.local;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

/**
 * 流光桌面主题与日出/日落护眼视觉系统 (v2.4.2)：
 * - 6 套 ColorOS 灵感护眼昼夜壁纸，拉大夜间色相区分度（深林青、幽海蓝、暖暮棕、星夜紫、黛墨灰、暖炭黑）；
 * - 附带 presetIdx 供桌面背景与锁屏壁纸实时同源渲染。
 */
final class AuroraTheme {
    final String name;
    final int baseBg;
    final int textPrimary;
    final int textSecondary;
    final int accent;
    final int accentBubble;
    final int cardFill;
    final int cardStroke;
    final boolean isLight;
    final boolean useSystemWallpaper;
    final boolean followDayNight;
    final int[] auroraOrbs;

    final int nightBaseBg;
    final int nightAccent;
    final int nightAccentBubble;
    final int[] nightOrbs;
    int presetIdx = 0;
    int glassTopTint;
    int glassBotTint;

    AuroraTheme(String name,
                int baseBg, int textPrimary, int textSecondary,
                int accent, int accentBubble, int cardFill, int cardStroke,
                boolean isLight, boolean useSystemWallpaper, boolean followDayNight, int[] auroraOrbs,
                int nightBaseBg, int nightAccent, int nightAccentBubble, int[] nightOrbs) {
        this.name = name;
        this.baseBg = baseBg;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.accentBubble = accentBubble;
        this.cardFill = cardFill;
        this.cardStroke = cardStroke;
        this.isLight = isLight;
        this.useSystemWallpaper = useSystemWallpaper;
        this.followDayNight = followDayNight;
        this.auroraOrbs = auroraOrbs;
        this.nightBaseBg = nightBaseBg;
        this.nightAccent = nightAccent;
        this.nightAccentBubble = nightAccentBubble;
        this.nightOrbs = nightOrbs;
        this.glassTopTint = (auroraOrbs != null && auroraOrbs.length > 0) ? auroraOrbs[0] : baseBg;
        this.glassBotTint = (auroraOrbs != null && auroraOrbs.length > 2) ? auroraOrbs[2] : baseBg;
    }

    static final int IDX_DEFAULT = 0;

    static final int[] DAYLIGHT_ORBS = {
        0xFFE4ECE7,
        0xFFDCE7E1,
        0xFFEAF0EC,
        0xFFF0EDE5
    };

    static final int[] NIGHT_ORBS = {
        0xFF282B33,
        0xFF20232B,
        0xFF2E323C,
        0xFF1A1C23
    };

    static final String[] SHORT_PALETTE_NAMES = {
        "山岚叠翠", "流光水韵", "静谧沙丘", "云海天际", "宣纸墨韵", "纯色柔光"
    };

    static final AuroraTheme[] PRESETS = {
        // 0: 山岚叠翠（白昼青翠山岚 / 夜间深林松青）
        new AuroraTheme(
            "山岚叠翠 · 大地",
            0xFFE2EFE7, 0xFF14241D, 0x99314A3E,
            0xFF2D6A4F, 0xFF1B4332,
            0x72FFFFFF, 0x48FFFFFF,
            true, false, true, new int[]{0xFFBCE0CD, 0xFF9ACBB2, 0xFFD9EBDF, 0xFF83BBA0},
            0xFF0E231F, 0xFF52D1A0, 0xFF2D8A68, new int[]{0xFF1F5A4C, 0xFF2D7A67, 0xFF153E36, 0xFF398C77}
        ),
        // 1: 流光水韵（白昼晴湖冰蓝 / 夜间幽海湛蓝）
        new AuroraTheme(
            "流光水韵 · 灵动",
            0xFFDFEFF8, 0xFF102030, 0x9928455E,
            0xFF0284C7, 0xFF0369A1,
            0x68FFFFFF, 0x44FFFFFF,
            true, false, true, new int[]{0xFF9AD9FA, 0xFF7EC8F2, 0xFFB8E2FA, 0xFF74D6D0},
            0xFF0B1C34, 0xFF38BDF8, 0xFF0284C7, new int[]{0xFF1A4B84, 0xFF2366AD, 0xFF123663, 0xFF2E7BC2}
        ),
        // 2: 静谧沙丘（白昼暖杏砂岩 / 夜间暖暮琥珀棕）
        new AuroraTheme(
            "静谧沙丘 · 光影",
            0xFFF3E8D8, 0xFF2B1F16, 0x995E4836,
            0xFFB45309, 0xFF92400E,
            0x6CFFFFFF, 0x46FFFFFF,
            true, false, true, new int[]{0xFFECCCA6, 0xFFE3B78A, 0xFFF2DBC0, 0xFFD8A473},
            0xFF241814, 0xFFF59E0B, 0xFFD97706, new int[]{0xFF6B3E2A, 0xFF8A5136, 0xFF4C2C1E, 0xFFA36443}
        ),
        // 3: 云海天际（白昼晴空蔚蓝 / 夜间星夜靛紫）
        new AuroraTheme(
            "云海天际 · 晴空",
            0xFFE5ECFA, 0xFF171C30, 0x99384266,
            0xFF4F46E5, 0xFF4338CA,
            0x68FFFFFF, 0x44FFFFFF,
            true, false, true, new int[]{0xFFC5D5F7, 0xFFB5C4F2, 0xFFDCCDF7, 0xFFA9B9EE},
            0xFF191430, 0xFFA78BFA, 0xFF7C3AED, new int[]{0xFF46337A, 0xFF5D449E, 0xFF2E2257, 0xFF7052B8}
        ),
        // 4: 宣纸墨韵（白昼温润宣纸 / 夜间东方黛墨灰）
        new AuroraTheme(
            "宣纸墨韵 · 东方",
            0xFFF3EEE3, 0xFF201D18, 0x99595348,
            0xFFB4533C, 0xFF8C3B28,
            0x6EFFFFFF, 0x48FFFFFF,
            true, false, true, new int[]{0xFFE6DEC9, 0xFFD8CEB8, 0xFFEDE7D9, 0xFFCFC4AC},
            0xFF1D2026, 0xFFE07A5F, 0xFFB85A43, new int[]{0xFF3E4654, 0xFF4F5969, 0xFF2C323D, 0xFF5F6B7E}
        ),
        // 5: 纯色柔光（白昼米白极简 / 夜间暖炭柔灰）
        new AuroraTheme(
            "纯色柔光 · 护眼",
            0xFFF0EFEA, 0xFF181A1F, 0x99424752,
            0xFF2563EB, 0xFF1D4ED8,
            0x78FFFFFF, 0x48FFFFFF,
            true, false, true, DAYLIGHT_ORBS,
            0xFF1C1E24, 0xFF60A5FA, 0xFF3B82F6, NIGHT_ORBS
        ),
        // 6: 跟随系统白昼 · 灵动渐变
        new AuroraTheme(
            "跟随系统白昼 · 灵动渐变",
            0xFFEEF2FF, 0xFF141824, 0x991E293B,
            0xFF4338CA, 0xFF3730A3,
            0x66FFFFFF, 0x42FFFFFF,
            true, false, true, DAYLIGHT_ORBS,
            0xFF151B2E, 0xFF7DD3FC, 0xFF3B5998, NIGHT_ORBS
        ),
        // 7: 跟随系统壁纸 · 昼夜自适应
        new AuroraTheme(
            "跟随系统壁纸 · 昼夜自适应",
            0xFFEEF2FF, 0xFF141824, 0x991E293B,
            0xFF4338CA, 0xFF3730A3,
            0x66FFFFFF, 0x42FFFFFF,
            true, true, true, DAYLIGHT_ORBS,
            0xFF151B2E, 0xFF7DD3FC, 0xFF3B5998, NIGHT_ORBS
        )
    };

    static int resolveSolarPhase(Context ctx) {
        int uiNight = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (uiNight == Configuration.UI_MODE_NIGHT_YES) return WeatherSyncEngine.SOLAR_NIGHT;
        return WeatherSyncEngine.getSolarPhase(ctx);
    }

    static AuroraTheme resolve(Context ctx, int idx) {
        idx = Math.max(0, Math.min(PRESETS.length - 1, idx));
        AuroraTheme base = PRESETS[idx];
        int solarPhase = resolveSolarPhase(ctx);
        boolean sysNight = (solarPhase == WeatherSyncEngine.SOLAR_NIGHT);

        if (base.followDayNight && !base.useSystemWallpaper) {
            int bg = sysNight ? base.nightBaseBg : base.baseBg;
            if (solarPhase == WeatherSyncEngine.SOLAR_SUNRISE || solarPhase == WeatherSyncEngine.SOLAR_SUNSET) {
                bg = blendColors(bg, 0xFFF4DEC4, 0.28f);
            }
            boolean bright = isColorBright(bg);
            if (bright) {
                AuroraTheme t = new AuroraTheme(
                    base.name + (solarPhase == WeatherSyncEngine.SOLAR_SUNRISE ? "（日出）"
                            : solarPhase == WeatherSyncEngine.SOLAR_SUNSET ? "（日落）" : "（白昼）"),
                    bg, 0xFF141824, 0x9E263248,
                    base.accent, base.accentBubble,
                    0x66FFFFFF, 0x48FFFFFF,
                    true, false, true, base.auroraOrbs,
                    base.nightBaseBg, base.nightAccent, base.nightAccentBubble, base.nightOrbs
                );
                t.presetIdx = idx;
                t.glassTopTint = (base.auroraOrbs != null && base.auroraOrbs.length > 1)
                        ? blendColors(base.auroraOrbs[0], base.auroraOrbs[1], 0.4f) : bg;
                t.glassBotTint = (base.auroraOrbs != null && base.auroraOrbs.length > 3)
                        ? blendColors(base.auroraOrbs[2], base.auroraOrbs[3], 0.5f) : bg;
                return t;
            } else {
                int[] activeOrbs = sysNight ? base.nightOrbs : base.auroraOrbs;
                AuroraTheme t = new AuroraTheme(
                    base.name + (sysNight ? "（暗夜）" : "（深邃）"),
                    bg, 0xFFF8FAFC, 0xB8CBD5E1,
                    sysNight ? base.nightAccent : base.accent,
                    sysNight ? base.nightAccentBubble : base.accentBubble,
                    0x22FFFFFF, 0x2EFFFFFF,
                    false, false, true,
                    activeOrbs,
                    base.nightBaseBg, base.nightAccent, base.nightAccentBubble, base.nightOrbs
                );
                t.presetIdx = idx;
                t.glassTopTint = (activeOrbs != null && activeOrbs.length > 1)
                        ? blendColors(activeOrbs[0], activeOrbs[1], 0.45f) : bg;
                t.glassBotTint = (activeOrbs != null && activeOrbs.length > 3)
                        ? blendColors(activeOrbs[2], activeOrbs[3], 0.5f) : bg;
                return t;
            }
        }

        if (base.useSystemWallpaper) {
            boolean wallBright = AuroraBackdropView.isWallpaperBright(ctx) && !sysNight;
            int accent = wallBright ? 0xFF4338CA : 0xFF7DD3FC;
            int bubble = wallBright ? 0xFF3730A3 : 0xFF3B5998;
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    accent = ctx.getResources().getColor(
                            wallBright ? android.R.color.system_accent1_700 : android.R.color.system_accent1_200, null);
                    bubble = ctx.getResources().getColor(
                            wallBright ? android.R.color.system_accent1_800 : android.R.color.system_accent1_600, null);
                } catch (Throwable ignored) {}
            }
            AuroraTheme t = new AuroraTheme(
                base.name,
                wallBright ? 0xFFEEF2FF : 0xFF151B2E,
                wallBright ? 0xFF141824 : 0xFFFFFFFF,
                wallBright ? 0x9E1E293B : 0xCCFFFFFF,
                accent,
                bubble,
                wallBright ? 0x66FFFFFF : 0x26000000,
                wallBright ? 0x45FFFFFF : 0x32FFFFFF,
                wallBright, true, true,
                sysNight ? NIGHT_ORBS : DAYLIGHT_ORBS,
                base.nightBaseBg, base.nightAccent, base.nightAccentBubble, base.nightOrbs
            );
            t.presetIdx = idx;
            t.glassTopTint = AuroraBackdropView.getWallpaperTopColor(ctx, t.baseBg);
            t.glassBotTint = AuroraBackdropView.getWallpaperBottomColor(ctx, t.baseBg);
            return t;
        }

        base.presetIdx = idx;
        return base;
    }

    GradientDrawable glassCard(float density, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardFill);
        g.setStroke(Math.max(1, (int) (density * 0.85f)), cardStroke);
        g.setCornerRadius(radiusDp * density);
        return g;
    }

    GradientDrawable sheetPanelBackground(float density) {
        int topC = isLight ? 0xFAFFFFFF : 0xF8111827;
        int botC = isLight ? (0xF6000000 | (baseBg & 0x00FFFFFF)) : 0xF8090D1A;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{topC, botC});
        g.setStroke(Math.max(1, (int) (density * 1.0f)), isLight ? 0x88FFFFFF : 0x33FFFFFF);
        float r = 28f * density;
        g.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        return g;
    }

    GradientDrawable sheetTileCard(float density, float radiusDp) {
        int c1 = isLight ? 0x88FFFFFF : 0x1EFFFFFF;
        int c2 = isLight ? 0x55FFFFFF : 0x12FFFFFF;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setStroke(Math.max(1, (int) (density * 0.85f)), isLight ? 0x66FFFFFF : 0x24FFFFFF);
        g.setCornerRadius(radiusDp * density);
        return g;
    }

    GradientDrawable subtleGhostPill(float density, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(isLight ? 0x52FFFFFF : 0x1EFFFFFF);
        g.setStroke(Math.max(1, (int) (density * 0.85f)), isLight ? 0x50FFFFFF : 0x2AFFFFFF);
        g.setCornerRadius(radiusDp * density);
        return g;
    }

    int[] coinButtonGradientColors() {
        if (isLight) {
            return new int[]{
                withAlpha(blendColors(0xFFFFFFFF, glassBotTint, 0.16f), 0xDC),
                withAlpha(blendColors(0xFFF8FAFC, glassBotTint, 0.24f), 0xCC)
            };
        } else {
            return new int[]{
                withAlpha(blendColors(glassBotTint, 0xFFFFFFFF, 0.16f), 0x82),
                withAlpha(blendColors(glassBotTint, baseBg, 0.26f), 0x72)
            };
        }
    }

    int coinButtonRingColor() {
        if (isLight) {
            return withAlpha(blendColors(glassBotTint, 0xFF141824, 0.35f), 0x34);
        } else {
            return withAlpha(blendColors(accent, 0xFFFFFFFF, 0.45f), 0x52);
        }
    }

    static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    static int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = Math.round(((c1 >> 16) & 0xFF) * inv + ((c2 >> 16) & 0xFF) * ratio);
        int g = Math.round(((c1 >> 8) & 0xFF) * inv + ((c2 >> 8) & 0xFF) * ratio);
        int b = Math.round((c1 & 0xFF) * inv + (c2 & 0xFF) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    GradientDrawable favRowSurface(float density, boolean hasNotif) {
        GradientDrawable g = new GradientDrawable();
        if (isLight) {
            g.setColor(hasNotif ? 0x78FFFFFF : 0x46FFFFFF);
            g.setStroke(Math.max(1, (int) (density * 0.8f)), hasNotif ? ((accent & 0x00FFFFFF) | 0x44000000) : 0x38FFFFFF);
        } else {
            g.setColor(hasNotif ? 0x28FFFFFF : 0x16FFFFFF);
            g.setStroke(Math.max(1, (int) (density * 0.8f)), hasNotif ? ((accent & 0x00FFFFFF) | 0x48000000) : 0x20FFFFFF);
        }
        g.setCornerRadius(18f * density);
        return g;
    }

    GradientDrawable accentGlowPill(float density, float radiusDp) {
        int c1 = (accent & 0x00FFFFFF) | (isLight ? 0x22000000 : 0x3A000000);
        int c2 = (accentBubble & 0x00FFFFFF) | (isLight ? 0x14000000 : 0x24000000);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setStroke(Math.max(1, (int) (density * 0.9f)), (accent & 0x00FFFFFF) | 0x52000000);
        g.setCornerRadius(radiusDp * density);
        return g;
    }

    GradientDrawable solidAccentButton(float density, float radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{accent, accentBubble});
        g.setCornerRadius(radiusDp * density);
        return g;
    }

    static GradientDrawable paletteOrbPreview(float density, int presetIdx, boolean selected, boolean isLightUi) {
        AuroraTheme p = PRESETS[Math.max(0, Math.min(PRESETS.length - 1, presetIdx))];
        int[] orbs = isLightUi ? p.auroraOrbs : p.nightOrbs;
        int c1 = orbs != null && orbs.length > 0 ? orbs[0] : (isLightUi ? p.baseBg : p.nightBaseBg);
        int c2 = orbs != null && orbs.length > 1 ? orbs[1] : (isLightUi ? p.accent : p.nightAccent);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        if (selected) {
            g.setStroke(Math.max(2, (int) (density * 2.4f)), isLightUi ? p.accent : p.nightAccent);
        } else {
            g.setStroke(Math.max(1, (int) (density * 1.0f)), isLightUi ? 0x44141824 : 0x66FFFFFF);
        }
        return g;
    }

    static boolean isColorBright(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 0.299 + g * 0.587 + b * 0.114) > 135;
    }
}
