package io.lively.android;

import android.content.*;
import android.graphics.Point;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.*;
import java.util.*;

public final class LivelyWallpaperService extends WallpaperService {
  private final Set<LivelyEngine> engines = new HashSet<>();
  private static final Set<LivelyEngine> installedEngines = new HashSet<>();

  static boolean hasInstalledEngine() {
    return !installedEngines.isEmpty();
  }

  static LivelyEngine installedEngine() {
    return installedEngines.isEmpty() ? null : installedEngines.iterator().next();
  }

  public Engine onCreateEngine() {
    return new LivelyEngine();
  }

  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level >= TRIM_MEMORY_RUNNING_LOW)
      for (LivelyEngine e : new ArrayList<>(engines)) if (e.controller != null) e.controller.trim();
  }

  final class LivelyEngine extends Engine
      implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    RendererController controller;
    private boolean visible;
    private long lastTouch;

    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);
      engines.add(this);
      if (!isPreview()) {
        installedEngines.add(this);
        // The framework only creates this non-preview engine after installation.
        // Commit before its first frame, even if the picker killed our activity.
        WallpaperSelection.finish(LivelyWallpaperService.this, false);
      }
      setTouchEventsEnabled(true);
      setOffsetNotificationsEnabled(
          Settings.of(LivelyWallpaperService.this).getBoolean("parallax", true));
      Settings.of(LivelyWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
    }

    public void onSurfaceCreated(SurfaceHolder holder) {
      super.onSurfaceCreated(holder);
      // Wallpaper surfaces are sized by Android. setFixedSize() throws for normal apps.
      // Scenes use an internal capped render target instead of a hidden platform API.
    }

    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      super.onSurfaceChanged(holder, format, width, height);
      if (controller != null) controller.close();
      if (Build.VERSION.SDK_INT >= 30)
        try {
          holder
              .getSurface()
              .setFrameRate(
                  Settings.fps(LivelyWallpaperService.this),
                  Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (Exception ignored) {
        }
      Wallpaper fixed =
          isPreview()
              ? new LibraryStore(LivelyWallpaperService.this)
                  .find(Settings.candidate(LivelyWallpaperService.this))
              : null;
      controller =
          new RendererController(
              LivelyWallpaperService.this, fixed, holder.getSurface(), width, height, isPreview());
      controller.visible(visible);
    }

    public void onVisibilityChanged(boolean value) {
      visible = value;
      if (controller != null) controller.visible(value);
    }

    public void onSurfaceDestroyed(SurfaceHolder h) {
      if (controller != null) {
        controller.close();
        controller = null;
      }
      super.onSurfaceDestroyed(h);
    }

    public void onTouchEvent(MotionEvent event) {
      if (controller != null) {
        Point p = new Point();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);
        controller.touch(
            event.getX() / Math.max(1, p.x),
            event.getY() / Math.max(1, p.y),
            event.getActionMasked());
      }
      if (event.getActionMasked() == MotionEvent.ACTION_UP
          && !isPreview()
          && Settings.of(LivelyWallpaperService.this).getBoolean("double_tap", false)) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTouch < 300) {
          ArrayList<Wallpaper> ws = new LibraryStore(LivelyWallpaperService.this).all();
          int at = 0;
          for (int i = 0; i < ws.size(); i++)
            if (ws.get(i).id.equals(Settings.active(LivelyWallpaperService.this))) at = i;
          Settings.of(LivelyWallpaperService.this)
              .edit()
              .putString("active", ws.get((at + 1) % ws.size()).id)
              .apply();
          lastTouch = 0;
        } else lastTouch = now;
      }
    }

    public void onOffsetsChanged(float x, float y, float xs, float ys, int xp, int yp) {
      if (controller != null
          && Settings.of(LivelyWallpaperService.this).getBoolean("parallax", true))
        controller.touch(x, .5f, MotionEvent.ACTION_MOVE);
    }

    public void onDestroy() {
      if (controller != null) controller.close();
      Settings.of(LivelyWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
      engines.remove(this);
      installedEngines.remove(this);
      super.onDestroy();
    }

    public void onSharedPreferenceChanged(android.content.SharedPreferences p, String key) {
      if ("parallax".equals(key)) setOffsetNotificationsEnabled(p.getBoolean("parallax", true));
    }
  }
}
