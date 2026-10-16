#!/usr/bin/env bash
# Xvfb supplies a display; a window manager is also required for real fullscreen.
set -euo pipefail
openbox --sm-disable > /tmp/vlc-glass-window-manager.log 2>&1 &
wm_pid=$!
xcompmgr -a > /tmp/vlc-glass-compositor.log 2>&1 &
compositor_pid=$!
trap 'kill "$wm_pid" "$compositor_pid" 2>/dev/null || true' EXIT
ready=false
for ((attempt=0; attempt<50; attempt++)); do
  if xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id'; then
    ready=true
    break
  fi
  sleep 0.1
done
if [ "$ready" != true ]; then
  cat /tmp/vlc-glass-window-manager.log
  exit 1
fi
"$@"
