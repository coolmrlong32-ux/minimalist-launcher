package com.niagara.local;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通用应用多选与拖拽排序底部抽屉 (AppPickerSheet v2.9.0)：
 * - 统一替代 MainActivity 中原本 5 套重复编写的选常用/选Pop-up子应用/选文件夹应用及拖拽排序弹窗；
 * - 固定顶部（标题/搜索/字母筛选）+ 独立滚动列表 + 固定底部保存栏（高度上限 82% 屏高），单手不迷路；
 * - 全量零 Emoji 线性矢量视觉。
 */
final class AppPickerSheet {

    interface Host {
        Context getContext();
        AuroraTheme getThemeObj();
        Typeface getTypeface();
        float getDp();
        float sp(float size);
    }

    interface MultiSelectCallback {
        void onSave(List<String> orderedSelectedKeys);
    }

    interface ReorderCallback {
        void onSave(List<String> reorderedKeys);
    }

    static Dialog showMultiSelect(final Host host,
                                String title,
                                String subtitle,
                                final List<MainActivity.AppEntry> candidates,
                                final List<String> initialOrderedKeys,
                                final String secondaryLabel,
                                final Runnable onSecondaryClick,
                                final String savePrefix,
                                final MultiSelectCallback onSave) {
        final Context ctx = host.getContext();
        final AuroraTheme theme = host.getThemeObj();
        final Typeface tf = host.getTypeface();
        final float dp = host.getDp();

        final Set<String> selectedKeys = new LinkedHashSet<>(initialOrderedKeys);
        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(theme.sheetPanelBackground(dp));
        root.setPadding(dpx(dp, 20), dpx(dp, 12), dpx(dp, 20), dpx(dp, 20));
        root.addView(makeHandle(theme, dp, ctx));

        // 1. 固定头部：标题 + 已选数量徽章
        LinearLayout topHdr = new LinearLayout(ctx);
        topHdr.setOrientation(LinearLayout.HORIZONTAL);
        topHdr.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleTv = makeText(ctx, tf, host.sp(17.5f), theme.textPrimary, true, title);
        topHdr.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView badge = makeText(ctx, tf, host.sp(12f), theme.accent, true, "已选 " + selectedKeys.size() + " 个");
        badge.setBackground(theme.accentGlowPill(dp, 10));
        badge.setPadding(dpx(dp, 10), dpx(dp, 4), dpx(dp, 10), dpx(dp, 4));
        topHdr.addView(badge);
        root.addView(topHdr);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tip = makeText(ctx, tf, host.sp(11.8f), theme.textSecondary, false, subtitle);
            tip.setPadding(0, dpx(dp, 2), 0, dpx(dp, 6));
            root.addView(tip);
        }

        // 2. 固定搜索栏
        LinearLayout searchWrap = new LinearLayout(ctx);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setBackground(theme.sheetTileCard(dp, 12));
        searchWrap.setPadding(dpx(dp, 11), dpx(dp, 6), dpx(dp, 11), dpx(dp, 6));
        VectorGlyphView searchIc = new VectorGlyphView(ctx, VectorGlyphView.IC_SEARCH, theme.textSecondary, dp);
        LinearLayout.LayoutParams sicLp = new LinearLayout.LayoutParams(dpx(dp, 15), dpx(dp, 15));
        sicLp.rightMargin = dpx(dp, 8);
        searchWrap.addView(searchIc, sicLp);

