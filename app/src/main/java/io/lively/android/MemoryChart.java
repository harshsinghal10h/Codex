package io.lively.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Arc-inspired range transitions; every sample is the app's real process PSS. */
final class MemoryChart extends View {
  private final Paint p = new Paint(3);
  private final Path line = new Path();
  private final ArrayList<Float> samples = new ArrayList<>();
  private final int text;

  MemoryChart(Context c, int color) {
    super(c);
    text = color;
    setContentDescription("App memory graph. Values are in mebibytes.");
  }

  void add(float value) {
    samples.add(value);
    if (samples.size() > 40) samples.remove(0);
    setContentDescription("App process memory: " + Math.round(value) + " mebibytes");
    invalidate();
  }

  protected void onDraw(Canvas c) {
    float w = getWidth(), h = getHeight(), left = dp(44), bottom = h - dp(22);
    float max = 64;
    for (float v : samples) max = Math.max(max, v * 1.15f);
    max = (float) Math.ceil(max / 32) * 32;
    p.setTextSize(dp(10));
    for (int i = 0; i < 3; i++) {
      float y = dp(14) + (bottom - dp(14)) * i / 2;
      p.setColor(0x224e657e);
      p.setStrokeWidth(1);
      c.drawLine(left, y, w - dp(10), y, p);
      p.setColor(text);
      c.drawText(String.valueOf((int) (max * (2 - i) / 2)), dp(4), y + dp(3), p);
    }
    p.setColor(text);
    c.drawText("MiB", dp(4), h - dp(4), p);
    c.drawText("Last " + samples.size() + " samples", left, h - dp(4), p);
    if (samples.size() < 2) return;
    line.reset();
    for (int i = 0; i < samples.size(); i++) {
      float x = left + i * (w - left - dp(12)) / Math.max(1, samples.size() - 1),
          y = bottom - samples.get(i) / max * (bottom - dp(14));
      if (i == 0) line.moveTo(x, y);
      else line.lineTo(x, y);
    }
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(dp(2));
    p.setColor(0xff59a8fa);
    c.drawPath(line, p);
    p.setStyle(Paint.Style.FILL);
  }

  private float dp(float n) {
    return n * getResources().getDisplayMetrics().density;
  }
}
