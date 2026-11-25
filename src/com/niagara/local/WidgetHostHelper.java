package com.niagara.local;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生 Android 桌面微件宿主引擎 (WidgetHostHelper v2.5.0)：
 * - 基于标准 AppWidgetHost + AppWidgetManager 在首页顶部「一览」区域原生托管第三方桌面小组件；
 * - 支持多小组件高度调节（紧凑 / 标准 / 宽展）、配置页调起与安全移除；
 * - 完整处理权限绑定、配置回传与生命周期启停。
 */
final class WidgetHostHelper {

    static final int HOST_ID = 0x4E4947;
    static final int REQ_BIND_WIDGET = 301;
    static final int REQ_CONF_WIDGET = 302;
    private static final String K_WIDGETS = "pinned_widgets_v1";

    static final class PinnedWidget {
        int id;
        int heightDp;

        PinnedWidget(int id, int heightDp) {
            this.id = id;
            this.heightDp = heightDp;
        }
    }

    private final Context appCtx;
    private final AppWidgetManager manager;
    private final AppWidgetHost host;
    private boolean listening = false;
    private int pendingWidgetId = -1;
    private AppWidgetProviderInfo pendingProvider;

    WidgetHostHelper(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
        this.manager = AppWidgetManager.getInstance(this.appCtx);
        this.host = new AppWidgetHost(this.appCtx, HOST_ID);
    }

    void startListeningSafe() {
        if (listening) return;
        try {
            host.startListening();
            listening = true;
        } catch (Throwable ignored) {}
    }

    void stopListeningSafe() {
        if (!listening) return;
        try {
            host.stopListening();
        } catch (Throwable ignored) {}
        listening = false;
    }

    List<PinnedWidget> getPinnedWidgets(SharedPreferences prefs) {
        List<PinnedWidget> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(K_WIDGETS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                int id = o.optInt("id", -1);
                int h = Math.max(72, Math.min(340, o.optInt("heightDp", 136)));
                if (id >= 0) out.add(new PinnedWidget(id, h));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void savePinnedWidgets(SharedPreferences prefs, List<PinnedWidget> list) {
        JSONArray arr = new JSONArray();
        for (PinnedWidget pw : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", pw.id);
                o.put("heightDp", pw.heightDp);
                arr.put(o);
            } catch (Throwable ignored) {}
        }
        prefs.edit().putString(K_WIDGETS, arr.toString()).apply();
    }

    List<AppWidgetProviderInfo> getAllProviders() {
        try {
            List<AppWidgetProviderInfo> list = manager.getInstalledProviders();
            return list != null ? list : new ArrayList<>();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    void requestAddWidget(Activity act, SharedPreferences prefs, AppWidgetProviderInfo info, Runnable onAdded) {
        if (act == null || info == null) return;
        int id = host.allocateAppWidgetId();
        pendingWidgetId = id;
        pendingProvider = info;

        boolean allowed = false;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                UserHandle profile = info.getProfile();
                allowed = manager.bindAppWidgetIdIfAllowed(id, profile, info.provider, null);
            } else {
                allowed = manager.bindAppWidgetIdIfAllowed(id, info.provider);
            }
        } catch (Throwable ignored) {}

        if (allowed) {
            configureOrSaveWidget(act, prefs, id, info, onAdded);
        } else {
            try {
                Intent bindIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                if (Build.VERSION.SDK_INT >= 21) {
                    bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile());
                }
                act.startActivityForResult(bindIntent, REQ_BIND_WIDGET);
            } catch (Throwable t) {
                safeDeleteWidgetId(id);
                pendingWidgetId = -1;
                pendingProvider = null;
                Toast.makeText(act, "当前系统禁止桌面直接绑定微件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    boolean handleActivityResult(Activity act, SharedPreferences prefs, int requestCode, int resultCode, Intent data, Runnable onChanged) {
        if (requestCode == REQ_BIND_WIDGET) {
            int id = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) : pendingWidgetId;
            AppWidgetProviderInfo info = pendingProvider != null ? pendingProvider : (id >= 0 ? manager.getAppWidgetInfo(id) : null);
            if (resultCode == Activity.RESULT_OK && id >= 0 && info != null) {
                configureOrSaveWidget(act, prefs, id, info, onChanged);
            } else {
                if (id >= 0) safeDeleteWidgetId(id);
                pendingWidgetId = -1;
                pendingProvider = null;
            }
            return true;
        }
        if (requestCode == REQ_CONF_WIDGET) {
            int id = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) : pendingWidgetId;
            if (resultCode == Activity.RESULT_OK && id >= 0) {
                AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
                int defaultH = info != null ? Math.max(88, Math.min(240, info.minHeight / 2)) : 136;
                commitPinnedWidget(prefs, id, defaultH);
                if (onChanged != null) onChanged.run();
            } else if (id >= 0) {
                safeDeleteWidgetId(id);
            }
            pendingWidgetId = -1;
            pendingProvider = null;
            return true;
        }
        return false;
    }

    private void configureOrSaveWidget(Activity act, SharedPreferences prefs, int id, AppWidgetProviderInfo info, Runnable onAdded) {
        if (info.configure != null) {
            try {
                pendingWidgetId = id;
                pendingProvider = info;
                host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_CONF_WIDGET, null);
                return;
            } catch (Throwable ignored) {}
        }
        int defaultH = Math.max(88, Math.min(220, Math.round(info.minHeight / act.getResources().getDisplayMetrics().density)));
        if (defaultH < 88) defaultH = 136;
        commitPinnedWidget(prefs, id, defaultH);
        pendingWidgetId = -1;
        pendingProvider = null;
        if (onAdded != null) onAdded.run();
    }

