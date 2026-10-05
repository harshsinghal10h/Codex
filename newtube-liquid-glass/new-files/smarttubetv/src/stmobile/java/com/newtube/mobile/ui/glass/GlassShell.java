package com.newtube.mobile.ui.glass;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Geometry of the browse shell. Feed, placeholder and You panel share the same anchors. */
public final class GlassShell {
    private GlassShell() {}
    static void apply(View content, GlassPreferences.Snapshot s, GlassPalette p) {
        View root = content.findViewById(R.id.mobile_browse_root);
        if (!(root instanceof ConstraintLayout)) return;
        ConstraintLayout parent = (ConstraintLayout) root;
        View dock = parent.findViewById(R.id.mobile_bottom_nav);
        if (!(dock instanceof GlassNavigationView)) return;
        GlassNavigationView nav = (GlassNavigationView) dock;
        boolean enabled = s.style != GlassPreferences.STYLE_CLASSIC;
        View header = parent.findViewById(R.id.newtube_glass_header);
        if (header == null) {
            header = new View(parent.getContext()); header.setId(R.id.newtube_glass_header);
            header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, dp(root, 96));
            lp.topToTop = lp.startToStart = lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            parent.addView(header, 0, lp);
        }
        header.setVisibility(enabled && s.glassTop ? View.VISIBLE : View.GONE);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{p.canvas, GlassPalette.alpha(p.surface, p.vaso ? 0.34f : 0.95f)});
        float radius = dp(root, p.vaso ? 24 : 30);
        bg.setCornerRadii(new float[]{0,0,0,0,radius,radius,radius,radius});
        header.setBackground(bg);
        nav.configure(s, p);
        boolean floating = enabled && s.glassNav && s.floatingNav;
        if (floating) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) nav.getLayoutParams();
            lp.width = 0; lp.matchConstraintMaxWidth = dp(root, 390);
            lp.setMarginStart(dp(root, 20)); lp.setMarginEnd(dp(root, 20));
            lp.bottomMargin = dp(root, 12) + nav.unconsumedBottomInset();
            nav.setLayoutParams(lp);
        } else if (enabled && s.glassNav) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) nav.getLayoutParams();
            lp.width = 0; lp.matchConstraintMaxWidth = 0;
            lp.setMarginStart(dp(root, 8)); lp.setMarginEnd(dp(root, 8));
            lp.bottomMargin = nav.unconsumedBottomInset(); nav.setLayoutParams(lp);
        }
        // Extend the feed under the floating dock. The list's end padding keeps its last row reachable.
        for (int id : new int[]{R.id.mobile_content_swipe, R.id.mobile_feed_skeleton, R.id.mobile_you_panel, R.id.mobile_error_container}) {
            View surface = root.findViewById(id);
            if (surface == null) continue;
            GlassRuntime.remember(surface);
            if (floating) {
                ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) surface.getLayoutParams();
                lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                surface.setLayoutParams(lp);
            }
        }
        View rows = root.findViewById(R.id.mobile_you_rows);
        if (rows != null && floating) rows.setPadding(rows.getPaddingLeft(), rows.getPaddingTop(), rows.getPaddingRight(), navigationInset(rows));
    }
    public static int navigationInset(View list) {
        View nav = list.getRootView().findViewById(R.id.mobile_bottom_nav);
        if (!(nav instanceof GlassNavigationView) || !((GlassNavigationView) nav).isGlassEnabled()
                || !GlassPreferences.floatingNav(list.getContext())) return 0;
        int bottom = nav.getLayoutParams() instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) nav.getLayoutParams()).bottomMargin : 0;
        return Math.max(dp(nav, 64), nav.getMeasuredHeight()) + bottom + dp(nav, 16);
    }
    private static int dp(View v, float dp) { return Math.round(dp * v.getResources().getDisplayMetrics().density); }
}
