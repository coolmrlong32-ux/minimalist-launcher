package com.niagara.local;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 🌟 ColorOS 风格后台多任务卡片水平堆叠界面 (ColorOS Recents Overlay)
 * 1. 卡片水平层叠堆叠 (Card Stack)，右侧卡片压住左侧卡片，自然透出层次感；
 * 2. 顶部显示对应应用圆形图标与标题；
 * 3. 卡片支持点击直达应用、上滑飞出关闭任务；
 * 4. 底部中央专属「清除」毛玻璃胶囊按钮，一键清空后台并附带触感反馈。
 */
public final class ColorOsRecentsOverlay {

    public interface OnAppLaunchListener {
        void onLaunch(MainActivity.AppEntry app);
    }

    public interface OnRecentsClearedListener {
        void onCleared();
    }

    public static void show(final MainActivity activity,
                            final List<MainActivity.AppEntry> apps,
                            final OnAppLaunchListener launchListener,
                            final OnRecentsClearedListener clearListener) {
        if (activity == null || activity.isFinishing()) return;

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xD20D1117));
            if (Build.VERSION.SDK_INT >= 28) {
                win.getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            win.setStatusBarColor(0x00000000);
            win.setNavigationBarColor(0x00000000);
        }

        final float dp = activity.getResources().getDisplayMetrics().density;
        final FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xD00A0E14);

        final HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        final int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        final int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        final int cardW = (int) (screenW * 0.62f);
        final int cardH = (int) (screenH * 0.58f);
        final int overlapShift = (int) (cardW * 0.46f);

        final LinearLayout stackBox = new LinearLayout(activity);
        stackBox.setOrientation(LinearLayout.HORIZONTAL);
        stackBox.setGravity(Gravity.CENTER_VERTICAL);
        stackBox.setClipChildren(false);
        stackBox.setClipToPadding(false);
        stackBox.setPadding((int) (screenW * 0.18f), 0, (int) (screenW * 0.25f), 0);

        final List<View> cardViews = new ArrayList<>();
        final List<MainActivity.AppEntry> cardApps = new ArrayList<>(apps);

        for (int i = 0; i < cardApps.size(); i++) {
            final int index = i;
            final MainActivity.AppEntry app = cardApps.get(i);

            final LinearLayout cardContainer = new LinearLayout(activity);
            cardContainer.setOrientation(LinearLayout.VERTICAL);
            cardContainer.setGravity(Gravity.CENTER_HORIZONTAL);
            cardContainer.setClipChildren(false);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                clp.leftMargin = -overlapShift; // 水平层叠堆叠
            }
            cardContainer.setLayoutParams(clp);

            // 1. 顶部 Header (应用图标 + 标题)
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(0, 0, 0, (int) (8 * dp));

            ImageView iconView = new ImageView(activity);
            iconView.setImageDrawable(activity.getShapedIcon(app));
            header.addView(iconView, new LinearLayout.LayoutParams((int) (30 * dp), (int) (30 * dp)));

            TextView titleView = new TextView(activity);
            titleView.setText(app.label);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            titleView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            titleView.setSingleLine(true);
            titleView.setShadowLayer(4 * dp, 0, 2 * dp, 0x88000000);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = (int) (8 * dp);
            header.addView(titleView, tlp);

            cardContainer.addView(header);

            // 2. 主卡片视图 (带 22dp 圆角与仿真 ColorOS UI 预览)
            final FrameLayout cardSurface = new FrameLayout(activity);
            GradientDrawable cardBg = new GradientDrawable();
            // 每张卡片根据应用或状态呈现质感深邃色
            int[] darkPalettes = new int[]{
                    0xFF1C222B, 0xFF24232C, 0xFF1B2624, 0xFF2B2023, 0xFF1E2833
            };
            cardBg.setColor(darkPalettes[index % darkPalettes.length]);
            cardBg.setCornerRadius(22 * dp);
            cardBg.setStroke((int) (1.2f * dp), 0x33FFFFFF);
            cardSurface.setBackground(cardBg);
            cardSurface.setElevation(8 * dp);

            // 仿真应用顶部状态栏与微缩窗口界面
            LinearLayout previewBody = new LinearLayout(activity);
            previewBody.setOrientation(LinearLayout.VERTICAL);

            View mockStatusBar = new View(activity);
            mockStatusBar.setBackgroundColor(0x22FFFFFF);
            previewBody.addView(mockStatusBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (22 * dp)));

            View mockActionBar = new View(activity);
            mockActionBar.setBackgroundColor(0x18FFFFFF);
            LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (38 * dp));
            abLp.topMargin = (int) (2 * dp);
            previewBody.addView(mockActionBar, abLp);

            // 中间装饰性大图标水印
            ImageView centerWatermark = new ImageView(activity);
            centerWatermark.setImageDrawable(activity.getShapedIcon(app));
            centerWatermark.setAlpha(0.18f);
            FrameLayout.LayoutParams cmlp = new FrameLayout.LayoutParams(
                    (int) (cardW * 0.42f), (int) (cardW * 0.42f), Gravity.CENTER);
            cardSurface.addView(centerWatermark, cmlp);

            cardSurface.addView(previewBody, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            cardContainer.addView(cardSurface, new LinearLayout.LayoutParams(cardW, cardH));

            // 手势交互：点击进入应用，上滑划走移除任务
            setupCardInteractions(activity, cardContainer, app, dialog, launchListener);

            stackBox.addView(cardContainer);
            cardViews.add(cardContainer);
        }

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        slp.gravity = Gravity.CENTER_VERTICAL;
        scroll.addView(stackBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(scroll, slp);

        // 3. 底部专属「清除」毛玻璃胶囊按钮 (与 ColorOS 截图 100% 一致)
        final TextView clearBtn = new TextView(activity);
        clearBtn.setText("清 除");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        clearBtn.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        clearBtn.setGravity(Gravity.CENTER);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x40FFFFFF); // 毛玻璃半透明白底
        btnBg.setCornerRadius(24 * dp);
        btnBg.setStroke((int) (1.2f * dp), 0x50FFFFFF);
        clearBtn.setBackground(btnBg);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams((int) (136 * dp), (int) (44 * dp));
        blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        blp.bottomMargin = (int) (52 * dp);
        root.addView(clearBtn, blp);

        clearBtn.setOnClickListener(v -> {
            clearBtn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            clearBtn.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80)
                    .withEndAction(() -> {
                        // 所有卡片同时缩小淡出飞走
                        for (int i = 0; i < cardViews.size(); i++) {
                            View cv = cardViews.get(i);
                            cv.animate()
                                    .scaleX(0.4f)
                                    .scaleY(0.4f)
                                    .alpha(0f)
                                    .setDuration(220)
                                    .setStartDelay(i * 35L)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .start();
                        }
                        clearBtn.animate().alpha(0f).setDuration(180).start();
                        root.postDelayed(() -> {
                            if (clearListener != null) clearListener.onCleared();
                            Toast.makeText(activity, "已清理后台应用", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }, 280);
                    }).start();
        });

        // 点击空白处平滑退出
        root.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.show();

        // 卡片入场动效：从右至左依次平滑展开
        for (int i = 0; i < cardViews.size(); i++) {
            View cv = cardViews.get(i);
            cv.setTranslationX(120 * dp);
            cv.setAlpha(0f);
            cv.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(260)
                    .setStartDelay(i * 45L)
                    .setInterpolator(new OvershootInterpolator(1.04f))
                    .start();
        }
    }

    private static void setupCardInteractions(final MainActivity activity,
                                              final View cardContainer,
                                              final MainActivity.AppEntry app,
                                              final Dialog dialog,
                                              final OnAppLaunchListener launchListener) {
        cardContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean isSwipingUp = false;
            private final float dp = activity.getResources().getDisplayMetrics().density;
            private VelocityTracker velocityTracker;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = ev.getRawX();
                        startY = ev.getRawY();
                        isSwipingUp = false;
                        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                        else velocityTracker.clear();
                        velocityTracker.addMovement(ev);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (velocityTracker != null) velocityTracker.addMovement(ev);
                        float dy = ev.getRawY() - startY;
                        float dx = ev.getRawX() - startX;
                        if (!isSwipingUp && dy < -16 * dp && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                            isSwipingUp = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (isSwipingUp) {
                            v.setTranslationY(Math.min(0f, dy));
                            float progress = Math.min(1f, Math.abs(dy) / (300f * dp));
                            v.setAlpha(1f - progress * 0.7f);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (velocityTracker != null) {
                            velocityTracker.addMovement(ev);
                            velocityTracker.computeCurrentVelocity(1000);
                        }
                        if (isSwipingUp) {
                            float vy = velocityTracker != null ? velocityTracker.getYVelocity() : 0f;
                            float curY = v.getTranslationY();
                            if (curY < -90 * dp || vy < -800) {
                                // 向上划出屏幕移除卡片
                                v.animate()
                                        .translationY(-activity.getResources().getDisplayMetrics().heightPixels)
                                        .alpha(0f)
                                        .setDuration(180)
                                        .setInterpolator(new DecelerateInterpolator())
                                        .withEndAction(() -> {
                                            v.setVisibility(View.GONE);
                                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                        })
                                        .start();
                            } else {
                                // 回弹复原
                                v.animate().translationY(0f).alpha(1f).setDuration(160).start();
                            }
                            if (velocityTracker != null) velocityTracker.recycle();
                            return true;
                        }
                        if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                            float totalDist = Math.abs(ev.getRawX() - startX) + Math.abs(ev.getRawY() - startY);
                            if (totalDist < 12 * dp) {
                                // 点击卡片直达
                                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70)
                                        .withEndAction(() -> {
                                            dialog.dismiss();
                                            if (launchListener != null) launchListener.onLaunch(app);
                                        }).start();
                                return true;
                            }
                        }
                        if (velocityTracker != null) velocityTracker.recycle();
                        break;
                }
                return false;
            }
        });
    }
}
