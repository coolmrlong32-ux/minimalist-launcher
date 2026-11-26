package com.niagara.local;

import android.app.Activity;
import android.app.Notification;
import android.content.pm.ShortcutInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 原生 Niagara 统一锚点 Pop-up 浮层控制器 (PopupCardController v2.8.0)：
 * - 将应用右滑 Pop-up、应用长按编辑工具条、App Pop-up 关联子应用集合与文件夹 Pop-up 统一为同一视觉语言；
 * - 全面采用自绘线性矢量图标，彻底淘汰满屏 Emoji 三列大抽屉；
 * - Android 12+ 展开浮层自动启用原生 RenderEffect 背景模糊，快捷回复弹出软键盘时卡片自动上移避让；
 * - 文件夹头部支持编辑应用（含去重）、重命名、内部排序、切换「点击直开首项 / 点开浮层」与删除。
 */
final class PopupCardController {

    interface Host {
        Activity getActivity();
        FrameLayout getWorkspaceRoot();
        View[] getBlurTargets();
        AuroraTheme getThemeObj();
        Typeface getTypeface();
        float getDp();
        float sp(float size);
        int getTopInsetPx();
        int getNavBottomPx();
        Drawable getAppIcon(MainActivity.AppEntry app);
        View findRowIconView(View rowView);
        boolean isFavorite(MainActivity.AppEntry app);
        boolean isInlineNotifEnabled();
        boolean isVirtualSettingsApp(MainActivity.AppEntry app);
        List<ShortcutInfo> queryPopupShortcuts(MainActivity.AppEntry app);
        Drawable loadShortcutIcon(ShortcutInfo sc);
        void startPopupShortcut(ShortcutInfo sc, MainActivity.AppEntry app);
        void launchApp(MainActivity.AppEntry app, View sourceView);
        void toggleFavorite(MainActivity.AppEntry app);
        void openRenameApp(MainActivity.AppEntry app);
        void openCustomAppIcon(MainActivity.AppEntry app);
        void openAppDetails(MainActivity.AppEntry app);
        void hideApp(MainActivity.AppEntry app);
        void uninstallApp(MainActivity.AppEntry app);
        void openAddAppToFolder(MainActivity.AppEntry app);
        void openControlCenter();
        void hideSoftKeyboard();
        void markLaunchedExternalApp(boolean launched);
        List<MainActivity.AppEntry> getAppPopupChildren(MainActivity.AppEntry parent);
        void openEditAppPopupChildren(MainActivity.AppEntry parent);
        void removeAppPopupChild(MainActivity.AppEntry parent, MainActivity.AppEntry child);
        JSONObject getFolderJson(int folderIdx);
        MainActivity.AppEntry findAppByKey(String idKey);
        void openEditFolderApps(int folderIdx);
        void openRenameFolder(int folderIdx);
        void openReorderFolderApps(int folderIdx);
        boolean toggleFolderTapLaunchFirst(int folderIdx);
        void deleteFolder(int folderIdx);
    }

    private final Host host;
    private FrameLayout activeScrim;
    private int touchSlopPx = 0;

    PopupCardController(Host host) {
        this.host = host;
    }

    boolean isShowing() {
        return activeScrim != null;
    }

    void dismiss(boolean animate) {
        final FrameLayout scrim = activeScrim;
        if (scrim == null) return;
        activeScrim = null;
        applyBackdropBlur(false);
        host.hideSoftKeyboard();
        final Runnable remove = () -> {
            ViewGroup p = (ViewGroup) scrim.getParent();
            if (p != null) p.removeView(scrim);
        };
        if (animate) {
            scrim.animate().alpha(0f).setDuration(120).withEndAction(remove).start();
        } else {
            scrim.animate().cancel();
            remove.run();
        }
    }