    private void commitPinnedWidget(SharedPreferences prefs, int id, int heightDp) {
        List<PinnedWidget> list = getPinnedWidgets(prefs);
        for (PinnedWidget pw : list) {
            if (pw.id == id) return;
        }
        list.add(new PinnedWidget(id, heightDp));
        savePinnedWidgets(prefs, list);
    }

    void updateWidgetHeight(SharedPreferences prefs, int widgetId, int heightDp) {
        List<PinnedWidget> list = getPinnedWidgets(prefs);
        for (PinnedWidget pw : list) {
            if (pw.id == widgetId) {
                pw.heightDp = heightDp;
                break;
            }
        }
        savePinnedWidgets(prefs, list);
    }

    void removeWidget(SharedPreferences prefs, int widgetId) {
        List<PinnedWidget> list = getPinnedWidgets(prefs);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id == widgetId) list.remove(i);
        }
        savePinnedWidgets(prefs, list);
        safeDeleteWidgetId(widgetId);
    }

    private void safeDeleteWidgetId(int id) {
        try {
            host.deleteAppWidgetId(id);
        } catch (Throwable ignored) {}
    }

    void populateWidgets(Activity act, SharedPreferences prefs, LinearLayout container, AuroraTheme theme, float dp,
                         final WidgetActionListener actionListener) {
        if (container == null) return;
        container.removeAllViews();
        List<PinnedWidget> pinned = getPinnedWidgets(prefs);
        if (pinned.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        startListeningSafe();

        int screenW = act.getResources().getDisplayMetrics().widthPixels;
        int widthDp = Math.max(200, Math.round((screenW - 56 * dp) / dp));

        for (final PinnedWidget pw : pinned) {
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(pw.id);
            if (info == null) continue;
            try {
                AppWidgetHostView hv = host.createView(act, pw.id, info);
                hv.setAppWidget(pw.id, info);
                Bundle opts = new Bundle();
                opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
                opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
                opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, pw.heightDp);
                opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, pw.heightDp);
                hv.updateAppWidgetOptions(opts);

                FrameLayout wrap = new FrameLayout(act);
                wrap.setBackground(theme.glassCard(dp, 18));
                wrap.setClipToPadding(true);
                int pad = Math.round(4 * dp);
                wrap.setPadding(pad, pad, pad, pad);
                wrap.addView(hv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.round(pw.heightDp * dp), Gravity.CENTER));
                wrap.setOnLongClickListener(v -> {
                    if (actionListener != null) actionListener.onWidgetLongPress(pw, info);
                    return true;
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Math.round(8 * dp);
                container.addView(wrap, lp);
            } catch (Throwable ignored) {}
        }
        container.setVisibility(container.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    interface WidgetActionListener {
        void onWidgetLongPress(PinnedWidget widget, AppWidgetProviderInfo info);
    }
}
