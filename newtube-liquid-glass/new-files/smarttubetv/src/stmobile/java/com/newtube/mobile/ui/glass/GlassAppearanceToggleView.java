package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.ThemeMode;

/** Constant geometry for the three main appearance switches, independent of glass/density. */
public final class GlassAppearanceToggleView extends LinearLayout {
    private final ImageView icon;
    private final TextView title, summary;
    private final MaterialSwitch toggle;

    public GlassAppearanceToggleView(Context c) {
        super(c);
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(12), 0, dp(12), 0);
        int height = dp(88 + Math.max(0, getResources().getConfiguration().fontScale - 1) * 40);
        androidx.recyclerview.widget.RecyclerView.LayoutParams row =
                new androidx.recyclerview.widget.RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, height);
        row.setMarginStart(dp(16)); row.setMarginEnd(dp(16));
        row.topMargin = dp(3); row.bottomMargin = dp(3); setLayoutParams(row);

        icon = new ImageView(c); icon.setId(R.id.settings_row_icon);
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams tile = new LayoutParams(dp(52), dp(52)); tile.setMarginEnd(dp(12)); addView(icon, tile);
        LinearLayout text = new LinearLayout(c); text.setOrientation(VERTICAL);
        title = new TextView(c); title.setId(R.id.settings_row_title); title.setTextSize(18);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        summary = new TextView(c); summary.setId(R.id.settings_row_summary); summary.setTextSize(14);
        summary.setSingleLine(true); summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams sub = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); sub.topMargin = dp(3);
        text.addView(summary, sub); addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        toggle = new MaterialSwitch(c); toggle.setId(R.id.settings_row_switch);
        toggle.setClickable(false); toggle.setFocusable(false);
        toggle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        StateListDrawable mark = new StateListDrawable();
        mark.addState(new int[]{android.R.attr.state_checked}, ContextCompat.getDrawable(c, R.drawable.ic_glass_check));
        mark.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        toggle.setThumbIconDrawable(mark);
        LayoutParams control = new LayoutParams(dp(64), dp(48)); control.setMarginStart(dp(12)); addView(toggle, control);
        render();
    }

    /** Recolor in place: selecting a profile or flipping a switch never replaces this row. */
    public void render() {
        GlassPreferences.Snapshot s = GlassPreferences.snapshot(getContext());
        GlassPalette p = new GlassPalette(getContext(), s,
                ThemeMode.currentNight(getContext()) == Configuration.UI_MODE_NIGHT_YES);
        GradientDrawable background = new GradientDrawable(); background.setColor(p.surface); background.setCornerRadius(dp(20));
        setBackground(new RippleDrawable(ColorStateList.valueOf(GlassPalette.alpha(p.ink, .10f)), background, null));
        int tileColor = getId() == R.id.newtube_glass_amoled_option ? (p.dark ? 0xFF42472F : 0xFFE4E8D4)
                : androidx.core.graphics.ColorUtils.blendARGB(p.surface, p.accent, p.dark ? .30f : .18f);
        GradientDrawable tile = new GradientDrawable(); tile.setColor(tileColor); tile.setCornerRadius(dp(18));
        icon.setBackground(tile); icon.setImageTintList(ColorStateList.valueOf(p.ink));
        title.setTextColor(p.ink); summary.setTextColor(p.muted);
        int id = getId();
        if (id == R.id.newtube_glass_amoled_option) toggle.setChecked(GlassPreferences.amoled(getContext()));
        else if (id == R.id.newtube_glass_dynamic_option) toggle.setChecked(GlassPreferences.dynamicTint(getContext()));
        else if (id == R.id.newtube_glass_enabled_option) toggle.setChecked(GlassPreferences.glassEnabled(getContext()));
        title.setTypeface(p.vaso ? android.graphics.Typeface.create("sans-serif", 0) : GlassTypography.body(getContext()));
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        toggle.setTrackTintList(new ColorStateList(states, new int[]{p.selected, p.raised}));
        toggle.setThumbTintList(new ColorStateList(states, new int[]{p.ink, p.muted}));
        toggle.setThumbIconTintList(ColorStateList.valueOf(p.surface));
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
