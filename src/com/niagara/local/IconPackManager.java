package com.niagara.local;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 第三方图标包加载引擎 + Niagara 极简圆点 / 文件夹图标生成器 (v2.5.0)：
 * - 兼容 ADW / Nova / Apex / Go 规范第三方图标包（appfilter.xml + iconback/iconmask/iconupon 兜底合成）；
 * - 支持单个应用独立自定义图标映射；
 * - 提供原生风格 Niagara Dots 极简色点图标与 2x2 文件夹聚合图标绘制。
 */
final class IconPackManager {

    static final class PackInfo {
        final String packageName;
        final String label;

        PackInfo(String pkg, String label) {
            this.packageName = pkg;
            this.label = label;
        }
    }

    private static final String[] PACK_ACTIONS = {
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME"
    };

    private static String loadedPkg = "";
    private static Resources packRes;
    private static final Map<String, String> componentMap = new HashMap<>();
    private static final List<String> allDrawableNames = new ArrayList<>();
    private static final List<String> iconBackNames = new ArrayList<>();
    private static String iconMaskName = "";
    private static String iconUponName = "";
    private static float iconScale = 1.0f;

    static List<PackInfo> discoverIconPacks(Context ctx) {
        List<PackInfo> out = new ArrayList<>();
        if (ctx == null) return out;
        PackageManager pm = ctx.getPackageManager();
        Map<String, String> found = new LinkedHashMap<>();
        for (String act : PACK_ACTIONS) {
            try {
                List<ResolveInfo> list = pm.queryIntentActivities(new Intent(act), PackageManager.GET_META_DATA);
                if (list == null) continue;
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || found.containsKey(pkg)) continue;
                    CharSequence lb = ri.loadLabel(pm);
                    found.put(pkg, lb != null && lb.length() > 0 ? lb.toString().trim() : pkg);
                }
            } catch (Throwable ignored) {}
        }
        for (Map.Entry<String, String> e : found.entrySet()) {
            out.add(new PackInfo(e.getKey(), e.getValue()));
        }
        return out;
    }

    static synchronized void loadPack(Context ctx, String pkg) {
        if (pkg == null) pkg = "";
        if (pkg.equals(loadedPkg) && (pkg.isEmpty() || packRes != null)) return;

        loadedPkg = pkg;
        packRes = null;
        componentMap.clear();
        allDrawableNames.clear();
        iconBackNames.clear();
        iconMaskName = "";
        iconUponName = "";
        iconScale = 1.0f;

        if (pkg.isEmpty() || ctx == null) return;
        try {
            packRes = ctx.getPackageManager().getResourcesForApplication(pkg);
            XmlPullParser parser = null;
            InputStream assetStream = null;
            int xmlId = packRes.getIdentifier("appfilter", "xml", pkg);
            if (xmlId != 0) {
                parser = packRes.getXml(xmlId);
            } else {
                try {
                    assetStream = packRes.getAssets().open("appfilter.xml");
                    XmlPullParserFactory f = XmlPullParserFactory.newInstance();
                    f.setNamespaceAware(true);
                    parser = f.newPullParser();
                    parser.setInput(assetStream, "UTF-8");
                } catch (Throwable ignored) {}
            }
            if (parser == null) return;

            Set<String> seenDrawables = new LinkedHashSet<>();
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("item".equals(tag)) {
                        String comp = parser.getAttributeValue(null, "component");
                        String draw = parser.getAttributeValue(null, "drawable");
                        if (comp != null && draw != null && !draw.isEmpty()) {
                            componentMap.put(comp, draw);
                            componentMap.put(comp.toLowerCase(Locale.ROOT), draw);
                            seenDrawables.add(draw);
                        }
                    } else if ("iconback".equals(tag)) {
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            String v = parser.getAttributeValue(i);
                            if (v != null && !v.isEmpty()) iconBackNames.add(v);
                        }
                    } else if ("iconmask".equals(tag)) {
                        String v = parser.getAttributeValue(null, "img1");
                        if (v == null && parser.getAttributeCount() > 0) v = parser.getAttributeValue(0);
                        if (v != null) iconMaskName = v;
                    } else if ("iconupon".equals(tag)) {
                        String v = parser.getAttributeValue(null, "img1");
                        if (v == null && parser.getAttributeCount() > 0) v = parser.getAttributeValue(0);
                        if (v != null) iconUponName = v;
                    } else if ("scale".equals(tag)) {
                        String v = parser.getAttributeValue(null, "factor");
                        if (v != null) {
                            try { iconScale = Math.max(0.4f, Math.min(1.2f, Float.parseFloat(v))); }
                            catch (Throwable ignored) {}
                        }
                    }
                }
                event = parser.next();
            }
            allDrawableNames.addAll(seenDrawables);
            if (assetStream != null) {
                try { assetStream.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            packRes = null;
        }
    }

    static List<String> getSampleDrawableNames(int maxCount) {
        if (allDrawableNames.isEmpty()) return Collections.emptyList();
        int end = Math.min(maxCount, allDrawableNames.size());
        return new ArrayList<>(allDrawableNames.subList(0, end));
    }

    static Drawable loadDrawableByName(Context ctx, String pkg, String drawableName) {
        if (ctx == null || pkg == null || pkg.isEmpty() || drawableName == null || drawableName.isEmpty()) {
            return null;
        }
        try {
            Resources r = pkg.equals(loadedPkg) && packRes != null
                    ? packRes
                    : ctx.getPackageManager().getResourcesForApplication(pkg);
            int id = r.getIdentifier(drawableName, "drawable", pkg);
            if (id != 0) return r.getDrawable(id, null);
        } catch (Throwable ignored) {}
        return null;
    }

    static synchronized Drawable resolveIconFromActivePack(Context ctx, String activePkg, ComponentName comp, Drawable rawFallback, int sizePx) {
        if (activePkg == null || activePkg.isEmpty() || comp == null) return null;
        loadPack(ctx, activePkg);
        if (packRes == null) return null;

        String key1 = "ComponentInfo{" + comp.getPackageName() + "/" + comp.getClassName() + "}";
        String drawName = componentMap.get(key1);
        if (drawName == null) drawName = componentMap.get(key1.toLowerCase(Locale.ROOT));
        if (drawName != null) {
            Drawable d = loadDrawableByName(ctx, activePkg, drawName);
            if (d != null) return d;
        }

        // 若图标包未收录此应用，但提供了 iconback / iconmask，则执行自动合成蒙版兜底
        if (rawFallback != null && (!iconBackNames.isEmpty() || !iconMaskName.isEmpty())) {
            return compositeFallbackIcon(ctx, activePkg, comp, rawFallback, sizePx);
        }
        return null;
    }

    private static Drawable compositeFallbackIcon(Context ctx, String pkg, ComponentName comp, Drawable raw, int size) {
        try {
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);

            if (!iconBackNames.isEmpty()) {
                int idx = Math.abs(comp.getPackageName().hashCode()) % iconBackNames.size();
                Drawable back = loadDrawableByName(ctx, pkg, iconBackNames.get(idx));
                if (back != null) {
                    back.setBounds(0, 0, size, size);
                    back.draw(cv);
                }
            }

            Bitmap fgBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas fgCv = new Canvas(fgBmp);
            int pad = Math.round(size * (1f - iconScale) * 0.5f);
            raw.setBounds(pad, pad, size - pad, size - pad);
            raw.draw(fgCv);

            if (!iconMaskName.isEmpty()) {
                Drawable mask = loadDrawableByName(ctx, pkg, iconMaskName);
                if (mask != null) {
                    Bitmap maskBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    Canvas mCv = new Canvas(maskBmp);
                    mask.setBounds(0, 0, size, size);
                    mask.draw(mCv);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                    fgCv.drawBitmap(maskBmp, 0f, 0f, p);
                    maskBmp.recycle();
                }
            }
            cv.drawBitmap(fgBmp, 0f, 0f, null);
            fgBmp.recycle();

            if (!iconUponName.isEmpty()) {
                Drawable upon = loadDrawableByName(ctx, pkg, iconUponName);
                if (upon != null) {
                    upon.setBounds(0, 0, size, size);
                    upon.draw(cv);
                }
            }
            return new BitmapDrawable(ctx.getResources(), out);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 生成原生 Niagara Dots 风格极简圆点图标：提取应用主色调生成柔润发光圆点。
     */
    static Drawable createNiagaraDotIcon(Context ctx, Drawable rawIcon, int size, int accentFallback) {
        int domColor = extractDominantColor(rawIcon, accentFallback);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float cx = size * 0.5f, cy = size * 0.5f;

        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setColor((domColor & 0x00FFFFFF) | 0x30000000);
        cv.drawCircle(cx, cy, size * 0.30f, halo);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF000000 | domColor);
        cv.drawCircle(cx, cy, size * 0.21f, dot);

        Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlight.setColor(0x45FFFFFF);
        cv.drawCircle(cx - size * 0.055f, cy - size * 0.055f, size * 0.06f, highlight);

        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private static int extractDominantColor(Drawable d, int fallback) {
        if (d == null) return fallback;
        try {
            int s = 24;
            Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            if (d instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
                Drawable fg = ad.getForeground();
                Drawable bg = ad.getBackground();
                if (bg != null) { bg.setBounds(0, 0, s, s); bg.draw(cv); }
                if (fg != null) { fg.setBounds(0, 0, s, s); fg.draw(cv); }
            } else {
                d.setBounds(0, 0, s, s);
                d.draw(cv);
            }
            long rSum = 0, gSum = 0, bSum = 0;
            int cnt = 0;
            float[] hsv = new float[3];
            for (int y = 4; y < s - 4; y += 2) {
                for (int x = 4; x < s - 4; x += 2) {
                    int px = bmp.getPixel(x, y);
                    if (Color.alpha(px) < 180) continue;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] > 0.22f && hsv[2] > 0.2f) {
                        rSum += Color.red(px);
                        gSum += Color.green(px);
                        bSum += Color.blue(px);
                        cnt++;
                    }
                }
            }
            bmp.recycle();
            if (cnt > 0) {
                return Color.rgb((int) (rSum / cnt), (int) (gSum / cnt), (int) (bSum / cnt));
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    /**
     * 生成原生风格 2x2 文件夹聚合图标（最多展示前 4 个子应用微缩图标）。
     */
    static Drawable createFolderIcon(Context ctx, List<Drawable> icons, int size, AuroraTheme theme) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(theme.isLight ? 0x99FFFFFF : 0x36FFFFFF);
        RectF r = new RectF(size * 0.04f, size * 0.04f, size * 0.96f, size * 0.96f);
        float cr = size * 0.28f;
        cv.drawRoundRect(r, cr, cr, bg);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.2f, size * 0.025f));
        stroke.setColor(theme.isLight ? 0x44141824 : 0x44FFFFFF);
        cv.drawRoundRect(r, cr, cr, stroke);

        int count = icons == null ? 0 : Math.min(4, icons.size());
        if (count > 0) {
            int cell = Math.round(size * 0.30f);
            int gap = Math.round(size * 0.08f);
            int start = (size - cell * 2 - gap) / 2;
            for (int i = 0; i < count; i++) {
                Drawable d = icons.get(i);
                if (d == null) continue;
                int col = i % 2;
                int row = i / 2;
                int x = start + col * (cell + gap);
                int y = start + row * (cell + gap);
                d.setBounds(new Rect(x, y, x + cell, y + cell));
                d.draw(cv);
            }
        } else {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(theme.accent);
            cv.drawCircle(size * 0.5f, size * 0.5f, size * 0.14f, dot);
        }
        return new BitmapDrawable(ctx.getResources(), bmp);
    }
}
