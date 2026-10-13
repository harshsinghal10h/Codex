package io.lively.android;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
  public static SharedPreferences of(Context c) {
    return c.getSharedPreferences("lively", Context.MODE_PRIVATE);
  }

  public static int cap(Context c) {
    return of(c).getInt("resolution", 1920);
  }

  public static int fps(Context c) {
    return of(c).getInt("fps", 30);
  }

  public static float value(Context c, Wallpaper w, String key, float fallback) {
    return of(c).getFloat(w.id + "." + key, fallback);
  }

  public static String active(Context c) {
    return of(c).getString("active", "fluids");
  }

  public static String candidate(Context c) {
    return of(c).getString("candidate", active(c));
  }

  public static int background(Context c) {
    return of(c).getString("theme", "system").equals("light") ? 0xfff2f3f5 : 0xff191b20;
  }
}
