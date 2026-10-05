package com.newtube.mobile.ui.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.glass.GlassPreferences;

import java.util.ArrayList;
import java.util.List;

/** Builds Settings > Appearance & glass without coupling visual preferences to player code. */
final class GlassSettingsPage {
    private GlassSettingsPage() {}

    static SettingsPages.Page build(Context context) {
        List<SettingsRow> rows = new ArrayList<>();

        rows.add(SettingsRow.appearanceToggle(R.id.newtube_glass_amoled_option, R.drawable.ic_glass_amoled,
                context.getString(R.string.mobile_glass_amoled), context.getString(R.string.mobile_glass_amoled_summary),
                () -> GlassPreferences.amoled(context), v -> GlassPreferences.setAmoled(context, v)));
        rows.add(SettingsRow.appearanceToggle(R.id.newtube_glass_dynamic_option, R.drawable.ic_glass_palette,
                context.getString(R.string.mobile_glass_dynamic_tint), context.getString(R.string.mobile_glass_dynamic_tint_summary),
                () -> GlassPreferences.dynamicTint(context), v -> GlassPreferences.setDynamicTint(context, v)));
        rows.add(SettingsRow.appearanceToggle(R.id.newtube_glass_enabled_option, R.drawable.ic_glass_bubbles,
                context.getString(R.string.mobile_glass_enabled), context.getString(R.string.mobile_glass_enabled_summary),
                () -> GlassPreferences.glassEnabled(context), v -> GlassPreferences.setGlassEnabled(context, v)));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_glass_group_style)));
        rows.add(SettingsRow.<Integer>choice(context.getString(R.string.mobile_glass_style))
                .option(context.getString(R.string.mobile_glass_style_vaso),
                        context.getString(R.string.mobile_glass_style_vaso_summary), GlassPreferences.STYLE_VASO)
                .option(context.getString(R.string.mobile_glass_style_lastwave),
                        context.getString(R.string.mobile_glass_style_lastwave_summary), GlassPreferences.STYLE_LASTWAVE)
                .option(context.getString(R.string.mobile_glass_style_classic),
                        context.getString(R.string.mobile_glass_style_classic_summary), GlassPreferences.STYLE_CLASSIC)
                .bind(() -> GlassPreferences.style(context), value -> GlassPreferences.applyPreset(context, value)));
        rows.add(SettingsRow.action(context.getString(R.string.mobile_glass_reset_profile),
                context.getString(R.string.mobile_glass_reset_profile_summary), page -> {
                    GlassPreferences.applyPreset(context, GlassPreferences.style(context));
                    page.rebuild();
                }));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_glass_group_optics)));
        rows.add(percentChoice(context, R.string.mobile_glass_blur,
                new int[]{0, 20, 40, 55, 70, 85, 100}, GlassPreferences::blur, GlassPreferences::setBlur));
        rows.add(percentChoice(context, R.string.mobile_glass_opacity,
                new int[]{35, 50, 60, 72, 82, 92, 100}, GlassPreferences::opacity, GlassPreferences::setOpacity));
        rows.add(percentChoice(context, R.string.mobile_glass_depth,
                new int[]{0, 25, 40, 55, 70, 85, 100}, GlassPreferences::depth, GlassPreferences::setDepth));
        rows.add(percentChoice(context, R.string.mobile_glass_dispersion,
                new int[]{0, 10, 18, 25, 40, 55, 75}, GlassPreferences::dispersion, GlassPreferences::setDispersion));
        rows.add(percentChoice(context, R.string.mobile_glass_specular,
                new int[]{0, 20, 40, 60, 75, 90, 100}, GlassPreferences::specular, GlassPreferences::setSpecular));
        rows.add(percentChoice(context, R.string.mobile_glass_tint_strength,
                new int[]{0, 20, 35, 50, 65, 80, 100}, GlassPreferences::tintStrength, GlassPreferences::setTintStrength));
        rows.add(percentChoice(context, R.string.mobile_glass_rim_strength,
                new int[]{0, 20, 40, 60, 75, 90, 100}, GlassPreferences::rimStrength, GlassPreferences::setRimStrength));
        rows.add(valueChoice(context, R.string.mobile_glass_saturation,
                new int[]{75, 90, 100, 118, 135, 150, 170}, "%", GlassPreferences::saturation, GlassPreferences::setSaturation));
        rows.add(valueChoice(context, R.string.mobile_glass_contrast,
                new int[]{80, 90, 100, 105, 115, 125, 140}, "%", GlassPreferences::contrast, GlassPreferences::setContrast));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_glass_reduce_transparency),
                context.getString(R.string.mobile_glass_reduce_transparency_summary),
                () -> GlassPreferences.reduceTransparency(context), v -> GlassPreferences.setReduceTransparency(context, v)));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_glass_group_geometry)));
        rows.add(valueChoice(context, R.string.mobile_glass_radius,
                new int[]{0, 8, 12, 16, 22, 28, 34, 40}, " dp", GlassPreferences::radius, GlassPreferences::setRadius));
        rows.add(valueChoice(context, R.string.mobile_glass_elevation,
                new int[]{0, 2, 4, 6, 8, 10, 14, 18}, " dp", GlassPreferences::elevation, GlassPreferences::setElevation));
        rows.add(SettingsRow.<Integer>choice(context.getString(R.string.mobile_glass_density))
                .option(context.getString(R.string.mobile_glass_density_compact), GlassPreferences.DENSITY_COMPACT)
                .option(context.getString(R.string.mobile_glass_density_comfortable), GlassPreferences.DENSITY_COMFORTABLE)
                .option(context.getString(R.string.mobile_glass_density_spacious), GlassPreferences.DENSITY_SPACIOUS)
                .bind(() -> GlassPreferences.density(context), v -> GlassPreferences.setDensity(context, v)));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_glass_rounded_thumbnails),
                context.getString(R.string.mobile_glass_rounded_thumbnails_summary),
                () -> GlassPreferences.roundedThumbnails(context), v -> GlassPreferences.setRoundedThumbnails(context, v)));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_glass_group_motion)));
        rows.add(valueChoice(context, R.string.mobile_glass_motion,
                new int[]{0, 50, 75, 100, 120, 140}, "%", GlassPreferences::motion, GlassPreferences::setMotion));
        rows.add(valueChoice(context, R.string.mobile_glass_press_scale,
                new int[]{100, 102, 104, 106, 108, 110, 112}, "%", GlassPreferences::press, GlassPreferences::setPress));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_glass_haptics),
                context.getString(R.string.mobile_glass_haptics_summary),
                () -> GlassPreferences.hapticPress(context), v -> GlassPreferences.setHapticPress(context, v)));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_glass_group_surfaces)));
        rows.add(surface(context, R.string.mobile_glass_nav, R.string.mobile_glass_nav_summary,
                GlassPreferences::glassNav, GlassPreferences::setGlassNav));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_glass_floating_nav),
                context.getString(R.string.mobile_glass_floating_nav_summary),
                () -> GlassPreferences.floatingNav(context), v -> GlassPreferences.setFloatingNav(context, v)));
        rows.add(surface(context, R.string.mobile_glass_top, R.string.mobile_glass_top_summary,
                GlassPreferences::glassTop, GlassPreferences::setGlassTop));
        rows.add(surface(context, R.string.mobile_glass_cards, R.string.mobile_glass_cards_summary,
                GlassPreferences::glassCards, GlassPreferences::setGlassCards));
        rows.add(surface(context, R.string.mobile_glass_buttons, R.string.mobile_glass_buttons_summary,
                GlassPreferences::glassButtons, GlassPreferences::setGlassButtons));
        rows.add(surface(context, R.string.mobile_glass_settings_surfaces, R.string.mobile_glass_settings_surfaces_summary,
                GlassPreferences::glassSettings, GlassPreferences::setGlassSettings));
        rows.add(surface(context, R.string.mobile_glass_mini_player, R.string.mobile_glass_mini_player_summary,
                GlassPreferences::glassMini, GlassPreferences::setGlassMini));

        rows.add(SettingsRow.header(context.getString(R.string.mobile_glass_group_accessibility)));
        rows.add(SettingsRow.toggle(context.getString(R.string.mobile_glass_high_contrast),
                context.getString(R.string.mobile_glass_high_contrast_summary),
                () -> GlassPreferences.highContrastText(context), v -> GlassPreferences.setHighContrastText(context, v)));
        rows.add(SettingsRow.note(context.getString(R.string.mobile_glass_compat_note)));

        return new SettingsPages.Page(context.getString(R.string.mobile_settings_appearance), rows);
    }

    private interface Getter { int get(Context context); }
    private interface Setter { void set(Context context, int value); }
    private interface BoolGetter { boolean get(Context context); }
    private interface BoolSetter { void set(Context context, boolean value); }

    private static SettingsRow percentChoice(Context context, int title, int[] values, Getter getter, Setter setter) {
        return valueChoice(context, title, values, "%", getter, setter);
    }

    private static SettingsRow valueChoice(Context context, int title, int[] values, String suffix,
                                           Getter getter, Setter setter) {
        SettingsRow.Choice<Integer> choice = SettingsRow.choice(context.getString(title));
        for (int value : values) choice.option(value + suffix, value);
        return choice.bind(() -> getter.get(context), v -> setter.set(context, v));
    }

    private static SettingsRow surface(Context context, int title, int summary, BoolGetter getter, BoolSetter setter) {
        return SettingsRow.toggle(context.getString(title), context.getString(summary),
                () -> getter.get(context), v -> setter.set(context, v));
    }
}
