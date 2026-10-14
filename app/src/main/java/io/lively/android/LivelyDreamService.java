package io.lively.android;

import android.service.dreams.DreamService;
import android.view.*;

public final class LivelyDreamService extends DreamService implements SurfaceHolder.Callback {
  private RendererController controller;
  private boolean active;

  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    setInteractive(false);
    setFullscreen(true);
    setScreenBright(false);
    SurfaceView view = new SurfaceView(this);
    view.getHolder().addCallback(this);
    setContentView(view);
  }

  public void onDreamingStarted() {
    active = true;
    if (controller != null) controller.visible(true);
  }

  public void onDreamingStopped() {
    active = false;
    if (controller != null) controller.visible(false);
  }

  public void surfaceCreated(SurfaceHolder holder) {}

  public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {
    if (controller != null) controller.close();
    controller =
        new RendererController(
            this,
            new LibraryStore(this).find(Settings.active(this)),
            h.getSurface(),
            width,
            height,
            true);
    controller.visible(active);
  }

  public void surfaceDestroyed(SurfaceHolder h) {
    if (controller != null) {
      controller.close();
      controller = null;
    }
  }

  public void onDetachedFromWindow() {
    if (controller != null) controller.close();
    super.onDetachedFromWindow();
  }
}
