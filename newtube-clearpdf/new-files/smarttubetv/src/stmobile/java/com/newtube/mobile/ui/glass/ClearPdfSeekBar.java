package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import androidx.appcompat.widget.AppCompatSeekBar;

/** Capsule track and a white lens thumb; native seeking and accessibility remain intact. */
public final class ClearPdfSeekBar extends AppCompatSeekBar {
    private final Drawable originalThumb, originalTrack;
    private final android.content.res.ColorStateList originalProgressTint, originalThumbTint,
            originalBackgroundTint, originalSecondaryTint;
    private final boolean originalSplit;
    private boolean clear;
    public ClearPdfSeekBar(Context c, AttributeSet attrs) {
        super(c, attrs);
        originalThumb = getThumb(); originalTrack = getProgressDrawable();
        originalProgressTint = getProgressTintList(); originalThumbTint = getThumbTintList();
        originalBackgroundTint = getProgressBackgroundTintList(); originalSecondaryTint = getSecondaryProgressTintList();
        originalSplit = getSplitTrack();
        render();
    }
    public void render() {
        boolean enabled = GlassPreferences.glassEnabled(getContext())
                && GlassPreferences.style(getContext()) == GlassPreferences.STYLE_CLEARPDF;
        if (clear == enabled) return;
        clear = enabled;
        if (!clear) {
            setThumb(originalThumb); setProgressDrawable(originalTrack); setSplitTrack(originalSplit);
            setProgressTintList(originalProgressTint); setThumbTintList(originalThumbTint);
            setProgressBackgroundTintList(originalBackgroundTint); setSecondaryProgressTintList(originalSecondaryTint);
            return;
        }
        int progress = getProgress(), secondary = getSecondaryProgress();
        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_pressed}, capsule(Color.WHITE, 42, 26, true));
        thumb.addState(new int[]{}, capsule(Color.WHITE, 34, 22, true));
        setThumb(thumb); setSplitTrack(false);
        LayerDrawable track = new LayerDrawable(new Drawable[]{capsule(0x44787880, 0, 6, false),
                new ClipDrawable(capsule(0x66787880, 0, 6, false), Gravity.START, ClipDrawable.HORIZONTAL),
                new ClipDrawable(capsule(0xFF0A84FF, 0, 6, false), Gravity.START, ClipDrawable.HORIZONTAL)});
        int[] ids = {android.R.id.background, android.R.id.secondaryProgress, android.R.id.progress};
        for (int i=0;i<3;i++) { track.setId(i, ids[i]); track.setLayerHeight(i, dp(6)); track.setLayerGravity(i, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL); }
        setProgressTintList(null); setThumbTintList(null); setProgressBackgroundTintList(null);
        setSecondaryProgressTintList(null); setProgressDrawable(track);
        setProgress(progress); setSecondaryProgress(secondary);
    }
    private GradientDrawable capsule(int color, int w, int h, boolean rim) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(28));
        d.setSize(dp(w), dp(h)); if (rim) d.setStroke(dp(1), 0xB3FFFFFF); return d;
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
