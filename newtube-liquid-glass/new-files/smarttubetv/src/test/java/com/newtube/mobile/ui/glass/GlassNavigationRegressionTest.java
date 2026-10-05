package com.newtube.mobile.ui.glass;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.util.concurrent.atomic.AtomicInteger;

/** Contracts broken by the original generic glass overlay: navigation, recycling and restoration. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GlassNavigationRegressionTest {
    private Context context;
    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(R.style.Theme_NewTube);
        context.getSharedPreferences("newtube_liquid_glass", Context.MODE_PRIVATE).edit().clear().commit();
    }
    private GlassNavigationView dock() {
        GlassNavigationView nav = new GlassNavigationView(context, null);
        nav.getMenu().add(0,801,0,"Home").setIcon(R.drawable.ic_nav_home);
        nav.getMenu().add(0,802,1,"Subscriptions").setIcon(R.drawable.ic_mobile_account);
        nav.getMenu().add(0,803,2,"History").setIcon(R.drawable.ic_mobile_account);
        nav.getMenu().add(0,804,3,"You").setIcon(R.drawable.ic_mobile_account);
        GlassPreferences.Snapshot s=GlassPreferences.snapshot(context);
        nav.configure(s,new GlassPalette(context,s,false));
        nav.measure(View.MeasureSpec.makeMeasureSpec(340,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(76,View.MeasureSpec.AT_MOST));
        nav.layout(0,0,nav.getMeasuredWidth(),nav.getMeasuredHeight());
        return nav;
    }
    @Test public void customTabsPreserveSelectionAndReselectionCallbacks() {
        GlassNavigationView nav=dock(); AtomicInteger chosen=new AtomicInteger(), reselected=new AtomicInteger();
        nav.setOnItemSelectedListener(item -> {chosen.set(item.getItemId());return true;});
        nav.setOnItemReselectedListener(item -> reselected.incrementAndGet());
        View tab=nav.findDockTab(803); assertNotNull(tab); assertTrue(tab.performClick());
        assertEquals(803,chosen.get()); assertEquals(803,nav.getSelectedItemId());
        nav.findDockTab(803).performClick(); assertEquals(1,reselected.get());
        assertEquals("History",nav.findDockTab(803).getContentDescription().toString());
    }
    @Test public void selectingTabsKeepsTheSameViewsAndGeometry() {
        for (int style : new int[]{GlassPreferences.STYLE_LASTWAVE, GlassPreferences.STYLE_VASO}) {
            GlassPreferences.applyPreset(context, style);
            GlassNavigationView nav = dock();
            View[] tabs = new View[]{nav.findDockTab(801), nav.findDockTab(802), nav.findDockTab(803), nav.findDockTab(804)};
            int width = tabs[0].getWidth();
            for (int id : new int[]{802, 804, 801, 803}) {
                nav.setSelectedItemId(id);
                for (int i = 0; i < tabs.length; i++) {
                    assertSame("Selection must update the existing tab", tabs[i], nav.findDockTab(801+i));
                    assertEquals("Tab width must stay constant", width, tabs[i].getWidth());
                    assertFalse("Selection must not request a new layout", nav.isLayoutRequested());
                }
            }
        }
    }
    @Test public void disablingGlassPreservesProfileAndCustomValues() {
        GlassPreferences.applyPreset(context, GlassPreferences.STYLE_VASO);
        GlassPreferences.setBlur(context, 41); GlassPreferences.setRadius(context, 37);
        GlassPreferences.setGlassEnabled(context, false);
        assertFalse(GlassPreferences.glassEnabled(context));
        assertEquals(GlassPreferences.STYLE_CLASSIC, GlassPreferences.snapshot(context).style);
        assertEquals(GlassPreferences.STYLE_VASO, GlassPreferences.style(context));
        GlassPreferences.setGlassEnabled(context, true);
        assertEquals(GlassPreferences.STYLE_VASO, GlassPreferences.snapshot(context).style);
        assertEquals(41, GlassPreferences.blur(context)); assertEquals(37, GlassPreferences.radius(context));
        GlassPreferences.applyPreset(context, GlassPreferences.STYLE_CLASSIC);
        GlassPreferences.setGlassEnabled(context, true);
        assertEquals(GlassPreferences.STYLE_VASO, GlassPreferences.style(context));
    }
    @Test public void appearanceRowsAndSwitchesKeepFixedBounds() {
        for (float fontScale : new float[]{1f, 1.8f}) {
            Configuration c = new Configuration(context.getResources().getConfiguration()); c.fontScale = fontScale;
            context.getResources().updateConfiguration(c, context.getResources().getDisplayMetrics());
            LinearLayout list = new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL);
            for (String label : new String[]{"AMOLED Mode", "Dynamic Color", "Liquid Glass"}) {
                GlassAppearanceToggleView row = new GlassAppearanceToggleView(context);
                ((android.widget.TextView) row.findViewById(R.id.settings_row_title)).setText(label);
                ((android.widget.TextView) row.findViewById(R.id.settings_row_summary)).setText("An explanation that is longer than the available width");
                list.addView(row, new LinearLayout.LayoutParams(320, row.getLayoutParams().height));
            }
            list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            list.layout(0, 0, 320, list.getMeasuredHeight());
            int height = list.getChildAt(0).getHeight();
            for (int style : new int[]{GlassPreferences.STYLE_VASO, GlassPreferences.STYLE_LASTWAVE, GlassPreferences.STYLE_CLASSIC}) {
                GlassPreferences.applyPreset(context, style);
                for (int i = 0; i < list.getChildCount(); i++) {
                    GlassAppearanceToggleView row = (GlassAppearanceToggleView) list.getChildAt(i); row.render();
                    android.widget.CompoundButton control = row.findViewById(R.id.settings_row_switch);
                    int controlWidth = control.getWidth(), controlHeight = control.getHeight();
                    control.setChecked(!control.isChecked());
                    assertEquals(height, row.getHeight()); assertEquals(320, row.getWidth());
                    assertEquals(controlWidth, control.getWidth()); assertEquals(controlHeight, control.getHeight());
                    assertEquals(1, ((android.widget.TextView) row.findViewById(R.id.settings_row_summary)).getMaxLines());
                }
            }
        }
    }
    @Test public void rejectedSelectionKeepsTheCurrentTab() {
        GlassNavigationView nav=dock(); nav.setSelectedItemId(801);
        nav.setOnItemSelectedListener(item -> false); nav.findDockTab(802).performClick();
        assertEquals(801,nav.getSelectedItemId());
    }
    @Test public void sectionLongPressSurvivesTabRebuilds() {
        GlassNavigationView nav=dock(); AtomicInteger calls=new AtomicInteger();
        nav.setNavigationLongClickListener(801,v -> {calls.incrementAndGet();return true;});
        nav.setSelectedItemId(803); nav.setSelectedItemId(802);
        assertTrue(nav.findDockTab(801).performLongClick()); assertEquals(1,calls.get());
    }
    @Test public void fiveTabsAtLargeFontKeepTouchTargetsAndLabelsInBounds() {
        Configuration c=new Configuration(context.getResources().getConfiguration()); c.fontScale=1.8f;
        context.getResources().updateConfiguration(c,context.getResources().getDisplayMetrics());
        GlassNavigationView nav=dock(); nav.getMenu().add(0,805,4,"Downloads").setIcon(R.drawable.ic_mobile_account);
        GlassPreferences.Snapshot s=GlassPreferences.snapshot(context); nav.configure(s,new GlassPalette(context,s,false));
        nav.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(90,View.MeasureSpec.AT_MOST));
        nav.layout(0,0,320,nav.getMeasuredHeight());
        for(int id:new int[]{801,802,803,804,805}) {
            View tab=nav.findDockTab(id); assertNotNull(tab); assertTrue(tab.getWidth()>=48);
            assertTrue(tab.getHeight()>=48); assertTrue(tab.getRight()<=320); assertTrue(tab.getLeft()>=0);
            assertNotNull(tab.getContentDescription());
        }
    }
    @Test public void classicAndGlassTogglesRestoreBrowseConstraintsAndCardGeometry() {
        Activity a=Robolectric.buildActivity(Activity.class).setup().get(); a.setTheme(R.style.Theme_NewTube);
        a.setContentView(R.layout.activity_mobile_browse);
        View content=a.findViewById(android.R.id.content);
        View swipe=a.findViewById(R.id.mobile_content_swipe);
        int original=((ConstraintLayout.LayoutParams)swipe.getLayoutParams()).bottomToTop;
        GlassRuntime.apply(a); assertEquals(ConstraintLayout.LayoutParams.UNSET,((ConstraintLayout.LayoutParams)swipe.getLayoutParams()).bottomToTop);
        for(int i=0;i<3;i++) {
            GlassPreferences.setStyle(context,GlassPreferences.STYLE_CLASSIC); GlassRuntime.apply(a);
            assertEquals(original,((ConstraintLayout.LayoutParams)swipe.getLayoutParams()).bottomToTop);
            assertFalse(((GlassNavigationView)a.findViewById(R.id.mobile_bottom_nav)).isGlassEnabled());
            GlassPreferences.setStyle(context,GlassPreferences.STYLE_VASO); GlassRuntime.apply(a);
            assertEquals(1, countId(content,R.id.newtube_glass_header));
        }
    }
    @Test public void readableTextContrastAcrossBothPalettes() {
        for(int style:new int[]{GlassPreferences.STYLE_VASO,GlassPreferences.STYLE_LASTWAVE}) {
            GlassPreferences.applyPreset(context,style);
            for(boolean dark:new boolean[]{false,true}) {
                GlassPalette p=new GlassPalette(context,GlassPreferences.snapshot(context),dark);
                assertTrue(ColorUtils.calculateContrast(p.ink,p.canvas)>=7);
                assertTrue(ColorUtils.calculateContrast(p.muted,p.surface)>=4.5);
                assertTrue(ColorUtils.calculateContrast(p.onSelected,p.selected)>=4.5);
            }
        }
    }
    @Test public void restylingPopulatedFeedPreservesRecyclerViewHolderMetadata() {
        Activity a=Robolectric.buildActivity(Activity.class).setup().get(); a.setTheme(R.style.Theme_NewTube);
        RecyclerView feed=new RecyclerView(a); feed.setId(R.id.mobile_content_grid);
        feed.setLayoutManager(new LinearLayoutManager(a));
        feed.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) {
                MaterialCardView card=new MaterialCardView(a); card.setId(R.id.video_card_root);
                RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,96);
                lp.bottomMargin=3; card.setLayoutParams(lp);
                return new RecyclerView.ViewHolder(card) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position) {}
            @Override public int getItemCount() { return 4; }
        });
        a.setContentView(feed); layoutFeed(feed);
        assertTrue(feed.getChildCount()>0);
        View card=feed.getChildAt(0); RecyclerView.ViewHolder holder=feed.getChildViewHolder(card);
        for(int style:new int[]{GlassPreferences.STYLE_VASO,GlassPreferences.STYLE_LASTWAVE,GlassPreferences.STYLE_CLASSIC,GlassPreferences.STYLE_VASO}) {
            GlassPreferences.setStyle(context,style); GlassRuntime.apply(a);
            assertSame(holder,feed.getChildViewHolder(card));
            if(style==GlassPreferences.STYLE_CLASSIC) assertEquals(3,((RecyclerView.LayoutParams)card.getLayoutParams()).bottomMargin);
            layoutFeed(feed);
            for(int i=0;i<feed.getChildCount();i++) assertNotNull(feed.getChildViewHolder(feed.getChildAt(i)));
        }
    }
    private static void layoutFeed(RecyclerView feed) {
        feed.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));
        feed.layout(0,0,360,600);
    }
    private static int countId(View v,int id) {
        int n=v.getId()==id?1:0;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) n+=countId(((ViewGroup)v).getChildAt(i),id);
        return n;
    }
}
