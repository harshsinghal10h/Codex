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
import android.os.SystemClock;
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
 * LastWave: selected icon + label, quiet unselected icons. Vaso: even segments + moving lens.
 * The original navigation is retained for Classic and disabling navigation glass.
 */
public final class GlassNavigationView extends BottomNavigationView {
    private static final int MESH_X = 20, MESH_Y = 6;
    private static final long CAPTURE_INTERVAL_MS = 80;
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
    private String menuSignature = "";
    private Bitmap snapshot;
    private long lastCapture;
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
            menuSignature = "";
            refreshItems();
        } else {
            if (animator != null) animator.cancel();
            setBackground(nativeBackground);
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
        StringBuilder signature = new StringBuilder().append(settings.style).append(':').append(getSelectedItemId());
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            BadgeDrawable badge = getBadge(item.getItemId());
            signature.append('|').append(item.getItemId()).append(item.getTitle()).append(item.isVisible()).append(item.isEnabled())
                    .append(badge != null && badge.isVisible());
        }
        if (menuSignature.equals(signature.toString())) return;
        menuSignature = signature.toString();
        track.removeAllViews();
        boolean largeType = getResources().getConfiguration().fontScale >= 1.3f;
        boolean vertical = palette.vaso || largeType;
        int visibleCount = 0;
        for (int i = 0; i < getMenu().size(); i++) if (getMenu().getItem(i).isVisible()) visibleCount++;
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (!item.isVisible()) continue;
            boolean selected = item.getItemId() == getSelectedItemId();
            LinearLayout tab = new LinearLayout(getContext());
            tab.setId(item.getItemId());
            tab.setGravity(Gravity.CENTER);
            tab.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            tab.setPadding(dp(vertical ? 3 : 9), dp(3), dp(vertical ? 3 : 9), dp(3));
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
            if (selected || vertical) {
                TextView label = new TextView(getContext());
                label.setText(item.getTitle()); label.setTextSize(vertical ? 10 : 14);
                label.setTypeface(palette.vaso ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : GlassTypography.label(getContext()));
                label.setTextColor(selected ? palette.onSelected : palette.muted);
                label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER); label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(vertical ? ViewGroup.LayoutParams.MATCH_PARENT : 0, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (vertical) text.topMargin = dp(3); else { text.leftMargin = dp(7); text.weight = 1; }
                tab.addView(label, text);
            }
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                    !vertical && selected && visibleCount < 6 ? 1.85f : 1f);
            track.addView(tab, cell);
        }
        requestLayout(); invalidate();
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
        lastCapture = 0;
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
        long now = SystemClock.uptimeMillis();
        if (now - lastCapture < CAPTURE_INTERVAL_MS) return;
        lastCapture = now;
        View root = getRootView().findViewById(R.id.mobile_browse_root);
        if (root == null) return;
        // Haze is produced by a small sampled image; no full-screen bitmap or video capture.
        float scale = Math.max(0.10f, 0.55f - settings.blur / 100f * 0.43f);
        int width = Math.max(1, Math.round(getWidth() * scale)), height = Math.max(1, Math.round(getHeight() * scale));
        if (snapshot == null || snapshot.getWidth() != width || snapshot.getHeight() != height) {
            if (snapshot != null) snapshot.recycle();
            snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        getLocationInWindow(location); root.getLocationInWindow(parentLocation);
        snapshot.eraseColor(palette.canvas);
        Canvas sample = new Canvas(snapshot);
        sample.scale(scale, scale);
        sample.translate(parentLocation[0] - location[0], parentLocation[1] - location[1]);
        capturing = true;
        try { root.draw(sample); } catch (RuntimeException ignored) { snapshot.eraseColor(palette.canvas); }
        finally { capturing = false; }
        invalidate();
    }

    @Override public void draw(@NonNull Canvas canvas) { if (!capturing) super.draw(canvas); }

    @Override protected void dispatchDraw(@NonNull Canvas c) {
        if (!glass) { super.dispatchDraw(c); return; }
        clip.reset(); clip.addRoundRect(0, 0, getWidth(), getHeight(), dp(36), dp(36), Path.Direction.CW);
        int save = c.save(); c.clipPath(clip);
        c.drawColor(palette.canvas);
        if (snapshot != null && !settings.reduceTransparency) c.drawBitmapMesh(snapshot, MESH_X, MESH_Y, vertices, 0, null, 0, bitmapPaint);
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
        if (snapshot != null) { snapshot.recycle(); snapshot = null; }
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
