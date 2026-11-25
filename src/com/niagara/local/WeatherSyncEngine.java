package com.niagara.local;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 轻量级实时天气同步引擎 (WeatherSyncEngine v2.3.0)：
 * - 仅在用户主动授权定位权限后启用，支持系统最后定位 + Open-Meteo 实时天气，
 *   并在无 GPS 缓存时自动降级到 wttr.in IP 定位兜底，确保授权后 1-2 秒内即可显示实时温度与天气图标；
 * - 结果缓存至 SharedPreferences（自动 25 分钟节流刷新），启动首帧零等待、零卡顿。
 */
final class WeatherSyncEngine {

    interface Callback {
        void onWeatherUpdated(String summary);
    }

    private static final String PREF_FILE = "niagara_local";
    private static final String K_WEATHER_TEXT = "weather_cache_text_v1";
    private static final String K_WEATHER_TIME = "weather_cache_time_v1";
    private static final long REFRESH_INTERVAL_MS = 25L * 60L * 1000L;
    private static final String K_SUNRISE_MIN = "sun_sunrise_min_v1";
    private static final String K_SUNSET_MIN = "sun_sunset_min_v1";
    private static final String K_SUN_DOY = "sun_day_of_year_v1";
    private static final long CACHE_VALID_MS = 6L * 60L * 60L * 1000L;

    static final int SOLAR_DAY = 0;
    static final int SOLAR_SUNRISE = 1;
    static final int SOLAR_SUNSET = 2;
    static final int SOLAR_NIGHT = 3;

