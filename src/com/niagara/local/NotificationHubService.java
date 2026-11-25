package com.niagara.local;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
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
        /** 不含 RemoteInput 的常规动作（标记已读/暂停等），至多保留 2 个供 Pop-up 渲染为胶囊钮。 */
        List<Notification.Action> plainActions = Collections.emptyList();
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
                        List<Notification.Action> plain = null;
                        for (Notification.Action act : n.actions) {
                            if (act == null) continue;
                            RemoteInput[] ris = act.getRemoteInputs();
                            boolean hasRemoteInput = ris != null && ris.length > 0;
                            if (hasRemoteInput) {
                                if (item.quickReplyAction == null && act.actionIntent != null) {
                                    item.quickReplyAction = act;
                                }
                            } else if (act.actionIntent != null && act.title != null
                                    && act.title.length() > 0 && (plain == null || plain.size() < 2)) {
                                if (plain == null) plain = new ArrayList<>(2);
                                plain.add(act);
                            }
                        }
                        if (plain != null) item.plainActions = plain;
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

    // ---------- v2.6.0 Pop-up 浮层辅助：查询 / 清除 / 快捷回复 ----------

    /** 按包名获取当前未被清除的活跃通知（最新在前，至多 5 条）。永不返回 null，返回只读快照。 */
    static List<NotifItem> getAllByPackage(String pkg) {
        if (pkg == null) return Collections.emptyList();
        List<NotifItem> list = allByPkg.get(pkg);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** 根据通知 Key 主动清除，并立即在本地快照中剔除，让桌面行摘要/计数即时刷新（系统回调随后会校准）。 */
    static boolean dismissNotification(String sbnKey) {
        if (sbnKey == null || !cancelByKey(sbnKey)) return false;
        Map<String, List<NotifItem>> src = allByPkg;
        Map<String, List<NotifItem>> nextAll = new HashMap<>();
        Map<String, NotifItem> nextLatest = new HashMap<>();
        Map<String, Integer> nextCounts = new HashMap<>();
        for (Map.Entry<String, List<NotifItem>> e : src.entrySet()) {
            List<NotifItem> kept = new ArrayList<>();
            for (NotifItem it : e.getValue()) {
                if (it != null && !sbnKey.equals(it.key)) kept.add(it);
            }
            if (kept.isEmpty()) continue;
            nextAll.put(e.getKey(), kept);
            nextCounts.put(e.getKey(), kept.size());
            nextLatest.put(e.getKey(), kept.get(0));
        }
        allByPkg = nextAll;
        latestByPkg = nextLatest;
        countByPkg = nextCounts;
        notifyLauncher();
        return true;
    }

    /** 提取支持直接回复的 RemoteInput 悬挂 Intent；逐层校验，结构不规范的第三方通知返回 null。 */
    static PendingIntent getQuickReplyAction(NotifItem info) {
        if (info == null || info.quickReplyAction == null) return null;
        try {
            RemoteInput[] ris = info.quickReplyAction.getRemoteInputs();
            if (ris == null || ris.length == 0) return null;
            return info.quickReplyAction.actionIntent;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通过通知自带的 RemoteInput 直接发送回复文本。 */
    static boolean sendQuickReply(Context ctx, NotifItem info, String text) {
        PendingIntent pi = getQuickReplyAction(info);
        if (pi == null || ctx == null || text == null || text.trim().isEmpty()) return false;
        try {
            RemoteInput[] ris = info.quickReplyAction.getRemoteInputs();
            Intent fill = new Intent();
            Bundle results = new Bundle();
            for (RemoteInput r : ris) {
                if (r != null && r.getResultKey() != null) {
                    results.putCharSequence(r.getResultKey(), text);
                }
            }
            RemoteInput.addResultsToIntent(ris, fill, results);
            pi.send(ctx, 0, fill);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 相对时间文案：刚刚 / N分钟前 / N小时前 / N天前。 */
    static String formatRelativeTime(long whenMs) {
        long diff = System.currentTimeMillis() - whenMs;
        if (whenMs <= 0 || diff < 60_000L) return "刚刚";
        long min = diff / 60_000L;
        if (min < 60) return min + "分钟前";
        long hr = min / 60;
        if (hr < 24) return hr + "小时前";
        return (hr / 24) + "天前";
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
