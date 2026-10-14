package io.lively.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.AnimatedImageDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** System-picker, real WallpaperService and animated-art tests. Never packaged in the app. */
public final class WallpaperFlowTests extends Instrumentation {
  private int assertions;
  private final StringBuilder report = new StringBuilder();
  private volatile MainActivity activity;
  private Context c;

  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  public void callActivityOnResume(Activity resumed) {
    super.callActivityOnResume(resumed);
    if (resumed instanceof MainActivity) activity = (MainActivity) resumed;
  }

  private void check(boolean pass, String label) {
    if (!pass) throw new AssertionError(label);
    assertions++;
    report.append("PASS ").append(label).append('\n');
  }

  private void main(Runnable r) {
    runOnMainSync(r);
  }

  private void waitMs(long time) {
    SystemClock.sleep(time);
  }

  private Object field(Object target, String name) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(target);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void call(String name, Class<?>[] types, Object... args) {
    main(
        () -> {
          try {
            Method m = MainActivity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            m.invoke(activity, args);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private void until(BooleanSupplier condition, long timeout, String label) {
    long end = SystemClock.uptimeMillis() + timeout;
    while (SystemClock.uptimeMillis() < end) {
      if (condition.getAsBoolean()) {
        check(true, label);
        return;
      }
      waitMs(100);
    }
    check(false, label);
  }

  private boolean systemPicker() {
    AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
    if (root == null || root.getPackageName() == null) return false;
    String pkg = root.getPackageName().toString();
    return !pkg.equals(c.getPackageName())
        && (pkg.contains("wallpaper") || pkg.contains("livepicker"));
  }

  private boolean clickText(String label) {
    AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
    if (root == null) return false;
    for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
      if (node.getText() != null && !node.getText().toString().equalsIgnoreCase(label)) continue;
      AccessibilityNodeInfo action = node;
      while (action != null && !action.isClickable()) action = action.getParent();
      if (action != null && action.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
    }
    return false;
  }

  private void key(int code) {
    long now = SystemClock.uptimeMillis();
    getUiAutomation().injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true);
    getUiAutomation().injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0), true);
  }

  private void bringApp() {
    try (ParcelFileDescriptor command =
            getUiAutomation().executeShellCommand("am start -n io.lively.android/.MainActivity");
        InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(command)) {
      byte[] data = new byte[1024];
      while (in.read(data) != -1) {}
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    until(
        () ->
            activity != null
                && !activity.isDestroyed()
                && activity.getWindow().getDecorView().hasWindowFocus(),
        10000,
        "App returns to foreground");
  }

  private RendererController installedController() {
    LivelyWallpaperService.LivelyEngine engine = LivelyWallpaperService.installedEngine();
    return engine == null ? null : engine.controller;
  }

  private Renderer installedRenderer() {
    RendererController controller = installedController();
    return controller == null ? null : (Renderer) field(controller, "renderer");
  }

  private long sceneFrames() {
    Renderer renderer = installedRenderer();
    return renderer instanceof ShaderRenderer ? (Long) field(renderer, "renderedFrames") : -1;
  }

  private void shot(String name) throws IOException {
    Bitmap image = getUiAutomation().takeScreenshot();
    if (image != null) {
      File dir = new File(c.getExternalFilesDir(null), "wallpaper-flow");
      dir.mkdirs();
      try (OutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
        image.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
      image.recycle();
    }
  }

  private void dismissDetail() {
    main(
        () -> {
          AlertDialog dialog = (AlertDialog) field(activity, "detailDialog");
          if (dialog != null) dialog.dismiss();
        });
  }

  private Wallpaper imported(String fixture) throws Exception {
    return new LibraryStore(c)
        .importUri(Uri.parse("content://io.lively.android.tests.fixtures/" + fixture));
  }

  private void set(Wallpaper w) {
    call("applyLive", new Class[] {Wallpaper.class}, w);
  }

  private <T extends View> T typed(View view, Class<T> type) {
    if (type.isInstance(view)) return type.cast(view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        T match = typed(group.getChildAt(i), type);
        if (match != null) return match;
      }
    }
    return null;
  }

  public void onStart() {
    Bundle result = new Bundle();
    try {
      c = getTargetContext();
      main(
          () ->
              Settings.of(c)
                  .edit()
                  .clear()
                  .putString("theme", "dark")
                  .putInt("resolution", 720)
                  .putInt("fps", 15)
                  .putInt("low_battery", 0)
                  .putBoolean("pause_saver", false)
                  .putBoolean("pause_thermal", false)
                  .commit());
      activity =
          (MainActivity)
              startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitMs(1000);
      MainActivity initial = activity;
      main(initial::recreate);
      until(
          () ->
              activity != initial
                  && !activity.isDestroyed()
                  && activity.getWindow().getDecorView().hasWindowFocus(),
          10000,
          "Activity recreation preserves a drawable glass surface");
      check(!WallpaperSelection.installed(c), "Fresh emulator starts without Lively installed");
      String original = Settings.active(c);
      set(Wallpaper.BUILTINS[3]);
      until(this::systemPicker, 10000, "System live-wallpaper picker opens");
      waitMs(1800);
      check(
          Settings.of(c).getString("last_error", "").isEmpty(),
          "Real wallpaper preview attaches without surface-size crash");
      shot("system-preview");
      key(KeyEvent.KEYCODE_BACK);
      until(
          () -> !Settings.of(c).contains("pending_apply"),
          7000,
          "System cancellation clears pending selection");
      check(original.equals(Settings.active(c)), "Cancel preserves the active wallpaper");
      check(original.equals(Settings.candidate(c)), "Cancel restores the picker candidate");
      check(!WallpaperSelection.installed(c), "Cancel does not install a wallpaper");

      Wallpaper chosen = Wallpaper.BUILTINS[1];
      set(chosen);
      until(this::systemPicker, 10000, "System picker reopens after cancellation");
      until(
          () -> systemPicker() && (clickText("Set wallpaper") || clickText("Apply")),
          10000,
          "System Set wallpaper button is actionable");
      waitMs(900);
      clickText("Home screen");
      until(
          () -> WallpaperSelection.installed(c),
          12000,
          "Android installs the actual Lively service");
      until(
          () -> chosen.id.equals(Settings.active(c)) && !Settings.of(c).contains("pending_apply"),
          10000,
          "Installed engine commits the requested scene");
      key(KeyEvent.KEYCODE_HOME);
      until(() -> sceneFrames() > 3, 10000, "Chosen scene renders on the actual home screen");
      long before = sceneFrames();
      waitMs(600);
      check(sceneFrames() > before, "Home-screen scene continues animating");
      ShaderRenderer shader = (ShaderRenderer) installedRenderer();
      check(
          (Integer) field(shader, "targetHeight") <= 720
              && (Integer) field(shader, "targetWidth") <= 720,
          "Resolution cap uses a bounded internal GPU render target");
      check(
          (Integer) field(shader, "outputHeight") > 720,
          "Capped scene fills the larger system surface");
      shot("home-ribbons");

      bringApp();
      set(Wallpaper.BUILTINS[4]);
      check(
          "aurora".equals(Settings.active(c)),
          "Installed wallpaper switches without a second system picker");
      key(KeyEvent.KEYCODE_HOME);
      until(
          () ->
              installedController() != null
                  && "aurora".equals(field(installedController(), "currentId"))
                  && sceneFrames() > 3,
          10000,
          "Selected replacement renders on the home screen");

      bringApp();
      call("showDetail", new Class[] {Wallpaper.class}, Wallpaper.BUILTINS[0]);
      waitMs(1500);
      RendererController preview = (RendererController) field(activity, "preview");
      check(
          preview != null && ((Renderer) field(preview, "renderer")).ready(),
          "App preview renders alongside the installed engine");
      dismissDetail();
      key(KeyEvent.KEYCODE_HOME);
      until(
          () -> sceneFrames() > 3,
          8000,
          "Closing app preview preserves the home-screen EGL context");
      before = sceneFrames();
      waitMs(500);
      check(
          sceneFrames() > before,
          "Installed renderer remains alive after preview context is released");

      main(
          () ->
              Settings.of(c)
                  .edit()
                  .putBoolean("paused", true)
                  .putString("active", "gradient")
                  .commit());
      until(
          () ->
              installedRenderer() != null
                  && installedRenderer().ready()
                  && "gradient".equals(field(installedController(), "currentId")),
          10000,
          "Paused newly selected wallpaper displays a decoded first frame");
      waitMs(800);
      before = sceneFrames();
      waitMs(600);
      check(
          sceneFrames() == before, "Paused wallpaper keeps its first frame without a render loop");
      shot("paused-first-frame");
      main(() -> Settings.of(c).edit().putBoolean("paused", false).commit());
      final long stopped = before;
      until(() -> sceneFrames() > stopped, 5000, "Paused scene resumes correctly");

      LibraryStore store = new LibraryStore(c);
      Wallpaper gif = imported("gif");
      check(gif.kind.equals("gif"), "SAF import keeps an unnamed GIF animated");
      Wallpaper webp = imported("webp");
      check(webp.kind.equals("gif"), "SAF import keeps an unnamed WebP animated");
      Wallpaper image = imported("no-name");
      check(
          image.kind.equals("image") && store.file(image).isFile(),
          "Import accepts providers with no MIME or display metadata");
      bringApp();
      set(gif);
      key(KeyEvent.KEYCODE_HOME);
      until(
          () -> installedRenderer() instanceof ImageRenderer && installedRenderer().ready(),
          10000,
          "Imported GIF renders through the actual wallpaper service");
      ImageRenderer ir = (ImageRenderer) installedRenderer();
      check(
          Build.VERSION.SDK_INT >= 28
              ? field(ir, "drawable") instanceof AnimatedImageDrawable
              : field(ir, "movie") != null,
          "GIF uses a native animation decoder");
      shot("home-gif");
      if (Build.VERSION.SDK_INT >= 28) {
        bringApp();
        set(webp);
        key(KeyEvent.KEYCODE_HOME);
        until(
            () -> installedRenderer() instanceof ImageRenderer && installedRenderer().ready(),
            10000,
            "Imported animated WebP renders through the actual wallpaper service");
      }
      Wallpaper video = imported("video");
      check(video.kind.equals("video"), "SAF imports a video without a filename extension");
      bringApp();
      set(video);
      key(KeyEvent.KEYCODE_HOME);
      until(
          () -> installedRenderer() instanceof VideoRenderer && installedRenderer().ready(),
          15000,
          "Imported video plays as the actual home-screen wallpaper");
      android.media.MediaPlayer player =
          (android.media.MediaPlayer) field(installedRenderer(), "player");
      check(player.getVideoWidth() == 3840, "Home-screen wallpaper decodes the original 4K source");
      shot("home-video");
      bringApp();
      waitMs(400);
      check(
          installedRenderer() == null,
          "Hidden video immediately frees its decoder for app/system previews");
      call("showDetail", new Class[] {Wallpaper.class}, video);
      until(
          () ->
              field(activity, "preview") != null
                  && ((Renderer) field(field(activity, "preview"), "renderer")).ready(),
          15000,
          "4K video preview reopens after applying it");
      dismissDetail();
      set(Wallpaper.BUILTINS[0]);

      call("controlPanel", new Class[] {});
      waitMs(700);
      AlertDialog panel = (AlertDialog) field(activity, "controlDialog");
      EngineFigureView figure = typed(panel.getWindow().getDecorView(), EngineFigureView.class);
      check(figure != null, "Display panel uses the native SVG illustration");
      until(
          () -> (Long) field(figure, "animationFrames") > 2,
          4000,
          "SVG illustration actually animates");
      shot("svg-motion-a");
      waitMs(500);
      shot("svg-motion-b");
      main(() -> figure.performClick());
      waitMs(150);
      before = (Long) field(figure, "animationFrames");
      waitMs(400);
      check((Long) field(figure, "animationFrames") == before, "Tap pauses SVG animation");
      main(() -> figure.performClick());
      final long illustrationPaused = before;
      until(
          () -> (Long) field(figure, "animationFrames") > illustrationPaused,
          2000,
          "Tap resumes SVG animation");
      main(() -> Settings.of(c).edit().putBoolean("reduce_motion", true).commit());
      waitMs(150);
      before = (Long) field(figure, "animationFrames");
      waitMs(400);
      check((Long) field(figure, "animationFrames") == before, "Reduce motion stops SVG animation");
      main(() -> Settings.of(c).edit().putBoolean("reduce_motion", false).commit());
      main(() -> panel.dismiss());
      waitMs(100);
      before = (Long) field(figure, "animationFrames");
      waitMs(400);
      check(
          (Long) field(figure, "animationFrames") == before,
          "Dismissed SVG has no background animation loop");

      key(KeyEvent.KEYCODE_HOME);
      until(() -> sceneFrames() > 3, 8000, "Wallpaper returns after SVG controls are dismissed");
      bringApp();
      waitMs(16000);
      check(
          installedRenderer() == null,
          "Hidden GPU wallpaper releases resources after the idle timeout");
      key(KeyEvent.KEYCODE_HOME);
      until(() -> sceneFrames() > 3, 8000, "Home-screen wallpaper recreates after idle release");
      check(
          Settings.of(c).getString("last_error", "").isEmpty(),
          "No accumulated errors during real wallpaper flows");
      store.delete(gif);
      store.delete(webp);
      store.delete(image);
      store.delete(video);
      result.putString(
          "stream",
          report
              + "\nPASS: "
              + assertions
              + " wallpaper flow assertions on API "
              + Build.VERSION.SDK_INT
              + "\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      try {
        shot("failure");
      } catch (Exception ignored) {
      }
      result.putString(
          "stream", report + "\nFAIL: " + e + "\n" + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
