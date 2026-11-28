package com.niagara.local;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.View;

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
 * 本地通知中心服务 (v2.9.1 深度应用内联动版)：
 * - 为应用行提供未读通知圆点、聚合消息数量、MessagingStyle/InboxStyle 最新单条消息预览、一键消除通知、快捷回复及媒体会话权限；
 * - 适配 Android 14/15 (API 34+) PendingIntent 后台启动授权（BAL），点击桌面通知直达应用内具体聊天或详情页；
 * - 严格隔离应用分身 (UserHandle) 与媒体/常驻伪通知，并支持点击自动消通知与返回桌面实时校准。
 */
public class NotificationHubService extends NotificationListenerService {

    static final class NotifItem {
        String pkg;
        String key;
        UserHandle user;
        String title;
        String text;
        long postTime;
        boolean isClearable;
        boolean autoCancel;
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

        if (!tempStr.isEmpty() && !cond.isEmpty()) return tempStr + " · " + cond;
        if (!tempStr.isEmpty()) return tempStr;
        String clean = raw.replaceAll("\\s+", " ");
        if (clean.length() > 12) clean = clean.substring(0, 12);
        return clean;
    }

    private static boolean userMatches(UserHandle notifUser, UserHandle appUser) {
        if (notifUser == null || appUser == null) return true;
        return notifUser.equals(appUser);
    }

    private static String scopeKey(String pkg, UserHandle u) {
        return pkg + "#" + (u != null ? u.hashCode() : 0);
    }

