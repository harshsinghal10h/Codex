package com.newtube.mobile.ui.glass;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.browse.MobileBrowseActivity;
import com.newtube.mobile.ui.common.ThemeMode;
import com.newtube.mobile.ui.settings.MobileSettingsActivity;
import com.newtube.mobile.ui.settings.SettingsPages;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.ScrollView;
import android.widget.CompoundButton;
import android.view.Choreographer;

/** Runs the real Activities on an Android emulator. Screenshots never substitute a web mock. */
@RunWith(AndroidJUnit4.class)
public class GlassDeviceTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void bothModesNavigateRestoreAndRender() throws Exception {
        for(int style:new int[]{GlassPreferences.STYLE_LASTWAVE,GlassPreferences.STYLE_VASO}) {
            String name=style==GlassPreferences.STYLE_VASO?"vaso":"lastwave";
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                ThemeMode.set(context,style==GlassPreferences.STYLE_VASO?ThemeMode.LIGHT:ThemeMode.DARK);
                GlassPreferences.applyPreset(context,style);
            });
            try(ActivityScenario<MobileBrowseActivity> scenario=ActivityScenario.launch(MobileBrowseActivity.class)) {
                SystemClock.sleep(2200);
                scenario.onActivity(a -> {
                    GlassNavigationView nav=a.findViewById(R.id.mobile_bottom_nav);
                    assertNotNull(nav); assertTrue(nav.isGlassEnabled()); assertTrue(nav.getMenu().size()>=2);
                    assertTrue(nav.getWidth() < a.findViewById(R.id.mobile_browse_root).getWidth());
                    assertTrue(nav.getHeight()>=48);
                    for(int i=0;i<nav.getMenu().size();i++) {
                        View tab=nav.findDockTab(nav.getMenu().getItem(i).getItemId());
                        assertNotNull(tab); assertNotNull(tab.getContentDescription());
                        assertTrue(tab.getWidth()>=48*a.getResources().getDisplayMetrics().density-1);
                    }
                });
                assertDockFollowsScrollingContent(scenario, name);
                screenshot(name+"-home");
                scenario.onActivity(a -> {
                    GlassNavigationView nav=a.findViewById(R.id.mobile_bottom_nav);
                    int you=nav.getMenu().getItem(nav.getMenu().size()-1).getItemId();
                    assertTrue(nav.findDockTab(you).performClick()); assertEquals(you,nav.getSelectedItemId());
                });
                SystemClock.sleep(500); screenshot(name+"-you");
                scenario.onActivity(a -> {
                    GlassPreferences.setStyle(a,GlassPreferences.STYLE_CLASSIC);
                    assertFalse(((GlassNavigationView)a.findViewById(R.id.mobile_bottom_nav)).isGlassEnabled());
                    GlassPreferences.applyPreset(a,style);
                    assertTrue(((GlassNavigationView)a.findViewById(R.id.mobile_bottom_nav)).isGlassEnabled());
                });
            }
            try(ActivityScenario<MobileSettingsActivity> settings=ActivityScenario.launch(MobileSettingsActivity.intent(context,SettingsPages.APPEARANCE))) {
                SystemClock.sleep(900);
                settings.onActivity(a -> {
                    assertNotNull(a.findViewById(R.id.newtube_glass_profile_picker));
                    assertNotNull(a.findViewById(R.id.settings_list));
                    int height = a.findViewById(R.id.newtube_glass_amoled_option).getHeight();
                    for (int id : new int[]{R.id.newtube_glass_amoled_option, R.id.newtube_glass_dynamic_option, R.id.newtube_glass_enabled_option}) {
                        View row = a.findViewById(id); assertNotNull(row); assertEquals(height, row.getHeight());
                        int width = row.getWidth(); CompoundButton toggle = row.findViewById(R.id.settings_row_switch);
                        boolean previous = toggle.isChecked(); assertTrue(row.performClick());
                        assertEquals(!previous, ((CompoundButton)row.findViewById(R.id.settings_row_switch)).isChecked());
                        assertEquals(width, row.getWidth()); assertEquals(height, row.getHeight());
                        assertTrue(row.performClick());
                    }
                });
                screenshot(name+"-appearance");
            }
        }
    }
    private void assertDockFollowsScrollingContent(ActivityScenario<MobileBrowseActivity> scenario, String name) throws Exception {
        ScrollView[] backdrop = {null}; int[] stripe = {0}; int[] point = new int[2];
        CountDownLatch initial = new CountDownLatch(1);
        scenario.onActivity(a -> {
            ViewGroup root = a.findViewById(R.id.mobile_browse_root);
            GlassNavigationView nav = a.findViewById(R.id.mobile_bottom_nav);
            stripe[0] = root.getHeight() + 100;
            ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true);
            View colors = new View(a) {
                final Paint paint = new Paint();
                @Override protected void onDraw(Canvas c) {
                    paint.setColor(Color.MAGENTA); c.drawRect(0, 0, getWidth(), stripe[0], paint);
                    paint.setColor(Color.GREEN); c.drawRect(0, stripe[0], getWidth(), stripe[0]*2, paint);
                }
            };
            scroll.addView(colors, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stripe[0]*2));
            root.addView(scroll, root.indexOfChild(nav), new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            backdrop[0] = scroll;
            int[] location = new int[2]; nav.getLocationOnScreen(location);
            point[0] = location[0] + nav.getWidth()/2;
            // Below the label track and selection lens, away from the glass rim.
            point[1] = location[1] + nav.getHeight() - Math.round(3 * a.getResources().getDisplayMetrics().density);
            nav.getViewTreeObserver().registerFrameCommitCallback(initial::countDown);
        });
        assertTrue("Initial backdrop frame was not committed", initial.await(5, TimeUnit.SECONDS));
        int before = pixel(point);
        assertTrue("Glass must show the magenta backdrop without its own labels", Color.red(before) > Color.green(before)+10);
        java.util.List<Long> frames = new java.util.ArrayList<>();
        android.view.Window.OnFrameMetricsAvailableListener metrics = (window, frame, dropped) -> frames.add(frame.getMetric(android.view.FrameMetrics.TOTAL_DURATION));
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(a -> {
            a.getWindow().addOnFrameMetricsAvailableListener(metrics, new android.os.Handler(android.os.Looper.getMainLooper()));
            long start = SystemClock.uptimeMillis();
            Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                @Override public void doFrame(long time) {
                    float progress = Math.min(1, (SystemClock.uptimeMillis()-start)/1200f);
                    backdrop[0].scrollTo(0, Math.round(progress * stripe[0]));
                    if (progress < 1) Choreographer.getInstance().postFrameCallback(this);
                    else a.findViewById(R.id.mobile_bottom_nav).getViewTreeObserver().registerFrameCommitCallback(done::countDown);
                }
            });
        });
        assertTrue("Scrolling frame did not finish", done.await(8, TimeUnit.SECONDS));
        int after = pixel(point);
        screenshot(name+"-scroll-glass");
        assertTrue("The navbar must follow the current committed scroll frame: before="+Integer.toHexString(before)+" after="+Integer.toHexString(after), Color.green(after) > Color.red(after)+10);
        assertTrue("The backdrop must change together with content", Color.green(after)-Color.red(after) > Color.green(before)-Color.red(before)+24);
        scenario.onActivity(a -> {
            a.getWindow().removeOnFrameMetricsAvailableListener(metrics);
            ((ViewGroup)backdrop[0].getParent()).removeView(backdrop[0]);
        });
        assertTrue("Scrolling must produce frame metrics", frames.size() > 5);
        java.util.Collections.sort(frames);
        File folder = new File(context.getExternalFilesDir(null), "glass-screenshots"); assertTrue(folder.exists() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, name+"-scroll-metrics.txt"))) {
            String report = "Android emulator, controlled 1.2 s scroll\nframes="+frames.size()+"\np50_ms="+(frames.get(frames.size()/2)/1000000f)+"\np95_ms="+(frames.get((frames.size()-1)*95/100)/1000000f)+"\n";
            out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private int pixel(int[] point) {
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(image);
        int color = image.getPixel(point[0], point[1]); image.recycle(); return color;
    }
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File folder=new File(context.getExternalFilesDir(null),"glass-screenshots"); assertTrue(folder.exists()||folder.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out)); }
        image.recycle();
    }
}
