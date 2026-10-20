package com.newtube.mobile.ui.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import com.newtube.mobile.ui.playback.PlayerTimeBar;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClearPdfFrontendTest {
    private Context context;
    @Before public void setup() {
        context = new android.view.ContextThemeWrapper(org.robolectric.RuntimeEnvironment.getApplication(),
                com.liskovsoft.smartyoutubetv2.tv.R.style.Theme_NewTube);
        context.getSharedPreferences("newtube_liquid_glass",0).edit().clear().commit();
    }
    @Test public void newInstallUsesClearPdfWithCorrectLightAndDarkInk() {
        assertEquals(GlassPreferences.STYLE_CLEARPDF,GlassPreferences.style(context));
        GlassPalette light = new GlassPalette(context, GlassPreferences.snapshot(context), false);
        GlassPalette dark = new GlassPalette(context, GlassPreferences.snapshot(context), true);
        assertEquals(0xFFE9EAEE,light.canvas); assertEquals(0xFF1C1E1D,dark.canvas);
        assertEquals(0xFF0A84FF,light.accent); assertEquals(0xFF0A84FF,dark.accent);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(light.ink,light.canvas)>7);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(dark.ink,dark.canvas)>7);
    }
    @Test public void nativeSliderPreservesProgressWhenChangingProfiles() {
        ClearPdfSeekBar bar = new ClearPdfSeekBar(context,null);
        bar.setMax(100); bar.setProgress(37); bar.setSecondaryProgress(68);
        android.graphics.drawable.Drawable clearThumb=bar.getThumb();
        GlassPreferences.applyPreset(context,GlassPreferences.STYLE_CLASSIC); bar.render();
        assertEquals(37,bar.getProgress()); assertEquals(68,bar.getSecondaryProgress());
        assertNotSame(clearThumb,bar.getThumb());
        GlassPreferences.applyPreset(context,GlassPreferences.STYLE_CLEARPDF); bar.render();
        assertEquals(37,bar.getProgress()); assertEquals(68,bar.getSecondaryProgress());
        assertTrue(bar.getThumb().getIntrinsicWidth()>bar.getThumb().getIntrinsicHeight());
    }
    @Test public void playerRetainsDurationAndAccessibleRangeWithGlassSlider() {
        PlayerTimeBar bar = new PlayerTimeBar(context,null);
        bar.setDuration(120000); bar.setPosition(60000); bar.setBufferedPosition(90000);
        bar.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));
        bar.layout(0,0,360,48);
        Bitmap bitmap=Bitmap.createBitmap(360,48,Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(bitmap));
        android.view.accessibility.AccessibilityNodeInfo node=android.view.accessibility.AccessibilityNodeInfo.obtain();
        bar.onInitializeAccessibilityNodeInfo(node);
        assertNotNull(node.getRangeInfo());
        assertEquals(60000f,node.getRangeInfo().getCurrent(),1f);
        assertTrue(node.getActionList().contains(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS));
        android.os.Bundle target=new android.os.Bundle();
        target.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,90000f);
        assertTrue(bar.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),target));
        android.view.accessibility.AccessibilityNodeInfo updated=android.view.accessibility.AccessibilityNodeInfo.obtain();
        bar.onInitializeAccessibilityNodeInfo(updated); assertEquals(90000f,updated.getRangeInfo().getCurrent(),1f);
        target.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,Float.NaN);
        assertFalse(bar.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),target));
        updated.recycle();
        bitmap.recycle(); node.recycle();
    }
    @Test public void clearPdfBrowseHeaderAndDockFitTheScreen() throws Exception {
        android.app.Activity activity=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        activity.setTheme(com.liskovsoft.smartyoutubetv2.tv.R.style.Theme_NewTube);
        activity.setContentView(com.liskovsoft.smartyoutubetv2.tv.R.layout.activity_mobile_browse);
        android.widget.TextView title=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_title_bar);
        title.setText("NewTube");
        GlassNavigationView dock=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_bottom_nav);
        dock.getMenu().clear();
        dock.getMenu().add(0,801,0,"Home").setIcon(com.liskovsoft.smartyoutubetv2.tv.R.drawable.ic_nav_home);
        dock.getMenu().add(0,802,1,"You").setIcon(com.liskovsoft.smartyoutubetv2.tv.R.drawable.ic_nav_you);
        dock.setSelectedItemId(801);
        GlassRuntime.apply(activity);
        View root=activity.findViewById(android.R.id.content);
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,360,800);
        assertTrue(title.getWidth()>0); assertTrue(title.getRight()<=360);
        assertTrue(title.getLeft()>=0); assertTrue(dock.getWidth()<=360);
        assertTrue(dock.getHeight()>=48);
        View cast=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_cast_button);
        assertEquals(title.getTop()+title.getHeight()/2,cast.getTop()+cast.getHeight()/2);
        Bitmap bitmap=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        java.io.File directory=new java.io.File("build/clear-pdf-qa"); directory.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"browse-light.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }
        bitmap.recycle(); activity.finish();
    }
    @Test public void offlinePlayerControlsRenderWithGlassAndKeepTheirTouchTargets() throws Exception {
        android.app.Activity activity=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        activity.setTheme(com.liskovsoft.smartyoutubetv2.tv.R.style.Theme_NewTube);
        activity.setContentView(com.liskovsoft.smartyoutubetv2.tv.R.layout.activity_mobile_playback);
        View video=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_video_area);
        android.view.ViewGroup.LayoutParams lp=video.getLayoutParams(); lp.height=203; video.setLayoutParams(lp);
        View controls=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_controls_root);
        controls.setVisibility(View.VISIBLE); controls.setAlpha(1f);
        android.widget.TextView title=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_watch_title);
        title.setText("Offline control rendering check");
        GlassRuntime.apply(activity);
        View root=activity.findViewById(android.R.id.content);
        root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        root.layout(0,0,360,800);
        View play=activity.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.mobile_player_play_pause);
        assertTrue(play.getWidth()>=48); assertTrue(play.getHeight()>=48);
        assertTrue(play.getBackground() instanceof GlassDrawable);
        Bitmap bitmap=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        java.io.File directory=new java.io.File("build/clear-pdf-qa"); directory.mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"player-offline.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }
        bitmap.recycle(); activity.finish();
    }
}
