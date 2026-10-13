package io.lively.android;

import android.content.*;
import android.graphics.Point;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.*;
import java.util.*;

public final class LivelyWallpaperService extends WallpaperService {
  private final Set<LivelyEngine> engines = new HashSet<>();

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
    private boolean surfaceReady;

    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);
      engines.add(this);
      setTouchEventsEnabled(true);
      setOffsetNotificationsEnabled(
          Settings.of(LivelyWallpaperService.this).getBoolean("parallax", true));
      Settings.of(LivelyWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
    }

    private void size(SurfaceHolder holder) {
      Wallpaper w =
          new LibraryStore(LivelyWallpaperService.this)
              .find(
                  isPreview()
                      ? Settings.candidate(LivelyWallpaperService.this)
                      : Settings.active(LivelyWallpaperService.this));
      Point p = new Point();
      ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);
      int[] d =
          EnginePolicy.dimensions(
              p.x, p.y, w.kind.equals("video") ? 3840 : Settings.cap(LivelyWallpaperService.this));
      holder.setFixedSize(d[0], d[1]);
    }

    public void onSurfaceCreated(SurfaceHolder holder) {
      surfaceReady = true;
      size(holder);
    }

    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
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
      surfaceReady = false;
      if (controller != null) {
        controller.close();
        controller = null;
      }
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
      super.onDestroy();
    }

    public void onSharedPreferenceChanged(android.content.SharedPreferences p, String key) {
      if ("resolution".equals(key) && surfaceReady) size(getSurfaceHolder());
      if ("parallax".equals(key)) setOffsetNotificationsEnabled(p.getBoolean("parallax", true));
    }
  }
}