        final EditText searchBox = new EditText(ctx);
        searchBox.setHint("按字母或应用名即时筛选…");
        searchBox.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x88000000);
        searchBox.setTextColor(theme.textPrimary);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, host.sp(13.2f));
        searchBox.setTypeface(tf);
        searchBox.setSingleLine(true);
        searchBox.setBackground(null);
        searchBox.setPadding(0, dpx(dp, 2), 0, dpx(dp, 2));
        searchBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchWrap.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.topMargin = dpx(dp, 6);
        root.addView(searchWrap, swLp);

        // 3. 固定字母筛选条
        final List<String> letters = new ArrayList<>();
        letters.add("全部");
        letters.add("已选");
        for (char c = 'A'; c <= 'Z'; c++) {
            for (MainActivity.AppEntry a : candidates) {
                if (a.groupChar == c) {
                    letters.add(String.valueOf(c));
                    break;
                }
            }
        }
        for (MainActivity.AppEntry a : candidates) {
            if (a.groupChar == '#') {
                letters.add("#");
                break;
            }
        }

        HorizontalScrollView letterScroll = new HorizontalScrollView(ctx);
        letterScroll.setHorizontalScrollBarEnabled(false);
        letterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout letterRow = new LinearLayout(ctx);
        letterRow.setOrientation(LinearLayout.HORIZONTAL);
        letterRow.setGravity(Gravity.CENTER_VERTICAL);
        letterRow.setPadding(0, dpx(dp, 8), 0, dpx(dp, 8));

        final String[] curFilter = new String[]{"全部"};
        final List<TextView> pills = new ArrayList<>();
        final LinearLayout gridContainer = new LinearLayout(ctx);
        gridContainer.setOrientation(LinearLayout.VERTICAL);

        final TextView saveBtn = makeBtn(ctx, tf, theme, dp, host.sp(13.2f), savePrefix + " (" + selectedKeys.size() + ")", null);
        saveBtn.setBackground(theme.solidAccentButton(dp, 14));
        saveBtn.setTextColor(0xFFFFFFFF);

        final Runnable refreshGrid = new Runnable() {
            @Override
            public void run() {
                gridContainer.removeAllViews();
                String filter = curFilter[0];
                String q = searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
                List<MainActivity.AppEntry> matched = new ArrayList<>();
                for (MainActivity.AppEntry a : candidates) {
                    if ("已选".equals(filter)) {
                        if (!selectedKeys.contains(a.idKey)) continue;
                    } else if (!"全部".equals(filter)) {
                        if (filter.length() == 1 && a.groupChar != filter.charAt(0)) continue;
                    }
                    if (!q.isEmpty()) {
                        boolean hit = (a.label != null && a.label.toLowerCase(Locale.ROOT).contains(q))
                                || (a.iniPy != null && a.iniPy.toLowerCase(Locale.ROOT).contains(q))
                                || (a.fullPy != null && a.fullPy.toLowerCase(Locale.ROOT).contains(q))
                                || (q.length() == 1 && Character.toUpperCase(q.charAt(0)) == a.groupChar);
                        if (!hit) continue;
                    }
                    matched.add(a);
                }

                if (matched.isEmpty()) {
                    TextView empty = makeText(ctx, tf, host.sp(12.5f), theme.textSecondary, false, "无匹配应用，请尝试其他字母或清空搜索");
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(0, dpx(dp, 22), 0, dpx(dp, 22));
                    gridContainer.addView(empty);
                    return;
                }

                List<TextView> btns = new ArrayList<>();
                for (final MainActivity.AppEntry app : matched) {
                    final boolean checked = selectedKeys.contains(app.idKey);
                    final TextView btn = makeText(ctx, tf, host.sp(13.2f),
                            checked ? theme.accent : theme.textPrimary, checked,
                            (checked ? "✓ " : "○ ") + app.label);
                    btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    btn.setSingleLine(true);
                    btn.setEllipsize(TextUtils.TruncateAt.END);
                    btn.setBackground(checked ? theme.accentGlowPill(dp, 14) : theme.sheetTileCard(dp, 14));
                    btn.setPadding(dpx(dp, 12), dpx(dp, 10), dpx(dp, 12), dpx(dp, 10));
                    btn.setOnClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        if (selectedKeys.contains(app.idKey)) selectedKeys.remove(app.idKey);
                        else selectedKeys.add(app.idKey);
                        boolean now = selectedKeys.contains(app.idKey);
                        btn.setText((now ? "✓ " : "○ ") + app.label);
                        btn.setTextColor(now ? theme.accent : theme.textPrimary);
                        btn.setTypeface(now ? Typeface.create(tf, Typeface.BOLD) : tf);
                        btn.setBackground(now ? theme.accentGlowPill(dp, 14) : theme.sheetTileCard(dp, 14));
                        badge.setText("已选 " + selectedKeys.size() + " 个");
                        saveBtn.setText(savePrefix + " (" + selectedKeys.size() + ")");
                        if ("已选".equals(curFilter[0])) run();
                    });
                    btns.add(btn);
                }
                addGridButtons(ctx, dp, gridContainer, btns, 2);
            }
        };

        for (int i = 0; i < letters.size(); i++) {
            final String l = letters.get(i);
            final TextView pill = makeText(ctx, tf, host.sp(12f),
                    i == 0 ? theme.accent : theme.textSecondary, i == 0, l);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dpx(dp, 11), dpx(dp, 5), dpx(dp, 11), dpx(dp, 5));
            pill.setBackground(i == 0 ? theme.accentGlowPill(dp, 10) : theme.subtleGhostPill(dp, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dpx(dp, 6);
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> {
                curFilter[0] = l;
                for (TextView p : pills) {
                    boolean active = l.equals(p.getText().toString());
                    p.setTextColor(active ? theme.accent : theme.textSecondary);
                    p.setTypeface(active ? Typeface.create(tf, Typeface.BOLD) : tf);
                    p.setBackground(active ? theme.accentGlowPill(dp, 10) : theme.subtleGhostPill(dp, 10));
                }
                refreshGrid.run();
            });
            pills.add(pill);
            letterRow.addView(pill);
        }
        letterScroll.addView(letterRow);
        root.addView(letterScroll);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshGrid.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 4. 中间独立滚动区域
        ScrollView bodyScroll = new ScrollView(ctx);
        bodyScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        bodyScroll.setVerticalScrollBarEnabled(false);
        bodyScroll.addView(gridContainer);
        refreshGrid.run();
        root.addView(bodyScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 5. 固定底部动作栏
        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dpx(dp, 10);

        TextView secBtn = makeBtn(ctx, tf, theme, dp, host.sp(13f),
                secondaryLabel != null ? secondaryLabel : "取消", () -> {
                    dlg.dismiss();
                    if (onSecondaryClick != null) onSecondaryClick.run();
                });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.rightMargin = dpx(dp, 8);
        footer.addView(secBtn, blp);

        saveBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            List<String> ordered = new ArrayList<>();
            for (String k : initialOrderedKeys) {
                if (selectedKeys.contains(k) && !ordered.contains(k)) ordered.add(k);
            }
            for (MainActivity.AppEntry a : candidates) {
                if (selectedKeys.contains(a.idKey) && !ordered.contains(a.idKey)) ordered.add(a.idKey);
            }
            dlg.dismiss();
            if (onSave != null) onSave.onSave(ordered);
        });
        footer.addView(saveBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(footer, flp);

        int maxH = Math.round(ctx.getResources().getDisplayMetrics().heightPixels * 0.82f);
        dlg.setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH));
        applySheetWindow(dlg, maxH);
        dlg.show();
        return dlg;
    }

    static Dialog showDragReorder(final Host host,
                                String title,
                                String subtitle,
                                List<String> initialKeys,
                                List<MainActivity.AppEntry> allApps,
                                final ReorderCallback onSave) {
        final Context ctx = host.getContext();
        final AuroraTheme theme = host.getThemeObj();
        final Typeface tf = host.getTypeface();
        final float dp = host.getDp();

        final Map<String, MainActivity.AppEntry> byKey = new HashMap<>();
        for (MainActivity.AppEntry a : allApps) byKey.put(a.idKey, a);
        final List<String> keys = new ArrayList<>();
        for (String k : initialKeys) {
            if (byKey.containsKey(k) && !keys.contains(k)) keys.add(k);
        }

        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(theme.sheetPanelBackground(dp));
        root.setPadding(dpx(dp, 20), dpx(dp, 12), dpx(dp, 20), dpx(dp, 20));
        root.addView(makeHandle(theme, dp, ctx));

        root.addView(makeText(ctx, tf, host.sp(17.5f), theme.textPrimary, true, title));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tip = makeText(ctx, tf, host.sp(12f), theme.textSecondary, false, subtitle);
            tip.setPadding(0, dpx(dp, 2), 0, dpx(dp, 6));
            root.addView(tip);
        }

        final LinearLayout listCol = new LinearLayout(ctx);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(0, dpx(dp, 4), 0, dpx(dp, 8));

        final Runnable[] refreshUi = new Runnable[1];
        refreshUi[0] = () -> {
            listCol.removeAllViews();
            for (int i = 0; i < keys.size(); i++) {
                final int idx = i;
                final LinearLayout r = new LinearLayout(ctx);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setBackground(theme.sheetTileCard(dp, 14));
                r.setPadding(dpx(dp, 10), dpx(dp, 6), dpx(dp, 8), dpx(dp, 6));

                TextView dragHandle = makeText(ctx, tf, host.sp(18f), theme.accent, true, "≡");
                dragHandle.setGravity(Gravity.CENTER);
                dragHandle.setOnTouchListener(new View.OnTouchListener() {
                    private float startRawY;
                    private int curIdx = idx;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        int act = event.getActionMasked();
                        if (act == MotionEvent.ACTION_DOWN) {
                            startRawY = event.getRawY();
                            curIdx = idx;
                            if (v.getParent() != null && v.getParent().getParent() != null) {
                                v.getParent().getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            r.setAlpha(0.85f);
                            r.setScaleX(1.02f);
                            r.setScaleY(1.02f);
                            r.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            return true;
                        }
                        if (act == MotionEvent.ACTION_MOVE) {
                            float dy = event.getRawY() - startRawY;
                            float stepPx = Math.max(dpx(dp, 44), r.getHeight() + dpx(dp, 6));
                            if (dy > stepPx * 0.7f && curIdx < keys.size() - 1) {
                                Collections.swap(keys, curIdx, curIdx + 1);
                                curIdx++;
                                startRawY += stepPx;
                                r.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                updateDragRowLabels(listCol, keys, byKey);
                            } else if (dy < -stepPx * 0.7f && curIdx > 0) {
                                Collections.swap(keys, curIdx, curIdx - 1);
                                curIdx--;
                                startRawY -= stepPx;
                                r.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                updateDragRowLabels(listCol, keys, byKey);
                            }
                            return true;
                        }
                        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                            r.setAlpha(1f);
                            r.setScaleX(1f);
                            r.setScaleY(1f);
                            refreshUi[0].run();
                            return true;
                        }
                        return false;
                    }
                });
                r.addView(dragHandle, new LinearLayout.LayoutParams(dpx(dp, 34), dpx(dp, 40)));

                MainActivity.AppEntry item = byKey.get(keys.get(i));
                TextView tv = makeText(ctx, tf, host.sp(15f), theme.textPrimary, true,
                        (i + 1) + ".  " + (item != null ? item.label : ""));
                tv.setGravity(Gravity.CENTER_VERTICAL);
                r.addView(tv, new LinearLayout.LayoutParams(0, dpx(dp, 40), 1f));

                for (int k = 0; k < 2; k++) {
                    final int delta = (k == 0) ? -1 : 1;
                    TextView arrow = makeText(ctx, tf, host.sp(15f), theme.accent, true, k == 0 ? "▲" : "▼");
                    arrow.setGravity(Gravity.CENTER);
                    boolean valid = idx + delta >= 0 && idx + delta < keys.size();
                    arrow.setAlpha(valid ? 1f : 0.22f);
                    if (valid) {
                        arrow.setOnClickListener(v -> {
                            Collections.swap(keys, idx, idx + delta);
                            refreshUi[0].run();
                        });
                    }
                    r.addView(arrow, new LinearLayout.LayoutParams(dpx(dp, 38), dpx(dp, 40)));
                }
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dpx(dp, 6);
                listCol.addView(r, rlp);
            }
        };
        refreshUi[0].run();

        ScrollView scroll = new ScrollView(ctx);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(listCol);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView saveBtn = makeBtn(ctx, tf, theme, dp, host.sp(13.5f), "保存顺序", () -> {
            dlg.dismiss();
            if (onSave != null) onSave.onSave(keys);
        });
        saveBtn.setBackground(theme.solidAccentButton(dp, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dpx(dp, 10);
        root.addView(saveBtn, slp);

        int estH = Math.min(Math.round(ctx.getResources().getDisplayMetrics().heightPixels * 0.80f),
                dpx(dp, 145) + keys.size() * dpx(dp, 58));
        dlg.setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, estH));
        applySheetWindow(dlg, estH);
        dlg.show();
        return dlg;
    }

    private static void updateDragRowLabels(LinearLayout listCol, List<String> keys, Map<String, MainActivity.AppEntry> byKey) {
        for (int i = 0; i < Math.min(listCol.getChildCount(), keys.size()); i++) {
            View child = listCol.getChildAt(i);
            if (child instanceof LinearLayout && ((LinearLayout) child).getChildCount() >= 2) {
                View tv = ((LinearLayout) child).getChildAt(1);
                if (tv instanceof TextView) {
                    MainActivity.AppEntry a = byKey.get(keys.get(i));
                    if (a != null) ((TextView) tv).setText((i + 1) + ".  " + a.label);
                }
            }
        }
    }

    private static Dialog createFixedSheetDialog(Context ctx, AuroraTheme theme, float dp, java.util.function.Consumer<Void> unused) {
        Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dlg;
    }

    private static void applySheetWindow(Dialog dlg, int height) {
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
            w.setDimAmount(0.42f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private static View makeHandle(AuroraTheme theme, float dp, Context ctx) {
        View handle = new View(ctx);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(theme.textSecondary);
        hg.setCornerRadius(2.5f * dp);
        handle.setBackground(hg);
        handle.setAlpha(0.55f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dpx(dp, 40), dpx(dp, 4.5f));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dpx(dp, 12);
        handle.setLayoutParams(hlp);
        return handle;
    }

    private static TextView makeText(Context ctx, Typeface tf, float spSize, int color, boolean bold, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTypeface(bold ? Typeface.create(tf, Typeface.BOLD) : tf);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize);
        return tv;
    }

    private static TextView makeBtn(Context ctx, Typeface tf, AuroraTheme theme, float dp, float spSize, String label, Runnable onClick) {
        TextView btn = makeText(ctx, tf, spSize, theme.textPrimary, false, label);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(theme.sheetTileCard(dp, 15));
        btn.setPadding(dpx(dp, 10), dpx(dp, 11.5f), dpx(dp, 10), dpx(dp, 11.5f));
        btn.setMaxLines(2);
        btn.setEllipsize(TextUtils.TruncateAt.END);
        if (onClick != null) {
            btn.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                onClick.run();
            });
        }
        return btn;
    }

    private static void addGridButtons(Context ctx, float dp, LinearLayout parent, List<TextView> buttons, int cols) {
        LinearLayout row = null;
        for (int i = 0; i < buttons.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(ctx);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dpx(dp, 8);
                parent.addView(row, rlp);
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i % cols != cols - 1) blp.rightMargin = dpx(dp, 8);
            row.addView(buttons.get(i), blp);
        }
        if (row != null && buttons.size() % cols != 0) {
            for (int i = buttons.size() % cols; i < cols; i++) {
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (i != cols - 1) elp.rightMargin = dpx(dp, 8);
                row.addView(new View(ctx), elp);
            }
        }
    }

    private static int dpx(float dp, float v) {
        return Math.round(v * dp);
    }
}
