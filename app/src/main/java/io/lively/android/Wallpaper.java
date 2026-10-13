package io.lively.android;

import org.json.JSONObject;

public final class Wallpaper {
  public final String id, title, description, author, kind, file, thumbnail;
  public final int scene;

  public Wallpaper(
      String id,
      String title,
      String description,
      String author,
      String kind,
      String file,
      String thumbnail,
      int scene) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.author = author;
    this.kind = kind;
    this.file = file;
    this.thumbnail = thumbnail;
    this.scene = scene;
  }

  public boolean builtin() {
    return "shader".equals(kind);
  }

  public String typeLabel() {
    return builtin()
        ? "Interactive scene"
        : "video".equals(kind)
            ? "Video"
            : "web".equals(kind)
                ? "Web wallpaper"
                : "gif".equals(kind) ? "Animated image" : "Image";
  }

  public JSONObject json() throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)
        .put("author", author)
        .put("kind", kind)
        .put("file", file)
        .put("thumbnail", thumbnail)
        .put("scene", scene);
  }

  public static Wallpaper from(JSONObject j) {
    return new Wallpaper(
        j.optString("id"),
        j.optString("title"),
        j.optString("description"),
        j.optString("author"),
        j.optString("kind"),
        j.optString("file"),
        j.optString("thumbnail"),
        j.optInt("scene"));
  }

  public static final Wallpaper[] BUILTINS = {
    new Wallpaper(
        "fluids", "Fluids", "Flowing colour fields", "Lively Android", "shader", "", "", 0),
    new Wallpaper("ribbons", "Ribbon", "Light in motion", "Lively Android", "shader", "", "", 1),
    new Wallpaper(
        "rain", "Rain", "A window into the weather", "Lively Android", "shader", "", "", 2),
    new Wallpaper(
        "waves", "Waves", "An endless quiet ocean", "Lively Android", "shader", "", "", 3),
    new Wallpaper(
        "aurora",
        "Aurora",
        "Northern light, slowly shifting",
        "Lively Android",
        "shader",
        "",
        "",
        4),
    new Wallpaper(
        "depth",
        "Contour",
        "Layered terrain and touch parallax",
        "Lively Android",
        "shader",
        "",
        "",
        5),
    new Wallpaper(
        "stars",
        "Starfield",
        "Through a field of distant stars",
        "Lively Android",
        "shader",
        "",
        "",
        6),
    new Wallpaper(
        "gradient",
        "Gradient",
        "Soft gradients, subtle motion",
        "Lively Android",
        "shader",
        "",
        "",
        7)
  };
}
