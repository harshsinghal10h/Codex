package io.lively.android;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** WebView on an app-owned virtual display: no screen capture or bitmap readback. */
final class WebRenderer implements Renderer {
  private final Context context;
  private final Wallpaper wallpaper;
  private final Surface surface;
  private final int width, height;
  private VirtualDisplay display;
  private Presentation presentation;
  private WebView view;
  private boolean running, released;
  private static final Set<WebRenderer> active = new HashSet<>();

  WebRenderer(Context c, Wallpaper w, Surface s, int width, int height) {
    context = c;
    wallpaper = w;
    surface = s;
    this.width = width;
    this.height = height;
  }

  private void init() throws Exception {
    DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    display =
        dm.createVirtualDisplay(
            "Lively web",
            width,
            height,
            context.getResources().getDisplayMetrics().densityDpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    if (display == null)
      throw new IOException("This device cannot render web wallpapers to a private display");
    presentation = new Presentation(context, display.getDisplay());
    view = new WebView(presentation.getContext());
    view.setBackgroundColor(Color.BLACK);
    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    WebSettings s = view.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMediaPlaybackRequiresUserGesture(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setSupportMultipleWindows(false);
    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
    view.setWebViewClient(
        new WebViewClient() {
          public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return !"https".equals(r.getUrl().getScheme());
          }

          public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
            Uri u = request.getUrl();
            if ((wallpaper.id + ".lively.local").equals(u.getHost())
                && "https".equals(u.getScheme()))
              try {
                String path = u.getPath();
                if (path == null || path.equals("/")) path = "/" + wallpaper.file;
                File f =
                    ZipSandbox.resolve(
                        new LibraryStore(context).folder(wallpaper), path.substring(1));
                if (!f.isFile()) return denied(404);
                String ext = MimeTypeMap.getFileExtensionFromUrl(f.getName());
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (ext.equals("js")) mime = "application/javascript";
                if (mime == null) mime = "application/octet-stream";
                return new WebResourceResponse(mime, "UTF-8", new FileInputStream(f));
              } catch (Exception e) {
                return denied(403);
              }
            if (!"https".equals(u.getScheme())
                || !(wallpaper.file.startsWith("https:")
                    || Settings.of(context).getBoolean("web_network", false))) return denied(403);
            return null;
          }

          public void onPageFinished(WebView v, String url) {
            configure();
            if (!running) v.onPause();
          }

          public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame()) Metrics.error(context, "Web wallpaper: " + e.getDescription());
          }

          public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
            Metrics.error(context, "Web renderer stopped. Reopen the wallpaper to retry.");
            release();
            return true;
          }
        });
    presentation.setContentView(view);
    Window w = presentation.getWindow();
    if (w != null) {
      w.setFlags(
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
          WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
      w.setBackgroundDrawableResource(android.R.color.black);
    }
    presentation.show();
    view.loadUrl(
        wallpaper.file.startsWith("https:")
            ? wallpaper.file
            : "https://" + wallpaper.id + ".lively.local/" + Uri.encode(wallpaper.file, "/"));
  }

  private static WebResourceResponse denied(int code) {
    return new WebResourceResponse(
        "text/plain",
        "UTF-8",
        code,
        code == 404 ? "Not found" : "Blocked",
        Collections.emptyMap(),
        new ByteArrayInputStream(new byte[0]));
  }

  public void start() {
    if (released) return;
    running = true;
    try {
      if (view == null) init();
      active.add(this);
      view.resumeTimers();
      view.onResume();
      presentation.show();
      view.evaluateJavascript(
          "if(typeof"
              + " livelyWallpaperPlaybackChanged==='function')livelyWallpaperPlaybackChanged(true)",
          null);
    } catch (Exception e) {
      Metrics.error(context, "Web engine: " + e.getMessage());
      release();
    }
  }

  public void pause() {
    running = false;
    active.remove(this);
    if (view != null) {
      view.evaluateJavascript(
          "if(typeof"
              + " livelyWallpaperPlaybackChanged==='function')livelyWallpaperPlaybackChanged(false)",
          null);
      view.onPause();
      if (active.isEmpty()) view.pauseTimers();
    }
    if (presentation != null) presentation.hide();
  }

  public void release() {
    if (released) return;
    released = true;
    pause();
    if (view != null) {
      view.stopLoading();
      view.loadUrl("about:blank");
      view.destroy();
      view = null;
    }
    if (presentation != null) {
      presentation.dismiss();
      presentation = null;
    }
    if (display != null) {
      display.release();
      display = null;
    }
  }

  public void configure() {
    if (view == null) return;
    JSONObject properties = new LibraryStore(context).properties(wallpaper);
    Iterator<String> names = properties.keys();
    while (names.hasNext()) {
      String name = names.next();
      JSONObject prop = properties.optJSONObject(name);
      if (prop == null) continue;
      Object value = prop.opt("value");
      String saved = Settings.of(context).getString(wallpaper.id + ".property." + name, null);
      if (saved != null)
        try {
          value = new JSONTokener(saved).nextValue();
        } catch (Exception ignored) {
        }
      view.evaluateJavascript(
          "if(typeof livelyPropertyListener==='function')livelyPropertyListener("
              + JSONObject.quote(name)
              + ","
              + jsonValue(value)
              + ")",
          null);
    }
  }

  private static String jsonValue(Object value) {
    if (value == null || value == JSONObject.NULL) return "null";
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    return JSONObject.quote(String.valueOf(value));
  }

  public void touch(float x, float y) {
    if (view != null)
      view.evaluateJavascript(
          "document.dispatchEvent(new MouseEvent('mousemove',{clientX:"
              + (int) (x * width)
              + ",clientY:"
              + (int) (y * height)
              + ",bubbles:true}))",
          null);
  }

  void touchEvent(float x, float y, int action) {
    if (view != null) {
      long now = SystemClock.uptimeMillis();
      MotionEvent event = MotionEvent.obtain(now, now, action, x * width, y * height, 0);
      view.dispatchTouchEvent(event);
      event.recycle();
    }
  }
}
