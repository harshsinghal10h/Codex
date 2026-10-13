package io.lively.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Real framework tests, run with am instrument. Not part of the installed release. */
public final class NativeTests extends Instrumentation {
  private int assertions;
  private Bundle arguments;
  private final StringBuilder report = new StringBuilder();
  private MainActivity activity;

  public void onCreate(Bundle args) {
    arguments = args;
    super.onCreate(args);
    start();
  }

  private void check(boolean condition, String label) {
    assertions++;
    if (!condition) throw new AssertionError(label);
    report.append("PASS ").append(label).append('\n');
  }

  private void main(Runnable action) {
    runOnMainSync(action);
  }

  private static void waitMs(long millis) {
    android.os.SystemClock.sleep(millis);
  }

  private Object field(Object target, String name) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(target);
  }

  private void call(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    main(
        () -> {
          try {
            m.invoke(activity, args);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private void shot(String name) throws Exception {
    Bitmap b = getUiAutomation().takeScreenshot();
    if (b != null) {
      File f = new File(getTargetContext().getExternalFilesDir(null), "qa-" + name + ".png");
      try (OutputStream out = new FileOutputStream(f)) {
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
      b.recycle();
    }
  }

  public void onStart() {
    Bundle result = new Bundle();
    try {
      Context c = getTargetContext();
      LibraryStore store = new LibraryStore(c);
      main(
          () ->
              Settings.of(c)
                  .edit()
                  .putString("theme", "dark")
                  .putInt("fps", 15)
                  .putInt("resolution", 720)
                  .putInt("low_battery", 0)
                  .putBoolean("pause_saver", false)
                  .putBoolean("pause_thermal", false)
                  .remove("last_error")
                  .commit());
      check(store.all().size() >= 8, "Built-in library initialises");
      File cache = new File(c.getCacheDir(), "exports");
      cache.mkdirs();
      File pkg = new File(cache, "qa-image.lively.zip");
      Bitmap b = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
      b.eraseColor(0xffe39540);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      b.compress(Bitmap.CompressFormat.PNG, 100, png);
      b.recycle();
      packageFile(pkg, "test.png", 11, png.toByteArray(), null);
      Wallpaper imported =
          store.importUri(Uri.parse("content://io.lively.android.share/qa-image.lively.zip"));
      check(imported.kind.equals("image"), "Lively image import");
      check(
          Arrays.equals(
              hash(png.toByteArray()),
              hash(java.nio.file.Files.readAllBytes(store.file(imported).toPath()))),
          "Original image byte preservation");
      File exported = store.export(imported);
      try (ZipFile z = new ZipFile(exported)) {
        check(
            z.getEntry("LivelyInfo.json") != null && z.getEntry("test.png") != null,
            "Export is a Lively package");
      }
      if (Build.VERSION.SDK_INT >= 34) {
        Bitmap sdr = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        sdr.eraseColor(0xff587aa2);
        Bitmap gain = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        gain.eraseColor(0xffaaaaaa);
        Gainmap gm = new Gainmap(gain);
        gm.setRatioMax(4, 4, 4);
        gm.setDisplayRatioForFullHdr(4);
        sdr.setGainmap(gm);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        sdr.compress(Bitmap.CompressFormat.JPEG, 95, jpeg);
        sdr.recycle();
        gain.recycle();
        File hdrPkg = new File(cache, "qa-hdr.lively.zip");
        packageFile(hdrPkg, "hdr.jpg", 11, jpeg.toByteArray(), null);
        Wallpaper hdr =
            store.importUri(Uri.parse("content://io.lively.android.share/qa-hdr.lively.zip"));
        Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(store.file(hdr)));
        check(decoded.hasGainmap(), "Ultra HDR gain map retained after import");
        decoded.recycle();
        check(
            Arrays.equals(
                hash(jpeg.toByteArray()),
                hash(java.nio.file.Files.readAllBytes(store.file(hdr).toPath()))),
            "HDR original bytes unchanged");
        store.delete(hdr);
      }
      File hostile = new File(cache, "qa-hostile.lively.zip");
      packageFile(hostile, "../escape", 11, new byte[] {1}, null);
      int before = store.all().size();
      try {
        store.importUri(Uri.parse("content://io.lively.android.share/qa-hostile.lively.zip"));
        throw new AssertionError("Traversal import allowed");
      } catch (IOException expected) {
      }
      check(store.all().size() == before, "Failed imports roll back");
      File app = new File(cache, "qa-app.lively.zip");
      packageFile(app, "app.exe", 0, new byte[] {1}, null);
      try {
        store.importUri(Uri.parse("content://io.lively.android.share/qa-app.lively.zip"));
        throw new AssertionError("Windows EXE import allowed");
      } catch (IOException expected) {
      }
      check(store.all().size() == before, "Windows executable rejected without library pollution");
      Intent intent = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity = (MainActivity) startActivitySync(intent);
      waitForIdleSync();
      waitMs(1800);
      shot("library-r1");
      for (Wallpaper w : Wallpaper.BUILTINS) {
        long frames = Metrics.frames.get();
        call("showDetail", new Class[] {Wallpaper.class}, w);
        waitMs(2500);
        check(Metrics.frames.get() > frames, "GPU scene renders: " + w.id);
        if (w.scene == 0) shot("preview-r1");
        RendererController controller = (RendererController) field(activity, "preview");
        main(() -> controller.visible(false));
        waitMs(300);
        long stopped = Metrics.frames.get();
        waitMs(600);
        check(Metrics.frames.get() == stopped, "GPU idle while hidden: " + w.id);
        main(() -> ((AlertDialog) safeField(activity, "detailDialog")).dismiss());
        waitMs(300);
      }
      String web =
          "<!doctype"
              + " html><html><style>body{margin:0;background:#22dd88}canvas{width:100vw;height:100vh}</style><canvas"
              + " id='c'></canvas><script>window.livelyPropertyListener=(n,v)=>{document.body.dataset[n]=v};let"
              + " g=c.getContext('2d');function"
              + " frame(t){g.fillStyle='#22dd88';g.fillRect(0,0,300,150);requestAnimationFrame(frame)}frame(0)</script></html>";
      File webPkg = new File(cache, "qa-web.lively.zip");
      packageFile(
          webPkg,
          "wallpaper.html",
          1,
          web.getBytes("UTF-8"),
          "{\"amount\":{\"type\":\"slider\",\"text\":\"Amount\",\"min\":0,\"max\":10,\"value\":3}}");
      Wallpaper webW =
          store.importUri(Uri.parse("content://io.lively.android.share/qa-web.lively.zip"));
      call("showDetail", new Class[] {Wallpaper.class}, webW);
      waitMs(5000);
      check(
          Settings.of(c).getString("last_error", "").isEmpty(),
          "Web presentation starts without permission or renderer errors");
      RendererController rc = (RendererController) field(activity, "preview");
      Renderer rr = (Renderer) field(rc, "renderer");
      check(rr instanceof WebRenderer, "Web package uses native WebView surface path");
      android.webkit.WebView webView = (android.webkit.WebView) field(rr, "view");
      check(webView != null, "Private-display WebView created");
      shot("web-preview");
      main(() -> ((AlertDialog) safeField(activity, "detailDialog")).dismiss());
      waitMs(300);
      call("showDetail", new Class[] {Wallpaper.class}, imported);
      waitMs(1800);
      shot("image-preview");
      check(
          Settings.of(c).getString("last_error", "").isEmpty(),
          "Image preview renders without errors");
      main(() -> ((AlertDialog) safeField(activity, "detailDialog")).dismiss());
      {
        ByteArrayOutputStream vb = new ByteArrayOutputStream();
        try (InputStream vin = getContext().getAssets().open("video-4k.mp4")) {
          byte[] buf = new byte[8192];
          int n;
          while ((n = vin.read(buf)) != -1) vb.write(buf, 0, n);
        }
        byte[] video = vb.toByteArray();
        File vidPkg = new File(cache, "qa-video.lively.zip");
        packageFile(vidPkg, "video.mp4", 7, video, null);
        Wallpaper v =
            store.importUri(Uri.parse("content://io.lively.android.share/qa-video.lively.zip"));
        call("showDetail", new Class[] {Wallpaper.class}, v);
        waitMs(4500);
        check(
            Settings.of(c).getString("last_error", "").isEmpty(),
            "4K video preview starts without decoder errors");
        RendererController cv = (RendererController) field(activity, "preview");
        VideoRenderer vr = (VideoRenderer) field(cv, "renderer");
        android.media.MediaPlayer player = (android.media.MediaPlayer) field(vr, "player");
        check(
            player != null && player.getVideoWidth() == 3840, "Original 3840-pixel video decoded");
        shot("video-preview");
        main(() -> ((AlertDialog) safeField(activity, "detailDialog")).dismiss());
        store.delete(v);
      }
      call("navigate", new Class[] {String.class}, "Settings");
      waitMs(1000);
      shot("settings-r1");
      main(
          () -> {
            try {
              Field f = MainActivity.class.getDeclaredField("settingCategory");
              f.setAccessible(true);
              f.set(activity, "Performance");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      call("build", new Class[] {});
      waitMs(1600);
      shot("performance-r1");
      main(() -> Settings.of(c).edit().putString("theme", "light").commit());
      call("navigate", new Class[] {String.class}, "Library");
      waitMs(1200);
      shot("light-r1");
      main(() -> Settings.of(c).edit().putString("theme", "dark").commit());
      check(Settings.of(c).getString("last_error", "").isEmpty(), "No accumulated playback errors");
      store.delete(imported);
      store.delete(webW);
      main(
          () ->
              Settings.of(c)
                  .edit()
                  .putInt("fps", 30)
                  .putInt("resolution", 1920)
                  .putInt("low_battery", 15)
                  .putBoolean("pause_saver", true)
                  .putBoolean("pause_thermal", true)
                  .commit());
      result.putString("stream", report + "\nPASS: " + assertions + " native assertions\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString(
          "stream", report + "\nFAIL: " + e + "\n" + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private Object safeField(Object target, String name) {
    try {
      return field(target, name);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] hash(byte[] data) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(data);
  }

  private void packageFile(File file, String name, int type, byte[] data, String properties)
      throws Exception {
    try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(file))) {
      JSONObject meta =
          new JSONObject().put("Title", "QA " + name).put("FileName", name).put("Type", type);
      entry(z, "LivelyInfo.json", meta.toString().getBytes("UTF-8"));
      entry(z, name, data);
      if (properties != null) entry(z, "LivelyProperties.json", properties.getBytes("UTF-8"));
    }
  }

  private void entry(ZipOutputStream z, String path, byte[] data) throws Exception {
    z.putNextEntry(new ZipEntry(path));
    z.write(data);
    z.closeEntry();
  }
}
