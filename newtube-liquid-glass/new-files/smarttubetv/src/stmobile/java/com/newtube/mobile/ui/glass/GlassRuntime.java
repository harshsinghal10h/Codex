package com.newtube.mobile.ui.glass;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.StateSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.ThemeMode;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Central presentation-only adapter for NewTube's touch frontend. */
public final class GlassRuntime {
    private static final List<WeakReference<Activity>> ACTIVITIES = new ArrayList<>();

    private GlassRuntime() {}

    public static void register(@NonNull Activity activity) {
        synchronized (ACTIVITIES) {
            prune();
            for (WeakReference<Activity> ref : ACTIVITIES) if (ref.get() == activity) return;
            ACTIVITIES.add(new WeakReference<>(activity));
        }
    }

    public static void unregister(@NonNull Activity activity) {
        synchronized (ACTIVITIES) {
            for (int i = ACTIVITIES.size() - 1; i >= 0; i--) {
                Activity a = ACTIVITIES.get(i).get();
                if (a == null || a == activity) ACTIVITIES.remove(i);
            }
        }
    }

    public static void refreshAll() {
        List<Activity> live = new ArrayList<>();
        synchronized (ACTIVITIES) {
            prune();
            for (WeakReference<Activity> ref : ACTIVITIES) {
                Activity a = ref.get();
                if (a != null) live.add(a);
            }
        }
        for (Activity activity : live) activity.runOnUiThread(() -> apply(activity));
    }

    public static void apply(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        GlassPreferences.Snapshot s = GlassPreferences.snapshot(activity);
        boolean dark = ThemeMode.currentNight(activity) == Configuration.UI_MODE_NIGHT_YES;
        int accent = resolveAccent(activity, s, dark);
        applyTree(content, s, dark, accent);
    }

