package io.lively.android;

import android.os.Parcel;
import android.view.Surface;

/** Own a Java Surface wrapper without taking ownership of the framework's holder. */
final class SurfaceLease {
  static Surface copy(Surface source) {
    Parcel parcel = Parcel.obtain();
    try {
      source.writeToParcel(parcel, 0);
      parcel.setDataPosition(0);
      return Surface.CREATOR.createFromParcel(parcel);
    } finally {
      parcel.recycle();
    }
  }
}
