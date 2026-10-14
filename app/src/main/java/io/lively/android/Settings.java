package io.lively.android;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
  public static SharedPreferences of(Context c) {
    return c.getSharedPreferences("lively", Context.MODE_PRIVATE);
  }

  public static int cap(Context c) {
    return EnginePolicy.resolutionCap(of(c).getInt("resolution", 1920));
  }

  public static int fps(Context c) {
    return EnginePolicy.frameRate(of(c).getInt("fps", 30), false, false);
  }

  static void quality(Context c, int index) {
    if (index < 0 || index >= QualityPreset.CAPS.length) return;
    of(c)
        .edit()
        .putInt("resolution", QualityPreset.CAPS[index])
        .putInt("fps", QualityPreset.RATES[index])
        .apply();
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
