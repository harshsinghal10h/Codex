package com.newtube.mobile.ui.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * A real dock, backed by the original Material menu/presenter and selection listeners.
 * Both profiles retain even fixed-size segments; only the selection lens and tint move.
 * The original navigation is retained for Classic and disabling navigation glass.
 */
public final class GlassNavigationView extends BottomNavigationView {
    private static final int MESH_X = 20, MESH_Y = 6;
    private final LinearLayout track;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF lensBounds = new RectF(), targetLens = new RectF();
    private final float[] vertices = new float[(MESH_X + 1) * (MESH_Y + 1) * 2];
    private final int[] location = new int[2], parentLocation = new int[2];
    private final android.util.SparseArray<View.OnLongClickListener> longClicks = new android.util.SparseArray<>();
    private boolean glass, capturing;
    private final ViewTreeObserver.OnPreDrawListener observer = () -> {
        if (glass) { refreshItems(); captureBackdrop(); }
        return true;
    };
    private GlassPreferences.Snapshot settings;
    private GlassPalette palette;
    private GlassDrawable dock, lens;
    private long menuSignature = Long.MIN_VALUE, stateSignature = Long.MIN_VALUE;
    private HardwareBackdrop hardwareBackdrop;
    private ViewGroup backdropRoot;
    private long softwareScene = Long.MIN_VALUE;
    private final Path lensClip = new Path();
    private Bitmap snapshot;
    private ColorStateList nativeIconTint, nativeTextTint;
    private ValueAnimator animator;
    private Drawable nativeBackground;
    private int nativePaddingLeft, nativePaddingTop, nativePaddingRight, nativePaddingBottom;
    private boolean nativeSaved;

