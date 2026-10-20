# NewTube Clear

An Android NewTube frontend inspired by [ClearPDF](https://github.com/Chethan616/ClearPDF): neutral light/dark canvases, thin glass rims, blue accents, capsule headers, a floating glass dock, spring press motion, and capsule sliders.

## Install

Download the ARM64 preview APK from the GitHub release assets. Android 7.0 or newer is required. The published preview uses `io.github.harshsinghal10h.newtubeclear.preview` and a dedicated Android test certificate; it installs separately from NewTube Glass. The source overlay also supports private release signing with the original fork identity.

Version: `1.15.0-clear.1` (`11504`). ClearPDF Glass is selected once after the update. Settings → Appearance & glass lets you choose ClearPDF, Vaso, LastWave, or Classic.

## Scope

The overlay includes browsing, search and settings surfaces, feed cards, mini-player, watch-page actions, playback transport buttons, programmatic bottom sheets, the video timeline, and native seek bars. Existing player gestures, feeds, downloads, account handling, and network/extraction code are retained. The time bar additionally exposes its range and direct progress adjustment to accessibility services.

The interface is implemented in NewTube's existing Java/View architecture. ClearPDF's Compose renderer is not embedded. The glass dock samples its backdrop; other controls use translucent native materials. This is a design adaptation, not a pixel-identical port of ClearPDF's blur/refraction engine.

## Build from source

The source overlay is complete and pinned to NewTube `v1.15.0`, commit `756e7e56acba61ccdf5b4d5a802963f0eb62616c`, including that commit's recursive submodules.

```bash
git clone --recurse-submodules https://github.com/aleixrodriala/newtube.git
cd newtube
git checkout 756e7e56acba61ccdf5b4d5a802963f0eb62616c
git submodule update --init --recursive
python ../Codex/newtube-clearpdf/apply_frontend.py .
./gradlew :smarttubetv:testStmobileDebugUnitTest --tests 'com.newtube.mobile.ui.glass.*'
./gradlew :smarttubetv:assembleStmobileRelease
```

Use JDK 21, the checked-in Gradle wrapper, Android SDK Platform 37.0, and Build Tools 36.0.0. Configure `sdk.dir` in an untracked `local.properties`. A release build requires your private signing configuration in an untracked `keystore.properties`; no signing keys or passwords are included in this repository.

## Validation

See the release notes and verification report beside the APK. Validation distinguishes compile/unit checks from physical-device testing. Live YouTube availability depends on the retained upstream extraction engine and service responses.

## Attribution

- [NewTube](https://github.com/aleixrodriala/newtube), built on SmartTube; original MIT license retained.
- [ClearPDF](https://github.com/Chethan616/ClearPDF), MIT, design reference and palette attribution. License included under app assets/licenses.
- Existing NewTube Glass work and Google Sans font license retained.
