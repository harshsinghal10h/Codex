package io.lively.android;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;

/** Selection is committed only after the system has installed our component. */
final class WallpaperSelection {
  static boolean installed(Context c) {
    try {
      WallpaperInfo info = WallpaperManager.getInstance(c).getWallpaperInfo();
      if (info != null)
        return new ComponentName(c, LivelyWallpaperService.class).equals(info.getComponent());
    } catch (RuntimeException ignored) {
      // Android 13 and some OEMs restrict the wallpaper-info query. Our own live
      // engine is an equivalent signal and needs no broad package/storage permission.
    }
    return LivelyWallpaperService.hasInstalledEngine();
  }

  static void begin(Context c, String id) {
    Settings.of(c)
        .edit()
        .putString("candidate", id)
        .putString("pending_apply", id)
        .putBoolean("pending_direct", true)
        .commit();
  }

  static void select(Context c, String id) {
    Settings.of(c)
        .edit()
        .putString("active", id)
        .putString("candidate", id)
        .putBoolean("paused", false)
        .remove("pending_apply")
        .remove("pending_direct")
        .remove("last_error")
        .commit();
  }

  static boolean finish(Context c, boolean confirmed) {
    String id = Settings.of(c).getString("pending_apply", null);
    boolean applied =
        id != null
            && ((confirmed && Settings.of(c).getBoolean("pending_direct", true)) || installed(c));
    if (applied) select(c, id);
    else cancel(c);
    return applied;
  }

  static void cancel(Context c) {
    Settings.of(c)
        .edit()
        .remove("pending_apply")
        .remove("pending_direct")
        .putString("candidate", Settings.active(c))
        .commit();
  }
}
