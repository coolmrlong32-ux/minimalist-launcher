package com.niagara.local;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 现代分组卡片式控制中心与后台保活体检中心 (SettingsSheetController v2.4.0)：
 * - 顶部固定标题栏 + 5 大圆角分组卡片（外观与壁纸 / 首页与常用 / 手势与交互 / 后台保活与权限体检 / 工具隐私与备份）；
 * - 统一线性矢量图标 + 原生 Switch 开关 + 实时 SeekBar 滑块，彻底告别 Emoji 调试面板感；
 * - 切换开关原地局部刷新，不再关闭重弹导致闪烁与滚动条回顶；
 * - 后台保活与权限体检区在返回弹窗时自动重新检测 5 项系统状态（默认桌面/常驻服务/免电池优化/无障碍/通知使用权）。
 */
final class SettingsSheetController {

    interface Host {
        Context getContext();
        SharedPreferences getPrefs();
        AuroraTheme getThemeObj();
        Typeface getTypeface();
        float getDp();
        float sp(float size);
        int getFavoritesCount();
        int getHiddenCount();
        String getCurrentIconShapeName();
        String getCurrentIconPackName();
        String getCurrentFontName();
        String getCurrentDensityName();
        String getCurrentCoinActionName();
        String getCurrentClockStyleName();
        String getCurrentSearchEngineName();
        int getPinnedWidgetCount();
        void showDeviceStatus();
        void showPickFavorites();
        void showReorderFavorites();
        void applyThemePreset(int styleIdx);
        void pickGalleryWallpaper();
        void onDimPercentChanged(int pct);
        void onWallLockOnlyChanged(boolean lockOnly);
        void onFlowAnimChanged(boolean on);
        void showIconShapePicker();
        void showIconPackPicker();
        void onShowIconsChanged(boolean on);
        void showFontPicker();
        void onTextScaleChanged(float scale);
        void onUse24HChanged(boolean use24h);
        void showClockStylePicker();
        String getCurrentStatusBarModeName();
        void showStatusBarModePicker();
        void onFavCardsChanged(boolean on);
        void onOneHandGravityChanged(boolean on);
        void onSurfaceUnreadHomeChanged(boolean on);
        void onShowMediaChanged(boolean on);
        void onShowAgendaChanged(boolean on);
        void openWidgetManager();
        void showDensityPicker();
        void onPullDownSearchChanged(boolean on);
        void onWaveIdleDotsChanged(boolean dots);
        void onWaveAdaptiveAlphaChanged(boolean adaptive);
        void showSearchEnginePicker();
        void onCoinLeftChanged(boolean left);
        void showCoinActionPicker();
        void onPullLaunchChanged(boolean on);
        void onDoubleTapLockChanged(boolean on);
        void onInlineNotifChanged(boolean on);
        void onKeepAliveChanged(boolean on);
        void onBiometricHideChanged(boolean on);
        void openStickyNoteEditor();
        void openCreateFolder();
        void openHiddenApps();
        void exportBackup();
        void importBackup();
        void openHomeSettings();
        void openBatteryOptimizationSettings();
        void openAppDetailsSettings();
        void openAccessibilitySettings();
        void openNotificationListenerSettings();
    }

    static Dialog show(final Host host) {
        final Context ctx = host.getContext();
        final SharedPreferences prefs = host.getPrefs();
        final AuroraTheme theme = host.getThemeObj();
        final Typeface tf = host.getTypeface();
        final float dp = host.getDp();

        final List<Runnable> statusRefreshers = new ArrayList<>();

        final Dialog dlg = new Dialog(ctx) {
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                super.onWindowFocusChanged(hasFocus);
                if (hasFocus) {
                    for (Runnable r : statusRefreshers) r.run();
                }
            }
        };
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(theme.sheetPanelBackground(dp));

        // 顶部拖拽指示条
        View handle = new View(ctx);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(theme.textSecondary);
        hg.setCornerRadius(2.5f * dp);
        handle.setBackground(hg);
        handle.setAlpha(0.45f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Math.round(38 * dp), Math.round(4 * dp));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = Math.round(10 * dp);
        hlp.bottomMargin = Math.round(8 * dp);
        root.addView(handle, hlp);

