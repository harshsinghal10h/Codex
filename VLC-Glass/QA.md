# Verification scope

The build produces architecture-specific Android packages and independent desktop packages. The Android APK is signed with the existing private development key, which is excluded from source and public artifacts.

Checks cover timeline normalization, merging, padding, hysteresis, speed-adjusted look-ahead, seeks into intervals, end-of-video behavior, incomplete frame coverage and invalid scores. Package checks verify bundled UI/model/native libraries, APK signatures, and 16 KB ELF load alignment on 64-bit Android libraries.

The desktop packaged-runtime smoke test loads LibVLC and the ONNX detector and evaluates a safe synthetic frame. The UI playback test uses safe colour bars and a synthetic skip interval; it is intended to check the actual Qt page/bridge, scanning, native playback and seeking, not recognition accuracy.

No explicit-video benchmark or held-out domain calibration was supplied. False-positive/false-negative rates and all-device latency remain unmeasured. No live Gemini request can be validated without user credentials. Physical phones, TVs, HDR output, external displays, Bluetooth/casting, lifecycle behavior and OS-wide compatibility require device testing. Report individual CI outcomes rather than treating one platform's success as proof of another.

Android integration tests use the installed, same-certificate x86-64 APK on Android 9 phone and Android 15 tablet emulators. They check the actual WebView/native bridge, safe synthetic-video scanning, native playback clock, a synthetic skip, mask clearing and portrait/landscape prompt bounds. Reports include the exact WebView version. These tests do not establish real-device latency or classifier accuracy. Desktop UI checks also require an active video output; the Mac fallback must paint decoded frames.