    public GlassNavigationView(Context c, AttributeSet attrs) {
        super(c, attrs);
        track = new LinearLayout(c);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(track, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        track.setVisibility(GONE);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void configure(GlassPreferences.Snapshot s, GlassPalette p) {
        boolean enabled = s.style != GlassPreferences.STYLE_CLASSIC && s.glassNav;
        if (!nativeSaved) {
            nativeBackground = getBackground();
            nativeIconTint = getItemIconTintList(); nativeTextTint = getItemTextColor();
            nativePaddingLeft = getPaddingLeft(); nativePaddingTop = getPaddingTop();
            nativePaddingRight = getPaddingRight(); nativePaddingBottom = getPaddingBottom();
            nativeSaved = true;
        }
        settings = s; palette = p;
        glass = enabled;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != track) {
                child.setVisibility(enabled ? GONE : VISIBLE);
                child.setImportantForAccessibility(enabled ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
        }
        track.setVisibility(enabled ? VISIBLE : GONE);
        if (enabled) {
            setBackground(null);
            setPadding(0, 0, 0, 0);
            setClipToOutline(true);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(36)); o.setAlpha(0.5f);
                }
            });
            ViewCompat.setElevation(this, dp(s.elevation));
            dock = new GlassDrawable(s, p, GlassDrawable.Role.DOCK, density(), 36);
            lens = new GlassDrawable(s, p, GlassDrawable.Role.LENS, density(), 30);
            menuSignature = Long.MIN_VALUE; stateSignature = Long.MIN_VALUE;
            if (Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated()) {
                if (hardwareBackdrop == null) hardwareBackdrop = new HardwareBackdrop();
                hardwareBackdrop.configure(s, density());
            }
            refreshItems();
        } else {
            if (animator != null) animator.cancel();
            releaseBackdrop();
            setBackground(s.amoled && p.dark ? new android.graphics.drawable.ColorDrawable(Color.BLACK) : nativeBackground);
            int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
            ColorStateList colors = new ColorStateList(states, new int[]{p.accent, p.muted});
            setItemIconTintList(s.dynamicTint ? colors : nativeIconTint);
            setItemTextColor(s.dynamicTint ? colors : nativeTextTint);
            setPadding(nativePaddingLeft, nativePaddingTop, nativePaddingRight, nativePaddingBottom);
            setClipToOutline(false);
            ViewCompat.setElevation(this, 0);
            lensBounds.setEmpty();
        }
        requestLayout(); invalidate();
    }

    public boolean isGlassEnabled() { return glass; }
    public View findDockTab(int id) { return track.findViewById(id); }
    public View findNavigationItem(int id) { return glass ? findDockTab(id) : findViewById(id); }
    public void setNavigationLongClickListener(int id, View.OnLongClickListener listener) {
        longClicks.put(id, listener);
        View nativeTab = findViewById(id), customTab = findDockTab(id);
        if (nativeTab != null) nativeTab.setOnLongClickListener(listener);
        if (customTab != null) customTab.setOnLongClickListener(listener);
    }
    void rebaselineNative() { nativeSaved = false; }

    @Override public void setSelectedItemId(int id) {
        super.setSelectedItemId(id);
        // Also used by the presenter's restore/highlight paths, not only clicks.
        if (track != null && glass) refreshItems();
    }

    private void refreshItems() {
        if (!glass || settings == null) return;
        long shape = 17, state = getSelectedItemId();
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            shape = shape * 31 + item.getItemId();
            shape = shape * 31 + (item.getTitle() == null ? 0 : item.getTitle().hashCode());
            shape = shape * 31 + (item.isVisible() ? 1 : 0);
            shape = shape * 31 + System.identityHashCode(item.getIcon());
            BadgeDrawable badge = getBadge(item.getItemId());
            state = state * 31 + (item.isEnabled() ? 1 : 0);
            state = state * 31 + (badge != null && badge.isVisible() ? 1 : 0);
        }
        if (menuSignature == shape) {
            if (stateSignature != state) { updateTabStates(); updateLens(); stateSignature = state; invalidate(); }
            return;
        }
        menuSignature = shape; stateSignature = state;
        track.removeAllViews();
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (!item.isVisible()) continue;
            boolean selected = item.getItemId() == getSelectedItemId();
            LinearLayout tab = new LinearLayout(getContext());
            tab.setId(item.getItemId());
            tab.setGravity(Gravity.CENTER);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setPadding(dp(3), dp(3), dp(3), dp(3));
            tab.setMinimumWidth(dp(48)); tab.setMinimumHeight(dp(48));
            tab.setClickable(true); tab.setFocusable(true); tab.setEnabled(item.isEnabled());
            tab.setSelected(selected); tab.setContentDescription(item.getTitle());
            tab.setHapticFeedbackEnabled(settings.hapticPress);
            tab.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View v, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(v, info);
                    info.setClassName("android.widget.RadioButton"); info.setCheckable(true); info.setChecked(v.isSelected());
                }
            });
            tab.setOnClickListener(v -> {
                if (settings.hapticPress) v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                setSelectedItemId(item.getItemId());
            });
            tab.setOnLongClickListener(longClicks.get(item.getItemId()));
            ImageView icon = new ImageView(getContext());
            Drawable source = item.getIcon();
            if (source != null) {
                Drawable copy = source.getConstantState() != null ? source.getConstantState().newDrawable(getResources()).mutate() : source.mutate();
                copy.setState(selected ? new int[]{android.R.attr.state_checked} : new int[]{});
                copy.setTint(selected ? palette.onSelected : palette.muted); icon.setImageDrawable(copy);
            }
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
            {
                TextView label = new TextView(getContext());
                label.setText(item.getTitle()); label.setTextSize(10);
                label.setTypeface(palette.vaso ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : GlassTypography.label(getContext()));
                label.setTextColor(selected ? palette.onSelected : palette.muted);
                label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER); label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                text.topMargin = dp(3);
                tab.addView(label, text);
            }
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                    1f);
            track.addView(tab, cell);
        }
        requestLayout(); invalidate();
    }

    private void updateTabStates() {
        for (int i = 0; i < track.getChildCount(); i++) {
            LinearLayout tab = (LinearLayout) track.getChildAt(i);
            MenuItem item = getMenu().findItem(tab.getId());
            if (item == null) continue;
            boolean selected = item.getItemId() == getSelectedItemId();
            tab.setSelected(selected); tab.setEnabled(item.isEnabled());
            Drawable icon = ((ImageView) tab.getChildAt(0)).getDrawable();
            if (icon != null) {
                icon.setState(selected ? new int[]{android.R.attr.state_checked} : new int[]{});
                icon.setTint(selected ? palette.onSelected : palette.muted);
            }
            ((TextView) tab.getChildAt(1)).setTextColor(selected ? palette.onSelected : palette.muted);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (!glass) { super.onMeasure(widthSpec, heightSpec); return; }
        int width = MeasureSpec.getSize(widthSpec);
        int height = resolveSize(dp(getResources().getConfiguration().fontScale >= 1.3f ? 76 : 64), heightSpec);
        setMeasuredDimension(width, height);
        track.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(16)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - dp(12)), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!glass) { super.onLayout(changed, l, t, r, b); return; }
        track.layout(dp(8), dp(6), getWidth() - dp(8), getHeight() - dp(6));
        dock.setBounds(0, 0, getWidth(), getHeight());
        updateLens();
        updateMesh();
        backdropRoot = null;
    }

    private void updateLens() {
        View selected = track.findViewById(getSelectedItemId());
        if (selected == null || selected.getWidth() == 0) return;
        targetLens.set(track.getLeft() + selected.getLeft(), track.getTop() + selected.getTop(),
                track.getLeft() + selected.getRight(), track.getTop() + selected.getBottom());
        if (lensBounds.isEmpty() || settings.motion == 0 || !isLaidOut()) { lensBounds.set(targetLens); return; }
        if (Math.abs(targetLens.left - lensBounds.left) < 0.5f && Math.abs(targetLens.right - lensBounds.right) < 0.5f) return;
        if (animator != null) animator.cancel();
        final float left = lensBounds.left, right = lensBounds.right, endLeft = targetLens.left, endRight = targetLens.right;
        lensBounds.top = targetLens.top; lensBounds.bottom = targetLens.bottom;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(Math.round(240 * Math.min(1.4f, settings.motion / 100f)));
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            lensBounds.left = left + (endLeft - left) * f; lensBounds.right = right + (endRight - right) * f;
            invalidate();
        });
        animator.start();
    }

    private void updateMesh() {
        float strength = settings.depth / 100f * (palette.vaso ? 0.14f : 0.025f);
        int n = 0;
        for (int y = 0; y <= MESH_Y; y++) for (int x = 0; x <= MESH_X; x++) {
            float u = x / (float) MESH_X, v = y / (float) MESH_Y;
            // Boundary vertices remain fixed. The bezel bulges the captured interior.
            float dx = (float) Math.sin(u * Math.PI * 2) * strength * getHeight();
            float dy = (float) Math.sin(v * Math.PI * 2) * strength * getHeight() * 0.28f;
            vertices[n++] = u * getWidth() - dx; vertices[n++] = v * getHeight() - dy;
        }
    }

    private void captureBackdrop() {
        if (settings.reduceTransparency || !isShown() || getWidth() <= 0 || getHeight() <= 0) return;
        if (backdropRoot == null) {
            View root = getRootView().findViewById(R.id.mobile_browse_root);
            if (!(root instanceof ViewGroup)) return;
            backdropRoot = (ViewGroup) root;
        }
        getLocationInWindow(location); backdropRoot.getLocationInWindow(parentLocation);
        if (Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated()) {
            if (hardwareBackdrop == null) { hardwareBackdrop = new HardwareBackdrop(); hardwareBackdrop.configure(settings, density()); }
            // Update the referenced display list before this frame is drawn. No invalidation here:
            // an idle screen schedules no extra frames, and scrolling has no delayed pixel readback.
            hardwareBackdrop.record(this);
        } else {
            long scene = 17; boolean dirty = snapshot == null;
            for (int i = 0; i < backdropRoot.getChildCount(); i++) {
                View child = backdropRoot.getChildAt(i); if (child == this || child.getId() == R.id.mobile_mini_player) continue;
                scene = scene * 31 + child.getVisibility(); scene = scene * 31 + child.getScrollY();
                scene = scene * 31 + child.getTop(); scene = scene * 31 + child.getBottom();
                dirty |= child.getVisibility() == VISIBLE && child.isDirty();
            }
            dirty |= scene != softwareScene; softwareScene = scene;
            captureSoftwareBackdrop();
            if (dirty) invalidate();
        }
    }

    private void captureSoftwareBackdrop() {
        float scale = Math.max(0.10f, 0.55f - settings.blur / 100f * 0.43f);
        int width = Math.max(1, Math.round(getWidth() * scale)), height = Math.max(1, Math.round(getHeight() * scale));
        if (snapshot == null || snapshot.getWidth() != width || snapshot.getHeight() != height) {
            if (snapshot != null) snapshot.recycle();
            snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        snapshot.eraseColor(palette.canvas);
        Canvas sample = new Canvas(snapshot); sample.scale(scale, scale);
        try { drawBackdrop(sample, 0); }
        catch (IllegalArgumentException hardwareBitmapOnSoftwareCanvas) { snapshot.eraseColor(palette.canvas); }
    }

    /** Draw only content behind the dock, clipped to its small viewport. Never draw the dock/video. */
    private void drawBackdrop(Canvas c, int padding) {
        int save = c.save();
        c.clipRect(0, 0, getWidth() + padding * 2, getHeight() + padding * 2);
        c.drawColor(palette.canvas);
        c.translate(parentLocation[0] - location[0] + padding, parentLocation[1] - location[1] + padding);
        Drawable background = backdropRoot.getBackground(); if (background != null) background.draw(c);
        capturing = true;
        try {
            for (int i = 0; i < backdropRoot.getChildCount(); i++) {
                View child = backdropRoot.getChildAt(i);
                if (child == this || child.getId() == R.id.mobile_mini_player || child.getVisibility() != VISIBLE || child.getAlpha() == 0) continue;
                float left = child.getLeft() + child.getTranslationX(), top = child.getTop() + child.getTranslationY();
                if (c.quickReject(left, top, left + child.getWidth(), top + child.getHeight(), Canvas.EdgeType.AA)) continue;
                int childSave = c.save();
                c.translate(child.getLeft(), child.getTop());
                if (!child.getMatrix().isIdentity()) c.concat(child.getMatrix());
                int alphaSave = child.getAlpha() < 1 ? c.saveLayerAlpha(0, 0, child.getWidth(), child.getHeight(), Math.round(child.getAlpha() * 255)) : -1;
                child.draw(c);
                if (alphaSave >= 0) c.restoreToCount(alphaSave);
                c.restoreToCount(childSave);
            }
        } finally { capturing = false; c.restoreToCount(save); }
    }

    private void releaseBackdrop() {
        if (snapshot != null) { snapshot.recycle(); snapshot = null; }
        if (hardwareBackdrop != null) { hardwareBackdrop.release(); hardwareBackdrop = null; }
        backdropRoot = null;
    }

    /** API 29 classes stay isolated from the API 24-28 compatibility path. */
    @androidx.annotation.RequiresApi(29)
    private static final class HardwareBackdrop {
        final android.graphics.RenderNode node = new android.graphics.RenderNode("NewTube dock backdrop");
        int padding;
        void configure(GlassPreferences.Snapshot s, float density) {
            padding = Math.round(24 * density);
            if (Build.VERSION.SDK_INT >= 31) Blur.configure(node, s, density);
        }
        void record(GlassNavigationView nav) {
            int width = nav.getWidth() + padding * 2, height = nav.getHeight() + padding * 2;
            node.setPosition(0, 0, width, height);
            Canvas sample = node.beginRecording(width, height);
            try { nav.drawBackdrop(sample, padding); } finally { node.endRecording(); }
        }
        void draw(Canvas c) {
            int save = c.save(); c.translate(-padding, -padding); c.drawRenderNode(node); c.restoreToCount(save);
        }
        void release() { node.discardDisplayList(); }
    }
    @androidx.annotation.RequiresApi(31)
    private static final class Blur {
        static void configure(android.graphics.RenderNode node, GlassPreferences.Snapshot s, float density) {
            android.graphics.ColorMatrix saturation = new android.graphics.ColorMatrix(); saturation.setSaturation(s.saturation / 100f);
            float contrast = s.contrast / 100f, shift = (1 - contrast) * 127.5f;
            android.graphics.ColorMatrix grading = new android.graphics.ColorMatrix(new float[]{contrast,0,0,0,shift, 0,contrast,0,0,shift, 0,0,contrast,0,shift, 0,0,0,1,0});
            grading.postConcat(saturation);
            android.graphics.RenderEffect effect = android.graphics.RenderEffect.createColorFilterEffect(new android.graphics.ColorMatrixColorFilter(grading));
            float radius = s.blur * .20f * density;
            if (radius > 0) effect = android.graphics.RenderEffect.createBlurEffect(radius, radius, effect, android.graphics.Shader.TileMode.CLAMP);
            node.setRenderEffect(effect);
        }
    }

    @Override public void draw(@NonNull Canvas canvas) { if (!capturing) super.draw(canvas); }

    @Override protected void dispatchDraw(@NonNull Canvas c) {
        // Software ViewGroup.drawChild can dispatch children without calling public draw().
        // Exclude the dock from both entry paths, otherwise it refracts its own old labels.
        if (capturing) return;
        if (!glass) { super.dispatchDraw(c); return; }
        clip.reset(); clip.addRoundRect(0, 0, getWidth(), getHeight(), dp(36), dp(36), Path.Direction.CW);
        int save = c.save(); c.clipPath(clip);
        c.drawColor(palette.canvas);
        if (!settings.reduceTransparency) {
            if (Build.VERSION.SDK_INT >= 29 && c.isHardwareAccelerated() && hardwareBackdrop != null) {
                hardwareBackdrop.draw(c);
                if (!lensBounds.isEmpty() && settings.depth > 0) {
                    int refraction = c.save(); lensClip.reset();
                    lensClip.addRoundRect(lensBounds, dp(30), dp(30), Path.Direction.CW); c.clipPath(lensClip);
                    float scale = 1 + settings.depth / 100f * (palette.vaso ? .055f : .02f);
                    c.scale(scale, scale, lensBounds.centerX(), lensBounds.centerY()); hardwareBackdrop.draw(c);
                    c.restoreToCount(refraction);
                }
            } else {
                if (backdropRoot != null && snapshot == null) captureSoftwareBackdrop();
                if (snapshot != null) c.drawBitmapMesh(snapshot, MESH_X, MESH_Y, vertices, 0, null, 0, bitmapPaint);
            }
        }
        dock.draw(c);
        if (!lensBounds.isEmpty()) {
            lens.setBounds(Math.round(lensBounds.left), Math.round(lensBounds.top), Math.round(lensBounds.right), Math.round(lensBounds.bottom));
            lens.draw(c);
        }
        super.dispatchDraw(c);
        // Mirror the existing update badge; its Material child is hidden in dock mode.
        badgePaint.setColor(palette.accent);
        for (int i = 0; i < track.getChildCount(); i++) {
            View tab = track.getChildAt(i);
            BadgeDrawable badge = getBadge(tab.getId());
            if (badge != null && badge.isVisible()) c.drawCircle(track.getLeft() + tab.getRight() - dp(10), track.getTop() + dp(9), dp(3), badgePaint);
        }
        c.restoreToCount(save);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); getViewTreeObserver().addOnPreDrawListener(observer);
    }
    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(observer);
        if (animator != null) animator.cancel();
        releaseBackdrop();
        super.onDetachedFromWindow();
    }

    /** Insets still belong to NewTube. Only add the portion not already consumed by its parent. */
    public int unconsumedBottomInset() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets == null || !(getParent() instanceof View)) return 0;
        View parent = (View) getParent(); parent.getLocationInWindow(parentLocation);
        int fitted = Math.max(0, getRootView().getHeight() - parentLocation[1] - parent.getHeight());
        return Math.max(0, insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).bottom - fitted);
    }
    private float density() { return getResources().getDisplayMetrics().density; }
    private int dp(float value) { return Math.round(value * density()); }
}
