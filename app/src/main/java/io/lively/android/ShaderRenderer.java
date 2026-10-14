package io.lively.android;

import android.content.Context;
import android.opengl.*;
import android.os.*;
import android.view.Choreographer;
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
  private int blitProgram, targetTexture, targetBuffer, targetWidth, targetHeight;
  private int outputWidth, outputHeight;
  private boolean scaledFrame;
  private volatile int cap;
  private volatile long renderedFrames;
  private volatile String failure = "";
  private final FloatBuffer quad;
  private volatile boolean released, running;
  private volatile float touchX = .5f, touchY = .5f, speed = 1, intensity = 1;
  private volatile int fps = 30;
  private volatile int accent = 0xff59a8fa;
  private Choreographer choreographer;
  private long nextFrameNanos;
  private long elapsed, startTime;
  private int timeLoc, resLoc, touchLoc, intensityLoc, accentLoc;

  ShaderRenderer(Context c, Wallpaper w, Surface s, int width, int height) {
    context = c;
    wallpaper = w;
    surface = SurfaceLease.copy(s);
    this.width = width;
    this.height = height;
    quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    quad.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    thread.start();
    handler = new Handler(thread.getLooper());
    configure();
  }

  private void init() throws Exception {
    display = EglRuntime.acquire();
    int[] attrs = {
      EGL14.EGL_RENDERABLE_TYPE,
      EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_SURFACE_TYPE,
      EGL14.EGL_WINDOW_BIT,
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
    if (egl == EGL14.EGL_NO_CONTEXT || !EGL14.eglMakeCurrent(display, window, window, egl))
      throw new IOException("Cannot attach the scene's GPU context");
    int[] actual = new int[1];
    EGL14.eglQuerySurface(display, window, EGL14.EGL_WIDTH, actual, 0);
    outputWidth = Math.max(1, actual[0]);
    EGL14.eglQuerySurface(display, window, EGL14.EGL_HEIGHT, actual, 0);
    outputHeight = Math.max(1, actual[0]);
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

  private void bindQuad(int p) {
    GLES20.glUseProgram(p);
    int pos = GLES20.glGetAttribLocation(p, "p");
    GLES20.glEnableVertexAttribArray(pos);
    quad.position(0);
    GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad);
  }

  private void renderTarget() throws IOException {
    int[] dims = EnginePolicy.dimensions(outputWidth, outputHeight, cap);
    scaledFrame = dims[0] != outputWidth || dims[1] != outputHeight;
    if (dims[0] == outputWidth && dims[1] == outputHeight) {
      deleteTarget();
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
      GLES20.glViewport(0, 0, outputWidth, outputHeight);
      bindQuad(program);
      GLES20.glUniform2f(resLoc, outputWidth, outputHeight);
      return;
    }
    if (blitProgram == 0) {
      int v =
          shader(
              GLES20.GL_VERTEX_SHADER,
              "attribute vec2 p;varying vec2 uv;void"
                  + " main(){uv=p*.5+.5;gl_Position=vec4(p,0.,1.);}");
      int f =
          shader(
              GLES20.GL_FRAGMENT_SHADER,
              "precision mediump float;varying vec2 uv;uniform sampler2D image;void"
                  + " main(){gl_FragColor=texture2D(image,uv);}");
      blitProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(blitProgram, v);
      GLES20.glAttachShader(blitProgram, f);
      GLES20.glLinkProgram(blitProgram);
      int[] ok = new int[1];
      GLES20.glGetProgramiv(blitProgram, GLES20.GL_LINK_STATUS, ok, 0);
      GLES20.glDeleteShader(v);
      GLES20.glDeleteShader(f);
      if (ok[0] == 0) throw new IOException("Cannot scale the scene render target");
    }
    if (targetWidth != dims[0] || targetHeight != dims[1]) {
      deleteTarget();
      int[] id = new int[1];
      GLES20.glGenTextures(1, id, 0);
      targetTexture = id[0];
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTexture);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          dims[0],
          dims[1],
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          null);
      GLES20.glGenFramebuffers(1, id, 0);
      targetBuffer = id[0];
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetBuffer);
      GLES20.glFramebufferTexture2D(
          GLES20.GL_FRAMEBUFFER,
          GLES20.GL_COLOR_ATTACHMENT0,
          GLES20.GL_TEXTURE_2D,
          targetTexture,
          0);
      if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE)
        throw new IOException("Cannot allocate the requested scene resolution");
      targetWidth = dims[0];
      targetHeight = dims[1];
    }
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetBuffer);
    GLES20.glViewport(0, 0, targetWidth, targetHeight);
    bindQuad(program);
    GLES20.glUniform2f(resLoc, targetWidth, targetHeight);
  }

  private void present() {
    if (!scaledFrame) return;
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GLES20.glViewport(0, 0, outputWidth, outputHeight);
    bindQuad(blitProgram);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, targetTexture);
    GLES20.glUniform1i(GLES20.glGetUniformLocation(blitProgram, "image"), 0);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
  }

  private void deleteTarget() {
    if (targetBuffer != 0) GLES20.glDeleteFramebuffers(1, new int[] {targetBuffer}, 0);
    if (targetTexture != 0) GLES20.glDeleteTextures(1, new int[] {targetTexture}, 0);
    targetTexture = targetBuffer = targetWidth = targetHeight = 0;
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

  private final Choreographer.FrameCallback frame =
      new Choreographer.FrameCallback() {
        public void doFrame(long frameTimeNanos) {
          if (released || !running) return;
          if (nextFrameNanos != 0 && frameTimeNanos + 1000000L < nextFrameNanos) {
            scheduleFrame();
            return;
          }
          long interval = 1000000000L / fps;
          if (nextFrameNanos == 0 || frameTimeNanos - nextFrameNanos > interval * 2)
            nextFrameNanos = frameTimeNanos + interval;
          else nextFrameNanos += interval;
          try {
            if (program == 0) init();
            renderTarget();
            float t = (elapsed + SystemClock.elapsedRealtime() - startTime) * .001f * speed;
            GLES20.glUniform1f(timeLoc, t);
            GLES20.glUniform2f(touchLoc, touchX, 1 - touchY);
            GLES20.glUniform1f(intensityLoc, intensity);
            int color = accent;
            GLES20.glUniform3f(
                accentLoc,
                ((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f,
                (color & 255) / 255f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            present();
            if (!EGL14.eglSwapBuffers(display, window))
              throw new IOException("Wallpaper surface closed");
            Metrics.frame(context);
            renderedFrames++;
            scheduleFrame();
          } catch (Exception e) {
            running = false;
            if (!released && surface.isValid()) {
              failure = "Scene renderer: " + e.getMessage();
              Metrics.error(context, failure);
            }
            cleanup();
          }
        }
      };

  private void scheduleFrame() {
    if (released || !running) return;
    // Align presentation to display vsync while retaining phase for 24/30/60 FPS.
    long delay = Math.max(0, (nextFrameNanos - System.nanoTime()) / 1000000L - 2);
    choreographer.postFrameCallbackDelayed(frame, delay);
  }

  private void frameHint(float rate) {
    if (Build.VERSION.SDK_INT >= 30 && surface.isValid())
      try {
        surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
      } catch (Exception ignored) {
      }
  }

  public void start() {
    handler.post(
        () -> {
          if (released || running) return;
          running = true;
          startTime = SystemClock.elapsedRealtime();
          nextFrameNanos = 0;
          if (choreographer == null) choreographer = Choreographer.getInstance();
          frameHint(fps);
          choreographer.postFrameCallback(frame);
        });
  }

  public void pause() {
    handler.post(
        () -> {
          if (running) elapsed += SystemClock.elapsedRealtime() - startTime;
          running = false;
          if (choreographer != null) choreographer.removeFrameCallback(frame);
          frameHint(0);
        });
  }

  public void configure() {
    cap = Settings.cap(context);
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    int previousRate = fps;
    fps =
        EnginePolicy.frameRate(
            Settings.fps(context),
            pm.isPowerSaveMode(),
            Build.VERSION.SDK_INT >= 29
                && pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE);
    speed = Settings.value(context, wallpaper, "speed", 1);
    intensity = Settings.value(context, wallpaper, "intensity", 1);
    accent = Settings.of(context).getInt(wallpaper.id + ".accent", 0xff59a8fa);
    handler.post(
        () -> {
          if (released || !running) return;
          frameHint(fps);
          if (previousRate != fps && choreographer != null) {
            nextFrameNanos = 0;
            choreographer.removeFrameCallback(frame);
            choreographer.postFrameCallback(frame);
          }
        });
  }

  public void touch(float x, float y) {
    touchX = x;
    touchY = y;
  }

  private void cleanup() {
    if (display != EGL14.EGL_NO_DISPLAY) {
      deleteTarget();
      if (blitProgram != 0) GLES20.glDeleteProgram(blitProgram);
      if (program != 0) GLES20.glDeleteProgram(program);
      EGL14.eglMakeCurrent(
          display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
      if (egl != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, egl);
      EglRuntime.release();
      EGL14.eglReleaseThread();
    }
    display = EGL14.EGL_NO_DISPLAY;
    program = 0;
    blitProgram = 0;
  }

  public void release() {
    if (released) return;
    released = true;
    java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
    handler.post(
        () -> {
          try {
            running = false;
            if (choreographer != null) choreographer.removeFrameCallback(frame);
            frameHint(0);
            cleanup();
          } finally {
            surface.release();
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

  public boolean failed() {
    return released || !failure.isEmpty();
  }

  public boolean ready() {
    return renderedFrames > 0;
  }

  public String error() {
    return failure;
  }
}
