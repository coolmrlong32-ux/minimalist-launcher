package com.niagara.local;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 统一线性矢量图标视图 (VectorGlyphView v2.9.0)：
 * 替代原本分散在 SettingsSheetController / PopupCardController / NiagaraHeaderView / MainActivity
 * 的 4 套重复自绘图标内部类，全应用零 Emoji 统一描边质感。
 */
final class VectorGlyphView extends View {

    static final int IC_WALLPAPER = 1;
    static final int IC_SUN = 2;
    static final int IC_SPARK = 3;
    static final int IC_SHAPES = 4;
    static final int IC_FONT = 5;
    static final int IC_CLOCK = 6;
    static final int IC_STAR = 7;
    static final int IC_STAR_FILLED = 8;
    static final int IC_SORT = 9;
    static final int IC_BATTERY = 10;
    static final int IC_CARD = 11;
    static final int IC_DENSITY = 12;
    static final int IC_GESTURE = 13;
    static final int IC_LOCK = 14;
    static final int IC_BELL = 15;
    static final int IC_SHIELD = 16;
    static final int IC_HOME = 17;
    static final int IC_NOTE = 18;
    static final int IC_FOLDER = 19;
    static final int IC_EYE_OFF = 20;
    static final int IC_BACKUP = 21;
    static final int IC_SEARCH = 22;
    static final int IC_PLUS = 23;
    static final int IC_MORE = 24;
    static final int IC_EDIT = 25;
    static final int IC_INFO = 26;
    static final int IC_TRASH = 27;
    static final int IC_LAYERS = 28;
    static final int IC_SHORTCUT = 29;
    static final int IC_CLOSE = 30;
    static final int IC_CALENDAR = 31;
    static final int IC_PREV = 32;
    static final int IC_PLAY = 33;
    static final int IC_PAUSE = 34;
    static final int IC_NEXT = 35;
    static final int IC_CLIPBOARD = 36;
    static final int IC_CALC = 37;
    static final int IC_GLOBE = 38;

