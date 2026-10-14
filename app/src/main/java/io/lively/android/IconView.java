package io.lively.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class IconView extends View {
  private final Paint p = new Paint(3);
  private final Path path = new Path();
  private final String icon;
  private final int color;

  IconView(Context c, String icon, int color) {
    super(c);
    this.icon = icon;
    this.color = color;
    setContentDescription(icon);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    float size = Math.min(getWidth(), getHeight()) * .46f;
    c.translate((getWidth() - size) * .5f, (getHeight() - size) * .5f);
    c.scale(size / 24, size / 24);
    p.setColor(color);
    p.setStrokeWidth(1.7f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    switch (icon) {
      case "add":
        c.drawLine(12, 4, 12, 20, p);
        c.drawLine(4, 12, 20, 12, p);
        break;
      case "search":
        c.drawCircle(10, 10, 6.5f, p);
        c.drawLine(15, 15, 21, 21, p);
        break;
      case "display":
        c.drawRoundRect(3, 3, 21, 16, 2, 2, p);
        c.drawLine(12, 16, 12, 21, p);
        c.drawLine(7, 21, 17, 21, p);
        break;
      case "back":
        path.reset();
        path.moveTo(14, 4);
        path.lineTo(6, 12);
        path.lineTo(14, 20);
        c.drawPath(path, p);
        break;
      case "more":
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(12, 5, 1.5f, p);
        c.drawCircle(12, 12, 1.5f, p);
        c.drawCircle(12, 19, 1.5f, p);
        break;
      case "play":
        path.reset();
        path.moveTo(7, 4);
        path.lineTo(20, 12);
        path.lineTo(7, 20);
        path.close();
        c.drawPath(path, p);
        break;
      case "pause":
        c.drawLine(8, 5, 8, 19, p);
        c.drawLine(16, 5, 16, 19, p);
        break;
      case "star":
        path.reset();
        for (int i = 0; i < 10; i++) {
          double a = Math.PI * i / 5 - Math.PI / 2;
          float r = i % 2 == 0 ? 9 : 4.2f;
          float x = 12 + (float) Math.cos(a) * r, y = 12 + (float) Math.sin(a) * r;
          if (i == 0) path.moveTo(x, y);
          else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, p);
        break;
      case "check":
        path.reset();
        path.moveTo(4, 12);
        path.lineTo(9, 17);
        path.lineTo(20, 6);
        c.drawPath(path, p);
        break;
      default:
        c.drawCircle(12, 12, 4, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          float x = (float) Math.cos(a), y = (float) Math.sin(a);
          c.drawLine(12 + x * 8, 12 + y * 8, 12 + x * 10, 12 + y * 10, p);
        }
        c.drawCircle(12, 12, 8, p);
    }
    c.restore();
  }
}