    private static volatile boolean isFetching = false;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean hasLocationPermission(Context ctx) {
        if (ctx == null) return false;
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static String getCachedSummary(Context ctx) {
        if (ctx == null) return "";
        SharedPreferences sp = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        String text = sp.getString(K_WEATHER_TEXT, "");
        long ts = sp.getLong(K_WEATHER_TIME, 0L);
        if (text == null || text.isEmpty()) return "";
        if (System.currentTimeMillis() - ts > CACHE_VALID_MS) return "";
        return text;
    }

    static void fetchWeatherAsync(final Context ctx, final boolean force, final Callback cb) {
        if (ctx == null || !hasLocationPermission(ctx)) return;
        final Context appCtx = ctx.getApplicationContext();
        SharedPreferences sp = appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        long lastTs = sp.getLong(K_WEATHER_TIME, 0L);
        String cached = sp.getString(K_WEATHER_TEXT, "");
        if (!force && cached != null && !cached.isEmpty()
                && (System.currentTimeMillis() - lastTs) < REFRESH_INTERVAL_MS) {
            return;
        }
        if (isFetching) return;
        isFetching = true;

        AppExecutors.execute(() -> {
            String result = "";
            try {
                Location loc = getBestLastKnownLocation(appCtx);
                if (loc != null) {
                    result = fetchFromOpenMeteo(appCtx, loc.getLatitude(), loc.getLongitude());
                }
                if (result.isEmpty()) {
                    result = fetchFromWttrFallback();
                }
                if (!result.isEmpty()) {
                    appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                            .edit()
                            .putString(K_WEATHER_TEXT, result)
                            .putLong(K_WEATHER_TIME, System.currentTimeMillis())
                            .apply();
                }
            } catch (Throwable ignored) {
            } finally {
                isFetching = false;
            }
            if (!result.isEmpty() && cb != null) {
                final String finalRes = result;
                MAIN.post(() -> cb.onWeatherUpdated(finalRes));
            }
        });
    }

    private static Location getBestLastKnownLocation(Context ctx) {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            List<String> providers = lm.getProviders(true);
            Location best = null;
            if (providers != null) {
                for (String p : providers) {
                    try {
                        Location l = lm.getLastKnownLocation(p);
                        if (l == null) continue;
                        if (best == null || l.getTime() > best.getTime()) {
                            best = l;
                        }
                    } catch (SecurityException ignored) {}
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String fetchFromOpenMeteo(Context ctx, double lat, double lon) {
        try {
            String urlStr = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true&daily=sunrise,sunset&forecast_days=1&timezone=auto",
                    lat, lon);
            String body = httpGet(urlStr, 6500);
            if (body == null || body.isEmpty()) return "";
            JSONObject root = new JSONObject(body);
            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                JSONArray srArr = daily.optJSONArray("sunrise");
                JSONArray ssArr = daily.optJSONArray("sunset");
                if (srArr != null && srArr.length() > 0 && ssArr != null && ssArr.length() > 0) {
                    int srMin = parseIsoTimeMinutes(srArr.optString(0, ""));
                    int ssMin = parseIsoTimeMinutes(ssArr.optString(0, ""));
                    if (srMin >= 0 && ssMin > srMin) {
                        int doy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                                .edit()
                                .putInt(K_SUNRISE_MIN, srMin)
                                .putInt(K_SUNSET_MIN, ssMin)
                                .putInt(K_SUN_DOY, doy)
                                .apply();
                    }
                }
            }
            JSONObject cw = root.optJSONObject("current_weather");
            if (cw == null) return "";
            int temp = (int) Math.round(cw.optDouble("temperature", Double.NaN));
            if (Double.isNaN(cw.optDouble("temperature", Double.NaN))) return "";
            int code = cw.optInt("weathercode", 0);
            String cond = wmoCodeToChinese(code);
            return temp + "° " + cond;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int parseIsoTimeMinutes(String iso) {
        if (iso == null) return -1;
        int tIdx = iso.indexOf('T');
        if (tIdx < 0 || iso.length() < tIdx + 6) return -1;
        try {
            int hh = Integer.parseInt(iso.substring(tIdx + 1, tIdx + 3));
            int mm = Integer.parseInt(iso.substring(tIdx + 4, tIdx + 6));
            return hh * 60 + mm;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static int[] getSunriseSunsetMinutes(Context ctx) {
        Calendar cal = Calendar.getInstance();
        int doy = cal.get(Calendar.DAY_OF_YEAR);
        if (ctx != null) {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            if (sp.getInt(K_SUN_DOY, -1) == doy) {
                int sr = sp.getInt(K_SUNRISE_MIN, -1);
                int ss = sp.getInt(K_SUNSET_MIN, -1);
                if (sr >= 0 && ss > sr) return new int[]{sr, ss};
            }
        }
        double tzHours = TimeZone.getDefault().getOffset(cal.getTimeInMillis()) / 3600000.0;
        double lat = 30.0;
        double lon = tzHours * 15.0;
        if (ctx != null && hasLocationPermission(ctx)) {
            Location loc = getBestLastKnownLocation(ctx);
            if (loc != null) {
                lat = loc.getLatitude();
                lon = loc.getLongitude();
            }
        }
        return computeAstronomicalSunTimes(doy, lat, lon, tzHours);
    }

    private static int[] computeAstronomicalSunTimes(int doy, double lat, double lon, double tzHours) {
        double gamma = 2.0 * Math.PI / 365.0 * (doy - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double latRad = Math.toRadians(Math.max(-65.0, Math.min(65.0, lat)));
        double zenith = Math.toRadians(90.833);
        double cosHa = (Math.cos(zenith) / (Math.cos(latRad) * Math.cos(decl))) - Math.tan(latRad) * Math.tan(decl);
        cosHa = Math.max(-0.99, Math.min(0.99, cosHa));
        double haDeg = Math.toDegrees(Math.acos(cosHa));
        double sunriseUtc = 720.0 - 4.0 * (lon + haDeg) - eqTime;
        double sunsetUtc = 720.0 - 4.0 * (lon - haDeg) - eqTime;
        int sr = Math.max(240, Math.min(600, (int) Math.round(sunriseUtc + tzHours * 60.0)));
        int ss = Math.max(900, Math.min(1320, (int) Math.round(sunsetUtc + tzHours * 60.0)));
        return new int[]{sr, ss};
    }

    static int getSolarPhase(Context ctx) {
        int[] sun = getSunriseSunsetMinutes(ctx);
        Calendar cal = Calendar.getInstance();
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int sr = sun[0];
        int ss = sun[1];
        if (nowMin >= sr - 40 && nowMin <= sr + 45) return SOLAR_SUNRISE;
        if (nowMin > sr + 45 && nowMin < ss - 45) return SOLAR_DAY;
        if (nowMin >= ss - 45 && nowMin <= ss + 40) return SOLAR_SUNSET;
        return SOLAR_NIGHT;
    }

    static String formatSunWindow(Context ctx) {
        int[] sun = getSunriseSunsetMinutes(ctx);
        return String.format(Locale.US, "日出 %02d:%02d · 日落 %02d:%02d",
                sun[0] / 60, sun[0] % 60, sun[1] / 60, sun[1] % 60);
    }

    private static String fetchFromWttrFallback() {
        try {
            String body = httpGet("https://wttr.in/?format=j1&lang=zh", 7000);
            if (body == null || body.isEmpty()) return "";
            JSONObject root = new JSONObject(body);
            JSONArray curArr = root.optJSONArray("current_condition");
            if (curArr == null || curArr.length() == 0) return "";
            JSONObject cur = curArr.getJSONObject(0);
            String tempC = cur.optString("temp_C", "").trim();
            String cond = "";
            JSONArray zhArr = cur.optJSONArray("lang_zh");
            if (zhArr != null && zhArr.length() > 0) {
                cond = zhArr.getJSONObject(0).optString("value", "").trim();
            }
            if (cond.isEmpty()) {
                JSONArray descArr = cur.optJSONArray("weatherDesc");
                if (descArr != null && descArr.length() > 0) {
                    cond = englishCondToChinese(descArr.getJSONObject(0).optString("value", ""));
                }
            }
            cond = simplifyCondition(cond);
            if (!tempC.isEmpty() && !cond.isEmpty()) return tempC + "° " + cond;
            if (!tempC.isEmpty()) return tempC + "°";
            return "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String httpGet(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "NiagaraLocal/2.3.0");
            if (conn.getResponseCode() != 200) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String wmoCodeToChinese(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "小雨";
        if (code == 61 || code == 80) return "小雨";
        if (code == 63 || code == 81) return "中雨";
        if (code == 65 || code == 82) return "大雨";
        if (code >= 66 && code <= 67) return "冻雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code >= 85 && code <= 86) return "阵雪";
        if (code >= 95) return "雷雨";
        return "多云";
    }

    private static String englishCondToChinese(String s) {
        if (s == null) return "多云";
        String l = s.toLowerCase(Locale.ROOT);
        if (l.contains("thunder")) return "雷雨";
        if (l.contains("snow")) return "雪";
        if (l.contains("rain") || l.contains("drizzle") || l.contains("shower")) return "雨";
        if (l.contains("fog") || l.contains("mist") || l.contains("haze")) return "雾";
        if (l.contains("overcast")) return "阴";
        if (l.contains("cloud")) return "多云";
        if (l.contains("sun") || l.contains("clear")) return "晴";
        return "多云";
    }

    private static String simplifyCondition(String cond) {
        if (cond == null) return "";
        String c = cond.trim();
        if (c.contains("雷")) return "雷雨";
        if (c.contains("雪")) return "雪";
        if (c.contains("暴雨")) return "暴雨";
        if (c.contains("大雨")) return "大雨";
        if (c.contains("中雨")) return "中雨";
        if (c.contains("小雨") || c.contains("阵雨") || c.contains("雨")) return "小雨";
        if (c.contains("霾") || c.contains("雾")) return "雾";
        if (c.contains("阴")) return "阴";
        if (c.contains("多云") || c.contains("少云") || c.contains("云")) return "多云";
        if (c.contains("晴")) return "晴";
        if (c.length() > 3) return c.substring(0, 3);
        return c;
    }
}
