package io.lively.android;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;

final class UiMotion {
  static boolean enabled(View v) {
    return ValueAnimator.areAnimatorsEnabled()
        && !Settings.of(v.getContext()).getBoolean("reduce_motion", false);
  }

  static void press(View view) {
    view.setOnTouchListener(
        (v, event) -> {
          int action = event.getActionMasked();
          if (action == MotionEvent.ACTION_DOWN
              || action == MotionEvent.ACTION_UP
              || action == MotionEvent.ACTION_CANCEL) {
            boolean down = action == MotionEvent.ACTION_DOWN;
            v.animate().cancel();
            float scale = down && enabled(v) ? .96f : 1;
            if (enabled(v))
              v.animate().scaleX(scale).scaleY(scale).setDuration(down ? 90 : 160).start();
            else {
              v.setScaleX(1);
              v.setScaleY(1);
            }
          }
          return false;
        });
  }
}
