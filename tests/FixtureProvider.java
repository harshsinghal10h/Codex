package io.lively.android;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Test-APK-only provider for real SAF-style URI imports, including incomplete metadata. */
public final class FixtureProvider extends ContentProvider {
  public boolean onCreate() {
    return true;
  }

  private String asset(Uri uri) throws FileNotFoundException {
    switch (uri.getLastPathSegment()) {
      case "gif":
        return "motion.gif";
      case "webp":
        return "motion.webp";
      case "no-name":
        return "sample.png";
      case "video":
        return "video-4k.mp4";
      default:
        throw new FileNotFoundException("Unknown test fixture");
    }
  }

  public String getType(Uri uri) {
    if ("gif".equals(uri.getLastPathSegment())) return "image/gif";
    if ("webp".equals(uri.getLastPathSegment())) return "image/webp";
    if ("video".equals(uri.getLastPathSegment())) return "video/mp4";
    return null;
  }

  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    if ("no-name".equals(uri.getLastPathSegment()))
      throw new UnsupportedOperationException("No display metadata");
    MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
    cursor.addRow(new Object[] {"video".equals(uri.getLastPathSegment()) ? "Shared video" : null});
    return cursor;
  }

  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read-only fixture");
    String name = asset(uri);
    try {
      ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
      new Thread(
              () -> {
                try (InputStream in = getContext().getAssets().open(name);
                    OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                  byte[] buffer = new byte[8192];
                  int n;
                  while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                } catch (IOException ignored) {
                }
              },
              "Lively-QA-fixture")
          .start();
      return pipe[0];
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
  }

  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
