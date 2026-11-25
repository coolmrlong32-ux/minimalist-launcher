package com.niagara.local;

import android.Manifest;
import android.app.Activity;
import android.animation.ValueAnimator;import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
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
import android.graphics.BitmapFactory;
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
import android.text.TextUtils;
import android.text.TextWatcher;
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
import android.widget.TextClock;
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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流光桌面 · Niagara Local (v1.1.0 深度优化版)
 *
 * 核心升级：
 * 1. 【左、右侧丝滑流体波浪导轨】右侧按参考视频还原「远端圆点 · + 近端柔美弧线字母 + 波峰深靛蓝圆球 + 触点光环」，
 *    采用 C² 连续余弦钟形包络 + 二阶弹簧阻尼 + 纵向弧长舒展补偿，彻底告别折角僵硬感；左侧继续保持无字母隐形触发；
 * 2. 【点按/滑动字母即时显示对应应用】无论是轻点还是滑动左/右侧边缘某字母，列表立即切换并精准定位到该字母开头的应用组，
 *    当前字母组高亮显示、其余字母组柔和变淡（松手后保持平滑高亮过渡）；
 * 3. 【桌面壁纸跟随系统白昼变化】默认主题自动响应系统深浅色模式 (UI_MODE_NIGHT) 与昼夜时段，白昼呈现视频同款清透晨曦丁香蓝紫柔雾，
 *    夜晚自动切换为深邃星云夜空（系统壁纸模式亦支持夜间自动柔暗护眼）；
 * 4. 【第一页常用应用栏深度优化】顶部时钟与日期常驻不跳动，第一页常用应用支持丝绒毛玻璃微卡片、高频 4 宫格灵动槽、
 *    内嵌通知/快捷方式直达及底部「✦ 编辑常用 · 全部应用」双胶囊栏。
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
        static final int TYPE_FAV_FOOTER = 4;
        static final int TYPE_GROUP_ROW = 5;
        static final int TYPE_FOLDER = 6;
        static final int TYPE_SHORTCUT_RESULT = 7;
        static final int TYPE_WEB_SEARCH = 8;

        final int type;
        final String text;
        final AppEntry app;
        final List<AppEntry> groupApps;
        final int folderIdx;
        final ShortcutInfo shortcut;

        StreamRow(int type, String text, AppEntry app) {
            this(type, text, app, null, -1, null);
        }

        StreamRow(int type, String text, AppEntry app, List<AppEntry> groupApps) {
            this(type, text, app, groupApps, -1, null);
        }

        StreamRow(int type, String text, AppEntry app, int folderIdx) {
            this(type, text, app, null, folderIdx, null);
        }

        StreamRow(int type, String text, AppEntry app, ShortcutInfo shortcut) {
            this(type, text, app, null, -1, shortcut);
        }

        StreamRow(int type, String text, AppEntry app, List<AppEntry> groupApps, int folderIdx, ShortcutInfo shortcut) {
            this.type = type;
            this.text = text;
            this.app = app;
            this.groupApps = groupApps;
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

    // 右侧显示 ★ 与 A-Z #（搜索统一由右下角放大镜圆钮或上滑触发，与视频完全一致）
    private static final String WAVE_SYMBOLS = "★ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private static final String PREF_FILE = "niagara_local";

    // 配置键
    private static final String K_FAVS = "favs";
    private static final String K_HIDDEN = "hidden";
    private static final String K_ALIASES = "aliases";
    private static final String K_FOLDERS = "folders";
    private static final String K_LAUNCH_COUNTS = "launch_counts";
    private static final String K_RECENT_KEYS = "recent_keys";
    private static final String K_STICKY_NOTE = "sticky_note";
    private static final String K_THEME_IDX = "theme_idx";
    private static final String K_DIM_PCT = "dim_pct";
    private static final String K_FLOW_ANIM = "flow_anim";
    private static final String K_ICON_SHAPE = "icon_shape";
    private static final String K_SHOW_ICONS = "show_icons";
    private static final String K_SHOW_RECENT_BAR = "show_recent_bar";
    private static final String K_FAV_CARDS = "fav_cards_v2";
    private static final String K_FAV_RAIL = "fav_rail_v1";
    private static final String K_SHOW_GLANCE_CARD = "show_glance_card_v1";
    private static final String K_PULL_DOWN_SEARCH = "pull_down_search_v1";
    private static final String K_COIN_LEFT = "coin_left_v1";
    private static final String K_WALL_STYLE_IDX = "wall_style_idx";
    private static final String K_WALL_APPLIED_NIGHT = "wall_applied_night";
    private static final String K_FONT_IDX = "font_idx";
    private static final String K_TEXT_SCALE = "text_scale";
    private static final String K_ROW_DENSITY = "row_density";
    private static final String K_USE_24H = "use_24h";
    private static final String K_AUTO_HIDE_WAVE = "auto_hide_wave";
    private static final String K_PULL_LAUNCH = "pull_launch";
    private static final String K_INLINE_NOTIF = "inline_notif";
    private static final String K_SHOW_MEDIA = "show_media";
    private static final String K_SHOW_AGENDA = "show_agenda";
    private static final String K_DOUBLE_TAP_LOCK = "double_tap_lock";
    private static final String K_SWIPE_DOWN_NOTIF = "swipe_down_notif";
    private static final String K_BIOMETRIC_HIDE = "biometric_hide";
    private static final String K_KEEP_ALIVE = "keep_alive";
    private static final String K_SORT_BY_USAGE = "sort_by_usage";
    private static final String K_STATUS_BAR_FULL = "status_bar_full_v1";
    private static final String K_HERO_HIGH_CONTRAST = "hero_high_contrast_v1";
    private static final String K_WAVE_IDLE_DOTS = "wave_idle_dots_v1";
    private static final String K_ICON_PACK = "icon_pack_pkg_v1";
    private static final String K_CUSTOM_ICONS = "custom_app_icons_v1";
    private static final String K_ONE_HAND_GRAVITY = "one_hand_gravity_v1";
    private static final String K_COIN_TAP_ACTION = "coin_tap_action_v1";

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
    private static final String[] FONT_NAMES = {"现代纤巧", "均衡标准", "醒目中粗", "人文衬线", "极客等宽"};
    private static final String[] FONT_FAMILIES = {"sans-serif-light", "sans-serif", "sans-serif-medium", "serif", "monospace"};
    private static final String[] DENSITY_NAMES = {"紧凑灵巧", "黄金舒适", "舒展宽松"};

    static volatile MainActivity activeInstance;
    static volatile boolean wasOnHomeBeforeLock = true;
    static volatile boolean hasWindowFocusNow = false;
    static volatile boolean launchedExternalApp = false;
    static volatile long lastPauseUptime = 0;

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
    private float dpUnit;
    private float textScale = 1.0f;
    private AuroraTheme theme;
    private Typeface typeface;

    // 视图组件
    private AuroraBackdropView backdropView;
    private LinearLayout workspaceMainColumn;
    private LinearLayout persistentTopHeader; // 首页顶部：大时钟 + 日期/农历 + 状态栏 + 一览组件
    private LinearLayout firstPageExtrasBox;  // 保留字段兼容
    private View heroSpacerTop, heroSpacerBottom;
    private TextClock mainClockView;
    private TextView dateLunarView;
    private HeroStatusBarView heroStatusBar;
    private TextView stickyNoteSummaryView;
    private LinearLayout glanceBarRow;
    private TextView glanceRamPill;
    private TextView lunarStatusView;
    private TextView stickyNotePill;
    private TextView agendaPill;
    private LinearLayout homeWidgetsBox;
    private TextView swipeHintPill;
    private LinearLayout recentDockCard;
    private LinearLayout dockRow1, dockRow2;
    private LinearLayout recentIconsRow;
    private LinearLayout folderCapsulesRow;
    private HorizontalScrollView folderScroll;
    private LinearLayout mediaWaveCard;
    private ImageView mediaArtView;
    private TextView mediaTitleView, mediaSubtitleView, mediaPlayBtn, mediaTimeView;
    private View mediaProgressFill;
    private LinearLayout searchBarCard;
    private EditText searchInput;
    private ListView streamListView;
    private StreamAdapter streamAdapter;
    private NiagaraWaveView rightWaveBar, leftGhostWaveBar;
    private NiagaraCoinButton coinButton;

    private int currentTopInsetPx = 0;
    private int currentNavBottomPx = 0;
    private int currentImeBottomPx = 0;
    private final Map<String, Drawable> folderIconCache = new HashMap<>();
    private Map<String, String> customIconsCache = null;

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

    private final Runnable clearHighlightTask = new Runnable() {
        @Override
        public void run() {
            highlightedGroup = 0;
            if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activeInstance = this;
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
        userManager = (UserManager) getSystemService(USER_SERVICE);
        widgetHelper = new WidgetHostHelper(this);
        IconPackManager.loadPack(this, prefs.getString(K_ICON_PACK, ""));
        dpUnit = getResources().getDisplayMetrics().density;

        applyThemeAndWindowFlags();
        virtualSettingsApp = createSettingsAppEntry();
        setContentView(buildNiagaraWorkspace());
        refreshHeroStatusWidgets();
        refreshHomeWidgets();

        readDiskAppCache();
        loadInstalledAppsAsync();
        launcherApps.registerCallback(pkgChangeCallback, uiHandler);

        NotificationHubService.uiCallback = () -> {
            uiHandler.removeCallbacks(refreshNotificationsTask);
            long sinceResume = SystemClock.uptimeMillis() - lastResumeUptime;
            long delay = (isResumed && sinceResume < 520L) ? (540L - sinceResume) : 220L;
            uiHandler.postDelayed(refreshNotificationsTask, delay);
        };

        if (prefs.getBoolean(K_KEEP_ALIVE, true)) {
            KeepAliveService.startServiceSafely(this);
        }
        gestureDetector = new GestureDetector(this, screenGestureListener);
        registerAndroid13BackCallback();
    }

    private final Runnable refreshNotificationsTask = () -> {
        if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
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
            wasOnHomeBeforeLock = true;
            launchedExternalApp = false;
            resetLockFlags();
        }
    }

    void requestDismissStuckKeyguard() {
        uiHandler.post(() -> {
            try {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null && km.isDeviceLocked()) return;
                if (Build.VERSION.SDK_INT >= 27) {
                    setShowWhenLocked(true);
                    setTurnScreenOn(true);
                } else {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                }
                if (km != null) km.requestDismissKeyguard(this, null);
            } catch (Throwable ignored) {}
        });
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
        wasOnHomeBeforeLock = true;
        launchedExternalApp = false;

        AuroraTheme latestTheme = AuroraTheme.resolve(this, prefs.getInt(K_THEME_IDX, AuroraTheme.IDX_DEFAULT));
        if (latestTheme.isLight != lastResolvedLightState) {
            recreate();
            return;
        }
        if (backdropView != null) backdropView.setScreenVisible(true);

        uiHandler.removeCallbacks(resumeRefreshTask);
        uiHandler.postDelayed(resumeRefreshTask, 900L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        dismissAppPopupCard(false);
        uiHandler.removeCallbacks(resumeRefreshTask);
        lastPauseUptime = SystemClock.uptimeMillis();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean locking = GestureLockService.wasJustLocked()
                || (SystemClock.uptimeMillis() - lastDoubleTapLock < 4500)
                || (pm != null && !pm.isInteractive())
                || (km != null && km.isKeyguardLocked());
        wasOnHomeBeforeLock = locking || !launchedExternalApp;
        if (backdropView != null) backdropView.setScreenVisible(false);
        uiHandler.removeCallbacks(mediaProgressTicker);
        uiHandler.removeCallbacks(refreshNotificationsTask);
        uiHandler.removeCallbacks(clearHighlightTask);
    }

    @Override
    protected void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        super.onDestroy();
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
        if (inAllAppsMode || searchActive || (currentSheetDialog != null && currentSheetDialog.isShowing())) {
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

    // ---------- 构建 Niagara 主界面（还原视频清爽头部 + 深度优化第一页常用应用栏） ----------
    private View buildNiagaraWorkspace() {
        final FrameLayout root = new FrameLayout(this);
        workspaceRoot = root;

        // 1. 底层：跟随系统白昼/暗夜变化的柔光流体/壁纸画布
        backdropView = new AuroraBackdropView(this);
        backdropView.configure(theme, prefs.getInt(K_DIM_PCT, 0), prefs.getBoolean(K_FLOW_ANIM, true));
        root.addView(backdropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. 主纵向容器
        final LinearLayout mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        workspaceMainColumn = mainColumn;

        // 2.1 常驻顶部时钟与日期栏（如视频 9995.mp4 所示：左上方大时钟 + 日期星期 + 状态行，在首页与全部应用页均常驻）
        persistentTopHeader = new LinearLayout(this);
        persistentTopHeader.setOrientation(LinearLayout.VERTICAL);

        TextClock mainClock = new TextClock(this);
        mainClockView = mainClock;
        String timeFmt = prefs.getBoolean(K_USE_24H, true) ? "HH:mm" : "h:mm";
        mainClock.setFormat24Hour(timeFmt);
        mainClock.setFormat12Hour(timeFmt);
        mainClock.setTextColor(theme.textPrimary);
        mainClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(HERO_CLOCK_SP));
        mainClock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mainClock.setLetterSpacing(0.01f);
        mainClock.setFontFeatureSettings("tnum");
        mainClock.setIncludeFontPadding(false);
        mainClock.setShadowLayer(theme.isLight ? dp(1.5f) : dp(3.5f), 0f, dp(1f), theme.isLight ? 0x18000000 : 0x55000000);
        mainClock.setOnClickListener(v -> {
            if (searchActive || inAllAppsMode) {
                returnToHomeFavorites();
            } else {
                startIntentSafely(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
            }
        });
        mainClock.setOnLongClickListener(v -> { openControlCenterSheet(); return true; });
        persistentTopHeader.addView(mainClock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dateLunarView = new TextView(this);
        // v2.6.0 字重对比：日期 Medium 14sp、alpha 0.85；农历为 Regular 更淡的次级层级
        dateLunarView.setTextColor(theme.isLight ? 0xD9141824 : 0xD9F1F5F9);
        dateLunarView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(14.0f));
        dateLunarView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateLunarView.setLetterSpacing(0.02f);
        dateLunarView.setPadding(dp(2), dp(6), 0, 0);
        dateLunarView.setShadowLayer(theme.isLight ? dp(1.0f) : dp(2.0f), 0f, dp(0.6f), theme.isLight ? 0x14000000 : 0x44000000);
        dateLunarView.setOnClickListener(v -> {
            if (searchActive || inAllAppsMode) {
                returnToHomeFavorites();
            } else {
                openSystemCalendarApp();
            }
        });
        dateLunarView.setOnLongClickListener(v -> { openControlCenterSheet(); return true; });
        persistentTopHeader.addView(dateLunarView);

        // 首页顶部无边框极简状态栏（方案 A：去边框、精简天气+高质感电量，支持设置切换完整模式与高对比度）
        heroStatusBar = new HeroStatusBarView(this, theme, typeface, textScale, prefs.getBoolean(K_STATUS_BAR_FULL, false), new HeroStatusBarView.Callbacks() {
            @Override public void onWeatherClick() { handleWeatherBarClick(); }
            @Override public void onDeviceStatusClick() { showDeviceStatusDialog(); }
            @Override public void onLongPress() { openControlCenterSheet(); }
        });
        heroStatusBar.setAestheticStyle(prefs.getBoolean(K_HERO_HIGH_CONTRAST, false)
                ? HeroStatusBarView.STYLE_HIGH_CONTRAST : HeroStatusBarView.STYLE_AIRY);
        LinearLayout.LayoutParams dsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dsLp.topMargin = dp(6);
        dsLp.bottomMargin = dp(2);
        persistentTopHeader.addView(heroStatusBar, dsLp);

        // 一览日程行（原生 Niagara 无框排版：有近期日程且开启时轻盈浮现在状态栏下方）
        agendaPill = makeText("", 12.6f, theme.isLight ? 0xCC141824 : 0xD9F1F5F9, false);
        agendaPill.setSingleLine(true);
        agendaPill.setEllipsize(TextUtils.TruncateAt.END);
        agendaPill.setPadding(dp(2), dp(4), dp(4), dp(2));
        agendaPill.setShadowLayer(theme.isLight ? dp(0.9f) : dp(2.0f), 0f, dp(0.6f), theme.isLight ? 0x16000000 : 0x48000000);
        agendaPill.setVisibility(View.GONE);
        agendaPill.setOnClickListener(v -> openActiveCalendarEvent());
        LinearLayout.LayoutParams agLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        agLp.topMargin = dp(2);
        persistentTopHeader.addView(agendaPill, agLp);

        // 桌面速记便签行（无边框轻排版，留空自动隐藏）
        stickyNotePill = makeText("", 12.6f, theme.isLight ? 0xCC141824 : 0xD9F1F5F9, false);
        stickyNotePill.setSingleLine(true);
        stickyNotePill.setEllipsize(TextUtils.TruncateAt.END);
        stickyNotePill.setPadding(dp(2), dp(3), dp(4), dp(2));
        stickyNotePill.setShadowLayer(theme.isLight ? dp(0.9f) : dp(2.0f), 0f, dp(0.6f), theme.isLight ? 0x16000000 : 0x48000000);
        stickyNotePill.setVisibility(View.GONE);
        stickyNotePill.setOnClickListener(v -> promptEditStickyNote());
        LinearLayout.LayoutParams snPillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        snPillLp.topMargin = dp(2);
        persistentTopHeader.addView(stickyNotePill, snPillLp);
        refreshStickyNotePill();

        // 一览媒体播放器（播放音乐时自动浮现，带专辑封面与进度控制）
        mediaWaveCard = buildMediaWaveCard();
        LinearLayout.LayoutParams mediaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mediaLp.topMargin = dp(8);
        persistentTopHeader.addView(mediaWaveCard, mediaLp);

        // 原生桌面微件宿主容器（AppWidgetHost）
        homeWidgetsBox = new LinearLayout(this);
        homeWidgetsBox.setOrientation(LinearLayout.VERTICAL);
        homeWidgetsBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams hwLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hwLp.topMargin = dp(4);
        persistentTopHeader.addView(homeWidgetsBox, hwLp);

        mainColumn.addView(persistentTopHeader);

        // 2.2 极速搜索与计算器输入卡（v2.7.0 改为贴底单手悬浮，紧贴软键盘顶缘）
        searchBarCard = buildSearchBarCard();
        searchBarCard.setElevation(dp(10));

        // 2.4 主应用列表 (第一页常用应用流 / 字母分组全部应用流)
        streamListView = new ListView(this);
        streamListView.setDivider(null);
        streamListView.setVerticalScrollBarEnabled(false);
        streamListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        streamListView.setSelector(android.R.color.transparent);
        streamListView.setClipToPadding(false);
        streamListView.setScrollingCacheEnabled(false);
        streamListView.setAnimationCacheEnabled(false);
        streamAdapter = new StreamAdapter();
        streamListView.setAdapter(streamAdapter);

        streamListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= streamRows.size()) return;
            StreamRow row = streamRows.get(position);
            if (row.type == StreamRow.TYPE_CALC_RESULT) {
                copyToClipboard(row.text);
            }
        });;

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
                // 波浪擦拭期间列表被程序化跳转/重排后，立即对新的可见项重新施加内推位移，避免残留或错位
                if (waveCouplingActive) applyWaveDisplacementToVisibleItems();
            }
        });

        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = dp(4);
        listLp.bottomMargin = dp(12);
        streamListView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((b - t) != (ob - ot)) {
                applyOneHandedListPadding();
            }
        });
        mainColumn.addView(streamListView, listLp);

        root.addView(mainColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2.5 应用行左右滑实时视觉提示胶囊
        swipeHintPill = makeText("", 12.5f, 0xFFFFFFFF, true);
        swipeHintPill.setBackground(theme.solidAccentButton(dpUnit, 16));
        swipeHintPill.setPadding(dp(16), dp(8), dp(16), dp(8));
        swipeHintPill.setElevation(dp(8));
        swipeHintPill.setVisibility(View.GONE);
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        shLp.bottomMargin = dp(96);
        root.addView(swipeHintPill, shLp);

        // 3. 左右双侧流体波浪导轨：右侧支持静默圆点/字母切换，左侧支持真正的本侧镜像展开（静止完全隐形）
        rightWaveBar = new NiagaraWaveView(this, WAVE_SYMBOLS);
        rightWaveBar.setAlignLeft(false);
        leftGhostWaveBar = new NiagaraWaveView(this, WAVE_SYMBOLS);
        leftGhostWaveBar.setAlignLeft(true);
        leftGhostWaveBar.setSilentAtRest(true);
        leftGhostWaveBar.setVisibility(View.VISIBLE);

        boolean autoHideWave = prefs.getBoolean(K_AUTO_HIDE_WAVE, false);
        boolean idleDots = prefs.getBoolean(K_WAVE_IDLE_DOTS, true);
        rightWaveBar.bindStyle(theme, typeface, autoHideWave);
        rightWaveBar.setIdleDotsMode(idleDots);
        leftGhostWaveBar.bindStyle(theme, typeface, autoHideWave);
        leftGhostWaveBar.setIdleDotsMode(idleDots);

        NiagaraWaveView.Listener waveListener = new NiagaraWaveView.Listener() {
            @Override
            public void onWaveScrub(int index, char letter, float normX, float normY, float strength, boolean pullArmed) {
                isWaveScrubbing = true;
                uiHandler.removeCallbacks(clearHighlightTask);
                if (backdropView != null) {
                    backdropView.onWaveInteract(normX, normY, strength);
                }
                jumpToLetterApplications(index, letter);
            }

            @Override
            public void onWaveRelease(int index, char letter, boolean triggerPullLaunch) {
                isWaveScrubbing = false;
                lastWaveReleaseMs = android.os.SystemClock.uptimeMillis();
                if (backdropView != null) {
                    backdropView.onWaveInteract(0.85f, 0.5f, 0f);
                }
                // 松手后保留所点字母开头应用的高亮 900ms 再平滑恢复全亮，让视线一眼锁定目标字母应用
                uiHandler.removeCallbacks(clearHighlightTask);
                uiHandler.postDelayed(clearHighlightTask, 900);

                if (triggerPullLaunch && prefs.getBoolean(K_PULL_LAUNCH, true)) {
                    AppEntry top = letterTopApp.get(letter);
                    if (top != null) {
                        Toast.makeText(MainActivity.this, "⚡ 极速直达：" + top.label, Toast.LENGTH_SHORT).show();
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

        // 4. 右下角 Niagara 灵动悬浮按钮（默认极速搜索，支持多任务后台 / 小布语音 / 隐藏）
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

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int navBottom = insets.getSystemWindowInsetBottom();
            int imeBottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    topInset = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                    navBottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                    imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                } catch (Throwable ignored) {}
            } else {
                imeBottom = navBottom;
            }
            currentTopInsetPx = topInset;
            currentNavBottomPx = navBottom;
            currentImeBottomPx = Math.max(navBottom, imeBottom);

            mainColumn.setPadding(dp(22), topInset + dp(20), dp(34), 0);
            applySearchBarInsets();
            applyOneHandedListPadding();

            for (NiagaraWaveView wb : new NiagaraWaveView[]{leftGhostWaveBar, rightWaveBar}) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wb.getLayoutParams();
                // 🚀 人体工学下移：把字母导轨整体下调至单手大拇指舒适弧形区，单手大拇指自然落在最上方的字母
                lp.topMargin = topInset + dp(168);
                lp.bottomMargin = navBottom + dp(90);
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
            int targetBot = Math.max(currentNavBottomPx, currentImeBottomPx) + dp(10);
            if (lp.bottomMargin != targetBot) {
                lp.bottomMargin = targetBot;
                searchBarCard.setLayoutParams(lp);
            }
        }
    }

    /**
     * 点到或滑到某个字母时，立即显示该字母开头的相关应用（并高亮该字母组）
     */
    private void jumpToLetterApplications(int idx, char ch) {
        if (ch == '★') {
            returnToHomeFavorites();
            return;
        }
        if (searchActive) {
            searchActive = false;
            searchInput.setText("");
            searchBarCard.setVisibility(View.GONE);
        }

        if (!inAllAppsMode) {
            inAllAppsMode = true;
            updateFirstPageExtrasVisibility();
            rebuildStreamRows();
        }
        highlightedGroup = ch;
        streamAdapter.notifyDataSetChanged();

        Integer pos = letterRowIndex.get(ch);
        if (pos == null) pos = findNearestLetterPosition(idx);
        if (pos != null) {
            final int targetPos = pos;
            streamListView.smoothScrollBy(0, 0);
            streamListView.setSelectionFromTop(targetPos, 0);
            streamListView.post(() -> streamListView.setSelectionFromTop(targetPos, 0));
        }
        hideSoftKeyboard();
    }

    // ---------- v2.7.0 边缘波浪导轨 ↔ 主列表物理联动（安全区内微推 + 图标视差，零左边界裁切） ----------
    private static final float WAVE_LIST_SIGMA_DP = 72f;          // 列表高斯σ
    private static final float WAVE_LIST_PUSH_DP = 12f;           // 列表项最大横向内推（< 22dp 左边距，永不裁切图标）
    private static final float WAVE_LIST_ICON_PARALLAX_DP = 4f;   // 图标额外视差
    private static final float WAVE_LIST_SCALE_GAIN = 0.03f;      // 微缩放增益
    private static final long WAVE_REBOUND_MS = 180L;             // 松手阻尼回弹时长

    private boolean waveCouplingActive = false;
    private float waveCouplingTouchY = -1f;
    private float waveCouplingDir = -1f;   // -1：向屏幕左侧（右手导轨内侧）推；+1：左手导轨向右推
    private ValueAnimator waveReboundAnim;
    private final int[] waveListLoc = new int[2];

    private void setupWaveListCoupling() {
        bindWaveCoupling(rightWaveBar, -1f);
        bindWaveCoupling(leftGhostWaveBar, 1f);
    }

    private void bindWaveCoupling(NiagaraWaveView bar, final float dir) {
        if (bar == null) return;
        bar.setOnWaveScrubListener(new NiagaraWaveView.OnWaveScrubListener() {
            @Override
            public void onWaveScrubStart() {
                cancelWaveRebound();
                waveCouplingActive = true;
                waveCouplingDir = dir;
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
            if (child == null) continue;
            float centerY = waveListLoc[1] + child.getTop() + child.getHeight() * 0.5f;
            float d = centerY - waveCouplingTouchY;
            float factor = (float) Math.exp(-(d * d) / (2.0f * sigma * sigma));
            applyItemWaveDisplacement(child, factor);
        }
    }

    private void applyItemWaveDisplacement(View itemView, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        itemView.setTranslationX(waveCouplingDir * dpUnit * WAVE_LIST_PUSH_DP * f);
        float scale = 1.0f + WAVE_LIST_SCALE_GAIN * f;
        itemView.setScaleX(scale);
        itemView.setScaleY(scale);
        View icon = findRowIconView(itemView);
        if (icon != null) {
            icon.setTranslationX(waveCouplingDir * dpUnit * WAVE_LIST_ICON_PARALLAX_DP * f);
        }
    }

    private View findRowIconView(View itemView) {
        if ("app_row_v2".equals(itemView.getTag()) && itemView instanceof ViewGroup) {
            View card = ((ViewGroup) itemView).getChildAt(0);
            if (card instanceof ViewGroup && ((ViewGroup) card).getChildCount() > 1) {
                View icon = ((ViewGroup) card).getChildAt(1);
                if (icon instanceof ImageView) return icon;
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
        final List<float[]> from = new ArrayList<>(); // {itemTx, itemScale, iconTx}
        for (int i = 0; i < count; i++) {
            View child = streamListView.getChildAt(i);
            if (child == null) continue;
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
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (waveReboundAnim != anim) return; // 被新一轮擦拭取消：保持当前位移，由新位移接管
                waveReboundAnim = null;
                for (View child : views) resetItemWaveTransform(child);
            }
        });
        waveReboundAnim = anim;
        anim.start();
    }

    private Integer findNearestLetterPosition(int idx) {
        for (int i = idx; i < WAVE_SYMBOLS.length(); i++) {
            Integer p = letterRowIndex.get(WAVE_SYMBOLS.charAt(i));
            if (p != null) return p;
        }
        for (int i = idx; i >= 0; i--) {
            Integer p = letterRowIndex.get(WAVE_SYMBOLS.charAt(i));
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
        streamListView.setAlpha(0.55f);
        streamListView.setTranslationY(dp(12));
        streamListView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(170L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .start();
    }

    private void applyOneHandedListPadding() {
        if (streamListView == null) return;
        int left = streamListView.getPaddingLeft();
        int right = streamListView.getPaddingRight();
        int navBot = currentNavBottomPx;
        if (searchActive) {
            int searchBotPad = Math.max(navBot, currentImeBottomPx) + dp(82);
            if (streamListView.getPaddingTop() != dp(12) || streamListView.getPaddingBottom() != searchBotPad) {
                streamListView.setPadding(left, dp(12), right, searchBotPad);
            }
            return;
        }
        int botPad = navBot + dp(82);
        if (inAllAppsMode || !prefs.getBoolean(K_ONE_HAND_GRAVITY, true)) {
            int topP = inAllAppsMode ? dp(6) : dp(4);
            if (streamListView.getPaddingTop() != topP || streamListView.getPaddingBottom() != botPad) {
                streamListView.setPadding(left, topP, right, botPad);
            }
            return;
        }
        int availH = streamListView.getHeight();
        if (availH <= 0) {
            streamListView.setPadding(left, dp(8), right, botPad);
            return;
        }
        float rowHeightPx = dp(56f * getRowSpacingMultiplier());
        int estContentH = Math.round(streamRows.size() * rowHeightPx);
        int extraSpace = availH - estContentH - botPad;
        int topPad = dp(8);
        if (extraSpace > dp(36)) {
            topPad = Math.max(dp(8), Math.min(Math.round(extraSpace * 0.62f), availH / 2));
            // v2.6.0 首屏留白美学：时钟下方保留至少 18% 屏高的纯净留白，随时钟高度联动，且不超过剩余空间
            int clockH = mainClockView != null ? mainClockView.getHeight() : 0;
            int skyMin = Math.max(Math.round(getResources().getDisplayMetrics().heightPixels * 0.18f),
                    Math.round(clockH * 1.2f));
            topPad = Math.max(topPad, Math.min(skyMin, Math.min(extraSpace, availH / 2)));
        }
        if (streamListView.getPaddingTop() != topPad || streamListView.getPaddingBottom() != botPad) {
            streamListView.setPadding(left, topPad, right, botPad);
        }
    }

    private void applyCoinButtonConfig() {
        if (coinButton == null) return;
        int act = prefs.getInt(K_COIN_TAP_ACTION, 1);
        if (act == 3 || searchActive) {
            coinButton.setVisibility(View.GONE);
        } else {
            coinButton.setVisibility(View.VISIBLE);
            if (act == 1) coinButton.setIconMode(NiagaraCoinButton.ICON_SEARCH);
            else if (act == 2) coinButton.setIconMode(NiagaraCoinButton.ICON_ASSIST);
            else coinButton.setIconMode(NiagaraCoinButton.ICON_RECENTS);
        }
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
        if (homeWidgetsBox == null || widgetHelper == null) return;
        if (inAllAppsMode || searchActive) {
            homeWidgetsBox.setVisibility(View.GONE);
            return;
        }
        widgetHelper.populateWidgets(this, prefs, homeWidgetsBox, theme, dpUnit, (widget, info) -> {
            String label = info.loadLabel(getPackageManager());
            showSingleChoiceDialog("🧩 微件设置 · " + label,
                    new String[]{"📏 紧凑高度 (96dp)", "📐 标准高度 (136dp)", "🖼 宽展高度 (196dp)", "🗑 移除此微件"},
                    1, w -> {
                        if (w == 0) widgetHelper.updateWidgetHeight(prefs, widget.id, 96);
                        else if (w == 1) widgetHelper.updateWidgetHeight(prefs, widget.id, 136);
                        else if (w == 2) widgetHelper.updateWidgetHeight(prefs, widget.id, 196);
                        else if (w == 3) widgetHelper.removeWidget(prefs, widget.id);
                        refreshHomeWidgets();
                    });
        });
    }

    // ---------- v2.6.0 灵动搜索过渡：壁纸景深 / Hero 渐隐 / 圆钮同步 ----------
    private static final float HERO_CLOCK_SP = 44f;
    private static final long SEARCH_TRANSITION_MS = 220L;
    private float searchTransitionProgress = 0f;
    private boolean searchTransitionTargetOn = false;
    private ValueAnimator searchTransitionAnim;

    /**
     * 驱动搜索过渡进度（0=首屏，1=完全搜索态）：
     * 壁纸缩放 1.0→0.96 + 35% 压暗、Hero 时钟/日期/状态栏上滑淡出（-30dp）、搜索卡从下方浮入、圆钮圆环强调。
     */
    public void updateSearchTransition(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        searchTransitionProgress = p;
        if (backdropView != null) backdropView.setSearchTransitionProgress(p);
        if (heroStatusBar != null) heroStatusBar.applySearchFadeProgress(p);
        int heroVis = p >= 0.999f ? View.INVISIBLE : View.VISIBLE;
        if (mainClockView != null) {
            mainClockView.setAlpha(1f - p);
            mainClockView.setTranslationY(-dp(30) * p);
            if (mainClockView.getVisibility() != heroVis) mainClockView.setVisibility(heroVis);
        }
        if (dateLunarView != null) {
            dateLunarView.setAlpha(1f - p);
            dateLunarView.setTranslationY(-dp(24) * p);
            if (dateLunarView.getVisibility() != heroVis) dateLunarView.setVisibility(heroVis);
        }
        if (searchBarCard != null) {
            if (searchBarCard.getVisibility() == View.VISIBLE) {
                searchBarCard.setAlpha(p);
                searchBarCard.setTranslationY((1f - p) * dp(18));
            } else {
                searchBarCard.setAlpha(1f);
                searchBarCard.setTranslationY(0f);
            }
        }
        if (coinButton != null) coinButton.setSearchProgress(p);
    }

    private void animateSearchTransition(boolean on) {
        if (on == searchTransitionTargetOn) return;
        searchTransitionTargetOn = on;
        if (searchTransitionAnim != null) {
            searchTransitionAnim.cancel();
            searchTransitionAnim = null;
        }
        updateSearchTransition(searchTransitionProgress);
        ValueAnimator anim = ValueAnimator.ofFloat(searchTransitionProgress, on ? 1f : 0f);
        anim.setDuration(SEARCH_TRANSITION_MS);
        anim.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f));
        anim.addUpdateListener(va -> updateSearchTransition((float) va.getAnimatedValue()));
        searchTransitionAnim = anim;
        anim.start();
    }

    private void updateFirstPageExtrasVisibility() {
        animateSearchTransition(searchActive);
        if (firstPageExtrasBox != null) {
            firstPageExtrasBox.setVisibility(View.GONE);
        }
        boolean homeOnly = !inAllAppsMode && !searchActive;
        if (persistentTopHeader != null) {
            // 原生 Niagara 逻辑：切入全部应用或搜索态时收起顶部大时钟区，释放完整列表可视空间
            persistentTopHeader.setVisibility(homeOnly ? View.VISIBLE : View.GONE);
        }
        if (agendaPill != null && !homeOnly) agendaPill.setVisibility(View.GONE);
        if (stickyNotePill != null && !homeOnly) stickyNotePill.setVisibility(View.GONE);
        if (mediaWaveCard != null && !homeOnly) mediaWaveCard.setVisibility(View.GONE);
        if (homeWidgetsBox != null) {
            if (homeOnly) refreshHomeWidgets();
            else homeWidgetsBox.setVisibility(View.GONE);
        }
        if (homeOnly) {
            refreshStickyNotePill();
            refreshCalendarAgenda();
            updateMediaCardUi();
        }
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

    private LinearLayout buildMediaWaveCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.glassCard(dpUnit, 20));
        card.setPadding(dp(14), dp(12), dp(10), dp(12));
        card.setVisibility(View.GONE);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        mediaArtView = new ImageView(this);
        mediaArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtView.setVisibility(View.GONE);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        artLp.rightMargin = dp(10);
        topRow.addView(mediaArtView, artLp);

        LinearLayout metaCol = new LinearLayout(this);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        mediaTitleView = makeText("", 15, theme.textPrimary, true);
        mediaTitleView.setSingleLine(true);
        mediaTitleView.setEllipsize(TextUtils.TruncateAt.END);
        mediaSubtitleView = makeText("", 12, theme.textSecondary, false);
        mediaSubtitleView.setSingleLine(true);
        mediaSubtitleView.setEllipsize(TextUtils.TruncateAt.END);
        metaCol.addView(mediaTitleView);
        metaCol.addView(mediaSubtitleView);
        topRow.addView(metaCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String[] btns = {"⏮\uFE0E", "▶\uFE0E", "⏭\uFE0E"};
        for (int i = 0; i < 3; i++) {
            final int actionIdx = i;
            TextView btn = makeText(btns[i], 18, theme.textPrimary, false);
            btn.setGravity(Gravity.CENTER);
            btn.setOnClickListener(v -> triggerMediaTransport(actionIdx));
            topRow.addView(btn, new LinearLayout.LayoutParams(dp(40), dp(40)));
            if (i == 1) mediaPlayBtn = btn;
        }
        card.addView(topRow);

        FrameLayout barTrack = new FrameLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(theme.isLight ? 0x22000000 : 0x33FFFFFF);
        trackBg.setCornerRadius(dp(2));
        barTrack.setBackground(trackBg);

        mediaProgressFill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(theme.accent);
        fillBg.setCornerRadius(dp(2));
        mediaProgressFill.setBackground(fillBg);
        mediaProgressFill.setPivotX(0f);
        barTrack.addView(mediaProgressFill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        trackLp.topMargin = dp(8);
        trackLp.rightMargin = dp(4);
        card.addView(barTrack, trackLp);

        mediaTimeView = makeText("", 11, theme.textSecondary, false);
        mediaTimeView.setPadding(0, dp(4), 0, 0);
        card.addView(mediaTimeView);
        card.setOnClickListener(v -> launchMediaPlayerApp());
        return card;
    }

    private LinearLayout buildSearchBarCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(theme.glassCard(dpUnit, 22));
        box.setPadding(dp(16), dp(10), dp(16), dp(10));
        box.setVisibility(View.GONE);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = makeText("🔍", 15, theme.accent, false);
        icon.setPadding(0, 0, dp(10), 0);
        inputRow.addView(icon);

        searchInput = new EditText(this);
        searchInput.setHint("搜应用 / 拼音 / 快捷方式 (如 扫一扫) / 算式 / 换算");
        searchInput.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x88000000);
        searchInput.setTextColor(theme.textPrimary);
        searchInput.setTypeface(typeface);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(15.5f));
        searchInput.setSingleLine(true);
        searchInput.setBackground(null);
        searchInput.setPadding(0, dp(4), 0, dp(4));
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        inputRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView pasteBtn = makeText("📋", 14, theme.textSecondary, false);
        pasteBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
        pasteBtn.setOnClickListener(v -> {
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
        inputRow.addView(pasteBtn);

        TextView closeBtn = makeText("✕", 16, theme.textSecondary, false);
        closeBtn.setPadding(dp(10), dp(4), dp(4), dp(4));
        closeBtn.setOnClickListener(v -> {
            if (searchInput.getText().length() > 0) {
                searchInput.setText("");
            } else {
                closeInstantSearch();
            }
        });
        inputRow.addView(closeBtn);
        box.addView(inputRow);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { rebuildStreamRows(); }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN);
            if (go) {
                for (int i = streamRows.size() - 1; i >= 0; i--) {
                    StreamRow r = streamRows.get(i);
                    if (r.app != null) {
                        launchApp(r.app);
                        return true;
                    }
                }
            }
            return go;
        });

        return box;
    }

    private void openInstantSearch() {
        if (!searchActive) {
            searchActive = true;
            searchBarCard.setVisibility(View.VISIBLE);
            searchBarCard.bringToFront();
            updateFirstPageExtrasVisibility();
            rebuildStreamRows();
            if (!streamRows.isEmpty()) streamListView.setSelection(streamRows.size() - 1);
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
        searchBarCard.setVisibility(View.GONE);
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
        dismissAppPopupCard(false);
        if (currentSheetDialog != null && currentSheetDialog.isShowing()) currentSheetDialog.dismiss();
        cooldownWaveBars();
        boolean needRebuild = searchActive || inAllAppsMode;
        if (searchActive) {
            searchActive = false;
            searchInput.setText("");
            searchBarCard.setVisibility(View.GONE);
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
        if (dateLunarView != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("M月d日 EEE", Locale.CHINESE);
            String dateStr = sdf.format(new java.util.Date());
            String lunar = (dm.lunarLine != null && !dm.lunarLine.isEmpty()) ? dm.lunarLine : "清风徐来";
            // 去除「·」分割符：以自然字距区隔，农历降为次级字重/透明度
            android.text.SpannableStringBuilder dsb = new android.text.SpannableStringBuilder(dateStr);
            dsb.append("   ");
            int lunarStart = dsb.length();
            dsb.append(lunar);
            dsb.setSpan(new android.text.style.TypefaceSpan("sans-serif"), lunarStart, dsb.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            dsb.setSpan(new android.text.style.ForegroundColorSpan(theme.isLight ? 0x99141824 : 0x99F1F5F9),
                    lunarStart, dsb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            dateLunarView.setText(dsb);
        }
        if (heroStatusBar != null) {
            heroStatusBar.updateMetrics(dm.batteryPct, dm.charging, dm.batteryTempC, dm.ramAvailGb);
        }
        refreshWeatherPill();
        refreshStickyNotePill();
        refreshRecentDock();
    }

    private String getEffectiveWeatherSummary() {
        String locWeather = WeatherSyncEngine.getCachedSummary(this);
        if (locWeather != null && !locWeather.isEmpty()) return locWeather;
        String notifWeather = NotificationHubService.weatherSummary;
        if (notifWeather != null && !notifWeather.isEmpty()) return notifWeather;
        return "";
    }

    private void refreshWeatherPill() {
        if (heroStatusBar == null) return;
        heroStatusBar.updateWeather(getEffectiveWeatherSummary());
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

        box.addView(makeText("🌤 开启首页实时天气", 18.5f, theme.textPrimary, true));
        TextView sub = makeText("授权定位后即可在首页顶部直接显示实时温度与天气图标，也可授权读取系统通知栏/流体云天气。", 12.5f, theme.textSecondary, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        box.addView(sub);

        TextView locBtn = makeSheetButton("📍 授权定位 · 直接显示实时天气（推荐）", () -> {
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
        subBtns.add(makeSheetButton("🔔 授权通知读取", () -> {
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
        if (pi != null) {
            try {
                launchedExternalApp = true;
                pi.send();
                return;
            } catch (Throwable ignored) {}
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
        String note = prefs.getString(K_STICKY_NOTE, "").trim();
        if (stickyNoteSummaryView != null) {
            if (note.isEmpty()) {
                stickyNoteSummaryView.setText("点击写下一句话备忘、取件码或今日待办...");
                stickyNoteSummaryView.setTextColor(theme.textSecondary);
            } else {
                stickyNoteSummaryView.setText(note);
                stickyNoteSummaryView.setTextColor(theme.textPrimary);
            }
        }
        if (stickyNotePill != null) {
            if (note.isEmpty()) {
                stickyNotePill.setVisibility(View.GONE);
            } else {
                stickyNotePill.setText("📌 " + note);
                stickyNotePill.setVisibility(View.VISIBLE);
            }
        }
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

        box.addView(makeText("📝 桌面速记便签", 18f, theme.textPrimary, true));
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
        btns.add(makeSheetButton("🗑 清空隐藏", () -> {
            prefs.edit().remove(K_STICKY_NOTE).apply();
            refreshStickyNotePill();
            dismiss.run();
        }));
        TextView saveBtn = makeSheetButton("✓ 保存便签", () -> {
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
        TextView titleTv = makeText("⚡ 流光桌面 · 本机实时状态", 18f, theme.textPrimary, true);
        hdrRow.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badgeTv = makeText("🛡️ 零联网", 11.2f, theme.accent, true);
        badgeTv.setBackground(theme.accentGlowPill(dpUnit, 12));
        badgeTv.setPadding(dp(9), dp(4), dp(9), dp(4));
        hdrRow.addView(badgeTv);
        box.addView(hdrRow);

        String lunarInfoText = (dm.lunarLine == null || dm.lunarLine.isEmpty() || dm.lunarLine.equals(dm.greeting))
                ? dm.greeting : (dm.greeting + " · " + dm.lunarLine);
        TextView lunarTv = makeText("📅 " + lunarInfoText, 12.8f, theme.textSecondary, false);
        lunarTv.setPadding(0, dp(4), 0, dp(10));
        box.addView(lunarTv);

        // 2x2 可视化仪表盘磁贴（彻底告别原生黑框与文字断行）
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        View batTile = buildStatusMetricTile(
                "🔋 电池与温度",
                dm.batteryPct + "% · " + (dm.charging ? "充电中" : "放电中"),
                String.format(Locale.ROOT, "%.1f°C · %d mV", dm.batteryTempC, dm.batteryVoltMv),
                dm.batteryPct,
                dm.batteryPct >= 25 ? 0xFF10B981 : 0xFFF59E0B);
        View ramTile = buildStatusMetricTile(
                "🧠 运行内存 (RAM)",
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
                "💾 本机存储空间",
                String.format(Locale.ROOT, "剩余 %.1f GB", dm.storageFreeGb),
                String.format(Locale.ROOT, "已用 %d%% / 共 %.1fG", dm.storageUsedPct, dm.storageTotalGb),
                dm.storageUsedPct,
                0xFF8B5CF6);
        int favRatio = appCount > 0 ? Math.min(100, Math.max(12, favCount * 100 / Math.min(20, appCount))) : 35;
        View statTile = buildStatusMetricTile(
                "📱 桌面应用统计",
                "共 " + appCount + " 个应用",
                "常用 " + favCount + " 个 · 累计启动 " + totalLaunches + " 次",
                favRatio,
                theme.accent);
        LinearLayout.LayoutParams t2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t2Lp.rightMargin = dp(8);
        row2.addView(romTile, t2Lp);
        row2.addView(statTile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row2, r2Lp);

        TextView privBanner = makeText("🛡️ 隐私承诺：仅在授权天气时请求气象数据，其余所有配置与统计 100% 留在本机。", 11.8f, theme.textSecondary, false);
        privBanner.setBackground(theme.sheetTileCard(dpUnit, 14));
        privBanner.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.topMargin = dp(10);
        box.addView(privBanner, pbLp);

        List<TextView> actions = new ArrayList<>();
        actions.add(makeSheetButton("📝 添加/修改便签", () -> {
            dismiss.run();
            promptEditStickyNote();
        }));
        actions.add(makeSheetButton("🎛 打开控制中心", () -> {
            dismiss.run();
            openControlCenterSheet();
        }));
        TextView okBtn = makeSheetButton("✓ 完成", dismiss);
        okBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        okBtn.setTextColor(0xFFFFFFFF);
        actions.add(okBtn);
        addGridButtons(box, actions, 3);

        holder[0] = showBottomSheetDialog(box);
    }

    private final List<String> currentDockKeys = new ArrayList<>();
    private final Runnable refreshRecentDockTask = this::refreshRecentDock;

    void postRefreshRecentDockDelayed(long delayMs) {
        uiHandler.removeCallbacks(refreshRecentDockTask);
        uiHandler.postDelayed(refreshRecentDockTask, delayMs);
    }

    boolean isRecentDockVisible() {
        return firstPageExtrasBox != null && firstPageExtrasBox.getVisibility() == View.VISIBLE;
    }

    void refreshRecentDock() {
        if (recentDockCard == null || dockRow1 == null || dockRow2 == null) return;
        if (!isRecentDockVisible()) return;
        if (allApps.isEmpty()) {
            recentDockCard.setVisibility(View.GONE);
            currentDockKeys.clear();
            return;
        }

        // 🚀 核心流畅度优化（120Hz 满帧回弹）：
        // 将全部应用列表复制快照并在后台 computation 线程中计算权重分数与多级排序，
        // 彻底杜绝主线程在退出应用返回桌面动画高峰期的 CPU 计算与 I/O 阻塞！
        final Set<String> hidden = new HashSet<>(getStringList(K_HIDDEN));
        final List<String> activePkgs = GestureLockService.getActivePackages();
        final List<String> recents = getStringList(K_RECENT_KEYS);
        final List<String> favList = getStringList(K_FAVS);
        final List<AppEntry> allAppsSnapshot = new ArrayList<>(allApps);

        AppExecutors.getInstance().computation().execute(() -> {
            JSONObject launchCounts = getLaunchCountsJson();
            List<AppEntry> candidateApps = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            // 1. 优先提取无障碍后台捕获的最近活跃应用
            for (String pkg : activePkgs) {
                AppEntry a = findAppByPackageInSnapshot(allAppsSnapshot, pkg);
                if (a != null && !hidden.contains(a.idKey) && seen.add(a.idKey)) {
                    candidateApps.add(a);
                }
            }

            // 2. 其余所有已安装非隐藏应用，按启动频次与常用权重降序排序
            List<AppEntry> others = new ArrayList<>();
            for (AppEntry a : allAppsSnapshot) {
                if (!hidden.contains(a.idKey) && !seen.contains(a.idKey)) {
                    others.add(a);
                }
            }

            Collections.sort(others, (x, y) -> {
                int cx = launchCounts.optInt(x.idKey, 0);
                int cy = launchCounts.optInt(y.idKey, 0);
                int fx = favList.contains(x.idKey) ? 500 : 0;
                int fy = favList.contains(y.idKey) ? 500 : 0;
                int rx = recents.indexOf(x.idKey);
                int ry = recents.indexOf(y.idKey);
                int rBonusX = rx >= 0 ? Math.max(0, 100 - rx * 10) : 0;
                int rBonusY = ry >= 0 ? Math.max(0, 100 - ry * 10) : 0;
                int scoreX = cx * 10 + fx + rBonusX;
                int scoreY = cy * 10 + fy + rBonusY;
                return Integer.compare(scoreY, scoreX);
            });

            for (AppEntry a : others) {
                if (seen.add(a.idKey)) {
                    candidateApps.add(a);
                }
            }

            // 取出按频次排序的前 7 个应用
            final List<AppEntry> top7 = new ArrayList<>();
            final List<String> newKeys = new ArrayList<>();
            for (AppEntry a : candidateApps) {
                top7.add(a);
                newKeys.add(a.idKey);
                if (top7.size() == 7) break;
            }

            // 切回主线程比对 Diff 并按需局部构建 UI
            uiHandler.post(() -> {
                if (recentDockCard == null || dockRow1 == null || dockRow2 == null) return;
                if (top7.isEmpty()) {
                    recentDockCard.setVisibility(View.GONE);
                    currentDockKeys.clear();
                    return;
                }

                // 🚀 核心优化：智能 Diff 比较
                // 若高频应用列表与排列顺序没有发生变化，直接 0 操作，绝不执行 removeAllViews 与重新布局！
                if (newKeys.equals(currentDockKeys) && recentDockCard.getVisibility() == View.VISIBLE && dockRow1.getChildCount() > 0) {
                    return;
                }

                currentDockKeys.clear();
                currentDockKeys.addAll(newKeys);

                dockRow1.removeAllViews();
                dockRow2.removeAllViews();

                // 第一排：放前 4 个高频应用
                for (int i = 0; i < Math.min(4, top7.size()); i++) {
                    AppEntry app = top7.get(i);
                    dockRow1.addView(buildDockAppCell(app), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                }

                // 第二排：放第 5、6、7 个高频应用
                for (int i = 4; i < top7.size(); i++) {
                    AppEntry app = top7.get(i);
                    dockRow2.addView(buildDockAppCell(app), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                }

                // 若总数不足 7 个应用，用空 View 补齐前 3 列以保持等宽
                while (dockRow2.getChildCount() < 3 && top7.size() < 7) {
                    View dummy = new View(MainActivity.this);
                    dockRow2.addView(dummy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                }

                // 第二排最后一个（第 8 个）：专属「后台直达」多任务应用图标
                dockRow2.addView(buildRecentsOverviewCell(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                recentDockCard.setVisibility(View.VISIBLE);
                if (firstPageExtrasBox != null) firstPageExtrasBox.requestLayout();
            });
        });
    }

    private View buildDockAppCell(final AppEntry app) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(getShapedIcon(app));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        cell.addView(iv, ilp);

        TextView tv = new TextView(this);
        tv.setText(app.label);
        tv.setTextColor(theme.textPrimary);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.2f));
        tv.setTypeface(typeface);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        cell.addView(tv, tlp);

        cell.setOnClickListener(v -> {
            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(60)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                    .start();
            scheduleAppLaunch(app);
        });
        cell.setOnLongClickListener(v -> {
            showAppActionSheet(app);
            return true;
        });
        return cell;
    }

    void openColorOsRecents() {
        List<AppEntry> recents = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String pkg : GestureLockService.getActivePackages()) {
            AppEntry a = findAppByPackage(pkg);
            if (a != null && seen.add(a.idKey)) recents.add(a);
        }
        for (String k : getStringList(K_RECENT_KEYS)) {
            AppEntry a = findAppByKey(k);
            if (a != null && seen.add(a.idKey)) recents.add(a);
        }
        for (AppEntry a : allApps) {
            if (recents.size() >= 7) break;
            if (seen.add(a.idKey)) recents.add(a);
        }
        ColorOsRecentsOverlay.show(this, recents, app -> launchApp(app), () -> {
            refreshRecentDock();
        });
    }

    private View buildRecentsOverviewCell() {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(createRecentsTaskIcon(dp(44)));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        cell.addView(iv, ilp);

        TextView tv = new TextView(this);
        tv.setText("后台直达");
        tv.setTextColor(theme.accent);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.2f));
        tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(4);
        cell.addView(tv, tlp);

        cell.setOnClickListener(v -> {
            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(60)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                    .start();
            openColorOsRecents();
        });
        cell.setOnLongClickListener(v -> {
            showDeviceStatusDialog();
            return true;
        });
        return cell;
    }

    private Drawable createRecentsTaskIcon(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        int shape = prefs.getInt(K_ICON_SHAPE, 0);
        Path clip = new Path();
        if (shape == 3) { // 极简正圆
            clip.addCircle(size * 0.5f, size * 0.5f, size * 0.47f, Path.Direction.CW);
        } else if (shape == 2) { // One UI / HyperOS
            buildSuperEllipsePath(clip, size * 0.025f, size * 0.95f, 0.34);
        } else { // 超椭圆 / 液态玻璃等
            buildSuperEllipsePath(clip, size * 0.025f, size * 0.95f, 0.42);
        }

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(theme.isLight ? 0x22000000 : 0x33FFFFFF);
        cv.drawPath(clip, bgPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.2f));
        strokePaint.setColor((theme.accent & 0x00FFFFFF) | 0x88000000);
        cv.drawPath(clip, strokePaint);

        // 绘制质感多任务卡片图标（前置卡片 + 悬浮景深卡片）
        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setStyle(Paint.Style.STROKE);
        cardPaint.setStrokeWidth(dp(1.8f));
        cardPaint.setColor(theme.accent);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor((theme.accent & 0x00FFFFFF) | 0x33000000);

        float cr = dp(4f);
        RectF rBack = new RectF(size * 0.22f, size * 0.20f, size * 0.60f, size * 0.68f);
        cv.drawRoundRect(rBack, cr, cr, fillPaint);
        cv.drawRoundRect(rBack, cr, cr, cardPaint);

        RectF rFront = new RectF(size * 0.40f, size * 0.32f, size * 0.78f, size * 0.80f);
        fillPaint.setColor((theme.textPrimary & 0x00FFFFFF) | 0x28000000);
        cardPaint.setColor(theme.textPrimary);
        cv.drawRoundRect(rFront, cr, cr, fillPaint);
        cv.drawRoundRect(rFront, cr, cr, cardPaint);

        return new BitmapDrawable(getResources(), bmp);
    }

    // ---------- 双向应用行手势与双击锁屏 ----------
    private float touchStartX, touchStartY;
    private int swipedRowPos = -1;
    private View swipedRowView = null;
    private boolean rowSwiping = false;
    private boolean touchDownOnAppRow = false;
    private boolean touchDownInHeader = false;
    private Runnable pendingAppLaunchRunnable = null;
    private long lastTapDownTime = 0;

    private void scheduleAppLaunch(final AppEntry app) {
        cancelPendingAppLaunch();
        pendingAppLaunchRunnable = () -> {
            pendingAppLaunchRunnable = null;
            launchApp(app);
        };
        uiHandler.postDelayed(pendingAppLaunchRunnable, 140);
    }

    private void cancelPendingAppLaunch() {
        if (pendingAppLaunchRunnable != null) {
            uiHandler.removeCallbacks(pendingAppLaunchRunnable);
            pendingAppLaunchRunnable = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Pop-up 浮层展示期间：事件交由浮层自身（空白处点击即关闭），不再触发列表手势
        if (appPopupScrim != null) {
            return super.dispatchTouchEvent(ev);
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            rightRowSwiping = false;
            long nowDown = SystemClock.uptimeMillis();
            if (nowDown - lastTapDownTime < 340) {
                // 🚀 连续点击（双击）进行中，第一时间取消第一次点击可能预约的应用启动，防止误开应用
                cancelPendingAppLaunch();
            }
            lastTapDownTime = nowDown;

            rowSwiping = false;
            swipedRowPos = -1;
            swipedRowView = null;
            touchDownOnAppRow = false;
            touchStartX = ev.getRawX();
            touchStartY = ev.getRawY();

            if (streamListView != null) {
                int[] loc = new int[2];
                streamListView.getLocationOnScreen(loc);
                touchDownInHeader = touchStartY < loc[1];
                int w = getWindow().getDecorView().getWidth();
                int edgeGuard = (inAllAppsMode || searchActive) ? dp(48) : dp(38);
                boolean inWaveEdge = (touchStartX > w - edgeGuard) || (touchStartX < edgeGuard);
                // 若按在悬浮搜索圆钮上，直接豁免边缘波浪与列表滑动拦截，确保左下/右下点击 100% 灵敏
                if (coinButton != null && coinButton.getVisibility() == View.VISIBLE) {
                    int[] cLoc = new int[2];
                    coinButton.getLocationOnScreen(cLoc);
                    if (touchStartX >= cLoc[0] - dp(8) && touchStartX <= cLoc[0] + coinButton.getWidth() + dp(8)
                            && touchStartY >= cLoc[1] - dp(8) && touchStartY <= cLoc[1] + coinButton.getHeight() + dp(8)) {
                        return super.dispatchTouchEvent(ev);
                    }
                }
                if (!inWaveEdge && !searchActive) {
                    int pos = streamListView.pointToPosition(
                            (int) (touchStartX - loc[0]), (int) (touchStartY - loc[1]));
                    if (pos != AdapterView.INVALID_POSITION && pos < streamRows.size() && streamRows.get(pos).app != null) {
                        View child = streamListView.getChildAt(pos - streamListView.getFirstVisiblePosition());
                        swipedRowPos = pos;
                        swipedRowView = child;
                        touchDownOnAppRow = true;

                        // 🚀 精确识别：如果点击落在应用行右侧大片空白区域，判定为桌面空白处
                        if (child instanceof ViewGroup) {
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
        } else if (swipedRowPos >= 0) {
            // 🚀 v2.6.0 右滑直达 Pop-up：touchSlop + 极角隔离（dx > slop && dx > |dy|*2），优先于左滑逻辑判定
            AppEntry swipeApp = swipedRowPos < streamRows.size() ? streamRows.get(swipedRowPos).app : null;
            if (!(rowSwiping && !rightRowSwiping) && handleItemSwipeRight(swipedRowView, swipeApp, ev)) {
                return true;
            }
            float dx = ev.getRawX() - touchStartX;
            float dy = ev.getRawY() - touchStartY;
            if (!rowSwiping && action == MotionEvent.ACTION_MOVE && -dx > dp(20) && -dx > Math.abs(dy) * 2.0f) {
                rowSwiping = true;
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                if (gestureDetector != null) gestureDetector.onTouchEvent(cancel);
                cancel.recycle();
            }
            if (rowSwiping) {
                if (action == MotionEvent.ACTION_MOVE) {
                    if (swipedRowView != null) {
                        float clamped = Math.max(-dp(110), Math.min(0f, dx)) * 0.68f;
                        swipedRowView.setTranslationX(clamped);
                    }
                    if (swipeHintPill != null && swipedRowPos < streamRows.size()) {
                        AppEntry targetApp = streamRows.get(swipedRowPos).app;
                        if (targetApp != null && -dx > dp(24)) {
                            String pkg = targetApp.component.getPackageName();
                            List<NotificationHubService.NotifItem> nl = NotificationHubService.allByPkg.get(pkg);
                            boolean hasClearable = false;
                            if (nl != null) {
                                for (NotificationHubService.NotifItem ni : nl) {
                                    if (ni.isClearable) { hasClearable = true; break; }
                                }
                            }
                            if (hasClearable) {
                                swipeHintPill.setText("✕  清除该应用通知");
                            } else {
                                boolean isFav = getStringList(K_FAVS).contains(targetApp.idKey);
                                swipeHintPill.setText(isFav ? "★  移出常用应用" : "☆  加入常用应用");
                            }
                            swipeHintPill.setVisibility(View.VISIBLE);
                            swipeHintPill.setAlpha(Math.min(1f, (-dx - dp(20)) / dp(48)));
                        }
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (swipedRowView != null) {
                        swipedRowView.animate().translationX(0f).setDuration(190).start();
                    }
                    if (swipeHintPill != null) {
                        swipeHintPill.setVisibility(View.GONE);
                    }
                    if (action == MotionEvent.ACTION_UP && swipedRowPos < streamRows.size()) {
                        AppEntry targetApp = streamRows.get(swipedRowPos).app;
                        if (targetApp != null && dx < -dp(68) && targetApp != virtualSettingsApp) {
                            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            String pkg = targetApp.component.getPackageName();
                            int cleared = NotificationHubService.cancelAllForPackage(pkg);
                            if (cleared > 0) {
                                Toast.makeText(this, "已清除「" + targetApp.label + "」" + cleared + " 条通知", Toast.LENGTH_SHORT).show();
                            } else {
                                toggleFavoriteQuickly(targetApp);
                            }
                        }
                    }
                    rowSwiping = false;
                    swipedRowPos = -1;
                }
                return true;
            }
        }
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // ---------- v2.7.0 单手右滑直达 Pop-up（无底栏大胶囊干扰，过阈值单次微震确认） ----------
    private boolean rightRowSwiping = false;
    private boolean rightSwipeThresholdTicked = false;
    private int touchSlopPx = 0;

    /**
     * 列表项右滑手势识别：dx > touchSlop 且 dx > |dy|*2.0（极角隔离，避开纵向滚动与系统边缘手势）。
     * 触发后接管事件流：列表项以 min(dx*0.4, 48dp) 跟手位移，跨越 56dp 触发一次轻脆微震，松手呼出 Pop-up。
     *
     * @return true 表示事件已被右滑手势消费（调用方应直接返回 true）
     */
    private boolean handleItemSwipeRight(View itemView, AppEntry appInfo, MotionEvent event) {
        if (appInfo == null || itemView == null) return false;
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
            if (swipeHintPill != null) swipeHintPill.setVisibility(View.GONE);
            boolean trigger = action == MotionEvent.ACTION_UP && dx > dp(56);
            rightRowSwiping = false;
            rightSwipeThresholdTicked = false;
            rowSwiping = false;
            swipedRowPos = -1;
            if (trigger) {
                getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                showAppPopupCard(itemView, appInfo);
            }
            return true;
        }
        return true;
    }

    // ---------- v2.7.0 锚点跟随 Pop-up 浮层（API 31+ 背景模糊 / 软键盘自动避让 / 通知与快捷方式 / 文件夹统一体系） ----------
    private FrameLayout workspaceRoot;
    private FrameLayout appPopupScrim;

    private void applyPopupBackdropBlur(boolean enable) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            android.graphics.RenderEffect fx = enable
                    ? android.graphics.RenderEffect.createBlurEffect(
                            12f * dpUnit, 12f * dpUnit, android.graphics.Shader.TileMode.CLAMP)
                    : null;
            if (workspaceMainColumn != null) workspaceMainColumn.setRenderEffect(fx);
            if (backdropView != null) backdropView.setRenderEffect(fx);
        } catch (Throwable ignored) {}
    }

    private void dismissAppPopupCard(boolean animate) {
        final FrameLayout scrim = appPopupScrim;
        if (scrim == null) return;
        appPopupScrim = null;
        applyPopupBackdropBlur(false);
        hideSoftKeyboard();
        final Runnable remove = () -> {
            ViewGroup p = (ViewGroup) scrim.getParent();
            if (p != null) p.removeView(scrim);
        };
        if (animate) {
            scrim.animate().alpha(0f).setDuration(120).withEndAction(remove).start();
        } else {
            scrim.animate().cancel();
            remove.run();
        }
    }

    private GradientDrawable popupCardBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(theme.isLight ? 0xE6F4F5F7 : 0xE61A1C20);       // 微透明、无厚边框、无硬投影
        g.setCornerRadius(dp(20));
        g.setStroke(Math.max(1, Math.round(0.5f * dpUnit)), theme.isLight ? 0x66FFFFFF : 0x24FFFFFF); // 0.5dp 高光内轮廓
        return g;
    }

    /** 在目标列表项旁弹出与其几何锚定的无边框微卡片：自动上翻避让底部，左缘对齐图标中心 +64dp，右侧留 16dp。 */
    private void showAppPopupCard(final View anchorView, final AppEntry app) {
        if (app == null || anchorView == null || workspaceRoot == null) return;
        if (app == virtualSettingsApp) {
            openControlCenterSheet();
            return;
        }
        dismissAppPopupCard(false);
        final FrameLayout root = workspaceRoot;
        final String pkg = app.component != null ? app.component.getPackageName() : "";

        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(8));

        // 1. 头部：图标 + 名称 + 打开
        LinearLayout hdr = new LinearLayout(this);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        ImageView hIcon = new ImageView(this);
        hIcon.setImageDrawable(getShapedIcon(app));
        LinearLayout.LayoutParams hiLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        hiLp.rightMargin = dp(10);
        hdr.addView(hIcon, hiLp);
        TextView hTitle = makeText(app.label, 15.5f, theme.textPrimary, true);
        hTitle.setSingleLine(true);
        hTitle.setEllipsize(TextUtils.TruncateAt.END);
        hdr.addView(hTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hdr.addView(makeText("打开 ›", 12.5f, theme.accent, true));
        hdr.setPadding(0, 0, 0, dp(4));
        hdr.setOnClickListener(v -> {
            dismissAppPopupCard(true);
            launchApp(app);
        });
        content.addView(hdr);

        // 2. 未读通知摘要（至多 3 条）：右滑 / ✕ 清除，支持内联快捷回复与常规动作胶囊
        final List<NotificationHubService.NotifItem> notifs = NotificationHubService.getAllByPackage(pkg);
        if (!notifs.isEmpty()) {
            LinearLayout nHdr = new LinearLayout(this);
            nHdr.setGravity(Gravity.CENTER_VERTICAL);
            TextView nTitle = makeText("💬 未读通知 " + notifs.size(), 11.8f, theme.accent, true);
            nTitle.setPadding(0, dp(10), 0, dp(4));
            nHdr.addView(nTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView clearAll = makeText("全部清除", 11.5f, theme.textSecondary, true);
            clearAll.setPadding(dp(8), dp(10), 0, dp(4));
            clearAll.setOnClickListener(v -> {
                int cleared = NotificationHubService.cancelAllForPackage(pkg);
                Toast.makeText(this, cleared > 0 ? "已清除全部通知" : "没有可清除的通知", Toast.LENGTH_SHORT).show();
                dismissAppPopupCard(true);
            });
            nHdr.addView(clearAll);
            content.addView(nHdr);

            int shown = 0;
            for (NotificationHubService.NotifItem n : notifs) {
                if (shown++ >= 3) break;
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(5);
                content.addView(buildPopupNotifRow(n), rlp);
            }
        }

        // 3. 动态快捷方式（API 25+，SecurityException 安全降级）
        final List<ShortcutInfo> shortcuts = queryPopupShortcuts(app);
        if (!shortcuts.isEmpty()) {
            TextView sTitle = makeText("⚡ 快捷方式", 11.8f, theme.accent, true);
            sTitle.setPadding(0, dp(10), 0, dp(2));
            content.addView(sTitle);
            for (final ShortcutInfo sc : shortcuts) {
                LinearLayout sRow = new LinearLayout(this);
                sRow.setGravity(Gravity.CENTER_VERTICAL);
                sRow.setPadding(0, dp(8), 0, dp(8));
                Drawable sIcon = null;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && launcherApps != null) {
                        sIcon = launcherApps.getShortcutIconDrawable(sc, getResources().getDisplayMetrics().densityDpi);
                    }
                } catch (Throwable ignored) {}
                if (sIcon != null) {
                    ImageView siv = new ImageView(this);
                    siv.setImageDrawable(sIcon);
                    LinearLayout.LayoutParams silp = new LinearLayout.LayoutParams(dp(22), dp(22));
                    silp.rightMargin = dp(10);
                    sRow.addView(siv, silp);
                }
                TextView sLabel = makeText(String.valueOf(sc.getShortLabel()), 13.5f, theme.textPrimary, false);
                sLabel.setSingleLine(true);
                sLabel.setEllipsize(TextUtils.TruncateAt.END);
                sRow.addView(sLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                sRow.setOnClickListener(v -> {
                    dismissAppPopupCard(true);
                    startPopupShortcut(sc, app);
                });
                content.addView(sRow);
            }
        }

        // 4. 底部轻量操作
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(8), 0, 0);
        boolean isFav = getStringList(K_FAVS).contains(app.idKey);
        TextView favBtn = makeText(isFav ? "★ 移出常用" : "☆ 加入常用", 12.5f, theme.textSecondary, true);
        favBtn.setPadding(0, dp(6), dp(14), dp(6));
        favBtn.setOnClickListener(v -> {
            dismissAppPopupCard(true);
            toggleFavoriteQuickly(app);
        });
        footer.addView(favBtn);
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView moreBtn = makeText("⋯ 更多", 12.5f, theme.textSecondary, true);
        moreBtn.setPadding(dp(14), dp(6), 0, dp(6));
        moreBtn.setOnClickListener(v -> {
            dismissAppPopupCard(false);
            showAppActionSheet(app);
        });
        footer.addView(moreBtn);
        content.addView(footer);

        // 卡片容器：圆角裁剪 + 内容过高时可滚动
        final ScrollView card = new ScrollView(this);
        card.setVerticalScrollBarEnabled(false);
        card.setOverScrollMode(View.OVER_SCROLL_NEVER);
        card.setBackground(popupCardBackground());
        card.setClipToOutline(true);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        card.setClickable(true);
        card.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(theme.isLight ? 0x14000000 : 0x2A000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismissAppPopupCard(true));

        // 几何锚定：左缘自然贴合应用文本区，右侧距屏幕边缘 16dp；纵向对齐列表项，靠近底部自动上翻，且随软键盘自动避让
        int rootW = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int rootH = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int[] rootLoc = new int[2];
        int[] aLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        anchorView.getLocationOnScreen(aLoc);
        int anchorLeft = Math.round(aLoc[0] - anchorView.getTranslationX()) - rootLoc[0];
        int anchorTop = aLoc[1] - rootLoc[1];
        int anchorH = Math.max(anchorView.getHeight(), dp(40));
        int iconCenterX = anchorLeft + dp(20);
        View iconView = findRowIconView(anchorView);
        if (iconView != null && iconView.getParent() instanceof View) {
            iconCenterX = anchorLeft + ((View) iconView.getParent()).getLeft()
                    + iconView.getLeft() + iconView.getWidth() / 2;
        }

        int minW = dp(248), maxW = dp(328), margin = dp(16);
        int left = iconCenterX + dp(32);
        if (rootW - left - margin < minW) left = rootW - margin - minW;
        left = Math.max(dp(12), left);
        int width = Math.max(minW, Math.min(maxW, rootW - left - margin));

        int topInset = currentTopInsetPx, botInset = currentNavBottomPx;
        try {
            android.view.WindowInsets wi = root.getRootWindowInsets();
            if (wi != null) {
                topInset = wi.getSystemWindowInsetTop();
                botInset = wi.getSystemWindowInsetBottom();
            }
        } catch (Throwable ignored) {}
        int availTop = topInset + dp(8);
        int availBottom = rootH - botInset - dp(12);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(dp(120), availBottom - availTop), View.MeasureSpec.AT_MOST));
        final int cardH = card.getMeasuredHeight();
        int top = anchorTop - dp(6);
        if (top + cardH > availBottom) {
            top = anchorTop + anchorH + dp(6) - cardH; // 靠近底部：向上翻转展开
        }
        final int finalTop = Math.max(availTop, Math.min(top, availBottom - cardH));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(width, cardH);
        clp.leftMargin = left;
        clp.topMargin = finalTop;
        scrim.addView(card, clp);

        // 软键盘弹出（点击内联快捷回复）时自动上移卡片，避免输入框被键盘遮挡
        final int safeTopLimit = availTop;
        final int totalRootH = rootH;
        scrim.setOnApplyWindowInsetsListener((sv, insets) -> {
            int imeBot = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    imeBot = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                } catch (Throwable ignored) {}
            } else {
                imeBot = insets.getSystemWindowInsetBottom();
            }
            int cardBottom = finalTop + cardH;
            int visibleBottom = totalRootH - imeBot - dp(12);
            float shiftY = 0f;
            if (imeBot > dp(100) && cardBottom > visibleBottom) {
                shiftY = -Math.min(cardBottom - visibleBottom, Math.max(0, finalTop - safeTopLimit));
            }
            card.animate().translationY(shiftY).setDuration(150).start();
            return insets;
        });

        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        appPopupScrim = scrim;
        applyPopupBackdropBlur(true);

        // 入场：从锚点左缘轻微内推 + 缩放 + 渐显
        card.setPivotX(0f);
        card.setPivotY(Math.max(0f, Math.min(cardH, anchorTop + anchorH * 0.5f - finalTop)));
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setTranslationX(-dp(12));
        card.setAlpha(0f);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(150).start();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** 原生 Niagara 风格文件夹锚点 Pop-up：原地展开带应用图标与未读点的微卡片。 */
    private void openFolderPopup(final View anchorView, final int folderIdx) {
        if (anchorView == null || workspaceRoot == null) {
            openFolderSheet(folderIdx);
            return;
        }
        final JSONObject fo = getFoldersArray().optJSONObject(folderIdx);
        if (fo == null) return;
        dismissAppPopupCard(false);

        final FrameLayout root = workspaceRoot;
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(10));

        LinearLayout hdr = new LinearLayout(this);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(0, 0, 0, dp(6));
        TextView title = makeText(fo.optString("name", "文件夹"), 15.5f, theme.textPrimary, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        hdr.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView editBtn = makeText("管理", 12.2f, theme.accent, true);
        editBtn.setPadding(dp(10), dp(4), dp(2), dp(4));
        editBtn.setOnClickListener(v -> {
            dismissAppPopupCard(false);
            promptEditFolder(folderIdx);
        });
        hdr.addView(editBtn);
        content.addView(hdr);

        JSONArray apps = fo.optJSONArray("apps");
        int count = 0;
        if (apps != null) {
            for (int i = 0; i < apps.length(); i++) {
                final AppEntry a = findAppByKey(apps.optString(i));
                if (a == null) continue;
                count++;
                LinearLayout aRow = new LinearLayout(this);
                aRow.setOrientation(LinearLayout.HORIZONTAL);
                aRow.setGravity(Gravity.CENTER_VERTICAL);
                aRow.setPadding(dp(2), dp(7), dp(4), dp(7));

                ImageView aiv = new ImageView(this);
                aiv.setImageDrawable(getShapedIcon(a));
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
                ilp.rightMargin = dp(12);
                aRow.addView(aiv, ilp);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView nameTv = makeText(a.label, 14.2f, theme.textPrimary, false);
                nameTv.setSingleLine(true);
                nameTv.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(nameTv);

                String pkg = a.component != null ? a.component.getPackageName() : "";
                NotificationHubService.NotifItem notif = prefs.getBoolean(K_INLINE_NOTIF, true)
                        ? NotificationHubService.latestByPkg.get(pkg) : null;
                if (notif != null) {
                    String preview = (notif.title.isEmpty() ? "" : notif.title + "：") + notif.text;
                    TextView subTv = makeText(preview, 11.4f, theme.textSecondary, false);
                    subTv.setSingleLine(true);
                    subTv.setEllipsize(TextUtils.TruncateAt.END);
                    col.addView(subTv);
                }
                aRow.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                if (notif == null && NotificationHubService.activePackages.contains(pkg)) {
                    View dot = new View(this);
                    GradientDrawable dbg = new GradientDrawable();
                    dbg.setShape(GradientDrawable.OVAL);
                    dbg.setColor(theme.accent);
                    dot.setBackground(dbg);
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
                    dlp.leftMargin = dp(8);
                    aRow.addView(dot, dlp);
                }

                aRow.setOnClickListener(v -> {
                    dismissAppPopupCard(true);
                    launchApp(a, v);
                });
                aRow.setOnLongClickListener(v -> {
                    dismissAppPopupCard(false);
                    showAppActionSheet(a);
                    return true;
                });
                content.addView(aRow);
            }
        }
        if (count == 0) {
            TextView empty = makeText("文件夹为空，点击右上角「管理」添加应用", 12.5f, theme.textSecondary, false);
            empty.setPadding(0, dp(8), 0, dp(8));
            content.addView(empty);
        }

        final ScrollView card = new ScrollView(this);
        card.setVerticalScrollBarEnabled(false);
        card.setOverScrollMode(View.OVER_SCROLL_NEVER);
        card.setBackground(popupCardBackground());
        card.setClipToOutline(true);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        card.setClickable(true);
        card.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(theme.isLight ? 0x14000000 : 0x2A000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismissAppPopupCard(true));

        int rootW = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int rootH = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int[] rootLoc = new int[2];
        int[] aLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        anchorView.getLocationOnScreen(aLoc);
        int anchorTop = aLoc[1] - rootLoc[1];
        int anchorH = Math.max(anchorView.getHeight(), dp(44));

        int margin = dp(16);
        int width = Math.max(dp(248), Math.min(dp(320), rootW - dp(64)));
        int left = Math.max(dp(18), Math.min(rootW - margin - width, dp(42)));
        int availTop = currentTopInsetPx + dp(12);
        int availBottom = rootH - currentNavBottomPx - dp(16);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(dp(120), availBottom - availTop), View.MeasureSpec.AT_MOST));
        int cardH = card.getMeasuredHeight();
        int top = anchorTop - dp(6);
        if (top + cardH > availBottom) top = anchorTop + anchorH + dp(6) - cardH;
        top = Math.max(availTop, Math.min(top, availBottom - cardH));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(width, cardH);
        clp.leftMargin = left;
        clp.topMargin = top;
        scrim.addView(card, clp);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        appPopupScrim = scrim;
        applyPopupBackdropBlur(true);

        card.setPivotX(0f);
        card.setPivotY(Math.max(0f, Math.min(cardH, anchorTop + anchorH * 0.5f - top)));
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setTranslationX(-dp(10));
        card.setAlpha(0f);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(150).start();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** 单条通知微卡片：标题/摘要/相对时间、✕ 与右滑清除、常规动作胶囊、内联快捷回复。 */
    private View buildPopupNotifRow(final NotificationHubService.NotifItem n) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(theme.subtleGhostPill(dpUnit, 14));
        row.setPadding(dp(11), dp(8), dp(8), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (n.title != null && !n.title.isEmpty()) {
            TextView t = makeText(n.title, 12.8f, theme.textPrimary, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
        }
        if (n.text != null && !n.text.isEmpty()) {
            TextView t = makeText(n.text, 12.2f, theme.textPrimary, false);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
        }
        col.addView(makeText(NotificationHubService.formatRelativeTime(n.postTime), 10.6f, theme.textSecondary, false));
        top.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (n.isClearable) {
            TextView x = makeText("✕", 13.5f, theme.textSecondary, true);
            x.setPadding(dp(10), dp(4), dp(6), dp(4));
            x.setOnClickListener(v -> dismissPopupNotifRow(row, n));
            top.addView(x);
        }
        row.addView(top);

        // 常规 Action 胶囊（标记已读 / 暂停 …）
        if (n.plainActions != null && !n.plainActions.isEmpty()) {
            LinearLayout caps = new LinearLayout(this);
            caps.setPadding(0, dp(6), 0, 0);
            for (final Notification.Action act : n.plainActions) {
                TextView cap = makeText(String.valueOf(act.title), 11.6f, theme.accent, true);
                cap.setBackground(theme.accentGlowPill(dpUnit, 12));
                cap.setPadding(dp(10), dp(4), dp(10), dp(4));
                cap.setSingleLine(true);
                cap.setEllipsize(TextUtils.TruncateAt.END);
                cap.setOnClickListener(v -> {
                    try {
                        act.actionIntent.send();
                        Toast.makeText(this, "已执行：" + act.title, Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(this, "操作已失效", Toast.LENGTH_SHORT).show();
                    }
                    dismissAppPopupCard(true);
                });
                LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                capLp.rightMargin = dp(6);
                caps.addView(cap, capLp);
            }
            row.addView(caps);
        }

        // 内联快捷回复（RemoteInput）
        if (NotificationHubService.getQuickReplyAction(n) != null) {
            LinearLayout replyRow = new LinearLayout(this);
            replyRow.setGravity(Gravity.CENTER_VERTICAL);
            replyRow.setPadding(0, dp(4), 0, 0);
            final EditText replyIn = new EditText(this);
            replyIn.setHint("直接回复…");
            replyIn.setTextColor(theme.textPrimary);
            replyIn.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x88000000);
            replyIn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.8f);
            replyIn.setSingleLine(true);
            replyIn.setBackground(null);
            replyIn.setPadding(0, dp(4), dp(6), dp(4));
            replyRow.addView(replyIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView send = makeText("发送", 12.5f, theme.accent, true);
            send.setPadding(dp(8), dp(4), dp(6), dp(4));
            send.setOnClickListener(v -> {
                String msg = replyIn.getText().toString().trim();
                if (msg.isEmpty()) return;
                if (NotificationHubService.sendQuickReply(this, n, msg)) {
                    Toast.makeText(this, "已发送回复", Toast.LENGTH_SHORT).show();
                    dismissAppPopupCard(true);
                } else {
                    Toast.makeText(this, "回复失败，请进入应用回复", Toast.LENGTH_SHORT).show();
                }
            });
            replyRow.addView(send);
            row.addView(replyRow);
        }

        if (n.contentIntent != null) {
            row.setOnClickListener(v -> {
                try {
                    launchedExternalApp = true;
                    n.contentIntent.send();
                    dismissAppPopupCard(true);
                } catch (Throwable t) {
                    launchedExternalApp = false;
                }
            });
        }
        if (n.isClearable) attachSwipeToDismiss(row, n);
        return row;
    }

    private void dismissPopupNotifRow(final View row, NotificationHubService.NotifItem n) {
        if (!NotificationHubService.dismissNotification(n.key)) {
            Toast.makeText(this, "无法清除该通知", Toast.LENGTH_SHORT).show();
            row.animate().translationX(0f).alpha(1f).setDuration(160).start();
            return;
        }
        row.animate().translationX(row.getWidth()).alpha(0f).setDuration(160).withEndAction(() -> {
            ViewGroup p = (ViewGroup) row.getParent();
            if (p != null) p.removeView(row);
        }).start();
    }

    /** 通知微条目右滑清除（同样使用 touchSlop + 极角隔离，避免与卡片纵向滚动冲突）。 */
    private void attachSwipeToDismiss(final View row, final NotificationHubService.NotifItem n) {
        final float[] down = new float[2];
        final boolean[] swiping = new boolean[1];
        row.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                down[0] = e.getRawX();
                down[1] = e.getRawY();
                swiping[0] = false;
                return false;
            }
            float dx = e.getRawX() - down[0];
            float dy = e.getRawY() - down[1];
            if (a == MotionEvent.ACTION_MOVE) {
                if (!swiping[0]) {
                    if (touchSlopPx <= 0) touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();
                    if (dx > touchSlopPx && dx > Math.abs(dy) * 2.0f) {
                        swiping[0] = true;
                        v.setPressed(false);
                        v.cancelLongPress();
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (swiping[0]) {
                    v.setTranslationX(Math.max(0f, dx));
                    v.setAlpha(1f - Math.min(0.6f, dx / dp(180)));
                    return true;
                }
                return false;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (swiping[0]) {
                    swiping[0] = false;
                    if (a == MotionEvent.ACTION_UP && dx > dp(72)) {
                        dismissPopupNotifRow(v, n);
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(160).start();
                    }
                    return true;
                }
            }
            return false;
        });
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
            launchedExternalApp = true;
            UserHandle uh = sc.getUserHandle() != null ? sc.getUserHandle()
                    : (app.user != null ? app.user : Process.myUserHandle());
            launcherApps.startShortcut(sc.getPackage(), sc.getId(), null, null, uh);
        } catch (Throwable t) {
            launchedExternalApp = false;
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
            Toast.makeText(this, "★ 已加入第一页常用应用：" + app.label, Toast.LENGTH_SHORT).show();
        }
        rebuildStreamRows();
    }

    private final GestureDetector.SimpleOnGestureListener screenGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return false;
            if (searchActive || !prefs.getBoolean(K_DOUBLE_TAP_LOCK, true)) return false;
            // 🚀 核心优化：在首页模式下，无论是常用应用区域、应用右侧空白、还是顶栏，双击均可 100% 熄屏！
            // 仅在「全部应用抽屉」模式下按在应用实体行时让位给列表滚动交互
            if (touchDownOnAppRow) return false;
            int w = getWindow().getDecorView().getWidth();
            if (e.getRawX() > w - dp(38) || e.getRawX() < dp(32)) return false;
            long now = SystemClock.uptimeMillis();
            if (now - lastDoubleTapLock < 2000) return true;
            lastDoubleTapLock = now;
            cancelPendingAppLaunch(); // 确保双击时绝对不误开任何被点到的应用
            wasOnHomeBeforeLock = true;
            launchedExternalApp = false;
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

            // 在第二页（全部应用）或搜索模式下，非边缘中央区域的水平快滑可快速返回第一页常用应用
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
                // 第一页左手/中央下拉默认直接唤出极速搜索（可在控制中心切换为拉开通知栏）
                if (prefs.getBoolean(K_PULL_DOWN_SEARCH, true)) {
                    openInstantSearch();
                    return true;
                }
                if (prefs.getBoolean(K_SWIPE_DOWN_NOTIF, true)) {
                    expandSystemNotificationPanel();
                    return true;
                }
            }
            if (dy < 0 && touchDownInHeader) {
                openInstantSearch();
                return true;
            }
            if (dy < 0 && !inAllAppsMode && !searchActive) {
                switchStreamMode(true);
                return true;
            }
            return false;
        }
    };

    private boolean isListAtTop() {
        if (streamListView == null || streamListView.getChildCount() == 0) return true;
        return streamListView.getFirstVisiblePosition() == 0
                && streamListView.getChildAt(0).getTop() >= streamListView.getPaddingTop();
    }

    private void triggerScreenLock() {
        wasOnHomeBeforeLock = true;
        launchedExternalApp = false;
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

        box.addView(makeText("♿ " + featureName + " · 辅助手势授权", 17.5f, theme.textPrimary, true));
        TextView msg = makeText(
                "请在「无障碍 → 已下载的应用」中开启「流光桌面 · 手势双击锁屏与通知栏」。\n\n"
                + "• 该服务 100% 本地运行，仅执行锁屏与拉开通知栏动作，绝不读取屏幕内容。\n"
                + "• 如遇系统提示“受限制的设置”，请先点下方「解除受限设置」→ 右上角 ⋮ → 允许受限制的设置。",
                13f, theme.textSecondary, false);
        msg.setPadding(0, dp(8), 0, dp(12));
        box.addView(msg);

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("🔓 解除受限设置", () -> {
            dismiss.run();
            startIntentSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "请点右上角 ⋮ → 允许受限制的设置", Toast.LENGTH_LONG).show();
        }));
        TextView goBtn = makeSheetButton("✓ 去开启无障碍", () -> {
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
        final boolean byUsage = prefs.getBoolean(K_SORT_BY_USAGE, true);
        final JSONObject usage = byUsage ? getLaunchCountsJson() : new JSONObject();
        Collections.sort(list, (x, y) -> {
            int gx = WAVE_SYMBOLS.indexOf(x.groupChar);
            int gy = WAVE_SYMBOLS.indexOf(y.groupChar);
            if (gx != gy) return gx - gy;
            if (byUsage) {
                int ux = usage.optInt(x.idKey, 0);
                int uy = usage.optInt(y.idKey, 0);
                if (ux != uy) return uy - ux;
            }
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
                refreshRecentDock();
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
                String pkg = a.component.getPackageName().toLowerCase(Locale.ROOT);
                String lb = a.label.toLowerCase(Locale.ROOT);
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

    private void rebuildStreamRows() {
        streamRows.clear();
        letterRowIndex.clear();

        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        Set<String> hiddenSet = new HashSet<>(getStringList(K_HIDDEN));
        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry a : allApps) byKey.put(a.idKey, a);

        if (searchActive) {
            if (!query.isEmpty()) {
                // v2.7.0 贴底单手搜索排序：最佳匹配与计算结果置于列表底端，紧邻底部搜索栏与大拇指
                String calcOut = QuickCalcEngine.evaluateOrConvert(query);
                String rawSearchText = searchInput == null ? "" : searchInput.getText().toString().trim();
                if (!rawSearchText.isEmpty() && calcOut == null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_WEB_SEARCH, rawSearchText, null));
                }
                String cleanQ = query.replace(" ", "");
                // 匹配应用内快捷方式（如：扫一扫 / 付款码 / 乘车码）
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
                // 搜索框刚打开尚未输入时，将最近使用置于最底部紧贴拇指，上方衔接常用与全部应用
                Set<String> shown = new HashSet<>();
                List<AppEntry> recentEntries = new ArrayList<>();
                List<String> recents = getStringList(K_RECENT_KEYS);
                for (String rk : recents) {
                    AppEntry a = byKey.get(rk);
                    if (a != null && !hiddenSet.contains(rk) && shown.add(rk)) {
                        recentEntries.add(a);
                        if (recentEntries.size() >= 5) break;
                    }
                }
                List<AppEntry> otherEntries = new ArrayList<>();
                for (String fk : getStringList(K_FAVS)) {
                    AppEntry a = byKey.get(fk);
                    if (a != null && !hiddenSet.contains(fk) && shown.add(fk)) {
                        otherEntries.add(a);
                    }
                }
                for (AppEntry a : allApps) {
                    if (!hiddenSet.contains(a.idKey) && shown.add(a.idKey)) {
                        otherEntries.add(a);
                    }
                }
                if (!otherEntries.isEmpty()) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_SECTION, "★ 常用与全部应用", null));
                    for (int i = otherEntries.size() - 1; i >= 0; i--) {
                        streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, null, otherEntries.get(i)));
                    }
                }
                if (!recentEntries.isEmpty()) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_SECTION, "⚡ 最近使用", null));
                    for (int i = recentEntries.size() - 1; i >= 0; i--) {
                        streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, null, recentEntries.get(i)));
                    }
                }
            }
            rightWaveBar.setVisibility(View.GONE);
            leftGhostWaveBar.setVisibility(View.GONE);
            streamAdapter.notifyDataSetChanged();
            if (!streamRows.isEmpty() && streamListView != null) {
                streamListView.setSelection(streamRows.size() - 1);
            }
            return;
        }

        rightWaveBar.setVisibility(View.VISIBLE);
        rightWaveBar.setBackSwipePreferred(inAllAppsMode || searchActive);
        leftGhostWaveBar.setBackSwipePreferred(inAllAppsMode || searchActive);
        leftGhostWaveBar.setVisibility(searchActive ? View.GONE : View.VISIBLE);

        if (availableLetters.isEmpty()) {
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
        }

        if (!inAllAppsMode && !searchActive) {
            // 首页主态：经典 Niagara 纵向常用应用列表流 + 首页原生文件夹行
            List<String> favs = getStringList(K_FAVS);
            if (favs.isEmpty()) {
                autoPopulateInitialFavoritesIfEmpty();
                favs = getStringList(K_FAVS);
            }
            for (String fk : favs) {
                if (hiddenSet.contains(fk)) continue;
                AppEntry a = byKey.get(fk);
                if (a != null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_FAV_APP, null, a));
                }
            }
            JSONArray folders = getFoldersArray();
            for (int i = 0; i < folders.length(); i++) {
                JSONObject fo = folders.optJSONObject(i);
                if (fo != null) {
                    streamRows.add(new StreamRow(StreamRow.TYPE_FOLDER, fo.optString("name", "文件夹"), null, i));
                }
            }
            rightWaveBar.setAvailableData(new HashSet<>(availableLetters), new HashMap<>(letterTopPreview));
            leftGhostWaveBar.setAvailableData(new HashSet<>(availableLetters), new HashMap<>(letterTopPreview));
            streamAdapter.notifyDataSetChanged();
            applyOneHandedListPadding();
            return;
        }

        // 🚀 用户专属需求：「在所有应用内增加一条同一首字母的应用 按使用频次排序」
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

            // 1. 字母标头 (例如 "A")
            letterRowIndex.put(g, streamRows.size());
            streamRows.add(new StreamRow(StreamRow.TYPE_SECTION, String.valueOf(g), null));

            // 2. 🚀「竖列按使用频率排序即可 不要横列」：每个字母分组内的所有应用按使用频次降序排列（最高频排在最上方）
            List<AppEntry> sortedList = new ArrayList<>(list);
            Collections.sort(sortedList, (x, y) -> {
                int ux = launchCounts.optInt(x.idKey, 0);
                int uy = launchCounts.optInt(y.idKey, 0);
                if (ux != uy) return uy - ux;
                int c = x.fullPy.compareTo(y.fullPy);
                return c != 0 ? c : x.label.compareTo(y.label);
            });
            for (AppEntry a : sortedList) {
                streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, null, a));
            }
        }
        if (virtualSettingsApp != null) {
            streamRows.add(new StreamRow(StreamRow.TYPE_SECTION, "⚙", null));
            streamRows.add(new StreamRow(StreamRow.TYPE_ALL_APP, null, virtualSettingsApp));
        }

        rightWaveBar.setAvailableData(new HashSet<>(availableLetters), new HashMap<>(letterTopPreview));
        leftGhostWaveBar.setAvailableData(new HashSet<>(availableLetters), new HashMap<>(letterTopPreview));
        streamAdapter.notifyDataSetChanged();
        applyOneHandedListPadding();
    }

    private int matchRank(AppEntry a, String q) {
        String lb = a.label.toLowerCase(Locale.ROOT);
        if (lb.startsWith(q)) return 0;
        if (a.iniPy.startsWith(q)) return 1;
        if (a.t9Digits.startsWith(q)) return 2;
        if (a.fullPy.startsWith(q)) return 3;
        if (lb.contains(q)) return 4;
        if (a.iniPy.contains(q)) return 5;
        if (a.fullPy.contains(q)) return 6;
        if (a.component.getPackageName().toLowerCase(Locale.ROOT).contains(q)) return 7;
        return -1;
    }

    // ---------- 第一页常用应用栏与全部应用流渲染适配器 ----------
    private float getRowSpacingMultiplier() {
        int d = prefs.getInt(K_ROW_DENSITY, 1);
        return d == 0 ? 0.72f : d == 2 ? 1.35f : 1.0f;
    }

    final class StreamAdapter extends BaseAdapter {
        @Override public int getCount() { return streamRows.size(); }
        @Override public Object getItem(int position) { return streamRows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 9; }
        @Override public int getItemViewType(int position) { return streamRows.get(position).type; }
        @Override public boolean isEnabled(int position) {
            int t = streamRows.get(position).type;
            return t != StreamRow.TYPE_SECTION && t != StreamRow.TYPE_FAV_FOOTER && t != StreamRow.TYPE_GROUP_ROW;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            StreamRow row = streamRows.get(position);
            if (convertView != null && !waveCouplingActive && waveReboundAnim == null) {
                resetItemWaveTransform(convertView);
            }

            if (row.type == StreamRow.TYPE_CALC_RESULT) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
                tv.setText("🧮  = " + row.text + "   （点按复制）");
                tv.setTextColor(theme.accent);
                tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(18));
                tv.setBackground(theme.accentGlowPill(dpUnit, 18));
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                return tv;
            }

            if (row.type == StreamRow.TYPE_WEB_SEARCH) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
                tv.setText("🌐  在浏览器中搜索「" + row.text + "」");
                tv.setTextColor(theme.textPrimary);
                tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(14.2f));
                tv.setBackground(theme.subtleGhostPill(dpUnit, 16));
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                tv.setOnClickListener(v -> {
                    String url = "https://www.baidu.com/s?wd=" + Uri.encode(row.text);
                    startIntentSafely(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    closeInstantSearch();
                });
                return tv;
            }

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
                t1.setText("⚡ " + row.text);
                t1.setTextColor(theme.textPrimary);
                t1.setTypeface(typeface);
                t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(14.5f));
                t2.setText(row.app != null ? (row.app.label + " · 快捷功能") : "应用快捷功能");
                t2.setTextColor(theme.accent);
                t2.setTypeface(typeface);
                t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.5f));
                rowView.setOnClickListener(v -> {
                    if (row.shortcut != null) {
                        try {
                            launchedExternalApp = true;
                            launcherApps.startShortcut(row.shortcut, null, null);
                            hideSoftKeyboard();
                            uiHandler.postDelayed(() -> { if (searchActive) closeInstantSearch(); }, 350);
                        } catch (Throwable t) {
                            launchedExternalApp = false;
                            if (row.app != null) launchApp(row.app);
                        }
                    }
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
                List<Drawable> memberIcons = new ArrayList<>();
                boolean anyUnread = false;
                int cnt = appsArr != null ? appsArr.length() : 0;
                for (int i = 0; i < cnt; i++) {
                    AppEntry ma = findAppByKey(appsArr.optString(i));
                    if (ma != null) {
                        if (memberIcons.size() < 4) {
                            Drawable d = getShapedIcon(ma);
                            if (d != null) memberIcons.add(d);
                        }
                        if (ma.component != null && NotificationHubService.activePackages.contains(ma.component.getPackageName())) {
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
                t2.setText(cnt + " 个应用 · 胶囊文件夹");
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
                rowView.setOnClickListener(v -> openFolderPopup(v, fIdx));
                rowView.setOnLongClickListener(v -> { promptEditFolder(fIdx); return true; });
                return rowView;
            }

            // 第一页底部优化：双胶囊对称布局（左下角改为「⭐ 选择常用应用」，右侧「⠿ 全部应用 (N) ⌃」，彻底消除右侧挤成小点的第3胶囊）
            if (row.type == StreamRow.TYPE_FAV_FOOTER) {
                LinearLayout footer = new LinearLayout(MainActivity.this);
                footer.setOrientation(LinearLayout.HORIZONTAL);
                footer.setGravity(Gravity.CENTER_VERTICAL);
                footer.setPadding(dp(2), dp(16), dp(6), dp(12));

                TextView pickFavPill = makeText("编辑常用", 12.6f, theme.accent, true);
                pickFavPill.setGravity(Gravity.CENTER);
                pickFavPill.setBackground(theme.accentGlowPill(dpUnit, 14));
                pickFavPill.setPadding(dp(12), dp(9f), dp(12), dp(9f));
                pickFavPill.setSingleLine(true);
                pickFavPill.setEllipsize(TextUtils.TruncateAt.END);
                pickFavPill.setOnClickListener(v -> showPickFavoritesDialog());
                pickFavPill.setOnLongClickListener(v -> { showReorderFavoritesDialog(); return true; });

                TextView allAppsPill = makeText("全部应用 · " + allApps.size(), 12.6f, theme.textPrimary, false);
                allAppsPill.setGravity(Gravity.CENTER);
                allAppsPill.setBackground(theme.unifiedStatusBarBg(dpUnit));
                allAppsPill.setPadding(dp(12), dp(9f), dp(12), dp(9f));
                allAppsPill.setSingleLine(true);
                allAppsPill.setEllipsize(TextUtils.TruncateAt.END);
                allAppsPill.setOnClickListener(v -> switchStreamMode(true));

                LinearLayout.LayoutParams lpFav = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lpFav.rightMargin = dp(10);
                footer.addView(pickFavPill, lpFav);

                LinearLayout.LayoutParams lpAll = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                footer.addView(allAppsPill, lpAll);
                return footer;
            }

            if (row.type == StreamRow.TYPE_SECTION) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
                tv.setText(row.text);
                tv.setTextColor(theme.textPrimary);
                boolean isHighlighted = highlightedGroup != 0 && row.text.length() == 1 && row.text.charAt(0) == highlightedGroup;
                tv.setAlpha(highlightedGroup == 0 ? 0.65f : (isHighlighted ? 1.0f : 0.25f));
                tv.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13.5f));
                tv.setPadding(dp(4), dp(15 * getRowSpacingMultiplier()), 0, dp(4));
                return tv;
            }

            // 🚀 用户专属需求：「在所有应用内增加一条同一首字母的应用 按使用频次排序」
            if (row.type == StreamRow.TYPE_GROUP_ROW) {
                HorizontalScrollView hsv = (convertView instanceof HorizontalScrollView && "group_recents_row".equals(convertView.getTag()))
                        ? (HorizontalScrollView) convertView : null;
                LinearLayout container;
                if (hsv == null) {
                    hsv = new HorizontalScrollView(MainActivity.this);
                    hsv.setTag("group_recents_row");
                    hsv.setHorizontalScrollBarEnabled(false);
                    hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                    hsv.setClipToPadding(false);
                    hsv.setPadding(dp(2), dp(2), dp(24), dp(8));

                    container = new LinearLayout(MainActivity.this);
                    container.setOrientation(LinearLayout.HORIZONTAL);
                    container.setGravity(Gravity.CENTER_VERTICAL);
                    hsv.addView(container, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else {
                    container = (LinearLayout) hsv.getChildAt(0);
                    container.removeAllViews();
                }

                if (row.groupApps != null) {
                    for (final AppEntry app : row.groupApps) {
                        LinearLayout cell = new LinearLayout(MainActivity.this);
                        cell.setOrientation(LinearLayout.VERTICAL);
                        cell.setGravity(Gravity.CENTER_HORIZONTAL);
                        cell.setPadding(dp(6), dp(3), dp(6), dp(3));

                        ImageView iv = new ImageView(MainActivity.this);
                        iv.setImageDrawable(getShapedIcon(app));
                        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
                        cell.addView(iv, ilp);

                        TextView tv = new TextView(MainActivity.this);
                        tv.setText(app.label);
                        tv.setTextColor(theme.textPrimary);
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.0f));
                        tv.setTypeface(typeface);
                        tv.setSingleLine(true);
                        tv.setEllipsize(TextUtils.TruncateAt.END);
                        tv.setMaxWidth(dp(68));
                        tv.setGravity(Gravity.CENTER);
                        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        tlp.topMargin = dp(3);
                        cell.addView(tv, tlp);

                        cell.setOnClickListener(v -> {
                            if (isWaveScrubbing) return;
                            v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(60)
                                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                                    .start();
                            if (!inAllAppsMode && !searchActive) {
                                scheduleAppLaunch(app);
                            } else {
                                launchApp(app);
                            }
                        });
                        cell.setOnLongClickListener(v -> {
                            if (isWaveScrubbing) return true;
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            showAppActionSheet(app);
                            return true;
                        });

                        container.addView(cell);
                    }
                }
                return hsv;
            }

            boolean isFav = (row.type == StreamRow.TYPE_FAV_APP);
            LinearLayout outerWrap;
            LinearLayout cardRow;
            View accentRail;
            ImageView iconView;
            TextView titleView, subNotifView;
            View unreadBadge;

            if (convertView instanceof LinearLayout && "app_row_v2".equals(convertView.getTag())) {
                outerWrap = (LinearLayout) convertView;
                cardRow = (LinearLayout) outerWrap.getChildAt(0);
                accentRail = cardRow.getChildAt(0);
                iconView = (ImageView) cardRow.getChildAt(1);
                LinearLayout textCol = (LinearLayout) cardRow.getChildAt(2);
                titleView = (TextView) textCol.getChildAt(0);
                subNotifView = (TextView) textCol.getChildAt(1);
                unreadBadge = cardRow.getChildAt(3);
            } else {
                outerWrap = new LinearLayout(MainActivity.this);
                outerWrap.setOrientation(LinearLayout.VERTICAL);
                outerWrap.setTag("app_row_v2");

                cardRow = new LinearLayout(MainActivity.this);
                cardRow.setOrientation(LinearLayout.HORIZONTAL);
                cardRow.setGravity(Gravity.CENTER_VERTICAL);

                accentRail = new View(MainActivity.this);
                LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(dp(3.2f), dp(24f));
                railLp.rightMargin = dp(10);

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

                unreadBadge = new View(MainActivity.this);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(theme.accent);
                unreadBadge.setBackground(dotBg);

                cardRow.addView(accentRail, railLp);
                cardRow.addView(iconView);
                cardRow.addView(textCol, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
                dotLp.leftMargin = dp(8);
                cardRow.addView(unreadBadge, dotLp);

                cardRow.setOnClickListener(v -> {
                    if (isWaveScrubbing || (android.os.SystemClock.uptimeMillis() - lastWaveReleaseMs) < 220L) {
                        return;
                    }
                    Object tag = v.getTag();
                    if (!(tag instanceof AppEntry)) return;
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(55)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                            .start();
                    launchApp((AppEntry) tag, v);
                });
                cardRow.setOnLongClickListener(v -> {
                    if (isWaveScrubbing) return true;
                    Object tag = v.getTag();
                    if (!(tag instanceof AppEntry)) return true;
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAppActionSheet((AppEntry) tag);
                    return true;
                });

                outerWrap.addView(cardRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            final AppEntry app = row.app;
            cardRow.setTag(app);

            boolean showIcons = prefs.getBoolean(K_SHOW_ICONS, true);
            int iconBaseDp = isFav ? 40 : 35; // 首页常用图标精致 40dp，视觉比例更协调
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
            titleView.setText(app.label);
            String pkg = app.component.getPackageName();
            boolean useFavCards = isFav && prefs.getBoolean(K_FAV_CARDS, false);
            boolean useFavRail = isFav && prefs.getBoolean(K_FAV_RAIL, true);
            boolean hasUnread = NotificationHubService.activePackages.contains(pkg);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int reservedW = dp(38 + 24 + (useFavRail ? 14 : 0) + (showIcons ? (iconBaseDp * Math.min(textScale, 1.15f) + 14) : 0)
                    + (useFavCards ? 28 : 12) + (hasUnread ? 15 : 0));
            int availTextW = Math.max(dp(210), screenW - reservedW);
            titleView.setMaxWidth(availTextW);
            subNotifView.setMaxWidth(availTextW);

            outerWrap.setAlpha(isDimmed ? 0.30f : 1.0f);

            NotificationHubService.NotifItem notif = prefs.getBoolean(K_INLINE_NOTIF, true)
                    ? NotificationHubService.latestByPkg.get(pkg) : null;
            Integer notifCountObj = NotificationHubService.countByPkg.get(pkg);
            int notifCount = notifCountObj != null ? notifCountObj : 0;
            boolean hasNotif = (notif != null);

            accentRail.setVisibility(View.GONE);

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

            // 已有内联通知摘要时不再重复叠加圆点，保持原生 Niagara 视觉克制
            unreadBadge.setVisibility((hasUnread && !hasNotif) ? View.VISIBLE : View.GONE);

            float mul = getRowSpacingMultiplier();
            if (useFavCards) {
                cardRow.setBackground(hasNotif ? cachedFavCardNotif : cachedFavCardNormal);
                int vPad = dp(6.5f * mul);
                cardRow.setPadding(dp(10), vPad, dp(14), vPad);
                outerWrap.setPadding(0, dp(2.2f * mul), dp(4), dp(2.2f * mul));
            } else {
                // 无卡片模式下不因未读通知改变 padding/背景，确保所有常用应用图标绝对左对齐
                cardRow.setBackground(null);
                int vPad = dp((isFav ? 8.5f : 6.0f) * mul);
                cardRow.setPadding(dp(2), vPad, dp(12), vPad);
                outerWrap.setPadding(0, 0, 0, 0);
            }
            return outerWrap;
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

    private void launchApp(AppEntry app) {
        launchApp(app, null);
    }

    private void launchApp(AppEntry app, View sourceView) {
        if (app == virtualSettingsApp) {
            hideSoftKeyboard();
            openControlCenterSheet();
            return;
        }

        // 🚀 专门适配 ColorOS 小布助手 AI：拦截并使用 ColorOS 专属助手通道唤醒
        if (isXiaoBuApp(app)) {
            launchedExternalApp = true;
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
            launchedExternalApp = true;

            // 🚀 1. 传入源图标锚点与平滑展开动画，让进出应用的系统过渡更跟手
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
                // 🚀 1. 系统快捷方式：优先使用 LauncherApps 原生启动！
                // 这让浏览器自身注册的 PWA / WebApp 意图（WebappLauncherActivity）直接以独立卡片与独立窗口唤醒，绝不掉进浏览器标签页！
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
                // 🚀 2. 若原生快捷方式启动失败，或者属于自定义反重力/独立 Web 快捷方式，使用 PWA 独立窗口规范启动
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

            // 🚀 2. 启动记录放入后台线程计算，主线程 0 负载，彻底解决应用推出/进入卡顿！
            final AppEntry launchedApp = app;
            AppExecutors.getInstance().diskIO().execute(() -> recordAppLaunch(launchedApp));

            hideSoftKeyboard();
            uiHandler.postDelayed(() -> {
                if (searchActive) closeInstantSearch();
            }, 380);
        } catch (Throwable t) {
            // 🚀 启动异常容错：若常规启动被系统拒绝且属于小布助手，转由专属通道唤醒
            if (isXiaoBuApp(app) && launchColorOsXiaoBu()) {
                final AppEntry launchedApp = app;
                AppExecutors.getInstance().diskIO().execute(() -> recordAppLaunch(launchedApp));
                hideSoftKeyboard();
                return;
            }
            launchedExternalApp = false;
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
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private TextView makeToggleSheetButton(String prefix, boolean active, String onLabel, String offLabel, final Runnable onClick) {
        String text = prefix + "：" + (active ? onLabel : offLabel);
        TextView btn = makeText(text, 12.6f, active ? theme.accent : theme.textPrimary, active);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(active ? theme.accentGlowPill(dpUnit, 15) : theme.sheetTileCard(dpUnit, 15));
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

    private void showAppActionSheet(final AppEntry app) {
        if (app == virtualSettingsApp) {
            openControlCenterSheet();
            return;
        }
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        LinearLayout hdr = new LinearLayout(this);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(getShapedIcon(app));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(48), dp(48));
        ilp.rightMargin = dp(14);
        hdr.addView(ic, ilp);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.addView(makeText(app.label, 18, theme.textPrimary, true));
        int launches = getLaunchCountsJson().optInt(app.idKey, 0);
        titleCol.addView(makeText("累计启动 " + launches + " 次 · " + app.component.getPackageName(), 11.5f, theme.textSecondary, false));
        hdr.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView openBtn = makeSheetButton("打开", () -> { dismiss.run(); launchApp(app); });
        openBtn.setPadding(dp(16), dp(9), dp(16), dp(9));
        hdr.addView(openBtn);
        box.addView(hdr);

        String pkg = app.component != null ? app.component.getPackageName() : "";
        final List<NotificationHubService.NotifItem> notifs = NotificationHubService.allByPkg.get(pkg);
        if (notifs != null && !notifs.isEmpty()) {
            LinearLayout nHdr = new LinearLayout(this);
            nHdr.setGravity(Gravity.CENTER_VERTICAL);
            TextView nTitle = makeText("💬 应用通知 (" + notifs.size() + ")", 12.5f, theme.accent, true);
            nTitle.setPadding(dp(4), dp(16), 0, dp(2));
            nHdr.addView(nTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView clearAllBtn = makeText("全部清除", 11.8f, theme.textSecondary, true);
            clearAllBtn.setPadding(dp(8), dp(14), dp(4), dp(2));
            clearAllBtn.setOnClickListener(v -> {
                NotificationHubService.cancelAllForPackage(pkg);
                Toast.makeText(this, "已清除全部通知", Toast.LENGTH_SHORT).show();
                dismiss.run();
            });
            nHdr.addView(clearAllBtn);
            box.addView(nHdr);

            for (final NotificationHubService.NotifItem notif : notifs) {
                LinearLayout nCard = new LinearLayout(this);
                nCard.setOrientation(LinearLayout.VERTICAL);
                nCard.setBackground(theme.glassCard(dpUnit, 16));
                nCard.setPadding(dp(14), dp(10), dp(12), dp(10));

                LinearLayout topN = new LinearLayout(this);
                topN.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout textN = new LinearLayout(this);
                textN.setOrientation(LinearLayout.VERTICAL);
                if (!notif.title.isEmpty()) textN.addView(makeText(notif.title, 13.8f, theme.textPrimary, true));
                textN.addView(makeText(notif.text, 13.0f, theme.textPrimary, false));
                topN.addView(textN, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                if (notif.isClearable) {
                    TextView xBtn = makeText("✕", 14f, theme.textSecondary, true);
                    xBtn.setGravity(Gravity.CENTER);
                    xBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
                    xBtn.setOnClickListener(v -> {
                        NotificationHubService.cancelByKey(notif.key);
                        nCard.setVisibility(View.GONE);
                    });
                    topN.addView(xBtn);
                }
                nCard.addView(topN);

                if (notif.contentIntent != null) {
                    nCard.setOnClickListener(v -> {
                        try {
                            launchedExternalApp = true;
                            notif.contentIntent.send();
                            dismiss.run();
                        } catch (Throwable ignored) {
                            launchedExternalApp = false;
                        }
                    });
                }

                if (notif.quickReplyAction != null) {
                    LinearLayout replyRow = new LinearLayout(this);
                    replyRow.setGravity(Gravity.CENTER_VERTICAL);
                    final EditText replyIn = new EditText(this);
                    replyIn.setHint("在此直接输入回复…");
                    replyIn.setTextColor(theme.textPrimary);
                    replyIn.setHintTextColor(theme.textSecondary);
                    replyIn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                    replyRow.addView(replyIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    TextView sendBtn = makeSheetButton("发送", () -> {
                        String msg = replyIn.getText().toString().trim();
                        if (!msg.isEmpty() && dispatchDirectReply(notif, msg)) {
                            Toast.makeText(this, "已发送回复", Toast.LENGTH_SHORT).show();
                            dismiss.run();
                        }
                    });
                    replyRow.addView(sendBtn);
                    nCard.addView(replyRow);
                }
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nlp.topMargin = dp(6);
                box.addView(nCard, nlp);
            }
        }

        List<ShortcutInfo> shortcuts = queryAppShortcuts(app);
        if (!shortcuts.isEmpty()) {
            addSheetSectionHeader(box, "⚡ 快捷功能直达 (App Shortcuts)");
            List<TextView> sBtns = new ArrayList<>();
            for (final ShortcutInfo sc : shortcuts) {
                CharSequence lb = sc.getShortLabel();
                sBtns.add(makeSheetButton("⚡ " + (lb == null ? "" : lb), () -> {
                    dismiss.run();
                    try {
                        launchedExternalApp = true;
                        launcherApps.startShortcut(sc, null, null);
                    } catch (Throwable t) {
                        launchedExternalApp = false;
                        Toast.makeText(this, "无法打开该快捷方式", Toast.LENGTH_SHORT).show();
                    }
                }));
            }
            addGridButtons(box, sBtns, 2);
        }

        addSheetSectionHeader(box, "🛠 第一页常用与桌面管理");
        final List<String> favs = getStringList(K_FAVS);
        final boolean isFav = favs.contains(app.idKey);
        List<TextView> mBtns = new ArrayList<>();
        mBtns.add(makeSheetButton(isFav ? "★ 移出常用栏" : "☆ 加入第一页常用", () -> {
            dismiss.run();
            toggleFavoriteQuickly(app);
        }));
        if (isFav && favs.indexOf(app.idKey) > 0) {
            mBtns.add(makeSheetButton("▲ 上移一位", () -> {
                int i = favs.indexOf(app.idKey);
                Collections.swap(favs, i, i - 1);
                putStringList(K_FAVS, favs);
                dismiss.run();
                rebuildStreamRows();
            }));
        }
        if (isFav && favs.indexOf(app.idKey) < favs.size() - 1) {
            mBtns.add(makeSheetButton("▼ 下移一位", () -> {
                int i = favs.indexOf(app.idKey);
                Collections.swap(favs, i, i + 1);
                putStringList(K_FAVS, favs);
                dismiss.run();
                rebuildStreamRows();
            }));
        }
        mBtns.add(makeSheetButton("✎ 自定义名称", () -> { dismiss.run(); promptRenameApp(app); }));
        mBtns.add(makeSheetButton("🎨 自定义图标", () -> { dismiss.run(); showCustomAppIconDialog(app); }));
        mBtns.add(makeSheetButton("📁 放入文件夹", () -> { dismiss.run(); promptAddAppToFolder(app); }));
        mBtns.add(makeSheetButton("ⓘ 应用详情", () -> {
            dismiss.run();
            try {
                launchedExternalApp = true;
                launcherApps.startAppDetailsActivity(app.component, app.user, null, null);
            } catch (Throwable t) { launchedExternalApp = false; }
        }));
        mBtns.add(makeSheetButton("🙈 隐藏应用", () -> {
            dismiss.run();
            List<String> hid = getStringList(K_HIDDEN);
            if (!hid.contains(app.idKey)) hid.add(app.idKey);
            putStringList(K_HIDDEN, hid);
            favs.remove(app.idKey);
            putStringList(K_FAVS, favs);
            rebuildStreamRows();
            Toast.makeText(this, "已隐藏，可在控制中心恢复", Toast.LENGTH_SHORT).show();
        }));
        TextView uninst = makeSheetButton("🗑 卸载应用", () -> {
            dismiss.run();
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.getPackageName()));
            i.putExtra(Intent.EXTRA_USER, app.user);
            startIntentSafely(i);
        });
        uninst.setTextColor(0xFFEF4444);
        mBtns.add(uninst);
        addGridButtons(box, mBtns, 3);

        holder[0] = showBottomSheetDialog(box);
    }

    private void applyWallpaperAndThemePreset(final int styleIdx) {
        prefs.edit().putInt(K_THEME_IDX, styleIdx).apply();
        Toast.makeText(this, "✨ 已切换为「" + AuroraTheme.SHORT_PALETTE_NAMES[styleIdx] + "」并同步昼夜壁纸", Toast.LENGTH_SHORT).show();
        AppExecutors.getInstance().computation().execute(() -> {
            try {
                int phase = AuroraTheme.resolveSolarPhase(this);
                boolean sysNight = (phase == WeatherSyncEngine.SOLAR_NIGHT);
                Bitmap bmp = AuroraBackdropView.renderAuroraWallpaper(this, styleIdx);
                AuroraBackdropView.saveAndApplySystemWallpaper(this, bmp);
                prefs.edit()
                        .putInt(K_WALL_STYLE_IDX, styleIdx)
                        .putInt("wall_applied_phase_v2", phase)
                        .putBoolean(K_WALL_APPLIED_NIGHT, sysNight)
                        .apply();
            } catch (Throwable ignored) {}
        });
        recreate();
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
            @Override public int getPinnedWidgetCount() {
                return widgetHelper != null ? widgetHelper.getPinnedWidgets(prefs).size() : 0;
            }
            @Override public void showDeviceStatus() { showDeviceStatusDialog(); }
            @Override public void showPickFavorites() { showPickFavoritesDialog(); }
            @Override public void showReorderFavorites() { showReorderFavoritesDialog(); }
            @Override public void applyThemePreset(int styleIdx) { applyWallpaperAndThemePreset(styleIdx); }
            @Override public void pickGalleryWallpaper() {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                try { launchedExternalApp = true; startActivityForResult(pick, REQ_PICK_WALL); }
                catch (Throwable t) { launchedExternalApp = false; }
            }
            @Override public void onDimPercentChanged(int pct) {
                prefs.edit().putInt(K_DIM_PCT, pct).apply();
                if (backdropView != null) backdropView.updateDimPercent(pct);
            }
            @Override public void onWallLockOnlyChanged(boolean lockOnly) {
                prefs.edit().putBoolean("wall_lock_only_v1", lockOnly).apply();
                int styleIdx = Math.max(0, Math.min(5, prefs.getInt(K_WALL_STYLE_IDX, prefs.getInt(K_THEME_IDX, 0))));
                applyWallpaperAndThemePreset(styleIdx);
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
                if (streamAdapter != null) streamAdapter.notifyDataSetChanged();
            }
            @Override public void onUse24HChanged(boolean use24h) {
                prefs.edit().putBoolean(K_USE_24H, use24h).apply();
                if (mainClockView != null) {
                    String fmt = use24h ? "HH:mm" : "h:mm";
                    mainClockView.setFormat24Hour(fmt);
                    mainClockView.setFormat12Hour(fmt);
                }
            }
            @Override public void onStatusBarFullChanged(boolean full) {
                prefs.edit().putBoolean(K_STATUS_BAR_FULL, full).apply();
                if (heroStatusBar != null) heroStatusBar.setFullMode(full);
            }
            @Override public void onHeroHighContrastChanged(boolean highContrast) {
                prefs.edit().putBoolean(K_HERO_HIGH_CONTRAST, highContrast).apply();
                if (heroStatusBar != null) {
                    heroStatusBar.setAestheticStyle(highContrast
                            ? HeroStatusBarView.STYLE_HIGH_CONTRAST : HeroStatusBarView.STYLE_AIRY);
                }
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
                try { launchedExternalApp = true; startActivityForResult(i, REQ_EXPORT_JSON); }
                catch (Throwable t) { launchedExternalApp = false; }
            }
            @Override public void importBackup() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*");
                try { launchedExternalApp = true; startActivityForResult(i, REQ_IMPORT_JSON); }
                catch (Throwable t) { launchedExternalApp = false; }
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
        showSingleChoiceDialog("🎨 选择第三方图标包", labels, sel, idx -> {
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

        box.addView(makeText("🎨 为「" + app.label + "」自定义图标", 17.5f, theme.textPrimary, true));
        box.addView(makeText("来自图标包：" + packPkg, 12f, theme.textSecondary, false));

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("↺ 恢复默认图标", () -> {
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

        box.addView(makeText("🧩 首页原生桌面微件 (Widgets)", 18f, theme.textPrimary, true));
        TextView tip = makeText("可添加系统时钟、天气、便签、待办等原生小组件到首页顶部，长按微件可调高度或移除。", 12.2f, theme.textSecondary, false);
        tip.setPadding(0, dp(3), 0, dp(10));
        box.addView(tip);

        List<WidgetHostHelper.PinnedWidget> pinned = widgetHelper.getPinnedWidgets(prefs);
        if (!pinned.isEmpty()) {
            addSheetSectionHeader(box, "已添加到首页 (" + pinned.size() + ")");
            List<TextView> remBtns = new ArrayList<>();
            for (final WidgetHostHelper.PinnedWidget pw : pinned) {
                remBtns.add(makeSheetButton("🗑 移除微件 #" + pw.id + " (" + pw.heightDp + "dp)", () -> {
                    widgetHelper.removeWidget(prefs, pw.id);
                    refreshHomeWidgets();
                    dismiss.run();
                    showWidgetManagerDialog();
                }));
            }
            addGridButtons(box, remBtns, 1);
        }

        addSheetSectionHeader(box, "＋ 选择要添加的系统微件");
        final List<AppWidgetProviderInfo> providers = widgetHelper.getAllProviders();
        final PackageManager pm = getPackageManager();
        List<TextView> pBtns = new ArrayList<>();
        int cap = 0;
        for (final AppWidgetProviderInfo info : providers) {
            if (info == null || info.provider == null) continue;
            CharSequence lb = info.loadLabel(pm);
            String title = (lb != null && lb.length() > 0) ? lb.toString().trim() : info.provider.getShortClassName();
            pBtns.add(makeSheetButton("🧩 " + title, () -> {
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
        showSingleChoiceDialog("🔘 悬浮按钮轻点动作", COIN_ACTIONS, cur, idx -> {
            prefs.edit().putInt(K_COIN_TAP_ACTION, idx).apply();
            applyCoinButtonConfig();
            Toast.makeText(MainActivity.this, "已设为：" + COIN_ACTIONS[idx], Toast.LENGTH_SHORT).show();
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

    private void showThemePickerDialog() {
        String[] names = new String[AuroraTheme.PRESETS.length];
        for (int i = 0; i < names.length; i++) names[i] = AuroraTheme.PRESETS[i].name;
        int cur = Math.max(0, Math.min(AuroraTheme.PRESETS.length - 1, prefs.getInt(K_THEME_IDX, AuroraTheme.IDX_DEFAULT)));
        showSingleChoiceDialog("🌓 选择昼夜交替壁纸 / 主题模式", names, cur, idx -> {
            if (idx < 6) {
                applyWallpaperAndThemePreset(idx);
            } else {
                prefs.edit().putInt(K_THEME_IDX, idx).apply();
                recreate();
            }
        });
    }

    private void showWallpaperPickerDialog() {
        String[] opts = {
            "⛰️ 山岚叠翠 · 大地（日出日落护眼壁纸）",
            "🌊 流光水韵 · 灵动（日出日落护眼壁纸）",
            "🏜️ 静谧沙丘 · 光影（日出日落护眼壁纸）",
            "☁️ 云海天际 · 晴空（日出日落护眼壁纸）",
            "📜 宣纸墨韵 · 东方（日出日落护眼壁纸）",
            "🌙 纯色柔光 · 护眼（日出日落护眼壁纸）",
            "📷 从本机相册选择图片…"
        };
        showSingleChoiceDialog("🖼 统一设置系统桌面与锁屏壁纸", opts,
                Math.max(0, Math.min(5, prefs.getInt(K_THEME_IDX, 0))), w -> {
            if (w == 6) {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                try { launchedExternalApp = true; startActivityForResult(pick, REQ_PICK_WALL); }
                catch (Throwable t) { launchedExternalApp = false; }
                return;
            }
            applyWallpaperAndThemePreset(w);
        });
    }

    private void showPickFavoritesDialog() {
        final Set<String> hidden = new HashSet<>(getStringList(K_HIDDEN));
        final List<AppEntry> candidates = new ArrayList<>();
        for (AppEntry a : allApps) if (!hidden.contains(a.idKey)) candidates.add(a);
        final List<String> curFavs = getStringList(K_FAVS);
        final Set<String> selectedKeys = new LinkedHashSet<>(curFavs);

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        // 1. 顶部标题栏 + 已选常用计数徽章
        LinearLayout topHeader = new LinearLayout(this);
        topHeader.setOrientation(LinearLayout.HORIZONTAL);
        topHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleTv = makeText("⭐ 勾选第一页常用应用", 18f, theme.textPrimary, true);
        topHeader.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView countBadge = makeText("已选 " + selectedKeys.size() + " 个", 12f, theme.accent, true);
        countBadge.setBackground(theme.accentGlowPill(dpUnit, 10));
        countBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        topHeader.addView(countBadge);
        box.addView(topHeader);

        TextView tip = makeText("可点击下方字母栏快速跳转/筛选，或直接输入字母查找", 11.8f, theme.textSecondary, false);
        tip.setPadding(0, dp(2), 0, dp(8));
        box.addView(tip);

        // 2. 字母与关键字快速搜索栏
        final EditText searchBox = new EditText(this);
        searchBox.setHint("🔍 按字母(如 wx)或应用名即时查找...");
        searchBox.setHintTextColor((theme.textSecondary & 0x00FFFFFF) | 0x88000000);
        searchBox.setTextColor(theme.textPrimary);
        searchBox.setTextSize(13.2f);
        searchBox.setTypeface(typeface);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(theme.isLight ? 0x14000000 : 0x24FFFFFF);
        sbg.setCornerRadius(dp(12));
        searchBox.setBackground(sbg);
        searchBox.setPadding(dp(12), dp(7), dp(12), dp(7));
        box.addView(searchBox);

        // 3. 收集所有候选应用实际存在的首字母并构建字母栏
        final List<String> letters = new ArrayList<>();
        letters.add("全部");
        letters.add("★已选");
        for (char c = 'A'; c <= 'Z'; c++) {
            for (AppEntry a : candidates) {
                if (a.groupChar == c) {
                    letters.add(String.valueOf(c));
                    break;
                }
            }
        }
        for (AppEntry a : candidates) {
            if (a.groupChar == '#') {
                letters.add("#");
                break;
            }
        }

        // 4. 横向滑动字母快捷筛选条
        HorizontalScrollView letterScroll = new HorizontalScrollView(this);
        letterScroll.setHorizontalScrollBarEnabled(false);
        letterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout letterRow = new LinearLayout(this);
        letterRow.setOrientation(LinearLayout.HORIZONTAL);
        letterRow.setGravity(Gravity.CENTER_VERTICAL);
        letterRow.setPadding(0, dp(8), 0, dp(8));

        final String[] curFilterLetter = new String[] { "全部" };
        final List<TextView> letterPills = new ArrayList<>();

        final LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);

        final TextView saveBtn = makeSheetButton("✓ 保存常用 (" + selectedKeys.size() + ")", null);

        final Runnable refreshGrid = new Runnable() {
            @Override
            public void run() {
                gridContainer.removeAllViews();
                String filter = curFilterLetter[0];
                String query = searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);

                List<AppEntry> matched = new ArrayList<>();
                for (AppEntry a : candidates) {
                    if ("★已选".equals(filter)) {
                        if (!selectedKeys.contains(a.idKey)) continue;
                    } else if (!"全部".equals(filter)) {
                        if (filter.length() == 1 && a.groupChar != filter.charAt(0)) continue;
                    }

                    if (!query.isEmpty()) {
                        boolean hit = false;
                        if (a.label != null && a.label.toLowerCase(Locale.ROOT).contains(query)) hit = true;
                        else if (a.iniPy != null && a.iniPy.toLowerCase(Locale.ROOT).contains(query)) hit = true;
                        else if (a.fullPy != null && a.fullPy.toLowerCase(Locale.ROOT).contains(query)) hit = true;
                        else if (query.length() == 1 && Character.toUpperCase(query.charAt(0)) == a.groupChar) hit = true;
                        if (!hit) continue;
                    }
                    matched.add(a);
                }

                if (matched.isEmpty()) {
                    TextView emptyTv = makeText("无匹配应用，请尝试其他字母或清空搜索", 12.5f, theme.textSecondary, false);
                    emptyTv.setGravity(Gravity.CENTER);
                    emptyTv.setPadding(0, dp(24), 0, dp(24));
                    gridContainer.addView(emptyTv);
                    return;
                }

                List<TextView> itemBtns = new ArrayList<>();
                for (AppEntry app : matched) {
                    final AppEntry curApp = app;
                    final boolean isChecked = selectedKeys.contains(curApp.idKey);
                    final TextView btn = makeText(
                            (isChecked ? "★ " : "☆ ") + curApp.label,
                            13.2f,
                            isChecked ? theme.accent : theme.textPrimary,
                            isChecked);
                    btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    btn.setSingleLine(true);
                    btn.setEllipsize(TextUtils.TruncateAt.END);
                    btn.setBackground(isChecked ? theme.accentGlowPill(dpUnit, 14) : theme.sheetTileCard(dpUnit, 14));
                    btn.setPadding(dp(12), dp(10), dp(12), dp(10));
                    btn.setOnClickListener(v -> {
                        if (selectedKeys.contains(curApp.idKey)) {
                            selectedKeys.remove(curApp.idKey);
                        } else {
                            selectedKeys.add(curApp.idKey);
                        }
                        boolean nowChecked = selectedKeys.contains(curApp.idKey);
                        btn.setText((nowChecked ? "★ " : "☆ ") + curApp.label);
                        btn.setTextColor(nowChecked ? theme.accent : theme.textPrimary);
                        btn.setTypeface(nowChecked ? Typeface.create(typeface, Typeface.BOLD) : typeface);
                        btn.setBackground(nowChecked ? theme.accentGlowPill(dpUnit, 14) : theme.sheetTileCard(dpUnit, 14));
                        countBadge.setText("已选 " + selectedKeys.size() + " 个");
                        saveBtn.setText("✓ 保存常用 (" + selectedKeys.size() + ")");
                        if ("★已选".equals(curFilterLetter[0])) {
                            run();
                        }
                    });
                    itemBtns.add(btn);
                }
                addGridButtons(gridContainer, itemBtns, 2);
            }
        };

        // 渲染字母筛选胶囊
        for (int i = 0; i < letters.size(); i++) {
            final String l = letters.get(i);
            final TextView pill = makeText(l, 12.0f, theme.textSecondary, false);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(11), dp(5), dp(11), dp(5));
            pill.setBackground(theme.subtleGhostPill(dpUnit, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            pill.setLayoutParams(lp);

            pill.setOnClickListener(v -> {
                curFilterLetter[0] = l;
                for (TextView p : letterPills) {
                    boolean active = l.equals(p.getText().toString());
                    p.setTextColor(active ? theme.accent : theme.textSecondary);
                    p.setTypeface(active ? Typeface.create(typeface, Typeface.BOLD) : typeface);
                    p.setBackground(active ? theme.accentGlowPill(dpUnit, 10) : theme.subtleGhostPill(dpUnit, 10));
                }
                refreshGrid.run();
            });

            if (i == 0) {
                pill.setTextColor(theme.accent);
                pill.setTypeface(Typeface.create(typeface, Typeface.BOLD));
                pill.setBackground(theme.accentGlowPill(dpUnit, 10));
            }
            letterPills.add(pill);
            letterRow.addView(pill);
        }
        letterScroll.addView(letterRow);
        box.addView(letterScroll);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshGrid.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 初始填充应用网格
        box.addView(gridContainer);
        refreshGrid.run();

        // 5. 底部保存与调整排序按钮
        List<TextView> actions = new ArrayList<>();
        actions.add(makeSheetButton("↕ 调整顺序", () -> {
            dismiss.run();
            showReorderFavoritesDialog();
        }));

        saveBtn.setOnClickListener(v -> {
            List<String> updated = new ArrayList<>();
            for (String k : curFavs) if (selectedKeys.contains(k)) updated.add(k);
            for (AppEntry a : candidates) {
                if (selectedKeys.contains(a.idKey) && !updated.contains(a.idKey)) {
                    updated.add(a.idKey);
                }
            }
            putStringList(K_FAVS, updated);
            inAllAppsMode = false;
            updateFirstPageExtrasVisibility();
            rebuildStreamRows();
            dismiss.run();
            Toast.makeText(MainActivity.this, "已更新常用应用（共 " + updated.size() + " 个）", Toast.LENGTH_SHORT).show();
        });
        saveBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        actions.add(saveBtn);
        addGridButtons(box, actions, 2);

        holder[0] = showBottomSheetDialog(box);
    }

    private void showReorderFavoritesDialog() {
        final List<String> favs = getStringList(K_FAVS);
        final Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry a : allApps) byKey.put(a.idKey, a);
        for (Iterator<String> it = favs.iterator(); it.hasNext(); ) {
            if (!byKey.containsKey(it.next())) it.remove();
        }
        if (favs.size() < 2) {
            Toast.makeText(this, "请先勾选至少 2 个常用应用", Toast.LENGTH_SHORT).show();
            return;
        }
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("↕ 拖拽或点击调整常用顺序", 18f, theme.textPrimary, true));
        TextView dragTip = makeText("按住左侧 ≡ 手柄上下拖拽即可实时排序，也可轻点右侧箭头", 12f, theme.textSecondary, false);
        dragTip.setPadding(0, dp(2), 0, dp(6));
        box.addView(dragTip);

        final LinearLayout listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(0, dp(4), 0, dp(8));

        final Runnable[] refreshUi = new Runnable[1];
        refreshUi[0] = () -> {
            listCol.removeAllViews();
            for (int i = 0; i < favs.size(); i++) {
                final int idx = i;
                final LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setBackground(theme.sheetTileCard(dpUnit, 14));
                r.setPadding(dp(10), dp(6), dp(8), dp(6));

                TextView dragHandle = makeText("≡", 18f, theme.accent, true);
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
                            v.getParent().getParent().requestDisallowInterceptTouchEvent(true);
                            r.setAlpha(0.85f);
                            r.setScaleX(1.02f);
                            r.setScaleY(1.02f);
                            r.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            return true;
                        }
                        if (act == MotionEvent.ACTION_MOVE) {
                            float dy = event.getRawY() - startRawY;
                            float stepPx = Math.max(dp(44), r.getHeight() + dp(6));
                            if (dy > stepPx * 0.7f && curIdx < favs.size() - 1) {
                                Collections.swap(favs, curIdx, curIdx + 1);
                                curIdx++;
                                startRawY += stepPx;
                                r.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                updateDragRowLabels(listCol, favs, byKey);
                            } else if (dy < -stepPx * 0.7f && curIdx > 0) {
                                Collections.swap(favs, curIdx, curIdx - 1);
                                curIdx--;
                                startRawY -= stepPx;
                                r.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                updateDragRowLabels(listCol, favs, byKey);
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
                r.addView(dragHandle, new LinearLayout.LayoutParams(dp(34), dp(40)));

                TextView tv = makeText((i + 1) + ".  " + byKey.get(favs.get(i)).label, 15f, theme.textPrimary, true);
                r.addView(tv, new LinearLayout.LayoutParams(0, dp(40), 1f));
                tv.setGravity(Gravity.CENTER_VERTICAL);

                for (int k = 0; k < 2; k++) {
                    final int delta = (k == 0) ? -1 : 1;
                    TextView arrow = makeText(k == 0 ? "▲" : "▼", 15f, theme.accent, true);
                    arrow.setGravity(Gravity.CENTER);
                    boolean valid = idx + delta >= 0 && idx + delta < favs.size();
                    arrow.setAlpha(valid ? 1f : 0.22f);
                    if (valid) {
                        arrow.setOnClickListener(v -> {
                            Collections.swap(favs, idx, idx + delta);
                            refreshUi[0].run();
                        });
                    }
                    r.addView(arrow, new LinearLayout.LayoutParams(dp(38), dp(40)));
                }
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(6);
                listCol.addView(r, rlp);
            }
        };
        refreshUi[0].run();
        box.addView(listCol);

        TextView saveBtn = makeSheetButton("✓ 保存顺序", () -> {
            putStringList(K_FAVS, favs);
            rebuildStreamRows();
            dismiss.run();
        });
        saveBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(8);
        box.addView(saveBtn, sLp);

        holder[0] = showBottomSheetDialog(box);
    }

    private void updateDragRowLabels(LinearLayout listCol, List<String> favs, Map<String, AppEntry> byKey) {
        for (int i = 0; i < Math.min(listCol.getChildCount(), favs.size()); i++) {
            View child = listCol.getChildAt(i);
            if (child instanceof LinearLayout && ((LinearLayout) child).getChildCount() >= 2) {
                View tv = ((LinearLayout) child).getChildAt(1);
                if (tv instanceof TextView) {
                    AppEntry a = byKey.get(favs.get(i));
                    if (a != null) ((TextView) tv).setText((i + 1) + ".  " + a.label);
                }
            }
        }
    }

    // ---------- 文件夹管理 ----------
    private JSONArray getFoldersArray() {
        try { return new JSONArray(prefs.getString(K_FOLDERS, "[]")); }
        catch (Throwable t) { return new JSONArray(); }
    }

    private void saveFoldersArray(JSONArray arr) {
        prefs.edit().putString(K_FOLDERS, arr.toString()).apply();
        folderIconCache.clear();
        rebuildFolderCapsules();
        rebuildStreamRows();
    }

    private void rebuildFolderCapsules() {
        if (folderCapsulesRow == null || folderScroll == null) return;
        folderCapsulesRow.removeAllViews();
        JSONArray arr = getFoldersArray();
        for (int i = 0; i < arr.length(); i++) {
            final int fIdx = i;
            JSONObject fo = arr.optJSONObject(i);
            if (fo == null) continue;
            JSONArray apps = fo.optJSONArray("apps");
            int cnt = apps == null ? 0 : apps.length();
            TextView pill = makeText("📁 " + fo.optString("name", "文件夹") + " · " + cnt, 13f, theme.textPrimary, false);
            pill.setBackground(theme.glassCard(dpUnit, 16));
            pill.setPadding(dp(12), dp(7), dp(12), dp(7));
            pill.setOnClickListener(v -> openFolderSheet(fIdx));
            pill.setOnLongClickListener(v -> { promptEditFolder(fIdx); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            folderCapsulesRow.addView(pill, lp);
        }
        folderScroll.setVisibility(folderCapsulesRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void promptCreateFolder(final AppEntry initialApp) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("📁 新建弹出式胶囊文件夹", 18f, theme.textPrimary, true));
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
        TextView createBtn = makeSheetButton("✓ 创建文件夹", () -> {
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
        final List<AppEntry> candidates = new ArrayList<>(allApps);
        final Set<String> existing = new HashSet<>();
        JSONArray cur = fo.optJSONArray("apps");
        if (cur != null) for (int i = 0; i < cur.length(); i++) existing.add(cur.optString(i));

        final boolean[] checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            checked[i] = existing.contains(candidates.get(i).idKey);
        }

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("📁 勾选「" + fo.optString("name") + "」内的应用", 18f, theme.textPrimary, true));
        List<TextView> itemBtns = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            final int idx = i;
            final AppEntry app = candidates.get(i);
            final TextView btn = makeText(
                    (checked[idx] ? "✓ " : "○ ") + app.label,
                    13.2f,
                    checked[idx] ? theme.accent : theme.textPrimary,
                    checked[idx]);
            btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            btn.setSingleLine(true);
            btn.setEllipsize(TextUtils.TruncateAt.END);
            btn.setBackground(checked[idx] ? theme.accentGlowPill(dpUnit, 14) : theme.sheetTileCard(dpUnit, 14));
            btn.setPadding(dp(12), dp(10), dp(12), dp(10));
            btn.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                btn.setText((checked[idx] ? "✓ " : "○ ") + app.label);
                btn.setTextColor(checked[idx] ? theme.accent : theme.textPrimary);
                btn.setTypeface(checked[idx] ? Typeface.create(typeface, Typeface.BOLD) : typeface);
                btn.setBackground(checked[idx] ? theme.accentGlowPill(dpUnit, 14) : theme.sheetTileCard(dpUnit, 14));
            });
            itemBtns.add(btn);
        }
        addGridButtons(box, itemBtns, 2);

        TextView saveBtn = makeSheetButton("✓ 保存文件夹", () -> {
            try {
                JSONArray next = new JSONArray();
                for (int i = 0; i < candidates.size(); i++) {
                    if (checked[i]) next.put(candidates.get(i).idKey);
                }
                fo.put("apps", next);
                saveFoldersArray(arr);
            } catch (Throwable ignored) {}
            dismiss.run();
        });
        saveBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        saveBtn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(8);
        box.addView(saveBtn, sLp);

        holder[0] = showBottomSheetDialog(box);
    }

    private void promptEditFolder(final int folderIdx) {
        final JSONArray arr = getFoldersArray();
        final JSONObject fo = arr.optJSONObject(folderIdx);
        if (fo == null) return;
        showSingleChoiceDialog("📁 管理「" + fo.optString("name") + "」",
                new String[]{"⭐ 勾选文件夹内应用", "🗑 删除该文件夹（不卸载应用）"}, 0, w -> {
            if (w == 0) promptSelectFolderApps(folderIdx);
            else {
                arr.remove(folderIdx);
                saveFoldersArray(arr);
            }
        });
    }

    private void openFolderSheet(final int folderIdx) {
        JSONObject fo = getFoldersArray().optJSONObject(folderIdx);
        if (fo == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];

        LinearLayout hdr = new LinearLayout(this);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.addView(makeText("📁 " + fo.optString("name"), 18, theme.textPrimary, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView editBtn = makeSheetButton("管理", () -> {
            if (holder[0] != null) holder[0].dismiss();
            promptEditFolder(folderIdx);
        });
        hdr.addView(editBtn);
        box.addView(hdr);

        JSONArray apps = fo.optJSONArray("apps");
        List<TextView> btns = new ArrayList<>();
        if (apps != null) {
            for (int i = 0; i < apps.length(); i++) {
                final AppEntry a = findAppByKey(apps.optString(i));
                if (a == null) continue;
                btns.add(makeSheetButton(a.label, () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    launchApp(a);
                }));
            }
        }
        if (btns.isEmpty()) {
            box.addView(makeText("文件夹为空，点击右上角「管理」添加应用", 13, theme.textSecondary, false));
        } else {
            addGridButtons(box, btns, 2);
        }
        holder[0] = showBottomSheetDialog(box);
    }

    private void promptAddAppToFolder(final AppEntry app) {
        final JSONArray arr = getFoldersArray();
        String[] items = new String[arr.length() + 1];
        for (int i = 0; i < arr.length(); i++) items[i] = "📁 " + arr.optJSONObject(i).optString("name");
        items[arr.length()] = "＋ 新建胶囊文件夹…";
        showSingleChoiceDialog("📁 将「" + app.label + "」加入文件夹", items, 0, w -> {
            if (w == arr.length()) {
                promptCreateFolder(app);
                return;
            }
            try {
                JSONObject fo = arr.getJSONObject(w);
                JSONArray aList = fo.optJSONArray("apps");
                if (aList == null) { aList = new JSONArray(); fo.put("apps", aList); }
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
        if (mediaWaveCard == null) return;
        if (!prefs.getBoolean(K_SHOW_MEDIA, true)) {
            mediaWaveCard.setVisibility(View.GONE);
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
            mediaWaveCard.setVisibility(View.GONE);
        }
    }

    private void updateMediaCardUi() {
        uiHandler.removeCallbacks(mediaProgressTicker);
        if (mediaWaveCard == null) return;
        MediaMetadata meta = activeMediaController == null ? null : activeMediaController.getMetadata();
        if (meta == null || TextUtils.isEmpty(meta.getString(MediaMetadata.METADATA_KEY_TITLE))) {
            mediaWaveCard.setVisibility(View.GONE);
            return;
        }
        String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (mediaArtView != null) {
            Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art != null) {
                mediaArtView.setImageBitmap(art);
                mediaArtView.setVisibility(View.VISIBLE);
            } else {
                mediaArtView.setVisibility(View.GONE);
            }
        }
        mediaTitleView.setText("🎵 " + title);
        mediaSubtitleView.setText(TextUtils.isEmpty(artist) ? "正在播放" : artist);
        PlaybackState st = activeMediaController.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        mediaPlayBtn.setText(playing ? "❚❚" : "▶\uFE0E");

        long dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = st == null ? 0 : st.getPosition();
        if (playing && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        if (dur > 0) {
            pos = Math.min(pos, dur);
            mediaProgressFill.setScaleX(pos / (float) dur);
            mediaTimeView.setText(formatMmSs(pos) + " / " + formatMmSs(dur));
            mediaTimeView.setVisibility(View.VISIBLE);
        } else {
            mediaTimeView.setVisibility(View.GONE);
        }
        mediaWaveCard.setVisibility(View.VISIBLE);
        if (playing && isResumed && dur > 0) uiHandler.postDelayed(mediaProgressTicker, 1000);
    }

    private static String formatMmSs(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
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
        if (pi != null) {
            try { launchedExternalApp = true; pi.send(); return; } catch (Throwable ignored) {}
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(activeMediaController.getPackageName());
        if (i != null) startIntentSafely(i);
    }

    private long nextEventId = -1, nextEventStart, nextEventEnd;

    private void refreshCalendarAgenda() {
        if (agendaPill == null) return;
        if (!prefs.getBoolean(K_SHOW_AGENDA, false)
                || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            agendaPill.setVisibility(View.GONE);
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
                    summary = "📅 近期日程：" + (TextUtils.isEmpty(t) ? "未命名日程" : t) + " · " + hm.format(new Date(b0));
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
                if (fSum == null) agendaPill.setVisibility(View.GONE);
                else {
                    agendaPill.setText(fSum);
                    agendaPill.setVisibility(View.VISIBLE);
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

    private boolean dispatchDirectReply(NotificationHubService.NotifItem item, String text) {
        return NotificationHubService.sendQuickReply(this, item, text);
    }

    private void promptRenameApp(final AppEntry app) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("✎ 自定义应用名称", 18f, theme.textPrimary, true));
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
        TextView saveBtn = makeSheetButton("✓ 保存名称", () -> {
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

    private void promptBatteryKeepAliveGuide() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        final Runnable dismiss = () -> { if (holder[0] != null) holder[0].dismiss(); };

        box.addView(makeText("🔋 保持桌面毫秒级秒回（防后台清理）", 17.5f, theme.textPrimary, true));
        TextView msg = makeText(
                "1. 点击下方「忽略电池优化」并允许；\n"
                + "2. ColorOS / 一加手机建议：进入「应用详情 → 耗电管理」，开启「允许完全后台行为」与「允许自启动」；\n"
                + "3. 开启「通知使用权」后，系统会持久绑定本桌面服务，永不杀后台。",
                13f, theme.textSecondary, false);
        msg.setPadding(0, dp(8), 0, dp(12));
        box.addView(msg);

        List<TextView> btns = new ArrayList<>();
        btns.add(makeSheetButton("⚙ 应用详情", () -> {
            dismiss.run();
            startIntentSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }));
        TextView optBtn = makeSheetButton("⚡ 忽略电池优化", () -> {
            dismiss.run();
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            if (!startIntentSafely(i)) {
                startIntentSafely(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        optBtn.setBackground(theme.solidAccentButton(dpUnit, 14));
        optBtn.setTextColor(0xFFFFFFFF);
        btns.add(optBtn);
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
            launchedExternalApp = true;
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
        showSingleChoiceDialog("🙈 已隐藏的应用（点选恢复显示到桌面）", names, 0, w -> {
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
        launchedExternalApp = false;
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
        if (appPopupScrim != null) {
            dismissAppPopupCard(true);
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

    private AppEntry findAppByPackageInSnapshot(List<AppEntry> snapshot, String pkgName) {
        if (pkgName == null || snapshot == null) return null;
        for (AppEntry a : snapshot) {
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
            launchedExternalApp = true;
            startActivity(i);
            return true;
        } catch (Throwable t) {
            launchedExternalApp = false;
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
