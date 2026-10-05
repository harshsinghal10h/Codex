package com.newtube.mobile.ui.glass;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.ThemeMode;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Explicit component roles, with complete baseline restoration between live profile changes. */
public final class GlassRuntime {
    private static final List<WeakReference<Activity>> ACTIVITIES = new ArrayList<>();
    private GlassRuntime() {}
    public static void register(@NonNull Activity a) {
        prune(); for (WeakReference<Activity> ref : ACTIVITIES) if (ref.get() == a) return;
        ACTIVITIES.add(new WeakReference<>(a));
    }
    public static void unregister(@NonNull Activity a) { ACTIVITIES.removeIf(ref -> ref.get() == null || ref.get() == a); }
    private static void prune() { ACTIVITIES.removeIf(ref -> ref.get() == null); }
    public static void refreshAll() {
        prune(); for (WeakReference<Activity> ref : new ArrayList<>(ACTIVITIES)) {
            Activity a = ref.get(); if (a != null) a.runOnUiThread(() -> apply(a));
        }
    }
    public static void apply(@NonNull Activity a) {
        View content = a.findViewById(android.R.id.content); if (content == null) return;
        GlassPreferences.Snapshot s = GlassPreferences.snapshot(a);
        boolean dark = ThemeMode.currentNight(a) == Configuration.UI_MODE_NIGHT_YES;
        GlassPalette p = new GlassPalette(a, s, dark);
        // Restore first: no accumulated margins, stale font sizes, colour or nested old glass.
        restoreTree(content);
        if (s.style == GlassPreferences.STYLE_CLASSIC) {
            View nav = content.findViewById(R.id.mobile_bottom_nav);
            if (nav instanceof GlassNavigationView) ((GlassNavigationView) nav).configure(s, p);
            View header = content.findViewById(R.id.newtube_glass_header);
            if (header != null) header.setVisibility(View.GONE);
            View picker = content.findViewById(R.id.newtube_glass_profile_picker);
            if (picker instanceof GlassProfilePicker) ((GlassProfilePicker) picker).render();
            if (content.findViewById(R.id.mobile_browse_root) != null || content.findViewById(R.id.settings_container) != null) {
                a.getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(a, R.color.mobile_color_background));
                a.getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(a, R.color.mobile_color_navigation_bar));
            }
            return;
        }
        applyTree(content, s, p);
        GlassShell.apply(content, s, p);
        // Geometry has now established the dock's actual inset.
        View grid = content.findViewById(R.id.mobile_content_grid);
        if (grid != null) grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(),
                ((Baseline) grid.getTag(R.id.newtube_glass_original_saved)).bottom + GlassShell.navigationInset(grid));
        // Match the opaque system bars to the new canvas without touching immersive player flags.
        if (content.findViewById(R.id.mobile_browse_root) != null || content.findViewById(R.id.settings_container) != null) {
            a.getWindow().setStatusBarColor(p.canvas);
            a.getWindow().setNavigationBarColor(p.canvas);
        }
    }
    public static void restoreForThemeRefresh(@NonNull Activity a) {
        View content = a.findViewById(android.R.id.content); if (content != null) restoreTree(content);
    }
    public static void rebaseline(@NonNull Activity a) {
        View content = a.findViewById(android.R.id.content); if (content != null) clearBaseline(content);
    }

    private static void applyTree(View v, GlassPreferences.Snapshot s, GlassPalette p) {
        if (v instanceof GlassNavigationView) { remember(v); return; }
        if (v instanceof GlassProfilePicker) { ((GlassProfilePicker) v).render(); return; }
        if (v.getId() != android.R.id.content) remember(v);
        String id = idName(v);
        switch (id) {
            case "mobile_browse_root": case "mobile_search_root": case "settings_container":
                v.setBackground(new GlassCanvasDrawable(p, s.amoled && p.dark)); break;
            case "newtube_glass_settings_page":
                v.setBackgroundColor(p.canvas); break;
            case "mobile_title_bar":
                if (s.glassTop) {
                    v.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    TextView title = (TextView) v; title.setTextSize(p.vaso ? 28 : 31);
                    title.setTypeface(p.vaso ? Typeface.create("sans-serif",Typeface.BOLD) : GlassTypography.display(v.getContext())); title.setTextColor(p.ink);
                    ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
                    lp.height = dp(v, 76); lp.topMargin = dp(v, 8); v.setLayoutParams(lp);
                    v.setPadding(dp(v, 20), 0, dp(v, 8), 0);
                }
                break;
            case "mobile_search_button": case "mobile_cast_button": case "mobile_title_back":
                if (s.glassTop) {
                    ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
                    lp.height = dp(v, 48); lp.topMargin = dp(v, 14); v.setLayoutParams(lp);
                    if (s.glassButtons) surface(v, s, p, GlassDrawable.Role.CONTROL, 24);
                }
                break;
            case "mobile_search_input": case "settings_search_field": case "settings_search_input":
                if (s.glassTop) surface(v, s, p, GlassDrawable.Role.FIELD, 26);
                break;
            case "settings_title":
                ((TextView) v).setTextColor(p.ink); ((TextView) v).setTextSize(25);
                ((TextView) v).setTypeface(p.vaso ? Typeface.create("sans-serif",Typeface.BOLD) : GlassTypography.display(v.getContext())); break;
            case "newtube_glass_settings_row":
                if (s.glassSettings) {
                    surface(v, s, p, GlassDrawable.Role.CARD, 18);
                    margins(v, 16, 2, 16, 2); v.setMinimumHeight(dp(v, s.density == 1 ? 58 : s.density == 2 ? 82 : 70));
                }
                break;
            case "settings_header_title":
                ((TextView) v).setTextColor(p.muted); ((TextView) v).setTextSize(12);
                ((TextView) v).setLetterSpacing(0.08f); v.setPadding(dp(v, 22), dp(v, 24), dp(v, 16), dp(v, 10)); break;
            case "settings_row_title": case "settings_choice_label": case "video_title":
                ((TextView) v).setTextColor(p.ink);
                if (!p.vaso) ((TextView) v).setTypeface(GlassTypography.body(v.getContext())); break;
            case "settings_row_summary": case "settings_choice_description": case "video_meta":
                ((TextView) v).setTextColor(p.muted); break;
            case "video_card_root":
                if (s.glassCards && v instanceof MaterialCardView) {
                    MaterialCardView card = (MaterialCardView) v;
                    card.setCardBackgroundColor(p.surface); card.setRadius(dp(v, p.vaso ? Math.max(12, s.radius) : Math.max(16, s.radius + 4)));
                    card.setCardElevation(0); card.setStrokeWidth(p.vaso ? dp(v, 0.7f) : 0);
                    card.setStrokeColor(GlassPalette.alpha(p.muted, 0.10f));
                    int inset = dp(v, s.density == 1 ? 6 : s.density == 2 ? 14 : 10);
                    card.setContentPadding(inset, inset, inset, dp(v, 2)); margins(v, 0, 4, 0, s.density == 1 ? 10 : 16);
                }
                break;
            case "video_thumbnail_frame":
                if (v instanceof MaterialCardView) ((MaterialCardView) v).setRadius(dp(v, s.roundedThumbnails ? (p.vaso ? 16 : 18) : 0)); break;
            case "mobile_mini_player":
                if (s.glassMini && v instanceof MaterialCardView) {
                    MaterialCardView card = (MaterialCardView) v; card.setRadius(dp(v, 16));
                    card.setStrokeWidth(dp(v, 1)); card.setStrokeColor(GlassPalette.alpha(p.ink, 0.2f));
                }
                break;
            case "mobile_content_grid": case "mobile_search_grid":
                v.setPadding(dp(v, 16), dp(v, 12), dp(v, 16), v.getPaddingBottom()); break;
            default: break;
        }
        if (v instanceof RecyclerView) installRecyclerHook((RecyclerView) v);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i), s, p);
        }
    }
    private static void surface(View v, GlassPreferences.Snapshot s, GlassPalette p, GlassDrawable.Role role, float radius) {
        Drawable d = new GlassDrawable(s, p, role, v.getResources().getDisplayMetrics().density, radius);
        v.setBackground(v.isClickable() ? new RippleDrawable(ColorStateList.valueOf(GlassPalette.alpha(p.ink, 0.10f)), d, null) : d);
        // Expansion belongs to controls; feed cards and container geometry remain stable.
        if (v instanceof ImageButton && s.motion > 0 && s.press > 100) {
            StateListAnimator states = new StateListAnimator();
            states.addState(new int[]{android.R.attr.state_pressed}, scale(v, Math.min(1.06f, s.press / 100f), 100));
            states.addState(new int[]{}, scale(v, 1, 160)); v.setStateListAnimator(states);
        }
    }
    private static AnimatorSet scale(View v, float to, long time) {
        AnimatorSet a = new AnimatorSet(); a.playTogether(ObjectAnimator.ofFloat(v, View.SCALE_X, to), ObjectAnimator.ofFloat(v, View.SCALE_Y, to)); a.setDuration(time); return a;
    }
    private static void margins(View v, int l, int t, int r, int b) {
        if (!(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        lp.setMarginStart(dp(v,l)); lp.setMarginEnd(dp(v,r)); lp.topMargin = dp(v,t); lp.bottomMargin = dp(v,b); v.setLayoutParams(lp);
    }
    private static void installRecyclerHook(RecyclerView rv) {
        if (Boolean.TRUE.equals(rv.getTag(R.id.newtube_glass_recycler_hook))) return;
        rv.setTag(R.id.newtube_glass_recycler_hook, true);
        rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View child) {
                GlassPreferences.Snapshot s = GlassPreferences.snapshot(child.getContext());
                restoreTree(child);
                if (s.style != GlassPreferences.STYLE_CLASSIC) applyTree(child, s, new GlassPalette(child.getContext(), s,
                        ThemeMode.currentNight(child.getContext()) == Configuration.UI_MODE_NIGHT_YES));
            }
            @Override public void onChildViewDetachedFromWindow(@NonNull View child) {}
        });
    }
    static void remember(View v) { if (!(v.getTag(R.id.newtube_glass_original_saved) instanceof Baseline)) v.setTag(R.id.newtube_glass_original_saved, new Baseline(v)); }
    private static void restoreTree(View v) {
        Object original = v.getTag(R.id.newtube_glass_original_saved);
        if (original instanceof Baseline) ((Baseline) original).restore(v);
        if (v instanceof GlassNavigationView || v instanceof GlassProfilePicker) return;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) restoreTree(((ViewGroup) v).getChildAt(i));
    }
    private static void clearBaseline(View v) {
        v.setTag(R.id.newtube_glass_original_saved, null);
        if (v instanceof GlassNavigationView) ((GlassNavigationView) v).rebaselineNative();
        if (v instanceof GlassNavigationView || v instanceof GlassProfilePicker) return;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) clearBaseline(((ViewGroup) v).getChildAt(i));
    }
    private static String idName(View v) {
        if (v.getId() == View.NO_ID) return "";
        try { return v.getResources().getResourceEntryName(v.getId()); } catch (RuntimeException e) { return ""; }
    }
    private static int dp(View v, float d) { return Math.round(d * v.getResources().getDisplayMetrics().density); }
    private static ViewGroup.LayoutParams copy(ViewGroup.LayoutParams lp) {
        if (lp == null) return null;
        if (lp instanceof ConstraintLayout.LayoutParams) return new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams) lp);
        if (lp instanceof RecyclerView.LayoutParams) return new RecyclerView.LayoutParams((RecyclerView.LayoutParams) lp);
        if (lp instanceof LinearLayout.LayoutParams) return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) lp);
        if (lp instanceof FrameLayout.LayoutParams) return new FrameLayout.LayoutParams((FrameLayout.LayoutParams) lp);
        if (lp instanceof ViewGroup.MarginLayoutParams) return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) lp);
        return new ViewGroup.LayoutParams(lp);
    }
    private static final class Baseline {
        final Drawable background;
        final ViewGroup.LayoutParams layout;
        final int left,top,right,bottom,minHeight;
        final float elevation, textSize, letterSpacing, radius, cardElevation;
        final int stroke, contentLeft,contentTop,contentRight,contentBottom;
        final ColorStateList textColors, cardColors, strokeColors;
        final Typeface font;
        final StateListAnimator animator;
        final ViewOutlineProvider outline;
        final boolean clip;
        Baseline(View v) {
            background = v.getBackground(); layout = copy(v.getLayoutParams());
            left=v.getPaddingLeft();top=v.getPaddingTop();right=v.getPaddingRight();bottom=v.getPaddingBottom(); minHeight=v.getMinimumHeight();
            elevation=ViewCompat.getElevation(v); animator=v.getStateListAnimator(); outline=v.getOutlineProvider(); clip=v.getClipToOutline();
            TextView tv=v instanceof TextView ? (TextView) v : null;
            textColors=tv == null ? null : tv.getTextColors(); textSize=tv == null ? 0 : tv.getTextSize(); font=tv == null ? null : tv.getTypeface(); letterSpacing=tv == null ? 0 : tv.getLetterSpacing();
            MaterialCardView c=v instanceof MaterialCardView ? (MaterialCardView) v : null;
            cardColors=c == null ? null : c.getCardBackgroundColor(); radius=c == null ? 0 : c.getRadius(); cardElevation=c == null ? 0 : c.getCardElevation();
            stroke=c == null ? 0 : c.getStrokeWidth(); strokeColors=c == null ? null : c.getStrokeColorStateList();
            contentLeft=c == null ? 0 : c.getContentPaddingLeft();contentTop=c == null ? 0 : c.getContentPaddingTop();contentRight=c == null ? 0 : c.getContentPaddingRight();contentBottom=c == null ? 0 : c.getContentPaddingBottom();
        }
        void restore(View v) {
            if (!(v instanceof MaterialCardView)) v.setBackground(background);
            if (layout != null) v.setLayoutParams(copy(layout));
            v.setPadding(left,top,right,bottom);v.setMinimumHeight(minHeight); ViewCompat.setElevation(v,elevation);
            v.setStateListAnimator(animator);v.setScaleX(1);v.setScaleY(1);v.setOutlineProvider(outline);v.setClipToOutline(clip);
            if (v instanceof TextView) {
                TextView tv=(TextView) v;tv.setTextColor(textColors);tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,textSize);tv.setTypeface(font);tv.setLetterSpacing(letterSpacing);
            }
            if (v instanceof MaterialCardView) {
                MaterialCardView c=(MaterialCardView) v;c.setCardBackgroundColor(cardColors);c.setRadius(radius);c.setCardElevation(cardElevation);c.setStrokeWidth(stroke);c.setStrokeColor(strokeColors);c.setContentPadding(contentLeft,contentTop,contentRight,contentBottom);
            }
        }
    }
}
