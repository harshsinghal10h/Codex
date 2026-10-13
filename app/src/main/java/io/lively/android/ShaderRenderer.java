package io.lively.android;

import android.content.Context;
import android.opengl.*;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.nio.*;

final class ShaderRenderer implements Renderer {
  private final Context context;
  private final Wallpaper wallpaper;
  private final Surface surface;
  private final int width, height;
  private final HandlerThread thread =
      new HandlerThread("Lively-GL", android.os.Process.THREAD_PRIORITY_DISPLAY);
  private final Handler handler;
  private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
  private EGLContext egl = EGL14.EGL_NO_CONTEXT;
  private EGLSurface window = EGL14.EGL_NO_SURFACE;
  private int program;
  private final FloatBuffer quad;
  private volatile boolean released, running;
  private volatile float touchX = .5f, touchY = .5f, speed = 1, intensity = 1;
  private volatile int fps = 30;
  private long elapsed, startTime;
  private int timeLoc, resLoc, touchLoc, intensityLoc, accentLoc;

  ShaderRenderer(Context c, Wallpaper w, Surface s, int width, int height) {
    context = c;
    wallpaper = w;
    surface = s;
    this.width = width;
    this.height = height;
    quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    quad.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    thread.start();
    handler = new Handler(thread.getLooper());
    configure();
  }

  private void init() throws Exception {
    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    int[] ver = new int[2];
    if (!EGL14.eglInitialize(display, ver, 0, ver, 1))
      throw new IOException("Cannot initialise OpenGL");
    int[] attrs = {
      EGL14.EGL_RENDERABLE_TYPE,
      EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_RED_SIZE,
      8,
      EGL14.EGL_GREEN_SIZE,
      8,
      EGL14.EGL_BLUE_SIZE,
      8,
      EGL14.EGL_ALPHA_SIZE,
      8,
      EGL14.EGL_NONE
    };
    EGLConfig[] configs = new EGLConfig[1];
    int[] count = new int[1];
    EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0);
    if (count[0] == 0) throw new IOException("No GPU configuration");
    egl =
        EGL14.eglCreateContext(
            display,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE},
            0);
    window =
        EGL14.eglCreateWindowSurface(display, configs[0], surface, new int[] {EGL14.EGL_NONE}, 0);
    if (window == EGL14.EGL_NO_SURFACE) throw new IOException("Wallpaper surface is unavailable");
    EGL14.eglMakeCurrent(display, window, window, egl);
    EGL14.eglSwapInterval(display, 1);
    String frag;
    try (InputStream in = context.getAssets().open("scene.frag")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
      frag = out.toString("UTF-8");
    }
    int
        v =
            shader(
                GLES20.GL_VERTEX_SHADER,
                "attribute vec2 p;void main(){gl_Position=vec4(p,0.,1.);}"),
        f = shader(GLES20.GL_FRAGMENT_SHADER, frag);
    program = GLES20.glCreateProgram();
    GLES20.glAttachShader(program, v);
    GLES20.glAttachShader(program, f);
    GLES20.glLinkProgram(program);
    int[] ok = new int[1];
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
    if (ok[0] == 0) throw new IOException(GLES20.glGetProgramInfoLog(program));
    GLES20.glDeleteShader(v);
    GLES20.glDeleteShader(f);
    GLES20.glUseProgram(program);
    int pos = GLES20.glGetAttribLocation(program, "p");
    GLES20.glEnableVertexAttribArray(pos);
    GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad);
    GLES20.glViewport(0, 0, width, height);
    timeLoc = GLES20.glGetUniformLocation(program, "uTime");
    resLoc = GLES20.glGetUniformLocation(program, "uResolution");
    touchLoc = GLES20.glGetUniformLocation(program, "uTouch");
    intensityLoc = GLES20.glGetUniformLocation(program, "uIntensity");
    accentLoc = GLES20.glGetUniformLocation(program, "uAccent");
    GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uScene"), wallpaper.scene);
    GLES20.glUniform2f(resLoc, width, height);
  }

  private int shader(int type, String source) throws IOException {
    int s = GLES20.glCreateShader(type);
    GLES20.glShaderSource(s, source);
    GLES20.glCompileShader(s);
    int[] ok = new int[1];
    GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
    if (ok[0] == 0) {
      String error = GLES20.glGetShaderInfoLog(s);
      GLES20.glDeleteShader(s);
      throw new IOException(error);
    }
    return s;
  }

  private final Runnable frame =
      new Runnable() {
        public void run() {
          if (released || !running) return;
          long frameStarted = SystemClock.uptimeMillis();
          try {
            if (program == 0) init();
            float t = (elapsed + SystemClock.elapsedRealtime() - startTime) * .001f * speed;
            GLES20.glUniform1f(timeLoc, t);
            GLES20.glUniform2f(touchLoc, touchX, 1 - touchY);
            GLES20.glUniform1f(intensityLoc, intensity);
            int color = Settings.of(context).getInt(wallpaper.id + ".accent", 0xff59a8fa);
            GLES20.glUniform3f(
                accentLoc,
                ((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f,
                (color & 255) / 255f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            if (!EGL14.eglSwapBuffers(display, window))
              throw new IOException("Wallpaper surface closed");
            Metrics.frame(context);
            handler.postDelayed(
                this, Math.max(1, 1000 / fps - (SystemClock.uptimeMillis() - frameStarted)));
          } catch (Exception e) {
            running = false;
            Metrics.error(context, "Scene renderer: " + e.getMessage());
            cleanup();
          }
        }
      };

  public void start() {
    handler.post(
        () -> {
          if (released || running) return;
          running = true;
          startTime = SystemClock.elapsedRealtime();
          handler.post(frame);
        });
  }

  public void pause() {
    handler.post(
        () -> {
          if (running) elapsed += SystemClock.elapsedRealtime() - startTime;
          running = false;
          handler.removeCallbacks(frame);
        });
  }

  public void configure() {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    fps =
        EnginePolicy.frameRate(
            Settings.fps(context),
            pm.isPowerSaveMode(),
            Build.VERSION.SDK_INT >= 29
                && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE);
    speed = Settings.value(context, wallpaper, "speed", 1);
    intensity = Settings.value(context, wallpaper, "intensity", 1);
  }

  public void touch(float x, float y) {
    touchX = x;
    touchY = y;
  }

  private void cleanup() {
    if (display != EGL14.EGL_NO_DISPLAY) {
      if (program != 0) GLES20.glDeleteProgram(program);
      EGL14.eglMakeCurrent(
          display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
      if (egl != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, egl);
      EGL14.eglTerminate(display);
      EGL14.eglReleaseThread();
    }
    display = EGL14.EGL_NO_DISPLAY;
    program = 0;
  }

  public void release() {
    if (released) return;
    released = true;
    java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
    handler.post(
        () -> {
          try {
            running = false;
            handler.removeCallbacks(frame);
            cleanup();
          } finally {
            closed.countDown();
            thread.quitSafely();
          }
        });
    try {
      closed.await(800, java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
