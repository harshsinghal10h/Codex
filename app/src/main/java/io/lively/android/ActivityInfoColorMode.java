package io.lively.android;

import android.content.Context;
import android.view.*;

final class ActivityInfoColorMode {
  static int hdr(Context c) {
    Display d = ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    return android.os.Build.VERSION.SDK_INT >= 26 && d.isHdr()
        ? android.content.pm.ActivityInfo.COLOR_MODE_HDR
        : android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT;
  }
}