    private void applyBackdropBlur(boolean enable) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            float radius = 12f * host.getDp();
            android.graphics.RenderEffect fx = enable
                    ? android.graphics.RenderEffect.createBlurEffect(
                            radius, radius, android.graphics.Shader.TileMode.CLAMP)
                    : null;
            View[] targets = host.getBlurTargets();
            if (targets != null) {
                for (View v : targets) {
                    if (v != null) v.setRenderEffect(fx);
                }
            }
        } catch (Throwable ignored) {}
    }

    private int dp(float v) {
        return Math.round(v * host.getDp());
    }

    private TextView makeText(String s, float spSize, int color, boolean bold) {
        Activity act = host.getActivity();
        TextView tv = new TextView(act);
        tv.setText(s);
        tv.setTextColor(color);
        Typeface tf = host.getTypeface();
        tv.setTypeface(bold ? Typeface.create(tf, Typeface.BOLD) : tf);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, host.sp(spSize));
        return tv;
    }

    private GradientDrawable popupCardBackground() {
        AuroraTheme theme = host.getThemeObj();
        GradientDrawable g = new GradientDrawable();
        g.setColor(theme.isLight ? 0xEEF4F6F9 : 0xEB181B22);
        g.setCornerRadius(dp(20));
        g.setStroke(Math.max(1, Math.round(0.6f * host.getDp())), theme.isLight ? 0x80FFFFFF : 0x28FFFFFF);
        return g;
    }

    /**
     * 展示应用统一锚点 Pop-up 浮层。
     * @param showFullTools true 时默认展开线性矢量编辑工具条（长按入口），false 时默认折叠（右滑入口，点 ⋯ 原地展开）。
     */
    void showAppPopup(final View anchorView, final MainActivity.AppEntry app, final boolean showFullTools) {
        final Activity act = host.getActivity();
        final FrameLayout root = host.getWorkspaceRoot();
        if (app == null || anchorView == null || root == null) return;
        if (host.isVirtualSettingsApp(app)) {
            host.openControlCenter();
            return;
        }
        dismiss(false);

        final AuroraTheme theme = host.getThemeObj();
        final String pkg = app.component != null ? app.component.getPackageName() : "";

        final LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(10));

        final ScrollView card = new ScrollView(act);
        final Runnable[] relayoutCard = new Runnable[1];

        // 1. 头部：图标 + 名称（点开主应用） + 紧凑线性矢量按钮（常用 / 集合 / 展开工具条）
        LinearLayout hdr = new LinearLayout(act);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0, 0, 0, dp(4));

        LinearLayout titleTap = new LinearLayout(act);
        titleTap.setOrientation(LinearLayout.HORIZONTAL);
        titleTap.setGravity(Gravity.CENTER_VERTICAL);
        ImageView hIcon = new ImageView(act);
        hIcon.setImageDrawable(host.getAppIcon(app));
        LinearLayout.LayoutParams hiLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        hiLp.rightMargin = dp(10);
        titleTap.addView(hIcon, hiLp);

        TextView hTitle = makeText(app.label, 15.2f, theme.textPrimary, true);
        hTitle.setSingleLine(true);
        hTitle.setEllipsize(TextUtils.TruncateAt.END);
        titleTap.addView(hTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleTap.setOnClickListener(v -> {
            dismiss(true);
            host.launchApp(app, anchorView);
        });
        hdr.addView(titleTap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final boolean isFav = host.isFavorite(app);
        hdr.addView(makeHeaderIconBtn(isFav ? PopupGlyphView.IC_STAR_FILLED : PopupGlyphView.IC_STAR,
                isFav ? theme.accent : theme.textSecondary, () -> {
                    dismiss(true);
                    host.toggleFavorite(app);
                }));
        hdr.addView(makeHeaderIconBtn(PopupGlyphView.IC_PLUS, theme.textSecondary, () -> {
            dismiss(false);
            host.openEditAppPopupChildren(app);
        }));

        final LinearLayout toolsPanel = buildAppEditToolbar(app, isFav);
        toolsPanel.setVisibility(showFullTools ? View.VISIBLE : View.GONE);

        final FrameLayout moreToggleBtn = makeHeaderIconBtn(PopupGlyphView.IC_MORE,
                showFullTools ? theme.accent : theme.textSecondary, null);
        moreToggleBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            boolean nextVis = toolsPanel.getVisibility() != View.VISIBLE;
            toolsPanel.setVisibility(nextVis ? View.VISIBLE : View.GONE);
            View g = moreToggleBtn.getChildAt(0);
            if (g instanceof PopupGlyphView) {
                ((PopupGlyphView) g).setColor(nextVis ? theme.accent : theme.textSecondary);
            }
            if (relayoutCard[0] != null) relayoutCard[0].run();
        });
        hdr.addView(moreToggleBtn);
        content.addView(hdr);

        // 1.5 可折叠线性矢量编辑工具条
        content.addView(toolsPanel);

        // 2. 未读通知区（至多 3 条）
        final List<NotificationHubService.NotifItem> notifs = NotificationHubService.getAllByPackage(pkg);
        if (!notifs.isEmpty()) {
            LinearLayout nHdr = buildSectionHeader(PopupGlyphView.IC_BELL, "未读通知 " + notifs.size(),
                    "全部清除", () -> {
                        int cleared = NotificationHubService.cancelAllForPackage(pkg);
                        Toast.makeText(act, cleared > 0 ? "已清除全部通知" : "没有可清除的通知", Toast.LENGTH_SHORT).show();
                        dismiss(true);
                    });
            content.addView(nHdr);

            int shown = 0;
            for (NotificationHubService.NotifItem n : notifs) {
                if (shown++ >= 3) break;
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(5);
                content.addView(buildPopupNotifRow(n), rlp);
            }
        }

        // 3. App Pop-up 关联子应用集合
        final List<MainActivity.AppEntry> popupChildren = host.getAppPopupChildren(app);
        if (!popupChildren.isEmpty()) {
            content.addView(buildSectionHeader(PopupGlyphView.IC_LAYERS, "关联集合", "管理", () -> {
                dismiss(false);
                host.openEditAppPopupChildren(app);
            }));
            for (final MainActivity.AppEntry child : popupChildren) {
                content.addView(buildMemberAppRow(child, v -> {
                    dismiss(true);
                    host.launchApp(child, v);
                }, v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    host.removeAppPopupChild(app, child);
                    Toast.makeText(act, "已从「" + app.label + "」集合移出：" + child.label, Toast.LENGTH_SHORT).show();
                    showAppPopup(anchorView, app, showFullTools);
                    return true;
                }));
            }
        }

        // 4. 原生动态快捷方式（至多 4 个）
        final List<ShortcutInfo> shortcuts = host.queryPopupShortcuts(app);
        if (!shortcuts.isEmpty()) {
            content.addView(buildSectionHeader(PopupGlyphView.IC_SHORTCUT, "快捷方式", null, null));
            for (final ShortcutInfo sc : shortcuts) {
                LinearLayout sRow = new LinearLayout(act);
                sRow.setOrientation(LinearLayout.HORIZONTAL);
                sRow.setGravity(Gravity.CENTER_VERTICAL);
                sRow.setPadding(dp(2), dp(7), dp(4), dp(7));
                Drawable sIcon = host.loadShortcutIcon(sc);
                if (sIcon != null) {
                    ImageView siv = new ImageView(act);
                    siv.setImageDrawable(sIcon);
                    LinearLayout.LayoutParams silp = new LinearLayout.LayoutParams(dp(22), dp(22));
                    silp.rightMargin = dp(10);
                    sRow.addView(siv, silp);
                }
                TextView sLabel = makeText(String.valueOf(sc.getShortLabel()), 13.4f, theme.textPrimary, false);
                sLabel.setSingleLine(true);
                sLabel.setEllipsize(TextUtils.TruncateAt.END);
                sRow.addView(sLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                sRow.setOnClickListener(v -> {
                    dismiss(true);
                    host.startPopupShortcut(sc, app);
                });
                content.addView(sRow);
            }
        }

        // 5. 空集合轻量引导（仅在无通知、无子应用、无快捷方式且未展开编辑栏时呈现）
        if (notifs.isEmpty() && popupChildren.isEmpty() && shortcuts.isEmpty() && !showFullTools) {
            LinearLayout addRow = new LinearLayout(act);
            addRow.setOrientation(LinearLayout.HORIZONTAL);
            addRow.setGravity(Gravity.CENTER_VERTICAL);
            addRow.setBackground(theme.subtleGhostPill(host.getDp(), 12));
            addRow.setPadding(dp(10), dp(8), dp(10), dp(8));
            PopupGlyphView plusIc = new PopupGlyphView(act, PopupGlyphView.IC_PLUS, theme.accent, host.getDp());
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(14), dp(14));
            plp.rightMargin = dp(8);
            addRow.addView(plusIc, plp);
            TextView hint = makeText("添加关联子应用到 Pop-up 集合", 12.2f, theme.textSecondary, false);
            hint.setSingleLine(true);
            hint.setEllipsize(TextUtils.TruncateAt.END);
            addRow.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            addRow.setOnClickListener(v -> {
                dismiss(false);
                host.openEditAppPopupChildren(app);
            });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = dp(6);
            content.addView(addRow, alp);
        }

        presentAnchoredCard(anchorView, card, content, true, relayoutCard);
    }

    /**
     * 展示原生 Niagara 风格文件夹锚点 Pop-up 浮层（点击 / 右滑 / 长按统一体系）。
     */
    void showFolderPopup(final View anchorView, final int folderIdx, final boolean showEditTools) {
        final Activity act = host.getActivity();
        final FrameLayout root = host.getWorkspaceRoot();
        final JSONObject fo = host.getFolderJson(folderIdx);
        if (fo == null || anchorView == null || root == null) return;
        dismiss(false);

        final AuroraTheme theme = host.getThemeObj();
        final LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(10));

        final ScrollView card = new ScrollView(act);
        final Runnable[] relayoutCard = new Runnable[1];

        // 头部：文件夹名称 + 快速添加应用 + 展开文件夹工具条
        LinearLayout hdr = new LinearLayout(act);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0, 0, 0, dp(4));

        PopupGlyphView fIcon = new PopupGlyphView(act, PopupGlyphView.IC_FOLDER, theme.accent, host.getDp());
        LinearLayout.LayoutParams fiLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        fiLp.rightMargin = dp(8);
        hdr.addView(fIcon, fiLp);

        TextView title = makeText(fo.optString("name", "文件夹"), 15.2f, theme.textPrimary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        hdr.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hdr.addView(makeHeaderIconBtn(PopupGlyphView.IC_PLUS, theme.textSecondary, () -> {
            dismiss(false);
            host.openEditFolderApps(folderIdx);
        }));

        final LinearLayout folderTools = buildFolderEditToolbar(folderIdx, fo.optBoolean("tapLaunchFirst", false));
        folderTools.setVisibility(showEditTools ? View.VISIBLE : View.GONE);

        final FrameLayout moreBtn = makeHeaderIconBtn(PopupGlyphView.IC_MORE,
                showEditTools ? theme.accent : theme.textSecondary, null);
        moreBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            boolean nextVis = folderTools.getVisibility() != View.VISIBLE;
            folderTools.setVisibility(nextVis ? View.VISIBLE : View.GONE);
            View g = moreBtn.getChildAt(0);
            if (g instanceof PopupGlyphView) {
                ((PopupGlyphView) g).setColor(nextVis ? theme.accent : theme.textSecondary);
            }
            if (relayoutCard[0] != null) relayoutCard[0].run();
        });
        hdr.addView(moreBtn);
        content.addView(hdr);

        content.addView(folderTools);

        JSONArray apps = fo.optJSONArray("apps");
        int count = 0;
        if (apps != null) {
            for (int i = 0; i < apps.length(); i++) {
                final MainActivity.AppEntry member = host.findAppByKey(apps.optString(i));
                if (member == null) continue;
                count++;
                content.addView(buildMemberAppRow(member, v -> {
                    dismiss(true);
                    host.launchApp(member, v);
                }, v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAppPopup(anchorView, member, true);
                    return true;
                }));
            }
        }
        if (count == 0) {
            TextView empty = makeText("文件夹为空，点右上角 + 选择应用", 12.4f, theme.textSecondary, false);
            empty.setPadding(dp(2), dp(10), dp(2), dp(8));
            content.addView(empty);
        }

        presentAnchoredCard(anchorView, card, content, false, relayoutCard);
    }

    private LinearLayout buildAppEditToolbar(final MainActivity.AppEntry app, final boolean isFav) {
        Activity act = host.getActivity();
        AuroraTheme theme = host.getThemeObj();
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(theme.subtleGhostPill(host.getDp(), 14));
        panel.setPadding(dp(6), dp(8), dp(6), dp(6));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(4);
        plp.bottomMargin = dp(6);
        panel.setLayoutParams(plp);

        LinearLayout row1 = new LinearLayout(act);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(makeToolCell(isFav ? PopupGlyphView.IC_STAR_FILLED : PopupGlyphView.IC_STAR,
                isFav ? "移出常用" : "加入常用", isFav ? theme.accent : theme.textPrimary, () -> {
                    dismiss(true);
                    host.toggleFavorite(app);
                }));
        row1.addView(makeToolCell(PopupGlyphView.IC_LAYERS, "关联集合", theme.textPrimary, () -> {
            dismiss(false);
            host.openEditAppPopupChildren(app);
        }));
        row1.addView(makeToolCell(PopupGlyphView.IC_EDIT, "重命名", theme.textPrimary, () -> {
            dismiss(false);
            host.openRenameApp(app);
        }));
        row1.addView(makeToolCell(PopupGlyphView.IC_ICON, "换图标", theme.textPrimary, () -> {
            dismiss(false);
            host.openCustomAppIcon(app);
        }));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(act);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2lp.topMargin = dp(4);
        row2.addView(makeToolCell(PopupGlyphView.IC_FOLDER, "进文件夹", theme.textPrimary, () -> {
            dismiss(false);
            host.openAddAppToFolder(app);
        }));
        row2.addView(makeToolCell(PopupGlyphView.IC_INFO, "详情", theme.textPrimary, () -> {
            dismiss(true);
            host.openAppDetails(app);
        }));
        row2.addView(makeToolCell(PopupGlyphView.IC_EYE_OFF, "隐藏", theme.textPrimary, () -> {
            dismiss(true);
            host.hideApp(app);
        }));
        row2.addView(makeToolCell(PopupGlyphView.IC_TRASH, "卸载", 0xFFEF4444, () -> {
            dismiss(true);
            host.uninstallApp(app);
        }));
        panel.addView(row2, r2lp);

        return panel;
    }

    private LinearLayout buildFolderEditToolbar(final int folderIdx, final boolean initialTapLaunchFirst) {
        Activity act = host.getActivity();
        AuroraTheme theme = host.getThemeObj();
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(theme.subtleGhostPill(host.getDp(), 14));
        panel.setPadding(dp(4), dp(8), dp(4), dp(6));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(4);
        plp.bottomMargin = dp(6);
        panel.setLayoutParams(plp);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(makeToolCell(PopupGlyphView.IC_PLUS, "选应用", theme.textPrimary, () -> {
            dismiss(false);
            host.openEditFolderApps(folderIdx);
        }));
        row.addView(makeToolCell(PopupGlyphView.IC_EDIT, "重命名", theme.textPrimary, () -> {
            dismiss(false);
            host.openRenameFolder(folderIdx);
        }));
        row.addView(makeToolCell(PopupGlyphView.IC_SORT, "排序", theme.textPrimary, () -> {
            dismiss(false);
            host.openReorderFolderApps(folderIdx);
        }));

        final boolean[] tapFirst = new boolean[]{initialTapLaunchFirst};
        final LinearLayout tapModeCell = makeToolCell(PopupGlyphView.IC_SHORTCUT,
                tapFirst[0] ? "直开首项" : "点开浮层",
                tapFirst[0] ? theme.accent : theme.textPrimary, null);
        tapModeCell.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            tapFirst[0] = host.toggleFolderTapLaunchFirst(folderIdx);
            PopupGlyphView gv = (PopupGlyphView) tapModeCell.getChildAt(0);
            TextView tv = (TextView) tapModeCell.getChildAt(1);
            int c = tapFirst[0] ? theme.accent : theme.textPrimary;
            gv.setColor(c);
            tv.setText(tapFirst[0] ? "直开首项" : "点开浮层");
            tv.setTextColor(c);
            Toast.makeText(act, tapFirst[0]
                    ? "已切换：轻点直开首个应用，右滑展开文件夹"
                    : "已切换：轻点与右滑均展开文件夹", Toast.LENGTH_SHORT).show();
        });
        row.addView(tapModeCell);

        row.addView(makeToolCell(PopupGlyphView.IC_TRASH, "删除", 0xFFEF4444, () -> {
            dismiss(true);
            host.deleteFolder(folderIdx);
        }));
        panel.addView(row);
        return panel;
    }

    private FrameLayout makeHeaderIconBtn(int glyphType, int color, final Runnable onClick) {
        Activity act = host.getActivity();
        FrameLayout wrap = new FrameLayout(act);
        PopupGlyphView gv = new PopupGlyphView(act, glyphType, color, host.getDp());
        wrap.addView(gv, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        wrap.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        if (onClick != null) {
            wrap.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                onClick.run();
            });
        }
        return wrap;
    }

    private LinearLayout makeToolCell(int glyphType, String label, int color, final Runnable onClick) {
        Activity act = host.getActivity();
        LinearLayout cell = new LinearLayout(act);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));

        PopupGlyphView gv = new PopupGlyphView(act, glyphType, color, host.getDp());
        cell.addView(gv, new LinearLayout.LayoutParams(dp(17), dp(17)));

        TextView tv = makeText(label, 10.4f, color, false);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(0, dp(3), 0, 0);
        cell.addView(tv);

        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (onClick != null) {
            cell.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                onClick.run();
            });
        }
        return cell;
    }

    private LinearLayout buildSectionHeader(int glyphType, String title, String trailingAction, final Runnable onTrailing) {
        Activity act = host.getActivity();
        AuroraTheme theme = host.getThemeObj();
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(9), dp(2), dp(3));

        PopupGlyphView gv = new PopupGlyphView(act, glyphType, theme.accent, host.getDp());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(13), dp(13));
        glp.rightMargin = dp(6);
        row.addView(gv, glp);

        TextView tv = makeText(title, 11.6f, theme.accent, true);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (trailingAction != null && onTrailing != null) {
            TextView actTv = makeText(trailingAction, 11.4f, theme.textSecondary, true);
            actTv.setPadding(dp(8), dp(2), dp(2), dp(2));
            actTv.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                onTrailing.run();
            });
            row.addView(actTv);
        }
        return row;
    }

    private LinearLayout buildMemberAppRow(final MainActivity.AppEntry a,
                                           View.OnClickListener onClick,
                                           View.OnLongClickListener onLongClick) {
        Activity act = host.getActivity();
        AuroraTheme theme = host.getThemeObj();
        LinearLayout aRow = new LinearLayout(act);
        aRow.setOrientation(LinearLayout.HORIZONTAL);
        aRow.setGravity(Gravity.CENTER_VERTICAL);
        aRow.setPadding(dp(2), dp(7), dp(4), dp(7));

        ImageView aiv = new ImageView(act);
        aiv.setImageDrawable(host.getAppIcon(a));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(30), dp(30));
        ilp.rightMargin = dp(11);
        aRow.addView(aiv, ilp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nameTv = makeText(a.label, 14.0f, theme.textPrimary, false);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(nameTv);

        String pkg = a.component != null ? a.component.getPackageName() : "";
        NotificationHubService.NotifItem notif = host.isInlineNotifEnabled()
                ? NotificationHubService.latestByPkg.get(pkg) : null;
        if (notif != null) {
            String preview = (notif.title.isEmpty() ? "" : notif.title + "：") + notif.text;
            TextView subTv = makeText(preview, 11.3f, theme.textSecondary, false);
            subTv.setSingleLine(true);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(subTv);
        }
        aRow.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (notif == null && NotificationHubService.activePackages.contains(pkg)) {
            View dot = new View(act);
            GradientDrawable dbg = new GradientDrawable();
            dbg.setShape(GradientDrawable.OVAL);
            dbg.setColor(theme.accent);
            dot.setBackground(dbg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
            dlp.leftMargin = dp(8);
            aRow.addView(dot, dlp);
        }

        if (onClick != null) aRow.setOnClickListener(onClick);
        if (onLongClick != null) aRow.setOnLongClickListener(onLongClick);
        return aRow;
    }

    /** 单条通知微卡片：标题/摘要/相对时间、矢量 × 与右滑清除、常规动作胶囊、内联快捷回复。 */
    private View buildPopupNotifRow(final NotificationHubService.NotifItem n) {
        final Activity act = host.getActivity();
        final AuroraTheme theme = host.getThemeObj();
        final LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(theme.subtleGhostPill(host.getDp(), 14));
        row.setPadding(dp(11), dp(8), dp(8), dp(8));

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        if (n.title != null && !n.title.isEmpty()) {
            TextView t = makeText(n.title, 12.8f, theme.textPrimary, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
        }
        if (n.text != null && !n.text.isEmpty()) {
            TextView t = makeText(n.text, 12.2f, theme.textPrimary, false);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
        }
        col.addView(makeText(NotificationHubService.formatRelativeTime(n.postTime), 10.6f, theme.textSecondary, false));
        top.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (n.isClearable) {
            FrameLayout xBtn = makeHeaderIconBtn(PopupGlyphView.IC_CLOSE, theme.textSecondary,
                    () -> dismissPopupNotifRow(row, n));
            top.addView(xBtn);
        }
        row.addView(top);

        if (n.plainActions != null && !n.plainActions.isEmpty()) {
            LinearLayout caps = new LinearLayout(act);
            caps.setOrientation(LinearLayout.HORIZONTAL);
            caps.setPadding(0, dp(6), 0, 0);
            for (final Notification.Action nAct : n.plainActions) {
                TextView cap = makeText(String.valueOf(nAct.title), 11.6f, theme.accent, true);
                cap.setBackground(theme.accentGlowPill(host.getDp(), 12));
                cap.setPadding(dp(10), dp(4), dp(10), dp(4));
                cap.setSingleLine(true);
                cap.setEllipsize(TextUtils.TruncateAt.END);
                cap.setOnClickListener(v -> {
                    try {
                        nAct.actionIntent.send();
                        Toast.makeText(act, "已执行：" + nAct.title, Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(act, "操作已失效", Toast.LENGTH_SHORT).show();
                    }
                    dismiss(true);
                });
                LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                capLp.rightMargin = dp(6);
                caps.addView(cap, capLp);
            }
            row.addView(caps);
        }

        if (NotificationHubService.getQuickReplyAction(n) != null) {
            LinearLayout replyRow = new LinearLayout(act);
            replyRow.setOrientation(LinearLayout.HORIZONTAL);
            replyRow.setGravity(Gravity.CENTER_VERTICAL);
            replyRow.setPadding(0, dp(4), 0, 0);
            final EditText replyIn = new EditText(act);
            replyIn.setHint("直接回复…");
            replyIn.setTextColor(theme.textPrimary);
            replyIn.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x88000000);
            replyIn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f);
            replyIn.setSingleLine(true);
            replyIn.setBackground(null);
            replyIn.setPadding(0, dp(4), dp(6), dp(4));
            replyRow.addView(replyIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView send = makeText("发送", 12.5f, theme.accent, true);
            send.setPadding(dp(8), dp(4), dp(6), dp(4));
            send.setOnClickListener(v -> {
                String msg = replyIn.getText().toString().trim();
                if (msg.isEmpty()) return;
                if (NotificationHubService.sendQuickReply(act, n, msg)) {
                    Toast.makeText(act, "已发送回复", Toast.LENGTH_SHORT).show();
                    dismiss(true);
                } else {
                    Toast.makeText(act, "回复失败，请进入应用回复", Toast.LENGTH_SHORT).show();
                }
            });
            replyRow.addView(send);
            row.addView(replyRow);
        }

        if (n.contentIntent != null) {
            row.setOnClickListener(v -> {
                try {
                    host.markLaunchedExternalApp(true);
                    n.contentIntent.send();
                    dismiss(true);
                } catch (Throwable t) {
                    host.markLaunchedExternalApp(false);
                }
            });
        }
        if (n.isClearable) attachSwipeToDismiss(row, n);
        return row;
    }

    private void dismissPopupNotifRow(final View row, NotificationHubService.NotifItem n) {
        if (!NotificationHubService.dismissNotification(n.key)) {
            Toast.makeText(host.getActivity(), "无法清除该通知", Toast.LENGTH_SHORT).show();
            row.animate().translationX(0f).alpha(1f).setDuration(160).start();
            return;
        }
        row.animate().translationX(row.getWidth()).alpha(0f).setDuration(160).withEndAction(() -> {
            ViewGroup p = (ViewGroup) row.getParent();
            if (p != null) p.removeView(row);
        }).start();
    }

    private void attachSwipeToDismiss(final View row, final NotificationHubService.NotifItem n) {
        final float[] down = new float[2];
        final boolean[] swiping = new boolean[1];
        row.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                down[0] = e.getRawX();
                down[1] = e.getRawY();
                swiping[0] = false;
                return false;
            }
            float dx = e.getRawX() - down[0];
            float dy = e.getRawY() - down[1];
            if (a == MotionEvent.ACTION_MOVE) {
                if (!swiping[0]) {
                    if (touchSlopPx <= 0) touchSlopPx = ViewConfiguration.get(host.getActivity()).getScaledTouchSlop();
                    if (dx > touchSlopPx && dx > Math.abs(dy) * 2.0f) {
                        swiping[0] = true;
                        v.setPressed(false);
                        v.cancelLongPress();
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (swiping[0]) {
                    v.setTranslationX(Math.max(0f, dx));
                    v.setAlpha(1f - Math.min(0.6f, dx / dp(180)));
                    return true;
                }
                return false;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (swiping[0]) {
                    swiping[0] = false;
                    if (a == MotionEvent.ACTION_UP && dx > dp(72)) {
                        dismissPopupNotifRow(v, n);
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(160).start();
                    }
                    return true;
                }
            }
            return false;
        });
    }

    private void presentAnchoredCard(final View anchorView, final ScrollView card, final LinearLayout content,
                                     final boolean anchorByIcon, final Runnable[] relayoutOut) {
        final Activity act = host.getActivity();
        final FrameLayout root = host.getWorkspaceRoot();
        final AuroraTheme theme = host.getThemeObj();

        card.setVerticalScrollBarEnabled(false);
        card.setOverScrollMode(View.OVER_SCROLL_NEVER);
        card.setBackground(popupCardBackground());
        card.setClipToOutline(true);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        card.setClickable(true);
        card.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout scrim = new FrameLayout(act);
        scrim.setBackgroundColor(theme.isLight ? 0x16000000 : 0x2E000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss(true));

        final int rootW = root.getWidth() > 0 ? root.getWidth() : act.getResources().getDisplayMetrics().widthPixels;
        final int rootH = root.getHeight() > 0 ? root.getHeight() : act.getResources().getDisplayMetrics().heightPixels;
        int[] rootLoc = new int[2];
        int[] aLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        anchorView.getLocationOnScreen(aLoc);
        final int anchorLeft = Math.round(aLoc[0] - anchorView.getTranslationX()) - rootLoc[0];
        final int anchorTop = aLoc[1] - rootLoc[1];
        final int anchorH = Math.max(anchorView.getHeight(), dp(42));

        int margin = dp(16);
        int left;
        final int width;
        if (anchorByIcon) {
            int iconCenterX = anchorLeft + dp(20);
            View iconView = host.findRowIconView(anchorView);
            if (iconView != null && iconView.getParent() instanceof View) {
                iconCenterX = anchorLeft + ((View) iconView.getParent()).getLeft()
                        + iconView.getLeft() + iconView.getWidth() / 2;
            }
            int minW = dp(256), maxW = dp(334);
            left = iconCenterX + dp(28);
            if (rootW - left - margin < minW) left = rootW - margin - minW;
            left = Math.max(dp(14), left);
            width = Math.max(minW, Math.min(maxW, rootW - left - margin));
        } else {
            width = Math.max(dp(256), Math.min(dp(328), rootW - dp(56)));
            left = Math.max(dp(16), Math.min(rootW - margin - width, dp(38)));
        }

        int topInset = host.getTopInsetPx();
        int botInset = host.getNavBottomPx();
        try {
            android.view.WindowInsets wi = root.getRootWindowInsets();
            if (wi != null) {
                if (Build.VERSION.SDK_INT >= 30) {
                    topInset = wi.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                    botInset = wi.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                } else {
                    topInset = wi.getSystemWindowInsetTop();
                    botInset = wi.getSystemWindowInsetBottom();
                }
            }
        } catch (Throwable ignored) {}
        final int availTop = topInset + dp(10);
        final int availBottom = rootH - botInset - dp(14);
        final int maxH = Math.max(dp(120), availBottom - availTop);

        final int[] curMetrics = new int[2]; // [finalTop, cardH]
        final int finalLeft = left;

        Runnable layoutCard = () -> {
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
            int ch = Math.min(maxH, card.getMeasuredHeight());
            int top = anchorTop - dp(6);
            if (top + ch > availBottom) {
                top = anchorTop + anchorH + dp(6) - ch;
            }
            top = Math.max(availTop, Math.min(top, availBottom - ch));
            curMetrics[0] = top;
            curMetrics[1] = ch;
            FrameLayout.LayoutParams lp = card.getLayoutParams() instanceof FrameLayout.LayoutParams
                    ? (FrameLayout.LayoutParams) card.getLayoutParams()
                    : new FrameLayout.LayoutParams(width, ch);
            lp.width = width;
            lp.height = ch;
            lp.leftMargin = finalLeft;
            lp.topMargin = top;
            card.setLayoutParams(lp);
        };
        if (relayoutOut != null && relayoutOut.length > 0) {
            relayoutOut[0] = layoutCard;
        }
        layoutCard.run();
        scrim.addView(card);

        scrim.setOnApplyWindowInsetsListener((sv, insets) -> {
            int imeBot = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    imeBot = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                } catch (Throwable ignored) {}
            } else {
                imeBot = insets.getSystemWindowInsetBottom();
            }
            int cardBottom = curMetrics[0] + curMetrics[1];
            int visibleBottom = rootH - imeBot - dp(12);
            float shiftY = 0f;
            if (imeBot > dp(100) && cardBottom > visibleBottom) {
                shiftY = -Math.min(cardBottom - visibleBottom, Math.max(0, curMetrics[0] - availTop));
            }
            card.animate().translationY(shiftY).setDuration(140).start();
            return insets;
        });

        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeScrim = scrim;
        applyBackdropBlur(true);

        card.setPivotX(0f);
        card.setPivotY(Math.max(0f, Math.min(curMetrics[1], anchorTop + anchorH * 0.5f - curMetrics[0])));
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setTranslationX(-dp(10));
        card.setAlpha(0f);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(145).start();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    // ---------- 极简线性矢量图标（零 Emoji） ----------
    private static final class PopupGlyphView extends View {
        static final int IC_STAR = 0;
        static final int IC_STAR_FILLED = 1;
        static final int IC_PLUS = 2;
        static final int IC_MORE = 3;
        static final int IC_EDIT = 4;
        static final int IC_ICON = 5;
        static final int IC_INFO = 6;
        static final int IC_EYE_OFF = 7;
        static final int IC_TRASH = 8;
        static final int IC_FOLDER = 9;
        static final int IC_LAYERS = 10;
        static final int IC_BELL = 11;
        static final int IC_SHORTCUT = 12;
        static final int IC_CLOSE = 13;
        static final int IC_SORT = 14;

        private final int type;
        private final float dp;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        PopupGlyphView(android.content.Context ctx, int type, int color, float dp) {
            super(ctx);
            this.type = type;
            this.dp = dp;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.45f * dp);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            fillP.setColor(color);
            fillP.setStyle(Paint.Style.FILL);
        }

        void setColor(int color) {
            p.setColor(color);
            fillP.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w * 0.5f, cy = h * 0.5f;
            float u = Math.min(w, h) * 0.38f;

            switch (type) {
                case IC_STAR:
                case IC_STAR_FILLED:
                    path.reset();
                    for (int i = 0; i < 10; i++) {
                        double a = Math.toRadians(-90 + i * 36);
                        float rad = (i % 2 == 0) ? u : u * 0.44f;
                        float x = cx + (float) Math.cos(a) * rad;
                        float y = cy + (float) Math.sin(a) * rad;
                        if (i == 0) path.moveTo(x, y);
                        else path.lineTo(x, y);
                    }
                    path.close();
                    c.drawPath(path, type == IC_STAR_FILLED ? fillP : p);
                    break;
                case IC_PLUS:
                    c.drawLine(cx - u * 0.85f, cy, cx + u * 0.85f, cy, p);
                    c.drawLine(cx, cy - u * 0.85f, cx, cy + u * 0.85f, p);
                    break;
                case IC_MORE:
                    float dr = 1.45f * dp;
                    c.drawCircle(cx - u * 0.72f, cy, dr, fillP);
                    c.drawCircle(cx, cy, dr, fillP);
                    c.drawCircle(cx + u * 0.72f, cy, dr, fillP);
                    break;
                case IC_EDIT:
                    c.drawLine(cx - u * 0.65f, cy + u * 0.65f, cx + u * 0.55f, cy - u * 0.55f, p);
                    c.drawLine(cx - u * 0.85f, cy + u * 0.85f, cx + u * 0.85f, cy + u * 0.85f, p);
                    break;
                case IC_ICON:
                    r.set(cx - u * 0.9f, cy - u * 0.9f, cx - 1f * dp, cy - 1f * dp);
                    c.drawRoundRect(r, 1.5f * dp, 1.5f * dp, p);
                    r.set(cx + 1f * dp, cy - u * 0.9f, cx + u * 0.9f, cy - 1f * dp);
                    c.drawOval(r, p);
                    r.set(cx - u * 0.9f, cy + 1f * dp, cx - 1f * dp, cy + u * 0.9f);
                    c.drawOval(r, p);
                    r.set(cx + 1f * dp, cy + 1f * dp, cx + u * 0.9f, cy + u * 0.9f);
                    c.drawRoundRect(r, 1.5f * dp, 1.5f * dp, p);
                    break;
                case IC_INFO:
                    c.drawCircle(cx, cy, u * 0.92f, p);
                    c.drawLine(cx, cy - u * 0.05f, cx, cy + u * 0.45f, p);
                    c.drawCircle(cx, cy - u * 0.42f, 0.95f * dp, fillP);
                    break;
                case IC_EYE_OFF:
                    c.drawOval(cx - u, cy - u * 0.52f, cx + u, cy + u * 0.52f, p);
                    c.drawLine(cx - u * 0.82f, cy + u * 0.82f, cx + u * 0.82f, cy - u * 0.82f, p);
                    break;
                case IC_TRASH:
                    c.drawLine(cx - u * 0.85f, cy - u * 0.55f, cx + u * 0.85f, cy - u * 0.55f, p);
                    c.drawLine(cx - u * 0.35f, cy - u * 0.85f, cx + u * 0.35f, cy - u * 0.85f, p);
                    r.set(cx - u * 0.62f, cy - u * 0.55f, cx + u * 0.62f, cy + u * 0.88f);
                    c.drawRoundRect(r, 1.8f * dp, 1.8f * dp, p);
                    break;
                case IC_FOLDER:
                    path.reset();
                    path.moveTo(cx - u, cy - u * 0.65f);
                    path.lineTo(cx - u * 0.18f, cy - u * 0.65f);
                    path.lineTo(cx + u * 0.06f, cy - u * 0.32f);
                    path.lineTo(cx + u, cy - u * 0.32f);
                    path.lineTo(cx + u, cy + u * 0.72f);
                    path.lineTo(cx - u, cy + u * 0.72f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                case IC_LAYERS:
                    path.reset();
                    path.moveTo(cx, cy - u * 0.82f);
                    path.lineTo(cx + u * 0.95f, cy - u * 0.25f);
                    path.lineTo(cx, cy + u * 0.30f);
                    path.lineTo(cx - u * 0.95f, cy - u * 0.25f);
                    path.close();
                    c.drawPath(path, p);
                    c.drawLine(cx - u * 0.88f, cy + u * 0.28f, cx, cy + u * 0.80f, p);
                    c.drawLine(cx, cy + u * 0.80f, cx + u * 0.88f, cy + u * 0.28f, p);
                    break;
                case IC_BELL:
                    r.set(cx - u * 0.7f, cy - u * 0.8f, cx + u * 0.7f, cy + u * 0.52f);
                    c.drawArc(r, 180, 180, false, p);
                    c.drawLine(cx - u * 0.7f, cy - u * 0.14f, cx - u * 0.7f, cy + u * 0.50f, p);
                    c.drawLine(cx + u * 0.7f, cy - u * 0.14f, cx + u * 0.7f, cy + u * 0.50f, p);
                    c.drawLine(cx - u * 0.92f, cy + u * 0.50f, cx + u * 0.92f, cy + u * 0.50f, p);
                    c.drawLine(cx - u * 0.22f, cy + u * 0.84f, cx + u * 0.22f, cy + u * 0.84f, p);
                    break;
                case IC_SHORTCUT:
                    path.reset();
                    path.moveTo(cx + u * 0.12f, cy - u * 0.92f);
                    path.lineTo(cx - u * 0.68f, cy + u * 0.08f);
                    path.lineTo(cx + u * 0.05f, cy + u * 0.08f);
                    path.lineTo(cx - u * 0.12f, cy + u * 0.92f);
                    path.lineTo(cx + u * 0.68f, cy - u * 0.08f);
                    path.lineTo(cx - u * 0.05f, cy - u * 0.08f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                case IC_SORT:
                    c.drawLine(cx - u * 0.9f, cy - u * 0.55f, cx + u * 0.9f, cy - u * 0.55f, p);
                    c.drawLine(cx - u * 0.9f, cy, cx + u * 0.55f, cy, p);
                    c.drawLine(cx - u * 0.9f, cy + u * 0.55f, cx + u * 0.2f, cy + u * 0.55f, p);
                    break;
                case IC_CLOSE:
                default:
                    c.drawLine(cx - u * 0.65f, cy - u * 0.65f, cx + u * 0.65f, cy + u * 0.65f, p);
                    c.drawLine(cx + u * 0.65f, cy - u * 0.65f, cx - u * 0.65f, cy + u * 0.65f, p);
                    break;
            }
        }
    }
}
