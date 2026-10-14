package io.lively.android;

import android.content.*;
import android.os.*;
import android.view.*;

/** All lifecycle decisions run on the main looper. The GL loop owns its own thread. */
final class RendererController
    implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {
  private final Context context;
  private final Surface surface;
  private final Wallpaper fixedWallpaper;
  private final int width, height;
  private final boolean preview;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final PowerManager power;
  private Renderer renderer;
  private boolean visible, closed, charging;
  private int battery = 100, thermal;
  private String currentId = "";
  private boolean stagingStill;
  private long stillStarted;
  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
            battery =
                Math.max(0, i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100))
                    * 100
                    / Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
            charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
          }
          update();
        }
      };
  private PowerManager.OnThermalStatusChangedListener thermalListener;

  RendererController(Context c, Wallpaper w, Surface s, int width, int height, boolean preview) {
    context = c;
    fixedWallpaper = w;
    surface = s;
    this.width = width;
    this.height = height;
    this.preview = preview;
    power = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
    if (Build.VERSION.SDK_INT >= 33)
      c.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    else c.registerReceiver(receiver, filter);
    Settings.of(c).registerOnSharedPreferenceChangeListener(this);
    if (Build.VERSION.SDK_INT >= 29) {
      thermal = power.getCurrentThermalStatus();
      thermalListener =
          status -> {
            thermal = status;
            handler.post(this::update);
          };
      power.addThermalStatusListener(thermalListener);
    }
  }

  private final Runnable discard =
      new Runnable() {
        public void run() {
          if (renderer != null) {
            renderer.release();
            renderer = null;
          }
          stagingStill = false;
          if (!preview) Metrics.state = "Paused • resources released";
        }
      };
  private final Runnable rotation =
      new Runnable() {
        public void run() {
          if (closed || !visible) return;
          android.content.SharedPreferences p = Settings.of(context);
          if (p.getBoolean("rotation", false) && !preview) {
            long interval = p.getInt("rotation_minutes", 60) * 60000L;
            long last = p.getLong("last_rotation", System.currentTimeMillis());
            if (System.currentTimeMillis() - last >= interval) {
              java.util.ArrayList<Wallpaper> ws = new LibraryStore(context).all();
              java.util.ArrayList<Wallpaper> fav = new java.util.ArrayList<>();
              for (Wallpaper w : ws) if (p.getBoolean("fav." + w.id, false)) fav.add(w);
              if (fav.size() < 2) fav = ws;
              int at = 0;
              for (int i = 0; i < fav.size(); i++)
                if (fav.get(i).id.equals(Settings.active(context))) at = i;
              p.edit()
                  .putString("active", fav.get((at + 1) % fav.size()).id)
                  .putLong("last_rotation", System.currentTimeMillis())
                  .apply();
            }
          }
          handler.postDelayed(this, 30000);
        }
      };

  void visible(boolean value) {
    visible = value;
    handler.removeCallbacks(rotation);
    if (value && !preview) {
      if (!Settings.of(context).contains("last_rotation"))
        Settings.of(context).edit().putLong("last_rotation", System.currentTimeMillis()).apply();
      handler.post(rotation);
    }
    update();
  }

  private void update() {
    if (closed) return;
    android.content.SharedPreferences p = Settings.of(context);
    boolean run =
        preview
            ? visible
            : EnginePolicy.shouldRun(
                visible,
                power.isInteractive(),
                p.getBoolean("paused", false),
                power.isPowerSaveMode(),
                p.getBoolean("pause_saver", true),
                charging,
                p.getBoolean("pause_battery", false),
                battery,
                p.getInt("low_battery", 15),
                thermal >= PowerManager.THERMAL_STATUS_SEVERE,
                p.getBoolean("pause_thermal", true));
    handler.removeCallbacks(discard);
    Wallpaper w =
        fixedWallpaper == null
            ? new LibraryStore(context).find(Settings.active(context))
            : fixedWallpaper;
    if (renderer != null && (!currentId.equals(w.id) || renderer.failed())) {
      renderer.release();
      renderer = null;
      stagingStill = false;
      handler.removeCallbacks(settleStill);
    }
    if (!run) {
      if (!visible && (renderer instanceof VideoRenderer || renderer instanceof WebRenderer)) {
        renderer.release();
        renderer = null;
      }
      // Decode a first frame for a newly attached visible wallpaper while paused.
      // Keep that frame, but never start rendering while hidden or screen-off.
      if (visible && power.isInteractive() && (renderer == null || !renderer.ready())) {
        ensureRenderer(w);
        if (!stagingStill && !renderer.failed()) {
          stagingStill = true;
          stillStarted = SystemClock.uptimeMillis();
          renderer.configure();
          renderer.start();
          handler.postDelayed(settleStill, 100);
        }
      } else {
        stagingStill = false;
        handler.removeCallbacks(settleStill);
        if (renderer != null) renderer.pause();
      }
      if (!preview)
        Metrics.state = !visible ? "Paused • wallpaper hidden" : "Paused • power or playback rule";
      if (!visible || !power.isInteractive()) handler.postDelayed(discard, 15000);
      return;
    }
    stagingStill = false;
    handler.removeCallbacks(settleStill);
    ensureRenderer(w);
    renderer.configure();
    renderer.start();
    if (!preview) Metrics.state = "Playing • " + w.typeLabel();
  }

  private void ensureRenderer(Wallpaper w) {
    if (renderer == null) {
      currentId = w.id;
      if (w.builtin()) renderer = new ShaderRenderer(context, w, surface, width, height);
      else if (w.kind.equals("video")) renderer = new VideoRenderer(context, w, surface);
      else if (w.kind.equals("web")) {
        int[] d = EnginePolicy.dimensions(width, height, Settings.cap(context));
        renderer = new WebRenderer(context, w, surface, d[0], d[1]);
      } else renderer = new ImageRenderer(context, w, surface, width, height);
    }
  }

  private final Runnable settleStill =
      new Runnable() {
        public void run() {
          if (closed || !stagingStill || renderer == null) return;
          long elapsed = SystemClock.uptimeMillis() - stillStarted;
          if ((renderer.ready() && elapsed >= 300) || renderer.failed() || elapsed >= 10000) {
            renderer.pause();
            stagingStill = false;
          } else handler.postDelayed(this, 100);
        }
      };

  String status() {
    if (renderer != null && !renderer.error().isEmpty()) return renderer.error();
    if (!visible) return "Preview paused";
    return renderer != null && renderer.ready() ? "Live preview ready" : "Loading preview…";
  }

  void touch(float x, float y, int action) {
    if (renderer != null) {
      if (renderer instanceof WebRenderer) ((WebRenderer) renderer).touchEvent(x, y, action);
      else renderer.touch(x, y);
    }
  }

  void trim() {
    handler.removeCallbacks(discard);
    handler.removeCallbacks(settleStill);
    stagingStill = false;
    if (renderer != null) {
      renderer.release();
      renderer = null;
    }
    if (visible) update();
  }

  public void onSharedPreferenceChanged(android.content.SharedPreferences p, String key) {
    if (key == null
        || key.equals("last_error")
        || key.equals("candidate")
        || key.equals("last_rotation")
        || key.startsWith("fav.")
        || key.equals("theme")
        || key.equals("glass")
        || key.equals("reduce_motion")) return;
    handler.post(
        () -> {
          if (renderer instanceof WebRenderer && key.equals("web_network")) {
            renderer.release();
            renderer = null;
          }
          if ("resolution".equals(key)
              && renderer != null
              && (renderer instanceof ImageRenderer || renderer instanceof WebRenderer)) {
            renderer.release();
            renderer = null;
          }
          update();
        });
  }

  void close() {
    if (closed) return;
    closed = true;
    handler.removeCallbacksAndMessages(null);
    try {
      context.unregisterReceiver(receiver);
    } catch (Exception ignored) {
    }
    Settings.of(context).unregisterOnSharedPreferenceChangeListener(this);
    if (Build.VERSION.SDK_INT >= 29 && thermalListener != null)
      power.removeThermalStatusListener(thermalListener);
    if (renderer != null) {
      renderer.release();
      renderer = null;
    }
  }
}
