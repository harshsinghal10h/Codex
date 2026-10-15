# Android integration checks

A separate, same-certificate instrumentation APK exercises the installed release package. Only generated safe colour bars are used. The test waits for the WebView/native bridge, scans locally with the bundled detector, observes actual LibVLC playback, inserts a synthetic interval to exercise native skipping and masking, and checks portrait/landscape prompt bounds. It records the OS, ABI and WebView version and saves screenshots.

This checks integration on emulators. It does not evaluate nudity-recognition accuracy, physical-device decoder behavior or every Android build. The test APK has no networking, model or private signing key. Its source is included here; signing a rebuilt test requires the same private development certificate as the app. The private key is intentionally excluded from GitHub. `build.py` is the local SDK build recipe.
