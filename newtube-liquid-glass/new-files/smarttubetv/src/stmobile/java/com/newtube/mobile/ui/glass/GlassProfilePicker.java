package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Two clear, directly selectable visual profiles at the top of Appearance. */
public final class GlassProfilePicker extends LinearLayout {
    private final Runnable changed;
    public GlassProfilePicker(Context c, Runnable changed) {
        super(c); this.changed = changed; setId(R.id.newtube_glass_profile_picker);
        setOrientation(HORIZONTAL); setPadding(dp(16), dp(4), dp(16), dp(12));
        render();
    }
    public void render() {
        removeAllViews();
        addProfile(GlassPreferences.STYLE_VASO, R.string.newtube_glass_profile_vaso_name, R.string.newtube_glass_profile_vaso_detail);
        addProfile(GlassPreferences.STYLE_LASTWAVE, R.string.newtube_glass_profile_lastwave_name, R.string.newtube_glass_profile_lastwave_detail);
    }
    private void addProfile(int style, int name, int detail) {
        boolean selected = GlassPreferences.style(getContext()) == style, vaso = style == GlassPreferences.STYLE_VASO;
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        int ink = vaso ? Color.rgb(32, 38, 42) : Color.rgb(245, 242, 226);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                vaso ? new int[]{Color.rgb(251, 249, 241), Color.rgb(231, 235, 231)} : new int[]{Color.rgb(50, 49, 43), Color.rgb(29, 29, 27)});
        bg.setCornerRadius(dp(22)); bg.setStroke(dp(selected ? 2 : 1), selected ? (vaso ? Color.rgb(92, 130, 140) : Color.rgb(191, 177, 127)) : GlassPalette.alpha(ink, 0.12f));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(GlassPalette.alpha(ink, 0.1f)), bg, null));
        card.setClickable(true); card.setFocusable(true); card.setSelected(selected);
        card.setContentDescription(getContext().getString(name) + ", " + getContext().getString(detail));
        card.setOnClickListener(v -> { GlassPreferences.applyPreset(getContext(), style); changed.run(); });
        card.addView(new Preview(getContext(), vaso), new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));
        TextView title = new TextView(getContext()); title.setText(name); title.setTextColor(ink); title.setTextSize(16);
        title.setTypeface(GlassTypography.label(getContext()));
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams titleLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); titleLp.topMargin = dp(10);
        card.addView(title, titleLp);
        TextView caption = new TextView(getContext()); caption.setText(detail); caption.setTextColor(GlassPalette.alpha(ink, 0.75f)); caption.setTextSize(11);
        caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); caption.setMaxLines(2);
        card.addView(caption, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1); if (!vaso) lp.leftMargin = dp(10);
        addView(card, lp);
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private static final class Preview extends View {
        private final boolean vaso;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(Context c, boolean vaso) { super(c); this.vaso = vaso; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(), h=getHeight(), r=h/2;
            paint.setColor(vaso ? 0xAFFFFFFF : 0xFF45443E); c.drawRoundRect(0,0,w,h,r,r,paint);
            paint.setColor(vaso ? 0x7099B8B9 : 0xFF776C44); c.drawRoundRect(4,4,w*.48f,h-4,r,r,paint);
            paint.setColor(vaso ? 0xFF3B565E : 0xFFF4EBD0);
            c.drawCircle(w*.17f,h*.5f,h*.12f,paint); c.drawCircle(w*.68f,h*.5f,h*.09f,paint); c.drawCircle(w*.87f,h*.5f,h*.09f,paint);
            paint.setStrokeWidth(1); paint.setStyle(Paint.Style.STROKE); paint.setColor(vaso ? 0xDFFFFFFF : 0x30FFFFFF); c.drawRoundRect(1,1,w-1,h-1,r,r,paint); paint.setStyle(Paint.Style.FILL);
        }
    }
}
