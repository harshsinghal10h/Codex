package dev.harsh.protonbridge;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.view.Menu;
import android.view.MenuItem;
import java.util.Collections;

/** Adds the internal controller to Brave's native Settings menu. */
public final class BraveVpnMenuHook {
    private static final int MENU_ID=0x50665257;
    private BraveVpnMenuHook() { }

    public static void addMenu(Activity activity, Menu menu) {
        removeOldShortcut(activity);
        if (menu.findItem(MENU_ID)!=null) return;
        MenuItem item=menu.add(Menu.NONE,MENU_ID,Menu.NONE,"Proton Free VPN");
        item.setIcon(android.R.drawable.ic_lock_lock);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setOnMenuItemClickListener(selected -> {
            activity.startActivity(new Intent(activity,ProtonVpnActivity.class));
            return true;
        });
    }

    public static void removeOldShortcut(Activity activity) {
        try {
            ShortcutManager manager=activity.getSystemService(ShortcutManager.class);
            if (manager!=null) {
                manager.disableShortcuts(Collections.singletonList("personal-proton-free"));
                manager.removeDynamicShortcuts(Collections.singletonList("personal-proton-free"));
            }
        } catch (RuntimeException ignored) { }
    }
}
