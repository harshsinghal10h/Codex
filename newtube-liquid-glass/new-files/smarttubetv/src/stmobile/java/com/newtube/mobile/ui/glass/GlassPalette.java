package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

/** A deliberate palette shared by the shell, dock, cards and settings. */
public final class GlassPalette {
    public final boolean dark, vaso;
    public final int canvas, surface, raised, ink, muted, accent, selected, onSelected;
    public GlassPalette(Context c, GlassPreferences.Snapshot s, boolean dark) {
        this.dark = dark;
        vaso = s.style == GlassPreferences.STYLE_VASO;
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
