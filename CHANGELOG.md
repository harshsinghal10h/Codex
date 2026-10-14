# Changes

## 0.2.0 — 2026-10-14

- Repair the real Android live-wallpaper service: stop requesting fixed surface sizes, which Android forbids for wallpapers. Resolution limits now use a bounded internal GPU render target.
- Commit the chosen wallpaper after installation is confirmed; preserve the current choice when the picker is cancelled. Switch scenes directly when Lively is already installed, with Android picker fallbacks for the first installation.
- Fix GIF/image-to-video switching by giving each renderer its own surface wrapper and releasing its graphics producer. Keep the shared EGL display alive when a preview closes.
- Prevent activity recreation and delayed glass capture from drawing recycled bitmaps after a wallpaper change. Avoid repeating a shared-file import after recreation.
- Accept document providers with missing names or MIME metadata and normalize compatible Windows package paths. Cancelled imports roll back and cannot dismiss a later import dialog.
- Support Android 8 video imports without calling newer thumbnail APIs or decoding a full 4K thumbnail. Unsupported thumbnail extraction falls back to a library tile without rejecting the wallpaper.
- Show a first frame for paused wallpapers, then freeze it. Stop hidden playback; free hidden video/web resources immediately and hidden GPU resources after the idle timeout.
- Animate the supplied native SVG illustration, add touch feedback, and respect tap pause/resume, Reduce motion and panel visibility.
- Update web wallpaper properties without recreating the web renderer or its display.
- Add real system picker, home-screen service, GIF/WebP/video import, renderer switching, paused-frame, resource cleanup and SVG motion tests on Android 8, 13 and 15, alongside the native preview suite.

The release preserves the Library/Gallery layout and uses the same signing key as 0.1.0.
