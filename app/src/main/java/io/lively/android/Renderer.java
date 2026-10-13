package io.lively.android;

interface Renderer {
  void start();

  void pause();

  void release();

  void touch(float x, float y);

  void configure();
}