    private static void extractRichTitleAndText(Notification n, Bundle ex, String[] outTitleText) {
        CharSequence t = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence tx = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTx = ex == null ? null : ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence subTx = ex == null ? null : ex.getCharSequence(Notification.EXTRA_SUB_TEXT);
        String titleStr = t == null ? "" : t.toString().trim();
        String textStr = tx == null ? "" : tx.toString().trim();

        // 优先提取 MessagingStyle 最新一条对话消息（微信/Telegram/短信等）
        if (ex != null) {
            try {
                Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (msgs != null && msgs.length > 0) {
                    for (int i = msgs.length - 1; i >= 0; i--) {
                        if (!(msgs[i] instanceof Bundle)) continue;
                        Bundle mb = (Bundle) msgs[i];
                        CharSequence mText = mb.getCharSequence("text");
                        if (mText == null || mText.toString().trim().isEmpty()) continue;
                        String latestMsg = mText.toString().trim();
                        String sender = "";
                        CharSequence senderCs = mb.getCharSequence("sender");
                        if (senderCs != null && senderCs.length() > 0) {
                            sender = senderCs.toString().trim();
                        } else if (Build.VERSION.SDK_INT >= 28) {
                            try {
                                android.app.Person person = mb.getParcelable("sender_person");
                                if (person != null && person.getName() != null) {
                                    sender = person.getName().toString().trim();
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (titleStr.isEmpty() && !sender.isEmpty()) {
                            titleStr = sender;
                            textStr = latestMsg;
                        } else if (!sender.isEmpty() && !sender.equals(titleStr) && !latestMsg.startsWith(sender + ":") && !latestMsg.startsWith(sender + "：")) {
                            textStr = sender + "：" + latestMsg;
                        } else {
                            textStr = latestMsg;
                        }
                        break;
                    }
                }
            } catch (Throwable ignored) {}

            // 其次尝试 InboxStyle 末行（聚合多条消息时展示最新一行）
            if (textStr.isEmpty()) {
                try {
                    CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                    if (lines != null && lines.length > 0) {
                        for (int i = lines.length - 1; i >= 0; i--) {
                            if (lines[i] != null && !lines[i].toString().trim().isEmpty()) {
                                textStr = lines[i].toString().trim();
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (textStr.isEmpty() && bigTx != null) textStr = bigTx.toString().trim();
        if (textStr.isEmpty() && subTx != null) textStr = subTx.toString().trim();
        if (titleStr.isEmpty() && n.tickerText != null) titleStr = n.tickerText.toString().trim();
        outTitleText[0] = titleStr;
        outTitleText[1] = textStr;
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

    /** 回到桌面时由后台线程主动重校验系统当前活跃通知，防止应用内已读后桌面残留旧通知。 */
    static void refreshSnapshotAsync() {
        final NotificationHubService s = instance;
        if (s == null) return;
        AppExecutors.getInstance().computation().execute(() -> {
            try {
                s.rebuildSnapshot();
            } catch (Throwable ignored) {}
        });
    }

    private void rebuildSnapshot() {
        Set<String> pkgs = new HashSet<>();
        Map<String, NotifItem> latestMap = new HashMap<>();
        Map<String, List<NotifItem>> listMap = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, PendingIntent> summaryIntents = new HashMap<>();

        String ongoingWeatherText = "";
        String ongoingWeatherPkg = "";
        PendingIntent ongoingWeatherPi = null;
        String regularWeatherText = "";
        String regularWeatherPkg = "";
        PendingIntent regularWeatherPi = null;
        String[] titleText = new String[2];
        try {
            StatusBarNotification[] list = getActiveNotifications();
            if (list != null) {
                for (StatusBarNotification sbn : list) {
                    Notification n = sbn.getNotification();
                    if (n == null) continue;
                    Bundle ex = n.extras;
                    extractRichTitleAndText(n, ex, titleText);
                    String titleStr = titleText[0];
                    String textStr = titleText[1];

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
                    if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) continue;
                    if (Notification.CATEGORY_TRANSPORT.equals(n.category)) continue;
                    if (ex != null && ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) continue;

                    String pkgName = sbn.getPackageName();
                    UserHandle sbnUser = sbn.getUser();
                    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                        if (n.contentIntent != null) {
                            summaryIntents.put(scopeKey(pkgName, sbnUser), n.contentIntent);
                        }
                        continue;
                    }
                    if (titleStr.isEmpty() && textStr.isEmpty()) continue;

                    pkgs.add(pkgName);

                    long when = n.when > 0 ? n.when : sbn.getPostTime();
                    NotifItem item = new NotifItem();
                    item.pkg = pkgName;
                    item.key = sbn.getKey();
                    item.user = sbnUser;
                    item.title = titleStr;
                    item.text = textStr;
                    item.postTime = when;
                    item.isClearable = sbn.isClearable();
                    item.autoCancel = (n.flags & Notification.FLAG_AUTO_CANCEL) != 0;
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
            for (NotifItem item : bucket) {
                if (item.contentIntent == null) {
                    PendingIntent fallbackPi = summaryIntents.get(scopeKey(item.pkg, item.user));
                    if (fallbackPi != null) item.contentIntent = fallbackPi;
                }
            }
            Collections.sort(bucket, (a, b) -> Long.compare(b.postTime, a.postTime));
            if (bucket.size() > 8) {
                entry.setValue(new ArrayList<>(bucket.subList(0, 8)));
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

    // ---------- Android 14/15 PendingIntent 后台跳转授权与深度打开 ----------

    private static Bundle buildLaunchOptionsBundle(View sourceView) {
        try {
            ActivityOptions ao;
            if (sourceView != null && sourceView.getWidth() > 0 && sourceView.getHeight() > 0) {
                ao = ActivityOptions.makeClipRevealAnimation(
                        sourceView, 0, 0, sourceView.getWidth(), sourceView.getHeight());
            } else {
                ao = ActivityOptions.makeBasic();
            }
            if (Build.VERSION.SDK_INT >= 34) {
                ao.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            } else if (Build.VERSION.SDK_INT >= 33) {
                try {
                    ActivityOptions.class
                            .getMethod("setPendingIntentBackgroundActivityLaunchAllowed", boolean.class)
                            .invoke(ao, Boolean.TRUE);
                } catch (Throwable ignored) {}
            }
            return ao.toBundle();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 兼容 Android 14/15 BAL 策略的 PendingIntent 发送工具，支持揭露动画与应用内深链跳转。 */
    static boolean sendPendingIntentCompat(Context ctx, PendingIntent pi, Intent fillInIntent, View sourceView) {
        if (pi == null) return false;
        try {
            Bundle opts = buildLaunchOptionsBundle(sourceView);
            if (Build.VERSION.SDK_INT >= 23 && opts != null) {
                pi.send(ctx, 0, fillInIntent, null, null, null, opts);
            } else if (ctx != null && fillInIntent != null) {
                pi.send(ctx, 0, fillInIntent);
            } else {
                pi.send();
            }
            return true;
        } catch (Throwable t) {
            try {
                if (ctx != null && fillInIntent != null) pi.send(ctx, 0, fillInIntent);
                else pi.send();
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /** 打开单条通知对应的应用内页面，若通知带 FLAG_AUTO_CANCEL 则同步消除桌面残留。 */
    static boolean openNotificationItem(Context ctx, NotifItem item, View sourceView) {
        if (item == null || item.contentIntent == null) return false;
        boolean sent = sendPendingIntentCompat(ctx, item.contentIntent, null, sourceView);
        if (sent && item.autoCancel && item.isClearable && item.key != null) {
            dismissNotification(item.key);
        }
        return sent;
    }

    // ---------- 应用/分身感知的通知查询与清除接口 ----------

    /** 按包名获取当前未被清除的活跃通知（最新在前，至多 5 条）。永不返回 null，返回只读快照。 */
    static List<NotifItem> getAllByPackage(String pkg) {
        return getAllForApp(pkg, null);
    }

    /** 按包名 + 用户分身获取活跃通知（最新在前，至多 5 条）。 */
    static List<NotifItem> getAllForApp(String pkg, UserHandle user) {
        if (pkg == null || pkg.isEmpty()) return Collections.emptyList();
        List<NotifItem> list = allByPkg.get(pkg);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        List<NotifItem> out = new ArrayList<>();
        for (NotifItem it : list) {
            if (it != null && userMatches(it.user, user)) {
                out.add(it);
                if (out.size() >= 5) break;
            }
        }
        return out.isEmpty() ? Collections.<NotifItem>emptyList() : Collections.unmodifiableList(out);
    }

    static NotifItem getLatestForApp(String pkg, UserHandle user) {
        if (pkg == null || pkg.isEmpty()) return null;
        if (user == null) return latestByPkg.get(pkg);
        List<NotifItem> list = allByPkg.get(pkg);
        if (list == null || list.isEmpty()) return null;
        for (NotifItem it : list) {
            if (it != null && userMatches(it.user, user)) return it;
        }
        return null;
    }

    static int getCountForApp(String pkg, UserHandle user) {
        if (pkg == null || pkg.isEmpty()) return 0;
        if (user == null) {
            Integer c = countByPkg.get(pkg);
            return c != null ? c : 0;
        }
        List<NotifItem> list = allByPkg.get(pkg);
        if (list == null || list.isEmpty()) return 0;
        int c = 0;
        for (NotifItem it : list) {
            if (it != null && userMatches(it.user, user)) c++;
        }
        return c;
    }

    static boolean hasUnreadForApp(String pkg, UserHandle user) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (user == null) return activePackages.contains(pkg);
        return getLatestForApp(pkg, user) != null;
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
        activePackages = new HashSet<>(nextAll.keySet());
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
            return sendPendingIntentCompat(ctx, pi, fill, null);
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

    /** 返回该应用当前是否存在至少一条可清除的未读通知。 */
    static boolean hasClearableNotifications(String pkg) {
        return hasClearableForApp(pkg, null);
    }

    static boolean hasClearableForApp(String pkg, UserHandle user) {
        if (pkg == null || pkg.isEmpty()) return false;
        List<NotifItem> items = allByPkg.get(pkg);
        if (items == null || items.isEmpty()) return false;
        for (NotifItem it : items) {
            if (it != null && it.isClearable && userMatches(it.user, user)) return true;
        }
        return false;
    }

    /** 返回当前拥有可清除未读通知的包名列表，按最新消息时间倒序排列（供首页动态通知浮现使用）。 */
    static List<String> getNotifiedPackagesOrderedByTime() {
        Map<String, List<NotifItem>> src = allByPkg;
        if (src == null || src.isEmpty()) return Collections.emptyList();
        List<NotifItem> newestPerPkg = new ArrayList<>();
        for (Map.Entry<String, List<NotifItem>> e : src.entrySet()) {
            List<NotifItem> items = e.getValue();
            if (items == null || items.isEmpty()) continue;
            NotifItem best = null;
            for (NotifItem it : items) {
                if (it != null && it.isClearable) {
                    if (best == null || it.postTime > best.postTime) {
                        best = it;
                    }
                }
            }
            if (best != null) newestPerPkg.add(best);
        }
        if (newestPerPkg.isEmpty()) return Collections.emptyList();
        Collections.sort(newestPerPkg, (a, b) -> Long.compare(b.postTime, a.postTime));
        List<String> pkgs = new ArrayList<>(newestPerPkg.size());
        for (NotifItem it : newestPerPkg) {
            if (it.pkg != null) pkgs.add(it.pkg);
        }
        return pkgs;
    }

    static int cancelAllForPackage(String pkg) {
        return cancelAllForApp(pkg, null);
    }

    static int cancelAllForApp(String pkg, UserHandle user) {
        NotificationHubService s = instance;
        if (s == null || pkg == null) return 0;
        List<NotifItem> items = allByPkg.get(pkg);
        if (items == null || items.isEmpty()) return 0;
        int cleared = 0;
        Set<String> clearedKeys = new HashSet<>();
        for (NotifItem it : items) {
            if (it != null && it.isClearable && it.key != null && userMatches(it.user, user)) {
                try {
                    s.cancelNotification(it.key);
                    clearedKeys.add(it.key);
                    cleared++;
                } catch (Throwable ignored) {}
            }
        }
        if (cleared > 0) {
            Map<String, List<NotifItem>> src = allByPkg;
            Map<String, List<NotifItem>> nextAll = new HashMap<>();
            Map<String, NotifItem> nextLatest = new HashMap<>();
            Map<String, Integer> nextCounts = new HashMap<>();
            for (Map.Entry<String, List<NotifItem>> e : src.entrySet()) {
                String k = e.getKey();
                if (!pkg.equals(k)) {
                    List<NotifItem> list = e.getValue();
                    if (list != null && !list.isEmpty()) {
                        nextAll.put(k, list);
                        nextCounts.put(k, list.size());
                        nextLatest.put(k, list.get(0));
                    }
                    continue;
                }
                List<NotifItem> kept = new ArrayList<>();
                for (NotifItem it : e.getValue()) {
                    if (it != null && !clearedKeys.contains(it.key)) {
                        kept.add(it);
                    }
                }
                if (!kept.isEmpty()) {
                    nextAll.put(k, kept);
                    nextCounts.put(k, kept.size());
                    nextLatest.put(k, kept.get(0));
                }
            }
            allByPkg = nextAll;
            latestByPkg = nextLatest;
            countByPkg = nextCounts;
            activePackages = new HashSet<>(nextAll.keySet());
            notifyLauncher();
        }
        return cleared;
    }

    private static void notifyLauncher() {
        Runnable r = uiCallback;
        if (r != null) r.run();
    }
}
