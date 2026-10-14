# Android compatibility

| Lively feature | Android build |
|---|---|
| Library, Gallery, search, import, active wallpaper controls | Responsive native layout. Gallery contains the included Android scenes, not the Windows online account service. |
| Video | Original file played by Android's codec stack. MP4/H.264 is the broadest baseline; HEVC, WebM, AV1 and HDR profiles depend on device decoders. No automatic transcoding. |
| 4K | Native sources up to 3840×2160 and a 3840-pixel render cap. The phone's screen remains its physical resolution. 4K increases GPU and decoder memory use. |
| Ultra HDR images | Original gain-map JPEG is preserved, native HDR preview on Android 14+, and original static image application through WallpaperManager. Live wallpaper HDR output depends on the Android compositor and device. |
| GIF / animated WebP | Native ImageDecoder / AnimatedImageDrawable on Android 9+. Android 8 uses Movie for GIF; very large GIFs are rejected on that fallback. Animated WebP appears as a static image on Android 8. |
| Web / WebGL / HTML packages | Android System WebView on a private virtual display. Full wallpaper behavior depends on Android WebView support, the package's WebGL requirements and the device's compositor. |
| LivelyProperties | Slider, checkbox, dropdown, textbox and colour values. File selectors, buttons needing Windows APIs and arbitrary desktop process calls are not implemented. |
| Audio / desktop visualisers | Video audio is available, muted by default. Capturing other apps' audio, reading their music sessions or desktop hardware telemetry is not implemented. |
| Shadertoy | Included native GPU scenes are supported. Direct Shadertoy URL conversion / API credentials are not implemented; an exported self-contained WebGL page can be imported if Android WebView supports it. |
| Application / Unity / Godot / BizHawk wallpapers | Windows executables are rejected. Android would require a separately implemented and licensed runtime for each engine. |
| Performance rules | Pause when hidden, screen off, manually paused, Battery Saver, low battery, battery power or severe heat. Foreground Windows app rules map to wallpaper visibility on Android. |
| Quality presets | Settings → Performance offers 720p/30, 1080p/30 or 60, 1440p/30 or 60, and 4K UHD/30 or 60. Resolution and FPS apply together. Names use conventional 16:9 sizes; other aspect ratios are preserved within the maximum edge. No rendering beyond the surface's native size. |
| Frame rate | Native scenes use display-synchronized pacing and live surface hints. FPS is a target subject to display refresh, GPU load, Battery Saver and thermal limits. Videos retain source timing; GIFs and web pages keep their native animation timing. Android 8's GIF fallback is sampled at the requested rate. |
| Fit | Images: crop, fit, stretch. Video: crop and fit. Web: its own responsive page. |
| Rotation | Cycles favourites, or the library when fewer than two favourites exist. Evaluated only while visible; no hidden alarms or wake locks. |
| Screensavers | Android DreamService, on devices which expose screensaver settings. |
| Multiple monitors / span / per-monitor placement | Android single-display live wallpaper target. Windows monitor spanning and placement are not applicable. |
| Home / lock screen | Android's native live wallpaper picker determines available destinations. Static images can be applied explicitly to home, lock or both. Manufacturer restrictions can differ. |
| Tray / command-line / remote desktop rules | Quick Settings tile replaces tray playback control. Windows CLI, registry and remote-desktop hooks are not ported. |
| Startup | Android owns the selected WallpaperService lifecycle. No boot receiver or persistent foreground service is needed. |
| UI motion | Native SVG paths animate in the display panel. Tap pauses/resumes the illustration; Reduce motion and Android animator settings are respected. No WebView is used for the app UI. |
| Online account gallery / Windows ML depth generation | Not included. The build has offline scenes and local imports. No fake sign-in, subscription, depth inference or download controls. |

Default quality is 1080p/30, implemented as a 1920-pixel maximum edge and a 30 FPS target for native scenes. Existing custom settings survive the update. Image decode size and web display size follow the resolution budget; video decoding retains the original source. Presets do not interpolate extra video frames, transcode media or change its HDR format. Preview and live service instances are separate, and canceling the wallpaper picker does not update the active preference.

Native scenes use Android's [Choreographer](https://developer.android.com/reference/android/view/Choreographer) to align work with display callbacks. A [surface frame-rate request](https://developer.android.com/media/optimize/performance/frame-rate) is a system hint and does not guarantee achieved FPS or change an imported video's frame rate.
