package io.lively.android;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.io.*;
import java.util.*;

/** The original Anatomy SVG, drawn as native paths. Only the small inlays animate. */
final class EngineFigureView extends View
    implements SharedPreferences.OnSharedPreferenceChangeListener {
  private static final int[] DARK = {
    0xff2a2a2a, 0xff3e3e3e, 0xff6a6a6a, 0xffd4d4d4, 0xffffffff,
    0xff0f0f0f, 0xff121212, 0xff151515, 0xff181818, 0xff1c1c1c,
    0xff262626, 0xff1d1d1d, 0xff1b1b1b, 0xff131313
  };
  private static final int[] LIGHT = {
    0xffd4d4d6, 0xffb4b4b8, 0xff8a8a8f, 0xff26262a, 0xff18181a,
    0xffdcdcd9, 0xffe5e5e2, 0xffededea, 0xfff4f4f1, 0xfff8f8f6,
    0xffffffff, 0xffeeeeeb, 0xfff4f4f1, 0xfffbfbfa
  };
  private static List<Part> template;
  private final boolean light;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect visibleRect = new Rect();
  private Picture base;
  private boolean playing = true, ticking;
  private long began, animationFrames;
  private float phase;

  private static final class Part {
    final Path path;
    final int group, tone;
    final boolean line;

    Part(Path path, int group, int tone, boolean line) {
      this.path = path;
      this.group = group;
      this.tone = tone;
      this.line = line;
    }
  }

  EngineFigureView(Context c, boolean light) {
    super(c);
    this.light = light;
    setFocusable(true);
    setClickable(true);
    setOnClickListener(
        v -> {
          playing = !playing;
          updateMotion();
          announceForAccessibility(
              playing ? "Illustration animation resumed" : "Illustration animation paused");
        });
    try {
      if (template == null) template = read(c);
      base = new Picture();
      Canvas canvas = base.beginRecording(640, 400);
      for (Part p : template) drawPart(canvas, p, 1);
      base.endRecording();
    } catch (IOException e) {
      android.util.Log.e("Lively", "Vector illustration unavailable", e);
    }
    description();
  }

  private static List<Part> read(Context c) throws IOException {
    ArrayList<Part> result = new ArrayList<>();
    try (DataInputStream in = new DataInputStream(c.getAssets().open("display-engine.vec"))) {
      if (in.readInt() != 0x4c495647) throw new IOException("Invalid vector asset");
      in.readUnsignedShort();
      in.readUnsignedShort();
      int count = in.readUnsignedShort();
      for (int n = 0; n < count; n++) {
        int group = in.readShort(), tone = in.readUnsignedByte(), line = in.readUnsignedByte();
        int commands = in.readUnsignedShort();
        Path path = new Path();
        for (int k = 0; k < commands; k++) {
          int op = in.readUnsignedByte();
          if (op == 'M') path.moveTo(in.readFloat(), in.readFloat());
          else if (op == 'L') path.lineTo(in.readFloat(), in.readFloat());
          else if (op == 'Z') path.close();
          else throw new IOException("Invalid vector command");
        }
        result.add(new Part(path, group, tone, line == 1));
      }
    }
    return Collections.unmodifiableList(result);
  }

  private void drawPart(Canvas c, Part p, float opacity) {
    paint.setColor((light ? LIGHT : DARK)[p.tone]);
    paint.setAlpha(Math.round(255 * opacity));
    paint.setStyle(p.line ? Paint.Style.STROKE : Paint.Style.FILL);
    paint.setStrokeWidth(.6f);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    c.drawPath(p.path, paint);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    if (base == null) return;
    c.save();
    float scale = Math.min(getWidth() / 640f, getHeight() / 400f);
    c.translate((getWidth() - 640 * scale) / 2, (getHeight() - 400 * scale) / 2);
    c.scale(scale, scale);
    c.drawPicture(base);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1.3f);
    paint.setColor(light ? 0xff405a76 : 0xffc1d4e8);
    // A calm scan across the seated heat-sink inlays; no floating geometry.
    for (Part p : template) {
      if (p.line && p.tone >= 2 && p.group >= 39 && p.group <= 47) {
        float distance = (p.group - 39) / 8f - (phase % 1f);
        paint.setAlpha(Math.round(155 * (float) Math.exp(-distance * distance * 26)));
        c.drawPath(p.path, paint);
      }
    }
    for (Part p : template) {
      if (!p.line && p.tone == 10 && p.group == 34) {
        c.save();
        c.clipPath(p.path);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(light ? 0x30405a76 : 0x30c1d4e8);
        float x = 210 + (phase % 1f) * 120;
        c.drawRect(x - 12, 80, x + 3, 150, paint);
        c.restore();
      }
    }
    c.restore();
  }

  private final Runnable frame =
      new Runnable() {
        public void run() {
          if (!canAnimate()) {
            ticking = false;
            return;
          }
          phase = (SystemClock.uptimeMillis() - began) / 2400f;
          animationFrames++;
          invalidate();
          postDelayed(this, 42); // bounded at 24 fps; no loop while hidden
        }
      };

  private boolean canAnimate() {
    return playing
        && isAttachedToWindow()
        && getWindowVisibility() == VISIBLE
        && hasWindowFocus()
        && isShown()
        && getGlobalVisibleRect(visibleRect)
        && !Settings.of(getContext()).getBoolean("reduce_motion", false)
        && ValueAnimator.areAnimatorsEnabled();
  }

  private void updateMotion() {
    removeCallbacks(frame);
    ticking = canAnimate();
    if (ticking) {
      began = SystemClock.uptimeMillis() - (long) (phase * 2400);
      post(frame);
    }
    description();
  }

  private void description() {
    setContentDescription(
        "SVG rendering illustration. "
            + (playing ? "Tap to pause animation." : "Tap to resume animation."));
  }

  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    Settings.of(getContext()).registerOnSharedPreferenceChangeListener(this);
    updateMotion();
  }

  protected void onDetachedFromWindow() {
    removeCallbacks(frame);
    ticking = false;
    Settings.of(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    super.onDetachedFromWindow();
  }

  public void onWindowFocusChanged(boolean focus) {
    super.onWindowFocusChanged(focus);
    updateMotion();
  }

  protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (frame != null) updateMotion();
  }

  void refreshMotion() {
    updateMotion();
  }

  protected void onWindowVisibilityChanged(int v) {
    super.onWindowVisibilityChanged(v);
    if (frame != null) updateMotion();
  }

  public void onSharedPreferenceChanged(SharedPreferences p, String key) {
    if ("reduce_motion".equals(key)) updateMotion();
  }
}
