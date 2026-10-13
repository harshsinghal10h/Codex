package io.lively.android;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class ShareProvider extends ContentProvider {
  public boolean onCreate() {
    return true;
  }

  private File resolve(Uri uri) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if (name == null || !name.matches("[a-zA-Z0-9-]+\\.lively\\.zip"))
      throw new FileNotFoundException();
    File root = new File(getContext().getCacheDir(), "exports");
    try {
      File f = ZipSandbox.resolve(root, name);
      if (!f.isFile()) throw new FileNotFoundException();
      return f;
    } catch (IOException e) {
      throw new FileNotFoundException();
    }
  }

  public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(resolve(u), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  public String getType(Uri u) {
    return "application/zip";
  }

  public Cursor query(Uri u, String[] projection, String selection, String[] args, String order) {
    try {
      File f = resolve(u);
      MatrixCursor c =
          new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
      c.addRow(new Object[] {f.getName(), f.length()});
      return c;
    } catch (Exception e) {
      return null;
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
