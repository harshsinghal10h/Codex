package com.newtube.mobile.ui.glass;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/** Quiet surfaces; optical detail is reserved for floating chrome and the selected lens. */
public final class GlassDrawable extends Drawable {
    public enum Role { DOCK, LENS, CARD, FIELD, CONTROL, HEADER }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF rect = new RectF();
    private final GlassPreferences.Snapshot s;
    private final GlassPalette p;
    private final Role role;
    private final float density, radius;
    private int alpha = 255;
    private LinearGradient fill, rim;
    private Path path;
    public GlassDrawable(GlassPreferences.Snapshot s, GlassPalette p, Role role, float density, float radiusDp) {
        this.s = s; this.p = p; this.role = role; this.density = density; this.radius = radiusDp * density;
    }
    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds);
        float r = Math.min(radius, Math.min(rect.width(), rect.height()) / 2);
        path = new Path();
        if (!p.vaso && role == Role.CARD) path = GlassGeometry.squircle(rect, r);
        else path.addRoundRect(rect, r, r, Path.Direction.CW);
        float opacity = s.reduceTransparency ? 1 : s.opacity / 100f;
        boolean optical = role == Role.DOCK || role == Role.LENS || role == Role.FIELD;
        int base = role == Role.LENS ? p.selected : role == Role.CONTROL ? p.raised : p.surface;
        float amount = optical ? (p.vaso ? 0.42f + opacity * 0.24f : 0.67f + opacity * 0.25f) : 0.96f;
        if (role == Role.HEADER) amount = 0.95f;
        if (s.reduceTransparency) amount = 1;
        int top = ColorUtils.blendARGB(base, Color.WHITE, optical ? (p.vaso ? 0.11f : 0.055f) : 0.02f);
        fill = new LinearGradient(0, rect.top, 0, rect.bottom, GlassPalette.alpha(top, amount), GlassPalette.alpha(base, amount), Shader.TileMode.CLAMP);
        float strength = s.rimStrength / 100f, spec = s.specular / 100f;
        rim = new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{GlassPalette.alpha(Color.WHITE, (optical ? 0.60f * spec : 0.09f) * strength),
                        GlassPalette.alpha(p.vaso ? Color.rgb(158, 204, 213) : p.accent, (optical ? 0.24f : 0.055f) * strength),
                        GlassPalette.alpha(p.vaso ? Color.rgb(206, 178, 140) : Color.WHITE, (optical ? 0.20f : 0.06f) * strength)},
                new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP);
    }
    @Override public void draw(@NonNull Canvas canvas) {
        if (path == null || rect.isEmpty()) return;
        paint.setAlpha(alpha); paint.setStyle(Paint.Style.FILL); paint.setShader(fill);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density * (role == Role.CARD || role == Role.CONTROL ? 0.5f : 0.8f)); paint.setShader(rim);
        int save = canvas.save(); canvas.clipPath(path); canvas.drawPath(path, paint);
        if (p.vaso && (role == Role.DOCK || role == Role.LENS) && s.dispersion > 0) {
            paint.setShader(null); paint.setColor(GlassPalette.alpha(Color.rgb(255, 183, 130), s.dispersion / 100f * 0.24f));
            canvas.translate(density * 0.65f, 0); canvas.drawPath(path, paint);
        }
        canvas.restoreToCount(save); paint.setShader(null); paint.setAlpha(255);
    }
    @Override public void getOutline(@NonNull Outline o) { o.setRoundRect(getBounds(), Math.min(radius, getBounds().height() / 2f)); o.setAlpha(role == Role.DOCK ? 0.55f : 0.2f); }
    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter f) { paint.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
