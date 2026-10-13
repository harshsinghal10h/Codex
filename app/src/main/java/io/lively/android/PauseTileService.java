package io.lively.android;

import android.service.quicksettings.*;

public final class PauseTileService extends TileService {
  public void onStartListening() {
    update();
  }

  public void onClick() {
    Settings.of(this)
        .edit()
        .putBoolean("paused", !Settings.of(this).getBoolean("paused", false))
        .apply();
    update();
  }

  private void update() {
    Tile t = getQsTile();
    if (t != null) {
      boolean paused = Settings.of(this).getBoolean("paused", false);
      t.setState(paused ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
      t.setLabel(paused ? "Lively paused" : "Lively playing");
      t.updateTile();
    }
  }
}
