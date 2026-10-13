package io.lively.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.util.concurrent.*;

final class ImageRenderer implements Renderer, Drawable.Callback {
  private final Context context;
  private final Wallpaper wallpaper;
  private final Surface surface;
  private final int width, height;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean running, released;
  private Drawable drawable;
  private Movie movie;
  private Bitmap bitmap;
  private long started;
  private float focusX = .5f, focusY = .5f;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  ImageRenderer(Context c, Wallpaper w, Surface s, int width, int height) {
    context = c;
    wallpaper = w;
    surface = s;
    this.width = width;
    this.height = height;
    worker.execute(
        () -> {
          Drawable d = null;
          Bitmap b = null;
          Movie m = null;
          try {
            File f = new LibraryStore(context).file(wallpaper);
            if (Build.VERSION.SDK_INT >= 28) {
              d =
                  ImageDecoder.decodeDrawable(
                      ImageDecoder.createSource(f),
                      (decoder, info, source) -> {
                        int[] target =
                            EnginePolicy.dimensions(
                                info.getSize().getWidth(),
                                info.getSize().getHeight(),
                                Math.min(Settings.cap(context), Math.max(width, height)));
                        decoder.setTargetSize(target[0], target[1]);
                      });
            } else if (wallpaper.file.toLowerCase().endsWith(".gif")) {
              if (f.length() > 32 * 1024 * 1024)
                throw new IOException("Large GIFs require Android 9 or newer");
              m = Movie.decodeFile(f.getPath());
              if (m == null) throw new IOException("Invalid GIF");
            } else {
              BitmapFactory.Options o = new BitmapFactory.Options();
              o.inJustDecodeBounds = true;
              BitmapFactory.decodeFile(f.getPath(), o);
              int cap = Math.min(Settings.cap(context), Math.max(width, height));
              o.inSampleSize = 1;
              while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > cap * 1.2)
                o.inSampleSize *= 2;
              o.inJustDecodeBounds = false;
              b = BitmapFactory.decodeFile(f.getPath(), o);
              if (b == null) throw new IOException("Cannot decode image");
              d = new BitmapDrawable(context.getResources(), b);
            }
            final Drawable decoded = d;
            final Movie decodedMovie = m;
            final Bitmap decodedBitmap = b;
            main.post(
                () -> {
                  if (released) {
                    if (decodedBitmap != null) decodedBitmap.recycle();
                    return;
                  }
                  drawable = decoded;
                  movie = decodedMovie;
                  bitmap = decodedBitmap;
                  if (drawable != null) drawable.setCallback(this);
                  if (running) {
                    if (Build.VERSION.SDK_INT >= 28 && drawable instanceof AnimatedImageDrawable)
                      ((AnimatedImageDrawable) drawable).start();
                    draw();
                  }
                });
          } catch (Throwable e) {
            Metrics.error(context, "Image: " + e.getMessage());
          } finally {
            worker.shutdown();
          }
        });
  }

  private final Runnable tick = () -> draw();

  private void draw() {
    if (released || !running || !surface.isValid()) return;
    Canvas c = null;
    try {
      c = surface.lockHardwareCanvas();
      c.drawColor(Color.BLACK);
      int sw = drawable != null ? drawable.getIntrinsicWidth() : movie != null ? movie.width() : 0,
          sh =
              drawable != null ? drawable.getIntrinsicHeight() : movie != null ? movie.height() : 0;
      if (sw <= 0 || sh <= 0) return;
      String fit = Settings.of(context).getString("fit", "crop");
      float sx = c.getWidth() / (float) sw, sy = c.getHeight() / (float) sh;
      float scale = fit.equals("crop") ? Math.max(sx, sy) : Math.min(sx, sy);
      if (!fit.equals("stretch")) sx = sy = scale;
      c.save();
      c.translate(
          (c.getWidth() - sw * sx) * (fit.equals("crop") ? focusX : .5f),
          (c.getHeight() - sh * sy) * (fit.equals("crop") ? focusY : .5f));
      c.scale(sx, sy);
      if (drawable != null) {
        drawable.setBounds(0, 0, sw, sh);
        drawable.draw(c);
      } else if (movie != null) {
        movie.setTime(
            (int) ((SystemClock.uptimeMillis() - started) % Math.max(1, movie.duration())));
        movie.draw(c, 0, 0);
        main.postDelayed(tick, 1000 / Settings.fps(context));
      }
      c.restore();
      Metrics.frame(context);
    } catch (Exception e) {
      Metrics.error(context, "Image surface: " + e.getMessage());
    } finally {
      if (c != null)
        try {
          surface.unlockCanvasAndPost(c);
        } catch (Exception ignored) {
        }
    }
  }

  public void start() {
    if (released) return;
    running = true;
    started = SystemClock.uptimeMillis();
    if (Build.VERSION.SDK_INT >= 28 && drawable instanceof AnimatedImageDrawable)
      ((AnimatedImageDrawable) drawable).start();
    draw();
  }

  public void pause() {
    running = false;
    main.removeCallbacks(tick);
    if (Build.VERSION.SDK_INT >= 28 && drawable instanceof AnimatedImageDrawable)
      ((AnimatedImageDrawable) drawable).stop();
  }

  public void release() {
    released = true;
    pause();
    worker.shutdownNow();
    if (drawable != null) drawable.setCallback(null);
    drawable = null;
    movie = null;
    if (bitmap != null) bitmap.recycle();
    bitmap = null;
  }

  public void configure() {
    draw();
  }

  public void touch(float x, float y) {
    focusX = Math.max(0, Math.min(1, x));
    focusY = Math.max(0, Math.min(1, y));
    draw();
  }

  public void invalidateDrawable(Drawable d) {
    main.removeCallbacks(tick);
    if (running) main.post(tick);
  }

  public void scheduleDrawable(Drawable d, Runnable r, long time) {
    main.postAtTime(r, time);
  }

  public void unscheduleDrawable(Drawable d, Runnable r) {
    main.removeCallbacks(r);
  }
}
