package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Single source of truth for NewTube's customizable glass frontend.
 *
 * All appearance values live in one SharedPreferences file so the UI layer does not leak visual
 * state into player/network/presenter code. Values are intentionally primitive and version-stable:
 * they survive app updates and can be backed up with the normal Android app-data flow.
 */
public final class GlassPreferences {
    public static final int STYLE_VASO = 1;
    public static final int STYLE_LASTWAVE = 2;
    public static final int STYLE_CLASSIC = 3;

    public static final int DENSITY_COMFORTABLE = 0;
    public static final int DENSITY_COMPACT = 1;
    public static final int DENSITY_SPACIOUS = 2;

    private static final String FILE = "newtube_liquid_glass";

    private static final String K_STYLE = "style";
    private static final String K_BLUR = "blur";
    private static final String K_OPACITY = "opacity";
    private static final String K_RADIUS = "radius";
    private static final String K_DEPTH = "depth";
    private static final String K_DISPERSION = "dispersion";
    private static final String K_SPECULAR = "specular";
    private static final String K_SATURATION = "saturation";
    private static final String K_CONTRAST = "contrast";
    private static final String K_ELEVATION = "elevation";
    private static final String K_MOTION = "motion";
    private static final String K_PRESS = "press";
    private static final String K_DENSITY = "density";
    private static final String K_DYNAMIC_TINT = "dynamic_tint";
    private static final String K_GLASS_NAV = "glass_nav";
    private static final String K_GLASS_TOP = "glass_top";
    private static final String K_GLASS_CARDS = "glass_cards";
    private static final String K_GLASS_BUTTONS = "glass_buttons";
    private static final String K_GLASS_SETTINGS = "glass_settings";
    private static final String K_GLASS_MINI = "glass_mini";
    private static final String K_AMOLED = "amoled";
    private static final String K_REDUCE_TRANSPARENCY = "reduce_transparency";
    private static final String K_HIGH_CONTRAST_TEXT = "high_contrast_text";
    private static final String K_HAPTIC_PRESS = "haptic_press";
    private static final String K_ROUNDED_THUMBNAILS = "rounded_thumbnails";
    private static final String K_TINT_STRENGTH = "tint_strength";
    private static final String K_RIM_STRENGTH = "rim_strength";
    private static final String K_FLOATING_NAV = "floating_nav";

    private GlassPreferences() {}

