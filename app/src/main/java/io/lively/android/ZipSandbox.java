package io.lively.android;

import java.io.*;
import java.util.HashSet;
import java.util.zip.*;

/** Streaming, bounded extraction. Nothing is published until metadata validates. */
public final class ZipSandbox {
  public static final long MAX_TOTAL = 512L * 1024 * 1024, MAX_ENTRY = 256L * 1024 * 1024;

  public static File resolve(File root, String path) throws IOException {
    if (path == null
        || path.isEmpty()
        || path.indexOf('\\') >= 0
        || path.indexOf('\0') >= 0
        || path.startsWith("/")
        || path.matches("^[A-Za-z]:.*")) throw new IOException("Invalid package path");
    File f = new File(root, path).getCanonicalFile();
    if (!f.getPath().startsWith(root.getCanonicalPath() + File.separator))
      throw new IOException("Package path escapes its folder");
    return f;
  }

  public static void extract(InputStream input, File root) throws IOException {
    root.mkdirs();
    byte[] buffer = new byte[65536];
    long total = 0;
    int count = 0;
    HashSet<String> seen = new HashSet<>();
    try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        if (++count > 2048) throw new IOException("Package has too many files");
        String name = e.getName();
        File f = resolve(root, name);
        if (!seen.add(f.getCanonicalPath().toLowerCase(java.util.Locale.ROOT)))
          throw new IOException("Duplicate package path");
        if (e.isDirectory()) {
          f.mkdirs();
          continue;
        }
        if (e.getSize() > MAX_ENTRY) throw new IOException("Package file exceeds 256 MiB");
        File parent = f.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create folder");
        long entry = 0;
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
          int n;
          while ((n = zip.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled");
            entry += n;
            total += n;
            if (entry > MAX_ENTRY || total > MAX_TOTAL)
              throw new IOException("Expanded package is too large");
            out.write(buffer, 0, n);
          }
        }
        zip.closeEntry();
      }
    }
    if (count == 0) throw new IOException("Empty wallpaper package");
  }

  public static void delete(File f) {
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      if (files != null) for (File child : files) delete(child);
    }
    f.delete();
  }
}
