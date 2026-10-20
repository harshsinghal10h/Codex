package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

/** A deliberate palette shared by the shell, dock, cards and settings. */
public final class GlassPalette {
    public final boolean dark, vaso, clearpdf;
    public final int canvas, surface, raised, ink, muted, accent, selected, onSelected;
    public GlassPalette(Context c, GlassPreferences.Snapshot s, boolean dark) {
        this.dark = dark;
        clearpdf = s.style == GlassPreferences.STYLE_CLEARPDF;
        vaso = s.style == GlassPreferences.STYLE_VASO || clearpdf;
        if (clearpdf) {
            canvas = dark ? (s.amoled ? Color.BLACK : 0xFF1C1E1D) : 0xFFE9EAEE;
            surface = dark ? 0xFF2B2D2E : 0xFFF4F5F7;
            raised = dark ? 0xFF36383B : 0xFFFAFAFA;
            ink = dark ? 0xFFF7F7FA : 0xFF111216;
            muted = dark ? 0xFFB5B6BF : 0xFF5E6068;
            accent = s.dynamicTint && Build.VERSION.SDK_INT >= 31
                    ? ContextCompat.getColor(c, android.R.color.system_accent1_400) : 0xFF0A84FF;
            selected = ColorUtils.blendARGB(surface, accent, dark ? 0.24f : 0.12f);
            onSelected = dark ? 0xFF64B5FF : 0xFF0069D6;
            return;
        }
        canvas = dark ? (s.amoled ? Color.BLACK : Color.rgb(17, 17, 16))
                : vaso ? Color.rgb(246, 244, 237) : Color.rgb(246, 245, 241);
        surface = dark ? (vaso ? Color.rgb(32, 34, 36) : Color.rgb(39, 38, 35)) : Color.rgb(255, 254, 250);
        raised = dark ? Color.rgb(48, 47, 42) : Color.rgb(236, 232, 221);
        ink = s.highContrastText ? (dark ? Color.WHITE : Color.BLACK) : dark ? Color.rgb(245, 243, 233) : Color.rgb(26, 28, 30);
        muted = s.highContrastText ? ink : dark ? Color.rgb(180, 177, 165) : Color.rgb(99, 102, 105);
        int seed = vaso ? Color.rgb(97, 133, 147) : Color.rgb(171, 155, 99);
        if (s.dynamicTint && Build.VERSION.SDK_INT >= 31) seed = ContextCompat.getColor(c, android.R.color.system_accent1_400);
        accent = grade(ColorUtils.blendARGB(vaso ? Color.rgb(98, 132, 144) : Color.rgb(163, 145, 86), seed, s.tintStrength / 100f * 0.55f), s);
        selected = dark ? ColorUtils.blendARGB(raised, accent, vaso ? 0.25f : 0.47f)
                : ColorUtils.blendARGB(Color.rgb(236, 233, 219), accent, vaso ? 0.12f : 0.23f);
        onSelected = dark ? Color.rgb(255, 251, 229) : Color.rgb(34, 34, 28);
    }
    public static int alpha(int color, float amount) {
        return ColorUtils.setAlphaComponent(color, Math.max(0, Math.min(255, Math.round(amount * 255))));
    }
    private static int grade(int c, GlassPreferences.Snapshot s) {
        float[] hsl = new float[3]; ColorUtils.colorToHSL(c, hsl);
        hsl[1] = Math.max(0,Math.min(1,hsl[1]*s.saturation/100f));
        hsl[2] = Math.max(0,Math.min(1,0.5f+(hsl[2]-0.5f)*s.contrast/100f));
        return ColorUtils.HSLToColor(hsl);
    }
}
