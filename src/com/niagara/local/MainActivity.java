package com.niagara.local;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简桌面 · Minimalist Launcher (v2.8.0 原生 Niagara 深度改造版)
 *
 * 核心特性：
 * 1. 【首页一体流 Unified Stream】顶部时钟/日期农历天气/按需电量/矢量日程便签/无边框媒体条/原生小组件
 *    作为 ListView HeaderView 融入首页滚动流，内容少时自动留白下沉至单手拇指区，内容多时整屏顺滑同滚；
 * 2. 【自适应流体字母波浪】默认仅显示手机内实际存在应用的首字母，无大圆球遮挡纯字母涌出，指尖垂直对齐滚动，
 *    且左边缘静默态零抢占系统侧滑返回手势；
 * 3. 【原生统一 Pop-up 体系】支持 App Pop-up 关联子应用集合、右滑与长按视觉同源的线性矢量工具条、
 *    文件夹点击/右滑/重命名/内部排序/直开首项切换，全面淘汰满屏 Emoji 大抽屉；
 * 4. 【动态未读通知浮现与安全左滑】非首页应用来消息时自动临时呈现在首页，清除即隐退；左滑仅清通知绝不误删常用；
 * 5. 【120Hz 紧贴键盘搜索】API 30+ WindowInsetsAnimation 逐帧跟随输入法，空态仅呈最近，命中字高亮，回车执行最佳项。
 */
public class MainActivity extends Activity {

    static final class AppEntry {
        String label;
        String rawLabel;
        String idKey;
        String fullPy;
        String iniPy;
        String t9Digits;
        ComponentName component;
        UserHandle user;
        long userSerial;
        char groupChar;
        boolean isClone;
        Drawable rawIcon;
        Drawable shapedIcon;
        int shapedMode = -1;
        boolean isShortcut;
        ShortcutInfo shortcutInfo;
        Intent shortcutIntent;
    }

    static final class StreamRow {
        static final int TYPE_SECTION = 0;
        static final int TYPE_FAV_APP = 1;
        static final int TYPE_ALL_APP = 2;
        static final int TYPE_CALC_RESULT = 3;
        static final int TYPE_FOLDER = 4;
        static final int TYPE_SHORTCUT_RESULT = 5;
        static final int TYPE_WEB_SEARCH = 6;
        static final int TYPE_SURFACED_DIVIDER = 7;
        static final int VIEW_TYPE_COUNT = 8;

        final int type;
        final String text;
        final AppEntry app;
        final int folderIdx;
        final ShortcutInfo shortcut;

        StreamRow(int type, String text, AppEntry app) {
            this(type, text, app, -1, null);
        }

        StreamRow(int type, String text, AppEntry app, int folderIdx) {
            this(type, text, app, folderIdx, null);
        }

        StreamRow(int type, String text, AppEntry app, ShortcutInfo shortcut) {
            this(type, text, app, -1, shortcut);
        }

        StreamRow(int type, String text, AppEntry app, int folderIdx, ShortcutInfo shortcut) {
            this.type = type;
            this.text = text;
            this.app = app;
            this.folderIdx = folderIdx;
            this.shortcut = shortcut;
        }
    }

    static final class ShortcutSearchEntry {
        final ShortcutInfo shortcut;
        final AppEntry owner;
        final String label;
        final String fullPy;
        final String iniPy;

        ShortcutSearchEntry(ShortcutInfo sc, AppEntry owner, String label) {
            this.shortcut = sc;
            this.owner = owner;
            this.label = label;
            String[] keys = PinyinEngine.buildSearchKeys(label);
            this.fullPy = keys[0];
            this.iniPy = keys[1];
        }
    }

    // 完整字母序列（开启自适应字母导轨时按已安装应用动态精简）
    private static final String WAVE_SYMBOLS = "★ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private static final String PREF_FILE = "niagara_local";

    // 配置键
    private static final String K_FAVS = "favs";
    private static final String K_HIDDEN = "hidden";
    private static final String K_ALIASES = "aliases";
    private static final String K_FOLDERS = "folders";
    private static final String K_APP_POPUPS = "app_popups_v1";
    private static final String K_LAUNCH_COUNTS = "launch_counts";
    private static final String K_RECENT_KEYS = "recent_keys";
    private static final String K_STICKY_NOTE = "sticky_note";
    private static final String K_THEME_IDX = "theme_idx";
    private static final String K_DIM_PCT = "dim_pct";
    private static final String K_FLOW_ANIM = "flow_anim";
    private static final String K_ICON_SHAPE = "icon_shape";
    private static final String K_SHOW_ICONS = "show_icons";
    private static final String K_FAV_CARDS = "fav_cards_v2";
    private static final String K_PULL_DOWN_SEARCH = "pull_down_search_v1";
    private static final String K_COIN_LEFT = "coin_left_v1";
    private static final String K_WALL_STYLE_IDX = "wall_style_idx";
    private static final String K_FONT_IDX = "font_idx";
    private static final String K_TEXT_SCALE = "text_scale";
    private static final String K_ROW_DENSITY = "row_density";
    private static final String K_USE_24H = "use_24h";
    private static final String K_CLOCK_STYLE = "clock_style_v1";
    private static final String K_WAVE_ADAPTIVE_ALPHA = "wave_adaptive_alpha_v1";
    private static final String K_PULL_LAUNCH = "pull_launch";
    private static final String K_INLINE_NOTIF = "inline_notif";
    private static final String K_SURFACE_UNREAD_HOME = "surface_unread_home_v1";
    private static final String K_SHOW_MEDIA = "show_media";
    private static final String K_SHOW_AGENDA = "show_agenda";
    private static final String K_DOUBLE_TAP_LOCK = "double_tap_lock";
    private static final String K_BIOMETRIC_HIDE = "biometric_hide";
    private static final String K_KEEP_ALIVE = "keep_alive";
    private static final String K_STATUS_BAR_MODE = "status_bar_mode_v1";
    private static final String K_WAVE_IDLE_DOTS = "wave_idle_dots_v1";
    private static final String K_ICON_PACK = "icon_pack_pkg_v1";
    private static final String K_CUSTOM_ICONS = "custom_app_icons_v1";
    private static final String K_ONE_HAND_GRAVITY = "one_hand_gravity_v1";
    private static final String K_COIN_TAP_ACTION = "coin_tap_action_v1";
    private static final String K_SEARCH_ENGINE = "search_engine_v1";

    private static final int REQ_EXPORT_JSON = 201;
    private static final int REQ_IMPORT_JSON = 202;
    private static final int REQ_PICK_WALL = 203;
    private static final int REQ_CAL_PERM = 204;
    private static final int REQ_CRED_UNLOCK = 205;
    private static final int REQ_WEATHER_LOC = 206;

    private static final String[] ICON_SHAPES = {
            "超椭圆 · iOS / ColorOS 丝滑",
            "液态玻璃 · Liquid Glass 26",
            "圆润灵动 · One UI / HyperOS",
            "极简正圆 · Pixel 原生圆",
            "莫奈主题 · Material You 柔彩",
            "磨砂微立体 · 3D 质感",
            "极简黑白 · 禅意单色",
            "原样高清 · Native",
            "极简圆点 · Niagara Dots"
    };
    private static final String[] COIN_ACTIONS = {
            "多任务后台卡片",
            "极速搜索（原生模式）",
            "唤醒小布语音助手",
            "隐藏悬浮按钮"
    };
    private static final String[] SEARCH_ENGINE_NAMES = {
            "必应 (Bing)",
            "百度",
            "Google",
            "夸克"
    };
    private static final String[] SEARCH_ENGINE_URLS = {
            "https://www.bing.com/search?q=",
            "https://www.baidu.com/s?wd=",
            "https://www.google.com/search?q=",
            "https://quark.sm.cn/s?q="
    };
    private static final String[] FONT_NAMES = {"现代纤巧", "均衡标准", "醒目中粗", "人文衬线", "极客等宽"};
    private static final String[] FONT_FAMILIES = {"sans-serif-light", "sans-serif", "sans-serif-medium", "serif", "monospace"};
    private static final String[] DENSITY_NAMES = {"紧凑灵巧", "黄金舒适", "舒展宽松"};

    static volatile boolean hasWindowFocusNow = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<ShortcutSearchEntry> cachedShortcuts = new ArrayList<>();
    private final List<StreamRow> streamRows = new ArrayList<>();
    private final Map<Character, Integer> letterRowIndex = new HashMap<>();
    private final Map<Character, String> letterTopPreview = new HashMap<>();
    private final Map<Character, AppEntry> letterTopApp = new HashMap<>();
    private final Set<Character> availableLetters = new HashSet<>();
    private final Map<UserHandle, Long> serialCache = new ConcurrentHashMap<>();
    private final Map<Long, UserHandle> userCache = new ConcurrentHashMap<>();

    private SharedPreferences prefs;
    private LauncherApps launcherApps;
    private UserManager userManager;
    private WidgetHostHelper widgetHelper;
    private PopupCardController popupController;
    private float dpUnit;
    private float textScale = 1.0f;
    private AuroraTheme theme;
    private Typeface typeface;

    // 视图组件
    private FrameLayout workspaceRoot;
    private AuroraBackdropView backdropView;
    private LinearLayout workspaceMainColumn;
    private FrameLayout headerHolder;
    private NiagaraHeaderView niagaraHeaderView;
    private LinearLayout searchBarCard;
    private EditText searchInput;
    private FrameLayout searchPasteBtn;
    private ListView streamListView;
    private StreamAdapter streamAdapter;
    private NiagaraWaveView rightWaveBar, leftGhostWaveBar;
    private NiagaraCoinButton coinButton;

    private int currentTopInsetPx = 0;
    private int currentNavBottomPx = 0;
    private int currentImeBottomPx = 0;
    private float currentImeLiftPx = 0f;
    private boolean imeAnimating = false;
    private final Map<String, Drawable> folderIconCache = new HashMap<>();
    private Map<String, String> customIconsCache = null;
    private Map<String, List<String>> appPopupsCache = null;
    private List<String> lastSurfacedKeys = Collections.emptyList();

    private GestureDetector gestureDetector;
    private Dialog currentSheetDialog;
    private AppEntry virtualSettingsApp;

    private boolean inAllAppsMode = false;
    private boolean searchActive = false;
    private boolean isLoadingApps = false;
    private boolean pendingReloadApps = false;
    private boolean isResumed = false;
    private long lastResumeUptime = 0L;
    private long lastMetricsSyncUptime = 0L;
    private GradientDrawable cachedFavCardNormal, cachedFavCardNotif;
    private char highlightedGroup = 0;
    private boolean isWaveScrubbing = false;
    private long lastWaveReleaseMs = 0L;
    private long lastBackTime = 0;
    private long lastDoubleTapLock = 0;
    private float savedFontScale;
    private int savedDensityDpi;
    private int savedUiNightMode;
    private boolean lastResolvedLightState;
    private boolean dateReceiverRegistered = false;

