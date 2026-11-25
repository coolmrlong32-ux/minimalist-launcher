package com.niagara.local;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地通知中心服务 (v2.5.0 多通知聚合版)：
 * - 为应用行提供未读通知圆点、聚合消息数量、多条消息预览、一键消除通知、快捷回复及媒体会话权限；
 * - 提取系统天气应用的常驻/通知栏实时温度与天气状况供首页天气微件展示（100% 零网络权限）。
 */
public class NotificationHubService extends NotificationListenerService {

    static final class NotifItem {
        String pkg;
        String key;
        String title;
        String text;
        long postTime;
        boolean isClearable;
        PendingIntent contentIntent;
        Notification.Action quickReplyAction;
    }

    static volatile Set<String> activePackages = Collections.emptySet();
    static volatile Map<String, NotifItem> latestByPkg = Collections.emptyMap();
    static volatile Map<String, List<NotifItem>> allByPkg = Collections.emptyMap();
    static volatile Map<String, Integer> countByPkg = Collections.emptyMap();
    static volatile String weatherSummary = "";
    static volatile String weatherPackage = "";
    static volatile PendingIntent weatherIntent = null;
    static volatile Runnable uiCallback;
    static volatile NotificationHubService instance;

    private static final Pattern TEMP_PATTERN = Pattern.compile("(-?\\d{1,2})\\s*[°℃]");

    private static boolean isWeatherNotification(String pkg, String title, String text) {
        String combined = ((title == null ? "" : title) + " " + (text == null ? "" : text));
        if (!TEMP_PATTERN.matcher(combined).find()) {
            return false;
        }
        if (pkg != null) {
            String p = pkg.toLowerCase(Locale.ROOT);
            if (p.contains("weather") || p.contains("meteo") || p.contains("moji")
                    || p.contains("caiyun") || p.contains("hefeng") || p.contains("qweather")
                    || p.contains("oplus.weather")) {
                return true;
            }
        }
        return combined.contains("晴") || combined.contains("多云") || combined.contains("阴")
                || combined.contains("雨") || combined.contains("雪") || combined.contains("雾")
                || combined.contains("霾") || combined.contains("风");
    }

    private static String formatWeatherBadge(String title, String text) {
        String raw = ((title == null ? "" : title.trim()) + " " + (text == null ? "" : text.trim())).trim();
        if (raw.isEmpty()) return "";
        String icon = "🌤";
        if (raw.contains("雷")) icon = "⛈";
        else if (raw.contains("雪")) icon = "❄️";
        else if (raw.contains("雨")) icon = "🌧";
        else if (raw.contains("雾") || raw.contains("霾")) icon = "🌫";
        else if (raw.contains("阴")) icon = "☁️";
        else if (raw.contains("多云")) icon = "⛅";
        else if (raw.contains("晴")) icon = "☀️";

        Matcher m = TEMP_PATTERN.matcher(raw);
        String tempStr = m.find() ? (m.group(1) + "°C") : "";

        String cond = "";
        String[] conds = {"雷阵雨", "暴雨", "大雨", "中雨", "小雨", "阵雨", "大雪", "中雪", "小雪", "雨夹雪", "雾霾", "大雾", "多云", "晴", "阴"};
        for (String c : conds) {
            if (raw.contains(c)) {
                cond = c;
                break;
            }
        }

        if (!tempStr.isEmpty() && !cond.isEmpty()) return icon + " " + tempStr + " · " + cond;
        if (!tempStr.isEmpty()) return icon + " " + tempStr;
        String clean = raw.replaceAll("\\s+", " ");
        if (clean.length() > 12) clean = clean.substring(0, 12);
        return icon + " " + clean;
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        rebuildSnapshot();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        activePackages = Collections.emptySet();
        latestByPkg = Collections.emptyMap();
        allByPkg = Collections.emptyMap();
        countByPkg = Collections.emptyMap();
        weatherSummary = "";
        weatherPackage = "";
        weatherIntent = null;
        uiCallback = null;
        notifyLauncher();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        rebuildSnapshot();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        rebuildSnapshot();
    }

