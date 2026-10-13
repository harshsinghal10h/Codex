package io.lively.android;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Small bounded native lens. Refraction uses the same rounded-edge normal as the supplied glass
 * library.
 */
final class GlassDrawable extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final boolean light;
  private final float radius;
  private Bitmap backdrop;
  private RuntimeShader lens;

  GlassDrawable(boolean light, float radius, boolean glass) {
    this.light = light;
    this.radius = radius;
    if (glass && Build.VERSION.SDK_INT >= 33)
      try {
        lens =
            new RuntimeShader(
                "uniform shader backdrop;uniform float2 size;uniform float radius;uniform float"
                    + " light;half4 main(float2 p){float2 q=abs(p-size*.5)-(size*.5-radius);float"
                    + " d=length(max(q,0.))+min(max(q.x,q.y),0.)-radius;float2"
                    + " n=normalize(p-size*.5+float2(.001));float"
                    + " edge=1.-smoothstep(-14.,-1.,d);float bend=(1.-edge)*3.;half4"
                    + " c=backdrop.eval(p+n*bend);half3"
                    + " tint=mix(half3(.1,.12,.16),half3(.98,.99,1.),half(light));c.rgb=mix(c.rgb,tint,half(.78));float"
                    + " rim=1.-smoothstep(0.,1.6,abs(d+1.));float"
                    + " shine=clamp(-n.x*.55-n.y*.75,0.,1.);c.rgb+=half3(rim*(.08+.13*shine));c.a=half(1.-smoothstep(-.8,.8,d));return"
                    + " c;}");
      } catch (Exception ignored) {
      }
  }

  void backdrop(Bitmap b) {
    backdrop = b;
    invalidateSelf();
  }

  public void draw(Canvas canvas) {
    rect.set(getBounds());
    if (rect.width() <= 0 || rect.height() <= 0) return;
    if (Build.VERSION.SDK_INT >= 33
        && lens != null
        && canvas.isHardwareAccelerated()
        && backdrop != null) {
      canvas.save();
      canvas.translate(rect.left, rect.top);
      BitmapShader sample =
          new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
      Matrix scale = new Matrix();
      scale.setScale(rect.width() / backdrop.getWidth(), rect.height() / backdrop.getHeight());
      sample.setLocalMatrix(scale);
      lens.setInputShader("backdrop", sample);
      lens.setFloatUniform("size", rect.width(), rect.height());
      lens.setFloatUniform("radius", radius);
      lens.setFloatUniform("light", light ? 1 : 0);
      paint.setShader(lens);
      canvas.drawRoundRect(0, 0, rect.width(), rect.height(), radius, radius, paint);
      paint.setShader(null);
      canvas.restore();
    } else {
      paint.setShader(
          new LinearGradient(
              0,
              rect.top,
              rect.width(),
              rect.bottom,
              light
                  ? new int[] {0xf2ffffff, 0xf2e8ecf1}
                  : new int[] {0xf2393d46, 0xf2262a32, 0xf21e2229},
              null,
              Shader.TileMode.CLAMP));
      canvas.drawRoundRect(rect, radius, radius, paint);
      paint.setShader(null);
    }
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1);
    paint.setColor(light ? 0x44ffffff : 0x40ffffff);
    canvas.drawRoundRect(
        rect.left + .5f,
        rect.top + .5f,
        rect.right - .5f,
        rect.bottom - .5f,
        radius,
        radius,
        paint);
    paint.setStyle(Paint.Style.FILL);
  }

  public void setAlpha(int a) {
    paint.setAlpha(a);
  }

  public void setColorFilter(ColorFilter f) {
    paint.setColorFilter(f);
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
