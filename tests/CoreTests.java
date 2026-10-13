package io.lively.android;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public final class CoreTests {
  static int count;

  static void expect(boolean condition, String label) {
    count++;
    if (!condition) throw new AssertionError(label);
  }

  static void reject(RunnableIo task, String label) throws Exception {
    count++;
    try {
      task.run();
      throw new AssertionError(label);
    } catch (IOException expected) {
    }
  }

  interface RunnableIo {
    void run() throws IOException;
  }

  static byte[] zip(String path, byte[] bytes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      z.putNextEntry(new ZipEntry(path));
      z.write(bytes);
      z.closeEntry();
    }
    return out.toByteArray();
  }

  public static void main(String[] args) throws Exception {
    expect(
        EnginePolicy.shouldRun(true, true, false, false, true, false, false, 80, 15, false, true),
        "Normal visible playback");
    expect(
        !EnginePolicy.shouldRun(false, true, false, false, true, false, false, 80, 15, false, true),
        "No work while hidden");
    expect(
        !EnginePolicy.shouldRun(true, false, false, false, true, false, false, 80, 15, false, true),
        "Screen-off pause");
    expect(
        !EnginePolicy.shouldRun(true, true, true, false, true, false, false, 80, 15, false, true),
        "Manual pause");
    expect(
        !EnginePolicy.shouldRun(true, true, false, true, true, false, false, 80, 15, false, true),
        "Battery saver");
    expect(
        EnginePolicy.shouldRun(true, true, false, true, false, false, false, 80, 15, false, true),
        "Saver opt out");
    expect(
        !EnginePolicy.shouldRun(true, true, false, false, true, false, true, 80, 15, false, true),
        "Battery power rule");
    expect(
        EnginePolicy.shouldRun(true, true, false, false, true, true, true, 5, 15, false, true),
        "Charging overrides low battery");
    expect(
        !EnginePolicy.shouldRun(true, true, false, false, true, false, false, 10, 15, false, true),
        "Low battery threshold");
    expect(
        !EnginePolicy.shouldRun(true, true, false, false, true, false, false, 80, 15, true, true),
        "Thermal pause");
    expect(
        EnginePolicy.shouldRun(true, true, false, false, true, false, false, 80, 15, true, false),
        "Thermal opt out");
    expect(EnginePolicy.frameRate(60, true, false) == 24, "Saver frame cap");
    expect(EnginePolicy.frameRate(60, false, true) == 24, "Thermal frame cap");
    int[] d = EnginePolicy.dimensions(2160, 3840, 1920);
    expect(d[0] == 1080 && d[1] == 1920, "Portrait aspect and cap");
    d = EnginePolicy.dimensions(3840, 2160, 3840);
    expect(d[0] == 3840 && d[1] == 2160, "4K retained");
    d = EnginePolicy.dimensions(640, 360, 3840);
    expect(d[0] == 640 && d[1] == 360, "No pointless upscaling");
    d = EnginePolicy.dimensions(0, 0, 0);
    expect(d[0] > 0 && d[1] > 0, "Invalid dimensions bounded");
    expect(EnginePolicy.kind("MOVIE.MP4").equals("video"), "Case-insensitive video");
    expect(EnginePolicy.kind("game.exe").isEmpty(), "No executable loading");
    File root = Files.createTempDirectory("lively-sandbox").toFile();
    reject(() -> ZipSandbox.resolve(root, "../escape"), "Traversal");
    reject(() -> ZipSandbox.resolve(root, "/absolute"), "Absolute path");
    reject(() -> ZipSandbox.resolve(root, "C:/absolute"), "Drive path");
    reject(() -> ZipSandbox.resolve(root, "a\\b"), "Backslash traversal");
    reject(
        () -> ZipSandbox.extract(new ByteArrayInputStream(zip("../escape", new byte[] {1})), root),
        "Zip traversal");
    ZipSandbox.extract(
        new ByteArrayInputStream(zip("assets/scene.js", new byte[] {1, 2, 3})), root);
    expect(new File(root, "assets/scene.js").length() == 3, "Valid package assets");
    ByteArrayOutputStream dup = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(dup)) {
      z.putNextEntry(new ZipEntry("A.txt"));
      z.write(1);
      z.closeEntry();
      z.putNextEntry(new ZipEntry("a.txt"));
      z.write(2);
      z.closeEntry();
    }
    reject(
        () -> ZipSandbox.extract(new ByteArrayInputStream(dup.toByteArray()), root),
        "Case-fold collision");
    ByteArrayOutputStream many = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(many)) {
      for (int i = 0; i < 2049; i++) {
        z.putNextEntry(new ZipEntry("count/" + i));
        z.closeEntry();
      }
    }
    reject(
        () -> ZipSandbox.extract(new ByteArrayInputStream(many.toByteArray()), root),
        "Entry-count bomb");
    ZipSandbox.delete(root);
    expect(!root.exists(), "Rollback cleanup");
    java.lang.System.out.println("PASS: " + count + " policy and package assertions");
  }
}
