package com.niagara.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 轻量常驻守护服务：确保流光桌面在熄屏后不被系统后台清理，解锁瞬间 0ms 秒回桌面。
 */
public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "niagara_local_keepalive";
    static volatile boolean isRunning = false;

    static void startServiceSafely(Context ctx) {
        try {
            Intent i = new Intent(ctx, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable ignored) {}
    }

    static void stopServiceSafely(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, KeepAliveService.class));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "流光桌面常驻守护", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("保持桌面常驻内存以实现毫秒级响应，可长按此通知选择隐藏");
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        int iconId = getApplicationInfo().icon;
        if (iconId == 0) {
            iconId = getResources().getIdentifier("ic_launcher", "drawable", getPackageName());
        }
        if (iconId == 0) {
            iconId = android.R.drawable.sym_def_app_icon;
        }
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconId)
                .setContentTitle("流光桌面 · 极速常驻中")
                .setContentText("100% 本地零联网运行 · 长按可在系统通知栏隐藏")
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(101, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(101, n);
            }
            isRunning = true;
        } catch (Throwable t) {
            isRunning = false;
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
