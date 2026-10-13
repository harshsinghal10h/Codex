package io.lively.android;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.view.Surface;

final class VideoRenderer implements Renderer {
  private final Context context;
  private final Wallpaper wallpaper;
  private final Surface surface;
  private MediaPlayer player;
  private boolean running, prepared, released;

  VideoRenderer(Context c, Wallpaper w, Surface s) {
    context = c;
    wallpaper = w;
    surface = s;
  }

  public void start() {
    if (released) return;
    running = true;
    if (player == null)
      try {
        player = new MediaPlayer();
        player.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());
        if (wallpaper.file.startsWith("https:")) player.setDataSource(wallpaper.file);
        else player.setDataSource(new LibraryStore(context).file(wallpaper).getPath());
        player.setSurface(surface);
        player.setLooping(true);
        player.setVolume(0, 0);
        player.setOnPreparedListener(
            p -> {
              if (released) return;
              prepared = true;
              configure();
              if (running) p.start();
            });
        player.setOnErrorListener(
            (p, what, extra) -> {
              Metrics.error(
                  context,
                  "Video cannot play on this device ("
                      + what
                      + " / "
                      + extra
                      + "). Try an H.264 MP4 or a supported HEVC file.");
              release();
              return true;
            });
        player.prepareAsync();
      } catch (Exception e) {
        Metrics.error(context, "Video: " + e.getMessage());
        release();
      }
    else if (prepared)
      try {
        player.start();
      } catch (Exception e) {
        Metrics.error(context, e.getMessage());
      }
  }

  public void pause() {
    running = false;
    if (player != null && prepared)
      try {
        player.pause();
      } catch (Exception ignored) {
      }
  }

  public void configure() {
    if (player != null && prepared)
      try {
        String fit = Settings.of(context).getString("fit", "crop");
        player.setVideoScalingMode(
            fit.equals("crop")
                ? MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                : MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        float volume =
            Settings.of(context).getBoolean("sound", false)
                ? Settings.of(context).getInt("volume", 20) / 100f
                : 0;
        player.setVolume(volume, volume);
        float speed = Settings.value(context, wallpaper, "speed", 1);
        player.setPlaybackParams(new PlaybackParams().setSpeed(Math.max(.25f, Math.min(2, speed))));
        if (!running) player.pause();
      } catch (Exception e) {
        Metrics.error(context, "Video setting: " + e.getMessage());
      }
  }

  public void touch(float x, float y) {}

  public boolean failed() {
    return released;
  }

  public void release() {
    released = true;
    running = false;
    prepared = false;
    if (player != null) {
      player.reset();
      player.release();
      player = null;
    }
  }
}
