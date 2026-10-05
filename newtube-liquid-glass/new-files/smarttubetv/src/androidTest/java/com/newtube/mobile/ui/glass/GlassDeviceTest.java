package com.newtube.mobile.ui.glass;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
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
                });
                screenshot(name+"-appearance");
            }
        }
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
