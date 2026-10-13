# Release validation

Release: Lively Android 0.1.0, Android 8.0+ (minimum API 26, target API 35).

The published APK is 308,635 bytes. SHA-256:

```text
a3f67e3e99da9c802f05e0df59512998da1011097bc2cc54b5fcc7e4980c5588
```

The APK passes zip alignment and Android APK signature v2/v3 verification. The independent release key is kept outside this repository; the separate private signing backup is required to sign compatible updates.

The release application source at commit `fc0df6b9b341b5c830370e6b1a46634e3de9b359` passed the [Android build](https://github.com/harshsinghal10h/Codex/actions/runs/38430925957) and [Android 15 emulator suite](https://github.com/harshsinghal10h/Codex/actions/runs/38430926156). The [clean final capture run](https://github.com/harshsinghal10h/Codex/actions/runs/38432897849) also passed all 39 assertions. Later capture-harness changes do not change the release APK.

- 28 policy and package assertions cover visibility, power and thermal rules, resolution bounds, ZIP traversal/collision/size limits, unsupported executable types and extraction rollback.
- 39 real Android assertions cover original image bytes, gain-map preservation, import/export, all eight GPU scenes, stopping hidden GPU playback, web presentation, image preview, original 3840-pixel video decoding, inactive preview controls, unobscured last-row scrolling, stable glass opacity in both themes and complete secondary-button touch bounds.

The native suite builds a separate test APK and uses a temporary CI signing key for both test artifacts. The shipped APK contains no instrumentation. Runtime coverage therefore validates the same application source; the distributed release signature is verified separately.

Screenshots are emulator captures. One capture run encountered an Android Quickstep launcher error dialog; the capture harness handles that emulator dialog before screenshots. The dialog named the launcher, and logcat recorded `ANR in com.android.launcher3`.

Physical-device HDR output, manufacturer home/lock-screen restrictions, battery drain and whole-device memory consumption remain unmeasured. The app's memory chart reports its process PSS; decoder and WebView child processes are separate. Emulator screenshots do not establish HDR display output.

See [Compatibility](COMPATIBILITY.md) for Windows engine and gallery features that are not included in this independent Android adaptation.

