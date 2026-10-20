package com.newtube.mobile.ui.glass;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** ClearPDF's neutral canvas, single glass material, capsule controls and compact type. */
final class ClearPdfChrome {
    private ClearPdfChrome() {}

    static void style(View v, GlassPreferences.Snapshot s, GlassPalette p) {
        if (v instanceof ClearPdfSeekBar) ((ClearPdfSeekBar) v).render();
        if (!p.clearpdf) return;
        String id = name(v);
        boolean onVideo = inside(v, "mobile_video_area");
        GlassPalette material = onVideo && !p.dark ? new GlassPalette(v.getContext(), s, true) : p;

        if (id.equals("mobile_title_bar") && v instanceof TextView && s.glassTop) {
            TextView title = (TextView) v;
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = dp(v, 48);
            lp.startToStart = lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToEnd = lp.endToStart = ConstraintLayout.LayoutParams.UNSET;
            lp.setMarginStart(dp(v, 64)); lp.setMarginEnd(dp(v, 64)); lp.topMargin = dp(v, 14);
            title.setLayoutParams(lp); title.setGravity(Gravity.CENTER);
            title.setTextSize(18); title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            title.setPadding(dp(v, 20), 0, dp(v, 20), 0);
            GlassRuntime.surface(v, s, p, GlassDrawable.Role.HEADER, 28);
        }
        if (id.equals("settings_title") && v instanceof TextView) {
            TextView title = (TextView) v;
            title.setTextSize(18); title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if ((id.equals("mobile_cast_button") || id.equals("mobile_search_button") || id.equals("mobile_title_back"))
                && v.getLayoutParams() instanceof ConstraintLayout.LayoutParams && s.glassTop) {
            ConstraintLayout.LayoutParams lp=(ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topToTop=lp.bottomToBottom=R.id.mobile_title_bar;
            lp.topMargin=lp.bottomMargin=0;
            v.setLayoutParams(lp);
        }
        if (id.equals("mobile_watch_area") || id.equals("mobile_watch_scroll")
                || id.equals("mobile_watch_content") || id.equals("mobile_channel_root")
                || id.equals("mobile_channel_uploads_root") || id.equals("mobile_signin_root")
                || id.equals("mobile_add_device_root")) {
            v.setBackgroundColor(p.canvas);
        }
        if (id.endsWith("_toolbar") && !onVideo) v.setBackgroundColor(Color.TRANSPARENT);
        if (id.equals("mobile_dialog_content")) GlassRuntime.surface(v,s,p,GlassDrawable.Role.SHEET,28);
        if (id.equals("mobile_watch_title") && v instanceof TextView) {
            ((TextView) v).setTextColor(p.ink);
            ((TextView) v).setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (id.equals("video_card_root") && v instanceof MaterialCardView && s.glassCards) {
            MaterialCardView card = (MaterialCardView) v;
            card.setCardBackgroundColor(GlassPalette.alpha(p.surface, .58f));
            card.setRadius(dp(v, 28)); card.setStrokeWidth(dp(v, 1));
            card.setStrokeColor(GlassPalette.alpha(Color.WHITE, p.dark ? .42f : .94f));
            card.setCardElevation(0);
        }
        if (id.equals("mobile_mini_player") && v instanceof MaterialCardView && s.glassMini) {
            MaterialCardView card = (MaterialCardView) v;
            card.setCardBackgroundColor(p.surface); card.setRadius(dp(v, 28));
            card.setStrokeColor(GlassPalette.alpha(Color.WHITE, .60f)); card.setCardElevation(0);
        }

        boolean action = id.equals("mobile_watch_like") || id.equals("mobile_watch_dislike")
                || id.equals("mobile_watch_share") || id.equals("mobile_watch_download")
                || id.equals("mobile_watch_save") || id.equals("mobile_watch_subscribe");
        boolean panel = id.equals("mobile_watch_description") || id.equals("mobile_watch_comments_entry")
                || id.equals("mobile_watch_chat_entry") || id.equals("mobile_watch_queue_card");
        if ((action && s.glassButtons && !(v instanceof MaterialButton)) || (panel && s.glassCards)) {
            GlassRuntime.surface(v, s, material, panel ? GlassDrawable.Role.CARD : GlassDrawable.Role.CONTROL, 28);
        }
        if (v instanceof ImageButton && s.glassButtons && (onVideo || v.isClickable())) {
            GlassRuntime.surface(v, s, material, GlassDrawable.Role.CONTROL, 28);
        }
        if (v instanceof MaterialButton && s.glassButtons) {
            MaterialButton button = (MaterialButton) v;
            button.setCornerRadius(dp(v, 28)); button.setElevation(0);
            button.setStrokeWidth(dp(v, 1)); button.setStrokeColor(ColorStateList.valueOf(GlassPalette.alpha(Color.WHITE, .65f)));
            button.setBackgroundTintList(ColorStateList.valueOf(p.accent));
            button.setTextColor(Color.WHITE); button.setIconTint(ColorStateList.valueOf(Color.WHITE));
            button.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        } else if (v instanceof android.widget.Button && s.glassButtons) {
            GlassRuntime.surface(v,s,material,GlassDrawable.Role.CONTROL,28);
            ((android.widget.Button) v).setTextColor(p.accent);
        }
        if (v instanceof android.widget.ProgressBar && !(v instanceof android.widget.SeekBar)) {
            android.widget.ProgressBar progress=(android.widget.ProgressBar) v;
            progress.setProgressTintList(ColorStateList.valueOf(p.accent));
            progress.setIndeterminateTintList(ColorStateList.valueOf(onVideo ? Color.WHITE : p.accent));
        }
        if (id.equals("newtube_glass_settings_row") && s.glassSettings) {
            GlassRuntime.surface(v, s, p, GlassDrawable.Role.CARD, 22);
        }
        if (v instanceof TextView && (id.endsWith("_sheet_title") || id.equals("settings_header_title"))) {
            TextView title = (TextView) v;
            title.setTextColor(p.ink); title.setLetterSpacing(0);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
    }
    private static boolean inside(View v, String name) {
        for (View node = v; node != null; node = node.getParent() instanceof View ? (View) node.getParent() : null) {
            if (name(node).equals(name)) return true;
        }
        return false;
    }
    private static String name(View v) {
        try { return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId()); }
        catch (RuntimeException e) { return ""; }
    }
    private static int dp(View v, float d) { return Math.round(d * v.getResources().getDisplayMetrics().density); }
}
