package com.newtube.mobile.ui.glass;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * Lightweight liquid-glass material for NewTube's Views-based phone frontend.
 *
 * Vaso mode emphasizes a clean optical lens, spectral rim separation and a bright specular edge.
 * LastWave mode emphasizes tinted obsidian material, soft bloom and a continuous-corner
 * squircle inspired by LastWave's silhouette. The frost control changes haze/material diffusion; it intentionally does not capture
 * and blur arbitrary video/feed pixels behind every View, which would be much more expensive and
 * fragile inside NewTube's scrolling/player hierarchy.
 */
public final class GlassDrawable extends Drawable {
    private final Paint fill = paint(Paint.Style.FILL);
    private final Paint rim = paint(Paint.Style.STROKE);
    private final Paint highlight = paint(Paint.Style.STROKE);
    private final Paint bloom = paint(Paint.Style.FILL);
    private final RectF rect = new RectF();
    private final GlassPreferences.Snapshot s;
    private final boolean dark;
    private final int accent;
    private final float density;
    private int alpha = 255;

    public GlassDrawable(GlassPreferences.Snapshot s, boolean dark, int accent, float density) {
        this.s = s;
        this.dark = dark;
        this.accent = accent;
        this.density = density;
    }

    private static Paint paint(Paint.Style style) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setStyle(style);
        return p;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());
        float r = Math.min(Math.min(rect.width(), rect.height()) / 2f, s.radius * density);
        float a = (s.reduceTransparency ? Math.max(s.opacity, 90) : s.opacity) / 100f * (alpha / 255f);

        if (s.style == GlassPreferences.STYLE_VASO) {
            drawVaso(canvas, r, a);
        } else if (s.style == GlassPreferences.STYLE_LASTWAVE) {
            drawLastWave(canvas, r, a);
        } else {
            fill.setShader(null);
            fill.setColor(withAlpha(dark ? Color.rgb(30, 30, 30) : Color.WHITE, a));
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }

    private void drawVaso(Canvas canvas, float r, float a) {
        float frost = s.blur / 100f;
        int base = grade(dark ? Color.rgb(25, 27, 31) : Color.rgb(250, 252, 255));
        int bottom = grade(dark ? Color.rgb(5, 7, 11) : Color.rgb(226, 235, 245));
        base = blend(base, Color.WHITE, frost * (dark ? 0.10f : 0.16f));
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{withAlpha(Color.WHITE, (0.12f + 0.22f * frost) * a),
                        withAlpha(base, (0.38f + 0.20f * frost) * a),
                        withAlpha(bottom, 0.34f * a)},
                new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, r, r, fill);

        float dispersion = s.dispersion / 100f;
        float tintStrength = s.tintStrength / 100f;
        float rimStrength = s.rimStrength / 100f;
        int cyan = Color.rgb(90, 205, 255);
        int magenta = Color.rgb(255, 92, 205);
        int c1 = s.dynamicTint ? blend(cyan, accent, 0.25f * tintStrength) : cyan;
        int c3 = s.dynamicTint ? blend(magenta, accent, 0.18f * tintStrength) : magenta;
        rim.setStrokeWidth(dp(0.8f + Math.abs(s.depth) / 100f * 1.5f));
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{withAlpha(c1, dispersion * 0.72f * rimStrength),
                        withAlpha(Color.WHITE, (0.38f + 0.40f * a) * rimStrength),
                        withAlpha(c3, dispersion * 0.58f * rimStrength)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(inset(rect, rim.getStrokeWidth() / 2f), r, r, rim);

        float spec = s.specular / 100f;
        highlight.setStrokeWidth(dp(0.7f + spec));
        highlight.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                withAlpha(Color.WHITE, 0.90f * spec), withAlpha(Color.WHITE, 0f), Shader.TileMode.CLAMP));
        canvas.drawArc(inset(rect, dp(1.2f)), 194f, 120f, false, highlight);
    }

    private void drawLastWave(Canvas canvas, float r, float a) {
        float frost = s.blur / 100f;
        int neutral = dark ? Color.rgb(18, 20, 26) : Color.rgb(246, 248, 252);
        float tintStrength = s.tintStrength / 100f;
        float rimStrength = s.rimStrength / 100f;
        int tint = s.dynamicTint ? blend(neutral, accent, (dark ? 0.22f : 0.12f) * tintStrength) : neutral;
        tint = grade(tint);
        tint = blend(tint, Color.WHITE, frost * (dark ? 0.08f : 0.13f));
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                withAlpha(lighten(tint, dark ? 0.08f : 0.03f), a * 0.78f),
                withAlpha(tint, a * 0.96f), Shader.TileMode.CLAMP));

        Path shape = GlassGeometry.squircle(rect, r);
        canvas.drawPath(shape, fill);

        float spec = s.specular / 100f;
        rim.setStrokeWidth(dp(1f));
        int edgeTint = s.dynamicTint ? accent : (dark ? Color.rgb(180, 188, 204) : Color.rgb(110, 118, 132));
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{withAlpha(Color.WHITE, 0.42f * spec * rimStrength),
                        withAlpha(Color.WHITE, 0.10f * rimStrength),
                        withAlpha(edgeTint, 0.24f * a * rimStrength)}, null, Shader.TileMode.CLAMP));
        RectF inner = inset(rect, dp(0.6f));
        canvas.drawPath(GlassGeometry.squircle(inner, Math.max(0f, r - dp(0.6f))), rim);

        float depth = Math.abs(s.depth) / 100f;
        bloom.setShader(new RadialGradient(rect.left + rect.width() * 0.25f,
                rect.top + rect.height() * 0.10f, Math.max(rect.width(), rect.height()) * 0.9f,
                withAlpha(Color.WHITE, 0.12f * depth), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, bloom);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        RectF b = new RectF(getBounds());
        float r = Math.min(Math.min(b.width(), b.height()) / 2f, s.radius * density);
        if (s.style == GlassPreferences.STYLE_LASTWAVE && Build.VERSION.SDK_INT >= 21) {
            Path p = GlassGeometry.squircle(new RectF(0f, 0f, b.width(), b.height()), r);
            if (Build.VERSION.SDK_INT >= 30) outline.setPath(p); else outline.setConvexPath(p);
        } else {
            outline.setRoundRect(0, 0, Math.round(b.width()), Math.round(b.height()), r);
        }
        outline.setAlpha(0.92f);
    }

    private RectF inset(RectF source, float amount) {
        return new RectF(source.left + amount, source.top + amount,
                source.right - amount, source.bottom - amount);
    }

    private float dp(float v) { return v * density; }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(Math.max(0, Math.min(255, Math.round(alpha * 255f))),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int lighten(int c, float t) { return blend(c, Color.WHITE, t); }

    private int grade(int color) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = Math.max(0f, Math.min(1f, hsl[1] * (s.saturation / 100f)));
        int saturated = ColorUtils.HSLToColor(hsl);
        float contrast = s.contrast / 100f;
        return Color.rgb(contrastChannel(Color.red(saturated), contrast),
                contrastChannel(Color.green(saturated), contrast),
                contrastChannel(Color.blue(saturated), contrast));
    }

    private static int contrastChannel(int c, float contrast) {
        return Math.max(0, Math.min(255, Math.round(128f + (c - 128f) * contrast)));
    }

    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        rim.setColorFilter(colorFilter);
        highlight.setColorFilter(colorFilter);
        bloom.setColorFilter(colorFilter);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
