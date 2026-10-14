# Release validation

Release: Lively Android 0.3.0, Android 8.0+ (minimum API 26, target API 35), version code 3.

The published APK is 333,282 bytes. SHA-256:

```text
d1f9f9328602465f34b1bd7b8048493ec70b9e668c32b52a4a46e162fa73fd61
```

The APK passes zip alignment and Android APK signature v2/v3 verification. Its signing certificate matches the published 0.1.0 and 0.2.0 APKs, so it can update those installations. The independent release key is kept outside this repository; the separate private signing backup is required to sign compatible updates.

The release application source was last changed at `f8b1d476943894cba21d122dff1fa2e6d6973fae`. The completed build and runtime checks below ran at `7d9b173612cf032d60aaf8e2eed90c5dfd4dc646`, which adjusts test matching for Android's uppercase dialog buttons without changing application code. The subsequent release commit adds documentation and this independently signed APK.

| Check | Result | Evidence |
|---|---|---|
| Core policy/package tests | 40 assertions passed | `python3 tools/test-core.py`; also included in the Android build workflow |
| Android build | Passed | [Build run](https://github.com/harshsinghal10h/Codex/actions/runs/38574281423) |
| Native UI/preview suite, Android 15 | 51 assertions passed | [Native QA](https://github.com/harshsinghal10h/Codex/actions/runs/38574281376) |
| Real wallpaper flows, Android 8 / API 26 | 55 assertions passed | [Wallpaper matrix](https://github.com/harshsinghal10h/Codex/actions/runs/38574281525) |
| Real wallpaper flows, Android 13 / API 33 | 57 assertions passed | [Wallpaper matrix](https://github.com/harshsinghal10h/Codex/actions/runs/38574281525) |
| Real wallpaper flows, Android 15 / API 35 | 57 assertions passed | [Wallpaper matrix](https://github.com/harshsinghal10h/Codex/actions/runs/38574281525) |

The core suite covers visibility, power/thermal rules, a disabled low-battery threshold, bounded FPS/resolution preferences, correct 720p and 1080p dimensions, missing provider metadata, MIME aliases, ZIP traversal/collision/size limits, unsupported executable types and extraction rollback.

The native suite covers original image bytes and Ultra HDR gain-map preservation, import/export, all eight GPU scenes, stopping hidden playback, web presentation, live web property changes without renderer reload, image preview, original 3840-pixel video decoding, inactive preview controls, unobscured last-row scrolling, stable glass opacity in both themes and complete secondary-button touch bounds.

The quality UI checks open and cancel the actual native selector, verify cancellation preserves both settings, select 1080p/60, confirm resolution and FPS apply together, recreate the activity while retaining quality and its settings category, and choose custom FPS while preserving scroll position.

The system wallpaper suite opens Android's actual picker, cancels and confirms installation, verifies the installed home-screen service, switches scenes directly, tests capped rendering on the full system surface, reopens previews while preserving other EGL contexts, verifies a paused first frame, imports GIF/WebP/video through a real content provider, switches image-to-video producers, decodes the original 4K video on the home screen, verifies hidden decoder release, checks native SVG motion/pause/resume/Reduce motion/dismissal, and tests idle GPU release and recreation. Android 8 uses its GIF fallback and static WebP; the two additional assertions on newer systems cover animated WebP.

Additional real-service checks apply 1080p/60 to the running renderer, resize its internal target to the 1920-pixel cap without replacing the installed renderer, then restore a custom lower resolution and FPS without restarting the wallpaper. Tests verify requested settings and ongoing presentation; they do not guarantee sustained 60 FPS on every device or source type.

The native suite builds a separate test APK and uses a temporary CI signing key for both test artifacts. The shipped APK contains no instrumentation. Runtime coverage therefore validates the same application source; the distributed release signature is verified separately.

Version 0.1.0's preview tests did not exercise Android's real wallpaper service and missed a forbidden fixed-surface-size call. Version 0.2.0 removes that call and tests the installed service on all three Android versions. Other reproduced defects fixed during these tests included wallpaper-triggered activity recreation drawing a recycled glass bitmap, image-to-video surface ownership, and a newer thumbnail API being called on Android 8.

Screenshots are emulator captures. Tests wait for Android's destination dialog and visibility callbacks rather than assuming system transitions finish after a fixed delay. The final three-version matrix passes without accumulated renderer errors.

Physical-device HDR output, manufacturer home/lock-screen restrictions, battery drain and whole-device memory consumption remain unmeasured. The app's memory chart reports its process PSS; decoder and WebView child processes are separate. Emulator screenshots do not establish HDR display output.

See [Compatibility](COMPATIBILITY.md) for Windows engine and gallery features that are not included in this independent Android adaptation.