    private static SharedPreferences p(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static int i(Context c, String key, int fallback) { return p(c).getInt(key, fallback); }
    private static boolean b(Context c, String key, boolean fallback) { return p(c).getBoolean(key, fallback); }

    private static void putI(Context c, String key, int value) {
        p(c).edit().putInt(key, value).apply();
        GlassRuntime.refreshAll();
    }

    private static void putB(Context c, String key, boolean value) {
        p(c).edit().putBoolean(key, value).apply();
        GlassRuntime.refreshAll();
    }

    public static int style(Context c) {
        int value = i(c, K_STYLE, STYLE_LASTWAVE);
        return value == STYLE_VASO || value == STYLE_CLASSIC ? value : STYLE_LASTWAVE;
    }
    public static int blur(Context c) { return i(c, K_BLUR, 55); }
    public static int opacity(Context c) { return i(c, K_OPACITY, 72); }
    public static int radius(Context c) { return i(c, K_RADIUS, 22); }
    public static int depth(Context c) { return i(c, K_DEPTH, 55); }
    public static int dispersion(Context c) { return i(c, K_DISPERSION, 24); }
    public static int specular(Context c) { return i(c, K_SPECULAR, 62); }
    public static int saturation(Context c) { return i(c, K_SATURATION, 135); }
    public static int contrast(Context c) { return i(c, K_CONTRAST, 105); }
    public static int elevation(Context c) { return i(c, K_ELEVATION, 8); }
    public static int motion(Context c) { return i(c, K_MOTION, 100); }
    public static int press(Context c) { return i(c, K_PRESS, 104); }
    public static int density(Context c) { return i(c, K_DENSITY, DENSITY_COMFORTABLE); }
    public static boolean dynamicTint(Context c) { return b(c, K_DYNAMIC_TINT, true); }
    public static boolean glassNav(Context c) { return b(c, K_GLASS_NAV, true); }
    public static boolean glassTop(Context c) { return b(c, K_GLASS_TOP, true); }
    public static boolean glassCards(Context c) { return b(c, K_GLASS_CARDS, true); }
    public static boolean glassButtons(Context c) { return b(c, K_GLASS_BUTTONS, true); }
    public static boolean glassSettings(Context c) { return b(c, K_GLASS_SETTINGS, true); }
    public static boolean glassMini(Context c) { return b(c, K_GLASS_MINI, true); }
    public static boolean amoled(Context c) { return b(c, K_AMOLED, false); }
    public static boolean reduceTransparency(Context c) { return b(c, K_REDUCE_TRANSPARENCY, false); }
    public static boolean highContrastText(Context c) { return b(c, K_HIGH_CONTRAST_TEXT, false); }
    public static boolean hapticPress(Context c) { return b(c, K_HAPTIC_PRESS, true); }
    public static boolean roundedThumbnails(Context c) { return b(c, K_ROUNDED_THUMBNAILS, true); }
    public static int tintStrength(Context c) { return i(c, K_TINT_STRENGTH, 65); }
    public static int rimStrength(Context c) { return i(c, K_RIM_STRENGTH, 75); }
    public static boolean floatingNav(Context c) { return b(c, K_FLOATING_NAV, true); }

    public static void setStyle(Context c, int v) {
        putI(c, K_STYLE, v == STYLE_VASO || v == STYLE_CLASSIC ? v : STYLE_LASTWAVE);
    }
    public static void setBlur(Context c, int v) { putI(c, K_BLUR, clamp(v, 0, 100)); }
    public static void setOpacity(Context c, int v) { putI(c, K_OPACITY, clamp(v, 25, 100)); }
    public static void setRadius(Context c, int v) { putI(c, K_RADIUS, clamp(v, 0, 40)); }
    public static void setDepth(Context c, int v) { putI(c, K_DEPTH, clamp(v, 0, 100)); }
    public static void setDispersion(Context c, int v) { putI(c, K_DISPERSION, clamp(v, 0, 100)); }
    public static void setSpecular(Context c, int v) { putI(c, K_SPECULAR, clamp(v, 0, 100)); }
    public static void setSaturation(Context c, int v) { putI(c, K_SATURATION, clamp(v, 50, 180)); }
    public static void setContrast(Context c, int v) { putI(c, K_CONTRAST, clamp(v, 75, 140)); }
    public static void setElevation(Context c, int v) { putI(c, K_ELEVATION, clamp(v, 0, 20)); }
    public static void setMotion(Context c, int v) { putI(c, K_MOTION, clamp(v, 0, 140)); }
    public static void setPress(Context c, int v) { putI(c, K_PRESS, clamp(v, 100, 112)); }
    public static void setDensity(Context c, int v) { putI(c, K_DENSITY, clamp(v, DENSITY_COMFORTABLE, DENSITY_SPACIOUS)); }
    public static void setDynamicTint(Context c, boolean v) { putB(c, K_DYNAMIC_TINT, v); }
    public static void setGlassNav(Context c, boolean v) { putB(c, K_GLASS_NAV, v); }
    public static void setGlassTop(Context c, boolean v) { putB(c, K_GLASS_TOP, v); }
    public static void setGlassCards(Context c, boolean v) { putB(c, K_GLASS_CARDS, v); }
    public static void setGlassButtons(Context c, boolean v) { putB(c, K_GLASS_BUTTONS, v); }
    public static void setGlassSettings(Context c, boolean v) { putB(c, K_GLASS_SETTINGS, v); }
    public static void setGlassMini(Context c, boolean v) { putB(c, K_GLASS_MINI, v); }
    public static void setAmoled(Context c, boolean v) { putB(c, K_AMOLED, v); }
    public static void setReduceTransparency(Context c, boolean v) { putB(c, K_REDUCE_TRANSPARENCY, v); }
    public static void setHighContrastText(Context c, boolean v) { putB(c, K_HIGH_CONTRAST_TEXT, v); }
    public static void setHapticPress(Context c, boolean v) { putB(c, K_HAPTIC_PRESS, v); }
    public static void setRoundedThumbnails(Context c, boolean v) { putB(c, K_ROUNDED_THUMBNAILS, v); }
    public static void setTintStrength(Context c, int v) { putI(c, K_TINT_STRENGTH, clamp(v, 0, 100)); }
    public static void setRimStrength(Context c, int v) { putI(c, K_RIM_STRENGTH, clamp(v, 0, 100)); }
    public static void setFloatingNav(Context c, boolean v) { putB(c, K_FLOATING_NAV, v); }

    /** Applies one coherent preset while retaining per-surface toggles. */
    public static void applyPreset(Context c, int style) {
        SharedPreferences.Editor e = p(c).edit().putInt(K_STYLE, style);
        if (style == STYLE_VASO) {
            e.putInt(K_BLUR, 28).putInt(K_OPACITY, 58).putInt(K_RADIUS, 24)
                    .putInt(K_DEPTH, 82).putInt(K_DISPERSION, 52).putInt(K_SPECULAR, 86)
                    .putInt(K_SATURATION, 118).putInt(K_CONTRAST, 108).putInt(K_ELEVATION, 6)
                    .putInt(K_MOTION, 105).putInt(K_PRESS, 106)
                    .putInt(K_TINT_STRENGTH, 48).putInt(K_RIM_STRENGTH, 92);
        } else if (style == STYLE_LASTWAVE) {
            e.putInt(K_BLUR, 58).putInt(K_OPACITY, 72).putInt(K_RADIUS, 22)
                    .putInt(K_DEPTH, 55).putInt(K_DISPERSION, 18).putInt(K_SPECULAR, 64)
                    .putInt(K_SATURATION, 145).putInt(K_CONTRAST, 104).putInt(K_ELEVATION, 10)
                    .putInt(K_MOTION, 100).putInt(K_PRESS, 108)
                    .putInt(K_TINT_STRENGTH, 76).putInt(K_RIM_STRENGTH, 68);
        } else {
            e.putInt(K_BLUR, 0).putInt(K_OPACITY, 100).putInt(K_RADIUS, 12)
                    .putInt(K_DEPTH, 0).putInt(K_DISPERSION, 0).putInt(K_SPECULAR, 0)
                    .putInt(K_SATURATION, 100).putInt(K_CONTRAST, 100).putInt(K_ELEVATION, 0)
                    .putInt(K_MOTION, 100).putInt(K_PRESS, 100)
                    .putInt(K_TINT_STRENGTH, 0).putInt(K_RIM_STRENGTH, 0);
        }
        e.apply();
        GlassRuntime.refreshAll();
    }

    public static Snapshot snapshot(Context c) {
        return new Snapshot(style(c), blur(c), opacity(c), radius(c), depth(c), dispersion(c),
                specular(c), saturation(c), contrast(c), elevation(c), motion(c), press(c), density(c),
                dynamicTint(c), glassNav(c), glassTop(c), glassCards(c), glassButtons(c), glassSettings(c),
                glassMini(c), amoled(c), reduceTransparency(c), highContrastText(c), hapticPress(c),
                roundedThumbnails(c), tintStrength(c), rimStrength(c), floatingNav(c));
    }

    public static final class Snapshot {
        public final int style, blur, opacity, radius, depth, dispersion, specular, saturation,
                contrast, elevation, motion, press, density, tintStrength, rimStrength;
        public final boolean dynamicTint, glassNav, glassTop, glassCards, glassButtons, glassSettings,
                glassMini, amoled, reduceTransparency, highContrastText, hapticPress, roundedThumbnails,
                floatingNav;

        Snapshot(int style, int blur, int opacity, int radius, int depth, int dispersion, int specular,
                 int saturation, int contrast, int elevation, int motion, int press, int density,
                 boolean dynamicTint, boolean glassNav, boolean glassTop, boolean glassCards,
                 boolean glassButtons, boolean glassSettings, boolean glassMini, boolean amoled,
                 boolean reduceTransparency, boolean highContrastText, boolean hapticPress,
                 boolean roundedThumbnails, int tintStrength, int rimStrength, boolean floatingNav) {
            this.style = style; this.blur = blur; this.opacity = opacity; this.radius = radius;
            this.depth = depth; this.dispersion = dispersion; this.specular = specular;
            this.saturation = saturation; this.contrast = contrast; this.elevation = elevation;
            this.motion = motion; this.press = press; this.density = density;
            this.dynamicTint = dynamicTint; this.glassNav = glassNav; this.glassTop = glassTop;
            this.glassCards = glassCards; this.glassButtons = glassButtons;
            this.glassSettings = glassSettings; this.glassMini = glassMini; this.amoled = amoled;
            this.reduceTransparency = reduceTransparency; this.highContrastText = highContrastText;
            this.hapticPress = hapticPress; this.roundedThumbnails = roundedThumbnails;
            this.tintStrength = tintStrength; this.rimStrength = rimStrength; this.floatingNav = floatingNav;
        }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
