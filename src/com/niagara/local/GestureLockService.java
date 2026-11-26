package com.niagara.local;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 手势锁屏与通知栏辅助服务：
 * - 执行双击屏幕空白处锁屏、单手下滑展开通知栏、呼出系统多任务后台；
 * - 实时追踪后台活跃应用，仅在 Dock 可见时才延迟通知首页刷新，避免退出应用时主线程撞车。
 */
public class GestureLockService extends AccessibilityService {
    static volatile GestureLockService instance;
    static volatile long lastLockTimestamp;

    private static final List<String> activePackages = Collections.synchronizedList(new ArrayList<>());

    public static List<String> getActivePackages() {
        synchronized (activePackages) {
            return new ArrayList<>(activePackages);
        }
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public static boolean openRecentsOverview() {
        GestureLockService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    private void recordActiveApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        String myPkg = getPackageName();
        if (pkg.equals(myPkg) || pkg.equals("com.niagara.local") || pkg.equals("com.niagara.loca2")) return;
        if (pkg.equals("com.android.systemui") || pkg.equals("android")) return;
        String lower = pkg.toLowerCase();
        if (lower.contains("inputmethod") || lower.contains("keyboard") || lower.contains("ime")) return;

        synchronized (activePackages) {
            activePackages.remove(pkg);
            activePackages.add(0, pkg);
            while (activePackages.size() > 8) {
                activePackages.remove(activePackages.size() - 1);
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgName = event.getPackageName();
            if (pkgName != null) {
                recordActiveApp(pkgName.toString());
            }
        }
    }

    @Override
    public void onInterrupt() {}

    static boolean lockScreenNow() {
        GestureLockService s = instance;
        if (s == null || Build.VERSION.SDK_INT < 28) return false;
        long now = SystemClock.uptimeMillis();
        if (now - lastLockTimestamp < 2400) return true;
        lastLockTimestamp = now;
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    static boolean expandNotificationShade() {
        GestureLockService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }
}
