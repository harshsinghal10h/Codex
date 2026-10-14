package io.lively.android;

import android.content.Context;
import java.util.concurrent.atomic.AtomicLong;

final class Metrics {
  static final AtomicLong frames = new AtomicLong();
  static volatile String state = "Idle";

  static void frame(Context c) {
    frames.incrementAndGet();
  }

  static void error(Context c, String message) {
    state = "Playback error";
    Settings.of(c)
        .edit()
        .putString("last_error", message == null ? "Unknown playback error" : message)
        .apply();
    android.util.Log.e("Lively", message);
  }
}