    private int type;
    private final float dp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    VectorGlyphView(Context ctx, int type, int color, float dp) {
        super(ctx);
        this.type = type;
        this.dp = dp;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.45f * dp);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        fillP.setColor(color);
        fillP.setStyle(Paint.Style.FILL);
    }

    void setColor(int color) {
        p.setColor(color);
        fillP.setColor(color);
        invalidate();
    }

    void setGlyphType(int type) {
        if (this.type != type) {
            this.type = type;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w * 0.5f, cy = h * 0.5f;
        float u = Math.min(w, h) * 0.37f;

        switch (type) {
            case IC_WALLPAPER:
            case IC_CARD:
                r.set(cx - u, cy - u * 0.85f, cx + u, cy + u * 0.85f);
                c.drawRoundRect(r, 2.2f * dp, 2.2f * dp, p);
                c.drawLine(cx - u * 0.6f, cy + u * 0.35f, cx - u * 0.1f, cy - u * 0.15f, p);
                c.drawLine(cx - u * 0.1f, cy - u * 0.15f, cx + u * 0.6f, cy + u * 0.35f, p);
                break;
            case IC_SUN:
                c.drawCircle(cx, cy, u * 0.48f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    c.drawLine(cx + (float) Math.cos(a) * u * 0.72f, cy + (float) Math.sin(a) * u * 0.72f,
                            cx + (float) Math.cos(a) * u * 1.02f, cy + (float) Math.sin(a) * u * 1.02f, p);
                }
                break;
            case IC_SPARK:
            case IC_GESTURE:
                path.reset();
                path.moveTo(cx, cy - u);
                path.lineTo(cx + u * 0.28f, cy - u * 0.28f);
                path.lineTo(cx + u, cy);
                path.lineTo(cx + u * 0.28f, cy + u * 0.28f);
                path.lineTo(cx, cy + u);
                path.lineTo(cx - u * 0.28f, cy + u * 0.28f);
                path.lineTo(cx - u, cy);
                path.lineTo(cx - u * 0.28f, cy - u * 0.28f);
                path.close();
                c.drawPath(path, p);
                break;
            case IC_STAR:
            case IC_STAR_FILLED:
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float rad = (i % 2 == 0) ? u : u * 0.44f;
                    float x = cx + (float) Math.cos(a) * rad;
                    float y = cy + (float) Math.sin(a) * rad;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, type == IC_STAR_FILLED ? fillP : p);
                break;
            case IC_SHAPES:
                r.set(cx - u, cy - u, cx - 1f * dp, cy - 1f * dp);
                c.drawRoundRect(r, 1.5f * dp, 1.5f * dp, p);
                r.set(cx + 1f * dp, cy - u, cx + u, cy - 1f * dp);
                c.drawOval(r, p);
                r.set(cx - u, cy + 1f * dp, cx - 1f * dp, cy + u);
                c.drawOval(r, p);
                r.set(cx + 1f * dp, cy + 1f * dp, cx + u, cy + u);
                c.drawRoundRect(r, 1.5f * dp, 1.5f * dp, p);
                break;
            case IC_FONT:
                c.drawLine(cx - u * 0.85f, cy - u * 0.75f, cx + u * 0.85f, cy - u * 0.75f, p);
                c.drawLine(cx, cy - u * 0.75f, cx, cy + u * 0.85f, p);
                break;
            case IC_CLOCK:
                c.drawCircle(cx, cy, u, p);
                c.drawLine(cx, cy, cx, cy - u * 0.52f, p);
                c.drawLine(cx, cy, cx + u * 0.45f, cy + u * 0.2f, p);
                break;
            case IC_SORT:
            case IC_DENSITY:
                c.drawLine(cx - u, cy - u * 0.58f, cx + u, cy - u * 0.58f, p);
                c.drawLine(cx - u, cy, cx + u * 0.58f, cy, p);
                c.drawLine(cx - u, cy + u * 0.58f, cx + u * 0.22f, cy + u * 0.58f, p);
                break;
            case IC_BATTERY:
                r.set(cx - u, cy - u * 0.58f, cx + u * 0.82f, cy + u * 0.58f);
                c.drawRoundRect(r, 2f * dp, 2f * dp, p);
                c.drawLine(cx + u * 1.05f, cy - u * 0.24f, cx + u * 1.05f, cy + u * 0.24f, p);
                break;
            case IC_LOCK:
                r.set(cx - u * 0.78f, cy - u * 0.1f, cx + u * 0.78f, cy + u * 0.9f);
                c.drawRoundRect(r, 2f * dp, 2f * dp, p);
                r.set(cx - u * 0.45f, cy - u * 0.85f, cx + u * 0.45f, cy + u * 0.1f);
                c.drawArc(r, 180, 180, false, p);
                break;
            case IC_BELL:
                r.set(cx - u * 0.7f, cy - u * 0.8f, cx + u * 0.7f, cy + u * 0.52f);
                c.drawArc(r, 180, 180, false, p);
                c.drawLine(cx - u * 0.7f, cy - u * 0.14f, cx - u * 0.7f, cy + u * 0.50f, p);
                c.drawLine(cx + u * 0.7f, cy - u * 0.14f, cx + u * 0.7f, cy + u * 0.50f, p);
                c.drawLine(cx - u * 0.92f, cy + u * 0.50f, cx + u * 0.92f, cy + u * 0.50f, p);
                c.drawLine(cx - u * 0.22f, cy + u * 0.84f, cx + u * 0.22f, cy + u * 0.84f, p);
                break;
            case IC_SHIELD:
                path.reset();
                path.moveTo(cx, cy - u);
                path.lineTo(cx + u * 0.85f, cy - u * 0.55f);
                path.lineTo(cx + u * 0.72f, cy + u * 0.35f);
                path.lineTo(cx, cy + u);
                path.lineTo(cx - u * 0.72f, cy + u * 0.35f);
                path.lineTo(cx - u * 0.85f, cy - u * 0.55f);
                path.close();
                c.drawPath(path, p);
                break;
            case IC_HOME:
                path.reset();
                path.moveTo(cx - u, cy - u * 0.05f);
                path.lineTo(cx, cy - u * 0.95f);
                path.lineTo(cx + u, cy - u * 0.05f);
                c.drawPath(path, p);
                r.set(cx - u * 0.65f, cy - u * 0.15f, cx + u * 0.65f, cy + u * 0.85f);
                c.drawRect(r, p);
                break;
            case IC_NOTE:
                r.set(cx - u * 0.85f, cy - u * 0.92f, cx + u * 0.85f, cy + u * 0.92f);
                c.drawRoundRect(r, 2f * dp, 2f * dp, p);
                c.drawLine(cx - u * 0.45f, cy - u * 0.28f, cx + u * 0.45f, cy - u * 0.28f, p);
                c.drawLine(cx - u * 0.45f, cy + u * 0.26f, cx + u * 0.22f, cy + u * 0.26f, p);
                break;
            case IC_FOLDER:
                path.reset();
                path.moveTo(cx - u, cy - u * 0.65f);
                path.lineTo(cx - u * 0.18f, cy - u * 0.65f);
                path.lineTo(cx + u * 0.06f, cy - u * 0.32f);
                path.lineTo(cx + u, cy - u * 0.32f);
                path.lineTo(cx + u, cy + u * 0.72f);
                path.lineTo(cx - u, cy + u * 0.72f);
                path.close();
                c.drawPath(path, p);
                break;
            case IC_EYE_OFF:
                c.drawOval(cx - u, cy - u * 0.52f, cx + u, cy + u * 0.52f, p);
                c.drawLine(cx - u * 0.82f, cy + u * 0.82f, cx + u * 0.82f, cy - u * 0.82f, p);
                break;
            case IC_BACKUP:
                c.drawLine(cx, cy - u * 0.85f, cx, cy + u * 0.45f, p);
                c.drawLine(cx - u * 0.45f, cy, cx, cy + u * 0.45f, p);
                c.drawLine(cx + u * 0.45f, cy, cx, cy + u * 0.45f, p);
                c.drawLine(cx - u * 0.85f, cy + u * 0.85f, cx + u * 0.85f, cy + u * 0.85f, p);
                break;
            case IC_SEARCH:
                c.drawCircle(cx - u * 0.18f, cy - u * 0.18f, u * 0.66f, p);
                c.drawLine(cx + u * 0.28f, cy + u * 0.28f, cx + u * 0.92f, cy + u * 0.92f, p);
                break;
            case IC_PLUS:
                c.drawLine(cx - u * 0.85f, cy, cx + u * 0.85f, cy, p);
                c.drawLine(cx, cy - u * 0.85f, cx, cy + u * 0.85f, p);
                break;
            case IC_MORE:
                float dr = 1.45f * dp;
                c.drawCircle(cx - u * 0.72f, cy, dr, fillP);
                c.drawCircle(cx, cy, dr, fillP);
                c.drawCircle(cx + u * 0.72f, cy, dr, fillP);
                break;
            case IC_EDIT:
                c.drawLine(cx - u * 0.65f, cy + u * 0.65f, cx + u * 0.55f, cy - u * 0.55f, p);
                c.drawLine(cx - u * 0.85f, cy + u * 0.85f, cx + u * 0.85f, cy + u * 0.85f, p);
                break;
            case IC_INFO:
                c.drawCircle(cx, cy, u * 0.92f, p);
                c.drawLine(cx, cy - u * 0.05f, cx, cy + u * 0.45f, p);
                c.drawCircle(cx, cy - u * 0.42f, 0.95f * dp, fillP);
                break;
            case IC_TRASH:
                c.drawLine(cx - u * 0.85f, cy - u * 0.55f, cx + u * 0.85f, cy - u * 0.55f, p);
                c.drawLine(cx - u * 0.35f, cy - u * 0.85f, cx + u * 0.35f, cy - u * 0.85f, p);
                r.set(cx - u * 0.62f, cy - u * 0.55f, cx + u * 0.62f, cy + u * 0.88f);
                c.drawRoundRect(r, 1.8f * dp, 1.8f * dp, p);
                break;
            case IC_LAYERS:
                path.reset();
                path.moveTo(cx, cy - u * 0.82f);
                path.lineTo(cx + u * 0.95f, cy - u * 0.25f);
                path.lineTo(cx, cy + u * 0.30f);
                path.lineTo(cx - u * 0.95f, cy - u * 0.25f);
                path.close();
                c.drawPath(path, p);
                c.drawLine(cx - u * 0.88f, cy + u * 0.28f, cx, cy + u * 0.80f, p);
                c.drawLine(cx, cy + u * 0.80f, cx + u * 0.88f, cy + u * 0.28f, p);
                break;
            case IC_SHORTCUT:
                path.reset();
                path.moveTo(cx + u * 0.12f, cy - u * 0.92f);
                path.lineTo(cx - u * 0.68f, cy + u * 0.08f);
                path.lineTo(cx + u * 0.05f, cy + u * 0.08f);
                path.lineTo(cx - u * 0.12f, cy + u * 0.92f);
                path.lineTo(cx + u * 0.68f, cy - u * 0.08f);
                path.lineTo(cx - u * 0.05f, cy - u * 0.08f);
                path.close();
                c.drawPath(path, p);
                break;
            case IC_CALENDAR:
                r.set(cx - u, cy - u * 0.78f, cx + u, cy + u * 0.92f);
                c.drawRoundRect(r, 2f * dp, 2f * dp, p);
                c.drawLine(cx - u, cy - u * 0.22f, cx + u, cy - u * 0.22f, p);
                c.drawLine(cx - u * 0.45f, cy - u * 1.05f, cx - u * 0.45f, cy - u * 0.55f, p);
                c.drawLine(cx + u * 0.45f, cy - u * 1.05f, cx + u * 0.45f, cy - u * 0.55f, p);
                break;
            case IC_PREV:
                c.drawLine(cx - u * 0.72f, cy - u * 0.72f, cx - u * 0.72f, cy + u * 0.72f, p);
                path.reset();
                path.moveTo(cx + u * 0.72f, cy - u * 0.72f);
                path.lineTo(cx - u * 0.35f, cy);
                path.lineTo(cx + u * 0.72f, cy + u * 0.72f);
                path.close();
                c.drawPath(path, fillP);
                break;
            case IC_NEXT:
                c.drawLine(cx + u * 0.72f, cy - u * 0.72f, cx + u * 0.72f, cy + u * 0.72f, p);
                path.reset();
                path.moveTo(cx - u * 0.72f, cy - u * 0.72f);
                path.lineTo(cx + u * 0.35f, cy);
                path.lineTo(cx - u * 0.72f, cy + u * 0.72f);
                path.close();
                c.drawPath(path, fillP);
                break;
            case IC_PAUSE:
                r.set(cx - u * 0.68f, cy - u * 0.78f, cx - u * 0.18f, cy + u * 0.78f);
                c.drawRoundRect(r, 1.2f * dp, 1.2f * dp, fillP);
                r.set(cx + u * 0.18f, cy - u * 0.78f, cx + u * 0.68f, cy + u * 0.78f);
                c.drawRoundRect(r, 1.2f * dp, 1.2f * dp, fillP);
                break;
            case IC_PLAY:
                path.reset();
                path.moveTo(cx - u * 0.55f, cy - u * 0.82f);
                path.lineTo(cx + u * 0.82f, cy);
                path.lineTo(cx - u * 0.55f, cy + u * 0.82f);
                path.close();
                c.drawPath(path, fillP);
                break;
            case IC_CLIPBOARD:
                r.set(cx - u * 0.78f, cy - u * 0.68f, cx + u * 0.78f, cy + u * 0.92f);
                c.drawRoundRect(r, 2f * dp, 2f * dp, p);
                r.set(cx - u * 0.38f, cy - u * 0.95f, cx + u * 0.38f, cy - u * 0.50f);
                c.drawRoundRect(r, 1.2f * dp, 1.2f * dp, p);
                break;
            case IC_CALC:
                r.set(cx - u * 0.85f, cy - u * 0.95f, cx + u * 0.85f, cy + u * 0.95f);
                c.drawRoundRect(r, 2.2f * dp, 2.2f * dp, p);
                c.drawLine(cx - u * 0.45f, cy - u * 0.45f, cx + u * 0.45f, cy - u * 0.45f, p);
                c.drawLine(cx - u * 0.45f, cy + u * 0.12f, cx + u * 0.45f, cy + u * 0.12f, p);
                c.drawLine(cx - u * 0.45f, cy + u * 0.52f, cx + u * 0.45f, cy + u * 0.52f, p);
                break;
            case IC_GLOBE:
                c.drawCircle(cx, cy, u * 0.92f, p);
                r.set(cx - u * 0.44f, cy - u * 0.92f, cx + u * 0.44f, cy + u * 0.92f);
                c.drawOval(r, p);
                c.drawLine(cx - u * 0.90f, cy, cx + u * 0.90f, cy, p);
                break;
            case IC_CLOSE:
            default:
                c.drawLine(cx - u * 0.65f, cy - u * 0.65f, cx + u * 0.65f, cy + u * 0.65f, p);
                c.drawLine(cx + u * 0.65f, cy - u * 0.65f, cx - u * 0.65f, cy + u * 0.65f, p);
                break;
        }
    }
}