    private final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            uiHandler.post(() -> {
                refreshHeroStatusWidgets();
                refreshCalendarAgenda();
            });
        }
    };

    private final Runnable clearHighlightTask = new Runnable() {
        @Override
        public void run() {
            highlightedGroup = 0;
            if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        userManager = (UserManager) getSystemService(USER_SERVICE);
        widgetHelper = new WidgetHostHelper(this);
        IconPackManager.loadPack(this, prefs.getString(K_ICON_PACK, ""));
        dpUnit = getResources().getDisplayMetrics().density;

        applyThemeAndWindowFlags();
        virtualSettingsApp = createSettingsAppEntry();
        popupController = createPopupController();
        setContentView(buildNiagaraWorkspace());
        refreshHeroStatusWidgets();
        refreshHomeWidgets();

        readDiskAppCache();
        loadInstalledAppsAsync();
        launcherApps.registerCallback(pkgChangeCallback, uiHandler);
        registerDateChangeReceiver();

        NotificationHubService.uiCallback = () -> {
            uiHandler.removeCallbacks(refreshNotificationsTask);
            long sinceResume = SystemClock.uptimeMillis() - lastResumeUptime;
            long delay = (isResumed && sinceResume < 520L) ? (540L - sinceResume) : 200L;
            uiHandler.postDelayed(refreshNotificationsTask, delay);
        };

        if (prefs.getBoolean(K_KEEP_ALIVE, true)) {
            KeepAliveService.startServiceSafely(this);
        }
        gestureDetector = new GestureDetector(this, screenGestureListener);
        registerAndroid13BackCallback();
    }

    private void registerDateChangeReceiver() {
        if (dateReceiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dateChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(dateChangeReceiver, filter);
            }
            dateReceiverRegistered = true;
        } catch (Throwable ignored) {}
    }

    private final Runnable refreshNotificationsTask = () -> {
        if (!inAllAppsMode && !searchActive && prefs.getBoolean(K_SURFACE_UNREAD_HOME, true)) {
            List<String> nextSurfaced = computeSurfacedUnreadKeys();
            if (!nextSurfaced.equals(lastSurfacedKeys)) {
                rebuildStreamRows();
            } else if (streamAdapter != null) {
                streamAdapter.notifyDataSetChanged();
            }
        } else if (streamAdapter != null) {
            streamAdapter.notifyDataSetChanged();
        }
        refreshWeatherPill();
        if (isResumed) bindActiveMediaSession();
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int newNight = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean nightChanged = (newNight != savedUiNightMode);
        boolean scaleChanged = Math.abs(newConfig.fontScale - savedFontScale) > 0.001f
                || newConfig.densityDpi != savedDensityDpi;

        if (nightChanged || scaleChanged) {
            savedUiNightMode = newNight;
            AuroraBackdropView.syncDayNightWallpaperIfNeeded(this, prefs);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            // 仅在非锁屏状态下响应白昼/暗夜切换重建，避免干扰系统解锁动画
            if (hasWindowFocusNow && (km == null || !km.isKeyguardLocked())) {
                recreate();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hasWindowFocusNow = hasFocus;
        if (hasFocus) {
            resetLockFlags();
        }
    }

    void resetLockFlags() {
        uiHandler.post(() -> {
            try {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) return;
                if (Build.VERSION.SDK_INT >= 27) {
                    setShowWhenLocked(false);
                    setTurnScreenOn(false);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                }
            } catch (Throwable ignored) {}
        });
    }

    private final Runnable resumeRefreshTask = () -> {
        if (!isResumed) return;
        long now = SystemClock.uptimeMillis();
        if (lastMetricsSyncUptime > 0L && (now - lastMetricsSyncUptime) < 15000L) return;
        Looper.myQueue().addIdleHandler(() -> {
            if (!isResumed) return false;
            lastMetricsSyncUptime = SystemClock.uptimeMillis();
            AppExecutors.execute(() -> {
                AuroraBackdropView.syncDayNightWallpaperIfNeeded(MainActivity.this, prefs);
                final LunarStatusEngine.DeviceMetrics dm = LunarStatusEngine.readMetrics(MainActivity.this);
                uiHandler.post(() -> {
                    if (!isResumed) return;
                    applyHeroStatusMetrics(dm);
                    if (WeatherSyncEngine.hasLocationPermission(MainActivity.this)) {
                        WeatherSyncEngine.fetchWeatherAsync(MainActivity.this, false, s -> refreshWeatherPill());
                    }
                    refreshCalendarAgenda();
                    bindActiveMediaSession();
                });
            });
            return false;
        });
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (widgetHelper != null) widgetHelper.startListeningSafe();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (widgetHelper != null) widgetHelper.stopListeningSafe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        lastResumeUptime = SystemClock.uptimeMillis();

        AuroraTheme latestTheme = AuroraTheme.resolve(this, prefs.getInt(K_THEME_IDX, AuroraTheme.IDX_DEFAULT));
        if (latestTheme.isLight != lastResolvedLightState) {
            recreate();
            return;
        }
        if (backdropView != null) backdropView.setScreenVisible(true);

        NotificationHubService.refreshSnapshotAsync();
        uiHandler.removeCallbacks(refreshNotificationsTask);
        uiHandler.post(refreshNotificationsTask);

        uiHandler.removeCallbacks(resumeRefreshTask);
        uiHandler.postDelayed(resumeRefreshTask, 900L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        if (popupController != null) popupController.dismiss(false);
        uiHandler.removeCallbacks(resumeRefreshTask);
        if (backdropView != null) backdropView.setScreenVisible(false);
        uiHandler.removeCallbacks(mediaProgressTicker);
        uiHandler.removeCallbacks(clearHighlightTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dateReceiverRegistered) {
            try { unregisterReceiver(dateChangeReceiver); } catch (Throwable ignored) {}
            dateReceiverRegistered = false;
        }
        try { launcherApps.unregisterCallback(pkgChangeCallback); } catch (Throwable ignored) {}
        try { if (mediaSessionMgr != null && mediaListening) mediaSessionMgr.removeOnActiveSessionsChangedListener(sessionChangeListener); } catch (Throwable ignored) {}
        try { if (activeMediaController != null) activeMediaController.unregisterCallback(mediaControllerCb); } catch (Throwable ignored) {}
        NotificationHubService.uiCallback = null;
        uiHandler.removeCallbacks(resumeRefreshTask);
        if (currentSheetDialog != null && currentSheetDialog.isShowing()) currentSheetDialog.dismiss();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (inAllAppsMode || searchActive || (popupController != null && popupController.isShowing())
                || (currentSheetDialog != null && currentSheetDialog.isShowing())) {
            returnToHomeFavorites();
        } else {
            // 原本就在首页常用列表：0 运算，0 重建，保证系统窗口回弹动画 120Hz 丝滑！
            if (streamListView != null && streamListView.getFirstVisiblePosition() > 0) {
                stopListFlingAndResetTop();
            }
        }
    }

    private void applyThemeAndWindowFlags() {
        int themeIdx = prefs.getInt(K_THEME_IDX, AuroraTheme.IDX_DEFAULT);
        theme = AuroraTheme.resolve(this, themeIdx);
        lastResolvedLightState = theme.isLight;
        savedFontScale = getResources().getConfiguration().fontScale;
        savedDensityDpi = getResources().getConfiguration().densityDpi;
        savedUiNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        int fontIdx = Math.max(0, Math.min(FONT_FAMILIES.length - 1, prefs.getInt(K_FONT_IDX, 1)));
        typeface = Typeface.create(FONT_FAMILIES[fontIdx], Typeface.NORMAL);
        textScale = prefs.getFloat(K_TEXT_SCALE, 1.0f);

        int sysFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (theme.isLight) {
            sysFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(sysFlags);
        if (theme.useSystemWallpaper) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            getWindow().setBackgroundDrawable(new ColorDrawable(theme.baseBg));
        }
        cachedFavCardNormal = theme.favRowSurface(dpUnit, false);
        cachedFavCardNotif = theme.favRowSurface(dpUnit, true);
    }

    private int dp(float v) { return Math.round(v * dpUnit); }
    private float sp(float v) { return v * textScale; }

    private TextView makeText(String s, float spSize, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(color);
        tv.setTypeface(bold ? Typeface.create(typeface, Typeface.BOLD) : typeface);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(spSize));
        return tv;
    }

    // ---------- 构建原生 Niagara 一体流主界面 ----------
    private View buildNiagaraWorkspace() {
        final FrameLayout root = new FrameLayout(this);
        workspaceRoot = root;

        // 1. 底层：日出/日落护眼柔光壁纸画布（单次绘制零重影）
        backdropView = new AuroraBackdropView(this);
        backdropView.configure(theme, prefs.getInt(K_DIM_PCT, 0), prefs.getBoolean(K_FLOW_ANIM, true));
        root.addView(backdropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. 主纵向一体流容器（Unified Stream）
        final LinearLayout mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setClipChildren(false);
        mainColumn.setClipToPadding(false);
        workspaceMainColumn = mainColumn;

        // 2.1 原生 Niagara 顶部一览 HeaderView（时钟 + 日期农历天气 + 按需电量 + 矢量日程/便签 + 极简媒体条 + 原生小组件）
        niagaraHeaderView = new NiagaraHeaderView(
                this,
                theme,
                typeface,
                textScale,
                prefs.getInt(K_CLOCK_STYLE, NiagaraHeaderView.CLOCK_CLASSIC),
                prefs.getBoolean(K_USE_24H, true),
                resolveStatusBarMode(),
                new NiagaraHeaderView.Callbacks() {
                    @Override public void onClockClick() {
                        if (searchActive || inAllAppsMode) returnToHomeFavorites();
                        else startIntentSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                    }
                    @Override public void onDateClick() {
                        if (searchActive || inAllAppsMode) returnToHomeFavorites();
                        else openSystemCalendarApp();
                    }
                    @Override public void onWeatherClick() { handleWeatherBarClick(); }
                    @Override public void onDeviceStatusClick() { showDeviceStatusDialog(); }
                    @Override public void onAgendaClick() { openActiveCalendarEvent(); }
                    @Override public void onNoteClick() { promptEditStickyNote(); }
                    @Override public void onMediaClick() { launchMediaPlayerApp(); }
                    @Override public void onMediaTransport(int which) { triggerMediaTransport(which); }
                    @Override public void onLongPress() { openControlCenterSheet(); }
                });
        refreshStickyNotePill();

        headerHolder = new FrameLayout(this);
        headerHolder.setClipChildren(false);
        headerHolder.addView(niagaraHeaderView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2.2 贴底极速搜索栏（API 30+ 120Hz 紧贴软键盘滑升）
        searchBarCard = buildSearchBarCard();
        searchBarCard.setElevation(dp(10));

        // 2.3 主应用一体流 ListView（上下 28dp 羽化渐隐，严格避开状态栏与导航条）
        streamListView = new ListView(this);
        streamListView.setDivider(null);
        streamListView.setVerticalScrollBarEnabled(false);
        streamListView.setVerticalFadingEdgeEnabled(true);
        streamListView.setFadingEdgeLength(dp(28));
        streamListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        streamListView.setSelector(android.R.color.transparent);
        streamListView.setClipToPadding(false);
        streamListView.setClipChildren(false);
        streamListView.setScrollingCacheEnabled(false);
        streamListView.setAnimationCacheEnabled(false);
        streamListView.addHeaderView(headerHolder, null, false);
        streamAdapter = new StreamAdapter();
        streamListView.setAdapter(streamAdapter);

        streamListView.setOnItemClickListener((parent, view, position, id) -> {
            int idx = position - streamListView.getHeaderViewsCount();
            if (idx < 0 || idx >= streamRows.size()) return;
            StreamRow row = streamRows.get(idx);
            if (row.type == StreamRow.TYPE_CALC_RESULT) {
                copyToClipboard(row.text);
            }
        });

        streamListView.setOnItemLongClickListener((parent, view, position, id) -> true);

        streamListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState != SCROLL_STATE_IDLE) {
                    hideSoftKeyboard();
                    if (highlightedGroup != 0) {
                        uiHandler.removeCallbacks(clearHighlightTask);
                        highlightedGroup = 0;
                        streamAdapter.notifyDataSetChanged();
                    }
                }
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (waveCouplingActive) applyWaveDisplacementToVisibleItems();
            }
        });

        streamListView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((b - t) != (ob - ot) || (r - l) != (or - ol)) {
                applyOneHandedListPadding();
            }
        });
        mainColumn.addView(streamListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(mainColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. 左右双侧流体字母波浪导轨（自适应字母 + 静默圆点 + 无圆球纯字母涌出 + 左边缘静止零抢系统返回）
        rightWaveBar = new NiagaraWaveView(this, WAVE_SYMBOLS);
        rightWaveBar.setAlignLeft(false);
        leftGhostWaveBar = new NiagaraWaveView(this, WAVE_SYMBOLS);
        leftGhostWaveBar.setAlignLeft(true);
        leftGhostWaveBar.setSilentAtRest(true);
        leftGhostWaveBar.setVisibility(View.VISIBLE);

        boolean idleDots = prefs.getBoolean(K_WAVE_IDLE_DOTS, true);
        boolean pullLaunch = prefs.getBoolean(K_PULL_LAUNCH, true);
        rightWaveBar.bindStyle(theme, typeface);
        rightWaveBar.setIdleDotsMode(idleDots);
        rightWaveBar.setPullLaunchEnabled(pullLaunch);
        leftGhostWaveBar.bindStyle(theme, typeface);
        leftGhostWaveBar.setIdleDotsMode(idleDots);
        leftGhostWaveBar.setPullLaunchEnabled(pullLaunch);

        NiagaraWaveView.Listener waveListener = new NiagaraWaveView.Listener() {
            @Override
            public void onWaveScrub(int index, char letter, float normX, float normY, float strength, boolean pullArmed) {
                isWaveScrubbing = true;
                uiHandler.removeCallbacks(clearHighlightTask);
                if (backdropView != null) {
                    backdropView.onWaveInteract(normX, normY, strength);
                }
                jumpToLetterApplications(index, letter, normY);
            }

            @Override
            public void onWaveRelease(int index, char letter, boolean triggerPullLaunch) {
                isWaveScrubbing = false;
                lastWaveReleaseMs = SystemClock.uptimeMillis();
                if (backdropView != null) {
                    backdropView.onWaveInteract(0.85f, 0.5f, 0f);
                }
                uiHandler.removeCallbacks(clearHighlightTask);
                uiHandler.postDelayed(clearHighlightTask, 900);

                if (triggerPullLaunch && prefs.getBoolean(K_PULL_LAUNCH, true)) {
                    AppEntry top = letterTopApp.get(letter);
                    if (top != null) {
                        Toast.makeText(MainActivity.this, "直达：" + top.label, Toast.LENGTH_SHORT).show();
                        launchApp(top);
                    }
                }
            }

            @Override
            public void onWaveBackSwipe() {
                isWaveScrubbing = false;
                if (searchActive || inAllAppsMode) {
                    getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    returnToHomeFavorites();
                }
            }
        };
        rightWaveBar.setListener(waveListener);
        leftGhostWaveBar.setListener(waveListener);
        setupWaveListCoupling();

        root.addView(rightWaveBar, new FrameLayout.LayoutParams(
                dp(210), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        root.addView(leftGhostWaveBar, new FrameLayout.LayoutParams(
                dp(210), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        // 3.5 贴底悬浮搜索栏（紧贴软键盘上方，拇指零伸展直达）
        final FrameLayout.LayoutParams searchCardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        searchCardLp.leftMargin = dp(16);
        searchCardLp.rightMargin = dp(16);
        searchCardLp.bottomMargin = dp(12);
        root.addView(searchBarCard, searchCardLp);

        // 4. 右下角 Niagara 灵动悬浮按钮（平滑形变淡出联动搜索）
        coinButton = new NiagaraCoinButton(this);
        coinButton.bindTheme(theme);
        applyCoinButtonConfig();
        coinButton.setCallback(new NiagaraCoinButton.ActionCallback() {
            @Override
            public void onTap() {
                int act = prefs.getInt(K_COIN_TAP_ACTION, 1);
                if (act == 1) {
                    if (searchActive) closeInstantSearch();
                    else openInstantSearch();
                } else if (act == 2) {
                    if (!launchColorOsXiaoBu()) openInstantSearch();
                } else {
                    quickLaunchRecentsOverview();
                }
            }
            @Override
            public void onSwipeUp() {
                switchStreamMode(!inAllAppsMode);
            }
            @Override
            public void onSwipeDown() {
                if (searchActive || inAllAppsMode) returnToHomeFavorites();
                else expandSystemNotificationPanel();
            }
            @Override
            public void onSwipeLeft() {
                if (searchActive || inAllAppsMode) returnToHomeFavorites();
                else if (prefs.getInt(K_COIN_TAP_ACTION, 1) == 1) quickLaunchRecentsOverview();
                else openInstantSearch();
            }
            @Override
            public void onLongPress() {
                openControlCenterSheet();
            }
        });
        final FrameLayout.LayoutParams coinLp = new FrameLayout.LayoutParams(
                dp(54), dp(54),
                Gravity.BOTTOM | (prefs.getBoolean(K_COIN_LEFT, false) ? Gravity.START : Gravity.END));
        root.addView(coinButton, coinLp);
        coinButton.bringToFront();

        // API 30+ 120Hz 紧贴软键盘逐帧联动（WindowInsetsAnimation.Callback）
        if (Build.VERSION.SDK_INT >= 30) {
            root.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(
                    WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
                @Override
                public void onPrepare(WindowInsetsAnimation animation) {
                    if ((animation.getTypeMask() & WindowInsets.Type.ime()) != 0) {
                        imeAnimating = true;
                    }
                }

                @Override
                public WindowInsets onProgress(WindowInsets insets, List<WindowInsetsAnimation> runningAnimations) {
                    int imeBot = insets.getInsets(WindowInsets.Type.ime()).bottom;
                    int navBot = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                    currentNavBottomPx = navBot;
                    currentImeBottomPx = Math.max(navBot, imeBot);
                    currentImeLiftPx = Math.max(0f, currentImeBottomPx - currentNavBottomPx);
                    if (searchBarCard != null && searchBarCard.getVisibility() == View.VISIBLE) {
                        searchBarCard.setTranslationY(-currentImeLiftPx + (1f - searchTransitionProgress) * dp(18));
                    }
                    if (searchActive && streamListView != null) {
                        int searchBotPad = currentImeBottomPx + dp(82);
                        if (streamListView.getPaddingBottom() != searchBotPad) {
                            streamListView.setPadding(
                                    streamListView.getPaddingLeft(),
                                    streamListView.getPaddingTop(),
                                    streamListView.getPaddingRight(),
                                    searchBotPad);
                        }
                    }
                    return insets;
                }

                @Override
                public void onEnd(WindowInsetsAnimation animation) {
                    if ((animation.getTypeMask() & WindowInsets.Type.ime()) != 0) {
                        imeAnimating = false;
                        applySearchBarInsets();
                        applyOneHandedListPadding();
                    }
                }
            });
        }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int navBottom = insets.getSystemWindowInsetBottom();
            int imeBottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    topInset = insets.getInsets(WindowInsets.Type.statusBars()).top;
                    navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                    imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                } catch (Throwable ignored) {}
            } else {
                imeBottom = navBottom;
            }
            currentTopInsetPx = topInset;
            currentNavBottomPx = navBottom;
            currentImeBottomPx = Math.max(navBottom, imeBottom);
            if (!imeAnimating) {
                currentImeLiftPx = Math.max(0f, currentImeBottomPx - currentNavBottomPx);
            }

            mainColumn.setPadding(0, topInset, 0, 0);
            applySearchBarInsets();
            applyOneHandedListPadding();

            for (NiagaraWaveView wb : new NiagaraWaveView[]{leftGhostWaveBar, rightWaveBar}) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wb.getLayoutParams();
                lp.topMargin = topInset + dp(164);
                lp.bottomMargin = navBottom + dp(88);
                wb.setLayoutParams(lp);
            }
            boolean coinOnLeft = prefs.getBoolean(K_COIN_LEFT, false);
            coinLp.gravity = Gravity.BOTTOM | (coinOnLeft ? Gravity.START : Gravity.END);
            coinLp.bottomMargin = navBottom + dp(20);
            coinLp.leftMargin = coinOnLeft ? dp(22) : 0;
            coinLp.rightMargin = coinOnLeft ? 0 : dp(18);
            coinButton.setLayoutParams(coinLp);
            coinButton.bringToFront();
            if (searchBarCard != null) searchBarCard.bringToFront();
            return insets;
        });

        return root;
    }

    private void applySearchBarInsets() {
        if (searchBarCard == null) return;
        ViewGroup.LayoutParams rawLp = searchBarCard.getLayoutParams();
        if (rawLp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rawLp;
            if (Build.VERSION.SDK_INT >= 30) {
                int restBot = currentNavBottomPx + dp(10);
                if (lp.bottomMargin != restBot) {
                    lp.bottomMargin = restBot;
                    searchBarCard.setLayoutParams(lp);
                }
                if (searchBarCard.getVisibility() == View.VISIBLE) {
                    searchBarCard.setTranslationY(-currentImeLiftPx + (1f - searchTransitionProgress) * dp(18));
                }
            } else {
                int targetBot = Math.max(currentNavBottomPx, currentImeBottomPx) + dp(10);
                if (lp.bottomMargin != targetBot) {
                    lp.bottomMargin = targetBot;
                    searchBarCard.setLayoutParams(lp);
                }
            }
        }
    }

    /**
     * 点到或滑到某个字母时，仅在命中字母变化时刷新高亮与指尖垂直对齐定位，杜绝每像素全量 notifyDataSetChanged。
     */
    private void jumpToLetterApplications(int idx, char ch, float normY) {
        if (ch == '★') {
            if (inAllAppsMode || searchActive) {
                returnToHomeFavorites();
            }
            return;
        }
        if (searchActive) {
            searchActive = false;
            searchInput.setText("");
            searchBarCard.setVisibility(View.GONE);
            searchTransitionTargetOn = false;
            searchTransitionProgress = 0f;
            updateSearchTransition(0f);
        }

        boolean wasInAllApps = inAllAppsMode;
        if (!inAllAppsMode) {
            inAllAppsMode = true;
            updateFirstPageExtrasVisibility();
            rebuildStreamRows();
        }
        if (ch != highlightedGroup || !wasInAllApps) {
            highlightedGroup = ch;
            streamAdapter.notifyDataSetChanged();
            Integer pos = letterRowIndex.get(ch);
            if (pos == null) pos = findNearestLetterPosition(idx);
            if (pos != null) {
                int listH = streamListView.getHeight();
                int anchorOffset = Math.max(0, Math.min(Math.round(listH * 0.55f), Math.round(normY * listH) - dp(44)));
                final int targetAdapterPos = pos + streamListView.getHeaderViewsCount();
                final int targetOffset = anchorOffset;
                streamListView.smoothScrollBy(0, 0);
                streamListView.setSelectionFromTop(targetAdapterPos, targetOffset);
                if (!wasInAllApps) {
                    streamListView.post(() -> streamListView.setSelectionFromTop(targetAdapterPos, targetOffset));
                }
            }
        }
        hideSoftKeyboard();
    }

    // ---------- 边缘波浪导轨 ↔ 主列表物理联动（左缘锚定 + 安全正向内敛微推，彻底杜绝左侧裁切） ----------
    private static final float WAVE_LIST_SIGMA_DP = 72f;
    private static final float WAVE_LIST_PUSH_RIGHT_DP = 8f;
    private static final float WAVE_LIST_PUSH_LEFT_DP = 10f;
    private static final float WAVE_LIST_ICON_PARALLAX_DP = 2.5f;
    private static final float WAVE_LIST_SCALE_GAIN = 0.025f;
    private static final long WAVE_REBOUND_MS = 180L;

    private boolean waveCouplingActive = false;
    private float waveCouplingTouchY = -1f;
    private float waveCouplingPushDp = WAVE_LIST_PUSH_RIGHT_DP;
    private ValueAnimator waveReboundAnim;
    private final int[] waveListLoc = new int[2];

    private void setupWaveListCoupling() {
        bindWaveCoupling(rightWaveBar, WAVE_LIST_PUSH_RIGHT_DP);
        bindWaveCoupling(leftGhostWaveBar, WAVE_LIST_PUSH_LEFT_DP);
    }

    private void bindWaveCoupling(NiagaraWaveView bar, final float pushDp) {
        if (bar == null) return;
        bar.setOnWaveScrubListener(new NiagaraWaveView.OnWaveScrubListener() {
            @Override
            public void onWaveScrubStart() {
                cancelWaveRebound();
                waveCouplingActive = true;
                waveCouplingPushDp = pushDp;
            }

            @Override
            public void onWaveScrub(char currentLetter, float progress, float touchScreenY) {
                if (!waveCouplingActive) return;
                waveCouplingTouchY = touchScreenY;
                applyWaveDisplacementToVisibleItems();
            }

            @Override
            public void onWaveScrubEnd() {
                waveCouplingActive = false;
                waveCouplingTouchY = -1f;
                resetVisibleItemsDisplacement();
            }
        });
    }

    /** 仅遍历屏幕内可见项；只改 translationX / scale（GPU 合成层属性），绝不触发 requestLayout。 */
    private void applyWaveDisplacementToVisibleItems() {
        if (streamListView == null || waveCouplingTouchY < 0f) return;
        streamListView.getLocationOnScreen(waveListLoc);
        float sigma = WAVE_LIST_SIGMA_DP * dpUnit;
        int count = streamListView.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = streamListView.getChildAt(i);
            if (child == null || child == headerHolder) continue;
            float centerY = waveListLoc[1] + child.getTop() + child.getHeight() * 0.5f;
            float d = centerY - waveCouplingTouchY;
            float factor = (float) Math.exp(-(d * d) / (2.0f * sigma * sigma));
            applyItemWaveDisplacement(child, factor);
        }
    }

    private void applyItemWaveDisplacement(View itemView, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        itemView.setPivotX(0f);
        itemView.setPivotY(itemView.getHeight() * 0.5f);
        itemView.setTranslationX(dpUnit * waveCouplingPushDp * f);
        float scale = 1.0f + WAVE_LIST_SCALE_GAIN * f;
        itemView.setScaleX(scale);
        itemView.setScaleY(scale);
        View icon = findRowIconView(itemView);
        if (icon != null) {
            icon.setTranslationX(dpUnit * WAVE_LIST_ICON_PARALLAX_DP * f);
        }
    }

    private View findRowIconView(View itemView) {
        if ("app_row_v2".equals(itemView.getTag()) && itemView instanceof ViewGroup) {
            View card = ((ViewGroup) itemView).getChildAt(0);
            if (card instanceof ViewGroup) {
                ViewGroup cg = (ViewGroup) card;
                for (int i = 0; i < cg.getChildCount(); i++) {
                    View c = cg.getChildAt(i);
                    if (c instanceof ImageView) return c;
                }
            }
        }
        return null;
    }

    private void resetItemWaveTransform(View itemView) {
        if (itemView.getTranslationX() != 0f) itemView.setTranslationX(0f);
        if (itemView.getScaleX() != 1f) itemView.setScaleX(1f);
        if (itemView.getScaleY() != 1f) itemView.setScaleY(1f);
        View icon = findRowIconView(itemView);
        if (icon != null && icon.getTranslationX() != 0f) icon.setTranslationX(0f);
    }

    private void cancelWaveRebound() {
        if (waveReboundAnim != null) {
            ValueAnimator a = waveReboundAnim;
            waveReboundAnim = null;
            a.cancel();
        }
    }

    /** 松手后：对已偏移的可见项在 180ms 内以贝塞尔阻尼曲线回弹至 translationX=0 / scale=1。 */
    private void resetVisibleItemsDisplacement() {
        cancelWaveRebound();
        if (streamListView == null) return;
        int count = streamListView.getChildCount();
        final List<View> views = new ArrayList<>();
        final List<float[]> from = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            View child = streamListView.getChildAt(i);
            if (child == null || child == headerHolder) continue;
            View icon = findRowIconView(child);
            float tx = child.getTranslationX();
            float sc = child.getScaleX();
            float itx = icon != null ? icon.getTranslationX() : 0f;
            if (tx == 0f && sc == 1f && itx == 0f) continue;
            views.add(child);
            from.add(new float[]{tx, sc, itx});
        }
        if (views.isEmpty()) return;

        final ValueAnimator anim = ValueAnimator.ofFloat(1f, 0f);
        anim.setDuration(WAVE_REBOUND_MS);
        anim.setInterpolator(new PathInterpolator(0.25f, 1.0f, 0.5f, 1.0f));
        anim.addUpdateListener(va -> {
            float v = (float) va.getAnimatedValue();
            for (int i = 0; i < views.size(); i++) {
                View child = views.get(i);
                float[] f = from.get(i);
                child.setTranslationX(f[0] * v);
                float s = 1f + (f[1] - 1f) * v;
                child.setScaleX(s);
                child.setScaleY(s);
                View icon = findRowIconView(child);
                if (icon != null) icon.setTranslationX(f[2] * v);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (waveReboundAnim != anim) return;
                waveReboundAnim = null;
                for (View child : views) resetItemWaveTransform(child);
            }
        });
        waveReboundAnim = anim;
        anim.start();
    }

    private Integer findNearestLetterPosition(int idx) {
        String syms = rightWaveBar != null ? rightWaveBar.getSymbols() : WAVE_SYMBOLS;
        if (syms == null || syms.isEmpty()) return null;
        int clamped = Math.max(0, Math.min(syms.length() - 1, idx));
        for (int i = clamped; i < syms.length(); i++) {
            Integer p = letterRowIndex.get(syms.charAt(i));
            if (p != null) return p;
        }
        for (int i = clamped; i >= 0; i--) {
            Integer p = letterRowIndex.get(syms.charAt(i));
            if (p != null) return p;
        }
        return null;
    }

    private void stopListFlingAndResetTop() {
        if (streamListView == null) return;
        try {
            streamListView.smoothScrollBy(0, 0);
            streamListView.setSelectionFromTop(0, 0);
        } catch (Throwable ignored) {}
    }

    private void cooldownWaveBars() {
        isWaveScrubbing = false;
        lastWaveReleaseMs = SystemClock.uptimeMillis();
        if (rightWaveBar != null) rightWaveBar.cancelAndCooldown(420L);
        if (leftGhostWaveBar != null) leftGhostWaveBar.cancelAndCooldown(420L);
        if (backdropView != null) backdropView.onWaveInteract(0.85f, 0.5f, 0f);
    }

    private void switchStreamMode(boolean allApps) {
        if (inAllAppsMode != allApps) {
            if (!allApps) cooldownWaveBars();
            stopListFlingAndResetTop();
            inAllAppsMode = allApps;
            highlightedGroup = 0;
            updateFirstPageExtrasVisibility();
            rebuildStreamRows();
            stopListFlingAndResetTop();
            animateStreamListTransition();
        }
    }

    private void animateStreamListTransition() {
        if (streamListView == null) return;
        streamListView.animate().cancel();
        streamListView.setAlpha(0.60f);
        streamListView.setTranslationY(dp(10));
        streamListView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(165L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .start();
    }

    private void applyOneHandedListPadding() {
        if (streamListView == null) return;
        int left = dp(22);
        int right = dp(34);
        int navBot = currentNavBottomPx;

        if (searchActive) {
            int searchTopPad = dp(10);
            int searchBotPad = Math.max(navBot, currentImeBottomPx) + dp(82);
            if (streamListView.getPaddingLeft() != left || streamListView.getPaddingRight() != right
                    || streamListView.getPaddingTop() != searchTopPad || streamListView.getPaddingBottom() != searchBotPad) {
                streamListView.setPadding(left, searchTopPad, right, searchBotPad);
            }
            return;
        }

        int botPad = navBot + dp(82);
        if (inAllAppsMode) {
            int topP = dp(8);
            if (streamListView.getPaddingLeft() != left || streamListView.getPaddingRight() != right
                    || streamListView.getPaddingTop() != topP || streamListView.getPaddingBottom() != botPad) {
                streamListView.setPadding(left, topP, right, botPad);
            }
            return;
        }

        // 首页一体流模式：顶部时钟自然贴合状态栏下方，剩余留白由 NiagaraHeaderView 底部呼吸垫片动态控制
        int homeTopPad = dp(18);
        if (streamListView.getPaddingLeft() != left || streamListView.getPaddingRight() != right
                || streamListView.getPaddingTop() != homeTopPad || streamListView.getPaddingBottom() != botPad) {
            streamListView.setPadding(left, homeTopPad, right, botPad);
        }
        if (niagaraHeaderView == null) return;
        if (!prefs.getBoolean(K_ONE_HAND_GRAVITY, true)) {
            niagaraHeaderView.setGravitySpacerHeight(dp(16));
            return;
        }
        int listH = streamListView.getHeight();
        int listW = streamListView.getWidth() - left - right;
        if (listH <= 0 || listW <= 0) {
            niagaraHeaderView.setGravitySpacerHeight(dp(18));
            return;
        }
        int availH = listH - homeTopPad - botPad;
        int headerContentH = niagaraHeaderView.measureContentHeightWithoutSpacer(listW);
        float rowHeightPx = dp(56f * getRowSpacingMultiplier());
        int estRowsH = Math.round(streamRows.size() * rowHeightPx);
        int extraSpace = availH - headerContentH - estRowsH;

        int spacer;
        if (extraSpace <= dp(24)) {
            spacer = dp(12);
        } else if (niagaraHeaderView.hasPinnedWidgetsVisible() || niagaraHeaderView.isMediaVisible()) {
            spacer = Math.max(dp(12), Math.min(dp(32), Math.round(extraSpace * 0.25f)));
        } else {
            spacer = Math.max(dp(18), Math.min(Math.round(extraSpace * 0.62f), availH / 2));
            int skyMin = Math.round(getResources().getDisplayMetrics().heightPixels * 0.14f);
            spacer = Math.max(spacer, Math.min(skyMin, Math.min(extraSpace, availH / 2)));
        }
        niagaraHeaderView.setGravitySpacerHeight(spacer);
    }

    private void applyCoinButtonConfig() {
        if (coinButton == null) return;
        int act = prefs.getInt(K_COIN_TAP_ACTION, 1);
        if (act == 1) coinButton.setIconMode(NiagaraCoinButton.ICON_SEARCH);
        else if (act == 2) coinButton.setIconMode(NiagaraCoinButton.ICON_ASSIST);
        else coinButton.setIconMode(NiagaraCoinButton.ICON_RECENTS);
        coinButton.setUserHidden(act == 3);
    }

    private void openSystemCalendarApp() {
        try {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
            ContentUris.appendId(builder, System.currentTimeMillis());
            Intent i = new Intent(Intent.ACTION_VIEW, builder.build());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (startIntentSafely(i)) return;
        } catch (Throwable ignored) {}
        Intent fallback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startIntentSafely(fallback);
    }

    private void refreshHomeWidgets() {
        if (niagaraHeaderView == null || widgetHelper == null) return;
        LinearLayout widgetsBox = niagaraHeaderView.getWidgetsContainer();
        if (inAllAppsMode || searchActive) {
            widgetsBox.setVisibility(View.GONE);
            return;
        }
        widgetHelper.populateWidgets(this, prefs, widgetsBox, theme, dpUnit, (widget, info) -> {
            String label = info.loadLabel(getPackageManager());
            showSingleChoiceDialog("微件设置 · " + label,
                    new String[]{"紧凑高度 (96dp)", "标准高度 (136dp)", "宽展高度 (196dp)", "移除此微件"},
                    1, w -> {
                        if (w == 0) widgetHelper.updateWidgetHeight(prefs, widget.id, 96);
                        else if (w == 1) widgetHelper.updateWidgetHeight(prefs, widget.id, 136);
                        else if (w == 2) widgetHelper.updateWidgetHeight(prefs, widget.id, 196);
                        else if (w == 3) widgetHelper.removeWidget(prefs, widget.id);
                        refreshHomeWidgets();
                    });
        });
        applyOneHandedListPadding();
    }

    // ---------- 灵动搜索过渡（220ms 完整执行：壁纸景深 + Header 平滑淡出 + 圆钮柔和缩隐 + 搜索栏滑升） ----------
    private static final long SEARCH_TRANSITION_MS = 220L;
    private float searchTransitionProgress = 0f;
    private boolean searchTransitionTargetOn = false;
    private ValueAnimator searchTransitionAnim;

    public void updateSearchTransition(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        searchTransitionProgress = p;
        if (backdropView != null) backdropView.setSearchTransitionProgress(p);
        if (niagaraHeaderView != null) niagaraHeaderView.applySearchFadeProgress(p);
        if (coinButton != null) coinButton.applySearchTransition(p);
        if (searchBarCard != null && searchBarCard.getVisibility() == View.VISIBLE) {
            searchBarCard.setAlpha(p);
            float baseLift = Build.VERSION.SDK_INT >= 30 ? -currentImeLiftPx : 0f;
            searchBarCard.setTranslationY(baseLift + (1f - p) * dp(18));
        }
    }

    private void animateSearchTransition(final boolean on) {
        if (on == searchTransitionTargetOn && searchTransitionAnim == null
                && Math.abs(searchTransitionProgress - (on ? 1f : 0f)) < 0.001f) {
            return;
        }
        searchTransitionTargetOn = on;
        ValueAnimator prevAnim = searchTransitionAnim;
        searchTransitionAnim = null;
        if (prevAnim != null) {
            prevAnim.cancel();
        }
        if (on && searchBarCard != null) {
            searchBarCard.setVisibility(View.VISIBLE);
            searchBarCard.bringToFront();
        }
        if (!on && !inAllAppsMode && niagaraHeaderView != null) {
            niagaraHeaderView.setVisibility(View.VISIBLE);
        }
        updateSearchTransition(searchTransitionProgress);
        final ValueAnimator anim = ValueAnimator.ofFloat(searchTransitionProgress, on ? 1f : 0f);
        anim.setDuration(SEARCH_TRANSITION_MS);
        anim.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f));
        anim.addUpdateListener(va -> updateSearchTransition((float) va.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (searchTransitionAnim != anim) return;
                searchTransitionAnim = null;
                if (niagaraHeaderView != null) {
                    if (searchActive || inAllAppsMode) {
                        niagaraHeaderView.setVisibility(View.GONE);
                    } else {
                        niagaraHeaderView.setVisibility(View.VISIBLE);
                        niagaraHeaderView.resetSearchFade();
                    }
                }
                if (searchBarCard != null && !searchActive) {
                    searchBarCard.setVisibility(View.GONE);
                    searchBarCard.setAlpha(1f);
                    searchBarCard.setTranslationY(0f);
                }
            }
        });
        searchTransitionAnim = anim;
        anim.start();
    }

    private void updateFirstPageExtrasVisibility() {
        boolean homeOnly = !inAllAppsMode && !searchActive;
        if (niagaraHeaderView != null) {
            if (homeOnly) {
                niagaraHeaderView.setVisibility(View.VISIBLE);
                if (!searchTransitionTargetOn && searchTransitionAnim == null) {
                    niagaraHeaderView.resetSearchFade();
                }
                refreshStickyNotePill();
                refreshCalendarAgenda();
                updateMediaCardUi();
                refreshHomeWidgets();
            } else if (inAllAppsMode && !searchActive) {
                niagaraHeaderView.setVisibility(View.GONE);
            }
            // 进入搜索态时保持 HeaderView 短暂可见，由 animateSearchTransition(true) 执行完整 220ms 淡出后在回调隐藏
        }
        animateSearchTransition(searchActive);
        if (streamListView != null) {
            streamListView.setVisibility(View.VISIBLE);
            streamListView.setStackFromBottom(searchActive);
            applyOneHandedListPadding();
        }
        applySearchBarInsets();
        applyCoinButtonConfig();
        if (rightWaveBar != null) {
            rightWaveBar.setBackSwipePreferred(inAllAppsMode || searchActive);
        }
        if (leftGhostWaveBar != null) {
            leftGhostWaveBar.setBackSwipePreferred(inAllAppsMode || searchActive);
            leftGhostWaveBar.setVisibility(searchActive ? View.GONE : View.VISIBLE);
        }
    }

    private LinearLayout buildSearchBarCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(theme.glassCard(dpUnit, 22));
        box.setPadding(dp(14), dp(10), dp(12), dp(10));
        box.setVisibility(View.GONE);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        VectorGlyphView searchIc = new VectorGlyphView(this, VectorGlyphView.IC_SEARCH, theme.accent, dpUnit);
        LinearLayout.LayoutParams sicLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        sicLp.rightMargin = dp(10);
        inputRow.addView(searchIc, sicLp);

        searchInput = new EditText(this);
        searchInput.setHint("搜索应用、算式或网页…");
        searchInput.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x88000000);
        searchInput.setTextColor(theme.textPrimary);
        searchInput.setTypeface(typeface);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(15.2f));
        searchInput.setSingleLine(true);
        searchInput.setBackground(null);
        searchInput.setPadding(0, dp(4), 0, dp(4));
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        inputRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout pasteWrap = new FrameLayout(this);
        pasteWrap.addView(new VectorGlyphView(this, VectorGlyphView.IC_CLIPBOARD, theme.textSecondary, dpUnit),
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        pasteWrap.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                if (clip != null && clip.length() > 0) {
                    searchInput.setText(clip.toString().trim());
                    searchInput.setSelection(searchInput.getText().length());
                    return;
                }
            }
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
        });
        pasteWrap.setVisibility(View.GONE);
        searchPasteBtn = pasteWrap;
        inputRow.addView(pasteWrap, new LinearLayout.LayoutParams(dp(34), dp(34)));

        FrameLayout closeWrap = new FrameLayout(this);
        closeWrap.addView(new VectorGlyphView(this, VectorGlyphView.IC_CLOSE, theme.textSecondary, dpUnit),
                new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        closeWrap.setOnClickListener(v -> {
            if (searchInput.getText().length() > 0) {
                searchInput.setText("");
            } else {
                closeInstantSearch();
            }
        });
        inputRow.addView(closeWrap, new LinearLayout.LayoutParams(dp(34), dp(34)));
        box.addView(inputRow);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateSearchPasteVisibility();
                rebuildStreamRows();
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN);
            if (go) {
                return executeBestSearchResult();
            }
            return false;
        });

        return box;
    }

    private void updateSearchPasteVisibility() {
        if (searchPasteBtn == null) return;
        boolean emptyQuery = searchInput == null || searchInput.getText().length() == 0;
        boolean hasTextClip = false;
        if (emptyQuery) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                android.content.ClipDescription desc = cm.getPrimaryClipDescription();
                hasTextClip = desc != null && (desc.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
                        || desc.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML));
            }
        }
        searchPasteBtn.setVisibility(hasTextClip ? View.VISIBLE : View.GONE);
    }

    /** 回车智能执行最底端（距离拇指最近）的首个有效搜索结果：计算复制 / 应用启动 / 快捷方式 / 网页搜索。 */
    private boolean executeBestSearchResult() {
        for (int i = streamRows.size() - 1; i >= 0; i--) {
            StreamRow r = streamRows.get(i);
            if (r.type == StreamRow.TYPE_CALC_RESULT) {
                copyToClipboard(r.text);
                return true;
            }
            if (r.type == StreamRow.TYPE_SHORTCUT_RESULT && r.shortcut != null) {
                startSearchShortcut(r.shortcut, r.app);
                return true;
            }
            if (r.app != null) {
                launchApp(r.app);
                return true;
            }
            if (r.type == StreamRow.TYPE_WEB_SEARCH && r.text != null && !r.text.isEmpty()) {
                openWebSearch(r.text);
                return true;
            }
        }
        String raw = searchInput != null ? searchInput.getText().toString().trim() : "";
        if (!raw.isEmpty()) {
            openWebSearch(raw);
            return true;
        }
        return false;
    }

    private String getCurrentSearchEngineName() {
        int idx = Math.max(0, Math.min(SEARCH_ENGINE_NAMES.length - 1, prefs.getInt(K_SEARCH_ENGINE, 0)));
        return SEARCH_ENGINE_NAMES[idx];
    }

    private void openWebSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        int idx = Math.max(0, Math.min(SEARCH_ENGINE_URLS.length - 1, prefs.getInt(K_SEARCH_ENGINE, 0)));
        String url = SEARCH_ENGINE_URLS[idx] + Uri.encode(query.trim());
        startIntentSafely(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        closeInstantSearch();
    }

    private void startSearchShortcut(ShortcutInfo sc, AppEntry owner) {
        try {
            launcherApps.startShortcut(sc, null, null);
            hideSoftKeyboard();
            uiHandler.postDelayed(() -> { if (searchActive) closeInstantSearch(); }, 350);
        } catch (Throwable t) {
            if (owner != null) launchApp(owner);
        }
    }

    private void openInstantSearch() {
        if (!searchActive) {
            searchActive = true;
            searchBarCard.setVisibility(View.VISIBLE);
            searchBarCard.bringToFront();
            updateSearchPasteVisibility();
            updateFirstPageExtrasVisibility();
            rebuildStreamRows();
            if (streamListView != null && streamListView.getCount() > 0) {
                streamListView.setSelection(streamListView.getCount() - 1);
            }
            animateStreamListTransition();
        }
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeInstantSearch() {
        if (!searchActive) return;
        cooldownWaveBars();
        stopListFlingAndResetTop();
        searchActive = false;
        hideSoftKeyboard();
        searchInput.setText("");
        updateFirstPageExtrasVisibility();
        rebuildStreamRows();
        stopListFlingAndResetTop();
        animateStreamListTransition();
    }

    private void quickLaunchRecentsOverview() {
        boolean ok = GestureLockService.openRecentsOverview();
        if (!ok) {
            Toast.makeText(this, "提示：开启无障碍服务即可一键呼出系统多任务后台", Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) {}
        }
    }

    private void returnToHomeFavorites() {
        if (popupController != null) popupController.dismiss(false);
        if (currentSheetDialog != null && currentSheetDialog.isShowing()) currentSheetDialog.dismiss();
        cooldownWaveBars();
        boolean needRebuild = searchActive || inAllAppsMode;
        if (searchActive) {
            searchActive = false;
            searchInput.setText("");
        }
        hideSoftKeyboard();
        uiHandler.removeCallbacks(clearHighlightTask);
        highlightedGroup = 0;
        inAllAppsMode = false;
        updateFirstPageExtrasVisibility();
        if (needRebuild) {
            rebuildStreamRows();
            animateStreamListTransition();
        }
        stopListFlingAndResetTop();
    }

    private void applyHeroStatusMetrics(LunarStatusEngine.DeviceMetrics dm) {
        if (dm == null) return;
        if (niagaraHeaderView != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("M月d日 EEE", Locale.CHINESE);
            String dateStr = sdf.format(new Date());
            niagaraHeaderView.updateDateLunar(dateStr, dm.lunarLine);
            niagaraHeaderView.updateMetrics(dm.batteryPct, dm.charging, dm.batteryTempC, dm.ramAvailGb);
        }
        refreshWeatherPill();
        refreshStickyNotePill();
    }

    private String getEffectiveWeatherSummary() {
        String locWeather = WeatherSyncEngine.getCachedSummary(this);
        if (locWeather != null && !locWeather.isEmpty()) return locWeather;
        String notifWeather = NotificationHubService.weatherSummary;
        if (notifWeather != null && !notifWeather.isEmpty()) return notifWeather;
        return "";
    }

    private void refreshWeatherPill() {
        if (niagaraHeaderView == null) return;
        niagaraHeaderView.updateWeather(getEffectiveWeatherSummary());
    }

    private void handleWeatherBarClick() {
        String cur = getEffectiveWeatherSummary();
        boolean hasLoc = WeatherSyncEngine.hasLocationPermission(this);
        if (!cur.isEmpty() || hasLoc) {
            if (hasLoc) {
                WeatherSyncEngine.fetchWeatherAsync(this, cur.isEmpty(), s -> refreshWeatherPill());
            }
            if (cur.isEmpty()) {
                Toast.makeText(this, "正在同步实时天气...", Toast.LENGTH_SHORT).show();
            } else {
                openSystemWeatherApp();
            }
            return;
        }
        showWeatherAuthSheet();
    }

    private void showWeatherAuthSheet() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("开启首页实时天气", 18.5f, theme.textPrimary, true));
        TextView sub = makeText("授权定位后即可在首页顶部直接显示实时温度与天气图标，也可授权读取系统通知栏/流体云天气。", 12.5f, theme.textSecondary, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        box.addView(sub);

        TextView locBtn = makeSheetButton("授权定位 · 直接显示实时天气（推荐）", () -> {
            dismiss.run();
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_WEATHER_LOC);
        });
        locBtn.setBackground(theme.solidAccentButton(dpUnit, 15));
        locBtn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.bottomMargin = dp(8);
        box.addView(locBtn, lp1);

        List<TextView> subBtns = new ArrayList<>();
        subBtns.add(makeSheetButton("授权通知读取", () -> {
            dismiss.run();
            startIntentSafely(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }));
        subBtns.add(makeSheetButton("打开系统天气", () -> {
            dismiss.run();
            openSystemWeatherApp();
        }));
        addGridButtons(box, subBtns, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    private void openSystemWeatherApp() {
        PendingIntent pi = NotificationHubService.weatherIntent;
        if (pi != null && NotificationHubService.sendPendingIntentCompat(this, pi, null, null)) {
            return;
        }
        String cachedPkg = NotificationHubService.weatherPackage;
        if (cachedPkg != null && !cachedPkg.isEmpty()) {
            Intent i = getPackageManager().getLaunchIntentForPackage(cachedPkg);
            if (i != null && startIntentSafely(i)) return;
        }
        String[] weatherPkgs = new String[]{
            "com.coloros.weather2",
            "com.coloros.weather",
            "com.heytap.weather",
            "com.oppo.weather",
            "com.oneplus.weather",
            "com.miui.weather2",
            "com.huawei.android.totemweather",
            "com.sec.android.daemonapp",
            "com.moji.mjweather",
            "com.caiyun.weather",
            "com.sina.weather"
        };
        PackageManager pm = getPackageManager();
        for (String pkg : weatherPkgs) {
            try {
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (startIntentSafely(launch)) return;
                }
            } catch (Throwable ignored) {}
        }
        for (AppEntry a : allApps) {
            if (a.label != null && (a.label.contains("天气") || a.label.toLowerCase(Locale.ROOT).contains("weather"))) {
                launchApp(a);
                return;
            }
        }
        Toast.makeText(this, "未检测到系统天气应用，开启通知使用权可自动同步天气信息", Toast.LENGTH_SHORT).show();
    }

    private void refreshHeroStatusWidgets() {
        applyHeroStatusMetrics(LunarStatusEngine.readMetrics(this));
    }

    private void refreshStickyNotePill() {
        if (niagaraHeaderView == null) return;
        String note = prefs.getString(K_STICKY_NOTE, "").trim();
        niagaraHeaderView.updateStickyNote(note);
        applyOneHandedListPadding();
    }

    private void styleGlassEditText(EditText input, String hint) {
        input.setHint(hint);
        input.setTextColor(theme.textPrimary);
        input.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x80000000);
        input.setTypeface(typeface);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(15f));
        input.setBackground(theme.sheetTileCard(dpUnit, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
    }

    private void promptEditStickyNote() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("桌面速记便签", 18f, theme.textPrimary, true));
        TextView sub = makeText("写下一句话备忘、取件码或今日待办（留空即可隐藏首页便签条）", 12.2f, theme.textSecondary, false);
        sub.setPadding(0, dp(3), 0, dp(10));
        box.addView(sub);

        final EditText input = new EditText(this);
        styleGlassEditText(input, "例如：取件码 5-2-3018 / 下午 3 点开会");
        input.setText(prefs.getString(K_STICKY_NOTE, ""));
        input.setSingleLine(false);
        input.setMaxLines(3);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("清空隐藏", () -> {
            prefs.edit().remove(K_STICKY_NOTE).apply();
            refreshStickyNotePill();
            dismiss.run();
        }));
        TextView saveBtn = makeSheetButton("保存便签", () -> {
            prefs.edit().putString(K_STICKY_NOTE, input.getText().toString().trim()).apply();
            refreshStickyNotePill();
            dismiss.run();
        });
        saveBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        btns.add(saveBtn);
        addGridButtons(box, btns, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    private View buildStatusMetricTile(String label, String mainValue, String subValue, int progressPct, int barColor) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(theme.sheetTileCard(dpUnit, 18));
        tile.setPadding(dp(13), dp(12), dp(13), dp(12));

        TextView lbTv = makeText(label, 12f, theme.textSecondary, false);
        lbTv.setSingleLine(true);
        tile.addView(lbTv);

        TextView mainTv = makeText(mainValue, 16.5f, theme.textPrimary, true);
        mainTv.setSingleLine(true);
        mainTv.setEllipsize(TextUtils.TruncateAt.END);
        mainTv.setPadding(0, dp(3), 0, dp(6));
        tile.addView(mainTv);

        LinearLayout trackRow = new LinearLayout(this);
        trackRow.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(theme.isLight ? 0x1A141824 : 0x26FFFFFF);
        trackBg.setCornerRadius(dp(3f));
        trackRow.setBackground(trackBg);

        int clamped = Math.max(4, Math.min(100, progressPct));
        View fill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(barColor);
        fillBg.setCornerRadius(dp(3f));
        fill.setBackground(fillBg);
        trackRow.addView(fill, new LinearLayout.LayoutParams(0, dp(5), clamped));
        if (clamped < 100) {
            trackRow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(5), 100 - clamped));
        }
        tile.addView(trackRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5)));

        TextView subTv = makeText(subValue, 11.2f, theme.textSecondary, false);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        subTv.setPadding(0, dp(6), 0, 0);
        tile.addView(subTv);

        return tile;
    }

    private void showDeviceStatusDialog() {
        JSONObject counts = getLaunchCountsJson();
        int totalLaunches = 0;
        for (Iterator<String> it = counts.keys(); it.hasNext(); ) {
            totalLaunches += counts.optInt(it.next(), 0);
        }
        final LunarStatusEngine.DeviceMetrics dm = LunarStatusEngine.readMetrics(this);
        final int favCount = getStringList(K_FAVS).size();
        final int appCount = allApps.size();

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        LinearLayout hdrRow = new LinearLayout(this);
        hdrRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleTv = makeText("本机实时状态", 18f, theme.textPrimary, true);
        hdrRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(hdrRow);

        String lunarInfoText = (dm.lunarLine == null || dm.lunarLine.isEmpty() || dm.lunarLine.equals(dm.greeting))
                ? dm.greeting : (dm.greeting + " · " + dm.lunarLine);
        TextView lunarTv = makeText(lunarInfoText, 12.8f, theme.textSecondary, false);
        lunarTv.setPadding(0, dp(4), 0, dp(10));
        box.addView(lunarTv);

        // 2x2 可视化仪表盘磁贴
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        View batTile = buildStatusMetricTile(
                "电池与温度",
                dm.batteryPct + "% · " + (dm.charging ? "充电中" : "放电中"),
                String.format(Locale.ROOT, "%.1f°C · %d mV", dm.batteryTempC, dm.batteryVoltMv),
                dm.batteryPct,
                dm.batteryPct >= 25 ? 0xFF10B981 : 0xFFF59E0B);
        View ramTile = buildStatusMetricTile(
                "运行内存 (RAM)",
                "占用 " + dm.ramUsedPct + "%",
                String.format(Locale.ROOT, "可用 %.1fG / 共 %.1fG", dm.ramAvailGb, dm.ramTotalGb),
                dm.ramUsedPct,
                0xFF6366F1);
        LinearLayout.LayoutParams t1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t1Lp.rightMargin = dp(8);
        row1.addView(batTile, t1Lp);
        row1.addView(ramTile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = dp(8);
        View romTile = buildStatusMetricTile(
                "本机存储空间",
                String.format(Locale.ROOT, "剩余 %.1f GB", dm.storageFreeGb),
                String.format(Locale.ROOT, "已用 %d%% / 共 %.1fG", dm.storageUsedPct, dm.storageTotalGb),
                dm.storageUsedPct,
                0xFF8B5CF6);
        int favRatio = appCount > 0 ? Math.min(100, Math.max(12, favCount * 100 / Math.min(20, appCount))) : 35;
        View statTile = buildStatusMetricTile(
                "桌面应用统计",
                "共 " + appCount + " 个应用",
                "常用 " + favCount + " 个 · 累计启动 " + totalLaunches + " 次",
                favRatio,
                theme.accent);
        LinearLayout.LayoutParams t2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t2Lp.rightMargin = dp(8);
        row2.addView(romTile, t2Lp);
        row2.addView(statTile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row2, r2Lp);

        List<TextView> actions = new ArrayList<>();
        actions.add(makeSheetButton("桌面便签", () -> {
            dismiss.run();
            promptEditStickyNote();
        }));
        TextView okBtn = makeSheetButton("完成", dismiss);
        okBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        okBtn.setTextColor(0xFFFFFFFF);
        actions.add(okBtn);
        addGridButtons(box, actions, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    // ---------- 统一 PopupCardController 宿主桥接 ----------
    private PopupCardController createPopupController() {
        return new PopupCardController(new PopupCardController.Host() {
            @Override public Activity getActivity() { return MainActivity.this; }
            @Override public FrameLayout getWorkspaceRoot() { return workspaceRoot; }
            @Override public View[] getBlurTargets() { return new View[]{workspaceMainColumn, backdropView, coinButton}; }
            @Override public AuroraTheme getThemeObj() { return theme; }
            @Override public Typeface getTypeface() { return typeface; }
            @Override public float getDp() { return dpUnit; }
            @Override public float sp(float size) { return MainActivity.this.sp(size); }
            @Override public int getTopInsetPx() { return currentTopInsetPx; }
            @Override public int getNavBottomPx() { return currentNavBottomPx; }
            @Override public Drawable getAppIcon(AppEntry app) { return getShapedIcon(app); }
            @Override public View findRowIconView(View rowView) { return MainActivity.this.findRowIconView(rowView); }
            @Override public boolean isFavorite(AppEntry app) { return app != null && getStringList(K_FAVS).contains(app.idKey); }
            @Override public boolean isInlineNotifEnabled() { return prefs.getBoolean(K_INLINE_NOTIF, true); }
            @Override public boolean isVirtualSettingsApp(AppEntry app) { return app == virtualSettingsApp; }
            @Override public List<ShortcutInfo> queryPopupShortcuts(AppEntry app) { return MainActivity.this.queryPopupShortcuts(app); }
            @Override public Drawable loadShortcutIcon(ShortcutInfo sc) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && launcherApps != null && sc != null) {
                        return launcherApps.getShortcutIconDrawable(sc, getResources().getDisplayMetrics().densityDpi);
                    }
                } catch (Throwable ignored) {}
                return null;
            }
            @Override public void startPopupShortcut(ShortcutInfo sc, AppEntry app) { MainActivity.this.startPopupShortcut(sc, app); }
            @Override public void launchApp(AppEntry app, View sourceView) { MainActivity.this.launchApp(app, sourceView); }
            @Override public void launchAppOrNotification(AppEntry app, View sourceView) {
                MainActivity.this.openAppOrNotificationTarget(app, sourceView, true);
            }
            @Override public void onPopupVisibilityChanged(boolean visible) {
                if (rightWaveBar != null) {
                    rightWaveBar.animate().cancel();
                    rightWaveBar.animate().alpha(visible ? 0f : 1f).setDuration(140).start();
                    rightWaveBar.setEnabled(!visible);
                }
            }
            @Override public void toggleFavorite(AppEntry app) { toggleFavoriteQuickly(app); }
            @Override public void openRenameApp(AppEntry app) { promptRenameApp(app); }
            @Override public void openCustomAppIcon(AppEntry app) { showCustomAppIconDialog(app); }
            @Override public void openAppDetails(AppEntry app) {
                if (app == null || app.component == null) return;
                try {
                    launcherApps.startAppDetailsActivity(app.component, app.user, null, null);
                } catch (Throwable ignored) {}
            }
            @Override public void hideApp(AppEntry app) {
                if (app == null) return;
                List<String> hid = getStringList(K_HIDDEN);
                if (!hid.contains(app.idKey)) hid.add(app.idKey);
                putStringList(K_HIDDEN, hid);
                List<String> favs = getStringList(K_FAVS);
                favs.remove(app.idKey);
                putStringList(K_FAVS, favs);
                rebuildStreamRows();
                Toast.makeText(MainActivity.this, "已隐藏，可在控制中心恢复", Toast.LENGTH_SHORT).show();
            }
            @Override public void uninstallApp(AppEntry app) {
                if (app == null || app.component == null) return;
                Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.getPackageName()));
                if (app.user != null) i.putExtra(Intent.EXTRA_USER, app.user);
                startIntentSafely(i);
            }
            @Override public void openAddAppToFolder(AppEntry app) { promptAddAppToFolder(app); }
            @Override public void openControlCenter() { openControlCenterSheet(); }
            @Override public void hideSoftKeyboard() { MainActivity.this.hideSoftKeyboard(); }
            @Override public List<AppEntry> getAppPopupChildren(AppEntry parent) { return MainActivity.this.getAppPopupChildren(parent); }
            @Override public void openEditAppPopupChildren(AppEntry parent) { promptEditAppPopupChildren(parent); }
            @Override public void removeAppPopupChild(AppEntry parent, AppEntry child) { MainActivity.this.removeAppPopupChild(parent, child); }
            @Override public JSONObject getFolderJson(int folderIdx) { return getFoldersArray().optJSONObject(folderIdx); }
            @Override public AppEntry findAppByKey(String idKey) { return MainActivity.this.findAppByKey(idKey); }
            @Override public void openEditFolderApps(int folderIdx) { promptSelectFolderApps(folderIdx); }
            @Override public void openRenameFolder(int folderIdx) { promptRenameFolder(folderIdx); }
            @Override public void openReorderFolderApps(int folderIdx) { promptReorderFolderApps(folderIdx); }
            @Override public boolean toggleFolderTapLaunchFirst(int folderIdx) { return MainActivity.this.toggleFolderTapLaunchFirst(folderIdx); }
            @Override public void deleteFolder(int folderIdx) {
                JSONArray arr = getFoldersArray();
                if (folderIdx >= 0 && folderIdx < arr.length()) {
                    arr.remove(folderIdx);
                    saveFoldersArray(arr);
                }
            }
        });
    }

    // ---------- 双向应用/文件夹行手势与双击锁屏 ----------
    private float touchStartX, touchStartY;
    private int swipedRowPos = -1;
    private View swipedRowView = null;
    private boolean rowSwiping = false;
    private boolean rightRowSwiping = false;
    private boolean rightSwipeThresholdTicked = false;
    private boolean leftSwipeThresholdTicked = false;
    private int touchSlopPx = 0;
    private boolean touchDownOnAppRow = false;
    private boolean touchDownInHeader = false;

    private boolean hasClearableNotification(AppEntry app) {
        if (app == null || app.component == null) return false;
        return NotificationHubService.hasClearableForApp(app.component.getPackageName(), app.user);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (popupController != null && popupController.isShowing()) {
            return super.dispatchTouchEvent(ev);
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            rightRowSwiping = false;
            rightSwipeThresholdTicked = false;
            leftSwipeThresholdTicked = false;

            rowSwiping = false;
            swipedRowPos = -1;
            swipedRowView = null;
            touchDownOnAppRow = false;
            touchDownInHeader = false;
            touchStartX = ev.getRawX();
            touchStartY = ev.getRawY();

            if (streamListView != null) {
                int[] loc = new int[2];
                streamListView.getLocationOnScreen(loc);
                if (touchStartY < loc[1]) {
                    touchDownInHeader = true;
                }
                int w = getWindow().getDecorView().getWidth();
                int edgeGuard = (inAllAppsMode || searchActive) ? dp(48) : dp(38);
                boolean inWaveEdge = (touchStartX > w - edgeGuard) || (touchStartX < edgeGuard);

                // 按在悬浮圆钮上时豁免列表拦截，确保点击与四向手势 100% 灵敏
                if (coinButton != null && coinButton.getVisibility() == View.VISIBLE) {
                    int[] cLoc = new int[2];
                    coinButton.getLocationOnScreen(cLoc);
                    if (touchStartX >= cLoc[0] - dp(8) && touchStartX <= cLoc[0] + coinButton.getWidth() + dp(8)
                            && touchStartY >= cLoc[1] - dp(8) && touchStartY <= cLoc[1] + coinButton.getHeight() + dp(8)) {
                        return super.dispatchTouchEvent(ev);
                    }
                }
                if (!inWaveEdge && !searchActive) {
                    int rawPos = streamListView.pointToPosition(
                            (int) (touchStartX - loc[0]), (int) (touchStartY - loc[1]));
                    int headerCount = streamListView.getHeaderViewsCount();
                    if (rawPos != AdapterView.INVALID_POSITION && rawPos < headerCount) {
                        touchDownInHeader = true;
                    } else if (rawPos != AdapterView.INVALID_POSITION) {
                        int pos = rawPos - headerCount;
                        if (pos >= 0 && pos < streamRows.size()) {
                            StreamRow row = streamRows.get(pos);
                            if (row.app != null || row.type == StreamRow.TYPE_FOLDER) {
                                View child = streamListView.getChildAt(rawPos - streamListView.getFirstVisiblePosition());
                                swipedRowPos = pos;
                                swipedRowView = child;
                                touchDownOnAppRow = true;

                                // 精确识别应用行右侧大片空白区为桌面空白，支持双击熄屏
                                if (row.app != null && child instanceof ViewGroup) {
                                    ViewGroup vg = (ViewGroup) child;
                                    if (vg.getChildCount() > 0) {
                                        View content = vg.getChildAt(0);
                                        int[] cLoc = new int[2];
                                        content.getLocationOnScreen(cLoc);
                                        if (touchStartX > cLoc[0] + content.getWidth() + dp(12)) {
                                            touchDownOnAppRow = false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (swipedRowPos >= 0) {
            StreamRow swipedRow = swipedRowPos < streamRows.size() ? streamRows.get(swipedRowPos) : null;
            // 1. 右滑统一触发 Pop-up（同时支持应用行与文件夹行）
            if (!(rowSwiping && !rightRowSwiping) && handleItemSwipeRight(swipedRowView, swipedRow, ev)) {
                return true;
            }
            // 2. 安全化左滑：仅当目标应用存在可清除未读通知时才响应，绝不误删常用应用
            AppEntry targetApp = (swipedRow != null) ? swipedRow.app : null;
            boolean canLeftClear = targetApp != null && targetApp != virtualSettingsApp && hasClearableNotification(targetApp);
            float dx = ev.getRawX() - touchStartX;
            float dy = ev.getRawY() - touchStartY;
            if (!rowSwiping && canLeftClear && action == MotionEvent.ACTION_MOVE
                    && -dx > dp(20) && -dx > Math.abs(dy) * 2.0f) {
                rowSwiping = true;
                leftSwipeThresholdTicked = false;
                if (streamListView != null) streamListView.requestDisallowInterceptTouchEvent(true);
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                if (gestureDetector != null) gestureDetector.onTouchEvent(cancel);
                cancel.recycle();
            }
            if (rowSwiping && !rightRowSwiping) {
                if (action == MotionEvent.ACTION_MOVE) {
                    if (swipedRowView != null) {
                        float clamped = Math.max(-dp(90), Math.min(0f, dx)) * 0.56f;
                        swipedRowView.setTranslationX(clamped);
                        if (!leftSwipeThresholdTicked && dx < -dp(56)) {
                            leftSwipeThresholdTicked = true;
                            swipedRowView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } else if (leftSwipeThresholdTicked && dx > -dp(46)) {
                            leftSwipeThresholdTicked = false;
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (swipedRowView != null) {
                        swipedRowView.animate().translationX(0f).setDuration(190).start();
                    }
                    if (action == MotionEvent.ACTION_UP && targetApp != null && dx < -dp(56)) {
                        getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        String pkg = targetApp.component.getPackageName();
                        int cleared = NotificationHubService.cancelAllForApp(pkg, targetApp.user);
                        if (cleared > 0) {
                            Toast.makeText(this, "已清除「" + targetApp.label + "」" + cleared + " 条通知", Toast.LENGTH_SHORT).show();
                        }
                    }
                    rowSwiping = false;
                    leftSwipeThresholdTicked = false;
                    swipedRowPos = -1;
                }
                return true;
            }
        }
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 列表项右滑直达 Pop-up：支持应用行与文件夹行；dx > touchSlop 且 dx > |dy|*2.0 极角隔离。
     */
    private boolean handleItemSwipeRight(View itemView, StreamRow row, MotionEvent event) {
        if (row == null || itemView == null) return false;
        if (row.app == null && row.type != StreamRow.TYPE_FOLDER) return false;
        int action = event.getActionMasked();
        float dx = event.getRawX() - touchStartX;
        float dy = event.getRawY() - touchStartY;

        if (!rightRowSwiping) {
            if (action != MotionEvent.ACTION_MOVE) return false;
            if (touchSlopPx <= 0) touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();
            if (!(dx > touchSlopPx && dx > Math.abs(dy) * 2.0f)) return false;
            rightRowSwiping = true;
            rightSwipeThresholdTicked = false;
            rowSwiping = true;
            if (streamListView != null) streamListView.requestDisallowInterceptTouchEvent(true);
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            if (gestureDetector != null) gestureDetector.onTouchEvent(cancel);
            cancel.recycle();
        }

        if (action == MotionEvent.ACTION_MOVE) {
            itemView.setTranslationX(Math.max(0f, Math.min(dx * 0.42f, dp(48))));
            if (!rightSwipeThresholdTicked && dx > dp(56)) {
                rightSwipeThresholdTicked = true;
                itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } else if (rightSwipeThresholdTicked && dx < dp(46)) {
                rightSwipeThresholdTicked = false;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            itemView.animate().translationX(0f).setDuration(190).start();
            boolean trigger = action == MotionEvent.ACTION_UP && dx > dp(56);
            rightRowSwiping = false;
            rightSwipeThresholdTicked = false;
            rowSwiping = false;
            swipedRowPos = -1;
            if (trigger && popupController != null) {
                getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                if (row.type == StreamRow.TYPE_FOLDER) {
                    popupController.showFolderPopup(itemView, row.folderIdx, false);
                } else {
                    popupController.showAppPopup(itemView, row.app, false);
                }
            }
            return true;
        }
        return true;
    }

    /** 弹层展示用快捷方式：仅启用项，按 rank 排序，至多 4 个。 */
    private List<ShortcutInfo> queryPopupShortcuts(AppEntry app) {
        List<ShortcutInfo> all = queryAppShortcuts(app);
        List<ShortcutInfo> out = new ArrayList<>();
        for (ShortcutInfo s : all) {
            if (s != null && s.isEnabled() && s.getShortLabel() != null) out.add(s);
        }
        try {
            Collections.sort(out, (a, b) -> Integer.compare(a.getRank(), b.getRank()));
        } catch (Throwable ignored) {}
        return out.size() > 4 ? new ArrayList<>(out.subList(0, 4)) : out;
    }

    private void startPopupShortcut(ShortcutInfo sc, AppEntry app) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || launcherApps == null) {
                throw new IllegalStateException("shortcuts unsupported");
            }
            UserHandle uh = sc.getUserHandle() != null ? sc.getUserHandle()
                    : (app.user != null ? app.user : Process.myUserHandle());
            launcherApps.startShortcut(sc.getPackage(), sc.getId(), null, null, uh);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开该快捷方式", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFavoriteQuickly(AppEntry app) {
        List<String> favs = getStringList(K_FAVS);
        if (favs.contains(app.idKey)) {
            favs.remove(app.idKey);
            putStringList(K_FAVS, favs);
            Toast.makeText(this, "已移出常用应用：" + app.label, Toast.LENGTH_SHORT).show();
        } else {
            favs.add(app.idKey);
            putStringList(K_FAVS, favs);
            Toast.makeText(this, "已加入第一页常用应用：" + app.label, Toast.LENGTH_SHORT).show();
        }
        rebuildStreamRows();
    }

    private final GestureDetector.SimpleOnGestureListener screenGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
            if (searchActive || !prefs.getBoolean(K_DOUBLE_TAP_LOCK, true)) return false;
            if (touchDownOnAppRow) return false;
            int w = getWindow().getDecorView().getWidth();
            if (e.getRawX() > w - dp(38) || e.getRawX() < dp(32)) return false;
            long now = SystemClock.uptimeMillis();
            if (now - lastDoubleTapLock < 2000) return true;
            lastDoubleTapLock = now;
            uiHandler.postDelayed(MainActivity.this::triggerScreenLock, 50);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null) return false;
            int w = getWindow().getDecorView().getWidth();
            if (isWaveScrubbing || e1.getRawX() > w - dp(50) || e1.getRawX() < dp(42)) return false;

            float dy = e2.getY() - e1.getY();
            float dx = e2.getX() - e1.getX();

            if ((inAllAppsMode || searchActive) && !rowSwiping
                    && Math.abs(dx) > dp(64) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                returnToHomeFavorites();
                return true;
            }

            if (Math.abs(dy) < dp(52) || Math.abs(dy) < Math.abs(dx) * 1.35f) return false;

            if (dy > 0 && (isListAtTop() || touchDownInHeader)) {
                if (searchActive || inAllAppsMode) {
                    returnToHomeFavorites();
                    return true;
                }
                if (prefs.getBoolean(K_PULL_DOWN_SEARCH, true)) {
                    openInstantSearch();
                    return true;
                }
                expandSystemNotificationPanel();
                return true;
            }
            // 首页一体流：列表已到底（或单屏未满）时向上轻扫无缝进入全部应用，内容溢出时优先顺滑滚动首页流
            if (dy < 0 && !inAllAppsMode && !searchActive && isListAtBottom()) {
                switchStreamMode(true);
                return true;
            }
            return false;
        }
    };

    private boolean isListAtTop() {
        if (streamListView == null || streamListView.getChildCount() == 0) return true;
        return streamListView.getFirstVisiblePosition() == 0
                && streamListView.getChildAt(0).getTop() >= streamListView.getPaddingTop() - dp(2);
    }

    private boolean isListAtBottom() {
        if (streamListView == null || streamListView.getChildCount() == 0) return true;
        int lastVis = streamListView.getLastVisiblePosition();
        int total = streamListView.getCount();
        if (lastVis < total - 1) return false;
        View lastChild = streamListView.getChildAt(streamListView.getChildCount() - 1);
        return lastChild == null
                || lastChild.getBottom() <= streamListView.getHeight() - streamListView.getPaddingBottom() + dp(4);
    }

    private void triggerScreenLock() {
        if (GestureLockService.lockScreenNow()) return;
        promptAccessibilitySetup("双击锁屏");
    }

    private void expandSystemNotificationPanel() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable t) {
            if (!GestureLockService.expandNotificationShade()) {
                promptAccessibilitySetup("单手下滑通知栏");
            }
        }
    }

    private void promptAccessibilitySetup(String featureName) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText(featureName + " · 辅助手势授权", 17.5f, theme.textPrimary, true));
        TextView msg = makeText(
                "请在「无障碍 → 已下载的应用」中开启「流光桌面 · 手势双击锁屏与通知栏」。\n\n"
                + "• 该服务 100% 本地运行，仅执行锁屏与拉开通知栏动作，绝不读取屏幕内容。\n"
                + "• 如遇系统提示“受限制的设置”，请先点下方「解除受限设置」→ 右上角 ⋮ → 允许受限制的设置。",
                13f, theme.textSecondary, false);
        msg.setPadding(0, dp(8), 0, dp(12));
        box.addView(msg);

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("解除受限设置", () -> {
            dismiss.run();
            startIntentSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "请点右上角 ⋮ → 允许受限制的设置", Toast.LENGTH_LONG).show();
        }));
        TextView goBtn = makeSheetButton("去开启无障碍", () -> {
            dismiss.run();
            String cn = new ComponentName(this, GestureLockService.class).flattenToString();
            Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, cn);
            if (!startIntentSafely(i)) startIntentSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        goBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        goBtn.setTextColor(0xFFFFFFFF);
        btns.add(goBtn);
        addGridButtons(box, btns, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    // ---------- 应用读取与构建 ----------
    private long getUserSerial(UserHandle u) {
        Long s = serialCache.get(u);
        if (s == null) {
            s = userManager.getSerialNumberForUser(u);
            serialCache.put(u, s);
            userCache.put(s, u);
        }
        return s;
    }

    private UserHandle getUserBySerial(long serial) {
        UserHandle u = userCache.get(serial);
        if (u == null) {
            u = userManager.getUserForSerialNumber(serial);
            if (u != null) {
                userCache.put(serial, u);
                serialCache.put(u, serial);
            }
        }
        return u;
    }

    private AppEntry buildAppEntry(ComponentName comp, UserHandle user, String rawLabel) {
        AppEntry a = new AppEntry();
        a.component = comp;
        a.user = user;
        a.userSerial = getUserSerial(user);
        a.isClone = !user.equals(Process.myUserHandle());
        a.idKey = comp.flattenToShortString() + "@" + a.userSerial;
        a.rawLabel = rawLabel;

        String customAlias = getAliasesJson().optString(a.idKey, "");
        if (!customAlias.isEmpty()) {
            a.label = customAlias;
        } else {
            a.label = a.isClone ? (rawLabel + " ²") : rawLabel;
        }
        String[] keys = PinyinEngine.buildSearchKeys(a.label);
        a.fullPy = keys[0];
        a.iniPy = keys[1];
        a.t9Digits = keys[2];
        a.groupChar = PinyinEngine.groupLetter(a.label);
        return a;
    }

    private AppEntry buildShortcutAppEntry(ShortcutInfo sc, UserHandle user) {
        if (sc == null) return null;
        CharSequence raw = sc.getShortLabel();
        if (raw == null || raw.length() == 0) raw = sc.getLongLabel();
        if (raw == null || raw.length() == 0) raw = sc.getId();
        String name = raw.toString().trim();
        AppEntry a = new AppEntry();
        a.component = sc.getActivity() != null ? sc.getActivity() : new ComponentName(sc.getPackage(), sc.getId());
        a.user = user;
        a.userSerial = getUserSerial(user);
        a.isClone = false;
        a.isShortcut = true;
        a.shortcutInfo = sc;
        a.idKey = "shortcut:" + sc.getPackage() + ":" + sc.getId() + "@" + a.userSerial;
        a.rawLabel = name;
        a.label = name;
        String[] keys = PinyinEngine.buildSearchKeys(a.label);
        a.fullPy = keys[0];
        a.iniPy = keys[1];
        a.t9Digits = keys[2];
        a.groupChar = PinyinEngine.groupLetter(a.label);
        try {
            int dpi = getResources().getDisplayMetrics().densityDpi;
            Drawable d = null;
            try {
                // 🚀 优先获取未打角标的原生高质图标，去除浏览器的小狮子/浏览器角标
                d = launcherApps.getShortcutIconDrawable(sc, dpi);
            } catch (Throwable ignored) {}
            if (d == null) {
                try {
                    d = launcherApps.getShortcutBadgedIconDrawable(sc, dpi);
                } catch (Throwable ignored) {}
            }
            if (name.contains("反重力")) {
                d = createAntiGravityDrawable();
            }
            a.rawIcon = d;
        } catch (Throwable ignored) {}
        getShapedIcon(a);
        return a;
    }

    private Drawable createAntiGravityDrawable() {
        int size = dp(64);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // 1. 底色：深灰蓝高雅圆角矩形背景 (Squircle)
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF1E202E);
        RectF bgRect = new RectF(0, 0, size, size);
        float radius = size * 0.24f;
        canvas.drawRoundRect(bgRect, radius, radius, bgPaint);

        // 2. 绘制反重力彩虹拱门 (Antigravity Rainbow Arch)
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(size * 0.14f);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        int[] colors = new int[] {
                0xFF3B82F6, 0xFF06B6D4, 0xFFEAB308, 0xFFF97316, 0xFFEC4899, 0xFF8B5CF6
        };
        float[] positions = new float[] { 0f, 0.2f, 0.45f, 0.65f, 0.85f, 1f };
        android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                size * 0.24f, size * 0.82f, size * 0.76f, size * 0.82f,
                colors, positions, android.graphics.Shader.TileMode.CLAMP
        );
        strokePaint.setShader(gradient);

        Path archPath = new Path();
        float left = size * 0.25f;
        float top = size * 0.22f;
        float right = size * 0.75f;
        float bottom = size * 0.78f;

        archPath.moveTo(left, bottom);
        archPath.lineTo(left, top + (right - left) / 2f);
        RectF arcRect = new RectF(left, top, right, top + (right - left));
        archPath.arcTo(arcRect, 180, 180, false);
        archPath.lineTo(right, bottom);

        canvas.drawPath(archPath, strokePaint);
        return new BitmapDrawable(getResources(), bmp);
    }

    private AppEntry buildWebShortcutAppEntry(String name, String uri, String pkg) {
        if (name == null || name.isEmpty()) name = "反重力";
        AppEntry a = new AppEntry();
        a.user = Process.myUserHandle();
        a.userSerial = getUserSerial(a.user);
        a.isClone = false;
        a.isShortcut = true;
        Intent targetIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (pkg != null && !pkg.isEmpty()) targetIntent.setPackage(pkg);
        // 🚀 独立任务与独立窗口参数配置
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);

        targetIntent.putExtra("org.chromium.chrome.browser.webapp_url", uri);
        targetIntent.putExtra("org.chromium.chrome.browser.webapp_id", uri);
        targetIntent.putExtra("org.chromium.chrome.browser.webapp_title", name);
        targetIntent.putExtra("org.chromium.chrome.browser.webapp_display_mode", 1);
        targetIntent.putExtra("org.chromium.chrome.browser.theme_color", 0xFF18181B);
        Bundle cctBundle = new Bundle();
        cctBundle.putBinder("android.support.customtabs.extra.SESSION", null);
        cctBundle.putBinder("androidx.browser.customtabs.extra.SESSION", null);
        targetIntent.putExtras(cctBundle);
        targetIntent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF18181B);
        targetIntent.putExtra("androidx.browser.customtabs.extra.TOOLBAR_COLOR", 0xFF18181B);
        targetIntent.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1);
        targetIntent.putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", true);

        a.shortcutIntent = targetIntent;
        a.component = new ComponentName(pkg != null ? pkg : "com.brave.browser", "Shortcut." + name);
        a.idKey = "webshortcut:" + name + "@" + a.userSerial;
        a.rawLabel = name;
        a.label = name;
        String[] keys = PinyinEngine.buildSearchKeys(a.label);
        a.fullPy = keys[0];
        a.iniPy = keys[1];
        a.t9Digits = keys[2];
        a.groupChar = PinyinEngine.groupLetter(a.label);
        try {
            if (name.contains("反重力")) {
                a.rawIcon = createAntiGravityDrawable();
            } else {
                PackageManager pm = getPackageManager();
                if (pkg != null) a.rawIcon = pm.getApplicationIcon(pkg);
                else a.rawIcon = getApplicationInfo().loadIcon(pm);
            }
        } catch (Throwable ignored) {}
        getShapedIcon(a);
        return a;
    }

    private void sortAppList(List<AppEntry> list) {
        final JSONObject usage = getLaunchCountsJson();
        Collections.sort(list, (x, y) -> {
            int gx = WAVE_SYMBOLS.indexOf(x.groupChar);
            int gy = WAVE_SYMBOLS.indexOf(y.groupChar);
            if (gx != gy) return gx - gy;
            int ux = usage.optInt(x.idKey, 0);
            int uy = usage.optInt(y.idKey, 0);
            if (ux != uy) return uy - ux;
            int c = x.fullPy.compareTo(y.fullPy);
            return c != 0 ? c : x.label.compareTo(y.label);
        });
    }

    private void loadInstalledAppsAsync() {
        if (isLoadingApps) {
            pendingReloadApps = true;
            return;
        }
        isLoadingApps = true;
        final Map<String, AppEntry> existingMap = new HashMap<>();
        for (AppEntry a : allApps) existingMap.put(a.idKey, a);
        final int shapeMode = prefs.getInt(K_ICON_SHAPE, 0);

        AppExecutors.getInstance().computation().execute(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            final List<AppEntry> loaded = new ArrayList<>();
            try {
                for (UserHandle u : launcherApps.getProfiles()) {
                    for (LauncherActivityInfo info : launcherApps.getActivityList(null, u)) {
                        String pkg = info.getComponentName().getPackageName();
                        if (getPackageName().equals(pkg)) continue;
                        CharSequence lb = info.getLabel();
                        AppEntry a = buildAppEntry(
                                info.getComponentName(), u, lb == null ? pkg : lb.toString().trim());
                        AppEntry old = existingMap.get(a.idKey);
                        if (old != null && old.shapedIcon != null && old.shapedMode == shapeMode) {
                            a.rawIcon = old.rawIcon;
                            a.shapedIcon = old.shapedIcon;
                            a.shapedMode = old.shapedMode;
                        } else {
                            try { a.rawIcon = info.getBadgedIcon(0); } catch (Throwable ignored) {}
                            getShapedIcon(a);
                        }
                        loaded.add(a);
                    }
                }
            } catch (Throwable ignored) {}

            // 🚀 1. 读取系统已固化到桌面的 Pinned Shortcuts (仅限用户添加到桌面的快捷方式与 PWA，严禁引入动态动作快捷方式)
            boolean hasAntiGravity = false;
            try {
                if (launcherApps.hasShortcutHostPermission()) {
                    for (UserHandle u : launcherApps.getProfiles()) {
                        LauncherApps.ShortcutQuery sq = new LauncherApps.ShortcutQuery();
                        sq.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                        List<ShortcutInfo> pinned = launcherApps.getShortcuts(sq, u);
                        if (pinned != null) {
                            for (ShortcutInfo sc : pinned) {
                                if (sc == null || !sc.isPinned()) continue;
                                AppEntry sa = buildShortcutAppEntry(sc, u);
                                if (sa != null) {
                                    loaded.add(sa);
                                    if (sa.label != null && sa.label.contains("反重力")) hasAntiGravity = true;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 🚀 1.1 扫描并加载通过 Chrome/Chromium 等浏览器安装的 WebAPK 独立 PWA 应用
            try {
                PackageManager pm = getPackageManager();
                List<PackageInfo> installedPackages = pm.getInstalledPackages(0);
                for (PackageInfo pi : installedPackages) {
                    if (pi.packageName != null && pi.packageName.startsWith("org.chromium.webapk.")) {
                        boolean alreadyLoaded = false;
                        for (AppEntry ex : loaded) {
                            if (ex.component != null && pi.packageName.equals(ex.component.getPackageName())) {
                                alreadyLoaded = true;
                                break;
                            }
                        }
                        if (!alreadyLoaded) {
                            Intent launchIntent = pm.getLaunchIntentForPackage(pi.packageName);
                            if (launchIntent != null) {
                                ComponentName cn = launchIntent.getComponent();
                                if (cn == null) cn = new ComponentName(pi.packageName, "Main");
                                CharSequence lb = pi.applicationInfo.loadLabel(pm);
                                AppEntry wa = buildAppEntry(cn, Process.myUserHandle(), lb != null ? lb.toString().trim() : pi.packageName);
                                try { wa.rawIcon = pi.applicationInfo.loadIcon(pm); } catch (Throwable ignored) {}
                                getShapedIcon(wa);
                                loaded.add(wa);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 🚀 2. 用户特别指定需求：在 Brave 浏览器创建的「反重力」快捷方式，确保 100% 出现在所有应用列表内
            if (!hasAntiGravity) {
                String bravePkg = null;
                for (AppEntry a : loaded) {
                    if (a.component != null && a.component.getPackageName().contains("brave")) {
                        bravePkg = a.component.getPackageName();
                        break;
                    }
                }
                AppEntry ag = buildWebShortcutAppEntry("反重力", "https://antigravity.google.com", bravePkg != null ? bravePkg : "com.brave.browser");
                loaded.add(ag);
            }

            // 🚀 3. 注入 ColorOS 小布助手 AI，确保小布出现在所有应用列表与字母检索中
            injectXiaoBuIfAvailable(loaded);

            sortAppList(loaded);

            // 🚀 4. 后台预索引应用内快捷方式（如微信扫一扫、支付宝付款码），支持极速搜索直达
            final List<ShortcutSearchEntry> shortcutEntries = new ArrayList<>();
            try {
                if (launcherApps.hasShortcutHostPermission()) {
                    for (AppEntry a : loaded) {
                        if (a.isShortcut || a.component == null || a.user == null) continue;
                        List<ShortcutInfo> scs = queryAppShortcuts(a);
                        int cap = 0;
                        for (ShortcutInfo sc : scs) {
                            if (sc == null || !sc.isEnabled()) continue;
                            CharSequence sl = sc.getShortLabel();
                            if (sl == null || sl.length() == 0) sl = sc.getLongLabel();
                            if (sl == null || sl.length() == 0) continue;
                            shortcutEntries.add(new ShortcutSearchEntry(sc, a, sl.toString().trim()));
                            if (++cap >= 4) break;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            uiHandler.post(() -> {
                isLoadingApps = false;
                allApps.clear();
                allApps.addAll(loaded);
                cachedShortcuts.clear();
                cachedShortcuts.addAll(shortcutEntries);
                availableLetters.clear();
                letterTopPreview.clear();
                letterTopApp.clear();
                autoPopulateInitialFavoritesIfEmpty();
                rebuildStreamRows();
                writeDiskAppCache();
                if (pendingReloadApps) {
                    pendingReloadApps = false;
                    loadInstalledAppsAsync();
                }
            });
        });
    }

    private void autoPopulateInitialFavoritesIfEmpty() {
        if (prefs.getBoolean("auto_fav_seeded", false)) return;
        List<String> currentFavs = getStringList(K_FAVS);
        if (!currentFavs.isEmpty() || allApps.isEmpty()) {
            prefs.edit().putBoolean("auto_fav_seeded", true).apply();
            return;
        }
        String[] preferredKeywords = {
            "mm", "wechat", "微信", "alipay", "支付宝", "chrome", "browser", "浏览器",
            "camera", "相机", "gallery", "photos", "相册", "dialer", "contacts", "电话",
            "mms", "messaging", "短信", "files", "文件", "settings", "设置"
        };
        List<String> seeded = new ArrayList<>();
        for (String kw : preferredKeywords) {
            for (AppEntry a : allApps) {
                if (seeded.contains(a.idKey)) continue;
                String pkg = a.component != null ? a.component.getPackageName().toLowerCase(Locale.ROOT) : "";
                String lb = a.label != null ? a.label.toLowerCase(Locale.ROOT) : "";
                if (pkg.contains(kw) || lb.contains(kw)) {
                    seeded.add(a.idKey);
                    break;
                }
            }
            if (seeded.size() >= 7) break;
        }
        for (AppEntry a : allApps) {
            if (seeded.size() >= 6) break;
            if (!seeded.contains(a.idKey)) seeded.add(a.idKey);
        }
        if (!seeded.isEmpty()) {
            putStringList(K_FAVS, seeded);
        }
        prefs.edit().putBoolean("auto_fav_seeded", true).apply();
    }

    private void syncWaveBarSymbolsAndData(Set<String> hiddenSet) {
        availableLetters.clear();
        letterTopPreview.clear();
        letterTopApp.clear();
        for (AppEntry a : allApps) {
            if (hiddenSet.contains(a.idKey)) continue;
            char g = a.groupChar;
            availableLetters.add(g);
            if (!letterTopPreview.containsKey(g)) {
                letterTopPreview.put(g, a.label);
                letterTopApp.put(g, a);
            }
        }
        availableLetters.add('★');

        String syms;
        if (prefs.getBoolean(K_WAVE_ADAPTIVE_ALPHA, true)) {
            StringBuilder sb = new StringBuilder();
            sb.append('★');
            for (int i = 1; i < WAVE_SYMBOLS.length(); i++) {
                char c = WAVE_SYMBOLS.charAt(i);
                if (availableLetters.contains(c)) sb.append(c);
            }
            syms = sb.length() > 1 ? sb.toString() : WAVE_SYMBOLS;
        } else {
            syms = WAVE_SYMBOLS;
        }

        Set<Character> availCopy = new HashSet<>(availableLetters);
        Map<Character, String> previewCopy = new HashMap<>(letterTopPreview);
        if (rightWaveBar != null) {
            rightWaveBar.setSymbols(syms);
            rightWaveBar.setAvailableData(availCopy, previewCopy);
        }
        if (leftGhostWaveBar != null) {
            leftGhostWaveBar.setSymbols(syms);
            leftGhostWaveBar.setAvailableData(availCopy, previewCopy);
        }
    }

    private List<AppEntry> computeSurfacedUnreadApps(Set<String> hiddenSet, Map<String, AppEntry> byKey,
                                                     Set<String> homeKeys, Set<String> homePkgs) {
        if (!prefs.getBoolean(K_SURFACE_UNREAD_HOME, true)) return Collections.emptyList();
        List<String> notifPkgs = NotificationHubService.getNotifiedPackagesOrderedByTime();
        if (notifPkgs.isEmpty()) return Collections.emptyList();
        List<AppEntry> out = new ArrayList<>();
        Set<String> addedPkgs = new HashSet<>();
        for (String pkg : notifPkgs) {
            if (pkg == null || homePkgs.contains(pkg) || !addedPkgs.add(pkg)) continue;
            AppEntry match = null;
            for (AppEntry a : allApps) {
                if (a.isShortcut || a.component == null) continue;
                if (hiddenSet.contains(a.idKey) || homeKeys.contains(a.idKey)) continue;
                if (pkg.equals(a.component.getPackageName())) {
                    match = a;
                    break;
                }
            }
            if (match != null) {
                out.add(match);
                if (out.size() >= 4) break;
            }
        }
        return out;
    }

    private List<String> computeSurfacedUnreadKeys() {
        Set<String> hiddenSet = new HashSet<>(getStringList(K_HIDDEN));
        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry a : allApps) byKey.put(a.idKey, a);
        Set<String> homeKeys = new HashSet<>();
        Set<String> homePkgs = new HashSet<>();
        for (String fk : getStringList(K_FAVS)) {
            if (hiddenSet.contains(fk)) continue;
            AppEntry a = byKey.get(fk);
            if (a != null) {
                homeKeys.add(a.idKey);
                if (a.component != null) homePkgs.add(a.component.getPackageName());
            }
        }
        JSONArray folders = getFoldersArray();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject fo = folders.optJSONObject(i);
            JSONArray apps = fo != null ? fo.optJSONArray("apps") : null;
            if (apps == null) continue;
            for (int j = 0; j < apps.length(); j++) {
                String k = apps.optString(j);
                AppEntry ma = byKey.get(k);
                if (ma != null) {
                    homeKeys.add(ma.idKey);
                    if (ma.component != null) homePkgs.add(ma.component.getPackageName());
                }
            }
        }
        List<AppEntry> apps = computeSurfacedUnreadApps(hiddenSet, byKey, homeKeys, homePkgs);
        if (apps.isEmpty()) return Collections.emptyList();
        List<String> keys = new ArrayList<>(apps.size());
        for (AppEntry a : apps) keys.add(a.idKey);
        return keys;
    }

    private void rebuildStreamRows() {
        streamRows.clear();
        letterRowIndex.clear();

        String rawQuery = searchInput == null ? "" : searchInput.getText().toString().trim();
        String query = rawQuery.toLowerCase(Locale.ROOT);
        Set<String> hiddenSet = new HashSet<>(getStringList(K_HIDDEN));
        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry a : allApps) byKey.put(a.idKey, a);

        if (searchActive) {
            if (!query.isEmpty()) {
                // 贴底单手搜索排序：最佳匹配与计算结果置于列表底端，紧邻底部搜索栏与大拇指
                String calcOut = QuickCalcEngine.evaluateOrConvert(query);
                if (!rawQuery.isEmpty() && calcOut == null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_WEB_SEARCH, rawQuery, null));
                }
                String cleanQ = query.replace(" ", "");
                if (cleanQ.length() >= 2 && !cachedShortcuts.isEmpty()) {
                    int scCount = 0;
                    for (ShortcutSearchEntry sc : cachedShortcuts) {
                        if (hiddenSet.contains(sc.owner.idKey)) continue;
                        String sl = sc.label.toLowerCase(Locale.ROOT);
                        if (sl.contains(cleanQ) || sc.iniPy.startsWith(cleanQ) || sc.fullPy.contains(cleanQ)) {
                            streamRows.add(new StreamRow(StreamRow.TYPE_SHORTCUT_RESULT, sc.label, sc.owner, sc.shortcut));
                            if (++scCount >= 5) break;
                        }
                    }
                }
                final Map<AppEntry, Integer> rankMap = new HashMap<>();
                List<AppEntry> matches = new ArrayList<>();
                for (AppEntry a : allApps) {
                    if (hiddenSet.contains(a.idKey)) continue;
                    int rank = matchRank(a, cleanQ);
                    if (rank >= 0) {
                        rankMap.put(a, rank);
                        matches.add(a);
                    }
                }
                Collections.sort(matches, (x, y) -> rankMap.get(x) - rankMap.get(y));
                if (virtualSettingsApp != null && matchRank(virtualSettingsApp, cleanQ) >= 0) {
                    matches.add(virtualSettingsApp);
                }
                for (int i = matches.size() - 1; i >= 0; i--) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, null, matches.get(i)));
                }
                if (calcOut != null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_CALC_RESULT, calcOut, null));
                }
            } else {
                // 清爽空搜索态：仅呈最近使用/常用推荐（Top 6，自底向上紧贴拇指），绝不倾倒全部应用
                Set<String> shown = new HashSet<>();
                List<AppEntry> picks = new ArrayList<>();
                boolean hasRecents = false;
                for (String rk : getStringList(K_RECENT_KEYS)) {
                    AppEntry a = byKey.get(rk);
                    if (a != null && !hiddenSet.contains(rk) && shown.add(rk)) {
                        picks.add(a);
                        hasRecents = true;
                        if (picks.size() >= 6) break;
                    }
                }
                if (picks.size() < 6) {
                    for (String pkg : GestureLockService.getActivePackages()) {
                        AppEntry a = findAppByPackage(pkg);
                        if (a != null && !hiddenSet.contains(a.idKey) && shown.add(a.idKey)) {
                            picks.add(a);
                            hasRecents = true;
                            if (picks.size() >= 6) break;
                        }
                    }
                }
                if (picks.size() < 6) {
                    for (String fk : getStringList(K_FAVS)) {
                        AppEntry a = byKey.get(fk);
                        if (a != null && !hiddenSet.contains(fk) && shown.add(fk)) {
                            picks.add(a);
                            if (picks.size() >= 6) break;
                        }
                    }
                }
                if (!picks.isEmpty()) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_SECTION, hasRecents ? "最近使用" : "常用推荐", null));
                    for (int i = picks.size() - 1; i >= 0; i--) {
                        streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, null, picks.get(i)));
                    }
                }
            }
            rightWaveBar.setVisibility(View.GONE);
            leftGhostWaveBar.setVisibility(View.GONE);
            streamAdapter.notifyDataSetChanged();
            if (streamListView != null && streamListView.getCount() > 0) {
                streamListView.setSelection(streamListView.getCount() - 1);
            }
            return;
        }

        rightWaveBar.setVisibility(View.VISIBLE);
        rightWaveBar.setBackSwipePreferred(inAllAppsMode || searchActive);
        leftGhostWaveBar.setBackSwipePreferred(inAllAppsMode || searchActive);
        leftGhostWaveBar.setVisibility(searchActive ? View.GONE : View.VISIBLE);

        syncWaveBarSymbolsAndData(hiddenSet);

        if (!inAllAppsMode && !searchActive) {
            // 首页主态：常用应用流 + 原生文件夹行 + 动态未读通知浮现区
            List<String> favs = getStringList(K_FAVS);
            if (favs.isEmpty()) {
                autoPopulateInitialFavoritesIfEmpty();
                favs = getStringList(K_FAVS);
            }
            Set<String> homeKeys = new HashSet<>();
            Set<String> homePkgs = new HashSet<>();
            for (String fk : favs) {
                if (hiddenSet.contains(fk)) continue;
                AppEntry a = byKey.get(fk);
                if (a != null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_FAV_APP, null, a));
                    homeKeys.add(a.idKey);
                    if (a.component != null) homePkgs.add(a.component.getPackageName());
                }
            }
            JSONArray folders = getFoldersArray();
            for (int i = 0; i < folders.length(); i++) {
                JSONObject fo = folders.optJSONObject(i);
                if (fo != null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_FOLDER, fo.optString("name", "文件夹"), null, i));
                    JSONArray fApps = fo.optJSONArray("apps");
                    if (fApps != null) {
                        for (int j = 0; j < fApps.length(); j++) {
                            AppEntry ma = byKey.get(fApps.optString(j));
                            if (ma != null) {
                                homeKeys.add(ma.idKey);
                                if (ma.component != null) homePkgs.add(ma.component.getPackageName());
                            }
                        }
                    }
                }
            }

            List<AppEntry> surfaced = computeSurfacedUnreadApps(hiddenSet, byKey, homeKeys, homePkgs);
            if (!surfaced.isEmpty()) {
                List<String> sKeys = new ArrayList<>(surfaced.size());
                streamRows.add(new StreamRow(StreamRow.TYPE_SURFACED_DIVIDER, null, null));
                for (AppEntry sa : surfaced) {
                    sKeys.add(sa.idKey);
                    streamRows.add(new StreamRow(StreamRow.TYPE_FAV_APP, null, sa));
                }
                lastSurfacedKeys = sKeys;
            } else {
                lastSurfacedKeys = Collections.emptyList();
            }

            streamAdapter.notifyDataSetChanged();
            applyOneHandedListPadding();
            return;
        }

        // 全部应用流：保留同首字母内按使用频次竖列降序排列
        final JSONObject launchCounts = getLaunchCountsJson();
        Map<Character, List<AppEntry>> grouped = new LinkedHashMap<>();
        for (AppEntry a : allApps) {
            if (hiddenSet.contains(a.idKey)) continue;
            char g = a.groupChar;
            List<AppEntry> list = grouped.get(g);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(g, list);
            }
            list.add(a);
        }

        for (Map.Entry<Character, List<AppEntry>> entry : grouped.entrySet()) {
            char g = entry.getKey();
            List<AppEntry> list = entry.getValue();

            letterRowIndex.put(g, streamRows.size());

            List<AppEntry> sortedList = new ArrayList<>(list);
            Collections.sort(sortedList, (x, y) -> {
                int ux = launchCounts.optInt(x.idKey, 0);
                int uy = launchCounts.optInt(y.idKey, 0);
                if (ux != uy) return uy - ux;
                int c = x.fullPy.compareTo(y.fullPy);
                return c != 0 ? c : x.label.compareTo(y.label);
            });
            for (int i = 0; i < sortedList.size(); i++) {
                streamRows.add(new StreamRow(
                        StreamRow.TYPE_ALL_APP,
                        i == 0 ? String.valueOf(g) : "",
                        sortedList.get(i)));
            }
        }
        if (virtualSettingsApp != null) {
            streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, "·", virtualSettingsApp));
        }

        streamAdapter.notifyDataSetChanged();
        applyOneHandedListPadding();
    }

    private int matchRank(AppEntry a, String q) {
        String lb = a.label != null ? a.label.toLowerCase(Locale.ROOT) : "";
        if (lb.startsWith(q)) return 0;
        if (a.iniPy.startsWith(q)) return 1;
        if (a.t9Digits.startsWith(q)) return 2;
        if (a.fullPy.startsWith(q)) return 3;
        if (lb.contains(q)) return 4;
        if (a.iniPy.contains(q)) return 5;
        if (a.fullPy.contains(q)) return 6;
        if (a.component != null && a.component.getPackageName().toLowerCase(Locale.ROOT).contains(q)) return 7;
        return -1;
    }

    /** 搜索命中词高亮：支持直接子串匹配与拼音首字母位置映射高亮（theme.accent + 粗体）。 */
    private CharSequence buildHighlightedLabel(String label, String cleanQuery) {
        if (label == null || cleanQuery == null || cleanQuery.isEmpty()) return label;
        String lower = label.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(cleanQuery);
        if (idx >= 0) {
            SpannableString ss = new SpannableString(label);
            int end = Math.min(label.length(), idx + cleanQuery.length());
            ss.setSpan(new ForegroundColorSpan(theme.accent), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new StyleSpan(Typeface.BOLD), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ss;
        }
        // 拼音首字母精确映射高亮（例如输入 wx 高亮「微信」）
        StringBuilder iniBuf = new StringBuilder();
        List<Integer> charPos = new ArrayList<>();
        boolean wordStart = true;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            String py = PinyinEngine.charToPinyin(ch);
            if (py != null && !py.isEmpty()) {
                iniBuf.append(py.charAt(0));
                charPos.add(i);
                wordStart = true;
            } else if (Character.isLetterOrDigit(ch)) {
                if (wordStart || Character.isUpperCase(ch)) {
                    iniBuf.append(Character.toLowerCase(ch));
                    charPos.add(i);
                }
                wordStart = false;
            } else {
                wordStart = true;
            }
        }
        int iniIdx = iniBuf.toString().indexOf(cleanQuery);
        if (iniIdx >= 0 && iniIdx + cleanQuery.length() <= charPos.size()) {
            SpannableString ss = new SpannableString(label);
            for (int k = 0; k < cleanQuery.length(); k++) {
                int p = charPos.get(iniIdx + k);
                ss.setSpan(new ForegroundColorSpan(theme.accent), p, p + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new StyleSpan(Typeface.BOLD), p, p + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return ss;
        }
        return label;
    }

    // ---------- 第一页常用应用流与全部应用流渲染适配器 ----------
    private float getRowSpacingMultiplier() {
        int d = prefs.getInt(K_ROW_DENSITY, 1);
        return d == 0 ? 0.72f : d == 2 ? 1.35f : 1.0f;
    }

    final class StreamAdapter extends BaseAdapter {
        @Override public int getCount() { return streamRows.size(); }
        @Override public Object getItem(int position) { return streamRows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return StreamRow.VIEW_TYPE_COUNT; }
        @Override public int getItemViewType(int position) { return streamRows.get(position).type; }
        @Override public boolean isEnabled(int position) {
            int t = streamRows.get(position).type;
            return t != StreamRow.TYPE_SECTION && t != StreamRow.TYPE_SURFACED_DIVIDER;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            StreamRow row = streamRows.get(position);
            if (convertView != null && !waveCouplingActive && waveReboundAnim == null) {
                resetItemWaveTransform(convertView);
            }

            if (row.type == StreamRow.TYPE_SURFACED_DIVIDER) {
                LinearLayout wrap = convertView instanceof LinearLayout && "surf_div".equals(convertView.getTag())
                        ? (LinearLayout) convertView : null;
                if (wrap == null) {
                    wrap = new LinearLayout(MainActivity.this);
                    wrap.setTag("surf_div");
                    wrap.setOrientation(LinearLayout.HORIZONTAL);
                    wrap.setGravity(Gravity.CENTER_VERTICAL);
                    wrap.setPadding(dp(4), dp(10), dp(4), dp(6));

                    View bar = new View(MainActivity.this);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor((theme.accent & 0x00FFFFFF) | (theme.isLight ? 0x48000000 : 0x62000000));
                    bg.setCornerRadius(dp(1f));
                    bar.setBackground(bg);
                    wrap.addView(bar, new LinearLayout.LayoutParams(dp(26), Math.max(2, dp(1.5f))));
                }
                return wrap;
            }

            if (row.type == StreamRow.TYPE_CALC_RESULT) {
                FrameLayout wrap = convertView instanceof FrameLayout && "calc_row".equals(convertView.getTag())
                        ? (FrameLayout) convertView : null;
                TextView tv;
                if (wrap == null) {
                    wrap = new FrameLayout(MainActivity.this);
                    wrap.setTag("calc_row");
                    wrap.setPadding(0, dp(3), 0, dp(3));

                    LinearLayout pill = new LinearLayout(MainActivity.this);
                    pill.setOrientation(LinearLayout.HORIZONTAL);
                    pill.setGravity(Gravity.CENTER_VERTICAL);
                    pill.setBackground(theme.accentGlowPill(dpUnit, 18));
                    pill.setPadding(dp(14), dp(11), dp(14), dp(11));

                    VectorGlyphView ic = new VectorGlyphView(MainActivity.this, VectorGlyphView.IC_CALC, theme.accent, dpUnit);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(18), dp(18));
                    ilp.rightMargin = dp(10);
                    pill.addView(ic, ilp);

                    tv = new TextView(MainActivity.this);
                    pill.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    wrap.addView(pill, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else {
                    LinearLayout pill = (LinearLayout) wrap.getChildAt(0);
                    tv = (TextView) pill.getChildAt(1);
                }
                tv.setText("= " + row.text + "   · 点击复制");
                tv.setTextColor(theme.accent);
                tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(17));
                return wrap;
            }

            if (row.type == StreamRow.TYPE_WEB_SEARCH) {
                FrameLayout wrap = convertView instanceof FrameLayout && "web_row".equals(convertView.getTag())
                        ? (FrameLayout) convertView : null;
                LinearLayout pill;
                TextView tv;
                if (wrap == null) {
                    wrap = new FrameLayout(MainActivity.this);
                    wrap.setTag("web_row");
                    wrap.setPadding(0, dp(3), 0, dp(3));

                    pill = new LinearLayout(MainActivity.this);
                    pill.setOrientation(LinearLayout.HORIZONTAL);
                    pill.setGravity(Gravity.CENTER_VERTICAL);
                    pill.setBackground(theme.subtleGhostPill(dpUnit, 16));
                    pill.setPadding(dp(14), dp(11), dp(14), dp(11));

                    VectorGlyphView ic = new VectorGlyphView(MainActivity.this, VectorGlyphView.IC_GLOBE, theme.accent, dpUnit);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(17), dp(17));
                    ilp.rightMargin = dp(10);
                    pill.addView(ic, ilp);

                    tv = new TextView(MainActivity.this);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    pill.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    wrap.addView(pill, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else {
                    pill = (LinearLayout) wrap.getChildAt(0);
                    tv = (TextView) pill.getChildAt(1);
                }
                tv.setText("使用 " + getCurrentSearchEngineName() + " 搜索「" + row.text + "」");
                tv.setTextColor(theme.textPrimary);
                tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13.8f));
                final String qText = row.text;
                pill.setOnClickListener(v -> openWebSearch(qText));
                return wrap;
            }

            final String cleanSearchQ = (searchActive && searchInput != null)
                    ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT).replace(" ", "")
                    : "";

            if (row.type == StreamRow.TYPE_SHORTCUT_RESULT) {
                LinearLayout rowView = convertView instanceof LinearLayout && "sc_row".equals(convertView.getTag())
                        ? (LinearLayout) convertView : null;
                ImageView iv;
                TextView t1, t2;
                if (rowView == null) {
                    rowView = new LinearLayout(MainActivity.this);
                    rowView.setTag("sc_row");
                    rowView.setOrientation(LinearLayout.HORIZONTAL);
                    rowView.setGravity(Gravity.CENTER_VERTICAL);
                    rowView.setPadding(dp(4), dp(8), dp(12), dp(8));

                    iv = new ImageView(MainActivity.this);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34));
                    ilp.rightMargin = dp(13);
                    rowView.addView(iv, ilp);

                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    t1 = new TextView(MainActivity.this);
                    t1.setSingleLine(true);
                    t1.setEllipsize(TextUtils.TruncateAt.END);
                    t2 = new TextView(MainActivity.this);
                    t2.setSingleLine(true);
                    t2.setEllipsize(TextUtils.TruncateAt.END);
                    col.addView(t1);
                    col.addView(t2);
                    rowView.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                } else {
                    iv = (ImageView) rowView.getChildAt(0);
                    LinearLayout col = (LinearLayout) rowView.getChildAt(1);
                    t1 = (TextView) col.getChildAt(0);
                    t2 = (TextView) col.getChildAt(1);
                }
                if (row.app != null) iv.setImageDrawable(getShapedIcon(row.app));
                t1.setText(cleanSearchQ.isEmpty() ? row.text : buildHighlightedLabel(row.text, cleanSearchQ));
                t1.setTextColor(theme.textPrimary);
                t1.setTypeface(typeface);
                t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(14.5f));
                t2.setText(row.app != null ? (row.app.label + " · 快捷方式") : "应用快捷方式");
                t2.setTextColor(theme.accent);
                t2.setTypeface(typeface);
                t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.5f));
                rowView.setOnClickListener(v -> {
                    if (row.shortcut != null) startSearchShortcut(row.shortcut, row.app);
                });
                return rowView;
            }

            if (row.type == StreamRow.TYPE_FOLDER) {
                LinearLayout rowView = convertView instanceof LinearLayout && "folder_row".equals(convertView.getTag())
                        ? (LinearLayout) convertView : null;
                ImageView iv;
                TextView t1, t2;
                View dot;
                if (rowView == null) {
                    rowView = new LinearLayout(MainActivity.this);
                    rowView.setTag("folder_row");
                    rowView.setOrientation(LinearLayout.HORIZONTAL);
                    rowView.setGravity(Gravity.CENTER_VERTICAL);

                    iv = new ImageView(MainActivity.this);
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
                    ilp.rightMargin = dp(15);
                    rowView.addView(iv, ilp);

                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    t1 = new TextView(MainActivity.this);
                    t1.setSingleLine(true);
                    t1.setEllipsize(TextUtils.TruncateAt.END);
                    t2 = new TextView(MainActivity.this);
                    t2.setSingleLine(true);
                    t2.setEllipsize(TextUtils.TruncateAt.END);
                    col.addView(t1);
                    col.addView(t2);
                    rowView.addView(col, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    dot = new View(MainActivity.this);
                    GradientDrawable dbg = new GradientDrawable();
                    dbg.setShape(GradientDrawable.OVAL);
                    dbg.setColor(theme.accent);
                    dot.setBackground(dbg);
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
                    dlp.leftMargin = dp(8);
                    rowView.addView(dot, dlp);
                } else {
                    iv = (ImageView) rowView.getChildAt(0);
                    LinearLayout col = (LinearLayout) rowView.getChildAt(1);
                    t1 = (TextView) col.getChildAt(0);
                    t2 = (TextView) col.getChildAt(1);
                    dot = rowView.getChildAt(2);
                }

                JSONObject fo = getFoldersArray().optJSONObject(row.folderIdx);
                JSONArray appsArr = fo != null ? fo.optJSONArray("apps") : null;
                boolean tapLaunchFirst = fo != null && fo.optBoolean("tapLaunchFirst", false);
                List<Drawable> memberIcons = new ArrayList<>();
                AppEntry firstValidApp = null;
                boolean anyUnread = false;
                int cnt = appsArr != null ? appsArr.length() : 0;
                for (int i = 0; i < cnt; i++) {
                    AppEntry ma = findAppByKey(appsArr.optString(i));
                    if (ma != null) {
                        if (firstValidApp == null) firstValidApp = ma;
                        if (memberIcons.size() < 4) {
                            Drawable d = getShapedIcon(ma);
                            if (d != null) memberIcons.add(d);
                        }
                        if (ma.component != null && NotificationHubService.hasUnreadForApp(ma.component.getPackageName(), ma.user)) {
                            anyUnread = true;
                        }
                    }
                }
                boolean showIcons = prefs.getBoolean(K_SHOW_ICONS, true);
                iv.setVisibility(showIcons ? View.VISIBLE : View.GONE);
                if (showIcons) {
                    StringBuilder fk = new StringBuilder();
                    fk.append(theme.isLight ? 'L' : 'D').append(':').append(prefs.getInt(K_ICON_SHAPE, 0))
                            .append(':').append(prefs.getString(K_ICON_PACK, "")).append(':').append(row.folderIdx);
                    for (int i = 0; i < Math.min(4, cnt); i++) {
                        fk.append('|').append(appsArr.optString(i));
                    }
                    String cacheKey = fk.toString();
                    Drawable cachedFolder = folderIconCache.get(cacheKey);
                    if (cachedFolder == null) {
                        cachedFolder = IconPackManager.createFolderIcon(MainActivity.this, memberIcons, Math.max(88, dp(44)), theme);
                        folderIconCache.put(cacheKey, cachedFolder);
                    }
                    iv.setImageDrawable(cachedFolder);
                }
                t1.setText(row.text);
                t1.setTextColor(theme.textPrimary);
                t1.setTypeface(typeface);
                t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(15.5f));
                t1.setShadowLayer(theme.isLight ? dp(1.0f) : dp(2.2f), 0f, dp(0.6f), theme.isLight ? 0x20FFFFFF : 0x48000000);
                if (cnt == 0) {
                    t2.setText("空文件夹 · 长按选应用");
                    t2.setVisibility(View.VISIBLE);
                } else if (tapLaunchFirst) {
                    t2.setText("轻点直开 · " + cnt + " 项");
                    t2.setVisibility(View.VISIBLE);
                } else {
                    t2.setVisibility(View.GONE);
                }
                t2.setTextColor(theme.textSecondary);
                t2.setTypeface(typeface);
                t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.4f));
                dot.setVisibility(anyUnread ? View.VISIBLE : View.GONE);

                float mul = getRowSpacingMultiplier();
                int vPad = dp(8.5f * mul);
                rowView.setPadding(dp(2), vPad, dp(12), vPad);
                if (prefs.getBoolean(K_FAV_CARDS, false)) {
                    rowView.setBackground(cachedFavCardNormal);
                    rowView.setPadding(dp(10), dp(6.5f * mul), dp(14), dp(6.5f * mul));
                } else {
                    rowView.setBackground(null);
                }
                final int fIdx = row.folderIdx;
                final AppEntry fFirst = firstValidApp;
                final boolean fTapFirst = tapLaunchFirst;
                rowView.setOnClickListener(v -> {
                    if (fTapFirst && fFirst != null) {
                        launchApp(fFirst, v);
                    } else if (popupController != null) {
                        popupController.showFolderPopup(v, fIdx, false);
                    }
                });
                rowView.setOnLongClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (popupController != null) popupController.showFolderPopup(v, fIdx, true);
                    return true;
                });
                return rowView;
            }

            if (row.type == StreamRow.TYPE_SECTION) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
                tv.setText(row.text);
                tv.setTextColor(theme.textPrimary);
                boolean isHighlighted = highlightedGroup != 0 && row.text != null && row.text.length() == 1 && row.text.charAt(0) == highlightedGroup;
                tv.setAlpha(highlightedGroup == 0 ? 0.65f : (isHighlighted ? 1.0f : 0.25f));
                tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13.0f));
                tv.setPadding(dp(4), dp(14 * getRowSpacingMultiplier()), 0, dp(4));
                return tv;
            }

            boolean isFav = (row.type == StreamRow.TYPE_FAV_APP);
            LinearLayout outerWrap;
            LinearLayout cardRow;
            TextView gutterView;
            ImageView iconView;
            TextView titleView, subNotifView;
            View popupIndicator;
            View unreadBadge;

            if (convertView instanceof LinearLayout && "app_row_v2".equals(convertView.getTag())) {
                outerWrap = (LinearLayout) convertView;
                cardRow = (LinearLayout) outerWrap.getChildAt(0);
                gutterView = (TextView) cardRow.getChildAt(0);
                iconView = (ImageView) cardRow.getChildAt(1);
                LinearLayout textCol = (LinearLayout) cardRow.getChildAt(2);
                titleView = (TextView) textCol.getChildAt(0);
                subNotifView = (TextView) textCol.getChildAt(1);
                popupIndicator = cardRow.getChildAt(3);
                unreadBadge = cardRow.getChildAt(4);
            } else {
                outerWrap = new LinearLayout(MainActivity.this);
                outerWrap.setOrientation(LinearLayout.VERTICAL);
                outerWrap.setTag("app_row_v2");

                cardRow = new LinearLayout(MainActivity.this);
                cardRow.setOrientation(LinearLayout.HORIZONTAL);
                cardRow.setGravity(Gravity.CENTER_VERTICAL);

                gutterView = new TextView(MainActivity.this);
                gutterView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                gutterView.setSingleLine(true);

                iconView = new ImageView(MainActivity.this);
                LinearLayout textCol = new LinearLayout(MainActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);

                titleView = new TextView(MainActivity.this);
                titleView.setMaxLines(2);
                titleView.setHorizontallyScrolling(false);
                titleView.setEllipsize(TextUtils.TruncateAt.END);

                subNotifView = new TextView(MainActivity.this);
                subNotifView.setMaxLines(1);
                subNotifView.setHorizontallyScrolling(false);
                subNotifView.setEllipsize(TextUtils.TruncateAt.END);

                textCol.addView(titleView);
                textCol.addView(subNotifView);

                popupIndicator = new PopupChevronView(MainActivity.this, theme.textSecondary, dpUnit);
                LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(10), dp(12));
                chevLp.leftMargin = dp(6);

                unreadBadge = new View(MainActivity.this);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(theme.accent);
                unreadBadge.setBackground(dotBg);

                cardRow.addView(gutterView, new LinearLayout.LayoutParams(
                        dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));
                cardRow.addView(iconView);
                cardRow.addView(textCol, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                cardRow.addView(popupIndicator, chevLp);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
                dotLp.leftMargin = dp(8);
                cardRow.addView(unreadBadge, dotLp);

                final LinearLayout rowAnchor = outerWrap;
                cardRow.setOnClickListener(v -> {
                    if (isWaveScrubbing || (SystemClock.uptimeMillis() - lastWaveReleaseMs) < 220L) {
                        return;
                    }
                    Object tag = v.getTag();
                    if (!(tag instanceof AppEntry)) return;
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(55)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                            .start();
                    openAppOrNotificationTarget((AppEntry) tag, v, true);
                });
                cardRow.setOnLongClickListener(v -> {
                    if (isWaveScrubbing) return true;
                    Object tag = v.getTag();
                    if (!(tag instanceof AppEntry)) return true;
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (popupController != null) {
                        popupController.showAppPopup(rowAnchor, (AppEntry) tag, true);
                    }
                    return true;
                });

                outerWrap.addView(cardRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            final AppEntry app = row.app;
            cardRow.setTag(app);

            boolean showGutter = !isFav && row.text != null;
            boolean isGroupHead = showGutter && !row.text.isEmpty();
            if (showGutter) {
                gutterView.setVisibility(View.VISIBLE);
                gutterView.setText(row.text);
                gutterView.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                gutterView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13.5f));
                boolean isGutterHighlight = highlightedGroup != 0 && row.text.length() == 1 && row.text.charAt(0) == highlightedGroup;
                gutterView.setTextColor(isGutterHighlight ? theme.accent : theme.textPrimary);
                gutterView.setAlpha(highlightedGroup == 0 ? 0.62f : (isGutterHighlight ? 1.0f : 0.22f));
            } else {
                gutterView.setVisibility(View.GONE);
            }

            boolean showIcons = prefs.getBoolean(K_SHOW_ICONS, true);
            int iconBaseDp = isFav ? 40 : 35;
            int iconSize = dp(iconBaseDp * Math.min(textScale, 1.15f));
            int iconRightMargin = dp(isFav ? 15 : 13);
            ViewGroup.LayoutParams curIlp = iconView.getLayoutParams();
            if (!(curIlp instanceof LinearLayout.LayoutParams)
                    || curIlp.width != iconSize
                    || curIlp.height != iconSize
                    || ((LinearLayout.LayoutParams) curIlp).rightMargin != iconRightMargin) {
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
                ilp.rightMargin = iconRightMargin;
                iconView.setLayoutParams(ilp);
            }
            iconView.setImageDrawable(getShapedIcon(app));
            iconView.setVisibility(showIcons ? View.VISIBLE : View.GONE);

            boolean isGroupActive = highlightedGroup != 0 && !isFav && app.groupChar == highlightedGroup;
            boolean isDimmed = highlightedGroup != 0 && !isFav && !isGroupActive;

            titleView.setTextColor(theme.textPrimary);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(isFav ? 15.5f : 14.5f));
            titleView.setLetterSpacing(0.01f);
            titleView.setTypeface(isGroupActive ? Typeface.create(typeface, Typeface.BOLD) : typeface);
            titleView.setShadowLayer(
                    theme.isLight ? dp(1.0f) : dp(2.2f),
                    0f,
                    dp(0.6f),
                    theme.isLight ? 0x20FFFFFF : 0x48000000);
            titleView.setText(cleanSearchQ.isEmpty() ? app.label : buildHighlightedLabel(app.label, cleanSearchQ));

            String pkg = app.component != null ? app.component.getPackageName() : "";
            boolean useFavCards = isFav && prefs.getBoolean(K_FAV_CARDS, false);
            boolean hasUnread = !pkg.isEmpty() && NotificationHubService.hasUnreadForApp(pkg, app.user);
            boolean hasPopupChildren = isFav && hasAppPopupChildren(app.idKey);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int reservedW = dp(38 + 24 + (showGutter ? 26 : 0)
                    + (showIcons ? (iconBaseDp * Math.min(textScale, 1.15f) + 14) : 0)
                    + (useFavCards ? 28 : 12) + (hasUnread ? 15 : 0) + (hasPopupChildren ? 16 : 0));
            int availTextW = Math.max(dp(185), screenW - reservedW);
            titleView.setMaxWidth(availTextW);
            subNotifView.setMaxWidth(availTextW);

            outerWrap.setAlpha(isDimmed ? 0.30f : 1.0f);

            NotificationHubService.NotifItem notif = (!pkg.isEmpty() && prefs.getBoolean(K_INLINE_NOTIF, true))
                    ? NotificationHubService.getLatestForApp(pkg, app.user) : null;
            int notifCount = !pkg.isEmpty() ? NotificationHubService.getCountForApp(pkg, app.user) : 0;
            boolean hasNotif = (notif != null);

            if (hasNotif) {
                String extraCount = notifCount > 1 ? (" · +" + (notifCount - 1)) : "";
                String summary = (notif.title.isEmpty() ? "" : notif.title + "：") + notif.text + extraCount;
                subNotifView.setText(summary);
                subNotifView.setTextColor(theme.isLight ? 0xB8141824 : 0xCCE2E8F0);
                subNotifView.setTypeface(typeface);
                subNotifView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.8f));
                subNotifView.setVisibility(View.VISIBLE);
            } else {
                subNotifView.setVisibility(View.GONE);
            }

            // 已挂载 App Pop-up 子应用的首页行展示极克制微弱指示小箭头
            popupIndicator.setVisibility(hasPopupChildren ? View.VISIBLE : View.GONE);
            // 已有内联通知摘要时不再重复叠加圆点，保持原生 Niagara 视觉克制
            unreadBadge.setVisibility((hasUnread && !hasNotif) ? View.VISIBLE : View.GONE);

            float mul = getRowSpacingMultiplier();
            if (useFavCards) {
                cardRow.setBackground(hasNotif ? cachedFavCardNotif : cachedFavCardNormal);
                int vPad = dp(6.5f * mul);
                cardRow.setPadding(dp(10), vPad, dp(14), vPad);
                outerWrap.setPadding(0, dp(2.2f * mul), dp(4), dp(2.2f * mul));
            } else {
                cardRow.setBackground(null);
                int vPad = dp((isFav ? 8.5f : 6.0f) * mul);
                cardRow.setPadding(dp(2), vPad, dp(12), vPad);
                int groupTopGap = (isGroupHead && position > 0) ? dp(7f * mul) : 0;
                outerWrap.setPadding(0, groupTopGap, 0, 0);
            }
            return outerWrap;
        }
    }

    /** 极克制 App Pop-up 微指示小箭头（仅在首页已挂载子应用集合的行尾显示）。 */
    private static final class PopupChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PopupChevronView(Context ctx, int color, float dp) {
            super(ctx);
            paint.setColor(color);
            paint.setAlpha(112);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.3f * dp);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w * 0.5f, cy = h * 0.5f;
            float dx = w * 0.22f, dy = h * 0.26f;
            path.reset();
            path.moveTo(cx - dx, cy - dy);
            path.lineTo(cx + dx, cy);
            path.lineTo(cx - dx, cy + dy);
            c.drawPath(path, paint);
        }
    }

    // ---------- 图标重塑与设置入口 ----------
    private JSONObject getCustomIconsJson() {
        try { return new JSONObject(prefs.getString(K_CUSTOM_ICONS, "{}")); }
        catch (Throwable t) { return new JSONObject(); }
    }

    private String getCustomIconSpecCached(String idKey) {
        if (customIconsCache == null) {
            Map<String, String> map = new HashMap<>();
            JSONObject obj = getCustomIconsJson();
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = obj.optString(k, "");
                if (!v.isEmpty()) map.put(k, v);
            }
            customIconsCache = map;
        }
        String val = customIconsCache.get(idKey);
        return val != null ? val : "";
    }

    private void invalidateCustomIconsCache() {
        customIconsCache = null;
        folderIconCache.clear();
    }

    Drawable getShapedIcon(AppEntry app) {
        int shape = Math.max(0, Math.min(ICON_SHAPES.length - 1, prefs.getInt(K_ICON_SHAPE, 0)));
        String activePack = prefs.getString(K_ICON_PACK, "");
        String customSpec = getCustomIconSpecCached(app.idKey);
        int modeSignature = shape * 1000003 ^ activePack.hashCode() ^ customSpec.hashCode();
        if (app.shapedIcon != null && app.shapedMode == modeSignature) return app.shapedIcon;
        if (app.rawIcon == null) return app.shapedIcon;
        try {
            int size = Math.max(96, dp(48));

            // 1. 优先检查单应用自定义图标或第三方图标包
            Drawable sourceDrawable = app.rawIcon;
            boolean fromPack = false;
            if (!customSpec.isEmpty() && customSpec.contains("|")) {
                int sep = customSpec.indexOf('|');
                Drawable cd = IconPackManager.loadDrawableByName(this, customSpec.substring(0, sep), customSpec.substring(sep + 1));
                if (cd != null) {
                    sourceDrawable = cd;
                    fromPack = true;
                }
            } else if (!activePack.isEmpty() && app.component != null) {
                Drawable pd = IconPackManager.resolveIconFromActivePack(this, activePack, app.component, app.rawIcon, size);
                if (pd != null) {
                    sourceDrawable = pd;
                    fromPack = true;
                }
            }

            if (shape == 8) { // 8: 极简圆点 Niagara Dots
                app.shapedIcon = IconPackManager.createNiagaraDotIcon(this, sourceDrawable, size, theme.accent);
                app.shapedMode = modeSignature;
                return app.shapedIcon;
            }

            if (shape == 7 || fromPack) { // 7: 原样高清 Native 或图标包直出
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(bmp);
                sourceDrawable.setBounds(0, 0, size, size);
                sourceDrawable.draw(cv);
                app.shapedIcon = new BitmapDrawable(getResources(), bmp);
                app.shapedMode = modeSignature;
                return app.shapedIcon;
            }

            float pad = (shape == 5) ? size * 0.06f : size * 0.025f;
            float inner = size - pad * 2f;
            Path shapePath = new Path();
            if (shape == 3) { // 3: 极简正圆 Pixel 原生圆
                shapePath.addCircle(size * 0.5f, size * 0.5f, inner * 0.5f, Path.Direction.CW);
            } else if (shape == 2) { // 2: 圆润灵动 One UI / HyperOS
                buildSuperEllipsePath(shapePath, pad, inner, 0.34);
            } else { // 0, 1, 4, 5, 6: iOS / ColorOS G2 连续超椭圆 Squircle
                buildSuperEllipsePath(shapePath, pad, inner, 0.42);
            }

            // 1. 绘制高清原始内容位图
            Bitmap contentBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas contentCv = new Canvas(contentBmp);
            if (sourceDrawable instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable ad = (AdaptiveIconDrawable) sourceDrawable;
                int inset = Math.round(size * 0.22f);
                Rect bounds = new Rect(-inset, -inset, size + inset, size + inset);
                Drawable bg = ad.getBackground(), fg = ad.getForeground();
                if (bg != null) { bg.setBounds(bounds); bg.draw(contentCv); }
                if (fg != null) { fg.setBounds(bounds); fg.draw(contentCv); }
            } else {
                contentCv.drawColor(theme.isLight ? 0xFFFDFEFE : 0xFF1E293B);
                int iconPad = Math.round(size * 0.13f);
                sourceDrawable.setBounds(iconPad, iconPad, size - iconPad, size - iconPad);
                sourceDrawable.draw(contentCv);
            }

            // 2. 使用 PorterDuff.SRC_IN 抗锯齿蒙版，彻底消除 clipPath 锯齿
            Bitmap maskBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas maskCv = new Canvas(maskBmp);
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setColor(0xFFFFFFFF);
            maskCv.drawPath(shapePath, maskPaint);

            Bitmap maskedBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas maskedCv = new Canvas(maskedBmp);
            Paint xferPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            maskedCv.drawBitmap(maskBmp, 0f, 0f, xferPaint);
            xferPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            maskedCv.drawBitmap(contentBmp, 0f, 0f, xferPaint);
            contentBmp.recycle();
            maskBmp.recycle();

            // 3. 流行质感后处理
            Bitmap finalBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas finalCv = new Canvas(finalBmp);

            if (shape == 5) { // 5: 磨砂微立体 3D 质感（柔和投影）
                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setColor(0x01000000);
                shadowPaint.setShadowLayer(size * 0.055f, 0f, size * 0.032f, 0x55000000);
                finalCv.drawPath(shapePath, shadowPaint);
            }

            Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (shape == 6) { // 6: 极简黑白 禅意单色
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0f);
                drawPaint.setColorFilter(new ColorMatrixColorFilter(cm));
                drawPaint.setAlpha(232);
            } else if (shape == 4) { // 4: 莫奈主题 Material You 柔彩
                float r = Color.red(theme.accent) / 255f;
                float g = Color.green(theme.accent) / 255f;
                float b = Color.blue(theme.accent) / 255f;
                ColorMatrix cm = new ColorMatrix(new float[]{
                        0.299f * (0.45f + 0.55f * r), 0.587f * (0.45f + 0.55f * r), 0.114f * (0.45f + 0.55f * r), 0f, r * 36f,
                        0.299f * (0.45f + 0.55f * g), 0.587f * (0.45f + 0.55f * g), 0.114f * (0.45f + 0.55f * g), 0f, g * 36f,
                        0.299f * (0.45f + 0.55f * b), 0.587f * (0.45f + 0.55f * b), 0.114f * (0.45f + 0.55f * b), 0f, b * 36f,
                        0f, 0f, 0f, 1f, 0f
                });
                drawPaint.setColorFilter(new ColorMatrixColorFilter(cm));
            }
            finalCv.drawBitmap(maskedBmp, 0f, 0f, drawPaint);
            maskedBmp.recycle();

            if (shape == 1) { // 1: 液态玻璃 Liquid Glass 26（左上高光扫光 + 通透玻璃描边）
                Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                sheenPaint.setShader(new android.graphics.LinearGradient(
                        0f, 0f, size * 0.85f, size * 0.85f,
                        new int[]{0x58FFFFFF, 0x14FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.42f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
                finalCv.drawPath(shapePath, sheenPaint);

                Paint glassRim = new Paint(Paint.ANTI_ALIAS_FLAG);
                glassRim.setStyle(Paint.Style.STROKE);
                glassRim.setStrokeWidth(Math.max(1.8f, size * 0.032f));
                glassRim.setShader(new android.graphics.LinearGradient(
                        0f, 0f, size, size,
                        new int[]{0xD9FFFFFF, 0x38FFFFFF, 0x80FFFFFF},
                        new float[]{0f, 0.55f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
                finalCv.drawPath(shapePath, glassRim);
            } else if (shape == 5) { // 5: 磨砂微立体顶部高光边
                Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bevelPaint.setStyle(Paint.Style.STROKE);
                bevelPaint.setStrokeWidth(Math.max(1.5f, size * 0.028f));
                bevelPaint.setShader(new android.graphics.LinearGradient(
                        0f, 0f, 0f, size,
                        0x75FFFFFF, 0x26000000,
                        android.graphics.Shader.TileMode.CLAMP));
                finalCv.drawPath(shapePath, bevelPaint);
            } else {
                Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                rimPaint.setStyle(Paint.Style.STROKE);
                rimPaint.setStrokeWidth(Math.max(1.2f, size * 0.02f));
                rimPaint.setColor(theme.isLight ? 0x18000000 : 0x28FFFFFF);
                finalCv.drawPath(shapePath, rimPaint);
            }

            app.shapedIcon = new BitmapDrawable(getResources(), finalBmp);
            app.shapedMode = modeSignature;
            return app.shapedIcon;
        } catch (Throwable t) {
            return app.rawIcon;
        }
    }

    private static void buildSuperEllipsePath(Path path, float offset, float span, double exp) {
        float half = span * 0.5f;
        float cx = offset + half;
        float cy = offset + half;
        int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double ang = 2.0 * Math.PI * i / steps;
            double ct = Math.cos(ang), st = Math.sin(ang);
            float x = (float) (cx + half * Math.signum(ct) * Math.pow(Math.abs(ct), exp));
            float y = (float) (cy + half * Math.signum(st) * Math.pow(Math.abs(st), exp));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    private AppEntry createSettingsAppEntry() {
        AppEntry a = buildAppEntry(new ComponentName(this, MainActivity.class), Process.myUserHandle(), "流光桌面设置");
        a.idKey = "__niagara_settings__";
        a.label = "流光桌面设置";
        a.rawIcon = getDrawable(android.R.drawable.sym_def_app_icon);
        return a;
    }

    private boolean isXiaoBuApp(AppEntry a) {
        if (a == null) return false;
        if ("virtual_xiaobu_ai".equals(a.idKey)) return true;
        if (a.component != null) {
            String p = a.component.getPackageName().toLowerCase(Locale.ROOT);
            if (p.contains("speechassist") || p.contains("breeno") || "com.oppo.quicksearchbox".equals(p)) {
                return true;
            }
        }
        if (a.label != null) {
            String lb = a.label.toLowerCase(Locale.ROOT);
            if (lb.contains("小布") || lb.contains("breeno") || lb.contains("speech assist")) {
                return true;
            }
        }
        return false;
    }

    private boolean launchColorOsXiaoBu() {
        // 1. ColorOS 专属激活 Intent (直接唤醒语音/AI 对话浮窗)
        String[] actions = new String[]{
            "heytap.intent.action.ACTIVATE_SPEECH_ASSIST",
            "com.heytap.speechassist.ACTION_START_SPEECH",
            Intent.ACTION_ASSIST,
            Intent.ACTION_VOICE_COMMAND
        };
        for (String act : actions) {
            try {
                Intent it = new Intent(act);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return true;
            } catch (Throwable ignored) {}
        }

        // 2. 指定小布包名启动
        String[] pkgs = new String[]{
            "com.heytap.speechassist",
            "com.coloros.speechassist",
            "com.heytap.breeno",
            "com.oppo.quicksearchbox"
        };
        for (String p : pkgs) {
            try {
                Intent it = getPackageManager().getLaunchIntentForPackage(p);
                if (it != null) {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                    return true;
                }
            } catch (Throwable ignored) {}
            try {
                Intent it = new Intent(Intent.ACTION_ASSIST);
                it.setPackage(p);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return true;
            } catch (Throwable ignored) {}
        }

        // 3. 常见组件类名显式启动
        String[] acts = new String[]{
            "com.heytap.speechassist.launcher.speech.SpeechLauncherActivity",
            "com.heytap.speechassist.core.SpeechActivity",
            "com.heytap.speechassist.gui.activity.SpeechMainActivity",
            "com.coloros.speechassist.SpeechActivity"
        };
        for (String p : pkgs) {
            for (String act : acts) {
                try {
                    Intent ci = new Intent();
                    ci.setComponent(new ComponentName(p, act));
                    ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(ci);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private void injectXiaoBuIfAvailable(List<AppEntry> loaded) {
        for (AppEntry a : loaded) {
            if (isXiaoBuApp(a)) return;
        }
        // 1. 通过系统 Intent 解析小布
        try {
            Intent it = new Intent("heytap.intent.action.ACTIVATE_SPEECH_ASSIST");
            List<ResolveInfo> resolves = getPackageManager().queryIntentActivities(it, 0);
            if (resolves != null && !resolves.isEmpty()) {
                ResolveInfo ri = resolves.get(0);
                AppEntry xb = new AppEntry();
                xb.idKey = "virtual_xiaobu_ai";
                CharSequence cs = ri.loadLabel(getPackageManager());
                xb.rawLabel = (cs != null && cs.length() > 0) ? cs.toString() : "小布助手";
                xb.label = xb.rawLabel;
                String[] keys = PinyinEngine.buildSearchKeys(xb.label);
                xb.fullPy = keys[0];
                xb.iniPy = keys[1];
                xb.t9Digits = keys[2];
                xb.groupChar = PinyinEngine.groupLetter(xb.label);
                try { xb.rawIcon = ri.loadIcon(getPackageManager()); } catch (Throwable ignored) {}
                if (xb.rawIcon == null) xb.rawIcon = getDrawable(android.R.drawable.sym_def_app_icon);
                getShapedIcon(xb);
                loaded.add(xb);
                return;
            }
        } catch (Throwable ignored) {}

        // 2. 通过已安装包名扫描小布
        String[] pkgs = new String[]{"com.heytap.speechassist", "com.coloros.speechassist", "com.heytap.breeno", "com.oppo.quicksearchbox"};
        for (String p : pkgs) {
            try {
                PackageInfo pi = getPackageManager().getPackageInfo(p, 0);
                if (pi != null) {
                    AppEntry xb = new AppEntry();
                    xb.idKey = "virtual_xiaobu_ai";
                    xb.rawLabel = "小布助手";
                    xb.label = "小布助手";
                    String[] keys = PinyinEngine.buildSearchKeys(xb.label);
                    xb.fullPy = keys[0];
                    xb.iniPy = keys[1];
                    xb.t9Digits = keys[2];
                    xb.groupChar = PinyinEngine.groupLetter(xb.label);
                    try {
                        xb.rawIcon = pi.applicationInfo.loadIcon(getPackageManager());
                    } catch (Throwable ignored) {}
                    if (xb.rawIcon == null) {
                        xb.rawIcon = getDrawable(android.R.drawable.sym_def_app_icon);
                    }
                    getShapedIcon(xb);
                    loaded.add(xb);
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 3. 若为 OPPO / OnePlus / Realme 设备，默认添加小布助手入口
        String mfg = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "").toLowerCase(Locale.ROOT);
        String brand = (Build.BRAND != null ? Build.BRAND : "").toLowerCase(Locale.ROOT);
        if (mfg.contains("oppo") || mfg.contains("oneplus") || brand.contains("realme") || mfg.contains("realme")) {
            AppEntry xb = new AppEntry();
            xb.idKey = "virtual_xiaobu_ai";
            xb.rawLabel = "小布助手";
            xb.label = "小布助手";
            String[] keys = PinyinEngine.buildSearchKeys(xb.label);
            xb.fullPy = keys[0];
            xb.iniPy = keys[1];
            xb.t9Digits = keys[2];
            xb.groupChar = PinyinEngine.groupLetter(xb.label);
            xb.rawIcon = getDrawable(android.R.drawable.sym_def_app_icon);
            getShapedIcon(xb);
            loaded.add(xb);
        }
    }

    private boolean launchWebShortcutAsStandalone(AppEntry app, Bundle opts) {
        if (app == null) return false;
        String label = app.label != null ? app.label : "";
        boolean isAntiGravity = label.contains("反重力") || (app.rawLabel != null && app.rawLabel.contains("反重力"));

        String pkg = null;
        if (app.shortcutInfo != null) {
            pkg = app.shortcutInfo.getPackage();
        } else if (app.shortcutIntent != null && app.shortcutIntent.getPackage() != null) {
            pkg = app.shortcutIntent.getPackage();
        } else if (app.component != null) {
            pkg = app.component.getPackageName();
        }

        String targetUrl = null;
        if (app.shortcutIntent != null && app.shortcutIntent.getData() != null) {
            targetUrl = app.shortcutIntent.getData().toString();
        } else if (app.shortcutInfo != null) {
            String id = app.shortcutInfo.getId();
            if (id != null && (id.startsWith("http://") || id.startsWith("https://"))) {
                targetUrl = id;
            }
        }
        if (targetUrl == null || targetUrl.isEmpty()) {
            if (isAntiGravity) {
                targetUrl = "https://antigravity.google.com";
            } else {
                return false;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        if (pkg != null && !pkg.isEmpty()) {
            intent.setPackage(pkg);
        }

        // 🌟 1. 独立任务栈与多任务卡片隔离，确保完全独立，绝不影响再次打开主浏览器
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);

        // 🌟 2. Chromium / Brave PWA 独立全屏与独立应用窗口规范（隐藏地址栏与标签页）
        intent.putExtra("org.chromium.chrome.extra.LAUNCH_AS_PWA", true);
        intent.putExtra("org.chromium.chrome.browser.webapp_url", targetUrl);
        intent.putExtra("org.chromium.chrome.browser.webapp_id", targetUrl);
        intent.putExtra("org.chromium.chrome.browser.webapp_title", label.isEmpty() ? "Web App" : label);
        intent.putExtra("org.chromium.chrome.browser.webapp_display_mode", 1);
        intent.putExtra("org.chromium.chrome.browser.theme_color", 0xFF18181B);

        // 🌟 3. Chrome Custom Tabs (CCT) 独立会话规范（兜底无地址栏独立窗口）
        Bundle cctBundle = new Bundle();
        cctBundle.putBinder("android.support.customtabs.extra.SESSION", null);
        cctBundle.putBinder("androidx.browser.customtabs.extra.SESSION", null);
        intent.putExtras(cctBundle);
        intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF18181B);
        intent.putExtra("androidx.browser.customtabs.extra.TOOLBAR_COLOR", 0xFF18181B);
        intent.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1);
        intent.putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", true);
        intent.putExtra("android.support.customtabs.extra.EXTRA_ENABLE_INSTANT_APPS", true);

        try {
            startActivity(intent, opts);
            return true;
        } catch (Throwable t) {
            try {
                intent.setPackage(null);
                startActivity(intent, opts);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private void openAppOrNotificationTarget(AppEntry app, View sourceView, boolean preferNotification) {
        if (app == null) return;
        if (preferNotification && app != virtualSettingsApp && !app.isShortcut && app.component != null) {
            String pkg = app.component.getPackageName();
            NotificationHubService.NotifItem notif = NotificationHubService.getLatestForApp(pkg, app.user);
            if (notif != null && notif.contentIntent != null) {
                boolean opened = NotificationHubService.openNotificationItem(this, notif, sourceView);
                if (opened) {
                    final AppEntry launchedApp = app;
                    AppExecutors.getInstance().diskIO().execute(() -> recordAppLaunch(launchedApp));
                    hideSoftKeyboard();
                    uiHandler.postDelayed(() -> {
                        if (searchActive) closeInstantSearch();
                    }, 380);
                    return;
                }
            }
        }
        launchApp(app, sourceView);
    }

    private void launchApp(AppEntry app) {
        launchApp(app, null);
    }

    private void launchApp(AppEntry app, View sourceView) {
        if (app == virtualSettingsApp) {
            hideSoftKeyboard();
            openControlCenterSheet();
            return;
        }

        // 专门适配 ColorOS 小布助手 AI：拦截并使用 ColorOS 专属助手通道唤醒
        if (isXiaoBuApp(app)) {
            if (launchColorOsXiaoBu()) {
                final AppEntry launchedApp = app;
                AppExecutors.getInstance().diskIO().execute(() -> recordAppLaunch(launchedApp));
                hideSoftKeyboard();
                uiHandler.postDelayed(() -> {
                    if (searchActive) closeInstantSearch();
                }, 380);
                return;
            }
        }

        try {
            // 1. 传入源图标锚点与平滑展开动画，让进出应用的系统过渡更跟手
            Rect sourceBounds = null;
            Bundle opts = null;
            if (sourceView != null && sourceView.getWidth() > 0 && sourceView.getHeight() > 0) {
                int[] loc = new int[2];
                sourceView.getLocationOnScreen(loc);
                sourceBounds = new Rect(loc[0], loc[1], loc[0] + sourceView.getWidth(), loc[1] + sourceView.getHeight());
                if (Build.VERSION.SDK_INT >= 23) {
                    opts = ActivityOptions.makeClipRevealAnimation(sourceView, 0, 0, sourceView.getWidth(), sourceView.getHeight()).toBundle();
                }
            } else if (Build.VERSION.SDK_INT >= 24) {
                opts = ActivityOptions.makeBasic().toBundle();
            }

            if (app.isShortcut) {
                boolean launched = false;
                // 1. 系统快捷方式：优先使用 LauncherApps 原生启动
                if (app.shortcutInfo != null) {
                    try {
                        launcherApps.startShortcut(app.shortcutInfo, null, opts);
                        launched = true;
                    } catch (Throwable t1) {
                        try {
                            launcherApps.startShortcut(app.shortcutInfo.getPackage(), app.shortcutInfo.getId(), null, opts, app.user != null ? app.user : Process.myUserHandle());
                            launched = true;
                        } catch (Throwable t2) {
                            launched = false;
                        }
                    }
                }
                // 2. 若原生快捷方式启动失败，或者属于自定义反重力/独立 Web 快捷方式，使用 PWA 独立窗口规范启动
                if (!launched) {
                    if (launchWebShortcutAsStandalone(app, opts)) {
                        launched = true;
                    } else if (app.shortcutIntent != null) {
                        startActivity(app.shortcutIntent, opts);
                        launched = true;
                    }
                }
            } else {
                launcherApps.startMainActivity(app.component, app.user, sourceBounds, opts);
            }

            // 2. 启动记录放入后台线程计算，主线程 0 负载
            final AppEntry launchedApp = app;
            AppExecutors.getInstance().diskIO().execute(() -> recordAppLaunch(launchedApp));

            hideSoftKeyboard();
            uiHandler.postDelayed(() -> {
                if (searchActive) closeInstantSearch();
            }, 380);
        } catch (Throwable t) {
            // 启动异常容错：若常规启动被系统拒绝且属于小布助手，转由专属通道唤醒
            if (isXiaoBuApp(app) && launchColorOsXiaoBu()) {
                final AppEntry launchedApp = app;
                AppExecutors.getInstance().diskIO().execute(() -> recordAppLaunch(launchedApp));
                hideSoftKeyboard();
                return;
            }
            Toast.makeText(this, "无法启动 " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void recordAppLaunch(AppEntry app) {
        try {
            JSONObject counts = getLaunchCountsJson();
            counts.put(app.idKey, counts.optInt(app.idKey, 0) + 1);
            prefs.edit().putString(K_LAUNCH_COUNTS, counts.toString()).apply();

            List<String> recents = getStringList(K_RECENT_KEYS);
            recents.remove(app.idKey);
            recents.add(0, app.idKey);
            while (recents.size() > 8) recents.remove(recents.size() - 1);
            putStringList(K_RECENT_KEYS, recents);
        } catch (Throwable ignored) {}
    }

    // ---------- 底部毛玻璃抽屉与控制中心 ----------
    private Dialog showBottomSheetDialog(View contentView) {
        if (currentSheetDialog != null && currentSheetDialog.isShowing()) {
            currentSheetDialog.dismiss();
        }
        Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        View handle = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(theme.textSecondary);
        hg.setCornerRadius(dp(2.5f));
        handle.setBackground(hg);
        handle.setAlpha(0.55f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(40), dp(4.5f));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(12);
        container.addView(handle, hlp);
        container.addView(contentView);
        container.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(container);

        scroll.setBackground(theme.sheetPanelBackground(dpUnit));

        dlg.setContentView(scroll);
        Window w = dlg.getWindow();
        if (w != null) {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int maxH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.82f);
            scroll.measure(View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int winH = scroll.getMeasuredHeight() > maxH ? maxH : ViewGroup.LayoutParams.WRAP_CONTENT;
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, winH);
            w.setDimAmount(0.42f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dlg.show();
        currentSheetDialog = dlg;
        return dlg;
    }

    private TextView makeSheetButton(String label, final Runnable onClick) {
        TextView btn = makeText(label, 12.8f, theme.textPrimary, false);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(theme.sheetTileCard(dpUnit, 15));
        btn.setPadding(dp(10), dp(11.5f), dp(10), dp(11.5f));
        btn.setMaxLines(2);
        btn.setEllipsize(TextUtils.TruncateAt.END);
        btn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClick.run();
        });
        return btn;
    }

    private void addGridButtons(LinearLayout parent, List<TextView> buttons, int cols) {
        LinearLayout row = null;
        for (int i = 0; i < buttons.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(8);
                parent.addView(row, rlp);
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i % cols != cols - 1) blp.rightMargin = dp(8);
            row.addView(buttons.get(i), blp);
        }
        if (row != null && buttons.size() % cols != 0) {
            for (int i = buttons.size() % cols; i < cols; i++) {
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (i != cols - 1) elp.rightMargin = dp(8);
                row.addView(new View(this), elp);
            }
        }
    }

    private void addSheetSectionHeader(LinearLayout parent, String title) {
        TextView tv = makeText(title, 12.5f, theme.accent, true);
        tv.setPadding(dp(4), dp(16), 0, dp(2));
        parent.addView(tv);
    }

    private void applyWallpaperAndThemePreset(final int styleIdx) {
        prefs.edit().putInt(K_THEME_IDX, styleIdx).apply();
        Toast.makeText(this, "已切换为「" + AuroraTheme.SHORT_PALETTE_NAMES[styleIdx] + "」并同步昼夜壁纸", Toast.LENGTH_SHORT).show();
        AppExecutors.getInstance().computation().execute(() -> {
            try {
                Bitmap bmp = AuroraBackdropView.renderAuroraWallpaper(this, styleIdx);
                AuroraBackdropView.saveAndApplySystemWallpaper(this, bmp);
                prefs.edit().putInt(K_WALL_STYLE_IDX, styleIdx).apply();
            } catch (Throwable ignored) {}
        });
        recreate();
    }

    private int resolveStatusBarMode() {
        if (prefs.contains(K_STATUS_BAR_MODE)) {
            return Math.max(HeroStatusBarView.MODE_SMART,
                    Math.min(HeroStatusBarView.MODE_FULL, prefs.getInt(K_STATUS_BAR_MODE, HeroStatusBarView.MODE_SMART)));
        }
        int migrated = HeroStatusBarView.MODE_SMART;
        if (prefs.getBoolean("status_bar_full_v1", false)) {
            migrated = HeroStatusBarView.MODE_FULL;
        } else if (!prefs.getBoolean("smart_battery_only_v1", true)) {
            migrated = HeroStatusBarView.MODE_ALWAYS_BATTERY;
        }
        prefs.edit().putInt(K_STATUS_BAR_MODE, migrated).apply();
        return migrated;
    }

    private void showStatusBarModePickerDialog() {
        int cur = resolveStatusBarMode();
        showSingleChoiceDialog("顶部状态栏信息", HeroStatusBarView.STATUS_MODE_NAMES, cur, idx -> {
            prefs.edit().putInt(K_STATUS_BAR_MODE, idx).apply();
            if (niagaraHeaderView != null) niagaraHeaderView.setStatusBarMode(idx);
            Toast.makeText(MainActivity.this, "已设为：" + HeroStatusBarView.STATUS_MODE_NAMES[idx], Toast.LENGTH_SHORT).show();
        });
    }

    private void openControlCenterSheet() {
        if (currentSheetDialog != null && currentSheetDialog.isShowing()) {
            currentSheetDialog.dismiss();
        }
        currentSheetDialog = SettingsSheetController.show(new SettingsSheetController.Host() {
            @Override public android.content.Context getContext() { return MainActivity.this; }
            @Override public SharedPreferences getPrefs() { return prefs; }
            @Override public AuroraTheme getThemeObj() { return theme; }
            @Override public Typeface getTypeface() { return typeface; }
            @Override public float getDp() { return dpUnit; }
            @Override public float sp(float size) { return MainActivity.this.sp(size); }
            @Override public int getFavoritesCount() { return getStringList(K_FAVS).size(); }
            @Override public int getHiddenCount() { return getStringList(K_HIDDEN).size(); }
            @Override public String getCurrentIconShapeName() {
                int idx = Math.max(0, Math.min(ICON_SHAPES.length - 1, prefs.getInt(K_ICON_SHAPE, 0)));
                return ICON_SHAPES[idx].split(" · ")[0];
            }
            @Override public String getCurrentIconPackName() {
                String pkg = prefs.getString(K_ICON_PACK, "");
                if (pkg == null || pkg.isEmpty()) return "系统默认";
                for (IconPackManager.PackInfo p : IconPackManager.discoverIconPacks(MainActivity.this)) {
                    if (pkg.equals(p.packageName)) return p.label;
                }
                return pkg;
            }
            @Override public String getCurrentFontName() {
                int idx = Math.max(0, Math.min(FONT_NAMES.length - 1, prefs.getInt(K_FONT_IDX, 1)));
                return FONT_NAMES[idx];
            }
            @Override public String getCurrentDensityName() {
                int idx = Math.max(0, Math.min(DENSITY_NAMES.length - 1, prefs.getInt(K_ROW_DENSITY, 1)));
                return DENSITY_NAMES[idx];
            }
            @Override public String getCurrentCoinActionName() {
                int idx = Math.max(0, Math.min(COIN_ACTIONS.length - 1, prefs.getInt(K_COIN_TAP_ACTION, 1)));
                return COIN_ACTIONS[idx];
            }
            @Override public String getCurrentClockStyleName() {
                int idx = Math.max(0, Math.min(NiagaraHeaderView.CLOCK_STYLE_NAMES.length - 1,
                        prefs.getInt(K_CLOCK_STYLE, NiagaraHeaderView.CLOCK_CLASSIC)));
                return NiagaraHeaderView.CLOCK_STYLE_NAMES[idx].split(" · ")[0];
            }
            @Override public String getCurrentSearchEngineName() {
                return MainActivity.this.getCurrentSearchEngineName();
            }
            @Override public int getPinnedWidgetCount() {
                return widgetHelper != null ? widgetHelper.getPinnedWidgets(prefs).size() : 0;
            }
            @Override public void showDeviceStatus() { showDeviceStatusDialog(); }
            @Override public void showPickFavorites() { showPickFavoritesDialog(); }
            @Override public void showReorderFavorites() { showReorderFavoritesDialog(); }
            @Override public void applyThemePreset(int styleIdx) { applyWallpaperAndThemePreset(styleIdx); }
            @Override public void pickGalleryWallpaper() {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(pick, REQ_PICK_WALL); }
                catch (Throwable t) { Toast.makeText(MainActivity.this, "无法打开相册", Toast.LENGTH_SHORT).show(); }
            }
            @Override public void onDimPercentChanged(int pct) {
                prefs.edit().putInt(K_DIM_PCT, pct).apply();
                if (backdropView != null) backdropView.updateDimPercent(pct);
            }
            @Override public void onSyncLockWallpaperChanged(boolean syncLock) {
                prefs.edit().putBoolean("sync_lock_wallpaper_v1", syncLock).apply();
                if (syncLock) {
                    int curTheme = prefs.getInt(K_THEME_IDX, AuroraTheme.IDX_DEFAULT);
                    if (curTheme >= 0 && curTheme <= 5) {
                        applyWallpaperAndThemePreset(curTheme);
                    } else {
                        Toast.makeText(MainActivity.this, "已开启同步锁屏壁纸，切换主题或壁纸时将同步至锁屏", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "已关闭同步锁屏壁纸，当前锁屏将被保留", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFlowAnimChanged(boolean on) {
                prefs.edit().putBoolean(K_FLOW_ANIM, on).apply();
                if (backdropView != null) backdropView.updateAnimateFlow(on);
            }
            @Override public void showIconShapePicker() {
                final int curShape = prefs.getInt(K_ICON_SHAPE, 0);
                showSingleChoiceDialog("选择图标造型", ICON_SHAPES, curShape, idx -> {
                    prefs.edit().putInt(K_ICON_SHAPE, idx).apply();
                    folderIconCache.clear();
                    loadInstalledAppsAsync();
                });
            }
            @Override public void showIconPackPicker() { showIconPackPickerDialog(); }
            @Override public void onShowIconsChanged(boolean on) {
                prefs.edit().putBoolean(K_SHOW_ICONS, on).apply();
                if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
            }
            @Override public void onOneHandGravityChanged(boolean on) {
                prefs.edit().putBoolean(K_ONE_HAND_GRAVITY, on).apply();
                applyOneHandedListPadding();
            }
            @Override public void onSurfaceUnreadHomeChanged(boolean on) {
                prefs.edit().putBoolean(K_SURFACE_UNREAD_HOME, on).apply();
                rebuildStreamRows();
            }
            @Override public void onShowMediaChanged(boolean on) {
                prefs.edit().putBoolean(K_SHOW_MEDIA, on).apply();
                bindActiveMediaSession();
            }
            @Override public void onShowAgendaChanged(boolean on) {
                prefs.edit().putBoolean(K_SHOW_AGENDA, on).apply();
                if (on && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL_PERM);
                } else {
                    refreshCalendarAgenda();
                }
            }
            @Override public void openWidgetManager() { showWidgetManagerDialog(); }
            @Override public void showCoinActionPicker() { showCoinActionPickerDialog(); }
            @Override public void showFontPicker() {
                final int curFont = prefs.getInt(K_FONT_IDX, 1);
                showSingleChoiceDialog("选择字体风格", FONT_NAMES, curFont, idx -> {
                    prefs.edit().putInt(K_FONT_IDX, idx).apply();
                    recreate();
                });
            }
            @Override public void onTextScaleChanged(float scale) {
                textScale = scale;
                prefs.edit().putFloat(K_TEXT_SCALE, scale).apply();
                if (niagaraHeaderView != null) niagaraHeaderView.applyTextScale(scale);
                if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
                applyOneHandedListPadding();
            }
            @Override public void onUse24HChanged(boolean use24h) {
                prefs.edit().putBoolean(K_USE_24H, use24h).apply();
                if (niagaraHeaderView != null) niagaraHeaderView.setUse24Hour(use24h);
            }
            @Override public void showClockStylePicker() {
                showClockStylePickerDialog();
            }
            @Override public String getCurrentStatusBarModeName() {
                return HeroStatusBarView.STATUS_MODE_NAMES[resolveStatusBarMode()].split("（")[0];
            }
            @Override public void showStatusBarModePicker() {
                showStatusBarModePickerDialog();
            }
            @Override public void onFavCardsChanged(boolean on) {
                prefs.edit().putBoolean(K_FAV_CARDS, on).apply();
                if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
            }
            @Override public void showDensityPicker() {
                final int curDensity = prefs.getInt(K_ROW_DENSITY, 1);
                showSingleChoiceDialog("列表疏密程度", DENSITY_NAMES, curDensity, idx -> {
                    prefs.edit().putInt(K_ROW_DENSITY, idx).apply();
                    if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
                    applyOneHandedListPadding();
                });
            }
            @Override public void onPullDownSearchChanged(boolean on) {
                prefs.edit().putBoolean(K_PULL_DOWN_SEARCH, on).apply();
            }
            @Override public void onWaveIdleDotsChanged(boolean dots) {
                prefs.edit().putBoolean(K_WAVE_IDLE_DOTS, dots).apply();
                if (rightWaveBar != null) rightWaveBar.setIdleDotsMode(dots);
                if (leftGhostWaveBar != null) leftGhostWaveBar.setIdleDotsMode(dots);
            }
            @Override public void onWaveAdaptiveAlphaChanged(boolean adaptive) {
                prefs.edit().putBoolean(K_WAVE_ADAPTIVE_ALPHA, adaptive).apply();
                syncWaveBarSymbolsAndData(new HashSet<>(getStringList(K_HIDDEN)));
            }
            @Override public void showSearchEnginePicker() {
                showSearchEnginePickerDialog();
            }
            @Override public void onCoinLeftChanged(boolean left) {
                prefs.edit().putBoolean(K_COIN_LEFT, left).apply();
                if (coinButton != null) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) coinButton.getLayoutParams();
                    lp.gravity = Gravity.BOTTOM | (left ? Gravity.START : Gravity.END);
                    lp.leftMargin = left ? dp(22) : 0;
                    lp.rightMargin = left ? 0 : dp(18);
                    coinButton.setLayoutParams(lp);
                }
            }
            @Override public void onPullLaunchChanged(boolean on) {
                prefs.edit().putBoolean(K_PULL_LAUNCH, on).apply();
                if (rightWaveBar != null) rightWaveBar.setPullLaunchEnabled(on);
                if (leftGhostWaveBar != null) leftGhostWaveBar.setPullLaunchEnabled(on);
            }
            @Override public void onDoubleTapLockChanged(boolean on) {
                prefs.edit().putBoolean(K_DOUBLE_TAP_LOCK, on).apply();
            }
            @Override public void onInlineNotifChanged(boolean on) {
                prefs.edit().putBoolean(K_INLINE_NOTIF, on).apply();
                if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
            }
            @Override public void onKeepAliveChanged(boolean on) {
                prefs.edit().putBoolean(K_KEEP_ALIVE, on).apply();
                if (on) KeepAliveService.startServiceSafely(MainActivity.this);
                else KeepAliveService.stopServiceSafely(MainActivity.this);
            }
            @Override public void onBiometricHideChanged(boolean on) {
                prefs.edit().putBoolean(K_BIOMETRIC_HIDE, on).apply();
            }
            @Override public void openStickyNoteEditor() { promptEditStickyNote(); }
            @Override public void openCreateFolder() { promptCreateFolder(null); }
            @Override public void openHiddenApps() { authenticateThen(MainActivity.this::showHiddenAppsDialog); }
            @Override public void exportBackup() {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "niagara-local-backup.json");
                try { startActivityForResult(i, REQ_EXPORT_JSON); }
                catch (Throwable t) { Toast.makeText(MainActivity.this, "无法打开文件保存界面", Toast.LENGTH_SHORT).show(); }
            }
            @Override public void importBackup() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*");
                try { startActivityForResult(i, REQ_IMPORT_JSON); }
                catch (Throwable t) { Toast.makeText(MainActivity.this, "无法打开文件选择界面", Toast.LENGTH_SHORT).show(); }
            }
            @Override public void openHomeSettings() {
                startIntentSafely(new Intent(Settings.ACTION_HOME_SETTINGS));
            }
            @Override public void openBatteryOptimizationSettings() {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                if (!startIntentSafely(i)) {
                    startIntentSafely(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            }
            @Override public void openAppDetailsSettings() {
                startIntentSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
            @Override public void openAccessibilitySettings() {
                String cn = new ComponentName(MainActivity.this, GestureLockService.class).flattenToString();
                Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, cn);
                if (!startIntentSafely(i)) startIntentSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            @Override public void openNotificationListenerSettings() {
                startIntentSafely(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });
    }

    // ---------- 全系毛玻璃弹窗与选择器（彻底废除原生黑框 AlertDialog） ----------
    interface IntSelectCallback { void onSelect(int idx); }

    private void showIconPackPickerDialog() {
        List<IconPackManager.PackInfo> packs = IconPackManager.discoverIconPacks(this);
        String curPkg = prefs.getString(K_ICON_PACK, "");
        String[] labels = new String[packs.size() + 1];
        final String[] pkgs = new String[packs.size() + 1];
        labels[0] = "系统默认（应用自带图标 + 造型重塑）";
        pkgs[0] = "";
        int sel = 0;
        for (int i = 0; i < packs.size(); i++) {
            IconPackManager.PackInfo p = packs.get(i);
            labels[i + 1] = p.label + "  (" + p.packageName + ")";
            pkgs[i + 1] = p.packageName;
            if (p.packageName.equals(curPkg)) sel = i + 1;
        }
        if (packs.isEmpty()) {
            Toast.makeText(this, "未检测到已安装的第三方图标包 APK（支持 Nova / ADW 规范图标包）", Toast.LENGTH_LONG).show();
        }
        showSingleChoiceDialog("选择第三方图标包", labels, sel, idx -> {
            String chosen = pkgs[idx];
            prefs.edit().putString(K_ICON_PACK, chosen).apply();
            IconPackManager.loadPack(MainActivity.this, chosen);
            folderIconCache.clear();
            loadInstalledAppsAsync();
            Toast.makeText(MainActivity.this, "已应用图标包：" + labels[idx].split("  \\(")[0], Toast.LENGTH_SHORT).show();
        });
    }

    private void showCustomAppIconDialog(final AppEntry app) {
        String activePkg = prefs.getString(K_ICON_PACK, "");
        if (activePkg == null || activePkg.isEmpty()) {
            List<IconPackManager.PackInfo> packs = IconPackManager.discoverIconPacks(this);
            if (packs.isEmpty()) {
                Toast.makeText(this, "请先安装并启用一个第三方图标包，或在设置中切换「图标造型 / Niagara Dots」", Toast.LENGTH_LONG).show();
                return;
            }
            activePkg = packs.get(0).packageName;
        }
        final String packPkg = activePkg;
        IconPackManager.loadPack(this, packPkg);
        List<String> names = IconPackManager.getSampleDrawableNames(48);

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("为「" + app.label + "」自定义图标", 17.5f, theme.textPrimary, true));
        box.addView(makeText("来自图标包：" + packPkg, 12f, theme.textSecondary, false));

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("恢复默认图标", () -> {
            try {
                JSONObject ci = getCustomIconsJson();
                ci.remove(app.idKey);
                prefs.edit().putString(K_CUSTOM_ICONS, ci.toString()).apply();
                invalidateCustomIconsCache();
                app.shapedIcon = null;
                if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
            } catch (Throwable ignored) {}
            dismiss.run();
        }));
        for (final String dName : names) {
            btns.add(makeSheetButton(dName, () -> {
                try {
                    JSONObject ci = getCustomIconsJson();
                    ci.put(app.idKey, packPkg + "|" + dName);
                    prefs.edit().putString(K_CUSTOM_ICONS, ci.toString()).apply();
                    invalidateCustomIconsCache();
                    app.shapedIcon = null;
                    if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
                    Toast.makeText(MainActivity.this, "已更换图标：" + dName, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
                dismiss.run();
            }));
        }
        addGridButtons(box, btns, 2);
        holder[0] = showBottomSheetDialog(box);
    }

    private void showWidgetManagerDialog() {
        if (widgetHelper == null) return;
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("首页原生桌面微件 (Widgets)", 18f, theme.textPrimary, true));
        TextView tip = makeText("可添加系统时钟、天气、便签、待办等原生小组件到首页顶部，长按微件可调高度或移除。", 12.2f, theme.textSecondary, false);
        tip.setPadding(0, dp(3), 0, dp(10));
        box.addView(tip);

        List<WidgetHostHelper.PinnedWidget> pinned = widgetHelper.getPinnedWidgets(prefs);
        if (!pinned.isEmpty()) {
            addSheetSectionHeader(box, "已添加到首页 (" + pinned.size() + ")");
            List<TextView> remBtns = new ArrayList<>();
            for (final WidgetHostHelper.PinnedWidget pw : pinned) {
                remBtns.add(makeSheetButton("移除微件 #" + pw.id + " (" + pw.heightDp + "dp)", () -> {
                    widgetHelper.removeWidget(prefs, pw.id);
                    refreshHomeWidgets();
                    dismiss.run();
                    showWidgetManagerDialog();
                }));
            }
            addGridButtons(box, remBtns, 1);
        }

        addSheetSectionHeader(box, "选择要添加的系统微件");
        final List<AppWidgetProviderInfo> providers = widgetHelper.getAllProviders();
        final PackageManager pm = getPackageManager();
        List<TextView> pBtns = new ArrayList<>();
        int cap = 0;
        for (final AppWidgetProviderInfo info : providers) {
            if (info == null || info.provider == null) continue;
            CharSequence lb = info.loadLabel(pm);
            String title = (lb != null && lb.length() > 0) ? lb.toString().trim() : info.provider.getShortClassName();
            pBtns.add(makeSheetButton(title, () -> {
                dismiss.run();
                widgetHelper.requestAddWidget(MainActivity.this, prefs, info, () -> {
                    refreshHomeWidgets();
                    Toast.makeText(MainActivity.this, "已添加微件到首页顶部", Toast.LENGTH_SHORT).show();
                });
            }));
            if (++cap >= 36) break;
        }
        if (pBtns.isEmpty()) {
            box.addView(makeText("当前设备未发现可用的第三方桌面微件", 13f, theme.textSecondary, false));
        } else {
            addGridButtons(box, pBtns, 2);
        }
        holder[0] = showBottomSheetDialog(box);
    }

    private void showCoinActionPickerDialog() {
        int cur = Math.max(0, Math.min(COIN_ACTIONS.length - 1, prefs.getInt(K_COIN_TAP_ACTION, 1)));
        showSingleChoiceDialog("悬浮按钮轻点动作", COIN_ACTIONS, cur, idx -> {
            prefs.edit().putInt(K_COIN_TAP_ACTION, idx).apply();
            applyCoinButtonConfig();
            Toast.makeText(MainActivity.this, "已设为：" + COIN_ACTIONS[idx], Toast.LENGTH_SHORT).show();
        });
    }

    private void showClockStylePickerDialog() {
        int cur = Math.max(0, Math.min(NiagaraHeaderView.CLOCK_STYLE_NAMES.length - 1,
                prefs.getInt(K_CLOCK_STYLE, NiagaraHeaderView.CLOCK_CLASSIC)));
        showSingleChoiceDialog("时钟排版风格", NiagaraHeaderView.CLOCK_STYLE_NAMES, cur, idx -> {
            prefs.edit().putInt(K_CLOCK_STYLE, idx).apply();
            if (niagaraHeaderView != null) niagaraHeaderView.applyClockStyle(idx);
            applyOneHandedListPadding();
            Toast.makeText(MainActivity.this, "已切换为：" + NiagaraHeaderView.CLOCK_STYLE_NAMES[idx], Toast.LENGTH_SHORT).show();
        });
    }

    private void showSearchEnginePickerDialog() {
        int cur = Math.max(0, Math.min(SEARCH_ENGINE_NAMES.length - 1, prefs.getInt(K_SEARCH_ENGINE, 0)));
        showSingleChoiceDialog("默认网页搜索引擎", SEARCH_ENGINE_NAMES, cur, idx -> {
            prefs.edit().putInt(K_SEARCH_ENGINE, idx).apply();
            if (searchActive) rebuildStreamRows();
            Toast.makeText(MainActivity.this, "已设为：" + SEARCH_ENGINE_NAMES[idx], Toast.LENGTH_SHORT).show();
        });
    }

    private void showSingleChoiceDialog(String title, String[] items, int selected, final IntSelectCallback cb) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText(title, 17.5f, theme.textPrimary, true));
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            boolean sel = (i == selected);
            TextView rowBtn = makeText((sel ? "✓  " : "    ") + items[i], 14.2f, sel ? theme.accent : theme.textPrimary, sel);
            rowBtn.setBackground(sel ? theme.accentGlowPill(dpUnit, 14) : theme.sheetTileCard(dpUnit, 14));
            rowBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
            rowBtn.setOnClickListener(v -> {
                dismiss.run();
                cb.onSelect(idx);
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(7);
            box.addView(rowBtn, rlp);
        }
        holder[0] = showBottomSheetDialog(box);
    }

    private AppPickerSheet.Host appPickerHost() {
        return new AppPickerSheet.Host() {
            @Override public Context getContext() { return MainActivity.this; }
            @Override public AuroraTheme getThemeObj() { return theme; }
            @Override public Typeface getTypeface() { return typeface; }
            @Override public float getDp() { return dpUnit; }
            @Override public float sp(float size) { return MainActivity.this.sp(size); }
        };
    }

    private void showPickFavoritesDialog() {
        final Set<String> hidden = new HashSet<>(getStringList(K_HIDDEN));
        final List<AppEntry> candidates = new ArrayList<>();
        for (AppEntry a : allApps) if (!hidden.contains(a.idKey)) candidates.add(a);
        final List<String> curFavs = getStringList(K_FAVS);

        currentSheetDialog = AppPickerSheet.showMultiSelect(
                appPickerHost(),
                "勾选第一页常用应用",
                "点击下方字母栏快速筛选，或直接输入拼音查找",
                candidates,
                curFavs,
                "调整顺序",
                this::showReorderFavoritesDialog,
                "保存常用",
                updated -> {
                    putStringList(K_FAVS, updated);
                    inAllAppsMode = false;
                    updateFirstPageExtrasVisibility();
                    rebuildStreamRows();
                    Toast.makeText(MainActivity.this, "已更新常用应用（共 " + updated.size() + " 个）", Toast.LENGTH_SHORT).show();
                });
    }

    private void showReorderFavoritesDialog() {
        final List<String> favs = getStringList(K_FAVS);
        currentSheetDialog = AppPickerSheet.showDragReorder(
                appPickerHost(),
                "调整常用应用顺序",
                "按住左侧手柄上下拖拽，或点击右侧箭头调整顺序",
                favs,
                allApps,
                ordered -> {
                    putStringList(K_FAVS, ordered);
                    rebuildStreamRows();
                });
    }

    // ---------- App Pop-up 关联子应用集合 ----------
    private JSONObject getAppPopupsJson() {
        try { return new JSONObject(prefs.getString(K_APP_POPUPS, "{}")); }
        catch (Throwable t) { return new JSONObject(); }
    }

    private void invalidateAppPopupsCache() {
        appPopupsCache = null;
    }

    private List<String> getAppPopupChildKeysCached(String parentKey) {
        if (parentKey == null || parentKey.isEmpty()) return Collections.emptyList();
        if (appPopupsCache == null) {
            Map<String, List<String>> map = new HashMap<>();
            JSONObject obj = getAppPopupsJson();
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String pk = it.next();
                JSONArray arr = obj.optJSONArray(pk);
                if (arr == null || arr.length() == 0) continue;
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String ck = arr.optString(i, "");
                    if (!ck.isEmpty() && !ck.equals(pk) && !list.contains(ck)) {
                        list.add(ck);
                    }
                }
                if (!list.isEmpty()) map.put(pk, list);
            }
            appPopupsCache = map;
        }
        List<String> keys = appPopupsCache.get(parentKey);
        return keys != null ? keys : Collections.emptyList();
    }

    private boolean hasAppPopupChildren(String parentKey) {
        List<String> keys = getAppPopupChildKeysCached(parentKey);
        if (keys.isEmpty()) return false;
        for (String k : keys) {
            if (findAppByKey(k) != null) return true;
        }
        return false;
    }

    private List<AppEntry> getAppPopupChildren(AppEntry parent) {
        if (parent == null) return Collections.emptyList();
        List<String> keys = getAppPopupChildKeysCached(parent.idKey);
        if (keys.isEmpty()) return Collections.emptyList();
        List<AppEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String k : keys) {
            if (k.equals(parent.idKey) || !seen.add(k)) continue;
            AppEntry a = findAppByKey(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    private void removeAppPopupChild(AppEntry parent, AppEntry child) {
        if (parent == null || child == null) return;
        try {
            JSONObject obj = getAppPopupsJson();
            JSONArray cur = obj.optJSONArray(parent.idKey);
            if (cur == null) return;
            JSONArray next = new JSONArray();
            for (int i = 0; i < cur.length(); i++) {
                String k = cur.optString(i, "");
                if (!k.isEmpty() && !k.equals(child.idKey)) next.put(k);
            }
            if (next.length() == 0) obj.remove(parent.idKey);
            else obj.put(parent.idKey, next);
            prefs.edit().putString(K_APP_POPUPS, obj.toString()).apply();
            invalidateAppPopupsCache();
            if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
        } catch (Throwable ignored) {}
    }

    private void promptEditAppPopupChildren(final AppEntry parent) {
        if (parent == null) return;
        final List<AppEntry> candidates = new ArrayList<>();
        for (AppEntry a : allApps) {
            if (a == virtualSettingsApp || a.idKey.equals(parent.idKey)) continue;
            candidates.add(a);
        }
        final List<String> curKeys = new ArrayList<>(getAppPopupChildKeysCached(parent.idKey));
        currentSheetDialog = AppPickerSheet.showMultiSelect(
                appPickerHost(),
                "管理「" + parent.label + "」Pop-up",
                "在首页对「" + parent.label + "」右滑即可原地展开所选关联应用",
                candidates,
                curKeys,
                "取消",
                null,
                "保存关联",
                updated -> {
                    try {
                        JSONObject obj = getAppPopupsJson();
                        JSONArray next = new JSONArray();
                        for (String k : updated) next.put(k);
                        if (next.length() == 0) obj.remove(parent.idKey);
                        else obj.put(parent.idKey, next);
                        prefs.edit().putString(K_APP_POPUPS, obj.toString()).apply();
                        invalidateAppPopupsCache();
                        if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this,
                                next.length() == 0
                                        ? "已清空「" + parent.label + "」关联 Pop-up"
                                        : "已更新「" + parent.label + "」关联 Pop-up（" + next.length() + " 个应用）",
                                Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {}
                });
    }

    // ---------- 文件夹管理 ----------
    private JSONArray getFoldersArray() {
        try { return new JSONArray(prefs.getString(K_FOLDERS, "[]")); }
        catch (Throwable t) { return new JSONArray(); }
    }

    private void saveFoldersArray(JSONArray arr) {
        prefs.edit().putString(K_FOLDERS, arr.toString()).apply();
        folderIconCache.clear();
        rebuildStreamRows();
    }

    private void promptCreateFolder(final AppEntry initialApp) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("新建弹出式文件夹", 18f, theme.textPrimary, true));
        TextView tip = makeText("输入文件夹名称（如：高频办公、金融支付、影音娱乐）", 12.2f, theme.textSecondary, false);
        tip.setPadding(0, dp(3), 0, dp(10));
        box.addView(tip);

        final EditText input = new EditText(this);
        styleGlassEditText(input, "例如：金融支付 / 常用工具");
        input.setSingleLine(true);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("取消", dismiss));
        TextView createBtn = makeSheetButton("创建文件夹", () -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            dismiss.run();
            try {
                JSONArray arr = getFoldersArray();
                JSONObject fo = new JSONObject();
                fo.put("name", name);
                JSONArray aList = new JSONArray();
                if (initialApp != null) aList.put(initialApp.idKey);
                fo.put("apps", aList);
                arr.put(fo);
                saveFoldersArray(arr);
                if (initialApp == null) promptSelectFolderApps(arr.length() - 1);
            } catch (Throwable ignored) {}
        });
        createBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        createBtn.setTextColor(0xFFFFFFFF);
        btns.add(createBtn);
        addGridButtons(box, btns, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    private void promptSelectFolderApps(final int folderIdx) {
        final JSONArray arr = getFoldersArray();
        final JSONObject fo = arr.optJSONObject(folderIdx);
        if (fo == null) return;
        final List<AppEntry> candidates = new ArrayList<>();
        for (AppEntry a : allApps) {
            if (a != virtualSettingsApp) candidates.add(a);
        }
        final List<String> curOrdered = new ArrayList<>();
        JSONArray cur = fo.optJSONArray("apps");
        if (cur != null) {
            for (int i = 0; i < cur.length(); i++) {
                String k = cur.optString(i, "");
                if (!k.isEmpty() && !curOrdered.contains(k)) curOrdered.add(k);
            }
        }
        currentSheetDialog = AppPickerSheet.showMultiSelect(
                appPickerHost(),
                "勾选「" + fo.optString("name") + "」内应用",
                "按字母或名称快速筛选",
                candidates,
                curOrdered,
                "取消",
                null,
                "保存文件夹",
                updated -> {
                    try {
                        JSONArray next = new JSONArray();
                        for (String k : updated) next.put(k);
                        fo.put("apps", next);
                        saveFoldersArray(arr);
                    } catch (Throwable ignored) {}
                });
    }

    private void promptRenameFolder(final int folderIdx) {
        final JSONArray arr = getFoldersArray();
        final JSONObject fo = arr.optJSONObject(folderIdx);
        if (fo == null) return;
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("重命名文件夹", 18f, theme.textPrimary, true));
        final EditText input = new EditText(this);
        styleGlassEditText(input, "输入新的文件夹名称");
        input.setText(fo.optString("name", "文件夹"));
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(10);
        box.addView(input, ilp);

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("取消", dismiss));
        TextView saveBtn = makeSheetButton("保存名称", () -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            try {
                fo.put("name", name);
                saveFoldersArray(arr);
            } catch (Throwable ignored) {}
            dismiss.run();
        });
        saveBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        btns.add(saveBtn);
        addGridButtons(box, btns, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    private void promptReorderFolderApps(final int folderIdx) {
        final JSONArray arr = getFoldersArray();
        final JSONObject fo = arr.optJSONObject(folderIdx);
        if (fo == null) return;
        final List<String> keys = new ArrayList<>();
        JSONArray cur = fo.optJSONArray("apps");
        if (cur != null) {
            for (int i = 0; i < cur.length(); i++) {
                String k = cur.optString(i, "");
                if (!k.isEmpty() && !keys.contains(k)) keys.add(k);
            }
        }
        currentSheetDialog = AppPickerSheet.showDragReorder(
                appPickerHost(),
                "调整「" + fo.optString("name", "文件夹") + "」内应用顺序",
                "按住左侧手柄上下拖拽，或点击右侧箭头调整顺序（首位将用于点击直开）",
                keys,
                allApps,
                ordered -> {
                    try {
                        JSONArray next = new JSONArray();
                        for (String k : ordered) next.put(k);
                        fo.put("apps", next);
                        saveFoldersArray(arr);
                    } catch (Throwable ignored) {}
                });
    }

    private boolean toggleFolderTapLaunchFirst(int folderIdx) {
        JSONArray arr = getFoldersArray();
        JSONObject fo = arr.optJSONObject(folderIdx);
        if (fo == null) return false;
        boolean next = !fo.optBoolean("tapLaunchFirst", false);
        try {
            fo.put("tapLaunchFirst", next);
            saveFoldersArray(arr);
        } catch (Throwable ignored) {}
        return next;
    }

    private void promptAddAppToFolder(final AppEntry app) {
        final JSONArray arr = getFoldersArray();
        String[] items = new String[arr.length() + 1];
        for (int i = 0; i < arr.length(); i++) items[i] = arr.optJSONObject(i).optString("name", "文件夹");
        items[arr.length()] = "＋ 新建弹出式文件夹…";
        showSingleChoiceDialog("将「" + app.label + "」加入文件夹", items, 0, w -> {
            if (w == arr.length()) {
                promptCreateFolder(app);
                return;
            }
            try {
                JSONObject fo = arr.getJSONObject(w);
                JSONArray aList = fo.optJSONArray("apps");
                if (aList == null) { aList = new JSONArray(); fo.put("apps", aList); }
                for (int i = 0; i < aList.length(); i++) {
                    if (app.idKey.equals(aList.optString(i))) {
                        Toast.makeText(this, "「" + app.label + "」已在该文件夹中", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                aList.put(app.idKey);
                saveFoldersArray(arr);
                Toast.makeText(this, "已加入「" + fo.optString("name") + "」", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        });
    }

    // ---------- 音乐控制与日程查询 ----------
    private MediaSessionManager mediaSessionMgr;
    private MediaController activeMediaController;
    private boolean mediaListening = false;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionChangeListener = l -> uiHandler.post(this::bindActiveMediaSession);
    private final MediaController.Callback mediaControllerCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { updateMediaCardUi(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { updateMediaCardUi(); }
        @Override public void onSessionDestroyed() { uiHandler.post(MainActivity.this::bindActiveMediaSession); }
    };
    private final Runnable mediaProgressTicker = this::updateMediaCardUi;

    private void bindActiveMediaSession() {
        if (niagaraHeaderView == null) return;
        if (!prefs.getBoolean(K_SHOW_MEDIA, true)) {
            niagaraHeaderView.updateMedia(null, null, null, false, 0L, 0L);
            applyOneHandedListPadding();
            return;
        }
        ComponentName cn = new ComponentName(this, NotificationHubService.class);
        try {
            if (mediaSessionMgr == null) mediaSessionMgr = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> list = mediaSessionMgr.getActiveSessions(cn);
            if (!mediaListening) {
                mediaSessionMgr.addOnActiveSessionsChangedListener(sessionChangeListener, cn, uiHandler);
                mediaListening = true;
            }
            MediaController chosen = null;
            for (MediaController mc : list) {
                if (mc.getMetadata() == null) continue;
                if (chosen == null) chosen = mc;
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    chosen = mc;
                    break;
                }
            }
            if (activeMediaController != chosen) {
                if (activeMediaController != null) try { activeMediaController.unregisterCallback(mediaControllerCb); } catch (Throwable ignored) {}
                if (chosen != null) chosen.registerCallback(mediaControllerCb, uiHandler);
                activeMediaController = chosen;
            }
            updateMediaCardUi();
        } catch (Throwable t) {
            niagaraHeaderView.updateMedia(null, null, null, false, 0L, 0L);
            applyOneHandedListPadding();
        }
    }

    private void updateMediaCardUi() {
        uiHandler.removeCallbacks(mediaProgressTicker);
        if (niagaraHeaderView == null) return;
        boolean wasVisible = niagaraHeaderView.isMediaVisible();
        MediaMetadata meta = activeMediaController == null ? null : activeMediaController.getMetadata();
        if (meta == null || TextUtils.isEmpty(meta.getString(MediaMetadata.METADATA_KEY_TITLE))) {
            niagaraHeaderView.updateMedia(null, null, null, false, 0L, 0L);
            if (wasVisible) applyOneHandedListPadding();
            return;
        }
        String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        PlaybackState st = activeMediaController.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        long dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = st == null ? 0 : st.getPosition();
        if (playing && st != null && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        niagaraHeaderView.updateMedia(art, title, artist, playing, pos, dur);
        if (!wasVisible) applyOneHandedListPadding();
        if (playing && isResumed && dur > 0) uiHandler.postDelayed(mediaProgressTicker, 1000);
    }

    private void triggerMediaTransport(int which) {
        if (activeMediaController == null) return;
        MediaController.TransportControls tc = activeMediaController.getTransportControls();
        PlaybackState st = activeMediaController.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        if (which == 0) tc.skipToPrevious();
        else if (which == 2) tc.skipToNext();
        else if (playing) tc.pause();
        else tc.play();
    }

    private void launchMediaPlayerApp() {
        if (activeMediaController == null) return;
        PendingIntent pi = activeMediaController.getSessionActivity();
        if (pi != null && NotificationHubService.sendPendingIntentCompat(this, pi, null, null)) {
            return;
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(activeMediaController.getPackageName());
        if (i != null) startIntentSafely(i);
    }

    private long nextEventId = -1, nextEventStart, nextEventEnd;

    private void refreshCalendarAgenda() {
        if (niagaraHeaderView == null) return;
        if (!prefs.getBoolean(K_SHOW_AGENDA, false)
                || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            niagaraHeaderView.updateAgenda(null);
            applyOneHandedListPadding();
            return;
        }
        AppExecutors.getInstance().computation().execute(() -> {
            String summary = null;
            long eid = -1, b0 = 0, e0 = 0;
            Cursor c = null;
            try {
                long now = System.currentTimeMillis();
                Uri.Builder ub = CalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(ub, now);
                ContentUris.appendId(ub, now + 48L * 3600 * 1000);
                c = getContentResolver().query(ub.build(),
                        new String[]{CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END},
                        CalendarContract.Instances.END + ">?", new String[]{String.valueOf(now)},
                        CalendarContract.Instances.BEGIN + " ASC");
                if (c != null && c.moveToFirst()) {
                    eid = c.getLong(0);
                    String t = c.getString(1);
                    b0 = c.getLong(2);
                    e0 = c.getLong(3);
                    SimpleDateFormat hm = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA);
                    summary = (TextUtils.isEmpty(t) ? "未命名日程" : t) + " · " + hm.format(new Date(b0));
                }
            } catch (Throwable ignored) {
            } finally {
                if (c != null) c.close();
            }
            final String fSum = summary;
            final long fId = eid, fb = b0, fe = e0;
            uiHandler.post(() -> {
                nextEventId = fId;
                nextEventStart = fb;
                nextEventEnd = fe;
                if (niagaraHeaderView != null) {
                    niagaraHeaderView.updateAgenda(fSum);
                    applyOneHandedListPadding();
                }
            });
        });
    }

    private void openActiveCalendarEvent() {
        if (nextEventId < 0) return;
        Intent i = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, nextEventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, nextEventStart)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, nextEventEnd);
        startIntentSafely(i);
    }

    // ---------- 辅助工具方法 ----------
    private final Runnable debouncedReloadAppsTask = this::loadInstalledAppsAsync;

    private final LauncherApps.Callback pkgChangeCallback = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String p, UserHandle u) {
            uiHandler.removeCallbacks(debouncedReloadAppsTask);
            uiHandler.postDelayed(debouncedReloadAppsTask, 500L);
        }
        @Override public void onPackageAdded(String p, UserHandle u) {
            uiHandler.removeCallbacks(debouncedReloadAppsTask);
            uiHandler.postDelayed(debouncedReloadAppsTask, 500L);
        }
        @Override public void onPackageChanged(String p, UserHandle u) {
            uiHandler.removeCallbacks(debouncedReloadAppsTask);
            uiHandler.postDelayed(debouncedReloadAppsTask, 500L);
        }
        @Override public void onPackagesAvailable(String[] p, UserHandle u, boolean r) {
            uiHandler.removeCallbacks(debouncedReloadAppsTask);
            uiHandler.postDelayed(debouncedReloadAppsTask, 500L);
        }
        @Override public void onPackagesUnavailable(String[] p, UserHandle u, boolean r) {
            uiHandler.removeCallbacks(debouncedReloadAppsTask);
            uiHandler.postDelayed(debouncedReloadAppsTask, 500L);
        }
        @Override public void onShortcutsChanged(String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {
            // 🚀 核心流畅度优化（120Hz 满帧回弹）：
            // 外部应用（如微信、音乐等）退回后台时系统会频繁发送动态快捷方式更新广播。
            // 严禁在此处立即执行全量 loadInstalledAppsAsync！
            // 只有当存在用户固定在桌面的快捷方式 (isPinned) 发生实质变更，才在 1500ms 后静默异步更新，
            // 彻底杜绝退出应用返回桌面时因全局重扫、排序与视图重建导致的掉帧与卡顿！
            if (shortcuts == null || shortcuts.isEmpty()) return;
            boolean hasPinned = false;
            for (ShortcutInfo s : shortcuts) {
                if (s != null && s.isPinned()) {
                    hasPinned = true;
                    break;
                }
            }
            if (hasPinned) {
                uiHandler.removeCallbacks(debouncedReloadAppsTask);
                uiHandler.postDelayed(debouncedReloadAppsTask, 1500L);
            }
        }
    };

    private List<ShortcutInfo> queryAppShortcuts(AppEntry app) {
        // API 25+ 才有 LauncherApps.getShortcuts；非默认桌面 / 权限收回时抛 SecurityException，统一降级为空列表
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || launcherApps == null
                || app == null || app.component == null) {
            return Collections.emptyList();
        }
        try {
            if (!launcherApps.hasShortcutHostPermission()) return Collections.emptyList();
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(app.component.getPackageName());
            q.setActivity(app.component);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> res = launcherApps.getShortcuts(q, app.user != null ? app.user : Process.myUserHandle());
            return res == null ? Collections.<ShortcutInfo>emptyList() : res;
        } catch (SecurityException | NoSuchMethodError e) {
            return Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private void promptRenameApp(final AppEntry app) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("自定义应用名称", 18f, theme.textPrimary, true));
        TextView tip = makeText("原名：" + app.rawLabel + "（留空保存即可恢复默认名称）", 12.2f, theme.textSecondary, false);
        tip.setPadding(0, dp(3), 0, dp(10));
        box.addView(tip);

        final EditText input = new EditText(this);
        styleGlassEditText(input, app.rawLabel);
        input.setText(getAliasesJson().optString(app.idKey, ""));
        input.setSingleLine(true);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("恢复原名", () -> {
            try {
                JSONObject aliases = getAliasesJson();
                aliases.remove(app.idKey);
                prefs.edit().putString(K_ALIASES, aliases.toString()).apply();
                loadInstalledAppsAsync();
            } catch (Throwable ignored) {}
            dismiss.run();
        }));
        TextView saveBtn = makeSheetButton("保存名称", () -> {
            try {
                JSONObject aliases = getAliasesJson();
                String val = input.getText().toString().trim();
                if (val.isEmpty()) aliases.remove(app.idKey);
                else aliases.put(app.idKey, val);
                prefs.edit().putString(K_ALIASES, aliases.toString()).apply();
                loadInstalledAppsAsync();
            } catch (Throwable ignored) {}
            dismiss.run();
        });
        saveBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        btns.add(saveBtn);
        addGridButtons(box, btns, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    private Runnable pendingAuthTask;

    private void authenticateThen(final Runnable task) {
        if (!prefs.getBoolean(K_BIOMETRIC_HIDE, false)) {
            task.run();
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            task.run();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricPrompt bp = new BiometricPrompt.Builder(this)
                    .setTitle("验证机主身份")
                    .setSubtitle("查看与管理已隐藏的应用")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            bp.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    task.run();
                }
            });
        } else {
            Intent i = km.createConfirmDeviceCredentialIntent("验证身份", "查看已隐藏的应用");
            if (i == null) { task.run(); return; }
            pendingAuthTask = task;
            startActivityForResult(i, REQ_CRED_UNLOCK);
        }
    }

    private void showHiddenAppsDialog() {
        final List<String> hidden = getStringList(K_HIDDEN);
        if (hidden.isEmpty()) {
            Toast.makeText(this, "当前没有隐藏的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[hidden.size()];
        for (int i = 0; i < hidden.size(); i++) {
            AppEntry a = findAppByKey(hidden.get(i));
            names[i] = a != null ? a.label : hidden.get(i);
        }
        showSingleChoiceDialog("已隐藏的应用（点选恢复显示到桌面）", names, 0, w -> {
            final String k = hidden.get(w);
            hidden.remove(k);
            putStringList(K_HIDDEN, hidden);
            rebuildStreamRows();
            Toast.makeText(this, "已恢复显示：" + names[w], Toast.LENGTH_SHORT).show();
        });
    }

    private File appCacheFile() {
        return new File(getFilesDir(), "niagara_apps_cache.json");
    }

    private void writeDiskAppCache() {
        final List<AppEntry> copy = new ArrayList<>(allApps);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (AppEntry a : copy) {
                    if (a.component == null) continue;
                    JSONArray item = new JSONArray();
                    item.put(a.component.flattenToString()).put(a.userSerial).put(a.rawLabel);
                    arr.put(item);
                }
                try (OutputStream out = new FileOutputStream(appCacheFile())) {
                    out.write(arr.toString().getBytes("UTF-8"));
                }
            } catch (Throwable ignored) {}
        });
    }

    private void readDiskAppCache() {
        try {
            File f = appCacheFile();
            if (!f.exists()) return;
            byte[] buf = new byte[(int) f.length()];
            try (InputStream in = new FileInputStream(f)) {
                int off = 0, n;
                while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            }
            JSONArray arr = new JSONArray(new String(buf, "UTF-8"));
            List<AppEntry> cached = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONArray r = arr.getJSONArray(i);
                ComponentName cn = ComponentName.unflattenFromString(r.getString(0));
                UserHandle u = getUserBySerial(r.getLong(1));
                if (cn != null && u != null) {
                    cached.add(buildAppEntry(cn, u, r.getString(2)));
                }
            }
            sortAppList(cached);
            allApps.clear();
            allApps.addAll(cached);
            rebuildStreamRows();
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (widgetHelper != null && widgetHelper.handleActivityResult(
                this, prefs, requestCode, resultCode, data, this::refreshHomeWidgets)) {
            return;
        }
        if (requestCode == REQ_CRED_UNLOCK) {
            if (resultCode == RESULT_OK && pendingAuthTask != null) pendingAuthTask.run();
            pendingAuthTask = null;
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();

        if (requestCode == REQ_PICK_WALL) {
            Toast.makeText(this, "正在应用壁纸…", Toast.LENGTH_SHORT).show();
            AppExecutors.getInstance().diskIO().execute(() -> {
                try {
                    Bitmap bmp = AuroraBackdropView.decodeDownsampledBitmap(this, uri);
                    if (bmp != null) {
                        AuroraBackdropView.saveAndApplySystemWallpaper(this, bmp);
                        prefs.edit()
                                .putInt(K_THEME_IDX, 7)
                                .putInt(K_WALL_STYLE_IDX, -1)
                                .apply();
                        uiHandler.post(this::recreate);
                    }
                } catch (Throwable e) {
                    uiHandler.post(() -> Toast.makeText(this, "壁纸设置失败", Toast.LENGTH_SHORT).show());
                }
            });
            return;
        }

        try {
            if (requestCode == REQ_EXPORT_JSON) {
                JSONObject root = new JSONObject();
                root.put("launcher", "NiagaraLocal");
                root.put("version", 110);
                JSONObject pObj = new JSONObject();
                for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                    Object v = entry.getValue();
                    JSONObject node = new JSONObject();
                    node.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Float ? "f" : v instanceof Long ? "l" : "s");
                    node.put("v", v instanceof Float ? (double) (Float) v : v);
                    pObj.put(entry.getKey(), node);
                }
                root.put("prefs", pObj);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(root.toString(2).getBytes("UTF-8"));
                }
                Toast.makeText(this, "备份已导出到本地文件", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT_JSON) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                JSONObject root = new JSONObject(bo.toString("UTF-8"));
                JSONObject pObj = root.getJSONObject("prefs");
                SharedPreferences.Editor ed = prefs.edit().clear();
                for (Iterator<String> it = pObj.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject node = pObj.getJSONObject(k);
                    String t = node.getString("t");
                    if ("b".equals(t)) ed.putBoolean(k, node.getBoolean("v"));
                    else if ("i".equals(t)) ed.putInt(k, node.getInt("v"));
                    else if ("f".equals(t)) ed.putFloat(k, (float) node.getDouble("v"));
                    else if ("l".equals(t)) ed.putLong(k, node.getLong("v"));
                    else ed.putString(k, node.getString("v"));
                }
                ed.apply();
                Toast.makeText(this, "配置已导入", Toast.LENGTH_SHORT).show();
                recreate();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_CAL_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshCalendarAgenda();
            } else {
                prefs.edit().putBoolean(K_SHOW_AGENDA, false).apply();
            }
        } else if (requestCode == REQ_WEATHER_LOC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "已授权，正在同步实时天气...", Toast.LENGTH_SHORT).show();
                WeatherSyncEngine.fetchWeatherAsync(this, true, s -> refreshWeatherPill());
            } else {
                Toast.makeText(this, "未授予位置权限，可开启通知读取同步系统天气", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Object backCallbackObj;

    private void registerAndroid13BackCallback() {
        if (Build.VERSION.SDK_INT >= 34 && backCallbackObj == null) {
            android.window.OnBackAnimationCallback cb = new android.window.OnBackAnimationCallback() {
                @Override
                public void onBackStarted(android.window.BackEvent backEvent) {
                    if ((inAllAppsMode || searchActive) && streamListView != null) {
                        streamListView.setPivotX(streamListView.getWidth() * 0.5f);
                        streamListView.setPivotY(streamListView.getHeight() * 0.5f);
                    }
                }
                @Override
                public void onBackProgressed(android.window.BackEvent backEvent) {
                    if ((inAllAppsMode || searchActive) && streamListView != null) {
                        float p = backEvent.getProgress();
                        float s = 1f - 0.08f * p;
                        streamListView.setScaleX(s);
                        streamListView.setScaleY(s);
                        streamListView.setAlpha(1f - 0.35f * p);
                    }
                }
                @Override
                public void onBackCancelled() {
                    if (streamListView != null) {
                        streamListView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
                    }
                }
                @Override
                public void onBackInvoked() {
                    if (streamListView != null) {
                        streamListView.setScaleX(1f);
                        streamListView.setScaleY(1f);
                        streamListView.setAlpha(1f);
                    }
                    handleBackAction();
                }
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallbackObj = cb;
        } else if (Build.VERSION.SDK_INT == 33 && backCallbackObj == null) {
            android.window.OnBackInvokedCallback cb = this::handleBackAction;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallbackObj = cb;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                handleBackAction();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }

    private void handleBackAction() {
        if (popupController != null && popupController.isShowing()) {
            popupController.dismiss(true);
            return;
        }
        if (currentSheetDialog != null && currentSheetDialog.isShowing()) {
            currentSheetDialog.dismiss();
            return;
        }
        if (searchActive) {
            closeInstantSearch();
            return;
        }
        if (inAllAppsMode) {
            returnToHomeFavorites();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBackTime < 120) return;
        lastBackTime = now;
        returnToHomeFavorites();
    }

    private AppEntry findAppByKey(String idKey) {
        for (AppEntry a : allApps) if (a.idKey.equals(idKey)) return a;
        return null;
    }

    private AppEntry findAppByPackage(String pkgName) {
        if (pkgName == null) return null;
        for (AppEntry a : allApps) {
            if (a.component != null && pkgName.equals(a.component.getPackageName())) {
                return a;
            }
        }
        return null;
    }

    private List<String> getStringList(String key) {
        String raw = prefs.getString(key, "");
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split("\\|")));
        return out;
    }

    private void putStringList(String key, List<String> list) {
        prefs.edit().putString(key, TextUtils.join("|", list)).apply();
    }

    private JSONObject getAliasesJson() {
        try { return new JSONObject(prefs.getString(K_ALIASES, "{}")); }
        catch (Throwable t) { return new JSONObject(); }
    }

    private JSONObject getLaunchCountsJson() {
        try { return new JSONObject(prefs.getString(K_LAUNCH_COUNTS, "{}")); }
        catch (Throwable t) { return new JSONObject(); }
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("calc", text));
        Toast.makeText(this, "已复制：" + text, Toast.LENGTH_SHORT).show();
    }

    private boolean startIntentSafely(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开对应系统界面", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && searchInput != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        if (searchInput != null) searchInput.clearFocus();
    }
}
