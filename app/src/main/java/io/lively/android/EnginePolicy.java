package io.lively.android;

/** Pure policy shared by the wallpaper, preview, and tests. No wakeups when hidden. */
public final class EnginePolicy {
  public static boolean shouldRun(
      boolean visible,
      boolean screenOn,
      boolean paused,
      boolean saver,
      boolean pauseSaver,
      boolean charging,
      boolean pauseBattery,
      int percent,
      int threshold,
      boolean thermal,
      boolean pauseThermal) {
    return visible
        && screenOn
        && !paused
        && !(saver && pauseSaver)
        && !(!charging && pauseBattery)
        && !(percent <= threshold && !charging)
        && !(thermal && pauseThermal);
  }

  public static int[] dimensions(int width, int height, int cap) {
    width = Math.max(1, width);
    height = Math.max(1, height);
    cap = Math.max(240, Math.min(3840, cap));
    double scale = Math.min(1.0, cap / (double) Math.max(width, height));
    return new int[] {
      Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))
    };
  }

  public static int frameRate(int requested, boolean saver, boolean thermal) {
    return Math.max(15, Math.min(saver || thermal ? 24 : 60, requested));
  }

  public static String kind(String name) {
    String n = name.toLowerCase(java.util.Locale.ROOT);
    if (n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".mov"))
      return "video";
    if (n.endsWith(".gif") || n.endsWith(".webp")) return "gif";
    if (n.endsWith(".html") || n.endsWith(".htm")) return "web";
    if (n.endsWith(".jpg")
        || n.endsWith(".jpeg")
        || n.endsWith(".png")
        || n.endsWith(".heic")
        || n.endsWith(".avif")
        || n.endsWith(".bmp")) return "image";
    return "";
  }
}
