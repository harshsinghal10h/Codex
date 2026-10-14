package io.lively.android;

/** Conventional 16:9 quality names, with aspect-preserving maximum-edge render budgets. */
final class QualityPreset {
  static final String[] LABELS = {
    "720p · 30 FPS",
    "1080p · 30 FPS",
    "1080p · 60 FPS",
    "1440p · 30 FPS",
    "1440p · 60 FPS",
    "4K UHD · 30 FPS",
    "4K UHD · 60 FPS"
  };
  static final int[] CAPS = {1280, 1920, 1920, 2560, 2560, 3840, 3840};
  static final int[] RATES = {30, 30, 60, 30, 60, 30, 60};

  static int index(int cap, int fps) {
    for (int i = 0; i < CAPS.length; i++) if (CAPS[i] == cap && RATES[i] == fps) return i;
    return -1;
  }

  static String label(int cap, int fps) {
    int at = index(cap, fps);
    return at >= 0 ? LABELS[at] : "Custom · " + cap + " px · " + fps + " FPS";
  }
}
