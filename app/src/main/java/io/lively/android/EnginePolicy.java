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
        && !(threshold > 0 && percent <= threshold && !charging)
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
    if (name == null) return "";
    String n = name.toLowerCase(java.util.Locale.ROOT);
    if (n.endsWith(".mp4")
        || n.endsWith(".webm")
        || n.endsWith(".mkv")
        || n.endsWith(".mov")
        || n.endsWith(".m4v")
        || n.endsWith(".3gp")) return "video";
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

  public static String mimeExtension(String mime) {
    if (mime == null) return "";
    switch (mime.toLowerCase(java.util.Locale.ROOT)) {
      case "image/gif":
        return ".gif";
      case "image/webp":
        return ".webp";
      case "image/png":
        return ".png";
      case "image/heic":
      case "image/heif":
        return ".heic";
      case "image/avif":
        return ".avif";
      case "image/jpeg":
        return ".jpg";
      case "video/webm":
        return ".webm";
      case "video/3gpp":
        return ".3gp";
      case "video/x-matroska":
        return ".mkv";
      case "video/quicktime":
        return ".mov";
      case "video/mp4":
      case "video/x-m4v":
        return ".mp4";
      case "text/html":
        return ".html";
      case "application/zip":
      case "application/x-zip-compressed":
        return ".zip";
      default:
        return "";
    }
  }
}
