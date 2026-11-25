package com.niagara.local;

import android.app.Activity;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * 响应系统与各类浏览器（Chrome、Brave、Edge、Quark 等）桌面生成快捷方式请求 (CONFIRM_PIN_SHORTCUT)
 * 自动接收 PinItemRequest 并完成桌面固化，确保 PWA 独立快捷方式秒速入驻。
 */
public class ConfirmPinShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    LauncherApps.PinItemRequest request = launcherApps.getPinItemRequest(getIntent());
                    if (request != null && request.isValid()) {
                        String name = "快捷方式";
                        if (request.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                            ShortcutInfo si = request.getShortcutInfo();
                            if (si != null) {
                                CharSequence lb = si.getShortLabel();
                                if (lb == null || lb.length() == 0) lb = si.getLongLabel();
                                if (lb != null && lb.length() > 0) name = lb.toString();
                            }
                        }
                        request.accept();
                        Toast.makeText(this, "已生成桌面快捷方式：" + name, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Throwable ignored) {}
        finish();
    }
}