    public static void restoreForThemeRefresh(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content != null) restoreTree(content);
    }

    public static void rebaseline(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content != null) clearBaseline(content);
    }

    private static void applyTree(View view, GlassPreferences.Snapshot s, boolean dark, int accent) {
        remember(view);
        String id = idName(view);

        if (s.style == GlassPreferences.STYLE_CLASSIC) {
            restore(view);
        } else {
            boolean bottom = view instanceof BottomNavigationView || "mobile_bottom_nav".equals(id);
            boolean top = id.startsWith("mobile_title_") || id.contains("search_field") || id.contains("settings_search");
            boolean mini = "mobile_mini_player".equals(id);
            boolean card = view instanceof MaterialCardView || id.contains("sheet") || id.endsWith("_card");
            boolean button = view instanceof MaterialButton || view instanceof ImageButton;
            boolean settings = id.contains("settings_") && !(view instanceof TextView);

            boolean glass = (bottom && s.glassNav) || (top && s.glassTop)
                    || (mini && s.glassMini) || (card && s.glassCards)
                    || (button && s.glassButtons) || (settings && s.glassSettings);

            if (glass) applyGlass(view, s, dark || mini, accent);
            else restoreSurface(view);

            if (s.highContrastText && view instanceof TextView) {
                ((TextView) view).setTextColor(dark ? Color.WHITE : Color.BLACK);
            }

            if (s.roundedThumbnails && view instanceof ImageView
                    && (id.contains("thumb") || id.contains("thumbnail") || id.contains("avatar") || id.contains("artwork"))) {
                view.setClipToOutline(true);
                view.setOutlineProvider(new RoundedOutlineProvider(dp(view.getContext(), Math.max(8, s.radius - 6))));
            }
        }

        if (view instanceof RecyclerView) installRecyclerHook((RecyclerView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i), s, dark, accent);
        }
    }

    private static void applyGlass(View view, GlassPreferences.Snapshot s, boolean dark, int accent) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(new GlassDrawable(s, dark, accent, density));
        ViewCompat.setElevation(view, dp(view.getContext(), s.elevation));
        view.setClipToOutline(true);
        view.setOutlineProvider(s.style == GlassPreferences.STYLE_LASTWAVE
                ? new SquircleOutlineProvider(dp(view.getContext(), s.radius))
                : new RoundedOutlineProvider(dp(view.getContext(), s.radius)));
        if (view.isClickable()) view.setHapticFeedbackEnabled(s.hapticPress);
        installPressMotion(view, s);
    }

    private static void installPressMotion(View view, GlassPreferences.Snapshot s) {
        if (Build.VERSION.SDK_INT < 21 || !view.isClickable() || s.motion == 0 || s.press <= 100) return;
        float pressed = s.press / 100f;
        long duration = Math.max(70L, Math.round(120f * (s.motion / 100f)));
        StateListAnimator states = new StateListAnimator();

        AnimatorSet down = new AnimatorSet();
        down.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, pressed),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, pressed));
        down.setDuration(duration);

        AnimatorSet up = new AnimatorSet();
        up.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f));
        up.setDuration(duration + 40L);

        states.addState(new int[]{android.R.attr.state_pressed}, down);
        states.addState(StateSet.WILD_CARD, up);
        view.setStateListAnimator(states);
    }

    private static void installRecyclerHook(RecyclerView rv) {
        if (Boolean.TRUE.equals(rv.getTag(R.id.newtube_glass_recycler_hook))) return;
        rv.setTag(R.id.newtube_glass_recycler_hook, true);
        rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View child) {
                Context c = child.getContext();
                GlassPreferences.Snapshot s = GlassPreferences.snapshot(c);
                boolean dark = (c.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                applyTree(child, s, dark, resolveAccent(c, s, dark));
            }
            @Override public void onChildViewDetachedFromWindow(@NonNull View child) {}
        });
    }

    private static void remember(View view) {
        if (Boolean.TRUE.equals(view.getTag(R.id.newtube_glass_original_saved))) return;
        view.setTag(R.id.newtube_glass_original_saved, true);
        view.setTag(R.id.newtube_glass_original_background, view.getBackground());
        view.setTag(R.id.newtube_glass_original_elevation, ViewCompat.getElevation(view));
        view.setTag(R.id.newtube_glass_original_outline_provider, view.getOutlineProvider());
        view.setTag(R.id.newtube_glass_original_clip_outline, view.getClipToOutline());
        view.setTag(R.id.newtube_glass_original_haptic, view.isHapticFeedbackEnabled());
        if (Build.VERSION.SDK_INT >= 21) view.setTag(R.id.newtube_glass_original_animator, view.getStateListAnimator());
        if (view instanceof TextView) {
            view.setTag(R.id.newtube_glass_original_text_colors, ((TextView) view).getTextColors());
        }
    }

    private static void restoreSurface(View view) {
        if (!Boolean.TRUE.equals(view.getTag(R.id.newtube_glass_original_saved))) return;
        view.setBackground((Drawable) view.getTag(R.id.newtube_glass_original_background));
        Object elevation = view.getTag(R.id.newtube_glass_original_elevation);
        if (elevation instanceof Float) ViewCompat.setElevation(view, (Float) elevation);
        view.setOutlineProvider((ViewOutlineProvider) view.getTag(R.id.newtube_glass_original_outline_provider));
        Object clip = view.getTag(R.id.newtube_glass_original_clip_outline);
        if (clip instanceof Boolean) view.setClipToOutline((Boolean) clip);
        Object haptic = view.getTag(R.id.newtube_glass_original_haptic);
        if (haptic instanceof Boolean) view.setHapticFeedbackEnabled((Boolean) haptic);
        if (Build.VERSION.SDK_INT >= 21) {
            Object animator = view.getTag(R.id.newtube_glass_original_animator);
            if (animator == null || animator instanceof StateListAnimator) view.setStateListAnimator((StateListAnimator) animator);
        }
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private static void restore(View view) {
        restoreSurface(view);
        if (view instanceof TextView) {
            Object colors = view.getTag(R.id.newtube_glass_original_text_colors);
            if (colors instanceof android.content.res.ColorStateList) {
                ((TextView) view).setTextColor((android.content.res.ColorStateList) colors);
            }
        }
    }

    private static void restoreTree(View view) {
        restore(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) restoreTree(group.getChildAt(i));
        }
    }

    private static void clearBaseline(View view) {
        view.setTag(R.id.newtube_glass_original_saved, null);
        view.setTag(R.id.newtube_glass_original_background, null);
        view.setTag(R.id.newtube_glass_original_elevation, null);
        view.setTag(R.id.newtube_glass_original_outline_provider, null);
        view.setTag(R.id.newtube_glass_original_clip_outline, null);
        view.setTag(R.id.newtube_glass_original_haptic, null);
        view.setTag(R.id.newtube_glass_original_animator, null);
        view.setTag(R.id.newtube_glass_original_text_colors, null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clearBaseline(group.getChildAt(i));
        }
    }

    private static String idName(View view) {
        if (view.getId() == View.NO_ID) return "";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static int resolveAccent(Context c, GlassPreferences.Snapshot s, boolean dark) {
        if (!s.dynamicTint) return dark ? Color.rgb(190, 198, 214) : Color.rgb(92, 100, 116);
        if (Build.VERSION.SDK_INT >= 31) {
            try { return ContextCompat.getColor(c, android.R.color.system_accent1_500); }
            catch (RuntimeException ignored) {}
        }
        try { return ContextCompat.getColor(c, R.color.mobile_color_primary); }
        catch (RuntimeException ignored) { return Color.RED; }
    }

    private static float dp(Context c, float value) {
        return value * c.getResources().getDisplayMetrics().density;
    }

    private static void prune() {
        for (int i = ACTIVITIES.size() - 1; i >= 0; i--) if (ACTIVITIES.get(i).get() == null) ACTIVITIES.remove(i);
    }

    private static final class RoundedOutlineProvider extends ViewOutlineProvider {
        private final float radius;
        RoundedOutlineProvider(float radius) { this.radius = radius; }
        @Override public void getOutline(View view, Outline outline) {
            float max = Math.min(view.getWidth(), view.getHeight()) / 2f;
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.min(radius, max));
        }
    }

    private static final class SquircleOutlineProvider extends ViewOutlineProvider {
        private final float radius;
        SquircleOutlineProvider(float radius) { this.radius = radius; }
        @Override public void getOutline(View view, Outline outline) {
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) return;
            android.graphics.Path path = GlassGeometry.squircle(new RectF(0f, 0f, w, h), radius);
            if (Build.VERSION.SDK_INT >= 30) outline.setPath(path); else outline.setConvexPath(path);
        }
    }
}
