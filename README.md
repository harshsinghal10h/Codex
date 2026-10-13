# Lively Wallpaper for Android

An independent native Android adaptation inspired by [Lively Wallpaper](https://github.com/lively-community/lively). It preserves the top Library/Gallery navigation, wallpaper thumbnail grid, preview/customisation workflow, active wallpaper controls and settings categories, with Android touch targets and responsive columns.

**This is an initial Android build, not an official Lively release or a complete port of the Windows engine.** Windows binaries cannot be installed on Android. See [Compatibility](docs/COMPATIBILITY.md) for supported equivalents and platform limits.

## Install

Download [Lively-Android-0.1.0.apk](releases/Lively-Android-0.1.0.apk), allow installation from the app you downloaded it with, and install on Android 8.0 or newer. Open any scene, select **Set wallpaper**, and confirm in Android's wallpaper picker. Choosing a different scene and cancelling the picker keeps the current choice.

The APK is signed with this project's independent release key. Updates must use the same key. The private signing backup is distributed separately and is never included in the source repository.

## Features

- Eight offline GPU scenes with live preview, touch response, brightness and speed controls.
- Original video playback, without transcoding, through Android MediaPlayer and device decoders. 4K works only where the device supports the codec and source profile.
- Images, GIFs and animated WebP. Original Ultra HDR JPEG bytes and gain maps are retained. HDR image preview uses Android's native ImageView and requests HDR only for gain-map images on Android 14+. Wallpaper output is controlled by the OS compositor.
- Import videos, images, HTML and compatible Lively ZIP packages through Android's document picker. Import streaming uses a bounded buffer and preserves a local original.
- Web wallpapers on an app-owned virtual display; the app UI is fully native. No bitmap capture of a WebView, screen-recording permission, overlay permission or JavaScript-to-native bridge.
- LivelyProperties sliders, checkboxes, dropdowns, text and colour values, and package sharing with current property values.
- Search, favourites, imported-wallpaper rename/delete, rotation and double tap to change.
- Visibility, screen, battery, Battery Saver and thermal rules. Hidden playback stops; hidden renderer resources are released after 15 seconds. No background polling while hidden.
- Native Liquid Glass navigation on Android 13+, with a static material fallback on older systems; light, dark and system appearance.
- Android screensaver integration and a Quick Settings pause/resume tile.
- Device display/codec diagnostics and a real app-process PSS memory chart.

## Build

Install JDK 17 and Android SDK **platforms;android-35** and **build-tools;35.0.0**. No third-party runtime dependencies are required.

```sh
export ANDROID_HOME=/path/to/android-sdk
export LIVELY_KEY_PASSWORD='your-keystore-password'
python3 tools/build.py --keystore /private/path/lively-release.jks --alias lively
```

The SDK build path compiles Java, links Android resources, desugars to DEX, aligns the APK and verifies its signature. The source can also be opened as a conventional Android Studio / Gradle project; Gradle release output is unsigned until your signing configuration is added locally.

```sh
python3 tools/test-core.py
python3 tools/build-tests.py --sdk "$ANDROID_HOME" --keystore /private/path/lively-release.jks
adb install -r build/Lively-Android-0.1.0.apk
adb install -r build/native-tests/native-tests.apk
adb shell am instrument -w io.lively.android.tests/io.lively.android.NativeTests
```

The native test APK is a separate development artifact. It is not bundled into the installed release. Physical-device HDR output, manufacturer-specific lock-screen behavior, and real battery consumption require device testing.

[Release validation](docs/VALIDATION.md) records the completed checks and their limits. [Design review](docs/CRITIQUE.md) records the native screenshot review.

## Design and licensing

The upstream source/layout review uses commit `25a0a5f4ed1988ff42117d3a17feb321062237fe`. The native renderer is new Android code; it does not embed the Windows C#/WinUI runtime. Included scene artwork is generated from this project's own shaders.

GPL-3.0. Credit to Lively's original creator, rocksdanister / Dani John, and the lively-community maintainers. MIT design references and vendored Anatomy code are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
