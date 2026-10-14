package io.lively.android;

import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import java.io.IOException;

/** Preview and home-screen engines share a display, but own separate EGL contexts. */
final class EglRuntime {
  private static EGLDisplay display = EGL14.EGL_NO_DISPLAY;
  private static int clients;

  static synchronized EGLDisplay acquire() throws IOException {
    if (clients == 0) {
      display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
      int[] version = new int[2];
      if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
        display = EGL14.EGL_NO_DISPLAY;
        throw new IOException("Cannot initialise OpenGL");
      }
    }
    clients++;
    return display;
  }

  static synchronized void release() {
    if (clients <= 0) return;
    if (--clients == 0) {
      EGL14.eglTerminate(display);
      display = EGL14.EGL_NO_DISPLAY;
    }
  }
}