    private void rebuildSnapshot() {
        Set<String> pkgs = new HashSet<>();
        Map<String, NotifItem> latestMap = new HashMap<>();
        Map<String, List<NotifItem>> listMap = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        String ongoingWeatherText = "";
        String ongoingWeatherPkg = "";
        PendingIntent ongoingWeatherPi = null;
        String regularWeatherText = "";
        String regularWeatherPkg = "";
        PendingIntent regularWeatherPi = null;
        try {
            StatusBarNotification[] list = getActiveNotifications();
            if (list != null) {
                for (StatusBarNotification sbn : list) {
                    Notification n = sbn.getNotification();
                    if (n == null) continue;
                    Bundle ex = n.extras;
                    CharSequence t = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TITLE);
                    CharSequence tx = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TEXT);
                    CharSequence bigTx = ex == null ? null : ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    CharSequence subTx = ex == null ? null : ex.getCharSequence(Notification.EXTRA_SUB_TEXT);
                    String titleStr = t == null ? "" : t.toString();
                    String textStr = tx == null ? "" : tx.toString();
                    if (textStr.isEmpty() && bigTx != null) textStr = bigTx.toString();
                    if (textStr.isEmpty() && subTx != null) textStr = subTx.toString();
                    if (titleStr.isEmpty() && n.tickerText != null) titleStr = n.tickerText.toString();

                    if (isWeatherNotification(sbn.getPackageName(), titleStr, textStr)) {
                        String badge = formatWeatherBadge(titleStr, textStr);
                        if (!badge.isEmpty()) {
                            if (sbn.isOngoing() && ongoingWeatherText.isEmpty()) {
                                ongoingWeatherText = badge;
                                ongoingWeatherPkg = sbn.getPackageName();
                                ongoingWeatherPi = n.contentIntent;
                            } else if (regularWeatherText.isEmpty()) {
                                regularWeatherText = badge;
                                regularWeatherPkg = sbn.getPackageName();
                                regularWeatherPi = n.contentIntent;
                            }
                        }
                    }

                    if (sbn.isOngoing()) continue;
                    String pkgName = sbn.getPackageName();
                    pkgs.add(pkgName);
                    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    if (t == null && tx == null) continue;

                    long when = n.when > 0 ? n.when : sbn.getPostTime();
                    NotifItem item = new NotifItem();
                    item.pkg = pkgName;
                    item.key = sbn.getKey();
                    item.title = titleStr;
                    item.text = textStr;
                    item.postTime = when;
                    item.isClearable = sbn.isClearable();
                    item.contentIntent = n.contentIntent;
                    if (n.actions != null) {
                        for (Notification.Action act : n.actions) {
                            RemoteInput[] ris = act.getRemoteInputs();
                            if (ris != null && ris.length > 0 && act.actionIntent != null) {
                                item.quickReplyAction = act;
                                break;
                            }
                        }
                    }

                    List<NotifItem> bucket = listMap.get(pkgName);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        listMap.put(pkgName, bucket);
                    }
                    bucket.add(item);
                    counts.put(pkgName, bucket.size());

                    NotifItem prev = latestMap.get(pkgName);
                    if (prev == null || when >= prev.postTime) {
                        latestMap.put(pkgName, item);
                    }
                }
            }
        } catch (Throwable ignored) {}

        for (Map.Entry<String, List<NotifItem>> entry : listMap.entrySet()) {
            List<NotifItem> bucket = entry.getValue();
            Collections.sort(bucket, (a, b) -> Long.compare(b.postTime, a.postTime));
            if (bucket.size() > 5) {
                entry.setValue(new ArrayList<>(bucket.subList(0, 5)));
            }
        }

        activePackages = pkgs;
        latestByPkg = latestMap;
        allByPkg = listMap;
        countByPkg = counts;
        if (!ongoingWeatherText.isEmpty()) {
            weatherSummary = ongoingWeatherText;
            weatherPackage = ongoingWeatherPkg;
            weatherIntent = ongoingWeatherPi;
        } else if (!regularWeatherText.isEmpty()) {
            weatherSummary = regularWeatherText;
            weatherPackage = regularWeatherPkg;
            weatherIntent = regularWeatherPi;
        } else {
            weatherSummary = "";
            weatherPackage = "";
            weatherIntent = null;
        }
        notifyLauncher();
    }

    static boolean cancelByKey(String key) {
        NotificationHubService s = instance;
        if (s == null || key == null) return false;
        try {
            s.cancelNotification(key);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static int cancelAllForPackage(String pkg) {
        NotificationHubService s = instance;
        if (s == null || pkg == null) return 0;
        List<NotifItem> items = allByPkg.get(pkg);
        if (items == null || items.isEmpty()) return 0;
        int cleared = 0;
        for (NotifItem it : items) {
            if (it != null && it.isClearable && it.key != null) {
                try {
                    s.cancelNotification(it.key);
                    cleared++;
                } catch (Throwable ignored) {}
            }
        }
        return cleared;
    }

    private static void notifyLauncher() {
        Runnable r = uiCallback;
        if (r != null) r.run();
    }
}
