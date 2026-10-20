package com.newtube.mobile.ui.glass;

import android.graphics.Path;
import android.graphics.RectF;

/** Geometry helpers for the native Views renderer. */
final class GlassGeometry {
    private static final int CORNER_STEPS = 10;
    private static final double SQUIRCLE_EXPONENT = 4.4;

    private GlassGeometry() {}

    static Path squircle(RectF bounds, float radius) {
        float left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
        float w = Math.max(0f, bounds.width()), h = Math.max(0f, bounds.height());
        float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
        Path p = new Path();
        if (w <= 0f || h <= 0f) return p;
        if (r <= 0.001f) { p.addRect(bounds, Path.Direction.CW); return p; }

        p.moveTo(left + r, top);
        p.lineTo(right - r, top);
        corner(p, right - r, top + r, r, -Math.PI / 2.0, 0.0);
        p.lineTo(right, bottom - r);
        corner(p, right - r, bottom - r, r, 0.0, Math.PI / 2.0);
        p.lineTo(left + r, bottom);
        corner(p, left + r, bottom - r, r, Math.PI / 2.0, Math.PI);
        p.lineTo(left, top + r);
        corner(p, left + r, top + r, r, Math.PI, Math.PI * 1.5);
        p.close();
        return p;
    }

    private static void corner(Path p, float cx, float cy, float r, double start, double end) {
        double power = 2.0 / SQUIRCLE_EXPONENT;
        for (int i = 1; i <= CORNER_STEPS; i++) {
            double t = start + (end - start) * i / CORNER_STEPS;
            p.lineTo(cx + r * signedPow(Math.cos(t), power),
                    cy + r * signedPow(Math.sin(t), power));
        }
    }

    private static float signedPow(double value, double power) {
        return (float) Math.copySign(Math.pow(Math.abs(value), power), value);
    }
}
