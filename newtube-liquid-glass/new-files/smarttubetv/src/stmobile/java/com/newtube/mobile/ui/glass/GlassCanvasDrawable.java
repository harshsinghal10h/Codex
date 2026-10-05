package com.newtube.mobile.ui.glass;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Subtle ambient light behind the shell. No animation or per-frame allocations. */
final class GlassCanvasDrawable extends Drawable {
    private final GlassPalette p;
    private final boolean flat;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    GlassCanvasDrawable(GlassPalette p, boolean flat) { this.p = p; this.flat = flat; }
    @Override protected void onBoundsChange(Rect b) {
        if (!b.isEmpty()) paint.setShader(new RadialGradient(b.width() * 0.8f, b.height() * 0.85f,
                Math.max(b.width(), b.height()) * 0.72f, GlassPalette.alpha(p.accent, p.dark ? 0.055f : 0.045f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas c) { c.drawColor(p.canvas); if (!flat) c.drawRect(getBounds(), paint); }
    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