        // 顶部固定导航栏
        LinearLayout topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Math.round(20 * dp), Math.round(4 * dp), Math.round(16 * dp), Math.round(12 * dp));

        LinearLayout titleCol = new LinearLayout(ctx);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        String verName = "2.9.0";
        try {
            String vn = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            if (vn != null && !vn.isEmpty()) verName = vn;
        } catch (Throwable ignored) {}
        TextView titleTv = makeText(ctx, tf, host.sp(19f), theme.textPrimary, true, "流光桌面设置");
        TextView subTv = makeText(ctx, tf, host.sp(11.5f), theme.textSecondary, false, "v" + verName);
        subTv.setPadding(0, Math.round(2 * dp), 0, 0);
        titleCol.addView(titleTv);
        titleCol.addView(subTv);
        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView statusBtn = makeText(ctx, tf, host.sp(12.2f), theme.accent, true, "本机状态");
        statusBtn.setBackground(theme.accentGlowPill(dp, 14));
        statusBtn.setPadding(Math.round(12 * dp), Math.round(6.5f * dp), Math.round(12 * dp), Math.round(6.5f * dp));
        statusBtn.setOnClickListener(v -> {
            dlg.dismiss();
            host.showDeviceStatus();
        });
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sblp.rightMargin = Math.round(8 * dp);
        topBar.addView(statusBtn, sblp);

        FrameLayout closeBtn = new FrameLayout(ctx);
        closeBtn.setBackground(theme.subtleGhostPill(dp, 17));
        VectorGlyphView closeIc = new VectorGlyphView(ctx, VectorGlyphView.IC_CLOSE, theme.textSecondary, dp);
        closeBtn.addView(closeIc, new FrameLayout.LayoutParams(Math.round(14 * dp), Math.round(14 * dp), Gravity.CENTER));
        closeBtn.setOnClickListener(v -> dlg.dismiss());
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(Math.round(34 * dp), Math.round(34 * dp)));

        root.addView(topBar);

        // 顶部分割线
        View topDiv = new View(ctx);
        topDiv.setBackgroundColor(theme.isLight ? 0x12141824 : 0x1AFFFFFF);
        root.addView(topDiv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(0.8f * dp))));

        // 可滚动内容区
        ScrollView scroll = new ScrollView(ctx);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Math.round(16 * dp), Math.round(10 * dp), Math.round(16 * dp), Math.round(28 * dp));

        // ==================== 顶部系统权限与保活体检摘要条（可展开详情） ====================
        LinearLayout healthBanner = makeGroupCard(ctx, theme, dp);
        LinearLayout bannerRow = makeBaseRow(ctx, dp);
        final VectorGlyphView bannerIcon = new VectorGlyphView(ctx, VectorGlyphView.IC_SHIELD, theme.accent, dp);
        FrameLayout bannerIconWrap = new FrameLayout(ctx);
        GradientDrawable bannerIconBg = new GradientDrawable();
        bannerIconBg.setCornerRadius(9 * dp);
        bannerIconWrap.setBackground(bannerIconBg);
        bannerIconWrap.addView(bannerIcon, new FrameLayout.LayoutParams(Math.round(16 * dp), Math.round(16 * dp), Gravity.CENTER));
        LinearLayout.LayoutParams biLp = new LinearLayout.LayoutParams(Math.round(28 * dp), Math.round(28 * dp));
        biLp.rightMargin = Math.round(12 * dp);
        bannerRow.addView(bannerIconWrap, biLp);

        LinearLayout bannerTitleCol = makeTitleCol(ctx, tf, host, theme, dp, "系统权限与保活体检", "默认桌面 · 常驻守护 · 电池白名单 · 无障碍 · 通知");
        bannerRow.addView(bannerTitleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView bannerBadge = makeText(ctx, tf, host.sp(11.6f), theme.accent, true, "");
        bannerBadge.setPadding(Math.round(10 * dp), Math.round(5 * dp), Math.round(10 * dp), Math.round(5 * dp));
        LinearLayout.LayoutParams bbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bbLp.leftMargin = Math.round(8 * dp);
        bannerRow.addView(bannerBadge, bbLp);
        healthBanner.addView(bannerRow);
        content.addView(healthBanner);

        final LinearLayout gHealthWrap = new LinearLayout(ctx);
        gHealthWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout gHealth = makeGroupCard(ctx, theme, dp);
        LinearLayout.LayoutParams ghLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ghLp.topMargin = Math.round(8 * dp);

        statusRefreshers.add(addHealthCheckRow(ctx, tf, host, theme, dp, gHealth, VectorGlyphView.IC_HOME,
                "设为系统默认桌面", "确保按 Home 键或上滑秒回流光桌面",
                () -> isDefaultHomeApp(ctx), "去设置", host::openHomeSettings));
        addDivider(ctx, theme, dp, gHealth);
        addSwitchRow(ctx, tf, host, theme, dp, gHealth, VectorGlyphView.IC_SHIELD,
                "后台常驻守护服务", "防止系统回收桌面进程，实现退回桌面零重载",
                prefs.getBoolean("keep_alive", true), on -> {
                    host.onKeepAliveChanged(on);
                    for (Runnable r : statusRefreshers) r.run();
                });
        addDivider(ctx, theme, dp, gHealth);
        statusRefreshers.add(addHealthCheckRow(ctx, tf, host, theme, dp, gHealth, VectorGlyphView.IC_BATTERY,
                "忽略电池优化（白名单）", "阻止后台省电策略冻结或延迟桌面唤醒",
                () -> isIgnoringBatteryOpt(ctx), "去放行", host::openBatteryOptimizationSettings));
        addDivider(ctx, theme, dp, gHealth);
        statusRefreshers.add(addHealthCheckRow(ctx, tf, host, theme, dp, gHealth, VectorGlyphView.IC_LOCK,
                "无障碍手势服务", "驱动双击锁屏、单手拉通知栏与一键多任务后台",
                GestureLockService::isRunning, "去开启", host::openAccessibilitySettings));
        addDivider(ctx, theme, dp, gHealth);
        statusRefreshers.add(addHealthCheckRow(ctx, tf, host, theme, dp, gHealth, VectorGlyphView.IC_BELL,
                "通知使用权（兼系统级保活）", "驱动通知胶囊、音乐控制并让系统持久绑定进程",
                () -> isNotificationListenerEnabled(ctx), "去授权", host::openNotificationListenerSettings));
        addDivider(ctx, theme, dp, gHealth);
        addActionRow(ctx, tf, host, theme, dp, gHealth, VectorGlyphView.IC_SHIELD,
                "系统耗电与自启动详情", "ColorOS / 一加建议开启「允许完全后台行为」与「允许自启动」",
                "前往", host::openAppDetailsSettings);
        gHealthWrap.addView(gHealth, ghLp);

        int initialOk = countHealthyItems(ctx, prefs);
        gHealthWrap.setVisibility(initialOk == 5 ? View.GONE : View.VISIBLE);
        Runnable refreshBanner = () -> {
            int okCount = countHealthyItems(ctx, prefs);
            boolean expanded = gHealthWrap.getVisibility() == View.VISIBLE;
            if (okCount == 5) {
                int green = theme.isLight ? 0xFF059669 : 0xFF34D399;
                bannerIcon.setColor(green);
                bannerIconBg.setColor(theme.isLight ? 0x1A10B981 : 0x2634D399);
                bannerBadge.setText(expanded ? "5/5 已就绪 ▴" : "5/5 已就绪 ✓");
                bannerBadge.setTextColor(green);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(theme.isLight ? 0x1A10B981 : 0x2634D399);
                bg.setCornerRadius(12 * dp);
                bannerBadge.setBackground(bg);
            } else {
                int amber = theme.isLight ? 0xFFD97706 : 0xFFFBBF24;
                bannerIcon.setColor(amber);
                bannerIconBg.setColor(theme.isLight ? 0x1AF59E0B : 0x28F59E0B);
                bannerBadge.setText(okCount + "/5 待完善 " + (expanded ? "▴" : "›"));
                bannerBadge.setTextColor(amber);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(theme.isLight ? 0x1AF59E0B : 0x28F59E0B);
                bg.setStroke(Math.max(1, Math.round(0.8f * dp)), 0x55F59E0B);
                bg.setCornerRadius(12 * dp);
                bannerBadge.setBackground(bg);
            }
        };
        statusRefreshers.add(refreshBanner);
        refreshBanner.run();
        healthBanner.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            gHealthWrap.setVisibility(gHealthWrap.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            refreshBanner.run();
        });
        content.addView(gHealthWrap);

        // ==================== 分组 1：外观与壁纸 ====================
        addGroupTitle(ctx, tf, host, theme, dp, content, "外观与壁纸");
        LinearLayout gAppearance = makeGroupCard(ctx, theme, dp);

        // 6 色昼夜调色盘
        LinearLayout paletteBox = new LinearLayout(ctx);
        paletteBox.setOrientation(LinearLayout.VERTICAL);
        paletteBox.setPadding(Math.round(14 * dp), Math.round(14 * dp), Math.round(14 * dp), Math.round(12 * dp));

        TextView palLabel = makeText(ctx, tf, host.sp(12.5f), theme.textSecondary, true, "日出日落护眼壁纸（" + WeatherSyncEngine.formatSunWindow(ctx) + "）");
        palLabel.setPadding(0, 0, 0, Math.round(10 * dp));
        paletteBox.addView(palLabel);

        LinearLayout orbsRow = new LinearLayout(ctx);
        orbsRow.setOrientation(LinearLayout.HORIZONTAL);
        orbsRow.setGravity(Gravity.CENTER_VERTICAL);
        final int curTheme = Math.max(0, Math.min(AuroraTheme.PRESETS.length - 1, prefs.getInt("theme_idx", AuroraTheme.IDX_DEFAULT)));
        for (int i = 0; i < 6; i++) {
            final int styleIdx = i;
            LinearLayout orbCell = new LinearLayout(ctx);
            orbCell.setOrientation(LinearLayout.VERTICAL);
            orbCell.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout orbWrap = new FrameLayout(ctx);
            View orb = new View(ctx);
            boolean sel = (curTheme == i);
            orb.setBackground(AuroraTheme.paletteOrbPreview(dp, i, sel, theme.isLight));
            orbWrap.addView(orb, new FrameLayout.LayoutParams(Math.round(34 * dp), Math.round(34 * dp), Gravity.CENTER));
            if (sel) {
                TextView check = makeText(ctx, tf, 12f, AuroraTheme.isColorBright(AuroraTheme.PRESETS[i].baseBg) ? 0xFF141824 : 0xFFFFFFFF, true, "✓");
                check.setGravity(Gravity.CENTER);
                orbWrap.addView(check, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            }
            orbCell.addView(orbWrap);

            String shortLb = AuroraTheme.SHORT_PALETTE_NAMES[i].substring(0, 2);
            TextView lb = makeText(ctx, tf, host.sp(11f), sel ? theme.accent : theme.textSecondary, sel, shortLb);
            lb.setPadding(0, Math.round(4 * dp), 0, 0);
            orbCell.addView(lb);

            orbCell.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                dlg.dismiss();
                host.applyThemePreset(styleIdx);
            });
            orbsRow.addView(orbCell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        paletteBox.addView(orbsRow);
        gAppearance.addView(paletteBox);

        addDivider(ctx, theme, dp, gAppearance);
        addActionRow(ctx, tf, host, theme, dp, gAppearance, VectorGlyphView.IC_WALLPAPER, "从相册选择壁纸", "自动适配昼夜护眼亮度", "", () -> {
            dlg.dismiss();
            host.pickGalleryWallpaper();
        });

        addDivider(ctx, theme, dp, gAppearance);
        addActionRow(ctx, tf, host, theme, dp, gAppearance, VectorGlyphView.IC_SHAPES, "图标造型", "超椭圆、液态玻璃与 Niagara Dots 圆点",
                host.getCurrentIconShapeName(), () -> {
            dlg.dismiss();
            host.showIconShapePicker();
        });

        addDivider(ctx, theme, dp, gAppearance);
        addActionRow(ctx, tf, host, theme, dp, gAppearance, VectorGlyphView.IC_FONT, "字体风格", "调整时钟与列表字体气质",
                host.getCurrentFontName(), () -> {
            dlg.dismiss();
            host.showFontPicker();
        });

        addDivider(ctx, theme, dp, gAppearance);
        addActionRow(ctx, tf, host, theme, dp, gAppearance, VectorGlyphView.IC_CLOCK, "时钟排版风格", "经典纤巧 / 现代圆润 / 极简居中",
                host.getCurrentClockStyleName(), () -> {
            dlg.dismiss();
            host.showClockStylePicker();
        });

        LinearLayout moreAppearanceBox = new LinearLayout(ctx);
        moreAppearanceBox.setOrientation(LinearLayout.VERTICAL);
        moreAppearanceBox.setVisibility(View.GONE);

        addDivider(ctx, theme, dp, moreAppearanceBox);
        int curDim = prefs.getInt("dim_pct", 0);
        addSliderRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_SUN, "壁纸暗化遮罩", curDim + "%", 70, curDim, val -> {
            host.onDimPercentChanged(val);
            return val + "%";
        });

        addDivider(ctx, theme, dp, moreAppearanceBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_WALLPAPER, "仅同步锁屏壁纸", "日出日落仅更新锁屏，保留当前桌面壁纸",
                prefs.getBoolean("wall_lock_only_v1", false), host::onWallLockOnlyChanged);

        addDivider(ctx, theme, dp, moreAppearanceBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_SPARK, "极光流体微动", "触摸壁纸时的微光涟漪反馈",
                prefs.getBoolean("flow_anim", true), host::onFlowAnimChanged);

        addDivider(ctx, theme, dp, moreAppearanceBox);
        addActionRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_SHAPES, "第三方图标包", "支持 Nova / ADW 规范图标包 APK",
                host.getCurrentIconPackName(), () -> {
            dlg.dismiss();
            host.showIconPackPicker();
        });

        addDivider(ctx, theme, dp, moreAppearanceBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_EYE_OFF, "显示应用图标", "关闭则切换为极简纯文字模式",
                prefs.getBoolean("show_icons", true), host::onShowIconsChanged);

        addDivider(ctx, theme, dp, moreAppearanceBox);
        float curScale = prefs.getFloat("text_scale", 1.0f);
        int scaleStep = Math.max(0, Math.min(7, Math.round((curScale - 0.85f) / 0.05f)));
        addSliderRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_FONT, "全局字号缩放",
                String.format(Locale.ROOT, "%.0f%%", curScale * 100f), 7, scaleStep, val -> {
            float ns = 0.85f + val * 0.05f;
            host.onTextScaleChanged(ns);
            return String.format(Locale.ROOT, "%.0f%%", ns * 100f);
        });

        addDivider(ctx, theme, dp, moreAppearanceBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreAppearanceBox, VectorGlyphView.IC_CLOCK, "24 小时制时钟", "关闭则显示 12 小时制",
                prefs.getBoolean("use_24h", true), host::onUse24HChanged);

        addExpandToggleRow(ctx, tf, host, theme, dp, gAppearance, "更多外观细节", moreAppearanceBox);
        gAppearance.addView(moreAppearanceBox);
        content.addView(gAppearance);

        // ==================== 分组 2：首页与常用 ====================
        addGroupTitle(ctx, tf, host, theme, dp, content, "首页与常用");
        LinearLayout gHome = makeGroupCard(ctx, theme, dp);

        addActionRow(ctx, tf, host, theme, dp, gHome, VectorGlyphView.IC_STAR, "勾选第一页常用应用", "选择固定在首页的高频应用",
                "已选 " + host.getFavoritesCount() + " 个", () -> {
            dlg.dismiss();
            host.showPickFavorites();
        });

        addDivider(ctx, theme, dp, gHome);
        addActionRow(ctx, tf, host, theme, dp, gHome, VectorGlyphView.IC_SORT, "调整常用应用顺序", "自定义首页应用上下排列", "", () -> {
            dlg.dismiss();
            host.showReorderFavorites();
        });

        addDivider(ctx, theme, dp, gHome);
        addActionRow(ctx, tf, host, theme, dp, gHome, VectorGlyphView.IC_BATTERY, "顶部状态栏信息", "切换按需电量 / 常显电量 / 温度与运存",
                host.getCurrentStatusBarModeName(), () -> {
            dlg.dismiss();
            host.showStatusBarModePicker();
        });

        addDivider(ctx, theme, dp, gHome);
        addActionRow(ctx, tf, host, theme, dp, gHome, VectorGlyphView.IC_CARD, "桌面原生小组件 (Widgets)", "将系统天气、待办等微件贴到首页顶部一体流",
                "已贴 " + host.getPinnedWidgetCount() + " 个", () -> {
            dlg.dismiss();
            host.openWidgetManager();
        });

        addDivider(ctx, theme, dp, gHome);
        addActionRow(ctx, tf, host, theme, dp, gHome, VectorGlyphView.IC_DENSITY, "列表疏密程度", "调整应用行垂直呼吸间距",
                host.getCurrentDensityName(), () -> {
            dlg.dismiss();
            host.showDensityPicker();
        });

        LinearLayout moreHomeBox = new LinearLayout(ctx);
        moreHomeBox.setOrientation(LinearLayout.VERTICAL);
        moreHomeBox.setVisibility(View.GONE);

        addDivider(ctx, theme, dp, moreHomeBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreHomeBox, VectorGlyphView.IC_BELL, "首页动态浮现未读通知应用", "非首页应用来消息时临时呈现在首页，清除即隐退",
                prefs.getBoolean("surface_unread_home_v1", true), host::onSurfaceUnreadHomeChanged);

        addDivider(ctx, theme, dp, moreHomeBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreHomeBox, VectorGlyphView.IC_CARD, "常用应用毛玻璃底衬", "为首页常用应用行添加柔光卡片背景",
                prefs.getBoolean("fav_cards_v2", false), host::onFavCardsChanged);

        addDivider(ctx, theme, dp, moreHomeBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreHomeBox, VectorGlyphView.IC_GESTURE, "单手黄金区重心下沉", "常用较少时自动下移列表至拇指舒适区",
                prefs.getBoolean("one_hand_gravity_v1", true), host::onOneHandGravityChanged);

        addDivider(ctx, theme, dp, moreHomeBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreHomeBox, VectorGlyphView.IC_SPARK, "顶部一览媒体控制条", "播放音乐时自动浮现极简专辑封面与 2dp 进度线",
                prefs.getBoolean("show_media", true), host::onShowMediaChanged);

        addDivider(ctx, theme, dp, moreHomeBox);
        addSwitchRow(ctx, tf, host, theme, dp, moreHomeBox, VectorGlyphView.IC_CLOCK, "顶部一览近期日程", "在首页状态栏下方展示即将开始的日历事件",
                prefs.getBoolean("show_agenda", false), host::onShowAgendaChanged);

        addExpandToggleRow(ctx, tf, host, theme, dp, gHome, "更多排版细节", moreHomeBox);
        gHome.addView(moreHomeBox);
        content.addView(gHome);

        // ==================== 分组 3：手势与交互 ====================
        addGroupTitle(ctx, tf, host, theme, dp, content, "手势与交互");
        LinearLayout gGesture = makeGroupCard(ctx, theme, dp);

        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_GESTURE, "首页下拉呼出极速搜索", "关闭则首页下拉直接展开系统通知栏",
                prefs.getBoolean("pull_down_search_v1", true), host::onPullDownSearchChanged);

        addDivider(ctx, theme, dp, gGesture);
        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_SPARK, "字母导轨静默圆点模式", "静止显示极简圆点，触碰展开流体字母",
                prefs.getBoolean("wave_idle_dots_v1", true), host::onWaveIdleDotsChanged);

        addDivider(ctx, theme, dp, gGesture);
        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_SPARK, "自适应字母导轨", "仅显示实际存在应用的首字母，关闭则显示完整 A-Z",
                prefs.getBoolean("wave_adaptive_alpha_v1", true), host::onWaveAdaptiveAlphaChanged);

        addDivider(ctx, theme, dp, gGesture);
        addActionRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_SEARCH, "默认网页搜索引擎", "极速搜索输入关键词后一键跳转网页搜索",
                host.getCurrentSearchEngineName(), () -> {
            dlg.dismiss();
            host.showSearchEnginePicker();
        });

        addDivider(ctx, theme, dp, gGesture);
        addActionRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_GESTURE, "悬浮按钮轻点动作", "切换极速搜索、多任务后台、语音或隐藏",
                host.getCurrentCoinActionName(), () -> {
            dlg.dismiss();
            host.showCoinActionPicker();
        });

        addDivider(ctx, theme, dp, gGesture);
        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_GESTURE, "悬浮多任务按钮靠左", "适配左手单手握持习惯（默认在右下角）",
                prefs.getBoolean("coin_left_v1", false), host::onCoinLeftChanged);

        addDivider(ctx, theme, dp, gGesture);
        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_SPARK, "波浪导轨内拉直达", "在字母导轨停留后向内拉动直接打开首位应用",
                prefs.getBoolean("pull_launch", true), host::onPullLaunchChanged);

        addDivider(ctx, theme, dp, gGesture);
        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_LOCK, "双击空白处熄屏锁屏", "点应用图标 0 延迟秒开，仅空白区响应双击锁屏",
                prefs.getBoolean("double_tap_lock", true), host::onDoubleTapLockChanged);

        addDivider(ctx, theme, dp, gGesture);
        addSwitchRow(ctx, tf, host, theme, dp, gGesture, VectorGlyphView.IC_BELL, "首页内嵌未读通知", "在对应常用应用名称下方显示最新消息摘要",
                prefs.getBoolean("inline_notif", true), host::onInlineNotifChanged);

        content.addView(gGesture);

        // ==================== 分组 4：工具、隐私与数据 ====================
        addGroupTitle(ctx, tf, host, theme, dp, content, "工具、隐私与数据");
        LinearLayout gTools = makeGroupCard(ctx, theme, dp);

        addActionRow(ctx, tf, host, theme, dp, gTools, VectorGlyphView.IC_NOTE, "桌面速记便签", "编辑或清空取件码与待办备忘", "", () -> {
            dlg.dismiss();
            host.openStickyNoteEditor();
        });

        addDivider(ctx, theme, dp, gTools);
        addActionRow(ctx, tf, host, theme, dp, gTools, VectorGlyphView.IC_FOLDER, "新建胶囊文件夹", "将同类应用归纳到弹出式胶囊", "", () -> {
            dlg.dismiss();
            host.openCreateFolder();
        });

        addDivider(ctx, theme, dp, gTools);
        addActionRow(ctx, tf, host, theme, dp, gTools, VectorGlyphView.IC_EYE_OFF, "已隐藏的应用", "查看并恢复隐藏的桌面应用",
                host.getHiddenCount() + " 个", () -> {
            dlg.dismiss();
            host.openHiddenApps();
        });

        addDivider(ctx, theme, dp, gTools);
        addSwitchRow(ctx, tf, host, theme, dp, gTools, VectorGlyphView.IC_SHIELD, "隐藏应用生物识别锁", "查看隐藏应用前需验证指纹/面容或锁屏密码",
                prefs.getBoolean("biometric_hide", false), host::onBiometricHideChanged);

        addDivider(ctx, theme, dp, gTools);
        addActionRow(ctx, tf, host, theme, dp, gTools, VectorGlyphView.IC_BACKUP, "导出本地配置备份", "将常用、文件夹与外观偏好导出为 JSON", "", () -> {
            dlg.dismiss();
            host.exportBackup();
        });

        addDivider(ctx, theme, dp, gTools);
        addActionRow(ctx, tf, host, theme, dp, gTools, VectorGlyphView.IC_BACKUP, "导入本地配置备份", "从 JSON 备份文件一键还原桌面布局", "", () -> {
            dlg.dismiss();
            host.importBackup();
        });

        content.addView(gTools);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 限制最大高度为屏幕 86%，保留顶部壁纸透出感
        int maxH = Math.round(ctx.getResources().getDisplayMetrics().heightPixels * 0.86f);
        dlg.setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH));

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxH);
            w.setDimAmount(0.42f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        dlg.show();
        return dlg;
    }

    // ---------- 状态实时检测 ----------
    private static boolean isDefaultHomeApp(Context ctx) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = ctx.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && ctx.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isIgnoringBatteryOpt(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isNotificationListenerEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private static int countHealthyItems(Context ctx, SharedPreferences prefs) {
        int ok = 0;
        if (isDefaultHomeApp(ctx)) ok++;
        if (prefs.getBoolean("keep_alive", true)) ok++;
        if (isIgnoringBatteryOpt(ctx)) ok++;
        if (GestureLockService.isRunning()) ok++;
        if (isNotificationListenerEnabled(ctx)) ok++;
        return ok;
    }

    private static void addExpandToggleRow(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp,
                                           LinearLayout card, String label, final LinearLayout targetBox) {
        addDivider(ctx, theme, dp, card);
        LinearLayout row = makeBaseRow(ctx, dp);
        TextView titleTv = makeText(ctx, tf, host.sp(13.2f), theme.accent, true, label);
        row.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView stateTv = makeText(ctx, tf, host.sp(12.2f), theme.textSecondary, false, "展开 ›");
        row.addView(stateTv);
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            boolean nextVis = targetBox.getVisibility() != View.VISIBLE;
            targetBox.setVisibility(nextVis ? View.VISIBLE : View.GONE);
            stateTv.setText(nextVis ? "收起 ▴" : "展开 ›");
        });
        card.addView(row);
    }

    // ---------- 现代设置卡片构建器 ----------
    private static void addGroupTitle(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp, LinearLayout parent, String title) {
        TextView tv = makeText(ctx, tf, host.sp(12.2f), theme.accent, true, title);
        tv.setLetterSpacing(0.03f);
        tv.setPadding(Math.round(8 * dp), Math.round(14 * dp), 0, Math.round(6 * dp));
        parent.addView(tv);
    }

    private static LinearLayout makeGroupCard(Context ctx, AuroraTheme theme, float dp) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.isLight ? 0xB8FFFFFF : 0x1AFFFFFF);
        bg.setStroke(Math.max(1, Math.round(0.8f * dp)), theme.isLight ? 0x66FFFFFF : 0x24FFFFFF);
        bg.setCornerRadius(18f * dp);
        card.setBackground(bg);
        return card;
    }

    private static void addDivider(Context ctx, AuroraTheme theme, float dp, LinearLayout card) {
        View d = new View(ctx);
        d.setBackgroundColor(theme.isLight ? 0x10141824 : 0x14FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(0.75f * dp)));
        lp.leftMargin = Math.round(48 * dp);
        lp.rightMargin = Math.round(14 * dp);
        card.addView(d, lp);
    }

    private static void addActionRow(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp,
                                     LinearLayout card, int iconType, String title, String subtitle,
                                     String trailingValue, final Runnable onClick) {
        LinearLayout row = makeBaseRow(ctx, dp);
        row.addView(makeIconWrap(ctx, theme, dp, iconType));
        row.addView(makeTitleCol(ctx, tf, host, theme, dp, title, subtitle),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (trailingValue != null && !trailingValue.isEmpty()) {
            TextView valTv = makeText(ctx, tf, host.sp(12.2f), theme.textSecondary, false, trailingValue);
            valTv.setPadding(Math.round(6 * dp), 0, Math.round(4 * dp), 0);
            row.addView(valTv);
        }
        TextView chev = makeText(ctx, tf, host.sp(16f), theme.textSecondary, false, "›");
        chev.setAlpha(0.7f);
        row.addView(chev);

        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClick.run();
        });
        card.addView(row);
    }

    interface BoolCallback { void onChanged(boolean value); }

    private static void addSwitchRow(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp,
                                     LinearLayout card, int iconType, String title, String subtitle,
                                     boolean initial, final BoolCallback cb) {
        LinearLayout row = makeBaseRow(ctx, dp);
        row.addView(makeIconWrap(ctx, theme, dp, iconType));
        row.addView(makeTitleCol(ctx, tf, host, theme, dp, title, subtitle),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(ctx);
        sw.setChecked(initial);
        styleSwitch(sw, theme);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cb.onChanged(isChecked);
        });
        row.setOnClickListener(v -> sw.toggle());
        row.addView(sw);
        card.addView(row);
    }

    interface SliderCallback { String onValue(int progress); }

    private static void addSliderRow(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp,
                                     LinearLayout card, int iconType, String title, String initialValue,
                                     int max, int initialProgress, final SliderCallback cb) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Math.round(14 * dp), Math.round(11 * dp), Math.round(14 * dp), Math.round(10 * dp));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(makeIconWrap(ctx, theme, dp, iconType));
        TextView titleTv = makeText(ctx, tf, host.sp(14.2f), theme.textPrimary, false, title);
        top.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView valTv = makeText(ctx, tf, host.sp(12.5f), theme.accent, true, initialValue);
        valTv.setFontFeatureSettings("tnum");
        top.addView(valTv);
        wrap.addView(top);

        SeekBar bar = new SeekBar(ctx);
        bar.setMax(max);
        bar.setProgress(initialProgress);
        if (Build.VERSION.SDK_INT >= 21) {
            ColorStateList accentList = ColorStateList.valueOf(theme.accent);
            bar.setThumbTintList(accentList);
            bar.setProgressTintList(accentList);
        }
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    valTv.setText(cb.onValue(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Math.round(4 * dp);
        blp.leftMargin = Math.round(30 * dp);
        wrap.addView(bar, blp);

        card.addView(wrap);
    }

    interface BoolSupplier { boolean get(); }

    private static Runnable addHealthCheckRow(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp,
                                              LinearLayout card, int iconType, String title, String subtitle,
                                              final BoolSupplier checker, final String actionLabel, final Runnable onAction) {
        LinearLayout row = makeBaseRow(ctx, dp);
        row.addView(makeIconWrap(ctx, theme, dp, iconType));
        row.addView(makeTitleCol(ctx, tf, host, theme, dp, title, subtitle),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView badge = makeText(ctx, tf, host.sp(11.6f), theme.accent, true, "");
        badge.setPadding(Math.round(10 * dp), Math.round(5 * dp), Math.round(10 * dp), Math.round(5 * dp));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Math.round(8 * dp);
        row.addView(badge, blp);

        Runnable refresh = () -> {
            boolean ok = checker.get();
            if (ok) {
                badge.setText("✓ 已就绪");
                badge.setTextColor(theme.isLight ? 0xFF059669 : 0xFF34D399);
                GradientDrawable g = new GradientDrawable();
                g.setColor(theme.isLight ? 0x1A10B981 : 0x2634D399);
                g.setCornerRadius(12 * dp);
                badge.setBackground(g);
            } else {
                badge.setText(actionLabel + " ›");
                badge.setTextColor(theme.isLight ? 0xFFD97706 : 0xFFFBBF24);
                GradientDrawable g = new GradientDrawable();
                g.setColor(theme.isLight ? 0x1AF59E0B : 0x28F59E0B);
                g.setStroke(Math.max(1, Math.round(0.8f * dp)), 0x55F59E0B);
                g.setCornerRadius(12 * dp);
                badge.setBackground(g);
            }
        };
        refresh.run();

        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onAction.run();
        });
        card.addView(row);
        return refresh;
    }

    private static LinearLayout makeBaseRow(Context ctx, float dp) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(14 * dp), Math.round(12 * dp), Math.round(14 * dp), Math.round(12 * dp));
        return row;
    }

    private static LinearLayout makeTitleCol(Context ctx, Typeface tf, Host host, AuroraTheme theme, float dp, String title, String subtitle) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = makeText(ctx, tf, host.sp(14.2f), theme.textPrimary, false, title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = makeText(ctx, tf, host.sp(11.4f), theme.textSecondary, false, subtitle);
            s.setPadding(0, Math.round(2 * dp), Math.round(6 * dp), 0);
            s.setMaxLines(2);
            s.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(s);
        }
        return col;
    }

    private static View makeIconWrap(Context ctx, AuroraTheme theme, float dp, int iconType) {
        FrameLayout wrap = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((theme.accent & 0x00FFFFFF) | (theme.isLight ? 0x16000000 : 0x26000000));
        bg.setCornerRadius(9 * dp);
        wrap.setBackground(bg);

        VectorGlyphView iv = new VectorGlyphView(ctx, iconType, theme.accent, dp);
        wrap.addView(iv, new FrameLayout.LayoutParams(Math.round(16 * dp), Math.round(16 * dp), Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(28 * dp), Math.round(28 * dp));
        lp.rightMargin = Math.round(12 * dp);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private static void styleSwitch(Switch sw, AuroraTheme theme) {
        if (Build.VERSION.SDK_INT >= 23) {
            int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
            };
            sw.setThumbTintList(new ColorStateList(states, new int[]{
                theme.accent,
                theme.isLight ? 0xFFCBD5E1 : 0xFF94A3B8
            }));
            sw.setTrackTintList(new ColorStateList(states, new int[]{
                (theme.accent & 0x00FFFFFF) | 0x66000000,
                theme.isLight ? 0x28141824 : 0x33FFFFFF
            }));
        }
    }

    private static TextView makeText(Context ctx, Typeface tf, float sp, int color, boolean bold, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTypeface(bold ? Typeface.create(tf, Typeface.BOLD) : tf);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return tv;
    }
}
